package com.ronial.app.conf.db.query;

import java.util.Arrays;


public class CreateTableQuery {
    public enum Constraint {
        NOT_NULL("NOT NULL"),
        PRIMARY_KEY("PRIMARY KEY"),
        UNIQUE_KEY("UNIQUE"),
        AUTO_INCREMENT("AUTO_INCREMENT"),
        DEFAULT_CURRENT_TIMESTAMP("DEFAULT CURRENT_TIMESTAMP"),
        DEFAULT_CURRENT_TIMESTAMP_ON_UPDATE_CURRENT_TIMESTAMP("DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        final String value;

        Constraint(String value) {
            this.value = value;
        }
    }

    public enum Type {
        INT("INT"),
        VARCHAR("VARCHAR(255)"),
        TEXT("TEXT"),
        LONGTEXT("LONGTEXT"),
        BOOLEAN("BOOL"),
        DATE("DATE"),
        DATETIME("DATETIME"),
        TIME("TIME"),
        TIMESTAMP("TIMESTAMP"),
        ;
        final String value;

        Type(String value) {
            this.value = value;
        }

    }

    private final StringBuilder query;

    private CreateTableQuery(String tableName) {
        this.query = new StringBuilder()
                .append("CREATE TABLE IF NOT EXISTS ")
                .append(tableName).append("(");
    }

    public static CreateTableQuery createTable(String tableName) {
        return new CreateTableQuery(tableName);
    }

    public CreateTableQuery addField(String field, Type type, Constraint... constraint) {
        String cons = String.join(" ",
                Arrays.stream(constraint)
                        .map(cst -> cst.value)
                        .toList());
        this.query.append(field).append(" ")
                .append(type.value).append(" ")
                .append(cons)
                .append(",");
        return this;
    }

    public CreateTableQuery foreign(String fieldForeign, String table, String foreignKey) {
        this.query.append("FOREIGN KEY (")
                .append(fieldForeign)
                .append(")")
                .append(" REFERENCES ")
                .append(table).append("(").append(foreignKey).append(") ON DELETE CASCADE")
                .append(",");
        return this;
    }

    public String query() {
        return this.query
                .deleteCharAt(this.query.length() - 1)
                .append(")")
                .toString();
    }

}
