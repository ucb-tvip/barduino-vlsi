package chipyard.sky130

import chisel3._
import chisel3.experimental.BaseModule
import freechips.rocketchip.diplomacy.{InModuleBody, LazyModule}
import org.chipsalliance.cde.config.{Config, Field}
import chisel3.util.HasBlackBoxResource
import chisel3.util.HasBlackBoxInline

class Sky130EFCaravelPORIO extends Bundle {
  // HV domain
  val porb_h = Output(Bool())

  // LV domain
  val porb_l = Output(Bool())
  val por_l = Output(Bool())
}

trait HasSky130EFCaravelPORIO extends BaseModule {
  val io = IO(new Sky130EFCaravelPORIO)
}

class Sky130EFCaravelPOR extends BlackBox with HasSky130EFCaravelPORIO {
  override val desiredName = "simple_por"
}

class Sky130EFCaravelPORDummyRTLModelVerilog extends BlackBox with HasSky130EFCaravelPORIO with HasBlackBoxInline {
  // Oversimplified model for when the macro or behavioral model
  // are not available / usable

    setInline("Sky130EFCaravelPORDummyRTLModelVerilog.v",
    """module Sky130EFCaravelPORDummyRTLModelVerilog(
      |        output porb_h,
      |        output porb_l,
      |        output por_l
      |);
      |assign porb_h = 1'b1;
      |assign porb_l = 1'b1;
      |assign por_l = 1'b0;
      |endmodule
    """.stripMargin)
}

class Sky130EFCaravelPORDummyRTLModel extends Module with HasSky130EFCaravelPORIO {
  // Oversimplified model for when the macro or behavioral model
  // are not available / usable

  // Stay out of reset
  io.porb_h := true.B
  io.porb_l := true.B
  io.por_l := false.B
}

case object Sky130EFCaravelPORKey extends Field[() => HasSky130EFCaravelPORIO](() => new Sky130EFCaravelPOR)

trait HasSky130EFCaravelPOR {
  this: LazyModule =>

  val por = InModuleBody {
    val por = Module(p(Sky130EFCaravelPORKey)()).suggestName("por")
    por
  }
  val porb_h = InModuleBody {
    val porb_h = Wire(Bool()).suggestName("porb_h")
    porb_h := por.getWrappedValue.io.porb_h

    dontTouch(porb_h) // this net name used for routing

    porb_h
  }
}

class WithRealSky130EFCaravelPOR extends Config((_, _, _) => {
  case Sky130EFCaravelPORKey => () => new Sky130EFCaravelPOR
})
class WithDummySky130EFCaravelPOR extends Config((_, _, _) => {
  case Sky130EFCaravelPORKey => () => new Sky130EFCaravelPORDummyRTLModel
})
class WithVerilogDummySky130EFCaravelPOR extends Config((_, _, _) => {
  case Sky130EFCaravelPORKey => () => new Sky130EFCaravelPORDummyRTLModelVerilog
})
