package com.ronial.app.repositories.impl;

import com.ronial.app.conf.db.repository.Repository;
import com.ronial.app.context.Context;
import com.ronial.app.models.Email;
import com.ronial.app.models.User;
import com.ronial.app.repositories.EmailRepository;
import com.ronial.app.repositories.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmailRepositoryImpl implements EmailRepository {
    private final Connection connection;
    private final UserRepository userRepository;

    public EmailRepositoryImpl(Connection connection,
                               UserRepository userRepository) {
        this.connection = connection;
        this.userRepository = userRepository;
    }

    @Override
    public List<Email> findAllEmailSentByEmail(String email) throws SQLException {
        List<Email> emails = new ArrayList<>();
        String sql = "SELECT e.*, uS.email as senderMail, uR.email as recipientMail " +
                "FROM emails as e " +
                "JOIN users as uS ON uS.id = e.sender " +
                "JOIN users as uR ON uR.id = e.recipient " +
                "WHERE uS.email = ? AND e.is_sender_remove = FALSE ORDER BY e.id DESC";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            emails.add(Email.fromResultSet(rs));
        }
        return emails;
    }

    @Override
    public List<Email> findAllEmailReceiveByEmail(String email) throws SQLException {
        List<Email> emails = new ArrayList<>();
        String sql = "SELECT e.*, uS.email as senderMail, uR.email as recipientMail " +
                "FROM emails as e " +
                "JOIN users as uS ON uS.id = e.sender " +
                "JOIN users as uR ON uR.id = e.recipient " +
                "WHERE uR.email = ? AND e.is_recipient_remove = FALSE ORDER BY e.id DESC ";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setString(1, email);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            emails.add(Email.fromResultSet(rs));
        }
        return emails;
    }

    @Override
    public Email saveWithRemoveForSenderAndReceiver(Email email, boolean senderRemove, boolean receiverRemove) throws SQLException {
        Optional<User> userSender = userRepository.findByEmail(email.getFrom());
        Optional<User> userRecipient = userRepository.findByEmail(email.getTo());
        if (userRecipient.isEmpty()) {
            throw new SQLException("Recipient not found");
        }
        if (userSender.isEmpty()) {
            throw new SQLException("Sender not found");
        }
        String sql = "INSERT INTO `emails` ( `sender`, `recipient`, `subject`, `body`, `links`,`transferFrom`, `is_sender_remove`,`is_recipient_remove`,`is_seen`) " +
                "VALUES (? , ? , ? , ? , ? , ?, ?, ?, '0');";

        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, userSender.get().getId());
        ps.setInt(2, userRecipient.get().getId());
        ps.setString(3, email.getSubject());
        ps.setString(4, email.getBody());
        ps.setString(5, email.getLinks());
        ps.setString(6, email.getTransferFrom());
        ps.setBoolean(7, senderRemove);
        ps.setBoolean(8, receiverRemove);

        int rows = ps.executeUpdate();
        if (rows > 0) {
            return email;
        }
        return null;
    }

    @Override
    public Email save(Email email) throws SQLException {
        Optional<User> userSender = userRepository.findByEmail(email.getFrom());
        Optional<User> userRecipient = userRepository.findByEmail(email.getTo());
        if (userRecipient.isEmpty()) {
            throw new SQLException("Recipient not found");
        }
        if (userSender.isEmpty()) {
            throw new SQLException("Sender not found");
        }
        String sql = "INSERT INTO `emails` ( `sender`, `recipient`, `subject`, `body`, `links`,`transferFrom`, `is_seen`, `is_sender_remove`,`is_recipient_remove`) " +
                "VALUES (? , ? , ? , ? , ? , ?, '0','0','0');";

        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, userSender.get().getId());
        ps.setInt(2, userRecipient.get().getId());
        ps.setString(3, email.getSubject());
        ps.setString(4, email.getBody());
        ps.setString(5, email.getLinks());
        ps.setString(6, email.getTransferFrom());

        int rows = ps.executeUpdate();
        if (rows > 0) {
            return email;
        }
        return null;
    }

    @Override
    public Optional<Email> findById(Integer id) throws SQLException {
        String sql = "SELECT e.*, uS.email as senderMail, uR.email as recipientMail " +
                "FROM emails as e " +
                "JOIN users as uS ON uS.id = e.sender " +
                "JOIN users as uR ON uR.id = e.recipient " +
                "WHERE e.id = ?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return Optional.of(Email.fromResultSet(rs));
        }
        return Optional.empty();
    }

    @Override
    public void deleteById(Integer id) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean readEmail(int id) throws SQLException {
        String sql = "UPDATE emails " +
                "SET is_seen = TRUE " +
                "WHERE id = ?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, id);
        int rows = ps.executeUpdate();
        return rows > 0;
    }

    @Override
    public boolean deleteEmailBySender(int id) throws SQLException {
        String sql = "UPDATE emails " +
                "SET is_sender_remove = TRUE " +
                "WHERE id = ?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, id);
        int rows = ps.executeUpdate();
        return rows > 0;
    }

    @Override
    public boolean deleteEmailByRecipient(int id) throws SQLException {
        String sql = "UPDATE emails " +
                "SET is_recipient_remove = TRUE " +
                "WHERE id = ?";
        PreparedStatement ps = connection.prepareStatement(sql);
        ps.setInt(1, id);
        int rows = ps.executeUpdate();
        return rows > 0;
    }
}
