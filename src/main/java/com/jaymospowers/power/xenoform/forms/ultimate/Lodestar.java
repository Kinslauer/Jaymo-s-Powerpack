package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.forms.Polestar;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.37, Ultimates patch 2) Polestar's Ultimate, Lodestar: the lodestone alien grown to a magnetic pole, in heavier plates with
 * long curved prongs and four iron shards in orbit, on a health bar of its own on top of Polestar's. Its model is
 * {@code UltimateLodestar}. Everything it does is still iron: iron armour (and chainmail), iron blocks, iron golems and arrows' tips.
 * <ul>
 * <li>R, Great Pull: drags what wears iron within {@link #PULL_REACH} blocks (a wide cone) to you, hurting it when it arrives; loose
 * iron flies in.</li>
 * <li>G, Shock Repel: a wide burst that hurts and throws back everything iron, shoves the rest, and turns arrows back.</li>
 * <li>V, Triple Lift: tear up to {@link #MAX_ORBIT} iron blocks out of the world to orbit you; each V throws one.</li>
 * <li>B (hold), Bastion Field: arrows that come near fly back at their shooter, faster; iron-wearers that step inside are pushed out.</li>
 * <li>C, Lodestar: for {@link #POLE_TICKS} ticks you are a magnetic pole; every iron-wearer in range is dragged in and held against
 * you, loose iron flies in, then all of them are thrown out (at the end, or on C again).</li>
 * </ul>
 * Everything it starts is undone when it drops, breaks, reverts, dies, changes alien or dimension, or the server stops: held creatures
 * are let go (no hit), the iron blocks it holds come back into the world (as falling blocks overhead, or as items if there is no room).
 * It never hurts or drags a player that pvp or the team protects, and a thrown block never lands on anything but a clear block.
 */
public final class Lodestar extends XenoUltimate {
	// ---- tuning ----
	/** Great Pull: reach, the cone (dot of the look with the way to the target), the pull's base and per piece of iron, an iron golem's, a loose item's, the cooldown. */
	public static final double PULL_REACH = 24.0, PULL_CONE = 0.45, PULL_BASE = 0.6, PULL_PER_PIECE = 0.4, PULL_GOLEM = 1.6, PULL_ITEM = 0.8, PULL_MAX = 2.2;
	public static final int PULL_COOLDOWN = 30;
	/** What a pulled creature takes (per piece of iron) when it reaches you, how close counts as reaching you, and how long it is watched for (ticks). */
	public static final float ARRIVE_DAMAGE = 2.0F;
	public static final double ARRIVE_RANGE = 2.6;
	public static final int PULL_WATCH = 50;
	/** Shock Repel: radius, shove (base, per piece of iron, and the flat one for creatures with no iron), damage per piece, the cooldown. */
	public static final double REPEL_RADIUS = 12.0, REPEL_BASE = 0.8, REPEL_PER_PIECE = 0.4, REPEL_FLAT = 0.9;
	public static final float REPEL_PER_PIECE_DAMAGE = 2.5F;
	public static final int REPEL_COOLDOWN = 60;
	/** Triple Lift: how far a block can be torn out from, how many orbit you, the throw's speed, damage a block fallen (and at most), cooldowns, ticks a thrown block is watched. */
	public static final double LIFT_REACH = 8.0, THROW_SPEED = 1.8;
	public static final int MAX_ORBIT = 3, LIFT_COOLDOWN = 8, THROW_COOLDOWN = 20, THROW_WATCH = 200;
	public static final float THROW_DAMAGE = 3.0F, THROW_MAX = 30.0F;
	/** Bastion Field: radius, how much faster a returned arrow flies, the push on iron-wearers inside (every other tick). */
	public static final double FIELD_RADIUS = 9.0, FIELD_RETURN = 1.5, FIELD_PUSH = 0.25;
	/** Lodestar: how long the pole lasts (6 s), how far it reaches, the most it holds, how fast it drags (blocks a tick) and a loose item, the cooldown (30 s). */
	public static final int POLE_TICKS = 120, POLE_MAX = 24, POLE_COOLDOWN = 600;
	public static final double POLE_RADIUS = 16.0, POLE_SPEED = 1.0, POLE_ITEM = 0.7, POLE_ESCAPE = 8.0;
	/** The throw at the end: out and up, damage per piece of iron, how far a loose item is flung, the screen shake (degrees, how far it is felt). */
	public static final double FLING_OUT = 2.0, FLING_UP = 0.4, ITEM_FLING = 1.0, ITEM_FLING_RANGE = 4.0;
	public static final float FLING_DAMAGE = 2.0F, POLE_SHAKE = 1.4F, ENTER_SHAKE = 1.2F;
	public static final double POLE_SHAKE_RANGE = 24.0;

	/** A thrown block being watched: who it has hit already, and how long it has flown. */
	private static final class Thrown {
		final int id;
		final Set<Integer> hit = new HashSet<>();
		int age;

		Thrown(int id) {
			this.id = id;
		}
	}

	/** What each Lodestar has under way (server thread only). */
	private static final class Work {
		ServerLevel level;
		/** The iron blocks torn out of the world and orbiting. */
		final List<BlockState> orbit = new ArrayList<>();
		final List<Thrown> thrown = new ArrayList<>();
		/** Creatures Great Pull dragged (entity id to the game time it stops being watched). */
		final Map<Integer, Long> pulled = new LinkedHashMap<>();
		/** The pole: the game time it ends (0: not on) and the creatures held in it. */
		long poleUntil;
		final Set<Integer> caught = new LinkedHashSet<>();

		Work(ServerLevel level) {
			this.level = level;
		}

		boolean idle() {
			return orbit.isEmpty() && thrown.isEmpty() && pulled.isEmpty() && poleUntil == 0L;
		}
	}

	private static final Map<UUID, Work> WORK = new HashMap<>();

	public Lodestar(XenoForm base) {
		super(base, 1.5F, 24.0F, 9.0, -0.1, 0.8, 0.6);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.LODESTONE, Items.IRON_CHESTPLATE, Items.IRON_BLOCK, Items.ARROW, Items.COMPASS};
	}

	/** The change-in film zooms on these in turn (model part names): the body, the shoulder yoke, then both prongs. */
	@Override
	public String[] morphParts() {
		return new String[] {"body", "plates", "right_prong+left_prong"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Work old = WORK.remove(player.getUUID());
		if (old != null) {
			release(player, live, old); // a leftover from a change that never reached onExit
		}
		live.field = false;
		ring(level, MagicFx.CRACKLE, player.position(), 3.0, 40);
		ring(level, ParticleTypes.CRIT, player.position(), 2.0, 24);
		sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.6F, 0.5F);
		sound(player, SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
		Shakes.heavy(player, player.position(), POLE_SHAKE_RANGE, ENTER_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		Work work = WORK.remove(player.getUUID());
		if (work != null) {
			release(player, live, work);
		}
		live.field = false;
	}

	/** Lets go of everything: held creatures are left where they are (no hit), the orbiting blocks come back into the world. */
	private static void release(ServerPlayer player, XenoformPower.Live live, Work work) {
		work.caught.clear();
		work.pulled.clear();
		work.poleUntil = 0L;
		work.thrown.clear(); // a block in flight is an ordinary falling block: it lands where it lands
		for (BlockState state : work.orbit) {
			putBack(player, state);
		}
		work.orbit.clear();
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_4) {
			if (live.field != down) {
				live.field = down;
				if (down) {
					sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0F, 0.7F);
					cooldown(player, live, B, 1);
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					pull(player, work(player));
					cooldown(player, live, R, PULL_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					repel(player);
					cooldown(player, live, G, REPEL_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				Work work = work(player);
				BlockPos aim = aimBlock(player, LIFT_REACH);
				boolean ironAimed = aim != null && Polestar.liftable(player.level().getBlockState(aim));
				if (ironAimed && work.orbit.size() < MAX_ORBIT) {
					if (lift(player, work, aim)) {
						cooldown(player, live, V, LIFT_COOLDOWN);
					}
				} else if (!work.orbit.isEmpty()) {
					if (throwOne(player, work)) {
						cooldown(player, live, V, THROW_COOLDOWN);
					}
				} else {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.polestar.no_iron").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				}
			}
			default -> {
			}
		}
	}

	/** C: become the pole; C again while it holds throws everything out at once. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		Work work = work(player);
		if (work.poleUntil > 0L) {
			endPole(player, work, true);
			return;
		}
		if (!ready(player, live, C)) {
			return;
		}
		ServerLevel level = player.level();
		work.poleUntil = level.getGameTime() + POLE_TICKS;
		work.caught.clear();
		ring(level, MagicFx.CRACKLE, player.position(), POLE_RADIUS * 0.5, 40);
		ring(level, MagicFx.CRACKLE, player.position(), POLE_RADIUS, 64);
		sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.6F, 0.4F);
		sound(player, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
		Shakes.heavy(player, player.position(), POLE_SHAKE_RANGE, POLE_SHAKE);
		cooldown(player, live, C, POLE_COOLDOWN);
	}

	private static Work work(ServerPlayer player) {
		Work work = WORK.computeIfAbsent(player.getUUID(), id -> new Work(player.level()));
		work.level = player.level();
		return work;
	}

	// ---- helpers ----

	/** A player the attacker may not hurt (pvp off, or in creative) is never hit, dragged or pushed. */
	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || !other.isCreative() && player.canHarmPlayer(other);
	}

	/** How much iron {@code e} wears (an iron golem counts as four; one a player built is left alone). */
	private static int ironOf(LivingEntity e) {
		return e instanceof IronGolem golem && golem.isPlayerCreated() ? 0 : Polestar.iron(e);
	}

	/** A creature one of these moves may touch: not the player's own, no protected player, no passenger. */
	private static boolean target(ServerPlayer player, LivingEntity e) {
		return harmable(player, e) && !e.isPassenger();
	}

	// ---- R: Great Pull ----

	private static void pull(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		long now = level.getGameTime();
		int pulled = 0;
		for (LivingEntity e : around(player, PULL_REACH)) {
			int n = ironOf(e);
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			if (n == 0 || !target(player, e) || to.normalize().dot(look) < PULL_CONE || !player.hasLineOfSight(e)) {
				continue;
			}
			double strength = Math.min(PULL_MAX, e instanceof IronGolem ? PULL_GOLEM : PULL_BASE + PULL_PER_PIECE * n);
			Vec3 dragged = to.normalize().scale(-strength);
			e.setDeltaMovement(e.getDeltaMovement().add(dragged.x, 0.25, dragged.z));
			e.syncVelocity = true;
			work.pulled.put(e.getId(), now + PULL_WATCH);
			beam(level, eye.subtract(0.0, 0.3, 0.0), e.getBoundingBox().getCenter(), MagicFx.CRACKLE, 14);
			pulled++;
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(PULL_REACH), i -> Polestar.irony(i.getItem()))) {
			Vec3 to = player.position().add(0.0, 0.5, 0.0).subtract(item.position());
			item.setDeltaMovement(to.normalize().scale(PULL_ITEM).add(0.0, 0.2, 0.0));
			pulled++;
		}
		burst(level, MagicFx.CRACKLE, eye, 16, 0.4, 0.1);
		sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.2F, pulled > 0 ? 1.0F : 0.6F);
	}

	/** What Great Pull dragged is hit when it reaches you; whatever got away, died or unloaded is forgotten. */
	private static void arrivals(ServerPlayer player, Work work, long now) {
		ServerLevel level = player.level();
		for (Iterator<Map.Entry<Integer, Long>> it = work.pulled.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Long> entry = it.next();
			Entity found = level.getEntity(entry.getKey());
			if (!(found instanceof LivingEntity e) || !e.isAlive() || e.isRemoved() || now > entry.getValue()) {
				it.remove();
				continue;
			}
			if (e.distanceToSqr(player) <= ARRIVE_RANGE * ARRIVE_RANGE) {
				it.remove();
				int n = ironOf(e);
				if (n > 0 && target(player, e) && !spared(player, e)) {
					e.hurtServer(level, player.damageSources().playerAttack(player), ARRIVE_DAMAGE * n);
					burst(level, ParticleTypes.CRIT, e.getBoundingBox().getCenter(), 8, 0.4, 0.2);
					sound(player, SoundEvents.IRON_GOLEM_HURT, 0.8F, 1.2F);
				}
			}
		}
	}

	// ---- G: Shock Repel ----

	private static void repel(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity e : around(player, REPEL_RADIUS)) {
			if (!target(player, e)) {
				continue;
			}
			int n = ironOf(e);
			if (n == 0) {
				shove(e, player.position(), REPEL_FLAT, 0.3);
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), n * REPEL_PER_PIECE_DAMAGE);
			shove(e, player.position(), REPEL_BASE + REPEL_PER_PIECE * n, 0.4);
		}
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(REPEL_RADIUS),
			a -> a.getOwner() != player && a.getDeltaMovement().lengthSqr() > 0.04 && a.distanceToSqr(player) <= REPEL_RADIUS * REPEL_RADIUS)) {
			turn(arrow, player, 1.2);
		}
		ring(level, MagicFx.CRACKLE, player.position(), REPEL_RADIUS * 0.35, 24);
		ring(level, MagicFx.CRACKLE, player.position(), REPEL_RADIUS * 0.65, 40);
		ring(level, MagicFx.CRACKLE, player.position(), REPEL_RADIUS * 0.95, 56);
		sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.4F, 0.5F);
		Shakes.heavy(player, player.position(), POLE_SHAKE_RANGE, 0.8F);
	}

	/** An arrow turns round and flies back the way it came, {@code returnSpeed} times as fast, now the player's (not for a friend's, or a protected player's). */
	private static void turn(AbstractArrow arrow, ServerPlayer player, double returnSpeed) {
		Entity shooter = arrow.getOwner();
		if (shooter != null && (spared(player, shooter) || !harmable(player, shooter))) {
			return;
		}
		Vec3 v = arrow.getDeltaMovement();
		Vec3 back = shooter != null && shooter != player
			? shooter.getBoundingBox().getCenter().subtract(arrow.position()).normalize().scale(Math.max(0.8, v.length()) * returnSpeed)
			: v.scale(-returnSpeed);
		// (0.21.37) it turns without becoming the player's to pick up: the pickup rule stays as it was, and a trident keeps its thrower
		if (!(arrow instanceof ThrownTrident)) {
			AbstractArrow.Pickup pickup = arrow.pickup;
			arrow.setOwner(player);
			arrow.pickup = pickup;
		}
		arrow.setDeltaMovement(back);
		arrow.syncVelocity = true;
		player.level().sendParticles(MagicFx.CRACKLE, arrow.getX(), arrow.getY(), arrow.getZ(), 5, 0.1, 0.1, 0.1, 0.05);
	}

	// ---- V: Triple Lift ----

	/** Tears the iron block at {@code at} out of the world to orbit the player (never one it may not touch; the water stays if it was waterlogged). */
	private static boolean lift(ServerPlayer player, Work work, BlockPos at) {
		ServerLevel level = player.level();
		BlockState state = level.getBlockState(at);
		if (!Polestar.liftable(state) || state.hasBlockEntity() || !player.mayBuild() || !level.mayInteract(player, at)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.polestar.no_iron").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return false;
		}
		level.removeBlock(at, false);
		work.orbit.add(dry(state));
		beam(level, Vec3.atCenterOf(at), player.getEyePosition().add(0.0, 0.8, 0.0), MagicFx.CRACKLE, 10);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
		sound(player, SoundEvents.CHAIN_BREAK, 1.0F, 0.6F);
		return true;
	}

	/** The block as a falling block carries it: never waterlogged, so landing it can't pour water out of nowhere. */
	private static BlockState dry(BlockState state) {
		return state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
	}

	/** An air block in front of the eyes to spawn a falling block in, or null: never one the player (or anything) stands in, and never overhead (0.21.37). */
	private static BlockPos spawnSpot(ServerPlayer player, Vec3 look) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		for (double ahead : new double[] {1.2, 1.8, 2.4}) {
			BlockPos pos = BlockPos.containing(eye.add(look.scale(ahead)));
			if (level.getBlockState(pos).isAir() && level.getEntities(null, new AABB(pos)).isEmpty()) {
				return pos;
			}
		}
		return null;
	}

	/** V with blocks held: throws the last one where you look. Its damage is dealt by {@link #thrownTick}, only to what may be hurt. */
	private static boolean throwOne(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		BlockPos at = spawnSpot(player, look);
		if (at == null) {
			return false;
		}
		BlockState state = work.orbit.remove(work.orbit.size() - 1);
		FallingBlockEntity block = FallingBlockEntity.fall(level, at, state);
		block.setDeltaMovement(look.scale(THROW_SPEED).add(0.0, 0.1, 0.0));
		block.syncVelocity = true;
		block.fallDistance = 0.0;
		work.thrown.add(new Thrown(block.getId()));
		sound(player, SoundEvents.ANVIL_LAND, 0.6F, 1.6F);
		return true;
	}

	/** A held block goes back into the world: a falling block over the player's head, or the item if there is no room. */
	private static void putBack(ServerPlayer player, BlockState state) {
		ServerLevel level = player.level();
		// (0.21.37) over the head, never the eye block: that one falls straight onto the player's own feet
		BlockPos at = null;
		Vec3 eye = player.getEyePosition();
		for (int up = 1; up <= 2 && at == null; up++) {
			BlockPos pos = BlockPos.containing(eye.x, eye.y + up, eye.z);
			if (level.getBlockState(pos).isAir()) {
				at = pos;
			}
		}
		if (at != null && player.mayBuild() && level.mayInteract(player, at)) {
			FallingBlockEntity block = FallingBlockEntity.fall(level, at, state);
			block.setDeltaMovement(Vec3.ZERO);
			return;
		}
		Block.popResource(level, player.blockPosition(), new ItemStack(state.getBlock()));
	}

	/** The orbiting blocks circle the player as particles. */
	private static void orbitFx(ServerPlayer player, Work work) {
		int n = work.orbit.size();
		if (n == 0 || player.tickCount % 2 != 0) {
			return;
		}
		ServerLevel level = player.level();
		for (int i = 0; i < n; i++) {
			double a = player.tickCount * 0.15 + i * Math.PI * 2.0 / n;
			Vec3 at = player.position().add(Math.cos(a) * 1.7, player.getBbHeight() * 0.7 + Math.sin(player.tickCount * 0.1 + i) * 0.2, Math.sin(a) * 1.7);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, work.orbit.get(i)), at.x, at.y, at.z, 2, 0.12, 0.12, 0.12, 0.0);
			level.sendParticles(MagicFx.CRACKLE, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0.0);
		}
	}

	/** Whether a thrown block may land where it is heading (the spot it is in, the one under it, and the next one along). */
	private static boolean landingAllowed(ServerPlayer player, FallingBlockEntity block) {
		ServerLevel level = player.level();
		BlockPos here = block.blockPosition();
		BlockPos next = BlockPos.containing(block.position().add(block.getDeltaMovement()));
		return player.mayBuild() && level.mayInteract(player, here) && level.mayInteract(player, here.below()) && level.mayInteract(player, next);
	}

	/** Thrown blocks hit what they pass through, once each, for {@link #THROW_DAMAGE} a block fallen (at most {@link #THROW_MAX}); one that has landed is forgotten. */
	private static void thrownTick(ServerPlayer player, Work work) {
		ServerLevel level = player.level();
		for (Iterator<Thrown> it = work.thrown.iterator(); it.hasNext();) {
			Thrown t = it.next();
			Entity block = level.getEntity(t.id);
			if (block == null || block.isRemoved() || ++t.age > THROW_WATCH) {
				it.remove();
				continue;
			}
			// (0.21.37) it may not land where the thrower may not build: it turns back into the item instead
			if (block instanceof FallingBlockEntity falling && !landingAllowed(player, falling)) {
				Block.popResource(level, falling.blockPosition(), new ItemStack(falling.getBlockState().getBlock()));
				falling.discard();
				it.remove();
				continue;
			}
			float damage = (float)Math.min(THROW_MAX, THROW_DAMAGE * Math.max(1.0, Math.ceil(block.fallDistance)));
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, block.getBoundingBox().inflate(0.25),
				x -> x != player && x.isAlive() && !x.isSpectator() && !(x instanceof ArmorStand) && !spared(player, x) && harmable(player, x))) {
				if (t.hit.add(e.getId())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), damage);
					burst(level, ParticleTypes.CRIT, e.getBoundingBox().getCenter(), 8, 0.4, 0.2);
				}
			}
		}
	}

	// ---- B: Bastion Field ----

	private static void fieldTick(ServerPlayer player) {
		ServerLevel level = player.level();
		for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(FIELD_RADIUS),
			a -> a.getOwner() != player && a.getDeltaMovement().lengthSqr() > 0.04 && player.position().subtract(a.position()).dot(a.getDeltaMovement()) > 0.0)) {
			turn(arrow, player, FIELD_RETURN);
			sound(player, SoundEvents.CHAIN_BREAK, 0.6F, 1.6F);
		}
		if (player.tickCount % 2 == 0) {
			for (LivingEntity e : around(player, FIELD_RADIUS)) {
				if (ironOf(e) > 0 && target(player, e)) {
					shove(e, player.position(), FIELD_PUSH, 0.0);
				}
			}
		}
		if (player.tickCount % 6 == 0) {
			ring(level, MagicFx.CRACKLE, player.position().add(0.0, 0.8, 0.0), FIELD_RADIUS * 0.5, 14);
			ring(level, MagicFx.CRACKLE, player.position().add(0.0, 0.8, 0.0), FIELD_RADIUS, 24);
		}
	}

	// ---- C: Lodestar ----

	/** One tick of the pole: new iron-wearers are caught, everything caught is dragged in and held against the player, loose iron flies in. */
	private static void poleTick(ServerPlayer player, Work work, long now) {
		ServerLevel level = player.level();
		if (now >= work.poleUntil) {
			endPole(player, work, true);
			return;
		}
		for (LivingEntity e : around(player, POLE_RADIUS)) {
			if (work.caught.size() >= POLE_MAX) {
				break;
			}
			if (ironOf(e) > 0 && target(player, e)) {
				work.caught.add(e.getId());
			}
		}
		// the ones that are still held: where each sits against the player
		List<LivingEntity> held = new ArrayList<>();
		for (Iterator<Integer> it = work.caught.iterator(); it.hasNext();) {
			Entity found = level.getEntity(it.next());
			if (!(found instanceof LivingEntity e) || !e.isAlive() || e.isRemoved() || e.distanceToSqr(player) > (POLE_RADIUS + POLE_ESCAPE) * (POLE_RADIUS + POLE_ESCAPE)
				|| ironOf(e) == 0 || !target(player, e)) {
				it.remove();
				continue;
			}
			held.add(e);
		}
		for (int i = 0; i < held.size(); i++) {
			LivingEntity e = held.get(i);
			double a = i * Math.PI * 2.0 / held.size();
			double radius = 0.6 + (player.getBbWidth() + e.getBbWidth()) * 0.5;
			Vec3 slot = new Vec3(player.getX() + Math.cos(a) * radius, player.getY(), player.getZ() + Math.sin(a) * radius);
			Vec3 delta = slot.subtract(e.position());
			double len = delta.length();
			if (len < 0.2 && !(e instanceof ServerPlayer)) {
				e.setPos(slot.x, slot.y, slot.z);
				e.setDeltaMovement(Vec3.ZERO);
			} else {
				e.setDeltaMovement(delta.normalize().scale(Math.min(POLE_SPEED, len * 0.5)));
			}
			e.syncVelocity = true;
			e.resetFallDistance();
			if (e instanceof Mob mob) {
				mob.getNavigation().stop();
			}
			if ((now + i) % 5 == 0) {
				beam(level, e.getBoundingBox().getCenter(), player.getBoundingBox().getCenter(), MagicFx.CRACKLE, 6);
			}
		}
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(POLE_RADIUS), i -> Polestar.irony(i.getItem()))) {
			Vec3 to = player.position().add(0.0, 0.5, 0.0).subtract(item.position());
			item.setDeltaMovement(to.lengthSqr() < 1.0 ? Vec3.ZERO : to.normalize().scale(POLE_ITEM));
		}
		if (now % 4 == 0) {
			double shrink = 1.0 - (now % 20) / 20.0;
			ring(level, MagicFx.CRACKLE, player.position(), POLE_RADIUS * shrink, 48);
		}
		if (now % 20 == 0) {
			sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 0.7F, 0.5F);
		}
	}

	/** The pole ends: what it holds is thrown out and hurt by its iron (when {@code throwOut}), loose iron is flung, and it is over. */
	private static void endPole(ServerPlayer player, Work work, boolean throwOut) {
		ServerLevel level = player.level();
		if (throwOut) {
			for (int id : work.caught) {
				Entity found = level.getEntity(id);
				if (!(found instanceof LivingEntity e) || !e.isAlive() || e.isRemoved() || !target(player, e) || spared(player, e)) {
					continue;
				}
				int n = ironOf(e);
				if (n > 0) {
					e.hurtServer(level, player.damageSources().playerAttack(player), FLING_DAMAGE * n);
				}
				shove(e, player.position(), FLING_OUT, FLING_UP);
				burst(level, ParticleTypes.CRIT, e.getBoundingBox().getCenter(), 10, 0.4, 0.3);
			}
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(ITEM_FLING_RANGE), i -> Polestar.irony(i.getItem()))) {
				Vec3 out = item.position().subtract(player.position()).horizontal();
				out = out.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : out.normalize();
				item.setDeltaMovement(out.scale(ITEM_FLING).add(0.0, 0.3, 0.0));
			}
			ring(level, MagicFx.CRACKLE, player.position(), 3.0, 40);
			ring(level, ParticleTypes.POOF, player.position(), 6.0, 32);
			sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.4F, 0.4F);
			sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.2F);
			Shakes.heavy(player, player.position(), POLE_SHAKE_RANGE, POLE_SHAKE);
		}
		work.caught.clear();
		work.poleUntil = 0L;
	}

	// ---- the tick ----

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Work work = WORK.get(player.getUUID());
		if (work != null && work.level != level) {
			// changed dimension: ids belong to the old one, so what was dragged or held is let go (no hit); the held blocks come with the player
			work.level = level;
			work.caught.clear();
			work.pulled.clear();
			work.thrown.clear();
			work.poleUntil = 0L;
		}
		if (work != null) {
			if (work.poleUntil > 0L) {
				poleTick(player, work, now);
			}
			arrivals(player, work, now);
			thrownTick(player, work);
			orbitFx(player, work);
			if (work.idle()) {
				WORK.remove(player.getUUID());
			}
		}
		if (live.field) {
			fieldTick(player);
		}
		// it walks heavy, planted: a spark off the ground at each step
		if (player.tickCount % 4 == 0 && player.getDeltaMovement().horizontalDistanceSqr() > 0.01 && player.onGround()) {
			burst(level, MagicFx.CRACKLE, player.position(), 2, 0.5, 0.02);
		}
	}
}
