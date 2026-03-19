package com.akocis.babysleeptracker.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.sqrt

data class TelemetryData(
    val noiseDb: Double? = null,
    val noiseAvailable: Boolean = false,
    val temperatureC: Float? = null,
    val temperatureAvailable: Boolean = false,
    val humidityPercent: Float? = null,
    val humidityAvailable: Boolean = false,
    val pressureHpa: Float? = null,
    val pressureAvailable: Boolean = false
)

class TelemetryManager(private val context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _data = MutableStateFlow(TelemetryData())
    val data: StateFlow<TelemetryData> = _data

    private var audioRecord: AudioRecord? = null
    private var noiseJob: Job? = null
    private var sensorListener: SensorEventListener? = null

    private val hasTemperatureSensor: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE) != null

    private val hasHumiditySensor: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY) != null

    private val hasPressureSensor: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    private val hasAudioPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    fun checkAvailability(): TelemetryData {
        return TelemetryData(
            noiseAvailable = hasAudioPermission,
            temperatureAvailable = hasTemperatureSensor,
            humidityAvailable = hasHumiditySensor,
            pressureAvailable = hasPressureSensor
        )
    }

    suspend fun start() = coroutineScope {
        val availability = checkAvailability()
        _data.value = availability

        // Start environment sensors
        startSensors()

        // Start noise measurement
        if (availability.noiseAvailable) {
            noiseJob = launch(Dispatchers.Default) {
                measureNoise()
            }
        }
    }

    fun stop() {
        noiseJob?.cancel()
        noiseJob = null
        stopAudioRecord()
        stopSensors()
        _data.value = TelemetryData()
    }

    private fun startSensors() {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val current = _data.value
                _data.value = when (event.sensor.type) {
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> current.copy(
                        temperatureC = event.values[0],
                        temperatureAvailable = true
                    )
                    Sensor.TYPE_RELATIVE_HUMIDITY -> current.copy(
                        humidityPercent = event.values[0],
                        humidityAvailable = true
                    )
                    Sensor.TYPE_PRESSURE -> current.copy(
                        pressureHpa = event.values[0],
                        pressureAvailable = true
                    )
                    else -> current
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorListener = listener

        sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun stopSensors() {
        sensorListener?.let { sensorManager.unregisterListener(it) }
        sensorListener = null
    }

    private suspend fun measureNoise() {
        val sampleRate = 8000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) return

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: SecurityException) {
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return
        }

        audioRecord = recorder
        recorder.startRecording()

        val buffer = ShortArray(bufferSize / 2)
        try {
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val rms = calculateRms(buffer, read)
                    val db = if (rms > 0) 20.0 * log10(rms / Short.MAX_VALUE) + 90.0 else 0.0
                    _data.value = _data.value.copy(
                        noiseDb = db.coerceIn(0.0, 120.0),
                        noiseAvailable = true
                    )
                }
                delay(1000)
            }
        } finally {
            stopAudioRecord()
        }
    }

    private fun stopAudioRecord() {
        audioRecord?.let { rec ->
            try {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            } catch (_: Exception) { }
        }
        audioRecord = null
    }

    private fun calculateRms(buffer: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / count)
    }
}
