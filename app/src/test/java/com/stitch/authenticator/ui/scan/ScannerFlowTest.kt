package com.stitch.authenticator.ui.scan

import com.stitch.authenticator.AddAccount
import com.stitch.authenticator.enterKeyInsteadDestination
import com.stitch.authenticator.ui.add.AddAccountMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The permission-path contract behind the scanner screen: the states that decide whether the
 * viewfinder, the waiting spinner, or the rationale — the surface carrying "Enter key instead" —
 * is on screen, and where that button hands off.
 *
 * The back-stack swap itself (`removeLastOrNull` + push) is nav3 wiring and needs an
 * instrumented harness; everything decided *before* the swap is pinned here.
 */
class ScannerFlowTest {

  @Test
  fun `granted shows the viewfinder regardless of whether the answer was recorded`() {
    assertEquals(ScannerStage.Viewport, scannerStage(granted = true, asked = false))
    assertEquals(ScannerStage.Viewport, scannerStage(granted = true, asked = true))
  }

  @Test
  fun `an unanswered prompt waits instead of showing the rationale`() {
    assertEquals(ScannerStage.AwaitingPermission, scannerStage(granted = false, asked = false))
  }

  @Test
  fun `a refused camera lands on the rationale - the Enter key instead path`() {
    assertEquals(ScannerStage.Rationale, scannerStage(granted = false, asked = true))
  }

  @Test
  fun `enter key instead hands off to the manual form - never the link form`() {
    assertEquals(AddAccount(mode = AddAccountMode.Manual), enterKeyInsteadDestination())
  }
}
