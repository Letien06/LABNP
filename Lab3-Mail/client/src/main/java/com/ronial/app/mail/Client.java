package com.ronial.app.mail;

import com.ronial.app.context.Context;
import com.ronial.app.models.Request;
import com.ronial.app.models.Response;

import java.io.IOException;
import java.net.*;

public class Client implements Context {
    private final DatagramSocket socket;
    private final InetAddress address;
    private final int port;
    public Client(String host,int port) throws SocketException, UnknownHostException {
        this.socket = new DatagramSocket();
        this.address = InetAddress.getByName(host);
        this.port = port;
    }
    public void sendRequest(Request request) throws IOException {
        byte[] buffer = request.toBytes();
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, port);
        socket.send(packet);
    }
    public Response receiveResponse() throws IOException {
        byte[] buffer = new byte[1610612736]; //1,5gb
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return Response.fromDatagramPacket(packet);
    }
}
