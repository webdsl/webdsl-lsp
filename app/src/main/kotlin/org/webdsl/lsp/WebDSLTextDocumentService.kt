package org.webdsl.lsp

import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.webdsl.lsp.utils.parseFileURI
import java.util.concurrent.CompletableFuture

class WebDSLTextDocumentService(val clientProvider: LanguageClientProvider) : TextDocumentService {
  val compilerFacade: CompilerFacade by lazy { // lazy cause workspaceInterface might be uninitialized
    CompilerFacade(clientProvider.workspaceInterface!!)
  }

  override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
    return CompletableFuture.supplyAsync { Either.forLeft(listOf()) }
  }

  override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
    return CompletableFuture.supplyAsync { Either.forLeft(listOf()) }
  }

  override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun didOpen(params: DidOpenTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      println("didOpen")
      for ((k, v) in compilerFacade.analyse(it.path).toDiagnosticMap()) {
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(k, v))
      }
      println("analysis done")
    }
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      println("didChange")
      clientProvider.workspaceInterface?.change(it.path, params.contentChanges)
      for ((k, v) in compilerFacade.analyse(it.path).toDiagnosticMap()) {
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(k, v))
      }
      println("analysis done")
    }
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      clientProvider.workspaceInterface?.close(it.path)
    }
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      println("didSave")
      for ((k, v) in compilerFacade.analyse(it.path).toDiagnosticMap()) {
        println("$k -> $v")
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(k, v))
      }
      println("analysis done")
    }
  }

  override fun hover(params: HoverParams): CompletableFuture<Hover> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun documentHighlight(position: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
    return CompletableFuture.supplyAsync { listOf() }
  }
}
