package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.BlockCluster;
import com.jaymospowers.core.Debris;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.9) Bulwark's Ultimate, Titan: the four-armed brute grown to twice your size in stone plate, with a health bar of its own on
 * top of Bulwark's. Its model is {@code UltimateTitan}.
 * <ul>
 * <li>R, Quake Combo: four blows, one per arm, then a double-fisted hammer that launches the target and breaks the block behind it.</li>
 * <li>G, Wallbreaker Clap: a wider shockwave that also smashes the wall ahead.</li>
 * <li>V, Boulder Hurl: each pair of arms holds a creature; V again throws both. Aimed at the ground, it rips out a big chunk that
 * bursts on landing.</li>
 * <li>B, Meteor Slam: a higher leap and a landing that shakes the screen.</li>
 * <li>C, Earthsplitter: all four fists hammer the ground and a fissure runs along your look.</li>
 * </ul>
 */
public final class Titan extends XenoUltimate {
	// ---- tuning ----
	/** Quake Combo: blows before the hammer, ticks between beats, damage of a blow and of the hammer, reach, cooldown. */
	public static final int COMBO_BLOWS = 4, COMBO_SPACING = 4, COMBO_COOLDOWN = 60;
	public static final float BLOW_DAMAGE = 7.0F, HAMMER_DAMAGE = 11.0F;
	public static final double COMBO_REACH = 5.5, HAMMER_OUT = 1.7, HAMMER_UP = 0.9;
	/** Wallbreaker Clap: cooldown, radius, damage. */
	public static final int CLAP_COOLDOWN = 140;
	public static final double CLAP_RADIUS = 7.0;
	public static final float CLAP_DAMAGE = 9.0F;
	/** Boulder Hurl: cooldown, reach, widest creature held, throw speed (blocks/tick), the chunk's size, its landing radius and damage. */
	public static final int HURL_COOLDOWN = 30;
	public static final double GRAB_REACH = 6.0, GRAB_MAX_WIDTH = 2.4, GRAB_SECOND_RANGE = 5.0, THROW_SPEED = 1.7;
	public static final int CHUNK_BLOCKS = 8;
	public static final double HURL_RADIUS = 3.0;
	public static final float HURL_DAMAGE = 8.0F;
	/** The ticks a thrown chunk is watched for its landing. */
	public static final int HURL_WATCH = 160;
	/** Meteor Slam: cooldown, the leap's out and up, the landing's radius, damage and shove. */
	public static final int SLAM_COOLDOWN = 140;
	public static final double LEAP_OUT = 1.3, LEAP_UP = 1.25, SLAM_RADIUS = 6.0;
	public static final float SLAM_DAMAGE = 12.0F;
	/** Screen shake (degrees, and how far it's felt): the clap, the landing, the change-in. */
	public static final float CLAP_SHAKE = 1.3F, TITAN_SHAKE = 1.6F;
	public static final double CLAP_SHAKE_RANGE = 18.0, TITAN_SHAKE_RANGE = 24.0;
	/** Earthsplitter: cooldown (30 s), the fissure's length, how far it runs a tick, its half-width, damage, the fling, the softest blocks it tears (hardness). */
	public static final int SPLIT_COOLDOWN = 600;
	public static final double FISSURE_LENGTH = 12.0, FISSURE_SPEED = 1.5, FISSURE_HALF_WIDTH = 1.3;
	public static final float FISSURE_DAMAGE = 14.0F, FISSURE_TEAR_HARDNESS = 0.6F;
	public static final double FISSURE_FLING = 1.0;
	/** The wall smash that comes with every clap: how deep, how far either side of the middle, how tall (blocks), and the hardest block it breaks. */
	public static final int BREAK_DEPTH = 3, BREAK_HALF_WIDTH = 1, BREAK_HEIGHT = 3;
	public static final float BREAK_MAX_HARDNESS = 6.0F;

	/** A chunk in flight: watched until it is gone, then it bursts where it was last seen. */
	private static final class Hurl {
		final int id;
		Vec3 last;
		int age;

		Hurl(int id, Vec3 last) {
			this.id = id;
			this.last = last;
		}
	}

	/** A fissure on its way: where it started, the way it runs, how far it has got, and who it has already thrown up. */
	private static final class Fissure {
		final Vec3 origin, dir;
		double reached;
		final Set<Integer> struck = new HashSet<>();

		Fissure(Vec3 origin, Vec3 dir) {
			this.origin = origin;
			this.dir = dir;
		}
	}

