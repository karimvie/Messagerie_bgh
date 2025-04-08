package org.example;
import java.sql.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public class SmtpServer {
    // Use a custom port (2525) to avoid needing privileged ports.
    private static final int PORT = 2525;

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("SMTP Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());
                new SmtpSession(clientSocket).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class SmtpSession extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // SMTP session states
    private enum SmtpState {
        NOT_AUTHENTICATED,  // New connection, waiting for AUTH command.
        AUTHENTICATED,      // Authentication successful, waiting for HELO/EHLO.
        HELO_RECEIVED,      // HELO/EHLO received; ready for MAIL FROM.
        MAIL_FROM_SET,      // MAIL FROM processed; ready for RCPT TO.
        RCPT_TO_SET,        // At least one RCPT TO received; ready for DATA.
        DATA_RECEIVING      // DATA command received; reading email content.
    }

    private SmtpState state;
    private String authUsername; // Set after successful AUTH command.
    private String sender;
    private List<String> recipients;
    private StringBuilder dataBuffer;

    public SmtpSession(Socket socket) {
        this.socket = socket;
        // Start with authentication required
        this.state = SmtpState.NOT_AUTHENTICATED;
        this.recipients = new ArrayList<>();
        this.dataBuffer = new StringBuilder();
    }

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            // Initial greeting includes a note to authenticate.
            out.println("220 smtp.example.com Service Ready. Please authenticate using: AUTH <username> <password>");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line);
                String command = extractToken(line).toUpperCase();
                String argument = extractArgument(line);

                // If not authenticated, only allow AUTH command.
                if (state == SmtpState.NOT_AUTHENTICATED && !command.equals("AUTH")) {
                    out.println("530 Authentication required");
                    continue;
                }

                switch (command) {
                    case "HELO":
                    case "EHLO":
                        handleHelo(argument);
                        break;
                    case "MAIL":
                        handleMailFrom(argument);
                        break;
                    case "RCPT":
                        handleRcptTo(argument);
                        break;
                    case "DATA":
                        handleData();
                        break;
                    case "QUIT":
                        handleQuit();
                        return; // Terminate session after QUIT.
                    default:
                        out.println("500 Command unrecognized");
                        break;
                }

                // If we're in DATA receiving state, accumulate message lines.
                if (state == SmtpState.DATA_RECEIVING) {
                    if (line.equals(".")) {
                        storeEmail(dataBuffer.toString());
                        dataBuffer.setLength(0);
                        state = SmtpState.HELO_RECEIVED;
                        out.println("250 OK: Message accepted for delivery");
                    } else {
                        dataBuffer.append(line).append("\r\n");
                    }
                }
            }
            if (state == SmtpState.DATA_RECEIVING) {
                System.err.println("Connection interrupted during DATA phase. Email incomplete, not stored.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { }
        }
    }

    private void handleAuth(String arg) {
        // Expected format: "username password"
        String[] tokens = arg.split("\\s+");
        if (tokens.length != 2) {
            out.println("501 Syntax error. Use: AUTH <username> <password>");
            return;
        }
        String user = tokens[0];
        String pass = tokens[1];
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");
            if (authService.authenticate(user, pass)) {
                state = SmtpState.AUTHENTICATED;
                authUsername = user;
                out.println("235 Authentication successful");
            } else {
                out.println("535 Authentication credentials invalid");
            }
        } catch (Exception e) {
            out.println("454 Temporary authentication failure");
            e.printStackTrace();
        }
    }

    private void handleHelo(String arg) {
        state = SmtpState.HELO_RECEIVED;
        sender = "";
        recipients.clear();
        out.println("250 Hello " + arg);
    }

    private void handleMailFrom(String arg) {
        if (!arg.toUpperCase().matches("^FROM:\\s*<[^>]+>$")) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        String potentialEmail = arg.substring(5).trim(); // after "FROM:"
        potentialEmail = potentialEmail.substring(1, potentialEmail.length() - 1).trim(); // strip < >
        String email = extractEmail(potentialEmail);

        if (email == null) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        String localPart = email.split("@")[0];

        // Check that the sender exists in the database
        if (!userExistsInDatabase(localPart)) {
            out.println("550 Sender not recognized");
            return;
        }

        // Ensure the sender is the authenticated user
        if (!localPart.equalsIgnoreCase(authUsername)) {
            out.println("550 Sender not authorized. Use the authenticated username.");
            return;
        }

        sender = email;
        state = SmtpState.MAIL_FROM_SET;
        out.println("250 OK");
    }


    private void handleRcptTo(String arg) {
        if (state != SmtpState.MAIL_FROM_SET && state != SmtpState.RCPT_TO_SET) {
            out.println("503 Bad sequence of commands");
            return;
        }

        if (!arg.toUpperCase().startsWith("TO:")) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        String potentialEmail = arg.substring(3).trim();
        String email = extractEmail(potentialEmail);
        if (email == null) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }

        String localPart = email.split("@")[0];

        // Check if the recipient exists
        if (!userExistsInDatabase(localPart)) {
            out.println("550 Recipient address not found");
            return;
        }

        recipients.add(email);
        state = SmtpState.RCPT_TO_SET;
        out.println("250 OK");
    }


    private void handleData() {
        if (state != SmtpState.RCPT_TO_SET || recipients.isEmpty()) {
            out.println("503 Bad sequence of commands");
            return;
        }
        state = SmtpState.DATA_RECEIVING;
        out.println("354 Start mail input; end with <CRLF>.<CRLF>");
    }

    private void handleQuit() {
        out.println("221 smtp.example.com Service closing transmission channel");
    }

    // Helper to extract the first token (command) from the input line.
    private String extractToken(String line) {
        String[] parts = line.split(" ");
        return parts.length > 0 ? parts[0] : "";
    }

    // Helper to extract the argument portion (everything after the command).
    private String extractArgument(String line) {
        int index = line.indexOf(' ');
        return index > 0 ? line.substring(index).trim() : "";
    }

    // Basic email extraction that removes angle brackets and validates the format.
    private String extractEmail(String input) {
        input = input.replaceAll("[<>]", "");
        if (input.contains("@") && input.indexOf("@") > 0 && input.indexOf("@") < input.length() - 1) {
            return input;
        }
        return null;
    }

    // Store the email in the authenticated user's directory.
    private void storeEmail(String data) {
        // Connexion à la base de données MySQL (adapté pour XAMPP : username = "root", password = "")
        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb";
        String dbUser = "root";
        String dbPassword = ""; // par défaut sous XAMPP, il n'y a pas de mot de passe
        String sql = "INSERT INTO emails (sender, recipients, subject, content, date_sent, recipient) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            // Pour cet exemple, nous fixons le sujet à "Test Email".
            pst.setString(1, sender);
            pst.setString(2, String.join(", ", recipients));
            pst.setString(3, "Test Email");  // idéalement extraire le sujet du contenu DATA
            pst.setString(4, data);
            pst.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            // Pour simplifier, nous utilisons le premier destinataire comme identifiant pour la récupération
            pst.setString(6, recipients.get(0));

            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Email stored in database successfully.");
                out.println("250 OK: Message accepted for delivery");
            } else {
                System.out.println("Failed to store email in database.");
                out.println("550 Failed to store email");
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("550 Failed to store email");
        }
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
            return rs.next(); // true if user exists

        } catch (SQLException e) {
            System.err.println("Database error during user existence check: " + e.getMessage());
            return false;
        }
    }

}
