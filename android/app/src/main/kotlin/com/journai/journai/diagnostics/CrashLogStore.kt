package com.journai.journai.diagnostics

import android.content.Context
import com.journai.journai.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogStore {
    private const val DIRECTORY = "crash_logs"
    private const val LATEST_CRASH = "latest-crash.txt"
    private const val EVENTS = "events.log"
    private const val MAX_EVENTS_BYTES = 256 * 1024L

    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrash(applicationContext, thread, throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun recordEvent(event: String) {
        val context = appContext ?: return
        runCatching {
            val file = File(context.filesDir, "$DIRECTORY/$EVENTS")
            file.parentFile?.mkdirs()
            file.appendText("${timestamp()} $event\n")
            if (file.length() > MAX_EVENTS_BYTES) {
                val tail = file.readLines().takeLast(2000)
                file.writeText(tail.joinToString(separator = "", postfix = "\n"))
            }
        }
    }

    fun latestCrashFile(context: Context): File? {
        val file = File(context.filesDir, "$DIRECTORY/$LATEST_CRASH")
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        runCatching {
            val file = File(context.filesDir, "$DIRECTORY/$LATEST_CRASH")
            file.parentFile?.mkdirs()
            val stackTrace = StringWriter().also { writer ->
                throwable.printStackTrace(PrintWriter(writer))
            }
            file.writeText(
                buildString {
                    appendLine("JournAI crash report")
                    appendLine("Timestamp: ${timestamp()}")
                    appendLine("App version: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine("Uncaught exception:")
                    appendLine(stackTrace.toString())
                    appendLine("Recent app events:")
                    val events = File(context.filesDir, "$DIRECTORY/$EVENTS")
                    if (events.exists()) append(events.readText()) else appendLine("(none)")
                }
            )
        }
    }

    private fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
}
