# Add project specific ProGuard rules here.

# Keep Firestore-serialized models stable under shrinking/obfuscation.
-keep class br.ufu.chatapp.data.model.** { *; }
