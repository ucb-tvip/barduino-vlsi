package baseband

import chisel3._
import chisel3.{withClock => withNewClock}
import chisel3.util._
import org.chipsalliance.cde.config._
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.tilelink._
import fixedpoint._
import fixedpoint.{fromIntToBinaryPoint}
// import testchipip.util.{ClockedIO}

import ee290cdma._
import modem._
import freechips.rocketchip.prci.ClockSinkParameters

case class BasebandModemParams (
  address: BigInt = 0x8000,
  paddrBits: Int = 32,
  maxReadSize: Int = 258,
  adcBits: Int = 8,
  adcQueueDepth: Int = 2,
  cmdQueueDepth: Int = 4,
  modemQueueDepth: Int = 256,
  asyncQueueDepth: Int = 16,
  adcClockTuningAsyncQueueDepth: Int = 4,
  cyclesPerSymbol: Int = 10,
  samplesPerSymbol: Int = 20,
  interruptMessageQueueDepth: Int = 1,
  loadBLECoefficients: Boolean = true,
)

case object BasebandModemKey extends Field[Option[BasebandModemParams]](None)

class BasebandModemAnalogIO(params: BasebandModemParams) extends Bundle {
  val intra = new BasebandModemIntraIO(params)
  val bump = new BasebandModemBumpIO(params)
}

class BasebandModemIntraIO(params: BasebandModemParams) extends Bundle {
  val lo_div8_clock = Input(Bool())
  val data = new modem.ModemAnalogPunchIO(params) // problematic wires come from this
  val tuning = new modem.ModemTuningIO
}

class BasebandModemBumpIO(params: BasebandModemParams) extends Bundle {
  val offChipMode = new Bundle {
    val rx = Output(Bool())
    val tx = Output(Bool())
  }
}

trait HasBasebandModemAnalogIO {
  def io: BasebandModemAnalogIO
}

class BasebandModemCommand extends Bundle {
  val inst = new Bundle {
    val primaryInst = UInt(4.W)
    val secondaryInst = UInt(4.W)
    val data = UInt(24.W)
  }
  val additionalData = UInt(32.W)
}

class BasebandModemStatus extends Bundle {
  val status0 = UInt(32.W)
  val status1 = UInt(32.W)
  val status2 = UInt(32.W)
  val status3 = UInt(32.W)
  val status4 = UInt(64.W)
}

class BasebandModemInterrupts extends Bundle {
  val rxError = Bool()
  val rxStart = Bool()
  val rxFinish = Bool()
  val txError = Bool()
  val txFinish = Bool()
}

class BasebandModemMessagesIO extends Bundle {
  val rxErrorMessage = Decoupled(UInt(32.W))
  val rxFinishMessage = Decoupled(UInt(32.W))
  val txErrorMessage = Decoupled(UInt(32.W))
}

class BasebandModemBackendIO extends Bundle {
  val cmd = Decoupled(new BasebandModemCommand)
  val lutCmd = Valid(new ModemLUTCommand)
  val firCmd = Valid(new FIRCoefficientChangeCommand)
  val status = Input(new BasebandModemStatus)
  val interrupt = Input(new BasebandModemInterrupts)
  val messages = Flipped(new BasebandModemMessagesIO)
  val ifCounter = Flipped(new ModemIFCounterStateIO)
  val adcClockTuning = Flipped(new ModemADCClockTuningStateIO)
}
// class BasebandModemFrontend(params: BasebandModemParams, beatBytes: Int)(implicit p: Parameters)
//   extends TLRegisterRouter(
//     params.address, "baseband", Seq("ucbbar, riscv"),
//     beatBytes = beatBytes, interrupts = 6)( // TODO: Interrupts and compatible list
//       new TLRegBundle(params, _) with BasebandModemFrontendBundle)(
//       new TLRegModule(params, _, _) with BasebandModemFrontendModule)
class BasebandModemFrontend(val params: BasebandModemParams, beatBytes: Int)(implicit p: Parameters)
    extends RegisterRouter(RegisterRouterParams("baseband", Seq("ucbbar, riscv"),
      params.address, beatBytes= beatBytes))
    with HasTLControlRegMap
    with HasInterruptSources{
  override def nInterrupts = 6
  def tlRegmap(mapping: RegField.Map*): Unit = regmap(mapping:_*)
  override lazy val module = new BasebandModemFrontendModuleImp(this)
}

class BasebandModemFrontendModuleImp(outer: BasebandModemFrontend) (implicit p: Parameters) extends LazyModuleImp(outer) {
 val params = outer.params

 val io = IO(new Bundle {
  val back = new BasebandModemBackendIO
  val tuning = new ModemTuningIO
  val tuningControl = Output(new ModemTuningControl(params))
})

  // Assertions for RegMap Parameter Correctness
  assert(params.adcBits <= 8, s"ADC bits set to ${params.adcBits}, must less than or equal to 8")

