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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

object EmailService {
    private const val BREVO_API_KEY = "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"
    // MUST be a verified sender in your Brevo Dashboard
    private const val VERIFIED_SENDER_EMAIL = "ladagaas.820.stud@cdd.edu.ph"
    private const val SENDER_NAME = "Balik-Calasiao"

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
            sender.put("email", VERIFIED_SENDER_EMAIL)
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

    private fun getBaseHtmlTemplate(
        headerColor: String,
        headerTitle: String,
        headerSubtitle: String,
        contentHtml: String,
        footerText: String,
        buttonHtml: String = ""
    ): String {
        return """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 0; margin: 0; color: #333; line-height: 1.6; background-color: #F5F5F5;">
                <div style="max-width: 600px; margin: 20px auto; background-color: #FFFFFF; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 15px rgba(0,0,0,0.1); border: 1px solid #E0E0E0;">
                    <!-- Header -->
                    <div style="background-color: $headerColor; padding: 30px; text-align: center;">
                        <h1 style="color: #FFFFFF; margin: 0; font-size: 24px; letter-spacing: 1px;">$headerTitle</h1>
                        <p style="color: #FFFFFF; opacity: 0.85; margin: 8px 0 0 0; font-size: 14px;">$headerSubtitle</p>
                    </div>
                    
                    <!-- Content Body -->
                    <div style="padding: 35px;">
                        $contentHtml
                        
                        ${if (buttonHtml.isNotEmpty()) """
                            <div style="text-align: center; margin: 35px 0 10px 0;">
                                $buttonHtml
                            </div>
                        """ else ""}
                        
                        <hr style="border: none; border-top: 1px solid #EEEEEE; margin: 30px 0;" />
                        
                        <!-- Footer -->
                        <div style="font-size: 12px; color: #999; text-align: center;">
                            <p style="margin: 5px 0;"><strong>Balik-Calasiao</strong> | Official Community Service App</p>
                            <p style="margin: 5px 0; font-style: italic;">$footerText</p>
                        </div>
                    </div>
                </div>
            </div>
        """.trimIndent()
    }

    /**
     * Notify user about a new message.
     */
    fun sendNewMessageNotification(receiverEmail: String, senderName: String, messageText: String) {
        val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm a", Locale.getDefault()).format(Date())
        val content = """
            <p style="font-size: 16px; margin-top: 0;">Hi there,</p>
            <p><strong>$senderName</strong> has sent you a direct message regarding your active report.</p>
            
            <div style="background-color: #F9F9F9; border-left: 4px solid #2E7D32; padding: 20px; margin: 25px 0; color: #444; font-style: italic;">
                "$messageText"
            </div>

            <p>To reply, please open the <strong>Balik-Calasiao</strong> mobile application on your device.</p>
        """.trimIndent()

        val html = getBaseHtmlTemplate(
            headerColor = "#2E7D32",
            headerTitle = "📩 New Message Received",
            headerSubtitle = "Received on $timestamp",
            contentHtml = content,
            footerText = "This is an automated notification. For your security, do not share your login credentials.",
            buttonHtml = """<p style="font-weight: bold; color: #2E7D32;">Check your "Messages" tab inside the app.</p>"""
        )

        sendEmail(receiverEmail, "", "New Message from $senderName", html)
    }

    /**
     * Notify user about a potential item match.
     */
    fun sendSmartMatchNotification(userEmail: String, itemName: String, matchType: String) {
        val content = """
            <p style="font-size: 16px; margin-top: 0;">Good news!</p>
            <p>Our intelligent matching engine has identified a potential <strong>$matchType</strong> for the item you reported: <strong>$itemName</strong>.</p>
            
            <div style="border: 1px dashed #FFA000; background-color: #FFFDE7; padding: 20px; margin: 25px 0; border-radius: 8px;">
                <p style="margin: 0; color: #444;">This automated discovery significantly increases the chances of retrieving/returning the item. We recommend reviewing the match details immediately.</p>
            </div>

            <p><strong>Next Steps:</strong></p>
            <ul style="padding-left: 20px; color: #555;">
                <li>Open the Balik-Calasiao app.</li>
                <li>Go to the <strong>Notifications</strong> or <strong>Matches</strong> tab.</li>
                <li>Review the item details and verification photo.</li>
            </ul>
        """.trimIndent()

        val html = getBaseHtmlTemplate(
            headerColor = "#FFA000",
            headerTitle = "✨ Smart Match Detected",
            headerSubtitle = "Intelligent AI Matching System",
            contentHtml = content,
            footerText = "Improving our community through technology."
        )

        sendEmail(userEmail, "", "✨ Smart Match Detected: $itemName", html)
    }

