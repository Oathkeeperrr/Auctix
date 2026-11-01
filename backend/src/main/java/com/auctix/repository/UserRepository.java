package com.auctix.repository;

import com.auctix.domain.Role;
import com.auctix.domain.User;
import com.auctix.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

import java.time.Instant;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    long countByStatus(UserStatus status);

    /** Raw registration times for the sign-ups series; bucketed into days in Java. */
    @Query("select u.createdAt from User u where u.createdAt >= :since")
    List<Instant> createdAtSince(@Param("since") Instant since);
}