  // Instruction from processor
  val inst = Wire(new DecoupledIO(UInt(32.W)))
  val additionalData = Reg(UInt(32.W))

  // Writing to the instruction triggers the command to be valid.
  // So if you wish to set data you write that first then write inst
  inst.ready := io.back.cmd.ready
  io.back.cmd.bits.additionalData := additionalData
  io.back.cmd.bits.inst.primaryInst := inst.bits(3, 0)
  io.back.cmd.bits.inst.secondaryInst := inst.bits(7, 4)
  io.back.cmd.bits.inst.data := inst.bits(31, 8)
  io.back.cmd.valid := inst.valid

  // LUT set instruction from processor
  val lutCmd = Wire(new DecoupledIO(UInt(32.W)))
  lutCmd.ready := true.B
  io.back.lutCmd.bits.lut := lutCmd.bits(3, 0)
  io.back.lutCmd.bits.address := lutCmd.bits(9, 4)
  io.back.lutCmd.bits.value := lutCmd.bits(31, 10)
  io.back.lutCmd.valid := lutCmd.valid

  //FIR filter reprogramming instruction
  val firCmd = Wire(new DecoupledIO(UInt(32.W)))
  firCmd.ready := true.B
  io.back.firCmd.bits.fir := firCmd.bits(3, 0)
  io.back.firCmd.bits.coeff := firCmd.bits(9, 4)
  io.back.firCmd.bits.value := firCmd.bits(31, 10)
  io.back.firCmd.valid := firCmd.valid

  // Tuning bits store
  val trim_g0 = RegInit(12.U(8.W))
  val trim_g1 = RegInit(0.U(8.W))
  val trim_g2 = RegInit(0.U(8.W))
  // val trim_g3 = RegInit(0.U(8.W))
  // val trim_g4 = RegInit(0.U(8.W))
  // val trim_g5 = RegInit(0.U(8.W))
  // val trim_g6 = RegInit(0.U(8.W))
  // val trim_g7 = RegInit(0.U(8.W))
  
  val debug_enable = RegInit(false.B)

  val i_vga_gain_ctrl = RegInit(0.U(10.W))
  val i_vgaAtten_reset = RegInit(false.B)
  val i_vgaAtten_useAGC = RegInit(false.B)

  // TODO verify initial values
  val i_vgaAtten_sampleWindow = RegInit(22.U(8.W))
  val i_vgaAtten_idealPeakToPeak = RegInit(math.pow(2, params.adcBits - 1).toInt.U(params.adcBits.W))
  val i_vgaAtten_toleranceP2P = RegInit(10.U(8.W))
  val i_vgaAtten_gainInc = RegInit(1.U(8.W))
  val i_vgaAtten_gainDec = RegInit(4.U(8.W))

  // I BPF tuning
  val i_bpf_chp_0 = RegInit(0.U(4.W))
  val i_bpf_chp_1 = RegInit(0.U(4.W))
  val i_bpf_chp_2 = RegInit(0.U(4.W))
  val i_bpf_chp_3 = RegInit(0.U(4.W))
  val i_bpf_chp_4 = RegInit(0.U(4.W))
  val i_bpf_chp_5 = RegInit(0.U(4.W))
  val i_bpf_clp_0 = RegInit(0.U(4.W))
  val i_bpf_clp_1 = RegInit(0.U(4.W))
  val i_bpf_clp_2 = RegInit(0.U(4.W))

  val q_vga_gain_ctrl = RegInit(0.U(10.W))
  val q_vgaAtten_reset = RegInit(false.B)
  val q_vgaAtten_useAGC = RegInit(false.B)

  // TODO verify initial values
  val q_vgaAtten_sampleWindow = RegInit(22.U(8.W))
  val q_vgaAtten_idealPeakToPeak = RegInit((math.pow(2, params.adcBits - 1).toInt).U(params.adcBits.W))
  val q_vgaAtten_toleranceP2P = RegInit(10.U(8.W))
  val q_vgaAtten_gainInc = RegInit(1.U(8.W))
  val q_vgaAtten_gainDec = RegInit(4.U(8.W))

  // Q BPF tuning
  val q_bpf_chp_0 = RegInit(0.U(4.W))
  val q_bpf_chp_1 = RegInit(0.U(4.W))
  val q_bpf_chp_2 = RegInit(0.U(4.W))
  val q_bpf_chp_3 = RegInit(0.U(4.W))
  val q_bpf_chp_4 = RegInit(0.U(4.W))
  val q_bpf_chp_5 = RegInit(0.U(4.W))
  val q_bpf_clp_0 = RegInit(0.U(4.W))
  val q_bpf_clp_1 = RegInit(0.U(4.W))
  val q_bpf_clp_2 = RegInit(0.U(4.W))

  val i_DCO_useDCO = RegInit(false.B)
  val i_DCO_reset = RegInit(false.B)
  val i_DCO_gain = RegInit(32.U(8.W))

