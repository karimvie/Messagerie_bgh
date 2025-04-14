package org.example;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
    public boolean authenticate(String username, String password) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT password_hash FROM users WHERE username = ?";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, username);
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                String inputHash = hashPassword(password);

                rs.close();
                return storedHash.equals(inputHash);
            } else {
                rs.close();
                return false; // Username not found
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }


    @Override
    public boolean createUser(String username, String password) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "INSERT INTO users (username, password_clear, password_hash) VALUES (?, ?, ?)";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            String hashedPassword = hashPassword(password);

            pst.setString(1, username);
            pst.setString(2, password);  // store cleartext (for debugging, not recommended for prod)
            pst.setString(3, hashedPassword);  // store hash for verification

            int rowsAffected = pst.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("User " + username + " created successfully.");
                return true;
            } else {
                System.out.println("Failed to create user " + username);
                return false;
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }


    @Override

    public boolean updateUser(String username, String newPassword) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb?serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = ""; // XAMPP default
        String sql = "UPDATE users SET password_clear = ?, password_hash = ? WHERE username = ?";

        System.out.println("Attempting to update user: " + username);
        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement stmt = con.prepareStatement(sql)) {

            String hashedPassword = hashPassword(newPassword);

            stmt.setString(1, newPassword);         // plain password (for testing/dev)
            stmt.setString(2, hashedPassword);      // hashed password
            stmt.setString(3, username);            // where clause

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("User updated successfully.");
                return true;
            } else {
                System.out.println("User not found.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("SQL Error during user update: " + e.getMessage());
            return false;
        }
    }





    @Override
    public boolean deleteUser(String username) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb?serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = ""; // XAMPP default
        String sql = "DELETE FROM users WHERE username = ?";

        System.out.println("Attempting to delete user: " + username);
        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement stmt = con.prepareStatement(sql)) {

            stmt.setString(1, username);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("User deleted successfully.");
                return true;
            } else {
                System.out.println("User not found.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("SQL Error during user deletion: " + e.getMessage());
            return false;
        }
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