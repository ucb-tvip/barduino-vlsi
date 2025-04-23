package chipyard


import chisel3._
import chisel3.experimental.Analog
import org.chipsalliance.cde.config.{Config, Field, Parameters}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.util._
import freechips.rocketchip.subsystem.{PBUS, HasTileLinkLocations}
import freechips.rocketchip.devices.debug.{ExportDebug, JtagDTMKey, Debug}
import freechips.rocketchip.tilelink.{TLBuffer, TLFragmenter}
import chipyard.{BuildSystem, DigitalTop}
import chipyard.harness.{BuildTop}
import chipyard.clocking._
import chipyard.iobinders._
import chipyard.iocell._
import testchipip.serdes.{SerialTLKey}
import testchipip.spi.{SPIChipIO}
import sifive.blocks.devices.spi._
import sifive.blocks.devices.gpio._
import sifive.blocks.devices.i2c._
// import chipyard.sky130.ElaborateJSON
import chipyard.sky130._



class WithBarduinoChipTop extends Config((site, here, up) => {
  case BuildTop => (p: Parameters) => new BarduinoChipTop()(p)
})

// This "BarduinoChipTop" uses no IOBinders, so all the IO have
// to be explicitly constructed.
// This only supports the base "DigitalTop"
class BarduinoChipTop(implicit p: Parameters) extends LazyModule 
  with HasChipyardPorts 
  with chipyard.sky130.HasSky130EFCaravelPOR
  with chipyard.sky130.HasSky130EFIOCells
