package com.auctix.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "items",
        indexes = {
                @Index(name = "idx_items_seller", columnList = "seller_id"),
                @Index(name = "idx_items_category", columnList = "category")
        })
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "model_kind", nullable = false, length = 20)
    private ModelKind modelKind = ModelKind.COLLECTIBLE;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 120)
    private String brand;

    @Column(length = 120)
    private String conditionLabel;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * The ordered gallery. {@link #imageUrl} stays the single cover used by catalogue tiles;
     * this is what the detail screen shows. Cascading means saving an Item saves its gallery,
     * which is what lets the seeder build a lot and its photographs in one go.
     */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ItemImage> images = new ArrayList<>();

    protected Item() {
    }

    public Item(User seller, String title, String description, Category category) {
        this.seller = seller;
        this.title = title;
        this.description = description;
        this.category = category;
        this.modelKind = category.getDefaultModel();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getSeller() {
        return seller;
    }

    public void setSeller(User seller) {
        this.seller = seller;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public ModelKind getModelKind() {
        return modelKind;
    }

    public void setModelKind(ModelKind modelKind) {
        this.modelKind = modelKind;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getConditionLabel() {
        return conditionLabel;
    }

    public void setConditionLabel(String conditionLabel) {
        this.conditionLabel = conditionLabel;
    }

    public List<ItemImage> getImages() {
        return images;
    }

    public void setImages(List<ItemImage> images) {
        this.images = images;
    }

    /**
     * Appends a photograph at the next free position and makes the first one the cover.
     * Keeping both sides of the association in step is what lets a single save persist both.
     */
    public ItemImage addImage(String url, String caption) {
        ItemImage image = new ItemImage(this, url, images.size(), caption);
        images.add(image);
        if (imageUrl == null) {
            imageUrl = url;
        }
        return image;
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
        if (!(o instanceof Item other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
