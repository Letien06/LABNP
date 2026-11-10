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
import org.json.JSONArray;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class GetMailsCommandStrategy implements Command {
    private final EmailRepository emailRepository;

    public GetMailsCommandStrategy() {
        emailRepository = ContextProvider.get(EmailRepository.class);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        String email = request.getData().getString("email");
        log(request.toHostPortString() + ":" + email + " - Get mails started!");

        Response response = new Response(true);
        try {
            List<Email> emailsSent = emailRepository.findAllEmailSentByEmail(email)
                    .stream()
                    .map(e -> {
                        e.setBody("Wait loading ... ");
                        return e;
                    })
                    .toList();
            List<Email> emailsReceive = emailRepository.findAllEmailReceiveByEmail(email).stream()
                    .map(e -> {
                        e.setBody("Wait loading ... ");
                        return e;
                    })
                    .toList();
            response.getJSON()
                    .put("emailsSent", new JSONArray(emailsSent).toString())
                    .put("emailsReceive", new JSONArray(emailsReceive).toString());
            response.setMessage("Lấy dữ liệu thành công😊");
            log(request.toHostPortString() + " - Get mails finished!");
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - Get mails error: " + e.getMessage());
        } catch (SQLException e) {
            response.setSuccess(false)
                    .setMessage("Error in SQL");
            log(request.toHostPortString() + " - Get mails error: " + e.getMessage());
        } finally {
            server.sendResponse(response, request.getPacket());
        }
    }

    private void log(Object log) {
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(GetMailsCommandStrategy.class, log);
    }
}
