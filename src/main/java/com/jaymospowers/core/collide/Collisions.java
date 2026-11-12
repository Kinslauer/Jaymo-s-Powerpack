package com.jaymospowers.core.collide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BlastGore;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Wounds;
import com.jaymospowers.power.web.KitchenCompat;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy's BONK! picks (2026-11-12, ideas only: BONK! is All Rights Reserved): bodies that hit things hard get hurt.
 * <ul>
 * <li><b>Wall slams</b>: anyone knocked back or blown into a wall at speed takes damage by how hard they hit it, with a
 * thud, a puff of the wall's dust and a short stagger. Mobs are judged here on the server, a player on their own client
 * (their movement is theirs), which sends {@link Bump}. A hard enough slam knocks a player limp (Jaymo's Kitchen).</li>
 * <li><b>Running into walls</b>: a flat-out sprint into a wall face (not a step, not a glancing brush) bumps you, half a
 * heart at most. Freerun moves, ledge grabs, crouching and swimming never count (the client checks).</li>
 * <li><b>Head bumps</b>: jumping into a ceiling knocks your head, a little. Andy: "hitting hard enough can ragdoll you":
 * a launch or a boosted jump into it does.</li>
 * <li><b>Sore knuckles</b>: punching or starting to dig a hard block (one that needs a tool, as stone, ore and metal do)
 * with an empty hand bruises that arm, once per punch. Andy: "same goes for bare hands on armor or stoneborn skin, or
 * mobs with tough enough hide": punching a body that wears {@link #ARMOUR_HURTS} armour or more, a Stoneborn, or anything
 * in {@link #TOUGH_HIDE}. Fist gear spares you (it is held in the hand, so the hand isn't bare); so does being Stoneborn.
 * A boxer or a Z Fighter punching a body never bruises ({@link #fistFighter}); on rock they do.</li>
 * <li><b>Riders thrown</b>: a galloping horse that runs into a wall throws its rider forward, limp, and both are hurt.
 * Flying mounts ({@link #CRASH_PROOF}, anything called a dragon) never crash.</li>
 * </ul>
 * Logs already splinter the punching hand ({@code ground/Splinters}); those are left to it.
 */
public final class Collisions {
	/** A slam: impact speed (blocks a tick, what the wall took off the body's speed) from which it hurts. */
	public static final double SLAM_MIN = 0.55;
	/** A slam's damage: impact x this, less {@link #SLAM_OFFSET}, between the bounds (health points). */
	public static final float SLAM_PER_SPEED = 10.0F, SLAM_OFFSET = 4.5F, SLAM_DAMAGE_MIN = 1.0F, SLAM_DAMAGE_MAX = 20.0F;
	/** A slam this hard (damage) knocks a player limp, bounced off the wall this fast. */
	public static final float SLAM_LIMP = 6.0F;
	public static final double SLAM_BOUNCE = 0.15;
	/** The stagger after a slam (Slowness II, ticks). */
	public static final int STAGGER_TICKS = 20;
	/** No body slams twice within this many ticks. */
	public static final int SLAM_COOLDOWN = 10;

	/** A sprint into a wall: from this impact (blocks a tick) it bumps; damage = (impact - this) x the next, at most the cap. */
	public static final double SPRINT_MIN = 0.22;
	public static final float SPRINT_PER_SPEED = 12.0F, SPRINT_MAX = 1.0F;

	/** A head bump: from this upward speed (blocks a tick; a plain jump starts at 0.42); damage = a little plus speed past a jump. */
	public static final double HEAD_MIN = 0.2, JUMP = 0.42;
	public static final float HEAD_BASE = 0.5F, HEAD_PER_SPEED = 10.0F, HEAD_MAX = 8.0F;
	/** A head bump this fast (blocks a tick) knocks you limp. */
	public static final double HEAD_LIMP = 0.7;

	/** Bare fists: what one punch takes from the arm (health points); armour from this much up hurts the fist. */
	public static final float KNUCKLES = 0.5F;
	public static final int ARMOUR_HURTS = 8;
	/** Knuckles hurt once per this many ticks at most (a frantic clicker isn't hurt every frame). */
	public static final int KNUCKLE_COOLDOWN = 5;
	/** Mobs whose hide hurts a bare fist. */
	public static final TagKey<EntityType<?>> TOUGH_HIDE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("tough_hide"));

	/** A crash: the mount's impact speed (blocks a tick) from which it throws its rider; damage per speed to both. */
	public static final double CRASH_MIN = 0.4;
	public static final float CRASH_PER_SPEED = 12.0F, CRASH_DAMAGE_MAX = 14.0F;
	/** The rider flies on forward at this share of the mount's speed, and up this much. */
	public static final double THROW_SHARE = 0.9, THROW_UP = 0.25;
	/** Mounts that never crash. */
	public static final TagKey<EntityType<?>> CRASH_PROOF = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("crash_proof"));

	/** The most a client may claim (blocks a tick): anything faster is clamped. */
	static final double MAX_CLAIM = 4.0;

	public static final byte SLAM = 0, SPRINT = 1, HEAD = 2, CRASH = 3;

	/**
	 * Client → server: the player's own body hit something. {@code kind} is one of {@link #SLAM}, {@link #SPRINT},
	 * {@link #HEAD}, {@link #CRASH}; {@code speed} the impact (blocks a tick); {@code dx, dz} the way they were going.
	 */
	public record Bump(byte kind, float speed, float dx, float dz) implements CustomPacketPayload {
		public static final Type<Bump> TYPE = new Type<>(JaymosPowers.id("collision_bump"));
		public static final StreamCodec<ByteBuf, Bump> CODEC = StreamCodec.composite(
			ByteBufCodecs.BYTE, Bump::kind, ByteBufCodecs.FLOAT, Bump::speed, ByteBufCodecs.FLOAT, Bump::dx, ByteBufCodecs.FLOAT, Bump::dz,
			Bump::new);

		@Override
		public Type<Bump> type() {
			return TYPE;
		}
	}

	/** The client's say on its own moves (set by {@code client/collide/CollisionsClient}). */
	public interface ClientMoves {
		void moved(Entity entity, Vec3 movement, double dx, double dy, double dz);
	}

	private static volatile @Nullable ClientMoves clientMoves;
	/** Who last slammed when (game time). */
	private static final Map<LivingEntity, Long> SLAMMED = new WeakHashMap<>();
	/** Each player's last bump of each kind, and last sore knuckle (game time). */
	private static final Map<UUID, long[]> BUMPED = new HashMap<>();

	private Collisions() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Bump.TYPE, Bump.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Bump.TYPE, (payload, context) -> bumped(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> BUMPED.remove(handler.player.getUUID()));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && bare(sp)) {
				BlockState state = server.getBlockState(pos);
				if (!state.is(BlockTags.LOGS) && state.requiresCorrectToolForDrops() && state.getDestroySpeed(server, pos) >= 1.0F) {
					knuckles(sp, SoundEvents.STONE_HIT);
				}
			}
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (level instanceof ServerLevel && player instanceof ServerPlayer sp && bare(sp) && !fistFighter(sp) && target instanceof LivingEntity body && tough(body)) {
				knuckles(sp, body.getArmorValue() >= ARMOUR_HURTS ? SoundEvents.ANVIL_LAND : SoundEvents.STONE_HIT);
			}
			return InteractionResult.PASS;
		});
	}

	public static void setClientMoves(@Nullable ClientMoves moves) {
		clientMoves = moves;
	}

	/**
	 * Every {@link Entity#move} that bumped into something ({@code CollideMoveMixin}): {@code movement} is where it meant
	 * to go this tick, {@code dx, dy, dz} where it got. Mobs are judged here; the client judges its own player and mount.
	 */
	public static void moved(Entity entity, MoverType type, Vec3 movement, double dx, double dy, double dz) {
		if (type != MoverType.SELF && type != MoverType.PLAYER) {
			return;
		}
		if (entity.level().isClientSide()) {
			ClientMoves moves = clientMoves;
			if (moves != null) {
				moves.moved(entity, movement, dx, dy, dz);
			}
			return;
		}
		// (a player's moves are theirs: the server re-running them must not slam them twice)
		if (!(entity instanceof LivingEntity body) || entity instanceof Player || !entity.horizontalCollision || body.hurtTime <= 0
			|| body.isFallFlying() || entity.isPassenger() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		double impact = Math.hypot(movement.x - dx, movement.z - dz);
		if (impact >= SLAM_MIN) {
			slam(level, body, impact, movement.x, movement.z);
		}
	}

	/** {@code body} hit a wall {@code impact} hard (blocks a tick), going {@code mx, mz}. */
	public static void slam(ServerLevel level, LivingEntity body, double impact, double mx, double mz) {
		long now = level.getGameTime();
		Long last = SLAMMED.get(body);
		if (last != null && now - last < SLAM_COOLDOWN || !body.isAlive() || Downed.isDowned(body)) {
			return;
		}
		SLAMMED.put(body, now);
		float damage = Mth.clamp((float) impact * SLAM_PER_SPEED - SLAM_OFFSET, SLAM_DAMAGE_MIN, SLAM_DAMAGE_MAX);
		double flat = Math.hypot(mx, mz);
		double ux = flat < 1.0E-4 ? 0.0 : mx / flat, uz = flat < 1.0E-4 ? 0.0 : mz / flat;
		Vec3 wall = new Vec3(body.getX() + ux * (body.getBbWidth() * 0.5 + 0.05), body.getY() + body.getBbHeight() * 0.5,
			body.getZ() + uz * (body.getBbWidth() * 0.5 + 0.05));
		body.hurtServer(level, level.damageSources().flyIntoWall(), damage);
		body.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, 1));
		dust(level, wall, ux, uz, 6 + Math.round(damage));
		level.playSound(null, wall.x, wall.y, wall.z, body instanceof Player ? SoundEvents.PLAYER_BIG_FALL : SoundEvents.GENERIC_BIG_FALL,
			body.getSoundSource(), Mth.clamp(0.5F + damage * 0.08F, 0.5F, 1.5F), 0.8F);
		if (body instanceof ServerPlayer player) {
			Shakes.send(player, Mth.clamp(damage * 0.8F, 1.5F, 8.0F), wall);
			if (damage >= SLAM_LIMP && player.isAlive()) {
				KitchenCompat.knockLimp(player, -ux * SLAM_BOUNCE, 0.05, -uz * SLAM_BOUNCE);
			}
		}
	}

	/** A puff of the wall's own dust where a body hit it. */
	private static void dust(ServerLevel level, Vec3 at, double ux, double uz, int count) {
		BlockPos pos = BlockPos.containing(at.x + ux * 0.3, at.y, at.z + uz * 0.3);
		BlockState state = level.getBlockState(pos);
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, count, 0.15, 0.3, 0.15, 0.1);
		}
	}

	/** A player's own client says they hit something. */
	private static void bumped(ServerPlayer player, Bump bump) {
		if (!player.isAlive() || player.isSpectator() || player.isCreative() || bump.kind() < SLAM || bump.kind() > CRASH
			|| !(player.level() instanceof ServerLevel level) || Downed.isDowned(player)) {
			return;
		}
		double speed = Mth.clamp(Float.isFinite(bump.speed()) ? bump.speed() : 0.0F, 0.0, MAX_CLAIM);
		double dx = Float.isFinite(bump.dx()) ? bump.dx() : 0.0, dz = Float.isFinite(bump.dz()) ? bump.dz() : 0.0;
		switch (bump.kind()) {
			case SLAM -> {
				if (speed >= SLAM_MIN) {
					slam(level, player, speed, dx, dz);
				}
			}
			case SPRINT -> {
				if (speed >= SPRINT_MIN && ready(player, SPRINT, 20)) {
					float damage = Math.min(SPRINT_MAX, (float) (speed - SPRINT_MIN) * SPRINT_PER_SPEED + 0.2F);
					player.hurtServer(level, level.damageSources().flyIntoWall(), damage);
					level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.7F, 0.7F);
					Shakes.send(player, 2.0F, player.getEyePosition().add(dx, 0.0, dz));
					dust(level, player.position().add(0.0, 1.0, 0.0), unit(dx, dz)[0], unit(dx, dz)[1], 4);
				}
			}
			case HEAD -> {
				if (speed >= HEAD_MIN && ready(player, HEAD, 10)) {
					float damage = Mth.clamp(HEAD_BASE + (float) Math.max(0.0, speed - JUMP) * HEAD_PER_SPEED, HEAD_BASE, HEAD_MAX);
					// (a plain hurt, not the head part: a run of bumps in a low tunnel must never be what kills)
					player.hurtServer(level, level.damageSources().flyIntoWall(), damage);
					Vec3 top = player.position().add(0.0, player.getBbHeight() + 0.1, 0.0);
					level.playSound(null, top.x, top.y, top.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.8F, 0.6F);
					BlockState ceiling = level.getBlockState(BlockPos.containing(top));
					if (!ceiling.isAir()) {
						level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ceiling), top.x, top.y - 0.1, top.z, 5, 0.2, 0.0, 0.2, 0.05);
					}
					Shakes.send(player, Mth.clamp(2.0F + damage, 2.0F, 8.0F), top);
					if (speed >= HEAD_LIMP && player.isAlive()) {
						KitchenCompat.knockLimp(player, 0.0, -0.2, 0.0);
					}
				}
			}
			case CRASH -> {
				if (speed >= CRASH_MIN && player.getVehicle() instanceof AbstractHorse mount && crashes(mount) && ready(player, CRASH, 40)) {
					crash(level, player, mount, speed, dx, dz);
				}
			}
			default -> {
			}
		}
	}

	/** A galloping {@code mount} ran {@code speed} hard into a wall: its rider goes over its head. */
	private static void crash(ServerLevel level, ServerPlayer rider, AbstractHorse mount, double speed, double dx, double dz) {
		float damage = Mth.clamp((float) (speed - CRASH_MIN) * CRASH_PER_SPEED + 2.0F, 2.0F, CRASH_DAMAGE_MAX);
		double[] u = unit(dx, dz);
		Vec3 wall = mount.position().add(u[0] * (mount.getBbWidth() * 0.5 + 0.1), mount.getBbHeight() * 0.6, u[1] * (mount.getBbWidth() * 0.5 + 0.1));
		rider.stopRiding();
		mount.hurtServer(level, level.damageSources().flyIntoWall(), damage);
		mount.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS * 2, 2));
		rider.hurtServer(level, level.damageSources().flyIntoWall(), damage * 0.75F);
		dust(level, wall, u[0], u[1], 10);
		level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.HORSE_LAND, SoundSource.NEUTRAL, 1.2F, 0.6F);
		level.playSound(null, wall.x, wall.y, wall.z, SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
		Shakes.send(rider, 6.0F, wall);
		if (rider.isAlive()) {
			KitchenCompat.knockLimp(rider, u[0] * speed * THROW_SHARE, THROW_UP, u[1] * speed * THROW_SHARE);
		}
	}

	/** Whether {@code mount} can crash: not a flier, not a dragon. */
	public static boolean crashes(Entity mount) {
		return !mount.getType().builtInRegistryHolder().is(CRASH_PROOF) && !mount.isNoGravity()
			&& !net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mount.getType()).getPath().contains("dragon");
	}

	private static double[] unit(double x, double z) {
		double flat = Math.hypot(x, z);
		return flat < 1.0E-4 ? new double[] {0.0, 0.0} : new double[] {x / flat, z / flat};
	}

	/** Whether {@code player}'s last {@code kind} was at least {@code gap} ticks ago (and marks this one). */
	private static boolean ready(ServerPlayer player, int kind, int gap) {
		long now = player.level().getGameTime();
		long[] last = BUMPED.computeIfAbsent(player.getUUID(), id -> new long[] {-9999, -9999, -9999, -9999, -9999});
		if (now - last[kind] < gap) {
			return false;
		}
		last[kind] = now;
		return true;
	}

	/** An empty main hand, in survival. */
	private static boolean bare(Player player) {
		return !player.isCreative() && !player.isSpectator() && player.getMainHandItem().isEmpty() && !stoneborn(player);
	}

	/**
	 * A fist fighter: a boxer (any style) or a Z Fighter. Their fists are hardened for people, so armour, Stoneborn skin
	 * and thick hide don't bruise them. Rock still does (Cruor and BONK pick: "fist fighters keep their fists on people,
	 * not on rock").
	 */
	private static boolean fistFighter(ServerPlayer player) {
		return com.jaymospowers.boxing.Boxing.style(player) != null
			|| com.jaymospowers.core.Powers.has(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE);
	}

	private static boolean stoneborn(Entity entity) {
		return entity.entityTags().contains(BlastGore.STONE_TAG);
	}

	/** Whether punching {@code body} bare-handed hurts: armoured, Stoneborn, or thick-hided. */
	public static boolean tough(LivingEntity body) {
		return body.getArmorValue() >= ARMOUR_HURTS || stoneborn(body) || body.getType().builtInRegistryHolder().is(TOUGH_HIDE);
	}

	private static void knuckles(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
		if (!ready(player, 4, KNUCKLE_COOLDOWN)) {
			return;
		}
		BodyPart arm = player.getMainArm() == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		Wounds.hurtPart(player, arm, KNUCKLES, null);
		player.level().playSound(null, player.getX(), player.getY() + 1.2, player.getZ(), sound, SoundSource.PLAYERS, 0.35F, 1.6F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.4F, 1.4F);
	}
}