    /**
     * Notify user about claim status changes.
     */
    fun sendClaimStatusNotification(userEmail: String, itemName: String, status: String) {
        val isApproved = status.uppercase() == "APPROVED"
        val headerColor = if (isApproved) "#2E7D32" else "#C62828"
        val statusLabel = if (isApproved) "APPROVED ✅" else "REJECTED ❌"
        
        val content = """
            <p style="font-size: 16px; margin-top: 0;">Hello Citizen,</p>
            <p>This is an official update regarding your filed claim for the item: <strong>$itemName</strong>.</p>
            
            <div style="background-color: ${if (isApproved) "#F1F8E9" else "#FFEBEE"}; border-left: 5px solid $headerColor; padding: 20px; margin: 25px 0;">
                <p style="margin: 0; font-weight: bold; color: ${if (isApproved) "#2E7D32" else "#C62828"};">
                    STATUS: $statusLabel
                </p>
                <p style="margin: 5px 0 0 0; color: #555; font-size: 15px;">
                    ${if (isApproved) 
                        "Your claim has been verified and approved by the station administrator." 
                        else "Your claim has been reviewed and was unfortunately rejected."}
                </p>
            </div>

            ${if (isApproved) """
                <h3 style="color: #333; font-size: 16px; margin-bottom: 10px;">Steps for Retrieval:</h3>
                <ol style="padding-left: 20px; color: #555;">
                    <li style="margin-bottom: 8px;">Proceed to the <strong>Calasiao Police Station</strong> lobby.</li>
                    <li style="margin-bottom: 8px;">Bring a <strong>Valid Government ID</strong> for identity verification.</li>
                    <li style="margin-bottom: 8px;">Present your <strong>Claim Reference ID</strong> to the officer on duty.</li>
                </ol>
                <p style="font-size: 13px; color: #777; margin-top: 15px; background: #F9F9F9; padding: 10px; border-radius: 4px; display: inline-block;">
                    🕒 Station hours: 8:00 AM - 5:00 PM, Mon-Fri
                </p>
            """ else """
                <p>If you believe this decision was made in error, please visit the Calasiao Police Station to speak with an administrator or file a formal dispute through the app.</p>
            """}
        """.trimIndent()

        val html = getBaseHtmlTemplate(
            headerColor = headerColor,
            headerTitle = "Claim Resolution",
            headerSubtitle = "Case Update: $itemName",
            contentHtml = content,
            footerText = "Calasiao Police Department - Community Relations Division"
        )

        sendEmail(userEmail, "", "Update: Your claim for $itemName has been $status", html)
    }

    /**
     * Send an alert to a SPECIFIC admin based on their UID.
     * Fallback to broadcasting if the specific admin email can't be found.
     */
    fun sendTargetedAdminNotification(adminUid: String, type: String, itemName: String, reporterName: String, reporterEmail: String, details: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        db.collection("users").document(adminUid).get().addOnSuccessListener { snapshot ->
            val adminEmail = snapshot.getString("email")
            if (!adminEmail.isNullOrBlank()) {
                val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val content = """
                    <p style="font-size: 16px; margin-top: 0;">Hello Officer,</p>
                    <p>A citizen is following up on a case you recently handled. Immediate review is recommended.</p>
                    
                    <table style="width: 100%; margin: 25px 0; border-collapse: collapse; font-size: 14px;">
                        <tr style="background-color: #F9F9F9;">
                            <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE; width: 140px;">Action Category:</td>
                            <td style="padding: 12px; border-bottom: 1px solid #EEEEEE;">
                                <span style="background-color: #E8F5E9; color: #2E7D32; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; text-transform: uppercase;">$type</span>
                            </td>
                        </tr>
                        <tr>
                            <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE;">Item Context:</td>
                            <td style="padding: 12px; border-bottom: 1px solid #EEEEEE;">$itemName</td>
                        </tr>
                        <tr style="background-color: #F9F9F9;">
                            <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE;">Citizen Name:</td>
                            <td style="padding: 12px; border-bottom: 1px solid #EEEEEE;">$reporterName</td>
                        </tr>
                        <tr>
                            <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE;">Citizen Email:</td>
                            <td style="padding: 12px; border-bottom: 1px solid #EEEEEE; color: #2E7D32; font-weight: bold;">$reporterEmail</td>
                        </tr>
                    </table>

                    <div style="background-color: #FFFDE7; border-left: 5px solid #FBC02D; padding: 20px; margin: 25px 0; border-radius: 4px;">
                        <h3 style="margin: 0 0 10px 0; color: #827717; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;">Message Detail:</h3>
                        <p style="margin: 0; font-style: italic; font-size: 15px; color: #444;">"$details"</p>
                    </div>
                """.trimIndent()

                val html = getBaseHtmlTemplate(
                    headerColor = "#1B5E20",
                    headerTitle = "📥 Direct Response Alert",
                    headerSubtitle = "Case Update | $timestamp",
                    contentHtml = content,
                    footerText = "This is a targeted notification sent to the officer-in-charge.",
                    buttonHtml = """<a href="https://balikcalasiao.web.app/dashboard" style="background-color: #2E7D32; color: #FFFFFF; padding: 14px 28px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 15px; display: inline-block; box-shadow: 0 2px 5px rgba(0,0,0,0.1);">Open Admin Dashboard</a>"""
                )

                sendEmail(adminEmail, "", "Action Required: $type [$itemName]", html)
            } else {
                // Fallback to broadcast if the specific admin can't be reached
                sendAdminNotification(type, itemName, reporterName, reporterEmail, details)
            }
        }
    }

