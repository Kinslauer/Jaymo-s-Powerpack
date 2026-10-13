package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.FallingTree;
import com.jaymoskingdoms.Trees;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.12) Tornadoes on the ground. Each one wanders under its storm, pulls in and spins everything near it, lifts
 * what's in its core and flings it out, and (unless the {@link Disasters#DISASTER_DAMAGE} rule is off) tears up the
 * land in its path: plants and leaves, glass, wood, wool, roofs; a monster tears up stone too. What it tears loose
 * is thrown as falling blocks (the rest smashed). Settlements put back their ruined buildings in time ({@code Rebuilds}).
 *
 * <p>Townsfolk near one run home to their beds. Jaymo's Powers' Wind users (tagged by Powers) ride its winds instead
 * of being thrown, and a Cyclone Lance fired into one shoves it off course.
 */
public final class Tornadoes {
	/** By size (dust devil, tornado, monster): the core's radius, how far the winds pull, the funnel's height. */
	static final float[] CORE = {2.5F, 6.0F, 11.0F}, PULL = {7.0F, 20.0F, 34.0F}, HEIGHT = {22.0F, 75.0F, 115.0F};
	/** Lifetimes (ticks), min and max. */
	static final int[] LIFE_MIN = {500, 2400, 3600}, LIFE_MAX = {1200, 4800, 6000};
	/** How hard the winds pull in, spin round and lift (blocks/tick², at full strength). */
	static final double[] INWARD = {0.03, 0.07, 0.1}, SPIN = {0.06, 0.13, 0.18}, LIFT = {0.1, 0.2, 0.26};
	/** How high above the ground the winds carry a player before throwing them out (Wind users ride higher). */
	static final double[] CARRY = {4.0, 22.0, 38.0};
	/** Blocks torn up per tick at full strength. */
	static final int[] BLOCKS_PER_TICK = {0, 5, 12};
	/** How hard a block can be for it to tear it up (axe, hoe, plants), and for stone (a monster only). */
	static final float[] SOFT_HARDNESS = {0.0F, 2.5F, 3.0F}, STONE_HARDNESS = {-1.0F, -1.0F, 2.0F};
	/** At most this many torn-up blocks in the air at once per tornado (the rest are smashed). */
	static final int THROWN_MOST = 40;
	/** (0.6.12) Torn-up blocks and trees ride its winds this long (ticks), then fall like anything else. */
	static final int THROWN_RIDE = 50;
	/** (0.6.12) By size: the chance a tree it strikes is torn out whole (at full strength), its most logs, and the most
	 * torn-up trees in the air at once. */
	static final float[] UPROOT = {0.0F, 0.12F, 0.45F};
	static final int[] TREE_LOGS = {0, 12, 64}, TREES_MOST = {0, 3, 8};
	/** The share of smashed blocks that drop as items. */
	static final float SMASHED_DROP = 0.25F;
	/** The fastest anything moves in its winds (blocks/tick). */
	static final double FASTEST = 2.2;
	/** Damage to the living caught in its core (per second) and to anything a thrown block hits. */
	static final float CORE_DAMAGE = 1.0F, THROWN_DAMAGE = 1.5F;
	/** How long it takes to touch down or rope out (ticks). */
	static final int FORM_TICKS = 200;
	/** How fast it weaves about its course, and how far from its storm's middle it may stray (share of the radius). */
	static final double WANDER = 0.035, LEASH = 0.45;
	/** Townsfolk this near one run home (or away) (by size). */
	static final double[] SHELTER = {0.0, 110.0, 150.0};
	static final double HOME_NEAR = 64.0;
	/** Players hear of a touchdown within this distance. */
	static final double NEWS_RANGE = 320.0;
	/** Jaymo's Powers' tags: a Wind user, and a Cyclone Lance cast (followed by the game time). */
	static final String WIND_TAG = "jaymospowers_wind", LANCE_TAG = "jaymospowers_wind_lance:";
	/** A lance shoves a tornado within this range and this close to its line, this hard, wearing off by this per tick. */
	static final double LANCE_RANGE = 48.0, LANCE_AIM = 10.0, LANCE_SHOVE = 0.22, SHOVE_DECAY = 0.985;

