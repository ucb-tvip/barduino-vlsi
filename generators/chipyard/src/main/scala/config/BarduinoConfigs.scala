package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem.{MBUS, SBUS}
import testchipip.soc.{OBUS}

// --------------
// Rocket Configs
// --------------

class BarduinoConfig extends Config(
  // new chipyard.sky130.WithSky130EFIOCells(sim = false) ++ 
  new chipyard.WithBarduinoChipTop ++
  // new freechips.rocketchip.rocket.WithNBigCores(1) ++                      // 1 RocketTile
  new freechips.rocketchip.rocket.WithNSmallCores(1) ++                      // 1 RocketTile
  // new freechips.rocketchip.subsystem.WithIncoherentTiles ++
  //==================================
  // Set up I/O
  //==================================
  new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(              // 1 serial tilelink port
    manager = Some(testchipip.serdes.SerialTLManagerParams(                             // port acts as a manager of offchip memory
      memParams = Seq(testchipip.serdes.ManagerRAMParams(                               // 4 GB of off-chip memory
        address = BigInt("80000000", 16),
        size    = BigInt("100000000", 16)
      )),
      isMemoryDevice = true
    )),
    client = Some(testchipip.serdes.SerialTLClientParams()),                            // Allow an external manager to probe this chip
    phyParams = testchipip.serdes.ExternalSyncSerialPhyParams(phitWidth=1, flitWidth=16)   // 1-bit bidir interface, sync'd to an external clock
  ))) ++

  new freechips.rocketchip.subsystem.WithNoMemPort ++                                   // Remove axi4 mem port
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

  //==================================
  // Set up peripherals
  //==================================
  
  // Add ability to boot straight to Flash
  new testchipip.boot.WithCustomBootPinAltAddr(address = 0x20000000) ++                                // make custom boot address SPI base

  // Setup PWM
  new chipyard.config.WithPWM(address = 0x10060000, channels = 4, cmpWidth = 8) ++
  new chipyard.config.WithPWM(address = 0x10061000, channels = 4, cmpWidth = 16) ++

  // Setup I2C
  new chipyard.config.WithI2C(address = 0x10041000) ++
  new chipyard.config.WithI2C(address = 0x10040000) ++
  
  // Setup SPI
  new chipyard.config.WithSPI(address = 0x10032000, chipselWidth = 3) ++
  new chipyard.config.WithSPIFlash(address = 0x10031000, fAddress = 0x40000000, size = 0x10000000) ++             // add the SPI psram controller (1 MiB)
  new chipyard.config.WithSPIFlash(address = 0x10030000, fAddress = 0x20000000, size = 0x10000000) ++             // add the SPI flash controller (1 MiB)
  new chipyard.harness.WithSPIFlashTiedOff ++

  // Setup GPIOs
  new chipyard.config.WithGPIO(address = 0x10010000, width = 20, includeIOF = true) ++     // GPIOA

  // Setup UARTs
  new chipyard.config.WithUART(address = 0x10022000, baudrate = 115200) ++
  new chipyard.config.WithUART(address = 0x10021000, baudrate = 115200) ++
  new chipyard.config.WithUART(address = 0x10020000, baudrate = 115200) ++
  new chipyard.config.WithNoUART() ++


  new chipyard.config.WithJTAGDTMKey(partNum = 0x000, manufId = 0x489) ++


  
  //==================================
  // Set up buses
  //==================================

  new testchipip.soc.WithOffchipBusClient(MBUS) ++                                      // offchip bus connects to MBUS, since the serial-tl needs to provide backing memory
  new testchipip.soc.WithOffchipBus ++                                                  // attach a offchip bus, since the serial-tl will master some external tilelink memory
  new testchipip.soc.WithNoScratchpads ++                                               // all memory will be across the Serial-TL Interface
  // new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++                       // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNBanks(1) ++                                   // remove L2$
  new chipyard.config.WithBroadcastManager++ // Replace L2 with a broadcast hub for coherence
  new freechips.rocketchip.subsystem.WithCoherentBusTopology ++ 

  //==================================
  // Set up clock./reset
  //==================================
  new chipyard.clocking.WithSingleClockBroadcastClockGenerator ++

  // Create the uncore clock group
  new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus"), Nil)) ++

  // new chipyard.clocking.WithPureIOClockSky130(freqMHz = 5) ++

  // SETUP RING
  new chipyard.sky130.WithSky130EFIOCells(sim = false) ++
  new chipyard.sky130.WithSky130EFIOTotalCells(45) ++

  new chipyard.config.AbstractConfig)
