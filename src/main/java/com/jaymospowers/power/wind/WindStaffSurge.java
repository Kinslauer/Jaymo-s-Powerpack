package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (Andy) The Staff Surge: a Wind user who has mastered the whole power and is already moving faster than
 * {@link #MIN_SPEED} (30 m/s), flying or not, left-clicks a quarterstaff and the swing throws a gust out behind them that
 * speeds them up by {@link #BOOST}; ascended, by {@link #BOOST_ASCENDED}. In flight the surge may carry you past the top
 * speed by up to {@link #ROOM} ({@link #ROOM_ASCENDED}), and that headroom fades by {@link #ROOM_FADE} a tick, so you
 * drift back down to cruise unless you keep swinging. Motion is the client's (see WindFlightController); this side only
 * checks the click and shows the gust to everyone.
 */
public final class WindStaffSurge {
	/** Faster than this (blocks/tick; 1.5 = 30 m/s) before a swing will surge. */
	public static final double MIN_SPEED = 1.5;
	/** Speed added per surge (blocks/tick): mastered, and ascended. */
	public static final double BOOST = 0.4, BOOST_ASCENDED = 0.8;
	/** Flight: how far past the top speed surges can carry you (blocks/tick), and how fast that room fades each tick. */
	public static final double ROOM = 1.0, ROOM_ASCENDED = 2.0, ROOM_FADE = 0.02;
	/** Ticks between surges: mastered, and ascended. */
	public static final int COOLDOWN = 10, COOLDOWN_ASCENDED = 6;

	/** Client → server: I surged (the client already moved itself). */
	public record Surge() implements CustomPacketPayload {
		public static final Type<Surge> TYPE = new Type<>(JaymosPowers.id("wind_staff_surge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Surge> CODEC = StreamCodec.unit(new Surge());

		@Override
		public Type<Surge> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private WindStaffSurge() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Surge.TYPE, Surge.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Surge.TYPE, (payload, ctx) -> onSurge(ctx.player()));
	}

	/** Has the Wind power, every ability mastered, a quarterstaff in hand. */
	public static boolean eligible(Player player) {
		return Powers.has(player, WindPower.INSTANCE) && Mastery.unlocked(player) >= Mastery.ALL && WindStaff.isStaff(player.getMainHandItem());
	}

	public static double boost(Player player) {
		return Fate.isAscended(player) ? BOOST_ASCENDED : BOOST;
	}

	public static double room(Player player) {
		return Fate.isAscended(player) ? ROOM_ASCENDED : ROOM;
	}

	public static int cooldown(Player player) {
		return Fate.isAscended(player) ? COOLDOWN_ASCENDED : COOLDOWN;
	}

	private static void onSurge(ServerPlayer player) {
		if (!eligible(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player.getUUID());
		if (last != null && now - last < cooldown(player) - 2) {
			return;
		}
		LAST.put(player.getUUID(), now);
		boolean ascended = Fate.isAscended(player);
		// the gust left behind where the staff swept
		Vec3 behind = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(player.getLookAngle().scale(1.5));
		level.sendParticles(ascended ? ParticleTypes.GUST_EMITTER_SMALL : ParticleTypes.GUST, behind.x, behind.y, behind.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, ascended ? 14 : 8, 0.4, 0.4, 0.4, 0.05);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
			ascended ? 1.0F : 0.7F, ascended ? 0.8F : 1.1F);
	}
}
