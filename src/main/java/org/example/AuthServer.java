package org.example;


import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthServer {
    public static void main(String[] args) {
        try {
            // Create an instance of the AuthService implementation.
            AuthService authService = new AuthServiceImpl();
            // Create and export the RMI registry on a chosen port (e.g., 1099)
            Registry registry = LocateRegistry.createRegistry(1099);
            // Bind the service with a unique name
            registry.rebind("AuthService", authService);
            System.out.println("Authentication RMI Server is running...");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}