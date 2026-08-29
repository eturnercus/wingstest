package com.decorativewings.client;

import com.decorativewings.WingType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Pose-driven wing animation. Downstroke is the power beat (spread + forward),
 * upstroke tucks slightly — same idea as bird/elytra cosmetics (Figura butterfly
 * wings, vanilla elytra glide fold).
 */
public final class WingAnimator {
    private static final Map<Integer, State> STATES = new HashMap<>();

    public static final class State {
        public float flap;
        public float spread;
        public float fold;
        public float sway;
        public float stretch;
        public float twist;
        public float pitch;
        public float bank;
        public float turn;
        public float takeoff;
        public float landing;
        public float curl;
        public float dihedral;
        public float flutter;
        public float wave;
        public float billow;
        public float phase;
        public float phase2;
        public float lastYaw;
        public float lastAge;
        public float baseYaw;
        public int kind;
        public boolean wasFlying;
        public boolean initialized;
        public int lastTick;
    }

    private WingAnimator() {
    }

    public static State sample(Player player, float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, String style) {
        State state = STATES.computeIfAbsent(player.getId(), id -> new State());
        if (player.tickCount + 5 < state.lastTick) {
            state.flap = 0.0F;
            state.spread = 13.0F;
            state.fold = 14.0F;
            state.sway = 0.0F;
            state.stretch = 1.0F;
            state.twist = 0.0F;
            state.pitch = 0.0F;
            state.bank = 0.0F;
            state.turn = 0.0F;
            state.takeoff = 0.0F;
            state.landing = 0.0F;
            state.curl = 0.0F;
            state.dihedral = 6.0F;
            state.flutter = 0.0F;
            state.wave = 0.0F;
            state.billow = 0.0F;
            state.baseYaw = -90.0F;
            state.initialized = false;
        }
        state.lastTick = player.tickCount;

        float dt = ageInTicks - state.lastAge;
        if (!state.initialized || dt < 0.0F || dt > 8.0F) {
            dt = 1.0F;
        }
        state.lastAge = ageInTicks;

        Vec3 motion = player.getDeltaMovement();
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        float localStrafe = (float) (motion.x * Mth.cos(yawRad) - motion.z * Mth.sin(yawRad));
        float horiz = (float) Math.hypot(motion.x, motion.z);
        float vert = (float) motion.y;

        if (!state.initialized) {
            state.lastYaw = player.getYRot();
            state.initialized = true;
        }
        float yawDelta = Mth.clamp(Mth.wrapDegrees(player.getYRot() - state.lastYaw), -12.0F, 12.0F);
        state.lastYaw = player.getYRot();

        boolean flying = player.getAbilities().flying;
        boolean elytra = player.isFallFlying() || player.getPose() == Pose.FALL_FLYING;
        boolean onGround = player.onGround();
        boolean airborne = !onGround && !flying && !elytra && !player.isInWater() && !player.isPassenger();

        if (flying && !state.wasFlying) {
            state.takeoff = 1.0F;
        }
        if (!flying && state.wasFlying) {
            state.landing = 1.0F;
        }
        state.wasFlying = flying;
        float decay = (float) Math.pow(0.88, dt);
        state.takeoff = Math.max(0.0F, state.takeoff * decay - 0.008F * dt);
        state.landing = Math.max(0.0F, state.landing * (float) Math.pow(0.86, dt) - 0.012F * dt);

        float targetFlap;
        float targetSpread;
        float targetFold;
        float targetSway;
        float targetStretch;
        float targetTwist;
        float targetPitch;
        float targetBank;
        float targetCurl;
        float targetDihedral;
        float targetFlutter;
        float beatRate;
        float snap = 0.22F;

        if (player.isSleeping() || player.getPose() == Pose.SLEEPING) {
            beatRate = 0.035F;
            snap = 0.14F;
            targetFlap = Mth.sin(state.phase) * 1.5F;
            targetSpread = 3.0F;
            targetFold = 82.0F;
            targetSway = 0.0F;
            targetStretch = 0.80F;
            targetTwist = 12.0F;
            targetPitch = 10.0F;
            targetBank = 0.0F;
            targetCurl = 16.0F;
            targetDihedral = -4.0F;
            targetFlutter = 0.4F;
        } else if (player.isAutoSpinAttack()) {
            beatRate = 0.62F;
            snap = 0.42F;
            float beat = Mth.sin(state.phase);
            targetFlap = beat * 10.0F;
            targetSpread = 8.0F;
            targetFold = 66.0F;
            targetSway = beat * 18.0F;
            targetStretch = 1.20F;
            targetTwist = 34.0F;
            targetPitch = 14.0F;
            targetBank = Mth.sin(state.phase * 1.5F) * 32.0F;
            targetCurl = 8.0F;
            targetDihedral = 4.0F;
            targetFlutter = 10.0F;
        } else if (elytra) {
            beatRate = 0.07F;
            snap = 0.18F;
            float dive = 0.0F;
            if (vert < 0.0F && motion.lengthSqr() > 1.0E-4) {
                dive = 1.0F - (float) Math.pow(Mth.clamp((float) -motion.normalize().y, 0.0F, 1.0F), 1.5F);
            }
            float beat = Mth.sin(state.phase);
            targetFlap = beat * (3.0F + (1.0F - dive) * 4.0F) + Mth.clamp(-vert * 14.0F, -8.0F, 12.0F);
            targetSpread = Mth.lerp(dive, 18.0F + horiz * 10.0F, 8.0F);
            targetFold = Mth.lerp(dive, 36.0F, 58.0F);
            targetSway = localStrafe * 16.0F;
            targetStretch = 1.18F + horiz * 0.12F;
            targetTwist = 16.0F + dive * 10.0F;
            targetPitch = Mth.clamp(headPitch * 0.28F + vert * -18.0F, -30.0F, 28.0F);
            targetBank = Mth.clamp(localStrafe * 42.0F + yawDelta * 2.4F, -34.0F, 34.0F);
            targetCurl = 6.0F + dive * 8.0F;
            targetDihedral = Mth.lerp(dive, 10.0F, 2.0F);
            targetFlutter = 1.2F + (1.0F - dive) * 2.0F;
        } else if (flying) {
            float hover = Mth.clamp(1.0F - horiz * 7.0F, 0.0F, 1.0F);
            float cruise = 1.0F - hover;
            beatRate = 0.22F + hover * 0.12F + cruise * 0.10F;
            snap = 0.16F;
            float down = downstroke(state.phase);
            float beat = Mth.sin(state.phase);
            float beat2 = Mth.sin(state.phase * 0.5F + 0.8F);
            targetFlap = beat * (12.0F + hover * 20.0F) + beat2 * 4.0F + state.takeoff * beat * 16.0F;
            targetSpread = 16.0F + hover * 18.0F + down * 6.0F - cruise * 4.0F + state.takeoff * 10.0F;
            targetFold = 8.0F + cruise * 18.0F + (1.0F - down) * 8.0F - state.takeoff * 10.0F;
            targetSway = beat2 * 5.0F + localStrafe * 18.0F;
            targetStretch = 1.08F + down * 0.10F + state.takeoff * 0.16F;
            targetTwist = 6.0F + beat * 8.0F - down * 4.0F;
            targetPitch = Mth.clamp(vert * 32.0F, -20.0F, 20.0F) + headPitch * 0.14F + cruise * 8.0F - down * 6.0F;
            targetBank = Mth.clamp(localStrafe * 52.0F + yawDelta * 3.0F, -36.0F, 36.0F);
            targetCurl = 6.0F + (1.0F - down) * 8.0F + cruise * 6.0F;
            targetDihedral = 6.0F + hover * 8.0F;
            targetFlutter = 2.0F + hover * 5.0F;
        } else if (player.onClimbable() && !onGround) {
            beatRate = 0.11F;
            snap = 0.22F;
            float climb = Mth.sin(limbSwing);
            targetFlap = climb * 6.0F * limbSwingAmount;
            targetSpread = 10.0F;
            targetFold = 54.0F;
            targetSway = climb * 4.0F;
            targetStretch = 0.90F;
            targetTwist = 10.0F;
            targetPitch = 8.0F;
            targetBank = 0.0F;
            targetCurl = 12.0F;
            targetDihedral = -2.0F;
            targetFlutter = 1.0F;
        } else if (player.getPose() == Pose.SWIMMING && !player.isInWater()) {
            beatRate = 0.10F;
            snap = 0.24F;
            targetFlap = Mth.sin(state.phase) * 4.0F;
            targetSpread = 8.0F;
            targetFold = 70.0F;
            targetSway = Mth.sin(limbSwing) * 5.0F * limbSwingAmount;
            targetStretch = 0.86F;
            targetTwist = 14.0F;
            targetPitch = 16.0F;
            targetBank = 0.0F;
            targetCurl = 14.0F;
            targetDihedral = -6.0F;
            targetFlutter = 0.6F;
        } else if (player.isVisuallySwimming() || player.getPose() == Pose.SWIMMING) {
            beatRate = 0.16F;
            snap = 0.20F;
            float pull = downstroke(state.phase);
            float stroke = Mth.sin(state.phase);
            targetFlap = stroke * 22.0F;
            targetSpread = 8.0F + pull * 20.0F;
            targetFold = 46.0F - pull * 30.0F;
            targetSway = stroke * 8.0F;
            targetStretch = 0.94F + pull * 0.10F;
            targetTwist = 10.0F + (1.0F - pull) * 8.0F;
            targetPitch = 18.0F - pull * 8.0F;
            targetBank = localStrafe * 16.0F;
            targetCurl = 8.0F + (1.0F - pull) * 10.0F;
            targetDihedral = 4.0F;
            targetFlutter = 1.8F;
        } else if (player.isInWater() && !onGround) {
            beatRate = 0.13F;
            snap = 0.18F;
            float pull = downstroke(state.phase);
            float paddle = Mth.sin(state.phase);
            targetFlap = paddle * 14.0F;
            targetSpread = 12.0F + pull * 10.0F;
            targetFold = 32.0F - pull * 14.0F;
            targetSway = paddle * 6.0F;
            targetStretch = 0.96F;
            targetTwist = 8.0F;
            targetPitch = 10.0F;
            targetBank = localStrafe * 18.0F;
            targetCurl = 8.0F;
            targetDihedral = 4.0F;
            targetFlutter = 1.4F;
        } else if (player.isPassenger()) {
            beatRate = 0.065F;
            snap = 0.16F;
            float beat = Mth.sin(state.phase);
            targetFlap = beat * 5.0F;
            targetSpread = 12.0F;
            targetFold = 22.0F;
            targetSway = beat * 3.0F;
            targetStretch = 0.97F;
            targetTwist = 6.0F;
            targetPitch = 4.0F;
            targetBank = yawDelta * 1.2F;
            targetCurl = 6.0F;
            targetDihedral = 8.0F;
            targetFlutter = 0.8F;
        } else if (player.isCrouching()) {
            beatRate = 0.09F;
            snap = 0.22F;
            float step = Mth.sin(limbSwing) * limbSwingAmount;
            targetFlap = Mth.sin(state.phase) * 3.0F + step * 6.0F;
            targetSpread = 12.0F;
            targetFold = 38.0F;
            targetSway = step * 8.0F;
            targetStretch = 0.90F;
            targetTwist = 10.0F + step * 4.0F;
            targetPitch = 12.0F;
            targetBank = step * 6.0F;
            targetCurl = 14.0F;
            targetDihedral = -2.0F;
            targetFlutter = 0.7F;
        } else if (airborne && vert > 0.02F) {
            beatRate = 0.18F;
            snap = 0.34F;
            float beat = Mth.sin(state.phase);
            targetFlap = -28.0F + beat * 8.0F;
            targetSpread = 32.0F;
            targetFold = -8.0F;
            targetSway = localStrafe * 10.0F;
            targetStretch = 1.22F;
            targetTwist = -12.0F;
            targetPitch = -14.0F + headPitch * 0.12F;
            targetBank = yawDelta * 1.8F;
            targetCurl = -4.0F;
            targetDihedral = 14.0F;
            targetFlutter = 2.0F;
        } else if (airborne) {
            beatRate = 0.10F;
            snap = 0.22F;
            float beat = Mth.sin(state.phase);
            float fall = Mth.clamp(-vert * 10.0F, 0.15F, 1.0F);
            targetFlap = 10.0F + fall * 6.0F + beat * 4.0F;
            targetSpread = 28.0F + fall * 6.0F;
            targetFold = 8.0F - fall * 4.0F;
            targetSway = beat * 5.0F + localStrafe * 14.0F;
            targetStretch = 1.14F + fall * 0.10F;
            targetTwist = 6.0F;
            targetPitch = 6.0F + fall * 12.0F;
            targetBank = Mth.clamp(localStrafe * 30.0F + yawDelta * 2.2F, -26.0F, 26.0F);
            targetCurl = 4.0F + fall * 4.0F;
            targetDihedral = 12.0F + fall * 6.0F;
            targetFlutter = 1.5F + fall * 2.0F;
        } else if (player.isSprinting() && onGround) {
            beatRate = 0.08F;
            snap = 0.26F;
            float gait = Mth.sin(limbSwing);
            float gait2 = Mth.sin(limbSwing + 1.1F);
            targetFlap = gait * 10.0F * limbSwingAmount + Mth.sin(state.phase) * 2.0F;
            targetSpread = 9.0F;
            targetFold = 26.0F + Math.abs(gait) * 6.0F;
            targetSway = gait * 16.0F * limbSwingAmount;
            targetStretch = 1.05F;
            targetTwist = gait2 * 12.0F * limbSwingAmount;
            targetPitch = 3.0F;
            targetBank = gait * 14.0F * limbSwingAmount + yawDelta * 1.6F;
            targetCurl = 7.0F;
            targetDihedral = 4.0F;
            targetFlutter = 1.2F;
        } else if (limbSwingAmount > 0.08F && onGround) {
            beatRate = 0.07F;
            snap = 0.22F;
            float gait = Mth.sin(limbSwing);
            float gait2 = Mth.sin(limbSwing * 0.5F + 0.6F);
            targetFlap = gait * 8.0F * limbSwingAmount + Mth.sin(state.phase) * 2.0F;
            targetSpread = 11.0F;
            targetFold = 20.0F;
            targetSway = gait * 12.0F * limbSwingAmount;
            targetStretch = 1.02F;
            targetTwist = gait2 * 8.0F * limbSwingAmount;
            targetPitch = 1.5F;
            targetBank = gait * 9.0F * limbSwingAmount + yawDelta * 1.2F;
            targetCurl = 5.0F;
            targetDihedral = 7.0F;
            targetFlutter = 0.9F;
        } else {
            beatRate = 0.055F;
            snap = 0.10F;
            float beat = Mth.sin(state.phase);
            float beat2 = Mth.sin(state.phase * 0.47F + 1.3F);
            float beat3 = Mth.sin(state.phase * 0.21F + 0.4F);
            float breathe = Mth.sin(state.phase * 0.13F);
            float twitch = Math.max(0.0F, Mth.sin(state.phase * 0.031F) - 0.86F) * 18.0F;
            targetFlap = beat * 5.0F + beat2 * 2.0F + twitch;
            targetSpread = 13.0F + beat3 * 2.5F;
            targetFold = 14.0F + beat2 * 3.0F;
            targetSway = beat3 * 3.2F;
            targetStretch = 1.0F + breathe * 0.03F;
            targetTwist = 5.0F + beat2 * 4.0F;
            targetPitch = beat3 * 2.0F;
            targetBank = beat2 * 2.0F;
            targetCurl = 4.0F + beat2 * 3.0F;
            targetDihedral = 8.0F + breathe * 3.0F;
            targetFlutter = 0.8F + twitch * 0.15F;
        }

        state.kind = WingType.kind(style);
        state.baseYaw = -90.0F;
        if (state.kind == WingType.KIND_INSECT) {
            float clap = 0.5F * (1.0F + Mth.cos(state.phase));
            float peel = 1.0F - clap;
            if (player.isSleeping() || player.getPose() == Pose.SLEEPING || player.isCrouching()) {
                beatRate = 0.04F;
                targetSpread = 6.0F;
                targetFold = 28.0F;
                targetFlap = 0.0F;
                targetDihedral = 10.0F;
                targetCurl = 8.0F;
                targetStretch = 0.92F;
                targetFlutter = 0.4F;
            } else if (player.isInWater()) {
                beatRate = 0.18F;
                snap = 0.22F;
                float pull = downstroke(state.phase);
                targetFlap = Mth.sin(state.phase) * 16.0F;
                targetSpread = 10.0F + pull * 16.0F;
                targetFold = 30.0F - pull * 14.0F;
                targetDihedral = 8.0F + clap * 12.0F;
                targetCurl = 10.0F;
                targetPitch = 12.0F;
                targetFlutter = 3.0F;
            } else if (flying || airborne || elytra) {
                float hover = Mth.clamp(1.0F - horiz * 7.0F, 0.0F, 1.0F);
                beatRate = (flying ? 0.44F : 0.28F) + hover * 0.10F;
                snap = 0.26F;
                targetFlap = Mth.sin(state.phase) * (22.0F + hover * 12.0F);
                targetSpread = 10.0F + peel * 28.0F - (1.0F - hover) * 6.0F;
                targetFold = 8.0F + clap * 16.0F;
                targetSway = localStrafe * 10.0F;
                targetStretch = 1.04F + peel * 0.10F;
                targetTwist = clap * 6.0F;
                targetPitch = Mth.clamp(vert * 18.0F, -14.0F, 14.0F) + (1.0F - hover) * 8.0F;
                targetBank = Mth.clamp(localStrafe * 36.0F + yawDelta * 2.0F, -28.0F, 28.0F);
                targetCurl = clap * 14.0F;
                targetDihedral = 10.0F + clap * 22.0F;
                targetFlutter = 6.0F + hover * 4.0F;
            } else if (limbSwingAmount > 0.08F && onGround) {
                beatRate = 0.08F;
                float gait = Mth.sin(limbSwing);
                targetFlap = gait * 6.0F * limbSwingAmount;
                targetSpread = 8.0F;
                targetFold = 22.0F;
                targetSway = gait * 10.0F * limbSwingAmount;
                targetDihedral = 8.0F;
                targetCurl = 8.0F;
                targetFlutter = 1.0F;
            } else {
                beatRate = 0.07F;
                snap = 0.14F;
                float rest = Mth.sin(state.phase * 0.35F);
                targetFlap = rest * 2.5F;
                targetSpread = 8.0F;
                targetFold = 18.0F;
                targetSway = Mth.sin(limbSwing) * 3.0F * limbSwingAmount;
                targetStretch = 0.96F;
                targetTwist = 4.0F;
                targetPitch = 4.0F;
                targetBank = 0.0F;
                targetCurl = 6.0F;
                targetDihedral = 10.0F + rest * 3.0F;
                targetFlutter = 1.2F;
            }
        } else if (state.kind == WingType.KIND_BIRD) {
            float down = downstroke(state.phase);
            if (player.isSleeping() || player.getPose() == Pose.SLEEPING || player.isCrouching()) {
                beatRate = 0.04F;
                targetFlap = 1.0F;
                targetSpread = 6.0F;
                targetFold = 72.0F;
                targetSway = 0.0F;
                targetStretch = 0.88F;
                targetTwist = 8.0F;
                targetPitch = 8.0F;
                targetBank = 0.0F;
                targetCurl = 16.0F;
                targetDihedral = -2.0F;
                targetFlutter = 0.3F;
            } else if (player.isInWater()) {
                beatRate = 0.14F;
                snap = 0.18F;
                float pull = downstroke(state.phase);
                targetFlap = Mth.sin(state.phase) * 12.0F;
                targetSpread = 10.0F + pull * 12.0F;
                targetFold = 40.0F - pull * 16.0F;
                targetSway = localStrafe * 8.0F;
                targetStretch = 0.94F;
                targetPitch = 12.0F;
                targetCurl = 12.0F;
                targetDihedral = 4.0F;
                targetFlutter = 1.0F;
            } else if (elytra || (airborne && vert <= 0.02F)) {
                beatRate = 0.06F;
                snap = 0.16F;
                targetFlap = Mth.sin(state.phase) * 3.0F;
                targetSpread = 26.0F + Mth.clamp(-vert * 6.0F, 0.0F, 6.0F);
                targetFold = 6.0F;
                targetSway = localStrafe * 12.0F;
                targetStretch = 1.16F;
                targetTwist = 6.0F;
                targetPitch = Mth.clamp(headPitch * 0.2F + vert * -12.0F, -22.0F, 18.0F);
                targetBank = Mth.clamp(localStrafe * 38.0F + yawDelta * 2.4F, -30.0F, 30.0F);
                targetCurl = 4.0F;
                targetDihedral = 14.0F;
                targetFlutter = 1.4F;
            } else if (flying || (airborne && vert > 0.02F)) {
                float hover = Mth.clamp(1.0F - horiz * 7.0F, 0.0F, 1.0F);
                beatRate = 0.24F + hover * 0.10F;
                snap = 0.22F;
                targetFlap = Mth.sin(state.phase) * (22.0F + hover * 8.0F);
                targetSpread = 14.0F + down * 18.0F - (1.0F - hover) * 4.0F;
                targetFold = 32.0F * (1.0F - down) + (1.0F - hover) * 8.0F;
                targetSway = localStrafe * 10.0F;
                targetStretch = 1.02F + down * 0.16F;
                targetTwist = (1.0F - down) * 10.0F;
                targetPitch = -down * 10.0F + Mth.clamp(vert * 24.0F, -16.0F, 16.0F);
                targetBank = Mth.clamp(localStrafe * 44.0F + yawDelta * 2.6F, -32.0F, 32.0F);
                targetCurl = (1.0F - down) * 20.0F;
                targetDihedral = 8.0F + hover * 6.0F;
                targetFlutter = 2.0F;
            } else if (player.isSprinting() && onGround) {
                beatRate = 0.05F;
                float gait = Mth.sin(limbSwing);
                targetFlap = gait * 5.0F * limbSwingAmount;
                targetSpread = 10.0F;
                targetFold = 54.0F;
                targetSway = gait * 8.0F * limbSwingAmount;
                targetStretch = 0.94F;
                targetTwist = 6.0F;
                targetPitch = 4.0F;
                targetBank = gait * 6.0F * limbSwingAmount;
                targetCurl = 12.0F;
                targetDihedral = 2.0F;
                targetFlutter = 0.6F;
            } else {
                beatRate = 0.045F;
                snap = 0.12F;
                float rest = Mth.sin(state.phase * 0.22F);
                targetFlap = rest * 2.0F + Mth.sin(limbSwing) * 3.0F * limbSwingAmount;
                targetSpread = 9.0F;
                targetFold = 60.0F;
                targetSway = Mth.sin(limbSwing) * 5.0F * limbSwingAmount;
                targetStretch = 0.92F + rest * 0.02F;
                targetTwist = 5.0F;
                targetPitch = 5.0F;
                targetBank = 0.0F;
                targetCurl = 14.0F;
                targetDihedral = 3.0F;
                targetFlutter = 0.5F;
            }
        }

        boolean v1 = WingType.isV1(style);
        float amp = v1 ? 2.2F : 1.7F;
        targetFlap *= amp;
        targetSway *= amp;
        targetFlutter *= amp * 1.45F;
        targetCurl *= amp;
        targetBank *= 1.4F;
        targetTwist *= amp;
        if (v1) {
            beatRate *= 1.55F;
            targetFlutter *= 1.35F;
        }
        float targetWave = (v1 ? 18.0F : 5.0F) + Math.abs(targetFlap) * 0.22F + targetFlutter * 0.55F;
        float targetBillow = (v1 ? 12.0F : 3.0F) + Math.abs(targetCurl) * 0.16F + Math.abs(targetSway) * 0.08F;

        if (state.landing > 0.04F) {
            targetFold += state.landing * 28.0F;
            targetSpread -= state.landing * 16.0F;
            targetFlap += Mth.sin(state.phase * 1.8F) * state.landing * 14.0F;
            targetDihedral -= state.landing * 8.0F;
            targetCurl += state.landing * 10.0F;
        }

        state.phase += beatRate * dt;
        state.phase2 += beatRate * (v1 ? 2.6F : 1.25F) * dt;
        float follow = 1.0F - (float) Math.pow(1.0F - snap, dt);
        state.flap += (targetFlap - state.flap) * follow;
        state.spread += (targetSpread - state.spread) * follow * 0.75F;
        state.fold += (targetFold - state.fold) * follow * 0.8F;
        state.sway += (targetSway - state.sway) * follow;
        state.stretch += (targetStretch - state.stretch) * follow * 0.55F;
        state.twist += (targetTwist - state.twist) * follow * 0.7F;
        state.pitch += (targetPitch - state.pitch) * (1.0F - (float) Math.pow(0.88, dt));
        state.bank += (targetBank - state.bank) * (1.0F - (float) Math.pow(0.86, dt));
        state.turn += (Mth.clamp(yawDelta * 1.8F, -18.0F, 18.0F) - state.turn) * (1.0F - (float) Math.pow(0.85, dt));
        state.curl += (targetCurl - state.curl) * follow * 0.65F;
        state.dihedral += (targetDihedral - state.dihedral) * follow * 0.6F;
        state.flutter += (targetFlutter - state.flutter) * follow;
        state.wave += (targetWave - state.wave) * follow;
        state.billow += (targetBillow - state.billow) * follow;
        return state;
    }

    private static float downstroke(float phase) {
        return Mth.clamp(-Mth.cos(phase), 0.0F, 1.0F);
    }

    public static void prune() {
        if (STATES.size() < 64) {
            return;
        }
        Iterator<State> iterator = STATES.values().iterator();
        int now = 0;
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            now = net.minecraft.client.Minecraft.getInstance().player.tickCount;
        }
        while (iterator.hasNext()) {
            State state = iterator.next();
            if (now - state.lastTick > 200) {
                iterator.remove();
            }
        }
    }
}
