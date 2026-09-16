package com.pocketwise.feature.backup

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.pocketwise.core.data.local.UserPreferences
import com.pocketwise.core.data.repository.BackupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data object Working : UiState
        data class Success(val message: String) : UiState
        data class Error(val message: String) : UiState
    }

    private val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    val signInIntent: Intent get() = googleSignInClient.signInIntent

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private val _account = MutableStateFlow<GoogleSignInAccount?>(
        GoogleSignIn.getLastSignedInAccount(context)?.takeIf {
            GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_APPDATA))
        }
    )
    val account: StateFlow<GoogleSignInAccount?> = _account

    val lastBackupMs = userPreferences.lastBackupMs
    val autoBackupEnabled = userPreferences.autoBackupEnabled

    fun onSignInResult(data: Intent?) {
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            _account.value = account
        } catch (e: ApiException) {
            _uiState.value = UiState.Error("Sign-in failed (code ${e.statusCode})")
        }
    }

    fun backup() {
        val account = _account.value ?: return
        _uiState.value = UiState.Working
        viewModelScope.launch {
            backupRepository.backup(account).fold(
                onSuccess = {
                    userPreferences.setLastBackupMs(System.currentTimeMillis())
                    _uiState.value = UiState.Success("Backup complete")
                },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Backup failed") }
            )
        }
    }

    fun restore() {
        val account = _account.value ?: return
        _uiState.value = UiState.Working
        viewModelScope.launch {
            backupRepository.restore(account).fold(
                onSuccess = { _uiState.value = UiState.Success("Restore complete") },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Restore failed") }
            )
        }
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            _account.value = null
            _uiState.value = UiState.Idle
        }
    }

    fun scheduleAutoBackup(enabled: Boolean) {
        viewModelScope.launch { userPreferences.setAutoBackupEnabled(enabled) }
        val wm = WorkManager.getInstance(context)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork("pocketwise_backup", ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            wm.cancelUniqueWork("pocketwise_backup")
        }
    }

    fun dismissMessage() { _uiState.value = UiState.Idle }
}
