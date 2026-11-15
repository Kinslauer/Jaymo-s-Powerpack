package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * The war drums of an orc or barbarian band on the march (the medieval picks' vp-patrols, Andy: "maybe drums from approaching
 * orc and barbarian warbands"): while a raid of theirs marches ({@link Raids}), a drum is beaten in the band, a bar every few
 * seconds, heard far off and louder as it comes (the sound is our own, {@code tools/gen_war_drum.py}). No words in chat: the
 * drum is the warning. The mobs about hear it too ({@link Hearing}).
 */
final class WarDrums {
	// ---- tuning ----
	/** A bar is beaten every this many ticks while the band marches. */
	static final long BAR_TICKS = 60L;
	/** How far the drum carries (blocks). */
	static final double REACH = 112.0;
	/** How far the mobs about hear it (blocks). */
	static final double HEARD = 48.0;

	static final Identifier ID = JaymosKingdoms.id("war_drum");
	static final Holder<SoundEvent> DRUM = Registry.registerForHolder(BuiltInRegistries.SOUND_EVENT, ID, SoundEvent.createVariableRangeEvent(ID));

	/** Camp id → when its band last beat a bar. */
	private static final Map<Integer, Long> LAST = new HashMap<>();

	private WarDrums() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("war drum sound: {}", ID);
	}

	/** {@code camp}'s band ({@code party}) marches: an orc or barbarian band beats its drum, from among its ranks. */
	static void march(ServerLevel level, int camp, List<Mob> party, long now) {
		String kind = Camps.kind(level.getServer(), camp);
		if (party.isEmpty() || !OrcCamps.KIND.equals(kind) && !Camps.BARBARIAN.equals(kind) || now - LAST.getOrDefault(camp, Long.MIN_VALUE / 2) < BAR_TICKS) {
			return;
		}
		LAST.put(camp, now);
		// (the drummer marches in the middle of the band)
		Mob drummer = party.get(party.size() / 2);
		Vec3 at = drummer.position().add(0.0, 1.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, DRUM, SoundSource.HOSTILE, (float)(REACH / 16.0), 0.95F + level.getRandom().nextFloat() * 0.1F);
		Hearing.noise(level, at, HEARD, null, Hearing.Kind.ACTION);
	}

	static void clear() {
		LAST.clear();
	}
}
