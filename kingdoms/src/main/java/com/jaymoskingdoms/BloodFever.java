package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.185) A vampire player's blood fever (Andy: "struggling against the blood fever or giving into the urge"). Two
 * things are kept: the fever ({@link #FEVER}, 0-100), how badly they need blood now, and their surrender
 * ({@link #SURRENDER}, 0-100), how much of them the blood already has.
 *
 * <ul>
 * <li><b>The fever</b> climbs while they go unfed: slowly by day, twice as fast at night, faster still starving, and
 * faster the more they've surrendered. Fresh blood near them (a person hurt within {@link #SCENT_RANGE}) spikes it. Each
 * bite brings it down {@link #BITE_EASE}.</li>
 * <li><b>The urge:</b> past {@link #urgeAt} (lower the more they've surrendered) with a person in sight, the blood
 * takes the wheel ({@link Seizures}): it drives them at the nearest person and drinks. They can fight it (each key a
 * heave; fill the bar) or give in ({@code U}). Tearing free eases the fever a little and gives them back some of
 * themselves, but leaves them shaking. Giving in feeds them till the fever breaks and the blood rewards them for it
 * (a frenzy), and it takes more of them. Losing the fight takes some too.</li>
 * <li><b>Surrender</b> by stage: under 25 restrained, then thirsting, bloodbound (from 50: the night's strength one
 * level higher), ravenous (from 75: players are prey too, and each bite heals twice), and at 100 lost to the blood
 * (the fight is all but hopeless).</li>
 * </ul>
 * A dhampir has no fever. The priest's cure lifts both.
 */
public final class BloodFever {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** The fever's climb a second: by day, and times this at night, and starving. */
	static final float RISE = 0.04F, NIGHT_RISE = 2.0F, STARVING_RISE = 1.5F;
	/** The climb's scale at no surrender, and at a whole surrender. */
	static final float RISE_RESTRAINED = 0.75F, RISE_SURRENDERED = 2.0F;
	/** A person hurt within this (blocks) spikes the fever this much (a second). */
	static final double SCENT_RANGE = 10.0;
	static final float SCENT = 3.0F;
	/** Each bite brings it down this much; under {@link #SATED} it lets go. */
	static final float BITE_EASE = 22.0F, SATED = 5.0F;
	/** The urge comes past this at no surrender, this at a whole surrender. */
	static final float URGE_AT_RESTRAINED = 70.0F, URGE_AT_SURRENDERED = 40.0F;
	/** Past the line, each second's chance it seizes them: this, rising by this at a fever of 100. */
	static final float URGE_CHANCE = 0.03F, URGE_CHANCE_FULL = 0.12F;
	/** It looks for a person within this (blocks), one it can see. */
	static final double URGE_RANGE = 16.0;
	/** How long it holds them (ticks), the presses to break free (at no surrender, and more at a whole one), and the bar's drain a tick
	 * (0.7.216: eased from 12 / 36 / 48 and 0.08 / 0.2; Andy found the mashing overdone). */
	static final int HOLD_TICKS = 600, NEEDED = 9, NEEDED_SURRENDERED = 26, NEEDED_LOST = 34;
	static final float FADE = 0.06F, FADE_SURRENDERED = 0.15F;
	/** After tearing free: the fever eased by this, surrender back by this, shaking this long (ticks), and no urge for this long. */
	static final float FREE_EASE = 15.0F;
	static final int FREE_RESTRAINT = 3, SHAKE_TICKS = 200, FREE_COOLDOWN = 2400;
	/** Surrender gained: giving in, losing the fight (fed), or it losing its prey. */
	static final int GIVE_IN_SURRENDER = 6, LOST_FIGHT_SURRENDER = 4, LOST_PREY_SURRENDER = 1;
	/** It lost its prey: no urge for this long (ticks). */
	static final int LOST_COOLDOWN = 600;
	/** Giving in, after the meal: the frenzy (ticks; Strength and Speed amplifiers). */
	static final int FRENZY_TICKS = 1200, FRENZY_STRENGTH = 0, FRENZY_SPEED = 0;
	/** Surrender stages: bloodbound, ravenous, lost. */
	static final int BLOODBOUND = 50, RAVENOUS = 75, LOST = 100;
	/** (0.7.332) Another mortal player, or one of the world's people, within this (blocks; Andy: "vampires should hunger when within 150
	 * blocks of other players", and asked whether villagers count too, "yes"). */
	static final double PLAYER_RANGE = 150.0;
	/** A player that near: the fever's extra climb a second, and the hunger it costs (food exhaustion a second), at the range's edge and up close. */
	static final float NEAR_RISE = 0.04F, NEAR_RISE_CLOSE = 0.12F, NEAR_HUNGER = 0.02F, NEAR_HUNGER_CLOSE = 0.08F;
	/** One in their sight (within this half-angle of where they look, degrees, nothing in between): this much more again, scaled by closeness. */
	static final float SEEN_ANGLE = 40.0F, SEEN_RISE = 0.15F;
	/** (0.7.332) A hurt mortal player within this (blocks) spikes the fever like a hurt villager ({@link #SCENT}); one under half health but not bleeding now, this much a second. */
	static final double WOUNDED_RANGE = 30.0;
	static final float WOUNDED_RISE = 0.6F;
	/** (0.7.332) Sneaking away: among this many mortal players (within {@link #GROUP_RANGE}), the urge prefers a person this far (blocks) from every player. */
	static final int GROUP_SIZE = 2;
	static final double GROUP_RANGE = 48.0, LONE_FROM_PLAYERS = 12.0;
	/** (0.7.332) A bite on their thrall eases the fever this much more ({@link BloodThralls}). */
	static final float THRALL_EASE = 12.0F;
	/** The fever's warnings: once it climbs past each. */
	static final float[] WARN_AT = {40.0F, 60.0F, 80.0F};

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** The fever (0-100); synced to the vampire (their blood vision beats with it). */
	public static final AttachmentType<Float> FEVER = AttachmentRegistry.create(JaymosKingdoms.id("blood_fever"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath().syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
	/** Surrender (0-100); synced to the vampire. */
	public static final AttachmentType<Integer> SURRENDER = AttachmentRegistry.create(JaymosKingdoms.id("blood_surrender"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

	/**
	 * (0.7.332) How loud the nearest mortal player in their sight beats for them (0 none, 1 face to face): synced to the
	 * vampire alone, whose screen pulses and ears pound with it ({@code BloodVision}). Not kept.
	 */
	public static final AttachmentType<Float> HEARTBEAT = AttachmentRegistry.create(JaymosKingdoms.id("blood_heartbeat"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	/** No urge before this game time (not kept). */
	private static final java.util.Map<java.util.UUID, Long> READY = new java.util.HashMap<>();

	private BloodFever() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BloodFever::tick);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("vampire").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("fever")
					.executes(c -> show(c.getSource()))
					.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
						.executes(c -> set(c.getSource(), IntegerArgumentType.getInteger(c, "value"), -1))))
				.then(Commands.literal("surrender")
					.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
						.executes(c -> set(c.getSource(), -1, IntegerArgumentType.getInteger(c, "value")))))
				.then(Commands.literal("urge").executes(c -> urgeNow(c.getSource()))))));
	}

	// ---- queries (both sides) ------------------------------------------------------------------------------------------

	public static float fever(Player player) {
		Float f = player.getAttached(FEVER);
		return f == null ? 0.0F : f;
	}

	/** The heartbeat in their sight (0-1), on either side. */
	public static float heartbeat(Player player) {
		Float f = player.getAttached(HEARTBEAT);
		return f == null ? 0.0F : f;
	}

	public static int surrender(Player player) {
		Integer s = player.getAttached(SURRENDER);
		return s == null ? 0 : s;
	}

	/** The fever's at the urge's line or past it (their blood vision runs hot). */
	public static boolean burning(Player player) {
		return Vampires.isVampire(player) && fever(player) >= urgeAt(player);
	}

	/** Bloodbound or worse: the night's strength one level higher. */
	static boolean bloodbound(Player player) {
		return surrender(player) >= BLOODBOUND;
	}

	static float urgeAt(Player player) {
		return Mth.lerp(surrender(player) / 100.0F, URGE_AT_RESTRAINED, URGE_AT_SURRENDERED);
	}

	static boolean sated(ServerPlayer player) {
		return fever(player) <= SATED;
	}

	private static boolean feverish(ServerPlayer player) {
		Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
		return v != null && v.type() != Vampires.Kind.DHAMPIR;
	}

	/** (0.7.231) Blood magic stokes a vampire's fever ({@link Hemomancy}): {@code by} more, if the fever has them at all. */
	static void stoke(ServerPlayer player, float by) {
		if (feverish(player)) {
			setFever(player, fever(player) + by);
		}
	}

	private static void setFever(ServerPlayer player, float value) {
		player.setAttached(FEVER, Mth.clamp(value, 0.0F, 100.0F));
	}

	private static void addSurrender(ServerPlayer player, int by) {
		int was = surrender(player);
		int now = Mth.clamp(was + by, 0, LOST);
		player.setAttached(SURRENDER, now);
		int[] stages = {25, BLOODBOUND, RAVENOUS, LOST};
		for (int i = 0; i < stages.length; i++) {
			if (was < stages[i] && now >= stages[i]) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.stage." + (i + 1))
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
				player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
			} else if (was >= stages[i] && now < stages[i]) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.back." + (i + 1))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
		}
	}

	// ---- every second --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		long clock = overworld.getOverworldClockTime();
		boolean night = Vampires.night(clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 3) {
				continue;
			}
			if (!feverish(player)) {
				// (cured, or never one: nothing to keep)
				if (player.hasAttached(FEVER) || player.hasAttached(SURRENDER) || player.hasAttached(HEARTBEAT)) {
					player.removeAttached(FEVER);
					player.removeAttached(SURRENDER);
					player.removeAttached(HEARTBEAT);
					Favourites.clear(player);
					player.removeAttached(BloodRequests.STATE);
				}
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				setHeartbeat(player, 0.0F);
				Favourites.setSeen(player, false);
				continue;
			}
			float was = fever(player);
			float rise = RISE * (night ? NIGHT_RISE : 1.0F) * (VampirePlayers.starving(player) ? STARVING_RISE : 1.0F)
				* Mth.lerp(surrender(player) / 100.0F, RISE_RESTRAINED, RISE_SURRENDERED);
			if (!Seizures.held(player) && bleeding(player)) {
				rise += SCENT;
			}
			rise += amongPlayers(player);
			float fever = Math.min(100.0F, was + rise);
			setFever(player, fever);
			BloodRequests.tick(player, clock);
			for (int i = 0; i < WARN_AT.length; i++) {
				if (was < WARN_AT[i] && fever >= WARN_AT[i]) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.warn." + (i + 1))
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
					player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 1.0F + i * 0.15F);
				}
			}
			if (Seizures.held(player) || now < READY.getOrDefault(player.getUUID(), 0L) || player.isSleeping()) {
				continue;
			}
			float line = urgeAt(player);
			if (fever < line) {
				continue;
			}
			float chance = URGE_CHANCE + URGE_CHANCE_FULL * (fever - line) / Math.max(1.0F, 100.0F - line);
			if (player.getRandom().nextFloat() < chance) {
				urge(player);
			}
		}
	}

	/**
	 * (0.7.332) Others near them: the nearest mortal player or person within {@link #PLAYER_RANGE} makes them hungry and the
	 * fever climb, more the closer they are; the nearest one in their sight sets their {@link #HEARTBEAT}. Returns the
	 * fever's extra climb this second.
	 */
	private static float amongPlayers(ServerPlayer player) {
		double nearest = Double.MAX_VALUE;
		float seen = 0.0F;
		// (0.7.332) their favourite ({@link Favourites}): its own climb counts twice, and its heartbeat reads closer
		float favouriteClose = 0.0F, favouriteSeen = 0.0F, wounded = 0.0F;
		ServerPlayer seenPlayer = null;
		java.util.List<ServerPlayer> near = new java.util.ArrayList<>();
		for (ServerPlayer other : player.level().players()) {
			if (other == player || !Vampires.mortal(other) || other.isInvisible()) {
				continue;
			}
			double d = other.distanceTo(player);
			if (d > PLAYER_RANGE) {
				continue;
			}
			near.add(other);
			nearest = Math.min(nearest, d);
			float close = (float)(1.0 - d / PLAYER_RANGE);
			boolean favourite = Favourites.is(player, other);
			if (favourite) {
				favouriteClose = close;
			}
			// (0.7.332) a hurt friend nearby: the smell of it (a fresh blow like a hurt villager, a standing wound a little)
			if (d <= WOUNDED_RANGE) {
				if (other.hurtTime > 0) {
					wounded = Math.max(wounded, SCENT);
				} else if (other.getHealth() < other.getMaxHealth() * 0.5F) {
					wounded = Math.max(wounded, WOUNDED_RISE);
				}
			}
			if (close <= seen && !favourite || Kinship.monster(other) && !favourite) {
				// (0.7.332) kin are heard another way ({@link Kinship}): no red pulse
				continue;
			}
			if (inSight(player, other)) {
				if (close > seen) {
					seen = close;
					seenPlayer = other;
				}
				if (favourite) {
					favouriteSeen = close;
				}
			}
		}
		Favourites.tally(player, near);
		Favourites.setSeen(player, favouriteSeen > 0.0F);
		if (favouriteSeen > 0.0F) {
			seen = Math.max(seen, Math.min(1.0F, favouriteSeen * Favourites.HEARTBEAT_BOOST));
			for (ServerPlayer other : near) {
				if (Favourites.is(player, other)) {
					seenPlayer = other;
				}
			}
		}
		setHeartbeat(player, seen);
		if (seenPlayer != null) {
			BloodTemptation.seen(player, seenPlayer, fever(player));
		}
		if (nearest > 0.0) {
			// villagers and folk count for the hunger too, not for the heartbeat (that's players in sight)
			for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Math.min(nearest, PLAYER_RANGE)),
				Vampires::person)) {
				nearest = Math.min(nearest, e.distanceTo(player));
			}
		}
		if (nearest > PLAYER_RANGE) {
			return 0.0F;
		}
		float close = (float)(1.0 - nearest / PLAYER_RANGE);
		player.causeFoodExhaustion(Mth.lerp(close, NEAR_HUNGER, NEAR_HUNGER_CLOSE));
		float climb = Mth.lerp(close, NEAR_RISE, NEAR_RISE_CLOSE) + SEEN_RISE * seen;
		if (favouriteClose > 0.0F) {
			// twice as hard for the one they've fixed on
			climb += Mth.lerp(favouriteClose, NEAR_RISE, NEAR_RISE_CLOSE) + SEEN_RISE * favouriteSeen;
		}
		if (wounded > 0.0F && !Seizures.held(player)) {
			climb += wounded;
		}
		return climb;
	}

	/** (0.7.332) Whether {@code other} is in {@code viewer}'s sight: within {@link #SEEN_ANGLE} of where they look, nothing in between. */
	static boolean inSight(ServerPlayer viewer, ServerPlayer other) {
		net.minecraft.world.phys.Vec3 eye = viewer.getEyePosition();
		net.minecraft.world.phys.Vec3 look = viewer.getViewVector(1.0F);
		double cos = Math.cos(Math.toRadians(SEEN_ANGLE));
		net.minecraft.world.phys.Vec3 to = other.getEyePosition().subtract(eye);
		net.minecraft.world.phys.Vec3 chest = other.position().add(0.0, other.getBbHeight() * 0.6, 0.0);
		return to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) >= cos && (sees(viewer, eye, other.getEyePosition()) || sees(viewer, eye, chest));
	}

	private static boolean sees(ServerPlayer player, net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 to) {
		return player.level().clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.VISUAL,
			net.minecraft.world.level.ClipContext.Fluid.NONE, player)).getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	private static void setHeartbeat(ServerPlayer player, float value) {
		float was = heartbeat(player);
		if (value <= 0.0F) {
			if (player.hasAttached(HEARTBEAT)) {
				player.removeAttached(HEARTBEAT);
			}
		} else if (Math.abs(value - was) > 0.01F) {
			player.setAttached(HEARTBEAT, value);
		}
	}

	/** Someone hurt near them: the smell of it. */
	private static boolean bleeding(ServerPlayer player) {
		return !player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SCENT_RANGE),
			e -> e != player && e.hurtTime > 0 && prey(player, e)).isEmpty();
	}

	/** Who the blood wants: a person (and from ravenous, a mortal player), not a beast, not one of us. */
	private static boolean prey(ServerPlayer player, LivingEntity e) {
		return e != player && (Vampires.person(e) || surrender(player) >= RAVENOUS && Vampires.mortal(e)) && !e.isInvisible();
	}

	private static @Nullable LivingEntity nearest(ServerPlayer player, boolean seen) {
		LivingEntity best = null;
		double bestD = URGE_RANGE * URGE_RANGE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(URGE_RANGE),
			e -> prey(player, e))) {
			double d = e.distanceToSqr(player);
			if (d < bestD && (!seen || player.hasLineOfSight(e))) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/**
	 * (0.7.332) Sneak away: among {@link #GROUP_SIZE} or more mortal players, the blood prefers a person (a villager or one
	 * of the folk) at least {@link #LONE_FROM_PLAYERS} blocks from every player, so the hold walks them away from the group.
	 * Null if there's no such person or they aren't in a group.
	 */
	private static @Nullable LivingEntity loner(ServerPlayer player, boolean seen) {
		int group = 0;
		for (ServerPlayer other : player.level().players()) {
			if (other != player && Vampires.mortal(other) && other.distanceTo(player) <= GROUP_RANGE) {
				group++;
			}
		}
		if (group < GROUP_SIZE) {
			return null;
		}
		LivingEntity best = null;
		double bestD = URGE_RANGE * URGE_RANGE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(URGE_RANGE),
			e -> Vampires.person(e) && !e.isInvisible())) {
			double d = e.distanceToSqr(player);
			if (d >= bestD || seen && !player.hasLineOfSight(e)) {
				continue;
			}
			boolean alone = true;
			for (ServerPlayer other : player.level().players()) {
				if (other != player && other.distanceTo(e) < LONE_FROM_PLAYERS) {
					alone = false;
					break;
				}
			}
			if (alone) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The urge: the blood takes the wheel. False if there's no one to go for. */
	static boolean urge(ServerPlayer player) {
		LivingEntity prey = loner(player, true);
		if (prey == null) {
			prey = nearest(player, true);
		}
		if (prey == null) {
			return false;
		}
		float s = surrender(player) / 100.0F;
		int needed = surrender(player) >= LOST ? NEEDED_LOST : Math.round(Mth.lerp(s, NEEDED, NEEDED_SURRENDERED));
		Seizures.start(player, Seizures.Kind.FEVER, prey, HOLD_TICKS, needed, Mth.lerp(s, FADE, FADE_SURRENDERED), true);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.urge." + (1 + player.getRandom().nextInt(4)))
			.withStyle(ChatFormatting.DARK_RED));
		return true;
	}

	/** Its prey fell or got away: the next nearest (seen or not), or null. */
	static @Nullable LivingEntity nextPrey(ServerPlayer player, Seizures.Hold hold) {
		if (sated(player)) {
			return null;
		}
		LivingEntity lone = loner(player, false);
		return lone != null ? lone : nearest(player, false);
	}

	/** A bite (from {@link VampirePlayers#drink}): the fever eases, and a ravenous one heals more. */
	static void bit(ServerPlayer player) {
		bit(player, false);
	}

	/** (0.7.332) A bite, on their {@code thrall} or not: on a thrall it heals twice over and eases the fever {@link #THRALL_EASE} more. */
	static void bit(ServerPlayer player, boolean thrall) {
		if (!feverish(player)) {
			return;
		}
		setFever(player, fever(player) - BITE_EASE - (thrall ? THRALL_EASE : 0.0F));
		if (surrender(player) >= RAVENOUS) {
			player.heal(VampirePlayers.DRINK_HEAL);
		}
		if (thrall) {
			player.heal(VampirePlayers.DRINK_HEAL);
		}
	}

	/** (0.7.332) {@link BloodRequests}: the fever and the surrender move by these (they eased or climbed). */
	static void grant(ServerPlayer player, float feverBy, int surrenderBy) {
		if (feverish(player)) {
			setFever(player, fever(player) + feverBy);
			if (surrenderBy != 0) {
				addSurrender(player, surrenderBy);
			}
		}
	}

	/** They stopped fighting it. */
	static void gaveIn(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.give_in." + (1 + player.getRandom().nextInt(3)))
			.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.6F);
	}

	/** The hold's over. */
	static void released(ServerPlayer player, Seizures.Hold hold, Seizures.End how) {
		long now = player.level().getGameTime();
		switch (how) {
			case BROKE_FREE -> {
				setFever(player, fever(player) - FREE_EASE);
				addSurrender(player, -FREE_RESTRAINT);
				READY.put(player.getUUID(), now + FREE_COOLDOWN);
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SHAKE_TICKS, 0));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHAKE_TICKS, 0));
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SHAKE_TICKS / 2, 0, false, false, false));
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.free." + (1 + player.getRandom().nextInt(3)))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			case SATED -> {
				addSurrender(player, hold.given ? GIVE_IN_SURRENDER : LOST_FIGHT_SURRENDER);
				if (hold.given) {
					player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, FRENZY_TICKS, FRENZY_STRENGTH));
					player.addEffect(new MobEffectInstance(MobEffects.SPEED, FRENZY_TICKS, FRENZY_SPEED));
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, FRENZY_TICKS / 4, 0));
				}
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blood_fever.fed." + (hold.given ? "given" : "taken"))
					.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
			}
			case LOST -> {
				if (hold.bites > 0 || hold.given) {
					addSurrender(player, LOST_PREY_SURRENDER);
				}
				READY.put(player.getUUID(), now + LOST_COOLDOWN);
			}
			case CUT -> READY.put(player.getUUID(), now + LOST_COOLDOWN);
		}
	}

	// ---- commands ------------------------------------------------------------------------------------------------------

	private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		source.sendSuccess(() -> Component.literal("Blood fever " + Math.round(fever(player)) + " (urge at " + Math.round(urgeAt(player))
			+ "), surrender " + surrender(player)), false);
		return 1;
	}

	private static int set(CommandSourceStack source, int fever, int surrender) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!feverish(player)) {
			source.sendFailure(Component.literal("You're not a vampire (or you're a dhampir)."));
			return 0;
		}
		if (fever >= 0) {
			setFever(player, fever);
		}
		if (surrender >= 0) {
			addSurrender(player, surrender - surrender(player));
		}
		return show(source);
	}

	private static int urgeNow(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!feverish(player) || !urge(player)) {
			source.sendFailure(Component.literal("No vampire here, or no one in sight to go for."));
			return 0;
		}
		return 1;
	}
}
