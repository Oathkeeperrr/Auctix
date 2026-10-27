package com.auctix.config;

import com.auctix.domain.*;
import com.auctix.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Loads the demo catalogue.
 *
 * <h2>Why this is idempotent per lot</h2>
 * The previous version returned early whenever the users table had any row, which meant a lot
 * added later never appeared without {@code docker compose down -v}. Each lot is now keyed on
 * its title: an existing title is skipped and anything new is created, so the catalogue can
 * grow across restarts. Users are matched on e-mail in the same way.
 *
 * <h2>Photographs</h2>
 * {@link Lot#slug()} names a file stem under {@code static/images/lots}; three frames are
 * attached as {@code <slug>-1.jpg} to {@code <slug>-3.jpg}. A null slug means the lot has no
 * photographs yet, and the catalogue tile falls back to its category glyph. See the README in
 * that folder for the filenames still wanted.
 *
 * <p>Disable the whole thing with {@code AUCTIX_SEED_ENABLED=false}.</p>
 */
@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AuctixProperties properties;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(AuctixProperties properties,
                      UserRepository userRepository,
                      ItemRepository itemRepository,
                      AuctionRepository auctionRepository,
                      BidRepository bidRepository,
                      PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.itemRepository = itemRepository;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }

        String password = passwordEncoder.encode(properties.getSeed().getDefaultPassword());

        User admin = user("Ava Administrator", "admin@auctix.io", Role.ADMIN, password);
        user("Meridian Luxury House", "seller@auctix.io", Role.SELLER, password);
        user("Apex Motors Collection", "motors@auctix.io", Role.SELLER, password);
        user("Atelier Verde", "atelier@auctix.io", Role.SELLER, password);
        List<User> bidders = List.of(
                user("Kalpesh Sharma", "kalpesh@auctix.io", Role.BIDDER, password),
                user("Riya Mehta", "riya@auctix.io", Role.BIDDER, password),
                user("Daniel Okafor", "daniel@auctix.io", Role.BIDDER, password));

        Instant now = Instant.now();
        int created = 0;
        int skipped = 0;

        for (Lot lot : catalogue()) {
            if (itemRepository.existsByTitleIgnoreCase(lot.title())) {
                skipped++;
                continue;
            }
            createLot(lot, admin, bidders, now);
            created++;
        }

        int backfilled = backfillMissingGalleries();

        if (backfilled > 0) {
            log.info("Attached photographs to {} lots that had none.", backfilled);
        }
        if (created > 0) {
            log.info("Seeded {} new lots ({} already present). Default password: {}",
                    created, skipped, properties.getSeed().getDefaultPassword());
        } else {
            log.info("Demo catalogue already complete: {} lots present.", skipped);
        }
    }

    /**
     * Gives photographs to lots that are already in the database without any.
     *
     * <p>Seeding is keyed on title, so a lot created before its artwork existed is skipped by
     * the main loop for ever. This fills that gap in place, which is what lets artwork be added
     * later without {@code docker compose down -v}. A lot that already has images is left
     * alone, so this is safe to run on every start.</p>
     */
    private int backfillMissingGalleries() {
        int touched = 0;
        for (Lot lot : catalogue()) {
            if (lot.slug() == null) {
                continue;
            }
            Item item = itemRepository.findFirstByTitleIgnoreCase(lot.title()).orElse(null);
            if (item == null || !item.getImages().isEmpty()) {
                continue;
            }
            attachGallery(item, lot.slug());
            itemRepository.save(item);
            touched++;
        }
        return touched;
    }

    // ------------------------------------------------------------------ the catalogue

    /**
     * The demo catalogue: 28 lots across all eight categories.
     *
     * <p>13 live, 7 scheduled, 4 ended and 4 awaiting admin approval. The four pending lots
     * deliberately carry no photographs, which is what a listing looks like before its seller
     * has uploaded any.</p>
     */
    private List<Lot> catalogue() {
        return List.of(
                // ---------------------------------------------------------- live, photographed
                new Lot("submariner", "Rolex Submariner Date 126610LN",
                        "A 2023 Submariner Date in Oystersteel with a black Cerachrom bezel and black "
                                + "dial. Complete set with box, papers and an unused warranty card. "
                                + "Serviced and authenticated by an independent watchmaker.",
                        Category.LUXURY_WATCHES, "Rolex", "Unworn, full set", "seller@auctix.io",
                        "9800.00", "11000.00", "250.00", AuctionStatus.LIVE, -1560, 3752, 4),

                new Lot("daytona", "Rolex Cosmograph Daytona 116500LN",
                        "Yellow gold Cosmograph Daytona with a champagne dial and tachymetric bezel. "
                                + "Purchased 2021, worn sparingly, accompanied by its original "
                                + "certificate and service pouch.",
                        Category.LUXURY_WATCHES, "Rolex", "Excellent", "seller@auctix.io",
                        "28000.00", "32000.00", "500.00", AuctionStatus.LIVE, -900, 2700, 3),

                new Lot("nautilus", "Patek Philippe Nautilus 5711/1A - Sprint Lot",
                        "Demonstration lot with a deliberately short runtime: it closes a few minutes "
                                + "after start-up so automatic closing, anti-sniping extensions and "
                                + "winner settlement can be watched end to end in one sitting.",
                        Category.LUXURY_WATCHES, "Patek Philippe", "Excellent", "seller@auctix.io",
                        "1000.00", null, "50.00", AuctionStatus.LIVE, -1, 10, 1),

                new Lot("jordan", "Air Jordan 1 Retro High OG 'Chicago'",
                        "Deadstock pair, US 10, 2022 reissue with the original box and both lace sets. "
                                + "Never worn, stored in a climate-controlled collection.",
                        Category.SNEAKERS, "Nike", "Deadstock", "seller@auctix.io",
                        "800.00", "950.00", "25.00", AuctionStatus.LIVE, -420, 4380, 5),

                new Lot("royal", "Air Jordan 1 Retro High OG 'Black Gold'",
                        "US 9.5, worn twice, with the original box. Metallic gold panels show no "
                                + "creasing; the midsole is unyellowed.",
                        Category.SNEAKERS, "Nike", "Very good", "seller@auctix.io",
                        "450.00", null, "20.00", AuctionStatus.LIVE, -300, 2100, 2),

                new Lot("chiron", "Bugatti Chiron Pur Sport",
                        "One of 60 examples worldwide. 1,500 hp quad-turbocharged W16, carbon fibre "
                                + "body, magnesium wheels. 1,240 km from new, full service history, "
                                + "French registration with export documentation available.",
                        Category.CARS, "Bugatti", "1,240 km from new", "motors@auctix.io",
                        "390000.00", "400000.00", "5000.00", AuctionStatus.LIVE, -600, 1815, 3),

                new Lot("composition", "Composition No. 7 - Original Oil on Canvas",
                        "Signed original abstract in oil, 90 x 70 cm, geometric planes in cadmium "
                                + "orange and ultramarine. Gallery-framed under museum glass with a "
                                + "certificate from the artist's studio.",
                        Category.ART, "Atelier Verde", "New, framed", "atelier@auctix.io",
                        "3200.00", "4000.00", "100.00", AuctionStatus.LIVE, -2880, 5760, 4),

                new Lot("macintosh", "Apple Macintosh 128K (1984)",
                        "First-generation Macintosh in working order, serial-matched to its original "
                                + "keyboard and mouse. Recapped power supply, new old-stock floppy "
                                + "drive belt, boots to System 1.1.",
                        Category.ELECTRONICS, "Apple", "Working, restored", "seller@auctix.io",
                        "2200.00", "2600.00", "100.00", AuctionStatus.LIVE, -1200, 2520, 3),

                // ---------------------------------------------------------- live, awaiting photographs
                new Lot("tank", "Cartier Tank Louis Cartier 18k",
                        "Hand-wound Tank Louis Cartier in 18k yellow gold on a Cartier alligator strap "
                                + "with deployant buckle. Serviced 2025, running within four seconds "
                                + "a day.",
                        Category.LUXURY_WATCHES, "Cartier", "Excellent", "seller@auctix.io",
                        "8500.00", "9500.00", "250.00", AuctionStatus.LIVE, -720, 3240, 2),

                new Lot("gullwing", "Mercedes-Benz 300 SL Gullwing",
                        "Matching-numbers 1956 Gullwing restored over four years to concours standard. "
                                + "Silver over red leather, original tool roll and fitted luggage. "
                                + "Documented ownership from new.",
                        Category.CARS, "Mercedes-Benz", "Concours restoration", "motors@auctix.io",
                        "1200000.00", "1350000.00", "20000.00", AuctionStatus.LIVE, -1440, 5760, 3),

                new Lot("dunkparis", "Nike Dunk Low SB 'Paris'",
                        "US 9, one of 202 pairs produced for the 2002 White Dunk exhibition. Worn, "
                                + "with visible sole separation at the right toe, sold as a "
                                + "collector's piece rather than wearable footwear.",
                        Category.SNEAKERS, "Nike", "Worn, collector grade", "seller@auctix.io",
                        "18000.00", "24000.00", "500.00", AuctionStatus.LIVE, -2160, 4320, 4),

                new Lot("birkin", "Hermes Birkin 30 Togo, Gold Hardware",
                        "Togo leather Birkin 30 in Gold with palladium hardware, 2022 blind stamp. "
                                + "Complete with dust bag, clochette, lock, keys and rain cover. "
                                + "Corners show no rubbing.",
                        Category.FASHION, "Hermes", "Pristine", "seller@auctix.io",
                        "14000.00", "16500.00", "500.00", AuctionStatus.LIVE, -840, 3360, 3),

                new Lot("mantle", "1952 Topps Mickey Mantle #311, PSA 5",
                        "PSA-encapsulated example of the 1952 Topps high-number Mantle, graded EX 5. "
                                + "Strong centring for the grade, four sharp corners, no creasing "
                                + "visible under magnification.",
                        Category.SPORTS_MEMORABILIA, "Topps", "PSA 5", "seller@auctix.io",
                        "95000.00", "120000.00", "2500.00", AuctionStatus.LIVE, -4320, 2880, 5),

                // ---------------------------------------------------------- scheduled
                new Lot("carrera", "Porsche 911 Carrera 3.2 Coupe",
                        "1987 Carrera 3.2 with the G50 gearbox, finished in Grand Prix White over "
                                + "black leather. 78,000 km, engine rebuilt at 70,000 km, complete "
                                + "service book and both keys.",
                        Category.CARS, "Porsche", "Excellent", "motors@auctix.io",
                        "95000.00", "110000.00", "2500.00", AuctionStatus.SCHEDULED, 2880, 11520, 0),

                new Lot("starrynight", "The Starry Night - Museum Grade Replica",
                        "Hand-painted oil-on-canvas museum-grade reproduction of Van Gogh's 1889 work, "
                                + "73 x 92 cm, finished in a hand-gilded frame. Certificate of "
                                + "reproduction included; this is not an original work.",
                        Category.ART, "Atelier Verde", "New, framed", "atelier@auctix.io",
                        "2500.00", null, "100.00", AuctionStatus.SCHEDULED, 7200, 17280, 0),

                new Lot("reverso", "Jaeger-LeCoultre Reverso Tribute Duoface",
                        "Stainless steel Reverso Tribute Duoface, 2023, with both dials in excellent "
                                + "order and the reversing case action tight. Full set including "
                                + "a spare strap.",
                        Category.LUXURY_WATCHES, "Jaeger-LeCoultre", "Full set", "seller@auctix.io",
                        "7200.00", "8000.00", "200.00", AuctionStatus.SCHEDULED, 4320, 13000, 0),

                new Lot("integrale", "Lancia Delta HF Integrale Evoluzione II",
                        "1993 Evoluzione II in Rosso Monza, 61,000 km, one of the final 'Dealers "
                                + "Collection' cars. Cambelt and water pump replaced 2024, Lancia "
                                + "heritage certificate present.",
                        Category.CARS, "Lancia", "Very good", "motors@auctix.io",
                        "68000.00", "78000.00", "1000.00", AuctionStatus.SCHEDULED, 5760, 15840, 0),

                new Lot("yeezyzebra", "Adidas Yeezy Boost 350 V2 'Zebra'",
                        "US 11, deadstock, 2022 restock with the original box and receipt. Primeknit "
                                + "shows no discolouration and the boost sole is unmarked.",
                        Category.SNEAKERS, "Adidas", "Deadstock", "seller@auctix.io",
                        "320.00", null, "10.00", AuctionStatus.SCHEDULED, 1440, 8640, 0),

                new Lot("gameboy", "Nintendo Game Boy DMG-01, Factory Sealed",
                        "1989 launch unit still in its factory seal, with the outer carton intact. "
                                + "Seal is unbroken and the shrink wrap carries the original "
                                + "fold pattern. Unverified contents, sold as sealed.",
                        Category.ELECTRONICS, "Nintendo", "Factory sealed", "seller@auctix.io",
                        "4500.00", "6000.00", "250.00", AuctionStatus.SCHEDULED, 10080, 20160, 0),

                new Lot("chanelflap", "Chanel Classic Flap Medium, Lambskin",
                        "Black lambskin medium Classic Flap with gold hardware, series 29 (2020). "
                                + "Interior clean, chain untarnished, authenticity card and "
                                + "original box included.",
                        Category.FASHION, "Chanel", "Very good", "seller@auctix.io",
                        "6800.00", "7500.00", "200.00", AuctionStatus.SCHEDULED, 3600, 12960, 0),

                // ---------------------------------------------------------- ended
                new Lot("speedmaster", "Omega Speedmaster Professional Moonwatch",
                        "Hesalite crystal, calibre 3861, full set purchased in 2024. Bracelet at full "
                                + "length with all removed links present.",
                        Category.LUXURY_WATCHES, "Omega", "Like new", "seller@auctix.io",
                        "4200.00", "4800.00", "100.00", AuctionStatus.SETTLED, -14400, -2880, 6),

                new Lot("rangefinder", "Leica M6 Classic 0.72 Rangefinder",
                        "1992 M6 Classic with a 0.72 finder, recently CLA'd by a Leica-trained "
                                + "technician. Rangefinder patch bright and correctly aligned; "
                                + "meter accurate across the range.",
                        Category.ELECTRONICS, "Leica", "Serviced", "seller@auctix.io",
                        "2800.00", "3200.00", "100.00", AuctionStatus.SETTLED, -20160, -5760, 5),

                new Lot("greatwave", "The Great Wave off Kanagawa - Later Edition Woodblock",
                        "Meiji-period impression from the original blocks, 25 x 37 cm. Colours "
                                + "well preserved with light toning at the margins. Reserve not "
                                + "met at close; relisting under consideration.",
                        Category.ART, "Katsushika Hokusai", "Good, toned", "atelier@auctix.io",
                        "6000.00", "15000.00", "250.00", AuctionStatus.CLOSED, -17280, -4320, 3),

                new Lot("worldcupshirt", "Match-Worn 1986 World Cup Final Shirt",
                        "Short-sleeved match-worn shirt from the 1986 final, accompanied by a letter "
                                + "of provenance from the player's family and a photo-matching "
                                + "report covering the second half.",
                        Category.SPORTS_MEMORABILIA, "Le Coq Sportif", "Match worn", "seller@auctix.io",
                        "45000.00", "55000.00", "1000.00", AuctionStatus.SETTLED, -28800, -10080, 7),

                // ---------------------------------------------------------- awaiting approval
                new Lot("cobalt", "Study in Cobalt - Original Oil on Board",
                        "Unsigned original study in oil on prepared board, 40 x 30 cm. Submitted for "
                                + "review; photographs to follow once the studio lighting is set up.",
                        Category.ART, "Atelier Verde", "New", "atelier@auctix.io",
                        "900.00", null, "50.00", AuctionStatus.PENDING_APPROVAL, 1440, 8640, 0),

                new Lot("levis501", "Levi's 501XX Big E, 1950s",
                        "Single-stitch 501XX with a hidden rivet waistband and Big E tab, 32 x 34. "
                                + "Two repaired knee blowouts, otherwise structurally sound. "
                                + "Awaiting approval and photographs.",
                        Category.FASHION, "Levi's", "Repaired, wearable", "seller@auctix.io",
                        "2400.00", "3000.00", "100.00", AuctionStatus.PENDING_APPROVAL, 2880, 11520, 0),

                new Lot("steiff", "Steiff Teddy Bear with Button-in-Ear, c.1905",
                        "Early blonde mohair bear with boot-button eyes and a blank Steiff button. "
                                + "Pads replaced at some point in its life; original stuffing "
                                + "intact. Condition report and photographs to follow.",
                        Category.COLLECTIBLES, "Steiff", "Restored pads", "seller@auctix.io",
                        "5500.00", "7000.00", "250.00", AuctionStatus.PENDING_APPROVAL, 4320, 14400, 0),

                new Lot("hobbit", "The Hobbit, First Edition, Allen and Unwin 1937",
                        "First impression of the first edition, one of 1,500 copies. Original cloth "
                                + "with the Tolkien runic dust jacket present but price-clipped. "
                                + "Full collation and photographs pending review.",
                        Category.COLLECTIBLES, "Allen and Unwin", "Very good in jacket", "seller@auctix.io",
                        "40000.00", "60000.00", "1000.00", AuctionStatus.PENDING_APPROVAL, 5760, 17280, 0));
    }

    // ------------------------------------------------------------------ construction

    private void createLot(Lot lot, User admin, List<User> bidders, Instant now) {
        User seller = userRepository.findByEmailIgnoreCase(lot.sellerEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Seed lot '" + lot.title() + "' names an unknown seller: " + lot.sellerEmail()));

        Item item = new Item(seller, lot.title(), lot.description(), lot.category());
        item.setBrand(lot.brand());
        item.setConditionLabel(lot.condition());
        attachGallery(item, lot.slug());
        itemRepository.save(item);

        Instant start = now.plus(lot.startOffsetMinutes(), ChronoUnit.MINUTES);
        Instant end = now.plus(lot.endOffsetMinutes(), ChronoUnit.MINUTES);

        Auction auction = new Auction(item, new BigDecimal(lot.startingPrice()),
                new BigDecimal(lot.increment()), start, end);
        if (lot.reservePrice() != null) {
            auction.setReservePrice(new BigDecimal(lot.reservePrice()));
        }
        auction.setStatus(lot.status());
        if (lot.status() != AuctionStatus.PENDING_APPROVAL) {
            auction.setApprovedBy(admin);
            // A lot that has not started yet was approved just now, not two hours before a
            // start time still in the future, which would date the approval forward.
            Instant approvedAt = start.isBefore(now) ? start.minus(2, ChronoUnit.HOURS) : now;
            auction.setApprovedAt(approvedAt);
        }
        // Auction carries @Version initialised to 0L rather than null, so Spring Data reads it
        // as an existing row and calls merge() instead of persist(). merge() returns a managed
        // COPY and leaves the object passed in detached, so everything downstream must use the
        // returned instance; a Bid built against the original would reference a transient
        // Auction and the flush would fail.
        Auction saved = auctionRepository.save(auction);

        // Bids are replayed between the start and whichever comes first: now, or the close.
        Instant ladderEnd = end.isBefore(now) ? end : now;
        saved = applyLadder(saved, bidders, lot.bids(), start, ladderEnd);

        if (lot.status() == AuctionStatus.CLOSED || lot.status() == AuctionStatus.SETTLED) {
            close(saved, end);
        }
    }

    /**
     * Replays a deterministic bid ladder so a lot opens with a credible history whose
     * {@code currentPrice} and {@code bidCount} are derived from real rows rather than typed in.
     *
     * <p>Bidders rotate, so no one ever bids against themselves, and the steps are multiples of
     * the lot's own increment.</p>
     *
     * @return the managed auction, which is not necessarily the instance passed in
     */
    private Auction applyLadder(Auction auction, List<User> bidders, int count,
                                Instant start, Instant end) {
        if (count <= 0 || bidders.isEmpty()) {
            return auction;
        }
        long span = Math.max(count, Duration.between(start, end).toMinutes());
        BigDecimal amount = auction.getStartingPrice();
        Bid previous = null;

        for (int i = 0; i < count; i++) {
            amount = amount.add(auction.getMinIncrement().multiply(BigDecimal.valueOf(1L + (i % 3))));
            User bidder = bidders.get(i % bidders.size());
            Instant at = start.plus(span * (i + 1) / (count + 1), ChronoUnit.MINUTES);

            Bid bid = new Bid(auction, bidder, amount, at);
            bid.setStatus(BidStatus.WINNING);
            bidRepository.save(bid);
            if (previous != null) {
                previous.setStatus(BidStatus.OUTBID);
                bidRepository.save(previous);
            }
            previous = bid;

            auction.setCurrentPrice(amount);
            auction.setHighestBidder(bidder);
            auction.setBidCount(auction.getBidCount() + 1);
        }
        return auctionRepository.save(auction);
    }

    /**
     * Finishes an ended lot. A reserve above the final bid leaves it closed with no winner,
     * which is what the admin screens need in order to show an unsold lot.
     */
    private void close(Auction auction, Instant end) {
        auction.setClosedAt(end);
        boolean sold = auction.getHighestBidder() != null && auction.isReserveMet();
        if (sold) {
            auction.setWinner(auction.getHighestBidder());
            auction.setWinningAmount(auction.getCurrentPrice());
            auction.setStatus(AuctionStatus.SETTLED);
            auction.setSettledAt(end.plus(2, ChronoUnit.HOURS));
        } else {
            auction.setStatus(AuctionStatus.CLOSED);
        }
        auctionRepository.save(auction);
    }

    /** Attaches the three-frame gallery. A null slug means the lot has no photographs yet. */
    private void attachGallery(Item item, String slug) {
        if (slug == null) {
            return;
        }
        item.addImage("/images/lots/" + slug + "-1.jpg", "Hero shot");
        item.addImage("/images/lots/" + slug + "-2.jpg", "Detail");
        item.addImage("/images/lots/" + slug + "-3.jpg", "Alternate angle");
    }

    private User user(String name, String email, Role role, String passwordHash) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(new User(name, email, passwordHash, role)));
    }

    /**
     * One row of the demo catalogue. Offsets are minutes relative to start-up, so the catalogue
     * is always correctly positioned in time however long after the build it is first run.
     */
    private record Lot(
            String slug,
            String title,
            String description,
            Category category,
            String brand,
            String condition,
            String sellerEmail,
            String startingPrice,
            String reservePrice,
            String increment,
            AuctionStatus status,
            long startOffsetMinutes,
            long endOffsetMinutes,
            int bids) {
    }
}
