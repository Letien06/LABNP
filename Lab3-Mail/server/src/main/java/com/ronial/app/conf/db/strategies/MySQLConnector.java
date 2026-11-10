package com.ronial.app.conf.db.strategies;

import com.mysql.cj.log.Log;
import com.ronial.app.conf.db.DatabaseConnector;
import com.ronial.app.conf.db.DatabaseProperty;
import com.ronial.app.conf.db.query.CreateTableQuery;
import com.ronial.app.context.ContextProvider;
import com.ronial.app.views.LogFrame;

import java.sql.*;


public class MySQLConnector implements DatabaseConnector {
    @Override
    public Connection connect(DatabaseProperty property) throws SQLException {
        DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
        Connection conn = DriverManager.getConnection("jdbc:mysql://" +
                        property.getHostname() + ":" +
                        property.getPort() + "/" +
                        property.getDatabase(),
                property.getUsername(),
                property.getPassword());
        String sql = queryCreateTables();
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(this.getClass(), "Connected to MySQL : " + conn.getCatalog());
        ContextProvider.<LogFrame>get(LogFrame.class)
                .addLog(this.getClass(),"Create table query: \n"+ sql);
        return conn;
    }

    private String queryCreateTables() {
        return CreateTableQuery.createTable("users")
                .addField("id", CreateTableQuery.Type.INT, CreateTableQuery.Constraint.PRIMARY_KEY, CreateTableQuery.Constraint.AUTO_INCREMENT)
                .addField("fullName", CreateTableQuery.Type.VARCHAR, CreateTableQuery.Constraint.NOT_NULL)
                .addField("email", CreateTableQuery.Type.VARCHAR, CreateTableQuery.Constraint.NOT_NULL, CreateTableQuery.Constraint.UNIQUE_KEY)
                .addField("password", CreateTableQuery.Type.TEXT, CreateTableQuery.Constraint.NOT_NULL)
                .addField("created_at", CreateTableQuery.Type.TIMESTAMP, CreateTableQuery.Constraint.DEFAULT_CURRENT_TIMESTAMP)
                .addField("updated_at", CreateTableQuery.Type.TIMESTAMP, CreateTableQuery.Constraint.DEFAULT_CURRENT_TIMESTAMP_ON_UPDATE_CURRENT_TIMESTAMP)
                .query() + ";\n" +
                CreateTableQuery.createTable("emails")
                        .addField("id", CreateTableQuery.Type.INT, CreateTableQuery.Constraint.PRIMARY_KEY, CreateTableQuery.Constraint.AUTO_INCREMENT)
                        .addField("sender", CreateTableQuery.Type.INT)
                        .addField("recipient", CreateTableQuery.Type.INT)
                        .addField("subject", CreateTableQuery.Type.TEXT, CreateTableQuery.Constraint.NOT_NULL)
                        .addField("body", CreateTableQuery.Type.LONGTEXT, CreateTableQuery.Constraint.NOT_NULL)
                        .addField("links", CreateTableQuery.Type.LONGTEXT)
                        .addField("transferFrom", CreateTableQuery.Type.VARCHAR)
                        .addField("is_seen", CreateTableQuery.Type.BOOLEAN, CreateTableQuery.Constraint.NOT_NULL)
                        .addField("is_sender_remove", CreateTableQuery.Type.BOOLEAN, CreateTableQuery.Constraint.NOT_NULL)
                        .addField("is_recipient_remove", CreateTableQuery.Type.BOOLEAN, CreateTableQuery.Constraint.NOT_NULL)
                        .addField("created_at", CreateTableQuery.Type.TIMESTAMP, CreateTableQuery.Constraint.DEFAULT_CURRENT_TIMESTAMP)
                        .foreign("sender", "users", "id")
                        .foreign("recipient", "users", "id")
                        .query();
    }
}
