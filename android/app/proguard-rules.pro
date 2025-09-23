# Keep JNI bridge
-keep class com.journai.journai.whisper.WhisperBridge { *; }
# Keep native methods signatures
-keepclasseswithmembernames class * { native <methods>; }
