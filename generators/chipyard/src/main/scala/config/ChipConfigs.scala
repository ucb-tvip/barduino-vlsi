// package chipyard

// import org.chipsalliance.cde.config.{Config}
// import freechips.rocketchip.diplomacy._
// import freechips.rocketchip.subsystem.{MBUS, SBUS}
// //import testchipip.soc.{OBUS}

// //brought from ScumConfig
// import chipyard._
// import chipyard.config._
// import freechips.rocketchip.diplomacy._
// import freechips.rocketchip.subsystem.{MBUS, SBUS}
// import freechips.rocketchip._
// import testchipip.soc._


// // // A simple config demonstrating how to set up a basic chip in Chipyard
// // class ChipLikeRocketConfig extends Config(
// //   //==================================
// //   // Set up TestHarness
// //   //==================================
// //   new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++ // use absolute frequencies for simulations in the harness
// //                                                                    // NOTE: This only simulates properly in VCS

// //   //==================================
// //   // Set up tiles
// //   //==================================
// //   new freechips.rocketchip.rocket.WithAsynchronousCDCs(depth=8, sync=3) ++ // Add async crossings between RocketTile and uncore
// //   new freechips.rocketchip.rocket.WithNHugeCores(1) ++                      // 1 RocketTile

// //   //==================================
// //   // Set up I/O
// //   //==================================
// //   new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(              // 1 serial tilelink port
// //     manager = Some(testchipip.serdes.SerialTLManagerParams(                             // port acts as a manager of offchip memory
// //       memParams = Seq(testchipip.serdes.ManagerRAMParams(                               // 4 GB of off-chip memory
// //         address = BigInt("80000000", 16),
// //         size    = BigInt("100000000", 16)
// //       )),
// //       isMemoryDevice = true
// //     )),
// //     client = Some(testchipip.serdes.SerialTLClientParams()),                            // Allow an external manager to probe this chip
// //     phyParams = testchipip.serdes.ExternalSyncSerialPhyParams(phitWidth=4, flitWidth=16)   // 4-bit bidir interface, sync'd to an external clock
// //   ))) ++

// //   new freechips.rocketchip.subsystem.WithNoMemPort ++                                   // Remove axi4 mem port
// //   new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++                          // 1 memory channel

// //   //==================================
// //   // Set up buses
// //   //==================================
// //   new testchipip.soc.WithOffchipBusClient(MBUS) ++                                      // offchip bus connects to MBUS, since the serial-tl needs to provide backing memory
// //   new testchipip.soc.WithOffchipBus ++                                                  // attach a offchip bus, since the serial-tl will master some external tilelink memory

// //   //==================================
// //   // Set up clock./reset
// //   //==================================
// //   new chipyard.clocking.WithPLLSelectorDividerClockGenerator ++   // Use a PLL-based clock selector/divider generator structure

// //   // Create the uncore clock group
// //   new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus"), Nil)) ++

// //   new chipyard.config.AbstractConfig)

// // class FlatChipTopChipLikeRocketConfig extends Config(
// //   new chipyard.example.WithFlatChipTop ++
// //   new chipyard.ChipLikeRocketConfig)

// // // A simple config demonstrating a "bringup prototype" to bringup the ChipLikeRocketconfig
// // class ChipBringupHostConfig extends Config(
// //   //=============================
// //   // Set up TestHarness for standalone-sim
// //   //=============================
// //   new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++  // Generate absolute frequencies
// //   new chipyard.harness.WithSerialTLTiedOff ++                       // when doing standalone sim, tie off the serial-tl port
// //   new chipyard.harness.WithSimTSIToUARTTSI ++                       // Attach SimTSI-over-UART to the UART-TSI port
// //   new chipyard.iobinders.WithSerialTLPunchthrough ++                // Don't generate IOCells for the serial TL (this design maps to FPGA)

// //   //=============================
// //   // Setup the SerialTL side on the bringup device
// //   //=============================
// //   new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(
// //     manager = Some(testchipip.serdes.SerialTLManagerParams(
// //       memParams = Seq(testchipip.serdes.ManagerRAMParams(                            // Bringup platform can access all memory from 0 to DRAM_BASE
// //         address = BigInt("00000000", 16),
// //         size    = BigInt("80000000", 16)
// //       ))
// //     )),
// //     client = Some(testchipip.serdes.SerialTLClientParams()),                                        // Allow chip to access this device's memory (DRAM)
// //     phyParams = testchipip.serdes.InternalSyncSerialPhyParams(phitWidth=4, flitWidth=16, freqMHz = 75) // bringup platform provides the clock
// //   ))) ++

