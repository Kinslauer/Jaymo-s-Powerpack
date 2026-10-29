package com.jaymoskingdoms;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.326) A villager drawn as a body of its own instead of MCA's (the client's {@code BbBodies}, from a Blockbench
 * model): the orc warchief as nyxaris' Chieftain. On the villager, synced: the body, and what it last did and when
 * ({@code kind}, or {@code kind|act|game time}), so its slam and its roar play as they happen. Set again each step, so
 * it needs no saving.
 */
public final class Bodies {
	public static final AttachmentType<String> BODY = AttachmentRegistry.create(JaymosKingdoms.id("bb_body"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));
	/** The bodies there are. */
	public static final String WARCHIEF = "warchief";

	private Bodies() {
	}

	/** {@code e} is drawn as {@code kind} (left as it is if it already is). */
	static void wear(Entity e, String kind) {
		if (!kind.equals(kind(e))) {
			e.setAttached(BODY, kind);
		}
	}

	/** {@code e} does {@code act} now (one of its body's animations), if it has a body of its own. */
	static void act(ServerLevel level, Entity e, String act) {
		String kind = kind(e);
		if (kind != null) {
			e.setAttached(BODY, kind + "|" + act + "|" + level.getGameTime());
		}
	}

	public static @Nullable String kind(Entity e) {
		String body = e.getAttached(BODY);
		if (body == null) {
			return null;
		}
		int bar = body.indexOf('|');
		return bar < 0 ? body : body.substring(0, bar);
	}
}
