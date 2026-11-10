package com.ronial.app.mail.commands;

import com.ronial.app.mail.CommandType;
import com.ronial.app.mail.Server;
import com.ronial.app.mail.commands.strategies.*;
import com.ronial.app.models.Request;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class CommandExecutor {
    private final Server server;
    private final Map<CommandType, Class<? extends Command>> commands;

    public CommandExecutor(Server server) {
        this.server = server;
        this.commands = new HashMap<>();
        putCommand(CommandType.REGISTER_ACCOUNT, RegisterCommandStrategy.class);
        putCommand(CommandType.LOGIN_ACCOUNT, LoginCommandStrategy.class);
        putCommand(CommandType.SEND_MAIL, SendMailCommandStrategy.class);
        putCommand(CommandType.GET_MAILS, GetMailsCommandStrategy.class);
        putCommand(CommandType.DELETE_MAIL, DeleteMailCommandStrategy.class);
        putCommand(CommandType.TRANSFER_MAIL, TransferMailCommandStrategy.class);
        putCommand(CommandType.READ_MAIL, ReadMailCommandStrategy.class);
    }

    private void putCommand(CommandType type, Class<? extends Command> command) {
        commands.put(type, command);
    }

    public void execute(Request request) {
        try {
            if (!commands.containsKey(request.getCommand())) {
                throw new RuntimeException("Unknown command: " + request.getCommand());
            }
            Class<? extends Command> commandClass = commands.get(request.getCommand());
            Command command = (commandClass.getDeclaredConstructor()).newInstance();
            command.execute(server, request);
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
