package org.webdsl.lsp

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemLabelDetails
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.spoofax.interpreter.terms.IStrategoTerm
import org.spoofax.terms.StrategoAppl
import org.spoofax.terms.StrategoInt
import org.spoofax.terms.StrategoList
import org.spoofax.terms.StrategoString
import org.spoofax.terms.StrategoTuple
import org.strategoxt.lang.Context
import org.strategoxt.lang.StrategoExit
import org.strategoxt.lang.Strategy
import org.webdsl.lsp.utils.Either
import org.webdsl.lsp.utils.case
import org.webdsl.lsp.utils.parseFileURI
import org.webdsl.webdslc.Main
import org.webdsl.webdslc.lsp_complete_0_0
import org.webdsl.webdslc.lsp_main_0_0
import org.webdsl.webdslc.lsp_parse_0_0
import org.webdsl.webdslc.lsp_resolve_0_0
import kotlin.io.copyTo
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

data class StrategoPosition(val line: Int, val column: Int) {
  constructor(pos: Position) : this(pos.line + 1, pos.character + 1)

  fun toLspPosition(): Position {
    return Position(line - 1, column - 1)
  }
}

// data class StrategoLocation(val file: String, val line: Int, val column: Int) {
data class StrategoLocation(val file: String, val start: StrategoPosition, val end: StrategoPosition) {
  constructor(loc: Location) : this(parseFileURI(loc.uri)!!.path, StrategoPosition(loc.range.start), StrategoPosition(loc.range.end))

  fun toLspLocation(): Location {
    return Location(
      "file://" + file,
      Range(start.toLspPosition(), end.toLspPosition()),
    )
  }

  val lspRange: Range
    get() = Range(start.toLspPosition(), end.toLspPosition())
}

data class StrategoMessage(val relatedTerm: IStrategoTerm?, val messageText: String, val location: StrategoLocation) {
  fun toDiagnostic(diagnosticSeverity: DiagnosticSeverity): Diagnostic {
    return Diagnostic().apply {
      severity = diagnosticSeverity
      message = messageText
      range = location.lspRange
    }
  }
}

data class LspAnalysisResult(val errors: List<StrategoMessage>, val warnings: List<StrategoMessage>, val additionalInfo: List<IStrategoTerm>, val clearedFiles: List<String>) {
  fun toDiagnosticMap(): Map<String, List<Diagnostic>> {
    val errors = (
      errors.map { it.location.file to it.toDiagnostic(DiagnosticSeverity.Error) } +
        warnings.map { it.location.file to it.toDiagnostic(DiagnosticSeverity.Warning) }
      ).groupBy { it.first }.mapValues { it.value.map { it.second } }.toMutableMap()

    for (c in clearedFiles) {
      errors.putIfAbsent(c, listOf())
    }

    return errors.toMap()
  }
}

data class StrategoCompletion(val completion: String, val details: String) {
  fun toLspCompletion(): CompletionItem {
    return CompletionItem().apply {
      label = completion
      labelDetails = CompletionItemLabelDetails().apply {
        description = details
      }
    }
  }
}

data class StrategoSemanticToken(val token: String, val position: StrategoPosition, val tokenType: WebDSLSemanticTokenType) {
  fun relativeTo(other: StrategoSemanticToken): StrategoSemanticToken {
    val (line, character) = position
    val (otherLine, otherCharacter) = other.position

    val relativeLine = line - otherLine
    val relativeCharacter = if (line == otherLine) {
      character - otherCharacter
    } else {
      character - 1 // conversion to LSP format
    }

    return this.copy(position = StrategoPosition(relativeLine, relativeCharacter))
  }

  fun flatten(): List<Int> {
    // line, startChar, length, tokenType, tokenModifiers
    return listOf(position.line, position.column, token.length, tokenType.ordinal, 0)
  }
}

class CompilerFacade(val workspaceInterface: WorkspaceInterface) {
  var dirtyFiles: Set<String> = setOf() // list of files that had errors/warnings at last analysis run
  var resolveDirty: Boolean = true // whether any files have changed since last lsp-resolve
  var completeDirty: Boolean = true // whether any files have changed since last lsp-complete

  fun ensureBuiltins() {
    val builtinPath = workspaceInterface.compilerRoot.resolve(".servletapp/src-webdsl-template/built-in.app")
    if (!builtinPath.exists()) {
      builtinPath.apply {
        val builtin = object {}::class.java.getResourceAsStream("/webdsl/template-webdsl/built-in.app")?.bufferedReader()?.readText()!!
        createParentDirectories()
        writeText(builtin)
      }
    }
  }

