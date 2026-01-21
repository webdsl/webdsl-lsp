package org.webdsl.lsp

import org.eclipse.lsp4j.CompletionOptions
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationOptions
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationsServerCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.net.URI
import java.util.concurrent.CompletableFuture
import kotlin.io.path.Path

class WebDSLLanguageServer() : LanguageServer, LanguageClientProvider {
  override var client: LanguageClient? = null
  override var workspaceInterface: WorkspaceInterface? = null
  val webdslTextDocumentService: TextDocumentService = WebDSLTextDocumentService(this)
  val webdslWorkspaceService: WorkspaceService = WebDSLWorkspaceService(this)

  override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
    val capabilities = ServerCapabilities().apply {
      textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
      completionProvider = CompletionOptions()
      workspace = WorkspaceServerCapabilities().apply {
        fileOperations = FileOperationsServerCapabilities().apply {
          val matchAllFileOperations = FileOperationOptions(
            listOf(
              FileOperationFilter(
                FileOperationPattern(
                  "**/*",
                ),
                "file",
              ),
            ),
          )
          didCreate = matchAllFileOperations
          didDelete = matchAllFileOperations
          didRename = matchAllFileOperations
        }
      }
    }

    // TODO: use `workspaceFolders` instead
    workspaceInterface = MirrorWorkspaceInterfaceImpl(Path(URI(params.rootUri).path))

    val info = ServerInfo("WebDSL Language Server", "1.0.0")
    val result = InitializeResult(capabilities, info)

    return CompletableFuture.supplyAsync { result }
  }

  override fun shutdown(): CompletableFuture<Any> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun exit() {
  }

  override fun getTextDocumentService(): TextDocumentService {
    return webdslTextDocumentService
  }

  override fun getWorkspaceService(): WorkspaceService {
    return webdslWorkspaceService
  }

  fun setRemoteProxy(languageClient: LanguageClient): Runnable {
    client = languageClient

    return Runnable({
      this@WebDSLLanguageServer.apply {
        client = null
        workspaceInterface?.close()
        workspaceInterface = null
      }
    })
  }
}
