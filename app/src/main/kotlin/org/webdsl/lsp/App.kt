package org.webdsl.lsp

import org.eclipse.lsp4j.launch.LSPLauncher
import WebDSLWorkspaceService

fun main() {
    var myServer = WebDSLLanguageServer(WebDSLTextDocumentService(), WebDSLWorkspaceService())
    val l = LSPLauncher.createServerLauncher(myServer, System.`in`, System.out)
    val startListening = l.startListening()
    myServer.setRemoteProxy(l.getRemoteProxy())
    startListening.get()
}
