package com.auctix.web;

import com.auctix.dto.assistant.AssistantAnswer;
import com.auctix.dto.assistant.AssistantQuery;
import com.auctix.service.AssistantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The assistant.
 *
 * <p>Open to anyone, signed in or not, because it only ever reads lots the public catalogue
 * already shows. It takes no user identity and returns nothing account-specific, so there is
 * nothing here an anonymous visitor could learn that browsing would not already tell them.</p>
 */
@RestController
@RequestMapping("/api/assistant")
@Tag(name = "Assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/ask")
    public AssistantAnswer ask(@Valid @RequestBody AssistantQuery query) {
        return assistantService.ask(query.question(), query.auctionId());
    }

    /** Opening prompts and which engine is answering, so the UI can label itself honestly. */
    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        List<String> suggestions = assistantService.openingSuggestions();
        return Map.of(
                "engine", "RULES",
                "engineLabel", "Rule-based assistant reading live AuctiX data",
                "modelConfigured", assistantService.modelConfigured(),
                "suggestions", suggestions);
    }
}
