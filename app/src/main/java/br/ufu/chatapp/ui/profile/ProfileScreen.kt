package br.ufu.chatapp.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import br.ufu.chatapp.ui.auth.AuthViewModel
import br.ufu.chatapp.util.createTempImageUri
import br.ufu.chatapp.util.getDisplayName

@Composable
fun ProfileScreen(
    vm: ProfileViewModel,
    authVm: AuthViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val profile by vm.profile.collectAsState()
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("online") }
    var cameraUri by remember { mutableStateOf(createTempImageUri(context)) }
    var showPhotoMenu by remember { mutableStateOf(false) }
    val sessionsCount by authVm.activeSessionsCount.collectAsState()

    LaunchedEffect(Unit) {
        authVm.refreshActiveSessionsCount()
        vm.loadProfile()
    }

    LaunchedEffect(profile) {
        profile?.let {
            name = it.name
            status = it.status
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) {
            vm.updateProfilePhoto(cameraUri, "perfil_camera.jpg")
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { vm.updateProfilePhoto(it, getDisplayName(context, it)) }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                IntentFlags.readOnly
            )
            vm.updateProfilePhoto(it, getDisplayName(context, it))
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraUri = createTempImageUri(context)
            cameraLauncher.launch(cameraUri)
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
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile?.photoUrl,
                            contentDescription = "Foto de perfil",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            (name.ifBlank { "P" }).take(1).uppercase(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        IconButton(
                            onClick = { showPhotoMenu = true },
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Alterar foto",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showPhotoMenu,
                            onDismissRequest = { showPhotoMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tirar foto") },
                                onClick = {
                                    showPhotoMenu = false
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                        cameraUri = createTempImageUri(context)
                                        cameraLauncher.launch(cameraUri)
                                    } else {
                                        cameraPermission.launch(Manifest.permission.CAMERA)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Escolher da galeria") },
                                onClick = {
                                    showPhotoMenu = false
                                    galleryLauncher.launch("image/*")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Escolher arquivo") },
                                onClick = {
                                    showPhotoMenu = false
                                    fileLauncher.launch(arrayOf("image/*"))
                                }
                            )
                        }
                    }
                }
                Text("Identity", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                Text(
                    "Ajuste sua presença no DevMessage e controle as sessões ativas.",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f)
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                Text("Sessões ativas: $sessionsCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { vm.update(name, profile?.photoUrl.orEmpty(), status) }, modifier = Modifier.fillMaxWidth()) { Text("Salvar perfil") }
                Button(onClick = { authVm.logoutOtherSessions() }, modifier = Modifier.fillMaxWidth()) { Text("Encerrar outras sessões") }
                Button(onClick = { vm.logout(onLogout) }, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
        }
    }
}

private object IntentFlags {
    const val readOnly: Int = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
}
