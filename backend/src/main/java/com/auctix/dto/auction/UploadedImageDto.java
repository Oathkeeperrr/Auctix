package com.auctix.dto.auction;

/**
 * The result of storing one uploaded photograph.
 *
 * @param url          where the browser can fetch it, server-relative
 * @param originalName the name the file had on the seller's machine, echoed back so the UI can
 *                     label the preview; never used to build a path
 * @param sizeBytes    the stored size
 */
public record UploadedImageDto(String url, String originalName, long sizeBytes) {
}
