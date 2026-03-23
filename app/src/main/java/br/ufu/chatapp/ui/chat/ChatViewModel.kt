package br.ufu.chatapp.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.ufu.chatapp.data.model.ChatUser
import br.ufu.chatapp.data.model.Conversation
import br.ufu.chatapp.data.model.Message
import br.ufu.chatapp.data.model.MessageStatus
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val contactsRepository: ContactsRepository,
    private val supabaseStorageService: SupabaseStorageService
) : ViewModel() {

    private val conversationId = MutableStateFlow("")
    private val search = MutableStateFlow("")
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _groupCandidates = MutableStateFlow<List<ChatUser>>(emptyList())

    val errorMessage: StateFlow<String?> = _errorMessage
    val groupCandidates: StateFlow<List<ChatUser>> = _groupCandidates.asStateFlow()

    val conversation: StateFlow<Conversation?> = conversationId
        .flatMapLatest { cId -> if (cId.isBlank()) flowOf(null) else chatRepository.observeConversation(cId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val chatPartner: StateFlow<ChatUser?> = conversation
        .flatMapLatest { conv ->
            val partnerId = conv?.participants
                ?.firstOrNull { it != chatRepository.currentUserId() }
                .orEmpty()
            if (conv == null || conv.isGroup || partnerId.isBlank()) {
                flowOf(null)
            } else {
                chatRepository.observeUser(partnerId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val allMessages: StateFlow<List<Message>> = conversationId
        .flatMapLatest { cId ->
            if (cId.isBlank()) {
                flowOf(emptyList())
            } else {
                chatRepository.observeMessages(cId)
                    .onStart { emit(emptyList()) }
                    .onEach { online ->
                        if (online.isNotEmpty()) {
                            chatRepository.markAsDelivered(cId, online)
                            chatRepository.cacheMessages(cId, online)
                        }
                    }
                    .combine(chatRepository.observeMessagesOffline(cId)) { online, offline ->
                        if (online.isNotEmpty()) online else offline.map {
                            Message(
                                id = it.id,
                                conversationId = it.conversationId,
                                senderId = it.senderId,
                                senderName = it.senderName,
                                content = it.content,
                                mediaUrl = it.mediaUrl,
                                mediaName = it.mediaName,
                                latitude = it.latitude,
                                longitude = it.longitude,
                                timestamp = it.timestamp,
                                type = it.type,
                                isPinned = it.isPinned,
                                statusByUser = mapOf(chatRepository.currentUserId() to it.status)
                            )
                        }
                    }
                    .onEach { current ->
                        if (current.isNotEmpty()) {
                            chatRepository.markConversationRead(cId, current)
                        }
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val messages: StateFlow<List<Message>> = allMessages
        .combine(search) { all, q ->
            if (q.isBlank()) all else all.filter {
                it.content.contains(q, true) || it.mediaName.contains(q, true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pinnedMessage: StateFlow<Message?> = combine(allMessages, conversation) { list, conv ->
        val pinnedId = conv?.pinnedMessageId
        when {
            pinnedId.isNullOrBlank() -> list.firstOrNull { it.isPinned }
            else -> list.firstOrNull { it.id == pinnedId } ?: list.firstOrNull { it.isPinned }
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun currentUserId(): String = chatRepository.currentUserId()

    fun setConversation(id: String) {
        conversationId.value = id
        loadGroupCandidates()
    }

    fun setSearch(value: String) {
        search.value = value
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun sendText(text: String) {
        val cId = conversationId.value
        if (text.isBlank() || cId.isBlank()) return
        launchChatAction("Nao foi possivel enviar a mensagem") {
            chatRepository.sendText(cId, text)
        }
    }

    fun sendSticker(sticker: String) {
        val cId = conversationId.value
        if (sticker.isBlank() || cId.isBlank()) return
        launchChatAction("Nao foi possivel enviar o sticker") {
            chatRepository.sendSticker(cId, sticker)
        }
    }

    fun sendMedia(uri: Uri, type: MessageType, mediaName: String = "") {
        val cId = conversationId.value
        if (cId.isBlank()) return
        launchChatAction("Nao foi possivel enviar a midia") {
            chatRepository.sendMedia(cId, uri, type, mediaName)
        }
    }

    fun sendLocation(lat: Double, lng: Double) {
        val cId = conversationId.value
        if (cId.isBlank()) return
        launchChatAction("Nao foi possivel compartilhar a localizacao") {
            chatRepository.sendLocation(cId, lat, lng)
        }
    }

    fun pinMessage(messageId: String?) {
        val cId = conversationId.value
        if (cId.isBlank()) return
        launchChatAction("Nao foi possivel atualizar a mensagem fixada") {
            chatRepository.pinMessage(cId, messageId)
        }
    }

    fun updateGroup(name: String, participants: List<String>, photoUri: Uri? = null, photoName: String = "grupo.jpg") {
        val cId = conversationId.value
        if (cId.isBlank()) return
        launchChatAction("Nao foi possivel atualizar o grupo") {
            val groupPhotoUrl = photoUri?.let { uri ->
                supabaseStorageService.upload(
                    uri = uri,
                    conversationId = cId,
                    type = MessageType.IMAGE,
                    mediaName = photoName
                )
            }
            chatRepository.updateGroup(cId, name, participants, groupPhotoUrl)
        }
    }

    fun deleteMessages(messageIds: Set<String>) {
        val cId = conversationId.value
        if (cId.isBlank() || messageIds.isEmpty()) return
        launchChatAction("Nao foi possivel apagar as mensagens selecionadas") {
            chatRepository.deleteMessages(cId, messageIds.toList())
        }
    }

    fun effectiveStatus(message: Message): MessageStatus {
        val myId = currentUserId()
        return if (message.senderId != myId) {
            when (message.statusByUser[myId]) {
                MessageStatus.READ.name -> MessageStatus.READ
                MessageStatus.DELIVERED.name -> MessageStatus.DELIVERED
                else -> MessageStatus.SENT
            }
        } else {
            val others = message.statusByUser.filterKeys { it != myId }.values
            when {
                others.any { it == MessageStatus.READ.name } -> MessageStatus.READ
                others.any { it == MessageStatus.DELIVERED.name } -> MessageStatus.DELIVERED
                else -> MessageStatus.SENT
            }
        }
    }

    private fun launchChatAction(defaultMessage: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onFailure { error ->
                    _errorMessage.value = error.message?.takeIf { it.isNotBlank() } ?: defaultMessage
                }
        }
    }

    private fun loadGroupCandidates() {
        viewModelScope.launch {
            val currentConversation = conversation.value
            val conversationUsers = currentConversation?.participants
                ?.takeIf { it.isNotEmpty() }
                ?.let { contactsRepository.loadUsersByIds(it) }
                .orEmpty()
            val currentContacts = contactsRepository.loadCurrentContacts()
            _groupCandidates.value = (currentContacts + conversationUsers)
                .distinctBy { it.id }
                .filter { it.id != currentUserId() }
                .sortedBy { it.name.ifBlank { it.email }.lowercase() }
        }
    }
}