  val q_DCO_useDCO = RegInit(false.B)
  val q_DCO_reset = RegInit(false.B)
  val q_DCO_gain = RegInit(32.U(8.W))

  // Only stage 2 VGA is tunable
  val i_current_dac_vga_s2 = RegInit(0.U(6.W))
  val q_current_dac_vga_s2 = RegInit(0.U(6.W))

  // Current DAC for VCO
  val current_dac_vco = RegInit(0.U(6.W))

  val mux_dbg_in = RegInit(0.U(10.W))
  val mux_dbg_out = RegInit(0.U(10.W))

  // RF block enable signals default to low
  // Receiver circuits + LNA switched on only in RX mode
  val enable_i_mix = RegInit(false.B)
  val enable_i_buf = RegInit(false.B)
  val enable_i_vga = RegInit(false.B)
  val enable_i_bpf = RegInit(false.B)

  val enable_q_mix = RegInit(false.B)
  val enable_q_buf = RegInit(false.B)
  val enable_q_vga = RegInit(false.B)
  val enable_q_bpf = RegInit(false.B)

  // VCO LO stays on all the time
  val enable_rx1 = RegInit(false.B)
  val enable_rx2 = RegInit(false.B)
  val enable_vco_lo = RegInit(true.B)   // VCO LO stays on all the time
  val enable_ext_lo = RegInit(false.B)
  val enable_pa = RegInit(false.B)

  val rxErrorMessage = Wire(new DecoupledIO(UInt(32.W)))
  val rxFinishMessage = Wire(new DecoupledIO(UInt(32.W)))
  val txErrorMessage = Wire(new DecoupledIO(UInt(32.W)))

  val ifCounter_ifTickThreshold = RegInit(0.U(32.W))
  val ifCounter_control_restartCounter = RegInit(false.B)

  io.back.ifCounter.input.ifTickThreshold := ifCounter_ifTickThreshold
  io.back.ifCounter.input.control.restartCounter := ifCounter_control_restartCounter

  rxErrorMessage <> io.back.messages.rxErrorMessage
  rxFinishMessage <> io.back.messages.rxFinishMessage
  txErrorMessage <> io.back.messages.txErrorMessage
  val adcClockTuning_restart = RegInit(true.B)
  val adcClockTuning_mode = RegInit(0.U(1.W))
  val adcClockTuning_duration = RegInit(0.U(8.W))
  val adcClockTuning_k_p = RegInit(0.U(32.W))
  val adcClockTuning_k_i = RegInit(0.U(32.W))
  val adcClockTuning_k_p_fixed = Wire(FixedPoint(32.W, 30.BP))
  val adcClockTuning_k_i_fixed = Wire(FixedPoint(32.W, 30.BP))
  adcClockTuning_k_p_fixed := adcClockTuning_k_p.asFixedPoint(30.BP)
  adcClockTuning_k_i_fixed := adcClockTuning_k_i.asFixedPoint(30.BP)
  val adcClockTuning_p_control_only = RegInit(true.B)
  val adcClockTuning_nominal_adc_clock_freq = RegInit(0.U(32.W))

  val pwr_tuning = RegInit(32.U(10.W))
  
  val i_mux_ctrl = RegInit(1.U(2.W))
  val q_mux_ctrl = RegInit(1.U(2.W))

  io.back.adcClockTuning.input.restart := adcClockTuning_restart
  io.back.adcClockTuning.input.mode := adcClockTuning_mode
  io.back.adcClockTuning.input.duration := adcClockTuning_duration
  io.back.adcClockTuning.input.k_p := adcClockTuning_k_p_fixed
  io.back.adcClockTuning.input.k_i := adcClockTuning_k_i_fixed
  io.back.adcClockTuning.input.p_control_only := adcClockTuning_p_control_only
  io.back.adcClockTuning.input.nominal_adc_clock_freq := adcClockTuning_nominal_adc_clock_freq

  io.back.messages.rxErrorMessage <> rxErrorMessage
  io.back.messages.rxFinishMessage <> rxFinishMessage
  io.back.messages.txErrorMessage <> txErrorMessage

  io.tuning.trim.g0 := trim_g0
  trim_g1 := io.tuning.trim.g1
  io.tuning.trim.g2 := trim_g2
  // io.tuning.trim.g3 := trim_g3
  // io.tuning.trim.g4 := trim_g4
  // io.tuning.trim.g5 := trim_g5
  // io.tuning.trim.g6 := trim_g6
  // io.tuning.trim.g7 := trim_g7

