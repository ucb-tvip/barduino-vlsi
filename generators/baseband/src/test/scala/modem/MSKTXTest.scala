package modem

import chisel3._
import chiseltest._
import chiseltest.{TreadleBackendAnnotation, WriteVcdAnnotation}

import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.immutable.Seq

import breeze.plot.{Figure, plot}

// import verif._
import modem.TestUtility._
import baseband.BasebandModemParams

import java.io.{BufferedWriter, FileWriter}

/* Note this is a purely visual test and should be manually inspected to check that
   the two plots line up exactly.
 */
class MSKTXTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "Elaborate a MSKTX" in {
    test(new MSKTX(new BasebandModemParams)).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      // val inDriver = new DecoupledDriverMaster(c.clock, c.io.digital.in)
      c.io.digital.in.initSource().setSourceClock(c.clock)
      c.clock.setTimeout(2000)

      val packet = Seq(0x03, 0x69, 0x42, 0x31) // PHR, 1-byte payload, 2-byte CRC
      val inBits = symbolsToChip(rawLRWPANPacket(packet)).map {b => if (b) 1 else 0}

      // c.io.digital.in.enqueueSeq(inBits.map(b => b.U))
      fork {
        c.io.digital.in.enqueueSeq(inBits.map(b => b.U))
      }
      c.io.control.valid.poke(true.B)
      c.io.control.bits.totalBytes.poke(1.U) // 1 byte payload (rest is calculated by the module)
      c.clock.step()
      c.io.control.valid.poke(false.B)

      // Build the output codes array from the values peeked at the circuit output
      var outCodes = Seq[Int]()
      while (c.io.out.state.peek().litValue.toInt != 0) {
        outCodes = outCodes ++ Seq(c.io.modIndex.peek().litValue.toInt)
        c.clock.step()
      }

      // Save the output codes to a file
      var file = "msktx_vco_out.csv"
      var writer = new BufferedWriter(new FileWriter(file))
      outCodes.map(s => s.toString + ", ").foreach(writer.write)
      writer.close()

      // Save the corresponding input bits to a file, so they can be correlated together
      file = "msktx_bits_in.csv"
      writer = new BufferedWriter(new FileWriter(file))
      inBits.map(s => s.toString + ", ").foreach(writer.write)
      writer.close()
      
      val delay = 16 // adjust as needed until first code comes out
      val cyclesPerChip = 16 // 32 MHz ADC clock / 2 Mchip/s = 16 cycles/chip
      val expectCodes = Seq.fill(delay)(31) ++ chipToMSK(symbolsToChip(rawLRWPANPacket(packet)))
                                                .flatMap {i => Seq.fill(cyclesPerChip)(if (i) 63 else 0)}

      // Assert that the output codes are equal to the expected codes
      outCodes.zip(expectCodes).foreach{case(o, e) => assert(o == e)}

      val f = Figure()
      val p = f.subplot(0)
      p += plot(Seq.tabulate(outCodes.length)(i => i), outCodes)
      p += plot(Seq.tabulate(expectCodes.length)(i => i), expectCodes)
      p.title = "Output Codes vs Expected Codes"
      p.xlabel = "Sample Index"
      p.ylabel = "VCO modulation code"
    }
  }
}

import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._
import chisel3.experimental.{DataMirror, Direction}
import chiseltest._
import scala.collection.mutable

class DecoupledTX[T <: Data](gen: T) extends Bundle {
  val data: T = gen.cloneType
  // TODO: move these meta fields into typeclasses that can be mixed in with DecoupledTX
  val waitCycles: UInt = UInt(32.W)
  val postSendCycles: UInt = UInt(32.W)
  val cycleStamp: UInt = UInt(32.W)

  // TODO: split into driver and monitor TXs
  // TODO: how can we check that data: T fits into gen? (e.g. gen = UInt(2.W), data = 16.U shouldn't work)
  def tx(data: T, waitCycles: Int, postSendCycles: Int): DecoupledTX[T] = {
    this.Lit(_.data -> data, _.waitCycles -> waitCycles.U, _.postSendCycles -> postSendCycles.U, _.cycleStamp -> 0.U)
  }
  def tx(data: T): DecoupledTX[T] = {
    this.Lit(_.data -> data, _.waitCycles -> 0.U, _.postSendCycles -> 0.U)
  }
  def tx(data: T, cycleStamp: Int): DecoupledTX[T] = {
    this.Lit(_.data -> data, _.cycleStamp -> cycleStamp.U)
  }

