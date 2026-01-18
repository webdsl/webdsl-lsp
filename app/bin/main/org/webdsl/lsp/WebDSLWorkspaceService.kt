package org.webdsl.lsp

import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.services.WorkspaceService
import org.webdsl.lsp.utils.parseFileURI

class WebDSLWorkspaceService(val clientProvider: LanguageClientProvider) : WorkspaceService {
  override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
  }

  override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
  }

  override fun didCreateFiles(params: CreateFilesParams) {
    params.files.mapNotNull { parseFileURI(it.uri)?.path }.forEach {
      clientProvider.workspaceInterface?.create(it)
    }
  }

  override fun didDeleteFiles(params: DeleteFilesParams) {
    params.files.mapNotNull { parseFileURI(it.uri)?.path }.forEach {
      clientProvider.workspaceInterface?.delete(it)
    }
  }

  override fun didRenameFiles(params: RenameFilesParams) {
    params.files.mapNotNull {
      params.files.mapNotNull {
        val old = parseFileURI(it.oldUri)!!.path
        val new = parseFileURI(it.newUri)!!.path
        old to new
      }.forEach {
        clientProvider.workspaceInterface?.rename(it.first, it.second)
      }
    }
  }
}
