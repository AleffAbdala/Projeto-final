package br.ufu.chatapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufu.chatapp.data.repository.AuthRepository
import br.ufu.chatapp.util.ResultState
import com.google.firebase.auth.PhoneAuthCredential
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthAction {
    LOGIN,
    REGISTER,
    RESET_PASSWORD,
    GOOGLE_LOGIN,
    PHONE_LOGIN
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ResultState<AuthAction>?>(null)
    val state: StateFlow<ResultState<AuthAction>?> = _state.asStateFlow()

    private val _sessionValid = MutableStateFlow(true)
    val sessionValid: StateFlow<Boolean> = _sessionValid.asStateFlow()

    private val _activeSessionsCount = MutableStateFlow(0)
    val activeSessionsCount: StateFlow<Int> = _activeSessionsCount.asStateFlow()

    private var sessionWatchJob: Job? = null

    fun isLoggedIn(): Boolean = authRepository.currentUserId() != null

    fun login(email: String, password: String) {
        runAuthAction(AuthAction.LOGIN, "Falha no login") { authRepository.login(email, password) }
    }

    fun register(name: String, email: String, password: String) {
        runAuthAction(AuthAction.REGISTER, "Falha no cadastro") { authRepository.register(name, email, password) }
    }

    fun resetPassword(email: String) {
        runAuthAction(AuthAction.RESET_PASSWORD, "Falha ao enviar e-mail") { authRepository.sendPasswordReset(email) }
    }

    fun loginWithGoogleToken(idToken: String) {
        runAuthAction(AuthAction.GOOGLE_LOGIN, "Falha no login Google") { authRepository.loginWithGoogleIdToken(idToken) }
    }

    fun loginWithPhone(verificationId: String, code: String) {
        runAuthAction(AuthAction.PHONE_LOGIN, "Falha no login telefone") { authRepository.loginWithPhone(verificationId, code) }
    }

    fun loginWithPhoneCredential(credential: PhoneAuthCredential) {
        runAuthAction(AuthAction.PHONE_LOGIN, "Falha no login telefone") { authRepository.loginWithPhoneCredential(credential) }
    }

    fun refreshSession() {
        viewModelScope.launch {
            runCatching { authRepository.refreshCurrentSession() }
            refreshActiveSessionsCount()
            runCatching { watchSession() }
        }
    }

    fun watchSession() {
        sessionWatchJob?.cancel()
        if (!isLoggedIn()) {
            _sessionValid.value = true
            return
        }
        sessionWatchJob = viewModelScope.launch {
            authRepository.observeCurrentSessionActive().collect { active ->
                _sessionValid.value = active
                if (!active) {
                    authRepository.logout()
                }
            }
        }
    }

    fun refreshActiveSessionsCount() {
        viewModelScope.launch {
            _activeSessionsCount.value = authRepository.activeSessionsCount()
        }
    }

    fun logoutOtherSessions() {
        viewModelScope.launch {
            authRepository.logoutOtherSessions()
            refreshActiveSessionsCount()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }

    fun clearState() {
        _state.value = null
    }

    private fun runAuthAction(action: AuthAction, defaultMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = ResultState.Loading
            _state.value = runCatching { block() }
                .fold(
                    onSuccess = {
                        refreshActiveSessionsCount()
                        watchSession()
                        ResultState.Success(action)
                    },
                    onFailure = { ResultState.Error(it.message ?: defaultMessage, it) }
                )
        }
    }
}
