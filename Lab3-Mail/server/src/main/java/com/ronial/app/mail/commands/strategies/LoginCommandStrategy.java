package com.ronial.app.mail.commands.strategies;

import com.ronial.app.context.ContextProvider;
import com.ronial.app.exceptions.ServiceException;
import com.ronial.app.mail.Server;
import com.ronial.app.mail.commands.Command;
import com.ronial.app.mail.service.MailService;
import com.ronial.app.models.Request;
import com.ronial.app.models.Response;
import com.ronial.app.models.User;
import com.ronial.app.utils.RegexUtils;
import com.ronial.app.views.LogFrame;

import java.io.IOException;

public class LoginCommandStrategy implements Command {
    private final MailService mailService;

    public LoginCommandStrategy() {
        mailService = ContextProvider.get(MailService.class);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        //Get password and email from request
        String password = request.getData().getString("password");
        String email = request.getData().getString("email");
        log(request.toHostPortString() +  ":" + email + " - Login started!");

        //Create response
        Response response = new Response(true);
        try {
            //Valid email
            if (!RegexUtils.isEmail(email)) {
                response.setSuccess(false)
                        .setMessage("Mail phải có dạng example@gmail.com");
                log(request.toHostPortString() + " - Login failed!");
            } else {
                //Execute login if u have error then catch ServiceException
                User user = mailService.loginMailAccount(email, password);

                response.setMessage("Đăng nhập thành công 😊");
                response.getJSON().put("user", user.toJson());
                log(request.toHostPortString() + " - Login successful!");
            }
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - Login error: " + e.getMessage());
        } finally {
            //send response
            server.sendResponse(response, request.getPacket());
        }
    }
    private void log(Object log) {
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(LoginCommandStrategy.class, log);
    }
}
