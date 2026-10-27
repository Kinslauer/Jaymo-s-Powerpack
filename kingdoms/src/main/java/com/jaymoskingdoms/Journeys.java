package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.266) Andy: "this cutscene system should replace our carriage rides, they always get stuck and you cant see
 * anyway, at least this gives you an animation and hides imperfections." Stagecoach's idea (its Bukkit plugin can't run
 * in a Fabric pack, and nothing of it is copied): a player's ride to another settlement (a {@link Rickshaws rickshaw},
 * a rich realm's {@link Cabs cab}, a milestone's {@link Milestones coach}) sets off for a moment where they can see it,
 * then the road screen comes up: hills, trees and fence posts going by, their ride going along the road, the place it
 * goes on a sign with the time left. Meanwhile the ride stands still where it was; at the end the player, and the
 * rickshaw or carriage with them, are set down at the far end. The trip is never walked, so it never gets stuck.
 *
 * <p>The screen's "Turn back" button gets them down where they started with their fare back. Nothing hurts a traveller
 * on the way. Logging out on the way is turning back.
 */
public final class Journeys {
	/** How fast the road goes by (blocks a second): on foot behind a puller, or behind a horse. */
	static final double RICKSHAW_SPEED = 16.0, CARRIAGE_SPEED = 30.0;
	/** However near or far, a trip takes at least this long and at most this long (ticks). */
	static final int LEAST_TICKS = 100, MOST_TICKS = 600;
	/** The ride is seen setting off for this long (ticks) before the road screen comes up. */
	static final int DEPART_TICKS = 30;

	/** What the screen shows going along the road (a {@link Wagon} kind) and the land at each end of it. */
	public static final int GRASS = 0, SNOW = 1, SAND = 2, BLOSSOM = 3;

	/**
	 * Server → traveller: the road screen, for {@code ticks}. {@code kind} the {@link Wagon} kind, {@code color} its
	 * colour (RGB), {@code from}/{@code to} the land at each end, {@code time} the time of day it starts at.
	 */
	public record Start(int kind, Component place, int ticks, int blocks, int color, int from, int to, int time) implements CustomPacketPayload {
		public static final Type<Start> TYPE = new Type<>(JaymosKingdoms.id("journey_start"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Start> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.kind);
			ComponentSerialization.STREAM_CODEC.encode(buf, p.place);
			buf.writeVarInt(p.ticks);
			buf.writeVarInt(p.blocks);
			buf.writeInt(p.color);
			buf.writeVarInt(p.from);
			buf.writeVarInt(p.to);
			buf.writeVarInt(p.time);
		}, buf -> new Start(buf.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readInt(),
			buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Start> type() {
			return TYPE;
		}
	}

	/** Server → traveller: there (or turned back): the screen comes down. */
	public record End(boolean arrived) implements CustomPacketPayload {
		public static final Type<End> TYPE = new Type<>(JaymosKingdoms.id("journey_end"));
		public static final StreamCodec<RegistryFriendlyByteBuf, End> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeBoolean(p.arrived),
			buf -> new End(buf.readBoolean()));

		@Override
		public Type<End> type() {
			return TYPE;
		}
	}

