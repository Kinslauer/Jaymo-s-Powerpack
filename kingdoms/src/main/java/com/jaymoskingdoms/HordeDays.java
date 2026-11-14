package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Horde days (Andy's pick from Undead Nights, 2026-11-14: "these days should be overcast so there is no sun to speak of,
 * otherwise it wouldnt make sense they can suddenly daywalk"). Now and then a day dawns under a low grey sky that never
 * breaks: the Sun is hidden behind it from dawn till dusk, the light is flat and dim, and with no sun to speak of the
 * dead don't burn and don't run for shade (the game's own {@code MONSTERS_BURN}, which {@code SeekShelterGoal} heeds too),
 * so the hordes ({@link ZombieHordes}) walk all day. The day after a blood moon always dawns like this; otherwise each day
 * from {@link #FIRST_DAY} rolls, more often as the world ages ({@link #CHANCE} to {@link #CHANCE} + {@link #CHANCE_AGE}).
 * The roll is saved, so a restart keeps it. Every client fades the grey sky in and out ({@link #addLayers}, and the Sun
 * hidden in {@code OvercastSunMixin}).
 */
public final class HordeDays {
	/** No horde days before this day; the chance of one, and the extra once the world is old ({@link ZombieHordes#RAMP_DAYS}). */
	static final long FIRST_DAY = 10;
	static final float CHANCE = 0.04F, CHANCE_AGE = 0.08F;
	/** The overcast sky: its colour, and how much of the sky, fog and clouds take it; the daylight's dimming. */
	static final int GREY = 0x7D8288, CLOUD_GREY = 0x5E6166;
	static final float SKY_BLEND = 0.8F, FOG_BLEND = 0.7F, LIGHT_DIM = 0.72F;
	/** How long the grey takes to come and go on a client (ticks). */
	public static final int FADE_TICKS = 300;

	/** The Overworld's roll: the day it was for and whether it's overcast. */
	record Roll(long day, boolean overcast) {
		static final Codec<Roll> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(Roll::day),
			Codec.BOOL.fieldOf("overcast").forGetter(Roll::overcast)).apply(i, Roll::new));
	}

	static final AttachmentType<Roll> ROLL = AttachmentRegistry.create(JaymosKingdoms.id("horde_day"), builder -> builder.persistent(Roll.CODEC));

	/** Server → player: whether the Overworld's sky is overcast now. */
	public record Sync(boolean overcast) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("horde_day"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.overcast), buf -> new Sync(buf.readBoolean()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	/** Whether the server's Overworld is overcast now, as last sent. */
	private static boolean shown;
	/** The client's side: what it was told, and how strongly the grey shows (0-1). */
	public static volatile boolean clientOvercast;
	public static volatile float clientFade;

	private HordeDays() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(HordeDays::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, new Sync(shown)));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> shown = false);
	}

	/** Whether the Overworld's sky is overcast now (a horde day). */
	public static boolean overcast(ServerLevel overworld) {
		return shown && overworld.dimension() == Level.OVERWORLD;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		long clock = level.getOverworldClockTime();
		boolean now = false;
		if (Moons.nightOf(clock) < 0) {
			long day = Math.floorDiv(clock, 24000L);
			// the morning of a day comes before the night of the same count ends: a day starts at the night's end
			if (Math.floorMod(clock, 24000L) >= Moons.NIGHT_END) {
				day++;
			}
			Roll roll = level.getAttached(ROLL);
			if (roll == null || roll.day() != day) {
				roll = new Roll(day, roll(level, day));
				level.setAttached(ROLL, roll);
			}
			now = roll.overcast();
		}
		if (now != shown) {
			shown = now;
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerPlayNetworking.send(player, new Sync(now));
			}
		}
	}

	/** Whether {@code day} dawns grey: always after a blood moon, otherwise by chance once the world is old enough. */
	private static boolean roll(ServerLevel level, long day) {
		Moons.Roll moon = level.getAttached(Moons.ROLL);
		if (moon != null && moon.night() == day - 1 && Moons.Moon.parse(moon.moon()).blood()) {
			return true;
		}
		if (day < FIRST_DAY) {
			return false;
		}
		float age = Mth.clamp((day - ZombieHordes.GRACE_DAYS) / (float) ZombieHordes.RAMP_DAYS, 0.0F, 1.0F);
		return level.getRandom().nextFloat() < CHANCE + CHANCE_AGE * age;
	}

	// ---- what the world looks like (both sides) -------------------------------------------------------------------------

	public static float fadeFor(Level level) {
		return level.isClientSide() ? clientFade : shown ? 1.0F : 0.0F;
	}

	/** (EnvironmentAttributeSystemBuilderMixin) The Overworld's grey sky, fog, clouds and dim light, and no burning, on a horde day. */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		Vector3f grey = ARGB.vector3fFromRGB24(GREY);
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (sky, tick) -> {
			float fade = fadeFor(level);
			return fade <= 0.0F ? sky : ARGB.srgbLerp(SKY_BLEND * fade, sky, grey);
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (fog, tick) -> {
			float fade = fadeFor(level);
			return fade <= 0.0F ? fog : ARGB.srgbLerp(FOG_BLEND * fade, fog, grey);
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (cloud, tick) -> {
			float fade = fadeFor(level);
			if (fade <= 0.0F) {
				return cloud;
			}
			Vector3f c = ARGB.vector3fFromRGB24(CLOUD_GREY);
			return new Vector4f(Mth.lerp(fade, cloud.x(), c.x), Mth.lerp(fade, cloud.y(), c.y), Mth.lerp(fade, cloud.z(), c.z), cloud.w());
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (glow, tick) -> {
			float fade = fadeFor(level);
			return fade <= 0.0F ? glow : new Vector4f(glow.x(), glow.y(), glow.z(), glow.w() * (1.0F - fade));
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (light, tick) -> {
			float fade = fadeFor(level);
			if (fade <= 0.0F) {
				return light;
			}
			float dim = Mth.lerp(fade, 1.0F, LIGHT_DIM);
			return ARGB.multiply(light, new Vector3f(dim, dim, dim));
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.MONSTERS_BURN, (burn, tick) -> fadeFor(level) > 0.0F ? false : burn);
	}
}
