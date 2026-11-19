package com.jaymospowers.core.brawl;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.gifted.GiftKit;
import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Powering up mid-fight (NPC fights patch 3, row F13): at half health, once, a tyrant, a Galactic Patrolman or a Z Fighter's gifted
 * villager backs off from the foe ({@link #BACK_MIN} to {@link #BACK_MAX} blocks), hovers and flares for {@link #FLARE_MIN} to
 * {@link #FLARE_MAX} ticks (rocks lift off the ground under them, the ground shakes, a hum climbs), and then goes up a form: a
 * tyrant's own Super 2 (his owner asks {@link #ready}), the others a rank boost ({@link #boost}: they think and rest faster, guard
 * and dodge more, with a brighter aura). A blow of {@link #INTERRUPT_SHARE} of their health (or {@link #INTERRUPT_MIN}) during
 * the build-up cuts it short: no new form, and they stagger. Everything works the same against a player as against an NPC.
 */
public final class PowerUp {
	/** It starts below this share of health. */
	static final float START_HEALTH = 0.5F;
	/** The backing off: how far (blocks), how fast (blocks a tick) and at most how long (ticks). */
	static final double BACK_MIN = 6.0, BACK_MAX = 10.0, BACK_SPEED = 0.7;
	static final int BACK_TICKS = 14;
	/** The flare: how long (ticks), and how high they hover over the ground (blocks). */
	static final int FLARE_MIN = 30, FLARE_MAX = 40;
	static final double HOVER_HEIGHT = 2.5, HOVER_LIFT = 0.08;
	/** The rank a power-up adds, and the brighter aura's colour. */
	static final float BOOST_RANK = 0.3F;
	public static final int AURA_COLOR = 0xFFF080;
	/** The flare's shake (degrees) rises to this, how far it carries (blocks); the finish shakes harder. */
	static final float FLARE_SHAKE = 0.9F, FINISH_SHAKE = 1.3F;
	static final double SHAKE_RANGE = 28.0;
	/** A blow of this share of max health, or this much, during the build-up cuts it short; they stagger (level, ticks). */
	static final float INTERRUPT_SHARE = 0.12F, INTERRUPT_MIN = 6.0F;
	static final int STAGGER_LEVEL = 2, STAGGER_TICKS = 50;
	/** Nothing starts unless the foe is within this far (blocks). */
	static final double FOE_RANGE = 48.0;

	private static final Identifier ROOT_ID = JaymosPowers.id("power_up_root");

	private enum Phase {
		BACK, FLARE
	}

	private static final class Up {
		Phase phase = Phase.BACK;
		final long start;
		long flareStart, end;
		final Vec3 backTo;
		final LivingEntity foe;
		double groundY;

		Up(long start, Vec3 backTo, LivingEntity foe, double groundY) {
			this.start = start;
			this.backTo = backTo;
			this.foe = foe;
			this.groundY = groundY;
		}
	}

	/** Building up right now. */
	private static final Map<Mob, Up> UPS = new WeakHashMap<>();
	/** Finished (a tyrant's owner takes it once with {@link #ready}); with {@link #CUT}, no second try. */
	private static final Map<Mob, Boolean> DONE = new WeakHashMap<>();
	private static final Map<Mob, Boolean> CUT = new WeakHashMap<>();
	/** The boosted: patrolmen and Z Fighter gifted that have powered up. */
	private static final Map<Mob, Boolean> BOOSTED = new WeakHashMap<>();

	private PowerUp() {
	}

	/** From {@link Brawl#init}. */
	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(PowerUp::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PowerUp::allowDamage);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			UPS.clear();
			DONE.clear();
			CUT.clear();
			BOOSTED.clear();
		});
	}

	/** Whether {@code mob} is building up now: the fighter brain leaves its body alone. */
	public static boolean holding(Mob mob) {
		return UPS.containsKey(mob);
	}

	/** The rank a power-up has added to {@code mob} (0 if it has not powered up). */
	public static float boost(Mob mob) {
		return BOOSTED.containsKey(mob) ? BOOST_RANK : 0.0F;
	}

	/**
	 * From {@link Brawl#tick}, every call: a patrolman or a Z Fighter's gifted villager (the ki kit) below half health, once, starts
	 * to power up. (A tyrant's owner asks {@link #ready} instead.)
	 */
	static void watch(ServerLevel level, Mob mob, Brawl.Profile profile, @Nullable LivingEntity foe) {
		GiftKit kit = profile.kit();
		if (kit == null || !ZFighterPower.ID.equals(kit.power())) {
			return;
		}
		start(level, mob, foe);
	}

	/**
	 * A tyrant's owner asks each tick, while he is below his Super 2: true once he has built up (the owner then goes up a form).
	 * Below half health, with a foe, it starts the build-up; it stays false while he builds, and for good if a blow cut it short.
	 */
	public static boolean ready(Mob mob) {
		if (DONE.containsKey(mob)) {
			return true;
		}
		if (!CUT.containsKey(mob) && mob.level() instanceof ServerLevel level) {
			start(level, mob, mob.getTarget());
		}
		return false;
	}

	private static void start(ServerLevel level, Mob mob, @Nullable LivingEntity foe) {
		if (foe == null || !foe.isAlive() || foe.level() != mob.level() || UPS.containsKey(mob) || DONE.containsKey(mob) || CUT.containsKey(mob)
			|| mob.getHealth() >= mob.getMaxHealth() * START_HEALTH || mob.distanceTo(foe) > FOE_RANGE || mob.isInWater() || mob.isPassenger()
			|| com.jaymospowers.core.BeamClash.clashing(mob)) {
			return;
		}
		Vec3 away = mob.position().subtract(foe.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
		double distance = BACK_MIN + mob.getRandom().nextDouble() * (BACK_MAX - BACK_MIN);
		Vec3 to = mob.position().add(away.scale(distance)).add(0.0, 1.0, 0.0);
		Up up = new Up(level.getGameTime(), to, foe, mob.getY());
		UPS.put(mob, up);
		mob.setNoGravity(true);
		mob.getNavigation().stop();
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(ROOT_ID, -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.2F, 0.7F);
	}

	private static void tick(MinecraftServer server) {
		if (UPS.isEmpty()) {
			return;
		}
		for (Map.Entry<Mob, Up> entry : new java.util.ArrayList<>(UPS.entrySet())) {
			Mob mob = entry.getKey();
			Up up = entry.getValue();
			if (mob == null || mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
				if (mob != null) {
					release(mob);
				}
				UPS.remove(mob);
				continue;
			}
			long now = level.getGameTime();
			mob.getNavigation().stop();
			mob.setTarget(null);
			if (up.foe.isAlive() && up.foe.level() == mob.level()) {
				mob.getLookControl().setLookAt(up.foe, 30.0F, 30.0F);
			}
			if (up.phase == Phase.BACK) {
				Vec3 want = up.backTo.subtract(mob.position());
				double len = want.length();
				if (len < 1.0 || now - up.start >= BACK_TICKS) {
					up.phase = Phase.FLARE;
					up.flareStart = now;
					up.end = now + FLARE_MIN + mob.getRandom().nextInt(FLARE_MAX - FLARE_MIN + 1);
					up.groundY = groundY(level, mob);
				} else {
					Vec3 v = want.scale(Math.min(BACK_SPEED, len) / len);
					mob.setDeltaMovement(v.x, v.y * 0.5, v.z);
					mob.syncVelocity = true;
					MagicFx.burst(level, mob.getX(), mob.getY() + 1.0, mob.getZ(), AURA_COLOR, 0.9F, 2, 0.2, 0.3, 0.2);
				}
				continue;
			}
			flare(level, mob, up, now);
			if (now >= up.end) {
				finish(level, mob);
				UPS.remove(mob);
			}
		}
	}

	private static double groundY(ServerLevel level, Mob mob) {
		BlockPos pos = mob.blockPosition();
		for (int i = 0; i < 12; i++) {
			if (!level.getBlockState(pos.below()).isAir()) {
				return pos.getY();
			}
			pos = pos.below();
		}
		return mob.getY();
	}

	/** One tick of the flare: hovering, rocks lifting, the ground shaking, the hum climbing. */
	private static void flare(ServerLevel level, Mob mob, Up up, long now) {
		double age = now - up.flareStart;
		float progress = Mth.clamp((float)(age / Math.max(1.0, up.end - up.flareStart)), 0.0F, 1.0F);
		double lift = mob.getY() < up.groundY + HOVER_HEIGHT ? HOVER_LIFT : 0.0;
		mob.setDeltaMovement(0.0, lift, 0.0);
		mob.syncVelocity = true;
		mob.resetFallDistance();
		Vec3 at = mob.position();
		// the aura: a swirl of ki round them, thickening
		MagicFx.burst(level, at.x, at.y + 1.0, at.z, AURA_COLOR, 1.0F + progress, 2 + (int)(progress * 4.0F), 0.45, 0.8, 0.45);
		if (now % 10 == 0) {
			MagicFx.ring(level, new Vec3(at.x, up.groundY + 0.1, at.z), null, 1.5F + 2.5F * progress, AURA_COLOR, MagicFx.RING_TICKS);
		}
		// rocks lift off the ground under them
		BlockState ground = Spectacle.ground(level, new Vec3(at.x, up.groundY + 0.3, at.z));
		if (ground != null && now % 2 == 0) {
			RandomHolder r = new RandomHolder(level);
			for (int i = 0; i < 2 + (int)(progress * 3.0F); i++) {
				double a = r.next() * Math.PI * 2.0, d = 0.6 + r.next() * 2.4;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, false, at.x + Math.cos(a) * d, up.groundY + 0.2,
					at.z + Math.sin(a) * d, 0, 0.0, 0.25 + r.next() * 0.35, 0.0, 1.0);
			}
		}
		// the ground shakes
		if (now % 4 == 0) {
			Shakes.around(level, at, SHAKE_RANGE, FLARE_SHAKE * (0.4F + 0.6F * progress), 6, null);
		}
		// the hum climbs
		if (now % 4 == 0) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F + 1.1F * progress);
		}
	}

	/** The build-up is over: a flash and a roar, and they are a form higher. */
	private static void finish(ServerLevel level, Mob mob) {
		release(mob);
		DONE.put(mob, Boolean.TRUE);
		Vec3 at = mob.position();
		MagicFx.blast(level, at.add(0.0, 1.0, 0.0), 3.0F, AURA_COLOR);
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), null, 5.0F, AURA_COLOR, MagicFx.RING_TICKS);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1.0, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.8F);
		Shakes.around(level, at, SHAKE_RANGE, FINISH_SHAKE);
		// a tyrant's owner takes the new form (ready); a patrolman or gifted villager gets the rank boost
		BOOSTED.put(mob, Boolean.TRUE);
	}

	/** Puts {@code mob} back on the ground and lets it walk. */
	private static void release(Mob mob) {
		mob.setNoGravity(false);
		mob.resetFallDistance();
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(ROOT_ID);
		}
		if (!mob.onGround()) {
			mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
		}
	}

	/** A blow big enough during the build-up cuts it short: no new form, a stagger. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (!(victim instanceof Mob mob) || !UPS.containsKey(mob) || amount < Math.max(INTERRUPT_MIN, victim.getMaxHealth() * INTERRUPT_SHARE)
			|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(mob.level() instanceof ServerLevel level)) {
			return true;
		}
		UPS.remove(mob);
		CUT.put(mob, Boolean.TRUE);
		release(mob);
		mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_LEVEL, false, false));
		Vec3 at = mob.position().add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(MagicFx.CRACKLE, at.x, at.y, at.z, 24, 0.5, 0.7, 0.5, 0.4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 0.8F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 1.2F, 1.4F);
		return true;
	}

	/** A random for the rocks without threading one through. */
	private record RandomHolder(ServerLevel level) {
		double next() {
			return this.level.getRandom().nextDouble();
		}
	}
}
