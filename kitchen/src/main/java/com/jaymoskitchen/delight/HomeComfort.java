package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Block;

/**
 * Home comfort (Andy, on Farmer's Delight's storage: "We can implement home comfort too"). A bedroom that is kept like
 * a home (cabinets, baskets, crates and bags of stores, rugs, tatami, canvas signs, a hearth, and the carpets, candles,
 * flower pots, lanterns and bookshelves a cafe counts too: the block tag {@code jaymoskitchen:home_comfort}) sends you
 * out warm: wake there after a night's sleep and you have {@link Delight#COMFORT} for a while, longer the more there
 * is. A bare room does nothing, and nothing is lost by sleeping in one.
 */
public final class HomeComfort {
	public static final TagKey<Block> COMFORT = TagKey.create(Registries.BLOCK, JaymosKitchen.id("home_comfort"));
	/** How far from the bed things count, sideways and up or down. */
	static final int REACH = 5, HEIGHT = 3;
	/** The fewest things that make a room a home, and the most that are counted. */
	static final int COMFORT_LEAST = 4, COMFORT_MOST = 16;
	/** Comfort (ticks) each thing counted gives: half a minute, so 2 to 8 minutes. */
	static final int TICKS_EACH = 30 * 20;

	private HomeComfort() {
	}

	public static void register() {
		EntitySleepEvents.STOP_SLEEPING.register((entity, bed) -> {
			// woke at dawn, not roused in the night
			if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || !level.isBrightOutside()) {
				return;
			}
			int comfort = count(level, bed);
			if (comfort >= COMFORT_LEAST) {
				player.addEffect(new MobEffectInstance(Delight.COMFORT, comfort * TICKS_EACH));
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.home_comfort"));
			}
		});
	}

	/** The comfortable things round a bed, up to {@link #COMFORT_MOST}. */
	static int count(ServerLevel level, BlockPos bed) {
		int comfort = 0;
		for (BlockPos pos : BlockPos.betweenClosed(bed.offset(-REACH, -HEIGHT, -REACH), bed.offset(REACH, HEIGHT, REACH))) {
			if (level.getBlockState(pos).is(COMFORT) && ++comfort >= COMFORT_MOST) {
				break;
			}
		}
		return comfort;
	}
}
