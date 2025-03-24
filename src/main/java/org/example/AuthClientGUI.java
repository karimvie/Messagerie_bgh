package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class AuthClientGUI extends JFrame {

    private AuthService authService;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextArea outputArea;

    public AuthClientGUI() {
        // Setup the GUI window
        setTitle("Authentication Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(400, 350);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Create panel for user inputs
        JPanel inputPanel = new JPanel(new GridLayout(3, 2, 5, 5));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        inputPanel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        inputPanel.add(usernameField);

        inputPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        inputPanel.add(passwordField);

        // Create buttons panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 4, 5, 5));
        JButton createButton = new JButton("Create");
        JButton updateButton = new JButton("Update");
        JButton deleteButton = new JButton("Delete");
        JButton authButton = new JButton("Authenticate");

        buttonPanel.add(createButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(authButton);

        // Create output area for results
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);

        add(inputPanel, BorderLayout.NORTH);
        add(buttonPanel, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);

        // Connect to the RMI authentication service
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            authService = (AuthService) registry.lookup("AuthService");
            outputArea.append("Connected to AuthService.\n");
        } catch (Exception e) {
            outputArea.append("Error connecting to AuthService: " + e.getMessage() + "\n");
            e.printStackTrace();
        }

        // Action Listeners for buttons

        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performCreate();
            }
        });

        updateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performUpdate();
            }
        });

        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performDelete();
            }
        });

        authButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAuthenticate();
            }
        });
    }

    private void performCreate() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            outputArea.append("Username and password must not be empty.\n");
            return;
        }
        try {
            boolean success = authService.createUser(username, password);
            if (success) {
                // Create the user's mail directory (if it doesn't exist)
                File userDir = new File("mailserver/" + username);
                if (!userDir.exists()) {
                    boolean dirCreated = userDir.mkdirs();
                    if (dirCreated) {
                        outputArea.append("Mail directory created for user '" + username + "'.\n");
                    } else {
                        outputArea.append("Warning: Failed to create mail directory for user '" + username + "'.\n");
                    }
                }
                outputArea.append("User '" + username + "' created successfully.\n");
            } else {
                outputArea.append("Failed to create user '" + username + "'. It might already exist.\n");
            }
        } catch (Exception ex) {
            outputArea.append("Error creating user: " + ex.getMessage() + "\n");
            ex.printStackTrace();
        }
    }

    private void performUpdate() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            outputArea.append("Username and new password must not be empty.\n");
            return;
        }
        try {
            boolean success = authService.updateUser(username, password);
            if (success) {
                outputArea.append("User '" + username + "' updated successfully.\n");
            } else {
                outputArea.append("Failed to update user '" + username + "'. User may not exist.\n");
            }
        } catch (Exception ex) {
            outputArea.append("Error updating user: " + ex.getMessage() + "\n");
            ex.printStackTrace();
        }
    }

    private void performDelete() {
        String username = usernameField.getText().trim();
        if (username.isEmpty()) {
            outputArea.append("Username must not be empty.\n");
            return;
        }
        try {
            boolean success = authService.deleteUser(username);
            if (success) {
                outputArea.append("User '" + username + "' deleted successfully.\n");
            } else {
                outputArea.append("Failed to delete user '" + username + "'. User may not exist.\n");
            }
        } catch (Exception ex) {
            outputArea.append("Error deleting user: " + ex.getMessage() + "\n");
            ex.printStackTrace();
        }
    }

    private void performAuthenticate() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            outputArea.append("Username and password must not be empty.\n");
            return;
        }
        try {
            boolean valid = authService.authenticate(username, password);
            if (valid) {
                outputArea.append("Authentication successful for user '" + username + "'.\n");
            } else {
                outputArea.append("Authentication failed for user '" + username + "'.\n");
            }
        } catch (Exception ex) {
            outputArea.append("Error authenticating user: " + ex.getMessage() + "\n");
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AuthClientGUI clientGUI = new AuthClientGUI();
            clientGUI.setVisible(true);
        });
    }
}
