package com.decorativewings;

import java.util.Locale;

public final class WingType {
    public static final String NONE = "";
    public static final String SPRITE = "sprite";
    public static final String MODEL = "model";
    public static final String INSECT = "insect";
    public static final String BIRD = "bird";
    public static final String SPRITE_V1 = "sprite_v1";
    public static final String MODEL_V1 = "model_v1";
    public static final String INSECT_V1 = "insect_v1";
    public static final String BIRD_V1 = "bird_v1";

    public static final int KIND_BAT = 0;
    public static final int KIND_INSECT = 1;
    public static final int KIND_BIRD = 2;

    private WingType() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return SPRITE;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "sprite", "pixel", "old", "2d", "first", "1" -> SPRITE;
            case "model", "fbx", "plane", "test", "3d", "wingstest", "elytra" -> MODEL;
            case "insect", "leaf", "fairy", "butterfly", "second", "2" -> INSECT;
            case "bird", "feather", "angel", "third", "3" -> BIRD;
            case "sprite_v1", "spritev1", "pixel_v1" -> SPRITE_V1;
            case "model_v1", "modelv1", "elytra_v1" -> MODEL_V1;
            case "insect_v1", "insectv1", "leaf_v1" -> INSECT_V1;
            case "bird_v1", "birdv1", "feather_v1" -> BIRD_V1;
            case "none", "off", "remove" -> NONE;
            default -> null;
        };
    }

    public static String base(String style) {
        if (style != null && style.endsWith("_v1")) {
            return style.substring(0, style.length() - 3);
        }
        return style;
    }

    public static boolean isV1(String style) {
        return style != null && style.endsWith("_v1");
    }

    public static int kind(String style) {
        String base = base(style);
        if (INSECT.equals(base)) {
            return KIND_INSECT;
        }
        if (BIRD.equals(base)) {
            return KIND_BIRD;
        }
        return KIND_BAT;
    }

    public static boolean isSet(String style) {
        return style != null && !style.isBlank();
    }
}