	/** What each Titan has under way (server thread only). */
	private static final class Work {
		final List<Hurl> hurls = new ArrayList<>();
		final List<Fissure> fissures = new ArrayList<>();
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();

	public Titan(XenoForm base) {
		super(base, 2.0F, 30.0F, 10.0, -0.15, 0.9, 1.0);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.NETHERITE_INGOT, Items.HEAVY_CORE, Items.COBBLED_DEEPSLATE, Items.MAGMA_BLOCK, Items.NETHERITE_BLOCK};
	}

	/** The change-in film zooms on these in turn (model part names): the gauntlet arms, the shoulder plates, the spine's spikes, the head. */
	@Override
	public String[] morphParts() {
		return new String[] {"right_lower_arm", "left_lower_arm", "pauldron_r+pauldron_l+pauldron", "spine", "head"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.combo = 0;
		if (live.leaping || live.leapTicks > 0) {
			player.resetFallDistance(); // (0.20.9) a leap cut short by a change: the fall it forgave is forgiven
		}
		live.leaping = false;
		live.leapTicks = 0;
		ServerLevel level = player.level();
		Debris.ring(level, player.blockPosition(), 3, 1.2F);
		ring(level, ParticleTypes.POOF, player.position(), 3.0, 24);
		sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.5F, 0.5F);
		sound(player, SoundEvents.ANVIL_LAND, 1.0F, 0.4F);
		Shakes.heavy(player, player.position(), TITAN_SHAKE_RANGE, TITAN_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		release(player, live);
		WORK.remove(player.getUUID());
		live.combo = 0;
		if (live.leaping || live.leapTicks > 0) {
			player.resetFallDistance(); // (0.20.9) a leap cut short by a change: the fall it forgave is forgiven
		}
		live.leaping = false;
		live.leapTicks = 0;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					live.combo = COMBO_BLOWS + 1;
					live.comboNext = player.level().getGameTime();
					cooldown(player, live, R, COMBO_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					clap(player, live);
					smashWalls(player);
					cooldown(player, live, G, CLAP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V) && grabOrThrow(player, live)) {
					cooldown(player, live, V, HURL_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && player.onGround() && !live.leaping) {
					Vec3 out = player.getLookAngle().horizontal();
					out = out.lengthSqr() < 1.0E-4 ? Vec3.ZERO : out.normalize().scale(LEAP_OUT);
					player.setDeltaMovement(out.x, LEAP_UP, out.z);
					player.syncVelocity = true;
					live.leaping = true;
					live.leapTicks = 0;
					sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.4F);
					Shakes.send(player, 0.6F, player.position());
					cooldown(player, live, B, SLAM_COOLDOWN);
				}
			}
		}
	}

	/** C: Earthsplitter. All four fists hammer the ground and a fissure runs along the look. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (!ready(player, live, C)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 dir = player.getLookAngle().horizontal();
		dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()).horizontal() : dir.normalize();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		Vec3 feet = player.position();
		WORK.computeIfAbsent(player.getUUID(), id -> new Work()).fissures.add(new Fissure(feet, dir));
		Debris.ring(level, player.blockPosition(), 3, 1.4F);
		sound(player, SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.6F);
		Shakes.heavy(player, feet, TITAN_SHAKE_RANGE, TITAN_SHAKE);
		cooldown(player, live, C, SPLIT_COOLDOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		long now = player.level().getGameTime();
		if (live.combo > 0 && now >= live.comboNext) {
			live.combo--;
			live.comboNext = now + COMBO_SPACING;
			punch(player, live.combo == 0);
		}
		if (live.leaping) {
			live.leapTicks++;
			if (live.leapTicks > 3 && (player.onGround() || player.isInWater())) {
				live.leaping = false;
				slam(player);
				live.leapTicks = 0; // (0.20.9) the landing is over: later falls hurt again
			} else if (live.leapTicks > 100) {
				live.leaping = false;
				live.leapTicks = 0; // (0.20.9) timed out with no slam: later falls hurt again
			}
		}
		holdTick(player, live);
		Work work = WORK.get(player.getUUID());
		if (work != null) {
			workTick(player, work);
		}
		// it lumbers: dust at every heavy step
		if (player.tickCount % 3 == 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0.01) {
			burst(player.level(), ParticleTypes.POOF, player.position(), 2, 0.6, 0.0);
		}
	}

	/** A leap's landing never hurts the Titan. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !(source.is(DamageTypeTags.IS_FALL) && (live.leaping || live.leapTicks > 0 && live.leapTicks < 120));
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.leaping;
	}

	// ---- R: Quake Combo ----

	private static void punch(ServerPlayer player, boolean hammer) {
		if (hammer) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		} else {
			player.swing(player.getRandom().nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		}
		LivingEntity target = aimed(player, COMBO_REACH, e -> true);
		if (target == null) {
			sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.5F);
			return;
		}
		ServerLevel level = player.level();
		target.hurtServer(level, player.damageSources().playerAttack(player), hammer ? HAMMER_DAMAGE : BLOW_DAMAGE);
		target.hurtTime = 0;
		Vec3 centre = target.getBoundingBox().getCenter();
		if (hammer) {
			shove(target, player.position(), HAMMER_OUT, HAMMER_UP);
			sound(player, SoundEvents.ANVIL_LAND, 1.0F, 0.6F);
			sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.5F);
			burst(level, ParticleTypes.CRIT, centre, 16, 0.5, 0.3);
			breakBehind(player, target);
			Shakes.heavy(player, centre, 14.0, 1.2F);
		} else {
			shove(target, player.position(), 0.3, 0.1);
			sound(player, SoundEvents.IRON_GOLEM_ATTACK, 0.9F, 0.8F);
			burst(level, ParticleTypes.CRIT, centre, 5, 0.3, 0.2);
		}
	}

	/** The hammer's other half: the block behind the target (feet and head height) breaks, if it is breakable. */
	private static void breakBehind(ServerPlayer player, LivingEntity target) {
		Vec3 away = target.position().subtract(player.position()).horizontal();
		if (away.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 spot = target.position().add(away.normalize().scale(target.getBbWidth() * 0.5 + 1.0));
		int broken = 0;
		for (int h = 0; h < 2; h++) {
			if (breakBlock(player, BlockPos.containing(spot.x, target.getY() + h, spot.z))) {
				broken++;
			}
		}
		if (broken > 0) {
			sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.0F);
		}
	}

	/** Breaks the block at {@code pos} if it is breakable (hardness up to {@link #BREAK_MAX_HARDNESS}, no contents, no fluid, and the player may touch it). */
	private static boolean breakBlock(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		if (state.isAir() || state.hasBlockEntity() || hardness < 0.0F || hardness > BREAK_MAX_HARDNESS || !state.getFluidState().isEmpty()
			|| !level.mayInteract(player, pos)) {
			return false;
		}
		return level.destroyBlock(pos, true, player);
	}

	// ---- G: Wallbreaker Clap ----

	private static void clap(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		for (LivingEntity living : around(player, CLAP_RADIUS)) {
			if (living.getId() == live.grabbed || live.grips.contains(living.getId())) {
				continue;
			}
			living.hurtServer(level, player.damageSources().playerAttack(player), CLAP_DAMAGE);
			shove(living, player.position(), 1.2, 0.6);
		}
		Vec3 feet = player.position();
		ring(level, ParticleTypes.CLOUD, feet, CLAP_RADIUS * 0.5, 20);
		ring(level, ParticleTypes.POOF, feet, CLAP_RADIUS, 32);
		Debris.ring(level, player.blockPosition(), (int)CLAP_RADIUS, 1.2F);
		sound(player, SoundEvents.ANVIL_LAND, 1.4F, 0.4F);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.0F);
		Shakes.heavy(player, feet, CLAP_SHAKE_RANGE, CLAP_SHAKE);
	}

	/** The wall ahead (a box {@link #BREAK_DEPTH} deep) comes down, block by breakable block. */
	private static void smashWalls(ServerPlayer player) {
		net.minecraft.core.Direction facing = player.getDirection();
		net.minecraft.core.Direction across = facing.getClockWise();
		BlockPos feet = player.blockPosition();
		int broken = 0;
		for (int d = 1; d <= BREAK_DEPTH; d++) {
			for (int w = -BREAK_HALF_WIDTH; w <= BREAK_HALF_WIDTH; w++) {
				for (int h = 0; h < BREAK_HEIGHT; h++) {
					if (breakBlock(player, feet.relative(facing, d).relative(across, w).above(h))) {
						broken++;
					}
				}
			}
		}
		if (broken > 0) {
			sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
		}
	}

	// ---- V: Boulder Hurl ----

	private static boolean grabbable(Entity e) {
		return e.getBbWidth() <= GRAB_MAX_WIDTH && !(e instanceof IronGolem);
	}

	private static boolean holding(XenoformPower.Live live) {
		return live.grabbed >= 0 || !live.grips.isEmpty();
	}

	/** V: throw what is held (both); else grab the creature looked at and the nearest other one; else rip a chunk out of the ground and throw it. */
	private static boolean grabOrThrow(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (holding(live)) {
			return throwHeld(player, live);
		}
		LivingEntity first = aimed(player, GRAB_REACH, Titan::grabbable);
		if (first != null) {
			if (!first.startRiding(player, true, true)) {
				return false;
			}
			live.grabbed = first.getId();
			player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.6F);
			// the second pair of arms takes the nearest other creature
			LivingEntity second = null;
			double best = Double.MAX_VALUE;
			for (LivingEntity near : around(player, GRAB_SECOND_RANGE)) {
				if (near == first || near instanceof net.minecraft.world.entity.player.Player || !grabbable(near) || near.isPassenger()) {
					continue;
				}
				double d = near.distanceToSqr(first);
				if (d < best) {
					best = d;
					second = near;
				}
			}
			if (second != null) {
				live.grips.add(second.getId());
				second.stopRiding();
				sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.9F);
			}
			return true;
		}
		// a chunk of the ground, hurled
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(GRAB_REACH + 1.0)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockCluster chunk = BlockCluster.rip(level, hit, CHUNK_BLOCKS, 50.0F, player);
		if (chunk == null) {
			return false;
		}
		Vec3 look = player.getLookAngle();
		chunk.setPos(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z);
		chunk.setDeltaMovement(look.scale(THROW_SPEED * 0.9).add(0.0, 0.15, 0.0));
		chunk.syncVelocity = true;
		chunk.launch(player);
		WORK.computeIfAbsent(player.getUUID(), id -> new Work()).hurls.add(new Hurl(chunk.getId(), chunk.position()));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.4F);
		return true;
	}

	private static boolean throwHeld(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		boolean threw = false;
		List<Entity> held = new ArrayList<>();
		if (live.grabbed >= 0) {
			held.add(level.getEntity(live.grabbed));
		}
		for (int id : live.grips) {
			held.add(level.getEntity(id));
		}
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		side = side.lengthSqr() < 1.0E-4 ? Vec3.ZERO : side.normalize();
		int index = 0;
		for (Entity e : held) {
			if (e != null && e.isAlive()) {
				if (e.getVehicle() == player) {
					e.stopRiding();
				}
				double lateral = (index == 0 ? 0.0 : index % 2 == 1 ? 0.18 : -0.18);
				e.setPos(player.getX() + look.x, player.getEyeY(), player.getZ() + look.z);
				e.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, 0.25, 0.0).add(side.scale(lateral)));
				e.syncVelocity = true;
				e.resetFallDistance();
				threw = true;
				index++;
			}
		}
		live.grabbed = -1;
		live.grips.clear();
		if (threw) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.4F);
		}
		return threw;
	}

	/** Lets go of everything held (a drop back or a revert): the rider climbs off, the carried one is left where it is. */
	private static void release(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.grabbed >= 0) {
			Entity held = level.getEntity(live.grabbed);
			if (held != null && held.getVehicle() == player) {
				held.stopRiding();
			}
			live.grabbed = -1;
		}
		live.grips.clear();
	}

	/** The second creature is carried over the other shoulder; any that got away is forgotten. */
	private static void holdTick(ServerPlayer player, XenoformPower.Live live) {
		if (live.grips.isEmpty()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
		Vec3 side = new Vec3(-look.z, 0.0, look.x);
		int slot = 0;
		for (java.util.Iterator<Integer> it = live.grips.iterator(); it.hasNext();) {
			Entity e = level.getEntity(it.next());
			if (e == null || !e.isAlive() || e.isRemoved()) {
				it.remove();
				continue;
			}
			double lateral = (slot++ % 2 == 0 ? 1.0 : -1.0) * 0.9;
			e.setPos(player.getX() + side.x * lateral, player.getY() + player.getBbHeight() + 0.1, player.getZ() + side.z * lateral);
			e.setDeltaMovement(Vec3.ZERO);
			e.resetFallDistance();
			e.syncVelocity = true;
			if (e instanceof LivingEntity living) {
				living.hurtTime = 0;
			}
		}
	}

	// ---- B: Meteor Slam ----

	private static void slam(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, SLAM_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), SLAM_DAMAGE);
			com.jaymospowers.core.Crushing.mark(living);
			shove(living, player.position(), 1.3, 0.8);
		}
		Vec3 feet = player.position();
		Debris.smash(level, feet, 1.8, player);
		ring(level, ParticleTypes.POOF, feet, SLAM_RADIUS, 32);
		ring(level, ParticleTypes.CLOUD, feet, SLAM_RADIUS * 0.5, 16);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.8F);
		sound(player, SoundEvents.IRON_GOLEM_HURT, 1.2F, 0.4F);
		Shakes.heavy(player, feet, TITAN_SHAKE_RANGE, TITAN_SHAKE);
		player.resetFallDistance();
	}

	// ---- work under way: chunks in flight, fissures ----

	private static void workTick(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		for (java.util.Iterator<Hurl> it = work.hurls.iterator(); it.hasNext();) {
			Hurl hurl = it.next();
			Entity chunk = level.getEntity(hurl.id);
			if (chunk != null && !chunk.isRemoved() && ++hurl.age <= HURL_WATCH) {
				hurl.last = chunk.position();
				continue;
			}
			it.remove();
			if (chunk == null || chunk.isRemoved()) {
				land(player, hurl.last);
			}
		}
		for (java.util.Iterator<Fissure> it = work.fissures.iterator(); it.hasNext();) {
			if (!advance(player, it.next())) {
				it.remove();
			}
		}
		if (work.hurls.isEmpty() && work.fissures.isEmpty()) {
			WORK.remove(player.getUUID());
		}
	}

	/** The chunk landed (it shattered) at {@code at}: it bursts into debris and hits everything near. */
	private static void land(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.level();
		AABB box = new AABB(at, at).inflate(HURL_RADIUS);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !spared(player, e) && e.distanceToSqr(at) <= HURL_RADIUS * HURL_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), HURL_DAMAGE);
			shove(living, at, 0.7, 0.4);
		}
		Debris.ring(level, BlockPos.containing(at), (int)HURL_RADIUS, 1.4F);
		burst(level, ParticleTypes.POOF, at, 14, HURL_RADIUS * 0.5, 0.05);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.9F);
		Shakes.heavy(player, at, 16.0, 1.0F);
	}

	/** One tick of a fissure: it runs {@link #FISSURE_SPEED} blocks further, tearing soft ground and throwing up what it passes. False when it has run its length. */
	private static boolean advance(ServerPlayer player, Fissure fissure) {
		ServerLevel level = player.level();
		double to = Math.min(FISSURE_LENGTH, fissure.reached + FISSURE_SPEED);
		for (double d = fissure.reached + 0.5; d <= to + 1.0E-6; d += 0.5) {
			Vec3 at = fissure.origin.add(fissure.dir.scale(d));
			BlockPos ground = groundAt(level, at);
			if (ground != null) {
				BlockState state = level.getBlockState(ground);
				Debris.puff(level, ground.above(), state, 1.1F);
				float hardness = state.getDestroySpeed(level, ground);
				if (hardness >= 0.0F && hardness <= FISSURE_TEAR_HARDNESS && !state.hasBlockEntity() && level.mayInteract(player, ground)) {
					level.removeBlock(ground, false);
				}
				burst(level, ParticleTypes.POOF, Vec3.atBottomCenterOf(ground).add(0.0, 1.0, 0.0), 2, 0.4, 0.02);
			}
			AABB box = new AABB(at, at).inflate(FISSURE_HALF_WIDTH, 2.5, FISSURE_HALF_WIDTH);
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, box,
				e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !spared(player, e) && !e.isPassengerOfSameVehicle(player)
					&& !fissure.struck.contains(e.getId()))) {
				fissure.struck.add(living.getId());
				living.hurtServer(level, player.damageSources().playerAttack(player), FISSURE_DAMAGE);
				living.setDeltaMovement(living.getDeltaMovement().add(0.0, FISSURE_FLING, 0.0));
				living.syncVelocity = true;
				Debris.ring(level, living.blockPosition(), 1, 1.0F);
			}
		}
		fissure.reached = to;
		if (player.tickCount % 2 == 0) {
			sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.4F, 0.6F);
		}
		return fissure.reached < FISSURE_LENGTH - 1.0E-6;
	}

	/** The top solid block under (or just above) {@code at}, or null over a drop. */
	private static BlockPos groundAt(ServerLevel level, Vec3 at) {
		BlockPos column = BlockPos.containing(at);
		for (int dy = 1; dy >= -3; dy--) {
			BlockPos pos = column.above(dy);
			BlockState state = level.getBlockState(pos);
			if (!state.isAir() && state.getFluidState().isEmpty() && level.getBlockState(pos.above()).isAir()) {
				return pos;
			}
		}
		return null;
	}
}
