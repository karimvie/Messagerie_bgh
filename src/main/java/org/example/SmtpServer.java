package org.example;

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
                    case "AUTH":
                        handleAuth(argument);
                        break;
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
        String potentialEmail = arg.substring(5).trim();  // after "FROM:"
        potentialEmail = potentialEmail.substring(1, potentialEmail.length() - 1).trim();
        String email = extractEmail(potentialEmail);
        if (email == null) {
            out.println("501 Syntax error in parameters or arguments");
            return;
        }
        // Check if the authenticated user matches the sender's local part.
        String localPart = email.split("@")[0];
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
        // For simplicity, you can decide whether to restrict RCPT TO to the authenticated user.
        // Here we allow any recipient (or you can enforce that RCPT TO equals authUsername).
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
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        // The mail directory for the sender is assumed to be "mailserver/<authUsername>"
        File userDir = new File("mailserver/" + authUsername);
        if (!userDir.exists()) {
            userDir.mkdirs();
        }
        File emailFile = new File(userDir, timestamp + ".txt");
        try (PrintWriter writer = new PrintWriter(new FileWriter(emailFile))) {
            writer.println("From: " + sender);
            writer.println("To: " + String.join(", ", recipients));
            writer.println("Date: " + new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z").format(new Date()));
            writer.println("Subject: Test Email");
            writer.println();
            writer.print(data);
            System.out.println("Stored email for " + authUsername + " in " + emailFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error storing email: " + e.getMessage());
        }
    }
}
