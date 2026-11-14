package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.ground.ThrownRocks;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a speedster's hands do at speed (Overclock and Velocity both):
 * <ul>
 * <li><b>Running blow:</b> a melee hit landed sprinting deals more the faster you really are going and the higher your
 * gear ({@link #blowBonus}), and shoves the target along your path ({@link #BLOW_SHOVE}).</li>
 * <li><b>Rapid bows:</b> a bow is full draw after {@link #BOW_DRAW_TICKS} ticks and a crossbow loads at once
 * ({@code ChronoRapidBowMixin}).</li>
 * <li><b>Catch and hurl:</b> use, aiming at a shot in flight within {@link #CATCH_REACH}: you pluck it from the air and
 * it hangs before you. Use again and it flies back {@link #HURL_FACTOR} times as fast, down your sight.</li>
 * <li><b>Weapon snatch:</b> use on an armed mob while you run past: its main-hand weapon is in your hand (or your pack,
 * or on the ground). Never on players.</li>
 * <li><b>Quickened rocks:</b> Andy: "chrono should be able to accelerate thrown rocks to an insane fps. almost becoming
 * a bullet". A loose rock a speedster throws ({@link ThrownRocks}), or a caught one hurled back, leaves the hand with
 * its own time run fast: {@link #ROCK_SPEED} blocks a tick, dead flat, {@link #ROCK_DAMAGE} where it hits, a crack of
 * air as it goes and a gold streak behind it.</li>
 * </ul>
 * The client sends one {@link Use} per press of use while it is at speed.
 */
public final class ChronoRapid {
	// ---- tuning ----
	/** Running blow: extra damage share per block-per-tick of real speed, its cap, and the shove (blocks a tick) along your path. */
	static final double BLOW_PER_SPEED = 2.2, BLOW_MAX = 2.0, BLOW_SHOVE = 1.1;
	/** Rapid bows: ticks to full draw. */
	public static final int BOW_DRAW_TICKS = 4;
	/** Catching: how far, how near to the sight line a shot must be, and how far in front of you it hangs. */
	static final double CATCH_REACH = 6.0, CATCH_WIDTH = 0.9, HOLD_AHEAD = 1.7;
	/** Hurling: the speed it goes back at is this times the speed it came at, at least {@link #HURL_MIN} (blocks a tick), at most {@link #HURL_MAX}. */
	static final double HURL_FACTOR = 2.2, HURL_MIN = 2.6, HURL_MAX = 5.0;
	/**
	 * A quickened rock: its speed (blocks a tick; 6 is 120 blocks a second), what it hits for, how long it flies before it
	 * is gone (ticks), and its streak's colour, width and how long each stretch of it stays (ticks).
	 */
	public static final float ROCK_SPEED = 6.0F, ROCK_DAMAGE = 12.0F;
	public static final int ROCK_LIFE = 40;
	static final int ROCK_COLOR = 0xFFD070, ROCK_STREAK_TICKS = 8;
	static final float ROCK_STREAK = 0.05F;
	/**
	 * An overclocked Chrono's rock charge: this many ticks shorter for each ability learned and each Mastery rank (0 to
	 * 9 in all: 1.5 s with nothing learned, 0.15 s at Paragon), never under {@link #ROCK_CHARGE_MIN}. Velox never charges.
	 */
	static final int ROCK_CHARGE_STEP = 3, ROCK_CHARGE_MIN = 3;
	/** A held shot is let go after this many ticks, and if you stop being a speedster. */
	static final int HOLD_TICKS = 100;
	/** Weapon snatch: reach, the speed you need (blocks a tick), the wait between snatches. */
	static final double SNATCH_REACH = 3.6, SNATCH_SPEED = 0.25;
	static final int SNATCH_GAP = 15;
	/** Using needs to be this fast (blocks a tick) at least, or sprinting. */
	static final double USE_SPEED = 0.15;

	/** Client to server: use was pressed at speed. */
	public record Use() implements CustomPacketPayload {
		public static final Type<Use> TYPE = new Type<>(JaymosPowers.id("chrono_rapid_use"));
		public static final StreamCodec<ByteBuf, Use> CODEC = StreamCodec.unit(new Use());

		@Override
		public Type<Use> type() {
			return TYPE;
		}
	}

	private record Held(UUID shot, double speed, boolean noGravity, int since) {}

	private static final Map<UUID, Held> HELD = new HashMap<>();
	private static final Map<UUID, Integer> SNATCHED = new HashMap<>();

	private ChronoRapid() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, ctx) -> use(ctx.player()));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken > 0.0F && !blocked && source.getEntity() instanceof ServerPlayer player && source.is(DamageTypes.PLAYER_ATTACK)
				&& source.getDirectEntity() == player && ChronoSpeed.overclocked(player) && player.isSprinting()) {
				shove(player, victim);
			}
		});
	}

	/** Takes everything off (power lost, Overclock ended): a held shot is let go. */
	static void clear(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held != null && player.level().getEntity(held.shot) instanceof Projectile shot) {
			let(player, shot, held, 0.35);
		}
		ChronoMeters.set(player, ChronoMeters.CAUGHT, -1.0F);
	}

	/** The real horizontal speed of {@code player} this tick (blocks a tick). */
	static double speed(Player player) {
		double dx = player.getX() - player.xo, dz = player.getZ() - player.zo;
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---------------------------------------------------------------- running blow

	/**
	 * The extra damage share (0 and up) of a melee blow landed by the source's attacker: 0 unless it is a speedster
	 * sprinting. Read by {@code ChronoRunBlowMixin}.
	 */
	public static float blowBonus(DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || source.getDirectEntity() != player || !source.is(DamageTypes.PLAYER_ATTACK)
			|| !player.isSprinting() || !ChronoSpeed.overclocked(player)) {
			return 0.0F;
		}
		double gear = (0.5 + 0.25 * ChronoGears.gear(player)) / 1.25;
		return (float)Math.min(BLOW_MAX, speed(player) * BLOW_PER_SPEED * gear);
	}

	private static void shove(ServerPlayer player, LivingEntity victim) {
		Vec3 path = new Vec3(player.getX() - player.xo, 0.0, player.getZ() - player.zo);
		if (path.lengthSqr() < 1.0E-4 || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		double push = Math.min(BLOW_SHOVE, BLOW_SHOVE * speed(player) / 0.5);
		Vec3 along = path.normalize();
		victim.setDeltaMovement(victim.getDeltaMovement().add(along.x * push, 0.18, along.z * push));
		victim.syncVelocity = true;
		MagicFx.ring(level, victim.position().add(0.0, 0.2, 0.0), 1.4F, 0xFFD070);
		level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 1.3F);
	}

	// ---------------------------------------------------------------- catch, hurl, snatch

	private static void use(ServerPlayer player) {
		if (!ChronoSpeed.overclocked(player) || !player.isAlive() || player.isSpectator() || player.isPassenger()
			|| !player.isSprinting() && speed(player) < USE_SPEED) {
			return;
		}
		ServerLevel level = player.level();
		Held held = HELD.get(player.getUUID());
		if (held != null) {
			HELD.remove(player.getUUID());
			ChronoMeters.set(player, ChronoMeters.CAUGHT, -1.0F);
			if (level.getEntity(held.shot) instanceof Projectile shot && shot.isAlive()) {
				hurl(player, level, shot, held);
				return;
			}
		}
		Projectile shot = shotAhead(player, level);
		if (shot != null) {
			Vec3 v = shot.getDeltaMovement();
			HELD.put(player.getUUID(), new Held(shot.getUUID(), v.length(), shot.isNoGravity(), player.tickCount));
			shot.setNoGravity(true);
			shot.setDeltaMovement(Vec3.ZERO);
			shot.syncVelocity = true;
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.8F, 1.8F);
			MagicFx.blast(level, shot.position(), 0.6F, 0xFFE9A0);
			return;
		}
		snatch(player, level);
	}

	/** The nearest shot in flight along the sight line within reach, or null. */
	private static Projectile shotAhead(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(CATCH_REACH));
		Projectile best = null;
		double bestAlong = Double.MAX_VALUE;
		for (Projectile shot : level.getEntitiesOfClass(Projectile.class, new AABB(eye, end).inflate(CATCH_WIDTH + 0.5), p -> p.isAlive())) {
			if (HELD.values().stream().anyMatch(h -> h.shot.equals(shot.getUUID())) || shot.getOwner() == player
				|| shot.getDeltaMovement().lengthSqr() < 0.01) {
				continue;
			}
			Vec3 to = shot.position().subtract(eye);
			double along = to.dot(look);
			if (along < 0.0 || along > CATCH_REACH) {
				continue;
			}
			double off = to.subtract(look.scale(along)).length();
			if (off <= CATCH_WIDTH + shot.getBbWidth() && along < bestAlong) {
				best = shot;
				bestAlong = along;
			}
		}
		return best;
	}

	private static void hurl(ServerPlayer player, ServerLevel level, Projectile shot, Held held) {
		Vec3 look = player.getLookAngle();
		double speed = Mth.clamp(held.speed * HURL_FACTOR, HURL_MIN, HURL_MAX);
		shot.setOwner(player);
		shot.setNoGravity(true);
		shot.snapTo(player.getEyePosition().add(look.scale(HOLD_AHEAD)));
		// a rock goes back quickened
		boolean rock = shot instanceof Snowball ball && ThrownRocks.quicken(ball, ROCK_DAMAGE);
		shot.setDeltaMovement(look.scale(rock ? ROCK_SPEED : speed));
		shot.syncVelocity = true;
		shot.needsSync = true;
		if (rock) {
			rockCrack(level, shot.position(), look);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.6F);
		MagicFx.ring(level, shot.position(), 1.0F, 0xFFD070);
	}

	// ---------------------------------------------------------------- quickened rocks

	/** Whether a rock {@code player} throws goes quickened: any speedster, overclocked. */
	public static boolean quickens(Player player) {
		return ChronoSpeed.overclocked(player);
	}

	/** A rock thrown in Velox's Velocity goes at once, with no charge (Andy: "only chrono has to charge"). On either side. */
	public static boolean instantRock(Player player) {
		return ChronoSpeed.velocity(player);
	}

	/**
	 * The charge (ticks) of {@code player}'s rock, {@code charge} for anyone else: an overclocked Chrono's "lessens w/
	 * mastery" (Andy), by {@link #ROCK_CHARGE_STEP} for each ability learned and each Mastery rank. On either side.
	 */
	public static int rockCharge(Player player, int charge) {
		if (!quickens(player) || ChronoSpeed.velocity(player)) {
			return charge;
		}
		int mastery = Mastery.unlocked(player) + Mastery.rank(player);
		return Math.max(Math.min(charge, ROCK_CHARGE_MIN), charge - ROCK_CHARGE_STEP * mastery);
	}

	/** {@code player}'s rock has just left the hand at {@link #ROCK_SPEED}: it is quickened from here ({@link ThrownRocks#quicken}). */
	public static void quickened(ServerPlayer player, Snowball ball) {
		if (ThrownRocks.quicken(ball, ROCK_DAMAGE)) {
			ball.setNoGravity(true);
			rockCrack(player.level(), ball.position(), ball.getDeltaMovement());
		}
	}

	/** The crack of air as a quickened rock goes: a ring across its path, and the sound. */
	private static void rockCrack(ServerLevel level, Vec3 at, Vec3 way) {
		MagicFx.ring(level, at, way, 0.8F, ROCK_COLOR, MagicFx.RING_TICKS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.9F, 1.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8F, 1.9F);
	}

	/** A quickened rock's streak, over the stretch it flew this tick. */
	public static void rockStreak(ServerLevel level, Vec3 from, Vec3 to) {
		if (from.distanceToSqr(to) > 1.0E-4) {
			MagicFx.beam(level, from, to, ROCK_STREAK, ROCK_COLOR, ROCK_STREAK_TICKS);
		}
	}

	/** A quickened rock has struck at {@code at}: the last of its streak, and the burst. */
	public static void rockStruck(ServerLevel level, Vec3 from, Vec3 at) {
		rockStreak(level, from, at);
		MagicFx.blast(level, at, 0.6F, ROCK_COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7F, 0.8F);
	}

	/** Lets a held shot go gently, as it was (gravity back). */
	private static void let(ServerPlayer player, Projectile shot, Held held, double push) {
		shot.setNoGravity(held.noGravity);
		shot.setDeltaMovement(player.getLookAngle().scale(push));
		shot.syncVelocity = true;
	}

	private static void snatch(ServerPlayer player, ServerLevel level) {
		if (speed(player) < SNATCH_SPEED || SNATCHED.getOrDefault(player.getUUID(), 0) > player.tickCount) {
			return;
		}
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(SNATCH_REACH));
		Mob best = null;
		double bestAlong = Double.MAX_VALUE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(eye, end).inflate(1.5), m -> m.isAlive() && !m.getMainHandItem().isEmpty())) {
			var hit = mob.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent()) {
				double along = hit.get().distanceTo(eye);
				if (along < bestAlong) {
					best = mob;
					bestAlong = along;
				}
			}
		}
		if (best == null) {
			return;
		}
		ItemStack weapon = best.getMainHandItem();
		best.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		SNATCHED.put(player.getUUID(), player.tickCount + SNATCH_GAP);
		if (player.getMainHandItem().isEmpty()) {
			player.setItemInHand(InteractionHand.MAIN_HAND, weapon);
		} else if (!player.getInventory().add(weapon)) {
			player.drop(weapon, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, best.getX(), best.getY(), best.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.sendParticles(ParticleTypes.CRIT, best.getX(), best.getEyeY() - 0.4, best.getZ(), 6, 0.3, 0.2, 0.3, 0.2);
	}

	/** Each tick (server) while overclocked: a held shot hangs before you, and is let go when held too long. */
	static void tick(ServerPlayer player, ServerLevel level) {
		Held held = HELD.get(player.getUUID());
		if (held == null) {
			return;
		}
		if (!(level.getEntity(held.shot) instanceof Projectile shot) || !shot.isAlive()) {
			HELD.remove(player.getUUID());
			ChronoMeters.set(player, ChronoMeters.CAUGHT, -1.0F);
			return;
		}
		int age = player.tickCount - held.since;
		if (age >= HOLD_TICKS) {
			HELD.remove(player.getUUID());
			let(player, shot, held, 0.5);
			ChronoMeters.set(player, ChronoMeters.CAUGHT, -1.0F);
			return;
		}
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(HOLD_AHEAD)).add(0.0, -0.25, 0.0);
		shot.snapTo(at);
		shot.setDeltaMovement(Vec3.ZERO);
		shot.syncVelocity = true;
		shot.needsSync = true;
		ChronoMeters.set(player, ChronoMeters.CAUGHT, 1.0F - age / (float)HOLD_TICKS);
	}

	/** Whether {@code entity} is a speedster (a bow draws fast, a crossbow loads at once). */
	public static boolean rapid(LivingEntity entity) {
		return entity instanceof Player player && ChronoSpeed.overclocked(player);
	}
}