// //   //============================
// //   // Setup bus topology on the bringup system
// //   //============================
// //   new testchipip.soc.WithOffchipBusClient(SBUS,                                // offchip bus hangs off the SBUS
// //     blockRange = AddressSet.misaligned(0x80000000L, (BigInt(1) << 30) * 4)) ++ // offchip bus should not see the main memory of the testchip, since that can be accessed directly
// //   new testchipip.soc.WithOffchipBus ++                                         // offchip bus

// //   //=============================
// //   // Set up memory on the bringup system
// //   //=============================
// //   new freechips.rocketchip.subsystem.WithExtMemSize((1 << 30) * 4L) ++         // match what the chip believes the max size should be

// //   //=============================
// //   // Generate the TSI-over-UART side of the bringup system
// //   //=============================
// //   new testchipip.tsi.WithUARTTSIClient(initBaudRate = BigInt(921600)) ++       // nonstandard baud rate to improve performance

// //   //=============================
// //   // Set up clocks of the bringup system
// //   //=============================
// //   new chipyard.clocking.WithPassthroughClockGenerator ++ // pass all the clocks through, since this isn't a chip
// //   new chipyard.config.WithUniformBusFrequencies(75.0) ++   // run all buses of this system at 75 MHz

// //   // Base is the no-cores config
// //   new chipyard.NoCoresConfig)

// // // DOC include start: TetheredChipLikeRocketConfig
// // class TetheredChipLikeRocketConfig extends Config(
// //   new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++   // use absolute freqs for sims in the harness
// //   new chipyard.harness.WithMultiChipSerialTL(0, 1) ++                // connect the serial-tl ports of the chips together
// //   new chipyard.harness.WithMultiChip(0, new ChipLikeRocketConfig) ++ // ChipTop0 is the design-to-be-taped-out
// //   new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig))  // ChipTop1 is the bringup design
// // // DOC include end: TetheredChipLikeRocketConfig

// // // Verilator does not initialize some of the async-reset reset-synchronizer
// // // flops properly, so this config disables them.
// // // This config should only be used for verilator simulations
// // class VerilatorCITetheredChipLikeRocketConfig extends Config(
// //   new chipyard.harness.WithAbsoluteFreqHarnessClockInstantiator ++   // use absolute freqs for sims in the harness
// //   new chipyard.harness.WithMultiChipSerialTL(0, 1) ++                // connect the serial-tl ports of the chips together
// //   new chipyard.harness.WithMultiChip(0,                                         // These fragments remove all troublesome
// //     new chipyard.clocking.WithPLLSelectorDividerClockGenerator(enable=false) ++ // clocking features from the design
// //     new chipyard.iobinders.WithDebugIOCells(syncReset = false) ++
// //     new chipyard.config.WithNoResetSynchronizers ++
// //     new ChipLikeRocketConfig) ++
// //   new chipyard.harness.WithMultiChip(1, new ChipBringupHostConfig))




// class ScumConfig extends Config(
//   // BEGIN: Common adjustments (don't change these)
//   new chipyard.config.WithNPMPs(0) ++
//   new chipyard.config.WithNoClockTap ++

//   // END: Common adjustments
  
//   // // BEGIN: Crypto Accelerator
//   // new encryptionAccelerator.WithAesAccelerator ++
//   // new encryptionAccelerator.Withsha256Accelerator ++
//   // new ecc.WithEccAccelerator ++
//   // //END: Crypto Accelerator

//   // BEGIN: RocketTile settings
//  // new freechips.rocketchip.rocket.WithL1ICacheSets(64) ++
//  // new freechips.rocketchip.rocket.WithL1ICacheWays(2) ++
//  // new freechips.rocketchip.rocket.WithL1DCacheSets(64) ++
// //  new freechips.rocketchip.rocket.WithL1DCacheWays(2) ++
//   // new freechips.rocketchip.subsystem.WithRV32 ++
// //  new freechips.rocketchip.rocket.WithFastMulDiv ++
//   // new freechips.rocketchip.subsystem.WithDefaultFPU ++
//   // new freechips.rocketchip.subsystem.WithFPUWithoutDivSqrt ++
//   new freechips.rocketchip.rocket.WithNSmallCores(1) ++                              // No vm, fpu and btb
//   // END: RocketTile settings

//   //==================================
//   // Set up peripherals
//   //==================================
//   // new testchipip.soc.WithNoScratchpadMonitors ++
//   // new chipyard.config.WithNoBusErrorDevices ++
  
  
//   //==================================
//   // Set up 256 KB scratchpad
//   //==================================
//   // new chipyard.config.WithL2TLBs(0) ++
//   // new testchipip.soc.WithMbusScratchpad(base = 0x80000000L, size = 256 * 1024, banks = 1) ++
//   // new freechips.rocketchip.subsystem.WithNBanks(1) ++   
//   // new chipyard.config.WithBroadcastManager ++           // No L2 cache
//   // new testchipip.soc.WithNoScratchpads ++                      // remove subsystem scratchpads, confusingly named, does not remove the L1D$ scratchpads
//   // new freechips.rocketchip.subsystem.WithNoMemPort ++       

  
//   // END: Memory subsystem settings
  