  io.tuning.i.vga_gain_ctrl := i_vga_gain_ctrl
  io.tuningControl.i.AGC.useAGC := i_vgaAtten_useAGC
  io.tuningControl.i.AGC.control.reset := i_vgaAtten_reset
  io.tuningControl.i.AGC.control.sampleWindow := i_vgaAtten_sampleWindow
  io.tuningControl.i.AGC.control.idealPeakToPeak := i_vgaAtten_idealPeakToPeak
  io.tuningControl.i.AGC.control.toleranceP2P := i_vgaAtten_toleranceP2P
  io.tuningControl.i.AGC.control.gainInc := i_vgaAtten_gainInc
  io.tuningControl.i.AGC.control.gainDec := i_vgaAtten_gainDec

  io.tuning.i.bpf.chp_0:=  i_bpf_chp_0
  io.tuning.i.bpf.chp_1:=  i_bpf_chp_1
  io.tuning.i.bpf.chp_2:=  i_bpf_chp_2
  io.tuning.i.bpf.chp_3:=  i_bpf_chp_3
  io.tuning.i.bpf.chp_4:=  i_bpf_chp_4
  io.tuning.i.bpf.chp_5:=  i_bpf_chp_5
  io.tuning.i.bpf.clp_0:=  i_bpf_clp_0
  io.tuning.i.bpf.clp_1:=  i_bpf_clp_1
  io.tuning.i.bpf.clp_2:=  i_bpf_clp_2

  io.tuning.q.vga_gain_ctrl := q_vga_gain_ctrl
  io.tuningControl.q.AGC.useAGC := q_vgaAtten_useAGC
  io.tuningControl.q.AGC.control.reset := q_vgaAtten_reset
  io.tuningControl.q.AGC.control.sampleWindow := q_vgaAtten_sampleWindow
  io.tuningControl.q.AGC.control.idealPeakToPeak := q_vgaAtten_idealPeakToPeak
  io.tuningControl.q.AGC.control.toleranceP2P := q_vgaAtten_toleranceP2P
  io.tuningControl.q.AGC.control.gainInc := q_vgaAtten_gainInc
  io.tuningControl.q.AGC.control.gainDec := q_vgaAtten_gainDec

  io.tuning.q.bpf.chp_0:=  q_bpf_chp_0
  io.tuning.q.bpf.chp_1:=  q_bpf_chp_1
  io.tuning.q.bpf.chp_2:=  q_bpf_chp_2
  io.tuning.q.bpf.chp_3:=  q_bpf_chp_3
  io.tuning.q.bpf.chp_4:=  q_bpf_chp_4
  io.tuning.q.bpf.chp_5:=  q_bpf_chp_5
  io.tuning.q.bpf.clp_0:=  q_bpf_clp_0
  io.tuning.q.bpf.clp_1:=  q_bpf_clp_1
  io.tuning.q.bpf.clp_2:=  q_bpf_clp_2

  io.tuningControl.i.DCO.useDCO := i_DCO_useDCO
  io.tuningControl.i.DCO.control.reset := i_DCO_reset
  io.tuningControl.i.DCO.control.gain := Cat(0.U(1.W), i_DCO_gain).asFixedPoint(2.BP)

  io.tuningControl.q.DCO.useDCO := q_DCO_useDCO
  io.tuningControl.q.DCO.control.reset := q_DCO_reset
  io.tuningControl.q.DCO.control.gain := Cat(0.U(1.W), q_DCO_gain).asFixedPoint(2.BP)

  // io.tuningControl.debug.enabled := trim_g7(0)
  io.tuningControl.debug.enabled := debug_enable

  io.tuning.current_dac.i.vga_s2 := i_current_dac_vga_s2
  io.tuning.current_dac.q.vga_s2 := q_current_dac_vga_s2

  io.tuning.current_dac.vco := current_dac_vco

  io.tuning.adc_clk_tuning := Cat(io.back.adcClockTuning.output.control_word, 0.U(1.W))

  io.tuning.mux.dbg.in := mux_dbg_in
  io.tuning.mux.dbg.out := mux_dbg_out

  // Enable signals (again, these default to low unless otherwise driven high over MMIO)
  io.tuning.enable.i.mix :=     enable_i_mix
  io.tuning.enable.i.buf :=     enable_i_buf
  io.tuning.enable.i.vga :=     enable_i_vga
  io.tuning.enable.i.bpf :=     enable_i_bpf

  io.tuning.enable.q.mix :=     enable_q_mix
  io.tuning.enable.q.buf :=     enable_q_buf
  io.tuning.enable.q.vga :=     enable_q_vga
  io.tuning.enable.q.bpf :=     enable_q_bpf

  io.tuning.enable.rx1 :=    enable_rx1
  io.tuning.enable.rx2 :=    enable_rx2
  io.tuning.enable.vco_lo :=    enable_vco_lo
  io.tuning.enable.ext_lo :=    enable_ext_lo
  io.tuning.enable.pa :=    enable_pa

  io.tuning.pwr_tuning := pwr_tuning

  io.tuning.adc.i.mux_ctrl := i_mux_ctrl
  io.tuning.adc.q.mux_ctrl := q_mux_ctrl
  

