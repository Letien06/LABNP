package com.ronial.app.mail.commands.strategies;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.exceptions.ServiceException;
import com.ronial.app.mail.Server;
import com.ronial.app.mail.commands.Command;
import com.ronial.app.mail.service.MailService;
import com.ronial.app.models.Email;
import com.ronial.app.models.Request;
import com.ronial.app.models.Response;
import com.ronial.app.utils.DateUtils;
import com.ronial.app.utils.RegexUtils;
import com.ronial.app.views.LogFrame;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public class SendMailCommandStrategy implements Command {
    private final MailService mailService;

    public SendMailCommandStrategy() {
        mailService = ContextProvider.get(MailService.class);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        String emailJson = request.getData().getString("email");
        Email email = Email.fromJSON(emailJson);

        log(request.toHostPortString() + ":" + email.getFrom() + " - Send mail started!");
        logSendMail(request.toHostPortString(), email);

        //Create response
        Response response = new Response(true);
        if (email.getTo().contains(email.getFrom())) {
            response.setSuccess(false)
                    .setMessage("Không thể tự gửi mail cho chính mình!");
            server.sendResponse(response, request.getPacket());
            return;
        }

        try {
            mailService.saveEmail(email);
            response.setMessage("Gửi mail thành công 😊");
            log(request.toHostPortString() + " - Send mail completed!");
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - Send mail error: " + e.getMessage());

        } finally {
            server.sendResponse(response, request.getPacket());
        }
    }

    private void logSendMail(String hostPort, Email email) {
        StringBuilder sendLog = new StringBuilder();
        sendLog.append("\n\n").append(hostPort).append(" - ").append("FROM: ").append(email.getFrom()).append("\n");
        sendLog.append(hostPort).append(" - ").append("TO: ").append(email.getTo()).append("\n");
        sendLog.append(hostPort).append(" - ").append("SUBJECT: ").append(email.getSubject()).append("\n");
        sendLog.append(hostPort).append(" - ").append("BODY: ").append(email.getBody()).append("\n");
        sendLog.append(hostPort).append(" - ").append("ATTACHMENTS: ").append(email.getLinks()).append("\n");
        log(sendLog.toString());
    }

    private void log(Object log) {
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(SendMailCommandStrategy.class, log);
    }
}
