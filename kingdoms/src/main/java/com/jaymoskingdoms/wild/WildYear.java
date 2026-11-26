package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The wild's year (Andy's nature picks, 2026-11-26; {@link Nature}; Kitchen's seasons).
 * <ul>
 * <li><b>The autumn rut</b>: stags, bull moose and bison bellow, square off with another of their kind near and shove
 * ({@link #RUT_SHOVE}), and charge anyone who isn't sneaking within {@link #RUT_ANGRY}.</li>
 * <li><b>Spring young</b>: in spring two grown wild ones of a kind with no young near have one now and then
 * ({@link #BIRTH_EVERY}, never past {@link #BIRTH_CROWD} of the kind round there), and a mother charges anyone who comes
 * within {@link #MOTHER_NEAR} of her young ({@code nature/fierce_mothers}), or stamps and takes her young off
 * ({@code nature/shy_mothers}: the deer).</li>
 * <li><b>Bears sleep the winter</b>: in winter a bear finds a covered spot (under trees, an overhang, a cave) and sleeps
 * there till spring. A blow, or someone who isn't sneaking within {@link #DEN_WAKE}, wakes it angry; in spring it comes
 * out hungry.</li>
 * <li><b>Fewer birds in winter</b>: the summer birds and insects ({@code nature/summer_birds}) mostly aren't about in a
 * winter that has seasons ({@link #WINTER_GONE}), and in spring a flock comes in now and then.</li>
 * <li><b>Frogs</b>: on spring nights after rain frogs lay frogspawn in the shallows ({@link #SPAWN_MOST} round a pond);
 * they eat crickets too (the {@code minecraft:frog_food} tag).</li>
 * <li><b>Seeds carried</b>: an animal that's been at a berry bush leaves a new one where it wanders, now and then; and grass
 * nothing grazes ({@link #SCRUB_UNGRAZED}), away from any village, slowly takes saplings: a pasture left alone goes back
 * to scrub.</li>
 * </ul>
 */
final class WildYear {
	static final int STEP_EVERY = 20;
	static final double RUT_RIVAL = 10.0, RUT_ANGRY = 6.0, CHARGE_REACH = 2.2, CHARGE_PACE = 1.6;
	static final float RUT_SHOVE = 1.0F, CHARGE_BLOW = 4.0F;
	static final long CHARGE_TICKS = 20L * 6;
	static final long BIRTH_EVERY = 20L * 60 * 3;
	static final int BIRTH_CROWD = 8;
	static final double MOTHER_NEAR = 6.0;
	static final double DEN_LOOK = 24.0, DEN_WAKE = 3.0;
	static final float WINTER_GONE = 0.7F;
	static final int SPAWN_MOST = 3;
	static final double SEED_FAR = 24.0;
	static final long BERRY_KEPT = 20L * 60 * 5;
	static final double SCRUB_UNGRAZED = 32.0;
	static final int SCRUB_LOOK = 48;

	/** A bear asleep for the winter: where its den is (a BlockPos as a long). */
	static final AttachmentType<Long> DEN = AttachmentRegistry.create(JaymosKingdoms.id("bear_den"), b -> b.persistent(Codec.LONG));

	private record Charge(LivingEntity at, long until) {
	}

	private static final Map<Mob, Charge> CHARGES = new WeakHashMap<>();
	private static final Map<Mob, Long> BORN = new WeakHashMap<>();
	private static final Map<Mob, BlockPos> BERRIES = new WeakHashMap<>();
	private static final Map<Mob, Long> BERRY_AT = new WeakHashMap<>();
	private static final Set<Entity> LOOKED = java.util.Collections.newSetFromMap(new WeakHashMap<>());

	private WildYear() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WildYear::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob bear && bear.hasAttached(DEN)) {
				wake(bear, source.getEntity() instanceof LivingEntity l ? l : null);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob m && Nature.is(m, Nature.SUMMER_BIRDS) && LOOKED.add(m) && !m.isPersistenceRequired() && Nature.wild(m)
				&& Nature.season(level, "winter") && level.getBiome(m.blockPosition()).value().getBaseTemperature() < 0.9F
				&& level.getRandom().nextFloat() < WINTER_GONE) {
				// (gone for the winter)
				m.discard();
			}
		});
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % STEP_EVERY != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		String season = Nature.season(overworld);
		charges(overworld.getGameTime());
		if (season == null) {
			return;
		}
		if ("autumn".equals(season)) {
			for (Mob m : Nature.of(Nature.RUTTING)) {
				if (Nature.free(m) && !CHARGES.containsKey(m) && m.level() instanceof ServerLevel level) {
					rut(level, m);
				}
			}
		}
		if (t % 40 == 0) {
			mothers(Nature.of(Nature.FIERCE_MOTHERS), true, "spring".equals(season));
			mothers(Nature.of(Nature.SHY_MOTHERS), false, "spring".equals(season));
		}
		if (t % 100 == 0) {
			for (Mob bear : Nature.of(Nature.DENNING)) {
				if (bear.level() instanceof ServerLevel level) {
					den(level, bear, "winter".equals(season));
				}
			}
		}
		if (t % 200 == 0) {
			for (Mob m : Nature.of(Nature.BERRY_EATERS)) {
				if (m.isAlive() && Nature.wild(m) && m.level() instanceof ServerLevel level) {
					seeds(level, m);
				}
			}
		}
		if (t % 1200 == 0) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (p.level() instanceof ServerLevel level) {
					if (t % 6000 == 0 && !"winter".equals(season)) {
						scrub(level, p);
					}
					if ("spring".equals(season)) {
						frogspawn(level, p);
						if (level.getRandom().nextInt(4) == 0) {
							flockIn(level, p);
						}
					}
				}
			}
		}
	}

	// ---- charges: a rutting male, a mother --------------------------------------------------------------------------

	static void charge(Mob m, LivingEntity at) {
		CHARGES.put(m, new Charge(at, m.level().getGameTime() + CHARGE_TICKS));
	}

	private static void charges(long now) {
		for (Map.Entry<Mob, Charge> e : List.copyOf(CHARGES.entrySet())) {
			Mob m = e.getKey();
			Charge c = e.getValue();
			if (!m.isAlive() || !c.at.isAlive() || now > c.until || !(m.level() instanceof ServerLevel level) || m.distanceTo(c.at) > 24.0) {
				CHARGES.remove(m);
				continue;
			}
			if (m.distanceTo(c.at) > CHARGE_REACH) {
				m.getNavigation().moveTo(c.at, CHARGE_PACE);
				continue;
			}
			boolean rival = c.at.getType() == m.getType();
			Nature.strike(level, m, c.at, rival ? RUT_SHOVE : CHARGE_BLOW);
			Vec3 push = c.at.position().subtract(m.position()).multiply(1.0, 0.0, 1.0).normalize().scale(rival ? 0.5 : 0.9);
			c.at.push(push.x, 0.25, push.z);
			c.at.needsSync = true;
			level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.NEUTRAL, 0.8F, 0.8F);
			CHARGES.remove(m);
		}
	}

	private static void rut(ServerLevel level, Mob m) {
		RandomSource random = level.getRandom();
		if (random.nextInt(8) == 0) {
			m.playAmbientSound();
		}
		Player person = level.getNearestPlayer(m, RUT_ANGRY);
		if (person != null && !person.isShiftKeyDown() && !person.isCreative() && !person.isSpectator()) {
			charge(m, person);
			return;
		}
		if (random.nextInt(10) != 0) {
			return;
		}
		for (Mob rival : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(RUT_RIVAL), o -> o != m && o.getType() == m.getType() && Nature.free(o))) {
			charge(m, rival);
			charge(rival, m);
			return;
		}
	}

	/** Spring births; and mothers with young near keep people off them. */
	private static void mothers(List<Mob> kind, boolean fierce, boolean spring) {
		for (Mob m : kind) {
			if (!Nature.free(m) || !(m.level() instanceof ServerLevel level) || CHARGES.containsKey(m)) {
				continue;
			}
			Mob young = null;
			for (Mob y : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(8.0), o -> o.getType() == m.getType() && o.isBaby() && o.isAlive())) {
				young = y;
				break;
			}
			if (young != null) {
				Player near = level.getNearestPlayer(young, MOTHER_NEAR);
				if (near != null && !near.isCreative() && !near.isSpectator()) {
					if (fierce) {
						charge(m, near);
					} else if (m instanceof PathfinderMob pm) {
						m.playAmbientSound();
						Vec3 to = Nature.away(pm, near.position(), 16);
						if (to != null) {
							Nature.go(m, to, 1.5);
							Nature.go(young, to, 1.5);
						}
					}
				}
			} else if (spring) {
				birth(level, m);
			}
		}
	}

	private static void birth(ServerLevel level, Mob m) {
		long now = level.getGameTime();
		if (!(m instanceof AgeableMob a) || now - BORN.getOrDefault(m, Long.MIN_VALUE / 2) < BIRTH_EVERY || level.getRandom().nextInt(4) != 0) {
			return;
		}
		BORN.put(m, now);
		List<Mob> kin = level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(32.0), o -> o.getType() == m.getType() && o.isAlive());
		if (kin.size() >= BIRTH_CROWD || kin.stream().noneMatch(o -> o != m && !o.isBaby() && o.distanceTo(m) < 12.0)) {
			return;
		}
		Entity baby = m.getType().create(level, EntitySpawnReason.BREEDING);
		if (baby instanceof AgeableMob b) {
			b.setAge(AgeableMob.BABY_START_AGE);
			b.snapTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0.0F);
			level.addFreshEntity(b);
			level.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight(), m.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		}
		a.setAge(6000);
	}

	// ---- dens ------------------------------------------------------------------------------------------------------

	private static void den(ServerLevel level, Mob bear, boolean winter) {
		Long den = bear.getAttached(DEN);
		if (den != null) {
			if (!winter) {
				// (spring: out it comes, hungry)
				wake(bear, null);
				FedPredators.starve(bear);
				return;
			}
			Player near = level.getNearestPlayer(bear, DEN_WAKE);
			if (near != null && !near.isShiftKeyDown() && !near.isCreative() && !near.isSpectator()) {
				wake(bear, near);
			}
			return;
		}
		if (!winter || bear.isBaby() || !Nature.wild(bear) || bear.getTarget() != null) {
			return;
		}
		BlockPos at = bear.blockPosition();
		if (!level.canSeeSky(at.above())) {
			bear.getNavigation().stop();
			bear.setAttached(DEN, at.asLong());
			bear.setNoAi(true);
			return;
		}
		BlockPos cover = cover(level, at);
		if (cover != null) {
			Nature.go(bear, Vec3.atBottomCenterOf(cover), 0.8);
		}
	}

	private static @Nullable BlockPos cover(ServerLevel level, BlockPos at) {
		RandomSource random = level.getRandom();
		for (int i = 0; i < 12; i++) {
			BlockPos p = at.offset(random.nextInt((int) DEN_LOOK * 2 + 1) - (int) DEN_LOOK, random.nextInt(9) - 4, random.nextInt((int) DEN_LOOK * 2 + 1) - (int) DEN_LOOK);
			if (level.isLoaded(p) && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolid()
				&& !level.canSeeSky(p.above())) {
				return p;
			}
		}
		return null;
	}

	private static void wake(Mob bear, @Nullable LivingEntity by) {
		bear.removeAttached(DEN);
		bear.setNoAi(false);
		if (by != null && !(by instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			bear.setTarget(by);
		}
	}

	// ---- birds, frogs, seeds ----------------------------------------------------------------------------------------

	/** Spring: a flock of the summer birds comes in near {@code p}. */
	private static void flockIn(ServerLevel level, ServerPlayer p) {
		List<Mob> about = Nature.of(Nature.SUMMER_BIRDS);
		net.minecraft.world.entity.EntityType<?> kind = null;
		for (var holder : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(Nature.SUMMER_BIRDS)) {
			if (net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(holder.value()).getPath().equals("duck")) {
				kind = holder.value();
			}
		}
		if (kind == null || about.size() > 12) {
			return;
		}
		RandomSource random = level.getRandom();
		double a = random.nextDouble() * Math.PI * 2.0;
		double x = p.getX() + Math.cos(a) * 40.0, z = p.getZ() + Math.sin(a) * 40.0;
		BlockPos top = Nature.top(level, (int) x, (int) z);
		if (!level.isLoaded(top)) {
			return;
		}
		int n = 3 + random.nextInt(4);
		for (int i = 0; i < n; i++) {
			Entity bird = kind.create(level, EntitySpawnReason.NATURAL);
			if (bird != null) {
				bird.snapTo(x + random.nextGaussian() * 2.0, top.getY() + 18.0 + random.nextGaussian(), z + random.nextGaussian() * 2.0, random.nextFloat() * 360.0F, 0.0F);
				level.addFreshEntity(bird);
			}
		}
	}

	/** Spring nights after rain: frogs near water lay spawn in the shallows. */
	private static void frogspawn(ServerLevel level, ServerPlayer p) {
		if (!level.isDarkOutside() || Nature.dryDays(level) > 1) {
			return;
		}
		for (Mob frog : level.getEntitiesOfClass(net.minecraft.world.entity.animal.frog.Frog.class, p.getBoundingBox().inflate(Nature.NEAR), f -> f.isAlive() && !f.isBaby())) {
			if (level.getRandom().nextInt(3) != 0) {
				continue;
			}
			BlockPos at = frog.blockPosition();
			int spawn = 0;
			BlockPos open = null;
			for (BlockPos q : BlockPos.betweenClosed(at.offset(-6, -2, -6), at.offset(6, 2, 6))) {
				BlockState s = level.getBlockState(q);
				if (s.is(Blocks.FROGSPAWN)) {
					spawn++;
				} else if (open == null && s.isAir() && level.getFluidState(q.below()).is(Fluids.WATER) && level.getFluidState(q.below()).isSource()) {
					open = q.immutable();
				}
			}
			if (spawn < SPAWN_MOST && open != null) {
				level.setBlockAndUpdate(open, Blocks.FROGSPAWN.defaultBlockState());
			}
		}
	}

	/** One that's been at a berry bush leaves a new one where it wanders, now and then. */
	private static void seeds(ServerLevel level, Mob m) {
		BlockPos at = m.blockPosition();
		long now = level.getGameTime();
		for (BlockPos q : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
			BlockState s = level.getBlockState(q);
			if (s.is(Blocks.SWEET_BERRY_BUSH) && s.getValue(SweetBerryBushBlock.AGE) >= 2) {
				BERRIES.put(m, q.immutable());
				BERRY_AT.put(m, now);
				return;
			}
		}
		BlockPos ate = BERRIES.get(m);
		if (ate == null || now - BERRY_AT.getOrDefault(m, 0L) > BERRY_KEPT || ate.closerThan(at, SEED_FAR) || level.getRandom().nextInt(6) != 0) {
			return;
		}
		BlockPos top = Nature.top(level, at.getX(), at.getZ());
		if (!level.getBlockState(top.below()).is(Blocks.GRASS_BLOCK) || !level.getBlockState(top).isAir()) {
			return;
		}
		for (BlockPos q : BlockPos.betweenClosed(top.offset(-8, -3, -8), top.offset(8, 3, 8))) {
			if (level.getBlockState(q).is(Blocks.SWEET_BERRY_BUSH)) {
				return;
			}
		}
		level.setBlockAndUpdate(top, Blocks.SWEET_BERRY_BUSH.defaultBlockState());
		BERRIES.remove(m);
	}

	/** Grass nothing grazes, away from villages, takes a sapling now and then. */
	private static void scrub(ServerLevel level, ServerPlayer p) {
		RandomSource random = level.getRandom();
		BlockPos top = Nature.top(level, p.getBlockX() + random.nextInt(SCRUB_LOOK * 2 + 1) - SCRUB_LOOK, p.getBlockZ() + random.nextInt(SCRUB_LOOK * 2 + 1) - SCRUB_LOOK);
		BlockState plant = level.getBlockState(top);
		if (!level.isLoaded(top) || !level.getBlockState(top.below()).is(Blocks.GRASS_BLOCK) || !(plant.isAir() || plant.is(Blocks.SHORT_GRASS))
			|| level.isVillage(top) || !level.canSeeSky(top)) {
			return;
		}
		var biome = level.getBiome(top);
		if (!biome.is(net.minecraft.tags.BiomeTags.IS_FOREST) && !biome.is(net.minecraft.world.level.biome.Biomes.PLAINS)) {
			return;
		}
		boolean grazed = !level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(top).inflate(SCRUB_UNGRAZED),
			m -> m instanceof net.minecraft.world.entity.animal.Animal && (Nature.is(m, Nature.GRAZERS) || m instanceof net.minecraft.world.entity.animal.cow.Cow
				|| m instanceof net.minecraft.world.entity.animal.sheep.Sheep || m instanceof net.minecraft.world.entity.animal.equine.AbstractHorse)).isEmpty();
		if (grazed) {
			return;
		}
		level.setBlockAndUpdate(top, (biome.is(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST) ? Blocks.BIRCH_SAPLING : Blocks.OAK_SAPLING).defaultBlockState());
	}
}
