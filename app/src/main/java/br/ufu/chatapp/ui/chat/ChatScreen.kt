package br.ufu.chatapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.statusBars
import androidx.core.content.ContextCompat
import br.ufu.chatapp.data.model.Message
import br.ufu.chatapp.data.model.MessageStatus
import br.ufu.chatapp.data.model.MessageType
import br.ufu.chatapp.util.SimpleAudioRecorder
import br.ufu.chatapp.util.createTempImageUri
import br.ufu.chatapp.util.getDisplayName
import coil.compose.AsyncImage
import com.google.android.gms.location.Priority
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.absoluteValue

@Composable
fun ChatScreen(
    conversationId: String,
    vm: ChatViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val orderedMessages = remember(messages) {
        messages.sortedWith(compareBy<Message>({ it.serverTimestamp?.toDate()?.time ?: it.timestamp }, { it.timestamp }, { it.id }))
    }
    val pinned by vm.pinnedMessage.collectAsState()
    val conversation by vm.conversation.collectAsState()
    val chatPartner by vm.chatPartner.collectAsState()
    val groupCandidates by vm.groupCandidates.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    val currentUserId = vm.currentUserId()
    var text by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf(createTempImageUri(context)) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var recorder by remember { mutableStateOf(SimpleAudioRecorder(context)) }
    var recording by remember { mutableStateOf(false) }
    var showGroupEditor by remember { mutableStateOf(false) }
    var showAttachmentsMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }
    var selectedGroupMembers by remember { mutableStateOf(setOf<String>()) }
    var groupPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var groupPhotoName by remember { mutableStateOf("grupo.jpg") }
    var groupPhotoMenu by remember { mutableStateOf(false) }
    var groupCameraUri by remember { mutableStateOf(createTempImageUri(context)) }
    var selectedMessageIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectingMessages by rememberSaveable { mutableStateOf(false) }
    var previewPhotoUrl by remember { mutableStateOf<String?>(null) }
    var previewPhotoTitle by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messageFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isGroupConversation = conversation?.let {
        isGroupConversation(
            conversation = it,
            partnerName = chatPartner?.name
        )
    } == true
    val chatTitle = when {
        isGroupConversation -> conversation?.name?.ifBlank { "Grupo" } ?: "Grupo"
        !chatPartner?.name.isNullOrBlank() -> chatPartner?.name.orEmpty()
        else -> conversation?.name?.ifBlank { "Conversa" } ?: "Conversa"
    }
    val chatSubtitle = when {
        isGroupConversation -> groupCandidates
            .filter { conversation?.participants?.contains(it.id) == true && it.id != currentUserId }
            .joinToString(", ") { it.name.ifBlank { it.email } }
            .ifBlank { "Sem participantes" }
        else -> chatPartner?.status?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            ?: "Offline"
    }

    LaunchedEffect(conversationId) { vm.setConversation(conversationId) }
    LaunchedEffect(conversation?.id) {
        groupName = conversation?.name.orEmpty()
        selectedGroupMembers = conversation?.participants
            ?.filter { it != currentUserId }
            ?.toSet()
            .orEmpty()
        groupPhotoUri = conversation?.photoUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        groupPhotoName = "grupo.jpg"
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearErrorMessage()
        }
    }
    LaunchedEffect(orderedMessages.size) {
        if (orderedMessages.isEmpty()) return@LaunchedEffect
        val lastIndex = orderedMessages.lastIndex
        val visibleLast = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val latestIsMine = orderedMessages.lastOrNull()?.senderId == currentUserId
        val shouldSnapToBottom = visibleLast < 0 || latestIsMine || visibleLast >= lastIndex - 2
        if (shouldSnapToBottom) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val capturedUri = pendingCameraUri ?: cameraUri
        if (ok) {
            vm.sendMedia(capturedUri, MessageType.IMAGE, getDisplayName(context, capturedUri).ifBlank { "camera.jpg" })
        }
        pendingCameraUri = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendMedia(it, MessageType.IMAGE, getDisplayName(context, it)) }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendMedia(it, MessageType.VIDEO, getDisplayName(context, it)) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.sendMedia(it, MessageType.FILE, getDisplayName(context, it)) }
    }
    val groupPhotoCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            groupPhotoUri = groupCameraUri
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
            pendingCameraUri = cameraUri
            cameraLauncher.launch(cameraUri)
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            requestAndSendCurrentLocation(
                context = context,
                onLocation = { lat, lng -> vm.sendLocation(lat, lng) }
            )
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching { recorder.start() }
                .onSuccess { uri ->
                    if (uri.toString().isNotBlank()) recording = true
                }
                .onFailure {
                    Toast.makeText(context, it.message ?: "Nao foi possivel iniciar a gravacao", Toast.LENGTH_LONG).show()
                }
        }
    }
    val groupPhotoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            groupCameraUri = createTempImageUri(context)
            groupPhotoCameraLauncher.launch(groupCameraUri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .navigationBarsPadding()
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
                    .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onBack,
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    ChatHeaderAvatar(
                        if (isGroupConversation) conversation?.photoUrl.orEmpty() else chatPartner?.photoUrl.orEmpty(),
                        chatTitle,
                        onClick = {
                            val photoUrl = if (isGroupConversation) conversation?.photoUrl.orEmpty() else chatPartner?.photoUrl.orEmpty()
                            if (photoUrl.isNotBlank()) {
                                previewPhotoUrl = photoUrl
                                previewPhotoTitle = chatTitle
                            }
                        }
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp, bottom = 4.dp)
                            .clickable(enabled = isGroupConversation) { showGroupEditor = !showGroupEditor },
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            chatTitle,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            chatSubtitle,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                        )
                    }
                    if (selectingMessages) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (selectedMessageIds.size == 1) {
                                IconButton(
                                    onClick = {
                                        vm.pinMessage(selectedMessageIds.first())
                                        selectedMessageIds = emptySet()
                                        selectingMessages = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Fixar mensagem",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    vm.deleteMessages(selectedMessageIds)
                                    selectedMessageIds = emptySet()
                                    selectingMessages = false
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Apagar mensagens",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            Text(
                                "Cancelar",
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.clickable {
                                    selectedMessageIds = emptySet()
                                    selectingMessages = false
                                }
                            )
                        }
                    } else {
                        Button(
                            onClick = { showSearch = !showSearch },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = "Buscar na conversa",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (selectingMessages) {
                    Text(
                        "${selectedMessageIds.size} mensagem(ns) selecionada(s)",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = {
                            search = it
                            vm.setSearch(it)
                        },
                        label = { Text("Buscar na conversa", color = MaterialTheme.colorScheme.onPrimary) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }
        }

        if (showGroupEditor && isGroupConversation) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Editar grupo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ChatHeaderAvatar(groupPhotoUri?.toString().orEmpty(), groupName.ifBlank { "G" })
                        Box {
                            Button(onClick = { groupPhotoMenu = true }) {
                                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = "Alterar foto do grupo")
                                Text("Foto do grupo")
                            }
                            DropdownMenu(
                                expanded = groupPhotoMenu,
                                onDismissRequest = { groupPhotoMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Tirar foto") },
                                    onClick = {
                                        groupPhotoMenu = false
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            groupCameraUri = createTempImageUri(context)
                                            groupPhotoCameraLauncher.launch(groupCameraUri)
                                        } else {
                                            groupPhotoPermission.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Galeria") },
                                    onClick = {
                                        groupPhotoMenu = false
                                        groupPhotoGalleryLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Arquivo") },
                                    onClick = {
                                        groupPhotoMenu = false
                                        groupPhotoFileLauncher.launch(arrayOf("image/*"))
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(groupName, { groupName = it }, label = { Text("Nome do grupo") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                    val membersInGroup = groupCandidates.filter { selectedGroupMembers.contains(it.id) }
                    val availableContacts = groupCandidates.filter { !selectedGroupMembers.contains(it.id) }

                    Text("Participantes atuais", fontWeight = FontWeight.SemiBold)
                    if (membersInGroup.isEmpty()) {
                        Text(
                            "Nenhum participante selecionado",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(membersInGroup, key = { _, item -> item.id }) { _, user ->
                            GroupParticipantItem(
                                user = user,
                                actionLabel = "Remover",
                                onAction = { selectedGroupMembers = selectedGroupMembers - user.id }
                            )
                        }
                    }
                    Text("Adicionar pessoas", fontWeight = FontWeight.SemiBold)
                    if (availableContacts.isEmpty()) {
                        Text(
                            "Todos os contatos disponíveis já estão no grupo",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(180.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(availableContacts, key = { _, item -> item.id }) { _, user ->
                                GroupParticipantItem(
                                    user = user,
                                    actionLabel = "Adicionar",
                                    onAction = { selectedGroupMembers = selectedGroupMembers + user.id }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            vm.updateGroup(groupName, selectedGroupMembers.toList(), groupPhotoUri, groupPhotoName)
                            showGroupEditor = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Salvar alterações")
                    }
                }
            }
        }

        if (pinned != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mensagem fixada", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            pinned?.content.orEmpty(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text("Desafixar", modifier = Modifier.clickable { vm.pinMessage(null) }, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(10.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(orderedMessages, key = { _, item -> item.id }) { index, msg ->
                val previous = orderedMessages.getOrNull(index - 1)
                if (previous == null || !isSameDay(previous.timestamp, msg.timestamp)) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            formatDay(msg.timestamp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ChatMessageItem(
                    message = msg,
                    isMine = msg.senderId == currentUserId,
                    highlighted = search.isNotBlank() && (msg.content.contains(search, true) || msg.mediaName.contains(search, true)),
                    isSelected = selectedMessageIds.contains(msg.id),
                    status = vm.effectiveStatus(msg),
                    showSenderName = isGroupConversation,
                    onToggleSelected = {
                        selectingMessages = true
                        selectedMessageIds = if (selectedMessageIds.contains(msg.id)) {
                            selectedMessageIds - msg.id
                        } else {
                            selectedMessageIds + msg.id
                        }
                    },
                    onMessageClick = {
                        if (selectingMessages) {
                            val updatedSelection = if (selectedMessageIds.contains(msg.id)) {
                                selectedMessageIds - msg.id
                            } else {
                                selectedMessageIds + msg.id
                            }
                            selectedMessageIds = updatedSelection
                            if (updatedSelection.isEmpty()) {
                                selectingMessages = false
                            }
                        }
                    },
                    onOpenUri = { url ->
                        val uri = Uri.parse(url)
                        val isLocationLink = url.contains("google.com/maps", ignoreCase = true) ||
                            url.contains("maps.apple.com", ignoreCase = true) ||
                            uri.scheme == "geo"
                        val opened = if (isLocationLink) {
                            openLocation(context, uri)
                        } else {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(intent, "Abrir com").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                true
                            }.getOrElse { false }
                        }
                        if (!opened) {
                            Toast.makeText(
                                context,
                                if (isLocationLink) "Nenhum app disponivel para abrir a localizacao" else "Nenhum app disponivel para abrir este arquivo",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
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
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                }
            )
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Button(
                            onClick = { showAttachmentsMenu = true },
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("⋮", fontSize = 22.sp)
                        }
                        DropdownMenu(
                            expanded = showAttachmentsMenu,
                            onDismissRequest = { showAttachmentsMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Câmera") },
                                onClick = {
                                    showAttachmentsMenu = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        cameraUri = createTempImageUri(context)
                                        pendingCameraUri = cameraUri
                                        cameraLauncher.launch(cameraUri)
                                    } else cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Galeria") },
                                onClick = {
                                    showAttachmentsMenu = false
                                    galleryLauncher.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Vídeo") },
                                onClick = {
                                    showAttachmentsMenu = false
                                    videoLauncher.launch("video/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Arquivo") },
                                onClick = {
                                    showAttachmentsMenu = false
                                    fileLauncher.launch("*/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("GPS") },
                                onClick = {
                                    showAttachmentsMenu = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        requestAndSendCurrentLocation(
                                            context = context,
                                            onLocation = { lat, lng -> vm.sendLocation(lat, lng) }
                                        )
                                    } else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            )
                        }
                    }
                    Button(
                        onClick = { showEmojiPicker = !showEmojiPicker },
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("😊", fontSize = 20.sp, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = {
                            if (!recording) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    runCatching { recorder.start() }
                                        .onSuccess { recording = true }
                                        .onFailure {
                                            Toast.makeText(context, it.message ?: "Nao foi possivel iniciar a gravacao", Toast.LENGTH_LONG).show()
                                        }
                                } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                val audioUri = recorder.stop()
                                if (audioUri != null) vm.sendMedia(audioUri, MessageType.AUDIO, "Mensagem de voz")
                                recording = false
                            }
                        },
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text(
                            if (recording) "■" else "🎤",
                            fontSize = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Mensagem") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 88.dp)
                            .focusRequester(messageFocusRequester),
                        shape = RoundedCornerShape(22.dp)
                    )
                    Button(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty()) {
                                vm.sendText(trimmed)
                                text = ""
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Enviar")
                    }
                }

                if (showEmojiPicker) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                listOf("😀", "😂", "😍", "🥹", "😎"),
                                listOf("👍", "👏", "🔥", "🎉", "❤️"),
                                listOf("🤔", "🙏", "😭", "🤝", "🚀")
                            ).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    row.forEach { emoji ->
                                        Button(
                                            onClick = {
                                                text += emoji
                                                showEmojiPicker = false
                                                keyboardController?.show()
                                                messageFocusRequester.requestFocus()
                                            },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.size(48.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                        ) {
                                            Text(emoji, fontSize = 20.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHeaderAvatar(photoUrl: String, fallbackName: String, onClick: (() -> Unit)? = null) {
    if (photoUrl.isNotBlank()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Foto do contato",
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                fallbackName.take(1).uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun GroupParticipantItem(
    user: br.ufu.chatapp.data.model.ChatUser,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                ChatHeaderAvatar(user.photoUrl, user.name.ifBlank { user.email })
                Column {
                    Text(user.name.ifBlank { user.email }, fontWeight = FontWeight.SemiBold)
                    Text(
                        user.email.ifBlank { user.phone.ifBlank { "Sem identificacao" } },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Text(
                actionLabel,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction() }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatMessageItem(
    message: Message,
    isMine: Boolean,
    highlighted: Boolean,
    isSelected: Boolean,
    status: MessageStatus,
    showSenderName: Boolean,
    onToggleSelected: () -> Unit,
    onMessageClick: () -> Unit,
    onOpenUri: (String) -> Unit
) {
    val isAudioMessage = message.type == MessageType.AUDIO.name
    val shouldShowSenderName = showSenderName && !isMine

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onMessageClick,
                onLongClick = onToggleSelected
            ),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        Card(modifier = Modifier.width(290.dp), shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            highlighted -> MaterialTheme.colorScheme.primaryContainer
                            isAudioMessage -> MaterialTheme.colorScheme.surface
                            isMine -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.26f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    )
                    .padding(if (isAudioMessage && !shouldShowSenderName) 0.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (shouldShowSenderName) {
                    Text(
                        message.senderName.ifBlank { message.senderId },
                        fontWeight = FontWeight.Bold,
                        color = senderNameColor(message.senderId)
                    )
                } else if (!isMine && !isAudioMessage) {
                    Text(message.senderName.ifBlank { message.senderId }, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                MessageContent(message = message, onOpenUri = onOpenUri)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isAudioMessage) 12.dp else 0.dp, vertical = if (isAudioMessage) 8.dp else 0.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(formatTime(message.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isMine) {
                    Text(
                        text = when (status) {
                            MessageStatus.READ -> "Lida"
                            MessageStatus.DELIVERED -> "Entregue"
                            MessageStatus.SENT -> "Enviada"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = if (isAudioMessage) 12.dp else 0.dp,
                                end = if (isAudioMessage) 12.dp else 0.dp,
                                bottom = if (isAudioMessage) 10.dp else 0.dp
                            ),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageContent(message: Message, onOpenUri: (String) -> Unit) {
    when (message.type) {
        MessageType.IMAGE.name -> {
            AsyncImage(model = message.mediaUrl, contentDescription = "Imagem", modifier = Modifier.fillMaxWidth().height(180.dp))
            if (message.mediaName.isNotBlank()) Text(message.mediaName)
        }
        MessageType.VIDEO.name -> {
            VideoMessagePlayer(message.mediaUrl)
            Text(message.mediaName.ifBlank { "Vídeo" })
        }
        MessageType.AUDIO.name -> {
            AudioMessagePlayer(message.mediaUrl, message.mediaName.ifBlank { "Mensagem de voz" })
        }
        MessageType.FILE.name -> {
            Text("Arquivo: ${message.mediaName.ifBlank { message.content }}")
            Text("Abrir arquivo", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onOpenUri(message.mediaUrl) })
        }
        MessageType.LOCATION.name -> {
            val latitude = message.latitude
            val longitude = message.longitude
            val mapsUrl = if (latitude != null && longitude != null) {
                "https://www.google.com/maps/search/?api=1&query=$latitude,$longitude"
            } else {
                null
            }
            Column(
                modifier = Modifier.clickable(enabled = mapsUrl != null) {
                    mapsUrl?.let(onOpenUri)
                },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(message.content)
                Text("Latitude: $latitude")
                Text("Longitude: $longitude")
                if (mapsUrl != null) {
                    Text(
                        "Abrir localização",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        MessageType.STICKER.name -> {
            Text(message.content, fontSize = 32.sp, lineHeight = 36.sp)
        }
        else -> Text(message.content)
    }
}

@Composable
private fun VideoMessagePlayer(videoUrl: String) {
    val context = LocalContext.current
    var loading by remember(videoUrl) { mutableStateOf(true) }
    var failed by remember(videoUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(videoUrl))
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        loading = false
                        failed = false
                        seekTo(1)
                    }
                    setOnErrorListener { _, _, _ ->
                        loading = false
                        failed = true
                        true
                    }
                    setOnClickListener {
                        if (!isPlaying) start() else pause()
                    }
                }
            },
            update = { videoView ->
                if (videoView.tag != videoUrl) {
                    videoView.tag = videoUrl
                    loading = true
                    failed = false
                    videoView.stopPlayback()
                    videoView.setVideoURI(Uri.parse(videoUrl))
                    videoView.seekTo(1)
                }
            },
            modifier = Modifier.matchParentSize()
        )

        when {
            loading -> CircularProgressIndicator()
            failed -> Text(
                "Nao foi possivel carregar o video",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
            else -> Text(
                "Toque para reproduzir",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AudioMessagePlayer(audioUrl: String, title: String) {
    val context = LocalContext.current
    var mediaPlayer by remember(audioUrl) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(audioUrl) { mutableStateOf(false) }
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var progress by remember(audioUrl) { mutableStateOf(0f) }
    var durationMs by remember(audioUrl) { mutableStateOf(0) }
    var currentPositionMs by remember(audioUrl) { mutableStateOf(0) }

    DisposableEffect(audioUrl) {
        val player = MediaPlayer().apply {
            setOnPreparedListener {
                isPrepared = true
                progress = 0f
                durationMs = duration.coerceAtLeast(0)
                currentPositionMs = 0
            }
            setOnCompletionListener {
                isPlaying = false
                currentPositionMs = 0
                progress = 0f
                seekTo(0)
            }
            setOnErrorListener { _, _, _ ->
                isPlaying = false
                isPrepared = false
                Toast.makeText(context, "Nao foi possivel reproduzir o audio", Toast.LENGTH_LONG).show()
                true
            }
        }
        mediaPlayer = player

        onDispose {
            runCatching { player.stop() }
            runCatching { player.release() }
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying) {
            val player = mediaPlayer ?: break
            val duration = player.duration.takeIf { it > 0 } ?: 1
            currentPositionMs = player.currentPosition
            progress = player.currentPosition.toFloat() / duration.toFloat()
            delay(250)
        }
    }

    val shownTime = if (isPlaying) currentPositionMs else durationMs

    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    val player = mediaPlayer ?: return@Button
                    if (!isPrepared) {
                        runCatching {
                            player.reset()
                            player.setDataSource(audioUrl)
                            player.prepare()
                            player.start()
                            isPrepared = true
                            isPlaying = true
                            durationMs = player.duration.coerceAtLeast(0)
                        }.onFailure {
                            Toast.makeText(context, "Nao foi possivel reproduzir o audio", Toast.LENGTH_LONG).show()
                        }
                    } else if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        player.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar audio" else "Reproduzir audio",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        formatAudioTime(shownTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                            .height(4.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(18) { index ->
                        val activeBars = (progress.coerceIn(0f, 1f) * 18).toInt()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (index % 3 == 0) 12.dp else if (index % 2 == 0) 8.dp else 6.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(
                                    if (index < activeBars) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

private fun formatAudioTime(timeMs: Int): String {
    val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun isGroupConversation(conversation: br.ufu.chatapp.data.model.Conversation, partnerName: String?): Boolean {
    return conversation.isGroup ||
        conversation.participants.size > 2 ||
        (conversation.name.isNotBlank() &&
            conversation.name != "Conversa" &&
            conversation.name != partnerName)
}

private fun senderNameColor(senderId: String): Color {
    val palette = listOf(
        Color(0xFF8E24AA),
        Color(0xFF00897B),
        Color(0xFFEF6C00),
        Color(0xFF3949AB),
        Color(0xFFC62828),
        Color(0xFF2E7D32)
    )
    val index = (senderId.hashCode().absoluteValue) % palette.size
    return palette[index]
}

private fun openLocation(context: android.content.Context, uri: Uri): Boolean {
    val coordinates = when (uri.scheme) {
        "geo" -> uri.schemeSpecificPart.substringBefore("?").takeIf { it.isNotBlank() }
            ?: uri.encodedQuery?.substringAfter("q=")?.takeIf { it.isNotBlank() }
        else -> uri.getQueryParameter("query")?.takeIf { it.isNotBlank() }
    }?.trim()

    val candidates = buildList {
        if (coordinates != null) {
            add(Uri.parse("https://www.google.com/maps/search/?api=1&query=$coordinates"))
            add(Uri.parse("https://maps.google.com/?q=$coordinates"))
            add(Uri.parse("https://maps.apple.com/?q=$coordinates"))
            add(Uri.parse("geo:$coordinates?q=$coordinates"))
        }
        add(uri)
    }.distinct()

    candidates.forEach { candidate ->
        val intent = Intent(Intent.ACTION_VIEW, candidate).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Abrir localização").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(chooser)
            return true
        }
    }

    return false
}

private fun requestAndSendCurrentLocation(
    context: android.content.Context,
    onLocation: (Double, Double) -> Unit
) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    fused.lastLocation
        .addOnSuccessListener { lastLocation ->
            if (lastLocation != null) {
                onLocation(lastLocation.latitude, lastLocation.longitude)
                return@addOnSuccessListener
            }

            val cancellationTokenSource = CancellationTokenSource()
            fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
                .addOnSuccessListener { currentLocation ->
                    if (currentLocation != null) {
                        onLocation(currentLocation.latitude, currentLocation.longitude)
                    } else {
                        Toast.makeText(
                            context,
                            "Nao foi possivel obter a localizacao atual. Ative o GPS do aparelho.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        context,
                        it.message ?: "Nao foi possivel obter a localizacao atual",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
        .addOnFailureListener {
            Toast.makeText(
                context,
                it.message ?: "Nao foi possivel acessar a localizacao",
                Toast.LENGTH_LONG
            ).show()
        }
}

private fun formatDay(timestamp: Long): String {
    return SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun isSameDay(first: Long, second: Long): Boolean {
    return formatDay(first) == formatDay(second)
}