//   // new testchipip.serdes.WithSerialTL(Seq(testchipip.serdes.SerialTLParams(
//   //   // No manager for our SerialTL since we do not have off-chip memory
//   //   manager = None, 
//   //   // Allow an external manager to probe this chip - we must do this for the bringup
//   //   // platform to be able to probe/access our memory map
//   //   client = Some(testchipip.serdes.SerialTLClientParams()),                            
//   //   // 1-bit bidir interface, sync'd to OUR clock (SCuM-V's clock)
//   //   phyParams = testchipip.serdes.DecoupledInternalSyncSerialPhyParams(phitWidth=1, flitWidth=16, freqMHz = 350)   
//   // ))) ++

//                             // Remove axi4 mem port

//   // new chipyard.clocking.WithClockGroupsCombinedByName(("uncore", Seq("implicit", "sbus", "mbus", "cbus", "system_bus", "fbus", "pbus", "serial_tl_0_clock_0"), Nil)) ++
//   // new chipyard.clocking.WithClockGroupsCombinedByName(("precision", Seq("bm_block"), Nil)) ++
//   // new chipyard.config.WithFrontBusFrequency(350.0) ++     
//   // new chipyard.config.WithMemoryBusFrequency(350.0) ++
//   // new chipyard.config.WithPeripheryBusFrequency(350.0) ++
//   // new chipyard.config.WithSystemBusFrequency(350.0) ++
//   // new chipyard.config.WithControlBusFrequency(350.0) ++
//   // new chipyard.harness.WithHarnessBinderClockFreqMHz(350.0) ++
//   // new chipyard.clocking.WithPassthroughClockGenerator ++   // Pass through clock

//   // BEGIN: Baseband-modem peripheral settings
//   //new baseband.WithBasebandModem() ++
//  // new chipyard.iobinders.WithBasebandModemPunchthrough() ++
//  // new chipyard.harness.WithBasebandModemTiedOff ++
//   // END: Baseband-modem peripheral settings

//   // BEGIN: NFC-modem peripheral settings
//   // new nfc_modem.WithNFCModem() ++
//   // new chipyard.iobinders.WithNFCModemPunchthrough() ++
//   // new chipyard.harness.WithNFCModemTiedOff ++
//   // new chipyard.harness.WithNFCModemTestBench ++
//   // END: NFC-modem peripheral settings

//   // BEGIN: SCUMV tuning peripheral settings
//   // new scumvtuning.WithSCUMVTuning() ++
//   // new chipyard.iobinders.WithSCUMVTuningPunchthrough() ++
//   // new chipyard.harness.WithSCUMVTuningTiedOff ++
//   // END: SCUMV tuning peripheral settings


//   // BEGIN: AFE tuning peripheral settings
//   // new afe.WithAFE() ++
//   // new chipyard.iobinders.WithAFEPunchthrough() ++
//   // new chipyard.harness.WithAFETiedOff ++
//   // END: AFE tuning peripheral settings


//   // BEGIN: Sensor ADC peripheral settings
//   // new sensoradc.WithSensorADC() ++
//   // new chipyard.iobinders.WithSensorADCPunchthrough() ++
//   // new chipyard.harness.WithSensorADCTiedOff ++  
//   // END: Sensor ADC peripheral settings

//   //new chipyard.config.WithSPIFlash ++                       // add the SPI flash controller (100 MiB)
//   // new chipyard.harness.WithSimSPIFlashModel(false) ++       // add the SPI flash model in the harness (writeable)
//  // new chipyard.config.WithIntech22SPICells ++
//  // new testchipip.boot.WithCustomBootPinAltAddr(0x20000000) ++    // make custom boot address SPI base

//   // new rtc_timer.WithRTCTimer() ++
//   // new chipyard.iobinders.WithRTCTimerPunchthrough() ++
//   // new chipyard.harness.WithRTCTimerTiedOff ++

//   //new chipyard.config.WithGPIO(width = 22) ++
//  // new chipyard.config.WithJTAGDTMKey(partNum = 0x000, manufId = 0x489) ++

//   //from tapeout
//   // new chipyard.config.WithIntech22IOCells ++
//   // new chipyard.config.WithIntech22GPIOCells ++

//    new chipyard.sky130.WithSky130EFIOCells(sim = false) ++
//    new chipyard.sky130.WithSky130EFIOTotalCells(45) ++



//   new chipyard.config.AbstractConfig
// )