package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import com.jaymospowers.power.arsenal.Summoned;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * (Andy 2026-10-25) Drop (Q) the flintlock in your hand and you reach for the next one you carry, the way the Void
 * Armory's pair come back loaded ({@link ArsenalFlintlock#reloadOnDrop}); only these are your own: the dropped one stays
 * on the ground and nothing comes back once you've run out. Any gun named a flintlock counts (WeaponMod's, ours), the
 * Void Armory's summoned ones never (they have their own). A loaded one is taken first.
 * <pre>
 *   0 ─ REACH_TICKS ─ DONE_TICKS   (ticks since the drop)
 *   hand to the belt   gun drawn up into the hand
 * </pre>
 * The gun goes into the emptied slot at {@link #REACH_TICKS}; everyone sees the arm through the synced {@link #DRAW}
 * ({@code ArsenalArmPose}).
 */
public final class FlintlockDraw {
	/** Ticks for the hand to reach the belt (the gun is in it then), and the whole draw (back up to the hold). */
	public static final int REACH_TICKS = 6;
	public static final int DONE_TICKS = 12;

	/** A draw that began at game time {@code start} into hotbar slot {@code slot}; {@code drawn} once the gun is in it. */
	public record Draw(long start, int slot, boolean drawn) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Draw> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Draw::start,
			ByteBufCodecs.VAR_INT, Draw::slot,
			ByteBufCodecs.BOOL, Draw::drawn,
			Draw::new);
	}

	public static final AttachmentType<Draw> DRAW = AttachmentRegistry.create(JaymosPowers.id("flintlock_draw"),
		builder -> builder.syncWith(Draw.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private FlintlockDraw() {
	}

	public static void init() {
		ServerTickEvents.END_LEVEL_TICK.register(FlintlockDraw::tick);
	}

	/** One of your own flintlocks: any gun named a flintlock, but not a summoned one. */
	public static boolean isFlintlock(ItemStack stack) {
		return !stack.isEmpty() && !Summoned.is(stack)
			&& BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("flintlock");
	}

	/** The player just dropped (Q) {@code dropped} from their main hand: if that emptied it, reach for a spare. */
	public static void dropped(ServerPlayer player, ItemStack dropped) {
		if (!isFlintlock(dropped) || !player.getMainHandItem().isEmpty() || !player.isAlive() || player.isSpectator()
			|| player.getAttached(DRAW) != null || spare(player, player.getInventory().getSelectedSlot()) < 0) {
			return;
		}
		ServerLevel level = player.level();
		player.setAttached(DRAW, new Draw(level.getGameTime(), player.getInventory().getSelectedSlot(), false));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	/** The inventory slot of the spare to draw (a loaded one first), or -1. Never {@code into}, the slot drawn into. */
	private static int spare(ServerPlayer player, int into) {
		Inventory inventory = player.getInventory();
		int found = -1;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (i == into || !isFlintlock(stack)) {
				continue;
			}
			if (!Boolean.TRUE.equals(stack.get(ArsenalFlintlock.SPENT))) {
				return i;
			}
			if (found < 0) {
				found = i;
			}
		}
		return found;
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			Draw draw = player.getAttached(DRAW);
			if (draw == null) {
				continue;
			}
			long t = now - draw.start();
			if (t >= DONE_TICKS || t < 0 || !player.isAlive()) {
				player.removeAttached(DRAW);
			} else if (!draw.drawn() && t >= REACH_TICKS) {
				Inventory inventory = player.getInventory();
				int from = spare(player, draw.slot());
				// the hand was filled meanwhile (picked something up, swapped), or the spare's gone: no draw
				if (from < 0 || !inventory.getItem(draw.slot()).isEmpty()) {
					player.removeAttached(DRAW);
					continue;
				}
				inventory.setItem(draw.slot(), inventory.getItem(from));
				inventory.setItem(from, ItemStack.EMPTY);
				player.setAttached(DRAW, new Draw(draw.start(), draw.slot(), true));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
			}
		}
	}
}
