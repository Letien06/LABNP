package com.ronial.app.mail.commands.strategies;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.exceptions.ServiceException;
import com.ronial.app.mail.Server;
import com.ronial.app.mail.commands.Command;
import com.ronial.app.mail.service.MailService;
import com.ronial.app.models.Email;
import com.ronial.app.models.Request;
import com.ronial.app.models.Response;
import com.ronial.app.repositories.EmailRepository;
import com.ronial.app.views.LogFrame;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;

public class ReadMailCommandStrategy implements Command {
    private final MailService mailService;
    private final EmailRepository emailRepository;

    public ReadMailCommandStrategy() {
        mailService = ContextProvider.get(MailService.class);
        emailRepository = ContextProvider.get(EmailRepository.class);
    }

    private void log(Object log) {
        ContextProvider
                .<LogFrame>get(LogFrame.class)
                .addLog(ReadMailCommandStrategy.class, log);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        String email = request.getData().getString("email");
        int mailId = request.getData().getInt("id");
        log(request.toHostPortString() + " - " + email + " read mail " + mailId);
        Response response = new Response(true);
        try {
            Optional<Email> emailDb = emailRepository.findById(mailId);
            if (emailDb.isPresent()) {
                response.getJSON().put("email", emailDb.get().toJSON());
                response.setMessage("Read successfully");

                if (!emailDb.get().isSeen()) {
                    new Thread(() -> {
                        try {
                            mailService.readMail(mailId);
                        } catch (SQLException e) {
                            log(request.toHostPortString() + " - Read mail error: " + e.getMessage());
                        }
                    }).start();
                }
            } else {
                throw new ServiceException("Email not found");
            }
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - " + email + " read mail errors:  " + mailId + " -> " + e.getMessage());
        } catch (SQLException e) {
            response.setSuccess(false)
                    .setMessage("Error in SQL");
            log(request.toHostPortString() + " - " + email + " read mail errors:  " + mailId + " -> " + e.getMessage());
        } finally {
            server.sendResponse(response, request.getPacket());
        }
    }
}
