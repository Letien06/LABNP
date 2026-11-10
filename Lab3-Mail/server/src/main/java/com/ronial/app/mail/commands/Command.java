package com.ronial.app.mail.commands;

import com.ronial.app.mail.Server;
import com.ronial.app.models.Request;

import java.io.IOException;

@FunctionalInterface
public interface Command {
    void execute(Server server, Request request) throws IOException;
}
