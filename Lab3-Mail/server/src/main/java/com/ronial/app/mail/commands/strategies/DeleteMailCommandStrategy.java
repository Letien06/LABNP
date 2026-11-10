package com.ronial.app.mail.commands.strategies;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.exceptions.ServiceException;
import com.ronial.app.mail.Server;
import com.ronial.app.mail.commands.Command;
import com.ronial.app.mail.service.MailService;
import com.ronial.app.models.Request;
import com.ronial.app.models.Response;
import com.ronial.app.utils.RegexUtils;
import com.ronial.app.views.LogFrame;

import java.io.IOException;
import java.sql.SQLException;

public class DeleteMailCommandStrategy implements Command {
    private final MailService mailService;

    public DeleteMailCommandStrategy() {
        mailService = ContextProvider.get(MailService.class);
    }

    private void log(Object log) {
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(DeleteMailCommandStrategy.class, log);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        String email = request.getData().getString("email");
        int id = request.getData().getInt("id");

        log(request.toHostPortString() + ":" + email + " - Delete mail : " + id);

        Response response = new Response(true);
        try {
            if (!RegexUtils.isEmail(email)) {
                response.setSuccess(false)
                        .setMessage("Mail phải có dạng example@gmail.com");
                log(request.toHostPortString() + "- Delete mail failed!");
            } else {
                mailService.deleteEmail(email, id);
                response.setMessage("Xóa thư thành công😊");
                log(request.toHostPortString() + "- Delete mail successful!");
            }
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - Delete mail error: " + e.getMessage());
        } catch (SQLException e) {
            response.setSuccess(false)
                    .setMessage("Error delete mail");
            log(request.toHostPortString() + " - Delete mail error: " + e.getMessage());
        } finally {
            server.sendResponse(response, request.getPacket());
        }
    }
}
