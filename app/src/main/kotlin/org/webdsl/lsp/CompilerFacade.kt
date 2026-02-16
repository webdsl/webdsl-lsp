package org.webdsl.lsp

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
import org.webdsl.lsp.utils.Either
import org.webdsl.lsp.utils.case
import org.webdsl.lsp.utils.parseFileURI
import org.webdsl.webdslc.Main
import org.webdsl.webdslc.lsp_main_0_0
import org.webdsl.webdslc.lsp_resolve_0_0
import kotlin.io.copyTo
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

data class StrategoLocation(val file: String, val line: Int, val column: Int) {
  constructor(loc: Location) : this(parseFileURI(loc.uri)!!.path, loc.range.start.line + 1, loc.range.start.character + 1)

  fun toLspLocation(): Location {
    return Location(
      "file://" + file,
      Range(Position(line - 1, column - 1), Position(line - 1, column)),
    )
  }
}

data class StrategoMessage(val relatedTerm: IStrategoTerm?, val messageText: String, val location: StrategoLocation) {
  fun toDiagnostic(diagnosticSeverity: DiagnosticSeverity): Diagnostic {
    return Diagnostic().apply {
      severity = diagnosticSeverity
      message = messageText
      // TODO: properly calculate ranges
      range = Range(Position(location.line - 1, location.column - 1), Position(location.line - 1, location.column))
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

class CompilerFacade(val workspaceInterface: WorkspaceInterface) {
  var dirtyFiles: Set<String> = setOf()

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
    val relativeFile = workspaceInterface.compilerRoot.relativize(path).toString()

    ensureBuiltins()

    try {
      val appName = getAppName().case({ return null }, { it })

      val rawResult = ctx.invokeStrategyCLI(lsp_resolve_0_0.instance, "Main", "-i", appName + ".app", "--dir", workspaceInterface.compilerRoot.toString(), "-file", relativeFile, "-line", loc.line.toString(), "-column", loc.column.toString()) as StrategoAppl?

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
    } ?: StrategoLocation(workspaceInterface.clientRoot.resolve(workspaceInterface.appName?.let { it + ".app" } ?: "application.ini").toString(), 1, 1)
  }

  fun parseAtAnnotation(term: IStrategoTerm): StrategoLocation? = StrategoLocation(
    workspaceInterface.clientRoot.resolve(Path((term.getSubterm(0) as StrategoString).stringValue()).normalize()).toString(),
    (term.getSubterm(1) as StrategoInt).intValue(),
    (term.getSubterm(2) as StrategoInt).intValue(),
  )

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
    return LspAnalysisResult(listOf(StrategoMessage(null, error, StrategoLocation(fileName, 1, 1))), listOf(), listOf(), oldDirty)
  }
}
