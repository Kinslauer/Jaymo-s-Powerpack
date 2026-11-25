package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-24): "zombies should also bend over and down to gnaw on bodies in a new gruesome animation, chunks flying
 * everywhere and the body they are eating reacting to the movement". What a feeding mob is doing at a body, told to
 * everyone watching so they draw it ({@code GnawPose}, client): down over the body, each bite (from the lunge in to the
 * rip back), and chewing a part it tore off. Set by the scavengers' gnawing ({@link Scavengers}) and by Jaymo's Powers'
 * eaters of the downed ({@link #feed}, by name). Never saved: a mob loaded from disk is standing.
 */
public final class Gnawing {
	/** {@link #feed}: up off the body; down over it; a bite starting now; chewing a part it tore off. */
	public static final int UP = 0, DOWN = 1, BITE = 2, CHEW = 3;

	/** Down over {@code body} (its entity id) since game time {@code since}; its last bite began at {@code bite} (-1: none yet); chewing. */
	public record Gnaw(int body, long since, long bite, boolean chewing) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Gnaw> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Gnaw::body, ByteBufCodecs.VAR_LONG, Gnaw::since, ByteBufCodecs.VAR_LONG, Gnaw::bite,
			ByteBufCodecs.BOOL, Gnaw::chewing, Gnaw::new);
	}

	public static final AttachmentType<Gnaw> GNAWING = AttachmentRegistry.create(JaymosKitchen.id("gnawing"),
		builder -> builder.syncWith(Gnaw.CODEC, AttachmentSyncPredicate.all()));

	private Gnawing() {
	}

	/** Loads the class (and its attachment) at start-up, on both sides. */
	public static void register() {
	}

	/**
	 * {@code mob} at {@code body} (a corpse, or one of the downed): {@link #UP}, {@link #DOWN}, {@link #BITE} or
	 * {@link #CHEW}. Sent on only when it changes. Public for Jaymo's Powers (by name).
	 */
	public static void feed(Mob mob, @Nullable Entity body, int what) {
		Gnaw now = mob.getAttached(GNAWING);
		if (what == UP || body == null) {
			if (now != null) {
				mob.removeAttached(GNAWING);
			}
			return;
		}
		long time = mob.level().getGameTime();
		boolean same = now != null && now.body() == body.getId();
		long since = same ? now.since() : time;
		long bite = same ? now.bite() : -1L;
		Gnaw next = switch (what) {
			case BITE -> new Gnaw(body.getId(), since, time, false);
			case CHEW -> new Gnaw(body.getId(), since, bite, true);
			default -> new Gnaw(body.getId(), since, bite, false);
		};
		if (!next.equals(now)) {
			mob.setAttached(GNAWING, next);
		}
	}
}
