package scumvtuning

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.{Field, Parameters}
// import freechips.rocketchip.diplomacy.{LazyModule, LazyModuleImp}
import freechips.rocketchip.regmapper.{HasRegMap, RegField, RegisterWriteIO}
import freechips.rocketchip.prci._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.interrupts._
// import freechips.rocketchip.tilelink.{TLIdentityNode, TLRegBundle, TLRegModule, TLRegisterRouter}
import freechips.rocketchip.tilelink._
import testchipip.util.{ClockedIO}
import freechips.rocketchip.diplomacy._


import ee290cdma._
import freechips.rocketchip.prci.ClockSinkParameters

case class SCUMVTuningParams (
  address: BigInt = 0xa000,
  paddrBits: Int = 32,
  maxReadSize: Int = 258,
  oscillatorTuningBits: Int = 16,
  oscillatorDebugMuxBits: Int = 2,
  supplyDACBits: Int = 5
)

trait HasSCUMVTuningAnalogIO {
  def io: SCUMVTuningAnalogIO
}


case object SCUMVTuningKey extends Field[Option[SCUMVTuningParams]](None)

// Digital Oscillator and Supply teams - Tuning & Debugging
class SCUMVTuningAnalogIO(params: SCUMVTuningParams) extends Bundle {
  val oscillator = new SCUMVTuningOscillatorIO  
  val supply = new SCUMVTuningSupplyIO
}

class SCUMVTuningOscillatorIO extends Bundle {
  val tuneOut = new Bundle {
    val adc_coarse = Output(UInt(9.W))
    val dig = Output(UInt(6.W))
  }
  val sel = new Bundle {
    val cpu_clk = Output(UInt(2.W))
    val debug_clk = Output(UInt(1.W))
  }
}

class SCUMVTuningSupplyIO extends Bundle {
  val bgr = new Bundle {
    val tempCtrl = Output(UInt(5.W))
    val vrefCtrl = Output(UInt(5.W))
  }
  val clkOvrd = Output(Bool())
}


class SCUMVTuningFrontend(val params: SCUMVTuningParams, beatBytes: Int)(implicit p: Parameters)
    extends RegisterRouter(RegisterRouterParams("scumvtuning", Seq("ucbbar, riscv"),
      params.address, beatBytes= beatBytes))
    with HasTLControlRegMap
    with HasInterruptSources{
  override def nInterrupts = 5
  def tlRegmap(mapping: RegField.Map*): Unit = regmap(mapping:_*)
  override lazy val module = new SCUMVTuningFrontendModuleImp(this)
}

class SCUMVTuningFrontendModuleImp(outer: SCUMVTuningFrontend) (implicit p: Parameters) extends LazyModuleImp(outer) {
  // val params: SCUMVTuningParams
  val io = IO(new Bundle {
      val oscillator = new SCUMVTuningOscillatorIO
      val supply = new SCUMVTuningSupplyIO
  })
  val params = outer.params
  val adc_tune_out_coarse  = RegInit("b001000000".U(9.W))
  val dig_tune_out  = RegInit("b000001".U(6.W))
  val sel_cpu_clk = RegInit("b00".U(2.W))
  val sel_debug_clk = RegInit("b0".U(1.W))

  // little endian b4 b3 b2 b1 b0
  val bgr_temp_ctrl           = RegInit(21.U(5.W)) // 1 0 1 0 1 // doesn't match intra IO sheet
  val bgr_vref_ctrl           = RegInit(15.U(5.W)) // 0 1 1 1 1 // doesn't match intra IO sheet
  val clk_ovrd                = RegInit(false.B) // matches intra IO sheet

  io.oscillator.tuneOut.adc_coarse      := adc_tune_out_coarse
  // io.oscillator.tuneOut.adc_fine     := adc_tune_out_fine
  io.oscillator.tuneOut.dig      := dig_tune_out
  io.oscillator.sel.cpu_clk := sel_cpu_clk
  io.oscillator.sel.debug_clk := sel_debug_clk

  io.supply.bgr.tempCtrl         := bgr_temp_ctrl         
  io.supply.bgr.vrefCtrl         := bgr_vref_ctrl         
  io.supply.clkOvrd              := clk_ovrd

  outer.tlRegmap( // CHECK WITH DANIEL FOR CORRECTNESS
      0x00 -> Seq(RegField(9, adc_tune_out_coarse)),
      0x02 -> Seq(RegField(6, dig_tune_out)),                 
      0x03 -> Seq(RegField(2, sel_cpu_clk)),
      0x04 -> Seq(RegField(1, sel_debug_clk)),
      0x05 -> Seq(RegField(5, bgr_temp_ctrl)),             
      0x06 -> Seq(RegField(5, bgr_vref_ctrl)),             
      0x07 -> Seq(RegField(1, clk_ovrd)),
  )
}

class SCUMVTuning(params: SCUMVTuningParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p)  {
  val mmio = TLIdentityNode()
  val scumvtuningFrontend = LazyModule(new SCUMVTuningFrontend(params, beatBytes))
  val intnode = scumvtuningFrontend.intXing(NoCrossing)

  scumvtuningFrontend.node := mmio

  override lazy val module = new SCUMVTuningImpl
  class SCUMVTuningImpl  extends Impl with HasSCUMVTuningAnalogIO{
    val io = dontTouch(IO(new SCUMVTuningAnalogIO(params)))  
    io <> scumvtuningFrontend.module.io
  }
}
