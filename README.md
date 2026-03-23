# ProjetoFinalChat (Android)

Aplicativo de mensagens instantaneas em Kotlin + Jetpack Compose com Firebase + Supabase Storage.

## Funcionalidades implementadas
- Cadastro/login com email e senha
- Recuperacao de senha
- Lista e busca de conversas
- Criacao de grupos
- Mensagens em tempo real (Firestore)
- Status de mensagem (read por usuario)
- Mensagem fixada no topo
- Filtro por palavra-chave no chat com destaque
- Envio de imagem (camera/galeria), video, audio e localizacao
- Gerenciamento basico de contatos + importacao do dispositivo
- Perfil de usuario (nome/foto/status)
- Logout seguro
- Cache offline local (Room) + persistencia offline do Firestore
- Push notifications com Firebase Cloud Messaging
- Criptografia basica de texto de mensagem (AES)

## Stack
- Kotlin
- Jetpack Compose
- Firebase Auth / Firestore / Messaging
- Supabase Storage
- Hilt
- Room

## Setup rapido
1. Abra a pasta no Android Studio.
2. Adicione `app/google-services.json` do seu projeto Firebase.
3. Ative provedores no Firebase Authentication (Email/Senha).
4. Configure Firestore, FCM e Supabase Storage.
5. Sincronize o Gradle e rode no emulador/celular.

## Observacoes
- Login social e telefone podem ser adicionados no mesmo fluxo do `AuthRepository` usando credenciais Firebase.
- A chave de criptografia em `BuildConfig.MSG_CRYPTO_KEY` e apenas demonstrativa para o trabalho academico.
- O bucket `chat-media` do Supabase deve existir e aceitar uploads anonimos; ha um exemplo em `docs/supabase_storage.sql`.
