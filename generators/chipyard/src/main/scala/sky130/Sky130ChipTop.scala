package chipyard.sky130

import chipyard.ChipTop
import chipyard.harness.BuildTop
import org.chipsalliance.cde.config.{Config, Parameters}

class Sky130ChipTop(implicit p: Parameters) extends ChipTop()(p)
  with HasSky130EFCaravelPOR
  with HasSky130EFIOCells
  with HasSky130EFIONoConnCells {
  // Copied from example.CustomChipTop:
  // making the module name ChipTop instead of Sky130ChipTop means
  // we don't have to set the TOP make variable to Sky130ChipTop
  override lazy val desiredName = "Sky130ChipTop"
}

class WithSky130ChipTop extends Config((site, here, up) => {
  case BuildTop => p: Parameters => new Sky130ChipTop()(p)
})
