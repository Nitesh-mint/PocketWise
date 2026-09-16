package com.pocketwise.core.util

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * The mic start/stop chime, in the style of Google's voice input: two short,
 * clean sine notes (rising for start, falling for stop) with a soft fade
 * envelope so they read as a gentle "boop" rather than a telephone beep.
 * Synthesized on the fly — no bundled audio asset needed.
 */
object VoiceChime {
    private const val SAMPLE_RATE = 44_100
    private const val NOTE_MS = 70
    private const val GAP_MS = 15

    suspend fun playStart() = play(660f, 880f)
    suspend fun playStop() = play(880f, 660f)

    private suspend fun play(firstHz: Float, secondHz: Float) = withContext(Dispatchers.Default) {
        val samples = tone(firstHz) + ShortArray(SAMPLE_RATE * GAP_MS / 1000) + tone(secondHz)
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            samples.size * 2,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        track.write(samples, 0, samples.size)
        track.play()
        delay((NOTE_MS * 2 + GAP_MS + 30).toLong())
        track.release()
    }

    // A short sine burst with a linear fade in/out — the fade is what keeps
    // the tone from clicking/popping at its edges.
    private fun tone(hz: Float): ShortArray {
        val n = SAMPLE_RATE * NOTE_MS / 1000
        val fade = (n * 0.15f).toInt().coerceAtLeast(1)
        return ShortArray(n) { i ->
            val raw = sin(2.0 * PI * hz * i / SAMPLE_RATE).toFloat()
            val envelope = minOf(i.toFloat() / fade, (n - i).toFloat() / fade, 1f).coerceIn(0f, 1f)
            (raw * envelope * 0.5f * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
