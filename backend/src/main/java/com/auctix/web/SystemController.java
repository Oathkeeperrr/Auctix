package com.auctix.web;

import com.auctix.domain.Category;
import com.auctix.dto.common.CategoryDto;
import com.auctix.dto.common.ServerTimeDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "System")
public class SystemController {

    @Operation(summary = "Authoritative server clock used to synchronise every countdown")
    @GetMapping("/system/time")
    public ServerTimeDto time() {
        return ServerTimeDto.now();
    }

    @GetMapping("/system/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "auctix-backend", "time", ServerTimeDto.now().serverTime());
    }

    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return Arrays.stream(Category.values()).map(CategoryDto::from).toList();
    }
}
