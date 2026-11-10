package com.ronial.app.mail.service;

import com.ronial.app.context.Context;
import com.ronial.app.exceptions.ServiceException;
import com.ronial.app.models.Email;
import com.ronial.app.models.User;

import java.sql.SQLException;

public interface MailService extends Context {
    void createMailAccount(User user) throws ServiceException;
    User loginMailAccount(String email, String password) throws ServiceException;
    void saveEmail(Email email) throws ServiceException;
    void deleteEmail(String email,int id) throws ServiceException, SQLException;
    void transferMail(String[] emails ,Email email) throws ServiceException;
    void readMail(int mailId) throws ServiceException, SQLException;
}
