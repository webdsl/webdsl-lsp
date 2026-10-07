/**
 * Currently the only implementation of WorkspaceInterface.
 * Works by creating a copy of the whole WebDSL project in a temp directory.
 * This is required as we might want the LSP to analyse changes that are not yet saved to disk from IDEs point of view;
 * the mirrored directory will write all changes to disk as soon as possible, hence making the analysis easy without
 * complex changes being required in webdslc.
 */
package org.webdsl.lsp

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.webdsl.lsp.utils.applyChange
import org.webdsl.lsp.utils.recursivelyCopyFilesWithExtensions
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.deleteRecursively
import kotlin.io.path.Path
import kotlin.io.path.relativeTo

class MirrorWorkspaceInterfaceImpl(override val clientRoot: java.nio.file.Path) : WorkspaceInterface {
  override val compilerRoot = Files.createTempDirectory("webdsllsp")
  private val contentRevisionCounter = AtomicLong(0)
  override val contentRevision: Long
    get() = contentRevisionCounter.get()

  init {
    recursivelyCopyFilesWithExtensions(clientRoot.toFile(), compilerRoot.toFile(), listOf("app", "ini"))
    // java.io.File.deleteOnExit sadly doesn't delete recursively, this is one possible workaround.
    // The other workaround would be to manually call deleteOnExit on all child files
    // but this could prove tricky with files deleted during extension's runtime since files scheduled to be deleted on exit cannot be unscheduled
    Runtime.getRuntime().addShutdownHook(
      Thread {
        // note: in development, if the server is spawned with `./gradlew run` and killed with ^C, this message might not get printed.
        // The hook still triggers just fine though.
        println("Cleaning up $compilerRoot")
        compilerRoot.toFile().deleteRecursively()
      },
    )
    println("Created mapping $clientRoot -> $compilerRoot")
  }

  override fun create(path: String) {
    val created = compilerPathFor(path)?.toFile()?.createNewFile() ?: false
    if (created) {
      bumpContentRevision()
    }
  }

  override fun delete(path: String) {
    val file = compilerPathFor(path)?.toFile() ?: return
    if (!file.exists()) {
      return
    }
    if (file.deleteRecursively()) {
      bumpContentRevision()
    }
  }

  override fun rename(oldPath: String, newPath: String) {
    compilerPathFor(newPath)?.toFile()?.let {
      val renamed = compilerPathFor(oldPath)?.toFile()?.renameTo(it) ?: false
      if (renamed) {
        bumpContentRevision()
      }
    }
  }

  override fun change(path: String, changes: List<TextDocumentContentChangeEvent>) {
    val p = compilerPathFor(path)

    if (p == null) {
      return
    }

    val originalContent = Files.readString(p)
    val changedContent = changes.fold(originalContent, ::applyChange)
    if (changedContent == originalContent) {
      return
    }

    Files.writeString(p, changedContent)

    deleteParseCacheEntriesFor(p)
    bumpContentRevision()
  }

  private fun deleteParseCacheEntriesFor(compilerPath: java.nio.file.Path) {
    val relative = compilerPath.relativeTo(compilerRoot)
    val name = relative.fileName.toString()
    val cacheDir = cacheDirFor(relative.parent)

    cacheDir
      ?.listFiles { file -> file.isFile && file.name.startsWith(name) }
      ?.forEach { it.delete() }
  }

  private fun cacheDirFor(relativeDir: java.nio.file.Path?): java.io.File? {
    val cacheDir = compilerRoot.resolve(".webdsl-parsecache")
    val dir = if (relativeDir == null) cacheDir else cacheDir.resolve(relativeDir)
    return if (Files.isDirectory(dir)) dir.toFile() else null
  }

  private fun bumpContentRevision() {
    contentRevisionCounter.incrementAndGet()
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