// with chipyard.sky130.HasSky130EFIONoConnCells
  // with ElaborateJSON
 {
  override lazy val desiredName = "ChipTop"
  val system = LazyModule(p(BuildSystem)(p)).suggestName("system").asInstanceOf[DigitalTop]

  //========================
  // Diplomatic clock stuff
  //========================
  val tlbus = system.locateTLBusWrapper(system.prciParams.slaveWhere)
  val baseAddress = system.prciParams.baseAddress
  val clockDivider  = system.prci_ctrl_domain { LazyModule(new TLClockDivider (baseAddress + 0x20000, tlbus.beatBytes)) }
  val clockSelector = system.prci_ctrl_domain { LazyModule(new TLClockSelector(baseAddress + 0x30000, tlbus.beatBytes)) }
  val pllCtrl       = system.prci_ctrl_domain { LazyModule(new FakePLLCtrl    (baseAddress + 0x40000, tlbus.beatBytes)) }

  tlbus.coupleTo("clock-div-ctrl") { clockDivider.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := TLBuffer() := _ }
  tlbus.coupleTo("clock-sel-ctrl") { clockSelector.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := TLBuffer() := _ }
  tlbus.coupleTo("pll-ctrl") { pllCtrl.tlNode := TLFragmenter(tlbus.beatBytes, tlbus.blockBytes) := TLBuffer() := _ }

  system.chiptopClockGroupsNode := clockDivider.clockNode := clockSelector.clockNode

  // Connect all other requested clocks
  val slowClockSource = ClockSourceNode(Seq(ClockSourceParameters()))
  val pllClockSource = ClockSourceNode(Seq(ClockSourceParameters()))

  // The order of the connections to clockSelector.clockNode configures the inputs
  // of the clockSelector's clockMux. Default to using the slowClockSource,
  // software should enable the PLL, then switch to the pllClockSource
  clockSelector.clockNode := slowClockSource
  clockSelector.clockNode := pllClockSource

  val pllCtrlSink = BundleBridgeSink[FakePLLCtrlBundle]()
  pllCtrlSink := pllCtrl.ctrlNode

  val debugClockSinkNode = ClockSinkNode(Seq(ClockSinkParameters()))
  debugClockSinkNode := system.locateTLBusWrapper(p(ExportDebug).slaveWhere).fixedClockNode
  def debugClockBundle = debugClockSinkNode.in.head._1

  var ports: Seq[Port[_]] = Nil

  override lazy val module = new BarduinoChipTopImpl
  // lazy val iocells = module.iocells
  // def getIOCells: Seq[IOCell] = module.allIOCells.toSeq
  // lazy val iocells = InModuleBody { module.iocells.toSeq }
  class BarduinoChipTopImpl extends LazyRawModuleImp(this) {
    // val allIOCells = scala.collection.mutable.ArrayBuffer[IOCell]()

    def connectQSPIIOF(spiPin: SPIDataIO, iof: IOFPin) = {
      spiPin.i   := iof.i.ival
      iof.o.oval := spiPin.o
      iof.o.ie   := spiPin.ie
      iof.o.oe   := spiPin.oe

      iof.o.valid := true.B
    }

    // Tie off all unused secondary GPIO functions
    system.iof(0).get.iof_0.map {iof =>
      iof.o.oval  := false.B
      iof.o.oe    := false.B
      iof.o.ie    := false.B
      iof.o.valid := false.B
    }
    system.iof(0).get.iof_1.map {iof =>
      iof.o.oval  := false.B
      iof.o.oe    := false.B
      iof.o.ie    := false.B
      iof.o.valid := false.B
    }
    
    //=========================
    // Clock/reset
    //=========================
    val clock_wire = Wire(Input(Clock()))
    val reset_wire = Wire(Input(AsyncReset()))
    val (clock_pad, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
    val (reset_pad, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

    // allIOCells ++= clockIOCell
    // allIOCells ++= resetIOCell
    // clockIOCell.foreach(registerSky130EFIOCell)
    clockIOCell.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }
    resetIOCell.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    slowClockSource.out.unzip._1.map { o =>
      o.clock := clock_wire
      o.reset := reset_wire
    }

    ports = ports :+ ClockPort(() => clock_pad, 100.0)
    ports = ports :+ ResetPort(() => reset_pad)

    // For a real chip you should replace this ClockSourceAtFreqFromPlusArg
    // with a blackbox of whatever PLL is being integrated
    val fake_pll = Module(new ClockSourceAtFreqFromPlusArg("pll_freq_mhz"))
    fake_pll.io.power := pllCtrlSink.in(0)._1.power
    fake_pll.io.gate := pllCtrlSink.in(0)._1.gate

    pllClockSource.out.unzip._1.map { o =>
      o.clock := fake_pll.io.clk
      o.reset := reset_wire
    }

    //=========================
    // Custom Boot
    //=========================
    val (custom_boot_pad, customBootIOCell) = IOCell.generateIOFromSignal(system.custom_boot_pin.get.getWrappedValue, "custom_boot", p(IOCellKey))
    ports = ports :+ CustomBootPort(() => custom_boot_pad)
    
    // allIOCells ++= customBootIOCell
    customBootIOCell.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    //=========================
    // Serialized TileLink
    //=========================
    val (serial_tl_pad, serialTLIOCells) = IOCell.generateIOFromSignal(system.serial_tls(0).getWrappedValue, "serial_tl", p(IOCellKey))
    ports = ports :+ SerialTLPort(() => serial_tl_pad, p(SerialTLKey)(0), system.serdessers(0), 0)

    // allIOCells ++= serialTLIOCells
    serialTLIOCells.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    //=========================
    // JTAG/Debug
    //=========================
    val debug = system.debug.get
    // We never use the PSDIO, so tie it off on-chip
    system.psd.psd.foreach { _ <> 0.U.asTypeOf(new PSDTestMode) }
    system.resetctrl.map { rcio => rcio.hartIsInReset.map { _ := false.B } }

    // Tie off extTrigger
    debug.extTrigger.foreach { t =>
      t.in.req := false.B
      t.out.ack := t.out.req
    }
    // Tie off disableDebug
    debug.disableDebug.foreach { d => d := false.B }
    // Drive JTAG on-chip IOs
    debug.systemjtag.map { j =>
      j.reset := ResetCatchAndSync(j.jtag.TCK, debugClockBundle.reset.asBool)
      j.mfr_id := p(JtagDTMKey).idcodeManufId.U(11.W)
      j.part_number := p(JtagDTMKey).idcodePartNum.U(16.W)
      j.version := p(JtagDTMKey).idcodeVersion.U(4.W)
    }

    Debug.connectDebugClockAndReset(Some(debug), debugClockBundle.clock)

    // Add IOCells for the DMI/JTAG/APB ports
    require(!debug.clockeddmi.isDefined)
    require(!debug.apb.isDefined)
    val (jtag_pad, jtagIOCells) = debug.systemjtag.map { j =>
      val jtag_wire = Wire(new JTAGChipIO())
      j.jtag.TCK := jtag_wire.TCK
      j.jtag.TMS := jtag_wire.TMS
      j.jtag.TDI := jtag_wire.TDI
      jtag_wire.TDO := j.jtag.TDO.data
      IOCell.generateIOFromSignal(jtag_wire, "jtag", p(IOCellKey), abstractResetAsAsync = true)
    }.get

    ports = ports :+ JTAGPort(() => jtag_pad)

    // allIOCells ++= jtagIOCells
    jtagIOCells.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    //==========================
    // GPIO
    //==========================
    require(system.gpio.size == 1)
  
    ports = ports ++ system.gpio(0).pins.zipWithIndex.map { case (pin, i) =>
      val g = IO(Analog(1.W)).suggestName(s"gpio_${i}")
      // val iocell = p(IOCellKey).gpio().suggestName(s"gpio_iocell_${i}")
      val iocell = p(IOCellKey).gpio().named(s"gpio_iocell_${i}")
      iocell.io.o := pin.o.oval
      iocell.io.oe := pin.o.oe
      iocell.io.ie := pin.o.ie
      pin.i.ival := iocell.io.i
      pin.i.po.foreach(_ := DontCare)
      iocell.io.pad <> g

      // collect into IOCell buffer
      // allIOCells += iocell
      registerSky130EFIOCell(iocell.asInstanceOf[Sky130EFIOCellLike])

      GPIOPort(() => g, 0, i)
    }



    //==========================
    // UART
    //==========================
    require(system.uarts.size == 3)
    val (uart_pad, uartIOCells) = IOCell.generateIOFromSignal(system.uart(0), "uart_0", p(IOCellKey))
    val where = PBUS // TODO fix
    val bus = system.asInstanceOf[HasTileLinkLocations].locateTLBusWrapper(where)
    val freqMHz = bus.dtsFrequency.get / 1000000
    ports = ports :+ UARTPort(() => uart_pad, 0, freqMHz.toInt)
    
    OutputPortToIOF(system.uart(1).txd, system.iof(0).get.iof_0(16))
    system.uart(1).rxd := InputPortToIOF(system.iof(0).get.iof_0(17))
    OutputPortToIOF(system.uart(2).txd, system.iof(0).get.iof_0(18))
    system.uart(2).rxd := InputPortToIOF(system.iof(0).get.iof_0(19))

    // allIOCells ++= uartIOCells
    uartIOCells.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    //==========================
    // QSPI
    //==========================
    require(system.qspi.size == 2)
    
    val spiPorts = IO(new SPIChipIO(system.qspi(0).c.csWidth)).suggestName("qspi_0")

    // SCK and CS are unidirectional outputs
    val sckIOs = IOCell.generateFromSignal(system.qspi(0).sck, spiPorts.sck, "qspi_0_sck", p(IOCellKey), IOCell.toAsyncReset)
    val csIOs = IOCell.generateFromSignal(system.qspi(0).cs, spiPorts.cs, "qspi_0_cs", p(IOCellKey), IOCell.toAsyncReset)

    sckIOs.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    csIOs.foreach{
      case cell: Sky130EFIOCellLike => registerSky130EFIOCell(cell)
    }

    // DQ are bidirectional, so then need special treatment
    val dqIOs = system.qspi(0).dq.zip(spiPorts.dq).zipWithIndex.map { case ((pin, ana), j) =>
      // val iocell = p(IOCellKey).gpio().suggestName(s"qspi_0_dq_${j}")
      val iocell = p(IOCellKey).gpio().named(s"qspi_0_dq_${j}")
      iocell.io.o := pin.o
      iocell.io.oe := pin.oe
      iocell.io.ie := true.B
      pin.i := iocell.io.i
      iocell.io.pad <> ana

      // collect into IOCell buffer
      // allIOCells += iocell
      registerSky130EFIOCell(iocell.asInstanceOf[Sky130EFIOCellLike])

      iocell
    }

    // ports = ports :+ SPIFlashPort(() => spiPorts, p(PeripherySPIFlashKey)(0), 0)
    OutputPortToIOF(system.qspi(1).sck, system.iof(0).get.iof_0(0))
    connectQSPIIOF(system.qspi(1).dq(0), system.iof(0).get.iof_0(1))
    connectQSPIIOF(system.qspi(1).dq(1), system.iof(0).get.iof_0(2))
    connectQSPIIOF(system.qspi(1).dq(2), system.iof(0).get.iof_0(3))
    connectQSPIIOF(system.qspi(1).dq(3), system.iof(0).get.iof_0(4))
    OutputPortToIOF(system.qspi(1).cs(0), system.iof(0).get.iof_0(5))

    //==========================
    // SPI
    //==========================
    require(system.spi.size == 1 && system.spi(0).cs.size == 3)
    OutputPortToIOF(system.spi(0).sck, system.iof(0).get.iof_0(6))
    connectQSPIIOF(system.spi(0).dq(0), system.iof(0).get.iof_0(7))
    connectQSPIIOF(system.spi(0).dq(1), system.iof(0).get.iof_0(8))
    OutputPortToIOF(system.spi(0).cs(0), system.iof(0).get.iof_0(9))
    OutputPortToIOF(system.spi(0).cs(1), system.iof(0).get.iof_0(10))
    OutputPortToIOF(system.spi(0).cs(2), system.iof(0).get.iof_0(11))

    system.spi(0).dq(2) := DontCare
    system.spi(0).dq(3) := DontCare

    //==========================
    // PWM
    //==========================

    OutputPortToIOF(system.pwm(0).gpio(0), system.iof(0).get.iof_1(6))
    OutputPortToIOF(system.pwm(0).gpio(1), system.iof(0).get.iof_1(7))
    OutputPortToIOF(system.pwm(0).gpio(2), system.iof(0).get.iof_1(8))
    OutputPortToIOF(system.pwm(0).gpio(3), system.iof(0).get.iof_1(9))

    OutputPortToIOF(system.pwm(1).gpio(0), system.iof(0).get.iof_1(16))
    OutputPortToIOF(system.pwm(1).gpio(1), system.iof(0).get.iof_1(17))
    OutputPortToIOF(system.pwm(1).gpio(2), system.iof(0).get.iof_1(18))
    OutputPortToIOF(system.pwm(1).gpio(3), system.iof(0).get.iof_1(19))

    //==========================
    // I2C
    //==========================
  
    require(system.i2c.size == 2)
    
    def connectI2CIOF(i2cPin: I2CPin, iof: IOFPin) = {
      i2cPin.in := iof.i.ival
      iof.o.oval := i2cPin.out
      iof.o.ie := true.B
      iof.o.oe := i2cPin.oe

      iof.o.valid := true.B
    }
    

    connectI2CIOF(system.i2c(0).scl, system.iof(0).get.iof_0(12))
    connectI2CIOF(system.i2c(0).sda, system.iof(0).get.iof_0(13))
    connectI2CIOF(system.i2c(1).scl, system.iof(0).get.iof_0(14))
    connectI2CIOF(system.i2c(1).sda, system.iof(0).get.iof_0(15))


    //==========================
    // External interrupts (tie off)
    //==========================
    system.module.interrupts := DontCare
  }
}