
package com.decorativewings.client;

import java.util.HashMap;
import java.util.Map;

public class AnimationConfig {
    public Map<String, Map<String, StateParams>> types = new HashMap<>();

    public static class StateParams {
        public float beatRate = 0.05f;
        public float snap = 0.1f;
        public float spread = 13.0f;
        public float fold = 14.0f;
        public float swayBase = 0.0f;
        public float swayAmp = 0.0f;
        public float stretch = 1.0f;
        public float twist = 5.0f;
        public float pitch = 0.0f;
        public float bank = 0.0f;
        public float curl = 4.0f;
        public float dihedral = 8.0f;
        public float flutter = 0.8f;
        public float flapBase = 0.0f;
        public float flapAmp = 5.0f;
        public float phaseOffset = 0.0f;
    }
}