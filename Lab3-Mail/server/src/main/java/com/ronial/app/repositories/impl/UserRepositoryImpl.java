package com.ronial.app.repositories.impl;

import com.ronial.app.models.Email;
import com.ronial.app.models.User;
import com.ronial.app.repositories.UserRepository;
import com.ronial.app.utils.DateUtils;

import java.sql.*;
import java.util.Optional;

public class UserRepositoryImpl implements UserRepository {
    private final Connection connection;

    public UserRepositoryImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public User save(User user) throws SQLException {
        final String sql = "INSERT INTO `users` (`password`,`fullName`,`email`) VALUES (?,?,?);";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, user.getPassword());
        ps.setString(2, user.getName());
        ps.setString(3, user.getEmail());
        int rowsInserted = ps.executeUpdate();
        ps.close();
        if (rowsInserted > 0) {
            return user;
        }
        return null;
    }

    @Override
    public Optional<User> findById(Integer id) throws SQLException {
        String sql = "SELECT * FROM `users` WHERE `id` = ?;";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setLong(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return Optional.of(User.fromResultSet(rs));
        }
        return Optional.empty();
    }

    @Override
    public void deleteById(Integer id) {

    }

    @Override
    public Optional<User> findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM `users` WHERE `email` = ?;";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return Optional.of(User.fromResultSet(rs));
        }
        return Optional.empty();
    }
}
