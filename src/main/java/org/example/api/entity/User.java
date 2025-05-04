package org.example.api.entity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Column;

@Entity
@Table(name = "users")
public class User {
    @Id
    private String username;
    private String password_clear;
    private String password_hash;

    // getters/setters...
}
