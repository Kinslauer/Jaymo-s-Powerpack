package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * (0.17.48, Andy's NOEA picks) The Ego state: a dark mirror of himself. It opens through Corruption: sneak+C, either from
 * Corrupted mode ({@link ZFighterCorruption}; he keeps that mode under it) or, before he is corrupted, once a dark lesson's
 * worth of corruption has gathered ({@link #OPENS}). In it he hits {@link #FIST} harder and, for every blow he lands
 * ({@link #DEALT} stack) and every blow he takes ({@link #TAKEN} stacks and more for a hard one), grows stronger: each stack
 * is {@link #FIST_PER_STACK} fists and {@link #KI_PER_STACK} ki power, up to {@link #CAP} stacks; they bleed away
 * while no blow lands ({@link #DECAY_EVERY} ticks a stack). The longer he holds it the more it feeds his Corruption
 * ({@link #FEED} every {@link #FEED_EVERY} ticks: past a full meter, he is corrupted for good). It gives out after
 * {@link #MAX_TICKS}; a Super, the Great Ape or low health end it sooner. Dark purple aura, on everyone's screen.
 */
public final class ZFighterEgo {
	/** Corruption (of {@link Fate#MAX_CORRUPTION}) it takes to open Ego before he is corrupted: a dark lesson's worth. */
	static final int OPENS = ZFighterCorruption.LESSON_CORRUPTION;
	/** In it: fists and speed (added to the multiplier), ki power (times). */
	static final double FIST = 0.10;
	static final double SPEED = 0.03;
	static final float KI = 1.08F;
	/** Stacks: one for a blow landed, this many for a blow taken (plus one per this much damage); the cap. */
	static final int DEALT = 1;
	static final int TAKEN = 2;
	static final float TAKEN_DAMAGE_PER_STACK = 4.0F;
	static final int CAP = 40;
	/** What a stack gives: fists, ki power, speed. */
	static final double FIST_PER_STACK = 0.012;
	static final float KI_PER_STACK = 0.012F;
	static final double SPEED_PER_STACK = 0.0008;
	/** A stack bleeds away this often (ticks) once no blow has landed for this long. */
	static final int DECAY_EVERY = 80;
	static final int DECAY_AFTER = 100;
	/** Corruption fed, every this many ticks held. */
	static final int FEED = 1;
	static final int FEED_EVERY = 100;
	/** It gives out after this long (2 minutes), and can't be called again for this long. */
	static final int MAX_TICKS = 2400;
	static final int COOLDOWN = 1200;
	/** He needs this much health to open it. */
	static final float LEAST_HEALTH = 6.0F;
	/** The aura (RGB). */
	public static final int COLOR = 0x7A34C8;
	static final int SHADOW_COLOR = 0x240838;

	/** Ego is on, for everyone's client (the aura). Not saved. */
	public static final AttachmentType<Boolean> MODE = AttachmentRegistry.create(JaymosPowers.id("zfighter_ego"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	private static final Identifier FIST_ID = JaymosPowers.id("zfighter_ego_fists");
	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_ego_speed");

	/** One Ego: the stacks, when it opened, when a blow last landed or was taken, when a stack last bled away. */
	private static final class Ego {
		int stacks;
		long since;
		long lastBlow;
		long lastDecay;
		long lastFeed;
	}

	private static final Map<UUID, Ego> ACTIVE = new HashMap<>();
	/** When he may call it again (game time). */
	private static final Map<UUID, Long> RESTS = new HashMap<>();

	private ZFighterEgo() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer player && entity != player) {
				gain(player, DEALT);
			}
			if (entity instanceof ServerPlayer player) {
				gain(player, TAKEN + (int)(damageTaken / TAKEN_DAMAGE_PER_STACK));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ACTIVE.clear();
			RESTS.clear();
		});
	}

	public static boolean on(Player player) {
		return Boolean.TRUE.equals(player.getAttached(MODE));
	}

	/** Stacks he holds now (0 when Ego is off). */
	static int stacks(Player player) {
		Ego ego = ACTIVE.get(player.getUUID());
		return ego == null || !on(player) ? 0 : ego.stacks;
	}

	private static void gain(ServerPlayer player, int stacks) {
		Ego ego = ACTIVE.get(player.getUUID());
		if (ego == null || !on(player)) {
			return;
		}
		int before = ego.stacks;
		ego.stacks = Math.min(CAP, ego.stacks + stacks);
		ego.lastBlow = player.level().getGameTime();
		ego.lastDecay = ego.lastBlow;
		if (ego.stacks != before) {
			ZFighterPower.refresh(player, ZFighterPower.live(player));
			if (ego.stacks == CAP) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ego_full").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}

	/** Sneak+C: Ego off if it is on; on if it is his to open. False when the key is not Ego's to answer. */
	static boolean toggle(ServerPlayer player, ZFighterPower.Live live) {
		if (!ZFighterBranchForms.fighter(player)) {
			return false;
		}
		if (on(player)) {
			end(player, live, false);
			return true;
		}
		boolean corrupted = ZFighterCorruption.corrupted(player);
		boolean opens = corrupted ? ZFighterCorruption.mode(player) : Fate.corruption(player) >= OPENS;
		if (!opens) {
			return false;
		}
		long now = player.level().getGameTime();
		Long rests = RESTS.get(player.getUUID());
		// (resting or too hurt, a corrupted fighter's key goes on to leave Corrupted mode as it always did)
		if (rests != null && rests > now && !player.isCreative()) {
			if (!corrupted) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ego_rest", (rests - now) / 20).withStyle(ChatFormatting.GRAY));
			}
			return !corrupted;
		}
		if (player.getHealth() <= LEAST_HEALTH || live.superOn() || ZFighterLineage.ape(player)) {
			if (!corrupted) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ego_cannot").withStyle(ChatFormatting.GRAY));
			}
			return !corrupted;
		}
		Ego ego = new Ego();
		ego.since = now;
		ego.lastBlow = now;
		ego.lastDecay = now;
		ego.lastFeed = now;
		ACTIVE.put(player.getUUID(), ego);
		player.setAttached(MODE, true);
		ZFighterPower.refresh(player, live);
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(COLOR, 2.2F), true, false, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.9, 1.0, 0.9, 0.15);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.9, 0.5, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ego_on").withStyle(ChatFormatting.DARK_PURPLE));
		return true;
	}

	/** Ego ends: a key, a form, the ape, the clock or low health; the mirror cracks and he must rest before he calls it again. */
	static void end(ServerPlayer player, ZFighterPower.Live live, boolean spent) {
		if (!on(player)) {
			return;
		}
		player.removeAttached(MODE);
		ACTIVE.remove(player.getUUID());
		RESTS.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
		ZFighterPower.refresh(player, live);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		if (spent) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_ego_spent").withStyle(ChatFormatting.DARK_PURPLE));
		}
	}

	static void clear(ServerPlayer player) {
		player.removeAttached(MODE);
		ACTIVE.remove(player.getUUID());
		RESTS.remove(player.getUUID());
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Every tick of the power: the clock, the bleeding of stacks, the feed, the shadows round him. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		if (!on(player)) {
			if (ACTIVE.containsKey(player.getUUID())) {
				ACTIVE.remove(player.getUUID());
			}
			return;
		}
		Ego ego = ACTIVE.get(player.getUUID());
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (ego == null) {
			// (after a restart the mirror is gone from memory: let the attachment go too)
			player.removeAttached(MODE);
			ZFighterPower.refresh(player, live);
			return;
		}
		if (live.superOn() || ZFighterLineage.ape(player) || player.getHealth() <= 2.0F && !player.isCreative()) {
			end(player, live, false);
			return;
		}
		if (now - ego.since >= MAX_TICKS) {
			end(player, live, true);
			return;
		}
		if (ego.stacks > 0 && now - ego.lastBlow >= DECAY_AFTER && now - ego.lastDecay >= DECAY_EVERY) {
			ego.stacks--;
			ego.lastDecay = now;
			ZFighterPower.refresh(player, live);
		}
		if (now - ego.lastFeed >= FEED_EVERY) {
			ego.lastFeed = now;
			boolean was = Fate.isCorrupted(player);
			Fate.addCorruption(player, FEED);
			if (!was && Fate.isCorrupted(player)) {
				ZFighterPower.refresh(player, live);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_corrupted").withStyle(ChatFormatting.DARK_RED));
			}
		}
		if (now % 2 == 0) {
			double reach = 0.5 + 0.02 * ego.stacks;
			level.sendParticles(new DustParticleOptions(now % 4 == 0 ? COLOR : SHADOW_COLOR, 1.6F + 0.02F * ego.stacks), true, false, player.getX(),
				player.getY() + 0.9, player.getZ(), 3, reach, 0.7, reach, 0.0);
			if (now % 6 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, true, false, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.7, 0.4, 0.02);
			}
		}
	}

	/** What Ego adds to his fists and speed, refreshed with the rest of his attributes. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		boolean on = on(player);
		int stacks = stacks(player);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, on ? FIST + FIST_PER_STACK * stacks : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, on ? SPEED + SPEED_PER_STACK * stacks : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** Times his ki techniques. */
	static float kiFactor(ServerPlayer player) {
		return on(player) ? KI + KI_PER_STACK * stacks(player) : 1.0F;
	}
}
