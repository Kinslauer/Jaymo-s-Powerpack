package com.jaymoskingdoms;

import com.jaymoskingdoms.reaped.Reaped;
import java.util.ArrayList;
import java.util.List;
import com.jaymoskingdoms.sailing.Fleet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.spider.Spider;
import org.jspecify.annotations.Nullable;

/**
 * What the Reaper's host ({@link Reaped}, {@link ZombieHordes}) does beyond walking and killing:
 * <ul>
 * <li><b>Riders</b> ({@link #mount}): part of a host rides skeleton horses, and some of its pillagers ride spiders, which
 * take them straight up a wall. Only the dead ride spiders (Andy). A horse whose rider falls is left for whoever wants it.</li>
 * <li><b>The reaped evoker raises</b> ({@link #raise}): where a living evoker calls vexes, it lifts the bodies lying near
 * it (Jaymo's Kitchen's corpses) back into the fight: a dead illager as the same kind of reaped, anything else as the old
 * dead it would rise as for a necromancer. Without Jaymo's Kitchen it keeps its vexes.</li>
 * <li><b>A siege engine</b> ({@link #engine}): a big host with a seat to march on pushes a mangonel there and batters it.</li>
 * <li><b>A long ship</b> ({@link #longship}): by the water the host comes in a black drakkar and wades in from her deck.</li>
 * <li><b>Folk talk</b> ({@link #gossip}): now and then a villager speaks of the horn nobody was blowing.</li>
 * </ul>
 */
public final class ReapedHost {
	/** The share of a host's pillagers and vindicators that ride a skeleton horse, and of its pillagers that ride a spider. */
	static final float HORSE_SHARE = 0.25F, SPIDER_SHARE = 0.2F;
	/** On a host's mount. */
	/** The long ship: how far from the host's ground she may moor (blocks), and how near a player may be to where she does. */
	static final int SHIP_REACH = 40;
	static final double SHIP_HIDE = 24.0;
	static final String MOUNT_TAG = "jaymoskingdoms.reaped_mount";
	/** How far the reaped evoker reaches for bodies, and the most it lifts with one casting. */
	public static final double RAISE_RANGE = 12.0;
	static final int RAISE_MOST = 3;
	/** How often a villager's gossip is of the host, and how many lines there are. */
	static final float GOSSIP_CHANCE = 0.05F;
	static final int GOSSIP_LINES = 4;

	private ReapedHost() {
	}

