package org.example;
import java.io.*;
import java.net.*;
import java.util.*;

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

    public Pop3Session(Socket socket) {
        this.socket = socket;
        this.authenticated = false;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
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
                    case "QUIT":
                        handleQuit();
                        return;
                    default:
                        out.println("-ERR Unknown command");
                        break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { socket.close(); } catch (IOException e) { /* Ignore */ }
        }
    }

    private void handleUser(String arg) {
        File dir = new File("mailserver/" + arg);
        if (dir.exists() && dir.isDirectory()) {
            username = arg;
            userDir = dir;
            out.println("+OK User accepted");
        } else {
            out.println("-ERR User not found");
        }
    }

    private void handlePass(String arg) {
        if (username == null) {
            out.println("-ERR USER required first");
            return;
        }
        authenticated = true;
        emails = Arrays.asList(userDir.listFiles());
        out.println("+OK Password accepted");
    }

    private void handleStat() {
        if (!authenticated) {
            out.println("-ERR Authentication required");
            return;
        }
        long size = emails.stream().mapToLong(File::length).sum();
        out.println("+OK " + emails.size() + " " + size);
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
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size()) {
                out.println("-ERR No such message");
                return;
            }
            File emailFile = emails.get(index);
            out.println("+OK " + emailFile.length() + " octets");
            BufferedReader reader = new BufferedReader(new FileReader(emailFile));
            String line;
            while ((line = reader.readLine()) != null) {
                out.println(line);
            }
            out.println(".");
            reader.close();
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
            int index = Integer.parseInt(arg) - 1;
            if (index < 0 || index >= emails.size()) {
                out.println("-ERR No such message");
                return;
            }
            File emailFile = emails.get(index);
            if (emailFile.delete()) {
                emails.remove(index);
                out.println("+OK Message deleted");
            } else {
                out.println("-ERR Unable to delete message");
            }
        } catch (Exception e) {
            out.println("-ERR Invalid message number");
        }
    }

    private void handleQuit() {
        out.println("+OK POP3 server signing off");
    }
}

