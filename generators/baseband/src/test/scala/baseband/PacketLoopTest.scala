package baseband

import chisel3._
import chisel3.experimental.BundleLiterals.AddBundleLiteralConstructor
import chisel3.util._
import chiseltest._
import chiseltest.{TreadleBackendAnnotation, VerilatorBackendAnnotation, WriteVcdAnnotation}

import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.immutable.Seq

import modem.Modem
import modem.TestUtility._

// import verif._

class BLEPacketLoop extends Module {
  val io = IO(new Bundle {
    val assembler = new BLEPAInputIO
    val disassembler = new Bundle {
      val out = new PDAOutputIO
    }
    val constants = Input(new BasebandConstants)
  })

  /* Assembler */
  val assembler = Module(new BLEPacketAssembler)
  assembler.io.constants := io.constants
  assembler.io.in <> io.assembler

  /* Disassembler */
  val disassembler = Module(new BLEPacketDisassembler(BasebandModemParams()))
  disassembler.io.constants := io.constants
  disassembler.io.in.control.valid := true.B // not great, but it should work
  disassembler.io.in.control.bits.command := PDAControlInputCommands.START_CMD
  io.disassembler.out <> disassembler.io.out

  /* Modem */
  val modem = Module(new Modem(BasebandModemParams()))
  modem.io <> DontCare
  modem.io.digital.ble.tx <> assembler.io.out.data
  modem.io.digital.ble.rx <> disassembler.io.in.data
  modem.io.control.constants := io.constants
  modem.io.control.firCmd.valid := false.B
  modem.io.control.bleLoopback := true.B
  modem.io.control.rx.enable := true.B

  modem.io.control.gfskTX.in.bits.totalBytes := assembler.io.in.control.bits.pduLength + 2.U
  modem.io.control.gfskTX.in.valid := assembler.io.in.control.valid
  // gfskTX is always ready hopefully
}

class BLEPacketLoopTest extends AnyFlatSpec with ChiselScalatestTester {
  def seqToBinary(in: Seq[Int]): Seq[Int] = {
    in.map(x => String.format("%8s", x.toBinaryString).replaceAll(" ", "0").reverse).mkString("").map(c => c.toString.toInt)
  }

  /*
  // TODO: This does not check without whitening because the packet disassembler does not support disabling whitening
  it should "Pass a circular test without whitening" in {
    test(new BLEPacketLoop).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      val paControlDriver = new DecoupledDriverMaster(c.clock, c.io.assembler.control)
      val paDMADriver = new DecoupledDriverMaster(c.clock, c.io.assembler.data)
      val pdaDMADriver = new DecoupledDriverSlave(c.clock, c.io.disassembler.out.data, 0) // TODO: randomize?
      val pdaDMAMonitor = new DecoupledMonitor(c.clock, c.io.disassembler.out.data)

      val pduLength = scala.util.Random.nextInt(255)
      val inBytes = Seq(0, pduLength) ++ Seq.tabulate(pduLength)(_ => scala.util.Random.nextInt(255))
      //val pduLength = 4
      //val inBytes = Seq(0, pduLength) ++ Seq.tabulate(pduLength)(i => i)
      val aa = BigInt("8E89BED6", 16)

      c.io.constants.radioMode.poke(RadioMode.BLE)
      c.io.constants.crcSeed.poke("x555555".U)
      c.io.constants.accessAddress.poke(aa.U)
      c.io.constants.bleChannelIndex.poke("b000000".U)

      paDMADriver.push(inBytes.map(x => (new DecoupledTX(UInt(8.W))).tx(x.U)))
      paControlDriver.push(new DecoupledTX(new BLEPAControlInputBundle).tx((new BLEPAControlInputBundle).Lit(_.aa -> aa.U, _.pduLength -> pduLength.U)))

      val expectedOut = inBytes ++ bleCRCBytes(inBytes)
      println(expectedOut)

      while(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length != expectedOut.length) {
        c.clock.step()
      }

      c.clock.step(256)

      assert(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length == expectedOut.length)

      pdaDMAMonitor.monitoredTransactions
        .map(x => x.data.litValue)
        .zip(expectedOut)
        .foreach {case (o, e) => assert(o == e)}
    }
  }
*/
  it should "Pass a circular test with whitening" in {
    test(new BLEPacketLoop).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      val paControlDriver = new DecoupledDriverMaster(c.clock, c.io.assembler.control)
      val paDMADriver = new DecoupledDriverMaster(c.clock, c.io.assembler.data)
      val pdaDMADriver = new DecoupledDriverSlave(c.clock, c.io.disassembler.out.data, 0) // TODO: randomize?
      val pdaDMAMonitor = new DecoupledMonitor(c.clock, c.io.disassembler.out.data)

      val pduLength = scala.util.Random.nextInt(255)
      val inBytes = Seq(0, pduLength) ++ Seq.tabulate(pduLength)(_ => scala.util.Random.nextInt(255))
      val aa = BigInt("8E89BED6", 16)

      c.io.constants.radioMode.poke(RadioMode.BLE)
      c.io.constants.crcSeed.poke("x555555".U)
      c.io.constants.accessAddress.poke(aa.U)
      c.io.constants.bleChannelIndex.poke((scala.util.Random.nextInt(62) + 1).U) // Poke random 6 bit value (not 0)

      paDMADriver.push(inBytes.map(x => (new DecoupledTX(UInt(8.W))).tx(x.U)))
      paControlDriver.push(new DecoupledTX(new BLEPAControlInputBundle).tx((new BLEPAControlInputBundle).Lit(_.aa -> aa.U, _.pduLength -> pduLength.U)))

      val expectedOut = inBytes ++ bleCRCBytes(inBytes)
      println(expectedOut)

      while(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length != expectedOut.length) {
        c.clock.step()
      }

      c.clock.step(256)

      assert(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length == expectedOut.length)

      pdaDMAMonitor.monitoredTransactions
        .map(x => x.data.litValue)
        .zip(expectedOut)
        .foreach {case (o, e) => assert(o == e)}
    }
  }
