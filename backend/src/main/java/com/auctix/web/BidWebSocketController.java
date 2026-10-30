package com.auctix.web;

import com.auctix.dto.bid.BidResultDto;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.exception.ApiException;
import com.auctix.realtime.AuctionEvent;
import com.auctix.realtime.AuctionEventBus;
import com.auctix.realtime.AuctionEventType;
import com.auctix.security.UserPrincipal;
import com.auctix.service.BiddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

/**
 * Optional STOMP entry point for bidding. The REST endpoint
 * {@code POST /api/auctions/{id}/bids} is the primary path; this exists so a client that is
 * already holding a socket open can bid over the same connection. Both routes run through the
 * identical {@link BiddingService}, so validation and concurrency behaviour are the same.
 */
@Controller
public class BidWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(BidWebSocketController.class);

    private final BiddingService biddingService;
    private final AuctionEventBus eventBus;

    public BidWebSocketController(BiddingService biddingService, AuctionEventBus eventBus) {
        this.biddingService = biddingService;
        this.eventBus = eventBus;
    }

    @MessageMapping("/auctions/{auctionId}/bids")
    public void placeBid(@DestinationVariable Long auctionId,
                         @Payload PlaceBidRequest request,
                         Principal principal) {
        UserPrincipal user = resolve(principal);
        if (user == null) {
            return;
        }
        try {
            BidResultDto result = biddingService.placeBid(auctionId, user.getId(), user.getEmail(),
                    request, "websocket");
            log.debug("STOMP bid accepted on auction {} for {}", auctionId, result.state().currentPrice());
        } catch (ApiException ex) {
            eventBus.publish(new AuctionEvent(AuctionEventType.BID_REJECTED, auctionId, Instant.now(),
                    null, null, null, ex.getMessage(), user.getEmail()));
        }
    }

    private UserPrincipal resolve(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        return null;
    }
}
