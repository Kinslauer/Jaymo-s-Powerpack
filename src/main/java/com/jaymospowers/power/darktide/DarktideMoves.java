package com.jaymospowers.power.darktide;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.PowerInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * R, G, V and B, with their ascended knife versions:
 * <ul>
 * <li>R Dark Strike, held to charge (full at {@link DarktideMove#CHARGE_FULL_TICKS}): a blade raised overhead hits for
 * 1-{@link #STRIKE_FACTOR}× its damage, a fist drawn back for {@link #PUNCH_MIN}-{@link #PUNCH_MAX}; after a parry
 * ×{@link #EMPOWERED_FACTOR}/{@link #STRIKE_FACTOR} more for {@link #EMPOWER_FLOW} Flow.</li>
 * <li>R (knives, ascended) Pounce: a stab stance, then a leap onto the enemy's back; three stabs on a crippled humanoid.</li>
 * <li>V Undertow: sink into water or shadow where you look and surface there.</li>
 * <li>G Crescent (katana): a 120° slash, aimed: low cripples a humanoid (and may take a leg), high may take its head,
 * between an arm ({@link Dismember#KATANA_CHANCE} against humanoid mobs, players {@link Dismember#PLAYER_CHANCE}); Ink
 * Cross (knife): a crossing slash that slows, and cripples; Fang Barrage (knives, ascended).</li>
 * <li>B Black Tide: a rolling wave of dark water; Riptide Dash (knives, ascended), a flurry when R is held.</li>
 * </ul>
 */
final class DarktideMoves {
	// ---- R: Dark Strike ----
	static final double STRIKE_REACH = 3.5;
	/** A blade at full charge: this many times its damage (1× uncharged); empowered: {@link #EMPOWERED_FACTOR} at full charge. */
	static final float STRIKE_FACTOR = 1.5F, EMPOWERED_FACTOR = 2.5F;
	/** A bare fist: damage uncharged and at full charge; it lands on anything within PUNCH_RADIUS of the crosshair. */
	static final float PUNCH_MIN = 2.0F, PUNCH_MAX = 10.0F;
	static final double PUNCH_RADIUS = 0.9;
	static final float EMPOWER_FLOW = 25.0F;
	static final int STRIKE_COOLDOWN = 60;
	// ---- V: Undertow ----
	static final double UNDERTOW_RANGE = 12.0, UNDERTOW_RANGE_MASTERED = 18.0;
	static final float UNDERTOW_FLOW = 15.0F;
	static final int UNDERTOW_COOLDOWN = 120, UNDERTOW_DELAY = 8;
	static final float UNDERTOW_SURFACE_DAMAGE = 3.0F;
	static final double UNDERTOW_SURFACE_RADIUS = 1.6;
	// ---- G: Crescent (katana) ----
	static final double CRESCENT_RANGE = 4.0, CRESCENT_ARC = 120.0;
	static final int CRESCENT_TARGETS = 3;
	static final float CRESCENT_BONUS = 4.0F, CRESCENT_FLOW = 15.0F;
	static final int CRESCENT_COOLDOWN = 160;
	static final int CRESCENT_COLOR = 0x10285A;
	// ---- G: Ink Cross (knife) ----
	static final double INK_CROSS_RANGE = 3.0;
	static final float INK_CROSS_FACTOR = 1.5F, INK_CROSS_FLOW = 10.0F;
	static final int INK_CROSS_COOLDOWN = 120, INK_CROSS_SLOW_TICKS = 60, INK_CROSS_SLOW_AMPLIFIER = 2;
	// ---- B: Black Tide ----
	static final int TIDE_LENGTH = 10;
	static final double TIDE_WIDTH = 5.0, TIDE_HEIGHT = 2.5, TIDE_PUSH = 0.55;
	static final float TIDE_DAMAGE = 6.0F, TIDE_FLOW = 40.0F;
	static final int TIDE_COOLDOWN = 400, TIDE_EFFECT_TICKS = 40, TIDE_SLOW_AMPLIFIER = 1;
	/** Mastered (ascended): the wave lingers as a shallow puddle you can Undertow into. */
	static final int PUDDLE_TICKS = 60;
	// ---- R: Pounce (knives, ascended) ----
	static final int POUNCE_AIM_MAX = 20, POUNCE_FLIGHT = 15, POUNCE_COOLDOWN = 120;
	/** Launch speed: about {@code POUNCE_RANGE} blocks of leap. */
	static final double POUNCE_SPEED = 1.25, POUNCE_LIFT = 0.28, POUNCE_RANGE = 6.0;
	static final float POUNCE_FACTOR = 2.0F;
	static final int POUNCE_STABS_CRIPPLED = 3;
	// ---- G: Fang Barrage (knives, ascended) ----
	static final int BARRAGE_MAX = 80, BARRAGE_EVERY = 4, BARRAGE_COOLDOWN = 360, BARRAGE_STUN = 40;
	static final float BARRAGE_DAMAGE = 2.0F, BARRAGE_FLOW = 40.0F;
	static final double BARRAGE_REACH = 2.2, BARRAGE_HOLD_AT = 1.5;
	// ---- B: Riptide Dash (knives, ascended) ----
	static final double DASH_LENGTH = 5.0, DASH_SPEED = 1.0;
	/** A dash that lands is ready again at once; one that misses waits DASH_MISS_COOLDOWN, a flurry FLURRY_COOLDOWN (ticks). */
	static final int DASH_TICKS = 5, DASH_MISS_COOLDOWN = 100, FLURRY_COOLDOWN = 1200;
	static final float DASH_DAMAGE = 6.0F, DASH_FLOW = 30.0F;
	static final int FLURRY_HITS = 8, FLURRY_EVERY = 2;
	static final float FLURRY_HIT_DAMAGE = 2.5F;

	private static final class Wave {
		final ServerPlayer owner;
		final ServerLevel level;
		final Vec3 origin;
		final Vec3 dir;
		final boolean linger;
		final Set<Integer> hit = new HashSet<>();
		int step;

		Wave(ServerPlayer owner, Vec3 origin, Vec3 dir, boolean linger) {
			this.owner = owner;
			this.level = owner.level();
			this.origin = origin;
			this.dir = dir;
			this.linger = linger;
		}
	}

	private record Puddle(ServerLevel level, AABB area, long until) {
	}

	private static final List<Wave> WAVES = new ArrayList<>();
	private static final List<Puddle> PUDDLES = new ArrayList<>();

	private DarktideMoves() {
	}

	// ---- shared helpers ---------------------------------------------------------------------------------------------

	/** What the player's weapon (or fist) hits for: their attack damage. */
	static float weapon(ServerPlayer player) {
		return (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
	}

	/** The living thing under the crosshair within {@code range} (walls block it). */
	static @Nullable LivingEntity pick(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		BlockHitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity && !e.isSpectator() && e.isPickable() && e.isAlive() && !friendly(player, e), eye.distanceToSqr(end));
		return hit == null ? null : (LivingEntity) hit.getEntity();
	}

	/**
	 * Like {@link #pick}, but a near miss still counts: the nearest living thing within {@code radius} of the crosshair's
	 * line (that you can see).
	 */
	static @Nullable LivingEntity pickWide(ServerPlayer player, double range, double radius) {
		LivingEntity exact = pick(player, range);
		if (exact != null) {
			return exact;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : around(player, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(radius))) {
			Optional<Vec3> at = e.getBoundingBox().inflate(radius).clip(eye, end);
			if (at.isEmpty() || !player.hasLineOfSight(e)) {
				continue;
			}
			double d = eye.distanceToSqr(at.get());
			if (d < bestDistance) {
				bestDistance = d;
				best = e;
			}
		}
		return best;
	}

	/** Not to be hit: yourself, your own risen shadows, what you ride. */
	static boolean friendly(ServerPlayer player, Entity entity) {
		return entity == player || entity == player.getVehicle() || DarktideTide.risenOf(entity, player);
	}

	/** One Darktide blow: the damage, the legs (cripple), the risen told. */
	static boolean hit(ServerPlayer player, LivingEntity target, float damage) {
		target.setInvulnerableTime(0);
		boolean hurt = target.hurtServer(player.level(), player.level().damageSources().playerAttack(player), damage);
		if (hurt) {
			DarktideStatus.legHit(player, target);
			DarktideTide.enemy(player, target);
		}
		return hurt;
	}

	/** A G hit: always at the legs, so a humanoid it hurts is crippled. */
	static boolean hitLegs(ServerPlayer player, LivingEntity target, float damage) {
		boolean hurt = hit(player, target, damage);
		if (hurt && target.isAlive() && DarktideStatus.humanoid(target)) {
			DarktideStatus.cripple(target);
		}
		return hurt;
	}

	private static List<LivingEntity> around(ServerPlayer player, AABB box) {
		return player.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !friendly(player, e));
	}

	private static void swing(ServerPlayer player) {
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
	}

	private static boolean readyOrSilent(ServerPlayer player, int slot) {
		return DarktidePower.ready(player, slot);
	}

	// ---- R: Dark Strike -----------------------------------------------------------------------------------------------

	/** R down: the blade goes up overhead (or the fist draws back) and the charge builds. */
	static void chargeStrike(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.strikeStart >= 0L || !readyOrSilent(player, DarktideState.STRIKE)) {
			return;
		}
		ServerLevel level = player.level();
		live.strikeStart = level.getGameTime();
		boolean fist = DarktideWeapons.kit(player) == DarktideWeapons.Kit.UNARMED;
		DarktideMove.play(player, DarktideMove.CHARGE, DarktideMove.CHARGE_HOLD_MAX, fist ? DarktideMove.CHARGE_FIST : DarktideMove.CHARGE_BLADE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	/** How far the charge has built, 0-1. */
	static float charge(DarktideLive live, long now) {
		return live.strikeStart < 0L ? 0.0F : Mth.clamp((now - live.strikeStart) / (float) DarktideMove.CHARGE_FULL_TICKS, 0.0F, 1.0F);
	}

	/** R released: the blow, as hard as the charge built. */
	static void releaseStrike(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.strikeStart < 0L) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		float charge = charge(live, now);
		live.strikeStart = -1L;
		boolean fist = DarktideWeapons.kit(player) == DarktideWeapons.Kit.UNARMED;
		boolean empowered = now < live.empoweredUntil && live.flow >= EMPOWER_FLOW;
		DarktideMove.play(player, DarktideMove.STRIKE, DarktideMove.STRIKE_TICKS, (empowered ? 1 : 0) | (fist ? 0 : 2));
		swing(player);
		LivingEntity target = fist ? pickWide(player, STRIKE_REACH, PUNCH_RADIUS) : pick(player, STRIKE_REACH);
		if (target != null) {
			float damage = fist ? Mth.lerp(charge, PUNCH_MIN, PUNCH_MAX) : weapon(player) * Mth.lerp(charge, 1.0F, STRIKE_FACTOR);
			if (empowered) {
				live.flow -= EMPOWER_FLOW;
				live.empoweredUntil = 0L;
				damage *= EMPOWERED_FACTOR / STRIKE_FACTOR;
			}
			hit(player, target, damage);
			Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
			if (empowered) {
				level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 24, 0.3, 0.4, 0.3, 0.12);
				level.sendParticles(new DustParticleOptions(0x0A0F2A, 2.0F), at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.0);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8F, 1.2F);
			} else {
				level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 6 + Math.round(12 * charge), 0.3, 0.3, 0.3, 0.2);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), charge >= 1.0F ? SoundEvents.PLAYER_ATTACK_STRONG : SoundEvents.PLAYER_ATTACK_WEAK,
			SoundSource.PLAYERS, 0.8F, 0.9F);
		DarktidePower.cooldown(player, DarktideState.STRIKE, STRIKE_COOLDOWN);
	}

	private static void tickCharge(ServerPlayer player, DarktideLive live) {
		long now = player.level().getGameTime();
		// let go without the key-up arriving (a screen opened, the power changed hands), or held too long
		if (!PowerInput.isDown(player, PowerKey.ABILITY_1) || now - live.strikeStart >= DarktideMove.CHARGE_HOLD_MAX) {
			releaseStrike(player);
			return;
		}
		if (now - live.strikeStart == DarktideMove.CHARGE_FULL_TICKS) {
			// full: a glint on the blade or fist
			Vec3 hand = player.position().add(0.0, DarktideWeapons.kit(player) == DarktideWeapons.Kit.UNARMED ? 1.0 : 2.3, 0.0);
			player.level().sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 6, 0.15, 0.15, 0.15, 0.02);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
	}

	// ---- G: Crescent / Ink Cross --------------------------------------------------------------------------------------

	static void crescent(ServerPlayer player) {
		if (!readyOrSilent(player, DarktideState.CRESCENT) || !DarktidePower.spend(player, CRESCENT_FLOW)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		double cos = Math.cos(Math.toRadians(CRESCENT_ARC / 2.0));
		List<LivingEntity> targets = around(player, player.getBoundingBox().inflate(CRESCENT_RANGE, 1.0, CRESCENT_RANGE));
		targets.removeIf(e -> {
			Vec3 to = e.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			return to.length() > CRESCENT_RANGE + e.getBbWidth() / 2.0 || to.lengthSqr() > 0.01 && to.normalize().dot(look) < cos;
		});
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		float damage = weapon(player) + CRESCENT_BONUS;
		for (int i = 0; i < Math.min(CRESCENT_TARGETS, targets.size()); i++) {
			// aimed: low takes the legs (and cripples), high goes for the neck, between for the arms
			LivingEntity target = targets.get(i);
			int part = Dismember.aimedPart(player, target);
			boolean legs = part == Dismember.RIGHT_LEG || part == Dismember.LEFT_LEG;
			Dismember.cut(player, target, part, Dismember.KATANA_CHANCE, () -> {
				if (legs) {
					hitLegs(player, target, damage);
				} else {
					hit(player, target, damage);
				}
			});
		}
		DarktideMove.play(player, DarktideMove.CRESCENT, DarktideMove.CRESCENT_TICKS, 0);
		swing(player);
		// the arc trail, at the height it's aimed
		double aimed = Mth.clamp(player.getLookAngle().y * 2.6, -player.getBbHeight() * 0.55, player.getBbHeight() * 0.45);
		Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.6 + aimed, 0.0);
		for (int i = 0; i <= 16; i++) {
			float yaw = player.getYRot() - (float) CRESCENT_ARC / 2.0F + (float) CRESCENT_ARC * i / 16.0F;
			Vec3 p = center.add(Vec3.directionFromRotation(0.0F, yaw).scale(2.6));
			level.sendParticles(new DustParticleOptions(CRESCENT_COLOR, 1.8F), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x + look.x * 1.5, center.y, center.z + look.z * 1.5, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.75F);
		DarktidePower.cooldown(player, DarktideState.CRESCENT, CRESCENT_COOLDOWN);
	}

	static void inkCross(ServerPlayer player) {
		if (!readyOrSilent(player, DarktideState.INK_CROSS)) {
			return;
		}
		LivingEntity target = pick(player, INK_CROSS_RANGE);
		if (target == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_target").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!DarktidePower.spend(player, INK_CROSS_FLOW)) {
			return;
		}
		ServerLevel level = player.level();
		hitLegs(player, target, weapon(player) * INK_CROSS_FACTOR);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, INK_CROSS_SLOW_TICKS, INK_CROSS_SLOW_AMPLIFIER), player);
		DarktideMove.play(player, DarktideMove.INK_CROSS, DarktideMove.INK_CROSS_TICKS, 0);
		swing(player);
		Vec3 mid = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
		Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
		for (int i = 0; i <= 8; i++) {
			double t = i / 8.0 - 0.5;
			for (int s = -1; s <= 1; s += 2) {
				Vec3 p = mid.add(right.scale(t * 1.2)).add(0.0, s * t * 1.2, 0.0);
				level.sendParticles(new DustParticleOptions(0x05070F, 1.4F), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, mid.x, mid.y, mid.z, 10, 0.2, 0.3, 0.2, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.3F);
		DarktidePower.cooldown(player, DarktideState.INK_CROSS, INK_CROSS_COOLDOWN);
	}

	// ---- V: Undertow --------------------------------------------------------------------------------------------------

	static void undertow(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.undertow != null || !readyOrSilent(player, DarktideState.UNDERTOW)) {
			return;
		}
		ServerLevel level = player.level();
		boolean mastered = Fate.isAscended(player);
		double range = mastered ? UNDERTOW_RANGE_MASTERED : UNDERTOW_RANGE;
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range));
		BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		Vec3 to = destination(player, level, hit);
		if (to == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.darktide.no_shadow").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!DarktidePower.spend(player, UNDERTOW_FLOW)) {
			return;
		}
		live.undertow = new DarktideLive.Undertow(to, level.getGameTime() + UNDERTOW_DELAY);
		DarktideMove.play(player, DarktideMove.SINK, UNDERTOW_DELAY, 0);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.4, 0.1, 0.4, 0.02);
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.4, 0.05, 0.4, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 0.8F, 0.7F);
		DarktidePower.cooldown(player, DarktideState.UNDERTOW, UNDERTOW_COOLDOWN);
	}

	/** Where Undertow surfaces for this aim, or null: water, a shadow, or a Black Tide puddle, with room to stand. */
	private static @Nullable Vec3 destination(ServerPlayer player, ServerLevel level, BlockHitResult hit) {
		if (hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		BlockPos pos = hit.getBlockPos();
		BlockPos feet = level.getFluidState(pos).is(FluidTags.WATER) ? pos : pos.relative(hit.getDirection());
		for (int up = 0; up <= 2; up++) {
			BlockPos at = feet.above(up);
			Vec3 spot = Vec3.atBottomCenterOf(at);
			AABB box = player.getDimensions(player.getPose()).makeBoundingBox(spot);
			if (!level.noCollision(player, box)) {
				continue;
			}
			boolean water = level.getFluidState(at).is(FluidTags.WATER) || level.getFluidState(at.below()).is(FluidTags.WATER);
			if (water || DarktideFlow.shadow(level, at) || puddle(level, spot)) {
				return spot;
			}
			return null;
		}
		return null;
	}

	private static boolean puddle(ServerLevel level, Vec3 at) {
		long now = level.getGameTime();
		for (Puddle puddle : PUDDLES) {
			if (puddle.level == level && now < puddle.until && puddle.area.inflate(0.5).contains(at)) {
				return true;
			}
		}
		return false;
	}

	private static void surface(ServerPlayer player, DarktideLive.Undertow undertow) {
		ServerLevel level = player.level();
		Vec3 to = undertow.to;
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
		level.sendParticles(ParticleTypes.SQUID_INK, to.x, to.y + 0.3, to.z, 30, 0.4, 0.2, 0.4, 0.03);
		level.sendParticles(ParticleTypes.SPLASH, to.x, to.y + 0.2, to.z, 24, 0.5, 0.1, 0.5, 0.15);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 0.7F, 0.8F);
		if (Fate.isAscended(player)) {
			for (LivingEntity near : around(player, new AABB(to, to).inflate(UNDERTOW_SURFACE_RADIUS, 1.5, UNDERTOW_SURFACE_RADIUS))) {
				hit(player, near, UNDERTOW_SURFACE_DAMAGE);
			}
		}
	}

	// ---- B: Black Tide ------------------------------------------------------------------------------------------------

	static void blackTide(ServerPlayer player) {
		if (!readyOrSilent(player, DarktideState.BLACK_TIDE) || !DarktidePower.spend(player, TIDE_FLOW)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		player.clearFire();
		WAVES.add(new Wave(player, player.position().add(dir.scale(0.5)), dir, Fate.isAscended(player)));
		DarktideMove.play(player, DarktideMove.CAST, DarktideMove.CAST_TICKS, 0);
		swing(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.4F, 1.6F);
		DarktidePower.cooldown(player, DarktideState.BLACK_TIDE, TIDE_COOLDOWN);
	}

	private static boolean tickWave(Wave wave) {
		wave.step++;
		ServerLevel level = wave.level;
		Vec3 side = new Vec3(-wave.dir.z, 0.0, wave.dir.x);
		Vec3 front = wave.origin.add(wave.dir.scale(wave.step));
		// the crest: a line of dark water across the front
		for (int i = 0; i <= 10; i++) {
			double t = (i / 10.0 - 0.5) * TIDE_WIDTH;
			Vec3 p = front.add(side.scale(t));
			level.sendParticles(ParticleTypes.SPLASH, p.x, p.y + 0.4, p.z, 3, 0.15, 0.3, 0.15, 0.2);
			level.sendParticles(new DustParticleOptions(0x0A1838, 1.8F), p.x, p.y + 0.3 + level.getRandom().nextDouble() * 1.2, p.z, 1, 0.1, 0.2, 0.1, 0.0);
			if (i % 2 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y + 0.6, p.z, 1, 0.1, 0.2, 0.1, 0.01);
			}
			// fires in the path go out
			BlockPos block = BlockPos.containing(p.x, p.y + 0.2, p.z);
			for (int dy = -1; dy <= 1; dy++) {
				if (level.getBlockState(block.above(dy)).is(Blocks.FIRE)) {
					level.removeBlock(block.above(dy), false);
				}
			}
		}
		AABB crest = new AABB(front, front).inflate(TIDE_WIDTH / 2.0 + 0.5, 0.0, TIDE_WIDTH / 2.0 + 0.5).expandTowards(0.0, TIDE_HEIGHT, 0.0)
			.expandTowards(wave.dir.scale(-1.2));
		if (!wave.owner.isRemoved()) {
			for (LivingEntity target : around(wave.owner, crest)) {
				// carried along while the crest passes
				target.setDeltaMovement(target.getDeltaMovement().multiply(0.5, 1.0, 0.5).add(wave.dir.scale(TIDE_PUSH)).add(0.0, 0.05, 0.0));
				target.syncVelocity = true;
				target.clearFire();
				if (wave.hit.add(target.getId())) {
					hit(wave.owner, target, TIDE_DAMAGE);
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TIDE_EFFECT_TICKS, TIDE_SLOW_AMPLIFIER), wave.owner);
					target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, TIDE_EFFECT_TICKS, 0), wave.owner);
				}
			}
		}
		if (wave.step % 3 == 0) {
			level.playSound(null, front.x, front.y, front.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.6F, 0.7F);
		}
		if (wave.step >= TIDE_LENGTH) {
			if (wave.linger) {
				Vec3 a = wave.origin.add(side.scale(TIDE_WIDTH / 2.0));
				Vec3 b = front.add(side.scale(-TIDE_WIDTH / 2.0));
				PUDDLES.add(new Puddle(level, new AABB(a, b).inflate(0.0, 1.0, 0.0), level.getGameTime() + PUDDLE_TICKS));
			}
			return true;
		}
		return false;
	}

	// ---- R (knives): Pounce -------------------------------------------------------------------------------------------

	static void pounce(ServerPlayer player, boolean down) {
		DarktideLive live = DarktidePower.live(player);
		if (down) {
			if (live.pounce != null || !readyOrSilent(player, DarktideState.POUNCE)) {
				return;
			}
			live.pounce = new DarktideLive.Pounce(player.level().getGameTime());
			DarktideMove.play(player, DarktideMove.POUNCE_AIM, POUNCE_AIM_MAX + POUNCE_FLIGHT, 0);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
		} else if (live.pounce != null && live.pounce.launched < 0) {
			launch(player, live);
		}
	}

	private static void launch(ServerPlayer player, DarktideLive live) {
		DarktideLive.Pounce pounce = live.pounce;
		long now = player.level().getGameTime();
		pounce.launched = now;
		pounce.empowered = now < live.empoweredUntil;
		Vec3 look = player.getLookAngle();
		player.setDeltaMovement(look.scale(POUNCE_SPEED).add(0.0, POUNCE_LIFT, 0.0));
		player.syncVelocity = true;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.6F);
		DarktidePower.cooldown(player, DarktideState.POUNCE, POUNCE_COOLDOWN);
	}

	private static void tickPounce(ServerPlayer player, DarktideLive live) {
		DarktideLive.Pounce pounce = live.pounce;
		long now = player.level().getGameTime();
		if (pounce.launched < 0) {
			if (now - pounce.start >= POUNCE_AIM_MAX) {
				launch(player, live);
			}
			return;
		}
		long flying = now - pounce.launched;
		if (flying > POUNCE_FLIGHT || flying > 3 && player.onGround()) {
			live.pounce = null;
			DarktideMove.clear(player);
			return;
		}
		List<LivingEntity> touched = around(player, player.getBoundingBox().inflate(0.7));
		if (touched.isEmpty()) {
			return;
		}
		touched.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		LivingEntity target = touched.getFirst();
		ServerLevel level = player.level();
		// onto its back, and stab down
		player.teleportTo(level, target.getX(), target.getBoundingBox().maxY, target.getZ(), Set.of(), player.getYRot(), 60.0F, false);
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		boolean three = DarktideStatus.humanoid(target) && DarktideStatus.crippled(target);
		int stabs = three ? POUNCE_STABS_CRIPPLED : 1;
		float factor = POUNCE_FACTOR;
		if (pounce.empowered && live.flow >= EMPOWER_FLOW) {
			live.flow -= EMPOWER_FLOW;
			live.empoweredUntil = 0L;
			factor = EMPOWERED_FACTOR;
		}
		for (int i = 0; i < stabs && target.isAlive(); i++) {
			hit(player, target, weapon(player) * factor);
		}
		Vec3 at = target.position().add(0.0, target.getBbHeight(), 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 16 * stabs, 0.3, 0.2, 0.3, 0.08);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 10 * stabs, 0.3, 0.2, 0.3, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 1.0F, 0.8F);
		DarktideMove.play(player, DarktideMove.POUNCE_STAB, DarktideMove.POUNCE_STAB_TICKS, stabs);
		live.pounce = null;
	}

	// ---- G (knives): Fang Barrage -------------------------------------------------------------------------------------

	static void barrage(ServerPlayer player, boolean down) {
		DarktideLive live = DarktidePower.live(player);
		if (down) {
			if (live.barrage != null || !readyOrSilent(player, DarktideState.BARRAGE) || !DarktidePower.spend(player, BARRAGE_FLOW)) {
				return;
			}
			live.barrage = new DarktideLive.Barrage(player.level().getGameTime());
			DarktideMove.play(player, DarktideMove.BARRAGE, BARRAGE_MAX, 0);
		} else if (live.barrage != null) {
			endBarrage(player, live);
		}
	}

	private static void tickBarrage(ServerPlayer player, DarktideLive live) {
		DarktideLive.Barrage barrage = live.barrage;
		ServerLevel level = player.level();
		long t = level.getGameTime() - barrage.start;
		if (t >= BARRAGE_MAX || !PowerInput.isDown(player, PowerKey.ABILITY_2) || DarktideWeapons.kit(player) != DarktideWeapons.Kit.KNIFE) {
			endBarrage(player, live);
			return;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		Vec3 ahead = player.position().add(look.scale(BARRAGE_HOLD_AT));
		if (t % BARRAGE_EVERY == 0) {
			AABB reach = new AABB(ahead, ahead).inflate(BARRAGE_REACH / 2.0 + 0.6, 1.2, BARRAGE_REACH / 2.0 + 0.6).expandTowards(0.0, 1.0, 0.0);
			for (LivingEntity target : around(player, reach)) {
				barrage.held.add(target.getId());
			}
			for (int id : barrage.held) {
				if (level.getEntity(id) instanceof LivingEntity target && target.isAlive()) {
					hitLegs(player, target, BARRAGE_DAMAGE);
				}
			}
			swing(player);
			Vec3 p = ahead.add(0.0, 1.2, 0.0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.3, 0.3, 0.3, 0.0);
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 4, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.7F, 1.4F + level.getRandom().nextFloat() * 0.3F);
		}
		// held in place in front of you
		int n = 0;
		for (int id : barrage.held) {
			if (level.getEntity(id) instanceof LivingEntity target && target.isAlive()) {
				Vec3 side = new Vec3(-look.z, 0.0, look.x).scale((n % 3 - 1) * 0.9);
				Vec3 want = ahead.add(look.scale(0.4)).add(side);
				Vec3 pull = want.subtract(target.position()).multiply(0.5, 0.0, 0.5);
				target.setDeltaMovement(pull.x, Math.min(0.0, target.getDeltaMovement().y), pull.z);
				target.syncVelocity = true;
				n++;
			}
		}
	}

	private static void endBarrage(ServerPlayer player, DarktideLive live) {
		DarktideLive.Barrage barrage = live.barrage;
		live.barrage = null;
		for (int id : barrage.held) {
			if (player.level().getEntity(id) instanceof LivingEntity target && target.isAlive()) {
				DarktideStatus.stun(target, BARRAGE_STUN);
			}
		}
		DarktideMove.clear(player);
		DarktidePower.cooldown(player, DarktideState.BARRAGE, BARRAGE_COOLDOWN);
	}

	// ---- B (knives): Riptide Dash -------------------------------------------------------------------------------------

	static void dash(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		if (live.dash != null || !readyOrSilent(player, DarktideState.DASH) || !DarktidePower.spend(player, DASH_FLOW)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 dir = Vec3.directionFromRotation(0.0F, player.getYRot());
		DarktideLive.Dash dash = new DarktideLive.Dash(level.getGameTime(), player.position(), dir);
		if (PowerInput.isDown(player, PowerKey.ABILITY_1)) {
			LivingEntity first = pick(player, DASH_LENGTH);
			if (first != null) {
				dash.target = first.getId();
				DarktideStatus.stun(first, FLURRY_HITS * FLURRY_EVERY + 6);
				Vec3 front = first.position().subtract(dir.scale(first.getBbWidth() / 2.0 + 0.8));
				player.teleportTo(level, front.x, first.getY(), front.z, Set.of(), player.getYRot(), player.getXRot(), false);
				DarktideMove.play(player, DarktideMove.FLURRY, FLURRY_HITS * FLURRY_EVERY + 2, 0);
				DarktidePower.cooldown(player, DarktideState.DASH, FLURRY_COOLDOWN);
			}
		}
		if (dash.target < 0) {
			DarktideMove.play(player, DarktideMove.DASH, DASH_TICKS + 2, 0);
		}
		live.dash = dash;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
	}

	private static void tickDash(ServerPlayer player, DarktideLive live) {
		DarktideLive.Dash dash = live.dash;
		ServerLevel level = player.level();
		long t = level.getGameTime() - dash.start;
		if (dash.target >= 0) {
			Entity entity = level.getEntity(dash.target);
			if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
				live.dash = null;
				return;
			}
			if (t % FLURRY_EVERY == 0 && dash.hits < FLURRY_HITS) {
				dash.hits++;
				swing(player);
				Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
				level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFCFE8FF), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.3F + dash.hits * 0.05F);
				DarktideNet.fx(player, target, new DarktideNet.Fx(DarktideNet.Fx.HIT, player.getId(), target.getId()));
			}
			if (dash.hits >= FLURRY_HITS && t >= FLURRY_HITS * FLURRY_EVERY + 2) {
				// out behind it, and only now does it feel the cuts
				Vec3 behind = target.position().add(dash.dir.scale(target.getBbWidth() / 2.0 + 1.0));
				player.teleportTo(level, behind.x, target.getY(), behind.z, Set.of(), player.getYRot() + 180.0F, player.getXRot(), false);
				hit(player, target, FLURRY_HIT_DAMAGE * FLURRY_HITS);
				live.dash = null;
			}
			return;
		}
		if (t > DASH_TICKS) {
			player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 1.0, 0.3));
			player.syncVelocity = true;
			live.dash = null;
			if (dash.hit.isEmpty()) {
				// a miss: the dash waits; one that lands is ready again at once
				DarktidePower.cooldown(player, DarktideState.DASH, DASH_MISS_COOLDOWN);
			}
			return;
		}
		player.setDeltaMovement(dash.dir.scale(DASH_SPEED).add(0.0, Math.max(0.0, player.getDeltaMovement().y), 0.0));
		player.syncVelocity = true;
		level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.8, player.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
		for (LivingEntity target : around(player, player.getBoundingBox().inflate(0.9, 0.3, 0.9))) {
			if (dash.hit.add(target.getId())) {
				hit(player, target, DASH_DAMAGE);
			}
		}
	}

	// ---- ticking ------------------------------------------------------------------------------------------------------

	static void tick(ServerPlayer player, DarktideLive live) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (live.undertow != null && now >= live.undertow.at) {
			DarktideLive.Undertow undertow = live.undertow;
			live.undertow = null;
			surface(player, undertow);
		}
		if (live.strikeStart >= 0L) {
			tickCharge(player, live);
		}
		if (live.pounce != null) {
			tickPounce(player, live);
		}
		if (live.barrage != null) {
			tickBarrage(player, live);
		}
		if (live.dash != null) {
			tickDash(player, live);
		}
		// empowered: the hands (or blade) wreathed in dark energy, for everyone to see
		if (now < live.empoweredUntil && now % 3 == 0) {
			Vec3 hand = player.position().add(0.0, 1.0, 0.0).add(Vec3.directionFromRotation(0.0F, player.getYRot() + 40.0F).scale(0.45));
			level.sendParticles(new DustParticleOptions(0x0B0820, 1.2F), hand.x, hand.y, hand.z, 3, 0.1, 0.15, 0.1, 0.0);
			level.sendParticles(ParticleTypes.SQUID_INK, hand.x, hand.y, hand.z, 1, 0.05, 0.1, 0.05, 0.0);
		}
	}

	static void tickWorld(MinecraftServer server) {
		WAVES.removeIf(DarktideMoves::tickWave);
		if (!PUDDLES.isEmpty()) {
			for (Iterator<Puddle> it = PUDDLES.iterator(); it.hasNext(); ) {
				Puddle puddle = it.next();
				long now = puddle.level.getGameTime();
				if (now >= puddle.until) {
					it.remove();
					continue;
				}
				if (now % 4 == 0) {
					AABB a = puddle.area;
					var random = puddle.level.getRandom();
					for (int i = 0; i < 4; i++) {
						double x = a.minX + random.nextDouble() * a.getXsize();
						double z = a.minZ + random.nextDouble() * a.getZsize();
						puddle.level.sendParticles(ParticleTypes.SPLASH, x, a.minY + 1.05, z, 1, 0.0, 0.0, 0.0, 0.0);
						puddle.level.sendParticles(new DustParticleOptions(0x0A1838, 1.0F), x, a.minY + 1.02, z, 1, 0.1, 0.0, 0.1, 0.0);
					}
				}
			}
		}
	}

	/** Stops whatever move is running (no cooldown refund). */
	static void cancel(ServerPlayer player) {
		DarktideLive live = DarktidePower.liveIfAny(player);
		if (live == null) {
			return;
		}
		live.undertow = null;
		live.strikeStart = -1L;
		live.pounce = null;
		live.dash = null;
		if (live.barrage != null) {
			endBarrage(player, live);
		}
		WAVES.removeIf(w -> w.owner == player);
	}

	static boolean moving(DarktideLive live) {
		return live.dash != null || live.pounce != null && live.pounce.launched >= 0 || live.undertow != null;
	}

	static void clear() {
		WAVES.clear();
		PUDDLES.clear();
	}
}
