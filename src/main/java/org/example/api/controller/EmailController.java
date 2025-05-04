package org.example.api.controller;

import org.example.api.dto.EmailRequest;
import org.example.api.entity.Email;
import org.example.api.service.EmailApiService;
import org.example.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/emails")
public class EmailController {

    @Autowired
    private EmailApiService mailService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/send")
    public ResponseEntity<String> sendEmail(@RequestBody EmailRequest request) {
        // Extract username from email address
        if (request.getTo() == null || !request.getTo().contains("@")) {
            return ResponseEntity.badRequest().body("Invalid recipient email format.");
        }

        String recipientUsername = request.getTo().split("@")[0];

        // Check recipient exists in DB
        boolean exists = userRepository.findByUsername(recipientUsername).isPresent();
        if (!exists) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Recipient not found");
        }

        boolean sent = mailService.sendEmail(request);
        return sent ? ResponseEntity.ok("Email sent") : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to send email");
    }

    @GetMapping("/inbox/{username}")
    public List<Email> inbox(@PathVariable String username) {
        return mailService.inbox(username);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        boolean ok = mailService.delete(id);
        return ok ? ResponseEntity.ok("Deleted") : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Email ID not found");
    }
}
