package com.decorativewings.client;

public record WingDefinition(
    String id,
    String textureFile,
    boolean sculpt,
    boolean attachOnRight,
    float targetHeight,
    boolean perPixel
) {}
