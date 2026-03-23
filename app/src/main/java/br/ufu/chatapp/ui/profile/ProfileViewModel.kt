package br.ufu.chatapp.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufu.chatapp.data.model.ChatUser
import br.ufu.chatapp.data.model.MessageType
import br.ufu.chatapp.data.repository.AuthRepository
import br.ufu.chatapp.data.remote.SupabaseStorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val supabaseStorageService: SupabaseStorageService
) : ViewModel() {

    private val _profile = MutableStateFlow<ChatUser?>(null)
    val profile: StateFlow<ChatUser?> = _profile.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _profile.value = authRepository.currentUserProfile()
        }
    }

    fun update(name: String, photoUrl: String, status: String) {
        viewModelScope.launch {
            authRepository.updateProfile(name, photoUrl, status)
            loadProfile()
        }
    }

    fun updateProfilePhoto(uri: Uri, fileName: String = "perfil.jpg") {
        viewModelScope.launch {
            val currentPhoto = _profile.value?.photoUrl.orEmpty()
            val uploadedUrl = runCatching {
                supabaseStorageService.upload(
                    uri = uri,
                    conversationId = "profilePhotos",
                    type = MessageType.IMAGE,
                    mediaName = fileName
                )
            }.getOrDefault(currentPhoto)
            authRepository.updateProfile(
                name = _profile.value?.name.orEmpty(),
                photoUrl = uploadedUrl,
                status = _profile.value?.status ?: "online"
            )
            loadProfile()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
