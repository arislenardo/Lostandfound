package com.example.lostandfound.data

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

object EmailService {
    // TODO: Replace with your actual Brevo API Key from https://app.brevo.com/settings/keys/api
    private const val BREVO_API_KEY = "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"
    private const val SENDER_EMAIL = "arislenardladaga09@gmail.com"
    private const val SENDER_NAME = "Lost and Found Calasiao"

    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun sendEmail(toEmail: String, toName: String, subject: String, htmlContent: String) {
        if (BREVO_API_KEY == "YOUR_BREVO_API_KEY_HERE") {
            println("EmailService: Brevo API key not set. Skipping email to $toEmail.")
            return
        }

        try {
            val json = JSONObject()
            
            val sender = JSONObject()
            sender.put("name", SENDER_NAME)
            sender.put("email", SENDER_EMAIL)
            json.put("sender", sender)

            val toArray = JSONArray()
            val toObj = JSONObject()
            toObj.put("email", toEmail)
            if (toName.isNotBlank()) {
                toObj.put("name", toName)
            }
            toArray.put(toObj)
            json.put("to", toArray)

            json.put("subject", subject)
            json.put("htmlContent", htmlContent)

            val body = json.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("https://api.brevo.com/v3/smtp/email")
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("api-key", BREVO_API_KEY)
                .addHeader("content-type", "application/json")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("EmailService: Failed to send email to $toEmail. Error: ${e.message}")
                    e.printStackTrace()
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        println("EmailService: Email sent successfully to $toEmail")
                    } else {
                        println("EmailService: Failed to send email via Brevo. Code: ${response.code}, Body: ${response.body?.string()}")
                    }
                    response.close()
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Notify user about a new message.
     */
    fun sendNewMessageNotification(receiverEmail: String, senderName: String, messageText: String) {
        val subject = "New Message from $senderName"
        val html = """
            <div style="font-family: sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #2E7D32;">You have a new message!</h2>
                <p><strong>$senderName</strong> sent you a message:</p>
                <blockquote style="background: #f5f5f5; padding: 15px; border-left: 5px solid #2E7D32;">
                    $messageText
                </blockquote>
                <p>Open the <strong>Lost & Found</strong> app to reply.</p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 12px; color: #888;">This is an automated notification from Calasiao Lost & Found.</p>
            </div>
        """.trimIndent()

        sendEmail(receiverEmail, "", subject, html)
    }

    /**
     * Notify user about a potential item match.
     */
    fun sendMatchNotification(userEmail: String, itemName: String, matchType: String) {
        val subject = "Potential Match for your $itemName!"
        val html = """
            <div style="font-family: sans-serif; padding: 20px; color: #333;">
                <h2 style="color: #FFA000;">Smart Match Detected!</h2>
                <p>Our AI found a potential <strong>$matchType</strong> for your reported item: <strong>$itemName</strong>.</p>
                <p>Check the "Notifications" or "Matches" tab in the app to view the details and contact the other user.</p>
                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 12px; color: #888;">Improving our community through technology.</p>
            </div>
        """.trimIndent()

        sendEmail(userEmail, "", subject, html)
    }

    /**
     * Notify user about claim status changes.
     */
    fun sendClaimStatusNotification(userEmail: String, itemName: String, status: String) {
        val color = if (status.uppercase() == "APPROVED") "#2E7D32" else "#C62828"
        val subject = "Claim Status Update: $status"
        val html = """
            <div style="font-family: sans-serif; padding: 20px; color: #333;">
                <h2 style="color: $color;">Claim $status</h2>
                <p>Your claim for the item <strong>$itemName</strong> has been <strong>${status.lowercase()}</strong> by the admin.</p>
                ${if (status.uppercase() == "APPROVED") "<p>Please proceed to the Calasiao Police Station with your Reference ID to retrieve your item.</p>" else "<p>If you believe this is an error, please contact support or visit the station.</p>"}
                <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                <p style="font-size: 12px; color: #888;">Thank you for using Calasiao Lost & Found.</p>
            </div>
        """.trimIndent()

        sendEmail(userEmail, "", subject, html)
    }
}
