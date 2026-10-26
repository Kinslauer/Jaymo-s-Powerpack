package com.jaymospowers.alchemy.flux;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Corruption;
import com.jaymospowers.alchemy.Flux;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.core.BlockPos;

/**
 * The cure for deep corruption, which no potion reaches: a full night's sleep in a bed, in a chunk with no flux at all,
 * on a full-moon night. Waking at dawn lowers deep corruption by {@link #DEEP_CURE}, once per moon (the eight-day cycle).
 * Only "You wake lighter than you lay down." is said. The last moon cured is remembered per player (kept through death).
 * The other cures are items: {@link PurifyingSalts}, {@link PurifyingDraughtItem} and the Ward Stone.
 */
public final class Cures {
	// ---- tuning ----
	/** Deep corruption lifted by one such night. */
	public static final int DEEP_CURE = 5;
	/** Ticks in a day, and days in a moon cycle. */
	public static final long DAY = 24000L;
	public static final int MOON_DAYS = 8;

	/** The moon cycle (clock days / 8) of the last cure; -1 for never. */
	public static final AttachmentType<Integer> LAST_CURE = AttachmentRegistry.create(JaymosPowers.id("alchemy_deep_cure"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> -1));

	/** Players who went to bed this night under a full moon in a flux-free chunk, and which moon cycle it was. */
	private static final Map<UUID, Integer> TONIGHT = new HashMap<>();

	private Cures() {
	}

	static void init() {
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
				boolean full = level.environmentAttributes().getValue(EnvironmentAttributes.MOON_PHASE, BlockPos.ZERO) == MoonPhase.FULL_MOON;
				if (full && Flux.get(level, pos) == 0) {
					TONIGHT.put(player.getUUID(), (int)(level.getOverworldClockTime() / DAY / MOON_DAYS));
				} else {
					TONIGHT.remove(player.getUUID());
				}
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
				return;
			}
			Integer cycle = TONIGHT.remove(player.getUUID());
			// woke at dawn (not roused in the night), flux still none, deep corruption to lift, and not yet cured this moon
			if (cycle == null || !level.isBrightOutside() || Flux.get(level, pos) != 0 || Corruption.of(player).deep() <= 0) {
				return;
			}
			Integer last = player.getAttached(LAST_CURE);
			if (last != null && last.equals(cycle)) {
				return;
			}
			player.setAttached(LAST_CURE, cycle);
			Corruption.add(player, Corruption.Kind.DEEP, -DEEP_CURE);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.cure.deep"), true);
		});
	}
}
