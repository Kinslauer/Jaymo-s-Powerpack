package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The big suit's temper. While the big suit is up the wearer builds fury (0 to {@link #MAX}) from blows dealt and blows
 * taken; it fades a few seconds after the last blow. Fury makes the melee hit harder, the reach longer and the suit
 * bigger. At full fury the roar key spends all of it in a fury burst (a bigger, harder roar). The longer the big suit
 * was refused, the angrier it is when it next forms. The roar makes the foes near it run and slows and darkens the
 * players near it. And the big suit with its tendrils drawn in walks at a sprint's pace, cannot sprint, steps up a full
 * block and is hard to shove. Fury is synced to its wearer (the HUD bar); the rest is server bookkeeping.
 */
public final class WebFury {
	// ================================================================================================================
	// Tuning
	/** Fury runs from 0 to this. */
	public static final int MAX = 100;
	/** Fury gained from a melee blow dealt, and from a blow taken (per point of damage, kept between the two limits). */
	static final int GAIN_DEALT = 6, GAIN_TAKEN_MIN = 4, GAIN_TAKEN_MAX = 14;
	static final float GAIN_TAKEN_PER_DAMAGE = 1.5F;
	/** Ticks after the last gain before fury starts to fade, then how much it loses every {@code DECAY_EVERY} ticks. */
	static final int DECAY_DELAY = 100, DECAY_EVERY = 3, DECAY_STEP = 1;
	/** At full fury: more melee damage (share), more reach (blocks, entities and blocks), a bigger body (share of its base size). */
	static final double DAMAGE_BONUS = 0.5, REACH_BONUS = 0.75, SIZE_BONUS = 0.15;
	/** The effects change in steps of this much fury, so the body does not resize on every point. */
	static final int STEP = 5;
	/** The fury burst: the roar's damage, knockback and radius are multiplied by these; the burst lapses this many ticks after the key. */
	static final float BURST_DAMAGE = 1.8F;
	static final double BURST_KNOCKBACK = 1.5, BURST_RADIUS = 1.4;
	static final int BURST_VALID_TICKS = 80;
	static final float BURST_SHAKE = 1.0F;
	static final int BURST_COLOR = 0x5A1070, ROAR_COLOR = 0x3A1050;
	/** Caged: no fury under {@code CAGE_FREE} ticks of play without the big suit (5 minutes), rising to {@code CAGE_FURY} by {@code CAGE_FULL} (40 minutes). */
	static final int CAGE_FREE = 6000, CAGE_FULL = 48000, CAGE_FURY = 60;
	/** It speaks of its anger when it starts with at least this much. */
	static final int CAGE_ANGRY = 15;
	/** Dread: foes within this many blocks of the roar flee for this many ticks (a burst scales both by {@code BURST_RADIUS}). */
	static final double DREAD_RADIUS = 14.0;
	static final int DREAD_TICKS = 100;
	/** Other players in the radius: Slowness (ticks, amplifier) and the dark pulse on their screens (ticks). */
	static final int DREAD_SLOW_TICKS = 80, DREAD_SLOW_LEVEL = 1, DREAD_SCREEN_TICKS = 40;
	/** The wearer's own screen under the roar (ticks of dark, extra shake). */
	static final int DREAD_SELF_TICKS = 30;
	static final float DREAD_SELF_SHAKE = 0.5F;
	/** The slow walk: speed added (the share a sprint adds), step height added (blocks), knockback resistance added. */
	static final double SLOW_SPEED = 0.3, SLOW_STEP = 0.5, SLOW_KNOCKBACK = 0.9;
	// ================================================================================================================