  // Interrupts
  outer.interrupts(0) := io.back.interrupt.rxError
  outer.interrupts(1) := io.back.interrupt.rxStart
  outer.interrupts(2) := io.back.interrupt.rxFinish
  outer.interrupts(3) := io.back.interrupt.txError
  outer.interrupts(4) := io.back.interrupt.txFinish
  outer.interrupts(5) := io.back.ifCounter.output.thresholdInterrupt

  val lo_counter_decoupled = Wire(new DecoupledIO(UInt(32.W)))
  val lo_start_debug_decoupled = Wire(new DecoupledIO(UInt(32.W)))
  val lo_stop_debug_decoupled = Wire(new DecoupledIO(UInt(32.W)))

  lo_counter_decoupled.bits := io.back.status.status4(31, 0)
  lo_start_debug_decoupled.bits := io.back.adcClockTuning.output.lo_start_debug(31, 0)
  lo_stop_debug_decoupled.bits := io.back.adcClockTuning.output.lo_stop_debug(31, 0)
  lo_counter_decoupled.valid := true.B
  lo_start_debug_decoupled.valid := true.B
  lo_stop_debug_decoupled.valid := true.B

  val lo_counter_buffer = RegInit(0.U(64.W))
  val lo_start_debug_buffer = RegInit(0.U(64.W))
  val lo_stop_debug_buffer = RegInit(0.U(64.W))

  if (lo_counter_decoupled.ready == true.B) {
    lo_counter_buffer := io.back.status.status4
  }

  if (lo_start_debug_decoupled.ready == true.B) {
    lo_start_debug_buffer := io.back.adcClockTuning.output.lo_start_debug
  }

  if (lo_stop_debug_decoupled.ready == true.B) {
    lo_stop_debug_buffer := io.back.adcClockTuning.output.lo_stop_debug
  }

