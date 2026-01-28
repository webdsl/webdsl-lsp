package org.webdsl.lsp.utils

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.net.URI

typealias WebDSLAppConfig = Map<String, String>

// TODO: check ranges
// TODO: does it make a difference which line terminator is used to join the lines?
// TODO: handle encodings and characters represented by multiple bytes correctly
fun applyChange(content: String, change: TextDocumentContentChangeEvent): String {
  if (change.range == null) {
    return change.text
  } else {
    val lines = content.lines() // bless whomever decided `Splits this char sequence to a list of lines delimited by any of the following character sequences: CRLF, LF or CR.`
    val start = change.range.start
    val end = change.range.end
    val beforeChange = lines.take(start.line).joinToString("\n") + lines[start.line].take(start.character)
    val afterChange = lines[end.line].drop(end.character + 1) + lines.drop(end.line).joinToString("\n")
    return beforeChange + change.text + afterChange
  }
}

/**
 * Parse config from application.ini file
 *
 * @param config contents of application.ini
 * @return WebDSL app configuration on success, null on parse error
 */
fun parseConfig(config: String): WebDSLAppConfig? {
  return try {
    config.lines().map {
      val kv = it.split("=", limit = 2)
      if (kv.size != 2) {
        null
      } else {
        kv[0] to kv[1]
      }
    }.requireNoNulls().toMap()
  } catch (ex: IllegalArgumentException) {
    null
  }
}

fun parseFileURI(uri: String): URI? {
  val u = URI(uri)
  if (u.scheme == "file") {
    return u
  } else {
    return null
  }
}
