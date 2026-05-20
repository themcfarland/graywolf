package com.nw5w.graywolf.platformsvc

import com.nw5w.graywolf.platformproto.PlatformMessage
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BtSerialAdapterTest {
    @Test fun bondedDevicesRequest_returnsAllPaired() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val facade = FakeBluetoothFacade(bonded = listOf(
            BondedDevice("AA:BB:CC:00:00:01", "Mobilinkd TNC4"),
            BondedDevice("AA:BB:CC:00:00:02", "TH-D75"),
        ))
        val sent = mutableListOf<PlatformMessage>()
        val adapter = BtSerialAdapter(facade, dispatcher) { sent.add(it) }

        adapter.handleBondedRequest()
        advanceUntilIdle()

        assertEquals(1, sent.size)
        val resp = sent[0].bondedBtDevicesResponse
        assertEquals(2, resp.devicesCount)
        assertEquals("Mobilinkd TNC4", resp.getDevices(0).name)
        assertTrue(resp.getDevices(0).mac == "AA:BB:CC:00:00:01")
    }
}
