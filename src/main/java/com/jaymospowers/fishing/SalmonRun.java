package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.water.RiverRun;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.Salmon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The salmon run (Andy's nature picks, 2026-11-26: "YES" to salmon swimming upriver in autumn; his note: "they should
 * deteriorate in the water after spawning like they do irl"). Vanilla salmon, nothing about the fish itself changed.
 * Living things' three rules:
 * <ul>
 * <li><b>Sympathy</b>: in autumn every grown salmon in a river swims against the flow ({@link RiverRun} says which way
 * that is), and where the water steps up it leaps, over and over, till it makes it ({@link #LEAP_MAKE}). Once it's come
 * {@link #RUN_LEAST} blocks upriver it spawns on the first shallow gravel it finds (or, after {@link #RUN_FAR}, on any
 * shallow bed, or where the river runs out), and from then it's spent: slow, drifting back down, losing health, dead in
 * about {@link #SPENT_TICKS} ticks. A run salmon that never spawns is spent at winter's start where it is. In spring the
 * small salmon of every bed that took come up from it and drift down to the sea.</li>
 * <li><b>Feedback loops</b>: the spent dead leave their bodies (Kitchen) on the bed, and in spring the banks round a bed
 * where many died grow lusher ({@link #BANK_PER_DEAD}): the sea's food carried into the land. Each reach of a river
 * ({@link #REACH_SHIFT}) keeps its own count ({@link Reach}): the run it gets each autumn ({@link Reach#stock}, the
 * schools {@link #school} brings in near players) is what reached its beds the year before.</li>
 * <li><b>Stewardship</b>: take the whole run and the next one is thin ({@link #STOCK_LEAST}); let them through, or put them
 * back ({@link CatchRelease}, which lets the same fish go on), and it grows to {@link #STOCK_MOST}. Nobody who leaves the
 * river alone loses anything.</li>
 * </ul>
 * Needs Kitchen's seasons ({@link FishSeasons#season}); without them there's no autumn and no run.
 */
public final class SalmonRun {
	/** How often run salmon are steered (ticks), how far upriver they aim (blocks), and at what pace. */
	static final int STEER_EVERY = 10, AIM = 10;
	static final double UP_PACE = 1.2, PUSH = 0.04;
	/** A leap at a step: up, on, how often one fish tries (ticks), and the chance it makes it. */
	static final double LEAP_UP = 0.7, LEAP_ON = 0.35, LEAP_SHORT = 0.4;
	static final long LEAP_GAP = 30;
	static final float LEAP_MAKE = 0.35F;
	/** Blocks upriver before it spawns on gravel, and before it spawns on any shallow bed; how shallow (water blocks). */
	static final double RUN_LEAST = 48.0, RUN_FAR = 160.0;
	static final int SHALLOW = 3;
	/** A spent salmon: how long it lives on (ticks), how slow it is, how hard it drifts back down. */
	static final long SPENT_TICKS = 24000L * 2;
	static final double SPENT_SLOW = -0.6, SPENT_DRIFT = 0.02;
	/** A reach of river is a square this many blocks a side (as a shift: 6 = 64 blocks). */
	static final int REACH_SHIFT = 6;
	/** The run a reach gets (salmon brought in): the first year, at least, at most, and per salmon that reached its beds. */
	static final int STOCK_FIRST = 12, STOCK_LEAST = 2, STOCK_MOST = 40, STOCK_PER_SPAWNED = 2;
	/** Schools brought in: how often per player (ticks), how big, how far off (blocks), and how many salmon near is enough. */
	static final long SCHOOL_EVERY = 20L * 30;
	static final int SCHOOL_MIN = 3, SCHOOL_MAX = 6, CROWD = 10;
	static final double SCHOOL_NEAR = 24.0, SCHOOL_FAR = 56.0;
	/** Spring: the most young one bed sends down, and how near a player must be for it (blocks). */
	static final int FRY_MOST = 8;
	static final double SPRING_NEAR = 64.0;
	/** Spring banks: tries per salmon that died at the bed, at most, and how far round the bed (blocks). */
	static final int BANK_PER_DEAD = 2, BANK_MOST = 30, BANK_RADIUS = 7;
	static final int SPRING = 0, AUTUMN = 2, WINTER = 3;
	/** Fishers at the run: how far they come (blocks), how near the fish they fish from, the hours they keep (day time), how many they leave in a school, the chance each look takes one. */
	static final double FISHER_RANGE = 48.0, FISHER_REACH = 5.0;
	static final long FISHER_FROM = 1000L, FISHER_UNTIL = 9000L;
	static final int FISHER_LEAVES = 2;
	static final float FISHER_TAKES = 0.3F;
	/** What salmon fetches at a fisherman's on a run river in autumn (salmon is everywhere then). */
	static final float RUN_PRICE = 0.5F;

	/** A run salmon: where it started up the river (a BlockPos as a long). */
	public static final AttachmentType<Long> RAN_FROM = AttachmentRegistry.create(JaymosPowers.id("salmon_ran_from"),
		b -> b.persistent(Codec.LONG));
	/** A spent salmon: the game time it spawned. */
	public static final AttachmentType<Long> SPENT = AttachmentRegistry.create(JaymosPowers.id("salmon_spent"),
		b -> b.persistent(Codec.LONG));
	private static final Identifier SPENT_SLOWNESS = JaymosPowers.id("salmon_spent");

	/**
	 * One reach of river, kept with its level: the run it gets this autumn, how many were brought in so far, how many
	 * spawned on its beds and how many died spent there, the last bed's place, and the season it was last seen in.
	 */
	record Reach(int stock, int brought, int spawned, int dead, long bed, int season) {
		static final Codec<Reach> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("stock").forGetter(Reach::stock),
			Codec.INT.fieldOf("brought").forGetter(Reach::brought),
			Codec.INT.fieldOf("spawned").forGetter(Reach::spawned),
			Codec.INT.fieldOf("dead").forGetter(Reach::dead),
			Codec.LONG.fieldOf("bed").forGetter(Reach::bed),
			Codec.INT.fieldOf("season").forGetter(Reach::season)
		).apply(i, Reach::new));
	}

	/** Every reach's count, kept on its level. */
	public static final AttachmentType<Map<String, Reach>> REACHES = AttachmentRegistry.create(JaymosPowers.id("salmon_reaches"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Reach.CODEC).xmap(HashMap::new, m -> m)).initializer(HashMap::new));

	private static final Set<Salmon> SALMON = Collections.newSetFromMap(new WeakHashMap<>());
	private static final Map<Salmon, Long> LEAPT = new WeakHashMap<>();
	private static final Map<ServerPlayer, Long> SCHOOLED = new WeakHashMap<>();

	private SalmonRun() {
	}

	static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Salmon s) {
				SALMON.add(s);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> SALMON.remove(entity));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Salmon s && s.hasAttached(SPENT) && s.level() instanceof ServerLevel level) {
				BlockPos at = s.blockPosition();
				Reach r = reach(level, at);
				put(level, at, new Reach(r.stock, r.brought, r.spawned, r.dead + 1, r.bed == 0L ? at.asLong() : r.bed, r.season));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(SalmonRun::tick);
	}

	/** Whether {@code s} has spawned and is wearing out (the bears and fishers of later patches can read this). */
	public static boolean spent(Salmon s) {
		return s.hasAttached(SPENT);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % STEER_EVERY != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		int season = FishSeasons.season(overworld);
		if (season < 0) {
			return;
		}
		for (Salmon s : SALMON.toArray(Salmon[]::new)) {
			if (s.isAlive() && s.isInWater() && s.level() instanceof ServerLevel level) {
				salmon(level, s, season);
			}
		}
		if (server.getTickCount() % 200 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
					seasons(level, p, season);
				}
			}
		}
	}

	/** One salmon's step: spent ones wear out, grown ones run in autumn, young ones drift down in spring. */
	private static void salmon(ServerLevel level, Salmon s, int season) {
		long now = level.getGameTime();
		BlockPos at = s.blockPosition();
		Long spentAt = s.getAttached(SPENT);
		if (spentAt != null) {
			wearOut(level, s, now - spentAt);
			Vec3 down = RiverRun.downstream(level, at);
			s.setDeltaMovement(s.getDeltaMovement().add(down.x * SPENT_DRIFT, 0.0, down.z * SPENT_DRIFT));
			return;
		}
		boolean young = s.getVariant() == Salmon.Variant.SMALL;
		if (young) {
			if (season == SPRING) {
				Vec3 down = RiverRun.downstream(level, at);
				if (down.lengthSqr() > 0.0) {
					s.getNavigation().moveTo(s.getX() + down.x * AIM, s.getY(), s.getZ() + down.z * AIM, 1.0);
				}
			}
			return;
		}
		Long from = s.getAttached(RAN_FROM);
		if (season == WINTER && from != null) {
			// (the run is over: one that never got to the beds is spent where it is)
			spend(level, s, false);
			return;
		}
		if (season != AUTUMN) {
			return;
		}
		Vec3 down = RiverRun.downstream(level, at);
		if (from == null) {
			if (down.lengthSqr() == 0.0) {
				return;
			}
			from = at.asLong();
			s.setAttached(RAN_FROM, from);
		}
		double come = Math.sqrt(BlockPos.of(from).distSqr(at));
		if (come >= RUN_LEAST && (down.lengthSqr() == 0.0 || shallow(level, at, come >= RUN_FAR))) {
			spend(level, s, true);
			return;
		}
		if (down.lengthSqr() == 0.0) {
			return;
		}
		s.getNavigation().moveTo(s.getX() - down.x * AIM, s.getY(), s.getZ() - down.z * AIM, UP_PACE);
		s.setDeltaMovement(s.getDeltaMovement().add(-down.x * PUSH, 0.0, -down.z * PUSH));
		if (s.horizontalCollision && now - LEAPT.getOrDefault(s, Long.MIN_VALUE / 2) >= LEAP_GAP) {
			leap(level, s, down);
		}
	}

	/** At a step: it leaps up the fall, and makes it or drops back. */
	private static void leap(ServerLevel level, Salmon s, Vec3 down) {
		LEAPT.put(s, level.getGameTime());
		boolean makes = level.getRandom().nextFloat() < LEAP_MAKE;
		double up = makes ? LEAP_UP : LEAP_UP * LEAP_SHORT;
		s.setDeltaMovement(-down.x * LEAP_ON, up, -down.z * LEAP_ON);
		s.needsSync = true;
		level.sendParticles(ParticleTypes.SPLASH, s.getX(), s.getY() + 0.3, s.getZ(), 12, 0.25, 0.1, 0.25, 0.1);
		level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.SALMON_FLOP, SoundSource.NEUTRAL, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
	}

	/** Shallow water over a bed it'd spawn on: gravel, or (far enough up) any floor. */
	private static boolean shallow(ServerLevel level, BlockPos at, boolean anyFloor) {
		BlockPos.MutableBlockPos p = at.mutable();
		for (int depth = 0; depth < SHALLOW; depth++) {
			p.move(0, -1, 0);
			BlockState floor = level.getBlockState(p);
			if (!floor.getFluidState().isEmpty()) {
				continue;
			}
			return anyFloor ? floor.isSolid() : floor.is(Blocks.GRAVEL);
		}
		return false;
	}

	/** {@code s} has spawned here (or the run's over and it never did): it's spent from now on. */
	private static void spend(ServerLevel level, Salmon s, boolean spawned) {
		s.setAttached(SPENT, level.getGameTime());
		s.removeAttached(RAN_FROM);
		AttributeInstance speed = s.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(SPENT_SLOWNESS) == null) {
			speed.addPermanentModifier(new AttributeModifier(SPENT_SLOWNESS, SPENT_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (spawned) {
			BlockPos at = s.blockPosition();
			Reach r = reach(level, at);
			put(level, at, new Reach(r.stock, r.brought, r.spawned + 1, r.dead, at.asLong(), r.season));
			level.sendParticles(ParticleTypes.BUBBLE, s.getX(), s.getY(), s.getZ(), 16, 0.4, 0.1, 0.4, 0.02);
			level.sendParticles(ParticleTypes.SPLASH, s.getX(), s.getY() + 0.5, s.getZ(), 10, 0.4, 0.1, 0.4, 0.1);
		}
	}

	/** A spent salmon wears out: a third of its health gone each third of {@link #SPENT_TICKS}, till it dies. */
	private static void wearOut(ServerLevel level, Salmon s, long since) {
		long third = SPENT_TICKS / 3;
		if (since > 0 && since % third < STEER_EVERY) {
			s.hurtServer(level, level.damageSources().starve(), s.getMaxHealth() / 3.0F + 0.01F);
		}
	}

	/** Near {@code p}: the year turns over for its reach, the run is brought in in autumn, and spring's young and banks come. */
	private static void seasons(ServerLevel level, ServerPlayer p, int season) {
		BlockPos here = p.blockPosition();
		Reach r = reach(level, here);
		if (r.season != season) {
			r = turn(level, here, r, season);
		}
		if (season == SPRING) {
			spring(level, p);
		} else if (season == AUTUMN) {
			long now = level.getGameTime();
			if (now - SCHOOLED.getOrDefault(p, Long.MIN_VALUE / 2) >= SCHOOL_EVERY) {
				SCHOOLED.put(p, now);
				school(level, p);
			}
			fishers(level, p);
		}
	}

	/**
	 * Lore ("The run", for Andy to approve): a river town's fishers go down to the run by day, and take from it but never
	 * all of it ({@link #FISHER_LEAVES} of a school are always let through to the beds).
	 */
	private static void fishers(ServerLevel level, ServerPlayer p) {
		long day = Math.floorMod(level.getOverworldClockTime(), 24000L);
		if (day < FISHER_FROM || day > FISHER_UNTIL) {
			return;
		}
		net.minecraft.world.phys.AABB round = p.getBoundingBox().inflate(FISHER_RANGE);
		java.util.List<Salmon> run = level.getEntitiesOfClass(Salmon.class, round, s -> s.isAlive() && s.hasAttached(RAN_FROM));
		if (run.isEmpty()) {
			return;
		}
		for (net.minecraft.world.entity.npc.villager.Villager v : level.getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class, round,
			v -> Fishermen.is(v) && v.isAlive() && !v.isSleeping() && !v.isPassenger())) {
			Salmon near = null;
			double best = Double.MAX_VALUE;
			for (Salmon s : run) {
				double d = s.distanceToSqr(v);
				if (d < best) {
					best = d;
					near = s;
				}
			}
			if (near == null) {
				continue;
			}
			if (best > FISHER_REACH * FISHER_REACH) {
				net.minecraft.world.entity.ai.behavior.BehaviorUtils.setWalkAndLookTargetMemories(v, near.blockPosition(), 0.6F, (int) FISHER_REACH - 2);
				continue;
			}
			v.getLookControl().setLookAt(near);
			Salmon caught = near;
			int school = level.getEntitiesOfClass(Salmon.class, near.getBoundingBox().inflate(8.0), s -> s.isAlive() && s.hasAttached(RAN_FROM)).size();
			if (school > FISHER_LEAVES && level.getRandom().nextFloat() < FISHER_TAKES) {
				v.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				level.sendParticles(ParticleTypes.SPLASH, caught.getX(), caught.getY() + 0.4, caught.getZ(), 14, 0.3, 0.1, 0.3, 0.1);
				level.playSound(null, caught.getX(), caught.getY(), caught.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
				caught.discard();
			}
		}
	}

	/** Whether {@code pos} lies on a river that carries a run (a river, in autumn, with Kitchen's seasons): salmon is cheap there then. */
	static boolean runRiver(ServerLevel level, BlockPos pos) {
		if (FishSeasons.season(level) != AUTUMN) {
			return false;
		}
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 4.0 * i;
			if (RiverRun.river(level, pos.offset((int) (Math.cos(a) * 32.0), 0, (int) (Math.sin(a) * 32.0)))) {
				return true;
			}
		}
		return RiverRun.river(level, pos);
	}

	/**
	 * The season has turned since {@code r} was last looked at. Into spring: the year turns, and next autumn's run is
	 * what spawned on its beds. Into autumn: a new run; if nobody was here in spring, the year turns now.
	 */
	private static Reach turn(ServerLevel level, BlockPos at, Reach r, int season) {
		Reach turned;
		if (r.season == -1) {
			turned = new Reach(STOCK_FIRST, 0, 0, 0, r.bed, season);
		} else if (season == SPRING) {
			turned = new Reach(stock(r), 0, r.spawned, r.dead, r.bed, season);
		} else if (season == AUTUMN && (r.spawned > 0 || r.dead > 0)) {
			turned = new Reach(stock(r), 0, 0, 0, r.bed, season);
		} else if (season == AUTUMN) {
			turned = new Reach(r.stock, 0, 0, 0, r.bed, season);
		} else {
			turned = new Reach(r.stock, r.brought, r.spawned, r.dead, r.bed, season);
		}
		put(level, at, turned);
		return turned;
	}

	/** Next run's size: what spawned on the reach's beds this one. */
	private static int stock(Reach r) {
		return Math.clamp((long) r.spawned * STOCK_PER_SPAWNED + STOCK_LEAST, STOCK_LEAST, STOCK_MOST);
	}

	/** Spring near {@code p}: each bed near that took sends its young down, and the banks where the spent died grow on. */
	private static void spring(ServerLevel level, ServerPlayer p) {
		Map<String, Reach> all = new HashMap<>(level.getAttachedOrCreate(REACHES));
		boolean changed = false;
		for (Map.Entry<String, Reach> e : Map.copyOf(all).entrySet()) {
			Reach r = e.getValue();
			if (r.bed == 0L || r.spawned <= 0 && r.dead <= 0) {
				continue;
			}
			BlockPos bed = BlockPos.of(r.bed);
			if (!bed.closerToCenterThan(p.position(), SPRING_NEAR) || !level.isLoaded(bed)) {
				continue;
			}
			RandomSource random = level.getRandom();
			for (int i = 0; i < Math.min(r.spawned, FRY_MOST); i++) {
				Salmon fry = EntityTypes.SALMON.create(level, EntitySpawnReason.NATURAL);
				if (fry != null) {
					fry.snapTo(bed.getX() + 0.5 + random.nextGaussian(), bed.getY() + 0.3, bed.getZ() + 0.5 + random.nextGaussian(),
						random.nextFloat() * 360.0F, 0.0F);
					fry.setComponent(DataComponents.SALMON_SIZE, Salmon.Variant.SMALL);
					if (level.getBlockState(fry.blockPosition()).getFluidState().isEmpty()) {
						continue;
					}
					level.addFreshEntity(fry);
				}
			}
			banks(level, bed, Math.min(r.dead * BANK_PER_DEAD, BANK_MOST), random);
			all.put(e.getKey(), new Reach(r.stock, r.brought, 0, 0, r.bed, r.season));
			changed = true;
		}
		if (changed) {
			level.setAttached(REACHES, all);
		}
	}

	/** {@code tries} times, a spot of bank near {@code bed} grows on: grass and ferns, now and then a berry bush. */
	private static void banks(ServerLevel level, BlockPos bed, int tries, RandomSource random) {
		for (int i = 0; i < tries; i++) {
			int x = bed.getX() + random.nextInt(BANK_RADIUS * 2 + 1) - BANK_RADIUS, z = bed.getZ() + random.nextInt(BANK_RADIUS * 2 + 1) - BANK_RADIUS;
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			BlockPos ground = top.below();
			BlockState state = level.getBlockState(ground);
			if (!state.is(Blocks.GRASS_BLOCK) || !level.getBlockState(top).isAir()) {
				continue;
			}
			if (random.nextInt(6) == 0) {
				level.setBlockAndUpdate(top, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1));
			} else if (state.getBlock() instanceof BonemealableBlock grow && grow.isValidBonemealTarget(level, ground, state, BonemealSource.INTERACTION)) {
				grow.performBonemeal(level, random, ground, state, BonemealSource.INTERACTION);
			}
		}
	}

	/** Autumn near {@code p}: a school of the run comes up the river near them, while the reach's run lasts. */
	private static void school(ServerLevel level, ServerPlayer p) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0, far = SCHOOL_NEAR + random.nextDouble() * (SCHOOL_FAR - SCHOOL_NEAR);
		int x = (int) Math.floor(p.getX() + Math.cos(angle) * far), z = (int) Math.floor(p.getZ() + Math.sin(angle) * far);
		BlockPos top = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z));
		BlockPos at = top.below(2);
		if (!level.isLoaded(at) || !RiverRun.river(level, at) || !level.getBlockState(at).getFluidState().isSource()
			|| !level.getBlockState(at.above()).getFluidState().isSource()) {
			return;
		}
		if (level.getEntitiesOfClass(Salmon.class, new net.minecraft.world.phys.AABB(at).inflate(32.0)).size() >= CROWD) {
			return;
		}
		Reach r = reach(level, at);
		int n = Math.min(SCHOOL_MIN + random.nextInt(SCHOOL_MAX - SCHOOL_MIN + 1), r.stock - r.brought);
		if (n <= 0) {
			return;
		}
		int made = 0;
		for (int i = 0; i < n; i++) {
			Salmon s = EntityTypes.SALMON.create(level, EntitySpawnReason.NATURAL);
			if (s == null) {
				continue;
			}
			s.snapTo(at.getX() + 0.5 + random.nextGaussian() * 0.8, at.getY() + 0.2, at.getZ() + 0.5 + random.nextGaussian() * 0.8,
				random.nextFloat() * 360.0F, 0.0F);
			s.setComponent(DataComponents.SALMON_SIZE, random.nextInt(4) == 0 ? Salmon.Variant.LARGE : Salmon.Variant.MEDIUM);
			if (level.getBlockState(s.blockPosition()).getFluidState().isEmpty()) {
				continue;
			}
			level.addFreshEntity(s);
			made++;
		}
		put(level, at, new Reach(r.stock, r.brought + made, r.spawned, r.dead, r.bed, r.season));
	}

	private static String key(BlockPos at) {
		return (at.getX() >> REACH_SHIFT) + "," + (at.getZ() >> REACH_SHIFT);
	}

	/** The reach {@code at} lies in (a fresh one, first year's run, if it's never been seen). */
	static Reach reach(ServerLevel level, BlockPos at) {
		Reach r = level.getAttachedOrCreate(REACHES).get(key(at));
		return r != null ? r : new Reach(STOCK_FIRST, 0, 0, 0, 0L, -1);
	}

	private static void put(ServerLevel level, BlockPos at, Reach r) {
		Map<String, Reach> all = new HashMap<>(level.getAttachedOrCreate(REACHES));
		all.put(key(at), r);
		level.setAttached(REACHES, all);
	}
}
