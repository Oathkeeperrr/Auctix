package com.auctix.realtime;

import com.auctix.TestDataFactory;
import com.auctix.domain.Auction;
import com.auctix.domain.Role;
import com.auctix.domain.User;
import com.auctix.dto.bid.PlaceBidRequest;
import com.auctix.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof of the headline requirement: a bid placed by one user appears on another
 * user's screen with no page refresh.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebSocketBiddingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("A bid is broadcast to every subscriber of the auction topic")
    void bidIsBroadcastToSubscribers() throws Exception {
        User seller = data.user("ws-seller@auctix.io", Role.SELLER);
        User watcher = data.user("ws-watcher@auctix.io", Role.BIDDER);
        User bidder = data.user("ws-bidder@auctix.io", Role.BIDDER);
        Auction auction = data.liveAuction(seller, new BigDecimal("1000.00"), new BigDecimal("100.00"));

        BlockingQueue<Map<String, Object>> topicEvents = new LinkedBlockingQueue<>();
        StompSession session = connect(watcher);
        session.subscribe("/topics/auction/" + auction.getId(), collector(topicEvents));

        // Give the broker a moment to register the subscription before the bid lands.
        Thread.sleep(400);

        placeBidOverRest(auction.getId(), bidder, new BigDecimal("1200.00"));

        Map<String, Object> event = topicEvents.poll(10, TimeUnit.SECONDS);
        assertThat(event).as("the watcher received a live event without refreshing").isNotNull();
        assertThat(event.get("type")).isEqualTo("BID_PLACED");
        assertThat(event.get("auctionId")).isEqualTo(auction.getId().intValue());

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) event.get("state");
        assertThat(state).isNotNull();
        assertThat(new BigDecimal(String.valueOf(state.get("currentPrice"))))
                .isEqualByComparingTo("1200.00");

        @SuppressWarnings("unchecked")
        Map<String, Object> bid = (Map<String, Object>) event.get("bid");
        assertThat(bid).isNotNull();
        assertThat(String.valueOf(bid.get("bidderMasked")))
                .as("the live feed shows a masked identity, never the full name")
                .doesNotContain("@")
                .contains("***");

        session.disconnect();
    }

    @Test
    @DisplayName("The previous leader receives a private OUTBID notification")
    void previousLeaderIsNotifiedPrivately() throws Exception {
        User seller = data.user("ws-outbid-seller@auctix.io", Role.SELLER);
        User first = data.user("ws-outbid-first@auctix.io", Role.BIDDER);
        User second = data.user("ws-outbid-second@auctix.io", Role.BIDDER);
        Auction auction = data.liveAuction(seller, new BigDecimal("100.00"), new BigDecimal("10.00"));

        BlockingQueue<Map<String, Object>> privateEvents = new LinkedBlockingQueue<>();
        StompSession session = connect(first);
        session.subscribe("/user/queue/notifications", collector(privateEvents));
        Thread.sleep(400);

        placeBidOverRest(auction.getId(), first, new BigDecimal("100.00"));
        // The bidder's own "bid accepted" notification arrives first.
        Map<String, Object> accepted = privateEvents.poll(10, TimeUnit.SECONDS);
        assertThat(accepted).isNotNull();

        placeBidOverRest(auction.getId(), second, new BigDecimal("500.00"));

        Map<String, Object> outbid = null;
        for (int i = 0; i < 4 && outbid == null; i++) {
            Map<String, Object> next = privateEvents.poll(10, TimeUnit.SECONDS);
            if (next != null && "OUTBID".equals(next.get("type"))) {
                outbid = next;
            }
        }

        assertThat(outbid).as("the outbid bidder was told immediately").isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> notification = (Map<String, Object>) outbid.get("notification");
        assertThat(notification).isNotNull();
        assertThat(String.valueOf(notification.get("title"))).contains("outbid");

        session.disconnect();
    }

    // ------------------------------------------------------------------ helpers

    private StompSession connect(User user) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        client.setMessageConverter(converter);

        // The broker advertises heartbeats, so the client needs a scheduler to answer them.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-test-");
        scheduler.initialize();
        client.setTaskScheduler(scheduler);

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer "
                + jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name()));

        return client.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders,
                        new StompSessionHandlerAdapter() {
                        })
                .get(10, TimeUnit.SECONDS);
    }

    private StompFrameHandler collector(BlockingQueue<Map<String, Object>> sink) {
        return new StompFrameHandler() {
            @Override
            @NonNull
            public Type getPayloadType(@NonNull StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(@NonNull StompHeaders headers, Object payload) {
                if (payload instanceof Map<?, ?> map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> typed = (Map<String, Object>) map;
                    sink.add(typed);
                }
            }
        };
    }

    private void placeBidOverRest(Long auctionId, User bidder, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtService.generateToken(bidder.getId(), bidder.getEmail(),
                bidder.getRole().name()));
        PlaceBidRequest request = new PlaceBidRequest(amount, null, null);
        new RestTemplate().postForEntity("http://localhost:" + port + "/api/auctions/" + auctionId + "/bids",
                new HttpEntity<>(request, headers), String.class);
    }
}
