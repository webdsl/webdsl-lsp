package org.webdsl.lsp

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.webdsl.lsp.utils.applyChange
import java.nio.file.Files
import kotlin.io.copyRecursively
import kotlin.io.deleteRecursively
import kotlin.io.path.Path
import kotlin.io.path.relativeTo

class MirrorWorkspaceInterfaceImpl(override val clientRoot: java.nio.file.Path) : WorkspaceInterface {
  override val compilerRoot = Files.createTempDirectory("webdsllsp")

  init {
    clientRoot.toFile().copyRecursively(compilerRoot.toFile(), true)
    println("Created mapping $clientRoot -> $compilerRoot")
  }

  override fun create(path: String) {
    compilerPathFor(path)?.toFile()?.createNewFile()
  }

  override fun delete(path: String) {
    compilerPathFor(path)?.toFile()?.deleteRecursively()
  }

  override fun rename(oldPath: String, newPath: String) {
    compilerPathFor(newPath)?.toFile()?.let {
      compilerPathFor(oldPath)?.toFile()?.renameTo(it)
    }
  }

  override fun change(path: String, changes: List<TextDocumentContentChangeEvent>) {
    val p = compilerPathFor(path)

    if (p == null) {
      return
    }

    val changedContent = changes.fold(Files.readString(p), ::applyChange)
    Files.writeString(p, changedContent)
  }

  override fun open(path: String) {
    // nop
  }

  override fun close(path: String) {
    // nop
  }

  override fun close() {
    compilerRoot.toFile().deleteRecursively()
  }

  override fun compilerPathFor(clientPath: String): java.nio.file.Path? {
    var p = Path(clientPath)

    if (p.startsWith(clientRoot)) {
      return compilerRoot.resolve(p.relativeTo(clientRoot))
    } else {
      return null
    }
  }

  override fun clientPathFor(compilerPath: String): java.nio.file.Path? {
    var p = Path(compilerPath)

    if (p.startsWith(compilerRoot)) {
      return clientRoot.resolve(p.relativeTo(compilerRoot))
    } else {
      return null
    }
  }
}
