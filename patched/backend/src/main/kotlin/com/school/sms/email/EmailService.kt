package com.school.sms.email

import jakarta.mail.internet.MimeMessage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class EmailService(
    private val mailSender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
    @Value("\${app.mail.enabled}") private val enabled: Boolean
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    fun send(to: String, subject: String, htmlBody: String) {
        if (!enabled) { log.info("[EMAIL DISABLED] to={} subject={}", to, subject); return }
        try {
            val msg: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(msg, true, "UTF-8")
            helper.setFrom(from); helper.setTo(to); helper.setSubject(subject); helper.setText(htmlBody, true)
            mailSender.send(msg)
            log.info("Email sent to {}", to)
        } catch (e: Exception) { log.error("Email failed to {}", to, e) }
    }

    fun sendLoginAlert(to: String, username: String, ip: String?) =
        send(to, "New login to your account",
            "<p>Hello $username,</p><p>A new login was detected from IP: <b>${ip ?: "unknown"}</b>.</p>")

    fun sendFeeReminder(to: String, studentName: String, amount: String, dueDate: String) =
        send(to, "Fee Payment Reminder",
            "<p>Dear Parent,</p><p>This is a reminder that fee of <b>$amount</b> for <b>$studentName</b> is due on <b>$dueDate</b>.</p>")

    fun sendPasswordReset(to: String, fullName: String, token: String) = send(to, "Password Reset Request", "<p>Hello $fullName,</p><p>Use this token to reset your password: <b>$token</b></p><p>This token expires in 1 hour.</p><p>If you did not request this, ignore this email.</p>")

    fun sendAssignmentNotification(to: String, title: String, dueDate: String) =
        send(to, "New Assignment: $title",
            "<p>A new assignment <b>$title</b> has been posted. Due: <b>$dueDate</b>.</p>")
}
