package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Footsteps others hear (Andy's ImmersiveFX pick, "wolves and guards could hear them"; the sounds themselves are Jaymo's
 * Powers' Footfalls, on each client). Every {@link #EVERY} ticks a walking player's feet carry {@link #WALK_REACH}
 * blocks, a running one's {@link #RUN_REACH}, times:
 * <ul>
 * <li>the ground ({@link #ground}): metal and gravel loudest, then wood, snow and stone, mud, and soft grass, sand and
 * carpet the quietest;</li>
 * <li>the armour worn ({@link #ARMOUR_PER_POINT} more a point of armour, so plate is heard farther than leather);</li>
 * <li>sneaking (the same share as for every other noise, {@link Awareness#noise}, so Quiet Step's ranks count), and the
 * stance wheel's low crouch ({@link #LOW}) and crawling ({@link #PRONE}) less again;</li>
 * <li>a wall between the feet and the ear: {@link #WALL} as far.</li>
 * </ul>
 * Who hears it, as loud as what's left of the reach where they are:
 * <ul>
 * <li><b>wolves</b> (and anything in {@link #HEARS}): a wild one comes to see (the same search as for a player it lost);
 * a tame one turns its head;</li>
 * <li><b>guards</b> (a realm's, a camp's, an MCA guard): awake, they turn to the sound for {@link #LOOK_TICKS}, so their
 * own eyes take it from there; asleep, loud feet ({@link #WAKE_REACH} or more: running, or plate on gravel) may wake them
 * as a chest's creak does ({@link ChestNoise}), more likely the closer.</li>
 * </ul>
 * Creative and spectator players make none. Zombies keep their own ear for a runner ({@link Awareness}).
 */
public final class Footsteps {
	/** How often footsteps are heard (ticks), and how far a walk and a run carry (blocks, before the rest). */
	static final int EVERY = 10;
	static final double WALK_REACH = 6.0, RUN_REACH = 12.0;
	/** Slower than this (blocks a check) and there are no footsteps. */
	static final double MIN_MOVED = 0.4;
	/** Armour: this much farther per point of armour worn. */
	static final double ARMOUR_PER_POINT = 0.025;
	/** The low crouch and crawling, on top of sneaking's share; a wall between. */
	static final double LOW = 0.7, PRONE = 0.4, WALL = 0.5;
	/** Too faint to notice (what's left of the reach, blocks). */
	static final double FAINTEST = 1.0;
	/** Guards: how long they look; the reach that can wake one, and the chance at arm's length. */
	static final int LOOK_TICKS = 40;
	static final double WAKE_REACH = 8.0;
	static final float WAKE_CHANCE = 0.35F;

	/** Animals that hear footsteps: wolves and foxes, and any a pack adds. */
	public static final TagKey<EntityType<?>> HEARS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("hears_footsteps"));

	private record Look(Vec3 at, long until) {
	}

	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<Mob, Look> LOOKING = new WeakHashMap<>();

	private Footsteps() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Footsteps::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST.clear();
			LOOKING.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		looks();
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Vec3 now = player.position();
			Vec3 last = LAST.put(player.getUUID(), now);
			if (last == null || player.isCreative() || player.isSpectator() || !player.isAlive() || player.isPassenger() || !player.onGround()
				|| !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			double dx = now.x - last.x, dz = now.z - last.z;
			if (dx * dx + dz * dz < MIN_MOVED * MIN_MOVED) {
				continue;
			}
			double reach = reach(level, player);
			if (reach >= FAINTEST) {
				heard(level, player, reach);
			}
		}
	}

	/** How far {@code player}'s feet carry right now (blocks). */
	static double reach(ServerLevel level, ServerPlayer player) {
		double reach = player.isSprinting() ? RUN_REACH : WALK_REACH;
		reach *= ground(level.getBlockState(player.getOnPosLegacy()).getSoundType());
		reach *= 1.0 + ARMOUR_PER_POINT * player.getArmorValue();
		reach *= Awareness.noise(player);
		if (player.getPose() == Pose.SWIMMING && !player.isInWater()) {
			reach *= PRONE;
		} else if (player.isCrouching() && PowersCompat.lowStance(player)) {
			reach *= LOW;
		}
		return reach;
	}

	/** How loud the ground is underfoot, beside stone. */
	static double ground(SoundType type) {
		if (type == SoundType.METAL || type == SoundType.ANVIL || type == SoundType.CHAIN || type == SoundType.COPPER || type == SoundType.IRON
			|| type == SoundType.NETHERITE_BLOCK) {
			return 1.4;
		}
		if (type == SoundType.GRAVEL || type == SoundType.SUSPICIOUS_GRAVEL) {
			return 1.3;
		}
		if (type == SoundType.WOOD || type == SoundType.BAMBOO_WOOD || type == SoundType.NETHER_WOOD || type == SoundType.CHERRY_WOOD
			|| type == SoundType.LADDER || type == SoundType.SCAFFOLDING) {
			return 1.2;
		}
		if (type == SoundType.SNOW || type == SoundType.POWDER_SNOW) {
			return 1.1;
		}
		if (type == SoundType.MUD || type == SoundType.SLIME_BLOCK || type == SoundType.HONEY_BLOCK) {
			return 0.9;
		}
		if (type == SoundType.GRASS || type == SoundType.MOSS || type == SoundType.MOSS_CARPET || type == SoundType.SAND || type == SoundType.SOUL_SAND
			|| type == SoundType.SOUL_SOIL || type == SoundType.ROOTED_DIRT || type == SoundType.LEAF_LITTER) {
			return 0.7;
		}
		if (type == SoundType.WOOL) {
			return 0.4;
		}
		return 1.0;
	}

	private static void heard(ServerLevel level, ServerPlayer player, double reach) {
		Vec3 at = player.position();
		BlockPos pos = player.blockPosition();
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(reach), m -> m.isAlive() && m.getTarget() == null)) {
			boolean animal = mob.getType().builtInRegistryHolder().is(HEARS);
			boolean guard = guard(mob);
			if (!animal && !guard) {
				continue;
			}
			double d = mob.position().distanceTo(at);
			double loudness = reach - d;
			if (loudness >= FAINTEST && ChestNoise.walled(level, at.add(0, 0.2, 0), mob)) {
				loudness = reach * WALL - d;
			}
			if (loudness < FAINTEST) {
				continue;
			}
			if (mob.isSleeping()) {
				if (guard && reach >= WAKE_REACH && level.getRandom().nextFloat() < WAKE_CHANCE * loudness / reach) {
					ChestNoise.wakeBy(level, mob, player, pos);
				}
				continue;
			}
			LOOKING.put(mob, new Look(at, level.getGameTime() + LOOK_TICKS));
			if (animal && !(mob instanceof TamableAnimal tame && tame.isTame())) {
				Senses.lure(mob, at, player.getUUID(), loudness);
			}
		}
	}

	/** A realm's or camp's guard or archer, or an MCA villager guarding a settlement. */
	static boolean guard(Mob mob) {
		if (mob instanceof Folk folk) {
			return folk.role() == Folk.GUARD || folk.role() == Folk.ARCHER;
		}
		return mob instanceof Worker worker && worker.role() == Folk.GUARD;
	}

	/** Keeps the ones who heard looking toward the sound a while. */
	private static void looks() {
		if (LOOKING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<Mob, Look>> it = LOOKING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Mob, Look> e = it.next();
			Mob mob = e.getKey();
			Look look = e.getValue();
			if (mob.isRemoved() || !mob.isAlive() || mob.getTarget() != null || mob.level().getGameTime() >= look.until()) {
				it.remove();
				continue;
			}
			mob.getLookControl().setLookAt(look.at().x, look.at().y + 1.0, look.at().z);
		}
	}
}
