package com.auctix.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to the frontend.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "The submitted data is not valid."),
    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "The auction schedule is not valid."),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "E-mail or password is incorrect."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),

    USER_NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "You are not allowed to perform this action."),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "This account has been suspended."),
    SELF_BID_FORBIDDEN(HttpStatus.FORBIDDEN, "Sellers cannot bid on their own auction."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Item not found."),
    AUCTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Auction not found."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Notification not found."),
    // Reported for a thread that exists but is none of the caller's business too: saying
    // "forbidden" would confirm that two particular people are talking.
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found."),

    EMAIL_ALREADY_USED(HttpStatus.CONFLICT, "An account with this e-mail already exists."),
    AUCTION_NOT_ACTIVE(HttpStatus.CONFLICT, "This auction is not open for bidding."),
    AUCTION_ENDED(HttpStatus.CONFLICT, "This auction has already ended."),
    AUCTION_NOT_APPROVED(HttpStatus.CONFLICT, "This auction has not been approved yet."),
    AUCTION_ALREADY_STARTED(HttpStatus.CONFLICT, "This auction has already started and can no longer be edited."),
    DUPLICATE_BID(HttpStatus.CONFLICT, "You are already the highest bidder."),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "That auction state change is not allowed."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Another bid landed first. Please try again."),

    BID_TOO_LOW(HttpStatus.UNPROCESSABLE_ENTITY, "Your bid is below the minimum required amount."),
    RESERVE_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY, "The reserve price was not met."),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many bids. Please slow down."),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
