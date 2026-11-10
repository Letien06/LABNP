package com.ronial.app.models;

import org.json.JSONObject;

public class Response {
    private final JSONObject res;
    private boolean success;
    private String message;
    public Response(boolean success) {
        res = new JSONObject();
        this.success = success;
        this.message = "";
    }
    public JSONObject getJSON() {
        return res;
    }

    public Response setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public Response setMessage(String message) {
        this.message = message;
        return this;
    }

    public byte[] toBytes(){
        res.put("success", success);
        res.put("message", message);
        return res.toString().getBytes();
    }

    @Override
    public String toString() {
        return "Response{" +
                "res=" + res +
                ", success=" + success +
                ", message='" + message + '\'' +
                '}';
    }
}
