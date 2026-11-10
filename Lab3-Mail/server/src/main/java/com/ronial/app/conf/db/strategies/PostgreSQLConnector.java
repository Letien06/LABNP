package com.ronial.app.conf.db.strategies;

import com.ronial.app.conf.db.DatabaseConnector;
import com.ronial.app.conf.db.DatabaseProperty;

import java.sql.Connection;
import java.sql.SQLException;

public class PostgreSQLConnector implements DatabaseConnector {
    @Override
    public Connection connect(DatabaseProperty property) throws SQLException {
        return null;
    }
}
