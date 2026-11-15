package com.jaymoskingdoms.disasters;

import com.jaymoskingdoms.JaymosKingdoms;
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
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * What the great disasters do to the sky (both sides, through the game's own environment attributes, as
 * {@code HordeDays} does): the world's ending turns it red, day and night ({@link Doomsday}); a great star's dust dims and
 * greys it for a day after it falls ({@link Bolides#veil}); the signs before a disaster darken it over the land they're on
 * ({@link Signs}); and the Last Bell's visions flash it red for its holder alone ({@link LastBell}). The server sends the
 * first two now and then ({@link Sync}); the client works out the other two itself.
 */
public final class RedSky {
	/** The end's red, the dust's grey, a sign's dark; how much of the sky and fog they take at full strength. */
	static final int RED = 0x8A1408, RED_FOG = 0x6A1A0E, DUST = 0x6E665C, DARK = 0x2A2C33;
	static final float RED_SKY = 0.8F, RED_FOG_BLEND = 0.7F, DUST_SKY = 0.55F, DARK_SKY = 0.45F, FLASH_SKY = 0.9F;
	/** How much the light dims under the dust and the signs, and the red cast of the light at the end. */
	static final float DUST_LIGHT = 0.7F, DARK_LIGHT = 0.8F;
	/** How often the server sends the sky (ticks). */
	static final int SYNC_EVERY = 40;

	/** The client's: the end's red and the dust's grey (eased toward the server's), a sign's dark where the camera is, and
	 * a vision's flash, 0..1. Set by {@code LastBellClient}. */
	public static volatile float clientRed, clientDust, clientDark, clientFlash;
	/** What the server last said (the client eases toward these). */
	public static volatile float clientRedTarget, clientDustTarget;

	private RedSky() {
	}

	/** Server → client: how red the sky is (the world's ending) and how much dust hangs in it (a great star). */
	public record Sync(float red, float dust) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(JaymosKingdoms.id("red_sky"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeFloat(p.red);
				buf.writeFloat(p.dust);
			}, buf -> new Sync(buf.readFloat(), buf.readFloat()));

		@Override
		public Type<Sync> type() {
			return TYPE;
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ServerPlayNetworking.send(handler.player, now(server)));
	}

	private static Sync now(MinecraftServer server) {
		ServerLevel level = server.overworld();
		return new Sync(Doomsday.red(level), Bolides.veil(level));
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % SYNC_EVERY != 0) {
			return;
		}
		Sync sync = now(server);
		for (ServerPlayer p : server.overworld().players()) {
			ServerPlayNetworking.send(p, sync);
		}
	}

	private static float red(Level level) {
		return level.isClientSide() ? clientRed : level instanceof ServerLevel s ? Doomsday.red(s) : 0.0F;
	}

	private static float dust(Level level) {
		return level.isClientSide() ? clientDust : level instanceof ServerLevel s ? Bolides.veil(s) : 0.0F;
	}

	private static float dark(Level level) {
		return level.isClientSide() ? clientDark : 0.0F;
	}

	private static float flash(Level level) {
		return level.isClientSide() ? clientFlash : 0.0F;
	}

	/** (EnvironmentAttributeSystemBuilderMixin) The Overworld's sky, fog, clouds and light as the disasters have them. */
	public static void addLayers(EnvironmentAttributeSystem.Builder builder, Level level) {
		if (level.dimension() != Level.OVERWORLD) {
			return;
		}
		Vector3f red = ARGB.vector3fFromRGB24(RED), redFog = ARGB.vector3fFromRGB24(RED_FOG), dust = ARGB.vector3fFromRGB24(DUST),
			dark = ARGB.vector3fFromRGB24(DARK);
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_COLOR, (sky, tick) -> {
			float r = Math.max(red(level) * RED_SKY, flash(level) * FLASH_SKY), d = dust(level) * DUST_SKY, k = dark(level) * DARK_SKY;
			if (r <= 0.0F && d <= 0.0F && k <= 0.0F) {
				return sky;
			}
			Vector3fc out = d > 0.0F ? ARGB.srgbLerp(d, sky, dust) : sky;
			out = k > 0.0F ? ARGB.srgbLerp(k, out, dark) : out;
			return r > 0.0F ? ARGB.srgbLerp(r, out, red) : out;
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.FOG_COLOR, (fog, tick) -> {
			float r = Math.max(red(level) * RED_FOG_BLEND, flash(level) * RED_FOG_BLEND), d = dust(level) * DUST_SKY, k = dark(level) * DARK_SKY;
			if (r <= 0.0F && d <= 0.0F && k <= 0.0F) {
				return fog;
			}
			Vector3fc out = d > 0.0F ? ARGB.srgbLerp(d, fog, dust) : fog;
			out = k > 0.0F ? ARGB.srgbLerp(k, out, dark) : out;
			return r > 0.0F ? ARGB.srgbLerp(r, out, redFog) : out;
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.CLOUD_COLOR, (cloud, tick) -> {
			float r = red(level) * RED_SKY, d = Math.max(dust(level), dark(level)) * DUST_SKY;
			if (r <= 0.0F && d <= 0.0F) {
				return cloud;
			}
			Vector3f to = r >= d ? red : dust;
			float t = Math.max(r, d);
			return new Vector4f(Mth.lerp(t, cloud.x(), to.x), Mth.lerp(t, cloud.y(), to.y), Mth.lerp(t, cloud.z(), to.z), cloud.w());
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (glow, tick) -> {
			float d = Math.max(dust(level), red(level));
			return d <= 0.0F ? glow : new Vector4f(glow.x(), glow.y(), glow.z(), glow.w() * (1.0F - d));
		});
		builder.addTimeBasedLayer(EnvironmentAttributes.SKY_LIGHT_COLOR, (light, tick) -> {
			float r = red(level), d = dust(level), k = dark(level);
			if (r <= 0.0F && d <= 0.0F && k <= 0.0F) {
				return light;
			}
			float dim = Mth.lerp(d, 1.0F, DUST_LIGHT) * Mth.lerp(k, 1.0F, DARK_LIGHT);
			return ARGB.multiply(light, new Vector3f(dim, dim * Mth.lerp(r, 1.0F, 0.6F), dim * Mth.lerp(r, 1.0F, 0.5F)));
		});
	}
}
