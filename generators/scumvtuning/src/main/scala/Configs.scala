package scumvtuning
import chisel3.{withClock => withNewClock}

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Config
import freechips.rocketchip.diplomacy.{InModuleBody, LazyModule}
import freechips.rocketchip.subsystem.{BaseSubsystem, PBUS}
import freechips.rocketchip.tilelink._


trait CanHavePeripherySCUMVTuning { this: BaseSubsystem =>
  private val portName = "scumvtuning_io"
  private val pbus = locateTLBusWrapper(PBUS)
  val scumvtuning = p(SCUMVTuningKey).map { params =>
    val scumvtuning = LazyModule(new SCUMVTuning(params, pbus.beatBytes)(p)) 
    scumvtuning.clockNode := pbus.fixedClockNode
    pbus.coupleTo("scumvtuning") { scumvtuning.mmio := TLFragmenter(pbus.beatBytes, pbus.blockBytes) := _ }

    ibus.fromSync := scumvtuning.intnode
    val io = InModuleBody {
      val scumtuningio = IO(new SCUMVTuningAnalogIO(params)).suggestName(portName)
      scumvtuning.module.io.oscillator <> scumtuningio.oscillator
      scumvtuning.module.io.supply <> scumtuningio.supply
      scumtuningio
      }
      io
    }
  }


class WithSCUMVTuning(params: SCUMVTuningParams = SCUMVTuningParams()) extends Config((site, here, up) => {
  case SCUMVTuningKey => Some(params)
})
