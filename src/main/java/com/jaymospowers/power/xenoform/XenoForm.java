package com.jaymospowers.power.xenoform;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One alien the Xeno-Band can turn you into. A form is a body (size, health, the strength of its fists, its speed) and
 * four moves on R G V B, with its own cooldowns; the band ({@link XenoformPower}) decides when you are in it and when
 * you turn back. Each form is one class under {@code forms/}; {@link XenoForms} lists them, in dial order.
 *
 * <p>Every form's name and lines are lang {@code xenoform.jaymospowers.<id>} (the name), {@code .desc}, and
 * {@code .key.ability_1}-{@code .key.ability_4} (its moves), in {@code lang/parts/xenoform.json}.
 */
public abstract class XenoForm {
	/** Cooldown slots, one per key (R G V B). */
	public static final int R = 0, G = 1, V = 2, B = 3;

	public final String id;
	/** Drawn and hit this many times the player's size ({@code Attributes.SCALE}). */
	public final float scale;
	/** The form's own life bar: your max health while in it. */
	public final float health;
	/** What its bare fists hit for (the player's attack damage while in it; a human's is 1). */
	public final double damage;
	/** Movement speed, as a share of the player's own (+0.6 = 60% faster; 0 = the same). */
	public final double speed;
	/** Knockback resistance (0-1), and how much higher it can step than a human (blocks). */
	public final double knockbackResist;
	public final double stepUp;
	/** The HUD and dial draw this item as its icon. */
	public final Item icon;
	/** Its accent colour. */
	public final int color;

	protected XenoForm(String id, float scale, float health, double damage, double speed, double knockbackResist, double stepUp, Item icon, int color) {
		this.id = id;
		this.scale = scale;
		this.health = health;
		this.damage = damage;
		this.speed = speed;
		this.knockbackResist = knockbackResist;
		this.stepUp = stepUp;
		this.icon = icon;
		this.color = color;
	}

	/** Whether {@code creature} carries this alien's DNA: scanning it (hold X at it) unlocks the form. */
	public abstract boolean scans(Entity creature);

	/** A form no creature carries (the void alien, {@link XenoVoices}): not counted toward a complete band. */
	public boolean hidden() {
		return false;
	}

	public Component name() {
		return Component.translatable("xenoform.jaymospowers." + id);
	}

