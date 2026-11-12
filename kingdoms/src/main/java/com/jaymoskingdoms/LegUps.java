package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A hand up (docs/Pawn-Plan.md row F3; after the pawns who boost you onto a ledge in Dragon's Dogma, a commercial game:
 * ideas only).
 * <ul>
 * <li><b>Friend</b>: jump at a wall two or three blocks high with a companion beside you ({@link #NEAR}) and it cups
 * its hands and boosts you up ("legup"). A wall you could step or climb anyway, or one with no room on top, isn't
 * boosted.</li>
 * <li><b>Foe</b>: a raider whose foe stands two or three blocks above it, out of its reach, gets a boost from a mate
 * beside it: you're not safe on a low roof from a band.</li>
 * <li><b>Across a gap</b>: jump at the lip of a gap {@link #GAP_MIN} to {@link #GAP_MAX} blocks wide with a companion beside
 * you and it throws you over ("toss"); a raider stuck at a gap with its foe on the far side is thrown over by a mate.</li>
 * </ul>
 */
public final class LegUps {
	/** A companion this near (blocks) gives you a leg up; a mate this near gives a raider one. */
	static final double NEAR = 3.5, MATE_NEAR = 3.0;
	/** The wall: you're within this of it (blocks, centre to centre). */
	static final double AT_WALL = 1.3;
	/** Up and over (blocks a tick) for a two-block wall and a three-block one; a little forward too. */
	static final double BOOST_2 = 0.62, BOOST_3 = 0.78, FORWARD = 0.18;
	/** Not again for this long (ticks); raiders are looked at this often (ticks). */
	static final int GAP = 30, FOE_GAP = 100, FOE_EVERY = 20;
	/** A raider's foe this far above it (blocks, at least two) and this near across (blocks) gets it boosted. */
	static final double FOE_ABOVE_MAX = 3.5, FOE_ACROSS = 4.0;
	/** A gap this many blocks wide (at least, at most) gets a throw across. */
	static final int GAP_MIN = 2, GAP_MAX = 5;
	/** The throw: up (blocks a tick), and forward enough to carry the gap and this much more, over about this many ticks' flight. */
	static final double TOSS_UP = 0.42, TOSS_SPARE = 1.5, TOSS_CARRY = 7.0;

	private static final Map<UUID, Boolean> JUMP_WAS = new HashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private LegUps() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(LegUps::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			JUMP_WAS.clear();
			NEXT.clear();
		});
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean jump = player.getLastClientInput().jump();
			Boolean was = JUMP_WAS.put(player.getUUID(), jump);
			if (jump && !Boolean.TRUE.equals(was) && now >= NEXT.getOrDefault(player.getUUID(), 0L)) {
				boost(player, now);
			}
		}
		if (server.getTickCount() % FOE_EVERY == 0) {
			NEXT.values().removeIf(t -> t < now);
			for (ServerLevel level : server.getAllLevels()) {
				for (Mob m : Comrades.fighters(level)) {
					foe(level, m, now);
				}
			}
		}
	}

	/** {@code player} jumped: at a wall they can't get up alone, with a companion by, they're boosted. */
	private static void boost(ServerPlayer player, long now) {
		if (player.isSpectator() || player.getAbilities().flying || player.isPassenger() || Downed.isDowned(player) || player.isInWater()
			|| Grapples.held(player)) {
			return;
		}
		int high = wall(player.level(), player.blockPosition(), player.getDirection(), player.position());
		int across = high < 2 ? gap(player.level(), player.blockPosition(), player.getDirection()) : 0;
		if (high < 2 && across == 0) {
			return;
		}
		Villager helper = null;
		double best = NEAR * NEAR;
		for (Villager v : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR),
			v -> !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && Followers.takesOrders(v, player) && !Comrades.busy(v))) {
			double d = v.distanceToSqr(player);
			if (d < best) {
				best = d;
				helper = v;
			}
		}
		if (helper == null) {
			return;
		}
		NEXT.put(player.getUUID(), now + GAP);
		if (across > 0) {
			toss(player.level(), helper, player, player.getDirection(), across);
			Callouts.say(helper, "toss", player, true, player.getDisplayName());
			return;
		}
		lift(player.level(), helper, player, high);
		Callouts.say(helper, "legup", player, true, player.getDisplayName());
	}

	/** A raider can't reach its foe up on a ledge: a mate beside it boosts it. */
	private static void foe(ServerLevel level, Mob m, long now) {
		LivingEntity t = Comrades.target(m);
		if (t == null || NEXT.containsKey(m.getUUID()) || !m.onGround() || !(m instanceof PathfinderMob) || !WorldTiers.hostile(m)
			|| !Temperaments.humanoid(m) || Comrades.busy(m)) {
			return;
		}
		double above = t.getY() - m.getY();
		double across = Math.sqrt(Math.pow(t.getX() - m.getX(), 2) + Math.pow(t.getZ() - m.getZ(), 2));
		Direction toward = Direction.getApproximateNearest(t.getX() - m.getX(), 0.0, t.getZ() - m.getZ());
		int high = 0, gap = 0;
		if (above >= 2.0 && above <= FOE_ABOVE_MAX && across <= FOE_ACROSS) {
			high = wall(level, m.blockPosition(), toward, m.position());
		} else if (Math.abs(above) <= 1.5 && across >= 3.0 && across <= GAP_MAX + 3.0 && m.getNavigation().isDone()) {
			// across a gap it can't path over: a mate throws it over
			gap = gap(level, m.blockPosition(), toward);
		}
		if (high < 2 && gap == 0) {
			return;
		}
		String side = Comrades.side(m);
		Mob mate = null;
		for (Mob o : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(MATE_NEAR),
			o -> o != m && o.isAlive() && !Downed.isDowned(o) && Temperaments.humanoid(o) && Comrades.side(o).equals(side) && !Comrades.busy(o))) {
			mate = o;
			break;
		}
		if (mate == null) {
			return;
		}
		NEXT.put(m.getUUID(), now + FOE_GAP);
		NEXT.put(mate.getUUID(), now + FOE_GAP);
		if (gap > 0) {
			toss(level, mate, m, toward, gap);
			Callouts.say(mate, "toss", m, false, m.getDisplayName());
			return;
		}
		lift(level, mate, m, high);
		Callouts.say(mate, "legup", m, false, m.getDisplayName());
	}

	/**
	 * How high the wall right in front is (blocks) if it wants a boost: 2 or 3, with two clear blocks on top of it and
	 * clear headroom over {@code feet}; else 0.
	 */
	static int wall(ServerLevel level, BlockPos feet, Direction facing, Vec3 at) {
		if (facing.getAxis().isVertical()) {
			return 0;
		}
		BlockPos front = feet.relative(facing);
		if (Vec3.atBottomCenterOf(front).distanceTo(new Vec3(at.x, front.getY(), at.z)) > AT_WALL) {
			return 0;
		}
		int high = 0;
		while (high < 4 && solid(level, front.above(high))) {
			high++;
		}
		if (high < 2 || high > 3) {
			return 0;
		}
		for (int y = 0; y < high + 2; y++) {
			if (y >= 2 && solid(level, feet.above(y))) {
				// no room overhead to go up
				return 0;
			}
		}
		return !solid(level, front.above(high)) && !solid(level, front.above(high + 1)) ? high : 0;
	}

	/**
	 * How wide a gap right ahead is (blocks) if it wants a throw across: {@link #GAP_MIN} to {@link #GAP_MAX} columns with
	 * nothing under them for three blocks down and room to fly through, then ground on the far side a block up at most or
	 * a block down at most; else 0. You must be standing on something.
	 */
	static int gap(ServerLevel level, BlockPos feet, Direction facing) {
		if (facing.getAxis().isVertical() || !solid(level, feet.below())) {
			return 0;
		}
		int w = 0;
		for (int i = 1; i <= GAP_MAX + 1; i++) {
			BlockPos col = feet.relative(facing, i);
			boolean open = !solid(level, col) && !solid(level, col.above());
			boolean empty = open && !solid(level, col.below()) && !solid(level, col.below(2)) && !solid(level, col.below(3));
			if (empty) {
				w++;
				continue;
			}
			boolean land = open && (solid(level, col.below()) || solid(level, col.below(2)))
				|| solid(level, col) && !solid(level, col.above()) && !solid(level, col.above(2));
			return land && w >= GAP_MIN ? w : 0;
		}
		return 0;
	}

	/** {@code helper} swings {@code who} by the arm and belt and throws it across a {@code width}-block gap. */
	private static void toss(ServerLevel level, Mob helper, LivingEntity who, Direction facing, int width) {
		helper.getNavigation().stop();
		helper.getLookControl().setLookAt(who, 30.0F, 30.0F);
		helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		double v = (width + TOSS_SPARE) / TOSS_CARRY;
		who.setDeltaMovement(facing.getStepX() * v, TOSS_UP, facing.getStepZ() * v);
		who.needsSync = true;
		who.resetFallDistance();
		level.playSound(null, helper.getX(), helper.getY(), helper.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.4F, 0.7F);
	}

	private static boolean solid(ServerLevel level, BlockPos pos) {
		return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
	}

	/** {@code helper} crouches, cups its hands and sends {@code who} up a {@code high}-block wall. */
	private static void lift(ServerLevel level, Mob helper, LivingEntity who, int high) {
		helper.getNavigation().stop();
		helper.getLookControl().setLookAt(who, 30.0F, 30.0F);
		helper.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Vec3 ahead = who instanceof ServerPlayer ? who.getLookAngle().multiply(1.0, 0.0, 1.0) : faceOf(who);
		if (ahead.lengthSqr() > 1.0E-4) {
			ahead = ahead.normalize().scale(FORWARD);
		}
		who.setDeltaMovement(ahead.x, high >= 3 ? BOOST_3 : BOOST_2, ahead.z);
		who.needsSync = true;
		who.resetFallDistance();
		level.playSound(null, helper.getX(), helper.getY(), helper.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.7F, 1.1F);
		level.playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.NEUTRAL, 0.4F, 0.8F);
	}

	private static Vec3 faceOf(@Nullable LivingEntity e) {
		if (e instanceof Mob m && Comrades.target(m) instanceof LivingEntity t) {
			return t.position().subtract(m.position()).multiply(1.0, 0.0, 1.0);
		}
		return e == null ? Vec3.ZERO : e.getLookAngle().multiply(1.0, 0.0, 1.0);
	}
}
