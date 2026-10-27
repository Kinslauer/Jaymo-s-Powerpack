package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.50, Andy's NOEA picks) Silver instinct: a state on top of the god form, costly and short. A quick tap of Z in the
 * god form (half mastered, {@link #MASTERY}) lets the body act before the mind: for {@link #DURATION} ticks every melee
 * blow and every projectile aimed at him is slipped ({@link #BASE_CHANCE}, up to {@link #BASE_CHANCE} +
 * {@link #MASTERY_CHANCE} with mastery; each slip costs {@link #DODGE_KI} ki), and a slipped blow is answered at once
 * with a counter ({@link #COUNTER_SHARE} of his punch cap, and poise worn). It drains {@link #DRAIN} ki a tick, ends
 * when his ki runs out, the god form drops, he taps Z again or the time is up, and cannot be called again for
 * {@link #COOLDOWN} ticks. Not as the Great Ape; the explosions, fire and falls it cannot read still hurt.
 * The silver is in the aura, the glow and the hair (the client reads {@link #ACTIVE}). Reuses {@link ZFighterSidestep}'s step.
 */
public final class ZFighterInstinct {
	/** The god-form mastery it needs (hundredths of a percent). */
	public static final int MASTERY = 5000;
	/** Ki to call it, and the drain a tick (12 a second) while it lasts. */
	static final int ENTER_KI = 60;
	static final float DRAIN = 0.6F;
	/** Ki for each blow slipped. */
	static final float DODGE_KI = 6.0F;
	/** How long it lasts, and how long before it can be called again (ticks): 20 s, 2 min. */
	static final int DURATION = 400;
	static final int COOLDOWN = 2400;
	/** The chance a blow is slipped with no mastery beyond the half needed, and what the rest of mastery adds. */
	static final float BASE_CHANCE = 0.8F;
	static final float MASTERY_CHANCE = 0.18F;
	/** A counter hits for this share of his punch cap, wears this much poise, and throws the foe this hard. */
	static final float COUNTER_SHARE = 0.8F;
	static final float COUNTER_POISE = 5.0F;
	static final double COUNTER_PUSH = 0.9;
	/** At most one counter on the same foe in this many ticks. */
	static final int COUNTER_SPACING = 8;
	/** How far he steps aside, and the blur it leaves. */
	static final double STEP = 0.5;
	/** A tap of Z shorter than this (ticks) is a tap; longer is the start of a flight. */
	static final int TAP_TICKS = 8;
	/** The silver of it. */
	public static final int COLOR = 0xDCE6F5;

	/** Silver instinct is on, for everyone's client (aura, glow and hair go silver). Not saved. */
	public static final AttachmentType<Boolean> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("zfighter_instinct"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Until when each fighter's instinct lasts, when each may call it again, and when Z went down (and whether he was flying). */
	private static final Map<UUID, Long> UNTIL = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, long[]> PRESSED = new HashMap<>();
	private static final Map<UUID, Long> COUNTERED = new HashMap<>();

	private ZFighterInstinct() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !active(player)) {
				return true;
			}
			boolean melee = source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by && by != player
				&& !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.BYPASSES_ARMOR);
			boolean shot = source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
			if (!melee && !shot) {
				return true;
			}
			ZFighterPower.Live live = ZFighterPower.live(player);
			float chance = BASE_CHANCE + MASTERY_CHANCE * Mth.clamp((ZFighterGod.mastery(player) - MASTERY) / (float)(ZFighterTraining.MASTERY_MAX - MASTERY), 0.0F, 1.0F);
			if (live.guarding || ZFighterPoise.stunned(player) || player.getRandom().nextFloat() >= chance) {
				return true;
			}
			if (!player.isCreative()) {
				if (live.ki < DODGE_KI) {
					end(player, live, true);
					return true;
				}
				live.ki -= DODGE_KI;
			}
			slip(player, source.getDirectEntity(), source.getEntity(), melee);
			return false;
		});
	}

	static void clear() {
		UNTIL.clear();
		READY.clear();
		PRESSED.clear();
		COUNTERED.clear();
	}

	static void forget(ServerPlayer player) {
		UUID id = player.getUUID();
		UNTIL.remove(id);
		READY.remove(id);
		PRESSED.remove(id);
		COUNTERED.remove(id);
	}

	public static boolean active(Player player) {
		return Boolean.TRUE.equals(player.getAttached(ACTIVE));
	}

	/**
	 * Z in the god form: the press is noted (and left to the fusion dance's tap as before); a quick release with the form
	 * still the god's, and nothing else going on, is a tap and answers here. True when this took the release.
	 */
	static boolean tapped(ServerPlayer player, ZFighterPower.Live live, boolean down) {
		UUID id = player.getUUID();
		long now = player.level().getGameTime();
		if (down) {
			PRESSED.put(id, new long[] {now, live.flying ? 1L : 0L});
			return false;
		}
		long[] pressed = PRESSED.remove(id);
		if (pressed == null || live.form != ZFighterForms.GOD || !Powers.has(player, ZFighterPower.INSTANCE) || now - pressed[0] > TAP_TICKS
			|| (pressed[1] == 1L) != live.flying || ZFighterLineage.ape(player)) {
			return false;
		}
		if (active(player)) {
			end(player, live, false);
			return true;
		}
		call(player, live, now);
		return true;
	}

	private static void call(ServerPlayer player, ZFighterPower.Live live, long now) {
		if (ZFighterGod.mastery(player) < MASTERY && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_instinct_unmastered", MASTERY / 100).withStyle(ChatFormatting.GRAY));
			return;
		}
		Long ready = READY.get(player.getUUID());
		if (ready != null && now < ready && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_instinct_rest", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (live.ki < ENTER_KI && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.isCreative()) {
			live.ki -= ENTER_KI;
		}
		UNTIL.put(player.getUUID(), now + DURATION);
		player.setAttached(ACTIVE, true);
		live.silver = true;
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(COLOR, 1.8F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.15);
		level.sendParticles(ParticleTypes.END_ROD, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.8, 0.4, 0.1);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_instinct_on").withStyle(ChatFormatting.AQUA));
	}

	/** It is over: by choice, by the clock, by his ki or because the god form went. The cooldown starts. */
	static void end(ServerPlayer player, ZFighterPower.Live live, boolean spent) {
		if (UNTIL.remove(player.getUUID()) == null && !active(player)) {
			return;
		}
		READY.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
		player.removeAttached(ACTIVE);
		live.silver = false;
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
		if (spent) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_instinct_spent").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Every tick: the drain, the clock, and the silver wisps. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (!active(player)) {
			live.silver = false;
			return;
		}
		live.silver = true;
		long now = player.level().getGameTime();
		Long until = UNTIL.get(player.getUUID());
		if (until == null || now >= until || live.form != ZFighterForms.GOD || ZFighterLineage.ape(player)) {
			end(player, live, false);
			return;
		}
		if (!player.isCreative()) {
			live.ki -= DRAIN;
			if (live.ki <= 0.0F) {
				end(player, live, true);
				return;
			}
		}
		if (now % 3 == 0) {
			player.level().sendParticles(new DustParticleOptions(COLOR, 1.0F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.8,
				0.35, 0.02);
		}
	}

	/** A blow slipped: a step aside, a blur, and (for a melee blow) the counter. */
	private static void slip(ServerPlayer player, net.minecraft.world.entity.Entity direct, net.minecraft.world.entity.Entity by, boolean melee) {
		ServerLevel level = player.level();
		Vec3 from = direct != null ? direct.position() : by != null ? by.position() : player.position().add(player.getLookAngle());
		Vec3 away = player.position().subtract(from);
		Vec3 side = new Vec3(-away.z, 0.0, away.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize().scale(player.getRandom().nextBoolean() ? STEP : -STEP);
		player.setDeltaMovement(player.getDeltaMovement().add(side));
		player.syncVelocity = true;
		Vec3 at = player.position();
		DustParticleOptions blur = new DustParticleOptions(COLOR, 1.3F);
		for (double h = 0.1; h < player.getBbHeight(); h += 0.35) {
			level.sendParticles(blur, true, false, at.x, at.y + h, at.z, 2, 0.15, 0.05, 0.15, 0.0);
		}
		level.sendParticles(ParticleTypes.POOF, true, false, at.x, at.y + 0.8, at.z, 4, 0.2, 0.3, 0.2, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 1.9F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.5F, 2.0F);
		// (0.17.56) the slip is a quick sway and an afterimage
		ZFighterSonicSway.flick(player, from);
		if (melee && by instanceof LivingEntity foe) {
			counter(player, foe);
		}
	}

	/** The answer: a snap blow on whoever swung, poise worn, and thrown back. */
	private static void counter(ServerPlayer player, LivingEntity foe) {
		long now = player.level().getGameTime();
		Long last = COUNTERED.get(foe.getUUID());
		if (last != null && now - last < COUNTER_SPACING || !foe.isAlive()) {
			return;
		}
		COUNTERED.put(foe.getUUID(), now);
		if (COUNTERED.size() > 64) {
			COUNTERED.values().removeIf(t -> now - t > COUNTER_SPACING);
		}
		ServerLevel level = player.level();
		foe.hurtServer(level, ZFighterKi.source(player), COUNTER_SHARE * ZFighterBattle.punchCap(player));
		ZFighterPoise.wear(foe, COUNTER_POISE);
		ZFighterKi.push(foe, foe.position().subtract(player.position()), COUNTER_PUSH, 0.25);
		Vec3 c = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, true, false, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(new DustParticleOptions(COLOR, 1.4F), true, false, c.x, c.y, c.z, 8, 0.25, 0.25, 0.25, 0.1);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.5F);
	}
}