    /**
     * Send general system alerts to ALL accounts in the "admins" collection.
     */
    fun sendAdminNotification(type: String, itemName: String, reporterName: String, reporterEmail: String, details: String) {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        db.collection("admins").get().addOnSuccessListener { snapshot ->
            val emails = snapshot.documents.mapNotNull { it.getString("email") }.filter { it.isNotBlank() }
            if (emails.isEmpty()) return@addOnSuccessListener

            val timestamp = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()).format(Date())
            val content = """
                <p style="font-size: 16px; margin-top: 0; color: #C62828; font-weight: bold;">High Priority Task Detected</p>
                <p>The system has logged a new activity that requires administrative review within the Balik-Calasiao platform.</p>
                
                <table style="width: 100%; margin: 25px 0; border-collapse: collapse; font-size: 14px;">
                    <tr style="background-color: #F9F9F9;">
                        <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE; width: 140px;">Activity Type:</td>
                        <td style="padding: 12px; border-bottom: 1px solid #EEEEEE;">
                            <span style="background-color: #FFEBEE; color: #C62828; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; text-transform: uppercase;">$type</span>
                        </td>
                    </tr>
                    <tr>
                        <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE;">Related Item:</td>
                        <td style="padding: 12px; border-bottom: 1px solid #EEEEEE;">$itemName</td>
                    </tr>
                    <tr style="background-color: #F9F9F9;">
                        <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE;">Reported By:</td>
                        <td style="padding: 12px; border-bottom: 1px solid #EEEEEE;">$reporterName</td>
                    </tr>
                    <tr>
                        <td style="padding: 12px; font-weight: bold; border-bottom: 1px solid #EEEEEE;">Contact Email:</td>
                        <td style="padding: 12px; border-bottom: 1px solid #EEEEEE; color: #C62828; font-weight: bold;">$reporterEmail</td>
                    </tr>
                </table>

                <div style="background-color: #F5F5F5; border-left: 5px solid #757575; padding: 20px; margin: 25px 0; border-radius: 4px;">
                    <h3 style="margin: 0 0 10px 0; color: #424242; font-size: 13px; text-transform: uppercase; letter-spacing: 1px;">Actionable Details:</h3>
                    <p style="margin: 0; color: #616161; font-size: 15px;">$details</p>
                </div>
            """.trimIndent()

            val html = getBaseHtmlTemplate(
                headerColor = "#C62828",
                headerTitle = "🚨 System Alert",
                headerSubtitle = "Automated System Notification | $timestamp",
                contentHtml = content,
                footerText = "This is a system-generated broadcast sent to all station administrators.",
                buttonHtml = """<a href="https://balikcalasiao.web.app/dashboard" style="background-color: #1B5E20; color: #FFFFFF; padding: 14px 28px; text-decoration: none; border-radius: 6px; font-weight: bold; font-size: 15px; display: inline-block; box-shadow: 0 2px 5px rgba(0,0,0,0.1);">Login to Admin Dashboard</a>"""
            )

            for (email in emails) {
                sendEmail(email, "Official Admin", "🚨 System Alert: $type - $itemName", html)
            }
        }.addOnFailureListener { e ->
            println("EmailService: Failed to fetch admin emails for notification: ${e.message}")
        }
    }
}
