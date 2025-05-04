package org.example.api.repository;

import org.example.api.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<Object> findByUsername(String recipientUsername);
}
