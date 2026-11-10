package com.ronial.app.conf.db;

import com.ronial.app.conf.db.strategies.MySQLConnector;
import com.ronial.app.conf.db.strategies.PostgreSQLConnector;
import com.ronial.app.context.Context;
import com.ronial.app.context.ContextProvider;
import com.ronial.app.views.LogFrame;

import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DatabaseConf implements Context {
    private Connection conn;
    public Connection getConnection() {
        return conn;
    }

    public enum DatabaseType {
        MYSQL,
        ORACLE,
        SQLSERVER,
        POSTGRESQL,
    }

    private final Map<DatabaseType, Class<? extends DatabaseConnector>> connectors;

    public DatabaseConf() {
        conn = null;
        connectors = new HashMap<>();
        addConnector(DatabaseType.MYSQL, MySQLConnector.class);
        addConnector(DatabaseType.POSTGRESQL, PostgreSQLConnector.class);
    }

    private void addConnector(DatabaseType type, Class<? extends DatabaseConnector> connector) {
        connectors.put(type, connector);
    }

    public void connect(DatabaseType type, DatabaseProperty property) {
        try {
            if (!connectors.containsKey(type)) {
                throw new RuntimeException("Unknown command: " + type.name());
            }
            Class<? extends DatabaseConnector> connector = connectors.get(type);
            DatabaseConnector connectorInstance = connector.getDeclaredConstructor().newInstance();
            conn = connectorInstance.connect(property);
            ContextProvider.<LogFrame>get(LogFrame.class).addLog(this.getClass(), "\n" + property);
        } catch (SQLException | NoSuchMethodException | InvocationTargetException | InstantiationException |
                 IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

}
