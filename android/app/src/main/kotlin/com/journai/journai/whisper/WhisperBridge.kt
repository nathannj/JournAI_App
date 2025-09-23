package com.journai.journai.whisper

object WhisperBridge {
    init {
        try {
            System.loadLibrary("whisper-jni")
        } catch (t: Throwable) {
            android.util.Log.e("Whisper", "Failed to load native lib: ${t.message}")
        }
    }

    external fun nativeInit(modelPath: String): Boolean
    external fun nativeRelease()
    external fun nativeFeedPcm(pcm: ShortArray, length: Int)
    external fun nativeProcess(translate: Boolean, threads: Int): String
}


