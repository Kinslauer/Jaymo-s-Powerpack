package com.jaymoskitchen.archery;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/**
 * (0.5.23) Andy: "make mobs pin cushion like players: they keep the arrows in them until they die, with a chance for the
 * arrows to be usable if you pull them out of the corpse with a right click." Every arrow that sticks in a creature (not
 * a player: the game already draws theirs) stays in it, drawn where it went in (client {@code StuckArrowsLayer}), until
 * it dies; its corpse keeps them ({@code CorpseEntity#arrows}), and an empty hand pulls them out one a click, each one that
 * could have been picked up off the ground coming out whole {@link #SALVAGE_CHANCE} of the time.
 */
public final class StuckArrows {
	/** At most this many arrows are kept in one body (a few hundred volleys don't need drawing). */
	public static final int MAX = 24;
	/** The share of pickable arrows that come out of a corpse whole; the rest snap. */
	public static final float SALVAGE_CHANCE = 0.6F;

	/** The arrows in a creature: how many (low 8 bits), and how many of those could be picked up again (next 8 bits). Synced to all, saved. */
	public static final AttachmentType<Integer> STUCK = AttachmentRegistry.create(JaymosKitchen.id("stuck_arrows"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.all()));

	private StuckArrows() {
	}

	/** Loads the attachment at start-up. */
	public static void register() {
		java.util.Objects.requireNonNull(STUCK);
	}

	public static int total(int packed) {
		return packed & 0xFF;
	}

	public static int salvageable(int packed) {
		return packed >>> 8 & 0xFF;
	}

	public static int pack(int total, int salvageable) {
		total = Math.max(0, Math.min(MAX, total));
		return total | Math.max(0, Math.min(total, salvageable)) << 8;
	}

	/** The arrows {@code entity} carries, packed. */
	public static int of(LivingEntity entity) {
		Integer packed = entity.getAttached(STUCK);
		return packed == null ? 0 : packed;
	}

	/** Called when {@code arrow} has just hurt {@code target} (see AbstractArrowMixin): it stays in. */
	public static void onArrowHit(AbstractArrow arrow, LivingEntity target) {
		if (target instanceof Player || target.level().isClientSide() || !sticks(arrow) || arrow.getPierceLevel() > 0) {
			return;
		}
		int packed = of(target);
		boolean pickable = arrow.pickup == AbstractArrow.Pickup.ALLOWED;
		target.setAttached(STUCK, pack(total(packed) + 1, salvageable(packed) + (pickable ? 1 : 0)));
	}

	/** Arrows and spectral arrows stick; a trident doesn't. */
	private static boolean sticks(AbstractArrow arrow) {
		return !arrow.getClass().getSimpleName().contains("Trident");
	}
}
