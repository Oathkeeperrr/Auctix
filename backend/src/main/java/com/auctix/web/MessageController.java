package com.auctix.web;

import com.auctix.dto.message.ConversationDto;
import com.auctix.dto.message.MessageDto;
import com.auctix.dto.message.SendMessageRequest;
import com.auctix.dto.message.StartConversationRequest;
import com.auctix.security.UserPrincipal;
import com.auctix.service.MessagingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Private messaging. Every route needs a signed-in user; SecurityConfig's
 * {@code anyRequest().authenticated()} covers the path, and participation in a particular
 * thread is decided inside {@link MessagingService}, never here.
 */
@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messaging")
public class MessageController {

    private final MessagingService messagingService;

    public MessageController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> conversations(@AuthenticationPrincipal UserPrincipal principal) {
        return messagingService.conversations(CurrentUser.require(principal).getId());
    }

    @PostMapping("/conversations")
    public ConversationDto start(@Valid @RequestBody StartConversationRequest request,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        return messagingService.startOrFind(user.getId(), request.recipientId(), request.auctionId());
    }

    @GetMapping("/conversations/{id}")
    public List<MessageDto> thread(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal user = CurrentUser.require(principal);
        return messagingService.thread(id, user.getId(), user.getRole());
    }

    @PostMapping("/conversations/{id}")
    public MessageDto send(@PathVariable Long id,
                           @Valid @RequestBody SendMessageRequest request,
                           @AuthenticationPrincipal UserPrincipal principal) {
        return messagingService.send(id, CurrentUser.require(principal).getId(), request.body());
    }

    @PostMapping("/conversations/{id}/read")
    public Map<String, Long> markRead(@PathVariable Long id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        long updated = messagingService.markRead(id, CurrentUser.require(principal).getId());
        return Map.of("marked", updated);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return Map.of("count", messagingService.unreadCount(CurrentUser.require(principal).getId()));
    }
}
