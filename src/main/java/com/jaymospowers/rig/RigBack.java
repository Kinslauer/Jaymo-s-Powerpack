package com.jaymospowers.rig;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The back rig (Andy's Rig picks, ideas only; Frostborn Rig tech: jointed metal that slides and locks, nothing grown). Plates on the
 * Core's back unfold into:
 * <ul>
 * <li><b>Wings</b>: two vanes spread in full flight and fold in hover (client {@code RigBackClient}), with a buzz while they
 * beat. No menu entry: they come with flight.</li>
 * <li><b>Wing slash</b> ({@link #WINGSLASH}, level 3): the thrusters' dash with the vanes out as blades: it cuts everything it
 * passes ({@link #SLASH_DAMAGE}, each once) instead of ramming the first thing.</li>
 * <li><b>Spear legs</b> ({@link #SPEARS}, level 2): four jointed legs stab out of the back in a ring and back in: everything within
 * {@link #SPEAR_RANGE} is run through.</li>
 * <li><b>Pincers</b> ({@link #PINCER}, level 3): two arms over the shoulders seize the creature in front and hold it before you;
 * pressed again, they throw it along your look.</li>
 * <li><b>Grapple claw</b> ({@link #GRAPPLE}, level 2): a claw on a line: at a wall it pulls you there, at a creature it pulls it
 * to you, no farther than {@link #GRAPPLE_RANGE}.</li>
 * <li><b>Horn lightning</b> ({@link #HORNS}, level 4): lightning leaps from the helm to the nearest foe and on, up to
 * {@link #ARCS} of them; stronger in the rain, and it overcharges the Core.</li>
 * </ul>
 * All need the whole set and cost charge. Menu entries and moves are numbered clear of the other Rig patches'.
 */
public final class RigBack {
	// ---- tuning ----
	/** Rig menu entries ({@link RigPicks}). */
	public static final int WINGSLASH = 60, SPEARS = 61, PINCER = 62, GRAPPLE = 63, HORNS = 64;
	/** Synced moves ({@link RigMoves}), and how long each plays (ticks). */
	public static final int MOVE_SLASH = 40, MOVE_SPEARS = 41, MOVE_GRAB = 42, MOVE_THROW = 43, MOVE_GRAPPLE = 44, MOVE_HORNS = 45;
	public static final int SLASH_TICKS = 10, SPEARS_TICKS = 14, GRAB_TICKS = 100, THROW_TICKS = 8, GRAPPLE_TICKS = 12, HORNS_TICKS = 12;
	/** Mind levels. */
	static final int SLASH_LEVEL = 3, SPEARS_LEVEL = 2, PINCER_LEVEL = 3, GRAPPLE_LEVEL = 2, HORNS_LEVEL = 4;
	/** Charge each costs. */
	static final int SLASH_COST = 60, SPEARS_COST = 50, PINCER_COST = 40, GRAPPLE_COST = 25, HORNS_COST = 90;
	/** Wing slash: speed (b/t), how long (ticks), the blades' reach round you (blocks), damage. */
	static final double SLASH_SPEED = 1.6, SLASH_REACH = 1.8;
	static final int SLASH_DASH_TICKS = 8;
	static final float SLASH_DAMAGE = 9.0F;
	/** Spear legs: reach (blocks), damage, the lift they give what they run through. */
	static final double SPEAR_RANGE = 3.5, SPEAR_LIFT = 0.35;
	static final float SPEAR_DAMAGE = 8.0F;
	/** Pincers: reach (blocks), how far in front they hold it, the throw's speed (b/t); the largest they lift (health). */
	static final double PINCER_REACH = 3.5, PINCER_HOLD = 1.8, THROW_SPEED = 1.7;
	static final float PINCER_MOST_HEALTH = 60.0F;
	/** Grapple: reach (blocks), pull speed (b/t). */
	static final double GRAPPLE_RANGE = 24.0, GRAPPLE_PULL = 1.4;
	/** Horn lightning: how many it leaps to, how far to the first and between, damage, and how much more in the rain. */
	static final int ARCS = 4;
	static final double ARC_FIRST = 14.0, ARC_NEXT = 7.0;
	static final float ARC_DAMAGE = 7.0F, RAIN_BOOST = 1.5F;
	/** The wings' buzz: how often (ticks) in full flight. */
	static final int BUZZ_EVERY = 6;
	static final int ARC_COLOR = 0x9FE8FF;

	private static final class Slash {
		final Vec3 dir;
		int left = SLASH_DASH_TICKS;
		final Set<UUID> cut = new HashSet<>();

		Slash(Vec3 dir) {
			this.dir = dir;
		}
	}

	private static final Map<UUID, Slash> SLASHES = new HashMap<>();
	/** Who each wearer's pincers hold (entity UUID). */
	private static final Map<UUID, UUID> HELD = new HashMap<>();

	private RigBack() {
	}

	static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SLASHES.clear();
			HELD.clear();
		});
	}

	/** Whether {@code action} is one of these menu entries. */
	public static boolean is(int action) {
		return action >= WINGSLASH && action <= HORNS;
	}

	/** How long one of these moves plays (ticks), or 0. */
	static int length(int kind) {
		return switch (kind) {
			case MOVE_SLASH -> SLASH_TICKS;
			case MOVE_SPEARS -> SPEARS_TICKS;
			case MOVE_GRAB -> GRAB_TICKS;
			case MOVE_THROW -> THROW_TICKS;
			case MOVE_GRAPPLE -> GRAPPLE_TICKS;
			case MOVE_HORNS -> HORNS_TICKS;
			default -> 0;
		};
	}

	static void use(ServerPlayer player, int action) {
		int level = switch (action) {
			case WINGSLASH -> SLASH_LEVEL;
			case SPEARS -> SPEARS_LEVEL;
			case PINCER -> PINCER_LEVEL;
			case GRAPPLE -> GRAPPLE_LEVEL;
			default -> HORNS_LEVEL;
		};
		if (!RigActives.needs(player, level)) {
			return;
		}
		if (action == PINCER && HELD.containsKey(player.getUUID())) {
			// pressed again: the throw (already paid for)
			throwHeld(player);
			return;
		}
		int cost = switch (action) {
			case WINGSLASH -> SLASH_COST;
			case SPEARS -> SPEARS_COST;
			case PINCER -> PINCER_COST;
			case GRAPPLE -> GRAPPLE_COST;
			default -> HORNS_COST;
		};
		if (!Rig.spend(player, cost)) {
			RigMind.empty(player);
			return;
		}
		switch (action) {
			case WINGSLASH -> slash(player);
			case SPEARS -> spears(player);
			case PINCER -> grab(player);
			case GRAPPLE -> grapple(player);
			case HORNS -> horns(player);
			default -> {
			}
		}
	}

	// ---- wing slash -----------------------------------------------------------------------------------------------------

	private static void slash(ServerPlayer player) {
		Vec3 dir = player.getLookAngle().normalize();
		SLASHES.put(player.getUUID(), new Slash(dir));
		RigMoves.start(player, MOVE_SLASH);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
	}

	private static void tickSlash(ServerPlayer player, Slash s) {
		ServerLevel level = player.level();
		player.setDeltaMovement(s.dir.scale(SLASH_SPEED));
		player.syncVelocity = true;
		player.resetFallDistance();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SLASH_REACH),
			e -> e != player && e.isAlive() && !s.cut.contains(e.getUUID()))) {
			s.cut.add(e.getUUID());
			e.hurtServer(level, level.damageSources().playerAttack(player), SLASH_DAMAGE * RigActives.levelDamage(player));
			Vec3 mid = e.getBoundingBox().getCenter();
			MagicFx.stroke(level, mid.subtract(s.dir), mid.add(s.dir), 0.15F, RigForgeBlock.FX_COLOR);
		}
		s.left--;
	}

	// ---- spear legs -----------------------------------------------------------------------------------------------------

	private static void spears(ServerPlayer player) {
		ServerLevel level = player.level();
		RigMoves.start(player, MOVE_SPEARS);
		RigActives.later(player, 3, () -> {
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SPEAR_RANGE),
				e -> e != player && e.isAlive() && e.distanceTo(player) <= SPEAR_RANGE)) {
				e.hurtServer(level, level.damageSources().playerAttack(player), SPEAR_DAMAGE * RigActives.levelDamage(player));
				e.push(0.0, SPEAR_LIFT, 0.0);
				e.syncVelocity = true;
			}
			MagicFx.ring(level, player.position().add(0.0, 1.0, 0.0), (float)SPEAR_RANGE, RigForgeBlock.FX_COLOR);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		});
	}

	// ---- pincers --------------------------------------------------------------------------------------------------------

	private static void grab(ServerPlayer player) {
		LivingEntity target = inFront(player, PINCER_REACH);
		if (target == null || target.getMaxHealth() > PINCER_MOST_HEALTH) {
			RigMind.say(player, "pincer_none", 1.0F);
			return;
		}
		HELD.put(player.getUUID(), target.getUUID());
		RigMoves.start(player, MOVE_GRAB);
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.0F, 1.3F);
	}

	private static @Nullable LivingEntity held(ServerPlayer player) {
		UUID id = HELD.get(player.getUUID());
		return id != null && player.level().getEntity(id) instanceof LivingEntity l && l.isAlive() ? l : null;
	}

	private static void tickHeld(ServerPlayer player) {
		LivingEntity held = held(player);
		RigMoves.Move move = RigMoves.get(player);
		if (held == null || move == null || move.kind() != MOVE_GRAB || !Rig.fullSet(player)) {
			// let go: it drops, or the hold ran out
			HELD.remove(player.getUUID());
			return;
		}
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(PINCER_HOLD)).subtract(0.0, held.getBbHeight() * 0.5, 0.0);
		held.teleportTo(at.x, at.y, at.z);
		held.setDeltaMovement(Vec3.ZERO);
		held.resetFallDistance();
	}

	private static void throwHeld(ServerPlayer player) {
		LivingEntity held = held(player);
		HELD.remove(player.getUUID());
		if (held == null) {
			return;
		}
		RigMoves.start(player, MOVE_THROW);
		held.setDeltaMovement(player.getLookAngle().scale(THROW_SPEED).add(0.0, 0.2, 0.0));
		held.syncVelocity = true;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	// ---- grapple claw ---------------------------------------------------------------------------------------------------

	private static void grapple(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(GRAPPLE_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.distanceToSqr(end));
		RigMoves.start(player, MOVE_GRAPPLE);
		if (hit != null && hit.getEntity() instanceof LivingEntity caught) {
			// a creature: pulled in to you
			Vec3 pull = eye.subtract(caught.position()).normalize().scale(GRAPPLE_PULL);
			caught.setDeltaMovement(pull.x, Math.max(0.3, pull.y), pull.z);
			caught.syncVelocity = true;
			MagicFx.beam(level, eye.subtract(0.0, 0.3, 0.0), caught.getBoundingBox().getCenter(), 0.03F, RigForgeBlock.FX_COLOR, 8);
		} else if (block.getType() != HitResult.Type.MISS) {
			// a wall: you're pulled there
			Vec3 pull = block.getLocation().subtract(player.position()).normalize().scale(GRAPPLE_PULL);
			player.setDeltaMovement(pull.x, Math.max(0.35, pull.y + 0.2), pull.z);
			player.syncVelocity = true;
			player.resetFallDistance();
			MagicFx.beam(level, eye.subtract(0.0, 0.3, 0.0), block.getLocation(), 0.03F, RigForgeBlock.FX_COLOR, 8);
		} else {
			MagicFx.beam(level, eye.subtract(0.0, 0.3, 0.0), end, 0.03F, RigForgeBlock.FX_COLOR, 4);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	// ---- horn lightning -------------------------------------------------------------------------------------------------

	private static void horns(ServerPlayer player) {
		ServerLevel level = player.level();
		RigMoves.start(player, MOVE_HORNS);
		boolean rain = level.isRainingAt(player.blockPosition());
		float damage = ARC_DAMAGE * RigActives.levelDamage(player) * (rain ? RAIN_BOOST : 1.0F);
		Vec3 from = player.getEyePosition().add(0.0, 0.3, 0.0);
		List<LivingEntity> struck = new ArrayList<>();
		LivingEntity next = nearestFoe(level, player, from, ARC_FIRST, struck);
		while (next != null && struck.size() < ARCS) {
			Vec3 to = next.getBoundingBox().getCenter();
			MagicFx.beam(level, from, to, 0.05F, ARC_COLOR, 6);
			MagicFx.blast(level, to, 0.4F, ARC_COLOR);
			next.hurtServer(level, level.damageSources().lightningBolt(), damage);
			struck.add(next);
			from = to;
			next = nearestFoe(level, player, from, ARC_NEXT, struck);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.9F, 1.4F);
		if (!struck.isEmpty()) {
			RigActives.overcharge(player);
		}
	}

	private static @Nullable LivingEntity nearestFoe(ServerLevel level, ServerPlayer player, Vec3 from, double range, List<LivingEntity> struck) {
		LivingEntity best = null;
		double bestD = range * range;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range),
			e -> e != player && e.isAlive() && e instanceof Enemy && !struck.contains(e))) {
			double d = e.getBoundingBox().getCenter().distanceToSqr(from);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	// ---- shared ---------------------------------------------------------------------------------------------------------

	/** The living thing right in front of {@code player}, within {@code reach}. */
	private static @Nullable LivingEntity inFront(ServerPlayer player, double reach) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(reach));
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && e != player, reach * reach);
		return hit != null && hit.getEntity() instanceof LivingEntity l ? l : null;
	}

	/** Every tick, for every wearer: a slash under way, a creature held, the wings' buzz. */
	static void tick(ServerPlayer player) {
		Slash s = SLASHES.get(player.getUUID());
		if (s != null) {
			if (s.left <= 0 || !player.isAlive()) {
				SLASHES.remove(player.getUUID());
			} else {
				tickSlash(player, s);
			}
		}
		if (HELD.containsKey(player.getUUID())) {
			tickHeld(player);
		}
		if (RigMoves.thrust(player) == RigMoves.THRUST_FLIGHT && player.tickCount % BUZZ_EVERY == 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEE_LOOP, SoundSource.PLAYERS, 0.25F, 1.8F);
		}
	}
}
