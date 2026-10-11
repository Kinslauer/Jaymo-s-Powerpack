package com.jaymospowers.power.chrono;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import java.util.ArrayDeque;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * An echo in the Nether fights for real. It's tougher there (a boss bar shows its health to everyone near), and it turns
 * its owner's own power on them: every Chrono ability its owner has unlocked, used the way a fighter would use it.
 * <ul>
 * <li><b>Rewind (R):</b> badly hurt, it snaps back to where it stood {@link #REWIND_TICKS} ago, with the health it had.</li>
 * <li><b>Stasis (G):</b> its target close, it stops time on them: frozen where they stand.</li>
 * <li><b>Overclock (V):</b> in a fight, it speeds itself up and drags everything near it down.</li>
 * <li><b>Time Bloom (B):</b> hurt, it mends itself over a few seconds.</li>
 * <li><b>Chrono Punch (C, if its owner is Timeless):</b> up close, a flurry of punches that ends in a throw.</li>
 * </ul>
 * With its owner offline it knows all four (it remembers them as it last saw them). Only killing it ends it.
 */
final class ChronoEchoFight {
	/** In the Nether its health is raised by this much (30 → 70). */
	static final double NETHER_HEALTH_BONUS = 40.0;
	/** Rewind: when it falls below this share of its health, back this far (ticks), then this long before again. */
	static final float REWIND_BELOW = 0.35F;
	static final int REWIND_TICKS = 60, REWIND_COOLDOWN = 400;
	/** Stasis: its target this close, frozen this long, then this long before again. */
	static final double STASIS_RANGE = 6.0;
	static final int STASIS_TICKS = 50, STASIS_COOLDOWN = 600;
	/** Overclock: this long, Speed amplifier, the drag on everything near it (radius, Slowness amplifier), and the wait. */
	static final int OVERCLOCK_TICKS = 120, OVERCLOCK_SPEED = 2, OVERCLOCK_COOLDOWN = 500;
	static final double OVERCLOCK_DRAG_RADIUS = 6.0;
	static final int OVERCLOCK_DRAG = 1;
	/** Below this share of its health, there's this chance it blinks out (once per appearance), to come back later. */
	static final float BLINK_BELOW = 0.5F, BLINK_CHANCE = 0.6F;
	/** Bloom: below this share of its health, this much healed every so many ticks for this long, then the wait. */
	static final float BLOOM_BELOW = 0.6F;
	static final float BLOOM_HEAL = 1.5F;
	static final int BLOOM_EVERY = 10, BLOOM_TICKS = 100, BLOOM_COOLDOWN = 800;
	/** Chrono Punch: reach, punches (one a tick), each punch's damage, the last one's throw, and the wait. */
	static final double PUNCH_REACH = 3.0;
	static final int PUNCHES = 10, PUNCH_COOLDOWN = 240;
	static final float PUNCH_DAMAGE = 1.0F;
	static final double PUNCH_THROW = 1.2;

	private static final Identifier HEALTH_MODIFIER = com.jaymospowers.JaymosPowers.id("echo_nether_health");
	private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFE08A, 1.0F);
	private static final DustParticleOptions SHARD = new DustParticleOptions(0xA8E8FF, 0.8F);

	private final ChronoEcho echo;
	/** Where it was and how hurt, each tick, for Rewind. */
	private final ArrayDeque<Vec3> positions = new ArrayDeque<>();
	private final ArrayDeque<Float> healths = new ArrayDeque<>();
	private int rewindReady, stasisReady, overclockReady, bloomReady, punchReady;
	private int overclockLeft, bloomLeft, punchStep = -1;
	private @Nullable ServerBossEvent bar;
	/** Whether it has dropped below {@link #BLINK_BELOW} this appearance (the blink is rolled once, on the way down). */
	private boolean belowHalf;

	ChronoEchoFight(ChronoEcho echo) {
		this.echo = echo;
	}

	/** Each server tick while it's in the Nether. */
	void tick(ServerLevel level) {
		toughen();
		record();
		@Nullable ServerPlayer owner = this.echo.owner() == null ? null : level.getServer().getPlayerList().getPlayer(this.echo.owner());
		int fx = ChronoFx.style(owner);
		LivingEntity target = this.echo.getTarget();
		boolean fighting = target != null && target.isAlive();
		updateBar(level, fighting);
		int now = this.echo.tickCount;
		float health = this.echo.getHealth() / this.echo.getMaxHealth();
		if (health < BLINK_BELOW && !this.belowHalf) {
			this.belowHalf = true;
			if (this.echo.getRandom().nextFloat() < BLINK_CHANCE) {
				end();
				this.echo.blinkAway(level);
				return;
			}
		}

		if (knows(owner, PowerKey.ABILITY_1) && health < REWIND_BELOW && now >= this.rewindReady && this.positions.size() >= REWIND_TICKS) {
			rewind(level, fx);
			this.rewindReady = now + REWIND_COOLDOWN;
		}
		if (knows(owner, PowerKey.ABILITY_4) && health < BLOOM_BELOW && now >= this.bloomReady) {
			this.bloomLeft = BLOOM_TICKS;
			this.bloomReady = now + BLOOM_COOLDOWN;
			level.playSound(null, this.echo.getX(), this.echo.getY(), this.echo.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 1.0F, 1.2F);
		}
		if (this.bloomLeft > 0) {
			this.bloomLeft--;
			if (this.bloomLeft % BLOOM_EVERY == 0) {
				this.echo.heal(BLOOM_HEAL);
				ChronoFx.particles(level, fx, ParticleTypes.HAPPY_VILLAGER, this.echo.getX(), this.echo.getY() + 1.0, this.echo.getZ(), 4, 0.4, 0.6, 0.4, 0.0);
			}
		}
		if (fighting && knows(owner, PowerKey.ABILITY_3) && now >= this.overclockReady) {
			this.echo.addEffect(new MobEffectInstance(MobEffects.SPEED, OVERCLOCK_TICKS, OVERCLOCK_SPEED, false, false, false));
			this.overclockLeft = OVERCLOCK_TICKS;
			this.overclockReady = now + OVERCLOCK_COOLDOWN;
			level.playSound(null, this.echo.getX(), this.echo.getY(), this.echo.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 0.8F, 2.0F);
			ChronoFx.particles(level, fx, ParticleTypes.WAX_ON, this.echo.getX(), this.echo.getY() + 1.0, this.echo.getZ(), 20, 0.4, 0.7, 0.4, 0.6);
		}
		if (this.overclockLeft > 0) {
			this.overclockLeft--;
			if (this.overclockLeft % 10 == 0) {
				drag(level, fx);
			}
		}
		if (fighting && knows(owner, PowerKey.ABILITY_2) && now >= this.stasisReady && this.echo.distanceTo(target) <= STASIS_RANGE
			&& this.echo.hasLineOfSight(target)) {
			stasis(level, fx, target);
			this.stasisReady = now + STASIS_COOLDOWN;
		}
		boolean timeless = owner == null || Fate.isAscended(owner);
		if (fighting && timeless && this.punchStep < 0 && now >= this.punchReady && this.echo.distanceTo(target) <= PUNCH_REACH) {
			this.punchStep = 0;
			this.punchReady = now + PUNCH_COOLDOWN;
			level.playSound(null, this.echo.getX(), this.echo.getY(), this.echo.getZ(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 1.0F, 1.6F);
		}
		if (this.punchStep >= 0) {
			punch(level, fx, owner, target);
		}
	}

	/** Its owner's unlocked abilities; with them offline, all of them. */
	private static boolean knows(@Nullable ServerPlayer owner, PowerKey key) {
		return owner == null || Mastery.isUnlocked(owner, key);
	}

	private void toughen() {
		AttributeInstance max = this.echo.getAttribute(Attributes.MAX_HEALTH);
		if (max != null && !max.hasModifier(HEALTH_MODIFIER)) {
			// saved with the echo, so a reload keeps its health
			max.addPermanentModifier(new AttributeModifier(HEALTH_MODIFIER, NETHER_HEALTH_BONUS, AttributeModifier.Operation.ADD_VALUE));
			float back = this.echo.returnHealth();
			this.echo.setHealth(back > 0.0F ? Math.min(back, this.echo.getMaxHealth()) : this.echo.getMaxHealth());
			// back already below half: it's here to finish the fight, no blinking away again
			this.belowHalf = back > 0.0F && back < this.echo.getMaxHealth() * BLINK_BELOW;
		}
	}

	/** Out of the Nether again: back to its usual self, no bar. */
	void end() {
		AttributeInstance max = this.echo.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.removeModifier(HEALTH_MODIFIER);
			this.echo.setHealth(Math.min(this.echo.getHealth(), this.echo.getMaxHealth()));
		}
		if (this.bar != null) {
			this.bar.removeAllPlayers();
			this.bar = null;
		}
	}

	private void record() {
		this.positions.addLast(this.echo.position());
		this.healths.addLast(this.echo.getHealth());
		while (this.positions.size() > REWIND_TICKS) {
			this.positions.removeFirst();
			this.healths.removeFirst();
		}
	}

	/** Everyone within 32 blocks sees its health while it fights. */
	private void updateBar(ServerLevel level, boolean fighting) {
		if (!fighting) {
			if (this.bar != null) {
				this.bar.removeAllPlayers();
			}
			return;
		}
		if (this.bar == null) {
			this.bar = new ServerBossEvent(this.echo.getUUID(), this.echo.getDisplayName(), BossEvent.BossBarColor.BLUE,
				BossEvent.BossBarOverlay.NOTCHED_10);
		}
		this.bar.setProgress(this.echo.getHealth() / this.echo.getMaxHealth());
		if (this.echo.tickCount % 20 == 0) {
			for (ServerPlayer player : level.players()) {
				if (player.distanceToSqr(this.echo) <= 32.0 * 32.0) {
					this.bar.addPlayer(player);
				} else {
					this.bar.removePlayer(player);
				}
			}
		}
	}

	private void rewind(ServerLevel level, int fx) {
		Vec3 from = this.echo.position();
		Vec3 to = this.positions.peekFirst();
		float then = this.healths.peekFirst();
		ChronoFx.particles(level, fx, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.4, 0.8, 0.4, 0.2);
		this.echo.teleportTo(to.x, to.y, to.z);
		this.echo.setHealth(Math.max(this.echo.getHealth(), then));
		this.echo.clearFire();
		this.positions.clear();
		this.healths.clear();
		ChronoFx.particles(level, fx, GOLD, to.x, to.y + 1.0, to.z, 20, 0.4, 0.8, 0.4, 0.0);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.HOSTILE, 1.0F, 0.7F);
	}

	private void stasis(ServerLevel level, int fx, LivingEntity target) {
		ChronoFlow.stun(target, STASIS_TICKS);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STASIS_TICKS, 6, false, false, true), this.echo);
		target.setDeltaMovement(Vec3.ZERO);
		target.syncVelocity = true;
		Vec3 c = target.position();
		for (int i = 0; i < 16; i++) {
			double a = i / 16.0 * Math.PI * 2.0;
			ChronoFx.particles(level, fx, i % 4 == 0 ? ParticleTypes.END_ROD : SHARD, c.x + Math.cos(a) * 1.2, c.y + 1.0, c.z + Math.sin(a) * 1.2,
				1, 0.0, 0.3, 0.0, 0.0);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.0F, 0.6F);
		if (target instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.chrono_echo_stasis"));
		}
	}

	private void drag(ServerLevel level, int fx) {
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, this.echo.getBoundingBox().inflate(OVERCLOCK_DRAG_RADIUS),
			e -> e != this.echo && e.isAlive() && !e.isSpectator() && !(e instanceof ChronoEcho)
				&& e.distanceToSqr(this.echo) <= OVERCLOCK_DRAG_RADIUS * OVERCLOCK_DRAG_RADIUS)) {
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, OVERCLOCK_DRAG, false, false, true), this.echo);
		}
		ChronoFx.particles(level, fx, ParticleTypes.ELECTRIC_SPARK, this.echo.getX(), this.echo.getY() + 0.2, this.echo.getZ(), 2, 0.25, 0.1, 0.25, 0.02);
	}

	/** One punch of the flurry a tick; the last throws its target. */
	private void punch(ServerLevel level, int fx, @Nullable ServerPlayer owner, @Nullable LivingEntity target) {
		int n = this.punchStep++;
		if (target == null || !target.isAlive() || this.echo.distanceTo(target) > PUNCH_REACH + 1.0 || n >= PUNCHES) {
			this.punchStep = -1;
			return;
		}
		boolean last = n == PUNCHES - 1;
		this.echo.getLookControl().setLookAt(target);
		this.echo.swing(n % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		target.setInvulnerableTime(0);
		Vec3 before = target.getDeltaMovement();
		if (target.hurtServer(level, this.echo.damageSources().mobAttack(this.echo), PUNCH_DAMAGE * (1.0F + 0.1F * n))) {
			Vec3 at = target.getBoundingBox().getCenter();
			if (last) {
				Vec3 away = target.position().subtract(this.echo.position()).horizontal();
				away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
				target.setDeltaMovement(away.scale(PUNCH_THROW).add(0.0, 0.45, 0.0));
				ChronoFlow.reel(target, ChronoFlow.FLUNG, 0);
				ChronoFx.particles(level, fx, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			} else {
				target.setDeltaMovement(before.scale(0.2));
				ChronoFlow.reel(target, ChronoFlow.FLINCH, n % 2 == 0 ? 1 : -1);
			}
			target.syncVelocity = true;
			ChronoFx.crackSound(level, owner, at.x, at.y, at.z);
			ChronoFx.particles(level, fx, ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.25, 0.3, 0.25, 0.25);
		}
		level.playSound(null, this.echo.getX(), this.echo.getY(), this.echo.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE,
			last ? 1.2F : 0.6F, 1.0F + n * 0.06F);
		if (last) {
			this.punchStep = -1;
		}
	}
}
