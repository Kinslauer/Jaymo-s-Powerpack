package com.jaymospowers.rig;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Bank shot: an aim helper for anything thrown or shot while the Rig is worn and the Bank is switched on (the Rig menu).
 * <ul>
 * <li>Looking at a wall with an enemy near it, a faint dotted line of cyan motes (shown to the wearer only) runs to the
 * wall and on, bent, to the enemy it would bounce into.</li>
 * <li>A projectile the wearer shot (an arrow, a thrown rock or dagger...) that is about to strike a block is turned at
 * that block toward the enemy near it: it keeps its speed and flies at the enemy. If a second enemy stands near the first
 * the projectile strikes the first itself and goes on to the second.</li>
 * <li>The palm blast does the same off the wall it strikes ({@link #palm}).</li>
 * </ul>
 * Nothing is used up; the wearer only has to be in the Rig. A bounce needs a clear line from the wall to the enemy.
 */
public final class RigBank {
	/** How far the aim line reaches (blocks), how near an enemy must be to the wall, and how near a second to the first. */
	public static final double AIM_REACH = 48.0, NEAR_WALL = 9.0, NEAR_FIRST = 9.0;
	/** The aim line: ticks between draws, blocks between motes, the colour and size. */
	public static final int LINE_EVERY = 4;
	public static final double LINE_STEP = 0.9;
	public static final int LINE_COLOR = 0x5FE6FF;
	public static final float LINE_SIZE = 0.22F;
	/** A projectile this slow is not flying (stuck, or dropped); how far ahead of it a wall is looked for, in its own steps. */
	public static final double MIN_SPEED = 0.3, LOOKAHEAD = 1.15;
	/** How long a projectile is followed, in ticks. */
	public static final int FOLLOW_TICKS = 160;
	/** Damage done to the first enemy for a projectile that is not an arrow, the arrows' base damage, and the palm blast's second target's share. */
	public static final float THROWN_DAMAGE = 3.0F, ARROW_BASE = 2.0F, PALM_CHAIN_SHARE = 0.7F;
	/** How far past the first enemy the projectile is set when it goes on (so it does not strike it twice). */
	public static final double CLEAR_PAST = 0.7;

	private static final class Track {
		int stage;
		int target = -1;
		long born;
	}

	private static final Set<UUID> ON = new HashSet<>();
	private static final Map<UUID, Track> TRACKS = new HashMap<>();

	private RigBank() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(RigBank::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ON.remove(handler.player.getUUID()));
	}

	static boolean on(ServerPlayer player) {
		return ON.contains(player.getUUID());
	}

	/** The Bank entry: on or off. */
	static void toggle(ServerPlayer player) {
		boolean now = ON.add(player.getUUID());
		if (!now) {
			ON.remove(player.getUUID());
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_picks.bank." + (now ? "on" : "off")).withStyle(ChatFormatting.AQUA));
	}

	// ---- picking the enemy ------------------------------------------------------------------------------------------------

	/** The enemy nearest {@code from} within {@code range} that {@code from} can see, not {@code except}; the lock, if it qualifies, first. */
	private static @Nullable LivingEntity enemyNear(ServerPlayer owner, ServerLevel level, Vec3 from, double range, @Nullable Entity except) {
		LivingEntity lock = RigActives.locked(owner);
		if (lock != null && lock != except && seen(level, from, lock) && lock.getBoundingBox().getCenter().distanceTo(from) <= range * 1.6) {
			return lock;
		}
		LivingEntity best = null;
		double near = range * range;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range), c -> c != except && PickKit.enemy(owner, c))) {
			double d = e.getBoundingBox().getCenter().distanceToSqr(from);
			if (d < near && seen(level, from, e)) {
				near = d;
				best = e;
			}
		}
		return best;
	}

	private static boolean seen(ServerLevel level, Vec3 from, LivingEntity e) {
		Vec3 to = e.getBoundingBox().getCenter();
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e)).getType() == HitResult.Type.MISS;
	}

	private static Vec3 normal(Direction face) {
		return Vec3.atLowerCornerOf(face.getUnitVec3i());
	}

	// ---- every tick ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (ON.isEmpty() && TRACKS.isEmpty()) {
			return;
		}
		for (UUID id : ON) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null || !player.isAlive() || player.isSpectator() || !Rig.wearsAny(player)) {
				continue;
			}
			if (player.tickCount % LINE_EVERY == 0) {
				aimLine(player);
			}
			follow(player);
		}
		for (Iterator<Map.Entry<UUID, Track>> it = TRACKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Track> entry = it.next();
			if (server.overworld().getGameTime() - entry.getValue().born > FOLLOW_TICKS * 4L) {
				it.remove();
			}
		}
	}

	/** The faint line: eye to the wall, and bent from the wall to the enemy it would go into. */
	private static void aimLine(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(AIM_REACH)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}
		Vec3 wall = hit.getLocation().add(normal(hit.getDirection()).scale(0.1));
		LivingEntity target = enemyNear(player, level, wall, NEAR_WALL, null);
		if (target == null) {
			return;
		}
		dots(player, level, eye.add(player.getLookAngle().scale(1.5)).add(0.0, -0.25, 0.0), wall);
		LivingEntity second = enemyNear(player, level, target.getBoundingBox().getCenter(), NEAR_FIRST, target);
		dots(player, level, wall, target.getBoundingBox().getCenter());
		if (second != null) {
			dots(player, level, target.getBoundingBox().getCenter(), second.getBoundingBox().getCenter());
		}
	}

	private static void dots(ServerPlayer player, ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		double length = step.length();
		if (length < 1.0E-3) {
			return;
		}
		Vec3 unit = step.scale(1.0 / length);
		for (double d = 0.0; d < length; d += LINE_STEP) {
			Vec3 at = from.add(unit.scale(d));
			level.sendParticles(player, MagicFx.mote(LINE_COLOR, LINE_SIZE), true, false, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** The wearer's projectiles in flight: one about to strike a wall turns off it. */
	private static void follow(ServerPlayer owner) {
		ServerLevel level = owner.level();
		long now = level.getGameTime();
		for (Projectile p : level.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(AIM_REACH + 16.0), c -> c.getOwner() == owner && c.isAlive())) {
			Vec3 v = p.getDeltaMovement();
			double speed = v.length();
			if (speed < MIN_SPEED) {
				continue;
			}
			Track track = TRACKS.computeIfAbsent(p.getUUID(), k -> {
				Track t = new Track();
				t.born = now;
				return t;
			});
			if (track.stage >= 2 || now - track.born > FOLLOW_TICKS) {
				continue;
			}
			Vec3 pos = p.position();
			if (track.stage == 0) {
				BlockHitResult hit = level.clip(new ClipContext(pos, pos.add(v.scale(LOOKAHEAD)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
				if (hit.getType() == HitResult.Type.MISS) {
					continue;
				}
				Vec3 wall = hit.getLocation().add(normal(hit.getDirection()).scale(0.15));
				LivingEntity target = enemyNear(owner, level, wall, NEAR_WALL, null);
				if (target == null) {
					track.stage = 2;
					continue;
				}
				p.setPos(wall.x, wall.y, wall.z);
				aim(p, wall, target, speed);
				track.stage = 1;
				track.target = target.getId();
				MagicFx.ring(level, wall, normal(hit.getDirection()), 0.5F, LINE_COLOR, 8);
				level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.8F);
			} else if (level.getEntity(track.target) instanceof LivingEntity first && first.isAlive()) {
				Vec3 centre = first.getBoundingBox().getCenter();
				if (pos.distanceTo(centre) > speed * LOOKAHEAD + first.getBbWidth() * 0.5 + 0.3) {
					continue;
				}
				LivingEntity second = enemyNear(owner, level, centre, NEAR_FIRST, first);
				track.stage = 2;
				if (second == null) {
					continue;
				}
				// strike the first ourselves, then go on to the second
				strike(owner, level, p, first, speed);
				Vec3 dir = second.getBoundingBox().getCenter().subtract(centre).normalize();
				Vec3 past = centre.add(dir.scale(first.getBbWidth() * 0.5 + CLEAR_PAST));
				p.setPos(past.x, past.y, past.z);
				aim(p, past, second, speed);
				MagicFx.ring(level, centre, dir, 0.6F, LINE_COLOR, 8);
			} else {
				track.stage = 2;
			}
		}
	}

	/** Points the projectile at {@code target} from {@code from}, at {@code speed}, allowing for its fall. */
	private static void aim(Projectile p, Vec3 from, LivingEntity target, double speed) {
		Vec3 to = target.getBoundingBox().getCenter();
		Vec3 line = to.subtract(from);
		double distance = line.length();
		double flight = distance / Math.max(speed, 0.1);
		double drop = p.isNoGravity() ? 0.0 : 0.5 * 0.05 * flight * flight;
		Vec3 dir = line.add(0.0, drop, 0.0).normalize();
		p.shoot(dir.x, dir.y, dir.z, (float)speed, 0.0F);
		p.syncVelocity = true;
		p.needsSync = true;
	}

	private static void strike(ServerPlayer owner, ServerLevel level, Projectile p, LivingEntity target, double speed) {
		DamageSource source = p instanceof AbstractArrow arrow ? level.damageSources().arrow(arrow, owner) : level.damageSources().thrown(p, owner);
		float damage = p instanceof AbstractArrow ? (float)Math.ceil(speed * ARROW_BASE) : THROWN_DAMAGE;
		target.hurtServer(level, source, damage);
	}

	// ---- the palm blast ------------------------------------------------------------------------------------------------------

	/**
	 * The palm blast struck a wall at {@code at} (its face {@code face}) and no creature: with the Bank on, it goes on off the
	 * wall into an enemy near it, and from that one to a second.
	 */
	static void palm(ServerPlayer player, Vec3 at, Direction face, float damage, int color) {
		if (!on(player)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 wall = at.add(normal(face).scale(0.1));
		LivingEntity first = enemyNear(player, level, wall, NEAR_WALL, null);
		if (first == null) {
			return;
		}
		Vec3 centre = first.getBoundingBox().getCenter();
		MagicFx.beam(level, wall, centre, 0.08F, color, 5);
		MagicFx.ring(level, wall, normal(face), 0.5F, color, 8);
		first.hurtServer(level, player.damageSources().playerAttack(player), damage);
		LivingEntity second = enemyNear(player, level, centre, NEAR_FIRST, first);
		if (second != null) {
			Vec3 next = second.getBoundingBox().getCenter();
			MagicFx.beam(level, centre, next, 0.07F, color, 5);
			second.hurtServer(level, player.damageSources().playerAttack(player), damage * PALM_CHAIN_SHARE);
		}
	}
}
