package com.jaymoskitchen.season;

import com.jaymoskitchen.JaymosKitchen;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * (0.5.6) Winter bites (Andy: "very harsh. yes."): out in the open in a temperate land in winter you burn through food,
 * unless you're under a roof or warming yourself by a fire or lava. Another mod can make someone proof against it
 * ({@link #setColdProof}: Kingdoms' Frostborn). Each worn piece tagged {@link #WARM_CLOTHING} (Kingdoms' wool clothes)
 * takes {@link #WARM_SHARE} of it off.
 */
public final class WinterCold {
	/** Extra exhaustion a second out in the winter cold (4 is a hunger point): about 7 points over a 20-minute day. */
	static final float EXHAUSTION = 0.025F;
	/** Worn clothing that keeps the winter off, a share a piece. */
	public static final TagKey<Item> WARM_CLOTHING = TagKey.create(Registries.ITEM, JaymosKitchen.id("warm_clothing"));
	static final float WARM_SHARE = 0.2F;
	/** How far a fire or lava warms you. */
	private static final int WARMTH = 3;
	private static Predicate<Player> coldProof = player -> false;

	private WinterCold() {
	}

	/** For other mods (by reflection): who the winter cold doesn't touch. */
	public static void setColdProof(Predicate<Player> proof) {
		coldProof = proof;
	}

	/** Called each second on the server for each player while it's winter. */
	static void tick(ServerPlayer player) {
		if (player.isCreative() || player.isSpectator() || coldProof.test(player)) {
			return;
		}
		BlockPos at = player.blockPosition();
		if (!Seasons.temperate(player.level().getBiome(at).value().getBaseTemperature()) || !player.level().canSeeSky(at)
			|| warmed(player, at)) {
			return;
		}
		float bare = 1.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (player.getItemBySlot(slot).is(WARM_CLOTHING)) {
				bare -= WARM_SHARE;
			}
		}
		if (bare > 0.0F) {
			player.causeFoodExhaustion(EXHAUSTION * bare);
		}
	}

	private static boolean warmed(ServerPlayer player, BlockPos at) {
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-WARMTH, -1, -WARMTH), at.offset(WARMTH, 1, WARMTH))) {
			BlockState state = player.level().getBlockState(pos);
			if (CampfireBlock.isLitCampfire(state) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)) {
				return true;
			}
		}
		return false;
	}
}
