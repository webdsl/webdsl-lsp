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

data class LspAnalysisResult(val errors: List<StrategoMessage>, val warnings: List<StrategoMessage>, val additionalInfo: List<IStrategoTerm>) {
  // TODO: separate by file path
  fun toDiagnosticList(): List<Diagnostic> {
    return errors.map { it.toDiagnostic(DiagnosticSeverity.Error) } + warnings.map { it.toDiagnostic(DiagnosticSeverity.Warning) }
  }
}

class CompilerFacade {
  fun analyse(fileName: String): LspAnalysisResult {
    val ctx: Context = Main.init()
    ctx.setStandAlone(true)

    val path = Path(fileName)
    val builtinPath = path.parent.resolve(".servletapp/src-webdsl-template/built-in.app")
    if (!builtinPath.exists()) {
      builtinPath.apply {
        val builtin = object {}::class.java.getResourceAsStream("/webdsl/template-webdsl/built-in.app")?.bufferedReader()?.readText()!!
        createParentDirectories()
        writeText(builtin)
      }
    }

    try {
      val rawResult = ctx.invokeStrategyCLI(lsp_main_0_0.instance, "Main", "-i", path.name, "--dir", path.parent.toString()) as StrategoTuple
      val errors = getMessages(rawResult.getSubterm(0) as StrategoList)
      val warnings = getMessages(rawResult.getSubterm(1) as StrategoList)
      val additionalInfo = rawResult.getSubterm(2) as StrategoList
      return LspAnalysisResult(errors, warnings, additionalInfo.toList())
    } catch (e: StrategoExit) {
      println("Exception occured while analysing file $fileName: $e")
      e.printStackTrace()
      return LspAnalysisResult(listOf(StrategoMessage(null, "Parse Error", StrategoLocation(fileName, 1, 1))), listOf(), listOf())
    }
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
      (it.getSubterm(0) as StrategoString).stringValue(),
      (it.getSubterm(1) as StrategoInt).intValue(),
      (it.getSubterm(2) as StrategoInt).intValue(),
    )
  }
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
