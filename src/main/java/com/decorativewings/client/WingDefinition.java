package com.decorativewings.client;

public record WingDefinition(
    String id,
    String texture,
    boolean sculpt,
    boolean attachOnRight,
    float targetHeight,
    boolean perPixel
) {}
