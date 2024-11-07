package chipyard.clocking
import scala.collection.mutable


import chisel3._
import chisel3.util._
import chipyard.iobinders._
import freechips.rocketchip.prci._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink._
import chipyard.iocell._

// Broadcasts a single clock IO to all clock domains. Ignores all requested frequencies. adds custom io binder logic to WithSingleClockBroadcastClockGenerator
class WithPureIOClockSky130(freqMHz: Int = 100) extends OverrideLazyIOBinder({
  (system: HasChipyardPRCI) => {
    implicit val p = GetSystemParameters(system)

    val clockGroupsAggregator = LazyModule(new ClockGroupAggregator("single_clock"))
    val clockGroupsSourceNode = ClockGroupSourceNode(Seq(ClockGroupSourceParameters()))
    system.chiptopClockGroupsNode :*= clockGroupsAggregator.node := clockGroupsSourceNode

    InModuleBody {
      val binderNodes = mutable.Buffer[Data]()
      val binderCells = mutable.Buffer[IOCell]()

      def addIOCell(data: (Data, Seq[IOCell])): Unit = {
        binderNodes += data._1
        binderCells ++= data._2
      }

      def generateIO(
        coreSignal: Data,
        name: String,
        abstractResetAsAsync: Boolean = false
      ): Unit =
        addIOCell(IOCell.generateIOFromSignal(
        coreSignal,
        name,
        p(IOCellKey),
        abstractResetAsAsync = abstractResetAsAsync)
      )

      val clock_wire = Wire(Input(Clock()))
      val reset_wire = Wire(Input(AsyncReset()))
      val (clock_io, clockIOCell) = IOCell.generateIOFromSignal(clock_wire, "clock", p(IOCellKey))
      val (reset_io, resetIOCell) = IOCell.generateIOFromSignal(reset_wire, "reset", p(IOCellKey))

      clockGroupsSourceNode.out.foreach { case (bundle, edge) =>
        bundle.member.data.foreach { b =>
          b.clock := clock_io
          b.reset := reset_io
        }
      }
      (Seq(ClockPort(() => clock_io, freqMHz), ResetPort(() => reset_io)), clockIOCell ++ resetIOCell)
    }
  }
})

