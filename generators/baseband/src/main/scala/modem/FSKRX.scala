package modem

import chisel3._
import chisel3.util._
import fixedpoint._
import fixedpoint.fromIntToBinaryPoint
import baseband.{BasebandModemParams, RadioMode, PDADataInputIO}
import freechips.rocketchip.util.AsyncQueue
import freechips.rocketchip.util.AsyncQueueParams

class FSKRXControlIO extends Bundle {
  val enable = Input(Bool())
}

class FSKRXIO(params: BasebandModemParams) extends Bundle {
  val analog = new Bundle {
    val i = Input(UInt(params.adcBits.W))
    val q = Input(UInt(params.adcBits.W))
  }
  val ble     = Flipped(new PDADataInputIO)
  val lrwpan  = Flipped(new PDADataInputIO)
  val control = new FSKRXControlIO
  val firCmd  = Flipped(Valid(new FIRCoefficientChangeCommand))

  val radioMode = Input(UInt(1.W))
  val accessAddress = Input(UInt(32.W))
  val shr = Input(UInt(16.W))

  val bleLoopback    = Input(Bool())
  val bleLoopBit     = Input(Bool())
  val lrwpanLoopback = Input(Bool())
  val lrwpanLoopBit  = Input(Bool())

  val ifCounter = new ModemIFCounterStateIO
  val adcClockTuning = new ModemADCClockTuningStateIO
  val lo_counter = Input(UInt(64.W))
}

class FSKRX(params: BasebandModemParams) extends Module {
  val io = IO(new FSKRXIO(params) {
  })

  val fsk_demod = Module(new FSKDemodulation(params))
  fsk_demod.io.i := io.analog.i
  fsk_demod.io.q := io.analog.q
  // firCmd Queue below

  val ble_cdr    =  Module(new CDR(32)) // 1Mbps
  val bleCDRQueue = Module(new Queue(Bool(), params.asyncQueueDepth))
  val lrwpan_cdr = Module(new CDR(16)) // 2Mbps
  val lrwpanCDRQueue = Module(new Queue(Bool(), params.asyncQueueDepth))
  
  val bleLoopback = RegNext(RegNext(io.bleLoopback))
  val lrwpanLoopback = RegNext(RegNext(io.lrwpanLoopback))
  ble_cdr.io.in    := Mux(bleLoopback, io.bleLoopBit, fsk_demod.io.out)
  lrwpan_cdr.io.in := Mux(lrwpanLoopback, io.lrwpanLoopBit, fsk_demod.io.out)

  // count bits for clock calibration
  val ble_ctr = RegInit(0.U(32.W))
  when(bleCDRQueue.io.deq.fire) {
    ble_ctr := ble_ctr + 1.U
  }
  io.ble.bitCount := ble_ctr

  val lrwpan_ctr = RegInit(0.U(32.W))
  when(lrwpanCDRQueue.io.deq.fire) {
    lrwpan_ctr := lrwpan_ctr + 1.U
  }
  io.lrwpan.bitCount := lrwpan_ctr

  // repurpose end-of-packet signal as a disable
  val ble_disable    = (io.radioMode =/= RadioMode.BLE)    | ~io.control.enable
  val lrwpan_disable = (io.radioMode =/= RadioMode.LRWPAN) | ~io.control.enable

  val ble_pkt_detect = Module(new BLEPacketDetector)
  ble_pkt_detect.io.aa  := io.accessAddress
  ble_pkt_detect.io.eop := io.ble.eop | ble_disable
  ble_pkt_detect.io.in.bits := bleCDRQueue.io.deq.bits
  ble_pkt_detect.io.in.valid := bleCDRQueue.io.deq.valid
  io.ble.sop  := ble_pkt_detect.io.sop
  io.ble.data := ble_pkt_detect.io.out

