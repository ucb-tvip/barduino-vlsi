package baseband

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Config
import freechips.rocketchip.diplomacy.{InModuleBody, LazyModule}
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.{BaseSubsystem, FBUS, PBUS}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util._

class WithBasebandModem(params: BasebandModemParams = BasebandModemParams()) extends Config((site, here, up) => {
  case BasebandModemKey => Some(params)
})

trait CanHavePeripheryBasebandModem { this: BaseSubsystem =>
  private val portName = "baseband_io"
  private val pbus = locateTLBusWrapper(PBUS)
  private val fbus = locateTLBusWrapper(FBUS)
  val (analog_bm_clock_node, analog_bm_clock_pin) = p(BasebandModemKey).map { params =>
    val analog_bm_clock_pin = InModuleBody {
      IO(Input(Clock())).suggestName("analog_bm_clock_in")
    }
    val analog_bm_ClockNode = ClockSourceNode(Seq(ClockSourceParameters()))
    InModuleBody {
      analog_bm_ClockNode.out(0)._1.clock := analog_bm_clock_pin
      analog_bm_ClockNode.out(0)._1.reset := ResetCatchAndSync(analog_bm_clock_pin, pbus.module.reset.asBool)
    }

    (analog_bm_ClockNode, analog_bm_clock_pin)
  }.getOrElse((null, null))

  //  lazy val bm_ios: Option[BasebandModemAnalogIO] = p(BasebandModemKey).map { params =>
   val bm_ios = p(BasebandModemKey).map { params =>
    val bm_block = { LazyModule(new BasebandModem(params, pbus.beatBytes)(p)) }

    if (analog_bm_clock_node != null) {
      bm_block.clockNode := analog_bm_clock_node
    } else {
      println("Warning: BM clock node is null, check BasebandModemKey configuration.")
    }

    pbus.coupleTo("baseband") {
      TLInwardClockCrossingHelper("bm_pbus_crossing", bm_block, bm_block.mmio)(AsynchronousCrossing()) :=
      TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _
    }
    fbus.coupleFrom("baseband") {
      _ := TLOutwardClockCrossingHelper("bm_fbus_crossing", bm_block, bm_block.mem)(AsynchronousCrossing())
    }
    ibus.fromSync := bm_block.intnode

    val io = InModuleBody {
      val bm_io = IO(new BasebandModemAnalogIO(params)).suggestName(portName)

      bm_block.module.io.intra.lo_div8_clock := bm_io.intra.lo_div8_clock
      bm_io.intra.data.tx := bm_block.module.io.intra.data.tx
      bm_block.module.io.intra.data.rx := bm_io.intra.data.rx
      bm_io.intra.data.tuning := bm_block.module.io.intra.data.tuning
      
      bm_io.intra.tuning <> bm_block.module.io.intra.tuning
      bm_io.bump.offChipMode.rx := bm_block.module.io.bump.offChipMode.rx
      bm_io.bump.offChipMode.tx := bm_block.module.io.bump.offChipMode.tx

      bm_io
    


    }
    io
  }
}

