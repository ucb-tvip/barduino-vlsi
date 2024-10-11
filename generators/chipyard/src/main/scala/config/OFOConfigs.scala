package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{InCluster}

// --------------
// OFO (EECS 151 ASIC) Core Configs
// --------------

class OFORocketConfig
    extends Config(
      //new chipyard.harness.WithSerialTLTiedOff ++ // don't attach anything to serial-tl
        // new chipyard.config.WithBroadcastManager ++                 // Use broadcast-based coherence hub
             new testchipip.soc.WithNoScratchpads ++ // Remove subsystem scratchpads
        new testchipip.serdes.WithSerialTLMem(size = (1 << 30) * 1L) ++ // Configure the off-chip memory accessible over serial-tl as backing memory

        new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams())) ++
        new freechips.rocketchip.rocket.With1TinyCore ++                // single tiny rocket-core
      //new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
           new testchipip.soc.WithOffchipBusClient(freechips.rocketchip.subsystem.MBUS) ++ // off-chip bus connects to MBUS to provide backing memory
        new chipyard.config.WithBroadcastManager ++                 // Use broadcast-based coherence hub
        new testchipip.soc.WithOffchipBus ++ // Attach off-chip bus
        new chipyard.config.WithBroadcastManager ++ // Replace L2 with a broadcast hub for coherence

        new chipyard.config.AbstractConfig
    )
