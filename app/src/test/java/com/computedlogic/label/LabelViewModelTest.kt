package com.computedlogic.label

import com.computedlogic.label.ble.BleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests for [LabelViewModel].
 *
 * Robolectric provides the application context via [RuntimeEnvironment.getApplication].
 * A [StandardTestDispatcher] is installed so coroutine launches are
 * deterministic – no real BLE hardware or I/O happens in these tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LabelViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var vm: LabelViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        vm = LabelViewModel(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial BLE state is Idle`() {
        assertEquals(BleManager.State.Idle, vm.bleState.value)
    }

    @Test
    fun `bleManager is not null after init`() {
        assertNotNull(vm.bleManager)
    }

    // ── PrintParams wiring ────────────────────────────────────────────────────
    // Verify that the UI-state → PrintParams mapping is self-consistent.
    // (The ViewModel builds params in the screen; we test the data class here.)

    @Test
    fun `PrintParams with all defaults has expected labelLengthPx`() {
        val params = buildTestParams()
        assertEquals(200, params.labelLengthPx)
    }

    @Test
    fun `PrintParams copies field is transmitted correctly`() {
        val params = buildTestParams(copies = 3)
        assertEquals(3, params.copies)
    }

    @Test
    fun `PrintParams text field is transmitted correctly`() {
        val params = buildTestParams(text = "Robolectric label")
        assertEquals("Robolectric label", params.text)
    }

    @Test
    fun `PrintParams alignment fields are transmitted correctly`() {
        val params = buildTestParams(hAlign = HAlign.RIGHT, vAlign = VAlign.BOTTOM)
        assertEquals(HAlign.RIGHT,  params.hAlign)
        assertEquals(VAlign.BOTTOM, params.vAlign)
    }

    @Test
    fun `PrintParams padding fields are transmitted correctly`() {
        val params = buildTestParams(padTop = 1, padBottom = 2, padLeft = 3, padRight = 4)
        assertEquals(1, params.paddingTopMm)
        assertEquals(2, params.paddingBottomMm)
        assertEquals(3, params.paddingLeftMm)
        assertEquals(4, params.paddingRightMm)
    }

    @Test
    fun `PrintParams style flags are transmitted correctly`() {
        val params = buildTestParams(bold = true, italic = false, underline = true)
        assertTrue(params.isBold)
        assertFalse(params.isItalic)
        assertTrue(params.isUnderline)
    }

    // ── BLE state after operations ────────────────────────────────────────────

    @Test
    fun `calling disconnect on fresh vm keeps state Idle`() {
        vm.bleManager.disconnect()
        assertEquals(BleManager.State.Idle, vm.bleState.value)
    }

    @Test
    fun `stopScan on fresh vm does not throw`() {
        vm.stopScan()   // no-op when not scanning; must not crash
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTestParams(
        text: String       = "Test",
        fontSize: Int      = 48,
        bold: Boolean      = false,
        italic: Boolean    = false,
        underline: Boolean = false,
        hAlign: HAlign     = HAlign.LEFT,
        vAlign: VAlign     = VAlign.TOP,
        padTop: Int = 0, padBottom: Int = 0, padLeft: Int = 0, padRight: Int = 0,
        copies: Int = 1,
    ) = PrintParams(
        text            = text,
        fontSize        = fontSize,
        isBold          = bold,
        isItalic        = italic,
        isUnderline     = underline,
        hAlign          = hAlign,
        vAlign          = vAlign,
        paddingTopMm    = padTop,
        paddingBottomMm = padBottom,
        paddingLeftMm   = padLeft,
        paddingRightMm  = padRight,
        copies          = copies,
    )
}


