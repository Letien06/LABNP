package com.ronial.app.models;

import com.ronial.app.utils.DateUtils;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class Email {
    private int id;
    private String from;
    private String to;
    private String subject;
    private String body;
    private String links;
    private String contentHtml;
    private String createdAt;
    private String transferFrom;
    private boolean seen;

    public Email() {
        id = 0;
        from = "";
        to = "";
        subject = "";
        body = "";
        links = "";
        seen = false;
        contentHtml = "";
        createdAt = "";
        transferFrom = "";
    }

    public Email(int id,
                 String from,
                 String to,
                 String subject,
                 String body,
                 String links,
                 String contentHtml,
                 boolean isSeen,
                 String createdAt,
                 String transferFrom) {
        this.from = from;
        this.id = id;
        this.to = to;
        this.subject = subject;
        this.body = body;
        this.links = links;
        this.contentHtml = contentHtml;
        this.seen = isSeen;
        this.createdAt = createdAt;
        this.transferFrom = transferFrom;
    }
    public static Email fromResultSet(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new Email(
                rs.getInt("id"),
                rs.getString("senderMail"),
                rs.getString("recipientMail"),
                rs.getString("subject"),
                rs.getString("body"),
                rs.getString("links"),
                "",
                rs.getBoolean("is_seen"),
                DateUtils.formatInstant(createdAt.toInstant()),
                rs.getString("transferFrom")
        );
    }

    public static Email fromJSON(String json) {
        JSONObject obj = new JSONObject(json);
        int id = obj.getInt("id");
        String from = obj.getString("from");
        String to = obj.getString("to");
        String subject = obj.getString("subject");
        String body = obj.getString("body");
        String links = obj.getString("links");
        String contentHtml = obj.getString("contentHtml");
        boolean isSeen = obj.getBoolean("seen");
        String createdAt = obj.getString("createdAt");
        String transferFrom = obj.getString("transferFrom");
        return new Email(id, from, to, subject, body, links, contentHtml, isSeen, createdAt, transferFrom);
    }

    public String toJSON() {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("from", from);
        obj.put("to", to);
        obj.put("subject", subject);
        obj.put("body", body);
        obj.put("links", links);
        obj.put("contentHtml", contentHtml);
        obj.put("seen", seen);
        obj.put("createdAt", createdAt);
        obj.put("transferFrom", transferFrom);
        return obj.toString();
    }

    public int getId() {
        return id;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public String getLinks() {
        return links;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void setLinks(String links) {
        this.links = links;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }

    public void setIsSeen(boolean isSeen) {
        this.seen = isSeen;
    }

    public boolean isSeen() {
        return seen;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setSeen(boolean seen) {
        this.seen = seen;
    }

    public String getTransferFrom() {
        return transferFrom;
    }

    public void setTransferFrom(String transferFrom) {
        this.transferFrom = transferFrom;
    }
}
