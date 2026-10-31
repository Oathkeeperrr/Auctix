package com.auctix.domain;

public enum Category {
    LUXURY_WATCHES("Luxury Watches", ModelKind.WATCH),
    CARS("Cars", ModelKind.CAR),
    SNEAKERS("Sneakers", ModelKind.SNEAKER),
    ART("Art", ModelKind.ARTWORK),
    ELECTRONICS("Electronics", ModelKind.COLLECTIBLE),
    COLLECTIBLES("Collectibles", ModelKind.COLLECTIBLE),
    FASHION("Fashion", ModelKind.SNEAKER),
    SPORTS_MEMORABILIA("Sports Memorabilia", ModelKind.COLLECTIBLE);

    private final String label;
    private final ModelKind defaultModel;

    Category(String label, ModelKind defaultModel) {
        this.label = label;
        this.defaultModel = defaultModel;
    }

    public String getLabel() {
        return label;
    }

    public ModelKind getDefaultModel() {
        return defaultModel;
    }
}
