package org.example;


import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;
import java.io.*;
import java.util.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

public class AuthServiceImpl extends UnicastRemoteObject implements AuthService {

    private static final String ACCOUNTS_FILE = "accounts.json";
    // A simple in-memory map: username -> password (hashed ideally in a real system)
    private Map<String, String> accounts;

    protected AuthServiceImpl() throws RemoteException {
        super();
        accounts = loadAccounts();
    }

    @Override
    public synchronized boolean authenticate(String username, String password) throws RemoteException {
        String storedPassword = accounts.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }

    @Override
    public synchronized boolean createUser(String username, String password) throws RemoteException {
        if (accounts.containsKey(username)) {
            return false; // User already exists.
        }
        accounts.put(username, password);
        return saveAccounts();
    }

    @Override
    public synchronized boolean updateUser(String username, String newPassword) throws RemoteException {
        if (!accounts.containsKey(username)) {
            return false;
        }
        accounts.put(username, newPassword);
        return saveAccounts();
    }

    @Override
    public synchronized boolean deleteUser(String username) throws RemoteException {
        if (!accounts.containsKey(username)) {
            return false;
        }
        accounts.remove(username);
        return saveAccounts();
    }

    // Helper to load accounts from a JSON file.
    private Map<String, String> loadAccounts() {
        try (Reader reader = new FileReader(ACCOUNTS_FILE)) {
            Gson gson = new Gson();
            return gson.fromJson(reader, new TypeToken<Map<String, String>>() {}.getType());
        } catch (IOException e) {
            // If file does not exist, return a new empty map.
            return new HashMap<>();
        }
    }

    // Helper to persist accounts to the JSON file.
    private boolean saveAccounts() {
        try (Writer writer = new FileWriter(ACCOUNTS_FILE)) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(accounts, writer);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}