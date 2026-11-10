package com.ronial.app.models;

import com.ronial.app.mail.CommandType;
import org.json.JSONObject;

public class Request {
    private final CommandType command;
    private final JSONObject data;
    public Request(CommandType command) {
        this.command = command;
        this.data = new JSONObject();
    }
    public JSONObject getData() {
        return data;
    }
    public byte[] toBytes(){
        this.data.put("command", command.name());
        return this.data.toString().getBytes();
    }
}