  def tx(data: T, randomWaitCycles: (Int, Int)): DecoupledTX[T] = {
    this.Lit(_.data -> data, _.waitCycles -> (scala.util.Random.nextInt(randomWaitCycles._2) + randomWaitCycles._1).U)
  }

  // override def cloneType: this.type = (new DecoupledTX(gen)).asInstanceOf[this.type]
}

// TODO: combine driver and monitor into VIP/Agent to keep API clean
// TODO: VIP/Agent should have master and slave modes (monitor should never peek)
class DecoupledDriverMaster[T <: Data](clock: Clock, interface: DecoupledIO[T]) {
  assert(DataMirror.directionOf(interface.valid) == Direction.Input, "DecoupledDriverMaster is connected to a master port, not a slave")
  val inputTransactions: mutable.Queue[DecoupledTX[T]] = mutable.Queue[DecoupledTX[T]]()
  fork.withRegion(TestdriverMain) {
    var cycleCount = 0
    var idleCycles = 0
    interface.valid.poke(false.B)
    while (true) {
      if (inputTransactions.nonEmpty && idleCycles == 0) {
        val t = inputTransactions.dequeue()
        if (t.waitCycles.litValue.toInt > 0) {
          idleCycles = t.waitCycles.litValue.toInt
          while (idleCycles > 0) {
            idleCycles -= 1
            cycleCount += 1
            clock.step()
          }
        }
        while (!interface.ready.peek().litToBoolean) {
          cycleCount += 1
          clock.step()
        }

        cycleCount += 1
        timescope { // TODO: why do we need a new timescope, can we force valid to false later explicitly?
          t.data match {
            case bundle: Bundle =>
              interface.bits.asInstanceOf[Bundle].pokePartial(bundle)
              // TODO: why is this special cased?
//              interface.bits.poke(t.data)
            case _ =>
              interface.bits.poke(t.data)
          }
          interface.valid.poke(true.B)
          clock.step()
        }

        idleCycles = t.postSendCycles.litValue.toInt
      } else {
        if (idleCycles > 0) idleCycles -= 1
        cycleCount += 1
        clock.step()
      }
    }
  }

  def push(txn: DecoupledTX[T]): Unit = {
    inputTransactions += txn
  }

  def push(txns: Seq[DecoupledTX[T]]): Unit = {
    for (t <- txns) {
      inputTransactions += t
    }
  }
}

// TODO: have this return a stream of seen transactions
class DecoupledDriverSlave[T <: Data](clock: Clock, interface: DecoupledIO[T], waitCycles: Int = 0, randomWaitCycles: (Int, Int) = (0,0)) {
  assert(DataMirror.directionOf(interface.valid) == Direction.Output, "DecoupledDriverSlave is connected to a slave port, not a master")
  fork.withRegion(TestdriverMain) {
    var cycleCount = 0
    var idleCyclesD = 0
    while (true) {
      interface.ready.poke(false.B)
      while (idleCyclesD > 0) {
        idleCyclesD -= 1
        cycleCount += 1
        clock.step()
      }
      interface.ready.poke(true.B)
      if (interface.valid.peek().litToBoolean) {
        if (randomWaitCycles != (0,0)) {
          idleCyclesD = scala.util.Random.nextInt(randomWaitCycles._2) + randomWaitCycles._1
        } else {
          idleCyclesD = waitCycles
        }
      }
      cycleCount += 1
      clock.step()
    }
  }
}

class DecoupledMonitor[T <: Data](clock: Clock, interface: DecoupledIO[T]) {
  val monitoredTransactions: mutable.Queue[DecoupledTX[T]] = mutable.Queue[DecoupledTX[T]]()
  fork.withRegion(Monitor) {
    var cycleCount = 0
    while (true) {
      if (interface.valid.peek().litToBoolean && interface.ready.peek().litToBoolean) {
        val t = new DecoupledTX(interface.bits.cloneType.asInstanceOf[T]) // asInstanceOf[T] to make IntelliJ happy
        val tLit = t.Lit(_.data -> interface.bits.peek(), _.cycleStamp -> cycleCount.U)
        monitoredTransactions += tLit
      }
      cycleCount += 1
      clock.step()
    }
  }

  def clearMonitoredTransactions(): Unit = {
    monitoredTransactions.clear()
  }
}
