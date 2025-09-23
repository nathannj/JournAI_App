package com.journai.journai.ui.screens.create

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import com.journai.journai.whisper.WhisperBridge
import com.journai.journai.network.ProxyApi
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream

@Singleton
class SpeechRecognitionManager @Inject constructor(
    private val api: ProxyApi,
    private val appContext: Context
) {
    
    private var isRecording = false
    private var isTranscribing = false
    private var recordingStartMs: Long? = null
    private var manualStopRequested = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var activityRef: WeakReference<ComponentActivity>? = null
    private var audioRecord: AudioRecord? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val processingExecutor = Executors.newSingleThreadExecutor()
    private var isNativeReady = false
    private var processingRunnable: Runnable? = null
    private var processingRunnablePosted = false
    private var processingInFlight = false
    private var committedText = "" // finalized and sent to UI
    private var lastHypothesis = ""
    private var stableCount = 0
    
    private val _recognitionState = MutableStateFlow(SpeechRecognitionState())
    val recognitionState: StateFlow<SpeechRecognitionState> = _recognitionState.asStateFlow()
    
    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()
    private val _finalText = MutableStateFlow("")
    val finalText: StateFlow<String> = _finalText.asStateFlow()
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    private var audioLevelEma: Float = 0f
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var pcmBuffer: ByteArrayOutputStream? = null

    // Audio config for whisper.cpp (expects 16 kHz mono)
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    fun startListening(activity: ComponentActivity) {
        if (isRecording) return
        
        _partialText.value = ""
        _finalText.value = ""
        committedText = ""
        lastHypothesis = ""
        stableCount = 0
        manualStopRequested = false
        activityRef = WeakReference(activity)
        
        if (ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            _recognitionState.value = _recognitionState.value.copy(
                error = "Microphone permission required"
            )
            return
        }
        
        try {
            // Initialize native model only if offline at start; otherwise defer
            _recognitionState.value = _recognitionState.value.copy(error = null)
            if (!hasNetwork(appContext)) {
                val modelFile = ensureModelFromAssets(activity)
                val ok = WhisperBridge.nativeInit(modelFile.absolutePath)
                if (!ok) {
                    _recognitionState.value = _recognitionState.value.copy(error = "Failed to init whisper model")
                    return
                }
                isNativeReady = true
            } else {
                isNativeReady = false
            }

            val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBuf * 2).coerceAtLeast(sampleRate / 2)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // Attach audio effects for better transcription quality if available
            try {
                val sessionId = audioRecord?.audioSessionId ?: 0
                if (sessionId != 0) {
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)
                        noiseSuppressor?.enabled = true
                    }
                    if (AutomaticGainControl.isAvailable()) {
                        automaticGainControl = AutomaticGainControl.create(sessionId)
                        automaticGainControl?.enabled = true
                    }
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)
                        echoCanceler?.enabled = true
                    }
                }
            } catch (_: Throwable) { }

            audioRecord?.startRecording()
            isRecording = true
            isTranscribing = false
            recordingStartMs = System.currentTimeMillis()
            pcmBuffer = ByteArrayOutputStream()
            _recognitionState.value = _recognitionState.value.copy(
                isRecording = true,
                isListening = true,
                isTranscribing = false,
                recordingStartMs = recordingStartMs,
                error = null
            )

            executor.execute { captureLoop(bufferSize) }
        } catch (t: Throwable) {
            _recognitionState.value = _recognitionState.value.copy(error = t.message ?: "Failed to start recording")
        }
    }
    
    fun stopListening() {
        manualStopRequested = true
        audioRecord?.let {
            try { it.stop() } catch (_: Throwable) {}
            try { it.release() } catch (_: Throwable) {}
        }
        audioRecord = null
        isRecording = false
        isTranscribing = true
        // Release audio effects
        try { noiseSuppressor?.enabled = false; noiseSuppressor?.release() } catch (_: Throwable) {}
        try { automaticGainControl?.enabled = false; automaticGainControl?.release() } catch (_: Throwable) {}
        try { echoCanceler?.enabled = false; echoCanceler?.release() } catch (_: Throwable) {}
        noiseSuppressor = null
        automaticGainControl = null
        echoCanceler = null
        _recognitionState.value = _recognitionState.value.copy(
            isRecording = false,
            isListening = false,
            isTranscribing = true
        )
        processingRunnable?.let { mainHandler.removeCallbacks(it) }
        processingRunnable = null
        processingExecutor.execute {
            val useCloud = hasNetwork(appContext)
            var finalText = ""
            try {
                if (useCloud) {
                    val bytes = pcmBuffer?.toByteArray() ?: ByteArray(0)
                    // chunk into ~30s windows: 10 * 16000 samples * 2 bytes = 960,000 bytes
                    val chunkBytes = 30 * sampleRate * 2
                    if (bytes.isNotEmpty() && bytes.size > chunkBytes) {
                        // Send chunks sequentially to keep each request small
                        val parts = mutableListOf<String>()
                        var offset = 0
                        while (offset < bytes.size) {
                            val end = kotlin.math.min(offset + chunkBytes, bytes.size)
                            val slice = bytes.copyOfRange(offset, end)
                            val b64 = android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP)
                            parts.add(b64)
                            offset = end
                        }
                        val assembled = StringBuilder()
                        for (chunk in parts) {
                            val resp = runBlocking {
                                api.transcribe(
                                    com.journai.journai.network.TranscribeRequest(
                                        audioBase64 = chunk,
                                        sampleRate = sampleRate,
                                        language = "en"
                                    )
                                )
                            }
                            if (assembled.isNotEmpty()) assembled.append(' ')
                            assembled.append(resp.text.orEmpty())
                        }
                        finalText = assembled.toString()
                    } else {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        val resp = runBlocking {
                            api.transcribe(
                                com.journai.journai.network.TranscribeRequest(
                                    audioBase64 = base64,
                                    sampleRate = sampleRate,
                                    language = "en"
                                )
                            )
                        }
                        finalText = resp.text.orEmpty()
                    }
                } else {
                    // Offline fallback: if native wasn't initialized earlier, init now and feed buffered PCM
                    if (!isNativeReady) {
                        try {
                            val modelFile = ensureModelFromAssets(activityRef?.get() ?: appContext as ComponentActivity)
                            val ok = WhisperBridge.nativeInit(modelFile.absolutePath)
                            if (ok) isNativeReady = true
                        } catch (_: Throwable) {}
                    }
                    if (isNativeReady) {
                        try {
                            val bytes = pcmBuffer?.toByteArray() ?: ByteArray(0)
                            if (bytes.isNotEmpty()) {
                                val shorts = ByteArrayToShortArrayLE(bytes)
                                var idx = 0
                                val chunk = ShortArray(4096)
                                while (idx < shorts.size) {
                                    val n = kotlin.math.min(chunk.size, shorts.size - idx)
                                    System.arraycopy(shorts, idx, chunk, 0, n)
                                    WhisperBridge.nativeFeedPcm(chunk, n)
                                    idx += n
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                    val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                    val builder = StringBuilder()
                    while (isNativeReady) {
                        val chunk = try { WhisperBridge.nativeProcess(false, threads) } catch (_: Throwable) { "" }
                        if (chunk.isBlank()) break
                        if (builder.isNotEmpty()) builder.append(' ')
                        builder.append(chunk.trim())
                    }
                    finalText = builder.toString()
                }
            } catch (_: Throwable) {
            }
            mainHandler.post {
                if (finalText.isNotBlank()) {
                    _finalText.value = finalText
                    _recognitionState.value = _recognitionState.value.copy(lastTranscription = finalText)
                    _finalText.value = ""
                }
                isTranscribing = false
                recordingStartMs = null
                _recognitionState.value = _recognitionState.value.copy(
                    isTranscribing = false,
                    recordingStartMs = null
                )
                if (isNativeReady) {
                    try { WhisperBridge.nativeRelease() } catch (_: Throwable) {}
                    isNativeReady = false
                }
                pcmBuffer = null
            }
        }
    }
    
    fun cancelListening() {
        stopListening()
    }
    
    fun clearTranscription() {
        _partialText.value = ""
        _finalText.value = ""
        _recognitionState.value = _recognitionState.value.copy(
            lastTranscription = "",
            error = null
        )
    }
    
    fun resetState() {
        isRecording = false
        manualStopRequested = false
        _partialText.value = ""
        _finalText.value = ""
        committedText = ""
        lastHypothesis = ""
        stableCount = 0
        _recognitionState.value = SpeechRecognitionState()
    }
    
    fun destroy() {
        try { stopListening() } catch (_: Throwable) {}
        _partialText.value = ""
        _finalText.value = ""
    }

    private fun captureLoop(bufferSize: Int) {
        val localRecord = audioRecord ?: return
        val buffer = ShortArray(bufferSize)
        while (isRecording) {
            val read = localRecord.read(buffer, 0, buffer.size)
            if (read > 0) {
                // Store raw PCM for cloud transcription
                pcmBuffer?.write(buffer.toByteArray(read))
                // Feed native for offline fallback only if initialized
                if (isNativeReady) {
                    WhisperBridge.nativeFeedPcm(buffer, read)
                }
                val level = mapRmsToLevel(computeRms(buffer, read))
                // More responsive EMA to better show dips between words
                audioLevelEma = (audioLevelEma * 0.3f) + (level * 0.7f)
                _audioLevel.value = audioLevelEma
            }
        }
    }

    private fun ShortArray.toByteArray(length: Int): ByteArray {
        val out = ByteArray(length * 2)
        var i = 0
        var j = 0
        while (i < length) {
            val v = this[i].toInt()
            out[j] = (v and 0xFF).toByte()
            out[j + 1] = ((v ushr 8) and 0xFF).toByte()
            i++
            j += 2
        }
        return out
    }

    private fun ByteArrayToShortArrayLE(bytes: ByteArray): ShortArray {
        val out = ShortArray(bytes.size / 2)
        var i = 0
        var j = 0
        while (i < out.size) {
            val lo = bytes[j].toInt() and 0xFF
            val hi = bytes[j + 1].toInt() and 0xFF
            out[i] = ((hi shl 8) or lo).toShort()
            i++
            j += 2
        }
        return out
    }

    private fun hasNetwork(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun scheduleProcessing() {
        // Disabled: we transcribe after stop now
    }

    private fun computeRms(samples: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val s = samples[i] / 32768.0
            sum += s * s
        }
        val mean = if (length > 0) sum / length else 0.0
        return kotlin.math.sqrt(mean)
    }

    private fun mapRmsToLevel(rms: Double): Float {
        // Perceptual mapping: dB scale -> normalized, with gamma shaping to accentuate dips
        val db = 20.0 * (kotlin.math.ln(rms + 1e-12) / kotlin.math.ln(10.0))
        val minDb = -80.0
        val maxDb = -8.0
        val norm = ((db - minDb) / (maxDb - minDb)).coerceIn(0.0, 1.0)
        val shaped = java.lang.Math.pow(norm, 1.6)
        return shaped.toFloat()
    }

    private fun removeOverlapSuffixPrefix(base: String, addition: String): String {
        if (base.isBlank() || addition.isBlank()) return addition
        val baseTrim = base.trimEnd()
        val addTrim = addition.trimStart()
        val maxOverlap = minOf(baseTrim.length, addTrim.length)
        for (len in maxOverlap downTo 1) {
            if (baseTrim.takeLast(len).equals(addTrim.take(len), ignoreCase = true)) {
                return addTrim.drop(len)
            }
        }
        return addition
    }

    private fun shouldFinalize(delta: String, stableCount: Int): Boolean {
        if (delta.isBlank()) return false
        val trimmed = delta.trimEnd()
        val endsSentence = trimmed.endsWith('.') || trimmed.endsWith('!') || trimmed.endsWith('?')
        val hasWordBoundary = trimmed.endsWith(' ')
        // Heuristics: sentence end, or observed stable hypothesis twice, or at least 2 words added
        val wordCount = trimmed.split(Regex("\\s+")).size
        return endsSentence || stableCount >= 1 || wordCount >= 2
    }

    private fun ensureModelFromAssets(activity: ComponentActivity): File {
        val dir = File(activity.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        // Require small.en only; do not fallback to base to respect space savings
        val small = File(dir, "ggml-small.en.bin")
        if (!small.exists()) {
            // Copy from bundled assets if present
            try {
                activity.assets.open("models/ggml-small.en.bin").use { input ->
                    small.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (t: Throwable) {
                throw IllegalStateException("Missing model: ggml-small.en.bin. Please bundle it under assets/models/.")
            }
        }
        return small
    }
}

data class SpeechRecognitionState(
    val isRecording: Boolean = false,
    val isListening: Boolean = false,
    val lastTranscription: String = "",
    val error: String? = null,
    val isTranscribing: Boolean = false,
    val recordingStartMs: Long? = null
)
