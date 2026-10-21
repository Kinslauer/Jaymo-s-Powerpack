package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Wyrm, the void drake (scanned from the Ender Dragon): a fifth bigger than you, 36 health, wings. Water and rain burn
 * it, as they do anything of the End.
 * <ul>
 * <li>R, Dragon's Breath: a cloud of purple fire ahead of you that burns whatever stands in it.</li>
 * <li>G (hold), Wings: fly where you look, for free; let go and you drift down.</li>
 * <li>V, Wing Gust: one great beat throws everything within {@link #GUST_RADIUS} away from you.</li>
 * <li>B, Rift: step through the void to where you look, up to {@link #RIFT_RANGE} blocks.</li>
 * <li>C (ascended), Elder Wyrm: twice your size with scales of {@link #ELDER_ARMOR} armour, wings that cost nothing, a
 * breath you can pour out every {@link #ELDER_BREATH_COOLDOWN} ticks, and a gust half again as wide.</li>
 * </ul>
 */
public final class Wyrm extends XenoForm {
	// ---- tuning ----
	public static final int BREATH_COOLDOWN = 200, BREATH_TICKS = 100;
	public static final double BREATH_AHEAD = 5.0;
	public static final float BREATH_RADIUS = 3.0F;
	/** The wings: this fast (blocks/tick), easing toward where you look; slow fall for this long after letting go. Free. */
	public static final double WING_SPEED = 0.9, WING_EASE = 0.25;
	public static final int WING_SLOW_FALL_TICKS = 60;
	public static final int GUST_COOLDOWN = 120;
	public static final double GUST_RADIUS = 6.0, GUST_OUT = 1.6, GUST_UP = 0.5, GUST_RECOIL = 0.5;
	public static final float GUST_DAMAGE = 3.0F;
	/** Screen shake (degrees, and how far it's felt): the wing gust, and growing into the Elder Wyrm. */
	public static final float GUST_SHAKE = 0.8F, ELDER_SHAKE = 1.4F;
	public static final double GUST_SHAKE_RANGE = 16.0, ELDER_SHAKE_RANGE = 28.0;
	public static final int RIFT_COOLDOWN = 80;
	public static final double RIFT_RANGE = 12.0;
	/** In water or rain: this much damage every this many ticks. */
	public static final float WET_DAMAGE = 1.0F;
	public static final int WET_EVERY = 20;
	/** Elder Wyrm: extra size (on the form's 1.2x), armour, the breath's cooldown, and the gust's reach multiplier. */
	public static final double ELDER_EXTRA_SCALE = 0.8, ELDER_ARMOR = 8.0, ELDER_GUST = 1.5;
	public static final int ELDER_BREATH_COOLDOWN = 40;

	private static final net.minecraft.resources.Identifier ELDER_SCALE_ID = com.jaymospowers.JaymosPowers.id("xenoform_elder_scale");
	private static final net.minecraft.resources.Identifier ELDER_ARMOR_ID = com.jaymospowers.JaymosPowers.id("xenoform_elder_armor");

	public Wyrm() {
		super("wyrm", 1.2F, 36.0F, 5.0, 0.05, 0.4, 0.4, Items.DRAGON_BREATH, 0xC060E0);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof EnderDragon || creature instanceof EnderDragonPart;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.wings = false;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			if (down != live.wings) {
				live.wings = down;
				if (down) {
					sound(player, SoundEvents.ENDER_DRAGON_FLAP, 1.0F, 1.2F);
					cooldown(player, live, G, 1);
				} else if (!player.onGround()) {
					player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, WING_SLOW_FALL_TICKS, 0, false, false));
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
					breath(player, 1.0F);
					cooldown(player, live, R, live.ultimate ? ELDER_BREATH_COOLDOWN : BREATH_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					gust(player, live.ultimate ? ELDER_GUST : 1.0);
					cooldown(player, live, V, GUST_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && rift(player)) {
					cooldown(player, live, B, RIFT_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.wings) {
			Vec3 want = player.getLookAngle().scale(WING_SPEED);
			Vec3 motion = player.getDeltaMovement().lerp(want, WING_EASE);
			player.setDeltaMovement(motion);
			player.syncVelocity = true;
			player.resetFallDistance();
			if (player.tickCount % 12 == 0) {
				sound(player, SoundEvents.ENDER_DRAGON_FLAP, 0.8F, 1.3F);
				level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.6, 0.3, 0.6, 0.1);
			}
		}
		if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.4, 0.6, 0.4, 0.02);
		}
		if (player.isInWaterOrRain() && !player.isCreative()) {
			if (player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.3, 0.4, 0.3, 0.02);
			}
			if (player.tickCount % WET_EVERY == 0) {
				player.hurtServer(level, player.damageSources().drown(), WET_DAMAGE);
				sound(player, SoundEvents.ENDERMAN_HURT, 0.5F, 1.4F);
			}
		}
	}

	/** Its own breath can't burn it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return !(source.getDirectEntity() instanceof AreaEffectCloud cloud && cloud.getOwner() == player);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.wings || player.hasEffect(MobEffects.SLOW_FALLING);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.wings;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return live.wings && !player.onGround() ? XenoformState.FLY_WINGS : XenoformState.FLY_NONE;
	}

	// ---- Elder Wyrm ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		var scale = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
		var armor = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
		if (scale == null || armor == null) {
			return false;
		}
		scale.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ELDER_SCALE_ID, ELDER_EXTRA_SCALE,
			net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		armor.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(ELDER_ARMOR_ID, ELDER_ARMOR,
			net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		player.refreshDimensions();
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y, at.z, 80, 1.0, 1.0, 1.0, 0.5);
		ring(level, PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), player.position(), 3.0, 24);
		sound(player, SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.9F);
		com.jaymospowers.core.Shakes.heavy(player, player.position(), ELDER_SHAKE_RANGE, ELDER_SHAKE);
		return true;
	}

	@Override
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
		if (player.tickCount % 3 == 0) {
			player.level().sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), player.getX(), player.getY() + 1.2, player.getZ(), 1,
				0.6, 0.6, 0.6, 0.0);
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		var scale = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
		var armor = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
		if (scale != null) {
			scale.removeModifier(ELDER_SCALE_ID);
		}
		if (armor != null) {
			armor.removeModifier(ELDER_ARMOR_ID);
		}
		player.refreshDimensions();
	}

	private static void breath(ServerPlayer player, float linger) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 at = eye.add(look.scale(BREATH_AHEAD));
		// down to the ground under that point, if it's near
		BlockHitResult ground = level.clip(new ClipContext(at, at.add(0.0, -6.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (ground.getType() == HitResult.Type.BLOCK) {
			at = ground.getLocation().add(0.0, 0.1, 0.0);
		}
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		cloud.setOwner(player);
		cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F));
		cloud.setRadius(BREATH_RADIUS);
		int ticks = (int)(BREATH_TICKS * linger);
		cloud.setDuration(ticks);
		cloud.setRadiusPerTick(-BREATH_RADIUS * 0.3F / ticks);
		cloud.setPotionDurationScale(0.25F);
		cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
		level.addFreshEntity(cloud);
		for (double d = 1.0; d < BREATH_AHEAD; d += 0.5) {
			Vec3 p = eye.add(look.scale(d));
			level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0F), p.x, p.y - 0.2, p.z, 3, 0.15, 0.15, 0.15, 0.02);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.ENDER_DRAGON_SHOOT, 1.2F, 1.1F);
	}

	private static void gust(ServerPlayer player, double reach) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, GUST_RADIUS * reach)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), GUST_DAMAGE);
			shove(living, player.position(), GUST_OUT, GUST_UP);
		}
		Vec3 back = player.getLookAngle().horizontal();
		if (back.lengthSqr() > 1.0E-4) {
			player.setDeltaMovement(player.getDeltaMovement().add(back.normalize().scale(-GUST_RECOIL)).add(0.0, 0.2, 0.0));
			player.syncVelocity = true;
		}
		ring(level, ParticleTypes.CLOUD, player.position().add(0.0, 1.0, 0.0), GUST_RADIUS * reach * 0.5, 16);
		ring(level, ParticleTypes.GUST, player.position(), GUST_RADIUS * reach, 8);
		sound(player, SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.7F);
		sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 1.0F, 0.8F);
		// the beat throws you back: felt from ahead
		com.jaymospowers.core.Shakes.heavy(player, player.getEyePosition().add(player.getLookAngle().scale(2.0)), GUST_SHAKE_RANGE, GUST_SHAKE);
	}

	private static boolean rift(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(RIFT_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 point = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.6)) : eye.add(look.scale(RIFT_RANGE));
		AABB box = player.getBoundingBox();
		for (int drop = 0; drop <= 3; drop++) {
			Vec3 feet = new Vec3(point.x, point.y - player.getEyeHeight() - drop, point.z);
			if (level.noCollision(player, box.move(feet.subtract(player.position())))) {
				Vec3 from = player.position();
				player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), player.getYRot(), player.getXRot(), false);
				player.resetFallDistance();
				level.sendParticles(ParticleTypes.PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.3);
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, feet.x, feet.y + 1.0, feet.z, 30, 0.3, 0.6, 0.3, 0.3);
				sound(player, SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.8F);
				return true;
			}
		}
		return false;
	}
}
