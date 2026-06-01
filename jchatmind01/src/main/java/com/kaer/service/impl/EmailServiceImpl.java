package com.kaer.service.impl;

import com.kaer.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件服务实现类
 * <p>
 * 负责发送邮件通知，使用 Spring 的 JavaMailSender 发送简单文本邮件。
 * 支持异步发送，避免阻塞主线程。
 */
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    /**
     * Spring 邮件发送器，负责实际的邮件发送操作
     */
    private final JavaMailSender mailSender;

    /**
     * 发件人邮箱地址
     * <p>
     * 从配置文件 spring.mail.username 中读取
     */
    @Value("${spring.mail.username}")
    private String form;

    /**
     * 构造函数，注入 JavaMailSender
     *
     * @param mailSender Spring 邮件发送器实例
     */
    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 异步发送邮件
     * <p>
     * 使用 {@code @Async} 注解标记为异步执行，通过线程池异步发送邮件，
     * 避免阻塞调用方线程。发送结果仅通过日志记录，不返回状态。
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文内容（纯文本）
     */
    @Override
    @Async
    public void sendEmailAsync(String to, String subject, String content) {
        try {
            // 创建简单邮件消息对象
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setTo(to);           // 设置收件人
            mailMessage.setSubject(subject); // 设置邮件主题
            mailMessage.setText(content);    // 设置邮件正文
            mailMessage.setFrom(form);       // 设置发件人

            // 发送邮件
            mailSender.send(mailMessage);
            log.info("异步发送邮件成功，收件人: {}, 主题: {}", to, subject);
        } catch (MailException e) {
            // 捕获邮件发送异常，记录日志但不抛出
            log.error("异步发送邮件失败，收件人: {}, 主题: {}, 错误: {}", to, subject, e.getMessage(), e);
        }
    }
}