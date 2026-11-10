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

public class TransferMailCommandStrategy implements Command {
    private final MailService mailService;
    private final EmailRepository emailRepository;

    public TransferMailCommandStrategy() {
        mailService = ContextProvider.get(MailService.class);
        emailRepository = ContextProvider.get(EmailRepository.class);
    }

    private void log(Object log) {
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(TransferMailCommandStrategy.class, log);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        log(request.toHostPortString() + " - Transfer mail started!");

        String emailJson = request.getData().getString("email");
        String emailsJson = request.getData().getString("emails");

        String[] emails = emailsJson.split(",");
        Email email = Email.fromJSON(emailJson);

        Response response = new Response(true);
        if (emailsJson.contains(email.getTransferFrom())){
            response.setSuccess(false)
                    .setMessage("Không thể chuyển tiếp mail cho chính mình!");
            server.sendResponse(response, request.getPacket());
            return;
        }
        try {
            email = emailRepository.findById(email.getId()).get();
            mailService.transferMail(emails, email);
            response.setMessage("Chuyển tiếp mail thành công 😊");
            log(request.toHostPortString() + " - Transfer mail completed!");
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - Transfer mail error: " + e.getMessage());
        } catch (SQLException e) {
            response.setSuccess(false)
                    .setMessage("SQL ERROR");
            log(request.toHostPortString() + " - Transfer mail error: " + e.getMessage());
        } finally {
            server.sendResponse(response, request.getPacket());
        }
    }
}
