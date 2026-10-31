package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoForms;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
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
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.96, Xeno-Band patch 4) Slipfin, the axolotl alien and the band's first swimmer: slim, gilled, finned down the
 * back, 22 health, fists of 4. Its skin is slick: grabs, carries, tendrils, bolas and nets slide off it
 * ({@link #slippery}). In water it swims like a dolphin and breathes; out of water too long ({@link #DRY_TICKS}) it
 * dries out and gasps. Rain keeps it wet.
 * <ul>
 * <li>R, Bubble Jet: a jet of water and bubbles, {@link #JET_REACH} blocks, harder under water.</li>
 * <li>G, Slip: wriggle out of anything holding you, shake off slowness and tangles, and a burst of speed.</li>
 * <li>V, Dart: a lunge where you look (a long one in water, a flop on land) that bites the first creature met.</li>
 * <li>B, Regrow: like the axolotl, it heals what's broken: a lost limb grows back, breaks knit, and it regenerates.</li>
 * </ul>
 */
public final class Slipfin extends XenoForm {
	// ---- tuning ----
	/** Out of water this long (ticks) it starts to dry out; then every {@link #GASP_EVERY} ticks it gasps for {@link #GASP_DAMAGE}. */
	public static final int DRY_TICKS = 600, GASP_EVERY = 40;
	public static final float GASP_DAMAGE = 1.0F;
	public static final double JET_REACH = 12.0, JET_WIDTH = 1.2, JET_PUSH = 0.6;
	public static final float JET_DAMAGE = 3.0F, JET_WET_DAMAGE = 6.0F;
	public static final int JET_COOLDOWN = 20;
	public static final int SLIP_COOLDOWN = 100, SLIP_SPEED_TICKS = 60;
	public static final double DART_WATER = 2.0, DART_LAND = 0.9, DART_HIT_REACH = 1.6;
	public static final float DART_DAMAGE = 6.0F;
	public static final int DART_COOLDOWN = 30, DART_TICKS = 10;
	public static final int REGROW_COOLDOWN = 900, REGROW_TICKS = 100;

	public Slipfin() {
		super("slipfin", 0.8F, 22.0F, 4.0, 0.1, 0.2, 0.0, Items.AXOLOTL_BUCKET, 0xF08CB8);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Axolotl;
	}

	/** Whether {@code entity} is a player who is Slipfin now: too slick to grab, carry, tangle or net. */
	public static boolean slippery(@Nullable Entity entity) {
		return entity instanceof Player player && XenoformState.form(player) == XenoForms.SLIPFIN;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.dry = 0;
		live.horn = 0;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_DROWNING);
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
					jet(player, level);
					cooldown(player, live, R, JET_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					free(player);
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, SLIP_SPEED_TICKS, 1, false, true));
					burst(level, ParticleTypes.SPLASH, player.position().add(0.0, 0.6, 0.0), 30, 0.4, 0.1);
					sound(player, SoundEvents.SLIME_SQUISH, 1.0F, 1.4F);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.slipfin.slip").withStyle(ChatFormatting.LIGHT_PURPLE));
					cooldown(player, live, G, SLIP_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					Vec3 look = player.getLookAngle();
					boolean wet = player.isInWater();
					double speed = wet ? DART_WATER : DART_LAND;
					player.setDeltaMovement(look.x * speed, wet ? look.y * speed : Math.max(0.35, look.y * speed), look.z * speed);
					player.syncVelocity = true;
					live.horn = DART_TICKS;
					sound(player, wet ? SoundEvents.DOLPHIN_SWIM : SoundEvents.AXOLOTL_SPLASH, 1.0F, 1.2F);
					cooldown(player, live, V, DART_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					com.jaymospowers.core.wounds.Breaks.clearAll(player);
					com.jaymospowers.core.Dismember.regrow(player, false);
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGROW_TICKS, 1, false, true));
					burst(level, ParticleTypes.HEART, player.position().add(0.0, 1.0, 0.0), 6, 0.4, 0.05);
					sound(player, SoundEvents.AXOLOTL_SPLASH, 1.0F, 0.8F);
					cooldown(player, live, B, REGROW_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** The Bubble Jet: everything on its line is hit and pushed; fires along it go out. */
	private static void jet(ServerPlayer player, ServerLevel level) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		boolean wet = player.isUnderWater();
		float damage = wet ? JET_WET_DAMAGE : JET_DAMAGE;
		for (LivingEntity e : around(player, JET_REACH)) {
			Vec3 c = e.getBoundingBox().getCenter().subtract(eye);
			double along = c.dot(look);
			if (along <= 0.0 || along > JET_REACH || c.subtract(look.scale(along)).length() > JET_WIDTH + e.getBbWidth() * 0.5 || !player.hasLineOfSight(e)) {
				continue;
			}
			e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			e.clearFire();
			e.setDeltaMovement(e.getDeltaMovement().add(look.scale(JET_PUSH)));
			e.syncVelocity = true;
		}
		for (int i = 1; i <= 12; i++) {
			Vec3 p = eye.add(look.scale(i * JET_REACH / 12.0));
			level.sendParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, p.x, p.y, p.z, 4, 0.12, 0.12, 0.12, 0.05);
		}
		sound(player, wet ? SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE : SoundEvents.PLAYER_SPLASH, 1.0F, 1.4F);
	}

	/** Slips out of whatever holds it: a carry, a ride it didn't choose, a trip, a tangle. */
	private static void free(ServerPlayer player) {
		Integer carrier = player.getAttached(com.jaymospowers.core.Carry.CARRIED_BY);
		if (carrier != null && player.level().getEntity(carrier) instanceof ServerPlayer holder) {
			com.jaymospowers.core.Carry.slip(holder);
		}
		Entity vehicle = player.getVehicle();
		if (vehicle instanceof net.minecraft.world.entity.monster.Monster || vehicle != null && !(vehicle instanceof net.minecraft.world.entity.vehicle.VehicleEntity)
			&& !(vehicle instanceof net.minecraft.world.entity.animal.equine.AbstractHorse)) {
			player.stopRiding();
		}
		player.removeEffect(MobEffects.SLOWNESS);
		player.removeAttached(com.jaymospowers.core.wounds.Tripped.TRIP);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// slick: whatever grabs it can't keep hold
		if (player.tickCount % 5 == 0 && (com.jaymospowers.core.Carry.isCarried(player) || player.getAttached(com.jaymospowers.core.wounds.Tripped.TRIP) != null)) {
			free(player);
		}
		// the dart bites the first creature it meets
		if (live.horn > 0) {
			live.horn--;
			for (LivingEntity e : around(player, DART_HIT_REACH)) {
				e.hurtServer(level, player.damageSources().playerAttack(player), DART_DAMAGE);
				sound(player, SoundEvents.AXOLOTL_ATTACK, 1.0F, 1.0F);
				live.horn = 0;
				break;
			}
		}
		if (player.isInWaterOrRain()) {
			if (live.dry > 0) {
				live.dry = Math.max(0, live.dry - 10);
			}
			if (player.isInWater()) {
				player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 10, 1, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
				player.setAirSupply(player.getMaxAirSupply());
			}
			return;
		}
		live.dry++;
		if (live.dry == DRY_TICKS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.slipfin.drying").withStyle(ChatFormatting.RED));
		}
		if (live.dry > DRY_TICKS && live.dry % GASP_EVERY == 0) {
			player.hurtServer(level, player.damageSources().dryOut(), GASP_DAMAGE);
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, GASP_EVERY + 5, 0, false, true));
			sound(player, SoundEvents.PLAYER_BREATH, 1.0F, 1.6F);
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		burst(level, ParticleTypes.SPLASH, player.position().add(0.0, 1.0, 0.0), 60, 0.8, 0.2);
		sound(player, SoundEvents.BUCKET_EMPTY_AXOLOTL, 1.4F, 0.7F);
		live.dry = 0;
		return true;
	}
}
