package com.jaymoskitchen.client.season;

import com.jaymoskitchen.net.Payloads;
import com.jaymoskitchen.season.Seasons;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * (0.5.6) Leaves and grass through the year, in temperate lands only: fresh green in early spring, a dry tinge late in
 * summer, gold to rust through autumn and a bare brown in winter. The colours shift once a part of a season (a week), so
 * the chunks are redrawn then. {@code SeasonTintMixin} applies them to the biome's own colours.
 */
public final class SeasonTints {
	/** For each part of the year (season * 3 + part): the leaf colour mixed in, how much of it, then the same for grass. */
	private static final int[][] TINT = {
		{0x7CC94A, 30, 0x7FD14F, 30}, // early spring
		{0x6FBE45, 12, 0x79C94B, 12},
		{0x000000, 0, 0x000000, 0},
		{0x000000, 0, 0x000000, 0},   // summer
		{0x000000, 0, 0x000000, 0},
		{0xA9A84E, 15, 0xB2A957, 20},
		{0xD9A23A, 35, 0xB09C4E, 25}, // autumn
		{0xD0742C, 60, 0xA88E4A, 35},
		{0xA8502A, 75, 0x9C8550, 45},
		{0x8A6E4B, 65, 0x9A957A, 50}, // winter
		{0x86704F, 70, 0x9C9A82, 55},
		{0x7E7652, 50, 0x8F9A70, 40},
	};
	/** The part of the year the chunks were last drawn for, or -1. */
	private static int drawn = -1;

	private SeasonTints() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(Payloads.SeasonSync.TYPE, (payload, ctx) -> Seasons.setShift(payload.shift()));
		ClientTickEvents.END_CLIENT_TICK.register(SeasonTints::tick);
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			drawn = -1;
			return;
		}
		Seasons.look(client.level);
		int at = stage();
		if (drawn >= 0 && at != drawn) {
			client.levelExtractor.allChanged();
		}
		drawn = at;
	}

	private static int stage() {
		return Seasons.today() / Seasons.DAYS_PER_PART;
	}

	public static int foliage(float baseTemperature, int color) {
		return Seasons.temperate(baseTemperature) ? mix(color, TINT[stage()][0], TINT[stage()][1]) : color;
	}

	public static int grass(float baseTemperature, int color) {
		return Seasons.temperate(baseTemperature) ? mix(color, TINT[stage()][2], TINT[stage()][3]) : color;
	}

	/** {@code color} with {@code percent} of {@code tint} mixed in (alpha kept). */
	private static int mix(int color, int tint, int percent) {
		if (percent <= 0) {
			return color;
		}
		int keep = 100 - percent;
		int r = (((color >> 16) & 0xFF) * keep + ((tint >> 16) & 0xFF) * percent) / 100;
		int g = (((color >> 8) & 0xFF) * keep + ((tint >> 8) & 0xFF) * percent) / 100;
		int b = ((color & 0xFF) * keep + (tint & 0xFF) * percent) / 100;
		return (color & 0xFF000000) | r << 16 | g << 8 | b;
	}
}
