package org.example;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
import java.net.*;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class Pop3Server {
    private static final int PORT = 1100; // Custom port to avoid conflicts

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("POP3 Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                new Pop3Session(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Pop3Session extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private File userDir;
    private List<File> emails;
    private boolean authenticated;
    private List<Boolean> deletionFlags;

    public Pop3Session(Socket socket) {
        this.socket = socket;
        this.authenticated = false;
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            out.println("+OK POP3 server ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line);
                String[] parts = line.split(" ", 2);
                String command = parts[0].toUpperCase();
                String argument = parts.length > 1 ? parts[1] : "";

                switch (command) {
                    case "USER":
                        handleUser(argument);
                        break;
                    case "PASS":
                        handlePass(argument);
                        break;
                    case "STAT":
                        handleStat();
                        break;
                    case "LIST":
                        handleList();
                        break;
                    case "RETR":
                        handleRetr(argument);
                        break;
                    case "DELE":
                        handleDele(argument);
                        break;
                    case "RSET":
                        handleRset();
                        break;
                    case "QUIT":
                        handleQuit();
                        return; // Terminate session after QUIT.
                    default:
                        out.println("-ERR Unknown command");
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading from connection: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }

    private void handleUser(String arg) {
        if (arg.trim().isEmpty()) {
            out.println("-ERR Username cannot be empty");
            return;
        }

        // Check if user exists in MySQL instead of directory
        if (!userExistsInDatabase(arg)) {
            out.println("-ERR No such user exists");
            return;
        }

        username = arg;
        out.println("+OK User found, please enter password");
    }



    private void handlePass(String arg) {
        if (username == null) {
            out.println("-ERR USER required first");
            return;
        }

        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");

            boolean isAuthenticated = authService.authenticate(username, arg);
            if (!isAuthenticated) {
                out.println("-ERR Wrong password, try again");
                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
            out.println("-ERR Server error during authentication");
            return;
        }

        authenticated = true;

        // You can still use local files for emails, or switch to DB later
        File userDir = new File("mailserver/" + username);
        File[] files = userDir.listFiles();
        emails = (files == null) ? new ArrayList<>() : new ArrayList<>(Arrays.asList(files));
        deletionFlags = new ArrayList<>(Collections.nCopies(emails.size(), false));

        out.println("+OK Password accepted, mailbox ready");
    }


    private void handleStat() {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        int emailCount = 0;
        long totalSize = 0;

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb?serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT content FROM emails WHERE recipient_email = ?";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            // Construct the full recipient email address
            String recipientEmail = username + "@example.com";  // Change domain if needed
            pst.setString(1, recipientEmail);

            ResultSet rs = pst.executeQuery();
            while (rs.next()) {
                String content = rs.getString("content");
                totalSize += content.getBytes(StandardCharsets.UTF_8).length;
                emailCount++;
            }

            out.println("+OK " + emailCount + " " + totalSize);
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Could not retrieve email statistics");
        }
    }


    private void handleList() {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        String recipientEmail = username + "@example.com";  // Adjust domain if needed

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT LENGTH(content) AS size FROM emails WHERE recipient_email = ? ORDER BY date_sent ASC";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, recipientEmail);
            ResultSet rs = pst.executeQuery();

            List<String> lines = new ArrayList<>();
            int index = 1;
            int totalSize = 0;

            while (rs.next()) {
                int size = rs.getInt("size");
                totalSize += size;
                lines.add(index + " " + size);  // Use sequence number, not DB ID
                index++;
            }

            if (lines.isEmpty()) {
                out.println("-ERR No messages found.");
            } else {
                out.println("+OK " + lines.size() + " " + totalSize);
                for (String line : lines) {
                    out.println(line);
                }
                out.println(".");  // End of multi-line response
            }

            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Unable to list emails");
        }
    }






    private void handleRetr(String msgId) {
        long startTime = System.currentTimeMillis();

        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(msgId) - 1;  // Convert to 0-based index
            if (index < 0) {
                out.println("-ERR Invalid message number");
                return;
            }
        } catch (NumberFormatException e) {
            out.println("-ERR Invalid message number format");
            return;
        }

        String recipientEmail = username + "@example.com";
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";

        String sql = "SELECT subject, content FROM emails WHERE recipient_email = ? AND is_deleted = 0 ORDER BY date_sent ASC";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, recipientEmail);
            ResultSet rs = pst.executeQuery();

            List<String> messages = new ArrayList<>();
            List<String> subjects = new ArrayList<>();

            while (rs.next()) {
                subjects.add(rs.getString("subject"));
                messages.add(rs.getString("content"));
            }

            if (index >= messages.size()) {
                out.println("-ERR No such message");
                return;
            }

            String subject = subjects.get(index);
            String content = messages.get(index);
            String fullMessage = "Subject: " + subject + "\r\n" + content;

            out.println("+OK " + fullMessage.length() + " octets");
            out.println(fullMessage);
            out.println(".");

            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Server error during message retrieval");
        }
        long endTime = System.currentTimeMillis();
        System.out.println("[POP3] Temps de réponse pour handleRetr : " + (endTime - startTime) + " ms");

    }






    private void handleDele(String msgId) {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(msgId) - 1;  // Convert to 0-based index
            if (index < 0) {
                out.println("-ERR Invalid message number");
                return;
            }
        } catch (NumberFormatException e) {
            out.println("-ERR Invalid message number format");
            return;
        }

        // Construct the recipient email (e.g., username@example.com)
        String recipientEmail = username + "@example.com";
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";

        String sql = "SELECT id FROM emails WHERE recipient_email = ? AND is_deleted = 0 ORDER BY date_sent ASC";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, recipientEmail);
            ResultSet rs = pst.executeQuery();

            List<Integer> emailIds = new ArrayList<>();
            while (rs.next()) {
                emailIds.add(rs.getInt("id"));
            }

            if (index >= emailIds.size()) {
                out.println("-ERR No such message");
                return;
            }

            int messageId = emailIds.get(index);

            // Soft delete the email by updating 'is_deleted' field
            String deleteSql = "UPDATE emails SET is_deleted = 1 WHERE id = ? AND recipient_email = ?";
            try (PreparedStatement deletePst = con.prepareStatement(deleteSql)) {
                deletePst.setInt(1, messageId);
                deletePst.setString(2, recipientEmail);
                int rowsAffected = deletePst.executeUpdate();

                if (rowsAffected > 0) {
                    out.println("+OK Message marked for deletion");
                } else {
                    out.println("-ERR No such message");
                }
            }

            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Server error during message deletion");
        }
    }




    private void handleRset() {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";

        String sql = "UPDATE emails SET is_deleted = 0 WHERE recipient_email = ? AND is_deleted = 1";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            String recipientEmail = username + "@example.com";  // Assuming this format
            pst.setString(1, recipientEmail);
            int rowsAffected = pst.executeUpdate();

            out.println("+OK Reset deletion flags on " + rowsAffected + " message(s)");
        } catch (SQLException e) {
            e.printStackTrace();
            out.println("-ERR Failed to reset deletion flags");
        }
    }


    private void handleQuit() {
        // If user is not authenticated, just exit
        if (!authenticated) {
            out.println("+OK POP3 server signing off");
            return;
        }

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";

        // Construct user's email address
        String recipientEmail = username + "@example.com";

        // SQL to permanently delete all soft-deleted messages for the authenticated user
        String sql = "DELETE FROM emails WHERE recipient_email = ? AND is_deleted = 1";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            pst.setString(1, recipientEmail);
            int deletedCount = pst.executeUpdate();

            out.println("+OK " + deletedCount + " message(s) deleted. Goodbye");

        } catch (SQLException e) {
            e.printStackTrace();
            out.println("-ERR Error during cleanup. Goodbye anyway");
        }
    }

    private boolean userExistsInDatabase(String username) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb?serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT 1 FROM users WHERE username = ? LIMIT 1";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement stmt = con.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();
            rs.close();
            return exists;

        } catch (SQLException e) {
            System.err.println("Database error during POP3 user check: " + e.getMessage());
            return false;
        }
    }


}
