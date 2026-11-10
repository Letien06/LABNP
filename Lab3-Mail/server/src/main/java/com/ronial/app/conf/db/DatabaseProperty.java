package com.ronial.app.conf.db;

public class DatabaseProperty {
    private String hostname;
    private int port;
    private String username;
    private String password;
    private String database;

    public DatabaseProperty(String hostname, int port, String username, String password, String database) {
        this.hostname = hostname;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabase() {
        return database;
    }

    @Override
    public String toString() {
        return "\t\tDatabaseProperty:" + '\n' +
                "\t\thostname = " + hostname + '\n' +
                "\t\tport = " + port + '\n' +
                "\t\tusername = " + username + '\n' +
                "\t\tpassword = " + password + '\n' +
                "\t\tdatabase = " + database + '\n';
    }
}
