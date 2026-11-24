package com.stitch.authenticator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RestoreBackupViewModel(
  private val backupManager: BackupManager,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RestoreBackupUiState(checking = true))
  val uiState: StateFlow<RestoreBackupUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      val hasBackup = runCatching { backupManager.hasBackup() }.getOrDefault(false)
      _uiState.value = RestoreBackupUiState(checking = false, backupFound = hasBackup)
    }
  }

  fun restore() {
    val s = _uiState.value
    if (s.checking || s.restoring || !s.backupFound) return
    _uiState.value = s.copy(restoring = true, error = null)
    viewModelScope.launch {
      val result = backupManager.restoreIfPresent()
      _uiState.value = _uiState.value.copy(
        restoring = false,
        restored = result.getOrDefault(false),
        backupFound = false,
        error = result.exceptionOrNull()?.message,
      )
    }
  }

  fun dismiss() {
    _uiState.value = _uiState.value.copy(backupFound = false)
  }
}

data class RestoreBackupUiState(
  val checking: Boolean = false,
  val backupFound: Boolean = false,
  val restoring: Boolean = false,
  val restored: Boolean = false,
  val error: String? = null,
)
