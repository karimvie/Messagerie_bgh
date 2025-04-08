package org.example;
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
        long totalSize = emails.stream().mapToLong(File::length).sum();
        out.println("+OK " + emails.size() + " " + totalSize);
    }

    private void handleList() {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        // Construct the recipient in the form of "username@example.com"
        String recipient = username + "@example.com";  // Assuming the domain is always "example.com"

        // Define the SQL query to find emails where the recipient matches the authenticated user
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT id, LENGTH(content) AS size FROM emails WHERE recipient = ?";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            // Set the recipient for the query
            pst.setString(1, recipient);

            // Execute the query and process the results
            ResultSet rs = pst.executeQuery();

            List<String> lines = new ArrayList<>();
            int count = 0;
            int totalSize = 0;

            while (rs.next()) {
                count++;
                int size = rs.getInt("size");
                totalSize += size;
                lines.add(rs.getString("id") + " " + size);  // Add the email ID as a string
            }

            // If no emails are found, return a message indicating no messages
            if (count == 0) {
                out.println("-ERR No messages found.");
            } else {
                // If there are emails, return the number of messages and total size
                out.println("+OK " + count + " " + totalSize);
                for (String line : lines) {
                    out.println(line);
                }
                out.println(".");  // End of response
            }

            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Unable to list emails");
        }
    }




    private void handleRetr(String msgId) {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT content FROM emails WHERE recipient = ? AND id = ?";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            // Construct the recipient in the form of "username@example.com"
            String recipient = username + "@example.com";  // Assuming the domain is always "example.com"

            // Set the recipient and message ID for the query
            pst.setString(1, recipient);
            pst.setString(2, msgId);  // Use msgId as a string

            // Execute the query and process the result
            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                // Successfully found the email, retrieve the content
                String content = rs.getString("content");
                out.println("+OK " + content.length() + " octets");
                out.println(content);
                out.println(".");
            } else {
                // Email with the specified ID not found
                out.println("-ERR No such message");
            }

            rs.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("-ERR Server error during message retrieval");
        }
    }




    private void handleDele(String arg) {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "DELETE FROM emails WHERE id = ? AND recipient = ?";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            int id = Integer.parseInt(arg);
            pst.setInt(1, id);
            pst.setString(2, username + "@localhost");
            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                out.println("+OK Message deleted");
            } else {
                out.println("-ERR No such message");
            }
        } catch (SQLException | NumberFormatException ex) {
            ex.printStackTrace();
            out.println("-ERR Unable to delete message");
        }
    }


    private void handleRset() {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }
        for (int i = 0; i < deletionFlags.size(); i++) {
            deletionFlags.set(i, false);
        }
        out.println("+OK Deletion marks reset");
    }

    private void handleQuit() {
        for (int i = deletionFlags.size() - 1; i >= 0; i--) {
            if (deletionFlags.get(i)) {
                File emailFile = emails.get(i);
                if (emailFile.delete()) {
                    System.out.println("Deleted email: " + emailFile.getAbsolutePath());
                    emails.remove(i);
                    deletionFlags.remove(i);
                } else {
                    System.err.println("Failed to delete email: " + emailFile.getAbsolutePath());
                }
            }
        }
        out.println("+OK POP3 server signing off");
    }
    private boolean userExistsInDatabase(String username) {
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb?serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement stmt = con.prepareStatement(sql)) {

            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();

        } catch (SQLException e) {
            System.err.println("Database error during POP3 user check: " + e.getMessage());
            return false;
        }
    }

}
