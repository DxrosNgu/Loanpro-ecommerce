package com.loanpro.ecommerce.domain;

public enum Category {
    FOOTWEAR,
    ELECTRONICS,
    ACCESSORIES,
    FOOD_AND_BEVERAGE,
    SPORTS,
    OUTDOORS,
    HOME_AND_OFFICE,
    CLOTHING,
    KITCHEN,
    BOOKS,
    GAMES,
    BEAUTY,
    STATIONERY,
    HEALTH,
    PETS,
    TOOLS,
    GIFTS,
    MISC,
    UNCATEGORIZED;

    public static Category fromCsvValue(String raw) {
        if (raw == null || raw.isBlank()) return UNCATEGORIZED;
        return switch (raw.trim().toLowerCase()) {
            case "footwear"         -> FOOTWEAR;
            case "electronics"      -> ELECTRONICS;
            case "accessories"      -> ACCESSORIES;
            case "food & beverage"  -> FOOD_AND_BEVERAGE;
            case "sports"           -> SPORTS;
            case "outdoors"         -> OUTDOORS;
            case "home & office"    -> HOME_AND_OFFICE;
            case "clothing"         -> CLOTHING;
            case "kitchen"          -> KITCHEN;
            case "books"            -> BOOKS;
            case "games"            -> GAMES;
            case "beauty"           -> BEAUTY;
            case "stationery"       -> STATIONERY;
            case "health"           -> HEALTH;
            case "pets"             -> PETS;
            case "tools"            -> TOOLS;
            case "gifts"            -> GIFTS;
            case "misc"             -> MISC;
            default                 -> UNCATEGORIZED;
        };
    }
}
