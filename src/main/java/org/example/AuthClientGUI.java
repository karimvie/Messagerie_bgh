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
        // Setup the GUI window with a more modern look
        setTitle("Authentication Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        setResizable(false);  // Disable resizing for a more controlled layout

        // Set a custom background color
        getContentPane().setBackground(new Color(240, 240, 240));

        // Add a custom font to improve readability
        Font labelFont = new Font("Arial", Font.PLAIN, 16);

        // Create panel for user inputs with better layout
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // Username label and field
        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setFont(labelFont);
        inputPanel.add(usernameLabel);
        usernameField = new JTextField();
        usernameField.setPreferredSize(new Dimension(300, 30));
        inputPanel.add(usernameField);

        // Password label and field
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setFont(labelFont);
        inputPanel.add(passwordLabel);
        passwordField = new JPasswordField();
        passwordField.setPreferredSize(new Dimension(300, 30));
        inputPanel.add(passwordField);

        // Create buttons panel with a more modern button layout
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 10));

        // Use styled buttons with different colors
        JButton createButton = createStyledButton("Create");
        JButton updateButton = createStyledButton("Update");
        JButton deleteButton = createStyledButton("Delete");

        buttonPanel.add(createButton);
        buttonPanel.add(updateButton);
        buttonPanel.add(deleteButton);

        // Create output area for results with better scrolling and font
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        outputArea.setBackground(new Color(255, 255, 255));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setPreferredSize(new Dimension(450, 150));

        // Add panels to the frame
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

;
    }

    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setPreferredSize(new Dimension(100, 40));
        button.setBackground(new Color(65, 105, 225));  // Royal Blue color
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createLineBorder(new Color(50, 90, 150), 2));
        return button;
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
