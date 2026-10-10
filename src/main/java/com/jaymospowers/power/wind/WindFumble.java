package com.jaymospowers.power.wind;

import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Fumbling: a wind that hasn't yet mastered {@link #MASTERED} abilities can't keep a grip on what it holds. Each Wind
 * ability used has a {@link #CAST_CHANCE} chance, and each second of flight a {@link #FLIGHT_CHANCE_PER_SECOND} one, of
 * the main-hand item slipping out: it's thrown ahead as an item (nothing is destroyed). Never in creative.
 */
final class WindFumble {
	/** Abilities mastered before the grip holds. */
	static final int MASTERED = 3;
	/** Chance per ability used, and per second of flight. */
	static final float CAST_CHANCE = 0.08F;
	static final float FLIGHT_CHANCE_PER_SECOND = 0.02F;
	/** How often flight rolls (ticks; once a second). */
	static final int FLIGHT_ROLL_TICKS = 20;
	/** The slipped item can't be caught again straight away (ticks). */
	static final int PICKUP_DELAY_TICKS = 30;

	private WindFumble() {
	}

	/** A Wind ability was used. */
	static void onCast(ServerPlayer player) {
		if (canFumble(player) && player.getRandom().nextFloat() < CAST_CHANCE) {
			slip(player);
		}
	}

	/** Every tick in flight. */
	static void flightTick(ServerPlayer player) {
		if (player.tickCount % FLIGHT_ROLL_TICKS == 0 && canFumble(player) && player.getRandom().nextFloat() < FLIGHT_CHANCE_PER_SECOND) {
			slip(player);
		}
	}

	private static boolean canFumble(ServerPlayer player) {
		return !player.isCreative() && !player.isSpectator() && player.isAlive() && Powers.has(player, WindPower.INSTANCE)
			&& Mastery.unlocked(player) < MASTERED && !player.getMainHandItem().isEmpty();
	}

	private static void slip(ServerPlayer player) {
		ItemStack held = player.getMainHandItem().copy();
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		// tossed ahead, the way a dropped item goes (an item entity; it never just vanishes)
		ItemEntity item = player.drop(held, false, Prediction.SERVER_ONLY);
		if (item == null) {
			// (dropping refused): back in the hand, so nothing is lost
			player.setItemInHand(InteractionHand.MAIN_HAND, held);
			return;
		}
		item.setPickUpDelay(PICKUP_DELAY_TICKS);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_IDLE_AIR, SoundSource.PLAYERS, 0.6F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.wind.fumble", held.getHoverName()).withStyle(ChatFormatting.GRAY));
	}
}
