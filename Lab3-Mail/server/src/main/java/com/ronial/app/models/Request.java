package com.ronial.app.models;

import com.ronial.app.mail.CommandType;
import org.json.JSONObject;

import java.net.DatagramPacket;

public class Request {
    private final CommandType command;
    private final JSONObject data;
    private final DatagramPacket packet;
    private Request(DatagramPacket packet) {
        this.packet = packet;
        JSONObject request = new JSONObject(
                new String(packet.getData(),0,packet.getLength()));
        this.data = request;
        this.command = CommandType.getFromString(request.getString("command"));
    }
    public static Request fromDatagramPacket(DatagramPacket packet) {
        return new Request(packet);
    }

    public CommandType getCommand() {
        return command;
    }

    public JSONObject getData() {
        return data;
    }
    public DatagramPacket getPacket() {
        return packet;
    }
    public String toHostPortString() {
        return packet.getAddress().getHostAddress() + ":" + packet.getPort();
    }
}
