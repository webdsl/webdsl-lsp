/**
 * An interface for handling workspace events (i.e. modifications to files).
 */
package org.webdsl.lsp

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.webdsl.lsp.utils.WebDSLAppConfig
import org.webdsl.lsp.utils.parseConfig
import java.io.FileNotFoundException
import java.nio.file.Path

interface WorkspaceInterface : java.io.Closeable {
  val clientRoot: Path
  val compilerRoot: Path
  val appConfig: WebDSLAppConfig?
    get() {
      return try {
        parseConfig(compilerRoot.resolve("application.ini").toFile().readText())
      } catch (ex: FileNotFoundException) {
        null
      }
    }
  val appName: String?
    get() {
      return appConfig?.get("appname")
    }
  fun create(path: String)
  fun delete(path: String)
  fun rename(oldPath: String, newPath: String)
  fun change(path: String, changes: List<TextDocumentContentChangeEvent>)
  fun open(path: String)
  fun close(path: String)
  // Compiler might use different paths than the ones provided by language client, hence the following methods
  fun compilerPathFor(clientPath: String): Path?
  fun clientPathFor(compilerPath: String): Path?
}
