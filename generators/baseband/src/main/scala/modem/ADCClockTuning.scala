package modem

import chisel3._
import chisel3.util._
import baseband.BasebandModemParams
import fixedpoint._
import fixedpoint.{fromIntToBinaryPoint, fromDoubleToLiteral}

class ModemADCClockTuningStateIO extends Bundle {
  val input = Input(new ModemADCClockTuningInputIO)
  val output = Output(new ModemADCClockTuningOutputIO)
  // val debug = Output(new ModemADCClockTuningDebugIO)
}

class ModemADCClockTuningInputIO extends Bundle {
  val restart = Bool() // MMIO Input
  val mode = UInt(1.W) // MMIO Input
  val duration = UInt(8.W) // MMIO Input
  val k_p = FixedPoint(32.W, 30.BP) // MMIO Input
  val k_i = FixedPoint(32.W, 30.BP) // MMIO Input
  val p_control_only = Bool() // MMIO Input
  val nominal_adc_clock_freq = UInt(32.W) // MMIO Input
}

class ModemADCClockTuningOutputIO extends Bundle {
  val control_word = UInt(8.W) // Digital Output + MMIO Debug Output
  val sop_eop_debug = UInt(2.W) // MMIO Debug Output
  val curr_calc_adc_clock_freq_debug = UInt(32.W) // MMIO Debug Output
  val lo_start_debug = UInt(64.W) // MMIO Debug Output
  val lo_stop_debug = UInt(64.W) // MMIO Debug Output
  val adc_ticks_counter = UInt(32.W) // MMIO Debug Output
  val f_err = SInt(32.W) // MMIO Debug Output
  val accumulated_err = SInt(32.W) // MMIO Debug Output
  val controller_active = Bool() // MMIO Debug Output
  val trigger_controller = Bool() // MMIO Debug Output
  val p_term_trunc = SInt(10.W) // MMIO Debug Output
  val i_term_trunc = SInt(10.W) // MMIO Debug Output
  val controller_state = UInt(1.W) // MMIO Debug Output
}

// Debug IO
// class ModemADCClockTuningDebugIO extends Bundle {
//   val adc_ticks_threshold = UInt(32.W)
//   val adc_ticks_per_us = UInt(32.W)
//   val f_lo_8 = UInt(32.W)
//   val f_err_fixed = FixedPoint(62.W, 30.BP)
//   val f_err_prev = SInt(32.W)
//   val accumulated_err_fixed = FixedPoint(62.W, 30.BP)
//   val prev_mode = UInt(1.W)
//   val p_term = FixedPoint(62.W, 30.BP)
//   val i_term = FixedPoint(62.W, 30.BP)
//   val control_word_prev = SInt(10.W)
//   val control_word_pos = SInt(10.W)
// }

class ADCClockTuning(params: BasebandModemParams) extends Module {
  val io = IO(new Bundle {
    val sop = Input(Bool())
    val eop = Input(Bool())
    val lo_counter = Input(UInt(64.W))
    val state = new ModemADCClockTuningStateIO
  })

  val adc_ticks_counter = RegInit(0.U(32.W))
  val adc_ticks_threshold = RegInit(0.U(32.W))
  val adc_ticks_per_us = 32.U
  val curr_calc_adc_clock_freq = Wire(UInt(32.W))
  val control_word = RegInit(32.S(10.W))
  val control_word_prev = RegInit(32.S(10.W))
  val control_word_pos = Wire(SInt(10.W))
  val lo_start = RegInit(0.U(64.W))
  val lo_stop = RegInit(0.U(64.W))
  val f_lo_8 = RegInit(300000000.U(32.W))
  val f_err = Wire(SInt(32.W))
  val f_err_fixed = Wire(FixedPoint(62.W, 30.BP))
  val f_err_prev = RegInit(0.S(32.W))
  val accumulated_err = RegInit(0.S(32.W))
  val accumulated_err_fixed = Wire(FixedPoint(62.W, 30.BP))
  val controller_active = RegInit(false.B)
  val trigger_controller = RegInit(false.B)
  val prev_mode = RegNext(io.state.input.mode)
  val p_term = Wire(FixedPoint(62.W, 30.BP))
  val p_term_trunc = Wire(SInt(10.W))
  val i_term = Wire(FixedPoint(62.W, 30.BP))
  val i_term_trunc = Wire(SInt(10.W))
  val controller_state = RegInit(0.U(1.W))
  adc_ticks_threshold := io.state.input.duration * adc_ticks_per_us

