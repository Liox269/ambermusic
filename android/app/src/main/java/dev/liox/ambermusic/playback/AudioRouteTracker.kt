package dev.liox.ambermusic.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioRouteTracker(context: Context) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val _route = MutableStateFlow(AudioRoute("Altavoz del teléfono", false))
    val route: StateFlow<AudioRoute> = _route

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    init {
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        val outputs = if (Build.VERSION.SDK_INT >= 33) {
            manager.getAudioDevicesForAttributes(attributes)
        } else {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }
        val selected = outputs.firstOrNull { isBluetooth(it.type) }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?: outputs.firstOrNull()
        val bt = selected != null && isBluetooth(selected.type)
        val defaultName = when {
            bt -> "Auriculares Bluetooth"
            selected?.type == AudioDeviceInfo.TYPE_USB_HEADSET -> "Auriculares USB"
            selected?.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                selected?.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con cable"
            else -> "Altavoz del teléfono"
        }
        val reported = selected?.productName?.toString()?.takeIf {
            it.isNotBlank() && !it.equals("unknown", ignoreCase = true) &&
                selected.type != AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        _route.value = AudioRoute(reported ?: defaultName, bt, Build.VERSION.SDK_INT >= 33)
    }

    private fun isBluetooth(type: Int): Boolean = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
        type == AudioDeviceInfo.TYPE_BLE_SPEAKER

    fun close() = manager.unregisterAudioDeviceCallback(callback)
}

data class AudioRoute(val name: String, val bluetooth: Boolean, val routeConfirmed: Boolean = false)
