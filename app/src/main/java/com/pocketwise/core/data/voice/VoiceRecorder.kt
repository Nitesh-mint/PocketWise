package com.pocketwise.core.data.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Records a short voice note for Gemini to hear directly, so any language (or a
 * mix) works — no on-device recognizer locked to one language in between.
 * Stops on its own once the speaker goes quiet.
 */
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns the recorded AAC bytes, or null if no speech was detected.
     * Cancelling the calling coroutine stops and discards the recording.
     */
    suspend fun record(
        shouldStop: () -> Boolean,
        onProgress: (level: Float, elapsedMs: Long) -> Unit
    ): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "voice_note.aac")
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioChannels(1)
            // Speech-quality mono: ~4 KB per second, tiny to upload.
            recorder.setAudioSamplingRate(16_000)
            recorder.setAudioEncodingBitRate(32_000)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()

            val startedAt = SystemClock.elapsedRealtime()
            var heardSpeech = false
            var lastLoudAt = startedAt
            while (true) {
                delay(TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val amplitude = recorder.maxAmplitude // peak since the last call, 0..32767
                if (amplitude >= SPEECH_AMPLITUDE) {
                    heardSpeech = true
                    lastLoudAt = now
                }
                onProgress((amplitude / LEVEL_FULL_SCALE).coerceIn(0f, 1f), now - startedAt)

                val done = shouldStop() ||
                    (heardSpeech && now - lastLoudAt >= END_SILENCE_MS) ||
                    (!heardSpeech && now - startedAt >= NO_SPEECH_TIMEOUT_MS) ||
                    now - startedAt >= MAX_DURATION_MS
                if (done) break
            }
            recorder.stop()
            if (heardSpeech) file.readBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // prepare()/start() fail when another app holds the mic; stop() fails on an empty recording.
            throw VoiceParseException("Couldn't use the microphone. Try again.")
        } finally {
            recorder.release()
            file.delete() // voice notes are financial data — never kept on disk
        }
    }

    companion object {
        const val MIME_TYPE = "audio/aac"

        private const val TICK_MS = 100L
        // Calibration knob: MediaRecorder peak amplitude that counts as speech.
        // Raise it if a noisy room never auto-stops; lower it if quiet speech isn't picked up.
        private const val SPEECH_AMPLITUDE = 2_500
        private const val END_SILENCE_MS = 1_200L
        private const val NO_SPEECH_TIMEOUT_MS = 6_000L
        private const val MAX_DURATION_MS = 20_000L
        // Normal speech peaks well below 32767; scale so the mic animation visibly moves.
        private const val LEVEL_FULL_SCALE = 12_000f
    }
}
