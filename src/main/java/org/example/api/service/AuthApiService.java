package org.example.api.service;

import org.example.AuthService;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import org.springframework.stereotype.Service;

@Service
public class AuthApiService {
    private AuthService getAuthService() throws Exception {
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        return (AuthService) registry.lookup("AuthService");
    }

    public boolean login(String username, String password) {
        try {
            return getAuthService().authenticate(username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean register(String username, String password) {
        try {
            return getAuthService().createUser(username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean delete(String username) {
        try {
            return getAuthService().deleteUser(username);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
