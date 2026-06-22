/**
 * Handles events related to working with code, e.g. go-to-reference, autocompletion etc.
 */
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
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
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
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.TextDocumentService
import org.webdsl.lsp.utils.parseFileURI
import java.util.concurrent.CompletableFuture

fun StrategoPosition.toLspPosition(): Position {
  return Position(line - 1, column - 1)
}

fun StrategoPosition.Companion.fromLspPosition(pos: Position): StrategoPosition {
  return StrategoPosition(pos.line + 1, pos.character + 1)
}

fun StrategoLocation.toLspLocation(): Location {
  return Location(
    "file://" + file,
    Range(start.toLspPosition(), end.toLspPosition()),
  )
}

fun StrategoLocation.Companion.fromLspLocation(loc: Location): StrategoLocation {
  return StrategoLocation(parseFileURI(loc.uri)!!.path, StrategoPosition.fromLspPosition(loc.range.start), StrategoPosition.fromLspPosition(loc.range.end))
}

val StrategoLocation.lspRange: Range
  get() = Range(start.toLspPosition(), end.toLspPosition())

fun StrategoMessage.toDiagnostic(diagnosticSeverity: DiagnosticSeverity): Diagnostic {
  return Diagnostic().apply {
    severity = diagnosticSeverity
    message = messageText
    range = location.lspRange
  }
}

fun LspAnalysisResult.toDiagnosticMap(): Map<String, List<Diagnostic>> {
  val errors = (
    errors.map { it.location.file to it.toDiagnostic(DiagnosticSeverity.Error) } +
      warnings.map { it.location.file to it.toDiagnostic(DiagnosticSeverity.Warning) }
    ).groupBy { it.first }.mapValues { it.value.map { it.second } }.toMutableMap()

  for (c in clearedFiles) {
    errors.putIfAbsent(c, listOf())
  }

  return errors.toMap()
}

fun StrategoCompletion.toLspCompletion(): CompletionItem {
  return CompletionItem().apply {
    label = completion
    documentation = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(details)
  }
}

fun StrategoInlayHint.toLspInlayHint(): InlayHint = run outer@{ // an ugly workaround to be able to access outer `this` inside of `apply`
  InlayHint(position.toLspPosition(), org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(label + ": ")).apply {
    tooltip = org.eclipse.lsp4j.jsonrpc.messages.Either.forLeft(this@outer.label)
  }
}

class WebDSLTextDocumentService(val clientProvider: LanguageClientProvider) : TextDocumentService {
  val compilerFacade: CompilerFacade by lazy { // lazy cause workspaceInterface might be uninitialized
    CompilerFacade(clientProvider.workspaceInterface!!)
  }

  override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> {
    val loc = StrategoLocation.fromLspLocation(Location(position.textDocument.uri, Range(position.position, position.position)))
    return CompletableFuture.supplyAsync {
      Either.forLeft(
        compilerFacade.complete(loc).map { it.toLspCompletion() },
      )
    }
  }

  override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun definition(params: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> {
    val loc = StrategoLocation.fromLspLocation(Location(params.textDocument.uri, Range(params.position, params.position)))
    return CompletableFuture.supplyAsync {
      Either.forLeft(
        compilerFacade.findDefinition(loc)?.let {
          listOf(it.toLspLocation())
        } ?: listOf(),
      )
    }
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
      for ((k, v) in compilerFacade.analyse(it.path).toDiagnosticMap()) {
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(k, v))
      }
    }
  }

  override fun didChange(params: DidChangeTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      clientProvider.workspaceInterface?.change(it.path, params.contentChanges)
      for ((k, v) in compilerFacade.analyse(it.path).toDiagnosticMap()) {
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(k, v))
      }
    }
  }

  override fun didClose(params: DidCloseTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      clientProvider.workspaceInterface?.close(it.path)
    }
  }

  override fun didSave(params: DidSaveTextDocumentParams) {
    parseFileURI(params.textDocument.uri)?.let {
      for ((k, v) in compilerFacade.analyse(it.path).toDiagnosticMap()) {
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(k, v))
      }
    }
  }

  override fun hover(params: HoverParams): CompletableFuture<Hover> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
    return CompletableFuture.supplyAsync { null }
  }

  override fun references(params: ReferenceParams): CompletableFuture<List<Location>> {
    val loc = StrategoLocation.fromLspLocation(Location(params.textDocument.uri, Range(params.position, params.position)))
    return CompletableFuture.supplyAsync {
      compilerFacade.findReferences(loc).map { it.toLspLocation() }
    }
  }

  override fun documentHighlight(position: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> {
    return CompletableFuture.supplyAsync { listOf() }
  }

  override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
    return CompletableFuture.supplyAsync {
      SemanticTokens(
        parseFileURI(params.textDocument.uri)?.let {
          val rawTokens = compilerFacade.semanticTokens(it.path)
          val guard = StrategoSemanticToken("", StrategoPosition(1, 1), WebDSLSemanticTokenType.KEYWORD)
          rawTokens.scan(guard to guard) { (acc, _), it ->
            it to it.relativeTo(acc)
          }.drop(1).flatMap { it.second.flatten() }
        } ?: listOf(),
      )
    }
  }

  override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> {
    return CompletableFuture.supplyAsync {
      val fileName = parseFileURI(params.textDocument.uri)?.path
      if (fileName == null) {
        return@supplyAsync listOf()
      }

      val start = StrategoPosition.fromLspPosition(params.range.start)
      val end = StrategoPosition.fromLspPosition(params.range.end)

      val loc = StrategoLocation(fileName, start, end)

      compilerFacade.inlayHints(loc).map { it.toLspInlayHint() }
    }
  }
}
