package com.jaymoskingdoms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.410) A Skyward Valor's wings ({@link Valor.Origin#SKYWARD}, Andy: "one of the valors should give an ability to fly
 * with your valor bar, manifesting as wings that fold like the xeno, but fade as your valor does"). The wings are the
 * Valor itself, in its look's colours (drawn by ValorWingsLook), and fade as the pool runs down:
 * <ul>
 * <li><b>Flight</b> (Kindled on): double-tap jump in the air and they spread; fly where you look, jump to climb, sneak to
 * drop, sprint for speed. It drains the pool ({@value #DRAIN_FLIGHT} a second); touch down and they fold away; run dry in
 * the air and they fade out from under you (a slow fall as they go, and Spent like any coating).</li>
 * <li><b>Guarding</b>: with the coating up they wrap round you like Xenoform Wyrm's cloak, armour over armour, and swing out
 * of the way of your own blows.</li>
 * <li><b>Beating back</b>: out (guarding or flying), they sometimes catch a blow or a shot on their own, and a flap throws
 * the striker back: as long as there's meter for it.</li>
 * </ul>
 */
public final class ValorWings {
	/** Flight: drain per second, the least pool to take off with. */
	static final float DRAIN_FLIGHT = 2.0F, FLY_MIN = 6.0F;
	/** Flight speed by rank (blocks a tick; none at Spark), sprinting this much faster, climbing/dropping this fast. */
	public static final float[] FLY_SPEED = {0.0F, 0.42F, 0.50F, 0.58F, 0.66F};
	public static final float FLY_SPRINT = 1.4F, FLY_CLIMB = 0.35F;
	/** Beating back: the odds by rank, its cost, the wait between (ticks), how long the flap shows (ticks). */
	static final float[] BLOCK = {0.30F, 0.35F, 0.40F, 0.45F, 0.55F};
	static final float BLOCK_COST = 5.0F;
	static final int BLOCK_EVERY = 30, FLAP_TICKS = 8;
	/** The flap's counter: harm by rank, how near the striker must be (blocks), how hard they're thrown. */
	static final float[] COUNTER = {1.0F, 2.0F, 2.0F, 3.0F, 4.0F};
	static final double COUNTER_REACH = 4.0, COUNTER_PUSH = 0.9;
	/** Guarding: armour and toughness by rank. */
	static final double[] ARMOR = {2.0, 3.0, 4.0, 5.0, 6.0};
	static final double[] TOUGHNESS = {0.0, 0.5, 1.0, 1.5, 2.0};
	/** Landing: on the ground this many ticks folds them; no fall harm this long after (ticks); fading in the air, a slow fall this long. */
	static final int LAND_TICKS = 3, CUSHION_TICKS = 40, FADE_FALL = 80;

	private static final Identifier ARMOR_ID = JaymosKingdoms.id("valor_wings_armor"), TOUGHNESS_ID = JaymosKingdoms.id("valor_wings_toughness");

	private ValorWings() {
	}

	public static boolean skyward(Valor.Data data) {
		return data.originKind() == Valor.Origin.SKYWARD;
	}

	/** Whether this Valor can fly: Skyward, from Kindled. */
	public static boolean canFly(Valor.Data data) {
		return skyward(data) && data.rank() >= Valor.KINDLED;
	}

	/** {@code e} is flying on its wings (either side). */
	public static boolean flying(Entity e) {
		return (Valor.state(e) & Valor.FLYING) != 0;
	}

	/** {@code e}'s wings are out: flying, or a Skyward Valor coated (either side). */
	public static boolean out(Entity e) {
		if (flying(e)) {
			return true;
		}
		Valor.Data data = Valor.of(e);
		return data != null && skyward(data) && (Valor.reinforced(e) || Valor.armed(e));
	}

	/** Double-tapped jump: take off, or fold the wings and drop. */
	static void toggle(ServerPlayer sp, Valor.Data data, Valor.Live live) {
		if (live.flying) {
			land(sp, live, false);
			return;
		}
		if (!canFly(data) || sp.isPassenger() || sp.isFallFlying() || sp.isInWater() || sp.getAbilities().flying) {
			return;
		}
		long now = sp.level().getGameTime();
		if (now < live.spentUntil || live.pool < FLY_MIN) {
			sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.valor.spent")
				.withStyle(net.minecraft.ChatFormatting.GRAY));
			return;
		}
		live.flying = true;
		live.grounded = 0;
		ServerLevel level = sp.level();
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7F, 1.3F);
		Valor.coatBurst(level, sp, data.lookKind().color, 16);
		Valor.sync(sp, data, live);
	}

	/** The wings fold (touching down, double-tapped) or fade (the pool dry, downed): a slow fall if it happens in the air. */
	static void land(ServerPlayer sp, Valor.Live live, boolean faded) {
		if (!live.flying) {
			return;
		}
		live.flying = false;
		long now = sp.level().getGameTime();
		live.cushionUntil = now + CUSHION_TICKS;
		if (!sp.onGround()) {
			sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, faded ? FADE_FALL : CUSHION_TICKS, 0, false, false, true), sp);
		}
		sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), faded ? SoundEvents.PHANTOM_FLAP : SoundEvents.ENDER_DRAGON_FLAP,
			SoundSource.PLAYERS, 0.5F, faded ? 0.6F : 1.6F);
		Valor.Data data = sp.getAttached(Valor.VALOR);
		if (data != null) {
			Valor.sync(sp, data, live);
		}
	}

	/** Every tick: in flight, no fall builds up and touching down folds them; guarding, their armour. */
	static void tick(ServerPlayer sp, Valor.Data data, Valor.Live live) {
		if (live.flying) {
			sp.resetFallDistance();
			if (!canFly(data) || !sp.isAlive() || Downed.isDowned(sp) || sp.isSpectator() || sp.isPassenger() || sp.isInWater() || sp.isInLava()) {
				land(sp, live, true);
			} else if (sp.onGround()) {
				if (++live.grounded >= LAND_TICKS) {
					land(sp, live, false);
				}
			} else {
				live.grounded = 0;
			}
			if (live.flying && sp.tickCount % 12 == 0) {
				sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.35F, 1.2F);
			}
		}
		boolean guarding = skyward(data) && (live.reinforced || live.armed);
		int rank = Mth.clamp(data.rank(), 0, ARMOR.length - 1);
		armour(sp.getAttribute(Attributes.ARMOR), ARMOR_ID, guarding ? ARMOR[rank] : 0.0);
		armour(sp.getAttribute(Attributes.ARMOR_TOUGHNESS), TOUGHNESS_ID, guarding ? TOUGHNESS[rank] : 0.0);
	}

	private static void armour(AttributeInstance attribute, Identifier id, double amount) {
		if (attribute == null) {
			return;
		}
		AttributeModifier had = attribute.getModifier(id);
		if (amount <= 0.0) {
			if (had != null) {
				attribute.removeModifier(id);
			}
		} else if (had == null || had.amount() != amount) {
			attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** No fall harm in flight or just after the wings fold. */
	static boolean cushions(ServerPlayer sp) {
		Valor.Live live = Valor.live(sp);
		return live.flying || sp.level().getGameTime() < live.cushionUntil;
	}

	/**
	 * A blow or a shot on {@code sp} with the wings out: sometimes they catch it themselves and flap, throwing a striker in
	 * reach back and swatting a shot away. True when it was caught.
	 */
	static boolean beatBack(ServerPlayer sp, Valor.Data data, Valor.Live live, DamageSource source, LivingEntity attacker, boolean melee) {
		if (!skyward(data) || !(live.flying || live.reinforced || live.armed)) {
			return false;
		}
		ServerLevel level = sp.level();
		long now = level.getGameTime();
		int rank = Mth.clamp(data.rank(), 0, BLOCK.length - 1);
		if (now < live.wingReady || live.pool < BLOCK_COST || level.getRandom().nextFloat() >= BLOCK[rank]) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		if (!melee && !(direct instanceof Projectile)) {
			return false;
		}
		live.wingReady = now + BLOCK_EVERY;
		live.flapUntil = now + FLAP_TICKS;
		live.pool -= BLOCK_COST;
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.0F, 1.1F);
		level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
		Valor.coatBurst(level, sp, data.lookKind().color, 14);
		level.sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY(0.6), sp.getZ(), 10, 0.5, 0.3, 0.5, 0.08);
		if (direct instanceof Projectile shot) {
			Vec3 v = shot.getDeltaMovement();
			shot.setDeltaMovement(v.scale(-0.4).add(0.0, 0.15, 0.0));
			shot.needsSync = true;
		} else if (attacker.distanceToSqr(sp) <= COUNTER_REACH * COUNTER_REACH) {
			attacker.knockback(COUNTER_PUSH, sp.getX() - attacker.getX(), sp.getZ() - attacker.getZ(), source, 0.0F);
			attacker.hurtServer(level, sp.damageSources().playerAttack(sp), COUNTER[rank]);
		}
		Valor.sync(sp, data, live);
		return true;
	}
}