	/** The form just took the player's shape. */
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
	}

	/** The player is turning back (the timer ran out, a knockout, by hand, or a relog): undo anything of the form's. */
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
	}

	/** Every server tick while the player is this alien. */
	public void tick(ServerPlayer player, XenoformPower.Live live) {
	}

	/** R G V B while the player is this alien. */
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
	}

	/** The player, as this alien, just hurt {@code target} (a punch or a move). */
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
	}

	// ---- the Ultimate (stage 3): C, once the power has ascended ---------------------------------------------------------

	/** The Ultimate form's name: lang {@code xenoform.jaymospowers.<id>.ultimate}. */
	public Component ultimateName() {
		return Component.translatable("xenoform.jaymospowers." + id + ".ultimate");
	}

	/**
	 * C while the player is this alien and the power is ascended: become the Ultimate for the rest of the timer. Return
	 * false if it can't start now (nothing to possess, say); the band then stays as it was.
	 */
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		return false;
	}

	/** C again while already the Ultimate (Swarm swaps bodies, Standstill starts again, ...). */
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
	}

	/** Every server tick while the Ultimate holds (after {@link #tick}). */
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
	}

	/** The Ultimate ends (the form is ending): undo anything of its own. {@link #onExit} follows. */
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
	}

	/**
	 * Every tick while the band is corrupted and this alien is out. The corruption is the wrong alien on a slam (and
	 * the band changing you on its own); no alien adds a twist of its own yet.
	 */
	public void corrupt(ServerPlayer player, XenoformPower.Live live) {
	}

	/** True while the form may hang in the air without the server's floating kick. */
	/** How it's flying this tick (a {@code XenoformState.FLY_*}), synced so everyone's client poses it; none by default. */
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return XenoformState.FLY_NONE;
	}

	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return false;
	}

	/** True while the form moves faster than the server's "moved too quickly" check allows. */
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return false;
	}

	/** Whether this blow lands at all (false: the form shrugs it off). */
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return true;
	}

	// ---- helpers for the forms --------------------------------------------------------------------------------------

	/** Whether the move in {@code slot} is off cooldown. */
	protected static boolean ready(ServerPlayer player, XenoformPower.Live live, int slot) {
		return live.cooldownUntil[slot] <= player.level().getGameTime();
	}

	/** Whether the Ultimate's own move (see {@code live.ultimateNext}) is off cooldown. */
	protected static boolean ultimateReady(ServerPlayer player, XenoformPower.Live live) {
		return live.ultimateNext <= player.level().getGameTime();
	}

	protected static void ultimateCooldown(ServerPlayer player, XenoformPower.Live live, int ticks) {
		live.ultimateNext = player.level().getGameTime() + ticks;
	}

	/** Spawns {@code type} at {@code at} as something of this form's (tagged, kept, silent about despawning). */
	protected static <T extends net.minecraft.world.entity.Mob> @Nullable T summon(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, Vec3 at,
		float yaw) {
		T mob = type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) {
			return null;
		}
		mob.snapTo(at.x, at.y, at.z, yaw, 0.0F);
		mob.setPersistenceRequired();
		mob.addTag(XenoformState.MINION_TAG);
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		return mob;
	}

	/** Removes every minion in {@code ids} (Swarm's gremlins) with a puff. */
	protected static void dismiss(ServerLevel level, List<Integer> ids) {
		for (int id : ids) {
			Entity e = level.getEntity(id);
			if (e != null && e.entityTags().contains(XenoformState.MINION_TAG)) {
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
				e.discard();
			}
		}
		ids.clear();
	}

	/** Starts {@code slot}'s cooldown, and counts the use toward mastery. */
	protected static void cooldown(ServerPlayer player, XenoformPower.Live live, int slot, int ticks) {
		live.cooldownUntil[slot] = player.level().getGameTime() + ticks;
		live.cooldownLength[slot] = ticks;
		live.hudDirty = true;
		Mastery.used(player);
	}

	/** The creature under the crosshair within {@code reach} blocks (scaled with the form), or null. */
	protected static @Nullable LivingEntity aimed(ServerPlayer player, double reach, Predicate<Entity> filter) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(reach));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
			e -> e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator() && !(e instanceof ArmorStand)
				&& !e.isPassengerOfSameVehicle(player) && !spared(player, e) && filter.test(e), 0.3F);
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** The player's own pets and mounts, and anyone on their team, are never hit by their form's moves. */
	protected static boolean spared(ServerPlayer player, Entity e) {
		return e instanceof OwnableEntity pet && pet.getOwner() == player || player.isAlliedTo(e);
	}

	/** Every living creature within {@code radius} of the player (not the player, their pets, team, passengers or spectators). */
	protected static List<LivingEntity> around(ServerPlayer player, double radius) {
		return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !e.isPassengerOfSameVehicle(player)
				&& !spared(player, e) && e.distanceToSqr(player) <= radius * radius);
	}

	/** Shoves {@code target} away from {@code from}, {@code out} along the ground and {@code up} upward. */
	protected static void shove(LivingEntity target, Vec3 from, double out, double up) {
		Vec3 push = target.position().subtract(from).horizontal();
		push = push.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : push.normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(push.scale(out)).add(0.0, up, 0.0));
		target.syncVelocity = true;
	}

	protected static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	protected static void burst(ServerLevel level, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
		level.sendParticles(particle, at.x, at.y, at.z, count, spread, spread * 0.5, spread, speed);
	}

	/** A ring of particles on the ground round {@code at}. */
	protected static void ring(ServerLevel level, ParticleOptions particle, Vec3 at, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = i / (double)points * Math.PI * 2.0;
			level.sendParticles(particle, at.x + Math.cos(a) * radius, at.y + 0.2, at.z + Math.sin(a) * radius, 1, 0.1, 0.05, 0.1, 0.0);
		}
	}
}
