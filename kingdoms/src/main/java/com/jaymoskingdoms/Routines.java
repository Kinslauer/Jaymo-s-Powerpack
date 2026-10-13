package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.9) Villagers keep a day, not just wander. Each one free of work (a realm's townsfolk, MCA's too; anyone with a
 * trade only outside working hours) now and then takes up something that fits the hour, walks there and stays a while:
 * <ul>
 * <li>dawn: fetching water at the pond or river, tending the flowers and the compost;</li>
 * <li>by day: gathering at the bell for the market, calling on a neighbour for a chat, a stroll round the outskirts,
 * the garden; children play chase with one another;</li>
 * <li>evening: sitting round a campfire (the bell if there's none), heading home early, a last chat.</li>
 * </ul>
 * Night is MCA's (they sleep). Anyone afraid, fighting, talking to a player, at work, on guard or playing tag is left be.
 */
final class Routines {
	/** How often routines are thought about (ticks); how far from a seat its villagers are looked at; how far landmarks are sought. */
	static final int EVERY = 20;
	static final double WATCHED = 96.0;
	static final int LANDMARK_RANGE = 40;
	/** How often a settlement's landmarks are looked for again (ticks). */
	static final long LANDMARK_TICKS = 6000L;
	/** The chance each think that an idle villager takes something up. */
	static final float START_CHANCE = 0.05F;
	/** How long they stay at it once there (ticks), and the longest they try to get there. */
	static final int LINGER_MIN = 300, LINGER_MORE = 500, TRAVEL_TICKS = 900;
	/** How close is there; their pace walking, and children's running. */
	static final int ARRIVE = 2;
	static final float WALK = 0.55F, RUN = 0.8F;
	/** While at it, the chance each think that they say something (heard this far), or do the thing (a swing, a splash). */
	static final float SAY_CHANCE = 0.03F, ACT_CHANCE = 0.25F;
	static final double SPEECH_RANGE = 12.0;
	/** How far they go calling on someone; how far a stroll takes them from the seat. */
	static final double CALL_RANGE = 32.0;
	static final int STROLL_MIN = 18, STROLL_MORE = 22;
	/** Children playing chase: a tag is this close; the runner darts this far. */
	static final double CHASE_REACH = 1.4;
	static final int DART = 6;
	/** Lines each routine has (lang {@code routine.jaymoskingdoms.<kind>.N}). */
	static final int LINES = 4;
	/** Working hours, by the time of day: anyone with a trade is at it then (vanilla's schedule). */
	static final long WORK_FROM = 2000L, WORK_TO = 9000L, EVENING = 9000L, NIGHT = 12000L;

	enum Kind {
		WATER, GARDEN, MARKET, CHAT, STROLL, FIRE, HOME, PLAY;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static final class Doing {
		final Kind kind;
		BlockPos to;
		/** What they're about: the water, the flower, the fire. */
		@Nullable BlockPos at;
		/** Whom they're with (a chat, a game of chase). */
		@Nullable Villager with;
		/** Chasing (not running), playing chase. */
		boolean chaser;
		final long started;
		long arrived = -1L;
		final int linger;

		Doing(Kind kind, BlockPos to, long now, int linger) {
			this.kind = kind;
			this.to = to;
			this.started = now;
			this.linger = linger;
		}
	}

	private record Landmarks(List<BlockPos> water, List<BlockPos> garden, List<BlockPos> fires, long at) {
	}

	private static final Map<UUID, Doing> DOING = new HashMap<>();
	private static final Map<Integer, Landmarks> LANDMARKS = new HashMap<>();

	private Routines() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Routines::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DOING.clear();
			LANDMARKS.clear();
		});
	}

	/** {@code v} is about one of its routines just now. */
	static boolean busy(Villager v) {
		return DOING.containsKey(v.getUUID());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		long t = Royals.timeOfDay(level);
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			Landmarks marks = LANDMARKS.get(f.id());
			if (marks == null || now - marks.at() >= LANDMARK_TICKS) {
				marks = landmarks(level, f.seat(), now);
				LANDMARKS.put(f.id(), marks);
			}
			List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), Villager::isAlive);
			for (Villager v : folk) {
				Doing d = DOING.get(v.getUUID());
				if (!free(v, t)) {
					if (d != null) {
						DOING.remove(v.getUUID());
					}
					continue;
				}
				if (d == null) {
					if (t < NIGHT && level.getRandom().nextFloat() < START_CHANCE) {
						start(level, f, marks, v, folk, t, now);
					}
				} else if (!step(level, v, d, now) || t >= NIGHT) {
					DOING.remove(v.getUUID());
				}
			}
		}
		DOING.values().removeIf(d -> now - d.started > TRAVEL_TICKS + LINGER_MIN + LINGER_MORE + 200);
	}

	/** Free for a routine at {@code t}: awake, unafraid, not working, guarding, fighting or talking. */
	private static boolean free(Villager v, long t) {
		if (v.isSleeping() || v.isPassenger() || v.isNoAi() || v.getTarget() != null || v.hurtTime > 0 || Frights.afraid(v.getUUID())
			|| Theft.rioting(v) || Approaches.talking(v) || Quests.listening(v) || Tag.playing(v) || Trespass.watching(v) || McaCompat.busy(v)
			|| McaCompat.panicking(v) || Hermits.is(v) || Rickshaws.onDuty(v)) {
			return false;
		}
		if (v instanceof Worker w && w.role() >= 0) {
			// (the realm's workers and guards have their own day)
			return false;
		}
		String job = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath();
		if (job.equals("guard") || job.equals("archer")) {
			return false;
		}
		boolean trade = !v.isBaby() && !v.getVillagerData().profession().is(VillagerProfession.NONE)
			&& !v.getVillagerData().profession().is(VillagerProfession.NITWIT);
		return !trade || t < WORK_FROM || t >= WORK_TO;
	}

	/** {@code v} takes up something fitting the hour. */
	private static void start(ServerLevel level, Faction f, Landmarks marks, Villager v, List<Villager> folk, long t, long now) {
		RandomSource random = level.getRandom();
		int linger = LINGER_MIN + random.nextInt(LINGER_MORE);
		if (v.isBaby()) {
			Villager friend = nearest(v, folk, true);
			if (friend != null && !DOING.containsKey(friend.getUUID()) && free(friend, t)) {
				Doing a = new Doing(Kind.PLAY, v.blockPosition(), now, linger), b = new Doing(Kind.PLAY, friend.blockPosition(), now, linger);
				a.with = friend;
				b.with = v;
				b.chaser = true;
				a.arrived = b.arrived = now;
				DOING.put(v.getUUID(), a);
				DOING.put(friend.getUUID(), b);
				dart(v, friend);
			}
			return;
		}
		List<Kind> kinds = new ArrayList<>();
		if (t < WORK_FROM) {
			kinds.addAll(List.of(Kind.WATER, Kind.WATER, Kind.GARDEN, Kind.MARKET));
		} else if (t < EVENING) {
			kinds.addAll(List.of(Kind.MARKET, Kind.MARKET, Kind.CHAT, Kind.CHAT, Kind.STROLL, Kind.GARDEN, Kind.WATER));
		} else {
			kinds.addAll(List.of(Kind.FIRE, Kind.FIRE, Kind.HOME, Kind.CHAT));
		}
		Kind kind = kinds.get(random.nextInt(kinds.size()));
		Doing d = switch (kind) {
			case WATER -> at(level, kind, pick(marks.water(), v, random), now, linger);
			case GARDEN -> at(level, kind, pick(marks.garden(), v, random), now, linger);
			case FIRE -> {
				BlockPos fire = pick(marks.fires(), v, random);
				yield fire != null ? at(level, kind, fire, now, linger) : at(level, Kind.MARKET, f.seat(), now, linger);
			}
			case MARKET -> {
				Doing m = at(level, kind, f.seat(), now, linger);
				// (milling about the bell, not all on one spot)
				m.to = Construction.standable(level, f.seat().offset(random.nextInt(9) - 4, 0, random.nextInt(9) - 4), 3);
				yield m;
			}
			case STROLL -> {
				double angle = random.nextDouble() * Math.PI * 2;
				int r = STROLL_MIN + random.nextInt(STROLL_MORE);
				BlockPos edge = f.seat().offset((int)(Math.cos(angle) * r), 0, (int)(Math.sin(angle) * r));
				yield new Doing(kind, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, edge), now, LINGER_MIN / 3);
			}
			case HOME -> {
				GlobalPos home = McaCompat.home(v).orElse(null);
				yield home != null && home.dimension() == level.dimension() ? new Doing(kind, home.pos(), now, linger) : null;
			}
			case CHAT -> {
				Villager friend = nearest(v, folk, false);
				if (friend == null || DOING.containsKey(friend.getUUID()) || !free(friend, t)) {
					yield null;
				}
				Doing c = new Doing(kind, friend.blockPosition(), now, linger);
				c.with = friend;
				yield c;
			}
			case PLAY -> null;
		};
		if (d != null) {
			DOING.put(v.getUUID(), d);
			walk(v, d.to, WALK);
		}
	}

	private static @Nullable Doing at(ServerLevel level, Kind kind, @Nullable BlockPos thing, long now, int linger) {
		if (thing == null) {
			return null;
		}
		Doing d = new Doing(kind, Construction.standable(level, thing.above(), 2), now, linger);
		d.at = thing;
		return d;
	}

	/** One think while at it; false once it's done (or can't be). */
	private static boolean step(ServerLevel level, Villager v, Doing d, long now) {
		RandomSource random = level.getRandom();
		if (d.kind == Kind.PLAY) {
			return play(level, v, d, now);
		}
		if (d.with != null) {
			if (!d.with.isAlive() || d.with.distanceToSqr(v) > CALL_RANGE * CALL_RANGE || d.arrived >= 0 && !DOING.containsKey(d.with.getUUID())) {
				return false;
			}
			d.to = d.with.blockPosition();
		}
		boolean there = v.blockPosition().closerThan(d.to, d.with != null ? ARRIVE + 1 : ARRIVE);
		if (!there) {
			if (d.arrived >= 0 && d.with != null) {
				// (the other walked off: the chat's over)
				return false;
			}
			if (d.arrived >= 0) {
				// (wandered: back to it)
				walk(v, d.to, WALK);
			} else if (now - d.started > TRAVEL_TICKS) {
				return false;
			} else {
				walk(v, d.to, WALK);
			}
			return true;
		}
		if (d.arrived < 0) {
			d.arrived = now;
			v.getNavigation().stop();
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			if (d.with != null && !DOING.containsKey(d.with.getUUID())) {
				// (the one called on stops for the chat)
				Doing back = new Doing(Kind.CHAT, v.blockPosition(), now, d.linger);
				back.with = v;
				back.arrived = now;
				DOING.put(d.with.getUUID(), back);
				d.with.getNavigation().stop();
			}
			say(level, v, d.kind, random);
		}
		if (now - d.arrived > d.linger) {
			return false;
		}
		// keep there, and about it
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		if (d.with != null) {
			v.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(d.with, true));
		} else if (d.at != null) {
			v.getLookControl().setLookAt(Vec3.atCenterOf(d.at));
		}
		if (random.nextFloat() < ACT_CHANCE) {
			act(level, v, d, random);
		}
		if (random.nextFloat() < SAY_CHANCE) {
			say(level, v, d.kind, random);
		}
		return true;
	}

	/** Doing the thing: a dip of the bucket, a hand in the soil, warming hands, a nod in the chat. */
	private static void act(ServerLevel level, Villager v, Doing d, RandomSource random) {
		switch (d.kind) {
			case WATER -> {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				if (d.at != null) {
					level.sendParticles(ParticleTypes.SPLASH, d.at.getX() + 0.5, d.at.getY() + 1.0, d.at.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.0);
					if (random.nextInt(4) == 0) {
						level.playSound(null, d.at, SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 0.4F, 1.0F);
					}
				}
			}
			case GARDEN -> {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				if (d.at != null && random.nextInt(3) == 0) {
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, d.at.getX() + 0.5, d.at.getY() + 0.8, d.at.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.0);
				}
			}
			case CHAT, MARKET -> {
				if (random.nextInt(3) == 0) {
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.5F, v.isBaby() ? 1.5F : 1.0F);
				}
			}
			case FIRE -> {
				if (d.at != null && random.nextInt(3) == 0) {
					v.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
				}
			}
			default -> {
			}
		}
	}

	/** Children playing chase: the runner darts about, the chaser after them; a tag swaps them. */
	private static boolean play(ServerLevel level, Villager v, Doing d, long now) {
		Villager other = d.with;
		if (other == null || !other.isAlive() || !DOING.containsKey(other.getUUID()) || now - d.started > d.linger
			|| other.distanceToSqr(v) > CALL_RANGE * CALL_RANGE) {
			return false;
		}
		if (!d.chaser) {
			if (v.distanceToSqr(other) < 25.0 || v.getNavigation().isDone()) {
				dart(v, other);
			}
			return true;
		}
		if (v.distanceTo(other) <= CHASE_REACH) {
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.7F);
			d.chaser = false;
			Doing theirs = DOING.get(other.getUUID());
			theirs.chaser = true;
			dart(v, other);
			if (level.getRandom().nextFloat() < 0.3F) {
				say(level, v, Kind.PLAY, level.getRandom());
			}
		} else {
			BehaviorUtils.setWalkAndLookTargetMemories(v, other, RUN, 0);
		}
		return true;
	}

	/** {@code v} darts off away from {@code from}, a little to one side. */
	private static void dart(Villager v, Villager from) {
		Vec3 away = v.position().subtract(from.position());
		if (away.lengthSqr() < 0.01) {
			away = new Vec3(1, 0, 0);
		}
		double side = (v.getRandom().nextDouble() - 0.5) * 1.5;
		Vec3 dir = away.normalize();
		Vec3 to = v.position().add(dir.x * DART - dir.z * side * DART, 0, dir.z * DART + dir.x * side * DART);
		walk(v, BlockPos.containing(to), RUN);
	}

	private static void walk(Villager v, BlockPos to, float speed) {
		BehaviorUtils.setWalkAndLookTargetMemories(v, to, speed, 1);
	}

	private static void say(ServerLevel level, Villager v, Kind kind, RandomSource random) {
		Speech.say(level, v, Component.translatable("routine.jaymoskingdoms." + kind.key() + "." + random.nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}

	/** The nearest other villager to {@code v}, a child (or a grown one), within reach. */
	private static @Nullable Villager nearest(Villager v, List<Villager> folk, boolean child) {
		Villager best = null;
		double bestD = CALL_RANGE * CALL_RANGE;
		for (Villager o : folk) {
			if (o != v && o.isBaby() == child) {
				double d = o.distanceToSqr(v);
				if (d < bestD) {
					bestD = d;
					best = o;
				}
			}
		}
		return best;
	}

	/** One of {@code spots}, nearer ones likelier (the nearest of two picked at random). */
	private static @Nullable BlockPos pick(List<BlockPos> spots, Villager v, RandomSource random) {
		if (spots.isEmpty()) {
			return null;
		}
		BlockPos a = spots.get(random.nextInt(spots.size())), b = spots.get(random.nextInt(spots.size()));
		return a.distSqr(v.blockPosition()) <= b.distSqr(v.blockPosition()) ? a : b;
	}

	/** Water, garden and fires on the surface about {@code seat}. */
	private static Landmarks landmarks(ServerLevel level, BlockPos seat, long now) {
		List<BlockPos> water = new ArrayList<>(), garden = new ArrayList<>(), fires = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -LANDMARK_RANGE; dx <= LANDMARK_RANGE; dx += 2) {
			for (int dz = -LANDMARK_RANGE; dz <= LANDMARK_RANGE; dz += 2) {
				int x = seat.getX() + dx, z = seat.getZ() + dz;
				if (!level.isLoaded(p.set(x, seat.getY(), z))) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
				BlockState top = level.getBlockState(p.set(x, y - 1, z));
				BlockState over = level.getBlockState(p.set(x, y, z));
				if (top.is(Blocks.WATER) && water.size() < 64) {
					water.add(new BlockPos(x, y - 1, z));
				} else if (top.getBlock() instanceof CampfireBlock) {
					fires.add(new BlockPos(x, y - 1, z));
				} else if ((top.is(Blocks.COMPOSTER) || top.is(Blocks.FLOWER_POT) || top.is(BlockTags.FLOWER_POTS)) && garden.size() < 64) {
					garden.add(new BlockPos(x, y - 1, z));
				} else if (over.is(BlockTags.FLOWERS) && garden.size() < 64) {
					garden.add(new BlockPos(x, y, z));
				}
			}
		}
		return new Landmarks(water, garden, fires, now);
	}
}
