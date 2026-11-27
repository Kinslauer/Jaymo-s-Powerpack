package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-27): "guards should also react and investigate based on your concealment. it should factor in light level,
 * (guards will have to get closer or turn on lights and approach) sounds, and other factors."
 * <p>A guard (a realm's, a camp's, an MCA guard: {@link Footsteps#guard}) grows suspicious of a player it can half make out,
 * instead of either seeing them at once or not at all. How well hidden the player is comes from the same reckoning a
 * monster's eyes use ({@link Senses#sight}): the light where they stand and any the guard carries, sneaking, standing still,
 * the slow pace, cover, a hood, the weather and mist, a smell, being lit up or on fire, the rest. Within that reach the guard
 * simply sees them. Out to {@link #GLIMPSE} times it, in front of it and with nothing solid between, it catches a glimpse
 * and its suspicion of them climbs, faster the nearer they are to being seen. Footsteps and other noises it hears from
 * them ({@link Footsteps#heard}) raise it too, by how loud they came through.
 * <ul>
 * <li><b>Wary</b> ({@link #HEED}): it stops and looks toward where it last glimpsed or heard them.</li>
 * <li><b>Investigating</b> ({@link #INVESTIGATE}): it goes to look, the same search monsters make ({@link Senses#lure}),
 * following fresher glimpses as it goes. If that's dark ({@link NightLights#CLEAR}) and it has no light in hand, it lights
 * one first ({@link NightLights#lightUp}): with a light it sees farther in the dark, so it closes in until it can.</li>
 * <li><b>Found</b>: once it sees them properly it stops searching, looks them over for a moment and then goes back to its
 * post; what it then makes of them (a trespasser, a thief, a wanted man) is up to the law as before. A player it has found
 * isn't suspected again for {@link #KNOWN_TICKS}.</li>
 * </ul>
 * Suspicion falls away by itself ({@link #FADE}). The awareness eye shows it filling ({@link Senses}' gauge), and the
 * spectate view lists it ({@link NpcFocus}). Creative and spectator players are never suspected.
 */
public final class GuardWatch {
	/** How often (ticks) guards are looked over, and how far round each player (blocks). */
	static final int EVERY = 5;
	static final double SCAN = 40.0;
	/** A glimpse reaches this many times as far as clear sight. */
	static final double GLIMPSE = 2.5;
	/** Suspicion a check of glimpsing, at the far edge and (plus) right at the edge of being seen. */
	static final float GLIMPSE_FAR = 0.03F, GLIMPSE_NEAR = 0.09F;
	/** Suspicion from a noise, times its loudness where the guard stands over the noise's reach. */
	static final float HEARD = 0.35F;
	/** Suspicion lost each check without a glimpse. */
	static final float FADE = 0.01F;
	/** It stops and looks at this much suspicion, and goes to look at this much. */
	static final float HEED = 0.25F, INVESTIGATE = 0.6F;
	/** A new glimpse this far (blocks) from the spot it's heading for sends it to the new spot. */
	static final double RESPOT = 3.0;
	/** How long (ticks) it looks a player over once found, and how long it doesn't suspect them again. */
	static final int FOUND_LOOK = 60, KNOWN_TICKS = 20 * 30;
	/** How loud (blocks) its search counts as, against other noises it might be drawn to ({@link Senses#lure}). */
	static final double SEARCH_LOUDNESS = 6.0;
	/** A villager guard's pace walking to look. */
	static final float VILLAGER_SPEED = 0.6F;

	/** One guard's suspicion of one player: how much, where it last glimpsed or heard them, and whom it has found lately. */
	private static final class Suspect {
		float level;
		@Nullable Vec3 spot;
		long knownUntil, lookUntil, raised;
		boolean investigating;
	}

	private static final Map<Mob, Map<UUID, Suspect>> WATCH = new WeakHashMap<>();

	private GuardWatch() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(GuardWatch::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WATCH.clear());
	}

	/** A guard who keeps watch now: awake, up, after no one and not in the middle of an arrest. */
	static boolean watching(Mob mob) {
		return Footsteps.guard(mob) && mob.isAlive() && !mob.isNoAi() && !mob.isSleeping() && !mob.isBaby() && !Downed.isDowned(mob)
			&& Comrades.target(mob) == null && !Arrests.busy(mob);
	}

	private static boolean suspectable(ServerPlayer player) {
		return player.isAlive() && !player.isCreative() && !player.isSpectator();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				if (!suspectable(player)) {
					continue;
				}
				for (Mob guard : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SCAN), GuardWatch::watching)) {
					look(level, guard, player);
				}
			}
		}
		// the guards' own doings: looking, lighting up, going to look; and the suspicions that have faded
		for (Iterator<Map.Entry<Mob, Map<UUID, Suspect>>> it = WATCH.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Map<UUID, Suspect>> e = it.next();
			Mob guard = e.getKey();
			if (guard.isRemoved() || !guard.isAlive()) {
				it.remove();
				continue;
			}
			long now = guard.level().getGameTime();
			for (Suspect s : e.getValue().values()) {
				if (now - s.raised >= EVERY) {
					s.level = Math.max(0.0F, s.level - FADE);
				}
			}
			act(guard, e.getValue(), now);
			e.getValue().values().removeIf(s -> s.level <= 0.0F && !s.investigating && s.knownUntil <= now && s.lookUntil <= now);
			if (e.getValue().isEmpty()) {
				it.remove();
			}
		}
	}

	/** What {@code guard} makes of {@code player} this check: nothing, a glimpse, or a proper sight of them. */
	private static void look(ServerLevel level, Mob guard, ServerPlayer player) {
		long now = level.getGameTime();
		Suspect s = WATCH.computeIfAbsent(guard, g -> new HashMap<>()).get(player.getUUID());
		double reach = guard.getAttributes().hasAttribute(Attributes.FOLLOW_RANGE) ? guard.getAttributeValue(Attributes.FOLLOW_RANGE) : 16.0;
		double seen = reach * Senses.sight(player, level, guard);
		double d = guard.distanceTo(player);
		if (d > seen * GLIMPSE || !Senses.inCone(guard, player) && !Senses.close(guard, player) || !clear(level, guard, player)) {
			// nothing to see: what it heard (Footsteps) may still keep it wary, and the rest fades
			return;
		}
		if (s == null) {
			s = new Suspect();
			WATCH.get(guard).put(player.getUUID(), s);
		}
		if (d <= seen) {
			// seen properly: found them, if it was looking
			if (s.investigating || s.level >= HEED) {
				found(guard, player, s, now);
			}
			s.level = 0.0F;
			s.knownUntil = now + KNOWN_TICKS;
			return;
		}
		if (now < s.knownUntil) {
			// it knows who that is
			return;
		}
		// a glimpse: the nearer to being seen, the faster it adds up
		double edge = 1.0 - (d - seen) / Math.max(0.5, seen * (GLIMPSE - 1.0));
		s.level = Math.min(1.0F, s.level + GLIMPSE_FAR + (float) ((GLIMPSE_NEAR - GLIMPSE_FAR) * edge));
		s.spot = player.position();
		s.raised = now;
	}

	/** Nothing solid between the guard's eyes and the player's eyes or chest, whatever the light (that's {@link Senses#sight}'s). */
	private static boolean clear(ServerLevel level, Mob guard, ServerPlayer player) {
		Vec3 eye = guard.getEyePosition();
		for (Vec3 to : new Vec3[] {player.getEyePosition(), player.position().add(0.0, player.getBbHeight() * 0.6, 0.0)}) {
			if (level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, guard)).getType() == HitResult.Type.MISS) {
				return true;
			}
		}
		return false;
	}

	/** From {@link Footsteps#heard}: {@code guard} heard {@code player}'s noise at {@code at}, {@code loudness} of {@code reach} left. */
	static void heard(Mob guard, @Nullable ServerPlayer player, Vec3 at, double reach, double loudness) {
		if (player == null || !suspectable(player) || !watching(guard) || reach <= 0.0) {
			return;
		}
		long now = guard.level().getGameTime();
		Suspect s = WATCH.computeIfAbsent(guard, g -> new HashMap<>()).computeIfAbsent(player.getUUID(), u -> new Suspect());
		if (now < s.knownUntil) {
			return;
		}
		s.level = Math.min(1.0F, s.level + HEARD * (float) Math.min(1.0, loudness / reach));
		s.spot = at;
		s.raised = now;
	}

	/** {@code guard}'s answer to the most suspicious player it has: look their way, light up and go to look, or look them over. */
	private static void act(Mob guard, Map<UUID, Suspect> suspects, long now) {
		// the one it's looking over, else the one it suspects most
		UUID who = null;
		Suspect top = null;
		for (Map.Entry<UUID, Suspect> e : suspects.entrySet()) {
			Suspect s = e.getValue();
			boolean looking = s.lookUntil > now;
			if (top == null || looking && top.lookUntil <= now || (looking == top.lookUntil > now) && s.level > top.level) {
				who = e.getKey();
				top = s;
			}
		}
		if (top == null || !watching(guard)) {
			return;
		}
		if (top.lookUntil > now) {
			// looking over the one it found
			ServerPlayer found = guard.level().getServer().getPlayerList().getPlayer(who);
			if (found != null) {
				guard.getLookControl().setLookAt(found, 30.0F, 30.0F);
			}
			return;
		}
		Senses.Track track = Senses.track(guard);
		if (top.investigating && (track == null || !track.searching || !who.equals(track.who))) {
			// the search is over (it looked and found nothing, and went back): it lets it go
			top.investigating = false;
			top.level = Math.min(top.level, HEED - FADE);
		}
		Vec3 spot = top.spot;
		if (spot == null) {
			return;
		}
		if (top.level >= INVESTIGATE && guard instanceof PathfinderMob pm && guard.level() instanceof ServerLevel level) {
			if (!top.investigating || track != null && track.spot != null && track.spot.distanceToSqr(spot) > RESPOT * RESPOT) {
				// dark there or where it stands: a light first, then it goes to look
				if (NightLights.ambient(level, BlockPos.containing(spot).above()) < NightLights.CLEAR
					|| NightLights.ambient(level, BlockPos.containing(guard.getEyePosition())) < NightLights.CLEAR) {
					NightLights.lightUp(level, pm);
				}
				top.investigating = Senses.lure(guard, spot, who, SEARCH_LOUDNESS) || top.investigating;
			}
			if (top.investigating && guard instanceof Villager v && track != null && track.searching && track.lookLeft < 0 && track.spot != null
				&& now % 10 == 0) {
				// an MCA guard walks by his brain: it's told where to go too, so its patrol doesn't take him off
				BehaviorUtils.setWalkAndLookTargetMemories(v, BlockPos.containing(track.spot), VILLAGER_SPEED, 1);
			}
			return;
		}
		if (top.level >= HEED && !top.investigating) {
			// wary: it stands and looks that way
			guard.getNavigation().stop();
			guard.getLookControl().setLookAt(spot.x, spot.y + 1.0, spot.z);
		}
	}

	/** {@code guard} has seen properly the one it was suspicious of: it stops looking for them and looks them over. */
	private static void found(Mob guard, ServerPlayer player, Suspect s, long now) {
		Senses.Track track = Senses.track(guard);
		if (track != null && track.searching && player.getUUID().equals(track.who)) {
			track.searching = false;
			track.spot = null;
			track.returning = track.home != null;
		}
		s.investigating = false;
		s.lookUntil = now + FOUND_LOOK;
	}

	/** Whether {@code mob} is going to look for someone it is suspicious of (its light stays lit meanwhile, day or night). */
	static boolean investigating(Mob mob) {
		Map<UUID, Suspect> suspects = WATCH.get(mob);
		if (suspects == null) {
			return false;
		}
		for (Suspect s : suspects.values()) {
			if (s.investigating) {
				return true;
			}
		}
		return false;
	}

	/** How suspicious {@code guard} is of {@code player}, 0 to 1. */
	public static float of(Mob guard, UUID player) {
		Map<UUID, Suspect> suspects = WATCH.get(guard);
		Suspect s = suspects == null ? null : suspects.get(player);
		return s == null ? 0.0F : s.level;
	}

	/** The player {@code guard} is most suspicious of and how much, for the spectate view; null if none. */
	public static Map.@Nullable Entry<UUID, Float> most(Mob guard) {
		Map<UUID, Suspect> suspects = WATCH.get(guard);
		if (suspects == null) {
			return null;
		}
		Map.Entry<UUID, Float> top = null;
		for (Map.Entry<UUID, Suspect> e : suspects.entrySet()) {
			if (e.getValue().level > 0.0F && (top == null || e.getValue().level > top.getValue())) {
				top = Map.entry(e.getKey(), e.getValue().level);
			}
		}
		return top;
	}
}