	/** Fury, 0 to {@link #MAX}. Synced to its wearer for the HUD. */
	public static final AttachmentType<Integer> FURY = AttachmentRegistry.create(JaymosPowers.id("web_fury"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** Ticks of play with the symbiote bonded and awake since the big suit was last up. Saved. */
	private static final AttachmentType<Integer> CAGED = AttachmentRegistry.create(JaymosPowers.id("web_fury_caged"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** The first full fury has been remarked on. Saved. */
	private static final AttachmentType<Boolean> TOLD = AttachmentRegistry.create(JaymosPowers.id("web_fury_told"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	private static final Identifier DAMAGE_ID = JaymosPowers.id("web_fury_damage");
	private static final Identifier ENTITY_REACH_ID = JaymosPowers.id("web_fury_entity_reach");
	private static final Identifier BLOCK_REACH_ID = JaymosPowers.id("web_fury_block_reach");
	private static final Identifier SIZE_ID = JaymosPowers.id("web_fury_size");
	private static final Identifier SLOW_SPEED_ID = JaymosPowers.id("web_slow_walk_speed");
	private static final Identifier SLOW_STEP_ID = JaymosPowers.id("web_slow_walk_step");
	private static final Identifier SLOW_KNOCKBACK_ID = JaymosPowers.id("web_slow_walk_knockback");

	/** Players whose big suit is up as far as this class has seen. */
	private static final Set<UUID> UP = new HashSet<>();
	/** Last game time each player's fury grew. */
	private static final Map<UUID, Long> LAST_GAIN = new HashMap<>();
	/** Players in the slow walk. */
	private static final Set<UUID> SLOW = new HashSet<>();
	/** Fury bursts waiting for the roar to land: wearer to the game time they lapse. */
	private static final Map<UUID, Long> BURSTS = new HashMap<>();

	private WebFury() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebFury::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && source.getDirectEntity() == attacker) {
				gain(attacker, GAIN_DEALT);
			}
			if (entity instanceof ServerPlayer victim) {
				gain(victim, Math.max(GAIN_TAKEN_MIN, Math.min(GAIN_TAKEN_MAX, Math.round(damageTaken * GAIN_TAKEN_PER_DAMAGE))));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			UP.clear();
			LAST_GAIN.clear();
			SLOW.clear();
			BURSTS.clear();
		});
	}

	/** Fury, 0 when there is none. */
	public static int fury(Player player) {
		Integer value = player.getAttached(FURY);
		return value == null ? 0 : value;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean bulk = WebSymbiote.bulked(player);
			boolean up = UP.contains(player.getUUID());
			if (bulk && !up) {
				UP.add(player.getUUID());
				formed(player);
			} else if (!bulk && up) {
				UP.remove(player.getUUID());
				gone(player);
			}
			if (bulk) {
				fade(player);
			} else {
				cage(player);
			}
			slowWalk(player, bulk && !WebSymbiote.armored(player));
		}
	}

	// ---- fury --------------------------------------------------------------------------------------------------------

	/** Fury grows by {@code amount} (only in the big suit). */
	static void gain(ServerPlayer player, int amount) {
		if (!UP.contains(player.getUUID()) || !WebSymbiote.bulked(player)) {
			return;
		}
		int before = fury(player);
		int after = Math.min(MAX, before + amount);
		LAST_GAIN.put(player.getUUID(), player.level().getGameTime());
		if (after == before) {
			return;
		}
		set(player, after);
		if (after >= MAX && !Boolean.TRUE.equals(player.getAttached(TOLD))) {
			player.setAttached(TOLD, true);
			WebSymbiote.whisper(player, "fury_full");
		}
	}

	private static void set(ServerPlayer player, int value) {
		if (value <= 0) {
			player.removeAttached(FURY);
		} else {
			player.setAttached(FURY, value);
		}
		double share = Math.min(MAX, value) / STEP * STEP / (double)MAX;
		modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, DAMAGE_BONUS * share);
		modifier(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, AttributeModifier.Operation.ADD_VALUE, REACH_BONUS * share);
		modifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, AttributeModifier.Operation.ADD_VALUE, REACH_BONUS * share);
		modifier(player, Attributes.SCALE, SIZE_ID, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, SIZE_BONUS * share);
	}

