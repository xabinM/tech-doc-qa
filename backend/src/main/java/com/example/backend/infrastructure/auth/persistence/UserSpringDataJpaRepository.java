package com.example.backend.infrastructure.auth.persistence;

import com.example.backend.domain.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserSpringDataJpaRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);
}
