package com.jaymospowers.power.wind;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * (Andy) The Staff Surge: a Wind user who has mastered the whole power and is already moving faster than
 * {@link #MIN_SPEED} (30 m/s), flying or not, holds left mouse with a quarterstaff and twirls it out ahead like a
 * propeller ("i just want a spinning animation"). While it spins the speed climbs slowly ("it increases slowly"):
 * {@link #ACCEL} a tick, {@link #ACCEL_ASCENDED} ascended. In flight that may carry you past the top speed by up to
 * {@link #ROOM} ({@link #ROOM_ASCENDED}); let go and the headroom fades by {@link #ROOM_FADE} a tick, so you drift back
 * down to cruise. Motion is the client's (see WindFlightController); this side checks who may, tells the watchers to
 * draw the twirl, and blows the gust behind you for everyone.
 */
public final class WindStaffSurge {
	/** Faster than this (blocks/tick; 1.5 = 30 m/s) before the twirl will push. */
	public static final double MIN_SPEED = 1.5;
	/** Speed added each tick the twirl is held (blocks/tick per tick; 0.02 = +8 m/s a second): mastered, and ascended. */
	public static final double ACCEL = 0.02, ACCEL_ASCENDED = 0.04;
	/** Flight: how far past the top speed the twirl can carry you (blocks/tick), and how fast that room fades once let go. */
	public static final double ROOM = 1.0, ROOM_FADE = 0.02;
	public static final double ROOM_ASCENDED = 2.0;
	/** The twirl: turns a second (ascended spins faster), and ticks for the arm to come out ahead / drop back. */
	public static final float TWIRL_TURNS_PER_SECOND = 3.0F, TWIRL_TURNS_PER_SECOND_ASCENDED = 5.0F;
	public static final int TWIRL_EASE_TICKS = 4;
	/** A puff of cloud behind the spinning staff every this many ticks, and the gust's roar every this many. */
	static final int PUFF_EVERY = 3, ROAR_EVERY = 12;

	/** Server → client: {@code entity} started ({@code held}) or stopped twirling (the twirler draws its own at once). */
	public record Twirl(int entity, boolean ascended, boolean held) implements CustomPacketPayload {
		public static final Type<Twirl> TYPE = new Type<>(JaymosPowers.id("wind_staff_twirl"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Twirl> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.entity);
				buf.writeBoolean(p.ascended);
				buf.writeBoolean(p.held);
			},
			buf -> new Twirl(buf.readVarInt(), buf.readBoolean(), buf.readBoolean()));

		@Override
		public Type<Twirl> type() {
			return TYPE;
		}
	}

	/** Client → server: I started or stopped twirling (the client already moves itself). */
	public record Surge(boolean held) implements CustomPacketPayload {
		public static final Type<Surge> TYPE = new Type<>(JaymosPowers.id("wind_staff_surge"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Surge> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.held), buf -> new Surge(buf.readBoolean()));

		@Override
		public Type<Surge> type() {
			return TYPE;
		}
	}

	private static final Set<UUID> TWIRLING = new HashSet<>();

	private WindStaffSurge() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Surge.TYPE, Surge.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Twirl.TYPE, Twirl.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Surge.TYPE, (payload, ctx) -> onSurge(ctx.player(), payload.held()));
		ServerTickEvents.END_SERVER_TICK.register(WindStaffSurge::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TWIRLING.remove(handler.player.getUUID()));
	}

	/** Has the Wind power, every ability mastered, a quarterstaff in hand. */
	public static boolean eligible(Player player) {
		return Powers.has(player, WindPower.INSTANCE) && Mastery.unlocked(player) >= Mastery.ALL && WindStaff.isStaff(player.getMainHandItem());
	}

	public static double accel(Player player) {
		return Fate.isAscended(player) ? ACCEL_ASCENDED : ACCEL;
	}

	public static double room(Player player) {
		return Fate.isAscended(player) ? ROOM_ASCENDED : ROOM;
	}

	private static void onSurge(ServerPlayer player, boolean held) {
		boolean twirling = held && eligible(player);
		if (twirling ? TWIRLING.add(player.getUUID()) : TWIRLING.remove(player.getUUID())) {
			tell(player, twirling);
			if (twirling && player.level() instanceof ServerLevel level) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
					0.7F, Fate.isAscended(player) ? 0.8F : 1.1F);
			}
		}
	}

	private static void tell(ServerPlayer player, boolean held) {
		Twirl twirl = new Twirl(player.getId(), Fate.isAscended(player), held);
		for (ServerPlayer watcher : PlayerLookup.tracking(player)) {
			if (watcher != player) {
				ServerPlayNetworking.send(watcher, twirl);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		Iterator<UUID> it = TWIRLING.iterator();
		while (it.hasNext()) {
			ServerPlayer player = server.getPlayerList().getPlayer(it.next());
			if (player == null) {
				it.remove();
				continue;
			}
			if (!eligible(player) || player.isRemoved()) {
				it.remove();
				tell(player, false);
				continue;
			}
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			boolean ascended = Fate.isAscended(player);
			// the wash off the spinning staff, left behind
			if (now % PUFF_EVERY == 0) {
				Vec3 behind = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).subtract(player.getLookAngle().scale(1.5));
				level.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, ascended ? 5 : 3, 0.3, 0.3, 0.3, 0.03);
				level.sendParticles(ParticleTypes.SMALL_GUST, behind.x, behind.y, behind.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
			if (now % ROAR_EVERY == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
					0.35F, ascended ? 0.7F : 1.0F);
			}
		}
	}
}
