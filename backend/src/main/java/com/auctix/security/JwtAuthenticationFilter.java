package com.auctix.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Reads {@code Authorization: Bearer <jwt>} and populates the security context.
 * Stateless: no session is created.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuctixUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, AuctixUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Optional<String> token = resolveToken(request);
        if (token.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<Claims> claims = jwtService.parse(token.get());
            if (claims.isPresent()) {
                try {
                    UserDetails principal = userDetailsService.loadUserByUsername(claims.get().getSubject());
                    if (principal.isEnabled() && principal.isAccountNonLocked()) {
                        var authentication = new UsernamePasswordAuthenticationToken(
                                principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                } catch (UsernameNotFoundException ignored) {
                    // Token references a user that no longer exists: stay anonymous.
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    public static Optional<String> readBearer(String headerValue) {
        if (headerValue != null && headerValue.startsWith(PREFIX) && headerValue.length() > PREFIX.length()) {
            return Optional.of(headerValue.substring(PREFIX.length()).trim());
        }
        return Optional.empty();
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        return readBearer(request.getHeader(HEADER));
    }
}
