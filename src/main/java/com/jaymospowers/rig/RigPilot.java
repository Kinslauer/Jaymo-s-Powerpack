package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;

/**
 * The Mind's autopilot (Andy's Rig picks, ideas only): "fly me to the village" ({@link RigTalk}) and, in full flight, the suit flies
 * the wearer to the nearest village it knows of (a meeting bell within {@link #SEARCH}), or "home" to their bed, for
 * {@link #COST} charge a second. The flying is the client's ({@code RigPilotClient}, steering the Rig's own flight); any movement
 * key takes control back ({@link Cancel}). It sets down near the spot and says so.
 */
public final class RigPilot {
	// ---- tuning ----
	/** How far it looks for a village (blocks), the charge a second it costs, and how near counts as there (blocks, flat). */
	static final int SEARCH = 256, COST = 2;
	static final double ARRIVE = 8.0;

	/** Server → wearer: fly to {@code to} (empty: the course is over). */
	public record Course(Optional<BlockPos> to) implements CustomPacketPayload {
		public static final Type<Course> TYPE = new Type<>(JaymosPowers.id("rig_course"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Course> CODEC = StreamCodec.composite(
			net.minecraft.network.codec.ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Course::to, Course::new);

		@Override
		public Type<Course> type() {
			return TYPE;
		}
	}

	/** Wearer → server: they took the controls back. */
	public record Cancel() implements CustomPacketPayload {
		public static final Type<Cancel> TYPE = new Type<>(JaymosPowers.id("rig_course_cancel"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Cancel> CODEC = StreamCodec.unit(new Cancel());

		@Override
		public Type<Cancel> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, BlockPos> COURSES = new HashMap<>();

	private RigPilot() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Course.TYPE, Course.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Cancel.TYPE, Cancel.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Cancel.TYPE, (payload, context) -> stop(context.player(), false));
		ServerTickEvents.END_SERVER_TICK.register(RigPilot::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> COURSES.clear());
	}

	/** "Fly me to {@code where}": sets the course, if the suit is flying and knows the place. */
	static void order(ServerPlayer player, ItemStack core, String where) {
		if (!Rig.fullSet(player) || Rig.level(player) < Rig.FLIGHT_LEVEL || !RigActives.flying(player)) {
			RigMind.tell(player, core, "pilot.airborne");
			return;
		}
		ServerLevel level = player.level();
		BlockPos to = null;
		String place = null;
		if (where.contains("village") || where.contains("town")) {
			PoiManager pois = level.getPoiManager();
			to = pois.findClosest(type -> type.is(PoiTypes.MEETING), player.blockPosition(), SEARCH, PoiManager.Occupancy.ANY).orElse(null);
			place = "village";
		} else if (where.contains("home") || where.contains("bed")) {
			ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
			if (respawn != null && respawn.respawnData().dimension() == level.dimension()) {
				to = respawn.respawnData().pos();
			}
			place = "home";
		}
		if (place == null) {
			RigMind.tell(player, core, "pilot.unknown", net.minecraft.network.chat.Component.literal(where));
			return;
		}
		if (to == null) {
			RigMind.tell(player, core, "pilot.none");
			return;
		}
		COURSES.put(player.getUUID(), to.immutable());
		ServerPlayNetworking.send(player, new Course(Optional.of(to)));
		RigMind.tell(player, core, "pilot.go", net.minecraft.network.chat.Component.translatable("rig.jaymospowers.pilot." + place),
			Math.round(Math.sqrt(to.distSqr(player.blockPosition()))));
	}

	/** The course is over; {@code said}: the Mind says so. True if there was one. */
	static boolean stop(ServerPlayer player, boolean said) {
		if (COURSES.remove(player.getUUID()) == null) {
			return false;
		}
		ServerPlayNetworking.send(player, new Course(Optional.empty()));
		if (said) {
			RigMind.tell(player, Rig.core(player), "pilot.stop");
		}
		return true;
	}

	private static void tick(MinecraftServer server) {
		if (COURSES.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		for (Iterator<Map.Entry<UUID, BlockPos>> it = COURSES.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, BlockPos> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			BlockPos to = e.getValue();
			double dx = to.getX() + 0.5 - player.getX(), dz = to.getZ() + 0.5 - player.getZ();
			String end = !RigActives.flying(player) || !Rig.fullSet(player) ? "stop"
				: dx * dx + dz * dz < ARRIVE * ARRIVE ? "here" : !Rig.spend(player, COST) ? "empty" : null;
			if (end != null) {
				it.remove();
				ServerPlayNetworking.send(player, new Course(Optional.empty()));
				RigMind.tell(player, Rig.core(player), "pilot." + end);
			}
		}
	}
}
