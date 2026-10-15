package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The future's dead: feral hordes of zombies (no husks or zombified villagers) roam the ruins in packs around
 * every player there, by day as well as night (nothing burns in the future's ashen sun; see {@code FutureSunMixin}).
 * They shamble slower than the zombies back home while you watch them, and sprint as soon as you look away;
 * they hear you: anyone running within {@link #HEAR_RUNNING} blocks, or
 * walking within {@link #HEAR_WALKING}, draws the nearest ferals to them, and a feral that sees you attacks. They're
 * the game's own zombies, so Kitchen treats them like any other: the dead they leave are torn into by scavenging
 * zombies, and a zombie's body left lying gets back up. Nearly half are Kitchen's corpse zombies in random skins
 * ({@link FutureKitchen}), and most of those carry a modern gun and use it ({@link FutureGuns}).
 */
public final class FutureFerals {
	public static final String TAG = "jaymospowers.feral";
	static final Identifier SPEED = JaymosPowers.id("feral_speed");
	static final Identifier SPRINT = JaymosPowers.id("feral_sprint");
	/** How often the hordes are topped up and listen for players. */
	static final int EVERY = 40;
	/** Of each pack, this share are Kitchen's corpse zombies in random skins (the rest the game's own), and this share of those carry a gun. */
	static final float CORPSE_SHARE = 0.45F, GUNNER_SHARE = 0.65F;
	/** Gunners look for a shot this often. */
	static final int GUN_EVERY = 2;
	/** At most this many ferals within {@link #COUNT_RADIUS} of a player, by day and by night. */
	static final int CAP_DAY = 10, CAP_NIGHT = 20;
	static final double COUNT_RADIUS = 64.0;
	/** A pack: this many at once, this far from the player. */
	static final int PACK_MIN = 3, PACK_MAX = 6;
	static final int SPAWN_MIN = 24, SPAWN_MAX = 44;
	/**
	 * Ferals shamble slower than a zombie (x0.75) while you're looking at them, and sprint (x1.5 a zombie) the moment
	 * you turn away; they track you from this far.
	 */
	static final double SPEED_BONUS = -0.25, SPRINT_BONUS = 0.75, FOLLOW_RANGE = 48.0;
	/** You're "looking at" a feral when it's within this cone (cosine of ~55 degrees) of where you face; checked this often. */
	static final double WATCHED_COS = 0.57;
	static final int SPRINT_EVERY = 4;
	/** How far they hear you: running, walking (sneaking, they don't). */
	static final double HEAR_RUNNING = 40.0, HEAR_WALKING = 16.0;
	/** A feral this close that can see you goes for you. */
	static final double SIGHT = 24.0;

	private FutureFerals() {
	}

	static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Future.FUTURE);
		if (level == null || level.players().isEmpty()) {
			return;
		}
		if (server.getTickCount() % GUN_EVERY == 0) {
			shoot(level);
		}
		if (server.getTickCount() % SPRINT_EVERY == 0) {
			sprint(level);
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !player.isAlive()) {
				continue;
			}
			List<Zombie> near = level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(COUNT_RADIUS), z -> z.entityTags().contains(TAG));
			int cap = level.isDarkOutside() ? CAP_NIGHT : CAP_DAY;
			if (near.size() < cap) {
				spawnPack(level, player, Math.min(cap - near.size(), PACK_MIN + level.getRandom().nextInt(PACK_MAX - PACK_MIN + 1)));
			}
			listen(player, near);
		}
	}

	/** Every gunner near a player fires if it can (each keeps its own reload). */
	private static void shoot(ServerLevel level) {
		java.util.Set<Mob> seen = new java.util.HashSet<>();
		for (ServerPlayer player : level.players()) {
			for (Mob gunner : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(COUNT_RADIUS),
				m -> m.entityTags().contains(FutureGuns.TAG) && m.isAlive())) {
				if (seen.add(gunner)) {
					FutureGuns.tick(level, gunner);
				}
			}
		}
	}

	/** A feral hunting someone who isn't looking at it sprints; watched, it slows back down. */
	private static void sprint(ServerLevel level) {
		java.util.Set<Mob> seen = new java.util.HashSet<>();
		for (ServerPlayer player : level.players()) {
			for (Zombie feral : level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(COUNT_RADIUS),
				z -> z.entityTags().contains(TAG) && z.isAlive())) {
				if (!seen.add(feral)) {
					continue;
				}
				AttributeInstance speed = feral.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed == null) {
					continue;
				}
				pace(speed);
				boolean run = feral.getTarget() instanceof ServerPlayer hunted && !watched(hunted, feral);
				if (run && !speed.hasModifier(SPRINT)) {
					speed.addTransientModifier(new AttributeModifier(SPRINT, SPRINT_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
				} else if (!run && speed.hasModifier(SPRINT)) {
					speed.removeModifier(SPRINT);
				}
			}
		}
	}

	/** The feral's own pace (ferals from before 0.11.38 were faster: theirs is replaced). */
	private static void pace(AttributeInstance speed) {
		AttributeModifier pace = speed.getModifier(SPEED);
		if (pace == null || pace.amount() != SPEED_BONUS) {
			speed.removeModifier(SPEED);
			speed.addPermanentModifier(new AttributeModifier(SPEED, SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	/** Is the player facing the feral (and able to see it)? */
	private static boolean watched(ServerPlayer player, Zombie feral) {
		net.minecraft.world.phys.Vec3 to = feral.getEyePosition().subtract(player.getEyePosition());
		double length = to.length();
		if (length < 1.0E-4) {
			return true;
		}
		return player.getViewVector(1.0F).dot(to.scale(1.0 / length)) > WATCHED_COS && player.hasLineOfSight(feral);
	}

	/** The ferals near a player who's making noise come; one that sees them attacks. */
	private static void listen(ServerPlayer player, List<Zombie> near) {
		double hear = player.isShiftKeyDown() ? 0.0 : player.isSprinting() ? HEAR_RUNNING : HEAR_WALKING;
		for (Zombie feral : near) {
			if (feral.getTarget() != null && feral.getTarget().isAlive()) {
				continue;
			}
			double d = feral.distanceTo(player);
			if (d < SIGHT && feral.hasLineOfSight(player)) {
				feral.setTarget(player);
			} else if (d < hear) {
				feral.getNavigation().moveTo(player, 1.1);
			}
		}
	}

	private static void spawnPack(ServerLevel level, ServerPlayer player, int count) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		int dist = SPAWN_MIN + random.nextInt(SPAWN_MAX - SPAWN_MIN + 1);
		int cx = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
		int cz = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
		for (int i = 0; i < count; i++) {
			int x = cx + random.nextInt(7) - 3;
			int z = cz + random.nextInt(7) - 3;
			if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
				continue;
			}
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.getFluidState(pos.below()).isEmpty() || !level.getFluidState(pos).isEmpty()
				|| !level.noCollision(new AABB(x + 0.2, y, z + 0.2, x + 0.8, y + 1.9, z + 0.8))) {
				continue;
			}
			Zombie feral = random.nextFloat() < CORPSE_SHARE ? FutureKitchen.corpseZombie(level) : null;
			boolean corpse = feral != null;
			if (feral == null) {
				feral = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
			}
			if (feral == null) {
				continue;
			}
			feral.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
			feral.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
			makeFeral(feral);
			if (corpse && random.nextFloat() < GUNNER_SHARE) {
				FutureGuns.arm(feral, random);
			}
			level.addFreshEntity(feral);
		}
	}

	/** A zombie turned feral: slow under your eye, keener, and it never stops hunting. */
	public static void makeFeral(Mob feral) {
		feral.addTag(TAG);
		AttributeInstance speed = feral.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			pace(speed);
		}
		AttributeInstance follow = feral.getAttribute(Attributes.FOLLOW_RANGE);
		if (follow != null) {
			follow.setBaseValue(Math.max(follow.getBaseValue(), FOLLOW_RANGE));
		}
	}
}
