package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.arsenal.Summoned;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.phys.Vec3;

/**
 * The blade of dark water ({@code jaymospowers:tide_blade}): a Darktide user who raises the guard with both hands empty
 * draws a katana out of the water in them, so the whole kit (the katana guard, the riposte, Crescent, Stillwater, the
 * parries that ascend) works with no blade of their own and without WeaponMod. It hits like an iron sword, counts as a
 * katana everywhere Darktide asks ({@link DarktideWeapons#isKatana}), can't be dropped or put away (it carries Arsenal's
 * {@link Summoned} mark), and runs back to water {@link #IDLE_TICKS} after it was last used, or as soon as it leaves the
 * hand.
 */
final class DarktideBlade {
	/** How long the blade lasts with nothing done with it (ticks). */
	static final int IDLE_TICKS = 300;
	/** An iron sword's blow and speed. */
	static final float DAMAGE = 3.0F, SPEED = -2.4F;
	/** Marks the stacks as this blade's (a {@link Summoned} batch of its own, beside Tide Arms'). */
	static final int BATCH = 0x7D1DF;
	static final int COLOR = 0x10285A;

	static Item BLADE;

	private DarktideBlade() {
	}

	static void registerItems() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("tide_blade"));
		BLADE = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().sword(ToolMaterial.IRON, DAMAGE, SPEED).setId(key)));
	}

	/** Both sides. */
	static boolean is(ItemStack stack) {
		return BLADE != null && stack.is(BLADE);
	}

	/** Both hands empty: the blade forms in the main hand. Returns whether it did. */
	static boolean draw(ServerPlayer player, DarktideLive live) {
		if (!player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty() || !player.getItemInHand(InteractionHand.OFF_HAND).isEmpty()) {
			return false;
		}
		ServerLevel level = player.level();
		ItemStack blade = new ItemStack(BLADE);
		blade.set(Summoned.TYPE, new Summoned(player.getUUID(), Long.MAX_VALUE, BATCH));
		player.setItemInHand(InteractionHand.MAIN_HAND, blade);
		live.bladeUntil = level.getGameTime() + IDLE_TICKS;
		Vec3 hand = player.position().add(0.0, 1.0, 0.0).add(Vec3.directionFromRotation(0.0F, player.getYRot() + 40.0F).scale(0.45));
		level.sendParticles(MagicFx.miasma(COLOR, 1.4F), hand.x, hand.y, hand.z, 14, 0.15, 0.35, 0.15, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_FILL, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	/** Kept while it's worked with (guarding, charging, a stance, a swing); gone once idle, or out of the hand. */
	static void tick(ServerPlayer player, DarktideLive live) {
		if (live.bladeUntil == 0L) {
			return;
		}
		long now = player.level().getGameTime();
		if (!is(player.getItemInHand(InteractionHand.MAIN_HAND))) {
			dismiss(player, true);
			return;
		}
		if (live.guarding || live.strikeStart >= 0L || live.gedan != null || live.maelstrom != null || player.getAttackStrengthScale(0.0F) < 1.0F) {
			live.bladeUntil = now + IDLE_TICKS;
		} else if (now >= live.bladeUntil) {
			dismiss(player, true);
		}
	}

	/** The blade runs back to water (every copy of it this player carries). */
	static void dismiss(ServerPlayer player, boolean effects) {
		DarktideLive live = DarktidePower.liveIfAny(player);
		if (live != null) {
			live.bladeUntil = 0L;
		}
		boolean any = false;
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (is(inventory.getItem(slot))) {
				inventory.setItem(slot, ItemStack.EMPTY);
				any = true;
			}
		}
		if (is(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			any = true;
		}
		if (any && effects) {
			ServerLevel level = player.level();
			Vec3 hand = player.position().add(0.0, 0.9, 0.0);
			level.sendParticles(MagicFx.miasma(COLOR, 1.2F), hand.x, hand.y, hand.z, 10, 0.2, 0.3, 0.2, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.5F, 1.5F);
		}
	}
}
