package com.decorativewings;

public final class WingType {
    public static final String NONE = "";
    public static final String SPRITE = "sprite";

    public static boolean isV1(String style) {
        return style != null && style.endsWith("_v1");
    }


    public static boolean isSet(String style) {
        return style != null && !style.isBlank();
    }
}
