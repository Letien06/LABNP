package com.ronial.app.conf.db.repository;

import java.sql.SQLException;
import java.util.Optional;

public interface Repository<Clazz, IdType> {
    Clazz save(Clazz clazz) throws SQLException;

    Optional<Clazz> findById(IdType id) throws SQLException;

    void deleteById(IdType id);

}
