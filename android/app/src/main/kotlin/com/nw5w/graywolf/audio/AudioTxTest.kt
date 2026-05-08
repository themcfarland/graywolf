package com.nw5w.graywolf.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One-shot AudioTrack TX. Opens, writes, plays, blocks until the playback
 * head reaches the buffer end, then releases. Each call allocates a fresh
 * AudioTrack so the trigger can fire repeatedly with no carry-over state.
 *
 * Spec: PCM16, mono, 22050 Hz, MODE_STATIC, USAGE_MEDIA + CONTENT_TYPE_MUSIC.
 * If MODE_STATIC rejects the buffer size or the HAL won't route to USB-Audio,
 * fall back to MODE_STREAM with blocking writes; document in the run report.
 *
 * Single-flight: a rapid double-tap on the trigger button must not stack two
 * AudioTracks (would either contend for the output device or leak one track).
 * `inFlight` gates concurrent calls; overlapping callers get `false` and a
 * warn log.
 */
object AudioTxTest {
    private const val TAG = "AudioTxTest"
    private const val SAMPLE_RATE_HZ = 22_050
    private val inFlight = AtomicBoolean(false)

    /**
     * Play [samples] synchronously. Returns true if the buffer played to
     * completion, false otherwise (including: empty samples, builder failed,
     * write short, deadline reached without playback head catching up, or
     * a previous call is still running).
     *
     * Caller is expected to invoke from a background thread; this method
     * blocks ~3 s during playback.
     */
    fun fireOnce(samples: ShortArray): Boolean {
        if (samples.isEmpty()) {
            Log.w(TAG, "fireOnce called with empty samples")
            return false
        }
        if (!inFlight.compareAndSet(false, true)) {
            Log.w(TAG, "fireOnce rejected: previous call still in flight")
            return false
        }
        return try {
            playOnce(samples)
        } finally {
            inFlight.set(false)
        }
    }

    private fun playOnce(samples: ShortArray): Boolean {
        val bufferBytes = samples.size * 2 // PCM16
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE_HZ)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        } catch (t: Throwable) {
            Log.e(TAG, "AudioTrack.Builder failed (MODE_STATIC, $bufferBytes B): $t")
            return false
        }

        // MODE_STATIC: post-build state is STATE_NO_STATIC_DATA; the track
        // becomes STATE_INITIALIZED only after data is written. STATE_UNINITIALIZED
        // is the only failure mode here.
        if (track.state == AudioTrack.STATE_UNINITIALIZED) {
            Log.e(TAG, "AudioTrack uninitialized after build; releasing")
            track.release()
            return false
        }

        Log.i(TAG, "AudioTrack built: rate=$SAMPLE_RATE_HZ samples=${samples.size} bufferBytes=$bufferBytes state=${track.state}")

        val written = track.write(samples, 0, samples.size)
        if (written != samples.size) {
            Log.e(TAG, "AudioTrack.write returned $written, expected ${samples.size}")
            track.release()
            return false
        }
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            Log.e(TAG, "AudioTrack state=${track.state} after write; expected INITIALIZED")
            track.release()
            return false
        }

        track.play()

        // Block until the playback head reaches the buffer end. AudioTrack
        // playbackHeadPosition is in frames; for mono PCM that equals
        // sample count. Poll at a coarse interval — we're not measuring
        // latency here, just confirming completion before logging done.
        val totalFrames = samples.size
        val deadlineMs = System.currentTimeMillis() + (totalFrames * 1000L / SAMPLE_RATE_HZ) + 1500L
        var played = 0
        while (System.currentTimeMillis() < deadlineMs) {
            played = track.playbackHeadPosition
            if (played >= totalFrames) break
            Thread.sleep(50)
        }
        val completed = played >= totalFrames
        if (!completed) {
            Log.w(TAG, "playback head=$played did not reach $totalFrames before deadline")
        } else {
            Log.i(TAG, "playback complete: head=$played frames")
        }

        try { track.stop() } catch (_: Throwable) {}
        track.release()
        return completed
    }
}
