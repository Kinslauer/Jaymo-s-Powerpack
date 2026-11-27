package com.jaymospowers.alchemy.toxic;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Hunter's eyes (Hunter's Road, patch 6, "H1"): after your first monster brew, bright slit pupils on any skin, for good.
 * Earned by {@link #earn} (a held dose in {@link Toxicity#dose} calls it, so a monster brew need not), kept through death,
 * synced to everyone so every client can draw them ({@code ToxicLayer}).
 */
public final class HunterEyes {
	public static final AttachmentType<Boolean> EYES = AttachmentRegistry.create(JaymosPowers.id("hunter_eyes"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().initializer(() -> Boolean.FALSE)
			.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private HunterEyes() {
	}

	static void init() {
	}

	/** Whether {@code player} has the eyes (both sides). */
	public static boolean has(Player player) {
		return Boolean.TRUE.equals(player.getAttached(EYES));
	}

	/** Gives {@code player} the eyes for good (the first monster brew). */
	public static void earn(ServerPlayer player) {
		if (!has(player)) {
			player.setAttached(EYES, Boolean.TRUE);
		}
	}
}
