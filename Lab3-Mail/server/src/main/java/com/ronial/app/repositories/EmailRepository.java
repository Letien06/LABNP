package com.ronial.app.repositories;

import com.ronial.app.conf.db.repository.Repository;
import com.ronial.app.context.Context;
import com.ronial.app.models.Email;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface EmailRepository  extends Context, Repository<Email, Integer> {
    List<Email> findAllEmailSentByEmail(String email) throws SQLException;
    List<Email> findAllEmailReceiveByEmail(String email) throws SQLException;
    Email saveWithRemoveForSenderAndReceiver(Email email,boolean senderRemove, boolean receiverRemove) throws SQLException;
    boolean readEmail(int id) throws SQLException;
    boolean deleteEmailBySender(int id) throws SQLException;
    boolean deleteEmailByRecipient(int id) throws SQLException;
}
