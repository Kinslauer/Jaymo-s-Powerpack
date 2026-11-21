package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * The vampire's claws (Andy's Awakening pick "Regenerator", 2026-11-17: "Add this to vampire"). A vampire or dhampir player:
 * <ul>
 * <li><b>never stops healing</b>: {@link #REGEN} every {@link #REGEN_EVERY} ticks, fed or not, fighting or not; a quarter as
 * often in the open sun or near a holy thing ({@link BloodLeap#weak});</li>
 * <li><b>claws</b>: a blow with an empty hand opens a wound, a stack of <b>bleeding</b> (to {@link #BLEED_MAX}): each stack
 * hurts {@link #BLEED_DAMAGE} a second, and {@link #BLEED_HEAL} of what the wounds bleed comes back to whoever opened them.
 * What has no blood (bones, golems) does not bleed.</li>
 * </ul>
 * And on the Claw key ({@link ArtKeys}):
 * <ul>
 * <li><b>Rake</b> (tap, a foe in reach): {@link #RAKE_HITS} quick slashes, each a wound; twice as hard on a pinned foe;</li>
 * <li><b>Rend</b> (sneak + tap): one tearing blow; a foe at {@link #REND_SHARE} of its health or less (never a great one) is
 * killed outright and its blood mends you;</li>
 * <li><b>Pounce</b> (sprint + tap at a foe): you fly at it and bear it down: it is <b>pinned</b> under you for
 * {@link #PIN_TICKS} ticks;</li>
 * <li><b>Roar of frenzy</b> (hold): everything near that can fear does, and for {@link #FRENZY_TICKS} ticks you are stronger
 * and quicker, your claws open two wounds a blow and you heal twice as fast.</li>
 * </ul>
 * In the sun or near a holy thing every one of them is weaker ({@link BloodLeap#WEAK_SHARE}).
 */
public final class ClawArts {
	// ---- tuning ----
	/** The healing that never stops: how much, how often (ticks), and how many times slower when weak. */
	static final float REGEN = 1.0F;
	static final int REGEN_EVERY = 40, REGEN_WEAK = 4;
	/** Bleeding: harm per stack each second, the beat, most stacks, ticks until they close after the last, the share healed, how near the healed must be. */
	static final float BLEED_DAMAGE = 0.5F, BLEED_HEAL = 0.5F;
	static final int BLEED_BEAT = 20, BLEED_MAX = 8, BLEED_FADE = 160;
	static final double BLEED_HEAL_RANGE = 24.0;
	/** Rake: reach, slashes, ticks between them, harm each, the factor on a pinned foe, cooldown. */
	static final double RAKE_REACH = 4.0;
	static final int RAKE_HITS = 3, RAKE_SPACING = 3, RAKE_COOLDOWN = 60;
	static final float RAKE_DAMAGE = 2.5F, RAKE_PINNED = 2.0F;
	/** Rend: reach, harm, wounds, the health share it kills at, the health a great foe has (never killed outright), what a kill heals, cooldown. */
	static final double REND_REACH = 3.5;
	static final float REND_DAMAGE = 7.0F, REND_SHARE = 0.25F, REND_GREAT = 100.0F, REND_HEAL = 6.0F;
	static final int REND_WOUNDS = 2, REND_COOLDOWN = 160;
	/** Pounce: reach, speed (blocks a tick), the longest it flies (ticks), how near counts as landed, the pin (ticks), harm, cooldown. */
	static final double POUNCE_REACH = 14.0, POUNCE_SPEED = 1.25, POUNCE_ARRIVE = 2.2;
	static final int POUNCE_TICKS = 24, PIN_TICKS = 50, POUNCE_COOLDOWN = 140;
	static final float POUNCE_DAMAGE = 3.0F;
	/** Roar: how far it is heard, how long the fright and the frenzy last (ticks), cooldown. */
	static final double ROAR_RANGE = 8.0;
	static final int ROAR_FEAR = 100, FRENZY_TICKS = 240, ROAR_COOLDOWN = 1800;
	/** What can't be frightened is slowed this long (ticks) at this level instead. The execute's blow is the foe's health times this. */
	static final int ROAR_SLOW_TICKS = 40, ROAR_SLOW_LEVEL = 1;
	static final float REND_OVERKILL = 4.0F;
	static final int BLOOD = 0x8A0A12;

	private static final class Bleed {
		int stacks;
		long last;
		UUID by;
	}

	private record Rake(ServerPlayer player, LivingEntity target, int left, long next) {
	}

	private record Pounce(LivingEntity target, long until) {
	}

	private static final Map<LivingEntity, Bleed> BLEEDS = new WeakHashMap<>();
	private static final List<Rake> RAKES = new ArrayList<>();
	private static final Map<UUID, Pounce> POUNCES = new HashMap<>();
	private static final Map<UUID, Long> FRENZY = new HashMap<>();
	/** Set while a move's own slash lands, so the claws' plain wound is not laid on top of the move's. */
	private static boolean slashing;

	private ClawArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ClawArts::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (!slashing && taken > 0.0F && !blocked && source.getEntity() instanceof ServerPlayer vampire && source.getDirectEntity() == vampire
				&& source.is(DamageTypes.PLAYER_ATTACK) && vampire.getMainHandItem().isEmpty() && Vampires.isVampire(vampire)) {
				bleed(victim, vampire, frenzied(vampire) ? 2 : 1);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BLEEDS.clear();
			RAKES.clear();
			POUNCES.clear();
			FRENZY.clear();
		});
	}

	static boolean frenzied(ServerPlayer player) {
		return FRENZY.getOrDefault(player.getUUID(), 0L) > player.level().getGameTime();
	}

	/** {@code harm}, less in the sun or near a holy thing. */
	private static float harm(ServerPlayer player, float harm) {
		return BloodLeap.weak(player) ? harm * (float) BloodLeap.WEAK_SHARE : harm;
	}

	// ---- bleeding -------------------------------------------------------------------------------------------------------------

	/** {@code by}'s claws open {@code wounds} more on {@code target}. */
	static void bleed(LivingEntity target, ServerPlayer by, int wounds) {
		if (!target.isAlive() || target == by || target.is(EntityTypeTags.SKELETONS) || target instanceof AbstractGolem) {
			return;
		}
		Bleed b = BLEEDS.computeIfAbsent(target, t -> new Bleed());
		b.stacks = Math.min(BLEED_MAX, b.stacks + wounds);
		b.last = target.level().getGameTime();
		b.by = by.getUUID();
	}

	/** The wounds bleeding on {@code target} now. */
	public static int wounds(LivingEntity target) {
		Bleed b = BLEEDS.get(target);
		return b == null ? 0 : b.stacks;
	}

	// ---- the key --------------------------------------------------------------------------------------------------------------

	static void key(ServerPlayer player, ArtKeys.Gesture gesture) {
		if (!Vampires.isVampire(player) || !(player.level() instanceof ServerLevel level) || player.isPassenger()) {
			return;
		}
		switch (gesture) {
			case TAP -> {
				LivingEntity foe = ArtKeys.sight(player, RAKE_REACH, e -> !ArtKeys.mine(player, e));
				if (foe == null) {
					ArtKeys.refuse(player, "no_foe_near");
				} else if (!ArtKeys.cooling(player, "claw_rake", RAKE_COOLDOWN)) {
					RAKES.add(new Rake(player, foe, RAKE_HITS, level.getGameTime()));
				}
			}
			case SNEAK -> {
				LivingEntity foe = ArtKeys.sight(player, REND_REACH, e -> !ArtKeys.mine(player, e));
				if (foe == null) {
					ArtKeys.refuse(player, "no_foe_near");
				} else if (!ArtKeys.cooling(player, "claw_rend", REND_COOLDOWN)) {
					rend(player, level, foe);
				}
			}
			case SPRINT -> {
				LivingEntity foe = ArtKeys.sight(player, POUNCE_REACH, e -> !ArtKeys.mine(player, e));
				if (foe == null) {
					ArtKeys.refuse(player, "no_foe");
				} else if (!ArtKeys.cooling(player, "claw_pounce", POUNCE_COOLDOWN)) {
					POUNCES.put(player.getUUID(), new Pounce(foe, level.getGameTime() + POUNCE_TICKS));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.PLAYERS, 1.0F, 0.8F);
				}
			}
			default -> {
				if (!ArtKeys.cooling(player, "claw_roar", ROAR_COOLDOWN)) {
					roar(player, level);
				}
			}
		}
	}

	private static void slash(ServerPlayer player, ServerLevel level, LivingEntity foe, float harm, int wounds, boolean left) {
		Vec3 centre = foe.getBoundingBox().getCenter();
		Vec3 look = player.getLookAngle();
		Vec3 side = new Vec3(-look.z, 0.0, look.x).scale(left ? 0.7 : -0.7);
		Fx.stroke(level, centre.add(side).add(0.0, 0.5, 0.0), centre.subtract(side).add(0.0, -0.5, 0.0), 0.25F, BLOOD);
		player.swing(left ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		foe.setInvulnerableTime(0);
		boolean hit;
		slashing = true;
		try {
			hit = foe.hurtServer(level, player.damageSources().playerAttack(player), harm(player, harm));
		} finally {
			slashing = false;
		}
		// (a blow that did nothing, on a player where players can't fight say, opens no wound)
		if (hit) {
			bleed(foe, player, wounds);
		}
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.5F);
	}

	private static void rend(ServerPlayer player, ServerLevel level, LivingEntity foe) {
		// (never a player, a boss, or anything great)
		boolean kill = foe.getMaxHealth() < REND_GREAT && !(foe instanceof Player) && !ArtKeys.great(foe)
			&& foe.getHealth() <= foe.getMaxHealth() * REND_SHARE && !BloodLeap.weak(player);
		slash(player, level, foe, kill ? Math.max(REND_DAMAGE, foe.getHealth() + foe.getAbsorptionAmount() + 1.0F) * REND_OVERKILL : REND_DAMAGE, REND_WOUNDS, true);
		if (kill && !foe.isAlive()) {
			player.heal(REND_HEAL);
			Fx.blast(level, foe.getBoundingBox().getCenter(), 1.0F, BLOOD);
			level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
	}

	private static void roar(ServerPlayer player, ServerLevel level) {
		FRENZY.put(player.getUUID(), level.getGameTime() + FRENZY_TICKS);
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FRENZY_TICKS, 0, true, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, FRENZY_TICKS, 0, true, false, true));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ROAR_RANGE), e -> ArtKeys.foe(player, e))) {
			Fear.frighten(e, player, ROAR_FEAR);
			if (e instanceof Mob mob && !Fear.frightened(mob)) {
				// what cannot fear is at least thrown off its stride
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ROAR_SLOW_TICKS, ROAR_SLOW_LEVEL), player);
			}
		}
		Fx.ring(level, player.position().add(0.0, 0.2, 0.0), (float) ROAR_RANGE, BLOOD);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.2F, 0.7F);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !Vampires.isVampire(player)) {
				POUNCES.remove(player.getUUID());
				continue;
			}
			// the healing that never stops
			int every = REGEN_EVERY * (BloodLeap.weak(player) ? REGEN_WEAK : 1) / (frenzied(player) ? 2 : 1);
			if ((player.tickCount + player.getId()) % every == 0 && player.getHealth() < player.getMaxHealth()) {
				player.heal(REGEN);
			}
			Pounce pounce = POUNCES.get(player.getUUID());
			if (pounce != null) {
				pounce(player, pounce);
			}
		}
		long now = server.overworld().getGameTime();
		// the rakes, one slash at a time
		List<Rake> next = new ArrayList<>();
		for (Iterator<Rake> it = RAKES.iterator(); it.hasNext();) {
			Rake r = it.next();
			if (!(r.player.level() instanceof ServerLevel level) || !r.player.isAlive() || !r.target.isAlive() || r.target.level() != level
				|| r.player.distanceToSqr(r.target) > (RAKE_REACH + 2.0) * (RAKE_REACH + 2.0)) {
				it.remove();
			} else if (level.getGameTime() >= r.next) {
				it.remove();
				slash(r.player, level, r.target, RAKE_DAMAGE * (HeldFast.held(r.target) ? RAKE_PINNED : 1.0F), 1, r.left % 2 == 0);
				if (r.left > 1) {
					next.add(new Rake(r.player, r.target, r.left - 1, level.getGameTime() + RAKE_SPACING));
				}
			}
		}
		RAKES.addAll(next);
		// the wounds bleed on a steady beat of their own, and what they bleed mends whoever opened them
		List<Runnable> hurts = new ArrayList<>();
		for (Iterator<Map.Entry<LivingEntity, Bleed>> it = BLEEDS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Bleed> e = it.next();
			LivingEntity target = e.getKey();
			Bleed b = e.getValue();
			if (!target.isAlive() || target.isRemoved() || !(target.level() instanceof ServerLevel level) || level.getGameTime() - b.last >= BLEED_FADE) {
				it.remove();
				continue;
			}
			if ((now + target.getId()) % BLEED_BEAT == 0) {
				float harm = b.stacks * BLEED_DAMAGE;
				ServerPlayer by = server.getPlayerList().getPlayer(b.by);
				hurts.add(() -> {
					target.setInvulnerableTime(0);
					if (target.hurtServer(level, level.damageSources().magic(), harm) && by != null && by.isAlive() && by.level() == level
						&& by.distanceToSqr(target) <= BLEED_HEAL_RANGE * BLEED_HEAL_RANGE) {
						by.heal(harm * BLEED_HEAL);
					}
				});
				PowersCompat.trailDrop(level, target, 0.3F);
				level.sendParticles(Fx.mote(BLOOD, 0.8F), target.getX(), target.getY(0.6), target.getZ(), 1 + b.stacks / 2, 0.2, 0.3, 0.2, 0.0);
			}
		}
		// (hurt after walking the map: a death in the middle of it would change the map under the walk)
		hurts.forEach(Runnable::run);
	}

	private static void pounce(ServerPlayer player, Pounce pounce) {
		ServerLevel level = (ServerLevel) player.level();
		LivingEntity target = pounce.target;
		if (!target.isAlive() || target.level() != level || level.getGameTime() >= pounce.until) {
			POUNCES.remove(player.getUUID());
			return;
		}
		Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition());
		if (to.length() > POUNCE_ARRIVE) {
			NightGifts.thrown(player);
			NightGifts.push(player, to.normalize().scale(POUNCE_SPEED * (BloodLeap.weak(player) ? BloodLeap.WEAK_SHARE : 1.0)));
			return;
		}
		POUNCES.remove(player.getUUID());
		NightGifts.push(player, Vec3.ZERO);
		target.setInvulnerableTime(0);
		boolean hit;
		slashing = true;
		try {
			hit = target.hurtServer(level, player.damageSources().playerAttack(player), harm(player, POUNCE_DAMAGE));
		} finally {
			slashing = false;
		}
		// borne down and pinned, if the blow told (a boss may shrug the pin off)
		if (hit) {
			if (!Legendary.shrugs(target)) {
				HeldFast.hold(target, PIN_TICKS);
				target.setDeltaMovement(Vec3.ZERO);
			}
			bleed(target, player, 1);
		}
		Fx.ring(level, target.position().add(0.0, 0.1, 0.0), 1.6F, BLOOD);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
	}
}
