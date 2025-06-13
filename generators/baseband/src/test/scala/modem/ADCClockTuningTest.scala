package modem

import chisel3._
import chisel3.util._
import chiseltest._
import circt.stage.ChiselStage

import baseband.{BasebandModemParams}

import org.scalatest.flatspec.AnyFlatSpec
import scala.collection.immutable.Seq
import scala.util.Random
import breeze.plot.{Figure, plot}
import breeze.linalg.linspace
import TestUtility._

class ADCClockTuningTest extends AnyFlatSpec with ChiselScalatestTester {
  it should "Test ADC Clock Tuning" in {
    test(new ADCClockTuning(new BasebandModemParams)) { a =>
      a.clock.setTimeout(32000000)
    }
  }

      // println((new ChiselStage).emitVerilog(new ADCClockTuning(new BasebandModemParams)))

  //     println("\n\n" + "========== P CONTROLLER, MODE 0 ==========\n\n")

  //     a.io.state.input.restart.poke(true.B)
  //     a.clock.step()
  //     a.io.state.input.restart.poke(false.B)
  //     assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //     assert(a.io.state.output.lo_start_debug.peek().litValue == 0)
  //     assert(a.io.state.output.lo_stop_debug.peek().litValue == 0)
  //     assert(a.io.state.output.accumulated_err.peek().litValue == 0)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)

  //     var controller_active = true
  //     var adc_clock_freq = 32e6 - 10e4
  //     var f_lo_8 = 300e6
  //     var num_iter = 156
  //     var duration_us = 64
  //     var dt = duration_us * 1e-6
  //     var k_p = 1/1.28e3
  //     var k_i = 5 * dt
  //     var nominal_adc_clock_freq = 32000000
  //     var test_duration_us = duration_us * num_iter
  //     a.io.state.input.mode.poke(0.U)
  //     a.io.state.input.p_control_only.poke(true.B)
  //     a.io.state.input.nominal_adc_clock_freq.poke(nominal_adc_clock_freq.U)
  //     a.io.state.input.duration.poke(duration_us.U)
  //     a.io.state.input.k_p.poke(k_p.F(32.W, 30.BP))
  //     a.io.state.input.k_i.poke(k_i.F(32.W, 30.BP))

