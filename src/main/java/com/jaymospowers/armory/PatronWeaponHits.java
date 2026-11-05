package com.jaymospowers.armory;

import com.jaymospowers.core.wounds.Resting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.18) What the patron weapons do ({@link PatronWeapons}): their timed states (Sunder stacks, Marked Prey, Smokestep's
 * window, Sun's Judgement's wind-up, Kindled Blow's gap), their server tick, and the guarded extra-damage helper.
 */
final class PatronWeaponHits {
	private static final Identifier SUNDER_ID = Identifier.fromNamespaceAndPath("jaymospowers", "sunder");

	/** Sunder: stacks and when they end (game time). */
	private record Sunder(int stacks, long until) {
	}

	/** Marked Prey: who marked it, whether sworn, and until when. */
	private record Mark(LivingEntity by, boolean sworn, long until) {
	}

	/** Smokestep: the next hit cuts deep until this time. */
	private record Window(long until) {
	}

	/** Sun's Judgement: a beam due at this time on this target. */
	private record Strike(LivingEntity target, LivingEntity by, long due, float damage) {
	}

	private static final Map<LivingEntity, Sunder> SUNDERS = new WeakHashMap<>();
	private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();
	private static final Map<LivingEntity, Window> WINDOWS = new WeakHashMap<>();
	private static final Map<LivingEntity, Long> KINDLED = new WeakHashMap<>();
	private static final List<Strike> STRIKES = new ArrayList<>();
	/** An extra hurt is never answered by another. */
	private static final ThreadLocal<Boolean> EXTRA = ThreadLocal.withInitial(() -> false);

	private PatronWeaponHits() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PatronWeaponHits::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(PatronWeaponHits::afterDamage);
	}

	/** An extra blow of {@code amount} on {@code target} from {@code by}, never chaining. */
	static void extra(ServerLevel level, LivingEntity target, LivingEntity by, float amount) {
		if (amount <= 0.0F || EXTRA.get() || !target.isAlive()) {
			return;
		}
		EXTRA.set(true);
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, by instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(by), amount);
		} finally {
			EXTRA.set(false);
		}
	}

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float taken, boolean blocked) {
		if (blocked || taken <= 0.0F || EXTRA.get() || !(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof LivingEntity by)) {
			return;
		}
		long now = level.getGameTime();
		Mark mark = MARKS.get(entity);
		if (mark != null) {
			if (now > mark.until()) {
				MARKS.remove(entity);
			} else if (mark.by() == by) {
				float share = entity instanceof Animal ? (mark.sworn() ? PatronWeapons.MARK_BEAST_SWORN : PatronWeapons.MARK_BEAST)
					: (mark.sworn() ? PatronWeapons.MARK_BONUS_SWORN : PatronWeapons.MARK_BONUS);
				extra(level, entity, by, taken * share);
			}
		}
		Window window = WINDOWS.get(by);
		if (window != null && source.getDirectEntity() == by) {
			WINDOWS.remove(by);
			if (now <= window.until()) {
				extra(level, entity, by, taken * (PatronWeapons.SMOKE_BACKSTAB - 1.0F));
				level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 12, 0.3, 0.3, 0.3, 0.3);
			}
		}
	}

	// ---- on-hit ------------------------------------------------------------------------------------------------------------

	static void kindled(ServerLevel level, LivingEntity wielder, boolean sworn) {
		long now = level.getGameTime();
		Long last = KINDLED.get(wielder);
		if (last != null && now - last < PatronWeapons.HEARTH_GAP) {
			return;
		}
		KINDLED.put(wielder, now);
		float heal = PatronWeapons.HEARTH_HEAL * (sworn ? PatronWeapons.HEARTH_SWORN : 1.0F);
		double range = sworn ? PatronWeapons.HEARTH_RANGE_SWORN : PatronWeapons.HEARTH_RANGE;
		heal(wielder, heal);
		for (Player p : level.getEntitiesOfClass(Player.class, wielder.getBoundingBox().inflate(range), p -> p != wielder && p.isAlive() && !p.isSpectator()
			&& p.distanceTo(wielder) <= range)) {
			heal(p, heal);
			level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1.0, p.getZ(), 5, 0.3, 0.5, 0.3, 0.01);
		}
		level.sendParticles(ParticleTypes.FLAME, wielder.getX(), wielder.getY() + 1.0, wielder.getZ(), 8, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, wielder.getX(), wielder.getY(), wielder.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static void heal(LivingEntity e, float amount) {
		if (e instanceof ServerPlayer sp) {
			Resting.healWorst(sp, amount);
		} else {
			e.heal(amount);
		}
	}

	static void sunder(ServerLevel level, LivingEntity target, boolean sworn) {
		AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
		if (armor == null) {
			return;
		}
		long now = level.getGameTime();
		Sunder was = SUNDERS.get(target);
		int most = sworn ? PatronWeapons.SUNDER_MOST_SWORN : PatronWeapons.SUNDER_MOST;
		int stacks = Math.min(most, (was == null || now > was.until() ? 0 : was.stacks()) + 1);
		SUNDERS.put(target, new Sunder(stacks, now + PatronWeapons.SUNDER_TICKS));
		armor.addOrUpdateTransientModifier(new AttributeModifier(SUNDER_ID, -PatronWeapons.SUNDER_ARMOR * stacks, AttributeModifier.Operation.ADD_VALUE));
		if (was == null || stacks > was.stacks()) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.6F);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
		}
	}

	static void mark(ServerLevel level, LivingEntity target, LivingEntity by, boolean sworn) {
		MARKS.put(target, new Mark(by, sworn, level.getGameTime() + PatronWeapons.MARK_TICKS));
		target.addEffect(new MobEffectInstance(MobEffects.GLOWING, PatronWeapons.MARK_TICKS, 0, false, false, true), by);
	}

	static void soulFlame(ServerLevel level, LivingEntity target, LivingEntity by, boolean sworn) {
		if (!target.is(EntityTypeTags.UNDEAD)) {
			return;
		}
		boolean night = level.isDarkOutside();
		target.igniteForTicks(PatronWeapons.GRAVE_FIRE);
		extra(level, target, by, (night ? PatronWeapons.GRAVE_NIGHT : PatronWeapons.GRAVE_BONUS) * (sworn ? PatronWeapons.GRAVE_SWORN : 1.0F));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
		if (sworn) {
			by.heal(PatronWeapons.GRAVE_HEAL);
		}
	}

	static void unmask(ServerLevel level, LivingEntity target, boolean sworn) {
		int left = sworn ? PatronWeapons.UNMASK_EACH_SWORN : PatronWeapons.UNMASK_EACH;
		if (target.hasEffect(MobEffects.INVISIBILITY)) {
			target.removeEffect(MobEffects.INVISIBILITY);
			target.addEffect(new MobEffectInstance(MobEffects.GLOWING, PatronWeapons.UNMASK_GLOW, 0));
			left--;
		}
		List<MobEffectInstance> good = new ArrayList<>();
		for (MobEffectInstance e : target.getActiveEffects()) {
			if (e.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
				good.add(e);
			}
		}
		good.sort(Comparator.comparingInt(MobEffectInstance::getDuration).reversed());
		boolean any = left < (sworn ? PatronWeapons.UNMASK_EACH_SWORN : PatronWeapons.UNMASK_EACH);
		for (int i = 0; i < left && i < good.size(); i++) {
			target.removeEffect(good.get(i).getEffect());
			any = true;
		}
		if (any) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.4F, 1.6F);
		}
	}

	// ---- used ----------------------------------------------------------------------------------------------------------------

	static void undertow(ServerLevel level, Player player, boolean sworn) {
		boolean wet = player.isInWater() || level.isRainingAt(player.blockPosition());
		double range = (sworn ? PatronWeapons.UNDERTOW_RANGE_SWORN : PatronWeapons.UNDERTOW_RANGE) * (wet ? PatronWeapons.UNDERTOW_WET : 1.0F);
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range), m -> m.isAlive() && (m instanceof Enemy || m.getTarget() == player)
			&& m.distanceTo(player) <= range)) {
			Vec3 d = player.position().subtract(m.position()).multiply(1.0, 0.0, 1.0);
			if (d.lengthSqr() > 1.0E-4) {
				d = d.normalize().scale(PatronWeapons.UNDERTOW_PULL);
				m.push(d.x, 0.2, d.z);
				m.syncVelocity = true;
			}
			m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PatronWeapons.UNDERTOW_SLOW, 1), player);
		}
		for (int ring = 0; ring < 3; ring++) {
			double r = range * (1.0 - ring / 3.0);
			for (int i = 0; i < 24; i++) {
				double a = i * Math.PI * 2.0 / 24.0;
				level.sendParticles(ParticleTypes.BUBBLE, player.getX() + Math.cos(a) * r, player.getY() + 0.5, player.getZ() + Math.sin(a) * r, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** Marks the foe in the player's sights for a beam; false if there is none (and so no cooldown). */
	static boolean judge(ServerLevel level, Player player, boolean sworn) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(PatronWeapons.SUN_RANGE));
		// a wall stops the sight: the search ends at the first block in the way
		BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() == HitResult.Type.BLOCK) {
			end = wall.getLocation();
		}
		AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity l && l.isAlive() && l.isPickable() && !e.isSpectator(), end.distanceToSqr(eye));
		if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
			return false;
		}
		float damage = sworn ? PatronWeapons.SUN_DAMAGE_SWORN : PatronWeapons.SUN_DAMAGE;
		STRIKES.add(new Strike(target, player, level.getGameTime() + PatronWeapons.SUN_WINDUP, damage));
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	static void smokestep(ServerLevel level, Player player, boolean sworn) {
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.03);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, sworn ? PatronWeapons.SMOKE_INVIS_SWORN : PatronWeapons.SMOKE_INVIS, 0, false, false, true));
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(PatronWeapons.SMOKE_LOSE_RANGE), m -> m.getTarget() == player)) {
			m.setTarget(null);
		}
		WINDOWS.put(player, new Window(level.getGameTime() + PatronWeapons.SMOKE_WINDOW));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	// ---- the server tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!STRIKES.isEmpty()) {
			for (Strike s : new ArrayList<>(STRIKES)) {
				if (!(s.target().level() instanceof ServerLevel level) || !s.target().isAlive()) {
					STRIKES.remove(s);
					continue;
				}
				Vec3 at = s.target().position();
				for (double y = 0.0; y < 6.0; y += 0.6) {
					level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + y, at.z, 1, 0.1, 0.0, 0.1, 0.0);
				}
				if (level.getGameTime() >= s.due()) {
					STRIKES.remove(s);
					beam(level, s);
				}
			}
		}
		if (server.getTickCount() % 10 == 0 && !SUNDERS.isEmpty()) {
			for (Map.Entry<LivingEntity, Sunder> e : new ArrayList<>(SUNDERS.entrySet())) {
				LivingEntity target = e.getKey();
				if (target == null || !target.isAlive() || target.level().getGameTime() > e.getValue().until()) {
					if (target != null) {
						AttributeInstance armor = target.getAttribute(Attributes.ARMOR);
						if (armor != null) {
							armor.removeModifier(SUNDER_ID);
						}
						SUNDERS.remove(target);
					}
				}
			}
		}
	}

	private static void beam(ServerLevel level, Strike s) {
		LivingEntity target = s.target();
		boolean day = level.isBrightOutside() && level.canSeeSky(target.blockPosition());
		float damage = s.damage() * (day ? PatronWeapons.SUN_DAY : 1.0F);
		target.setInvulnerableTime(0);
		Entity by = s.by();
		target.hurtServer(level, level.damageSources().indirectMagic(by, by), damage);
		target.igniteForTicks(PatronWeapons.SUN_FIRE);
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, PatronWeapons.SUN_BLIND, 0));
		for (double y = 0.0; y < 12.0; y += 0.4) {
			level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + y, target.getZ(), 2, 0.15, 0.0, 0.15, 0.02);
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 1.8F);
	}

}
