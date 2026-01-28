package org.webdsl.lsp

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
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
import org.webdsl.webdslc.Main
import org.webdsl.webdslc.lsp_main_0_0
import kotlin.io.copyTo
import kotlin.io.path.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.writeText

data class StrategoLocation(val file: String, val line: Int, val column: Int)

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
  // TODO: separate by file path
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

  fun analyse(fileName: String): LspAnalysisResult {
    val ctx: Context = Main.init()
    ctx.setStandAlone(true)

    val path = workspaceInterface.compilerPathFor(fileName)
    if (path == null) {
      return LspAnalysisResult(listOf(), listOf(), listOf(), listOf())
    }
    val builtinPath = workspaceInterface.compilerRoot.resolve(".servletapp/src-webdsl-template/built-in.app")
    if (!builtinPath.exists()) {
      builtinPath.apply {
        val builtin = object {}::class.java.getResourceAsStream("/webdsl/template-webdsl/built-in.app")?.bufferedReader()?.readText()!!
        createParentDirectories()
        writeText(builtin)
      }
    }

    try {
      // we don't get .ini files modification notifications (for now)
      workspaceInterface.clientRoot.resolve("application.ini")
        .toFile()
        .copyTo(
          workspaceInterface.compilerRoot.resolve("application.ini").toFile(),
          overwrite = true,
        )

      val appName = workspaceInterface.appName
      if (appName == null) {
        return singleErrorResult(fileName, "Couldn't find \"appname\" property in application.ini")
      }

      val mainFilePath = workspaceInterface.compilerRoot.resolve(appName + ".app")
      if (!mainFilePath.exists()) {
        return singleErrorResult(fileName, "File $appName.app doesn't exist")
      }

      // --enable-caching ??
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

  fun getLocation(term: IStrategoTerm): StrategoLocation? {
    val atAnnotation = term.annotations.find {
      val appl = it as? StrategoAppl

      if (appl != null) {
        appl.name == "At"
      } else {
        false
      }
    }

    return atAnnotation?.let {
      return StrategoLocation(
        workspaceInterface.clientRoot.resolve(Path((it.getSubterm(0) as StrategoString).stringValue()).normalize()).toString(),
        (it.getSubterm(1) as StrategoInt).intValue(),
        (it.getSubterm(2) as StrategoInt).intValue(),
      )
    } ?: StrategoLocation(workspaceInterface.clientRoot.resolve(workspaceInterface.appName?.let { it + ".app" } ?: "application.ini").toString(), 1, 1)
  }

  fun getMessages(messageList: StrategoList): List<StrategoMessage> {
    return messageList.map {
      val tuple = it as StrategoTuple
      val term = tuple.getSubterm(0)
      val message = (tuple.getSubterm(1) as StrategoString).stringValue()
      val location = getLocation(term)
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
