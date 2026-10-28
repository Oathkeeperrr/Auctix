package com.auctix.service;

import com.auctix.domain.AuctionStatus;
import com.auctix.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests: no Spring context, no database.
 */
class MaskingAndStateMachineTest {

    @ParameterizedTest(name = "{0} is published as {1}")
    @CsvSource({
            "Kalpesh Sharma, Kal*** S.",
            "Riya Mehta, Riy*** M.",
            "Bo, Bo***",
            "Ava Administrator, Ava*** A."
    })
    @DisplayName("Bid history never exposes a bidder's full name")
    void masksBidderNames(String input, String expected) {
        assertThat(User.maskName(input)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Blank names fall back to a neutral label")
    void masksBlankNames() {
        assertThat(User.maskName(null)).isEqualTo("Anonymous");
        assertThat(User.maskName("   ")).isEqualTo("Anonymous");
    }

    @Test
    @DisplayName("Only LIVE and ENDING auctions accept bids")
    void onlyLiveStatesAcceptBids() {
        assertThat(AuctionStatus.LIVE.acceptsBids()).isTrue();
        assertThat(AuctionStatus.ENDING.acceptsBids()).isTrue();
        assertThat(AuctionStatus.SCHEDULED.acceptsBids()).isFalse();
        assertThat(AuctionStatus.PENDING_APPROVAL.acceptsBids()).isFalse();
        assertThat(AuctionStatus.CLOSED.acceptsBids()).isFalse();
        assertThat(AuctionStatus.SETTLED.acceptsBids()).isFalse();
        assertThat(AuctionStatus.CANCELLED.acceptsBids()).isFalse();
    }

    @Test
    @DisplayName("The lifecycle only allows the documented transitions")
    void enforcesLifecycleTransitions() {
        assertThat(AuctionStatus.PENDING_APPROVAL.canTransitionTo(AuctionStatus.SCHEDULED)).isTrue();
        assertThat(AuctionStatus.PENDING_APPROVAL.canTransitionTo(AuctionStatus.REJECTED)).isTrue();
        assertThat(AuctionStatus.PENDING_APPROVAL.canTransitionTo(AuctionStatus.LIVE)).isFalse();

        assertThat(AuctionStatus.SCHEDULED.canTransitionTo(AuctionStatus.LIVE)).isTrue();
        assertThat(AuctionStatus.LIVE.canTransitionTo(AuctionStatus.ENDING)).isTrue();
        assertThat(AuctionStatus.LIVE.canTransitionTo(AuctionStatus.CLOSED)).isTrue();
        assertThat(AuctionStatus.ENDING.canTransitionTo(AuctionStatus.LIVE)).isTrue();
        assertThat(AuctionStatus.CLOSED.canTransitionTo(AuctionStatus.SETTLED)).isTrue();
        assertThat(AuctionStatus.SETTLED.canTransitionTo(AuctionStatus.ARCHIVED)).isTrue();

        assertThat(AuctionStatus.ARCHIVED.allowedTransitions()).isEmpty();
        assertThat(AuctionStatus.CLOSED.canTransitionTo(AuctionStatus.LIVE)).isFalse();
    }

    @Test
    @DisplayName("Terminal states are reported correctly")
    void identifiesTerminalStates() {
        assertThat(AuctionStatus.SETTLED.isTerminal()).isTrue();
        assertThat(AuctionStatus.ARCHIVED.isTerminal()).isTrue();
        assertThat(AuctionStatus.LIVE.isTerminal()).isFalse();
    }
}
