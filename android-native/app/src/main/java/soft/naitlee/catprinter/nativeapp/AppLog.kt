package soft.naitlee.catprinter.nativeapp

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLog {
    private const val MAX_LINES = 300
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun add(message: String) {
        lines.addLast("${formatter.format(Date())}  $message")
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    @Synchronized
    fun text(): String = if (lines.isEmpty()) "No log entries yet." else lines.joinToString("\n")
}
