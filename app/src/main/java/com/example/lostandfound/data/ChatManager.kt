package com.example.lostandfound.data

import com.example.lostandfound.model.Message
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


object ChatManager {
    private val db = FirebaseFirestore.getInstance()

    fun sendMessage(message: Message, skipEmail: Boolean = false, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        // Create a unique chat ID based on participants to group messages (optional, or just query by participants)
        // For simplicity in this app, we'll store all messages in a top-level "messages" collection
        // and query them. For scalability, subcollections `users/{uid}/chats` are better, but this is a prototype.
        
        db.collection("messages")
            .add(message)
            .addOnSuccessListener { doc ->
                db.collection("messages").document(doc.id).update("id", doc.id)
                
                // --- TRIGGER EMAIL NOTIFICATION (If not explicitly skipped) ---
                if (!skipEmail) {
                    db.collection("users").document(message.receiverId).get()
                        .addOnSuccessListener { userDoc ->
                            val receiverEmail = userDoc.getString("email") ?: ""
                            if (receiverEmail.isNotBlank()) {
                                com.example.lostandfound.data.EmailService.sendNewMessageNotification(
                                    receiverEmail = receiverEmail,
                                    senderName = message.senderName,
                                    messageText = message.text
                                )
                            }
                        }
                }
                // ----------------------------------------

                onSuccess()
            }
            .addOnFailureListener { onFailure(it) }
    }

    // Helper to generate a consistent Chat ID for two users (e.g., "minId_maxId")
    // This allows us to easily find the conversation between two people.
    fun getConversationId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}"
    }
}