  //     a.clock.step()
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == true)

  //     var adc_clock_freq_arr_p_only_mode_0 = Array[Int]()
  //     var calc_adc_clock_freq_arr_p_only_mode_0 = Array[Int]()

  //     for (i <- 0 until num_iter) {
  //       println("\n" + "========== ITERATION: " + i + " ==========\n")
  //       println("Current ADC Clock Frequency: " + adc_clock_freq.toInt)

  //       a.io.lo_counter.poke(lo_counter(adc_clock_freq, a.io.state.debug.adc_ticks_threshold.peek().litValue.toInt, f_lo_8, true).U)
  //       a.clock.step()
  //       println("LO START: " + a.io.state.output.lo_start_debug.peek().litValue)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 1)
  //       a.clock.step(a.io.state.debug.adc_ticks_threshold.peek().litValue.toInt - 1)
  //       println("ADC_TICKS_COUNTER: " + a.io.state.output.adc_ticks_counter.peek().litValue)
  //       a.io.lo_counter.poke(lo_counter(adc_clock_freq, a.io.state.debug.adc_ticks_threshold.peek().litValue.toInt, f_lo_8, false).U)
  //       println("CURR_CALC_ADC_CLOCK_FREQ: " + a.io.state.output.curr_calc_adc_clock_freq_debug.peek().litValue)
  //       println("F_ERR_PREV: " + a.io.state.debug.f_err_prev.peek().litValue)
  //       println("F_ERR: " + a.io.state.output.f_err.peek().litValue)

  //       a.clock.step()
  //       println("\n" + "===== Calculation Took Place =====" + "\n")

  //       println("LO_STOP: " + a.io.state.output.lo_stop_debug.peek().litValue)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //       println("CURR_CALC_ADC_CLOCK_FREQ: " + a.io.state.output.curr_calc_adc_clock_freq_debug.peek().litValue)
  //       println("F_ERR_PREV: " + a.io.state.debug.f_err_prev.peek().litValue)
  //       println("F_ERR: " + a.io.state.output.f_err.peek().litValue)
  //       println("F_ERR_FIXED: " + a.io.state.debug.f_err_fixed.peek())
  //       println("P_TERM: " + a.io.state.debug.p_term.peek())
  //       println("P_TERM_TRUNC: " + a.io.state.output.p_term_trunc.peek().litValue)
  //       assert(a.io.state.output.trigger_controller.peek().litToBoolean == true)
  //       assert(a.io.state.output.controller_state.peek().litValue == 0)

  //       adc_clock_freq_arr_p_only_mode_0 = adc_clock_freq_arr_p_only_mode_0 :+ adc_clock_freq.toInt
  //       calc_adc_clock_freq_arr_p_only_mode_0 = calc_adc_clock_freq_arr_p_only_mode_0 :+ a.io.state.output.curr_calc_adc_clock_freq_debug.peek().litValue.toInt

  //       a.clock.step()
  //       assert(a.io.state.output.trigger_controller.peek().litToBoolean == true)
  //       assert(a.io.state.output.controller_state.peek().litValue == 1)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //       a.clock.step()
  //       assert(a.io.state.output.trigger_controller.peek().litToBoolean == false)
  //       assert(a.io.state.output.controller_state.peek().litValue == 0)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)

  //       println("CONTROL_WORD_PREV: " + a.io.state.debug.control_word_prev.peek().litValue)
  //       println("CONTROL_WORD: " + a.io.state.output.control_word.peek().litValue)
        
  //       adc_clock_freq = adc_clock_freq + calc_freq_offset(a.io.state.debug.control_word_prev.peek().litValue.toInt, a.io.state.output.control_word.peek().litValue.toInt, controller_active)
  //       println("ADC_CLOCK_FREQ Adjusted To: " + adc_clock_freq.toInt)
  //       adc_clock_freq = adc_clock_freq + 2.56e3 * (Random.nextDouble() - 0.5)
  //       println("ADC_CLOCK_FREQ Drifted To: " + adc_clock_freq.toInt + "\n")
  //     }

  //     var adc_clock_freq_arr_filepath_p_only_mode_0 = "./generators/baseband/src/test/scala/modem/data/adc_clock_freq_arr_p_only_mode_0.csv"
  //     var calc_adc_clock_freq_arr_filepath_p_only_mode_0 = "./generators/baseband/src/test/scala/modem/data/calc_adc_clock_freq_arr_p_only_mode_0.csv"

  //     writeToCSV(adc_clock_freq_arr_p_only_mode_0, adc_clock_freq_arr_filepath_p_only_mode_0)
  //     writeToCSV(calc_adc_clock_freq_arr_p_only_mode_0, calc_adc_clock_freq_arr_filepath_p_only_mode_0)

  //     println("\n\n" + "========== PI CONTROLLER, MODE 0 ==========\n\n")

  //     a.io.state.input.restart.poke(true.B)
  //     a.clock.step()
  //     a.io.state.input.restart.poke(false.B)
  //     assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //     assert(a.io.state.output.lo_start_debug.peek().litValue == 0)
  //     assert(a.io.state.output.lo_stop_debug.peek().litValue == 0)
  //     assert(a.io.state.output.accumulated_err.peek().litValue == 0)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)

  //     controller_active = true
  //     adc_clock_freq = 32e6 + 2.5e4
  //     f_lo_8 = 300e6
  //     num_iter = 156
  //     duration_us = 64
  //     dt = duration_us * 1e-6
  //     k_p = 1/1.28e3 * 0.5
  //     k_i = 5 * dt
  //     nominal_adc_clock_freq = 32000000
  //     test_duration_us = duration_us * num_iter
  //     a.io.state.input.p_control_only.poke(false.B)
  //     a.io.state.input.nominal_adc_clock_freq.poke(nominal_adc_clock_freq.U)
  //     a.io.state.input.duration.poke(duration_us.U)
  //     a.io.state.input.k_p.poke(k_p.F(32.W, 30.BP))
  //     a.io.state.input.k_i.poke(k_i.F(32.W, 30.BP))

  //     a.clock.step()
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == true)

  //     var adc_clock_freq_arr_p_and_i_mode_0 = Array[Int]()
  //     var calc_adc_clock_freq_arr_p_and_i_mode_0 = Array[Int]()

  //     for (i <- 0 until num_iter) {
  //       println("\n" + "========== ITERATION: " + i + " ==========\n")
  //       println("Current ADC Clock Frequency: " + adc_clock_freq.toInt)

  //       a.io.lo_counter.poke(lo_counter(adc_clock_freq, a.io.state.debug.adc_ticks_threshold.peek().litValue.toInt, f_lo_8, true).U)
  //       a.clock.step()
  //       println("LO START: " + a.io.state.output.lo_start_debug.peek().litValue)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 1)
  //       a.clock.step(a.io.state.debug.adc_ticks_threshold.peek().litValue.toInt - 1)
  //       println("ADC_TICKS_COUNTER: " + a.io.state.output.adc_ticks_counter.peek().litValue)
  //       a.io.lo_counter.poke(lo_counter(adc_clock_freq, a.io.state.debug.adc_ticks_threshold.peek().litValue.toInt, f_lo_8, false).U)
  //       println("CURR_CALC_ADC_CLOCK_FREQ: " + a.io.state.output.curr_calc_adc_clock_freq_debug.peek().litValue)
  //       println("F_ERR_PREV: " + a.io.state.debug.f_err_prev.peek().litValue)
  //       println("F_ERR: " + a.io.state.output.f_err.peek().litValue)

  //       a.clock.step()
  //       println("\n" + "===== Calculation Took Place =====" + "\n")

  //       println("LO_STOP: " + a.io.state.output.lo_stop_debug.peek().litValue)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //       println("CURR_CALC_ADC_CLOCK_FREQ: " + a.io.state.output.curr_calc_adc_clock_freq_debug.peek().litValue)
  //       println("F_ERR_PREV: " + a.io.state.debug.f_err_prev.peek().litValue)
  //       println("F_ERR: " + a.io.state.output.f_err.peek().litValue)
  //       println("F_ERR_FIXED: " + a.io.state.debug.f_err_fixed.peek())
  //       println("P_TERM: " + a.io.state.debug.p_term.peek())
  //       println("P_TERM_TRUNC: " + a.io.state.output.p_term_trunc.peek().litValue)
  //       assert(a.io.state.output.trigger_controller.peek().litToBoolean == true)
  //       assert(a.io.state.output.controller_state.peek().litValue == 0)

  //       adc_clock_freq_arr_p_and_i_mode_0 = adc_clock_freq_arr_p_and_i_mode_0 :+ adc_clock_freq.toInt
  //       calc_adc_clock_freq_arr_p_and_i_mode_0 = calc_adc_clock_freq_arr_p_and_i_mode_0 :+ a.io.state.output.curr_calc_adc_clock_freq_debug.peek().litValue.toInt

  //       println("ACCUMULATED_ERR: " + a.io.state.output.accumulated_err.peek().litValue)
  //       a.clock.step()
  //       println("\n" + "===== Accumulated Error Updated =====" + "\n")

  //       println("ACCUMULATED_ERR: " + a.io.state.output.accumulated_err.peek().litValue)
  //       println("ACCUMULATED_ERR_FIXED: " + a.io.state.debug.accumulated_err_fixed.peek())
  //       println("I_TERM: " + a.io.state.debug.i_term.peek())
  //       println("I_TERM_TRUNC: " + a.io.state.output.i_term_trunc.peek().litValue)
  //       assert(a.io.state.output.trigger_controller.peek().litToBoolean == true)
  //       assert(a.io.state.output.controller_state.peek().litValue == 1)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //       a.clock.step()
  //       assert(a.io.state.output.trigger_controller.peek().litToBoolean == false)
  //       assert(a.io.state.output.controller_state.peek().litValue == 0)
  //       assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)

  //       println("CONTROL_WORD_PREV: " + a.io.state.debug.control_word_prev.peek().litValue)
  //       println("CONTROL_WORD: " + a.io.state.output.control_word.peek().litValue)
        
  //       adc_clock_freq = adc_clock_freq + calc_freq_offset(a.io.state.debug.control_word_prev.peek().litValue.toInt, a.io.state.output.control_word.peek().litValue.toInt, controller_active)
  //       println("ADC_CLOCK_FREQ Adjusted To: " + adc_clock_freq.toInt)
  //       adc_clock_freq = adc_clock_freq + 2.56e3 * (Random.nextDouble() - 0.5)
  //       println("ADC_CLOCK_FREQ Drifted To: " + adc_clock_freq.toInt + "\n")
  //     }

  //     var adc_clock_freq_arr_filepath_p_and_i_mode_0 = "./generators/baseband/src/test/scala/modem/data/adc_clock_freq_arr_p_and_i_mode_0.csv"
  //     var calc_adc_clock_freq_arr_filepath_p_and_i_mode_0 = "./generators/baseband/src/test/scala/modem/data/calc_adc_clock_freq_arr_p_and_i_mode_0.csv"

  //     writeToCSV(adc_clock_freq_arr_p_and_i_mode_0, adc_clock_freq_arr_filepath_p_and_i_mode_0)
  //     writeToCSV(calc_adc_clock_freq_arr_p_and_i_mode_0, calc_adc_clock_freq_arr_filepath_p_and_i_mode_0)

  //     println("\n\n" + "========== P CONTROLLER, MODE 1 ==========\n\n")

  //     a.io.state.input.mode.poke(1.U)
  //     a.clock.step()
  //     assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //     assert(a.io.state.output.lo_start_debug.peek().litValue == 0)
  //     assert(a.io.state.output.lo_stop_debug.peek().litValue == 0)
  //     assert(a.io.state.output.accumulated_err.peek().litValue == 0)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)

  //     controller_active = true
  //     adc_clock_freq = 32e6 - 5e4
  //     f_lo_8 = 300e6
  //     num_iter = 156
  //     duration_us = 64
  //     dt = duration_us * 1e-6
  //     k_p = 1/1.28e3
  //     k_i = 5 * dt
  //     nominal_adc_clock_freq = 32000000
  //     test_duration_us = duration_us * num_iter
  //     a.io.state.input.p_control_only.poke(true.B)
  //     a.io.state.input.nominal_adc_clock_freq.poke(nominal_adc_clock_freq.U)
  //     a.io.state.input.duration.poke(duration_us.U)
  //     a.io.state.input.k_p.poke(k_p.F(32.W, 30.BP))
  //     a.io.state.input.k_i.poke(k_i.F(32.W, 30.BP))

  //     println("ADC_TICKS_COUNTER: " + a.io.state.output.adc_ticks_counter.peek().litValue)
  //     println("LO_START: " + a.io.state.output.lo_start_debug.peek().litValue)
  //     println("LO_STOP: " + a.io.state.output.lo_stop_debug.peek().litValue)
  //     a.clock.step(5)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)
  //     println("SOP: " + a.io.sop.peek().litToBoolean)
  //     a.io.sop.poke(true.B)
  //     println("SOP: " + a.io.sop.peek().litToBoolean)
  //     a.clock.step()
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == true)
  //     a.clock.step(50)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == true)
  //     println("ADC_TICKS_COUNTER: " + a.io.state.output.adc_ticks_counter.peek().litValue)
  //     a.io.sop.poke(false.B)
  //     a.clock.step(50)
  //     println("ADC_TICKS_COUNTER: " + a.io.state.output.adc_ticks_counter.peek().litValue)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == true)
  //     println("EOP: " + a.io.eop.peek().litToBoolean)
  //     a.io.eop.poke(true.B)
  //     println("EOP: " + a.io.eop.peek().litToBoolean)
  //     a.clock.step()
  //     assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //     assert(a.io.state.output.lo_start_debug.peek().litValue == 0)
  //     assert(a.io.state.output.lo_stop_debug.peek().litValue == 0)
  //     assert(a.io.state.output.accumulated_err.peek().litValue == 0)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)      
  //     a.clock.step(50)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)
  //     a.io.eop.poke(false.B)
  //     a.clock.step(50)
  //     assert(a.io.state.output.adc_ticks_counter.peek().litValue == 0)
  //     assert(a.io.state.output.lo_start_debug.peek().litValue == 0)
  //     assert(a.io.state.output.lo_stop_debug.peek().litValue == 0)
  //     assert(a.io.state.output.accumulated_err.peek().litValue == 0)
  //     assert(a.io.state.output.controller_active.peek().litToBoolean == false)
  //   }
  // }
}
