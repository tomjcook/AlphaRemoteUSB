package com.lgauge.alpharemoteusb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class FUsbDevice(private val epIn: UsbEndpoint, private val epOut: UsbEndpoint, private val connection: UsbDeviceConnection) {

    private var transferred = 0

    suspend fun sendData(inData: Int, data: ByteArray, sendTimeout: Int, receiveTimeout: Int, endIdentifier: ByteArray?, onTransferred: () -> Unit = {}, onResponse: (result: Response) -> Unit = {}) {
        //TODO in thread?

        withContext(Dispatchers.Default) {

            Log.d("FUsbDevice", "Transferring: ${data.map { it.toUByte() }}")
            transferred = connection.bulkTransfer(epOut, data, data.size, sendTimeout)
            onTransferred.invoke()

            //transferred < 0 is failure
            if (inData > 0) { //only wait for data if user wants
                waitForResponse(inData, receiveTimeout, endIdentifier) {
                    launch(Dispatchers.Main) {
                        onResponse.invoke(it)
                    }
                }
            } else {
                launch(Dispatchers.Main) {
                    onResponse.invoke(Response("ok", transferred, ByteArray(0)));
                }
            }

        }


    }

    private fun waitForResponse(inData: Int, receiveTimeout: Int, endIdentifier: ByteArray?, callback: (result: Response) -> Unit = {}) {
        val long = System.currentTimeMillis()
        println("start wait for response")
        var response = false
        val inb = ByteBuffer.allocate(inData).order(ByteOrder.LITTLE_ENDIAN)
        var res = 0
        var transfer = true
        while (!response) {
            inb.position(0)
            while (transfer) { //until result is -1
                println("loop start bulk $res")
                val ress = connection.bulkTransfer(epIn, inb.array(), inData - res, receiveTimeout)

                endIdentifier?.let {arr ->
                    val index = Collections.indexOfSubList(inb.array().toList() , arr.toList())
                    if (index != -1){
                        res += ress
                        println("loop end bulk $ress and $res because contains end identifier at $index")
                        transfer = false
                    }
                }

                if (ress == -1) {
                    transfer = false
                } else {
                    res += ress
                }
                println("loop end bulk $ress and $res")
            }
            if (res >= 0) {
                response = true
            }
        }
        println("finished response after ${System.currentTimeMillis() - long} millis")

        callback.invoke(Response("ok", transferred, inb.array().copyOf(res)))
    }


}