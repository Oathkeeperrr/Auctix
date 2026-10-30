package com.auctix.config;

import com.auctix.security.AuctixUserDetailsService;
import com.auctix.security.JwtAuthenticationFilter;
import com.auctix.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Authenticates the STOMP CONNECT frame with the same JWT used for REST, and guards
 * private destinations.
 *
 * <p>Anonymous clients may watch public auction topics (so the landing page updates live for
 * visitors) but only an authenticated principal can subscribe to its own
 * {@code /user/queue/**} destination or send to {@code /app/**}.</p>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtService jwtService;
    private final AuctixUserDetailsService userDetailsService;

    public StompAuthChannelInterceptor(JwtService jwtService, AuctixUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            authenticate(accessor);
            return message;
        }

        boolean needsPrincipal =
                (StompCommand.SUBSCRIBE.equals(command) && isPrivateDestination(accessor.getDestination()))
                        || StompCommand.SEND.equals(command);

        if (needsPrincipal && accessor.getUser() == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Authentication required for " + command + " " + accessor.getDestination());
        }
        return message;
    }

    private boolean isPrivateDestination(String destination) {
        return destination != null && destination.startsWith("/user/");
    }

    private void authenticate(StompHeaderAccessor accessor) {
        Optional<String> token = Optional.ofNullable(accessor.getFirstNativeHeader(JwtAuthenticationFilter.HEADER))
                .flatMap(JwtAuthenticationFilter::readBearer)
                .or(() -> Optional.ofNullable(accessor.getFirstNativeHeader("token")));

        if (token.isEmpty()) {
            return;
        }

        jwtService.parse(token.get()).ifPresent(claims -> {
            try {
                UserDetails principal = userDetailsService.loadUserByUsername(claims.getSubject());
                if (principal.isEnabled() && principal.isAccountNonLocked()) {
                    accessor.setUser(new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities()));
                }
            } catch (RuntimeException ex) {
                log.debug("STOMP CONNECT rejected: {}", ex.getMessage());
            }
        });
    }
}
