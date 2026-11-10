package com.ronial.app.repositories;

import com.ronial.app.conf.db.repository.Repository;
import com.ronial.app.context.Context;
import com.ronial.app.models.Email;
import com.ronial.app.models.User;

import java.sql.SQLException;
import java.util.Optional;

public interface UserRepository extends Context, Repository<User, Integer> {
    Optional<User> findByEmail(String email) throws SQLException;
}
