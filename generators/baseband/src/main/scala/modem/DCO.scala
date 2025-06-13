package modem

import chisel3._
import chisel3.util._
import fixedpoint._
import fixedpoint.{fromIntToBinaryPoint}
import baseband.BasebandModemParams
import freechips.rocketchip.util.AsyncQueue
import freechips.rocketchip.util.AsyncQueueParams

class DCOControlIO extends Bundle {
  val gain  = FixedPoint(9.W, 2.BP)
  val reset = Bool()
}

/*  DC Offset compensation for one ADC

    Constantly integrate over input, apply gain, index into LUT, send to analog to calibrate
    Any DC offset will cause integral to overflow unless compensated
 */
class DCO(params: BasebandModemParams) extends Module {
  val io = IO(new Bundle {
    val adcIn = Input(UInt(params.adcBits.W))
    val dcoLUTIndex = Output(UInt(5.W))
    val control = Input(new DCOControlIO)
  })

  withReset(reset.asBool | io.control.reset) {
    // buffer and reinterpret as FixedPoint between -1 and ~1 (assumes middle is 0V)
    val dadc = RegNext(io.adcIn)
    val sadc = Cat(~dadc(params.adcBits-1), dadc(params.adcBits-2,0)).asSInt
    val fadc = sadc.asFixedPoint((params.adcBits-1).BP)

    val zeroDynamic = 0.F(16.W, 8.BP) + 0.F(16.W, 8.BP)
    val integral = RegInit(zeroDynamic)
    integral := integral + fadc

    val gainProduct = Wire(FixedPoint(16.W, 8.BP))
    gainProduct := io.control.gain * integral

    // note this is a signed index (negative offset = upper half of LUT)
    io.dcoLUTIndex := gainProduct(gainProduct.getWidth-1, gainProduct.getWidth-5)
  }
}

class DCOWrapper(params: BasebandModemParams) extends Module {
  val io = IO(new Bundle {
    val adcIn = Input(UInt(params.adcBits.W))
    val dcoLUTIndex = Output(UInt(5.W))
    val control = Input(new DCOControlIO)
    // val adc_clock = Input(Clock())
  })

  // val dco = withClock(io.adc_clock) {Module(new DCO(params))}
  val dco = Module(new DCO(params))
  dco.io.adcIn := io.adcIn

  val controlQueue = Module(new Queue(new DCOControlIO, params.modemQueueDepth))
  controlQueue.io.enq.bits := io.control

  val controlPrev = RegNext(io.control)
  controlQueue.io.enq.valid := controlPrev.asUInt =/= io.control.asUInt

  controlQueue.io.deq.ready := true.B
  dco.io.control := RegEnable(controlQueue.io.deq.bits, controlQueue.io.deq.valid)

  val idxQueue = Module(new Queue(UInt(params.adcBits.W), params.modemQueueDepth))
  idxQueue.io.enq.valid := true.B
  idxQueue.io.enq.bits := dco.io.dcoLUTIndex

  idxQueue.io.deq.ready := true.B
  io.dcoLUTIndex := RegEnable(idxQueue.io.deq.bits, idxQueue.io.deq.valid)

}