  outer.tlRegmap(
    0x00 -> Seq(RegField.w(32, inst)), // Command start
    0x04 -> Seq(RegField.w(32, additionalData)),
    0x08 -> Seq(RegField.r(32, io.back.status.status0)), // Status start
    0x0C -> Seq(RegField.r(32, io.back.status.status1)),
    0x10 -> Seq(RegField.r(32, io.back.status.status2)),
    0x14 -> Seq(RegField.r(32, io.back.status.status3)),
    0x18 -> Seq(RegField.r(32, lo_counter_decoupled)),
    0x1C -> Seq(RegField.r(32, lo_counter_buffer(63, 32))),
    0x20 -> Seq(RegField(8, trim_g0)), // Tuning start, Trim
    0x21 -> Seq(RegField(8, trim_g1)),
    0x22 -> Seq(RegField(8, trim_g2)),
    // 0x1F -> Seq(RegField(8, trim_g3)),
    // 0x20 -> Seq(RegField(8, trim_g4)),
    // 0x21 -> Seq(RegField(8, trim_g5)),
    // 0x22 -> Seq(RegField(8, trim_g6)),
    0x23 -> Seq(RegField(1, debug_enable)),
    0x24 -> Seq(RegField(10, i_vga_gain_ctrl)), // I VGA
    0x26 -> Seq(RegField(1, i_vgaAtten_reset)),
    0x27 -> Seq(RegField(1, i_vgaAtten_useAGC)),
    0x28 -> Seq(RegField(8, i_vgaAtten_sampleWindow)),
    0x29 -> Seq(RegField(8, i_vgaAtten_idealPeakToPeak)),
    0x2A -> Seq(RegField(8, i_vgaAtten_toleranceP2P)),
    0x2B -> Seq( // I Filter
      RegField(4, i_bpf_chp_0),
      RegField(4, i_bpf_chp_1)
    ),
    0x2C -> Seq(
      RegField(4, i_bpf_chp_2),
      RegField(4, i_bpf_chp_3)
    ),
    0x2D -> Seq(
      RegField(4, i_bpf_chp_4),
      RegField(4, i_bpf_chp_5)
    ),
    0x2E -> Seq(
      RegField(4, i_bpf_clp_0),
      RegField(4, i_bpf_clp_1)
    ),
    0x2F -> Seq(
      RegField(4, i_bpf_clp_2)
    ),
    0x30 -> Seq(RegField(10, q_vga_gain_ctrl)), // Q VGA
    0x32 -> Seq(RegField(1, q_vgaAtten_reset)),
    0x33 -> Seq(RegField(1, q_vgaAtten_useAGC)),
    0x34 -> Seq(RegField(8, q_vgaAtten_sampleWindow)),
    0x35 -> Seq(RegField(8, q_vgaAtten_idealPeakToPeak)),
    0x36 -> Seq(RegField(8, q_vgaAtten_toleranceP2P)),
    0x37 -> Seq( // Q Filter
      RegField(4, q_bpf_chp_0),
      RegField(4, q_bpf_chp_1)
    ),
    0x38 -> Seq(
      RegField(4, q_bpf_chp_2),
      RegField(4, q_bpf_chp_3)
    ),
    0x39 -> Seq(
      RegField(4, q_bpf_chp_4),
      RegField(4, q_bpf_chp_5)
    ),
    0x3A -> Seq(
      RegField(4, q_bpf_clp_0),
      RegField(4, q_bpf_clp_1)
    ),
    0x3B -> Seq(
      RegField(4, q_bpf_clp_2)
    ),
    0x3C -> Seq(RegField(1, i_DCO_useDCO)),
    0x3D -> Seq(RegField(1, i_DCO_reset)),
    0x3E -> Seq(RegField(8, i_DCO_gain)),
    0x3F -> Seq(RegField(1, q_DCO_useDCO)),
    0x40 -> Seq(RegField(1, q_DCO_reset)),
    0x41 -> Seq(RegField(8, q_DCO_gain)),
    0x42 -> Seq(RegField(6, i_current_dac_vga_s2)),
    0x43 -> Seq(RegField(6, q_current_dac_vga_s2)),
    0x44 -> Seq(RegField(6, current_dac_vco)),
    0x46 -> Seq(RegField(10, mux_dbg_in)), // Debug Configuration
    0x48 -> Seq(RegField(10, mux_dbg_out)),
    0x4A -> Seq( // Manual enable values
      RegField(1, enable_i_mix),
      RegField(1, enable_i_buf),
      RegField(1, enable_i_vga),
      RegField(1, enable_i_bpf),
    ),
    0x4B -> Seq( // Manual enable values
      RegField(1, enable_q_mix),
      RegField(1, enable_q_buf),
      RegField(1, enable_q_vga),
      RegField(1, enable_q_bpf),
    ),
    0x4C -> Seq( // Manual enable values
      RegField(1, enable_rx1),
      RegField(1, enable_rx2),
      RegField(1, enable_vco_lo),
      RegField(1, enable_ext_lo),
      RegField(1, enable_pa)
    ),
    0x50 -> Seq(RegField.w(32, lutCmd)),          // LUT Programming
    0x54 -> Seq(RegField.r(32, rxErrorMessage)),  // Interrupt Messages
    0x58 -> Seq(RegField.r(32, rxFinishMessage)),
    0x5C -> Seq(RegField.r(32, txErrorMessage)),
    0x60 -> Seq(RegField.w(32, firCmd)),

    // TODO move these near the appropriate places
    0x64 -> Seq(RegField(8, i_vgaAtten_gainInc)),
    0x65 -> Seq(RegField(8, i_vgaAtten_gainDec)),
    0x66 -> Seq(RegField(8, q_vgaAtten_gainInc)),
    0x67 -> Seq(RegField(8, q_vgaAtten_gainDec)),

    // IF Counter
    0x68 -> Seq(RegField(32, ifCounter_ifTickThreshold)),
    0x6C -> Seq(RegField.r(32, io.back.ifCounter.output.adcTicks)),
    0x70 -> Seq(RegField.r(32, io.back.ifCounter.output.ifTicksPacket)),
    0x74 -> Seq(RegField.r(32, io.back.ifCounter.output.adcTicksPacket)),
    0x78 -> Seq(RegField.r(32, io.back.ifCounter.output.ifTicksPrevPacket)),
    0x7C -> Seq(RegField.r(32, io.back.ifCounter.output.adcTicksPrevPacket)),
    0x80 -> Seq(RegField(1, ifCounter_control_restartCounter)),

    // ADC Clock Tuning
    0x81 -> Seq(RegField(1, adcClockTuning_restart)),
    0x82 -> Seq(RegField(1, adcClockTuning_mode)),
    0x83 -> Seq(RegField(1, adcClockTuning_p_control_only)),
    0x84 -> Seq(RegField(8, adcClockTuning_duration)),
    0x88 -> Seq(RegField(32, adcClockTuning_k_p)),
    0x8C -> Seq(RegField(32, adcClockTuning_k_i)),
    0x90 -> Seq(RegField(32, adcClockTuning_nominal_adc_clock_freq)),
    0x94 -> Seq(RegField.r(8, io.back.adcClockTuning.output.control_word)),
    0x95 -> Seq(RegField.r(2, io.back.adcClockTuning.output.sop_eop_debug)),
    0x98 -> Seq(RegField.r(32, io.back.adcClockTuning.output.curr_calc_adc_clock_freq_debug)),
    0x9C -> Seq(RegField.r(32, lo_start_debug_decoupled)),
    0xA0 -> Seq(RegField.r(32, lo_start_debug_buffer(63, 32))),
    0xA4 -> Seq(RegField.r(32, lo_stop_debug_decoupled)),
    0xA8 -> Seq(RegField.r(32, lo_stop_debug_buffer(63, 32))),
    0xAC -> Seq(RegField.r(32, io.back.adcClockTuning.output.adc_ticks_counter)),
    0xB0 -> Seq(RegField.r(32, io.back.adcClockTuning.output.f_err.asUInt)),
    0xB4 -> Seq(RegField.r(32, io.back.adcClockTuning.output.accumulated_err.asUInt)),
    0xB8 -> Seq(RegField.r(1, io.back.adcClockTuning.output.controller_active)),
    0xB9 -> Seq(RegField.r(1, io.back.adcClockTuning.output.trigger_controller)),
    0xBA -> Seq(RegField.r(10, io.back.adcClockTuning.output.p_term_trunc.asUInt)),
    0xBC -> Seq(RegField.r(10, io.back.adcClockTuning.output.i_term_trunc.asUInt)),
    0xBE -> Seq(RegField.r(1, io.back.adcClockTuning.output.controller_state)),
    0xC0 -> Seq(RegField(10, pwr_tuning)),
    0xC2 -> Seq(RegField(2, i_mux_ctrl)),
    0xC3 -> Seq(RegField(2, q_mux_ctrl)),
  )
}

