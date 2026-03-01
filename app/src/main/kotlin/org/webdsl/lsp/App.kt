package org.webdsl.lsp

import org.eclipse.lsp4j.launch.LSPLauncher
import java.net.ServerSocket

fun main(args: Array<String>) {
  val port = args.getOrNull(0)?.toIntOrNull() ?: 0
  val s = ServerSocket(port)
  println("WebDSL language server launcher has been started and is listening on port ${s.localPort}!")

  while (true) {
    val client = s.accept()
    Thread {
      var myServer = WebDSLLanguageServer()
      val l = LSPLauncher.createServerLauncher(myServer, client.inputStream, client.outputStream)
      val startListening = l.startListening()
      myServer.setRemoteProxy(l.getRemoteProxy())
      startListening.get()
    }.start()
  }
}
