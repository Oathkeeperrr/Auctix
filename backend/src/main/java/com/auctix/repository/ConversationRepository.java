package com.auctix.repository;

import com.auctix.domain.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * Threads this person takes part in, most recently active first.
     *
     * <p>Fetching both participants and the auction here keeps the conversation list to one
     * query instead of one per row.</p>
     */
    @Query("select c from Conversation c "
            + "left join fetch c.participantOne left join fetch c.participantTwo "
            + "left join fetch c.auction a left join fetch a.item "
            + "where c.participantOne.id = :userId or c.participantTwo.id = :userId "
            + "order by c.lastMessageAt desc")
    List<Conversation> findForUser(@Param("userId") Long userId);

    /** Existing thread about a particular lot. Participants are always id-ordered. */
    Optional<Conversation> findByParticipantOneIdAndParticipantTwoIdAndAuctionId(
            Long participantOneId, Long participantTwoId, Long auctionId);

    /**
     * Existing general thread. A separate method because a derived query with a null parameter
     * generates {@code = null}, which never matches; {@code IsNull} generates {@code is null}.
     */
    Optional<Conversation> findByParticipantOneIdAndParticipantTwoIdAndAuctionIsNull(
            Long participantOneId, Long participantTwoId);
}
