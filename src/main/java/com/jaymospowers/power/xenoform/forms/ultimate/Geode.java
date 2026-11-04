package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
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
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.21, Ultimates patch 1) Prism's Ultimate, Geode: the crystal golem grown to 1.5x your size in dark amethyst plate, with a
 * health bar of its own on top of Prism's. Its model is {@code UltimateGeode}.
 * <ul>
 * <li>R, Shard Storm: seven shards fan out wide, each cutting up to two creatures in its way.</li>
 * <li>G, Rampart: a curved amethyst wall wraps your front.</li>
 * <li>V (hold), Mirror Root: you root in crystal; every projectile that reaches you flies back at its shooter, and melee blows
 * return part of their damage to the striker.</li>
 * <li>B, Spike Forest: crystal bursts from the ground over a wide ring, and amethyst clusters stay up a few seconds, cutting
 * anything that steps in them.</li>
 * <li>C, Geode: everything within {@link #GEODE_RADIUS} is cased in crystal and held fast, and projectiles shatter.</li>
 * </ul>
 */
public final class Geode extends XenoUltimate {
	// ---- tuning ----
	/** Shard Storm: cooldown, shards, reach, the spread between shards (degrees), damage, creatures one shard cuts through. */
	public static final int SHARD_COOLDOWN = 40, SHARDS = 7, SHARD_PIERCE = 2;
	public static final double SHARD_RANGE = 18.0, SHARD_SPREAD_DEGREES = 11.0;
	public static final float SHARD_DAMAGE = 6.0F;
	/** Rampart: cooldown, ticks it stands (15 s), how far ahead its middle is, half its width, its height, how far its ends curve back toward you (divisor of the column's offset squared). */
	public static final int WALL_COOLDOWN = 240, WALL_TICKS = 300, WALL_AHEAD = 3, WALL_HALF_WIDTH = 3, WALL_HEIGHT = 4;
	public static final double WALL_CURVE = 4.0;
	/** Mirror Root: how fast a returned projectile flies, how far out a projectile is caught (blocks), the share of a melee blow returned. */
	public static final float REFLECT_SPEED = 2.6F, MELEE_RETURN = 0.3F;
	public static final double REFLECT_CATCH = 3.0;
	/** Spike Forest: cooldown, slow (ticks), reach, damage; the clusters it leaves, how long they stand, what stepping in them costs and how often. */
	public static final int SPIKE_COOLDOWN = 220, SPIKE_SLOW_TICKS = 80, CLUSTERS = 22, CLUSTER_TICKS = 60, CLUSTER_EVERY = 10;
	public static final double SPIKE_RADIUS = 7.0;
	public static final float SPIKE_DAMAGE = 7.0F, CLUSTER_DAMAGE = 2.0F;
	/** Geode: its reach, how long it holds (5 s), C's cooldown (20 s), and the resistance you get while it holds (amplifier). */
	public static final double GEODE_RADIUS = 8.0;
	public static final int GEODE_TICKS = 100, GEODE_COOLDOWN = 400, GEODE_RESISTANCE = 2;
	/** The spires chime at each heavy step: ticks between chimes while walking. */
	public static final int CHIME_EVERY = 9;
	/** Screen shake (degrees, how far it is felt): the change-in, Geode, Spike Forest. */
	public static final float ENTER_SHAKE = 1.4F, GEODE_SHAKE = 1.3F, SPIKE_SHAKE = 1.0F;
	public static final double ENTER_SHAKE_RANGE = 22.0;

	private static final BlockState CRYSTAL = Blocks.AMETHYST_BLOCK.defaultBlockState();
	private static final BlockState CLUSTER = Blocks.AMETHYST_CLUSTER.defaultBlockState();
	private static final BlockParticleOption CRYSTAL_DUST = new BlockParticleOption(ParticleTypes.BLOCK, CRYSTAL);

	/** The clusters one Geode has up (server thread only): where, and when they come down. */
	private static final class Forest {
		final List<BlockPos> clusters = new ArrayList<>();
		long until;
		/** (0.20.21) Where it was cast: the clusters cut around here, not around the Geode. */
		Vec3 centre = Vec3.ZERO;
	}

	private static final Map<UUID, Forest> FORESTS = new HashMap<>();
	/** (0.20.21) The level each Geode last built in, so a dimension change takes its blocks down where they stand. */
	private static final Map<UUID, ServerLevel> BUILT_IN = new HashMap<>();
	/** (0.20.21) The wall's and crystals' blocks while they stand: mining one breaks it without a drop. */
	private static final Set<GlobalPos> TEMP_BLOCKS = new HashSet<>();
	/** (0.20.21) Where each Mirror Root began. */
	private static final Map<UUID, Vec3> ROOT_ANCHOR = new HashMap<>();

	static {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!TEMP_BLOCKS.isEmpty() && TEMP_BLOCKS.remove(GlobalPos.of(level.dimension(), pos))) {
				level.removeBlock(pos, false);
				return false;
			}
			return true;
		});
	}

	/** (0.20.21) Called from XenoformPower.init so the stop hook is in place before this class is ever used. */
	public static void init() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (GlobalPos gp : TEMP_BLOCKS) {
				ServerLevel level = server.getLevel(gp.dimension());
				if (level != null) {
					BlockState state = level.getBlockState(gp.pos());
					if (state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.AMETHYST_CLUSTER)) {
						level.setBlock(gp.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
			TEMP_BLOCKS.clear();
			FORESTS.clear();
			BUILT_IN.clear();
		});
	}

	public Geode(XenoForm base) {
		super(base, 1.5F, 24.0F, 8.0, -0.1, 0.7, 0.5);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.AMETHYST_SHARD, Items.AMETHYST_BLOCK, Items.TINTED_GLASS, Items.AMETHYST_CLUSTER, Items.BUDDING_AMETHYST};
	}

	/** The change-in film zooms on these in turn (model part names): the legs, the core, the spires. */
	@Override
	public String[] morphParts() {
		return new String[] {"leg0+leg1+leg2+leg3+leg4+leg5", "core",
			"crystal0+crystal1+crystal2+crystal3+crystal4+crystal5+crystal6+crystal7+crystal8+crystal9"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.reflecting = false;
		ServerLevel level = player.level();
		Vec3 at = player.position();
		ring(level, CRYSTAL_DUST, at, 2.0, 16);
		ring(level, CRYSTAL_DUST, at, 4.0, 28);
		sound(player, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.5F, 0.5F);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.5F);
		Shakes.heavy(player, at, ENTER_SHAKE_RANGE, ENTER_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.reflecting = false;
		ROOT_ANCHOR.remove(player.getUUID());
		ServerLevel level = BUILT_IN.getOrDefault(player.getUUID(), player.level());
		BUILT_IN.remove(player.getUUID());
		clearWall(level, live);
		shatter(level, live);
		clearForest(level, player.getUUID());
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_3) {
			if (down != live.reflecting) {
				live.reflecting = down;
				if (down) {
					sound(player, SoundEvents.AMETHYST_BLOCK_PLACE, 1.2F, 0.6F);
					cooldown(player, live, V, 1);
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
					shards(player);
					cooldown(player, live, R, SHARD_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && rampart(player, live)) {
					cooldown(player, live, G, WALL_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					forest(player, live);
					cooldown(player, live, B, SPIKE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: Geode. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (ready(player, live, C)) {
			geode(player, live);
			cooldown(player, live, C, GEODE_COOLDOWN);
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		ServerLevel was = BUILT_IN.put(player.getUUID(), level);
		if (was != null && was != level) {
			// (0.20.21) changed dimension: the wall, crystals and clusters come down in the one they were built in
			clearWall(was, live);
			shatter(was, live);
			clearForest(was, player.getUUID());
		}
		if (live.reflecting) {
			// rooted: no walking while the stance holds (0.20.21: pulled back to where the stance began, since the server never sees a walking player's motion; Slowness would zoom the screen)
			Vec3 anchor = ROOT_ANCHOR.computeIfAbsent(player.getUUID(), id -> player.position());
			if (Math.abs(player.getX() - anchor.x) > 0.05 || Math.abs(player.getZ() - anchor.z) > 0.05) {
				player.teleportTo(level, anchor.x, player.getY(), anchor.z, Set.of(), player.getYRot(), player.getXRot(), false);
			}
			if (player.tickCount % 4 == 0) {
				level.sendParticles(CRYSTAL_DUST, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.6, 0.8, 0.6, 0.0);
			}
			catchProjectiles(player);
		} else {
			ROOT_ANCHOR.remove(player.getUUID());
		}
		if (!live.wall.isEmpty() && now >= live.wallUntil) {
			clearWall(level, live);
		}
		if (live.geodeUntil > 0L) {
			if (now >= live.geodeUntil) {
				shatter(level, live);
			} else {
				hold(player, live);
			}
		}
		Forest forest = FORESTS.get(player.getUUID());
		if (forest != null) {
			if (now >= forest.until) {
				clearForest(level, player.getUUID());
			} else if (now % CLUSTER_EVERY == 0) {
				cut(player, forest);
			}
		}
		// it walks heavy: the spires chime at each step
		if (!live.reflecting && player.onGround() && player.tickCount % CHIME_EVERY == 0 && player.getKnownMovement().horizontalDistanceSqr() > 0.004) {
			sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.5F + player.getRandom().nextFloat() * 0.3F);
			level.sendParticles(CRYSTAL_DUST, player.getX(), player.getY() + player.getBbHeight() * 0.9, player.getZ(), 2, 0.4, 0.2, 0.4, 0.0);
		}
	}

	/** In Mirror Root, a projectile that reaches you flies back at its shooter and a melee blow is partly returned. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (!live.reflecting) {
			return true;
		}
		ServerLevel level = player.level();
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			Entity incoming = source.getDirectEntity();
			if (incoming instanceof Projectile projectile && !projectile.isRemoved()) {
				// (0.20.21) it is mid-hit: a fresh one goes back instead of turning this one round
				Entity shooter = projectile.getOwner();
				Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.6, 0.0);
				if (!(projectile instanceof ThrownTrident)) { // (0.20.21) a thrown trident is turned aside by the refused hit, never deleted
					projectile.discard();
				}
				if (projectile instanceof AbstractArrow && !(projectile instanceof ThrownTrident)) {
					Vec3 back = shooter != null && shooter.isAlive() && shooter != player ? shooter.getEyePosition().subtract(at) : projectile.getDeltaMovement().scale(-1.0);
					if (back.lengthSqr() < 1.0E-4) {
						back = player.getLookAngle();
					}
					Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
					arrow.setPos(at.x, at.y, at.z);
					arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
					arrow.shoot(back.x, back.y, back.z, REFLECT_SPEED, 0.5F);
					level.addFreshEntity(arrow);
				}
				level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.0);
				sound(player, SoundEvents.AMETHYST_CLUSTER_HIT, 1.0F, 1.6F);
			}
			return false;
		}
		if (source.getEntity() instanceof LivingEntity striker && striker != player && striker.isAlive() && source.getDirectEntity() == striker
			&& (source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.PLAYER_ATTACK))) { // (0.20.21) melee blows only
			striker.hurtServer(level, player.damageSources().thorns(player), amount * MELEE_RETURN);
			Vec3 at = striker.getBoundingBox().getCenter();
			level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 8, 0.3, 0.3, 0.3, 0.0);
			sound(player, SoundEvents.AMETHYST_CLUSTER_HIT, 1.0F, 1.2F);
		}
		return true;
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return false;
	}

	// ---- V: Mirror Root ----

	/** Every projectile (not yours, not a bobber, not a teammate's) closing on you within {@link #REFLECT_CATCH} blocks goes back. */
	private static void catchProjectiles(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 heart = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(REFLECT_CATCH),
			p -> !p.isRemoved() && p.getOwner() != player && !(p instanceof FishingHook) && !(p instanceof ThrownEnderpearl) && !(p instanceof ThrownTrident) && (p.getOwner() == null || !spared(player, p.getOwner())))) {
			if (projectile.getDeltaMovement().dot(heart.subtract(projectile.position())) > 0.0) {
				sendBack(player, projectile);
			}
		}
	}

	/** Turns {@code projectile} round at {@link #REFLECT_SPEED}, towards whoever shot it (back along its path when it has no shooter), and makes it yours. */
	private static void sendBack(ServerPlayer player, Projectile projectile) {
		ServerLevel level = player.level();
		if (projectile instanceof net.minecraft.world.entity.projectile.ShulkerBullet) {
			// (0.20.21) a homing bullet keeps steering at its target, so it is broken here instead of coming back
			level.sendParticles(CRYSTAL_DUST, projectile.getX(), projectile.getY(), projectile.getZ(), 10, 0.3, 0.3, 0.3, 0.0);
			projectile.discard();
			sound(player, SoundEvents.AMETHYST_CLUSTER_HIT, 1.0F, 1.6F);
			return;
		}
		Entity shooter = projectile.getOwner();
		Vec3 aim = shooter != null && shooter.isAlive() && shooter != player
			? shooter.getBoundingBox().getCenter().subtract(projectile.position()) : projectile.getDeltaMovement().scale(-1.0);
		if (aim.lengthSqr() < 1.0E-4) {
			aim = player.getLookAngle();
		}
		Vec3 dir = aim.normalize();
		AbstractArrow.Pickup pickup = projectile instanceof AbstractArrow arrow ? arrow.pickup : null;
		projectile.setOwner(player);
		if (pickup != null) {
			((AbstractArrow)projectile).pickup = pickup; // (0.20.21) setOwner would make a mob's arrow pick-up-able
		}
		projectile.shoot(dir.x, dir.y, dir.z, REFLECT_SPEED, 0.0F);
		projectile.syncVelocity = true;
		Vec3 at = projectile.position();
		level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.0);
		sound(player, SoundEvents.AMETHYST_CLUSTER_HIT, 1.0F, 1.6F);
	}

	// ---- R: Shard Storm ----

	private static void shards(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		for (int i = 0; i < SHARDS; i++) {
			double spread = Math.toRadians((i - (SHARDS - 1) / 2.0) * SHARD_SPREAD_DEGREES);
			Vec3 dir = look.yRot((float)spread).normalize();
			Vec3 end = eye.add(dir.scale(SHARD_RANGE));
			// a shard stops at a wall
			BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			if (wall.getType() == HitResult.Type.BLOCK) {
				end = wall.getLocation();
			}
			AABB box = player.getBoundingBox().expandTowards(dir.scale(SHARD_RANGE)).inflate(1.0);
			Set<Integer> struck = new HashSet<>();
			Vec3 stop = end;
			for (int cut = 0; cut < SHARD_PIERCE; cut++) {
				EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, end, box,
					e -> e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand)
						&& !e.isPassengerOfSameVehicle(player) && !spared(player, e) && !struck.contains(e.getId()), 0.4F);
				if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
					break;
				}
				struck.add(target.getId());
				target.hurtServer(level, player.damageSources().playerAttack(player), SHARD_DAMAGE);
				target.hurtTime = 0;
				Vec3 at = hit.getLocation();
				level.sendParticles(CRYSTAL_DUST, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.0);
				if (cut == SHARD_PIERCE - 1) {
					stop = at;
				}
			}
			double length = stop.distanceTo(eye);
			for (double d = 1.0; d < length; d += 1.0) {
				Vec3 p = eye.add(dir.scale(d));
				level.sendParticles(CRYSTAL_DUST, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
		}
		sound(player, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 1.3F);
		sound(player, SoundEvents.SHULKER_SHOOT, 0.8F, 1.2F);
	}

	// ---- G: Rampart ----

	/** A curved wall of amethyst across your front: its middle {@link #WALL_AHEAD} blocks ahead, its ends curving back toward you; only air and grass make way for it. */
	private static boolean rampart(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		clearWall(level, live);
		Direction facing = player.getDirection();
		Direction across = facing.getClockWise();
		BlockPos feet = player.blockPosition();
		for (int w = -WALL_HALF_WIDTH; w <= WALL_HALF_WIDTH; w++) {
			int ahead = Math.max(1, WALL_AHEAD - (int)Math.round(w * w / WALL_CURVE));
			for (int h = 0; h < WALL_HEIGHT; h++) {
				BlockPos pos = feet.relative(facing, ahead).relative(across, w).above(h);
				BlockState state = level.getBlockState(pos);
				if ((state.isAir() || state.is(Blocks.SHORT_GRASS)) && level.getEntities(player, new AABB(pos)).isEmpty() && canPlace(player, level, pos)) {
					level.setBlock(pos, CRYSTAL, Block.UPDATE_ALL);
					live.wall.add(pos);
					TEMP_BLOCKS.add(GlobalPos.of(level.dimension(), pos));
				}
			}
		}
		if (live.wall.isEmpty()) {
			return false;
		}
		live.wallUntil = level.getGameTime() + WALL_TICKS;
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		BlockPos mid = feet.relative(facing, WALL_AHEAD);
		level.playSound(null, mid, SoundEvents.AMETHYST_CLUSTER_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, mid, SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
		return true;
	}

	/** (0.20.21) Spawn protection, the border and adventure mode all stop the crystal. */
	private static boolean canPlace(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return player.mayBuild() && level.mayInteract(player, pos);
	}

	private static void clearWall(ServerLevel level, XenoformPower.Live live) {
		for (BlockPos pos : live.wall) {
			if (TEMP_BLOCKS.remove(GlobalPos.of(level.dimension(), pos)) && level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)) { // (0.20.21) only blocks still ours
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(CRYSTAL_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!live.wall.isEmpty()) {
			level.playSound(null, live.wall.get(0), SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.9F);
		}
		live.wall.clear();
	}

	// ---- B: Spike Forest ----

	private static void forest(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, SPIKE_RADIUS)) {
			if (!living.hurtServer(level, player.damageSources().playerAttack(player), SPIKE_DAMAGE) && living instanceof Player) {
				continue; // (0.20.21) a player it could not hurt (pvp off) is not slowed or shoved
			}
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPIKE_SLOW_TICKS, 1));
			shove(living, player.position(), 0.25, 0.4);
		}
		Vec3 feet = player.position();
		ring(level, CRYSTAL_DUST, feet, SPIKE_RADIUS * 0.4, 12);
		ring(level, CRYSTAL_DUST, feet, SPIKE_RADIUS * 0.7, 20);
		ring(level, CRYSTAL_DUST, feet, SPIKE_RADIUS, 32);
		// the clusters that stay up
		Forest forest = FORESTS.computeIfAbsent(player.getUUID(), id -> new Forest());
		for (int i = 0; i < CLUSTERS; i++) {
			double a = player.getRandom().nextDouble() * Math.PI * 2.0;
			double r = 1.5 + player.getRandom().nextDouble() * (SPIKE_RADIUS - 1.5);
			BlockPos spot = standAt(level, BlockPos.containing(feet.x + Math.cos(a) * r, feet.y, feet.z + Math.sin(a) * r));
			if (spot != null && canPlace(player, level, spot) && !live.wall.contains(spot.below()) && !live.crystals.contains(spot.below())) { // (0.20.21) never on the Geode's own temporary blocks
				level.setBlock(spot, CLUSTER, Block.UPDATE_ALL);
				forest.clusters.add(spot);
				TEMP_BLOCKS.add(GlobalPos.of(level.dimension(), spot));
				level.sendParticles(CRYSTAL_DUST, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 4, 0.2, 0.3, 0.2, 0.0);
			}
		}
		forest.until = level.getGameTime() + CLUSTER_TICKS;
		forest.centre = feet;
		sound(player, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.4F, 0.5F);
		sound(player, SoundEvents.GLASS_BREAK, 0.9F, 0.6F);
		Shakes.heavy(player, feet, SPIKE_RADIUS * 2.5, SPIKE_SHAKE);
	}

	/** The air block near {@code column} with solid ground under it, or null. */
	private static BlockPos standAt(ServerLevel level, BlockPos column) {
		for (int dy = 2; dy >= -3; dy--) {
			BlockPos pos = column.above(dy);
			if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) {
				return pos;
			}
		}
		return null;
	}

	/** Anything standing in a cluster takes {@link #CLUSTER_DAMAGE}. */
	private static void cut(ServerPlayer player, Forest forest) {
		ServerLevel level = player.level();
		double reach = SPIKE_RADIUS + 2.0;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(forest.centre, forest.centre).inflate(reach),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player) && !spared(player, e)
				&& e.position().distanceToSqr(forest.centre) <= reach * reach)) {
			AABB body = living.getBoundingBox();
			for (BlockPos pos : forest.clusters) {
				if (body.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 0.8, pos.getZ() + 1.0)) {
					living.hurtServer(level, player.damageSources().playerAttack(player), CLUSTER_DAMAGE);
					level.sendParticles(CRYSTAL_DUST, living.getX(), living.getY() + 0.3, living.getZ(), 3, 0.2, 0.1, 0.2, 0.0);
					break;
				}
			}
		}
	}

	/** Takes the clusters down the way Geode's crystals come down. */
	private static void clearForest(ServerLevel level, UUID owner) {
		Forest forest = FORESTS.remove(owner);
		if (forest == null) {
			return;
		}
		for (BlockPos pos : forest.clusters) {
			if (TEMP_BLOCKS.remove(GlobalPos.of(level.dimension(), pos)) && level.getBlockState(pos).is(Blocks.AMETHYST_CLUSTER)) { // (0.20.21) only blocks still ours
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(CRYSTAL_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!forest.clusters.isEmpty()) {
			level.playSound(null, forest.clusters.get(0), SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 0.9F, 0.8F);
		}
	}

	// ---- C: Geode ----

	/** Crystal grows over everything near: the creatures are held and cased, and projectiles shatter. */
	private static void geode(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		shatter(level, live);
		for (LivingEntity living : around(player, GEODE_RADIUS)) {
			if (living instanceof ServerPlayer other && !player.canHarmPlayer(other)) {
				continue; // (0.20.21) no cage for a player pvp protects
			}
			live.frozen.add(living.getId());
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GEODE_TICKS, 9, false, false));
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, GEODE_TICKS, 4, false, false));
			living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, GEODE_TICKS, 4, false, false));
			// amethyst round its feet and head
			BlockPos feet = living.blockPosition();
			int height = Math.max(1, (int)Math.ceil(living.getBbHeight()));
			for (int h = 0; h < height; h++) {
				for (Direction side : Direction.Plane.HORIZONTAL) {
					BlockPos pos = feet.above(h).relative(side);
					if (level.getBlockState(pos).isAir() && level.getEntities(null, new AABB(pos)).isEmpty() && canPlace(player, level, pos)) {
						level.setBlock(pos, CRYSTAL, Block.UPDATE_ALL);
						live.crystals.add(pos);
						TEMP_BLOCKS.add(GlobalPos.of(level.dimension(), pos));
					}
				}
			}
		}
		for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(GEODE_RADIUS),
			p -> !(p instanceof ThrownTrident) && !(p instanceof FishingHook) && !(p instanceof ThrownEnderpearl) && !(p instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity)
				&& p.getOwner() != player && (p.getOwner() == null || !spared(player, p.getOwner())))) { // (0.20.21)
			level.sendParticles(CRYSTAL_DUST, projectile.getX(), projectile.getY(), projectile.getZ(), 6, 0.1, 0.1, 0.1, 0.0);
			projectile.discard();
		}
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, GEODE_TICKS, GEODE_RESISTANCE, false, false));
		live.geodeUntil = level.getGameTime() + GEODE_TICKS;
		Vec3 at = player.position();
		ring(level, CRYSTAL_DUST, at, GEODE_RADIUS * 0.4, 16);
		ring(level, CRYSTAL_DUST, at, GEODE_RADIUS * 0.7, 28);
		ring(level, CRYSTAL_DUST, at, GEODE_RADIUS, 40);
		sound(player, SoundEvents.AMETHYST_CLUSTER_PLACE, 1.6F, 0.5F);
		sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.5F);
		Shakes.heavy(player, at, GEODE_RADIUS * 2.5, GEODE_SHAKE);
	}

	/** Everything caught is held fast and loses its target, until Geode ends. */
	private static void hold(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		for (int id : live.frozen) {
			if (level.getEntity(id) instanceof LivingEntity living && living.isAlive()) {
				living.setDeltaMovement(0.0, Math.min(0.0, living.getDeltaMovement().y), 0.0);
				living.syncVelocity = true;
				if (living instanceof Mob mob) {
					mob.getNavigation().stop();
					mob.setTarget(null);
				}
				if (player.tickCount % 5 == 0) {
					level.sendParticles(CRYSTAL_DUST, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
				}
			}
		}
	}

	private static void shatter(ServerLevel level, XenoformPower.Live live) {
		for (BlockPos pos : live.crystals) {
			if (TEMP_BLOCKS.remove(GlobalPos.of(level.dimension(), pos)) && level.getBlockState(pos).is(Blocks.AMETHYST_BLOCK)) { // (0.20.21) only blocks still ours
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(CRYSTAL_DUST, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
			}
		}
		if (!live.crystals.isEmpty()) {
			level.playSound(null, live.crystals.get(0), SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		live.crystals.clear();
		live.frozen.clear();
		live.geodeUntil = 0L;
	}
}
