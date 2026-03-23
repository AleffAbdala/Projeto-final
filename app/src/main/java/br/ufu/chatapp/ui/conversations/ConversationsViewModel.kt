package br.ufu.chatapp.ui.conversations

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufu.chatapp.data.model.ChatUser
import br.ufu.chatapp.data.model.Conversation
import br.ufu.chatapp.data.model.MessageType
import br.ufu.chatapp.data.repository.ChatRepository
import br.ufu.chatapp.data.repository.ContactsRepository
import br.ufu.chatapp.data.remote.SupabaseStorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val contactsRepository: ContactsRepository,
    private val supabaseStorageService: SupabaseStorageService
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _contacts = MutableStateFlow<List<ChatUser>>(emptyList())
    private val _participantDirectory = MutableStateFlow<Map<String, ChatUser>>(emptyMap())
    private val _errorMessage = MutableStateFlow<String?>(null)

    val contacts: StateFlow<List<ChatUser>> = _contacts.asStateFlow()
    val participantDirectory: StateFlow<Map<String, ChatUser>> = _participantDirectory.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    val availableGroupMembers: StateFlow<List<ChatUser>> = combine(contacts, participantDirectory) { contactsList, participantMap ->
        (contactsList + participantMap.values.toList())
            .distinctBy { it.id }
            .filter { it.id != currentUserId() }
            .sortedBy { it.name.ifBlank { it.email }.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conversations: StateFlow<List<Conversation>> = combine(
        chatRepository.observeConversations(),
        query,
        participantDirectory
    ) { list, q, participants ->
        if (q.isBlank()) {
            list
        } else {
            val normalizedQuery = q.trim()
            list.filter { conversation ->
                val partner = if (conversation.isGroup) {
                    null
                } else {
                    conversation.participants
                        .firstOrNull { it != currentUserId() }
                        ?.let(participants::get)
                }
                val searchable = listOf(
                    conversation.name,
                    conversation.lastMessage,
                    partner?.name.orEmpty(),
                    partner?.email.orEmpty()
                ).joinToString(" ")
                searchable.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadContacts()
        observeConversationParticipants()
    }

    fun currentUserId(): String = chatRepository.currentUserId()

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun loadContacts() {
        viewModelScope.launch {
            runCatching { contactsRepository.loadCurrentContacts() }
                .onSuccess {
                    _contacts.value = it
                    _participantDirectory.value = _participantDirectory.value + it.associateBy { user -> user.id }
                }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel carregar os contatos" }
        }
    }

    private fun observeConversationParticipants() {
        viewModelScope.launch {
            chatRepository.observeConversations().collect { conversations ->
                val participantIds = conversations
                    .flatMap { it.participants }
                    .distinct()
                if (participantIds.isEmpty()) {
                    _participantDirectory.value = _contacts.value.associateBy { it.id }
                    return@collect
                }
                runCatching { contactsRepository.loadUsersByIds(participantIds) }
                    .onSuccess { users ->
                        _participantDirectory.value = users.associateBy { it.id }
                    }
                    .onFailure {
                        if (_participantDirectory.value.isEmpty()) {
                            _participantDirectory.value = _contacts.value.associateBy { user -> user.id }
                        }
                    }
            }
        }
    }

    fun createDirectChat(otherUserId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { chatRepository.getOrCreateDirectConversation(otherUserId) }
                .onSuccess { id ->
                    if (id.isNotBlank()) onCreated(id)
                }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel abrir a conversa" }
        }
    }

    fun createGroup(name: String, members: List<String>, photoUri: Uri? = null, photoName: String = "grupo.jpg") {
        viewModelScope.launch {
            if (name.isBlank()) {
                _errorMessage.value = "Informe o nome do grupo"
                return@launch
            }
            if (members.isEmpty()) {
                _errorMessage.value = "Selecione pelo menos um contato"
                return@launch
            }
            val groupPhotoUrl = photoUri?.let { uri ->
                runCatching {
                    supabaseStorageService.upload(
                        uri = uri,
                        conversationId = "groupPhotos",
                        type = MessageType.IMAGE,
                        mediaName = photoName
                    )
                }.getOrNull().orEmpty()
            }.orEmpty()
            runCatching {
                chatRepository.createConversation(
                    name = name.trim(),
                    participants = members,
                    isGroup = true,
                    photoUrl = groupPhotoUrl
                )
            }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel criar o grupo" }
        }
    }

    fun updateGroup(conversationId: String, name: String, members: List<String>, photoUri: Uri? = null, photoName: String = "grupo.jpg") {
        viewModelScope.launch {
            val groupPhotoUrl = photoUri?.let { uri ->
                runCatching {
                    supabaseStorageService.upload(
                        uri = uri,
                        conversationId = conversationId.ifBlank { "groupPhotos" },
                        type = MessageType.IMAGE,
                        mediaName = photoName
                    )
                }.getOrNull()
            }
            runCatching { chatRepository.updateGroup(conversationId, name, members, groupPhotoUrl) }
                .onFailure { _errorMessage.value = it.message ?: "Nao foi possivel atualizar o grupo" }
        }
    }

    fun deleteConversations(conversationIds: Set<String>) {
        if (conversationIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                conversationIds.forEach { chatRepository.deleteConversation(it) }
            }.onFailure {
                _errorMessage.value = it.message ?: "Nao foi possivel apagar as conversas selecionadas"
            }
        }
    }
}
