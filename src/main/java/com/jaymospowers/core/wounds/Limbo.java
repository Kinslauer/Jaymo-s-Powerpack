package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Limbo (Andy: "give players a 'Limbo' button alongside reincarnate that puts them in a black box and they are allowed to
 * spectate around their corpse, so that they can watch as their teammates bury them or wait to be brought back in a new
 * blood"). On the death screen of a death that ended the character ({@link Mortality#FINAL}) a second button, Limbo, sits beside
 * Reincarnate. Pressing it respawns them as a spectator tethered to their body: a free camera that may drift no further than
 * {@link #RADIUS} blocks from where the body lies (the body is followed if it is carried), under a black frame on their
 * screen. They see everything done to the body, and can go no further.
 * <ul>
 * <li>They leave Limbo by being reborn in another body ({@link #reborn}: the druid's rite, Kingdoms' {@code Rebirths}), or by
 * holding the Leave Limbo key (Backspace) for {@link #LEAVE_TICKS}: then the full wipe goes ahead as if they had pressed
 * Reincarnate.</li>
 * <li>It is kept through logging out and a server restart ({@link #STATE} is saved, and put on them again as they join).</li>
 * </ul>
 */
public final class Limbo {
	// ---- tuning ----
	/** How far (blocks) the camera may drift from the body. */
	public static final double RADIUS = 14.0;
	/** How long the Leave key is held to give up the wait (ticks). */
	public static final int LEAVE_TICKS = 60;
	/** How often the body is looked for, if it has been moved (ticks), and how far from the last place it is looked for. */
	static final int FIND_EVERY = 40;
	static final double FIND_RANGE = 96.0;
	/** The sure wakes already used in a body born again by the rite (of {@link Mortality#SURE_WAKES}). */
	static final int REBORN_WOKEN = 1;
	/** The share of their health a reborn body wakes with, and how long it lies out cold (ticks). */
	static final float REBORN_HEALTH = 0.6F;
	static final int REBORN_COLD = 100;

	/**
	 * Where they wait and what they were: {@code dimension|x|y|z|game mode|day of death}. Saved, kept through the respawn, told to its
	 * player (the black frame and the Leave key read it).
	 */
	public static final AttachmentType<String> STATE = AttachmentRegistry.create(JaymosPowers.id("limbo"),
		builder -> builder.persistent(Codec.STRING).copyOnDeath().syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.targetOnly()));

	/** Client to server: the Limbo button was pressed on the death screen (the respawn follows it). */
	public record Enter() implements CustomPacketPayload {
		public static final Type<Enter> TYPE = new Type<>(JaymosPowers.id("limbo_enter"));
		public static final StreamCodec<ByteBuf, Enter> CODEC = StreamCodec.unit(new Enter());

		@Override
		public Type<Enter> type() {
			return TYPE;
		}
	}

	/** Client to server: the Leave key was held long enough. */
	public record Leave() implements CustomPacketPayload {
		public static final Type<Leave> TYPE = new Type<>(JaymosPowers.id("limbo_leave"));
		public static final StreamCodec<ByteBuf, Leave> CODEC = StreamCodec.unit(new Leave());

		@Override
		public Type<Leave> type() {
			return TYPE;
		}
	}

	/** The place of each one's body, as last found; and those already put in spectator mode this session. */
	private static final Map<UUID, Vec3> CENTRE = new HashMap<>();
	private static final Set<UUID> APPLIED = new HashSet<>();
	private static @Nullable Method owner;
	private static boolean ownerLooked;

	private Limbo() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Enter.TYPE, Enter.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Leave.TYPE, Leave.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Enter.TYPE, (payload, ctx) -> enter(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Leave.TYPE, (payload, ctx) -> leave(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(Limbo::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> APPLIED.remove(handler.player.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			APPLIED.remove(handler.player.getUUID());
			CENTRE.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			APPLIED.clear();
			CENTRE.clear();
		});
	}

	/** Whether {@code player} waits in Limbo. */
	public static boolean in(Player player) {
		return player.hasAttached(STATE);
	}

	/** The place of {@code player}'s body (the camera is tethered to it), or null if they are not in Limbo. */
	public static @Nullable Vec3 body(Player player) {
		Vec3 found = CENTRE.get(player.getUUID());
		if (found != null) {
			return found;
		}
		String[] parts = parts(player);
		return parts == null ? null : new Vec3(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
	}

	/** The game day {@code player}'s body fell (the rite works only within days of it), or -1 if they are not in Limbo. */
	public static long diedOn(Player player) {
		String[] parts = parts(player);
		return parts == null ? -1L : Long.parseLong(parts[5]);
	}

	private static String @Nullable [] parts(Player player) {
		String state = player.getAttached(STATE);
		if (state == null) {
			return null;
		}
		String[] parts = state.split("\\|");
		if (parts.length != 6) {
			return null;
		}
		try {
			Double.parseDouble(parts[1]);
			Double.parseDouble(parts[2]);
			Double.parseDouble(parts[3]);
			Integer.parseInt(parts[4]);
			Long.parseLong(parts[5]);
		} catch (NumberFormatException e) {
			return null;
		}
		return parts;
	}

	private static @Nullable ServerLevel level(MinecraftServer server, String[] parts) {
		Identifier id = Identifier.tryParse(parts[0]);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	// ---- going in ---------------------------------------------------------------------------------------------------

	/** The Limbo button: only for one whose character is over, dead on the death screen (the client respawns them next). */
	private static void enter(ServerPlayer player) {
		if (!Mortality.isFinal(player) || in(player) || Revival.held(player) || player.isAlive()) {
			return;
		}
		String state = player.level().dimension().identifier() + "|" + player.getX() + "|" + player.getY() + "|" + player.getZ() + "|"
			+ GameType.SURVIVAL.getId() + "|" + player.level().getOverworldClockTime() / 24000L;
		player.setAttached(STATE, state);
		JaymosPowers.LOGGER.info("{} chose Limbo", player.getScoreboardName());
	}

	// ---- waiting ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || !in(player)) {
				continue;
			}
			String[] parts = parts(player);
			ServerLevel home = parts == null ? null : level(server, parts);
			if (parts == null || home == null) {
				// (a world that is gone: nothing to wait beside)
				release(player);
				continue;
			}
			Vec3 centre = body(player);
			if (server.getTickCount() % FIND_EVERY == player.getId() % FIND_EVERY) {
				Vec3 moved = findBody(home, player, centre);
				if (moved != null) {
					CENTRE.put(player.getUUID(), moved);
					centre = moved;
				}
			}
			if (!APPLIED.contains(player.getUUID()) || player.gameMode() != GameType.SPECTATOR) {
				APPLIED.add(player.getUUID());
				player.setGameMode(GameType.SPECTATOR);
				player.teleportTo(home, centre.x, centre.y + 1.0, centre.z, Set.of(), player.getYRot(), 20.0F, true);
			}
			if (player.getCamera() != player) {
				player.setCamera(player);
			}
			tether(player, home, centre);
		}
	}

	/** Keeps the camera inside {@link #RADIUS} of {@code centre}, in its own world. */
	private static void tether(ServerPlayer player, ServerLevel home, Vec3 centre) {
		if (player.level() != home) {
			player.teleportTo(home, centre.x, centre.y + 1.0, centre.z, Set.of(), player.getYRot(), player.getXRot(), true);
			return;
		}
		Vec3 away = player.position().subtract(centre);
		double far = away.length();
		if (far > RADIUS) {
			Vec3 back = centre.add(away.scale((RADIUS - 0.5) / far));
			player.teleportTo(home, back.x, back.y, back.z, Set.of(), player.getYRot(), player.getXRot(), true);
			player.setDeltaMovement(Vec3.ZERO);
		}
	}

	/** Where their body lies now if it was carried off (Kitchen's corpse of theirs, near the last place), or null. */
	private static @Nullable Vec3 findBody(ServerLevel level, ServerPlayer player, Vec3 near) {
		if (!ownerLooked) {
			ownerLooked = true;
			try {
				owner = Class.forName("com.jaymoskitchen.corpse.CorpseEntity").getMethod("owner");
			} catch (ReflectiveOperationException | LinkageError e) {
				owner = null;
			}
		}
		if (owner == null) {
			return null;
		}
		UUID id = player.getUUID();
		AABB box = new AABB(near, near).inflate(FIND_RANGE);
		for (Entity e : level.getEntities((Entity) null, box, entity -> owner.getDeclaringClass().isInstance(entity) && !entity.isRemoved())) {
			try {
				if (owner.invoke(e) instanceof Optional<?> who && who.isPresent() && id.equals(who.get())) {
					return e.position();
				}
			} catch (ReflectiveOperationException | RuntimeException ex) {
				owner = null;
				return null;
			}
		}
		return null;
	}

	// ---- coming out -------------------------------------------------------------------------------------------------

	/** The Leave key held: the wait is over and the full wipe goes ahead (as if they had pressed Reincarnate). */
	private static void leave(ServerPlayer player) {
		if (!in(player)) {
			return;
		}
		release(player);
		ServerLevel overworld = player.level().getServer().overworld();
		BlockPos spawn = Mortality.safe(overworld, overworld.getRespawnData().pos());
		player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, Set.of(), player.getYRot(), 0.0F, true);
		JaymosPowers.LOGGER.info("{} left Limbo to be born again", player.getScoreboardName());
	}

	/** Out of the spectator's body, back to what they were; the rebirth (if any) is for the caller. */
	private static void release(ServerPlayer player) {
		String[] parts = parts(player);
		if (player.gameMode() == GameType.SPECTATOR) {
			GameType was = GameType.SURVIVAL;
			if (parts != null) {
				GameType read = GameType.byId(Integer.parseInt(parts[4]));
				was = read == GameType.SPECTATOR ? GameType.SURVIVAL : read;
			}
			player.setGameMode(was);
		}
		player.removeAttached(STATE);
		APPLIED.remove(player.getUUID());
		CENTRE.remove(player.getUUID());
	}

	/**
	 * The druid's rite took (Kingdoms' {@code Rebirths}): {@code player} wakes at {@code at} in {@code level} in another body.
	 * Their name and memories are theirs still, and so is what they carry; the old body's gifts are not
	 * ({@link Mortality#shedGifts}; Kingdoms takes the blood). False if they were not in Limbo.
	 */
	public static boolean reborn(ServerPlayer player, ServerLevel level, Vec3 at) {
		if (!in(player)) {
			return false;
		}
		release(player);
		Mortality.undoFinal(player);
		Mortality.shedGifts(player);
		Mortality.setWoken(player, REBORN_WOKEN);
		player.teleportTo(level, at.x, at.y, at.z, Set.of(), player.getYRot(), 0.0F, true);
		player.setHealth(Math.max(1.0F, player.getMaxHealth() * REBORN_HEALTH));
		player.getFoodData().setFoodLevel(14);
		player.clearFire();
		String line = "message.jaymospowers.limbo.reborn";
		Downed.knockOut(level, player, REBORN_COLD, false, line);
		player.setAttached(Mortality.WAKE_LINE, line);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.6F);
		JaymosPowers.LOGGER.info("{} was born again in another body", player.getScoreboardName());
		return true;
	}

	/** Who is waiting in Limbo in {@code level} (their bodies lie there), for the ones with a rite to give. */
	public static java.util.List<ServerPlayer> waitingIn(ServerLevel level) {
		java.util.List<ServerPlayer> list = new java.util.ArrayList<>();
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			String[] parts = parts(player);
			if (parts != null && in(player) && parts[0].equals(level.dimension().identifier().toString())) {
				list.add(player);
			}
		}
		return list;
	}

	/** A line for a player the rite cannot reach. */
	public static Component noSpirit() {
		return Component.translatable("message.jaymospowers.limbo.no_spirit");
	}
}
