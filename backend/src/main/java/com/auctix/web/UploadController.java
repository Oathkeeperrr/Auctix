package com.auctix.web;

import com.auctix.config.AuctixProperties;
import com.auctix.dto.auction.UploadedImageDto;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import com.auctix.security.UserPrincipal;
import com.auctix.service.StorageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Seller photograph uploads. Writing is restricted to sellers and administrators in
 * {@code SecurityConfig}; reading back is public, because a catalogue nobody can see is no
 * catalogue.
 */
@RestController
@RequestMapping("/api/uploads")
@Tag(name = "Uploads")
public class UploadController {

    private final StorageService storageService;
    private final AuctixProperties.Uploads settings;

    public UploadController(StorageService storageService, AuctixProperties properties) {
        this.storageService = storageService;
        this.settings = properties.getUploads();
    }

    /**
     * Stores up to {@code auctix.uploads.max-per-listing} images and returns their URLs, in the
     * order supplied. Validation happens per file inside {@link StorageService}, so one bad file
     * fails the whole request rather than leaving a half-built gallery behind.
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<UploadedImageDto> uploadImages(@RequestPart("files") List<MultipartFile> files,
                                               @AuthenticationPrincipal UserPrincipal principal) {
        CurrentUser.require(principal);

        if (files == null || files.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Choose at least one image.");
        }
        if (files.size() > settings.getMaxPerListing()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "A listing may carry at most " + settings.getMaxPerListing() + " images.",
                    Map.of("supplied", files.size(), "limit", settings.getMaxPerListing()));
        }

        List<UploadedImageDto> stored = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            stored.add(new UploadedImageDto(
                    storageService.store(file),
                    file.getOriginalFilename(),
                    file.getSize()));
        }
        return stored;
    }
}
