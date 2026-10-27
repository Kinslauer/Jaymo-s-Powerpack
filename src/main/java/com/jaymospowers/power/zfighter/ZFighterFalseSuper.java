package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.48, Andy's NOEA picks) False Super ({@link ZFighterForms#FALSE_SUPER}, form 5): before a fighter's first Super
 * breakthrough, a near-death flare. Low in a hard fight (at or under {@link #TRIGGER_HEALTH} of his health, hurt by a
 * creature in the last five seconds), with {@link #MIN_HARDSHIP} of hardship behind him, he may flare into a golden,
 * unstable form: {@link #DURATION} ticks ({@link #WARRIOR_DURATION} for a Warrior-born), healed a little and stronger
 * than Super, but it flickers (every so often his strength and speed drop for a moment) and then gives out with a
 * stagger: weak, slowed, his ki all but gone. It fills the breakthrough meter faster than Rage does ({@link #FLARE_HARDSHIP}
 * at once, {@link #SURVIVE_HARDSHIP} more if he lives through it). Rare-ish: a roll ({@link #CHANCE}) every
 * {@link #ROLL_EVERY} ticks while he is that low, and {@link #COOLDOWN} ticks between flares (a Warrior-born's is
 * {@link #WARRIOR_COOLDOWN}, the blood being hotter). Only before Super; the real breakthrough ({@link ZFighterBattle})
 * ends it in a proper Super. Not in creative (the debug command starts one).
 */
public final class ZFighterFalseSuper {
	static final float TRIGGER_HEALTH = 0.2F;
	static final int MIN_HARDSHIP = 6;
	static final float CHANCE = 0.5F;
	static final int ROLL_EVERY = 100;
	/** How recently a creature must have hurt him for it to be a fight (ticks). */
	static final int IN_FIGHT_TICKS = 100;
	static final int DURATION = 500;
	static final int WARRIOR_DURATION = 640;
	/** Ticks between flares (20 minutes; 12 for the Warrior-born). */
	static final long COOLDOWN = 24000L;
	static final long WARRIOR_COOLDOWN = 14400L;
	/** Health the flare gives back, and the ki it starts him with. */
	static final float HEAL = 6.0F;
	static final float KI = 40.0F;
	/** Hardship: at once, and for living through it. Rage gives {@link ZFighterBattle#RAGE_HARDSHIP}. */
	static final int FLARE_HARDSHIP = 6;
	static final int SURVIVE_HARDSHIP = 4;
	/** Flicker: every so often (ticks, a range) his strength and speed fall for a moment; it comes faster in the last stretch. */
	static final int FLICKER_MIN = 50, FLICKER_SPREAD = 70, FLICKER_LAST = 120;
	static final int FLICKER_TICKS = 12;
	static final double FLICKER_FISTS = -0.4, FLICKER_SPEED = -0.15;
	/** The stagger when it gives out: slowness and weakness (ticks, amplifiers), and the ki he is left with. */
	static final int STAGGER_TICKS = 120;
	static final int STAGGER_SLOW = 2, STAGGER_WEAK = 1;
	static final float STAGGER_KI = 5.0F;
	static final float STAGGER_EXHAUSTION = 6.0F;

	private static final Identifier FLICKER_FIST_ID = JaymosPowers.id("zfighter_flare_flicker_fists");
	private static final Identifier FLICKER_SPEED_ID = JaymosPowers.id("zfighter_flare_flicker_speed");

	/** The game time he last flared (the cooldown). Saved, kept through death. */
	public static final AttachmentType<Long> LAST = AttachmentRegistry.create(JaymosPowers.id("zfighter_flare_last"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** A flare under way: when it began and ends, when he next flickers and until when the flicker lasts. */
	private static final class Flare {
		long since, until, nextFlicker, flickerUntil;
	}

	private static final Map<UUID, Flare> FLARES = new HashMap<>();
	/** When each was last rolled for. */
	private static final Map<UUID, Long> ROLLED = new HashMap<>();

	private ZFighterFalseSuper() {
	}

	static void init() {
	}

	static void clear() {
		FLARES.clear();
		ROLLED.clear();
	}

	static void forget(ServerPlayer player) {
		FLARES.remove(player.getUUID());
		ROLLED.remove(player.getUUID());
	}

	static boolean flaring(ServerPlayer player) {
		return FLARES.containsKey(player.getUUID());
	}

	/** Part of {@link ZFighterPower#refresh}: the flicker's drop in fists and speed. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		Flare flare = FLARES.get(player.getUUID());
		boolean flicker = flare != null && player.level().getGameTime() < flare.flickerUntil;
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FLICKER_FIST_ID, flicker ? FLICKER_FISTS : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, FLICKER_SPEED_ID, flicker ? FLICKER_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Every tick, per Z Fighter. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		UUID id = player.getUUID();
		Flare flare = FLARES.get(id);
		long now = player.level().getGameTime();
		if (flare != null) {
			running(player, live, flare, now);
			return;
		}
		if (live.form != 0 || live.superOn() || player.isCreative() || player.isSpectator() || ZFighterLineage.ape(player)
			|| Fate.isAscended(player) || ZFighterPower.hardship(player) < MIN_HARDSHIP) {
			return;
		}
		boolean inFight = player.getLastHurtByMob() != null && player.tickCount - player.getLastHurtByMobTimestamp() <= IN_FIGHT_TICKS;
		if (!inFight || player.getHealth() / player.getMaxHealth() > TRIGGER_HEALTH) {
			return;
		}
		Long last = player.getAttached(LAST);
		long cooldown = live.lineage == ZFighterLineage.WARRIOR ? WARRIOR_COOLDOWN : COOLDOWN;
		if (last != null && now - last < cooldown) {
			return;
		}
		Long rolled = ROLLED.get(id);
		if (rolled != null && now - rolled < ROLL_EVERY) {
			return;
		}
		ROLLED.put(id, now);
		if (player.getRandom().nextFloat() < CHANCE) {
			start(player, live);
		}
	}

	/**
	 * The flare begins (also the debug command). False when he is in a form already or is not a fighter at all. Heals him
	 * a little, gives him ki, ends any Rage, and fills hardship.
	 */
	static boolean start(ServerPlayer player, ZFighterPower.Live live) {
		if (live.form != 0 || ZFighterLineage.ape(player) || FLARES.containsKey(player.getUUID())) {
			return false;
		}
		long now = player.level().getGameTime();
		Flare flare = new Flare();
		flare.since = now;
		flare.until = now + (live.lineage == ZFighterLineage.WARRIOR ? WARRIOR_DURATION : DURATION);
		flare.nextFlicker = now + FLICKER_MIN + player.getRandom().nextInt(FLICKER_SPREAD);
		FLARES.put(player.getUUID(), flare);
		player.setAttached(LAST, now);
		// the beating he was taking is over, Rage and all
		live.rage = 0.0F;
		live.rageTicks = 0;
		live.rageCooldown = ZFighterBattle.RAGE_COOLDOWN;
		live.clutchFrom = -1L;
		live.form = ZFighterForms.FALSE_SUPER;
		live.formSince = now;
		player.heal(HEAL);
		live.ki = Math.min(ZFighterPower.maxKi(player), live.ki + KI);
		ZFighterPower.addHardship(player, FLARE_HARDSHIP);
		ZFighterPower.refresh(player, live);
		ServerLevel level = player.level();
		Vec3 c = player.position();
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE9A0), true, true, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(new DustParticleOptions(ZFighterForms.color(ZFighterForms.FALSE_SUPER), 2.0F), true, false, c.x, c.y + 1.0, c.z, 50, 0.9, 1.0, 0.9, 0.2);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, c.x, c.y + 1.0, c.z, 30, 0.6, 0.9, 0.6, 0.3);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.8F, 1.6F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.7F);
		com.jaymospowers.core.Shakes.heavy(player, c, 16.0, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_flare").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		return true;
	}

	/** One tick of the flare: the flicker, the sparks, and the end. */
	private static void running(ServerPlayer player, ZFighterPower.Live live, Flare flare, long now) {
		if (!player.isAlive()) {
			FLARES.remove(player.getUUID());
			return;
		}
		if (live.form != ZFighterForms.FALSE_SUPER) {
			// a real Super took it over (the breakthrough), or his ki ran dry and the form fell away: that one staggers
			FLARES.remove(player.getUUID());
			if (live.form == 0) {
				stagger(player, live);
			}
			ZFighterPower.refresh(player, live);
			return;
		}
		if (now >= flare.until) {
			end(player, live, true);
			return;
		}
		boolean last = flare.until - now <= FLICKER_LAST;
		if (now >= flare.nextFlicker && now >= flare.flickerUntil) {
			flare.flickerUntil = now + FLICKER_TICKS;
			flare.nextFlicker = flare.flickerUntil + (last ? FLICKER_MIN / 3 : FLICKER_MIN) + player.getRandom().nextInt(last ? FLICKER_SPREAD / 3 : FLICKER_SPREAD);
			ZFighterPower.refresh(player, live);
			ServerLevel level = player.level();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.5, 0.8, 0.5, 0.25);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35F, 1.9F);
			com.jaymospowers.core.Shakes.send(player, 0.35F, null);
		} else if (now == flare.flickerUntil) {
			ZFighterPower.refresh(player, live);
		}
		if (now % 4 == 0) {
			player.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.8, 0.4, 0.1);
		}
	}

	/** The flare is over: dropped by choice ({@code gaveOut} false) or run its course (true: the stagger and the hardship). */
	static void end(ServerPlayer player, ZFighterPower.Live live, boolean gaveOut) {
		if (FLARES.remove(player.getUUID()) == null) {
			return;
		}
		if (live.form == ZFighterForms.FALSE_SUPER) {
			ZFighterPower.endSuper(player, live);
		}
		if (gaveOut) {
			ZFighterPower.addHardship(player, SURVIVE_HARDSHIP);
			stagger(player, live);
		}
		ZFighterPower.refresh(player, live);
	}

	/** Gives out: the form drops, the ki is gone and he reels (a stagger on the poise bar, slow, weak, hungry). */
	private static void stagger(ServerPlayer player, ZFighterPower.Live live) {
		live.ki = Math.min(live.ki, STAGGER_KI);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STAGGER_TICKS, STAGGER_SLOW, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STAGGER_TICKS, STAGGER_WEAK, false, true));
		if (!player.isCreative()) {
			player.causeFoodExhaustion(STAGGER_EXHAUSTION);
		}
		ZFighterPoise.wear(player, ZFighterPoise.max(player));
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.POOF, true, false, player.getX(), player.getY() + 0.6, player.getZ(), 14, 0.4, 0.5, 0.4, 0.04);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.9F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_flare_end").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
