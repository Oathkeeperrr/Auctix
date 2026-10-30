package com.auctix.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Tunable platform behaviour. Every value is overridable through the environment,
 * e.g. {@code AUCTIX_AUCTION_ANTISNIPE_ENABLED=false}.
 */
@ConfigurationProperties(prefix = "auctix")
public class AuctixProperties {

    private final Auction auction = new Auction();
    private final Bidding bidding = new Bidding();
    private final Redis redis = new Redis();
    private final Cors cors = new Cors();
    private final Seed seed = new Seed();
    private final Uploads uploads = new Uploads();
    private final Assistant assistant = new Assistant();

    public Auction getAuction() {
        return auction;
    }

    public Bidding getBidding() {
        return bidding;
    }

    public Redis getRedis() {
        return redis;
    }

    public Cors getCors() {
        return cors;
    }

    public Seed getSeed() {
        return seed;
    }

    public Assistant getAssistant() {
        return assistant;
    }

    public Uploads getUploads() {
        return uploads;
    }

    public static class Auction {
        private final AntiSnipe antiSnipe = new AntiSnipe();
        /** How long before the end an auction is flagged as ENDING. */
        private int endingWindowSeconds = 60;
        /** Days after settlement before an auction is archived. */
        private int archiveAfterDays = 30;
        /** How often the lifecycle scheduler runs, in milliseconds. */
        private long schedulerIntervalMs = 1000;

        public AntiSnipe getAntiSnipe() {
            return antiSnipe;
        }

        public int getEndingWindowSeconds() {
            return endingWindowSeconds;
        }

        public void setEndingWindowSeconds(int endingWindowSeconds) {
            this.endingWindowSeconds = endingWindowSeconds;
        }

        public int getArchiveAfterDays() {
            return archiveAfterDays;
        }

        public void setArchiveAfterDays(int archiveAfterDays) {
            this.archiveAfterDays = archiveAfterDays;
        }

        public long getSchedulerIntervalMs() {
            return schedulerIntervalMs;
        }

        public void setSchedulerIntervalMs(long schedulerIntervalMs) {
            this.schedulerIntervalMs = schedulerIntervalMs;
        }
    }

    public static class AntiSnipe {
        private boolean enabled = true;
        /** A bid inside this many seconds of the end triggers an extension. */
        private int windowSeconds = 30;
        /** How many seconds the auction is extended by. */
        private int extensionSeconds = 30;
        /** Safety valve so an auction cannot be extended forever. */
        private int maxExtensions = 20;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public int getExtensionSeconds() {
            return extensionSeconds;
        }

        public void setExtensionSeconds(int extensionSeconds) {
            this.extensionSeconds = extensionSeconds;
        }

        public int getMaxExtensions() {
            return maxExtensions;
        }

        public void setMaxExtensions(int maxExtensions) {
            this.maxExtensions = maxExtensions;
        }
    }

    public static class Bidding {
        private boolean rateLimitEnabled = true;
        private int rateLimitMaxBids = 10;
        private int rateLimitWindowSeconds = 10;
        /** Bounded retries when an optimistic lock conflict is detected. */
        private int maxRetries = 3;
        /** Upper bound on proxy-bid resolution iterations per bid. */
        private int maxProxyIterations = 25;

        public boolean isRateLimitEnabled() {
            return rateLimitEnabled;
        }

        public void setRateLimitEnabled(boolean rateLimitEnabled) {
            this.rateLimitEnabled = rateLimitEnabled;
        }

        public int getRateLimitMaxBids() {
            return rateLimitMaxBids;
        }

        public void setRateLimitMaxBids(int rateLimitMaxBids) {
            this.rateLimitMaxBids = rateLimitMaxBids;
        }

        public int getRateLimitWindowSeconds() {
            return rateLimitWindowSeconds;
        }

        public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) {
            this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getMaxProxyIterations() {
            return maxProxyIterations;
        }

        public void setMaxProxyIterations(int maxProxyIterations) {
            this.maxProxyIterations = maxProxyIterations;
        }
    }

    public static class Redis {
        /** When false the platform runs single-instance and events are dispatched in-process. */
        private boolean enabled = true;
        private String channel = "auctix:events";
        /** TTL for cached auction snapshots, in seconds. */
        private long cacheTtlSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getChannel() {
            return channel;
        }

        public void setChannel(String channel) {
            this.channel = channel;
        }

        public long getCacheTtlSeconds() {
            return cacheTtlSeconds;
        }

        public void setCacheTtlSeconds(long cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Seed {
        /** Loads demo users and auctions on an empty database. */
        private boolean enabled = true;
        private String defaultPassword = "Password123!";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDefaultPassword() {
            return defaultPassword;
        }

        public void setDefaultPassword(String defaultPassword) {
            this.defaultPassword = defaultPassword;
        }
    }

    /**
     * Seller photograph uploads. The directory is the {@code uploads-data} Docker volume, which
     * docker-compose.yml already mounts at {@code /app/uploads}; it lives outside the jar so
     * uploads survive a rebuild.
     */
    public static class Uploads {
        private String directory = "/app/uploads";
        /** Per file. The edge proxy also caps the whole request at 12 MB. */
        private long maxFileSizeBytes = 5L * 1024 * 1024;
        private int maxPerListing = 6;
        private List<String> allowedTypes = List.of("image/jpeg", "image/png", "image/webp");

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public int getMaxPerListing() {
            return maxPerListing;
        }

        public void setMaxPerListing(int maxPerListing) {
            this.maxPerListing = maxPerListing;
        }

        public List<String> getAllowedTypes() {
            return allowedTypes;
        }

        public void setAllowedTypes(List<String> allowedTypes) {
            this.allowedTypes = allowedTypes;
        }
    }

    /**
     * The assistant. With no provider configured it answers from a documented rule engine and
     * says so; see AssistantService for why no model client is bundled.
     *
     * <p>The key is read from the environment and never reaches the browser: the frontend asks
     * the server a question and receives an answer, so there is no path by which a credential
     * could end up in a bundle.</p>
     */
    public static class Assistant {
        private boolean enabled = true;
        private String provider = "";
        private String apiKey = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
