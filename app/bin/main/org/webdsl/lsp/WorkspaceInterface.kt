package org.webdsl.lsp

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.nio.file.Path

interface WorkspaceInterface : java.io.Closeable {
  val clientRoot: Path
  val compilerRoot: Path
  fun create(path: String)
  fun delete(path: String)
  fun rename(oldPath: String, newPath: String)
  fun change(path: String, changes: List<TextDocumentContentChangeEvent>)
  fun open(path: String)
  fun close(path: String)
  fun compilerPathFor(clientPath: String): Path?
  fun clientPathFor(compilerPath: String): Path?
}
