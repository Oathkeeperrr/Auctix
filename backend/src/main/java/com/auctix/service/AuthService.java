package com.auctix.service;

import com.auctix.domain.AuditAction;
import com.auctix.domain.Role;
import com.auctix.domain.User;
import com.auctix.dto.auth.*;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.repository.UserRepository;
import com.auctix.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.auditService = auditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_USED);
        }
        Role role = request.roleOrDefault();
        if (role == Role.ADMIN) {
            // Administrators are provisioned, never self-registered.
            throw new ApiException(ErrorCode.USER_NOT_AUTHORIZED,
                    "Administrator accounts cannot be created through registration.");
        }
        User user = new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), role);
        userRepository.save(user);

        auditService.record(user.getId(), email, AuditAction.USER_REGISTERED, "User", user.getId(),
                "role=" + role);

        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));
        } catch (DisabledException | LockedException ex) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        } catch (BadCredentialsException ex) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        } catch (AuthenticationException ex) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        auditService.record(user.getId(), email, AuditAction.USER_LOGIN, "User", user.getId(), null);
        return issueToken(user);
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.bio() != null) {
            user.setBio(request.bio().trim());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().trim());
        }
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.USER_NOT_FOUND, userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Your current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        return AuthResponse.bearer(token, jwtService.getExpirationMinutes(), UserDto.from(user));
    }
}
