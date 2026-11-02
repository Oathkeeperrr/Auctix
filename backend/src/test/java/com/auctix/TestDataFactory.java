package com.auctix;

import com.auctix.domain.*;
import com.auctix.repository.AuctionRepository;
import com.auctix.repository.ItemRepository;
import com.auctix.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Builds the users and auctions the integration tests need.
 */
@Component
public class TestDataFactory {

    public static final String PASSWORD = "Password123!";

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final AuctionRepository auctionRepository;
    private final PasswordEncoder passwordEncoder;

    public TestDataFactory(UserRepository userRepository,
                           ItemRepository itemRepository,
                           AuctionRepository auctionRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.auctionRepository = auctionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User user(String email, Role role) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() ->
                userRepository.save(new User("Test " + role + " " + email.hashCode(), email,
                        passwordEncoder.encode(PASSWORD), role)));
    }

    @Transactional
    public Auction liveAuction(User seller, BigDecimal startingPrice, BigDecimal increment) {
        return liveAuction(seller, startingPrice, increment, Instant.now().plus(2, ChronoUnit.HOURS));
    }

    @Transactional
    public Auction liveAuction(User seller, BigDecimal startingPrice, BigDecimal increment, Instant endTime) {
        Item item = itemRepository.save(new Item(seller, "Test Lot",
                "An item created for the automated test suite.", Category.COLLECTIBLES));
        Auction auction = new Auction(item, startingPrice, increment,
                Instant.now().minus(5, ChronoUnit.MINUTES), endTime);
        auction.setStatus(AuctionStatus.LIVE);
        return auctionRepository.save(auction);
    }

    @Transactional
    public Auction pendingAuction(User seller) {
        Item item = itemRepository.save(new Item(seller, "Pending Lot",
                "Awaiting moderation in the automated test suite.", Category.ART));
        Auction auction = new Auction(item, new BigDecimal("100.00"), new BigDecimal("10.00"),
                Instant.now().plus(1, ChronoUnit.HOURS), Instant.now().plus(3, ChronoUnit.HOURS));
        return auctionRepository.save(auction);
    }
}
