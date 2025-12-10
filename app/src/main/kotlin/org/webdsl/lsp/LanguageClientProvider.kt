package org.webdsl.lsp

import org.eclipse.lsp4j.services.LanguageClient

interface LanguageClientProvider {
  val client: LanguageClient?
}
