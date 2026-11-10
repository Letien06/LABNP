package com.ronial.app.conf.db;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface DatabaseConnector {
    Connection connect(DatabaseProperty property) throws SQLException;
}
