package br.ufu.chatapp.util

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

fun createTempImageUri(context: Context): Uri {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "img_${System.currentTimeMillis()}.jpg").apply {
        parentFile?.mkdirs()
        if (!exists()) createNewFile()
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

fun getDisplayName(context: Context, uri: Uri): String {
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) {
                return cursor.getString(column)
            }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "arquivo"
}

class SimpleAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(): Uri {
        releaseRecorder()
        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
        recorder = runCatching {
            MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        }.getOrElse {
            releaseRecorder()
            throw it
        }
        output = file
        return Uri.fromFile(file)
    }

    fun stop(): Uri? {
        val recordedFile = output
        return runCatching {
            recorder?.stop()
            recordedFile?.let { Uri.fromFile(it) }
        }.getOrNull().also {
            releaseRecorder()
        }
    }

    private fun releaseRecorder() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        output = null
    }
}
