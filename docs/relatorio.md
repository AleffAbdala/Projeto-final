# Relatorio do Trabalho Pratico - Aplicativo de Mensagens Instantaneas

## 1. Identificacao do trabalho

**Disciplina:** Trabalho Pratico - Aplicativo de Mensagens Instantaneas  
**Professor:** Alexsandro Santos Soares  
**Instituicao:** Universidade Federal de Uberlandia - Faculdade de Computacao  

## 2. Integrantes do grupo

- Aleff Arantes Abdala Azevedo
- Allan Silva Neves
- Diogo Ricarte Loureiro Menezes
- Nathan Silva Neves

## 3. Objetivo do projeto

O objetivo deste trabalho foi desenvolver, em grupo, um aplicativo de mensagens instantaneas para Android, contemplando sincronizacao de dados em tempo real, notificacoes push, integracao com APIs externas, gerenciamento de estados de conversa e uso basico de sensores do smartphone, como GPS, camera e microfone.

O aplicativo foi projetado para permitir a troca de mensagens entre usuarios de forma simples, com suporte a recursos modernos encontrados em aplicativos de chat, como conversas em tempo real, envio de midia, criacao de grupos, busca de mensagens e funcionamento parcial offline.

## 4. Decisoes tecnicas adotadas

Durante o desenvolvimento, o grupo optou por utilizar tecnologias amplamente empregadas no ecossistema Android atual, buscando uma implementacao funcional, organizada e adequada ao escopo do trabalho.

As principais decisoes tecnicas foram:

- Utilizacao de **Kotlin** como linguagem principal do projeto.
- Desenvolvimento da interface com **Jetpack Compose**, visando uma construcao moderna e mais produtiva das telas.
- Organizacao do projeto em uma estrutura baseada em **MVVM + Repository**, separando camada de interface, regras de negocio e acesso a dados.
- Uso do **Firebase Authentication** para autenticacao de usuarios.
- Uso do **Firebase Firestore** para armazenamento e sincronizacao em tempo real das conversas e mensagens.
- Uso do **Firebase Cloud Messaging (FCM)** para notificacoes push.
- Uso do **Firebase Crashlytics** para apoio em monitoramento de falhas.
- Uso do **Room** para persistencia local e suporte basico ao funcionamento offline.
- Uso do **Supabase Storage** para armazenamento de arquivos de midia, como imagens, videos e audios.
- Uso de **Hilt** para injecao de dependencias e melhor organizacao do codigo.

Essas escolhas permitiram implementar um aplicativo com sincronizacao eficiente, suporte a diferentes tipos de autenticacao, envio de midia e recursos de armazenamento local.

## 5. Funcionalidades desenvolvidas

O aplicativo desenvolvido contempla as seguintes funcionalidades principais:

- Cadastro e login de usuarios.
- Autenticacao com email e senha.
- Autenticacao com conta Google.
- Autenticacao por telefone.
- Recuperacao de senha.
- Criacao, listagem e busca de conversas.
- Envio e recebimento de mensagens em tempo real.
- Status de mensagem, incluindo enviada, entregue e lida.
- Criacao e gerenciamento de grupos.
- Adicao e gerenciamento de contatos.
- Importacao de contatos do dispositivo.
- Edicao de perfil do usuario.
- Indicacao de status do usuario, como online e offline.
- Envio de imagens, videos, audios e arquivos.
- Suporte a stickers.
- Mensagens fixadas no topo da conversa.
- Filtro de mensagens por palavra-chave com destaque.
- Cache local para uso offline.
- Logout seguro.
- Estrutura para notificacoes push.
- Criptografia basica de mensagens de texto.

## 6. Papeis dos membros do grupo

Para organizar o desenvolvimento, as atividades foram divididas entre os integrantes da seguinte forma:

- **Aleff Arantes Abdala Azevedo:** coordenacao geral do projeto, integracao das funcionalidades principais do aplicativo, organizacao do repositorio, consolidacao das partes desenvolvidas pelo grupo e apoio na documentacao final.
- **Allan Silva Neves:** implementacao das telas e fluxos de autenticacao, incluindo login, cadastro, recuperacao de senha e apoio na integracao com Firebase Authentication.
- **Diogo Ricarte Loureiro Menezes:** implementacao das funcionalidades relacionadas as conversas, grupos, envio e recebimento de mensagens, alem dos recursos especiais de mensagens fixadas e filtro por palavra-chave.
- **Nathan Silva Neves:** implementacao e testes dos recursos de midia e sensores, incluindo envio de localizacao, uso da camera, gravacao de audio, alem do apoio nas notificacoes push e validacao do aplicativo em dispositivo.

A divisao foi utilizada como referencia de organizacao, mas houve colaboracao entre os membros em diferentes etapas do projeto, especialmente nos testes, ajustes de integracao e refinamentos da interface.

