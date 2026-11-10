package com.ronial.app.mail.service;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.exceptions.ServiceException;
import com.ronial.app.infrastructure.mail.SessionMailer;
import com.ronial.app.mail.MailHtmlFormat;
import com.ronial.app.models.Email;
import com.ronial.app.models.User;
import com.ronial.app.repositories.EmailRepository;
import com.ronial.app.repositories.UserRepository;
import com.ronial.app.security.RSASecurity;
import com.ronial.app.utils.DateUtils;
import com.ronial.app.views.LogFrame;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class MailServiceImpl implements MailService {
    private final RSASecurity security;
    private final UserRepository userRepository;
    private final SessionMailer sessionMailer;
    private final EmailRepository emailRepository;

    public MailServiceImpl() {
        security = ContextProvider.get(RSASecurity.class);
        userRepository = ContextProvider.get(UserRepository.class);
        sessionMailer = ContextProvider.get(SessionMailer.class);
        emailRepository = ContextProvider.get(EmailRepository.class);
    }

    @Override
    public void createMailAccount(User user) throws ServiceException {
        try {
            String passwordHash = security.encode(user.getPassword());
            user.setPassword(passwordHash);
            User userSaved = userRepository.save(user);
            if (userSaved == null) {
                throw new ServiceException("Could not save user");
            }
        } catch (NoSuchPaddingException | NoSuchAlgorithmException | IllegalBlockSizeException | InvalidKeyException |
                 BadPaddingException e) {
            throw new ServiceException(e.getMessage());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public User loginMailAccount(String email, String password) throws ServiceException {
        try {
            Optional<User> user = userRepository.findByEmail(email);
            if (user.isEmpty()) {
                throw new ServiceException("Invalid email");
            }
            String passwordDecode = security.decode(user.get().getPassword());
            if (!password.equals(passwordDecode)) {
                throw new ServiceException("Wrong password");
            }
            return user.get();

        } catch (NoSuchPaddingException | IllegalBlockSizeException | NoSuchAlgorithmException |
                 BadPaddingException | InvalidKeyException e) {
            ContextProvider.<LogFrame>get(LogFrame.class)
                    .addLog(MailServiceImpl.class, e.getMessage());
            throw new ServiceException("System error");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveEmail(Email email) throws ServiceException {
        StringBuilder errors = new StringBuilder();
        List<String> recipients = Arrays.stream(email.getTo().split(",")).toList();
        recipients.forEach(recipient -> {
            try {
                saveEmailItem(recipient, email);
            } catch (ServiceException e) {
                errors.append(e.getMessage()).append(",");
            }
        });
        if (!errors.isEmpty())
            throw new ServiceException(errors.deleteCharAt(errors.length() - 1).toString());
        email.setCreatedAt(DateUtils.formatInstant(Instant.now()));
        new Thread(() -> {
            recipients.forEach(recipient -> {
                try {
                    Message message = new MimeMessage(sessionMailer.getSession());
                    message.setFrom(new InternetAddress(email.getFrom()));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
                    message.setSubject(email.getSubject());
                    message.setContent(MailHtmlFormat.toContentHtml(email), "text/html; charset=UTF-8");
                    // Gửi email
                    Transport.send(message);
                } catch (MessagingException e) {
                    ContextProvider.<LogFrame>get(LogFrame.class)
                            .addLog(MailServiceImpl.class, e.getMessage());
                }
            });
        }).start();
    }

    private void saveEmailItem(String to, final Email email) throws ServiceException {
        try {
            email.setTo(to.trim());
            Email emailSaved = emailRepository.save(email);
            if (emailSaved == null) {
                throw new ServiceException("Could not save email");
            }
        } catch (SQLException e) {
            ContextProvider.<LogFrame>get(LogFrame.class)
                    .addLog(MailServiceImpl.class, e.getMessage());
            throw new ServiceException("Error saving email in SQL");
        }
    }

    @Override
    public void deleteEmail(String email, int id) throws ServiceException, SQLException {
        Optional<Email> emailDb = emailRepository.findById(id);
        if (emailDb.isEmpty()) {
            throw new ServiceException("Email not found");
        }
        if (emailDb.get().getFrom().equals(email)) {
            emailRepository.deleteEmailBySender(id);
        } else {
            emailRepository.deleteEmailByRecipient(id);
        }
    }

    @Override
    public void transferMail(String[] emails, Email email) throws ServiceException {
        Arrays.stream(emails).forEach(name -> {
            try {
                email.setTo(name);
                emailRepository.saveWithRemoveForSenderAndReceiver(email, true, false);
            } catch (ServiceException | SQLException e) {
                ContextProvider.<LogFrame>get(LogFrame.class)
                        .addLog(MailServiceImpl.class, e.getMessage());
            }
        });
        new Thread(() -> {
            Arrays.stream(emails).forEach(recipient -> {
                try {
                    Message message = new MimeMessage(sessionMailer.getSession());
                    message.setFrom(new InternetAddress(email.getFrom()));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient));
                    message.setSubject(email.getSubject());
                    message.setContent(MailHtmlFormat.toContentHtml(email), "text/html; charset=UTF-8");
                    // Gửi email
                    Transport.send(message);
                } catch (MessagingException e) {
                    ContextProvider.<LogFrame>get(LogFrame.class)
                            .addLog(MailServiceImpl.class, e.getMessage());
                }
            });
        }).start();
    }

    @Override
    public void readMail(int mailId) throws ServiceException, SQLException {
        emailRepository.readEmail(mailId);
    }
}
