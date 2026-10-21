package com.jaymoskitchen.season;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.net.Payloads;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * (0.5.4) Seasons: the pack's own, built from scratch after Andy picked "build our own" over Ecliptic Seasons (its Fabric
 * port is All Rights Reserved with no 26.3 build; see {@code docs/Seasons-Plan.md}). The year runs from the world's
 * day count, so sleeping and {@code /time} move it on too: spring, summer, autumn and winter, each an early, mid and late
 * part of {@link #DAYS_PER_PART} days. Only temperate lands (plains, forests, swamps, beaches) feel it: colder ones are
 * always wintry, hotter ones (deserts, savannas, jungles) always summery.
 * <p>
 * What changes: winter snows and freezes ponds in temperate lands, kills crops left in the open and makes you hungry
 * out in the cold ({@link WinterCold}), and the rest of the year that snow and ice melts
 * ({@link SeasonalWeather}); crops grow only in their seasons unless they're under a roof or glass ({@link SeasonalCrops});
 * leaves and grass turn (the client's {@code SeasonTints}). Each new part of a season is announced, holding a clock
 * shows the date, and {@code /season} tells it (ops: {@code /season set <season> [early|mid|late]}).
 * <p>
 * Other mods read it by reflection: {@link #seasonName(Level)} and {@link #dayOfYear(Level)}.
 */
public final class Seasons {
	/** Days in each part of a season (early, mid, late): 7 makes a season 21 days and the year 84. */
	public static final int DAYS_PER_PART = 7;
	public static final int PARTS = 3;
	public static final int DAYS_PER_SEASON = DAYS_PER_PART * PARTS;
	public static final int YEAR = DAYS_PER_SEASON * 4;
	public static final long DAY_TICKS = 24000L;
	/** Lands whose biome's base temperature is in this range have seasons. Plains are 0.8, forests 0.7, birch 0.6. */
	public static final float TEMPERATE_MIN = 0.55F, TEMPERATE_MAX = 0.9F;
	/** How often (ticks) the server looks at the calendar and shows the date to anyone holding a clock. */
	private static final int EVERY = 20;

	/** Days the calendar runs ahead of the world's own day count, set by {@code /season set}. Kept on the overworld. */
	public static final AttachmentType<Integer> SHIFT = AttachmentRegistry.create(JaymosKitchen.id("season_shift"),
		builder -> builder.persistent(Codec.INT));

	/**
	 * The shift and today's place in the year as this game knows them: the server's from the overworld, a client's from
	 * the server's {@link Payloads.SeasonSync}. Biome code has no level to ask, so it reads these. In single player both
	 * sides share them, and agree.
	 */
	private static volatile int shift;
	private static volatile int today;
	/** The part of the year (season * 3 + part) last announced, or -1 before the server's first look. */
	private static int announced = -1;

	private Seasons() {
	}

	public static Season season(int dayOfYear) {
		return Season.values()[Math.floorMod(dayOfYear, YEAR) / DAYS_PER_SEASON];
	}

	/** 0 early, 1 mid, 2 late. */
	public static int part(int dayOfYear) {
		return Math.floorMod(dayOfYear, YEAR) % DAYS_PER_SEASON / DAYS_PER_PART;
	}

	/** The day of the year, from 0, for this level's clock (the overworld's, in every dimension). */
	public static int dayOfYear(Level level) {
		return Math.floorMod(Math.floorDiv(level.getOverworldClockTime(), DAY_TICKS) + shift, YEAR);
	}

	public static Season of(Level level) {
		return season(dayOfYear(level));
	}

	/** For other mods (by reflection): "spring", "summer", "autumn" or "winter". */
	public static String seasonName(Level level) {
		return of(level).key();
	}

	/** Today's season as last worked out on this side; what biome code goes by. */
	public static Season now() {
		return season(today);
	}

	public static int today() {
		return today;
	}

	/** Whether a biome this warm at its base has seasons. */
	public static boolean temperate(float baseTemperature) {
		return baseTemperature >= TEMPERATE_MIN && baseTemperature <= TEMPERATE_MAX;
	}

	/** How much colder than its biome says a land with this base temperature is today. */
	public static float chill(float baseTemperature) {
		return temperate(baseTemperature) ? now().chill : 0.0F;
	}

	/** On this side: look at the level's clock again. The client calls it each tick, the server each second. */
	public static void look(Level level) {
		today = dayOfYear(level);
	}

	/** On a client: the server's shift. */
	public static void setShift(int value) {
		shift = value;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			shift = server.overworld().getAttachedOrElse(SHIFT, 0);
			look(server.overworld());
			announced = -1;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(new Payloads.SeasonSync(shift)));
		ServerTickEvents.END_SERVER_TICK.register(Seasons::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> command(dispatcher));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		look(overworld);
		int at = today / DAYS_PER_PART;
		if (announced >= 0 && at != announced) {
			Component line = Component.translatable("message.jaymoskitchen.season." + now().key() + "." + part(today));
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.sendSystemMessage(line);
			}
		}
		announced = at;
		boolean winter = now() == Season.WINTER;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (winter) {
				WinterCold.tick(player);
			}
			if (player.getMainHandItem().is(Items.CLOCK) || player.getOffhandItem().is(Items.CLOCK)) {
				player.sendOverlayMessage(date(today));
			}
		}
	}

	/** "Mid Autumn, day 3 of 7". */
	public static Component date(int dayOfYear) {
		return Component.translatable("message.jaymoskitchen.season.date",
			Component.translatable("season.jaymoskitchen.part." + part(dayOfYear)),
			Component.translatable("season.jaymoskitchen." + season(dayOfYear).key()),
			Math.floorMod(dayOfYear, YEAR) % DAYS_PER_PART + 1, DAYS_PER_PART);
	}

	private static void command(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> set = Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS));
		for (Season season : Season.values()) {
			LiteralArgumentBuilder<CommandSourceStack> one = Commands.literal(season.key()).executes(c -> set(c, season, 0));
			String[] parts = {"early", "mid", "late"};
			for (int p = 0; p < PARTS; p++) {
				int part = p;
				one.then(Commands.literal(parts[p]).executes(c -> set(c, season, part)));
			}
			set.then(one);
		}
		dispatcher.register(Commands.literal("season").executes(Seasons::tell).then(set));
	}

	private static int tell(CommandContext<CommandSourceStack> c) {
		ServerLevel level = c.getSource().getServer().overworld();
		int day = dayOfYear(level);
		int left = DAYS_PER_SEASON - Math.floorMod(day, DAYS_PER_SEASON);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskitchen.season.tell", date(day),
			Component.translatable("season.jaymoskitchen." + season(day).next().key()), left), false);
		return 1;
	}

	/** Moves the calendar so today is the first day of that part of the season. */
	private static int set(CommandContext<CommandSourceStack> c, Season season, int part) {
		MinecraftServer server = c.getSource().getServer();
		ServerLevel overworld = server.overworld();
		long worldDay = Math.floorDiv(overworld.getOverworldClockTime(), DAY_TICKS);
		int wanted = season.ordinal() * DAYS_PER_SEASON + part * DAYS_PER_PART;
		shift = (int)Math.floorMod(wanted - worldDay, (long)YEAR);
		overworld.setAttached(SHIFT, shift);
		look(overworld);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, new Payloads.SeasonSync(shift));
		}
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskitchen.season.set", date(today)), true);
		return 1;
	}
}
