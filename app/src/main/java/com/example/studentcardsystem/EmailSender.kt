package com.example.studentcardsystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class EmailSender {
    companion object {
        private const val SMTP_HOST = "smtp.gmail.com"
        private const val SMTP_PORT = "587"

        suspend fun sendEmail(
            studentId: String,
            fromEmail: String,
            password: String,
            subject: String,
            bodyTemplate: String,
            emailDomain: String
        ): Boolean = withContext(Dispatchers.IO) {
            try {
                val toEmail = "${studentId.lowercase()}@$emailDomain"

                // テンプレートの置換処理
                val timestamp =
                    SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss", Locale.getDefault()).format(Date())
                val body = bodyTemplate
                    .replace("{STUDENT_ID}", studentId)
                    .replace("{TIMESTAMP}", timestamp)

                val props = Properties().apply {
                    put("mail.smtp.host", SMTP_HOST)
                    put("mail.smtp.port", SMTP_PORT)
                    put("mail.smtp.auth", "true")
                    put("mail.smtp.starttls.enable", "true")
                }

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(fromEmail, password)
                    }
                })

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(fromEmail))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
                    this.subject = subject
                    setText(body, "UTF-8")
                }

                Transport.send(message)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }
}
