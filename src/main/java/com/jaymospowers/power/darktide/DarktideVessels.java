package com.jaymospowers.power.darktide;

import com.jaymospowers.compat.KitchenWaterskins;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/**
 * Carry your water (Andy's Awakening pick: "Darktide, and the Unpurified Water bottles we have"). Darktide's water moves (the
 * Surf and the Geyser, {@link DarktideSurf}) are made of water: near water or in the rain they take it from there, and far
 * from water they take it from what you carry. Each vessel holds a charge of it:
 * <ul>
 * <li>a <b>flask</b>: a bottle of water (Unpurified Water), {@link #FLASK} charge, the glass bottle left;</li>
 * <li>a <b>canteen</b>: a sip of a Kitchen waterskin, {@link #CANTEEN} charge;</li>
 * <li>a <b>reservoir</b>: a water bucket, {@link #RESERVOIR} charge, the bucket left.</li>
 * </ul>
 * What a vessel gives is kept as loose water ({@link #pool}) until it is spent, so a bottle isn't emptied for a sip. With no
 * water near and none carried, the move takes its water out of Flow instead, {@link #FLOW_PER_WATER} Flow a point. In the rain
 * or standing in water Darktide's moves hit {@link DarktideDark#WET_BONUS} harder.
 */
public final class DarktideVessels {
	// ---- tuning ----
	/** Water charge a flask (bottle), a canteen sip and a reservoir (bucket) give. */
	static final float FLASK = 25.0F, CANTEEN = 15.0F, RESERVOIR = 100.0F;
	/** How near water must be to draw on it (blocks), and the Flow a point of water costs when there's none at all. */
	static final int NEAR = 4;
	static final float FLOW_PER_WATER = 0.5F;

	/** Water drawn out of a vessel and not yet spent. Not saved. */
	private static final Map<UUID, Float> POOL = new HashMap<>();

	private DarktideVessels() {
	}

	static void forget(ServerPlayer player) {
		POOL.remove(player.getUUID());
	}

	/** Water about: standing in it, the rain, or water within {@link #NEAR} blocks. */
	static boolean waterNear(ServerPlayer player) {
		if (player.isInWaterOrRain()) {
			return true;
		}
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-NEAR, -NEAR, -NEAR), at.offset(NEAR, 1, NEAR))) {
			if (level.getFluidState(pos).is(FluidTags.WATER)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds {@code amount} water for a move: from water about (free), from what's carried, or from Flow. False (with a line)
	 * if none of them has it.
	 */
	static boolean draw(ServerPlayer player, float amount, boolean near) {
		if (near || player.getAbilities().instabuild) {
			return true;
		}
		float pool = POOL.getOrDefault(player.getUUID(), 0.0F);
		while (pool < amount) {
			float got = open(player);
			if (got <= 0.0F) {
				break;
			}
			pool += got;
		}
		if (pool >= amount) {
			POOL.put(player.getUUID(), pool - amount);
			return true;
		}
		POOL.put(player.getUUID(), pool);
		// none carried: the move wrings it out of the Flow
		DarktideLive live = DarktidePower.live(player);
		float flow = (amount - pool) * FLOW_PER_WATER;
		if (live.flow + 1.0E-3F < flow) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_water").withStyle(ChatFormatting.DARK_AQUA));
			return false;
		}
		live.flow -= flow;
		POOL.put(player.getUUID(), 0.0F);
		return true;
	}

	/** Opens the next vessel carried (a canteen's sip first, then a flask, then a reservoir): the charge it gives, 0 if none. */
	private static float open(ServerPlayer player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (KitchenWaterskins.sip(stack)) {
				pour(player);
				return CANTEEN;
			}
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
				stack.shrink(1);
				ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
				if (!inv.add(bottle)) {
					player.drop(bottle, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
				pour(player);
				return FLASK;
			}
		}
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Items.WATER_BUCKET)) {
				inv.setItem(i, new ItemStack(Items.BUCKET));
				pour(player);
				return RESERVOIR;
			}
		}
		return 0.0F;
	}

	private static void pour(ServerPlayer player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.6F, 1.3F);
	}
}
