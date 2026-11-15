package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;

/**
 * Settlements remember the great disasters (nd-tiers): one of {@link com.jaymoskingdoms.disasters.Magnitude} great or
 * worse that strikes a settlement ({@link DisasterRelief#struck}) is kept with the world, and for long after its folk
 * still speak of it to whoever is near, in their own words: the year the mountain burned, the night the sea came in.
 */
public final class DisasterMemory {
	/** Remembered from this magnitude (great); each settlement keeps its last few. */
	static final int GREAT = 4, KEPT = 3;
	/** Folk speak of it this long after (days), how often it's tried (ticks), how near (blocks), and how often the same
	 * player hears it (ticks). */
	static final long FORGOTTEN_AFTER = 120L;
	static final int TRY_EVERY = 400;
	static final double SEAT_RANGE = 160.0, SPEAKER_NEAR = 7.0;
	static final long AGAIN = 20L * 60 * 8;
	/** The lines there are for each kind. */
	static final int LINES = 2;

	record Memory(int faction, String kind, int magnitude, long day) {
		static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("faction").forGetter(Memory::faction),
			Codec.STRING.fieldOf("kind").forGetter(Memory::kind),
			Codec.INT.fieldOf("magnitude").forGetter(Memory::magnitude),
			Codec.LONG.fieldOf("day").forGetter(Memory::day)).apply(i, Memory::new));
	}

	static final AttachmentType<List<Memory>> REMEMBERED = AttachmentRegistry.create(JaymosKingdoms.id("disaster_memory"),
		b -> b.persistent(Memory.CODEC.listOf()));
	/** When each player last heard of one (not saved). */
	private static final Map<UUID, Long> HEARD = new HashMap<>();

	private DisasterMemory() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(DisasterMemory::tick);
	}

	private static long day(ServerLevel level) {
		return Math.floorDiv(level.getOverworldClockTime(), 24000L);
	}

	/** (DisasterRelief) A disaster of {@code kind} and {@code magnitude} has struck {@code f}: remembered if it was great. */
	static void struck(ServerLevel level, Faction f, String kind, int magnitude) {
		if (magnitude < GREAT) {
			return;
		}
		List<Memory> all = new ArrayList<>(level.getAttachedOrElse(REMEMBERED, List.of()));
		all.add(new Memory(f.id(), kind, magnitude, day(level)));
		List<Memory> mine = all.stream().filter(m -> m.faction() == f.id()).toList();
		if (mine.size() > KEPT) {
			all.remove(mine.get(0));
		}
		level.setAttached(REMEMBERED, all);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % TRY_EVERY != 0) {
			return;
		}
		List<Memory> all = level.getAttachedOrElse(REMEMBERED, List.of());
		if (all.isEmpty()) {
			return;
		}
		long today = day(level);
		HEARD.values().removeIf(t -> now - t > AGAIN * 4);
		for (ServerPlayer player : level.players()) {
			Long last = HEARD.get(player.getUUID());
			if (player.isSpectator() || last != null && now - last < AGAIN) {
				continue;
			}
			Memory memory = null;
			for (Memory m : all) {
				Faction f = Factions.get(server, m.faction());
				if (f != null && !f.fallen() && today - m.day() <= FORGOTTEN_AFTER && f.seat().closerThan(player.blockPosition(), SEAT_RANGE)
					&& (memory == null || m.magnitude() > memory.magnitude())) {
					memory = m;
				}
			}
			if (memory == null) {
				continue;
			}
			AbstractVillager speaker = null;
			for (AbstractVillager v : level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(SPEAKER_NEAR))) {
				if (v.isAlive() && !v.isSleeping() && v.hasLineOfSight(player)) {
					speaker = v;
					break;
				}
			}
			if (speaker == null) {
				continue;
			}
			HEARD.put(player.getUUID(), now);
			long ago = Math.max(1L, today - memory.day());
			Component words = Component.translatable("memory.jaymoskingdoms." + memory.kind() + "." + level.getRandom().nextInt(LINES), ago);
			Speech.say(level, speaker, words, Speech.WHITE, SPEAKER_NEAR + 2.0);
		}
	}
}