## 7. Principais dificuldades encontradas

Durante o desenvolvimento, o grupo enfrentou algumas dificuldades tecnicas e de integracao. As principais foram:

- Configurar corretamente os servicos do Firebase, principalmente autenticacao, Firestore e notificacoes push.
- Integrar diferentes formas de autenticacao no mesmo aplicativo, como email/senha, Google e telefone.
- Garantir sincronizacao em tempo real entre usuarios diferentes, com atualizacao adequada das conversas e mensagens.
- Implementar e testar o funcionamento offline de forma simples, conciliando dados locais com os dados remotos.
- Trabalhar com diferentes tipos de midia no chat, como imagem, video, audio e arquivos anexos.
- Integrar os sensores do smartphone ao fluxo da conversa de forma funcional e usavel.
- Organizar a integracao entre as partes desenvolvidas por diferentes membros do grupo, evitando conflitos e inconsistencias.
- Ajustar o comportamento da interface para tornar a experiencia do chat mais clara e amigavel.

Essas dificuldades exigiram testes frequentes, correcoes incrementais e validacoes em emulador e dispositivo fisico.

## 8. Uso de GPS, camera e microfone

Os sensores solicitados no enunciado foram utilizados de forma simples e integrada ao contexto do chat:

- **GPS:** o aplicativo permite enviar a localizacao atual do usuario dentro de uma conversa, possibilitando o compartilhamento de um ponto de localizacao.
- **Camera:** o usuario pode tirar uma foto diretamente pelo aplicativo e envia-la na conversa, sem depender apenas da selecao pela galeria.
- **Microfone:** o aplicativo permite gravar uma mensagem de voz curta e envia-la como audio dentro da conversa.

A implementacao desses recursos buscou atender aos requisitos minimos do trabalho, de maneira funcional e integrada a interface principal do chat.

## 9. Requisitos especiais implementados

O trabalho tambem exigia requisitos especiais. No projeto foram contemplados os seguintes:

### 9.1. Mensagens fixadas
Foi implementado um recurso para fixar uma mensagem importante no topo da conversa. O usuario pode selecionar uma mensagem, fixa-la e tambem desfazer essa acao posteriormente.

### 9.2. Filtro de mensagens por palavra-chave
Foi implementado um campo de busca dentro da conversa para filtrar mensagens por palavra-chave ou expressao, com destaque visual do texto encontrado.

### 9.3. Uso de sensores
Os sensores de GPS, camera e microfone foram incorporados ao aplicativo, permitindo o envio de localizacao, foto e audio, conforme solicitado no enunciado.

## 10. Consideracoes sobre seguranca e privacidade

No projeto foi adotada uma criptografia basica para mensagens de texto, com objetivo academico e demonstrativo. Tambem foram considerados cuidados minimos relacionados a autenticacao do usuario, controle de sessao e organizacao do acesso aos dados.

Como se trata de um trabalho pratico com foco didatico, algumas solucoes de seguranca foram implementadas de forma simplificada, priorizando a demonstracao funcional das caracteristicas exigidas.

## 11. Observacoes sobre o uso de LLMs

Ferramentas baseadas em LLM foram utilizadas como apoio durante partes do desenvolvimento, principalmente para:

- esclarecer duvidas sobre integracao entre Android e Firebase;
- revisar ideias de arquitetura e organizacao do projeto;
- consultar exemplos de implementacao de funcionalidades;
- revisar e melhorar textos de documentacao.

Exemplos de tipos de prompts utilizados:

- perguntas sobre integracao de autenticacao no Android;
- duvidas sobre sincronizacao de dados com Firebase;
- apoio para estruturar recursos como envio de midia e sensores;
- revisao textual de README, relatorio e outros documentos.

A opiniao geral do grupo e que as LLMs ajudaram a acelerar pesquisas, esclarecer conceitos e organizar partes do trabalho. No entanto, todas as respostas precisaram ser avaliadas com cuidado, adaptadas ao contexto do projeto e validadas por meio de testes praticos antes de serem incorporadas.

## 12. Consideracoes finais

O desenvolvimento deste trabalho permitiu ao grupo praticar conceitos importantes de desenvolvimento Android moderno, integracao com servicos em nuvem, sincronizacao em tempo real, uso de armazenamento local e integracao com recursos nativos do dispositivo.

O projeto resultou em um aplicativo de mensagens instantaneas funcional, com suporte a autenticacao, conversas em tempo real, envio de midia, grupos, sensores e recursos especiais solicitados no enunciado. Alem disso, a atividade contribuiu para o desenvolvimento da organizacao em equipe, divisao de responsabilidades e consolidacao de conhecimentos praticos sobre tecnologias utilizadas no ecossistema Android.