  curr_calc_adc_clock_freq := f_lo_8 * adc_ticks_threshold / (lo_stop - lo_start) // Divider
  f_err := io.state.input.nominal_adc_clock_freq.asSInt - curr_calc_adc_clock_freq.asSInt
  f_err_fixed := f_err.asFixedPoint(0.BP)
  accumulated_err_fixed := accumulated_err.asFixedPoint(0.BP)

  io.state.output.control_word := control_word(7, 0).asUInt
  io.state.output.sop_eop_debug := Cat(io.sop, io.eop)
  io.state.output.curr_calc_adc_clock_freq_debug := curr_calc_adc_clock_freq
  io.state.output.lo_start_debug := lo_start
  io.state.output.lo_stop_debug := lo_stop
  io.state.output.adc_ticks_counter := adc_ticks_counter
  io.state.output.f_err := f_err
  io.state.output.accumulated_err := accumulated_err
  io.state.output.controller_active := controller_active
  io.state.output.trigger_controller := trigger_controller
  io.state.output.p_term_trunc := p_term_trunc
  io.state.output.i_term_trunc := i_term_trunc
  io.state.output.controller_state := controller_state

  // Debug IO
  // io.state.debug.adc_ticks_threshold := adc_ticks_threshold
  // io.state.debug.adc_ticks_per_us := adc_ticks_per_us
  // io.state.debug.f_lo_8 := f_lo_8
  // io.state.debug.f_err_fixed := f_err_fixed
  // io.state.debug.f_err_prev := f_err_prev
  // io.state.debug.accumulated_err_fixed := accumulated_err_fixed
  // io.state.debug.prev_mode := prev_mode
  // io.state.debug.p_term := p_term
  // io.state.debug.i_term := i_term
  // io.state.debug.control_word_prev := control_word_prev
  // io.state.debug.control_word_pos := control_word_pos

  when (io.state.input.restart || io.state.input.mode =/= prev_mode) {
    adc_ticks_counter := 0.U
    lo_start := 0.U
    lo_stop := 0.U
    accumulated_err := 0.S
    controller_active := false.B
  }.otherwise {
    when (io.state.input.mode === 1.U) {
      when (io.sop) {
        controller_active := true.B
      }.elsewhen (io.eop) {
        adc_ticks_counter := 0.U
        lo_start := 0.U
        lo_stop := 0.U
        accumulated_err := 0.S
        controller_active := false.B
      }
    }.otherwise {
      controller_active := true.B
    }
  }

  when (controller_active && ~io.state.input.restart && io.state.input.mode === prev_mode && ~io.eop) {
    when (adc_ticks_counter === 0.U && ~trigger_controller) {
      lo_start := io.lo_counter
      adc_ticks_counter := adc_ticks_counter + 1.U
    }.elsewhen (adc_ticks_counter >= adc_ticks_threshold) {
      lo_stop := io.lo_counter
      adc_ticks_counter := 0.U
      f_err_prev := f_err
      control_word_prev := control_word
      trigger_controller := true.B
    }.elsewhen (trigger_controller) {
      adc_ticks_counter := 0.U
    }.otherwise {
      adc_ticks_counter := adc_ticks_counter + 1.U
    }
  }

  // PI Controller
  p_term := io.state.input.k_p * f_err_fixed
  when (p_term < 0.F(62.W, 30.BP)) {
    p_term_trunc := p_term(39, 30).asSInt + 1.S
  }.otherwise {
    p_term_trunc := p_term(39, 30).asSInt
  }

  i_term := io.state.input.k_i * accumulated_err_fixed
  when (i_term < 0.F(62.W, 30.BP)) {
    i_term_trunc := i_term(39, 30).asSInt + 1.S
  }.otherwise {
    i_term_trunc := i_term(39, 30).asSInt
  }

  when (io.state.input.p_control_only) {
    control_word_pos := control_word_prev + p_term_trunc
  }.otherwise {
    control_word_pos := p_term_trunc + i_term_trunc
  }

  when (trigger_controller) {
    when (controller_state === 0.U) {
      accumulated_err := accumulated_err + f_err
      controller_state := 1.U
    }.otherwise {
      when (control_word_pos < 0.S) {
        control_word := 0.S
      }.elsewhen (control_word_pos > 255.S) {
        control_word := 255.S
      }.otherwise {
        control_word := control_word_pos
      }
      trigger_controller := false.B
      controller_state := 0.U
    }
  }
}
