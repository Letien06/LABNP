package com.ronial.app.repositories;

import com.ronial.app.conf.db.DatabaseConf;
import com.ronial.app.context.ContextProvider;

import com.ronial.app.repositories.impl.EmailRepositoryImpl;
import com.ronial.app.repositories.impl.UserRepositoryImpl;

import java.sql.Connection;

public class RepositoryInitializer {
    private RepositoryInitializer() {}
    public static void initialize() {
        DatabaseConf databaseConf = ContextProvider.get(DatabaseConf.class);
        Connection connection = databaseConf.getConnection();

        UserRepository userRepository = new UserRepositoryImpl(connection);
        ContextProvider.register(UserRepository.class, userRepository);

        EmailRepository emailRepository = new EmailRepositoryImpl(connection, userRepository);
        ContextProvider.register(EmailRepository.class, emailRepository);
    }
}