/*
  // TODO: This does not check without whitening because the packet disassembler does not support disabling whitening
  it should "Pass repeated circular tests without whitening" in {
    test(new BLEPacketLoop).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      val paControlDriver = new DecoupledDriverMaster(c.clock, c.io.assembler.control)
      val paDMADriver = new DecoupledDriverMaster(c.clock, c.io.assembler.data)
      val pdaDMADriver = new DecoupledDriverSlave(c.clock, c.io.disassembler.out.data, 0) // TODO: randomize?
      val pdaDMAMonitor = new DecoupledMonitor(c.clock, c.io.disassembler.out.data)

      for (_ <- 0 until 8) {
        val pduLength = scala.util.Random.nextInt(255)
        val inBytes = Seq(0, pduLength) ++ Seq.tabulate(pduLength)(_ => scala.util.Random.nextInt(255))
        val aa = BigInt("8E89BED6", 16)

        c.io.constants.radioMode.poke(RadioMode.BLE)
        c.io.constants.crcSeed.poke("x555555".U)
        c.io.constants.accessAddress.poke(aa.U)
        c.io.constants.bleChannelIndex.poke("b000000".U)

        paDMADriver.push(inBytes.map(x => (new DecoupledTX(UInt(8.W))).tx(x.U)))
        paControlDriver.push(new DecoupledTX(new BLEPAControlInputBundle).tx((new BLEPAControlInputBundle).Lit(_.aa -> aa.U, _.pduLength -> pduLength.U)))

        val expectedOut = inBytes ++ bleCRCBytes(inBytes)
        println(expectedOut)

        while(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length != expectedOut.length) {
          c.clock.step()
        }

        c.clock.step(256)

        assert(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length == expectedOut.length)

        pdaDMAMonitor.monitoredTransactions
          .map(x => x.data.litValue)
          .zip(expectedOut)
          .foreach { case (o, e) => assert(o == e) }

        pdaDMAMonitor.monitoredTransactions.clear()
      }
    }
  }
 */

  it should "Pass repeated circular tests with whitening" in {
    test(new BLEPacketLoop).withAnnotations(Seq(TreadleBackendAnnotation, WriteVcdAnnotation)) { c =>
      val paControlDriver = new DecoupledDriverMaster(c.clock, c.io.assembler.control)
      val paDMADriver = new DecoupledDriverMaster(c.clock, c.io.assembler.data)
      val pdaDMADriver = new DecoupledDriverSlave(c.clock, c.io.disassembler.out.data, 0) // TODO: randomize?
      val pdaDMAMonitor = new DecoupledMonitor(c.clock, c.io.disassembler.out.data)

      for (_ <- 0 until 8) {
        val pduLength = scala.util.Random.nextInt(255)
        val inBytes = Seq(0, pduLength) ++ Seq.tabulate(pduLength)(_ => scala.util.Random.nextInt(255))
        val aa = BigInt("8E89BED6", 16)

        c.io.constants.radioMode.poke(RadioMode.BLE)
        c.io.constants.crcSeed.poke("x555555".U)
        c.io.constants.accessAddress.poke(aa.U)
        c.io.constants.bleChannelIndex.poke((scala.util.Random.nextInt(62) + 1).U) // Poke random 6 bit value (not 0)

        paDMADriver.push(inBytes.map(x => (new DecoupledTX(UInt(8.W))).tx(x.U)))
        paControlDriver.push(new DecoupledTX(new BLEPAControlInputBundle).tx((new BLEPAControlInputBundle).Lit(_.aa -> aa.U, _.pduLength -> pduLength.U)))

        val expectedOut = inBytes ++ bleCRCBytes(inBytes)
        println(expectedOut)

        while(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length != expectedOut.length) {
          c.clock.step()
        }

        c.clock.step(256)

        assert(pdaDMAMonitor.monitoredTransactions.map(x => x.data.litValue).length == expectedOut.length)

        pdaDMAMonitor.monitoredTransactions
          .map(x => x.data.litValue)
          .zip(expectedOut)
          .foreach { case (o, e) => assert(o == e) }

        pdaDMAMonitor.monitoredTransactions.clear()
      }
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
