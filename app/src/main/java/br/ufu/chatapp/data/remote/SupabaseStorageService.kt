package br.ufu.chatapp.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import android.webkit.MimeTypeMap
import br.ufu.chatapp.BuildConfig
import br.ufu.chatapp.data.model.MessageType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SupabaseStorageService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun upload(
        uri: Uri,
        conversationId: String,
        type: MessageType,
        mediaName: String
    ): String = withContext(Dispatchers.IO) {
        checkConfig()

        val contentResolver = context.contentResolver
        val bytes = readBytesForUpload(uri, type)
        val objectPath = buildObjectPath(conversationId, type, mediaName)
        val encodedPath = Uri.encode(objectPath, "/")
        val endpoint = "${BuildConfig.SUPABASE_URL}/storage/v1/object/${BuildConfig.SUPABASE_BUCKET}/$encodedPath"
        val mimeType = mimeTypeForUpload(uri, type, mediaName)
        val requestError = runCatching {
            executeUploadRequest(
                endpoint = endpoint,
                method = "POST",
                bytes = bytes,
                mimeType = mimeType
            )
        }.exceptionOrNull()

        if (requestError != null) {
            executeUploadRequest(
                endpoint = endpoint,
                method = "PUT",
                bytes = bytes,
                mimeType = mimeType
            )
        }

        "${BuildConfig.SUPABASE_URL}/storage/v1/object/public/${BuildConfig.SUPABASE_BUCKET}/$encodedPath"
    }

    private fun readBytesForUpload(uri: Uri, type: MessageType): ByteArray {
        return if (type == MessageType.IMAGE) {
            compressImage(uri)
        } else {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Nao foi possivel ler o arquivo selecionado")
        }
    }

    private fun compressImage(uri: Uri): ByteArray {
        val originalBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Nao foi possivel ler a imagem selecionada")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, bounds)
        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, 1280, 1280)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decodedBitmap = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, decodeOptions)
            ?: throw IOException("Nao foi possivel processar a imagem selecionada")
        val bitmap = applyExifRotation(originalBytes, decodedBitmap)

        return ByteArrayOutputStream().use { output ->
            var quality = 72
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            while (output.size() > 350 * 1024 && quality > 45) {
                output.reset()
                quality -= 7
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            }
            if (bitmap !== decodedBitmap) decodedBitmap.recycle()
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun applyExifRotation(imageBytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = ExifInterface(imageBytes.inputStream()).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.preScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sampleSize = 1
        if (width <= 0 || height <= 0) return sampleSize
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sampleSize >= reqWidth && halfHeight / sampleSize >= reqHeight) {
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private fun mimeTypeForUpload(uri: Uri, type: MessageType, mediaName: String): String {
        return if (type == MessageType.IMAGE) {
            "image/jpeg"
        } else {
            context.contentResolver.getType(uri) ?: fallbackMimeType(type, mediaName)
        }
    }

    private fun executeUploadRequest(
        endpoint: String,
        method: String,
        bytes: ByteArray,
        mimeType: String
    ) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("apikey", BuildConfig.SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_KEY}")
            setRequestProperty("x-upsert", "true")
            setRequestProperty("Content-Type", mimeType)
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { it.write(bytes) }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val error = readConnectionBody(connection)
                val details = buildString {
                    append("Upload falhou ($responseCode)")
                    if (error.isNotBlank()) append(": $error")
                    append(". Verifique se o bucket \"${BuildConfig.SUPABASE_BUCKET}\" existe e se as policies do Storage foram aplicadas.")
                }
                throw IOException(details)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun readConnectionBody(connection: HttpURLConnection): String {
        val stream = connection.errorStream ?: connection.inputStream ?: return ""
        return runCatching {
            stream.use { input ->
                ByteArrayOutputStream().use { output ->
                    input.copyTo(output)
                    output.toString(Charsets.UTF_8.name()).trim()
                }
            }
        }.getOrDefault("")
    }

    private fun buildObjectPath(conversationId: String, type: MessageType, mediaName: String): String {
        val safeName = sanitizeFileName(mediaName.ifBlank { defaultFileName(type) })
        return "chatMedia/$conversationId/${System.currentTimeMillis()}_$safeName"
    }

    private fun sanitizeFileName(fileName: String): String {
        val normalized = fileName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "arquivo.bin" }
        return if (normalized.contains('.')) normalized else "$normalized.bin"
    }

    private fun defaultFileName(type: MessageType): String {
        return when (type) {
            MessageType.IMAGE -> "imagem.jpg"
            MessageType.VIDEO -> "video.mp4"
            MessageType.AUDIO -> "audio.m4a"
            MessageType.FILE -> "arquivo.bin"
            else -> "midia.bin"
        }
    }

    private fun fallbackMimeType(type: MessageType, mediaName: String): String {
        val fromExtension = mediaName.substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.isNotBlank() }
            ?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
        if (!fromExtension.isNullOrBlank()) return fromExtension

        return when (type) {
            MessageType.IMAGE -> "image/jpeg"
            MessageType.VIDEO -> "video/mp4"
            MessageType.AUDIO -> "audio/mp4"
            MessageType.FILE -> "application/octet-stream"
            else -> "application/octet-stream"
        }
    }

    private fun checkConfig() {
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank() || BuildConfig.SUPABASE_BUCKET.isBlank()) {
            throw IOException("Configure o Supabase antes de enviar arquivos")
        }
    }
}
