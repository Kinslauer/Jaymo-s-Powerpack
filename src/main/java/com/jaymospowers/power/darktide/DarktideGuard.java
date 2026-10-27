package com.jaymospowers.power.darktide;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The guard (right click held: unarmed, a katana or a knife), its parry, the katana's riposte and the blade's reflect.
 * <ul>
 * <li>Blocks {@link #BLOCK_SHARE} of melee and projectile damage from the front {@link #FRONT_DEGREES}° ({@link #STARTER_BLOCK_SHARE}
 * fresh); never falls, fire, magic, explosions or anything from behind. Each blocked hit costs {@link #FLOW_PER_DAMAGE} × its
 * damage in Flow; one that empties Flow breaks the guard.</li>
 * <li>A hit in the first {@link #PARRY_TICKS} of raising it is parried: nothing taken, nothing spent, +{@link #PARRY_FLOW}
 * Flow, the next R empowered. Then {@link #PARRY_COOLDOWN} before the next.</li>
 * <li>Katana parry, R unlocked: humanoids reel and are stunned, and you answer with a slash (the riposte).</li>
 * <li>Katana or knife guard: projectiles fly back at their shooter ({@link #reflectAim} / {@link #reflectSpeed} grow with
 * mastery). Fire charges, shulker bullets and magic only once ascended.</li>
 * </ul>
 * The damage itself is cut in {@code DarktideBlockMixin} (LivingEntity.applyItemBlocking, so vanilla treats it like a
 * shield block); the bounce in {@code DarktideDeflectMixin} (Entity.deflection).
 */
public final class DarktideGuard {
	public static final double FRONT_DEGREES = 150.0;
	static final float BLOCK_SHARE = 0.75F;
	static final float STARTER_BLOCK_SHARE = 0.5F;
	static final float FLOW_PER_DAMAGE = 2.0F;
	/** A reflected projectile's Flow cost (its damage isn't known before it lands). */
	static final float REFLECT_FLOW = 6.0F;
	public static final int PARRY_TICKS = 8;
	static final int PARRY_COOLDOWN = 20;
	static final float PARRY_FLOW = 8.0F;
	/** How long a parry empowers the next R. */
	static final int EMPOWER_TICKS = 80;
	/** Walk speed while guarding: unarmed or knives / with a katana. */
	static final double GUARD_SPEED = 0.35, GUARD_SPEED_KATANA = 0.75;
	/** Guard break: staggered (stunned) for this long, and no guard for this long. */
	static final int STAGGER_TICKS = 12, BREAK_LOCK_TICKS = 40;
	/** The riposte: stun on a humanoid, damage on top of the katana's, shove for anything else. */
	static final int RIPOSTE_STUN_TICKS = 24;
	static final float RIPOSTE_DAMAGE = 6.0F;
	static final double PARRY_KNOCKBACK = 0.9;
	/** Reflect at mastery level 1 and 4 (in between, evenly): aim (how tightly at the shooter's chest) and speed kept. */
	static final double REFLECT_AIM_MIN = 0.2, REFLECT_AIM_MAX = 0.7;
	static final double REFLECT_SPEED_MIN = 0.3, REFLECT_SPEED_MAX = 0.9;
	/** At the worst aim, how far off (radians) a reflection may go. */
	static final double REFLECT_MAX_SPREAD = 0.6;

	private static final Identifier GUARD_SPEED_ID = JaymosPowers.id("darktide_guard_speed");

	/** Reflections waiting for the end of the tick, when their new owner and course are set for good. */
	private record Pending(Projectile projectile, ServerPlayer by, Vec3 velocity) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private DarktideGuard() {
	}

	// ---- the guard ----------------------------------------------------------------------------------------------------

	static void setGuard(ServerPlayer player, boolean up) {
		if (!DarktidePower.has(player)) {
			return;
		}
		DarktideLive live = DarktidePower.live(player);
		if (!up) {
			drop(player);
			return;
		}
		long now = player.level().getGameTime();
		if (live.guarding || !player.isAlive() || player.isSpectator() || now < live.guardLockedUntil || !DarktideWeapons.canGuard(player)
			|| DarktideStatus.stunned(player) || live.gedan != null) {
			return;
		}
		live.guarding = true;
		live.guardStart = now;
		player.setAttached(DarktideState.GUARD, now);
		speed(player);
	}

	static void drop(ServerPlayer player) {
		DarktideLive live = DarktidePower.liveIfAny(player);
		if (live != null) {
			live.guarding = false;
		}
		player.removeAttached(DarktideState.GUARD);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(GUARD_SPEED_ID);
		}
	}

	private static void speed(ServerPlayer player) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			double factor = DarktideWeapons.kit(player) == DarktideWeapons.Kit.KATANA ? GUARD_SPEED_KATANA : GUARD_SPEED;
			speed.addOrUpdateTransientModifier(new AttributeModifier(GUARD_SPEED_ID, factor - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	static void tick(ServerPlayer player, DarktideLive live) {
		if (!live.guarding) {
			return;
		}
		if (!DarktideWeapons.canGuard(player) || DarktideStatus.stunned(player) || live.gedan != null) {
			drop(player);
			return;
		}
		if (player.tickCount % 10 == 0) {
			speed(player);
		}
	}

	public static boolean guarding(Entity entity) {
		return entity.getAttached(DarktideState.GUARD) != null;
	}

	/** Whether {@code from} is in front of {@code player}, within the guard's arc. */
	static boolean inFront(ServerPlayer player, @Nullable Vec3 from) {
		if (from == null) {
			return false;
		}
		Vec3 to = from.subtract(player.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		double cos = to.normalize().dot(look);
		return cos >= Math.cos(Math.toRadians(FRONT_DEGREES / 2.0));
	}

	private static boolean parryWindow(ServerPlayer player, DarktideLive live) {
		long now = player.level().getGameTime();
		return now - live.guardStart <= PARRY_TICKS && now >= live.parryReadyAt;
	}

	// ---- blocking (DarktideBlockMixin) --------------------------------------------------------------------------------

	/**
	 * How much of {@code damage} the guard takes, or -1 when it has nothing to do with this hit (vanilla's own shield
	 * logic then runs as usual).
	 */
	public static float blocked(LivingEntity self, ServerLevel level, DamageSource source, float damage) {
		if (!(self instanceof ServerPlayer player) || !DarktidePower.has(player) || damage <= 0.0F) {
			return -1.0F;
		}
		DarktideLive live = DarktidePower.live(player);
		if (!live.guarding) {
			return -1.0F;
		}
		boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
		Entity direct = source.getDirectEntity();
		boolean melee = !projectile && direct instanceof LivingEntity && direct == source.getEntity();
		if (!projectile && !melee || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.IS_EXPLOSION)
			|| source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.WITCH_RESISTANT_TO) || !projectile && source.is(DamageTypeTags.IS_FIRE)) {
			return -1.0F;
		}
		if (!inFront(player, source.getSourcePosition())) {
			return -1.0F;
		}
		// a blade turns arrows back at their shooter (those that came through the damage path, like WeaponMod's bullets)
		if (projectile && direct instanceof Projectile shot && blade(player) && reflectable(player, shot)) {
			if (parryWindow(player, live)) {
				parry(player, null);
			} else if (!payOrBreak(player, live, REFLECT_FLOW, damage)) {
				return 0.0F;
			}
			queueReflection(player, shot);
			return damage;
		}
		if (parryWindow(player, live)) {
			parry(player, melee ? (LivingEntity) direct : null);
			return damage;
		}
		float share = Mastery.starterOnly(player) ? STARTER_BLOCK_SHARE : BLOCK_SHARE;
		float cost = FLOW_PER_DAMAGE * damage;
		if (live.flow >= cost) {
			live.flow -= cost;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
			level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
			return damage * share;
		}
		// this hit empties Flow: what Flow could pay for is blocked, the rest goes through, and the guard breaks
		float covered = live.flow / FLOW_PER_DAMAGE;
		live.flow = 0.0F;
		breakGuard(player);
		return Math.min(damage, covered) * share;
	}

	/** Pays a block's Flow; with too little, the guard breaks instead (returns false). */
	private static boolean payOrBreak(ServerPlayer player, DarktideLive live, float cost, float damage) {
		if (live.flow >= cost) {
			live.flow -= cost;
			return true;
		}
		live.flow = 0.0F;
		breakGuard(player);
		return false;
	}

	static void breakGuard(ServerPlayer player) {
		DarktideLive live = DarktidePower.live(player);
		drop(player);
		live.guardLockedUntil = player.level().getGameTime() + BREAK_LOCK_TICKS;
		DarktideStatus.stun(player, STAGGER_TICKS);
		DarktideMove.play(player, DarktideMove.STAGGER, DarktideMove.STAGGER_TICKS, 0);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
		player.level().sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.2, player.getZ(), 16, 0.3, 0.4, 0.3, 0.08);
		com.jaymospowers.core.Shakes.send(player, 0.8F);
	}

	/** A parry: nothing taken or spent, Flow gained, the next R empowered; a katana answers. */
	private static void parry(ServerPlayer player, @Nullable LivingEntity attacker) {
		DarktideLive live = DarktidePower.live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		live.parryReadyAt = now + PARRY_COOLDOWN;
		DarktidePower.addFlow(player, PARRY_FLOW);
		live.empoweredUntil = now + EMPOWER_TICKS;
		Mastery.used(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.8F, 1.4F);
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.8));
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y - 0.3, at.z, 12, 0.2, 0.2, 0.2, 0.3);
		level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y - 0.3, at.z, 10, 0.25, 0.25, 0.25, 0.05);
		boolean katana = DarktideWeapons.kit(player) == DarktideWeapons.Kit.KATANA;
		if (katana) {
			DarktidePower.countParry(player);
		}
		if (attacker == null || !attacker.isAlive()) {
			DarktideNet.fx(player, player, new DarktideNet.Fx(DarktideNet.Fx.PARRY, player.getId(), -1));
			return;
		}
		if (katana && Mastery.isUnlocked(player, PowerKey.ABILITY_1)) {
			if (DarktideStatus.humanoid(attacker)) {
				DarktideStatus.stun(attacker, RIPOSTE_STUN_TICKS);
				DarktideStatus.reel(attacker, player.position());
				riposte(player, attacker);
			} else {
				shove(player, attacker, PARRY_KNOCKBACK * 1.6);
				DarktideNet.fx(player, player, new DarktideNet.Fx(DarktideNet.Fx.PARRY, player.getId(), attacker.getId()));
			}
		} else {
			shove(player, attacker, PARRY_KNOCKBACK);
			DarktideNet.fx(player, player, new DarktideNet.Fx(DarktideNet.Fx.PARRY, player.getId(), attacker.getId()));
		}
	}

	/** The katana's answer: an instant slash from the shoulder across the body, a flash, a streak of darkness, a freeze frame. */
	private static void riposte(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		DarktideMove.play(player, DarktideMove.RIPOSTE, DarktideMove.RIPOSTE_TICKS, 0);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		float damage = RIPOSTE_DAMAGE + (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
		target.setInvulnerableTime(0);
		target.hurtServer(level, level.damageSources().playerAttack(player), damage);
		DarktideStatus.legHit(player, target);
		Vec3 mid = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), mid.x, mid.y, mid.z, 1, 0.0, 0.0, 0.0, 0.0);
		// the streak: a diagonal line of ink from the shoulder down across
		Vec3 right = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
		for (int i = 0; i <= 10; i++) {
			double t = i / 10.0 - 0.5;
			Vec3 p = mid.add(right.scale(t * 1.6)).add(0.0, -t * 1.2, 0.0);
			level.sendParticles(new DustParticleOptions(0x0A1430, 1.6F), p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.0);
			level.sendParticles(ParticleTypes.SQUID_INK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
		DarktideNet.fx(player, player, new DarktideNet.Fx(DarktideNet.Fx.RIPOSTE, player.getId(), target.getId()));
	}

	static void shove(Entity from, LivingEntity target, double strength) {
		Vec3 away = target.position().subtract(from.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			return;
		}
		away = away.normalize().scale(strength);
		target.setDeltaMovement(target.getDeltaMovement().add(away.x, 0.25, away.z));
		target.syncVelocity = true;
	}

	// ---- reflect (DarktideDeflectMixin) -------------------------------------------------------------------------------

	private static boolean blade(ServerPlayer player) {
		DarktideWeapons.Kit kit = DarktideWeapons.kit(player);
		return kit == DarktideWeapons.Kit.KATANA || kit == DarktideWeapons.Kit.KNIFE;
	}

	/** Fire charges, shulker bullets, potions and other powers' projectiles are magic: only an ascended guard sends them back. */
	static boolean reflectable(ServerPlayer player, Projectile projectile) {
		if (Fate.isAscended(player)) {
			return true;
		}
		boolean magic = projectile instanceof AbstractHurtingProjectile || projectile instanceof ShulkerBullet
			|| projectile instanceof AbstractThrownPotion
			|| JaymosPowers.MOD_ID.equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(projectile.getType()).getNamespace());
		return !magic;
	}

	/**
	 * Entity.deflection for a player: a guarding Darktide blade sends {@code projectile} back (or null: the usual path,
	 * where the guard still blocks it).
	 */
	public static @Nullable ProjectileDeflection deflection(Entity self, Projectile projectile) {
		if (!(self instanceof ServerPlayer player) || !DarktidePower.has(player) || projectile.getOwner() == player) {
			return null;
		}
		DarktideLive live = DarktidePower.live(player);
		if (!live.guarding || !blade(player) || !reflectable(player, projectile) || !inFront(player, projectile.position())) {
			return null;
		}
		if (parryWindow(player, live)) {
			parry(player, null);
		} else if (!payOrBreak(player, live, REFLECT_FLOW, REFLECT_FLOW)) {
			return null;
		}
		Vec3 velocity = reflectVelocity(player, projectile);
		PENDING.add(new Pending(projectile, player, velocity));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.8F, 1.6F);
		return (p, entity, random, power) -> {
			p.setDeltaMovement(velocity);
			p.needsSync = true;
		};
	}

	private static void queueReflection(ServerPlayer player, Projectile projectile) {
		PENDING.add(new Pending(projectile, player, reflectVelocity(player, projectile)));
	}

	/** Mastery level (1-4) → aim and speed: {@code AIM_MIN..MAX}, {@code SPEED_MIN..MAX}. */
	static double reflectAim(ServerPlayer player) {
		int level = Math.max(1, Mastery.unlocked(player));
		return REFLECT_AIM_MIN + (REFLECT_AIM_MAX - REFLECT_AIM_MIN) * (level - 1) / 3.0;
	}

	static double reflectSpeed(ServerPlayer player) {
		int level = Math.max(1, Mastery.unlocked(player));
		return REFLECT_SPEED_MIN + (REFLECT_SPEED_MAX - REFLECT_SPEED_MIN) * (level - 1) / 3.0;
	}

	private static Vec3 reflectVelocity(ServerPlayer player, Projectile projectile) {
		Vec3 incoming = projectile.getDeltaMovement();
		double speed = Math.max(0.6, incoming.length()) * reflectSpeed(player);
		Entity shooter = projectile.getOwner();
		Vec3 dir;
		if (shooter != null && shooter != player && shooter.isAlive()) {
			Vec3 chest = shooter.position().add(0.0, shooter.getBbHeight() * 0.6, 0.0);
			dir = chest.subtract(projectile.position()).normalize();
		} else {
			dir = incoming.lengthSqr() > 1.0E-6 ? incoming.normalize().scale(-1.0) : player.getLookAngle();
		}
		double spread = (1.0 - reflectAim(player)) * REFLECT_MAX_SPREAD;
		var random = player.getRandom();
		dir = dir.add((random.nextDouble() - 0.5) * 2.0 * spread, (random.nextDouble() - 0.5) * spread, (random.nextDouble() - 0.5) * 2.0 * spread)
			.normalize();
		// a touch of lift so arrows reach a distant shooter
		return dir.scale(speed).add(0.0, 0.05, 0.0);
	}

	static void tickReflections() {
		if (PENDING.isEmpty()) {
			return;
		}
		for (Pending pending : PENDING) {
			Projectile p = pending.projectile;
			if (p.isRemoved()) {
				continue;
			}
			p.setOwner(pending.by);
			p.setDeltaMovement(pending.velocity);
			p.needsSync = true;
			p.syncVelocity = true;
		}
		PENDING.clear();
	}

	// ---- damage events ------------------------------------------------------------------------------------------------

	/** Gedan's hold parries, the sunken can't be hurt; a hit during a roll's window spoils the evasion. */
	static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !DarktidePower.has(player)) {
			return true;
		}
		DarktideLive live = DarktidePower.live(player);
		if (live.gedan != null) {
			if (live.gedan.released >= 0) {
				return false;
			}
			if (source.getEntity() instanceof LivingEntity attacker && attacker != player && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				DarktideGedan.parry(player, live, attacker);
				return false;
			}
		}
		if (live.dash != null && live.dash.target >= 0) {
			// mid-flurry: untouchable, like the sunken
			return false;
		}
		if (live.rollStart >= 0 && source.getEntity() != null) {
			live.rollHit = true;
		}
		return true;
	}

	/** A Darktide user's own melee hit can cripple (aimed at the legs); a hit on them calls their risen shadows. */
	static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (source.getEntity() instanceof ServerPlayer player && DarktidePower.has(player) && entity != player) {
			if (source.getDirectEntity() == player && taken > 0.0F) {
				DarktideStatus.legHit(player, entity);
			}
			DarktideTide.enemy(player, entity);
		}
		if (entity instanceof ServerPlayer player && DarktidePower.has(player) && source.getEntity() instanceof LivingEntity attacker && attacker != player) {
			DarktideTide.enemy(player, attacker);
		}
	}
}
