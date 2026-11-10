package com.ronial.app.models;

import com.ronial.app.utils.DateUtils;
import org.json.JSONObject;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

public class User {
    private int id;
    private String name;
    private String email;
    private String password;
    private String createdAt;
    public User() {}
    public User(int id,
                String name,
                String email,
                String password,
                String createdAt) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.createdAt = createdAt;
        this.id = id;
    }

    public static User fromJson(String json) {
        JSONObject jsonObject = new JSONObject(json);
        String name = jsonObject.getString("name");
        String email = jsonObject.getString("email");
        String password = jsonObject.getString("password");
        String createdAt = jsonObject.getString("createdAt");
        int id = jsonObject.getInt("id");
        return new User(id,name, email, password, createdAt);
    }

    public String toJson() {
        return new JSONObject()
                .put("name", name)
                .put("email", email)
                .put("password", password)
                .put("createdAt", createdAt)
                .put("id", id)
                .toString();
    }
    public static User fromResultSet(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new User(
                rs.getInt("id"),
                rs.getString("fullName"),
                rs.getString("email"),
                rs.getString("password"),
                DateUtils.formatInstant(createdAt.toInstant())
        );
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    public String getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
}