	/** Part of {@code members} (a host just risen) mounts up. */
	static void mount(ServerLevel level, List<Mob> members, RandomSource random) {
		for (Mob m : members) {
			if (!(m instanceof Reaped) || m.isPassenger() || !(m instanceof Pillager || m instanceof Vindicator)) {
				continue;
			}
			float roll = random.nextFloat();
			Mob steed = null;
			if (m instanceof Pillager && roll < SPIDER_SHARE) {
				steed = EntityTypes.SPIDER.create(level, EntitySpawnReason.EVENT);
			} else if (roll < SPIDER_SHARE + HORSE_SHARE) {
				SkeletonHorse horse = EntityTypes.SKELETON_HORSE.create(level, EntitySpawnReason.EVENT);
				if (horse != null) {
					horse.setTamed(true);
					horse.setAge(0);
				}
				steed = horse;
			}
			if (steed == null) {
				continue;
			}
			steed.snapTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0.0F);
			if (!level.noCollision(steed)) {
				steed.discard();
				continue;
			}
			steed.addTag(MOUNT_TAG);
			level.addFreshEntity(steed);
			m.startRiding(steed, true, false);
		}
	}

	/** The Reaper takes a host's mount back with its rider. */
	static void dismiss(Mob rider) {
		Entity steed = rider.getVehicle();
		if (steed != null && steed.entityTags().contains(MOUNT_TAG)) {
			rider.stopRiding();
			steed.discard();
		}
	}

	/**
	 * A big host that has a seat to march on brings a mangonel ({@link Mangonel}, as the warring kingdoms do): set down
	 * behind {@code members} with two of them for its crew, or null if there's nowhere dry to stand it.
	 */
	static @Nullable Mangonel engine(ServerLevel level, List<Mob> members, Vec3 war) {
		List<PathfinderMob> hands = new ArrayList<>();
		for (Mob m : members) {
			if (m instanceof PathfinderMob p && !m.isPassenger() && !(m instanceof Evoker) && hands.size() < SiegeTrain.CREW) {
				hands.add(p);
			}
		}
		if (hands.size() < SiegeTrain.CREW) {
			return null;
		}
		PathfinderMob first = hands.getFirst();
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, first.blockPosition().offset(2, 0, 2));
		if (!level.getFluidState(at).isEmpty() || !level.getFluidState(at.below()).isEmpty() || Math.abs(at.getY() - first.getY()) > 6) {
			return null;
		}
		Mangonel engine = new Mangonel(Mangonel.TYPE, level);
		engine.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) (Mth.atan2(-(war.x - at.getX()), war.z - at.getZ()) * Mth.RAD_TO_DEG), 0.0F);
		level.addFreshEntity(engine);
		for (PathfinderMob hand : hands) {
			engine.haul.crew.add(hand.getUUID());
		}
		return engine;
	}

	/** {@code m} is one of {@code engine}'s crew, still at it. */
	static boolean crews(@Nullable Mangonel engine, Mob m) {
		return engine != null && !engine.isRemoved() && engine.haul.crew.contains(m.getUUID());
	}

	/** The host's engine pushed on toward {@code war}, or set down {@link SiegeTrain#MANGONEL_STANDOFF} short of it and worked. */
	static void drive(ServerLevel level, Mangonel engine, Vec3 war) {
		List<PathfinderMob> crew = engine.haul.crew(level);
		double dx = war.x - engine.getX(), dz = war.z - engine.getZ();
		double far = Math.sqrt(dx * dx + dz * dz);
		if (crew.isEmpty() || far <= SiegeTrain.MANGONEL_STANDOFF) {
			engine.haul.to = null;
			engine.seat = crew.isEmpty() ? null : BlockPos.containing(war);
			for (PathfinderMob hand : crew) {
				if (hand.getTarget() == null && hand.distanceToSqr(engine) > 9.0) {
					hand.getNavigation().moveTo(engine.getX(), engine.getY(), engine.getZ() - 1.5, 1.0);
				}
			}
			return;
		}
		engine.haul.to = new Vec3(war.x, engine.getY(), war.z);
		engine.seat = null;
		for (int i = 0; i < crew.size(); i++) {
			PathfinderMob hand = crew.get(i);
			if (hand.getTarget() != null) {
				continue;
			}
			double side = (i % 2 == 0 ? 1.0 : -1.0) * SiegeTrain.PUSH_ASIDE;
			double x = engine.getX() - dx / far * SiegeTrain.PUSH_BEHIND - dz / far * side, z = engine.getZ() - dz / far * SiegeTrain.PUSH_BEHIND + dx / far * side;
			if (hand.distanceToSqr(x, engine.getY(), z) > 1.0) {
				hand.getNavigation().moveTo(x, engine.getY(), z, 1.0);
			}
		}
	}

	/** The host is over: its engine goes with it. */
	static void scrap(ServerLevel level, @Nullable Mangonel engine) {
		if (engine != null && !engine.isRemoved()) {
			level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.6F), engine.getX(), engine.getY() + 1.0, engine.getZ(), 20, 0.8, 0.8, 0.8, 0.01);
			engine.discard();
		}
	}

	/**
	 * The host's long ship: a black drakkar ({@link Fleet.Rig#DRAKKAR}) laid on open water within {@link #SHIP_REACH} of
	 * {@code centre}, out of every player's sight, her bow to the shore. She stays where she is moored when the host is
	 * gone. Returns the places to stand on her deck (none: no water for her, or someone would see her come).
	 */
	static List<BlockPos> longship(ServerLevel level, BlockPos centre, RandomSource random) {
		BlockPos water = Fleet.openSea(level, centre, SHIP_REACH, 6, random);
		if (water == null || Raids.playerNear(level, water, SHIP_HIDE)) {
			return List.of();
		}
		Fleet.Look look = new Fleet.Look(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG, Blocks.WOOL.pick(DyeColor.BLACK), null);
		List<BlockPos> where = new ArrayList<>();
		Direction bow = Direction.getApproximateNearest(centre.getX() - water.getX(), 0.0, centre.getZ() - water.getZ());
		if (Fleet.lay(level, Fleet.Rig.DRAKKAR, look, water, bow, where) == null || where.isEmpty()) {
			return List.of();
		}
		BoundingBox box = BoundingBox.encapsulatingPositions(where).orElse(null);
		return box == null ? List.of() : SeaSites.deck(level, box, random);
	}

	/** There is a body within reach of {@code caster} for it to lift. */
	public static boolean bodies(Mob caster) {
		return KitchenCompat.loaded() && !near(caster).isEmpty();
	}

	/** Whether the reaped evoker raises bodies (Jaymo's Kitchen lays them) or keeps its vexes. */
	public static boolean raises() {
		return KitchenCompat.loaded();
	}

	private static List<Entity> near(Mob caster) {
		return caster.level().getEntities((Entity)null, caster.getBoundingBox().inflate(RAISE_RANGE),
			e -> KitchenCompat.isRaisableCorpse(e) && e.distanceToSqr(caster) <= RAISE_RANGE * RAISE_RANGE && risesAs(caster.level() instanceof ServerLevel l ? l : null, e) != null);
	}

	private static @Nullable EntityType<? extends Mob> risesAs(@Nullable ServerLevel level, Entity body) {
		EntityType<?> was = KitchenCompat.sourceType(body);
		if (was == EntityTypes.PILLAGER) {
			return Reaped.pillager();
		}
		if (was == EntityTypes.VINDICATOR) {
			return Reaped.vindicator();
		}
		if (was == EntityTypes.EVOKER || was == EntityTypes.ILLUSIONER) {
			return Reaped.evoker();
		}
		return level == null ? null : Necromancy.risesAs(was, Climate.at(level, body.blockPosition()));
	}

	/** {@code caster} (a reaped evoker) lifts the bodies near it; they fall in with its host. Returns how many got up. */
	public static int raise(Mob caster) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return 0;
		}
		int raised = 0;
		for (Entity body : new ArrayList<>(near(caster))) {
			if (raised >= RAISE_MOST) {
				break;
			}
			EntityType<? extends Mob> as = risesAs(level, body);
			Mob mob = as == null ? null : as.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (mob == null) {
				continue;
			}
			BlockPos pos = body.blockPosition();
			mob.snapTo(body.getX(), body.getY(), body.getZ(), body.getYRot(), 0.0F);
			mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.MOB_SUMMONED, null);
			Fx.sigil(level, body.position(), 1.2F, Reaper.RED, 30);
			level.sendParticles(Fx.miasma(Reaper.SMOKE, 1.3F), body.getX(), body.getY() + 0.5, body.getZ(), 12, 0.3, 0.4, 0.3, 0.01);
			body.discard();
			level.addFreshEntity(mob);
			ZombieHordes.enlist(caster, mob);
			raised++;
		}
		if (raised > 0) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2F, 0.5F);
		}
		return raised;
	}

	/** Now and then, a villager's word on the host (never what it is). */
	static @Nullable Component gossip(ServerLevel level) {
		if (!ZombieHordes.begun(level.getServer()) || level.getRandom().nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		return Component.translatable("talk.jaymoskingdoms.gossip.reaped." + level.getRandom().nextInt(GOSSIP_LINES));
	}
}
