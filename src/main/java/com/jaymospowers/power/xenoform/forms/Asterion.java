package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Wounds;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Asterion, the bull-headed brute (scanned from Kingdoms' minotaur, {@code XenoDna}): the minotaur's own build, heavy
 * through the shoulders and horned, carrying its great hammer. Tiv's spec (Tasks thread, 2026-11-08).
 * <ul>
 * <li>R, Charge (held): head down, a run that steers only by a slow lean, never more than {@link #CHARGE_ARC} degrees off
 * the line it started on. It breaks through dirt, wood and leaves whoever placed them ({@link #breakable}); anything else
 * solid stops it dead. The first creature met is caught on the horns and carried in front, stunned after every impact on
 * the way, each block it's put through adding {@link #IMPACT_RAMP}; a wall runs it through (a torso wound). Others met on
 * the way are flung aside. Asterion can't be hurt through the charge (and {@link #CHARGE_SAFE_AFTER} past it) by anything
 * but the carried creature's own blows. Let go and the catch is flung off ahead.</li>
 * <li>G, Roar (held): the longer it's held, the further and harder the shockwave that runs out from it when let go;
 * whoever it reaches is thrown back limp. Held, Asterion is slowed to a crawl, and a longer roar waits longer.</li>
 * <li>V, Slam: the minotaur's ground slam; everyone round it thrown off their feet.</li>
 * <li>B, Gore: the horns into the creature in reach, a bleeding cut and a toss.</li>
 * </ul>
 * Its own blows are the minotaur's hammer (its fists). It takes up big weapons ({@link #wields}: Better Combat's two-handed
 * ones, {@code core/TwoHanded}) and swings them in place of the hammer.
 */
public final class Asterion extends XenoForm {
	// ---- tuning ----
	/** The charge: speed (blocks a tick), the shortest and longest run (ticks), the whole arc it may lean through and how fast it leans (degrees), the wait after. */
	public static final double CHARGE_SPEED = 0.62;
	public static final int CHARGE_MIN_TICKS = 6, CHARGE_MAX_TICKS = 70, CHARGE_COOLDOWN = 140;
	public static final float CHARGE_ARC = 24.0F, CHARGE_TURN = 1.0F;
	/** Untouchable this long past the charge's end (ticks). */
	public static final int CHARGE_SAFE_AFTER = 10;
	/** How far ahead (blocks) blocks are broken or stop it; most blocks broken in one tick. */
	public static final double LOOKAHEAD = 0.45;
	public static final int MAX_BREAK_PER_TICK = 24;
	/** The hardest block it breaks through (dirt, wood and leaves are all well under). */
	public static final float BREAK_HARDNESS_MAX = 5.0F;
	/** The biggest creature it catches on its horns (blocks); anything bigger is gored and stops it. */
	public static final double CARRY_MAX_WIDTH = 1.6, CARRY_MAX_HEIGHT = 2.6;
	/** How high on its body the catch is held (share of its height), and the gap in front of it (blocks). */
	public static final double CARRY_LIFT = 0.4, CARRY_GAP = 0.15;
	/** The catch: the blow as it's caught, each impact's blow and how much more per block it has been put through, the most an impact does, and the stun after each (ticks). */
	public static final float CATCH_DAMAGE = 6.0F, IMPACT_DAMAGE = 3.0F, IMPACT_RAMP = 1.5F, IMPACT_MAX = 18.0F;
	public static final int IMPACT_STUN = 12;
	/** A wall: the catch is run through for this (plus the ramp), a torso wound this deep, stunned this long. */
	public static final float RUN_THROUGH_DAMAGE = 14.0F, RUN_THROUGH_TORSO = 6.0F;
	public static final int RUN_THROUGH_STUN = 40;
	/** Too big to lift: gored for this, and the charge stops. */
	public static final float TOO_BIG_DAMAGE = 10.0F;
	/** Others met while it carries: their blow and how far they're flung (out, up). */
	public static final float BYSTANDER_DAMAGE = 6.0F;
	public static final double BYSTANDER_OUT = 1.2, BYSTANDER_UP = 0.5;
	/** A carried mob that can fight strikes back this often (ticks), when it isn't stunned. */
	public static final int CARRIED_SWING_TICKS = 20;
	/** Let go (or out of breath): the catch is flung off ahead this hard (out, up). */
	public static final double DROP_OUT = 0.9, DROP_UP = 0.35;

	/** Held this long (ticks), the roar lets go by itself. */
	public static final int ROAR_LONGEST = 100;
	/** The roar: longest useful hold (ticks), its reach, force (out, up) and blow at the shortest and longest hold, how fast the wave runs, the wait at each end. */
	public static final int ROAR_MAX_HOLD = 50;
	public static final double ROAR_MIN_RADIUS = 3.0, ROAR_MAX_RADIUS = 14.0, ROAR_MIN_FORCE = 0.6, ROAR_MAX_FORCE = 2.0, ROAR_MIN_UP = 0.3, ROAR_MAX_UP = 0.7;
	public static final float ROAR_MIN_DAMAGE = 1.0F, ROAR_MAX_DAMAGE = 4.0F;
	public static final double ROAR_WAVE_SPEED = 1.0;
	public static final int ROAR_MIN_COOLDOWN = 100, ROAR_MAX_COOLDOWN = 400;
	/** Holding the roar: Slowness this strong; the bellow's pose after letting go (ticks). */
	public static final int ROAR_HOLD_SLOW = 3, ROAR_POSE = 12;

	/** The slam: ticks until it lands (its swing), then its follow-through, its reach and blow, how far it throws (out, up), the wait. */
	public static final int SLAM_WINDUP = 15, SLAM_RECOVER = 12, SLAM_COOLDOWN = 160;
	public static final double SLAM_RADIUS = 4.5, SLAM_OUT = 0.9, SLAM_UP = 0.6;
	public static final float SLAM_DAMAGE = 9.0F;

	/** The gore: reach, blow, toss (out, up), its pose (ticks), the wait. */
	public static final double GORE_REACH = 3.5, GORE_OUT = 1.0, GORE_UP = 0.45;
	public static final float GORE_DAMAGE = 9.0F;
	public static final int GORE_POSE = 8, GORE_COOLDOWN = 50;

	private static final boolean BETTER_COMBAT = FabricLoader.getInstance().isModLoaded("bettercombat");

	/** One player's Asterion, kept on {@code XenoformPower.Live}. */
	public static final class Scratch {
		/** Ticks into the charge (0: none), whether R is still held, its heading now and when it started, what it broke so far. */
		int charge;
		boolean held;
		Vec3 dir = Vec3.ZERO, start = Vec3.ZERO;
		int blocks;
		/** The creature on its horns (entity id, -1 none), ticks to its next blow back, and who was flung aside. */
		int carried = -1;
		int swingIn;
		final Set<Integer> bowled = new HashSet<>();
		/** Untouchable until (game time). */
		long safeUntil;
		/** Ticks G has been held (0: not held), and the bellow's pose left. */
		int roarHeld, roarPose;
		/** The shockwave running out: where from, how far it has gone and goes, how hard, and who it has reached. */
		@Nullable Vec3 roarAt;
		double roarRadius, roarMax, roarForce, roarUp;
		float roarDamage;
		final Set<Integer> roared = new HashSet<>();
		/** Ticks until the slam lands, and its pose left; the gore's pose left. */
		int slam, slamPose, gorePose;

		public void clear() {
			charge = blocks = swingIn = roarHeld = roarPose = slam = slamPose = gorePose = 0;
			held = false;
			dir = start = Vec3.ZERO;
			carried = -1;
			bowled.clear();
			safeUntil = 0L;
			roarAt = null;
			roarRadius = roarMax = roarForce = roarUp = 0.0;
			roarDamage = 0.0F;
			roared.clear();
		}
	}

	public Asterion() {
		super("asterion", 1.6F, 48.0F, 8.0, -0.05, 0.8, 0.5, Items.MACE, 0x8A78E0);
	}

	/** Only by the DNA table (Kingdoms' minotaur). */
	@Override
	public boolean scans(Entity creature) {
		return false;
	}

	/** Big weapons: Better Combat's two-handed ones (claymores, war axes, glaives, spears, scythes and the rest). */
	@Override
	public boolean wields(ItemStack stack) {
		return BETTER_COMBAT && !stack.isEmpty() && com.jaymospowers.core.TwoHanded.is(stack);
	}

	/** What its body shows for everyone ({@code XenoformState.ACT}). */
	public static @Nullable String act(XenoformPower.Live live) {
		Scratch s = live.asterion;
		if (s.charge > 0) {
			return "charge";
		}
		if (s.roarHeld > 0) {
			return "roar_hold";
		}
		if (s.roarPose > 0) {
			return "roar";
		}
		if (s.slamPose > 0) {
			return "slam";
		}
		return s.gorePose > 0 ? "gore" : null;
	}

	private static boolean busy(Scratch s) {
		return s.charge > 0 || s.roarHeld > 0 || s.slam > 0;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		Scratch s = live.asterion;
		LivingEntity carried = carried(player.level(), s);
		if (carried != null) {
			fling(carried, s.dir.scale(DROP_OUT).add(0.0, DROP_UP, 0.0));
		}
		s.clear();
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		Scratch s = live.asterion;
		switch (key) {
			case ABILITY_1 -> {
				if (!down) {
					s.held = false;
				} else if (!busy(s) && ready(player, live, R)) {
					startCharge(player, s);
				}
			}
			case ABILITY_2 -> {
				if (down) {
					if (!busy(s) && ready(player, live, G)) {
						s.roarHeld = 1;
						sound(player, SoundEvents.RAVAGER_AMBIENT, 1.4F, 0.5F);
					}
				} else if (s.roarHeld > 0) {
					roar(player, live, s);
				}
			}
			case ABILITY_3 -> {
				if (down && !busy(s) && ready(player, live, V)) {
					s.slam = SLAM_WINDUP;
					s.slamPose = SLAM_WINDUP + SLAM_RECOVER;
					sound(player, SoundEvents.RAVAGER_ROAR, 0.8F, 1.2F);
					cooldown(player, live, V, SLAM_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (down && !busy(s) && ready(player, live, B)) {
					gore(player, live, s);
				}
			}
			default -> {
			}
		}
	}

	// ---- the charge ------------------------------------------------------------------------------------------------

	private static void startCharge(ServerPlayer player, Scratch s) {
		Vec3 look = player.getLookAngle().horizontal();
		s.dir = s.start = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
		s.charge = 1;
		s.held = true;
		s.blocks = 0;
		s.carried = -1;
		s.bowled.clear();
		sound(player, SoundEvents.RAVAGER_ROAR, 1.5F, 0.8F);
	}

	private static void chargeTick(ServerPlayer player, XenoformPower.Live live, Scratch s, ServerLevel level) {
		s.charge++;
		if (!s.held && s.charge > CHARGE_MIN_TICKS || s.charge > CHARGE_MAX_TICKS) {
			endCharge(player, live, s, false);
			return;
		}
		steer(player, s);
		LivingEntity carried = carried(level, s);
		// what's in its way: dirt, wood and leaves go; anything else solid stops it dead
		AABB front = front(player, s, carried);
		List<BlockPos> breaks = new ArrayList<>();
		BlockState wall = null;
		BlockPos wallAt = null;
		for (BlockPos pos : BlockPos.betweenClosed(front.deflate(1.0E-3))) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			VoxelShape shape = state.getCollisionShape(level, pos);
			if (shape.isEmpty() || !shape.move(pos).bounds().intersects(front)) {
				continue;
			}
			if (breakable(state, level, pos) && breaks.size() < MAX_BREAK_PER_TICK) {
				breaks.add(pos.immutable());
			} else {
				wall = state;
				wallAt = pos.immutable();
				break;
			}
		}
		if (wall != null) {
			hitWall(player, live, s, carried, level, wall, wallAt);
			return;
		}
		if (!breaks.isEmpty()) {
			for (BlockPos pos : breaks) {
				level.destroyBlock(pos, true, player);
			}
			s.blocks += breaks.size();
			sound(player, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, 0.9F, 0.7F);
			if (carried != null) {
				// put through it: the blow grows with every block so far
				impact(player, live, s, carried, level);
			}
		}
		player.setDeltaMovement(s.dir.x * CHARGE_SPEED, player.getDeltaMovement().y, s.dir.z * CHARGE_SPEED);
		player.syncVelocity = true;
		// creatures met: the first is caught on the horns; with one carried, the rest are flung aside
		AABB reach = player.getBoundingBox().expandTowards(s.dir.scale(LOOKAHEAD + 0.3));
		if (carried != null) {
			reach = reach.minmax(carried.getBoundingBox().inflate(0.3));
		}
		for (LivingEntity e : around(player, 4.0 + (carried != null ? carried.getBbWidth() : 0.0))) {
			if (e == carried || s.bowled.contains(e.getId()) || !e.getBoundingBox().intersects(reach)
				|| e.position().subtract(player.position()).horizontal().dot(s.dir) <= 0.0) {
				// (only what's ahead of it: brushing past something beside or behind isn't meeting it)
				continue;
			}
			if (carried == null) {
				if (!liftable(e)) {
					// too big to lift: gored, and it stops here
					e.hurtServer(level, player.damageSources().playerAttack(player), TOO_BIG_DAMAGE);
					shove(e, player.position(), 0.5, 0.2);
					live.hurt = true;
					sound(player, SoundEvents.RAVAGER_ATTACK, 1.5F, 0.6F);
					Shakes.heavy(player, player.position(), 10.0, 0.6F);
					endCharge(player, live, s, true);
					return;
				}
				catchOn(player, live, s, e, level);
				carried = e;
			} else {
				e.setInvulnerableTime(0);
				e.hurtServer(level, player.damageSources().playerAttack(player), BYSTANDER_DAMAGE);
				Vec3 away = e.position().subtract(player.position()).horizontal();
				// thrown off to the side of its line, not ahead of it
				Vec3 side = new Vec3(-s.dir.z, 0.0, s.dir.x);
				side = side.dot(away) < 0.0 ? side.scale(-1.0) : side;
				fling(e, side.add(s.dir.scale(0.5)).normalize().scale(BYSTANDER_OUT).add(0.0, BYSTANDER_UP, 0.0));
				s.bowled.add(e.getId());
				live.hurt = true;
				sound(player, SoundEvents.RAVAGER_ATTACK, 1.0F, 0.9F);
				// the carried one feels it too, though only blocks make it worse
				impact(player, live, s, carried, level);
			}
		}
		if (carried != null) {
			hold(player, s, carried);
			strikeBack(player, s, carried, level);
		}
		if (player.tickCount % 3 == 0) {
			BlockState under = level.getBlockState(player.blockPosition().below());
			if (!under.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.4, 0.05,
					0.4, 0.05);
			}
		}
		if (player.tickCount % 6 == 0) {
			sound(player, SoundEvents.RAVAGER_STEP, 1.0F, 0.8F);
		}
	}

	/** A slow lean toward where it looks, never past half the arc either side of the line it started on. */
	private static void steer(ServerPlayer player, Scratch s) {
		Vec3 look = player.getLookAngle().horizontal();
		if (look.lengthSqr() < 1.0E-4) {
			return;
		}
		float start = yaw(s.start), now = yaw(s.dir);
		float turn = Mth.clamp(Mth.wrapDegrees(yaw(look) - now), -CHARGE_TURN, CHARGE_TURN);
		float off = Mth.clamp(Mth.wrapDegrees(now + turn - start), -CHARGE_ARC * 0.5F, CHARGE_ARC * 0.5F);
		float next = (start + off) * Mth.DEG_TO_RAD;
		s.dir = new Vec3(-Mth.sin(next), 0.0, Mth.cos(next));
	}

	/** A heading's yaw (degrees, the game's way: 0 is +z). */
	private static float yaw(Vec3 dir) {
		return (float)(Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG);
	}

	/** The space it runs into next tick: its body (and the catch's) moved ahead, from above what it can step up. */
	private static AABB front(ServerPlayer player, Scratch s, @Nullable LivingEntity carried) {
		double ahead = LOOKAHEAD + (carried != null ? carried.getBbWidth() + CARRY_GAP : 0.0);
		AABB box = player.getBoundingBox().expandTowards(s.dir.scale(ahead));
		double floor = player.getY() + player.maxUpStep() + 0.01;
		return new AABB(box.minX, Math.min(floor, box.maxY - 0.1), box.minZ, box.maxX, box.maxY, box.maxZ);
	}

	/** Dirt, wood (what an axe takes) and leaves, whoever placed them: no claim or owner is asked. Nothing with a block entity. */
	static boolean breakable(BlockState state, ServerLevel level, BlockPos pos) {
		if (state.hasBlockEntity()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0.0F || hardness > BREAK_HARDNESS_MAX) {
			return false;
		}
		return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH) || state.is(BlockTags.LEAVES)
			|| state.is(BlockTags.MINEABLE_WITH_AXE);
	}

	/** Whether {@code e} can be caught and carried on the horns. */
	private static boolean liftable(LivingEntity e) {
		return e.getBbWidth() <= CARRY_MAX_WIDTH && e.getBbHeight() <= CARRY_MAX_HEIGHT && !e.isPassenger() && !e.isVehicle() && !Carry.isCarried(e)
			&& !(e instanceof ArmorStand);
	}

	private static void catchOn(ServerPlayer player, XenoformPower.Live live, Scratch s, LivingEntity e, ServerLevel level) {
		s.carried = e.getId();
		s.swingIn = CARRIED_SWING_TICKS;
		e.setInvulnerableTime(0);
		e.hurtServer(level, player.damageSources().playerAttack(player), CATCH_DAMAGE);
		DarktideStatus.stun(e, IMPACT_STUN);
		if (e instanceof Mob mob) {
			mob.setTarget(player);
		}
		live.hurt = true;
		sound(player, SoundEvents.RAVAGER_ATTACK, 1.5F, 0.8F);
		Shakes.heavy(player, player.position(), 8.0, 0.4F);
	}

	/** An impact on the creature it carries: a blow that grows with every block it has been put through, and a stun. */
	private static void impact(ServerPlayer player, XenoformPower.Live live, Scratch s, LivingEntity carried, ServerLevel level) {
		float damage = Math.min(IMPACT_MAX, IMPACT_DAMAGE + IMPACT_RAMP * s.blocks);
		carried.setInvulnerableTime(0);
		carried.hurtServer(level, player.damageSources().playerAttack(player), damage);
		DarktideStatus.stun(carried, IMPACT_STUN);
		level.sendParticles(ParticleTypes.CRIT, carried.getX(), carried.getY() + carried.getBbHeight() * 0.5, carried.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		live.hurt = true;
	}

	/** Something solid: it stops dead. A creature on its horns is run through against it, a wound to the torso. */
	private static void hitWall(ServerPlayer player, XenoformPower.Live live, Scratch s, @Nullable LivingEntity carried, ServerLevel level, BlockState wall,
		BlockPos at) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wall), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.15);
		sound(player, SoundEvents.ANVIL_LAND, 1.0F, 0.5F);
		Shakes.heavy(player, player.position(), 12.0, 0.8F);
		if (carried != null) {
			carried.setInvulnerableTime(0);
			carried.hurtServer(level, player.damageSources().playerAttack(player), RUN_THROUGH_DAMAGE + IMPACT_RAMP * s.blocks);
			if (carried.isAlive()) {
				Wounds.hurtPart(carried, BodyPart.BODY, RUN_THROUGH_TORSO, player);
				Bleeding.open(carried, BodyPart.BODY, true);
				DarktideStatus.stun(carried, RUN_THROUGH_STUN);
			}
			carried.setDeltaMovement(Vec3.ZERO);
			carried.syncVelocity = true;
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, carried.getX(), carried.getY() + carried.getBbHeight() * 0.6, carried.getZ(), 10, 0.3, 0.3, 0.3,
				0.1);
			sound(player, SoundEvents.RAVAGER_ATTACK, 2.0F, 0.5F);
			live.hurt = true;
			s.carried = -1;
		}
		endCharge(player, live, s, true);
	}

	/** The charge is over: what it still carries is flung off ahead (unless a wall already took it), and it can be hurt again shortly. */
	private static void endCharge(ServerPlayer player, XenoformPower.Live live, Scratch s, boolean stopped) {
		LivingEntity carried = carried(player.level(), s);
		if (carried != null) {
			fling(carried, s.dir.scale(DROP_OUT).add(0.0, DROP_UP, 0.0));
		}
		Vec3 m = player.getDeltaMovement();
		player.setDeltaMovement(stopped ? 0.0 : m.x * 0.3, m.y, stopped ? 0.0 : m.z * 0.3);
		player.syncVelocity = true;
		s.charge = 0;
		s.held = false;
		s.carried = -1;
		s.blocks = 0;
		s.bowled.clear();
		s.safeUntil = player.level().getGameTime() + CHARGE_SAFE_AFTER;
		cooldown(player, live, R, CHARGE_COOLDOWN);
	}

	/** The catch, held on the horns in front of it, off the ground, facing it (a player keeps their own view, to fight back). */
	private static void hold(ServerPlayer player, Scratch s, LivingEntity carried) {
		double lift = Mth.clamp(player.getBbHeight() * CARRY_LIFT, 0.0, Math.max(0.0, player.getBbHeight() - carried.getBbHeight()));
		Vec3 at = player.position().add(s.dir.scale(player.getBbWidth() * 0.5 + carried.getBbWidth() * 0.5 + CARRY_GAP))
			.add(player.getDeltaMovement().multiply(1.0, 0.0, 1.0)).add(0.0, lift, 0.0);
		if (carried instanceof ServerPlayer caught) {
			caught.teleportTo(player.level(), at.x, at.y, at.z, Relative.ROTATION, 0.0F, 0.0F, false);
		} else {
			carried.setPos(at.x, at.y, at.z);
			float yaw = yaw(s.dir) + 180.0F;
			carried.setYRot(yaw);
			carried.setYBodyRot(yaw);
			carried.setYHeadRot(yaw);
			carried.setDeltaMovement(player.getDeltaMovement());
			carried.needsSync = true;
			carried.syncVelocity = true;
		}
		carried.resetFallDistance();
	}

	/** A carried mob that can fight swings at it with whatever it holds, whenever it isn't stunned (a player just swings). */
	private static void strikeBack(ServerPlayer player, Scratch s, LivingEntity carried, ServerLevel level) {
		if (!(carried instanceof Mob mob) || --s.swingIn > 0 || DarktideStatus.stunned(mob)) {
			return;
		}
		AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		if (attack == null || attack.getValue() <= 0.0) {
			return;
		}
		s.swingIn = CARRIED_SWING_TICKS;
		mob.swingForAttack(InteractionHand.MAIN_HAND);
		mob.doHurtTarget(level, player);
	}

	private static @Nullable LivingEntity carried(ServerLevel level, Scratch s) {
		if (s.carried < 0) {
			return null;
		}
		if (level.getEntity(s.carried) instanceof LivingEntity e && e.isAlive() && !e.isRemoved()) {
			return e;
		}
		s.carried = -1;
		return null;
	}

	/** Thrown limp: a player into Kitchen's ragdoll, anything else tumbling as a thrown body does. */
	static void fling(LivingEntity e, Vec3 velocity) {
		e.setDeltaMovement(velocity);
		e.needsSync = true;
		e.syncVelocity = true;
		e.resetFallDistance();
		if (e instanceof ServerPlayer player) {
			KitchenCompat.knockLimp(player, velocity.x, velocity.y, velocity.z);
		} else {
			e.setAttached(Carry.THROWN, e.level().getGameTime());
		}
	}

	/** Can't be hurt through the charge, and a moment past it, except by the blows of the creature it carries. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		Scratch s = live.asterion;
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || s.charge <= 0 && player.level().getGameTime() >= s.safeUntil) {
			return true;
		}
		Entity by = source.getEntity();
		return s.charge > 0 && s.carried >= 0 && by != null && by.getId() == s.carried && source.getDirectEntity() == by;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.asterion.charge > 0;
	}

	// ---- the roar --------------------------------------------------------------------------------------------------

	/** Let go: the shockwave starts out from where it stands, as far and as hard as the hold built it. */
	private static void roar(ServerPlayer player, XenoformPower.Live live, Scratch s) {
		float f = Mth.clamp((s.roarHeld - 1) / (float)ROAR_MAX_HOLD, 0.0F, 1.0F);
		s.roarHeld = 0;
		s.roarPose = ROAR_POSE;
		s.roarAt = player.position();
		s.roarRadius = 0.0;
		s.roarMax = Mth.lerp(f, ROAR_MIN_RADIUS, ROAR_MAX_RADIUS);
		s.roarForce = Mth.lerp(f, ROAR_MIN_FORCE, ROAR_MAX_FORCE);
		s.roarUp = Mth.lerp(f, ROAR_MIN_UP, ROAR_MAX_UP);
		s.roarDamage = Mth.lerp(f, ROAR_MIN_DAMAGE, ROAR_MAX_DAMAGE);
		s.roared.clear();
		sound(player, SoundEvents.RAVAGER_ROAR, 2.0F + f * 2.0F, 0.75F - f * 0.2F);
		Shakes.heavy(player, player.position(), s.roarMax + 4.0, 0.3F + f * 0.7F);
		cooldown(player, live, G, Math.round(Mth.lerp(f, ROAR_MIN_COOLDOWN, ROAR_MAX_COOLDOWN)));
	}

	private static void roarTick(ServerPlayer player, XenoformPower.Live live, Scratch s, ServerLevel level) {
		if (s.roarHeld > 0) {
			// held: rooted to a crawl, the breath drawn in, the rumble rising until it can't grow any more
			if (++s.roarHeld >= ROAR_LONGEST) {
				roar(player, live, s);
				return;
			}
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, ROAR_HOLD_SLOW, false, false));
			if (s.roarHeld % 10 == 0 && s.roarHeld <= ROAR_MAX_HOLD) {
				sound(player, SoundEvents.RAVAGER_AMBIENT, 1.0F, 0.5F + s.roarHeld / (float)ROAR_MAX_HOLD * 0.3F);
			}
			if (player.tickCount % 2 == 0) {
				level.sendParticles(ParticleTypes.POOF, player.getX(), player.getEyeY(), player.getZ(), 1 + Math.min(s.roarHeld, ROAR_MAX_HOLD) / 15, 0.3, 0.2,
					0.3, 0.01);
			}
		}
		if (s.roarPose > 0) {
			s.roarPose--;
		}
		if (s.roarAt == null) {
			return;
		}
		double from = s.roarRadius;
		s.roarRadius = Math.min(s.roarMax, s.roarRadius + ROAR_WAVE_SPEED);
		Vec3 at = s.roarAt;
		ring(level, ParticleTypes.CLOUD, at, s.roarRadius, Math.max(8, (int)(s.roarRadius * 6.0)));
		AABB box = new AABB(at, at).inflate(s.roarRadius, 3.0, s.roarRadius);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator()
			&& !(e instanceof ArmorStand) && !spared(player, e))) {
			double d = Math.sqrt(e.distanceToSqr(at.x, e.getY(), at.z));
			if (d > s.roarRadius || d < from - 1.0 || !s.roared.add(e.getId())) {
				continue;
			}
			// harder the nearer it began
			double k = 1.0 - 0.5 * Math.min(1.0, d / Math.max(1.0, s.roarMax));
			Vec3 away = e.position().subtract(at).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away;
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			e.hurtServer(level, player.damageSources().playerAttack(player), s.roarDamage);
			fling(e, away.scale(s.roarForce * k).add(0.0, s.roarUp * k, 0.0));
			live.hurt = true;
		}
		if (s.roarRadius >= s.roarMax) {
			s.roarAt = null;
		}
	}

	// ---- the slam and the gore -------------------------------------------------------------------------------------

	/** The slam lands: the ground breaks round it and everyone near is thrown off their feet. */
	private static void slam(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Vec3 at = player.position();
		for (LivingEntity e : around(player, SLAM_RADIUS)) {
			e.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE);
			Vec3 away = e.position().subtract(at).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			fling(e, away.scale(SLAM_OUT).add(0.0, SLAM_UP, 0.0));
			live.hurt = true;
		}
		BlockState ground = level.getBlockState(player.blockPosition().below());
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, 60, 2.0, 0.2, 2.0, 0.2);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 3, 1.5, 0.1, 1.5, 0.0);
		sound(player, SoundEvents.MACE_SMASH_GROUND_HEAVY, 2.0F, 0.6F);
		Shakes.heavy(player, at, SLAM_RADIUS + 8.0, 0.9F);
	}

	/** The horns into the creature in reach: a bleeding cut and a toss. Nothing there, nothing spent. */
	private static void gore(ServerPlayer player, XenoformPower.Live live, Scratch s) {
		LivingEntity target = aimed(player, GORE_REACH, e -> true);
		if (target == null) {
			return;
		}
		ServerLevel level = player.level();
		target.hurtServer(level, player.damageSources().playerAttack(player), GORE_DAMAGE);
		Bleeding.open(target, BodyPart.BODY, true);
		Vec3 away = target.position().subtract(player.position()).horizontal();
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away.normalize();
		fling(target, away.scale(GORE_OUT).add(0.0, GORE_UP, 0.0));
		s.gorePose = GORE_POSE;
		live.hurt = true;
		sound(player, SoundEvents.RAVAGER_ATTACK, 1.3F, 0.9F);
		cooldown(player, live, B, GORE_COOLDOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		Scratch s = live.asterion;
		ServerLevel level = player.level();
		if (s.charge > 0) {
			chargeTick(player, live, s, level);
		}
		roarTick(player, live, s, level);
		if (s.slam > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 3, 2, false, false));
			if (--s.slam == 0) {
				slam(player, live, level);
			}
		}
		if (s.slamPose > 0) {
			s.slamPose--;
		}
		if (s.gorePose > 0) {
			s.gorePose--;
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.CAMPFIRE_COSY_SMOKE, player.position().add(0.0, 1.5, 0.0), 24, 1.0, 0.02);
		sound(player, SoundEvents.RAVAGER_ROAR, 2.5F, 0.5F);
		return true;
	}
}
