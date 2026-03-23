const admin = require('firebase-admin');
const {onDocumentCreated} = require('firebase-functions/v2/firestore');

admin.initializeApp();

exports.sendChatPush = onDocumentCreated('conversations/{conversationId}/messages/{messageId}', async (event) => {
  const message = event.data && event.data.data();
  if (!message) return;

  const db = admin.firestore();
  const conversationId = event.params.conversationId;
  const conversationSnap = await db.collection('conversations').doc(conversationId).get();
  if (!conversationSnap.exists) return;

  const conversation = conversationSnap.data() || {};
  const participants = Array.isArray(conversation.participants) ? conversation.participants : [];
  const targetIds = participants.filter((id) => id && id !== message.senderId);
  if (targetIds.length === 0) return;

  const userSnaps = await Promise.all(targetIds.map((id) => db.collection('users').doc(id).get()));
  const tokens = userSnaps
    .map((snap) => (snap.exists ? snap.data().pushToken : null))
    .filter(Boolean);

  if (tokens.length === 0) return;

  const title = conversation.isGroup
    ? `Nova mensagem em ${conversation.name || 'grupo'}`
    : `${message.senderName || 'Contato'} enviou uma mensagem`;

  let body = 'Você recebeu uma nova mensagem';
  switch (message.type) {
    case 'IMAGE':
      body = 'Imagem recebida';
      break;
    case 'VIDEO':
      body = 'Vídeo recebido';
      break;
    case 'AUDIO':
      body = 'Áudio recebido';
      break;
    case 'FILE':
      body = `Arquivo: ${message.mediaName || 'anexo'}`;
      break;
    case 'LOCATION':
      body = 'Localização compartilhada';
      break;
    case 'STICKER':
      body = message.previewText || 'Sticker recebido';
      break;
    default:
      body = message.previewText || 'Nova mensagem de texto';
  }

  const response = await admin.messaging().sendEachForMulticast({
    tokens,
    notification: {title, body},
    data: {
      conversationId,
      type: message.type || 'TEXT',
      senderId: message.senderId || '',
      senderName: message.senderName || '',
      title,
      body
    }
  });

  const invalidTokens = response.responses
    .map((item, index) => ({item, token: tokens[index]}))
    .filter(({item}) => !item.success)
    .map(({token}) => token);

  if (invalidTokens.length > 0) {
    const cleanup = userSnaps
      .filter((snap) => snap.exists && invalidTokens.includes(snap.data().pushToken))
      .map((snap) => snap.ref.update({pushToken: ''}));
    await Promise.allSettled(cleanup);
  }
});
