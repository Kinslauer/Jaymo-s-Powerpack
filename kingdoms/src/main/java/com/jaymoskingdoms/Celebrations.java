package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Fireworks over a far city (Reference Mods patch 14, X7, after ImmersiveVFX's idea; our own code). When a realm
 * celebrates, its sky is lit over its seat that night, and anyone within {@link #FAR} blocks sees it: a victory in a war
 * ({@link Wars}), a festival bout won ({@code forms.Bouts}), a wedding in its town ({@link Neighbours}). A festival's own
 * rockets ({@link Festivals}) are real ones, seen only near; past {@link #NEAR} its show is sent here too, so a city keeping
 * a festival is seen celebrating from the hills.
 * <p>
 * The bursts are drawn on each client ({@code CelebrationsClient}), in the realm's colour and a second one, high over the
 * seat; the server only says where and what. A celebration called by day waits for dusk.
 */
public final class Celebrations {
	/** How far away a show is seen (blocks), and how near a festival's real rockets are (so its far show isn't sent there). */
	static final double FAR = 1024.0, NEAR = 128.0;
	/** How long a show lasts (ticks) for a victory, a tourney won and a wedding. */
	public static final int VICTORY_TICKS = 2400, TOURNEY_TICKS = 1600, WEDDING_TICKS = 1000;
	/** How often a show may send a burst (ticks) and the odds it does; a festival's far show's odds each tick it's called. */
	static final int EVERY = 10;
	static final float BURST_CHANCE = 0.45F, FESTIVAL_CHANCE = 0.05F;
	/** How high over the ground at the seat they burst (blocks), and how far off the seat (each way). */
	static final int HIGH_MIN = 30, HIGH_MAX = 60, SPREAD = 28;
	/** Night, when a show can be seen: from dusk to before dawn (time of day). */
	static final long DUSK = 12500L, DAWN = 22500L;

	/** Server → a player in sight of it: one burst, where and what. */
	public record Burst(double x, double y, double z, int shape, List<Integer> colours, List<Integer> fades, boolean trail, boolean twinkle)
		implements CustomPacketPayload {
		public static final Type<Burst> TYPE = new Type<>(JaymosKingdoms.id("celebration_burst"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Burst> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, Burst::x, ByteBufCodecs.DOUBLE, Burst::y, ByteBufCodecs.DOUBLE, Burst::z, ByteBufCodecs.VAR_INT, Burst::shape,
			ByteBufCodecs.INT.apply(ByteBufCodecs.list()), Burst::colours, ByteBufCodecs.INT.apply(ByteBufCodecs.list()), Burst::fades,
			ByteBufCodecs.BOOL, Burst::trail, ByteBufCodecs.BOOL, Burst::twinkle, Burst::new);

		@Override
		public Type<Burst> type() {
			return TYPE;
		}
	}

	/** A realm's show: over its seat in its colour, from one game time to another. */
	private record Show(int realm, BlockPos seat, DyeColor colour, long from, long until) {
	}

	private static final List<Show> SHOWS = new ArrayList<>();

	private Celebrations() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Burst.TYPE, Burst.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Celebrations::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SHOWS.clear());
	}

	/** Realm {@code f} celebrates: a show of {@code ticks} over its seat, now if it's night, else from dusk. */
	public static void hold(ServerLevel level, Faction f, int ticks) {
		if (f == null || f.fallen() || !f.type().isRealm()) {
			return;
		}
		long now = level.getGameTime(), time = Math.floorMod(level.getOverworldClockTime(), 24000L);
		long from = night(time) ? now : now + Math.floorMod(DUSK - time, 24000L);
		SHOWS.removeIf(s -> s.realm == f.id());
		SHOWS.add(new Show(f.id(), f.seat(), f.color(), from, from + ticks));
	}

	/** A realm keeping its festival at dusk ({@link Festivals}): those too far to see its real rockets see these. */
	static void far(ServerLevel level, Faction f) {
		if (level.getRandom().nextFloat() < FESTIVAL_CHANCE) {
			burst(level, f.seat(), f.color(), NEAR);
		}
	}

	private static void tick(MinecraftServer server) {
		if (SHOWS.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		SHOWS.removeIf(s -> now >= s.until);
		if (!night(Math.floorMod(level.getOverworldClockTime(), 24000L))) {
			return;
		}
		for (Show s : SHOWS) {
			if (now >= s.from && level.getRandom().nextFloat() < BURST_CHANCE) {
				burst(level, s.seat, s.colour, 0.0);
			}
		}
	}

	private static boolean night(long time) {
		return time >= DUSK && time < DAWN;
	}

	/** One burst high over {@code seat}, sent to everyone in sight of it but nearer than {@code skipNear}. */
	private static void burst(ServerLevel level, BlockPos seat, DyeColor colour, double skipNear) {
		List<ServerPlayer> watching = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			double dx = p.getX() - seat.getX() - 0.5, dz = p.getZ() - seat.getZ() - 0.5, d = dx * dx + dz * dz;
			if (d <= FAR * FAR && d >= skipNear * skipNear && ServerPlayNetworking.canSend(p, Burst.TYPE)) {
				watching.add(p);
			}
		}
		if (watching.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		int x = seat.getX() + random.nextInt(SPREAD * 2 + 1) - SPREAD, z = seat.getZ() + random.nextInt(SPREAD * 2 + 1) - SPREAD;
		// (over the ground there, which far chunks may not have loaded: then over the seat's own height)
		int ground = level.hasChunkAt(new BlockPos(x, 0, z)) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) : seat.getY();
		double y = Math.max(ground, seat.getY()) + HIGH_MIN + random.nextInt(HIGH_MAX - HIGH_MIN + 1);
		FireworkExplosion.Shape[] shapes = FireworkExplosion.Shape.values();
		DyeColor second = DyeColor.values()[random.nextInt(DyeColor.values().length)];
		Burst burst = new Burst(x + 0.5, y, z + 0.5, shapes[random.nextInt(shapes.length)].ordinal(),
			List.of(colour.getFireworkColor(), second.getFireworkColor()), List.of(DyeColor.WHITE.getFireworkColor()), random.nextBoolean(),
			random.nextBoolean());
		for (ServerPlayer p : watching) {
			ServerPlayNetworking.send(p, burst);
		}
	}
}
