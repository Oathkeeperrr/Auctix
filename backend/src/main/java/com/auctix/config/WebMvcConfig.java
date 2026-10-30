package com.auctix.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves seller uploads from the Docker volume.
 *
 * <p>Seeded lot photographs live in {@code static/images/lots} and are served by Spring's own
 * static handling. Uploads cannot live there: the jar is immutable and an image rebuild would
 * discard them. They are written to {@code /app/uploads} instead and mapped here, read-only.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuctixProperties properties;

    public WebMvcConfig(AuctixProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(properties.getUploads().getDirectory()).toAbsolutePath().normalize();
        // Built by hand rather than with Path.toUri(), which only appends the trailing slash
        // when the directory already exists on disk. This runs before StorageService has had a
        // chance to create it, and a location without the slash resolves nothing.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + root + "/")
                .setCachePeriod(604800);
    }
}