	/** Puts the modifier on at {@code amount}, or takes it off at zero; does nothing when it is already so. */
	private static void modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id, AttributeModifier.Operation operation, double amount) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier existing = instance.getModifier(id);
		if (amount <= 0.0) {
			if (existing != null) {
				instance.removeModifier(id);
			}
		} else if (existing == null || existing.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	/** Out of a fight a while, fury burns down. */
	private static void fade(ServerPlayer player) {
		int value = fury(player);
		if (value <= 0) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - LAST_GAIN.getOrDefault(player.getUUID(), 0L) >= DECAY_DELAY && now % DECAY_EVERY == 0) {
			set(player, Math.max(0, value - DECAY_STEP));
		}
	}

	// ---- caged -------------------------------------------------------------------------------------------------------

	/** The big suit has formed: it starts as angry as it has been kept waiting. */
	private static void formed(ServerPlayer player) {
		int caged = player.getAttachedOrElse(CAGED, 0);
		player.removeAttached(CAGED);
		int start = 0;
		if (caged > CAGE_FREE) {
			start = (int)Math.round(CAGE_FURY * Math.min(1.0, (caged - CAGE_FREE) / (double)(CAGE_FULL - CAGE_FREE)));
		}
		if (start > 0) {
			LAST_GAIN.put(player.getUUID(), player.level().getGameTime());
			set(player, start);
			if (start >= CAGE_ANGRY) {
				WebSymbiote.whisper(player, "fury_caged");
			}
		}
	}

	/** The big suit is down: the fury goes with it. */
	private static void gone(ServerPlayer player) {
		LAST_GAIN.remove(player.getUUID());
		BURSTS.remove(player.getUUID());
		set(player, 0);
	}

	/** Counts the time the big suit is refused: the symbiote bonded, awake and about, the big suit not up. */
	private static void cage(ServerPlayer player) {
		if (player.tickCount % 20 != 0 || !WebSymbiote.state(player).bonded() || WebSymbioteStrains.asleep(player) || WebSymbioteBond.dormant(player)) {
			return;
		}
		int caged = player.getAttachedOrElse(CAGED, 0);
		if (caged < CAGE_FULL) {
			player.setAttached(CAGED, Math.min(CAGE_FULL, caged + 20));
		}
	}

	// ---- the fury burst and the dread --------------------------------------------------------------------------------

	/** The roar key was pressed by the wearer: at full fury, all of it goes into the roar that is gathering. */
	static void startBurst(ServerPlayer player) {
		if (fury(player) >= MAX) {
			set(player, 0);
			BURSTS.put(player.getUUID(), player.level().getGameTime() + BURST_VALID_TICKS);
		}
	}

	/** The roar lands: whether it is a fury burst (used up by asking). */
	static boolean takeBurst(ServerPlayer player) {
		Long until = BURSTS.remove(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	/**
	 * The roar has gone out from {@code center}: foes near it lose their nerve and run, other players are slowed and
	 * their screens go dark, and the wearer's screen shudders. A burst reaches further and adds a shockwave.
	 */
	static void roared(ServerPlayer player, ServerLevel level, Vec3 center, boolean burst) {
		double reach = DREAD_RADIUS * (burst ? BURST_RADIUS : 1.0);
		long until = level.getGameTime() + (long)(DREAD_TICKS * (burst ? BURST_RADIUS : 1.0));
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(reach),
			m -> m.isAlive() && m instanceof PathfinderMob && !(m instanceof WitherBoss) && !(m instanceof EnderDragon)
				&& !(m instanceof OwnableEntity owned && owned.getOwner() == player) && !WebHanging.webbedUp(m))) {
			if (mob.distanceTo(player) <= reach) {
				mob.setTarget(null);
				WebFear.scare(mob, player, until);
			}
		}
		for (ServerPlayer other : level.players()) {
			if (other != player && other.isAlive() && !other.isSpectator() && other.distanceTo(player) <= reach) {
				other.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DREAD_SLOW_TICKS, DREAD_SLOW_LEVEL, false, true));
				ServerPlayNetworking.send(other, new WebNet.Dread(DREAD_SCREEN_TICKS, false, false, 0.0F));
			}
		}
		WebSymbiote.dread(player, DREAD_SELF_TICKS, false);
		Shakes.send(player, DREAD_SELF_SHAKE);
		Vec3 ground = center.subtract(0.0, player.getBbHeight() * 0.5, 0.0);
		MagicFx.ring(level, ground, (float)reach * 0.5F, ROAR_COLOR);
		if (burst) {
			Shakes.send(player, BURST_SHAKE);
			MagicFx.blast(level, center, (float)(WebSymbiote.ROAR_RADIUS * BURST_RADIUS), BURST_COLOR);
			MagicFx.ring(level, ground, (float)reach, BURST_COLOR);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6F, 0.5F);
		}
	}

	// ---- the slow walk -----------------------------------------------------------------------------------------------

	/** The big suit with its tendrils in: walks at a sprint's pace, no sprinting, a full block of step, near-total knockback resistance. */
	private static void slowWalk(ServerPlayer player, boolean on) {
		boolean was = SLOW.contains(player.getUUID());
		if (on) {
			SLOW.add(player.getUUID());
			modifier(player, Attributes.MOVEMENT_SPEED, SLOW_SPEED_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, SLOW_SPEED);
			modifier(player, Attributes.STEP_HEIGHT, SLOW_STEP_ID, AttributeModifier.Operation.ADD_VALUE, SLOW_STEP);
			modifier(player, Attributes.KNOCKBACK_RESISTANCE, SLOW_KNOCKBACK_ID, AttributeModifier.Operation.ADD_VALUE, SLOW_KNOCKBACK);
			if (player.isSprinting()) {
				player.setSprinting(false);
			}
		} else if (was) {
			SLOW.remove(player.getUUID());
			modifier(player, Attributes.MOVEMENT_SPEED, SLOW_SPEED_ID, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, 0.0);
			modifier(player, Attributes.STEP_HEIGHT, SLOW_STEP_ID, AttributeModifier.Operation.ADD_VALUE, 0.0);
			modifier(player, Attributes.KNOCKBACK_RESISTANCE, SLOW_KNOCKBACK_ID, AttributeModifier.Operation.ADD_VALUE, 0.0);
		}
	}
}
