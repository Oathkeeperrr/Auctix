package com.auctix.dto.assistant;

import java.util.List;

/**
 * What the assistant replies.
 *
 * @param engine      which engine produced it. {@code RULES} means a documented rule engine
 *                    reading the AuctiX database — not a language model. The UI shows this, so
 *                    nobody is left thinking a static matcher is an AI.
 * @param engineLabel the same thing in words, for display
 * @param suggestions follow-up questions worth asking next
 * @param references  lots named in the answer, so they can be opened directly
 */
public record AssistantAnswer(
        String answer,
        String engine,
        String engineLabel,
        List<String> suggestions,
        List<AssistantReference> references) {

    public static AssistantAnswer rules(String answer, List<String> suggestions,
                                        List<AssistantReference> references) {
        return new AssistantAnswer(answer, "RULES",
                "Rule-based assistant reading live AuctiX data", suggestions, references);
    }
}
