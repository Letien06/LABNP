package com.ronial.app.mail;

public enum CommandType {
    REGISTER_ACCOUNT,
    LOGIN_ACCOUNT,
    SEND_MAIL,
    GET_MAILS,
    REPLY_MAIL,
    DELETE_MAIL,
    TRANSFER_MAIL,
    READ_MAIL,
    ;

    public static CommandType getFromString(final String command) {
        CommandType result = null;
        try {
            result = CommandType.valueOf(command.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.out.println(command + " is not a valid command type.");
        }
        return result;
    }
}
