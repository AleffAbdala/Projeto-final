package br.ufu.chatapp.ui.contacts

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufu.chatapp.data.model.ChatUser
import br.ufu.chatapp.data.repository.ContactsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository
) : ViewModel() {

    private val _contacts = MutableStateFlow<List<ChatUser>>(emptyList())
    private val _directoryResults = MutableStateFlow<List<ChatUser>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)

    val contacts: StateFlow<List<ChatUser>> = _contacts.asStateFlow()
    val directoryResults: StateFlow<List<ChatUser>> = _directoryResults.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun loadContacts() {
        viewModelScope.launch {
            runCatching { contactsRepository.loadCurrentContacts() }
                .onSuccess {
                    _contacts.value = it
                }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel carregar os contatos" }
        }
    }

    fun searchUsers(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            _directoryResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            runCatching { contactsRepository.searchDirectoryUsers(normalizedQuery) }
                .onSuccess { results ->
                    val currentContactIds = _contacts.value.map { it.id }.toSet()
                    _directoryResults.value = results.filter { it.id !in currentContactIds }
                }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel buscar usuarios" }
        }
    }

    fun addContact(userId: String) {
        viewModelScope.launch {
            runCatching { contactsRepository.addContact(userId) }
                .onSuccess {
                    loadContacts()
                    _directoryResults.value = _directoryResults.value.filterNot { it.id == userId }
                }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel adicionar o contato" }
        }
    }

    fun removeContact(userId: String) {
        viewModelScope.launch {
            runCatching { contactsRepository.removeContact(userId) }
                .onSuccess { loadContacts() }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel remover o contato" }
        }
    }

    fun importFromDevice(resolver: ContentResolver) {
        viewModelScope.launch {
            runCatching { contactsRepository.importContactsFromDevice(resolver) }
                .onSuccess { imported ->
                    _directoryResults.value = imported.filter { importedUser ->
                        _contacts.value.none { it.id == importedUser.id }
                    }
                }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel importar contatos" }
        }
    }
}
