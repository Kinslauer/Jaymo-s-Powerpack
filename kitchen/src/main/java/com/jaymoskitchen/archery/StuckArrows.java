package com.jaymoskitchen.archery;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.23) Andy: "make mobs pin cushion like players: they keep the arrows in them until they die, with a chance for the
 * arrows to be usable if you pull them out of the corpse with a right click." Every arrow that sticks in a creature (not
 * a player: the game already draws theirs) stays in it, drawn where it went in (client {@code StuckArrowsLayer}), until
 * it dies; its corpse keeps them ({@code CorpseEntity#arrows}), and an empty hand pulls them out one a click, each one that
 * could have been picked up off the ground coming out whole {@link #SALVAGE_CHANCE} of the time.
 * <p>
 * (0.5.23) Andy: "Tinker arrows can be made with stronger shafts that refund you more of the time." Whether an arrow will
 * come out whole is rolled when it goes in: Jaymo's Powers tags a Tinkering arrow {@link #SALVAGE_TAG}{@code <percent>} from
 * its shaft (wood 60, crimson/warped 65, bone 80, blaze rod 90), any other arrow uses {@link #SALVAGE_CHANCE}. The ones that
 * will come out whole are kept as themselves ({@link #ITEMS}), so a Tinkering or tipped arrow comes back as it went in.
 */
public final class StuckArrows {
	/** At most this many arrows are kept in one body (a few hundred volleys don't need drawing). */
	public static final int MAX = 24;
	/** The share of pickable arrows (other than Tinkering ones) that come out of a corpse whole; the rest snap. */
	public static final float SALVAGE_CHANCE = 0.6F;

	/** The arrows in a creature: how many (low 8 bits), and how many of those could be picked up again (next 8 bits). Synced to all, saved. */
	public static final AttachmentType<Integer> STUCK = AttachmentRegistry.create(JaymosKitchen.id("stuck_arrows"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.all()));

	/** Jaymo's Powers' tag on a Tinkering arrow: its shaft's chance (0-100) to come out of a corpse whole. */
	public static final String SALVAGE_TAG = "jaymoskitchen.salvage.";

	/** The arrows in a creature that will come out whole, as the items they come back as. Saved, not synced. */
	public static final AttachmentType<List<ItemStack>> ITEMS = AttachmentRegistry.create(JaymosKitchen.id("stuck_arrow_items"),
		builder -> builder.persistent(ItemStack.CODEC.listOf()));

	private StuckArrows() {
	}

	/** Loads the attachment at start-up. */
	public static void register() {
		java.util.Objects.requireNonNull(STUCK);
		java.util.Objects.requireNonNull(ITEMS);
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
		if (total(packed) >= MAX) {
			return;
		}
		boolean whole = arrow.pickup == AbstractArrow.Pickup.ALLOWED && target.getRandom().nextFloat() < chance(arrow);
		if (whole) {
			List<ItemStack> items = new ArrayList<>(items(target));
			items.add(arrow.getPickupItemStackOrigin().copyWithCount(1));
			target.setAttached(ITEMS, items);
		}
		target.setAttached(STUCK, pack(total(packed) + 1, salvageable(packed) + (whole ? 1 : 0)));
	}

	/** The arrows in {@code entity} that will come out whole. */
	public static List<ItemStack> items(LivingEntity entity) {
		List<ItemStack> items = entity.getAttached(ITEMS);
		return items == null ? List.of() : items;
	}

	/** This arrow's chance to come out of a body whole: its Tinkering shaft's, or {@link #SALVAGE_CHANCE}. */
	private static float chance(AbstractArrow arrow) {
		for (String tag : arrow.entityTags()) {
			if (tag.startsWith(SALVAGE_TAG)) {
				try {
					return Integer.parseInt(tag.substring(SALVAGE_TAG.length())) / 100.0F;
				} catch (NumberFormatException ignored) {
					// fall through to the default
				}
			}
		}
		return SALVAGE_CHANCE;
	}

	/** Arrows and spectral arrows stick; a trident doesn't. */
	private static boolean sticks(AbstractArrow arrow) {
		return !arrow.getClass().getSimpleName().contains("Trident");
	}
}