  fun getAppName(): Either<String, String> {
    // we don't get .ini files modification notifications (for now)
    workspaceInterface.clientRoot.resolve("application.ini")
      .toFile()
      .copyTo(
        workspaceInterface.compilerRoot.resolve("application.ini").toFile(),
        overwrite = true,
      )

    val appName = workspaceInterface.appName
    if (appName == null) {
      // return singleErrorResult(fileName, "Couldn't find \"appname\" property in application.ini")
      return Either.Left("Couldn't find \"appname\" property in application.ini")
    }

    val mainFilePath = workspaceInterface.compilerRoot.resolve(appName + ".app")
    if (!mainFilePath.exists()) {
      // return singleErrorResult(fileName, "File $appName.app doesn't exist")
      return Either.Left("File $appName.app doesn't exist")
    }

    return Either.Right(appName)
  }

  fun analyse(fileName: String): LspAnalysisResult {
    // TODO: analyse can also be called from a simple didOpen, do not invalidate the cache in that situation
    resolveDirty = true
    completeDirty = true

    val ctx: Context = Main.init()
    ctx.setStandAlone(true)

    val path = workspaceInterface.compilerPathFor(fileName)
    if (path == null) {
      return LspAnalysisResult(listOf(), listOf(), listOf(), listOf())
    }

    ensureBuiltins()

    try {
      val appName = getAppName().case({ return singleErrorResult(fileName, it) }, { it })

      val rawResult = ctx.invokeStrategyCLI(lsp_main_0_0.instance, "Main", "-i", appName + ".app", "--dir", workspaceInterface.compilerRoot.toString()) as StrategoTuple
      val errors = getMessages(rawResult.getSubterm(0) as StrategoList)
      val warnings = getMessages(rawResult.getSubterm(1) as StrategoList)
      val additionalInfo = rawResult.getSubterm(2) as StrategoList

      val newDirty = errors.map { it.location.file }.toSet() + warnings.map { it.location.file }.toSet()
      val cleared = dirtyFiles - newDirty
      dirtyFiles = newDirty

      return LspAnalysisResult(errors, warnings, additionalInfo.toList(), cleared.toList())
    } catch (e: StrategoExit) {
      println("Exception occured while analysing file $fileName: $e")
      e.printStackTrace()
      return singleErrorResult(fileName, "Parse Error")
    }
  }

  fun findDefinition(loc: StrategoLocation): StrategoLocation? {
    val ctx: Context = Main.init()
    ctx.setStandAlone(true)

    val path = workspaceInterface.compilerPathFor(loc.file)
    if (path == null) {
      return null
    }

    ensureBuiltins()

    try {
      val appName = getAppName().case({ return null }, { it })

      val relativeFile = workspaceInterface.compilerRoot.relativize(path).toString().let {
        // this might get fixed in webdslc at some point
        (if (it == appName + ".app") "" else "./") + it
      }

      val strategy: Strategy = if (resolveDirty) {
        // println("normal")
        lsp_resolve_0_0.instance
      } else {
        // println("cached")
        // lsp_resolve_cached_0_0.instance
        lsp_resolve_0_0.instance // cached version doesn't seem to work for now
      }
      val rawResult = ctx.invokeStrategyCLI(strategy, "Main", "-i", appName + ".app", "--dir", workspaceInterface.compilerRoot.toString(), "-file", relativeFile, "-line", loc.start.line.toString(), "-column", loc.start.column.toString()) as StrategoAppl?
      resolveDirty = false

      // println("Raw result: $rawResult")
      if (rawResult == null) {
        return null
      }

      return parseAtAnnotation(rawResult)
    } catch (e: StrategoExit) {
      println("Exception occured while resolving definition at $loc: $e")
      e.printStackTrace()
      return null
    }
  }

