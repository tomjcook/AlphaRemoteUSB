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
                            /*
                            device?.getInterface(0)?.also { intf ->
                                intf.getEndpoint(0)?.also { endpoint ->
                                    usbManager.openDevice(device)?.apply {
                                        claimInterface(intf, true)
                                        bulkTransfer(endpoint, bytes, bytes.size, 0) //do in another thread
                                    }
                                }
                            }
                            */
                            Toast.makeText(context, serialNumber, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.d(TAG, "permission denied for device $device")
                    }
                }
            }
        }
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