package com.auctix.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/**
 * One photograph of a lot.
 *
 * <p>A lot is almost never sold on a single frame: a bidder wants the hero shot, a detail of
 * the part that carries the value, and at least one other angle. {@link Item#getImageUrl()}
 * remains the cover used by the catalogue tile, and this table holds the ordered gallery shown
 * on the auction detail screen.</p>
 *
 * <p>The column is {@code sort_order} rather than {@code position}, because {@code POSITION}
 * is a reserved function name in both PostgreSQL and H2 and an unquoted identifier of that
 * name fails at schema-generation time.</p>
 */
@Entity
@Table(name = "item_images",
        indexes = {
                @Index(name = "idx_item_images_item", columnList = "item_id"),
                @Index(name = "idx_item_images_order", columnList = "item_id, sort_order")
        })
public class ItemImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false, length = 500)
    private String url;

    /** Zero-based display order. Index 0 is the cover. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Short label shown under the thumbnail, e.g. "Dial detail". Optional. */
    @Column(length = 160)
    private String caption;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ItemImage() {
    }

    public ItemImage(Item item, String url, int sortOrder, String caption) {
        this.item = item;
        this.url = url;
        this.sortOrder = sortOrder;
        this.caption = caption;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ItemImage other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
