package org.example;

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

        File dir = new File("mailserver/" + arg);
        if (!dir.exists()) {
            out.println("-ERR No such user exists");
            return;
        }
        username = arg;
        userDir = dir;
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
            if (!authService.authenticate(username, arg)) {
                out.println("-ERR Wrong password, try again");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
            out.println("-ERR Server error during authentication");
            return;
        }

        authenticated = true;
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
        out.println("+OK " + emails.size() + " messages");
        for (int i = 0; i < emails.size(); i++) {
            out.println((i + 1) + " " + emails.get(i).length());
        }
        out.println(".");
    }

    private void handleRetr(String arg) {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }
        try {
            int index = Integer.parseInt(arg.trim()) - 1;
            if (index < 0 || index >= emails.size()) {
                out.println("-ERR No such message");
                return;
            }
            File emailFile = emails.get(index);
            out.println("+OK " + emailFile.length() + " octets");
            try (BufferedReader reader = new BufferedReader(new FileReader(emailFile))) {
                String fileLine;
                while ((fileLine = reader.readLine()) != null) {
                    out.println(fileLine);
                }
            }
            out.println(".");
        } catch (Exception e) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleDele(String arg) {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }
        try {
            int index = Integer.parseInt(arg.trim()) - 1;
            if (index < 0 || index >= emails.size()) {
                out.println("-ERR No such message");
                return;
            }
            if (deletionFlags.get(index)) {
                out.println("-ERR Message already marked for deletion");
                return;
            }
            deletionFlags.set(index, true);
            out.println("+OK Message marked for deletion");
        } catch (NumberFormatException nfe) {
            out.println("-ERR Invalid message number");
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
}
