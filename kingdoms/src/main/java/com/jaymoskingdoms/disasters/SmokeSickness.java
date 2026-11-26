package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.Temperaments;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;

/**
 * Breathing a big fire's smoke (Andy, 2026-11-26: "anyone within the smoke should get nausea after a long time, and can
 * stay low to diminish it indoors"). The smoke hangs low round every large flame the server knows of ({@link FireSmog}),
 * reaching as far as the client draws it ({@code FireSmoke}'s low bank, carried downwind). Each round anyone breathing it
 * (players, and the humanoids near them) takes in a dose, thicker near the middle and over a bigger blaze; after about
 * {@link #SICK_AT} seconds of thick smoke they turn sick (nausea, topped up while they stay in it). Out of it, or in thin
 * enough smoke, the chest clears a little faster than it filled.
 * <ul>
 * <li>Indoors the smoke is thinner ({@link #INDOORS}); staying low there (crouched, crawling, prone or lying) keeps under it
 * ({@link #INDOORS_LOW}), so the chest clears even with the house burning next door. Out in the open the smoke settles
 * low and staying low does not help.</li>
 * <li>Under water no smoke is breathed. Creative and spectator players are left alone.</li>
 * </ul>
 * No chat line, no hint: the screen swimming is the sign. Kept in memory only, so a restart clears everyone's chest.
 */
public final class SmokeSickness {
	/** Seconds a round lasts ({@link FireSmog#EVERY} ticks). */
	static final float ROUND = FireSmog.EVERY / 20.0F;
	/** How far the smoke reaches round a large flame (blocks, from its middle), how far downwind its middle sits, and how
	 * high over the highest flame and how far under it (the ground below a burning canopy) it hangs. */
	static final double REACH = 26.0, DOWNWIND = 12.0, ABOVE = 16.0, BELOW = 24.0;
	/** Fires round a large flame that make its smoke as thick as it gets; at the edge it is half as thick as in the middle. */
	static final float THICK = 40.0F, EDGE = 0.5F;
	/** Smoke thinner than this lets the chest clear, and how much faster it clears than it fills. */
	static final float CLEAN = 0.15F, CLEARS = 1.5F;
	/** Seconds of thick smoke before the nausea sets in (Andy: "after a long time"), and the most kept past that (so
	 * someone who stayed in it a long while is sick a little while after stepping out). */
	static final float SICK_AT = 150.0F, MOST = SICK_AT + 60.0F;
	/** Indoors the smoke is this much of itself, and only this much for one who stays low. */
	static final float INDOORS = 0.6F, INDOORS_LOW = 0.1F;
	/** How long the nausea lasts when given (ticks), and topped up once it has less than this left. */
	static final int NAUSEA_TICKS = 200, TOP_UP = 100;
	/** How far round each player humanoids are looked at (blocks). */
	static final double NPC_RANGE = 64.0;

	/** Seconds of thick smoke breathed, by who. */
	private static final Map<UUID, Float> BREATHED = new HashMap<>();
	private static final Set<UUID> SEEN = new HashSet<>();

	private SmokeSickness() {
	}

	static void clear() {
		BREATHED.clear();
		SEEN.clear();
	}

	/** (each round, every world) Everyone in {@code level} near a large flame breathes its smoke. */
	static void breathe(ServerLevel level, List<FireSmog.Source> smoke) {
		if (smoke.isEmpty()) {
			return;
		}
		double ca = Math.cos(Storms.windAngle()), sa = Math.sin(Storms.windAngle());
		for (ServerPlayer player : level.players()) {
			if (!player.isCreative() && !player.isSpectator()) {
				breathe(level, player, smoke, ca, sa);
			}
			AABB round = player.getBoundingBox().inflate(NPC_RANGE);
			for (LivingEntity npc : level.getEntitiesOfClass(LivingEntity.class, round, e -> e.isAlive() && Temperaments.humanoid(e))) {
				breathe(level, npc, smoke, ca, sa);
			}
		}
	}

	/** (each round, after every world) Whoever breathed no smoke this round clears a little. */
	static void settle() {
		for (Iterator<Map.Entry<UUID, Float>> it = BREATHED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Float> e = it.next();
			if (SEEN.contains(e.getKey())) {
				continue;
			}
			float now = e.getValue() - ROUND * CLEARS;
			if (now <= 0.0F) {
				it.remove();
			} else {
				e.setValue(now);
			}
		}
		SEEN.clear();
	}

	private static void breathe(ServerLevel level, LivingEntity who, List<FireSmog.Source> smoke, double ca, double sa) {
		if (!SEEN.add(who.getUUID())) {
			return;
		}
		float dose = dose(level, who, smoke, ca, sa);
		float had = BREATHED.getOrDefault(who.getUUID(), 0.0F);
		if (dose <= 0.0F && had <= 0.0F) {
			return;
		}
		float now = dose > CLEAN ? had + ROUND * dose : had - ROUND * CLEARS;
		now = Mth.clamp(now, 0.0F, MOST);
		if (now <= 0.0F) {
			BREATHED.remove(who.getUUID());
			return;
		}
		BREATHED.put(who.getUUID(), now);
		if (now >= SICK_AT) {
			MobEffectInstance sick = who.getEffect(MobEffects.NAUSEA);
			if (sick == null || sick.getDuration() < TOP_UP) {
				who.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0, true, false, true));
			}
		}
	}

	/** How thick the smoke {@code who} breathes is, 0..1. */
	static float dose(ServerLevel level, LivingEntity who, List<FireSmog.Source> smoke, double ca, double sa) {
		if (who.isUnderWater()) {
			return 0.0F;
		}
		double ex = who.getX(), ey = who.getEyeY(), ez = who.getZ();
		float dose = 0.0F;
		for (FireSmog.Source s : smoke) {
			if (ey > s.top() + ABOVE || ey < s.top() - BELOW) {
				continue;
			}
			double dx = ex - (s.x() + ca * DOWNWIND), dz = ez - (s.z() + sa * DOWNWIND);
			double d2 = dx * dx + dz * dz;
			if (d2 >= REACH * REACH) {
				continue;
			}
			float edge = (float)(Math.sqrt(d2) / REACH);
			dose += Math.min(1.0F, s.fires() / THICK) * (1.0F - (1.0F - EDGE) * edge);
			if (dose >= 1.0F) {
				dose = 1.0F;
				break;
			}
		}
		if (dose > 0.0F && !level.canSeeSky(BlockPos.containing(ex, ey, ez))) {
			dose *= low(who) ? INDOORS_LOW : INDOORS;
		}
		return dose;
	}

	/** Crouched, crawling or prone (out of water), or lying down. */
	static boolean low(LivingEntity who) {
		Pose pose = who.getPose();
		return who.isCrouching() || pose == Pose.CROUCHING || pose == Pose.SLEEPING || (pose == Pose.SWIMMING && !who.isInWater());
	}
}
