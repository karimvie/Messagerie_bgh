package org.example;
import java.sql.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Base64;
import java.io.UnsupportedEncodingException;

public class SmtpServer {
    // Use a custom port (2525) to avoid needing privileged ports.
    private static final int PORT = 25;

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
        WAITING_MAIL_FROM, DATA_RECEIVING      // DATA command received; reading email content.
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
            out.println("220 smtp.example.com Service Ready");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("Received: " + line);
                String command = extractToken(line).toUpperCase();
                String argument = extractArgument(line);

                // If not authenticated, only allow AUTH commands

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

        // Extract local part before '@'
        String localPart = email.split("@")[0];

        // Check if user with this username exists in DB
        if (!userExistsInDatabase(localPart)) {
            out.println("550 Sender not recognized");
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

        // Extract the local part of the email (before '@')
        String localPart = email.split("@")[0];

        // Check if the recipient exists in the database by matching the recipient_email
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

        StringBuilder messageContent = new StringBuilder();
        boolean endOfMessage = false;

        // Reading the email content from the client
        try {
            while (!endOfMessage) {
                String line = readLine();  // Use readLine() to capture the client's input
                if (line.equals(".")) {
                    endOfMessage = true; // End the message when a single dot is entered
                } else {
                    messageContent.append(line).append("\r\n"); // Add the line to the message content
                }
            }

            // After the message body is received, we process and store it
            storeEmail(messageContent.toString());  // Store the email
        } catch (IOException e) {
            out.println("550 Failed to store email");
            e.printStackTrace();
        }
    }

    private String readLine() throws IOException {
        // Use the InputStream of your socket to read data from the client
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Read one line from the input stream
        String line = reader.readLine();

        // Return the read line
        return line;
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
    private String extractSubject(String data) {
        // Find the headers part (before the empty line)
        int headerEndIndex = data.indexOf("\r\n\r\n");  // Locate the blank line separating headers and body
        if (headerEndIndex == -1) {
            return null;  // No body found, could be invalid data
        }

        String headers = data.substring(0, headerEndIndex);  // Extract headers part
        String[] lines = headers.split("\r\n");  // Split headers into lines

        // Look for the Subject header
        for (String line : lines) {
            if (line.toLowerCase().startsWith("subject:")) {
                // Extract the subject and check if it needs decoding
                String subject = line.substring(8).trim();  // Remove "Subject:" and any extra spaces

                // Check if the subject is base64 encoded (common for non-ASCII subjects)
                if (subject.contains("=?UTF-8?B?")) {
                    // Remove the encoding part (e.g., =?UTF-8?B?...)
                    String base64EncodedSubject = subject.split("\\?B\\?")[1].split("\\?=")[0];
                    return decodeBase64Subject(base64EncodedSubject);
                }
                return subject;  // If no encoding, return as is
            }
        }

        return null;  // No subject found
    }
    // Base64 decoding method
    private String decodeBase64Subject(String encodedSubject) {
        try {
            // Decode the base64 string and return the result as UTF-8
            return new String(Base64.getDecoder().decode(encodedSubject), "UTF-8");
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            e.printStackTrace();
            return encodedSubject;  // If decoding fails, return the raw subject
        }
    }
    // Store the email in the authenticated user's directory.
// Store the email after DATA command
    private void storeEmail(String data) {
        long startTime = System.currentTimeMillis();
        String body = extractEmailBody(data);
        String subject = extractSubject(data); // fixed typo from extracttSubject

        String jdbcUrl = "jdbc:mysql://localhost:3306/maildb?serverTimezone=UTC";
        String dbUser = "root";
        String dbPassword = "";
        String sql = "INSERT INTO emails (sender, content, date_sent, recipient_email, subject) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DriverManager.getConnection(jdbcUrl, dbUser, dbPassword);
             PreparedStatement pst = con.prepareStatement(sql)) {

            if (sender == null || recipients.isEmpty()) {
                out.println("550 Missing sender or recipient");
                return;
            }

            for (String recipientEmail : recipients) {
                pst.setString(1, sender); // FROM MAIL FROM
                pst.setString(2, body);   // email body from DATA
                pst.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
                pst.setString(4, recipientEmail); // FROM RCPT TO
                pst.setString(5, subject);        // From DATA

                pst.executeUpdate();
            }

            System.out.println("Email stored in database successfully.");
            out.println("250 OK: Message accepted for delivery");

            // ✅ Reset state for next email
            sender = null;
            recipients.clear();
            state = SmtpState.WAITING_MAIL_FROM;

        } catch (SQLException ex) {
            ex.printStackTrace();
            out.println("550 Failed to store email: " + ex.getMessage());
        }
        long endTime = System.currentTimeMillis();
        System.out.println("[SMTP] Temps de réponse pour storeEmail : " + (endTime - startTime) + " ms");

    }


    // Helper function to extract the subject from the email data
    private String extracttSubject(String data) {
        String[] lines = data.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Subject:")) {
                return line.substring(8).trim(); // Get subject after "Subject: "
            }
        }
        return "";  // Return empty if subject not found
    }


    // Helper function to extract the email body (ignores headers like Subject, From, To, etc.)
    private String extractEmailBody(String data) {
        String[] lines = data.split("\r\n");
        StringBuilder body = new StringBuilder();

        boolean isBody = false;
        for (String line : lines) {
            if (line.isEmpty()) {  // Empty line indicates the start of the body
                isBody = true;
                continue;
            }
            if (isBody) {
                body.append(line).append("\r\n");
            }
        }

        return body.toString().trim();  // Remove the last \r\n (if any)
    }

    // Helper function to extract the sender from the email data
    private String extractSender(String data) {
        String[] lines = data.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("From:")) {
                return line.substring(5).trim(); // Get email after "From: "
            }
        }
        return "";  // Return empty if sender not found
    }

    // Helper function to extract the recipient from the email data
    private String extractRecipient(String data) {
        String[] lines = data.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("To:")) {
                return line.substring(3).trim(); // Get email after "To: "
            }
        }
        return "";  // Return empty if recipient not found
    }


    // Helper function to extract the body of the email from the data
    private String extractBodyFromData(String data) {
        // Split by the first blank line, which separates headers and body
        int bodyStartIndex = data.indexOf("\r\n\r\n");

        if (bodyStartIndex != -1) {
            // Return the content after the blank line (which is the body)
            return data.substring(bodyStartIndex + 4).trim();
        } else {
            // No blank line, return the whole content (although this should not happen)
            return data;
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
