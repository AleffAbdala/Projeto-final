package br.ufu.chatapp.ui.conversations

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import br.ufu.chatapp.data.model.Conversation
import br.ufu.chatapp.data.model.ChatUser
import br.ufu.chatapp.util.createTempImageUri
import br.ufu.chatapp.util.getDisplayName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConversationsScreen(
    vm: ConversationsViewModel,
    onOpenChat: (String) -> Unit,
    onOpenContacts: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val items by vm.conversations.collectAsState()
    val availableGroupMembers by vm.availableGroupMembers.collectAsState()
    val participantDirectory by vm.participantDirectory.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    var query by remember { mutableStateOf("") }
    var newGroupName by remember { mutableStateOf("") }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var selectedConversationIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectingConversations by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showGroupPhotoMenu by remember { mutableStateOf(false) }
    var groupPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var groupPhotoName by remember { mutableStateOf("grupo.jpg") }
    var cameraUri by remember { mutableStateOf(createTempImageUri(context)) }
    var previewPhotoUrl by remember { mutableStateOf<String?>(null) }
    var previewPhotoTitle by remember { mutableStateOf("") }
    val currentUserId = vm.currentUserId()

    val groupPhotoCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            groupPhotoUri = cameraUri
            groupPhotoName = "grupo_camera.jpg"
        }
    }
    val groupPhotoGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            groupPhotoUri = it
            groupPhotoName = getDisplayName(context, it)
        }
    }
    val groupPhotoFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            groupPhotoUri = it
            groupPhotoName = getDisplayName(context, it)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraUri = createTempImageUri(context)
            groupPhotoCameraLauncher.launch(cameraUri)
        }
    }

    LaunchedEffect(Unit) {
        vm.loadContacts()
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearErrorMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
                    .padding(start = 18.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "DevMessage",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    if (selectingConversations) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    vm.deleteConversations(selectedConversationIds)
                                    selectedConversationIds = emptySet()
                                    selectingConversations = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Apagar conversas selecionadas",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            TextButton(onClick = {
                                selectedConversationIds = emptySet()
                                selectingConversations = false
                            }) {
                                Text("Cancelar", color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    showSearchBar = !showSearchBar
                                    if (!showSearchBar) {
                                        query = ""
                                        vm.onQueryChange("")
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search,
                                    contentDescription = if (showSearchBar) "Fechar busca" else "Buscar conversas",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Mais opcoes",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Selecionar conversas") },
                                        onClick = {
                                            showMenu = false
                                            selectingConversations = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Novo grupo") },
                                        onClick = {
                                            showMenu = false
                                            showCreateGroupDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Contatos") },
                                        onClick = {
                                            showMenu = false
                                            onOpenContacts()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Alterar perfil") },
                                        onClick = {
                                            showMenu = false
                                            onOpenProfile()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sair") },
                                        onClick = {
                                            showMenu = false
                                            onLogout()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                if (showSearchBar && !selectingConversations) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            vm.onQueryChange(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Buscar conversa") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp)
                    )
                }
                if (selectingConversations) {
                    Text(
                        "${selectedConversationIds.size} conversa(s) selecionada(s)",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    currentUserId = currentUserId,
                    participantDirectory = participantDirectory,
                    isSelected = selectedConversationIds.contains(conversation.id),
                    selecting = selectingConversations,
                    onToggleSelected = { id ->
                        selectedConversationIds = if (selectedConversationIds.contains(id)) {
                            selectedConversationIds - id
                        } else {
                            selectedConversationIds + id
                        }
                    },
                    onPreviewPhoto = { url, title ->
                        previewPhotoUrl = url
                        previewPhotoTitle = title
                    },
                    onOpenChat = onOpenChat
                )
            }
        }
    }

    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        vm.createGroup(newGroupName, selectedMembers.toList(), groupPhotoUri, groupPhotoName)
                        if (newGroupName.isNotBlank() && selectedMembers.isNotEmpty()) {
                            newGroupName = ""
                            selectedMembers = emptySet()
                            groupPhotoUri = null
                            groupPhotoName = "grupo.jpg"
                            showCreateGroupDialog = false
                        }
                    }
                ) {
                    Text("Criar grupo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Novo grupo") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConversationAvatar(photoUrl = groupPhotoUri?.toString().orEmpty(), fallback = newGroupName.ifBlank { "G" })
                        Box {
                            Button(onClick = { showGroupPhotoMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.CameraAlt,
                                    contentDescription = "Selecionar foto do grupo"
                                )
                                Text("Foto do grupo")
                            }
                            DropdownMenu(
                                expanded = showGroupPhotoMenu,
                                onDismissRequest = { showGroupPhotoMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tirar foto") },
                                    onClick = {
                                        showGroupPhotoMenu = false
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            cameraUri = createTempImageUri(context)
                                            groupPhotoCameraLauncher.launch(cameraUri)
                                        } else {
                                            cameraPermission.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Galeria") },
                                    onClick = {
                                        showGroupPhotoMenu = false
                                        groupPhotoGalleryLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Arquivo") },
                                    onClick = {
                                        showGroupPhotoMenu = false
                                        groupPhotoFileLauncher.launch(arrayOf("image/*"))
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Nome do grupo") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    if (availableGroupMembers.isEmpty()) {
                        Text(
                            "Adicione contatos antes de criar um grupo.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                showCreateGroupDialog = false
                                onOpenContacts()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Adicionar contatos")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableGroupMembers, key = { it.id }) { contact ->
                                val isSelected = selectedMembers.contains(contact.id)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedMembers = if (isSelected) {
                                                selectedMembers - contact.id
                                            } else {
                                                selectedMembers + contact.id
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    border = if (isSelected) {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        null
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            ConversationAvatar(
                                                photoUrl = contact.photoUrl,
                                                fallback = contact.name.ifBlank { contact.email }
                                            )
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(contact.name.ifBlank { contact.email }, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    contact.email.ifBlank { contact.phone.ifBlank { "Sem identificacao" } },
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        Text(
                                            if (isSelected) "Selecionado" else "Selecionar",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (!previewPhotoUrl.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { previewPhotoUrl = null },
            confirmButton = {
                TextButton(onClick = { previewPhotoUrl = null }) {
                    Text("Fechar")
                }
            },
            title = { Text(previewPhotoTitle) },
            text = {
                AsyncImage(
                    model = previewPhotoUrl,
                    contentDescription = "Foto ampliada",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 320.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        )
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    currentUserId: String,
    participantDirectory: Map<String, ChatUser>,
    isSelected: Boolean,
    selecting: Boolean,
    onToggleSelected: (String) -> Unit,
    onPreviewPhoto: (String, String) -> Unit,
    onOpenChat: (String) -> Unit
) {
    val unreadCount = conversation.unreadCountByUser[currentUserId] ?: 0
    val partner = if (conversation.isGroup) {
        null
    } else {
        conversation.participants.firstOrNull { it != currentUserId }?.let(participantDirectory::get)
    }
    val isGroupConversation = isGroupConversation(conversation, partner)
    val title = when {
        isGroupConversation -> conversation.name.ifBlank { "Grupo" }
        partner != null -> partner.name.ifBlank { partner.email.ifBlank { "Conversa" } }
        else -> conversation.name.ifBlank { "Conversa" }
    }
    val photoUrl = if (isGroupConversation) conversation.photoUrl else partner?.photoUrl.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (selecting) onToggleSelected(conversation.id) else onOpenChat(conversation.id)
            }
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.background
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConversationAvatar(
                photoUrl = photoUrl,
                fallback = title,
                onClick = {
                    if (photoUrl.isNotBlank()) {
                        onPreviewPhoto(photoUrl, title)
                    }
                }
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = conversation.lastMessage.ifBlank { "Sem mensagens ainda" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    if (isGroupConversation) "Grupo com ${conversation.participants.size} participantes" else "Conversa direta",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    formatConversationTime(conversation.lastMessageTs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            unreadCount.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(modifier = Modifier.width(24.dp))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    }
}

private fun isGroupConversation(conversation: Conversation, partner: ChatUser?): Boolean {
    return conversation.isGroup ||
        conversation.participants.size > 2 ||
        (conversation.name.isNotBlank() &&
            conversation.name != "Conversa" &&
            conversation.name != partner?.name)
}

@Composable
private fun ConversationAvatar(photoUrl: String, fallback: String, onClick: (() -> Unit)? = null) {
    if (photoUrl.isNotBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Foto da conversa",
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallback.take(1).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatConversationTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}
