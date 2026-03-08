package com.akocis.babysleeptracker.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.akocis.babysleeptracker.model.NoiseType
import kotlin.math.min
import kotlin.random.Random

class NoiseGenerator {

    private var audioTrack: AudioTrack? = null
    private var generationThread: Thread? = null
    @Volatile private var isRunning = false
    @Volatile private var targetVolume = 1.0f
    @Volatile private var isFadingOut = false

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val BUFFER_SIZE_SAMPLES = 4096
    }

    fun start(noiseType: NoiseType, volume: Float, fadeInSeconds: Float) {
        stop()

        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(BUFFER_SIZE_SAMPLES * 2)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        targetVolume = volume
        isFadingOut = false
        isRunning = true
        audioTrack?.play()

        generationThread = Thread {
            val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
            val fadeInSamples = (fadeInSeconds * SAMPLE_RATE).toInt()
            var samplesGenerated = 0L

            // Pink noise state (Voss-McCartney)
            val pinkRows = IntArray(16)
            var pinkRunningSum = 0
            var pinkIndex = 0

            // Brown noise state
            var brownValue = 0.0

            // Blue/Violet state (differentiated white/blue)
            var prevWhite = 0f
            var prevBlue = 0f

            // Gray noise state (shaped white noise for perceptual uniformity)
            var grayPrev1 = 0f
            var grayPrev2 = 0f

            while (isRunning) {
                val currentVolume = targetVolume
                for (i in buffer.indices) {
                    val sample = when (noiseType) {
                        NoiseType.WHITE -> Random.nextFloat() * 2f - 1f
                        NoiseType.PINK -> {
                            pinkIndex = (pinkIndex + 1) % (1 shl 16)
                            var newIndex = pinkIndex
                            for (row in 0 until min(16, pinkRows.size)) {
                                if (newIndex and 1 == 1) {
                                    pinkRunningSum -= pinkRows[row]
                                    pinkRows[row] = (Random.nextFloat() * 2f - 1f).let { (it * 1000).toInt() }
                                    pinkRunningSum += pinkRows[row]
                                    break
                                }
                                newIndex = newIndex shr 1
                            }
                            (pinkRunningSum / 1000f / 16f).coerceIn(-1f, 1f)
                        }
                        NoiseType.BROWN -> {
                            brownValue += (Random.nextFloat() * 2f - 1f) * 0.02
                            brownValue = brownValue.coerceIn(-1.0, 1.0)
                            brownValue.toFloat()
                        }
                        NoiseType.BLUE -> {
                            // Differentiated white noise (high-pass)
                            val white = Random.nextFloat() * 2f - 1f
                            val blue = (white - prevWhite).coerceIn(-1f, 1f)
                            prevWhite = white
                            blue
                        }
                        NoiseType.VIOLET -> {
                            // Double-differentiated white noise (steeper high-pass)
                            val white = Random.nextFloat() * 2f - 1f
                            val blue = white - prevWhite
                            val violet = (blue - prevBlue).coerceIn(-1f, 1f)
                            prevWhite = white
                            prevBlue = blue
                            violet * 0.5f
                        }
                        NoiseType.GRAY -> {
                            // Perceptually flat: mid-boost via simple IIR
                            val white = Random.nextFloat() * 2f - 1f
                            val shaped = (white + 0.5f * grayPrev1 - 0.25f * grayPrev2)
                                .coerceIn(-1f, 1f)
                            grayPrev2 = grayPrev1
                            grayPrev1 = white
                            shaped * 0.7f
                        }
                    }

                    // Apply fade-in gain
                    val totalSample = samplesGenerated + i
                    val fadeGain = if (fadeInSamples > 0 && totalSample < fadeInSamples) {
                        totalSample.toFloat() / fadeInSamples
                    } else {
                        1f
                    }

                    buffer[i] = (sample * currentVolume * fadeGain * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                samplesGenerated += buffer.size

                val track = audioTrack ?: break
                if (isRunning) {
                    track.write(buffer, 0, buffer.size)
                }
            }
        }.also { it.start() }
    }

    fun setVolume(volume: Float) {
        targetVolume = volume
    }

    fun fadeOutAndStop(fadeOutSeconds: Float, onComplete: () -> Unit) {
        if (isFadingOut) return
        isFadingOut = true

        Thread {
            val steps = 20
            val stepDelay = ((fadeOutSeconds * 1000) / steps).toLong().coerceAtLeast(10)
            val startVolume = targetVolume
            for (i in 1..steps) {
                if (!isRunning) break
                targetVolume = startVolume * (1f - i.toFloat() / steps)
                Thread.sleep(stepDelay)
            }
            stop()
            onComplete()
        }.start()
    }

    fun stop() {
        isRunning = false
        generationThread?.join(1000)
        generationThread = null
        try {
            audioTrack?.stop()
        } catch (_: IllegalStateException) { }
        audioTrack?.release()
        audioTrack = null
        isFadingOut = false
    }
}
