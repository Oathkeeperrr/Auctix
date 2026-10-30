package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.domain.Auction;
import com.auctix.domain.AuctionStatus;
import com.auctix.domain.Category;
import com.auctix.dto.assistant.AssistantAnswer;
import com.auctix.dto.assistant.AssistantReference;
import com.auctix.repository.AuctionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The AuctiX assistant.
 *
 * <h2>What this is, and what it is not</h2>
 * This is a rule engine, not a language model. It matches a question against an ordered list of
 * intents and answers from the database and from configuration. Every answer it returns is
 * labelled {@code RULES} and the interface says so, because presenting a keyword matcher as an
 * AI would be a lie the user could not check.
 *
 * <p>A real provider can be configured at {@code auctix.assistant.*}. No client is shipped:
 * writing an untested HTTP integration against a key nobody has would be worse than an honest
 * gap. When a key is present this logs once and still answers from the rules, so the behaviour
 * is never silently different from what the label claims. The extension point is
 * {@link #modelConfigured()}.</p>
 *
 * <h2>What it can see</h2>
 * Only publicly visible lots, through the same {@link AuctionSpecifications#publiclyVisible()}
 * filter the public catalogue uses. It has no access to users, bids by name, reserve prices,
 * messages or administrative data, so a question cannot be used to read something the asker
 * could not already see by browsing.
 */
@Service
public class AssistantService {

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);
    private static final int MAX_REFERENCES = 4;

    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "What is ending soon?",
            "How does bidding work?",
            "Show me luxury watches",
            "What is anti-sniping?");

    private final AuctionRepository auctionRepository;
    private final AuctixProperties properties;
    private boolean warnedAboutProvider;

    public AssistantService(AuctionRepository auctionRepository, AuctixProperties properties) {
        this.auctionRepository = auctionRepository;
        this.properties = properties;
    }

    public List<String> openingSuggestions() {
        return DEFAULT_SUGGESTIONS;
    }

    /** True when someone has configured a provider. Kept separate as the extension point. */
    public boolean modelConfigured() {
        String key = properties.getAssistant().getApiKey();
        return key != null && !key.isBlank();
    }

    @Transactional(readOnly = true)
    public AssistantAnswer ask(String rawQuestion, Long auctionId) {
        if (modelConfigured() && !warnedAboutProvider) {
            warnedAboutProvider = true;
            log.warn("auctix.assistant.api-key is set but no model client is bundled; "
                    + "answering from the rule engine and labelling replies as such.");
        }

        String q = rawQuestion == null ? "" : rawQuestion.toLowerCase(Locale.ROOT).trim();

        // Ordered intents. The first match wins, so the more specific ones come first.
        if (q.isEmpty()) {
            return help();
        }
        if (auctionId != null && mentions(q, "this lot", "this auction", "this item",
                "current bid", "how much", "time left", "ending", "next bid", "minimum")) {
            Optional<AssistantAnswer> about = aboutLot(auctionId, q);
            if (about.isPresent()) {
                return about.get();
            }
        }
        if (mentions(q, "hello", "hi ", "hey", "help", "what can you")) {
            return help();
        }
        if (mentions(q, "auto bid", "auto-bid", "automatic bid", "proxy bid", "maximum bid", "max bid")) {
            return autoBidding();
        }
        if (mentions(q, "snipe", "sniping", "last second", "extend")) {
            return antiSniping();
        }
        if (mentions(q, "reserve")) {
            return reserve();
        }
        if (mentions(q, "close", "closing", "end time", "when does", "how does it end", "winner")) {
            return closing();
        }
        if (mentions(q, "bid", "bidding", "increment")) {
            return bidding();
        }
        if (mentions(q, "sell", "selling", "list an item", "create a listing", "create an auction")) {
            return selling();
        }
        if (mentions(q, "register", "sign up", "account", "sign in", "log in")) {
            return accounts();
        }
        if (mentions(q, "watchlist", "watch list", "follow", "save a lot")) {
            return watchlist();
        }
        if (mentions(q, "message", "contact the seller", "chat", "ask the seller")) {
            return messaging();
        }
        if (mentions(q, "fee", "commission", "payment", "pay", "delivery", "shipping", "invoice")) {
            return notBuiltYet();
        }
        if (mentions(q, "ending soon", "finishing", "about to end", "last chance")) {
            return endingSoon();
        }
        if (mentions(q, "how many", "how much is there", "catalogue", "catalog", "inventory")) {
            return catalogueSize();
        }
        if (mentions(q, "cheap", "lowest", "affordable", "budget", "under")) {
            return byPrice(Sort.Direction.ASC);
        }
        if (mentions(q, "expensive", "highest", "priciest", "most valuable")) {
            return byPrice(Sort.Direction.DESC);
        }

        Category category = matchCategory(q);
        if (category != null) {
            return byCategory(category);
        }

        List<Auction> found = search(q);
        if (!found.isEmpty()) {
            return AssistantAnswer.rules(
                    "Here is what matches \"" + rawQuestion.trim() + "\" in the catalogue.",
                    List.of("What is ending soon?", "How does bidding work?"),
                    references(found));
        }
        return unknown(rawQuestion);
    }

    // ---------------------------------------------------------------- data-backed answers

    private Optional<AssistantAnswer> aboutLot(Long auctionId, String q) {
        Optional<Auction> maybe = auctionRepository.findDetailById(auctionId)
                .filter(a -> AuctionSpecifications.PUBLIC_STATUSES.contains(a.getStatus()));
        if (maybe.isEmpty()) {
            return Optional.empty();
        }
        Auction auction = maybe.get();
        Instant now = Instant.now();
        StringBuilder sb = new StringBuilder();
        sb.append(auction.getItem().getTitle()).append(" is currently ")
          .append(auction.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' ')).append(". ");

        if (auction.getBidCount() == 0) {
            sb.append("No bids yet, so the first bid simply has to meet the starting price of ")
              .append(money(auction.getStartingPrice())).append(". ");
        } else {
            sb.append("The current bid is ").append(money(auction.getCurrentPrice()))
              .append(" after ").append(auction.getBidCount())
              .append(auction.getBidCount() == 1 ? " bid" : " bids")
              .append(", and the next bid must be at least ")
              .append(money(auction.nextMinimumBid())).append(". ");
        }

        long seconds = auction.secondsRemaining(now);
        if (seconds > 0) {
            sb.append(auction.getStatus() == AuctionStatus.SCHEDULED ? "It opens in " : "It closes in ")
              .append(humanDuration(seconds)).append(", by the server's clock.");
        } else {
            sb.append("Bidding is closed.");
        }

        return Optional.of(AssistantAnswer.rules(sb.toString(),
                List.of("How does automatic bidding work?", "What is anti-sniping?", "What is a reserve price?"),
                List.of(AssistantReference.from(auction))));
    }

    private AssistantAnswer endingSoon() {
        List<Auction> soon = auctionRepository.findByStatusInOrderByEndTimeAsc(
                List.of(AuctionStatus.LIVE, AuctionStatus.ENDING), PageRequest.of(0, MAX_REFERENCES));
        if (soon.isEmpty()) {
            return AssistantAnswer.rules("Nothing is live at the moment.",
                    List.of("How many lots are there?", "Show me luxury watches"), List.of());
        }
        Auction first = soon.get(0);
        long seconds = first.secondsRemaining(Instant.now());
        return AssistantAnswer.rules(
                "These close soonest. " + first.getItem().getTitle() + " is first, in "
                        + humanDuration(seconds) + ".",
                List.of("How does automatic bidding work?", "What is anti-sniping?"),
                references(soon));
    }

    private AssistantAnswer catalogueSize() {
        long live = auctionRepository.countByStatus(AuctionStatus.LIVE);
        long scheduled = auctionRepository.countByStatus(AuctionStatus.SCHEDULED);
        long settled = auctionRepository.countByStatus(AuctionStatus.SETTLED)
                + auctionRepository.countByStatus(AuctionStatus.CLOSED);
        return AssistantAnswer.rules(
                "There are " + live + " lots bidding now, " + scheduled + " opening later, and "
                        + settled + " already finished. Browse them all under Auctions, where you can "
                        + "filter by category, status and price.",
                List.of("What is ending soon?", "Show me cars", "Show me art"),
                List.of());
    }

    private AssistantAnswer byCategory(Category category) {
        List<Auction> found = auctionRepository.findAll(
                AuctionSpecifications.publiclyVisible().and(AuctionSpecifications.hasCategory(category)),
                PageRequest.of(0, MAX_REFERENCES, Sort.by(Sort.Direction.ASC, "endTime"))).getContent();
        if (found.isEmpty()) {
            return AssistantAnswer.rules(
                    "Nothing in " + category.getLabel() + " is on the block right now.",
                    List.of("What is ending soon?", "How many lots are there?"), List.of());
        }
        return AssistantAnswer.rules(
                "Here is what is in " + category.getLabel() + ", closing soonest first.",
                List.of("What is ending soon?", "How does bidding work?"),
                references(found));
    }

    private AssistantAnswer byPrice(Sort.Direction direction) {
        List<Auction> found = auctionRepository.findAll(
                AuctionSpecifications.publiclyVisible()
                        .and(AuctionSpecifications.hasAnyStatus(List.of(AuctionStatus.LIVE, AuctionStatus.SCHEDULED))),
                PageRequest.of(0, MAX_REFERENCES, Sort.by(direction, "currentPrice"))).getContent();
        if (found.isEmpty()) {
            return AssistantAnswer.rules("Nothing is open for bidding right now.",
                    List.of("How many lots are there?"), List.of());
        }
        return AssistantAnswer.rules(
                direction == Sort.Direction.ASC
                        ? "These are the lowest current bids among open lots."
                        : "These carry the highest current bids among open lots.",
                List.of("What is ending soon?", "What is a reserve price?"),
                references(found));
    }

    private List<Auction> search(String text) {
        String cleaned = text.replaceAll("[^a-z0-9 ]", " ").trim();
        if (cleaned.length() < 3) {
            return List.of();
        }
        Specification<Auction> spec = AuctionSpecifications.publiclyVisible()
                .and(AuctionSpecifications.titleOrDescriptionContains(cleaned));
        List<Auction> direct = auctionRepository.findAll(spec,
                PageRequest.of(0, MAX_REFERENCES, Sort.by(Sort.Direction.ASC, "endTime"))).getContent();
        if (!direct.isEmpty()) {
            return direct;
        }
        // Fall back to the longest single word, which rescues "do you have any rolex watches".
        String longest = "";
        for (String word : cleaned.split("\\s+")) {
            if (word.length() > longest.length()) {
                longest = word;
            }
        }
        if (longest.length() < 4) {
            return List.of();
        }
        return auctionRepository.findAll(
                AuctionSpecifications.publiclyVisible()
                        .and(AuctionSpecifications.titleOrDescriptionContains(longest)),
                PageRequest.of(0, MAX_REFERENCES, Sort.by(Sort.Direction.ASC, "endTime"))).getContent();
    }

    // ---------------------------------------------------------------- explanations

    private AssistantAnswer help() {
        return AssistantAnswer.rules(
                "I can explain how AuctiX works and look things up in the live catalogue. "
                        + "Ask me what is ending soon, what is in a category, how bidding or "
                        + "anti-sniping works, or about the lot you are looking at. I read only "
                        + "public listing data, never anyone's account or messages.",
                DEFAULT_SUGGESTIONS, List.of());
    }

    private AssistantAnswer bidding() {
        return AssistantAnswer.rules(
                "Every bid is validated on the server inside a transaction that holds a row lock "
                        + "on the auction, so simultaneous bids are ordered and nothing is lost. "
                        + "The first bid must meet the starting price; after that each bid must "
                        + "clear the current price by at least the lot's minimum increment. The "
                        + "timestamp recorded is the server's, not your computer's.",
                List.of("How does automatic bidding work?", "What is anti-sniping?", "What is a reserve price?"),
                List.of());
    }

    private AssistantAnswer autoBidding() {
        return AssistantAnswer.rules(
                "Automatic bidding lets you set the most you are willing to pay. AuctiX then bids "
                        + "on your behalf, one increment at a time, only as high as it needs to keep "
                        + "you in front, and stops at your maximum. Your limit is never shown to "
                        + "anyone else. Note that the backend logic exists but the controls are not "
                        + "yet wired into the interface, so you cannot set a maximum from the site today.",
                List.of("How does bidding work?", "What is anti-sniping?"), List.of());
    }

    private AssistantAnswer antiSniping() {
        AuctixProperties.AntiSnipe snipe = properties.getAuction().getAntiSnipe();
        if (!snipe.isEnabled()) {
            return AssistantAnswer.rules("Anti-sniping is switched off on this installation, so lots "
                    + "close exactly at their end time.", List.of("How does bidding work?"), List.of());
        }
        return AssistantAnswer.rules(
                "A bid placed in the last " + snipe.getWindowSeconds() + " seconds pushes the end "
                        + "time out by another " + snipe.getExtensionSeconds() + " seconds, up to "
                        + snipe.getMaxExtensions() + " times. That stops someone winning by bidding "
                        + "a moment before the close, and means a lot ends only when nobody wants "
                        + "to bid again.",
                List.of("How does bidding work?", "What is ending soon?"), List.of());
    }

    private AssistantAnswer reserve() {
        return AssistantAnswer.rules(
                "A reserve is the lowest price a seller will accept. Bidding below it is allowed, "
                        + "but the lot will not sell unless the reserve is met. Whether a reserve "
                        + "has been met is shown on the lot; the figure itself is visible only to "
                        + "the seller and administrators.",
                List.of("How does bidding work?", "What happens when a lot closes?"), List.of());
    }

    private AssistantAnswer closing() {
        return AssistantAnswer.rules(
                "Lots open and close on the server clock, not your browser's — the countdown you "
                        + "see is only a rendering of the server's time. A scheduler checks every "
                        + "second, so a lot closes on time whether or not anyone is watching. When "
                        + "it closes, the highest bidder wins if the reserve was met, and both "
                        + "parties are notified.",
                List.of("What is anti-sniping?", "What is a reserve price?"), List.of());
    }

    private AssistantAnswer selling() {
        return AssistantAnswer.rules(
                "Sign in with a seller account, open Seller from the menu and choose Create auction. "
                        + "Add photographs, a description, a starting price, a minimum increment and "
                        + "the times you want it to run. The listing then waits for an administrator "
                        + "to approve it before it goes live.",
                List.of("How does bidding work?", "What is a reserve price?"), List.of());
    }

    private AssistantAnswer accounts() {
        return AssistantAnswer.rules(
                "Choose Sign Up to create an account as a bidder or a seller. On this demo "
                        + "installation you can also use the one-click accounts on the sign-in "
                        + "screen. Passwords are stored as salted PBKDF2 hashes, never in plain text.",
                List.of("How do I sell something?", "How does bidding work?"), List.of());
    }

    private AssistantAnswer watchlist() {
        return AssistantAnswer.rules(
                "The heart on a lot adds it to your watchlist. Watched lots are collected on your "
                        + "dashboard so you can find them again without searching.",
                List.of("What is ending soon?", "How does bidding work?"), List.of());
    }

    private AssistantAnswer messaging() {
        return AssistantAnswer.rules(
                "Open a lot, go to the Details tab and choose Message seller. That starts a private "
                        + "thread between the two of you, which you will find behind the envelope in "
                        + "the menu. Only the two participants can read it.",
                List.of("How do I sell something?", "What is ending soon?"), List.of());
    }

    private AssistantAnswer notBuiltYet() {
        return AssistantAnswer.rules(
                "Payment, fees and delivery are not part of this installation. AuctiX covers "
                        + "listing, approval, live bidding and settlement; what happens after a lot "
                        + "is won is handled outside the platform.",
                List.of("What happens when a lot closes?", "How does bidding work?"), List.of());
    }

    private AssistantAnswer unknown(String original) {
        return AssistantAnswer.rules(
                "I could not find anything for \"" + original.trim() + "\", and I would rather say "
                        + "so than guess. I am a rule-based assistant, so I answer a fixed set of "
                        + "questions about how AuctiX works and look up lots in the catalogue.",
                DEFAULT_SUGGESTIONS, List.of());
    }

    // ---------------------------------------------------------------- helpers

    private static boolean mentions(String question, String... needles) {
        for (String needle : needles) {
            if (question.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves a category from the wording of a question.
     *
     * <p>Matching is on whole words, not substrings: a bare {@code contains("art")} would make
     * "Cartier" an art query. Each label also contributes singular stems, so "watches" and
     * "watch", "cars" and "car" all land on the right category.</p>
     */
    private static Category matchCategory(String question) {
        for (Category category : Category.values()) {
            for (String token : categoryTokens(category)) {
                if (containsWord(question, token)) {
                    return category;
                }
            }
        }
        return null;
    }

    private static List<String> categoryTokens(Category category) {
        String label = category.getLabel().toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();
        tokens.add(label);
        for (String word : label.split("\\s+")) {
            if (word.length() < 4) {
                continue;
            }
            tokens.add(word);
            if (word.endsWith("es")) {
                tokens.add(word.substring(0, word.length() - 2));
            } else if (word.endsWith("s")) {
                tokens.add(word.substring(0, word.length() - 1));
            }
        }
        return tokens;
    }

    private static boolean containsWord(String haystack, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }

    private static List<AssistantReference> references(List<Auction> auctions) {
        List<AssistantReference> out = new ArrayList<>(auctions.size());
        for (Auction auction : auctions) {
            out.add(AssistantReference.from(auction));
        }
        return out;
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "--" : "$" + amount.stripTrailingZeros().toPlainString();
    }

    private static String humanDuration(long seconds) {
        Duration d = Duration.ofSeconds(Math.max(0, seconds));
        if (d.toDays() > 0) {
            return d.toDays() + (d.toDays() == 1 ? " day" : " days") + " and " + d.toHoursPart() + "h";
        }
        if (d.toHours() > 0) {
            return d.toHours() + "h " + d.toMinutesPart() + "m";
        }
        return Math.max(1, d.toMinutes()) + " minutes";
    }
}
