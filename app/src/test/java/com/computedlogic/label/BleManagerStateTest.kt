package com.computedlogic.label

import com.computedlogic.label.ble.BleManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM tests for [BleManager.State].
 * Verifies the sealed-class hierarchy: types, data fields, equality.
 *
 * Note: PrintSuccess and Connected require a BluetoothDevice which needs
 * Robolectric, so those are tested in the Robolectric suite. Here we only
 * test the states that are pure JVM objects/data classes.
 */
class BleManagerStateTest {

    // ── Object singletons ─────────────────────────────────────────────────────

    @Test
    fun `Idle is equal to itself`() {
        assertEquals(BleManager.State.Idle, BleManager.State.Idle)
    }

    @Test
    fun `Scanning is equal to itself`() {
        assertEquals(BleManager.State.Scanning, BleManager.State.Scanning)
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    @Test
    fun `Printing holds progress value`() {
        val state = BleManager.State.Printing(42)
        assertEquals(42, state.progress)
    }

    @Test
    fun `Printing(0) and Printing(100) are distinct`() {
        assertNotEquals(BleManager.State.Printing(0), BleManager.State.Printing(100))
    }

    @Test
    fun `Printing with same value is equal`() {
        assertEquals(BleManager.State.Printing(75), BleManager.State.Printing(75))
    }

    @Test
    fun `Printing progress range 0 to 100`() {
        val p0   = BleManager.State.Printing(0)
        val p100 = BleManager.State.Printing(100)
        assertEquals(0,   p0.progress)
        assertEquals(100, p100.progress)
    }

    @Test
    fun `Error holds message`() {
        val msg   = "Characteristic not found"
        val state = BleManager.State.Error(msg)
        assertEquals(msg, state.message)
    }

    @Test
    fun `Error with same message is equal`() {
        assertEquals(BleManager.State.Error("x"), BleManager.State.Error("x"))
    }

    @Test
    fun `Error with different messages is not equal`() {
        assertNotEquals(BleManager.State.Error("a"), BleManager.State.Error("b"))
    }

    @Test
    fun `DevicesFound holds empty list`() {
        val state = BleManager.State.DevicesFound(emptyList())
        assertTrue(state.devices.isEmpty())
    }

    @Test
    fun `DevicesFound with same list is equal`() {
        assertEquals(
            BleManager.State.DevicesFound(emptyList()),
            BleManager.State.DevicesFound(emptyList())
        )
    }

    // ── Cross-type inequality ─────────────────────────────────────────────────

    @Test
    fun `Idle and Scanning are different types`() {
        assertFalse(BleManager.State.Idle == BleManager.State.Scanning)
    }

    @Test
    fun `Idle and Error are different types`() {
        assertFalse(BleManager.State.Idle == BleManager.State.Error(""))
    }
}


