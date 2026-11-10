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


public class RegisterCommandStrategy implements Command {
    private final MailService mailService;

    public RegisterCommandStrategy() {
        mailService = ContextProvider.get(MailService.class);
    }

    @Override
    public void execute(Server server, Request request) throws IOException {
        log(request.toHostPortString() + " - Register account start");
        //Get info from request
        String name = request.getData().getString("name");
        String password = request.getData().getString("password");
        String email = request.getData().getString("email");
        //Create response
        Response response = new Response(true);
        try {
            //Valid email
            if (!RegexUtils.isEmail(email)) {
                response.setSuccess(false)
                        .setMessage("Mail phải có dạng example@gmail.com");
                log(request.toHostPortString() + " - Register account error");
            } else {
                //Create user object
                User user = new User();
                user.setName(name);
                user.setPassword(password);
                user.setEmail(email);
                //Execute create mail but if you have error then catch ServiceException
                mailService.createMailAccount(user);
                response.setMessage("Đăng ký thành công 😊");
                log(request.toHostPortString() + " - Register account successfully");
            }
        } catch (ServiceException e) {
            response.setSuccess(false)
                    .setMessage(e.getMessage());
            log(request.toHostPortString() + " - Register account error: " + e.getMessage());
        } finally {
            //Send response
            server.sendResponse(response, request.getPacket());
        }
    }

    private void log(Object log) {
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(RegisterCommandStrategy.class, log);
    }
}