	static final List<Tornado> SERVER = new ArrayList<>();
	/** Each tornado's thrown blocks (by tornado id), to cap them. */
	private static final java.util.Map<Integer, List<FallingBlockEntity>> THROWN = new java.util.HashMap<>();
	/** Each tornado's torn-up trees in the air (by tornado id), to cap them. */
	private static final java.util.Map<Integer, List<FallingTree>> TREES = new java.util.HashMap<>();

	private Tornadoes() {
	}

	/** Each Wind user's last Cyclone Lance already counted (so one cast shoves once). */
	private static final java.util.Map<java.util.UUID, Long> LANCED = new java.util.HashMap<>();

	static void clear() {
		SERVER.clear();
		THROWN.clear();
		TREES.clear();
		LANCED.clear();
	}

	/** A tornado of {@code size} touches down at (x, z) (under {@code storm}, or alone for a dust devil). */
	static Tornado spawn(ServerLevel level, int size, @Nullable Storm storm, double x, double z, RandomSource random) {
		int life = Mth.nextInt(random, LIFE_MIN[size], LIFE_MAX[size]);
		double vx, vz;
		if (storm != null) {
			vx = storm.vx;
			vz = storm.vz;
		} else {
			vx = Math.cos(Storms.windAngle) * Storms.windSpeed;
			vz = Math.sin(Storms.windAngle) * Storms.windSpeed;
		}
		double y = ground(level, x, z, level.getSeaLevel());
		Tornado t = new Tornado(Storms.nextId(), size, storm == null ? -1 : storm.id, x, y, z, vx, vz, 0, life, random.nextFloat() * 100.0F);
		SERVER.add(t);
		if (size != Tornado.DUST_DEVIL) {
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceToSqr(x, p.getY(), z) < NEWS_RANGE * NEWS_RANGE) {
					p.sendSystemMessage(Component.translatable("disaster.jaymoskingdoms.touchdown." + Tornado.NAMES[size],
						Storms.direction(p.getX(), p.getZ(), x, z)));
				}
			}
		}
		return t;
	}

	static void tick(ServerLevel level) {
		RandomSource random = level.getRandom();
		long now = level.getGameTime();
		for (Tornado t : SERVER) {
			Storm storm = storm(t);
			steer(t, storm);
			lance(level, t, now);
			t.x += t.vx;
			t.z += t.vz;
			t.age++;
			BlockPos column = BlockPos.containing(t.x, t.y, t.z);
			if (!level.isLoaded(column)) {
				continue;
			}
			t.y = ground(level, t.x, t.z, t.y);
			winds(level, t, now);
			if (level.getGameRules().get(Disasters.DISASTER_DAMAGE)) {
				tear(level, t, random);
			}
			if (now % 40 == t.id % 40) {
				roar(level, t, random);
			}
			if (now % 20 == 0 && SHELTER[t.size] > 0.0) {
				shelter(level, t);
			}
		}
		SERVER.removeIf(t -> {
			boolean gone = t.age >= t.life || t.storm >= 0 && storm(t) == null && t.age < t.life - FORM_TICKS && cut(t);
			if (gone) {
				THROWN.remove(t.id);
				TREES.remove(t.id);
			}
			return gone;
		});
	}

	/** Its storm broke up under it: it ropes out now. */
	private static boolean cut(Tornado t) {
		t.life = t.age + FORM_TICKS;
		return false;
	}

	private static @Nullable Storm storm(Tornado t) {
		for (Storm s : Storms.SERVER) {
			if (s.id == t.storm) {
				return s;
			}
		}
		return null;
	}

	/** Its course: its storm's drift, a weave of its own, kept under the storm, plus any shove. */
	private static void steer(Tornado t, @Nullable Storm storm) {
		double bx = storm != null ? storm.vx : Math.cos(Storms.windAngle) * Storms.windSpeed;
		double bz = storm != null ? storm.vz : Math.sin(Storms.windAngle) * Storms.windSpeed;
		double weave = Math.sin(t.age / 90.0 + t.seed) * WANDER, weave2 = Math.cos(t.age / 130.0 + t.seed * 1.7) * WANDER;
		double vx = bx + weave, vz = bz + weave2;
		if (storm != null) {
			double dx = storm.x - t.x, dz = storm.z - t.z, d = Math.sqrt(dx * dx + dz * dz), leash = storm.radius * LEASH;
			if (d > leash) {
				vx += dx / d * 0.02;
				vz += dz / d * 0.02;
			}
		}
		t.vx = vx + t.shoveX;
		t.vz = vz + t.shoveZ;
		t.shoveX *= SHOVE_DECAY;
		t.shoveZ *= SHOVE_DECAY;
	}

	/** The ground under (x, z) (leaves and plants don't count), or {@code was} if it isn't loaded. */
	static double ground(ServerLevel level, double x, double z, double was) {
		BlockPos column = BlockPos.containing(x, level.getMinY(), z);
		if (!level.isLoaded(column)) {
			return was;
		}
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
	}

	/** Everything in reach is pulled in and spun round; what's in the core is lifted and, high up, thrown out. */
	private static void winds(ServerLevel level, Tornado t, long now) {
		int size = t.size;
		double pull = t.pull(), height = t.height(), f = t.formed();
		AABB box = new AABB(t.x - pull, t.y - 4.0, t.z - pull, t.x + pull, t.y + height, t.z + pull);
		for (Entity e : level.getEntities((Entity)null, box, e -> e.isAlive() && !e.isSpectator()
			&& !(e instanceof Player p && (p.isCreative() || p.getAbilities().flying)) && !e.isPassenger() && !letGo(e))) {
			double dx = e.getX() - t.x, dz = e.getZ() - t.z, d = Math.sqrt(dx * dx + dz * dz);
			if (d > pull || d < 1.0E-3) {
				continue;
			}
			double h = e.getY() - t.y;
			double nx = dx / d, nz = dz / d;
			double reach = 1.0 - d / pull;
			double core = t.radiusAt((float)Math.max(0.0, h)) * 1.3;
			boolean wind = e.entityTags().contains(WIND_TAG);
			boolean player = e instanceof Player;
			double carry = wind ? height * 0.9 : player ? CARRY[size] : height * 0.7;
			Vec3 v = e.getDeltaMovement().scale(0.92);
			double in = INWARD[size] * reach * f, spin = SPIN[size] * (0.4 + reach) * f;
			v = v.add(-nx * in - nz * spin, 0.0, -nz * in + nx * spin);
			if (d < core) {
				if (h < carry) {
					v = v.add(0.0, LIFT[size] * f * (wind ? 1.3 : 1.0) + 0.08, 0.0);
				} else {
					// thrown out at the top (a Wind user simply leaves the winds there)
					double out = wind ? 0.3 : 0.9 + size * 0.4;
					v = new Vec3(v.x + nx * out, wind ? 0.6 : 0.25, v.z + nz * out);
				}
				if (!wind && e instanceof LivingEntity living && now % 20 == 0 && size > Tornado.DUST_DEVIL) {
					living.hurtServer(level, level.damageSources().flyIntoWall(), CORE_DAMAGE * size);
				}
				if (wind) {
					e.resetFallDistance();
				}
			}
			double speed = v.length();
			if (speed > FASTEST) {
				v = v.scale(FASTEST / speed);
			}
			e.setDeltaMovement(v);
			e.syncVelocity = true;
		}
	}

	/** (0.6.12) A torn-up block or tree it has carried long enough (or a tree only toppling): it falls on its own now. */
	private static boolean letGo(Entity e) {
		if (e instanceof FallingBlockEntity block) {
			return block.time > THROWN_RIDE;
		}
		if (e instanceof FallingTree tree) {
			int flown = tree.flown();
			return flown < 0 || flown > THROWN_RIDE;
		}
		return false;
	}

	/** It tears up a few blocks in its path. */
	private static void tear(ServerLevel level, Tornado t, RandomSource random) {
		int tries = Math.round(BLOCKS_PER_TICK[t.size] * t.formed());
		if (tries <= 0) {
			return;
		}
		List<FallingBlockEntity> thrown = THROWN.computeIfAbsent(t.id, k -> new ArrayList<>());
		thrown.removeIf(e -> !e.isAlive());
		List<FallingTree> trees = TREES.computeIfAbsent(t.id, k -> new ArrayList<>());
		trees.removeIf(e -> !e.isAlive());
		double swath = t.core() * 1.4;
		for (int i = 0; i < tries; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * swath;
			int x = Mth.floor(t.x + Math.cos(a) * r), z = Mth.floor(t.z + Math.sin(a) * r);
			BlockPos column = new BlockPos(x, level.getMinY(), z);
			if (!level.isLoaded(column)) {
				continue;
			}
			BlockPos pos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, column).below();
			if (pos.getY() < t.y - 6) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (!breakable(level, pos, state, t.size)) {
				continue;
			}
			double nx = (x - t.x) / Math.max(0.5, r), nz = (z - t.z) / Math.max(0.5, r);
			if ((state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) && trees.size() < TREES_MOST[t.size]
				&& random.nextFloat() < UPROOT[t.size] * t.formed()) {
				FallingTree tree = uproot(level, t, pos, nx, nz, random);
				if (tree != null) {
					trees.add(tree);
					continue;
				}
			}
			if (state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty()) {
				level.destroyBlock(pos, false);
			} else if (state.getSoundType() == SoundType.GLASS) {
				level.destroyBlock(pos, false);
			} else if (thrown.size() < THROWN_MOST && !state.getFluidState().isSource()) {
				FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
				block.setDeltaMovement(-nz * 0.6 + nx * 0.1, 0.7 + random.nextDouble() * 0.5, nx * 0.6 + nz * 0.1);
				block.setHurtsEntities(THROWN_DAMAGE, 6);
				block.syncVelocity = true;
				thrown.add(block);
			} else {
				level.destroyBlock(pos, random.nextFloat() < SMASHED_DROP);
			}
		}
	}

	/**
	 * (0.6.12) The tree {@code hit} (a leaf or log at the top of its column) belongs to is torn out whole and flung round
	 * its winds ({@code nx, nz}: out from its middle), if it's small enough for this tornado. Null if it isn't.
	 */
	private static @Nullable FallingTree uproot(ServerLevel level, Tornado t, BlockPos hit, double nx, double nz, RandomSource random) {
		BlockPos log = null;
		double best = Double.MAX_VALUE;
		for (BlockPos q : BlockPos.betweenClosed(hit.offset(-2, -8, -2), hit.offset(2, 0, 2))) {
			double d = q.distSqr(hit);
			if (d < best && level.getBlockState(q).is(BlockTags.LOGS)) {
				best = d;
				log = q.immutable();
			}
		}
		if (log == null) {
			return null;
		}
		BlockPos foot = log;
		while (foot.getY() > level.getMinY() && level.getBlockState(foot.below()).is(BlockTags.LOGS)) {
			foot = foot.below();
		}
		double power = 1.0 + (t.size - Tornado.TORNADO) * 0.35;
		Vec3 v = new Vec3(-nz * 0.55 + nx * 0.1, 0.55 + random.nextDouble() * 0.35, nx * 0.55 + nz * 0.1).scale(power);
		float heading = (float)Math.atan2(v.z, v.x), tumble = 4.0F + random.nextFloat() * 6.0F;
		return Trees.uproot(level, foot, TREE_LOGS[t.size], v, heading, tumble);
	}

	/** Whether a tornado of {@code size} tears up this block. */
	static boolean breakable(ServerLevel level, BlockPos pos, BlockState state, int size) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty() && state.canBeReplaced()) {
			return false;
		}
		if (state.is(Disasters.STORM_BELL) || state.is(Disasters.WEATHERVANE)) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F) {
			return false;
		}
		boolean plant = state.canBeReplaced() || state.getCollisionShape(level, pos).isEmpty() && hardness == 0.0F;
		if (size == Tornado.DUST_DEVIL) {
			return plant && !state.is(BlockTags.CROPS);
		}
		if (plant || state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL) || state.getSoundType() == SoundType.GLASS) {
			return true;
		}
		if ((state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.MINEABLE_WITH_HOE)) && hardness <= SOFT_HARDNESS[size]) {
			return true;
		}
		return state.is(BlockTags.MINEABLE_WITH_PICKAXE) && hardness <= STONE_HARDNESS[size];
	}

	/** Its roar, heard a long way off. */
	private static void roar(ServerLevel level, Tornado t, RandomSource random) {
		float volume = 3.0F + t.size * 3.0F;
		float pitch = 0.45F + (2 - t.size) * 0.12F + random.nextFloat() * 0.1F;
		level.playSound(null, t.x, t.y + 4.0, t.z, SoundEvents.ELYTRA_FLYING, SoundSource.WEATHER, volume * t.formed(), pitch);
		if (t.size > Tornado.DUST_DEVIL && random.nextInt(3) == 0) {
			level.playSound(null, t.x, t.y + 8.0, t.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.WEATHER, volume * 0.6F, 0.5F);
		}
	}

	/** Villagers near it (MCA's too) run home to their beds if home is near, else away from it. */
	private static void shelter(ServerLevel level, Tornado t) {
		double range = SHELTER[t.size];
		AABB box = new AABB(t.x - range, t.y - 30.0, t.z - range, t.x + range, t.y + 40.0, t.z + range);
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, Villager::isAlive)) {
			Optional<GlobalPos> home = v.getBrain().getMemoryInternal(MemoryModuleType.HOME);
			BlockPos to;
			if (home != null && home.isPresent() && home.get().dimension() == level.dimension() && home.get().pos().closerThan(v.blockPosition(), HOME_NEAR)) {
				to = home.get().pos();
			} else {
				Vec3 away = v.position().subtract(t.x, v.getY(), t.z);
				if (away.lengthSqr() < 1.0E-3) {
					away = new Vec3(1.0, 0.0, 0.0);
				}
				Vec3 at = v.position().add(away.normalize().scale(20.0));
				int x = Mth.floor(at.x), z = Mth.floor(at.z);
				to = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			}
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, 0.75F, 1);
		}
	}

	/** A Wind user's Cyclone Lance, cast just now toward this tornado, shoves it the way they were looking. */
	private static void lance(ServerLevel level, Tornado t, long now) {
		if (now % 2 != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(t.x, p.getY(), t.z) > LANCE_RANGE * LANCE_RANGE) {
				continue;
			}
			long cast = -1;
			for (String tag : p.entityTags()) {
				if (tag.startsWith(LANCE_TAG)) {
					try {
						cast = Long.parseLong(tag.substring(LANCE_TAG.length()));
					} catch (NumberFormatException ignored) {
						// not ours
					}
				}
			}
			if (cast < 0 || now - cast > 2 || Long.valueOf(cast).equals(LANCED.get(p.getUUID()))) {
				continue;
			}
			Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
			if (look.lengthSqr() < 1.0E-4) {
				continue;
			}
			look = look.normalize();
			Vec3 to = new Vec3(t.x - p.getX(), 0.0, t.z - p.getZ());
			double along = to.dot(look);
			double off = to.subtract(look.scale(along)).length();
			if (along > 0.0 && off < LANCE_AIM + t.core()) {
				double shove = LANCE_SHOVE / (1 + t.size * 0.5);
				LANCED.put(p.getUUID(), cast);
				t.shoveX += look.x * shove;
				t.shoveZ += look.z * shove;
				level.playSound(null, t.x, t.y + 6.0, t.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 4.0F, 0.7F);
			}
		}
	}
}
