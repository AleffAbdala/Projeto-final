# Relatorio do Trabalho Pratico - App de Mensagens

## Decisoes tecnicas
- Arquitetura MVVM + Repository
- Firebase para realtime e notificacoes
- Room para cache offline

## Papeis dos membros
- Aleff Arantes Abdala Azevedo: coordenacao geral do projeto, integracao das funcionalidades principais do aplicativo, organizacao do repositorio e documentacao final.
- Allan Silva Neves: implementacao das telas e fluxos de autenticacao, incluindo login, cadastro, recuperacao de senha e apoio na integracao com Firebase Authentication.
- Diogo Ricarte Loureiro Menezes: implementacao das funcionalidades de conversa, grupos, envio e recebimento de mensagens, alem dos recursos especiais de mensagens fixadas e filtro por palavra-chave.
- Nathan Silva Neves: implementacao e testes dos recursos de midia e sensores, incluindo envio de localizacao, uso da camera, gravacao de audio, alem do apoio nas notificacoes push e validacao do aplicativo em dispositivo.

## Principais dificuldades
- Configurar e integrar corretamente os servicos do Firebase, principalmente autenticacao, Firestore e notificacoes push.
- Garantir sincronizacao em tempo real mantendo o aplicativo funcional mesmo em cenarios com instabilidade de conexao.
- Implementar o suporte offline de forma simples, conciliando dados locais com os dados sincronizados na nuvem.
- Trabalhar com diferentes tipos de midia no chat, como imagem, video, audio e arquivos, mantendo uma interface coerente.
- Integrar os sensores do smartphone de forma funcional dentro do fluxo do chat, especialmente camera, GPS e microfone.
- Organizar as responsabilidades do grupo e consolidar todas as funcionalidades em uma unica versao estavel para apresentacao.

## Uso de GPS, camera e microfone
- GPS: envio da localizacao atual no chat
- Camera: captura de foto e envio direto
- Microfone: gravacao curta e envio como audio

## Observacoes sobre LLMs
- Modelos usados: ferramentas baseadas em LLM foram utilizadas como apoio para esclarecer duvidas de implementacao, estrutura de codigo, organizacao de documentacao e revisao textual.
- Prompts mais importantes: consultas sobre integracao entre Firebase e Android, estrutura de arquitetura MVVM com Repository, exemplos de implementacao para envio de midia, uso de sensores no Android e apoio na escrita e revisao de documentacao tecnica.
- Opiniao do grupo: o uso de LLMs foi util para acelerar pesquisas, esclarecer conceitos e sugerir caminhos de implementacao, mas todas as respostas precisaram de verificacao, adaptacao ao contexto do projeto e testes praticos no aplicativo antes de serem incorporadas.
