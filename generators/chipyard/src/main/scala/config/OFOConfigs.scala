package chipyard

import org.chipsalliance.cde.config.{Config}
import freechips.rocketchip.subsystem.{InCluster}

// --------------
// OFO (EECS 151 ASIC) Core Configs
// --------------

class OFORocketConfig
    extends Config(
      new ofo.WithOFOCores(Seq(ofo.OneFiftyOneCoreParams())) ++
  new chipyard.harness.WithDontTouchChipTopPorts(false) ++        // TODO FIX: Don't dontTouch the ports
  new testchipip.soc.WithNoScratchpads ++                         // All memory is the Rocket TCMs
  new freechips.rocketchip.subsystem.WithIncoherentBusTopology ++ // use incoherent bus topology
  new freechips.rocketchip.subsystem.WithNBanks(0) ++             // remove L2$
  new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
      new freechips.rocketchip.rocket.With1TinyCore ++                // single tiny rocket-core
      //new chipyard.harness.WithSerialTLTiedOff ++ // don't attach anything to serial-tl
        // new chipyard.config.WithBroadcastManager ++                 // Use broadcast-based coherence hub
        /*
             new testchipip.soc.WithNoScratchpads ++ // Remove subsystem scratchpads

      //new freechips.rocketchip.subsystem.WithNoMemPort ++             // remove backing memory
        new testchipip.soc.WithOffchipBusClient(freechips.rocketchip.subsystem.MBUS) ++ // off-chip bus connects to MBUS to provide backing memory
        new chipyard.config.WithBroadcastManager ++                 // Use broadcast-based coherence hub
        new testchipip.soc.WithOffchipBus ++ // Attach off-chip bus
        new chipyard.config.WithBroadcastManager ++ // Replace L2 with a broadcast hub for coherence
        */

        new chipyard.config.AbstractConfig
    )
