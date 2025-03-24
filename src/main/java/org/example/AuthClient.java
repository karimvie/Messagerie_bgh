package org.example;


import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

public class AuthClient {
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            AuthService authService = (AuthService) registry.lookup("AuthService");
            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.println("Select an option:");
                System.out.println("1: Create user");
                System.out.println("2: Update user");
                System.out.println("3: Delete user");
                System.out.println("4: Authenticate user");
                System.out.println("5: Exit");
                String choice = scanner.nextLine();

                if (choice.equals("5")) break;

                System.out.print("Enter username: ");
                String username = scanner.nextLine();

                if (choice.equals("1")) {
                    System.out.print("Enter password: ");
                    String password = scanner.nextLine();
                    boolean created = authService.createUser(username, password);
                    System.out.println(created ? "User created successfully." : "User creation failed (user might exist).");
                } else if (choice.equals("2")) {
                    System.out.print("Enter new password: ");
                    String newPassword = scanner.nextLine();
                    boolean updated = authService.updateUser(username, newPassword);
                    System.out.println(updated ? "User updated successfully." : "Update failed (user not found).");
                } else if (choice.equals("3")) {
                    boolean deleted = authService.deleteUser(username);
                    System.out.println(deleted ? "User deleted successfully." : "Deletion failed (user not found).");
                } else if (choice.equals("4")) {
                    System.out.print("Enter password: ");
                    String password = scanner.nextLine();
                    boolean valid = authService.authenticate(username, password);
                    System.out.println(valid ? "Authentication successful." : "Authentication failed.");
                } else {
                    System.out.println("Invalid option.");
                }
            }
            scanner.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}