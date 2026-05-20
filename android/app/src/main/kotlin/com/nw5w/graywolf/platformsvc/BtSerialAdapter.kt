package com.nw5w.graywolf.platformsvc

import android.util.Log
import com.nw5w.graywolf.platformproto.BondedBtDevicesResponse
import com.nw5w.graywolf.platformproto.PlatformMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * BtSerialAdapter handles Bluetooth-classic SPP/RFCOMM byte relay for the
 * platform service. All BluetoothAdapter / RFCOMM calls run on the
 * worker dispatcher; never the main thread.
 *
 * sendMessage is the callback the PlatformServer wires up to push frames
 * back to the connected Go client.
 */
class BtSerialAdapter(
    private val facade: BluetoothFacade,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sendMessage: (PlatformMessage) -> Unit,
) {
    private val tag = "BtSerialAdapter"
    private val scope = CoroutineScope(SupervisorJob() + workerDispatcher)

    fun handleBondedRequest() {
        scope.launch {
            val devices = try {
                facade.bondedDevices()
            } catch (sec: SecurityException) {
                Log.w(tag, "BLUETOOTH_CONNECT permission missing", sec)
                emptyList()
            }
            val resp = BondedBtDevicesResponse.newBuilder().apply {
                devices.forEach {
                    addDevices(
                        BondedBtDevicesResponse.Device.newBuilder()
                            .setMac(it.mac)
                            .setName(it.name)
                            .build()
                    )
                }
            }.build()
            sendMessage(
                PlatformMessage.newBuilder()
                    .setBondedBtDevicesResponse(resp)
                    .build()
            )
        }
    }

    fun shutdown() {
        // tear down all handles (filled in Task 2.4)
        scope.coroutineContext[Job]?.cancel()
    }
}
