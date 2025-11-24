package org.webdsl.lsp

import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.ServerInfo
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.CompletionOptions
import java.util.concurrent.CompletableFuture

class WebDSLLanguageServer(val textDocumentService: TextDocumentService, val workspaceService: WorkspaceService) : LanguageServer {
  var client: LanguageClient? = null;

  override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
    val capabilities = ServerCapabilities().apply {
      textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
      completionProvider = CompletionOptions()
    }

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
    return textDocumentService
  }

  override fun getWorkspaceService(): WorkspaceService {
    return workspaceService
  }

  fun setRemoteProxy(languageClient: LanguageClient): Runnable {
    client = languageClient

    return Runnable({
      this@WebDSLLanguageServer.client = null
    })
  }
}
