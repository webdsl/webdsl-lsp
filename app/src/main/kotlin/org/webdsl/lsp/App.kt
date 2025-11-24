package org.webdsl.lsp

import WebDSLWorkspaceService
import org.eclipse.lsp4j.launch.LSPLauncher

fun main() {
  var myServer = WebDSLLanguageServer(WebDSLTextDocumentService(), WebDSLWorkspaceService())
  val l = LSPLauncher.createServerLauncher(myServer, System.`in`, System.out)
  val startListening = l.startListening()
  myServer.setRemoteProxy(l.getRemoteProxy())
  startListening.get()
}
