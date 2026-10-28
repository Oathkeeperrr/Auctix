package com.auctix.service;

import com.auctix.config.AuctixProperties;
import com.auctix.exception.ApiException;
import com.auctix.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores seller-uploaded photographs on disk.
 *
 * <h2>What is trusted and what is not</h2>
 * Nothing the browser sends is trusted. The declared {@code Content-Type} is advisory, and the
 * original filename is never used to build a path or an extension: a request can claim
 * {@code image/png} for a shell script, or a name like {@code ../../application.yml}.
 *
 * <p>What is trusted is the first few bytes of the file. Every accepted format has a fixed
 * signature, so the type is decided by reading the content and the extension is derived from
 * what was actually found. The stored name is a fresh UUID, which means a caller cannot choose
 * where the file lands, cannot overwrite an existing file, and cannot smuggle a path separator
 * through. The resolved path is then checked to be inside the upload root as a second line of
 * defence.</p>
 *
 * <p>Files are written to the {@code uploads-data} Docker volume, outside the application jar
 * and outside the frontend image, and are served read-only under {@code /uploads/}.</p>
 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    /** How many bytes we need to recognise every supported signature. */
    private static final int SIGNATURE_LENGTH = 12;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final AuctixProperties.Uploads settings;
    private Path root;

    public StorageService(AuctixProperties properties) {
        this.settings = properties.getUploads();
    }

    @PostConstruct
    void prepare() {
        this.root = Path.of(settings.getDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            log.info("Upload directory ready: {}", root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create the upload directory: " + root, ex);
        }
    }

    /**
     * Validates and stores one image.
     *
     * @return the server-relative URL the browser should use, e.g. {@code /uploads/3f2a....jpg}
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The uploaded file is empty.");
        }
        if (file.getSize() > settings.getMaxFileSizeBytes()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Each image must be " + (settings.getMaxFileSizeBytes() / (1024 * 1024))
                            + " MB or smaller.",
                    Map.of("size", file.getSize(), "limit", settings.getMaxFileSizeBytes()));
        }

        String detected = sniff(file);
        if (!settings.getAllowedTypes().contains(detected)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Only JPEG, PNG and WebP images are accepted.",
                    Map.of("detected", detected));
        }

        String name = UUID.randomUUID() + "." + EXTENSIONS.get(detected);
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            // Unreachable with a UUID name; kept because the cost of being wrong here is a
            // write anywhere on the filesystem.
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Rejected image path.");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to store an upload at {}", target, ex);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The image could not be saved.");
        }

        log.debug("Stored upload {} ({} bytes, {})", name, file.getSize(), detected);
        return "/uploads/" + name;
    }

    /**
     * Identifies the format from the file's own leading bytes.
     *
     * @return a media type, or {@code "unknown"} when no supported signature matches
     */
    private String sniff(MultipartFile file) {
        byte[] head = new byte[SIGNATURE_LENGTH];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.readNBytes(head, 0, SIGNATURE_LENGTH);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The uploaded file could not be read.");
        }
        if (read < 4) {
            return "unknown";
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        if (read >= SIGNATURE_LENGTH
                && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        return "unknown";
    }

    /** True when a URL points at something this application serves, rather than off-site. */
    public static boolean isLocalImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim().toLowerCase(Locale.ROOT);
        return (trimmed.startsWith("/uploads/") || trimmed.startsWith("/images/"))
                && !trimmed.contains("..");
    }
}
