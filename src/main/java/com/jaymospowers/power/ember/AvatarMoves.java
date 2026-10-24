package com.jaymospowers.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.LimbUse;
import com.jaymospowers.power.smoke.SmokeField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.151) The Flame Avatar's three moves ({@link FlameAvatar}), Andy's design:
 * <ul>
 * <li><b>R, Fire Fist:</b> a giant fireball thrown from the fist; it bursts in a large blast ({@link #FIST_RADIUS}) that
 * burns and throws back everything in it and sets fire to what can burn round it. No block is broken.</li>
 * <li><b>G, Flaming Pillar:</b> tap: a pillar of flame bursts up round the avatar, throwing everyone in it up and away
 * and setting fire to what can burn; the damage it deals comes back as flame ({@link #PILLAR_REGEN}). Hold (past
 * {@link #HOLD_TICKS}): the spot under the cursor is marked (only the avatar sees it), and letting go raises the same
 * pillar there.</li>
 * <li><b>B, Ignition Switch:</b> tap: the hands light everything burnable in front ({@link #IGNITE_REACH} blocks):
 * campfires, candles, unlit torches and lanterns (other mods'), flammable blocks, creatures. Costs flame. Hold: what's
 * burning near ({@link #DETONATE_RANGE}; burning creatures and fires) is marked, and letting go sets each off in a small
 * blast (a flame each).</li>
 * </ul>
 * Each has its cooldown, sent to the avatar for the HUD ({@link #COOLDOWNS}). Fireballs aren't entities: the server moves and draws them, so nothing is saved.
 */
public final class AvatarMoves {
	// ---- tuning ----
	/** A key held this long (ticks) is a hold, not a tap. */
	public static final int HOLD_TICKS = 8;
	/** Fire Fist: speed (blocks a tick), life (ticks), the blast's radius, its damage at the middle (half at the edge), the burning (s), the push, fires started, the cooldown. */
	static final double FIST_SPEED = 1.4, FIST_RADIUS = 4.5, FIST_PUSH = 1.0;
	static final int FIST_LIFE = 50, FIST_BURN = 6, FIST_FIRES = 20;
	static final float FIST_DAMAGE = 10.0F;
	public static final int FIST_COOLDOWN = 80;
	/** Flaming Pillar: radius (round you, at the cursor), height, damage, burning (s), the throw (up, out), flame back a point of damage, reach of the hold, fires, cooldown. */
	static final double PILLAR_SELF_RADIUS = 3.5, PILLAR_RADIUS = 2.5, PILLAR_HEIGHT = 6.0, PILLAR_UP = 1.1, PILLAR_OUT = 0.8, PILLAR_RANGE = 32.0;
	static final float PILLAR_DAMAGE = 6.0F, PILLAR_REGEN = 0.5F;
	static final int PILLAR_BURN = 5, PILLAR_FIRES = 10;
	public static final int PILLAR_COOLDOWN = 100;
	/** Ignition Switch tap: reach (blocks), how wide (the cosine of the half-angle), flame cost, fires at most, burning (s), cooldown. */
	static final double IGNITE_REACH = 8.0, IGNITE_COS = 0.6;
	static final float IGNITE_COST = 3.0F;
	static final int IGNITE_FIRES = 12, IGNITE_BURN = 4;
	public static final int IGNITE_COOLDOWN = 20;
	/** Ignition Switch hold: range (blocks), blasts at most, each one's radius and damage, flame a blast, cooldown. */
	static final double DETONATE_RANGE = 16.0, BLAST_RADIUS = 2.0;
	static final int DETONATE_MAX = 16;
	static final float BLAST_DAMAGE = 4.0F, BLAST_COST = 1.0F;
	public static final int DETONATE_COOLDOWN = 120;
	/** How often (ticks) a held G or B marks its targets for the avatar. */
	static final int MARK_EVERY = 4;

	/** Index into each player's cooldowns: R, G, B. */
	public static final int FIST = 0, PILLAR = 1, SWITCH = 2;

	/** {@code PowerKey} ordinals of R, G and B. */
	public static final int KEY_R = 0, KEY_G = 1, KEY_B = 3;

	/**
	 * Each avatar's cooldowns (when each move is ready, game time), synced to the avatar for the HUD:
	 * {ready R, ready G, ready B, length R, length G, length B}.
	 */
	public static final AttachmentType<long[]> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("avatar_cooldowns"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list())
			.map(list -> list.stream().mapToLong(Long::longValue).toArray(), array -> Arrays.stream(array).boxed().toList()),
			AttachmentSyncPredicate.targetOnly()));

	private static final class Ball {
		final ServerLevel level;
		final ServerPlayer owner;
		Vec3 pos;
		final Vec3 velocity;
		int age;

		Ball(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 velocity) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	/** One avatar's held keys: when G and B went down (game time), or -1. */
	private static final class Held {
		long g = -1, b = -1;
	}

	private static final List<Ball> BALLS = new ArrayList<>();
	private static final Map<UUID, Held> HELD = new HashMap<>();

	private AvatarMoves() {
	}

	static void init() {
	}

	static void clear() {
		BALLS.clear();
		HELD.clear();
	}

	static String path(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	static void key(ServerPlayer player, int key, boolean down) {
		Held held = HELD.computeIfAbsent(player.getUUID(), k -> new Held());
		long now = player.level().getGameTime();
		if (key == KEY_R) {
			if (down) {
				fist(player);
			}
		} else if (key == KEY_G) {
			if (down) {
				held.g = now;
			} else if (held.g >= 0) {
				boolean hold = now - held.g >= HOLD_TICKS;
				held.g = -1;
				if (FlameAvatar.is(player)) {
					pillar(player, hold);
				}
			}
		} else if (key == KEY_B) {
			if (down) {
				held.b = now;
			} else if (held.b >= 0) {
				boolean hold = now - held.b >= HOLD_TICKS;
				held.b = -1;
				if (FlameAvatar.is(player)) {
					if (hold) {
						detonate(player);
					} else {
						ignite(player);
					}
				}
			}
		}
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Ball> it = BALLS.iterator(); it.hasNext();) {
			if (step(it.next())) {
				it.remove();
			}
		}
		for (Iterator<Map.Entry<UUID, Held>> it = HELD.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Held> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			Held held = entry.getValue();
			long now = player.level().getGameTime();
			if (now % MARK_EVERY != 0) {
				continue;
			}
			if (held.g >= 0 && now - held.g >= HOLD_TICKS && ready(player, PILLAR)) {
				Vec3 spot = cursor(player);
				if (spot != null) {
					markRing(player, spot, PILLAR_RADIUS);
				}
			}
			if (held.b >= 0 && now - held.b >= HOLD_TICKS && ready(player, SWITCH)) {
				for (Vec3 at : burning(player)) {
					player.level().sendParticles(player, ParticleTypes.FLAME, true, false, at.x, at.y, at.z, 3, 0.15, 0.25, 0.15, 0.02);
				}
			}
		}
	}

	// ---- cooldowns -----------------------------------------------------------------------------------------------------

	private static boolean ready(ServerPlayer player, int move) {
		long[] cd = player.getAttached(COOLDOWNS);
		return player.getAbilities().instabuild || cd == null || player.level().getGameTime() >= cd[move];
	}

	private static void cool(ServerPlayer player, int move, int ticks) {
		long[] cd = player.getAttached(COOLDOWNS);
		long[] next = cd == null ? new long[6] : cd.clone();
		next[move] = player.level().getGameTime() + ticks;
		next[move + 3] = ticks;
		player.setAttached(COOLDOWNS, next);
	}

	/** The spot under the cursor out to {@link #PILLAR_RANGE}: the block looked at, or the ground below the end of the look. */
	private static @Nullable Vec3 cursor(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(PILLAR_RANGE));
		HitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		Vec3 at = hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
		HitResult down = level.clip(new ClipContext(at.add(0.0, 0.5, 0.0), at.add(0.0, -PILLAR_HEIGHT, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		return down.getType() == HitResult.Type.MISS ? null : down.getLocation();
	}

	private static void markRing(ServerPlayer player, Vec3 at, double radius) {
		for (int i = 0; i < 16; i++) {
			double a = i * Math.PI * 2.0 / 16.0;
			player.level().sendParticles(player, ParticleTypes.FLAME, true, false, at.x + Math.cos(a) * radius, at.y + 0.1, at.z + Math.sin(a) * radius,
				1, 0.0, 0.02, 0.0, 0.0);
		}
		player.level().sendParticles(player, ParticleTypes.LAVA, true, false, at.x, at.y + 0.1, at.z, 1, 0.1, 0.0, 0.1, 0.0);
	}

	// ---- R: Fire Fist --------------------------------------------------------------------------------------------------

	private static void fist(ServerPlayer player) {
		if (!ready(player, FIST) || !LimbUse.usable(player, LimbUse.MAIN_ARM)) {
			return;
		}
		ServerLevel level = player.level();
		cool(player, FIST, FIST_COOLDOWN);
		Vec3 from = EmberKindle.palm(player);
		Vec3 look = LimbUse.sway(player, LimbUse.MAIN_ARM, player.getViewVector(1.0F));
		BALLS.add(new Ball(level, player, from, look.scale(FIST_SPEED)));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4F, 0.5F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 20, 0.2, 0.2, 0.2, 0.08);
	}

	/** One tick of a fist's flight; true once it's gone. */
	private static boolean step(Ball ball) {
		ServerLevel level = ball.level;
		ball.age++;
		Vec3 from = ball.pos;
		Vec3 to = from.add(ball.velocity);
		if (!level.isLoaded(BlockPos.containing(to)) || ball.owner.level() != level || ball.owner.isRemoved()) {
			return true;
		}
		if (level.getFluidState(BlockPos.containing(to)).is(FluidTags.WATER)) {
			level.sendParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 24, 0.6, 0.6, 0.6, 0.08);
			level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.5F, 0.8F);
			return true;
		}
		HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ball.owner));
		Vec3 end = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(ball.owner, from, end, new AABB(from, end).inflate(1.0),
			e -> e != ball.owner && !e.isSpectator() && e.isAlive() && e.isPickable(), from.distanceToSqr(end) + 1.0);
		if (hit != null) {
			blast(ball, hit.getLocation(), null);
			return true;
		}
		if (wall instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK) {
			blast(ball, block.getLocation().subtract(ball.velocity.normalize().scale(0.4)), block.getBlockPos());
			return true;
		}
		ball.pos = to;
		// a great ball of flame, a molten heart and a smoking tail
		level.sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 14, 0.4, 0.4, 0.4, 0.02);
		level.sendParticles(ParticleTypes.LAVA, to.x, to.y, to.z, 1, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 1, 0.1, 0.1, 0.1, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 2, 0.2, 0.2, 0.2, 0.01);
		if (ball.age % 6 == 0) {
			level.playSound(null, to.x, to.y, to.z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
		if (ball.age >= FIST_LIFE) {
			blast(ball, to, null);
			return true;
		}
		return false;
	}

	/** The fist bursts at {@code at}: a great blast of flame that throws back, burns and sets fire round; no block broken. */
	private static void blast(Ball ball, Vec3 at, @Nullable BlockPos block) {
		ServerLevel level = ball.level;
		ServerPlayer owner = ball.owner;
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 140, FIST_RADIUS * 0.45, FIST_RADIUS * 0.35, FIST_RADIUS * 0.45, 0.12);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 24, FIST_RADIUS * 0.3, FIST_RADIUS * 0.3, FIST_RADIUS * 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.0, at.z, 40, FIST_RADIUS * 0.4, FIST_RADIUS * 0.4, FIST_RADIUS * 0.4, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.5F, 0.4F);
		Shakes.around(level, at, FIST_RADIUS * 4.0, 4.0F);
		Flames.area(level, at, FIST_RADIUS, FIST_BURN, owner, e -> true);
		SmokeField.thaw(level, at, FIST_RADIUS + 0.5);
		double r2 = FIST_RADIUS * FIST_RADIUS;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(FIST_RADIUS),
			e -> e != owner && e.isAlive() && !e.isSpectator() && e.getBoundingBox().distanceToSqr(at) <= r2)) {
			double dist = Math.sqrt(living.getBoundingBox().distanceToSqr(at));
			double share = 1.0 - 0.5 * dist / FIST_RADIUS;
			living.hurtServer(level, level.damageSources().source(DamageTypes.IN_FIRE, owner), EmberKindle.seasonal(owner, (float)(FIST_DAMAGE * share)));
			throwFrom(living, at, FIST_PUSH * share, 0.4 * share);
		}
		if (block != null) {
			EmberKindle.touch(level, block, owner);
		}
		setFires(level, BlockPos.containing(at), (int)Math.ceil(FIST_RADIUS), FIST_FIRES);
	}

	// ---- G: Flaming Pillar ---------------------------------------------------------------------------------------------

	private static void pillar(ServerPlayer player, boolean atCursor) {
		if (!ready(player, PILLAR)) {
			return;
		}
		Vec3 at;
		double radius;
		if (atCursor) {
			at = cursor(player);
			if (at == null) {
				return;
			}
			radius = PILLAR_RADIUS;
		} else {
			at = player.position();
			radius = PILLAR_SELF_RADIUS;
		}
		cool(player, PILLAR, PILLAR_COOLDOWN);
		ServerLevel level = player.level();
		RandomSource random = level.getRandom();
		// the column: flame thrown up from a ring and from the middle
		for (int i = 0; i < 60; i++) {
			double a = random.nextDouble() * Math.PI * 2.0, r = Math.sqrt(random.nextDouble()) * radius;
			level.sendParticles(ParticleTypes.FLAME, at.x + Math.cos(a) * r, at.y + 0.1, at.z + Math.sin(a) * r, 0, 0.0, 1.0, 0.0,
				0.4 + random.nextDouble() * 0.5);
		}
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2.0 / 24.0;
			level.sendParticles(ParticleTypes.FLAME, at.x + Math.cos(a) * radius, at.y + 0.1, at.z + Math.sin(a) * radius, 0, 0.0, 1.0, 0.0, 0.6);
		}
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 1.0, at.z, 16, radius * 0.4, 1.5, radius * 0.4, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + PILLAR_HEIGHT, at.z, 20, radius * 0.4, 0.6, radius * 0.4, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.6F, 0.4F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.4F, 0.6F);
		Shakes.around(level, at, 12.0, 3.0F);
		AABB column = new AABB(at.x - radius, at.y - 0.5, at.z - radius, at.x + radius, at.y + PILLAR_HEIGHT, at.z + radius);
		Flames.area(level, column, PILLAR_BURN, player, e -> horizontal(e, at) <= radius);
		float dealt = 0.0F;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, column,
			e -> e != player && e.isAlive() && !e.isSpectator() && horizontal(e, at) <= radius)) {
			float before = living.getHealth();
			living.hurtServer(level, level.damageSources().source(DamageTypes.IN_FIRE, player), EmberKindle.seasonal(player, PILLAR_DAMAGE));
			dealt += Math.max(0.0F, before - living.getHealth());
			throwFrom(living, at, PILLAR_OUT, PILLAR_UP);
		}
		if (dealt > 0.0F) {
			FlameAvatar.feed(player, dealt * PILLAR_REGEN);
		}
		setFires(level, BlockPos.containing(at), (int)Math.ceil(radius), PILLAR_FIRES);
	}

	private static double horizontal(Entity entity, Vec3 at) {
		double dx = entity.getX() - at.x, dz = entity.getZ() - at.z;
		return Math.sqrt(dx * dx + dz * dz) - entity.getBbWidth() * 0.5;
	}

	// ---- B: Ignition Switch --------------------------------------------------------------------------------------------

	/** Tap: the hands light everything burnable in a cone in front. */
	private static void ignite(ServerPlayer player) {
		if (!ready(player, SWITCH) || !LimbUse.usable(player, LimbUse.ANY_ARM)) {
			return;
		}
		if (!FlameAvatar.spend(player, IGNITE_COST)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.no_flame")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		cool(player, SWITCH, IGNITE_COOLDOWN);
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F, 1.4F);
		// a sweep of sparks across the cone
		RandomSource random = level.getRandom();
		for (int i = 0; i < 30; i++) {
			Vec3 dir = look.add(random.nextGaussian() * 0.35, random.nextGaussian() * 0.2, random.nextGaussian() * 0.35).normalize();
			level.sendParticles(ParticleTypes.SMALL_FLAME, eye.x, eye.y - 0.3, eye.z, 0, dir.x, dir.y, dir.z, 0.5);
		}
		int r = (int)Math.ceil(IGNITE_REACH);
		BlockPos centre = BlockPos.containing(eye);
		int fires = 0;
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
			Vec3 mid = Vec3.atCenterOf(pos);
			Vec3 off = mid.subtract(eye);
			double dist = off.length();
			if (dist > IGNITE_REACH || dist < 0.5 || off.normalize().dot(look) < IGNITE_COS) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				continue;
			}
			boolean lights = unlit(state);
			boolean burns = state.ignitedByLava() && fires < IGNITE_FIRES;
			if (!lights && !burns || !inSight(level, player, eye, pos)) {
				continue;
			}
			if (lights) {
				level.setBlockAndUpdate(pos, state.setValue(BlockStateProperties.LIT, true));
				level.sendParticles(ParticleTypes.FLAME, mid.x, mid.y + 0.3, mid.z, 4, 0.1, 0.1, 0.1, 0.01);
			} else if (fireBeside(level, pos.immutable())) {
				fires++;
			}
		}
		Flames.area(level, new AABB(eye, eye).inflate(IGNITE_REACH), IGNITE_BURN, player, e -> {
			Vec3 off = e.getBoundingBox().getCenter().subtract(eye);
			return off.length() <= IGNITE_REACH && off.normalize().dot(look) >= IGNITE_COS;
		});
	}

	/** Something lightable not lit: a campfire, a candle, or another mod's unlit torch, lantern or brazier; not under water. */
	private static boolean unlit(BlockState state) {
		if (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT)) {
			return false;
		}
		if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
			return false;
		}
		String path = path(state);
		return path.endsWith("campfire") || path.endsWith("candle") || path.endsWith("candle_cake") || path.contains("torch")
			|| path.contains("lantern") || path.contains("brazier") || path.contains("fire_pit");
	}

	private static boolean inSight(ServerLevel level, ServerPlayer player, Vec3 eye, BlockPos pos) {
		HitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS || hit instanceof BlockHitResult b && b.getBlockPos().equals(pos);
	}

	/** A fire on the first open face of the burnable block at {@code pos} (above first). True if one started. */
	private static boolean fireBeside(ServerLevel level, BlockPos pos) {
		if (EmberKindle.ignite(level, pos.above())) {
			return true;
		}
		for (Direction side : Direction.Plane.HORIZONTAL) {
			BlockPos at = pos.relative(side);
			BlockState here = level.getBlockState(at);
			if (here.isAir() && BaseFireBlock.canBePlacedAt(level, at, side)) {
				level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
				return true;
			}
		}
		return false;
	}

	/** Hold: everything burning in range (in sight) goes up in a small blast. */
	private static void detonate(ServerPlayer player) {
		if (!ready(player, SWITCH) || !LimbUse.usable(player, LimbUse.ANY_ARM)) {
			return;
		}
		List<Vec3> targets = burning(player);
		if (targets.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.nothing_lit")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		ServerLevel level = player.level();
		int blasts = 0;
		for (Vec3 at : targets) {
			if (!FlameAvatar.spend(player, BLAST_COST)) {
				break;
			}
			smallBlast(player, level, at);
			blasts++;
		}
		if (blasts == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.avatar.no_flame")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		cool(player, SWITCH, DETONATE_COOLDOWN);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** What's burning near {@code player} and in sight, nearest first: burning creatures (not them), then fires. */
	private static List<Vec3> burning(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		List<Vec3> out = new ArrayList<>();
		for (Entity entity : level.getEntities(player, new AABB(eye, eye).inflate(DETONATE_RANGE),
			e -> e.isAlive() && !e.isSpectator() && e.isOnFire() && e.distanceToSqr(eye) <= DETONATE_RANGE * DETONATE_RANGE)) {
			Vec3 at = entity.getBoundingBox().getCenter();
			if (level.clip(new ClipContext(eye, at, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
				out.add(at);
			}
		}
		int r = (int)DETONATE_RANGE;
		BlockPos centre = player.blockPosition();
		List<Vec3> fires = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-r, -r / 2, -r), centre.offset(r, r / 2, r))) {
			if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock && Vec3.atCenterOf(pos).distanceToSqr(eye) <= DETONATE_RANGE * DETONATE_RANGE) {
				fires.add(Vec3.atBottomCenterOf(pos).add(0.0, 0.3, 0.0));
			}
		}
		fires.sort(Comparator.comparingDouble(v -> v.distanceToSqr(eye)));
		for (Vec3 at : fires) {
			if (out.size() >= DETONATE_MAX) {
				break;
			}
			// one blast per spot: fires side by side would stack
			if (out.stream().noneMatch(o -> o.distanceToSqr(at) < BLAST_RADIUS * BLAST_RADIUS)) {
				out.add(at);
			}
		}
		return out.size() > DETONATE_MAX ? out.subList(0, DETONATE_MAX) : out;
	}

	/** A small blast at {@code at}: harm and a shove round it, the fire there spent; no block broken. */
	private static void smallBlast(ServerPlayer player, ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 20, 0.5, 0.5, 0.5, 0.08);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 8, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8F, 1.3F);
		double r2 = BLAST_RADIUS * BLAST_RADIUS;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BLAST_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && e.getBoundingBox().distanceToSqr(at) <= r2)) {
			living.hurtServer(level, level.damageSources().source(DamageTypes.IN_FIRE, player), EmberKindle.seasonal(player, BLAST_DAMAGE));
			throwFrom(living, at, 0.5, 0.3);
		}
		BlockPos pos = BlockPos.containing(at);
		if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) {
			level.removeBlock(pos, false);
		}
	}

	// ---- shared --------------------------------------------------------------------------------------------------------

	/** Throws {@code entity} away from {@code from}: {@code out} along the ground, {@code up} upward. */
	private static void throwFrom(LivingEntity entity, Vec3 from, double out, double up) {
		Vec3 away = entity.position().subtract(from);
		away = new Vec3(away.x, 0.0, away.z);
		away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(out);
		double resist = 1.0 - Math.min(1.0, entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
		entity.push(away.x * resist, up * resist, away.z * resist);
		entity.syncVelocity = true;
	}

	/** Up to {@code fires} fires started round {@code centre} (within {@code r}), each beside something that burns, or on the ground. */
	private static void setFires(ServerLevel level, BlockPos centre, int r, int fires) {
		RandomSource random = level.getRandom();
		int started = 0;
		for (int i = 0; i < fires * 4 && started < fires; i++) {
			BlockPos pos = centre.offset(random.nextInt(r * 2 + 1) - r, random.nextInt(r + 1) - r / 2, random.nextInt(r * 2 + 1) - r);
			BlockState state = level.getBlockState(pos);
			if (state.ignitedByLava() ? fireBeside(level, pos) : i % 4 == 0 && EmberKindle.ignite(level, pos)) {
				started++;
			}
		}
	}

}
