package com.example.data.synth

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object AudioSynthGenerator {

    /**
     * Generates a 30-second melodic PCM WAV file with specified synth frequencies and rhythm.
     * Saved to app cache directory so Android MediaPlayer can play it offline with real audio.
     */
    fun getOrCreateDemoAudioUri(context: Context, filename: String, baseFreq: Float, isBeat: Boolean): String {
        val cacheFile = File(context.cacheDir, filename)
        if (cacheFile.exists() && cacheFile.length() > 1000) {
            return Uri.fromFile(cacheFile).toString()
        }

        try {
            val sampleRate = 22050
            val durationSeconds = 30
            val numSamples = sampleRate * durationSeconds
            val pcmData = ByteArray(numSamples * 2) // 16-bit mono

            val chords = floatArrayOf(baseFreq, baseFreq * 1.25f, baseFreq * 1.5f, baseFreq * 1.875f)

            for (i in 0 until numSamples) {
                val t = i.toDouble() / sampleRate
                // Chord progression every 3 seconds
                val chordIndex = ((t / 3).toInt()) % chords.size
                val freq = chords[chordIndex]

                // Main synth wave
                var sample = Math.sin(2.0 * Math.PI * freq * t) * 0.4

                // Harmony sub-octave
                sample += Math.sin(2.0 * Math.PI * (freq / 2) * t) * 0.25

                // Beat pulse if enabled
                if (isBeat) {
                    val beatPulse = Math.sin(2.0 * Math.PI * 2.0 * t) // 120 BPM beat
                    val kick = if ((t * 2) % 1.0 < 0.15) Math.sin(2.0 * Math.PI * 60.0 * t) * 0.5 else 0.0
                    sample = (sample * 0.6) + kick + (beatPulse * 0.15)
                }

                // Envelope fade in / fade out
                val envelope = when {
                    t < 1.0 -> t
                    t > durationSeconds - 1.0 -> durationSeconds - t
                    else -> 1.0
                }
                sample *= envelope

                // Clamp to 16-bit PCM range
                val shortVal = (sample * 32767).toInt().coerceIn(-32768, 32767)
                pcmData[i * 2] = (shortVal and 0x00FF).toByte()
                pcmData[i * 2 + 1] = ((shortVal shr 8) and 0x00FF).toByte()
            }

            FileOutputStream(cacheFile).use { out ->
                writeWavHeader(out, sampleRate, 1, numSamples * 2)
                out.write(pcmData)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Uri.fromFile(cacheFile).toString()
    }

    private fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, dataSize: Int) {
        val totalDataLen = dataSize + 36
        val byteRate = sampleRate * channels * 2

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()

        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        header[16] = 16 // 16 for PCM
        header[17] = 0
        header[18] = 0
        header[19] = 0

        header[20] = 1 // Audio format 1 = PCM
        header[21] = 0

        header[22] = channels.toByte()
        header[23] = 0

        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()

        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()

        header[32] = (channels * 2).toByte() // block align
        header[33] = 0

        header[34] = 16 // bits per sample
        header[35] = 0

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        header[40] = (dataSize and 0xff).toByte()
        header[41] = ((dataSize shr 8) and 0xff).toByte()
        header[42] = ((dataSize shr 16) and 0xff).toByte()
        header[43] = ((dataSize shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }
}
