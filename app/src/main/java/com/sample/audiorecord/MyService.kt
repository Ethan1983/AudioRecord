package com.sample.audiorecord

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class MyService : Service() {

    private val recordingInProgress = AtomicBoolean(false)
    private lateinit var recorder : AudioRecord

    override fun onBind(intent: Intent): IBinder {
        throw UnsupportedOperationException()
    }

    override fun onCreate() {
        super.onCreate()

        val builder = NotificationCompat.Builder(this, "CHANNEL_ID")
            .setOngoing(true)
            .setContentTitle("recording")
            .setSmallIcon(android.R.drawable.stat_sys_download)

        startForeground( 123, builder.build())

        val mainThreadHandler = Handler(Looper.getMainLooper())

        startRecording()

        mainThreadHandler.postDelayed( {
            stopRecording()
        }, 8000)
    }

    private fun stopRecording() {
        Toast.makeText(this, "Stop Recording", Toast.LENGTH_SHORT).show()
        recordingInProgress.set(false)
        recorder.stop()
        recorder.release()
        stopSelf()
    }

    private fun startRecording() {
        Toast.makeText(this, "Start Recording", Toast.LENGTH_SHORT).show()
        recordingInProgress.set(true)
        recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE
        )

        recorder.startRecording()
        val recordingThread = Thread(RecordingRunnable(), "Recording Thread")
        recordingThread.start()
    }

    private inner class RecordingRunnable : Runnable {

        override fun run() {
            val file = File( getExternalFilesDir(null), "recording.pcm")
            Log.d( "MyService", "Saving log in ${file.absolutePath}")
            val buffer = ByteBuffer.allocateDirect(BUFFER_SIZE)

            try {
                FileOutputStream(file).use { outStream ->
                    while (recordingInProgress.get()) {
                        val result = recorder.read(buffer, BUFFER_SIZE)
                        if (result < 0) {
                            throw RuntimeException(
                                "Reading of audio buffer failed: " + getBufferReadFailureReason(
                                    result )
                            )
                        }
                        outStream.write(buffer.array(), 0, BUFFER_SIZE)
                        buffer.clear()
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException("Writing of recorded audio failed", e)
            }
        }

        private fun getBufferReadFailureReason(errorCode: Int): String {
            return when (errorCode) {
                AudioRecord.ERROR_INVALID_OPERATION -> "ERROR_INVALID_OPERATION"
                AudioRecord.ERROR_BAD_VALUE -> "ERROR_BAD_VALUE"
                AudioRecord.ERROR_DEAD_OBJECT -> "ERROR_DEAD_OBJECT"
                AudioRecord.ERROR -> "ERROR"
                else -> "Unknown ($errorCode)"
            }
        }
    }

    companion object {
        private const val SAMPLING_RATE_IN_HZ = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        /**
         * Factor by that the minimum buffer size is multiplied. The bigger the factor is the less
         * likely it is that samples will be dropped, but more memory will be used. The minimum buffer
         * size is determined by [AudioRecord.getMinBufferSize] and depends on the
         * recording settings.
         */
        private const val BUFFER_SIZE_FACTOR = 2

        /**
         * Size of the buffer where the audio data is stored by Android
         */
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLING_RATE_IN_HZ,
            CHANNEL_CONFIG, AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
    }
}