  fun complete(loc: StrategoLocation): List<StrategoCompletion> {
    val ctx: Context = Main.init()
    ctx.setStandAlone(true)

    val path = workspaceInterface.compilerPathFor(loc.file)
    if (path == null) {
      return listOf()
    }

    ensureBuiltins()

    try {
      val appName = getAppName().case({ return listOf() }, { it })

      val relativeFile = workspaceInterface.compilerRoot.relativize(path).toString().let {
        // this might get fixed in webdslc at some point
        (if (it == appName + ".app") "" else "./") + it
      }

      val rawResult = ctx.invokeStrategyCLI(lsp_complete_0_0.instance, "Main", "-i", appName + ".app", "--dir", workspaceInterface.compilerRoot.toString(), "-file", relativeFile, "-line", loc.start.line.toString(), "-column", loc.start.column.toString()) as StrategoList?
      completeDirty = false

      if (rawResult == null) {
        return listOf()
      }

      return rawResult.getAllSubterms().asList().map { parseCompletion(it) }
    } catch (e: StrategoExit) {
      println("Exception occured while completing at $loc: $e")
      e.printStackTrace()
      return listOf()
    }
  }

  fun semanticTokens(fileName: String): List<StrategoSemanticToken> {
    val ctx: Context = Main.init()
    ctx.setStandAlone(true)

    val path = workspaceInterface.compilerPathFor(fileName)
    if (path == null) {
      return listOf()
    }

    try {
      val relativeFile = workspaceInterface.compilerRoot.relativize(path).toString()

      val rawResult = ctx.invokeStrategyCLI(lsp_parse_0_0.instance, "Main", "-i", relativeFile, "--dir", workspaceInterface.compilerRoot.toString()) as StrategoList?

      if (rawResult == null) {
        return listOf()
      }

      return rawResult.getAllSubterms().asList().map { parseSemanticToken(it) }
    } catch (e: StrategoExit) {
      println("Exception occured while parsing $fileName: $e")
      e.printStackTrace()
      return listOf()
    }
  }

  fun extractLocation(term: IStrategoTerm): StrategoLocation? {
    val atAnnotation = term.annotations.find {
      val appl = it as? StrategoAppl

      if (appl != null) {
        appl.name == "At"
      } else {
        false
      }
    }

    return atAnnotation?.let {
      return parseAtAnnotation(it)
    } ?: StrategoLocation(workspaceInterface.clientRoot.resolve(workspaceInterface.appName?.let { it + ".app" } ?: "application.ini").toString(), StrategoPosition(1, 1), StrategoPosition(1, 1))
  }

  fun parseAtAnnotation(term: IStrategoTerm): StrategoLocation? {
    val file = workspaceInterface.clientRoot.resolve(Path((term.getSubterm(0) as StrategoString).stringValue()).normalize()).toString()
    val start = StrategoPosition((term.getSubterm(1) as StrategoInt).intValue(), (term.getSubterm(2) as StrategoInt).intValue())
    val end = if (term.subtermCount == 3) {
      start
    } else {
      StrategoPosition((term.getSubterm(3) as StrategoInt).intValue(), (term.getSubterm(4) as StrategoInt).intValue())
    }
    return StrategoLocation(file, start, end)
  }

  fun parseCompletion(term: IStrategoTerm): StrategoCompletion = StrategoCompletion(
    (term.getSubterm(0) as StrategoString).stringValue(),
    (term.getSubterm(1) as StrategoString).stringValue(),
  )

  fun parseSemanticToken(term: IStrategoTerm): StrategoSemanticToken {
    val token = (term.getSubterm(0) as StrategoString).stringValue()
    val rawLoc = term.getSubterm(1) as StrategoAppl
    val position = StrategoPosition((rawLoc.getSubterm(0) as StrategoInt).intValue(), (rawLoc.getSubterm(1) as StrategoInt).intValue())
    val tokenType = WebDSLSemanticTokenType.valueOf((term.getSubterm(2) as StrategoString).stringValue().uppercase())
    return StrategoSemanticToken(token, position, tokenType)
  }

  fun getMessages(messageList: StrategoList): List<StrategoMessage> {
    return messageList.map {
      val tuple = it as StrategoTuple
      val term = tuple.getSubterm(0)
      val message = (tuple.getSubterm(1) as StrategoString).stringValue()
      val location = extractLocation(term)
      val filePath = location?.file

      return@map if (filePath == null || filePath.endsWith(".servletapp/src-webdsl-template/built-in.app")) {
        null
      } else {
        StrategoMessage(term, message, location)
      }
    }.filterNotNull()
  }

  fun singleErrorResult(fileName: String, error: String): LspAnalysisResult {
    val oldDirty = dirtyFiles.toList()
    dirtyFiles = setOf(fileName)
    return LspAnalysisResult(listOf(StrategoMessage(null, error, StrategoLocation(fileName, StrategoPosition(1, 1), StrategoPosition(1, 1)))), listOf(), listOf(), oldDirty)
  }
}
