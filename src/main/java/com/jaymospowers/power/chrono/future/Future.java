package com.jaymospowers.power.chrono.future;

import com.jaymospowers.JaymosPowers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chronokinesis's leap into the future: a lifetime on, the world is a ruined city overrun by feral dead
 * ({@code jaymospowers:future}, built from Los Perrito City by Uospag; see {@link LosPerrito}).
 * <ul>
 * <li>Sneak+B (Fast Forward), for any Chrono user past the starter: you, and every player within
 * {@link #COMPANION_RADIUS} blocks, leap to the future. Sneak+B there brings you (and those near you) back to where
 * you left.</li>
 * <li>The Time Rift beside where you arrive takes anyone home, so no one is ever stranded without a Chrono user. Dying
 * there sends you to your own spawn as usual.</li>
 * <li>The first leap on a server fetches the city ({@link LosPerrito}); until it's in place, the leap only says how far
 * along it is.</li>
 * </ul>
 * Ruins: {@link FutureRuins}. The dead: {@link FutureFerals}.
 */
public final class Future {
	public static final ResourceKey<Level> FUTURE = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("future"));
	/** Who comes along: every player this close to the one leaping. */
	static final double COMPANION_RADIUS = 4.0;
	/** Fast Forward's own recharge (a minute; it doesn't touch Time Bloom's). */
	static final int LEAP_COOLDOWN = 1200;
	/** The Time Rift stands this many blocks north of the arrival point; walk within RIFT_RADIUS of it to go home. */
	static final int RIFT_OFFSET = 5;
	static final double RIFT_RADIUS = 0.8;
	/** Nobody is sent through twice within this many ticks. */
	static final int PASS_COOLDOWN = 60;

	/** Where to send someone when they come back. */
	public record Return(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Return::dimension),
			Vec3.CODEC.fieldOf("pos").forGetter(Return::pos),
			Codec.FLOAT.fieldOf("yaw").forGetter(Return::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(Return::pitch)
		).apply(i, Return::new));
	}

	/** Where the player left from. */
	public static final AttachmentType<Return> RETURN = AttachmentRegistry.create(JaymosPowers.id("future_return"),
		builder -> builder.persistent(Return.CODEC).copyOnDeath());
	/** Has this player been told whose city this is? */
	public static final AttachmentType<Boolean> CREDITED = AttachmentRegistry.create(JaymosPowers.id("future_credited"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** On an item frame in the future: its map art has been moved to the city's map ids (or it came empty). */
	public static final AttachmentType<Boolean> FRAME_FIXED = AttachmentRegistry.create(JaymosPowers.id("future_frame"),
		builder -> builder.persistent(Codec.BOOL));

	private static final Map<UUID, Long> READY_AT = new HashMap<>();
	private static final Map<UUID, Long> LAST_PASS = new HashMap<>();
	private static BlockPos rift;

	private Future() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LosPerrito.load(server);
			rift = null;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			READY_AT.clear();
			LAST_PASS.clear();
			FutureRuins.clear();
			rift = null;
		});
		ServerChunkEvents.CHUNK_LOAD.register(FutureRuins::onLoad);
		ServerEntityEvents.ENTITY_LOAD.register(Future::onEntityLoad);
		ServerTickEvents.END_SERVER_TICK.register(FutureRuins::tick);
		ServerTickEvents.END_SERVER_TICK.register(FutureFerals::tick);
		ServerTickEvents.END_SERVER_TICK.register(Future::tickRift);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	// ---------------------------------------------------------------- the leap

	/** Sneak+B: into the future, or back from it, with everyone close by. */
	public static void leap(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		long ready = READY_AT.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.recharging", (ready - now + 19) / 20)
				.withStyle(ChatFormatting.GOLD));
			return;
		}
		if (player.level().dimension() == FUTURE) {
			for (ServerPlayer other : companions(player)) {
				goBack(other);
			}
			READY_AT.put(player.getUUID(), now + LEAP_COOLDOWN);
			return;
		}
		if (!ready(player)) {
			return;
		}
		List<ServerPlayer> going = companions(player);
		for (int i = 0; i < going.size(); i++) {
			goForward(going.get(i), i, going.size());
		}
		READY_AT.put(player.getUUID(), now + LEAP_COOLDOWN);
	}

	/** Is the city in place? If not, start it coming and say how far along it is. */
	static boolean ready(ServerPlayer player) {
		switch (LosPerrito.stage()) {
			case READY -> {
				return player.level().getServer().getLevel(FUTURE) != null;
			}
			case IDLE, FAILED -> {
				boolean retry = LosPerrito.stage() == LosPerrito.Stage.FAILED;
				LosPerrito.begin(player.level().getServer());
				player.sendSystemMessage(Component.translatable(retry ? "message.jaymospowers.future.retrying" : "message.jaymospowers.future.fetching")
					.withStyle(ChatFormatting.GOLD));
			}
			case DOWNLOADING -> player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.downloading", LosPerrito.percent())
				.withStyle(ChatFormatting.GOLD));
			case UNPACKING -> player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.unpacking", LosPerrito.percent())
				.withStyle(ChatFormatting.GOLD));
		}
		return false;
	}

	private static List<ServerPlayer> companions(ServerPlayer player) {
		List<ServerPlayer> list = new ArrayList<>();
		list.add(player);
		for (ServerPlayer other : player.level().getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(COMPANION_RADIUS))) {
			if (other != player && other.isAlive() && !other.isSpectator()) {
				list.add(other);
			}
		}
		return list;
	}

	static void goForward(ServerPlayer player, int index, int of) {
		MinecraftServer server = player.level().getServer();
		ServerLevel future = server.getLevel(FUTURE);
		if (future == null) {
			return;
		}
		ServerLevel from = player.level();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		player.setAttached(RETURN, new Return(from.dimension(), player.position(), player.getYRot(), player.getXRot()));
		burst(from, player.position());
		double angle = of <= 1 ? 0.0 : Math.PI * 2.0 * index / of;
		double radius = of <= 1 ? 0.0 : 1.5;
		int x = LosPerrito.SPAWN_X + (int) Math.round(Math.cos(angle) * radius);
		int z = LosPerrito.SPAWN_Z + (int) Math.round(Math.sin(angle) * radius);
		future.getChunk(x >> 4, z >> 4);
		int y = future.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		LAST_PASS.put(player.getUUID(), server.getTickCount() + 0L);
		// face the rift home, so the way back is the first thing you see
		player.teleportTo(future, x + 0.5, y, z + 0.5, Set.of(), 180.0F, 0.0F, true);
		burst(future, player.position());
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.arrived").withStyle(ChatFormatting.GOLD));
		if (!Boolean.TRUE.equals(player.getAttached(CREDITED))) {
			player.setAttached(CREDITED, true);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.future.credit")
				.withStyle(style -> style.withColor(ChatFormatting.GRAY).withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(LosPerrito.CREDIT_URL)))));
		}
	}

	/** Back to where they left from (or the world spawn if that's gone). */
	static void goBack(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Return back = player.getAttached(RETURN);
		ServerLevel to = back != null ? server.getLevel(back.dimension()) : null;
		Vec3 at;
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (to == null || to.dimension() == FUTURE) {
			to = server.overworld();
			at = Vec3.atBottomCenterOf(to.getRespawnData().pos());
		} else {
			at = back.pos();
			yaw = back.yaw();
			pitch = back.pitch();
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		burst(player.level(), player.position());
		LAST_PASS.put(player.getUUID(), server.getTickCount() + 0L);
		player.teleportTo(to, at.x, at.y, at.z, Set.of(), yaw, pitch, true);
		player.removeAttached(RETURN);
		burst(to, at);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.future.returned").withStyle(ChatFormatting.GOLD));
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 40, 0.4, 0.9, 0.4, 0.05);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.8, 0.3, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.6F, 0.8F);
	}

	// ---------------------------------------------------------------- the rift home

	static void tickRift(MinecraftServer server) {
		ServerLevel future = server.getLevel(FUTURE);
		if (future == null || future.players().isEmpty() || server.getTickCount() % 2 != 0) {
			return;
		}
		int x = LosPerrito.SPAWN_X, z = LosPerrito.SPAWN_Z - RIFT_OFFSET;
		if (!future.hasChunkAt(new BlockPos(x, 0, z))) {
			return;
		}
		if (rift == null) {
			rift = new BlockPos(x, future.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		}
		double cx = rift.getX() + 0.5, cy = rift.getY(), cz = rift.getZ() + 0.5;
		boolean watched = false;
		for (ServerPlayer player : future.players()) {
			if (player.distanceToSqr(cx, cy, cz) < 64.0 * 64.0) {
				watched = true;
				break;
			}
		}
		if (!watched) {
			return;
		}
		long tick = server.getTickCount();
		// a slow golden whirl, two blocks tall
		double turn = tick * 0.15;
		for (int i = 0; i < 3; i++) {
			double a = turn + i * Math.PI * 2.0 / 3.0;
			future.sendParticles(ParticleTypes.END_ROD, cx + Math.cos(a) * 0.6, cy + 0.2 + (tick + i * 13) % 40 / 20.0, cz + Math.sin(a) * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
		}
		future.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy + 1.0, cz, 2, 0.25, 0.7, 0.25, 0.02);
		if (tick % 80 == 0) {
			future.playSound(null, cx, cy, cz, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.6F, 1.4F);
		}
		AABB box = new AABB(cx - RIFT_RADIUS, cy - 0.5, cz - RIFT_RADIUS, cx + RIFT_RADIUS, cy + 2.0, cz + RIFT_RADIUS);
		for (ServerPlayer player : future.getEntitiesOfClass(ServerPlayer.class, box)) {
			if (player.isAlive() && !player.isSpectator() && tick - LAST_PASS.getOrDefault(player.getUUID(), -1000L) > PASS_COOLDOWN) {
				goBack(player);
			}
		}
	}

	// ---------------------------------------------------------------- the city's map art

	/** The city's billboards and posters are maps; their ids are moved up past the world's own (see {@link LosPerrito}). */
	private static void onEntityLoad(Entity entity, ServerLevel level) {
		if (level.dimension() != FUTURE || !(entity instanceof ItemFrame frame) || Boolean.TRUE.equals(frame.getAttached(FRAME_FIXED))) {
			return;
		}
		frame.setAttached(FRAME_FIXED, true);
		ItemStack held = frame.getItem();
		MapId id = held.get(DataComponents.MAP_ID);
		if (id != null && LosPerrito.mapOffset() > 0) {
			ItemStack moved = held.copy();
			moved.set(DataComponents.MAP_ID, new MapId(id.id() + LosPerrito.mapOffset()));
			frame.setItem(moved, false);
		}
	}

	// ---------------------------------------------------------------- testing

	/**
	 * {@code /future go} (you and those near you, Chrono or not), {@code /future back}, {@code /future fetch} (start
	 * fetching the city now; works from the console), {@code /future status} (gamemasters only).
	 */
	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("future")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("go").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (player.level().dimension() == FUTURE || !ready(player)) {
					return 0;
				}
				List<ServerPlayer> going = companions(player);
				for (int i = 0; i < going.size(); i++) {
					goForward(going.get(i), i, going.size());
				}
				return going.size();
			}))
			.then(Commands.literal("back").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (player.level().dimension() != FUTURE) {
					return 0;
				}
				goBack(player);
				return 1;
			}))
			.then(Commands.literal("fetch").executes(c -> {
				// from the console too: get the city in place before anyone leaps
				LosPerrito.begin(c.getSource().getServer());
				c.getSource().sendSuccess(() -> Component.literal("Los Perrito: " + LosPerrito.stage()), false);
				return 1;
			}))
			.then(Commands.literal("status").executes(c -> {
				String failure = LosPerrito.failure();
				c.getSource().sendSuccess(() -> Component.literal("Los Perrito: " + LosPerrito.stage() + " " + LosPerrito.percent() + "%"
					+ (failure.isEmpty() ? "" : " (" + failure + ")") + ", map offset " + LosPerrito.mapOffset()), false);
				return 1;
			})));
	}
}
