package org.webdsl.lsp.utils

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.io.File
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

// java.util.Properties
/**
 * Parse config from application.ini file
 *
 * @param config contents of application.ini
 * @return WebDSL app configuration on success, null on parse error
 */
fun parseConfig(config: String): WebDSLAppConfig? {
  val trimmedLines = config.lines().mapNotNull {
    val trimmed = it.trimStart()
    if (trimmed.length == 0 || trimmed.startsWith("#")) {
      null
    } else {
      trimmed
    }
  }

  return try {
    trimmedLines.map {
      val kv = it.split("=", limit = 2)
      if (kv.size != 2) {
        null
      } else {
        kv[0].trim() to kv[1].trimStart()
      }
    }.requireNoNulls().toMap()
  } catch (ex: IllegalArgumentException) {
    null
  }
}

/**
 * Parse a URI with file:// scheme
 *
 * @param uri a string representation of a URI
 * @return parsed URI if the scheme is file://, null otherwise
 */
fun parseFileURI(uri: String): URI? {
  val u = URI(uri)
  if (u.scheme == "file") {
    return u
  } else {
    return null
  }
}

/**
 * Recursively copy a directory including only files with specified extensions
 *
 * @param source the directory to copy
 * @param destiantion the destination to copy to
 * @param extensions the extensions to include
 * @return true on success, false otherwise
 */
fun recursivelyCopyFilesWithExtensions(source: File, destination: File, extensions: List<String>): Boolean {
  return if (source.isDirectory()) {
    destination.mkdir()
    source.listFiles().all {
      recursivelyCopyFilesWithExtensions(
        it,
        File(destination.toPath().resolve(it.name).toString()),
        extensions,
      )
    }
  } else if (extensions.contains(source.extension)) {
    try {
      source.copyTo(destination, overwrite = true)
      true
    } catch (ex: Exception) {
      false
    }
  } else {
    true
  }
}
