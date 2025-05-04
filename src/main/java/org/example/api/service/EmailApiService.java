package org.example.api.service;

import org.example.api.dto.EmailRequest;
import org.example.api.entity.Email;
import org.example.api.repository.EmailRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

@Service
public class EmailApiService {

    @Autowired
    private EmailRepository emailRepo;

    public boolean sendEmail(EmailRequest req) {
        Email mail = new Email();
        mail.setSender(req.from);
        mail.setRecipientEmail(req.to);
        mail.setSubject(req.subject);
        mail.setContent(req.content);
        mail.setDateSent(new Timestamp(System.currentTimeMillis()));
        mail.setDeleted(false);

        emailRepo.save(mail);
        return true;
    }

    public List<Email> inbox(String user) {
        String email = user + "@example.com";
        return emailRepo.findByRecipientEmailAndIsDeletedFalse(email);
    }

    public boolean delete(Long id) {
        return emailRepo.findById(id).map(email -> {
            email.setDeleted(true);
            emailRepo.save(email);
            return true;
        }).orElse(false);
    }
}
