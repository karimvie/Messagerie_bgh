package org.example;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AuthService extends Remote {
    // Verifies that the given username and password are valid.
    boolean authenticate(String username, String password) throws RemoteException;

    // Creates a new user account. Returns true if created successfully.
    boolean createUser(String username, String password) throws RemoteException;

    // Updates the password for an existing user.
    boolean updateUser(String username, String newPassword) throws RemoteException;

    // Deletes an existing user account.
    boolean deleteUser(String username) throws RemoteException;
}

