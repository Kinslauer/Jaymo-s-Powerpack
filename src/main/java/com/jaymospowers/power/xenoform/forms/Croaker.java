package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.102, Xeno-Band patch 5) Croaker, the war frog: big, wide-mouthed, bulging eyes on top of its head, a throat
 * sac that swells when it croaks, webbed feet. 26 health, fists of 5. Scanned from a frog (or a tadpole,
 * {@code XenoDna}). It swims well and lands every jump soft.
 * <ul>
 * <li>R, Tongue: lash out at the creature you look at (up to {@link #TONGUE_REACH}) and haul it to you, or snatch a
 * dropped item into your hand's reach.</li>
 * <li>G, Gulp / Spit: swallow a small creature right in front of you (it's held, hidden, and slowly digested); G again
 * spits it out hard where you look.</li>
 * <li>V, Bound: a long frog leap.</li>
 * <li>B, Croak: a booming croak that stuns everything round you.</li>
 * </ul>
 */
public final class Croaker extends XenoForm {
	// ---- tuning ----
	public static final double TONGUE_REACH = 10.0, TONGUE_PULL = 0.32;
	public static final float TONGUE_DAMAGE = 2.0F;
	public static final int TONGUE_COOLDOWN = 30;
	/** What it can swallow: no wider or taller than this, never a player. */
	public static final double GULP_REACH = 3.0, GULP_MAX_WIDTH = 1.0, GULP_MAX_HEIGHT = 1.5;
	public static final int GULP_COOLDOWN = 40, DIGEST_EVERY = 40;
	public static final float DIGEST_DAMAGE = 1.0F, SPIT_DAMAGE = 4.0F;
	public static final double SPIT_SPEED = 1.6;
	public static final double BOUND_OUT = 1.3, BOUND_UP = 0.8;
	public static final int BOUND_COOLDOWN = 40;
	public static final double CROAK_RADIUS = 6.0;
	public static final float CROAK_DAMAGE = 2.0F;
	public static final int CROAK_TICKS = 50, CROAK_COOLDOWN = 200;

	public Croaker() {
		super("croaker", 1.0F, 26.0F, 5.0, 0.0, 0.3, 0.5, Items.LILY_PAD, 0x6E9E3C);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Frog;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		Mob held = gullet(player, live);
		if (held != null) {
			release(player, held, Vec3.ZERO);
		}
		live.swallowed = -1;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_FALL);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					tongue(player, level);
					cooldown(player, live, R, TONGUE_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				Mob held = gullet(player, live);
				if (held != null) {
					release(player, held, player.getLookAngle().scale(SPIT_SPEED));
					held.hurtServer(level, player.damageSources().playerAttack(player), SPIT_DAMAGE);
					live.swallowed = -1;
					live.hudDirty = true;
					sound(player, SoundEvents.LLAMA_SPIT, 1.0F, 0.6F);
					cooldown(player, live, G, GULP_COOLDOWN);
					return;
				}
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, GULP_REACH, e -> e instanceof Mob m && !m.isNoAi() && !m.isVehicle()
					&& e.getBbWidth() <= GULP_MAX_WIDTH && e.getBbHeight() <= GULP_MAX_HEIGHT);
				if (target == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.croaker.too_big").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
					return;
				}
				Mob mob = (Mob)target;
				mob.stopRiding();
				if (!mob.startRiding(player, true, false)) {
					return;
				}
				mob.setNoAi(true);
				mob.setInvisible(true);
				mob.setTarget(null);
				live.swallowed = mob.getId();
				live.hudDirty = true;
				sound(player, SoundEvents.FROG_EAT, 1.2F, 0.6F);
				cooldown(player, live, G, 10);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					Vec3 look = player.getLookAngle().horizontal().normalize();
					player.setDeltaMovement(look.x * BOUND_OUT, BOUND_UP, look.z * BOUND_OUT);
					player.syncVelocity = true;
					sound(player, SoundEvents.FROG_LONG_JUMP, 1.0F, 0.7F);
					cooldown(player, live, V, BOUND_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					croak(player, level);
					cooldown(player, live, B, CROAK_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** The Tongue: the aimed creature hauled in, or else the dropped item on the line. */
	private static void tongue(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		LivingEntity target = aimed(player, TONGUE_REACH, e -> true);
		sound(player, SoundEvents.FROG_TONGUE, 1.2F, 0.8F);
		if (target != null) {
			beam(level, eye.add(0.0, -0.3, 0.0), target.getBoundingBox().getCenter(), ParticleTypes.ITEM_SLIME, 12);
			Vec3 in = eye.subtract(target.position());
			double pull = TONGUE_PULL * Math.min(3.0, Math.sqrt(in.length()));
			// big creatures barely budge
			pull /= Math.max(1.0, target.getBbWidth() * target.getBbHeight());
			target.setDeltaMovement(in.normalize().scale(pull).add(0.0, 0.25, 0.0));
			target.syncVelocity = true;
			target.hurtServer(level, player.damageSources().playerAttack(player), TONGUE_DAMAGE);
			return;
		}
		Vec3 look = player.getLookAngle();
		AABB line = player.getBoundingBox().expandTowards(look.scale(TONGUE_REACH)).inflate(1.0);
		ItemEntity best = null;
		double bestAlong = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, line)) {
			Vec3 c = item.position().subtract(eye);
			double along = c.dot(look);
			if (along <= 0.0 || along > TONGUE_REACH || c.subtract(look.scale(along)).length() > 1.0 || along >= bestAlong) {
				continue;
			}
			best = item;
			bestAlong = along;
		}
		if (best != null) {
			beam(level, eye.add(0.0, -0.3, 0.0), best.position(), ParticleTypes.ITEM_SLIME, 12);
			best.setPos(player.getX(), player.getY() + 0.5, player.getZ());
			best.setDeltaMovement(Vec3.ZERO);
			best.setNoPickUpDelay();
		}
	}

	private static void croak(ServerPlayer player, ServerLevel level) {
		for (LivingEntity e : around(player, CROAK_RADIUS)) {
			e.hurtServer(level, player.damageSources().playerAttack(player), CROAK_DAMAGE);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CROAK_TICKS, 3), player);
			if (e instanceof Player) {
				e.addEffect(new MobEffectInstance(MobEffects.NAUSEA, CROAK_TICKS + 40, 0), player);
			} else if (e instanceof Mob mob) {
				mob.setTarget(null);
			}
		}
		ring(level, ParticleTypes.NOTE, player.position().add(0.0, 1.0, 0.0), CROAK_RADIUS * 0.6, 16);
		sound(player, SoundEvents.FROG_AMBIENT, 2.0F, 0.4F);
	}

	/** What's in the gullet now (null: nothing, or it's gone). */
	private static @Nullable Mob gullet(ServerPlayer player, XenoformPower.Live live) {
		if (live.swallowed < 0) {
			return null;
		}
		if (player.level().getEntity(live.swallowed) instanceof Mob mob && mob.isAlive() && mob.getVehicle() == player) {
			return mob;
		}
		live.swallowed = -1;
		return null;
	}

	private static void release(ServerPlayer player, Mob mob, Vec3 throwAt) {
		mob.stopRiding();
		mob.setNoAi(false);
		mob.setInvisible(false);
		Vec3 front = player.getEyePosition().add(player.getLookAngle().horizontal().normalize().scale(1.0));
		mob.teleportTo(front.x, player.getY() + 0.5, front.z);
		mob.setDeltaMovement(throwAt);
		mob.syncVelocity = true;
		player.level().sendParticles(ParticleTypes.ITEM_SLIME, front.x, player.getY() + 1.0, front.z, 10, 0.3, 0.3, 0.3, 0.05);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 10, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false));
		}
		Mob held = gullet(player, live);
		if (held != null && player.tickCount % DIGEST_EVERY == 0) {
			held.hurtServer(player.level(), player.damageSources().playerAttack(player), DIGEST_DAMAGE);
			if (!held.isAlive()) {
				live.swallowed = -1;
				sound(player, SoundEvents.PLAYER_BURP, 1.0F, 0.7F);
				player.getFoodData().eat(2, 0.3F);
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.SPLASH, player.position().add(0.0, 1.0, 0.0), 40, 0.8, 0.2);
		sound(player, SoundEvents.FROG_AMBIENT, 2.0F, 0.3F);
		return true;
	}
}