	/** Traveller → server: turn back. */
	public record TurnBack() implements CustomPacketPayload {
		public static final Type<TurnBack> TYPE = new Type<>(JaymosKingdoms.id("journey_turn_back"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TurnBack> CODEC = StreamCodec.unit(new TurnBack());

		@Override
		public Type<TurnBack> type() {
			return TYPE;
		}
	}

	/** What the ride does with itself at the end of a trip, or when it's turned back. */
	interface Ride {
		/** The traveller's been set down at the far end, beside {@code wagon} (already moved there). */
		void arrived(ServerLevel level, ServerPlayer player, Wagon wagon);

		/** Turned back: the traveller's down where they started (and has their fare back). */
		void turnedBack(ServerLevel level, ServerPlayer player, Wagon wagon);
	}

	private static final class Trip {
		final UUID player;
		final Wagon wagon;
		final Component place;
		final BlockPos at;
		final int blocks, fare, paidTo;
		final Ride ride;
		final long departs, arrives;
		boolean shown;

		Trip(UUID player, Wagon wagon, Component place, BlockPos at, int blocks, int fare, int paidTo, Ride ride, long departs, long arrives) {
			this.player = player;
			this.wagon = wagon;
			this.place = place;
			this.at = at;
			this.blocks = blocks;
			this.fare = fare;
			this.paidTo = paidTo;
			this.ride = ride;
			this.departs = departs;
			this.arrives = arrives;
		}
	}

	/** Travellers' trips, by player. */
	private static final Map<UUID, Trip> TRIPS = new HashMap<>();

	private Journeys() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Start.TYPE, Start.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(End.TYPE, End.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TurnBack.TYPE, TurnBack.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TurnBack.TYPE, (payload, context) -> turnBack(context.player(), true));
		ServerTickEvents.END_SERVER_TICK.register(Journeys::tick);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && TRIPS.containsKey(p.getUUID())));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> turnBack(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRIPS.clear());
	}

	/**
	 * {@code player}, aboard {@code wagon}, is off to {@code place}: they're set down by {@code at}, {@code blocks}
	 * away. {@code fare} emeralds were paid into realm {@code paidTo}'s treasury (-1: nobody's), and are given back if
	 * they turn back.
	 */
	static void begin(ServerLevel level, ServerPlayer player, Wagon wagon, Component place, BlockPos at, int blocks, int fare, int paidTo, Ride ride) {
		double speed = wagon.kind() == Wagon.RICKSHAW ? RICKSHAW_SPEED : CARRIAGE_SPEED;
		int ticks = Math.clamp((int)Math.round(blocks / speed * 20.0), LEAST_TICKS, MOST_TICKS);
		long now = level.getGameTime();
		TRIPS.put(player.getUUID(), new Trip(player.getUUID(), wagon, place, at, blocks, player.isCreative() ? 0 : fare, paidTo, ride, now + DEPART_TICKS,
			now + DEPART_TICKS + ticks));
	}

	/** {@code wagon} carries someone on the road screen: it stands where it is (its driver or puller waits too). */
	static boolean underway(Wagon wagon) {
		for (Trip trip : TRIPS.values()) {
			if (trip.wagon == wagon && trip.shown) {
				return true;
			}
		}
		return false;
	}

	/** {@code player} is on a trip (seen setting off, or on the road screen). */
	public static boolean travelling(Entity player) {
		return TRIPS.containsKey(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		if (TRIPS.isEmpty()) {
			return;
		}
		for (Trip trip : new ArrayList<>(TRIPS.values())) {
			ServerPlayer player = server.getPlayerList().getPlayer(trip.player);
			if (player == null || player.isRemoved() || !player.isAlive()) {
				TRIPS.remove(trip.player);
				continue;
			}
			if (!(player.level() instanceof ServerLevel level) || trip.wagon.isRemoved() || player.getVehicle() != trip.wagon) {
				// (they got down while it set off, or the ride was lost from under them: the fare back)
				turnBack(player, true);
				continue;
			}
			long now = level.getGameTime();
			if (!trip.shown && now >= trip.departs) {
				trip.shown = true;
				DyeColor color = DyeColor.byId(trip.wagon.colorId());
				ServerPlayNetworking.send(player, new Start(trip.wagon.kind(), trip.place, (int)(trip.arrives - now), trip.blocks,
					color.getTextureDiffuseColor() & 0xFFFFFF, land(level, player.blockPosition()), land(level, trip.at), (int)Royals.timeOfDay(level)));
			}
			if (now >= trip.arrives) {
				arrive(level, player, trip);
			}
		}
	}

	/** There: the ride is moved to the far end, the traveller set down beside it. */
	private static void arrive(ServerLevel level, ServerPlayer player, Trip trip) {
		TRIPS.remove(trip.player);
		Wagon wagon = trip.wagon;
		player.stopRiding();
		BlockPos at = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, trip.at), 4);
		Vec3 to = Vec3.atBottomCenterOf(at);
		float yaw = player.getYRot();
		// (the traveller first, so the far end is loading for them before the ride comes)
		player.teleportTo(level, to.x, to.y, to.z, Set.of(), yaw, player.getXRot(), false);
		Entity horse = wagon.horse();
		if (horse != null && horse.isAlive()) {
			horse.teleportTo(to.x, to.y, to.z);
			wagon.trail(horse);
		} else {
			wagon.teleportTo(to.x, to.y, to.z);
		}
		Vec3 down = wagon.getDismountLocationForPassenger(player);
		BlockPos beside = Construction.standable(level, BlockPos.containing(down), 2);
		player.teleportTo(level, beside.getX() + 0.5, beside.getY(), beside.getZ() + 0.5, Set.of(), yaw, player.getXRot(), false);
		ServerPlayNetworking.send(player, new End(true));
		trip.ride.arrived(level, player, wagon);
	}

	/** Turned back (or logged out, {@code online} false): down where they started, their fare back. */
	private static void turnBack(ServerPlayer player, boolean online) {
		Trip trip = TRIPS.remove(player.getUUID());
		if (trip == null) {
			return;
		}
		if (trip.fare > 0) {
			ItemStack back = new ItemStack(Items.EMERALD, trip.fare);
			if (!player.getInventory().add(back) && !back.isEmpty()) {
				player.drop(back, false, Prediction.SERVER_ONLY);
			}
			if (trip.paidTo >= 0) {
				Factions.update(player.level().getServer(), trip.paidTo, f -> f.withTreasury(Math.max(0L, f.treasury() - trip.fare)));
			}
		}
		if (player.getVehicle() == trip.wagon) {
			player.stopRiding();
		}
		if (online) {
			ServerPlayNetworking.send(player, new End(false));
			player.sendSystemMessage(Component.translatable(trip.fare > 0 ? "message.jaymoskingdoms.journey.turned_back" : "message.jaymoskingdoms.journey.turned_back_free",
				trip.fare).withStyle(ChatFormatting.GRAY));
		}
		if (player.level() instanceof ServerLevel level && !trip.wagon.isRemoved()) {
			trip.ride.turnedBack(level, player, trip.wagon);
		}
	}

	/** The look of the land at {@code pos}, for the road screen. */
	static int land(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		if (biome.is(Biomes.CHERRY_GROVE)) {
			return BLOSSOM;
		}
		float temperature = biome.value().getBaseTemperature();
		if (temperature < 0.15F) {
			return SNOW;
		}
		return !biome.value().hasPrecipitation() && temperature > 1.0F ? SAND : GRASS;
	}

	/** The blocks from {@code a} to {@code b}, across. */
	static int blocks(Vec3 a, BlockPos b) {
		return (int)Math.sqrt(Vec3.atBottomCenterOf(b).distanceToSqr(a));
	}

	/** For a ride back from a restart with a traveller aboard: on the road again, no fare to give back. */
	static void resume(ServerLevel level, ServerPlayer player, Wagon wagon, Component place, BlockPos at, Ride ride) {
		if (!travelling(player)) {
			begin(level, player, wagon, place, at, blocks(player.position(), at), 0, -1, ride);
		}
	}
}
