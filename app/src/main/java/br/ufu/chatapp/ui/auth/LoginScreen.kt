package br.ufu.chatapp.ui.auth

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import br.ufu.chatapp.BuildConfig
import br.ufu.chatapp.R
import br.ufu.chatapp.util.ResultState
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.util.concurrent.TimeUnit

@Composable
fun LoginScreen(
    vm: AuthViewModel,
    onOpenRegister: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val state by vm.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf("") }
    var smsCode by remember { mutableStateOf("") }
    var phoneStatus by remember { mutableStateOf("") }
    val errorMessage = (state as? ResultState.Error)?.message
    val isLoading = state is ResultState.Loading

    LaunchedEffect(state) {
        when ((state as? ResultState.Success)?.data) {
            AuthAction.LOGIN, AuthAction.GOOGLE_LOGIN, AuthAction.PHONE_LOGIN -> {
                vm.clearState()
                onSuccess()
            }
            AuthAction.RESET_PASSWORD -> {
                vm.clearState()
                Toast.makeText(context, "E-mail de recuperacao enviado", Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
        }
    }

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching { task.result.idToken }
            .onSuccess { token ->
                if (!token.isNullOrBlank()) vm.loginWithGoogleToken(token)
            }
            .onFailure {
                Toast.makeText(context, it.message ?: "Falha no Google Sign-In", Toast.LENGTH_SHORT).show()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.Top
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("DevMessage", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Acessar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                )
                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        when {
                            normalizedEmail.isBlank() -> Toast.makeText(context, "Informe o e-mail", Toast.LENGTH_SHORT).show()
                            password.isBlank() -> Toast.makeText(context, "Informe a senha", Toast.LENGTH_SHORT).show()
                            else -> vm.login(normalizedEmail, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) { Text("Entrar com e-mail") }
                Button(
                    onClick = {
                        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(context.getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build()
                        googleLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Entrar com Google")
                }
                Button(
                    onClick = {
                        val normalizedEmail = email.trim()
                        if (normalizedEmail.isBlank()) {
                            Toast.makeText(context, "Informe o e-mail para recuperar a senha", Toast.LENGTH_SHORT).show()
                        } else {
                            vm.resetPassword(normalizedEmail)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) { Text("Recuperar senha") }
                Button(onClick = onOpenRegister, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("Criar conta") }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Telefone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(phoneNumber, { phoneNumber = it }, label = { Text("Telefone com DDI") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                Button(
                    onClick = {
                        val normalizedPhone = normalizePhoneToE164(phoneNumber)
                        if (activity == null) {
                            phoneStatus = "Nao foi possivel iniciar a verificacao neste contexto"
                            Toast.makeText(context, phoneStatus, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (normalizedPhone.isBlank()) {
                            phoneStatus = "Informe o telefone com DDI"
                            Toast.makeText(context, phoneStatus, Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!normalizedPhone.startsWith("+") || normalizedPhone.length < 12) {
                            phoneStatus = "Use um telefone valido no formato +5511999999999"
                            Toast.makeText(context, phoneStatus, Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (BuildConfig.DEBUG) {
                            // Real phone numbers still need an app check flow in debug builds.
                            // For local development, prefer forcing the reCAPTCHA fallback instead
                            // of disabling app verification, which only fits fictional test numbers.
                            Firebase.auth.firebaseAuthSettings.forceRecaptchaFlowForTesting(true)
                        }
                        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                phoneStatus = "Telefone verificado automaticamente"
                                vm.loginWithPhoneCredential(credential)
                            }

                            override fun onVerificationFailed(e: FirebaseException) {
                                phoneStatus = mapPhoneVerificationError(e)
                                Toast.makeText(context, phoneStatus, Toast.LENGTH_LONG).show()
                            }

                            override fun onCodeSent(verId: String, token: PhoneAuthProvider.ForceResendingToken) {
                                verificationId = verId
                                phoneStatus = "Código enviado por SMS"
                                Toast.makeText(context, phoneStatus, Toast.LENGTH_SHORT).show()
                            }
                        }

                        val options = PhoneAuthOptions.newBuilder(Firebase.auth)
                            .setPhoneNumber(normalizedPhone)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(activity)
                            .setCallbacks(callbacks)
                            .build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text("Enviar código")
                }
                OutlinedTextField(smsCode, { smsCode = it }, label = { Text("Código SMS") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
                Button(
                    onClick = {
                        when {
                            verificationId.isBlank() -> Toast.makeText(context, "Envie o codigo por SMS primeiro", Toast.LENGTH_SHORT).show()
                            smsCode.trim().isBlank() -> Toast.makeText(context, "Informe o codigo SMS", Toast.LENGTH_SHORT).show()
                            else -> vm.loginWithPhone(verificationId, smsCode.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = verificationId.isNotBlank() && smsCode.isNotBlank() && !isLoading
                ) {
                    Text("Confirmar telefone")
                }
                if (phoneStatus.isNotBlank()) Text(phoneStatus, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Exemplo: +5565981487299 ou 65981487299",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state is ResultState.Loading) CircularProgressIndicator()
            if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun normalizePhoneToE164(rawPhone: String): String {
    val trimmed = rawPhone.trim()
    if (trimmed.isBlank()) return ""

    val hasPlus = trimmed.startsWith("+")
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isBlank()) return ""

    return when {
        hasPlus -> "+$digits"
        digits.startsWith("00") -> "+${digits.drop(2)}"
        digits.length in 10..11 -> "+55$digits"
        else -> "+$digits"
    }
}

private fun mapPhoneVerificationError(error: FirebaseException): String {
    val message = error.message.orEmpty()
    return when {
        "missing a valid app identifier" in message.lowercase() ->
            "Falha na verificacao do app pelo Firebase. Cadastre o SHA-256 do app 'br.ufu.chatapp' no Firebase Console e deixe Phone Auth habilitado."
        message.isNotBlank() -> message
        else -> "Falha ao enviar SMS"
    }
}
