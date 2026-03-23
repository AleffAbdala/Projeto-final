package br.ufu.chatapp.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage

@Composable
fun ContactsScreen(
    vm: ContactsViewModel,
    onBack: () -> Unit,
    onOpenChatWithUser: (String) -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val contacts by vm.contacts.collectAsState()
    val directoryResults by vm.directoryResults.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()

    LaunchedEffect(Unit) {
        vm.loadContacts()
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.clearErrorMessage()
        }
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.importFromDevice(context.contentResolver)
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
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "People",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = onBack) { Text("Voltar") }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        vm.searchUsers(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar usuário", color = MaterialTheme.colorScheme.onPrimary) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedTextColor = MaterialTheme.colorScheme.onPrimary,
                        focusedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                        cursorColor = MaterialTheme.colorScheme.onPrimary,
                        focusedBorderColor = MaterialTheme.colorScheme.onPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                )
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            vm.importFromDevice(context.contentResolver)
                        } else {
                            contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Importar contatos do dispositivo")
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (contacts.isNotEmpty()) {
                item("contacts_header") {
                    Text(
                        "Meus contatos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
            items(contacts, key = { "contact_${it.id}" }) { user ->
                ContactCard(
                    user = user,
                    primaryActionLabel = "Abrir chat",
                    secondaryActionLabel = "Remover",
                    onPrimaryAction = { onOpenChatWithUser(user.id) },
                    onSecondaryAction = { vm.removeContact(user.id) }
                )
            }

            if (directoryResults.isNotEmpty()) {
                item("directory_header") {
                    Text(
                        if (query.isBlank()) "Usuários encontrados" else "Adicionar contato",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
            } else if (contacts.isEmpty()) {
                item("empty_state") {
                    Text(
                        if (query.isBlank()) "Nenhum contato ainda. Busque por nome para adicionar alguém." else "Nenhum usuário encontrado para \"$query\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }

            items(directoryResults, key = { "directory_${it.id}" }) { user ->
                ContactCard(
                    user = user,
                    primaryActionLabel = "Adicionar",
                    secondaryActionLabel = "Abrir chat",
                    onPrimaryAction = { vm.addContact(user.id) },
                    onSecondaryAction = { onOpenChatWithUser(user.id) }
                )
            }
        }
    }
}

@Composable
private fun ContactCard(
    user: br.ufu.chatapp.data.model.ChatUser,
    primaryActionLabel: String,
    secondaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit
) {
    Card(shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (user.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Foto do contato",
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            user.name.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    user.email.ifBlank { user.phone.ifBlank { "Sem email ou telefone" } },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Status: ${user.status}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.weight(1f)
            ) {
                Text(primaryActionLabel)
            }
            Button(
                onClick = onSecondaryAction,
                modifier = Modifier.weight(1f)
            ) {
                Text(secondaryActionLabel)
            }
        }
    }
}
