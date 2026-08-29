package com.decorativewings.attachment;

import com.decorativewings.DecorativeWingsMod;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, DecorativeWingsMod.MOD_ID);

    public static final Supplier<AttachmentType<String>> WING_STYLE = REGISTER.register("wing_style",
            () -> AttachmentType.builder(() -> "")
                    .serialize(Codec.STRING)
                    .sync(ByteBufCodecs.STRING_UTF8)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
