package com.jaymospowers.power.xenoform.nadir;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Nadir's Command (B): the void writes its own rules. Each of {@link #SLOTS} rules is a sentence, WHO + WHAT + WHEN
 * ("what I hit / burns / while sprinting"), set on the Rules screen; up to {@link #MAX_ON} hold at once, and they hold
 * only while Nadir is out. The rules are kept on the player ({@link #RULES}), so the next Nadir finds them as they were.
 * <ul>
 * <li>About me, the creatures near me, or what I look at: the rule is worked every {@link #PULSE} ticks while its WHEN
 * holds (an instant word, such as a push or a burst, no more than every {@link #INSTANT_EVERY}).</li>
 * <li>What I hit, or what hits me: the rule takes hold of each creature as the blow lands, no more than every
 * {@link #HIT_EVERY} ticks for the same one.</li>
 * </ul>
 * A from-scratch take on the "rules" idea of The Awakening's Command power (an all-rights-reserved mod: nothing of its
 * code or art is used).
 */
public final class NadirRules {
	// ---- tuning ----
	public static final int SLOTS = 3, MAX_ON = 2;
	/** Effects are refreshed this often (ticks), and last this long, so they hold without flicker. */
	public static final int PULSE = 20, EFFECT_TICKS = 50;
	/** An instant word acting on me or around me fires no more often than this; on a struck creature, this. */
	public static final int INSTANT_EVERY = 40, HIT_EVERY = 20;
	/** "Creatures near me" reach this far; "what I look at" this far. */
	public static final double NEAR = 8.0, LOOK = 24.0;
	/** "Bursts": void blast radius and damage; "feels my pain": damage; push and pull strength. */
	public static final double BURST_RADIUS = 3.0;
	public static final float BURST_DAMAGE = 5.0F, PAIN_DAMAGE = 4.0F;
	public static final double PUSH = 1.4, PULL = 1.1;

	/** WHO the rule is about. */
	public static final int WHO_ME = 0, WHO_HIT = 1, WHO_HITS_ME = 2, WHO_NEAR = 3, WHO_LOOK = 4;
	public static final String[] WHO = {"me", "hit", "hits_me", "near", "look"};
	/** WHAT happens to them. */
	public static final int DARK = 0, SWIFT = 1, SPRING = 2, SOFT = 3, BURNS = 4, GLOWS = 5, WEAK = 6, STILL = 7, PUSHED = 8,
		PULLED = 9, WEIGHTLESS = 10, HEAVY = 11, MENDS = 12, UNSEEN = 13, BURSTS = 14, PAIN = 15;
	public static final String[] WHAT = {"dark", "swift", "spring", "soft", "burns", "glows", "weak", "still", "pushed",
		"pulled", "weightless", "heavy", "mends", "unseen", "bursts", "pain"};
	/** WHEN it holds. */
	public static final int ALWAYS = 0, SNEAKING = 1, SPRINTING = 2, AIRBORNE = 3, NIGHT = 4, HURT = 5, UNDERGROUND = 6, STILLED = 7;
	public static final String[] WHEN = {"always", "sneaking", "sprinting", "airborne", "night", "hurt", "underground", "standing"};

	/** Four numbers a rule: who, what, when, on (1) or off (0). */
	public static final int FIELDS = 4;
	public static final List<Integer> DEFAULT = List.of(
		WHO_ME, DARK, ALWAYS, 0,
		WHO_HIT, BURNS, ALWAYS, 0,
		WHO_HITS_ME, PUSHED, ALWAYS, 0);

	public static final AttachmentType<List<Integer>> RULES = AttachmentRegistry.create(JaymosPowers.id("nadir_rules"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

	private NadirRules() {
	}

	/** The player's rules, always {@link #SLOTS} whole ones. */
	public static List<Integer> get(ServerPlayer player) {
		return clean(player.getAttachedOrElse(RULES, DEFAULT));
	}

	/** What the Rules screen sent: checked, and no more than {@link #MAX_ON} switched on. */
	public static void set(ServerPlayer player, List<Integer> rules) {
		player.setAttached(RULES, clean(rules));
	}

	/** {@code rules} made whole: each number in range, missing rules from {@link #DEFAULT}, the extra "on"s turned off. */
	public static List<Integer> clean(List<Integer> rules) {
		List<Integer> out = new ArrayList<>(SLOTS * FIELDS);
		int on = 0;
		for (int i = 0; i < SLOTS * FIELDS; i++) {
			int v = i < rules.size() ? rules.get(i) : DEFAULT.get(i);
			int max = switch (i % FIELDS) {
				case 0 -> WHO.length;
				case 1 -> WHAT.length;
				case 2 -> WHEN.length;
				default -> 2;
			};
			v = v < 0 || v >= max ? DEFAULT.get(i) : v;
			if (i % FIELDS == 3 && v == 1 && ++on > MAX_ON) {
				v = 0;
			}
			out.add(v);
		}
		return out;
	}

	// ---- the rules at work ----------------------------------------------------------------------------------------

	/** Every tick while Nadir is out: the rules about me, around me, and what I look at. */
	public static void tick(ServerPlayer player, NadirState st) {
		List<Integer> rules = get(player);
		long now = player.level().getGameTime();
		for (int slot = 0; slot < SLOTS; slot++) {
			int base = slot * FIELDS;
			int who = rules.get(base), what = rules.get(base + 1), when = rules.get(base + 2);
			if (rules.get(base + 3) == 0 || who == WHO_HIT || who == WHO_HITS_ME || !holds(player, when)) {
				continue;
			}
			boolean instant = instant(what);
			if (instant ? now < st.ruleNext[slot] : now % PULSE != slot) {
				continue;
			}
			if (instant) {
				st.ruleNext[slot] = now + INSTANT_EVERY;
			}
			List<LivingEntity> targets = switch (who) {
				case WHO_ME -> List.of(player);
				case WHO_NEAR -> NadirAim.around(player, player.position().add(0.0, 1.0, 0.0), NEAR);
				default -> {
					LivingEntity aimed = NadirAim.aimed(player, LOOK);
					yield aimed == null ? List.of() : List.of(aimed);
				}
			};
			for (LivingEntity target : targets) {
				work(player, st, target, what);
			}
		}
	}

	/** The player, as Nadir, just hurt {@code target}: the "what I hit" rules take hold of it. */
	public static void onHit(ServerPlayer player, NadirState st, LivingEntity target) {
		struck(player, st, target, WHO_HIT);
	}

	/** {@code attacker} is hurting the player, as Nadir: the "what hits me" rules take hold of it. */
	public static void onHurtBy(ServerPlayer player, NadirState st, LivingEntity attacker) {
		struck(player, st, attacker, WHO_HITS_ME);
	}

	private static void struck(ServerPlayer player, NadirState st, LivingEntity target, int whoWanted) {
		if (st.ruling || target == player || !target.isAlive()) {
			return;
		}
		List<Integer> rules = get(player);
		long now = player.level().getGameTime();
		for (int slot = 0; slot < SLOTS; slot++) {
			int base = slot * FIELDS;
			if (rules.get(base + 3) == 0 || rules.get(base) != whoWanted || !holds(player, rules.get(base + 2))) {
				continue;
			}
			long key = (long)target.getId() * SLOTS + slot;
			if (st.ruleHits.getOrDefault(key, 0L) > now) {
				continue;
			}
			st.ruleHits.put(key, now + HIT_EVERY);
			work(player, st, target, rules.get(base + 1));
		}
		if (st.ruleHits.size() > 256) {
			st.ruleHits.values().removeIf(t -> t <= now);
		}
	}

	/** Whether WHEN holds for the player right now. */
	public static boolean holds(ServerPlayer player, int when) {
		ServerLevel level = player.level();
		return switch (when) {
			case SNEAKING -> player.isShiftKeyDown();
			case SPRINTING -> player.isSprinting();
			case AIRBORNE -> !player.onGround();
			case NIGHT -> level.isDarkOutside();
			case HURT -> player.getHealth() < player.getMaxHealth() * 0.5F;
			case UNDERGROUND -> !level.canSeeSky(BlockPos.containing(player.getEyePosition()));
			case STILLED -> player.getDeltaMovement().horizontalDistanceSqr() < 1.0E-3;
			default -> true;
		};
	}

	/** Whether {@code what} happens all at once (and so is spaced out), rather than as a lasting effect. */
	public static boolean instant(int what) {
		return what == BURNS || what == PUSHED || what == PULLED || what == HEAVY || what == BURSTS || what == PAIN;
	}

	/** The word, worked on {@code target}. */
	private static void work(ServerPlayer player, NadirState st, LivingEntity target, int what) {
		ServerLevel level = player.level();
		boolean self = target == player;
		st.ruling = true;
		try {
			switch (what) {
				case DARK -> effect(target, MobEffects.NIGHT_VISION, 0, EFFECT_TICKS + 200);
				case SWIFT -> effect(target, MobEffects.SPEED, 1, EFFECT_TICKS);
				case SPRING -> effect(target, MobEffects.JUMP_BOOST, 1, EFFECT_TICKS);
				case SOFT -> effect(target, MobEffects.SLOW_FALLING, 0, EFFECT_TICKS);
				case BURNS -> target.igniteForSeconds(4.0F);
				case GLOWS -> effect(target, MobEffects.GLOWING, 0, EFFECT_TICKS);
				case WEAK -> {
					effect(target, MobEffects.WEAKNESS, 1, EFFECT_TICKS);
					effect(target, MobEffects.MINING_FATIGUE, 0, EFFECT_TICKS);
				}
				case STILL -> {
					effect(target, MobEffects.SLOWNESS, 6, EFFECT_TICKS);
					target.setDeltaMovement(0.0, Math.min(0.0, target.getDeltaMovement().y), 0.0);
					target.syncVelocity = true;
				}
				case PUSHED, PULLED -> {
					if (!self) {
						Vec3 away = target.position().subtract(player.position()).horizontal();
						away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().horizontal() : away.normalize();
						target.setDeltaMovement(target.getDeltaMovement().add(away.scale(what == PUSHED ? PUSH : -PULL)).add(0.0, 0.3, 0.0));
						target.syncVelocity = true;
					}
				}
				case WEIGHTLESS -> effect(target, MobEffects.LEVITATION, 1, 30);
				case HEAVY -> {
					target.setDeltaMovement(target.getDeltaMovement().x * 0.3, -1.5, target.getDeltaMovement().z * 0.3);
					target.syncVelocity = true;
					effect(target, MobEffects.SLOWNESS, 2, 30);
				}
				case MENDS -> effect(target, MobEffects.REGENERATION, 1, EFFECT_TICKS + 40);
				case UNSEEN -> effect(target, MobEffects.INVISIBILITY, 0, EFFECT_TICKS);
				case BURSTS -> burst(player, target.position().add(0.0, target.getBbHeight() * 0.5, 0.0));
				case PAIN -> {
					if (!self) {
						target.hurtServer(level, player.damageSources().playerAttack(player), PAIN_DAMAGE);
						level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight(), target.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
					}
				}
				default -> {
				}
			}
		} finally {
			st.ruling = false;
		}
		if (!self && what != BURSTS) {
			level.sendParticles(ParticleTypes.ENCHANT, target.getX(), target.getY() + target.getBbHeight() + 0.3, target.getZ(), 6, 0.25, 0.1, 0.25, 0.4);
		}
	}

	private static void effect(LivingEntity target, Holder<MobEffect> effect, int amplifier, int ticks) {
		target.addEffect(new MobEffectInstance(effect, ticks, amplifier, false, false, true));
	}

	/** A burst of the void at {@code at}: hurts and throws everything near it but Nadir and what Nadir spares. */
	private static void burst(ServerPlayer player, Vec3 at) {
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.3F);
		for (LivingEntity e : NadirAim.around(player, at, BURST_RADIUS)) {
			e.hurtServer(level, player.damageSources().playerAttack(player), BURST_DAMAGE);
			Vec3 push = e.position().subtract(at).horizontal();
			push = push.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : push.normalize();
			e.setDeltaMovement(e.getDeltaMovement().add(push.scale(0.8)).add(0.0, 0.35, 0.0));
			e.syncVelocity = true;
		}
	}
}
