package it.unibo.alchemist.model.monitors

import java.io.File
import java.util.Locale

internal fun exportToCsv(filename: String, header: String, format: String, history: List<Line>) {
    File(filename).printWriter().use { out ->
        out.println(header)
        history.forEach { step ->
            val line = String.format(
                Locale.US,
                format,
                *step.values,
            )
            out.println(line)
        }
    }
}

internal class Line(vararg val values: Any)