  val lrwpan_pkt_detect = Module(new LRWPANPacketDetector)
  lrwpan_pkt_detect.io.shr := io.shr
  lrwpan_pkt_detect.io.eop := io.lrwpan.eop | lrwpan_disable
  lrwpan_pkt_detect.io.in.bits := lrwpanCDRQueue.io.deq.bits
  lrwpan_pkt_detect.io.in.valid := lrwpanCDRQueue.io.deq.valid
  io.lrwpan.sop  := lrwpan_pkt_detect.io.sop
  io.lrwpan.data := lrwpan_pkt_detect.io.out

  val firCmdQueue = Module(new Queue(new FIRCoefficientChangeCommand, params.asyncQueueDepth))
  firCmdQueue.io.enq.bits := io.firCmd.bits
  firCmdQueue.io.enq.valid := io.firCmd.valid

  firCmdQueue.io.deq.ready := true.B
  fsk_demod.io.firCmd.bits := firCmdQueue.io.deq.bits
  fsk_demod.io.firCmd.valid := firCmdQueue.io.deq.valid

  bleCDRQueue.io.enq.bits := ble_cdr.io.out.bits
  bleCDRQueue.io.enq.valid := ble_cdr.io.out.valid

  bleCDRQueue.io.deq.ready := true.B

  lrwpanCDRQueue.io.enq.bits := lrwpan_cdr.io.out.bits
  lrwpanCDRQueue.io.enq.valid := lrwpan_cdr.io.out.valid

  lrwpanCDRQueue.io.deq.ready := true.B

  // IF Counter
  val if_counter_sop = Mux(io.radioMode === RadioMode.BLE, ble_pkt_detect.io.sop, lrwpan_pkt_detect.io.sop)
  val if_counter = Module(new IFCounter(params)) 
  if_counter.io.data := io.analog.i
  if_counter.io.sop := if_counter_sop

  val ifCounterInputQueue = Module(new Queue(new ModemIFCounterInputIO, params.asyncQueueDepth))
  ifCounterInputQueue.io.enq.bits := io.ifCounter.input
  ifCounterInputQueue.io.enq.valid := true.B
  ifCounterInputQueue.io.deq.ready := true.B
  if_counter.io.state.input := ifCounterInputQueue.io.deq.bits

  val ifCounterOutputQueue = Module(new Queue(new ModemIFCounterOutputIO, params.asyncQueueDepth))
  ifCounterOutputQueue.io.enq.bits := if_counter.io.state.output
  ifCounterOutputQueue.io.enq.valid := true.B
  ifCounterOutputQueue.io.deq.ready := true.B
  io.ifCounter.output := ifCounterOutputQueue.io.deq.bits

  // ADC Clock Tuning
  val adc_clock_tuning_sop = Mux(io.radioMode === RadioMode.BLE, ble_pkt_detect.io.sop, lrwpan_pkt_detect.io.sop)
  val adc_clock_tuning_eop = Mux(io.radioMode === RadioMode.BLE, ble_pkt_detect.io.eop, lrwpan_pkt_detect.io.eop)
  val adc_clock_tuning = Module(new ADCClockTuning(params)) 
  adc_clock_tuning.io.sop := adc_clock_tuning_sop
  adc_clock_tuning.io.eop := adc_clock_tuning_eop
  adc_clock_tuning.io.lo_counter := io.lo_counter

  val ADCClockTuningInputQueue = Module(new Queue(new ModemADCClockTuningInputIO, params.adcClockTuningAsyncQueueDepth))
  ADCClockTuningInputQueue.io.enq.bits := io.adcClockTuning.input
  ADCClockTuningInputQueue.io.enq.valid := true.B
  ADCClockTuningInputQueue.io.deq.ready := true.B
  adc_clock_tuning.io.state.input := ADCClockTuningInputQueue.io.deq.bits

  val ADCClockTuningOutputQueue = Module(new Queue(new ModemADCClockTuningOutputIO, params.adcClockTuningAsyncQueueDepth))
  ADCClockTuningOutputQueue.io.enq.bits := adc_clock_tuning.io.state.output
  ADCClockTuningOutputQueue.io.enq.valid := true.B
  ADCClockTuningOutputQueue.io.deq.ready := true.B
  io.adcClockTuning.output := ADCClockTuningOutputQueue.io.deq.bits
}