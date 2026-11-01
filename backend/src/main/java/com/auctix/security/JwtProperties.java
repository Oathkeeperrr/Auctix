package com.auctix.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings. The secret is supplied through the {@code JWT_SECRET} environment variable;
 * it is never hard-coded in source.
 */
@ConfigurationProperties(prefix = "auctix.jwt")
public class JwtProperties {

    private String secret = "";
    private long expirationMinutes = 720;
    private String issuer = "auctix";
    /**
     * Development convenience: when no secret is supplied, generate a random one at start-up
     * instead of refusing to boot. Enabled only by the {@code dev} and {@code test} profiles;
     * a production run must provide {@code JWT_SECRET}.
     */
    private boolean generateIfMissing = false;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }

    public boolean isGenerateIfMissing() {
        return generateIfMissing;
    }

    public void setGenerateIfMissing(boolean generateIfMissing) {
        this.generateIfMissing = generateIfMissing;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
