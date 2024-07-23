package com.lgauge.alpharemoteusb

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.lgauge.alpharemoteusb.ui.theme.AlphaRemoteUSBTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "MainActivityTag"

class MainActivity : ComponentActivity() {

    private val usbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            AlphaRemoteUSBTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(1.dp)) {
                        Greeting(
                            name = "Android",
                            modifier = Modifier.align(Alignment.TopCenter)
                        )

                        Devices(
                            usbManager.deviceList,
                            requestPermission = { device ->
                                val connector = Connector(this@MainActivity)
                                connector.onError {
                                    Log.d(TAG, "Connection error: $it")
                                }
                                connector.onConnected { fdevice ->
                                    lifecycleScope.launch {
                                        withContext(Dispatchers.IO) {
                                            Log.d(TAG, "Sending handshake")
                                            fdevice.sendData(0, handshake(0x01), 1000, 1000, null)
                                            fdevice.sendData(0, handshake(0x02), 1000, 1000, null)
                                            fdevice.sendData(0, requestSettings(), 1000, 1000, null)
                                            fdevice.sendData(0, handshake(0x03), 1000, 1000, null)
                                            fdevice.sendData(0, update(), 1000, 1000, null)

                                            Log.d(TAG, "Pressing...")
                                            fdevice.sendData(0, pressShutterHalf(), 1000, 1000, null)
                                            fdevice.sendData(0, pressShutterFull(), 1000, 1000, null)

                                            Log.d(TAG, "Waiting...")
                                            delay(1000)

                                            Log.d(TAG, "Releasing...")
                                            fdevice.sendData(0, releaseShutterHalf(), 1000, 1000, null)
                                            fdevice.sendData(0, releaseShutterFull(), 1000, 1000, null)

                                            Log.d(TAG, "Done")
                                        }
                                    }
                                }
                                connector.connect(device)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .border(2.dp, Color.Red)
                        )
                    }
                }
            }
        }

    }

    enum class OpCodes(val code: Int) {
        CONNECT(0x9201),
        SETTINGS_LIST(0x9202),
        SETTINGS(0x9209),
        MAIN_SETTING(0x9207)
    }
    enum class SettingIds(val code: Int) {
        HALF_PRESS_SHUTTER(0xD2C1),
        CAPTURE_PHOTO(0xD2C2)
    }

    fun createBuffer(opcode: OpCodes, capacity: Int): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(capacity)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

        byteBuffer.putShort(opcode.code.toShort())

        return byteBuffer
    }

    fun handshake(byte: Byte): ByteArray {
        val byteBuffer = createBuffer(OpCodes.CONNECT, 38)

        byteBuffer.put(10, byte)

        byteBuffer.put(30, 0x03)
        byteBuffer.put(34, 0x03)

        val bytes = ByteArray(byteBuffer.capacity())
        byteBuffer.rewind()
        byteBuffer.get(bytes)
        return bytes
    }

    fun requestSettings(): ByteArray {
        val byteBuffer = createBuffer(OpCodes.SETTINGS_LIST, 38)

        byteBuffer.put(10, 200.toByte())

        byteBuffer.put(30, 0x01)
        byteBuffer.put(34, 0x03)

        val bytes = ByteArray(byteBuffer.capacity())
        byteBuffer.rewind()
        byteBuffer.get(bytes)
        return bytes
    }

    fun update(): ByteArray {
        val byteBuffer = createBuffer(OpCodes.SETTINGS, 38)

        byteBuffer.put(34, 0x03)

        val bytes = ByteArray(byteBuffer.capacity())
        byteBuffer.rewind()
        byteBuffer.get(bytes)
        return bytes
    }

    fun pressShutterHalf(): ByteArray {
        return doSetting(OpCodes.MAIN_SETTING, SettingIds.HALF_PRESS_SHUTTER, 2, 0, 2, 0)
    }

    fun pressShutterFull(): ByteArray {
        return doSetting(OpCodes.MAIN_SETTING, SettingIds.CAPTURE_PHOTO, 2, 0, 2, 0)
    }

    fun releaseShutterHalf(): ByteArray {
        return doSetting(OpCodes.MAIN_SETTING, SettingIds.HALF_PRESS_SHUTTER, 1, 0, 2, 0)
    }

    fun releaseShutterFull(): ByteArray {
        return doSetting(OpCodes.MAIN_SETTING, SettingIds.CAPTURE_PHOTO, 1, 0, 2, 0)
    }

    fun doSetting(
        opcode: OpCodes,
        id: SettingIds,
        value1: Int,
        value2: Int,
        value1DataSize: Int,
        value2DataSize: Int)
    : ByteArray
    {
        val byteBuffer = createBuffer(opcode, 256)

        byteBuffer.put(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0))
        byteBuffer.putShort(id.code.toShort())
        byteBuffer.put(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x1, 0, 0, 0, 0x4, 0, 0, 0))

        when(value1DataSize) {
            1 -> byteBuffer.put(value1.toByte())
            2 -> byteBuffer.putShort(value1.toShort())
            4 -> byteBuffer.putInt(value1)
        }
        when(value2DataSize) {
            1 -> byteBuffer.put(value2.toByte())
            2 -> byteBuffer.putShort(value2.toShort())
            4 -> byteBuffer.putInt(value2)
        }

        val bytes = ByteArray(byteBuffer.position())
        byteBuffer.rewind()
        byteBuffer.get(bytes)
        return bytes

    }
}

@Composable
fun Devices(
    deviceList: HashMap<String, UsbDevice>,
    modifier: Modifier = Modifier,
    requestPermission: (UsbDevice) -> Unit
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(deviceList.entries.toList()) { (name, device) ->
            Card (
                onClick = {
                    requestPermission(device)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
            ) {
                Text(device.productName ?: name,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp))
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AlphaRemoteUSBTheme {
        Greeting("Android")
    }
}