// class BasebandModemFrontend(params: BasebandModemParams, beatBytes: Int)(implicit p: Parameters)
//   extends TLRegisterRouter(
//     params.address, "baseband", Seq("ucbbar, riscv"),
//     beatBytes = beatBytes, interrupts = 6)( // TODO: Interrupts and compatible list
//       new TLRegBundle(params, _) with BasebandModemFrontendBundle)(
//       new TLRegModule(params, _, _) with BasebandModemFrontendModule)

class BasebandModem(params: BasebandModemParams, beatBytes: Int)(implicit p: Parameters) extends ClockSinkDomain(ClockSinkParameters())(p) {
  val dma = LazyModule(new EE290CDMA(beatBytes, params.maxReadSize, "baseband"))

  val mmio = TLIdentityNode()
  val mem = dma.id_node

  val basebandFrontend = LazyModule(new BasebandModemFrontend(params, beatBytes))
  val intnode = basebandFrontend.intXing(NoCrossing)

  basebandFrontend.node := mmio

  override lazy val module = new ModemImpl
  class ModemImpl extends Impl with HasBasebandModemAnalogIO {
    val io = dontTouch(IO(new BasebandModemAnalogIO(params)))
    withClockAndReset(clock, reset) {

      // LO/8 Counter
      // val lo_counter = withNewClock(io.tuning.trim.g1(0).asClock) {
      val lo_counter = withNewClock(io.intra.lo_div8_clock.asClock) {
        val ctr = Reg(UInt(64.W))
        ctr := ctr + 1.U
        ctr
      }

      // Incoming Command Queue
      val cmdQueue = Queue(basebandFrontend.module.io.back.cmd, params.cmdQueueDepth)

      // Baseband Modem and Controller
      val bmc = Module(new BMC(params, beatBytes))
      bmc.clock := clock
      bmc.io.analog.data.rx <> io.intra.data.rx
      bmc.io.analog.data.tuning <> io.intra.data.tuning
      bmc.io.lo_div8_clock := io.intra.lo_div8_clock
      bmc.io.cmd <> cmdQueue
      bmc.io.dma.readReq <> dma.module.io.read.req
      bmc.io.dma.readResp <> dma.module.io.read.resp
      bmc.io.dma.readData <> dma.module.io.read.queue
      bmc.io.dma.writeReq <> dma.module.io.write.req
      bmc.io.lutCmd <> basebandFrontend.module.io.back.lutCmd
      bmc.io.firCmd <> basebandFrontend.module.io.back.firCmd
      bmc.io.ifCounter <> basebandFrontend.module.io.back.ifCounter
      bmc.io.adcClockTuning <> basebandFrontend.module.io.back.adcClockTuning
      bmc.io.lo_counter := lo_counter
      bmc.io.tuning := basebandFrontend.module.io.tuningControl

      // Interrupt Message Store
      val messageStore = Module(new MessageStore(params))
      messageStore.io.in <> bmc.io.messages
      messageStore.clock := clock
      basebandFrontend.module.io.back.messages <> messageStore.io.out

      // Interrupts
      basebandFrontend.module.io.back.interrupt.rxError  := bmc.io.interrupt.rxError
      basebandFrontend.module.io.back.interrupt.rxStart  := bmc.io.interrupt.rxStart
      basebandFrontend.module.io.back.interrupt.rxFinish := bmc.io.interrupt.rxFinish
      basebandFrontend.module.io.back.interrupt.txError  := bmc.io.interrupt.txError
      basebandFrontend.module.io.back.interrupt.txFinish := bmc.io.interrupt.txFinish

      // Status
      basebandFrontend.module.io.back.status.status0 := Cat(io.intra.data.rx.q.data,
                                                    io.intra.data.rx.i.data,
                                                    bmc.io.state.mainControllerState,
                                                    bmc.io.state.txControllerState,
                                                    bmc.io.state.rxControllerState,
                                                    bmc.io.state.txState,
                                                    bmc.io.state.disassemblerState,
                                                    bmc.io.state.assemblerState)
      basebandFrontend.module.io.back.status.status1 := Cat(io.intra.data.rx.q.valid,
                                                    io.intra.data.rx.i.valid,
                                                    bmc.io.state.q.dcoIndex,
                                                    bmc.io.state.q.agcIndex,
                                                    bmc.io.state.i.dcoIndex,
                                                    bmc.io.state.i.agcIndex,
                                                    bmc.io.state.modIndex)
      basebandFrontend.module.io.back.status.status2 := bmc.io.state.bleBitCount
      basebandFrontend.module.io.back.status.status3 := bmc.io.state.lrwpanBitCount
      basebandFrontend.module.io.back.status.status4 := lo_counter

      // Other off chip / analog IO
      io.intra.tuning.trim <> basebandFrontend.module.io.tuning.trim
      io.intra.tuning.i.bpf := basebandFrontend.module.io.tuning.i.bpf
      io.intra.tuning.q.bpf := basebandFrontend.module.io.tuning.q.bpf

      // Current DAC for the VGAs, optionally controlled by the DCOC circuit

      io.intra.tuning.current_dac.i.vga_s2 := Mux(basebandFrontend.module.io.tuningControl.i.DCO.useDCO,
        bmc.io.analog.data.tuning.i.vga_s2,
        basebandFrontend.module.io.tuning.current_dac.i.vga_s2
      )
      io.intra.tuning.current_dac.q.vga_s2 := Mux(basebandFrontend.module.io.tuningControl.q.DCO.useDCO,
        bmc.io.analog.data.tuning.q.vga_s2,
        basebandFrontend.module.io.tuning.current_dac.q.vga_s2
      )

      // VGA gain control, optionally controlled by the AGC circuit
      io.intra.tuning.i.vga_gain_ctrl := Mux(basebandFrontend.module.io.tuningControl.i.AGC.useAGC,
        bmc.io.analog.data.tuning.i.vgaAtten,
        basebandFrontend.module.io.tuning.i.vga_gain_ctrl
      )
      io.intra.tuning.q.vga_gain_ctrl := Mux(basebandFrontend.module.io.tuningControl.q.AGC.useAGC,
        bmc.io.analog.data.tuning.q.vgaAtten,
        basebandFrontend.module.io.tuning.q.vga_gain_ctrl
      )

      // Enable bits to the analog RF circuits
      io.intra.tuning.enable.i := Mux(basebandFrontend.module.io.tuningControl.debug.enabled,
        basebandFrontend.module.io.tuning.enable.i,
        bmc.io.analog.enable.rx
      )

      io.intra.tuning.enable.q := Mux(basebandFrontend.module.io.tuningControl.debug.enabled,
        basebandFrontend.module.io.tuning.enable.q,
        bmc.io.analog.enable.rx
      )

    io.intra.tuning.enable.vco_lo := basebandFrontend.module.io.tuning.enable.vco_lo
      io.intra.tuning.enable.rx1 := basebandFrontend.module.io.tuning.enable.rx1
      io.intra.tuning.enable.rx2 := basebandFrontend.module.io.tuning.enable.rx2
      io.intra.tuning.enable.vco_lo := basebandFrontend.module.io.tuning.enable.vco_lo
      io.intra.tuning.enable.ext_lo := basebandFrontend.module.io.tuning.enable.ext_lo
      io.intra.tuning.enable.pa := basebandFrontend.module.io.tuning.enable.pa

    io.intra.tuning.mux := basebandFrontend.module.io.tuning.mux
      io.intra.tuning.mux := basebandFrontend.module.io.tuning.mux
      io.intra.tuning.current_dac.vco := basebandFrontend.module.io.tuning.current_dac.vco
      io.intra.tuning.adc_clk_tuning := basebandFrontend.module.io.tuning.adc_clk_tuning
      io.intra.tuning.pwr_tuning := basebandFrontend.module.io.tuning.pwr_tuning
      io.intra.tuning.adc.i.mux_ctrl := basebandFrontend.module.io.tuning.adc.i.mux_ctrl
      io.intra.tuning.adc.q.mux_ctrl := basebandFrontend.module.io.tuning.adc.q.mux_ctrl

      io.intra.data.tx.vco.cap_mod := bmc.io.analog.data.tx.vco.cap_mod
      io.intra.data.tx.vco.cap_medium := bmc.io.analog.data.tx.vco.cap_medium
      io.intra.data.tx.vco.cap_coarse := bmc.io.analog.data.tx.vco.cap_coarse
      io.intra.data.tx.vco.freq_reset := bmc.io.analog.data.tx.vco.freq_reset

      io.bump.offChipMode.rx := bmc.io.analog.offChipMode.rx
      io.bump.offChipMode.tx := bmc.io.analog.offChipMode.tx
    }
  }
}

