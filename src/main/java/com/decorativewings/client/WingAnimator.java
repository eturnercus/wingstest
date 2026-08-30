
package com.decorativewings.client;

import com.decorativewings.WingType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

public final class WingAnimator {
    private static final Map<Integer, State> STATES = new HashMap<>();

    public static final class State {
        public float flap, spread, fold, sway, stretch, twist, pitch, bank, turn;
        public float takeoff, landing, curl, dihedral, flutter, wave, billow;
        public float phase, phase2, lastYaw, lastAge, baseYaw;
        public boolean wasFlying, initialized;
        public int lastTick;
    }

    private WingAnimator() {}

    public static State sample(Player player, float limbSwing, float limbSwingAmount, float ageInTicks,
                               float netHeadYaw, float headPitch, String style) {
        State state = STATES.computeIfAbsent(player.getId(), id -> new State());

        if (player.tickCount + 5 < state.lastTick) {
            resetState(state);
        }
        state.lastTick = player.tickCount;

        float dt = ageInTicks - state.lastAge;
        if (!state.initialized || dt < 0.0F || dt > 8.0F) dt = 1.0F;
        state.lastAge = ageInTicks;

        Vec3 motion = player.getDeltaMovement();
        float yawRad = player.getYRot() * Mth.DEG_TO_RAD;
        float localStrafe = (float) (motion.x * Mth.cos(yawRad) - motion.z * Mth.sin(yawRad));
        float horiz = (float) Math.hypot(motion.x, motion.z);
        float vert = (float) motion.y;
        float yawDelta = Mth.clamp(Mth.wrapDegrees(player.getYRot() - state.lastYaw), -12.0F, 12.0F);
        state.lastYaw = player.getYRot();

        boolean flying = player.getAbilities().flying;

        if (!state.initialized) {
            state.lastYaw = player.getYRot();
            state.initialized = true;
        }
        boolean elytra = player.isFallFlying() || player.getPose() == Pose.FALL_FLYING;
        boolean onGround = player.onGround();
        boolean airborne = !onGround && !flying && !elytra && !player.isInWater() && !player.isPassenger();

        // Takeoff/Landing logic
        if (flying && !state.wasFlying) state.takeoff = 1.0F;
        if (!flying && state.wasFlying) state.landing = 1.0F;
        state.wasFlying = flying;
        float decay = (float) Math.pow(0.88, dt);
        state.takeoff = Math.max(0.0F, state.takeoff * decay - 0.008F * dt);
        state.landing = Math.max(0.0F, state.landing * (float) Math.pow(0.86, dt) - 0.012F * dt);

        // State Determination
        String stateKey = "default";
        if (player.isSleeping() || player.getPose() == Pose.SLEEPING) stateKey = "sleeping";
        else if (player.isAutoSpinAttack()) stateKey = "spin";
        else if (elytra) stateKey = "elytra";
        else if (flying) stateKey = "flying";
        else if (player.onClimbable() && !onGround) stateKey = "climb";
        else if (player.getPose() == Pose.SWIMMING) stateKey = "swimming";
        else if (player.isInWater() && !onGround) stateKey = "water";
        else if (player.isPassenger()) stateKey = "passenger";
        else if (player.isCrouching()) stateKey = "crouch";
        else if (airborne && vert > 0.02F) stateKey = "jump";
        else if (airborne) stateKey = "fall";
        else if (player.isSprinting() && onGround) stateKey = "sprint";
        else if (limbSwingAmount > 0.08F && onGround) stateKey = "walk";

        AnimationConfig.StateParams p = AnimationParser.getParams(style, stateKey);

        float beatRate = p.beatRate;
        float snap = p.snap;

        // Dynamic calculation of targets based on config
        float targetFlap = p.flapBase + Mth.sin(state.phase + p.phaseOffset) * p.flapAmp;
        float targetSpread = p.spread;
        float targetFold = p.fold;
        float targetSway = p.swayBase + Mth.sin(state.phase) * p.swayAmp + localStrafe * 10.0f;
        float targetStretch = p.stretch;
        float targetTwist = p.twist;
        float targetPitch = p.pitch + (stateKey.equals("flying") ? vert * 15.0f : 0);
        float targetBank = p.bank + (localStrafe * 20.0f);
        float targetCurl = p.curl;
        float targetDihedral = p.dihedral;
        float targetFlutter = p.flutter;

        // Use previously unused variables for more dynamic motion
        if (stateKey.equals("walk") || stateKey.equals("sprint")) {
            targetSway += Mth.sin(limbSwing * 0.5F) * limbSwingAmount * 5.0F;
            targetFlap += Mth.cos(limbSwing * 0.5F) * limbSwingAmount * 2.0F;
        }

        targetPitch += headPitch * 0.1F;
        targetBank += (netHeadYaw - player.getYRot()) * 0.05F;
        targetBank += yawDelta * 2.0F;

        // Apply V1 multiplier if applicable
        if (WingType.isV1(style)) {
            float amp = 2.2F;
            targetFlap *= amp;
            targetSway *= amp;
            beatRate *= 1.5f;
        }

        // Landing override
        if (state.landing > 0.04F) {
            targetFold += state.landing * 20.0F;
            targetSpread -= state.landing * 10.0F;
        }

        // Update Phase
        state.phase += beatRate * dt;
        state.phase2 += beatRate * 1.25f * dt;

        // Smooth Interpolation (Snap)
        float follow = 1.0F - (float) Math.pow(1.0F - snap, dt);
        state.flap += (targetFlap - state.flap) * follow;
        state.spread += (targetSpread - state.spread) * follow * 0.75F;
        state.fold += (targetFold - state.fold) * follow * 0.8F;
        state.sway += (targetSway - state.sway) * follow;
        state.stretch += (targetStretch - state.stretch) * follow * 0.55F;
        state.twist += (targetTwist - state.twist) * follow * 0.7F;
        state.pitch += (targetPitch - state.pitch) * (1.0F - (float) Math.pow(0.88, dt));
        state.bank += (targetBank - state.bank) * (1.0F - (float) Math.pow(0.86, dt));
        state.curl += (targetCurl - state.curl) * follow * 0.65F;
        state.dihedral += (targetDihedral - state.dihedral) * follow * 0.6F;
        state.flutter += (targetFlutter - state.flutter) * follow;

        // Calculated waves
        state.wave = 5.0F + Math.abs(targetFlap) * 0.2F + targetFlutter * 0.5F;
        state.billow = 3.0F + Math.abs(targetCurl) * 0.1F;

        return state;
    }

    private static void resetState(State state) {
        state.flap = 0; state.spread = 13f; state.fold = 14f; state.sway = 0;
        state.stretch = 1f; state.twist = 0; state.pitch = 0; state.bank = 0;
        state.dihedral = 6f; state.initialized = false;
    }

    public static void prune() {
        if (STATES.size() < 64) return;
        int now = (net.minecraft.client.Minecraft.getInstance().player != null) ?
                net.minecraft.client.Minecraft.getInstance().player.tickCount : 0;
        STATES.entrySet().removeIf(entry -> now - entry.getValue().lastTick > 200);
    }
}