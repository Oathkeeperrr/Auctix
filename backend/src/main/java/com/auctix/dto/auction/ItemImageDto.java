package com.auctix.dto.auction;

import com.auctix.domain.ItemImage;

/**
 * One frame in a lot's gallery. {@code url} is relative to the server root
 * (e.g. {@code /images/lots/submariner-1.jpg} or {@code /uploads/3f2a....jpg}); the client
 * resolves it against its configured base URL.
 */
public record ItemImageDto(String url, String caption, int sortOrder) {

    public static ItemImageDto from(ItemImage image) {
        return new ItemImageDto(image.getUrl(), image.getCaption(), image.getSortOrder());
    }
}
