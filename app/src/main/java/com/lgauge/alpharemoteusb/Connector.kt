package com.lgauge.alpharemoteusb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbManager
import android.util.Log
import androidx.core.content.ContextCompat

class Connector(private val activity: Context) {

    private val usbPermissionAction = "com.lgauge.alpharemoteusb.USB_PERMISSION"
    private var onConnectedCallback: ((result: FUsbDevice) -> Unit)? = null
    private var onErrorCallback: ((result: String) -> Unit)? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == usbPermissionAction) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            //call method to set up device communication
                            setupConnection(this)
                        }
                    } else {
                        onErrorCallback?.invoke("permission denied")
                    }
                }
            }
        }
    }

    fun connect(device: UsbDevice) {
        val mUsbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        val mPermissionIntent =
            PendingIntent.getBroadcast(
                activity,0, Intent(usbPermissionAction).setPackage(activity.packageName), PendingIntent.FLAG_MUTABLE
            )
        ContextCompat.registerReceiver(activity, usbReceiver, IntentFilter(usbPermissionAction), ContextCompat.RECEIVER_EXPORTED)

        mUsbManager.requestPermission(device, mPermissionIntent)
    }

    private fun setupConnection(mDevice: UsbDevice) {
        val usbIf = mDevice.getInterface(0)
        Log.d("Connector", "Interface: $usbIf")
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        //endpoint: direction out 0, urb_bulk 0x03, endpoint number 2, device address 6
        for (i in 0 until usbIf.endpointCount) {// this loop is for finding endpoint direction and type
            if (usbIf.getEndpoint(i).type == UsbConstants.USB_ENDPOINT_XFER_BULK) { //TODO custom setting
                if (usbIf.getEndpoint(i).direction == UsbConstants.USB_DIR_IN) //TODO custom setting
                //from device to host(android device is host)
                    epIn = usbIf.getEndpoint(i)
                else
                    epOut = usbIf.getEndpoint(i)
            }
        }
        epIn?.let {
            epOut?.let {
                val mUsbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
                mUsbManager.openDevice(mDevice)?.let { conn ->
                    if (conn.claimInterface(usbIf, true)) {
                        Log.d("Connector", "Endpoint in: $epIn")
                        Log.d("Connector", "Endpoint out: $epOut")
                        onConnectedCallback?.invoke(FUsbDevice(epIn, epOut, conn))
                    } else {
                        onErrorCallback?.invoke("interface could not be claimed")
                    }
                    return
                } ?: run {
                    onErrorCallback?.invoke("device could not be opened")
                    return
                }
            }
        }
        onErrorCallback?.invoke("some error: \n endpoints: ${usbIf.endpointCount} \n endpoint In: $epIn \n endpoint Out: $epOut")
    }

    fun onConnected(callback: (result: FUsbDevice) -> Unit = {}): Connector {
        onConnectedCallback = callback
        return this
    }

    fun onError(callback: (result: String) -> Unit = {}): Connector {
        onErrorCallback = callback
        return this
    }

}