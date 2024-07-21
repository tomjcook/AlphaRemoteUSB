package com.lgauge.alpharemoteusb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lgauge.alpharemoteusb.ui.theme.AlphaRemoteUSBTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val TAG = "MainActivityTag"
private const val ACTION_USB_PERMISSION = "com.lgauge.alpharemoteusb.USB_PERMISSION"

class MainActivity : ComponentActivity() {

    private val usbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        ContextCompat.registerReceiver(this, usbReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        Log.d(TAG, "registerReceiver")

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
                                usbManager.requestPermission(device, getUsbPermissionPendingIntent())
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

    private fun getUsbPermissionPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        this,0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), PendingIntent.FLAG_MUTABLE
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive(${intent.extras})")
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            // call method to set up device communication
                            Log.d(TAG, "Permission granted! $device")

                            device?.getInterface(0)?.also { usbInterface ->
                                Log.d(TAG, "usbInterface: $usbInterface")

                                usbInterface.getEndpoint(1)?.also { endpoint ->
                                    Log.d(TAG, "endpoint: $endpoint")

                                    usbManager.openDevice(device)?.apply {
                                        claimInterface(usbInterface, true)
                                        //Log.d(TAG, "endpoint: $endpoint")

                                        for (byte in byteArrayOf(0x01, 0x02)) {
                                            val handshake = handshake(byte)
                                            Log.d(TAG, "Handshake bytes: ${handshake.contentToString()}")
                                            bulkTransfer(endpoint, handshake, handshake.size, 0)
                                        }

                                        val bytes = doSetting(OpCodes.MAIN_SETTING, SettingIds.HALF_PRESS_SHUTTER, 2, 0, 2, 0)
                                        Log.d(TAG, "Setting bytes: ${bytes.contentToString()}")
                                        bulkTransfer(endpoint, bytes, bytes.size, 0) //do in another thread
                                        //Toast.makeText(context, serialNumber, Toast.LENGTH_SHORT).show()
                                        /*
                                        if (!SimpleSend(OpCodes.Connect, "00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03 00 00 00 03 00 00 00") ||
                                            !SimpleSend(OpCodes.Connect, "00 00 00 00 00 00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03 00 00 00 03 00 00 00"))
                                        {
                                            return false;
                                        }
                                        */

                                        //val connect = 0x9201
                                        //val bytes = "00 00 00 00 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 03 00 00 00 03 00 00 00"
                                        //bulkTransfer(endpoint, bytes, bytes.size, 0)

                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "permission denied for device $device")
                    }
                }
            }
        }
    }

    enum class OpCodes(val code: Int) {
        CONNECT(0x9201),
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

    fun doSetting(
        opcode: OpCodes,
        id: SettingIds,
        value1: Int,
        value2: Int,
        value1DataSize: Int,
        value2DataSize: Int)
    : ByteArray
    {
        Toast.makeText(this, "Building Message", Toast.LENGTH_SHORT).show()

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

/*
        using (Packet request = new Packet(opcode))
        {
            request.WriteHexString("00 00 00 00 00 00 00 00");
            request.WriteUInt16((ushort)id);
            request.WriteHexString("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 04 00 00 00");
            for (int i = 0; i < 2; i++)
            {
                var dataSize = i == 0 ? value1DataSize : value2DataSize;
                int data = i == 0 ? value1 : value2;
                switch (dataSize)
                {
                    case 1:
                    request.WriteByte((byte)data);
                    break;
                    case 2:
                    request.WriteInt16((short)data);
                    break;
                    case 4:
                    request.WriteInt32(data);
                    break;
                }
            }
            byte[] buffer = SendCommand(request.GetBuffer());
            using (Packet response = Packet.Reader(buffer))
            {
                if (!IsValidResponse(response))
                {
                    return false;
                }
                return true;
            }
        }
*/
    }
}

@Composable
fun Devices(
    deviceList: HashMap<String, UsbDevice>,
    modifier: Modifier = Modifier,
    requestPermission: (UsbDevice) -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(deviceList.entries.toList()) { (name, device) ->
            Card ( onClick = {
                //Toast.makeText(context, device.serialNumber, Toast.LENGTH_SHORT).show()
                requestPermission(device)
            },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)) {
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