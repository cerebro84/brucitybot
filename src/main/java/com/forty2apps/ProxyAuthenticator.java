package com.forty2apps;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class ProxyAuthenticator extends Authenticator {

    private String userName, password;

    protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(userName, password.toCharArray());
    }

    public ProxyAuthenticator(String username, String password) {
        this.userName = userName;
        this.password = password;
    }
}
