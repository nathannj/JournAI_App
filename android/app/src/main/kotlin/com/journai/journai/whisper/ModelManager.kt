package com.journai.journai.whisper

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ModelManager {
    private const val TAG = "ModelManager"
    private const val ASSET_PATH = "models/ggml-base.en.bin"
    private const val MODEL_NAME = "ggml-base.en.bin"
    private const val DEFAULT_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    fun getModelFile(context: Context): File {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, MODEL_NAME)
    }

    fun ensureModel(context: Context, onProgress: (Int) -> Unit): File {
        val target = getModelFile(context)
        if (target.exists() && target.length() > 0) return target

        // Try copying from packaged assets first (offline-friendly)
        try {
            if (assetExists(context.assets, ASSET_PATH)) {
                Log.i(TAG, "Copying model from assets")
                copyAsset(context.assets, ASSET_PATH, target)
                if (target.exists() && target.length() > 0) return target
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Asset copy failed: ${t.message}")
        }

        // Download from network
        Log.i(TAG, "Downloading model from network")
        downloadToFile(DEFAULT_URL, target, onProgress)
        return target
    }

    private fun assetExists(assets: AssetManager, path: String): Boolean = try {
        assets.open(path).close(); true
    } catch (e: Exception) { false }

    private fun copyAsset(assets: AssetManager, path: String, dest: File) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        assets.open(path).use { input ->
            FileOutputStream(tmp).use { output ->
                input.copyTo(output)
            }
        }
        if (!tmp.renameTo(dest)) throw IllegalStateException("Failed to move temp asset to dest")
    }

    private fun downloadToFile(url: String, dest: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("HTTP ${'$'}{response.code}")
            val body = response.body ?: throw IllegalStateException("Empty body")
            val contentLength = body.contentLength()
            val tmp = File(dest.parentFile, dest.name + ".download")
            tmp.outputStream().use { fileOut ->
                var bytesCopied: Long = 0
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        fileOut.write(buffer, 0, read)
                        bytesCopied += read
                        if (contentLength > 0) {
                            val pct = ((bytesCopied * 100) / contentLength).toInt().coerceIn(0, 100)
                            onProgress(pct)
                        }
                    }
                }
                fileOut.flush()
            }
            if (contentLength > 0 && dest.exists() && dest.length() == contentLength) {
                // ok
            }
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) throw IllegalStateException("Failed to move downloaded file")
            onProgress(100)
        }
    }
}


