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
	/** The fever's warnings: once it climbs past each. */
	static final float[] WARN_AT = {40.0F, 60.0F, 80.0F};

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** The fever (0-100); synced to the vampire (their blood vision beats with it). */
	public static final AttachmentType<Float> FEVER = AttachmentRegistry.create(JaymosKingdoms.id("blood_fever"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath().syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
	/** Surrender (0-100); synced to the vampire. */
	public static final AttachmentType<Integer> SURRENDER = AttachmentRegistry.create(JaymosKingdoms.id("blood_surrender"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

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
				if (player.hasAttached(FEVER) || player.hasAttached(SURRENDER)) {
					player.removeAttached(FEVER);
					player.removeAttached(SURRENDER);
				}
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				continue;
			}
			float was = fever(player);
			float rise = RISE * (night ? NIGHT_RISE : 1.0F) * (VampirePlayers.starving(player) ? STARVING_RISE : 1.0F)
				* Mth.lerp(surrender(player) / 100.0F, RISE_RESTRAINED, RISE_SURRENDERED);
			if (!Seizures.held(player) && bleeding(player)) {
				rise += SCENT;
			}
			float fever = Math.min(100.0F, was + rise);
			setFever(player, fever);
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

	/** The urge: the blood takes the wheel. False if there's no one to go for. */
	static boolean urge(ServerPlayer player) {
		LivingEntity prey = nearest(player, true);
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
		return sated(player) ? null : nearest(player, false);
	}

	/** A bite (from {@link VampirePlayers#drink}): the fever eases, and a ravenous one heals more. */
	static void bit(ServerPlayer player) {
		if (!feverish(player)) {
			return;
		}
		setFever(player, fever(player) - BITE_EASE);
		if (surrender(player) >= RAVENOUS) {
			player.heal(VampirePlayers.DRINK_HEAL);
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
