package org.example.api.repository;

import org.example.api.entity.Email;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {
    List<Email> findByRecipientEmailAndIsDeletedFalse(String recipientEmail);
}
