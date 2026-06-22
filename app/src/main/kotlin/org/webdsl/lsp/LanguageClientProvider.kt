/**
 * This interface is required to avoid circular dependencies between WebDSLLanguageServer
 * and WebDSL{TextDocument,Workspace}Service.
 */
package org.webdsl.lsp

import org.eclipse.lsp4j.services.LanguageClient

interface LanguageClientProvider {
  val client: LanguageClient?
  val workspaceInterface: WorkspaceInterface?
}
