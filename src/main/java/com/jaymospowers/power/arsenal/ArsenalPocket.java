package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pocket Dimension (the idea of MavLeague's Pocket Dimension datapack, remade for Arsenal): every Arsenal user owns one
 * room in {@code jaymospowers:arsenal_pocket}. Z (or B uncorrupted) tears a rift open a couple of blocks in front of you;
 * walk into it to go in (and from inside, Z opens one that leads back out to where you left). Z again closes it. Any
 * player who walks into your rift follows you, and it stays open a few seconds after you pass through.
 * The room is yours to build in; its shell can't be broken. The Rift Hoard (the ender chest at the back) is the old
 * Pocket Stash, so everything already stored, and every relic you claim, is still there. The portal standing on the crying
 * obsidian pad by the door takes anyone home.
 */
public final class ArsenalPocket {
	public static final ResourceKey<Level> POCKET = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("arsenal_pocket"));
	/** The room's inside is (2 * HALF + 1) square and HEIGHT tall; its floor is at FLOOR - 1; rooms sit SPACING apart on x. */
	static final int HALF = 7;
	static final int HEIGHT = 7;
	static final int FLOOR = 64;
	static final int SPACING = 512;
	/** How long a rift stays open (and stays open again after its owner passes), and how close you must walk to it. */
	static final int RIFT_TICKS = 200;
	static final double RIFT_RADIUS = 0.7;
	/** How far in front of you the rift opens (it comes closer if something's in the way). */
	static final double RIFT_DISTANCE = 2.5;
	/** The rift's outline: half its width, and its height. */
	static final double RIFT_HALF_WIDTH = 0.6, RIFT_HEIGHT = 2.2;
	/** No one passes through twice within this many ticks (so arriving never bounces you straight back). */
	static final int PASS_COOLDOWN = 20;
	/** Ceiling lights every this many blocks. */
	static final int LIGHT_STEP = 4;

	/** Where to send someone when they step out. */
	public record Return(ResourceKey<Level> dimension, Vec3 pos, float yaw, float pitch) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Return::dimension),
			Vec3.CODEC.fieldOf("pos").forGetter(Return::pos),
			Codec.FLOAT.fieldOf("yaw").forGetter(Return::yaw),
			Codec.FLOAT.fieldOf("pitch").forGetter(Return::pitch)
		).apply(i, Return::new));
	}

	/** The player's room number, kept for good. */
	public static final AttachmentType<Integer> ROOM = AttachmentRegistry.create(JaymosPowers.id("arsenal_pocket_room"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** Where the player came in from. */
	public static final AttachmentType<Return> RETURN = AttachmentRegistry.create(JaymosPowers.id("arsenal_pocket_return"),
		builder -> builder.persistent(Return.CODEC).copyOnDeath());
	/** On the overworld: the next free room number. */
	public static final AttachmentType<Integer> NEXT_ROOM = AttachmentRegistry.create(JaymosPowers.id("arsenal_pocket_next"),
		builder -> builder.persistent(Codec.INT));

	/** A rift standing open: into {@code room}, or, when {@code out}, back out of the pocket. Faces along {@code yaw}. */
	private static final class Rift {
		final ResourceKey<Level> dimension;
		final Vec3 pos;
		final float yaw;
		final UUID owner;
		final int room;
		final boolean out;
		final boolean voidRift;
		final long opened;
		long until;

		Rift(ResourceKey<Level> dimension, Vec3 pos, float yaw, UUID owner, int room, boolean out, boolean voidRift, long opened, long until) {
			this.dimension = dimension;
			this.pos = pos;
			this.yaw = yaw;
			this.owner = owner;
			this.room = room;
			this.out = out;
			this.voidRift = voidRift;
			this.opened = opened;
			this.until = until;
		}
	}

	/** How far away a rift (or your own void gate) is drawn, and how often the list is re-sent. */
	static final double VIEW_DISTANCE = 96.0;
	static final int SYNC_EVERY = 5;
	/** How long the door pad's portal stays after the last person leaves the room. */
	static final int PAD_LINGER = 40;

	/**
	 * Server → client, every {@link #SYNC_EVERY} ticks: the doorways to draw near you. A pocket rift (nether portal swirl,
	 * or the End's starfield when its opener is corrupted) facing {@code yaw}, opening from {@code opened}; or one of
	 * your own void gates ({@code gate}), always the void, turned toward you.
	 */
	public record Portal(double x, double y, double z, float yaw, long opened, boolean voidRift, boolean gate) {
	}

	public record Portals(List<Portal> portals) implements CustomPacketPayload {
		public static final Type<Portals> TYPE = new Type<>(JaymosPowers.id("arsenal_portals"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Portals> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.portals.size());
				for (Portal portal : p.portals) {
					buf.writeDouble(portal.x);
					buf.writeDouble(portal.y);
					buf.writeDouble(portal.z);
					buf.writeFloat(portal.yaw);
					buf.writeVarLong(portal.opened);
					buf.writeBoolean(portal.voidRift);
					buf.writeBoolean(portal.gate);
				}
			},
			buf -> {
				int n = Math.min(buf.readVarInt(), 256);
				List<Portal> list = new ArrayList<>(n);
				for (int i = 0; i < n; i++) {
					list.add(new Portal(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarLong(), buf.readBoolean(),
						buf.readBoolean()));
				}
				return new Portals(list);
			});

		@Override
		public Type<Portals> type() {
			return TYPE;
		}
	}

	private static final List<Rift> RIFTS = new ArrayList<>();
	/** The standing portal over each occupied room's door pad belongs to no one (so no one's Z closes it). */
	private static final UUID PAD = new UUID(0L, 0L);
	/** Who was last sent a list with something in it (so they get one empty list when it all closes). */
	private static final Set<UUID> SHOWING = new HashSet<>();
	private static final Map<UUID, Long> LAST_PASS = new HashMap<>();

	private ArsenalPocket() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Portals.TYPE, Portals.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ArsenalPocket::serverTick);
		ServerTickEvents.END_SERVER_TICK.register(ArsenalPocket::sync);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			level.dimension() != POCKET || player.isCreative() || !protectedBlock(pos));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || level.dimension() != POCKET || hand != InteractionHand.MAIN_HAND
				|| player.isShiftKeyDown() || !(player instanceof ServerPlayer server)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			int room = roomAt(pos);
			if (pos.equals(exitPad(room))) {
				leave(server);
				return InteractionResult.SUCCESS;
			}
			if (pos.equals(hoard(room))) {
				if (server.getAttached(ROOM) instanceof Integer own && own == room) {
					ArsenalStash.open(server);
				} else {
					server.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.pocket_not_yours").withStyle(ChatFormatting.DARK_PURPLE));
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
	}

	// ---------------------------------------------------------------- the key

	/** Z (or B): a rift opens in front of you (into your room, or, from inside any pocket, back out); again, it closes. */
	public static void toggle(ServerPlayer player) {
		ServerLevel level = player.level();
		if (RIFTS.removeIf(r -> r.owner.equals(player.getUUID()) && r.dimension == level.dimension())) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 0.7F, 0.6F);
			return;
		}
		boolean out = level.dimension() == POCKET;
		int room = out ? roomAt(player.blockPosition()) : room(player);
		Vec3 at = riftSpot(player);
		RIFTS.add(new Rift(level.dimension(), at, player.getYRot(), player.getUUID(), room, out, ArsenalVoid.isVoid(player), level.getGameTime(),
			level.getGameTime() + RIFT_TICKS));
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 30, 0.3, 0.8, 0.3, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4F, 1.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.7F, 0.6F);
	}

	/** In front of you at your feet, as far as {@link #RIFT_DISTANCE}, but never inside a wall. */
	private static Vec3 riftSpot(ServerPlayer player) {
		double rad = Math.toRadians(player.getYRot());
		Vec3 ahead = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad));
		Vec3 feet = player.position();
		for (double d = RIFT_DISTANCE; d > 1.0; d -= 0.5) {
			Vec3 at = feet.add(ahead.scale(d));
			if (player.level().noCollision(new AABB(at.x - 0.3, at.y + 0.05, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3))) {
				return at;
			}
		}
		return feet.add(ahead);
	}

	private static int room(ServerPlayer player) {
		Integer room = player.getAttached(ROOM);
		if (room == null) {
			ServerLevel overworld = player.level().getServer().overworld();
			room = overworld.getAttachedOrElse(NEXT_ROOM, 0);
			overworld.setAttached(NEXT_ROOM, room + 1);
			player.setAttached(ROOM, room);
		}
		return room;
	}

	private static void enter(ServerPlayer player, int room) {
		MinecraftServer server = player.level().getServer();
		ServerLevel pocket = server.getLevel(POCKET);
		if (pocket == null) {
			return;
		}
		ServerLevel from = player.level();
		if (player.isPassenger()) {
			player.stopRiding();
		}
		if (from.dimension() != POCKET) {
			player.setAttached(RETURN, new Return(from.dimension(), player.position(), player.getYRot(), player.getXRot()));
		}
		burst(from, player.position());
		build(pocket, room);
		BlockPos centre = centre(room);
		Vec3 at = new Vec3(centre.getX() + 0.5, FLOOR, centre.getZ() + HALF - 1.5);
		LAST_PASS.put(player.getUUID(), server.getTickCount() + 0L);
		player.teleportTo(pocket, at.x, at.y, at.z, Set.of(), 180.0F, 0.0F, true);
		burst(pocket, at);
		boolean own = player.getAttached(ROOM) instanceof Integer mine && mine == room;
		player.sendOverlayMessage((own ? Component.translatable("message.jaymospowers.arsenal.pocket_enter")
			: Component.translatable("message.jaymospowers.arsenal.pocket_visit")).withStyle(ChatFormatting.LIGHT_PURPLE));
	}

	/** Back to where they came in from (or to the world spawn, if that's gone). */
	public static void leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Return back = player.getAttached(RETURN);
		ServerLevel to = back != null ? server.getLevel(back.dimension()) : null;
		Vec3 at;
		float yaw = player.getYRot();
		float pitch = player.getXRot();
		if (to == null || to.dimension() == POCKET) {
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
		// stepping out next to the rift you came in by doesn't pull you straight back in
		LAST_PASS.put(player.getUUID(), server.getTickCount() + (long) RIFT_TICKS);
		player.teleportTo(to, at.x, at.y, at.z, Set.of(), yaw, pitch, true);
		player.removeAttached(RETURN);
		// you're back: the rift you went in by closes behind you
		RIFTS.removeIf(r -> !r.out && r.owner.equals(player.getUUID()));
		burst(to, at);
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 40, 0.3, 0.8, 0.3, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.7F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.3F);
	}

	// ---------------------------------------------------------------- the rift left behind

	private static void serverTick(MinecraftServer server) {
		keepPadPortals(server);
		if (RIFTS.isEmpty()) {
			return;
		}
		long tick = server.getTickCount();
		for (Iterator<Rift> it = RIFTS.iterator(); it.hasNext(); ) {
			Rift rift = it.next();
			ServerLevel level = server.getLevel(rift.dimension);
			if (level == null || level.getGameTime() > rift.until) {
				it.remove();
				continue;
			}
			Vec3 p = rift.pos;
			if (tick % 2 == 0) {
				outline(level, rift);
			}
			AABB box = new AABB(p.x - RIFT_RADIUS, p.y - 0.5, p.z - RIFT_RADIUS, p.x + RIFT_RADIUS, p.y + 2.0, p.z + RIFT_RADIUS);
			for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, box)) {
				if (other.isSpectator() || !other.isAlive() || tick - LAST_PASS.getOrDefault(other.getUUID(), -1000L) <= PASS_COOLDOWN) {
					continue;
				}
				if (rift.out) {
					leave(other);
				} else {
					enter(other, rift.room);
				}
				if (other.getUUID().equals(rift.owner)) {
					// the owner's gone through: the rift holds a while longer for anyone following
					rift.until = level.getGameTime() + RIFT_TICKS;
				}
			}
		}
	}

	/**
	 * The door pad of every room someone is in holds a standing portal out (walk into it to leave, as through a rift
	 * opened with Z); it closes a little after the room empties.
	 */
	private static void keepPadPortals(MinecraftServer server) {
		ServerLevel pocket = server.getLevel(POCKET);
		if (pocket == null || server.getTickCount() % 10 != 0) {
			return;
		}
		long now = pocket.getGameTime();
		for (ServerPlayer player : pocket.players()) {
			int room = roomAt(player.blockPosition());
			Rift pad = null;
			for (Rift rift : RIFTS) {
				if (rift.owner.equals(PAD) && rift.room == room) {
					pad = rift;
					break;
				}
			}
			if (pad == null) {
				Vec3 at = Vec3.atBottomCenterOf(exitPad(room).above());
				// faces into the room
				RIFTS.add(pad = new Rift(POCKET, at, 180.0F, PAD, room, true, false, now, now));
			}
			pad.until = now + PAD_LINGER;
		}
	}

	/** The doorway itself is drawn on the client; here, only a few motes drift off it. */
	private static void outline(ServerLevel level, Rift rift) {
		Vec3 c = rift.pos.add(0.0, RIFT_HEIGHT / 2.0, 0.0);
		level.sendParticles(rift.voidRift ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, c.x, c.y, c.z, 3, 0.3, 0.7, 0.3, 0.2);
	}

	/** Every few ticks, each player is told the doorways near them (and their own void gates). */
	private static void sync(MinecraftServer server) {
		if (server.getTickCount() % SYNC_EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<Portal> list = new ArrayList<>();
			ResourceKey<Level> here = player.level().dimension();
			Vec3 me = player.position();
			for (Rift rift : RIFTS) {
				if (rift.dimension == here && rift.pos.distanceToSqr(me) < VIEW_DISTANCE * VIEW_DISTANCE) {
					// the door pad's portal shows each viewer their own kind (the void when they're corrupted)
					boolean voidRift = rift.owner.equals(PAD) ? ArsenalVoid.isVoid(player) : rift.voidRift;
					list.add(new Portal(rift.pos.x, rift.pos.y, rift.pos.z, rift.yaw, rift.opened, voidRift, false));
				}
			}
			if (com.jaymospowers.core.Fate.isCorrupted(player)) {
				for (ArsenalGates.Gate gate : ArsenalGates.gates(player)) {
					if (gate.dimension() == here && gate.pos().distanceToSqr(me) < VIEW_DISTANCE * VIEW_DISTANCE) {
						list.add(new Portal(gate.pos().x, gate.pos().y, gate.pos().z, 0.0F, 0L, true, true));
					}
				}
			}
			if (list.isEmpty() && !SHOWING.remove(player.getUUID())) {
				continue;
			}
			if (!list.isEmpty()) {
				SHOWING.add(player.getUUID());
			}
			if (ServerPlayNetworking.canSend(player, Portals.TYPE)) {
				ServerPlayNetworking.send(player, new Portals(list));
			}
		}
	}

	// ---------------------------------------------------------------- the room

	static BlockPos centre(int room) {
		return new BlockPos(room * SPACING, FLOOR, 0);
	}

	/** Which room a pocket block belongs to (the nearest). */
	static int roomAt(BlockPos pos) {
		return Math.floorDiv(pos.getX() + SPACING / 2, SPACING);
	}

	static BlockPos exitPad(int room) {
		return centre(room).offset(0, -1, HALF);
	}

	static BlockPos hoard(int room) {
		return centre(room).offset(0, 0, -HALF);
	}

	/** The shell, the door pad and the hoard: nobody but a creative player breaks those. */
	static boolean protectedBlock(BlockPos pos) {
		int room = roomAt(pos);
		if (pos.equals(hoard(room))) {
			return true;
		}
		int dx = Math.abs(pos.getX() - room * SPACING);
		int dz = Math.abs(pos.getZ());
		int y = pos.getY();
		if (dx > HALF + 1 || dz > HALF + 1 || y < FLOOR - 1 || y > FLOOR + HEIGHT) {
			return false;
		}
		return dx == HALF + 1 || dz == HALF + 1 || y == FLOOR - 1 || y == FLOOR + HEIGHT;
	}

	/** Builds the room the first time anyone comes in (the door pad marks it built). */
	static void build(ServerLevel pocket, int room) {
		BlockPos centre = centre(room);
		if (pocket.getBlockState(exitPad(room)).is(Blocks.CRYING_OBSIDIAN)) {
			return;
		}
		BlockState wall = Blocks.OBSIDIAN.defaultBlockState();
		BlockState light = Blocks.CRYING_OBSIDIAN.defaultBlockState();
		for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
			for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
				for (int y = FLOOR - 1; y <= FLOOR + HEIGHT; y++) {
					BlockPos pos = new BlockPos(centre.getX() + dx, y, dz);
					if (protectedBlock(pos)) {
						boolean lamp = y == FLOOR + HEIGHT && Math.abs(dx) <= HALF && Math.abs(dz) <= HALF
							&& Math.floorMod(dx, LIGHT_STEP) == LIGHT_STEP / 2 && Math.floorMod(dz, LIGHT_STEP) == LIGHT_STEP / 2;
						pocket.setBlock(pos, lamp ? light : wall, Block.UPDATE_CLIENTS);
					} else {
						pocket.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
		}
		pocket.setBlock(hoard(room), Blocks.ENDER_CHEST.defaultBlockState().setValue(EnderChestBlock.FACING, Direction.SOUTH), Block.UPDATE_CLIENTS);
		pocket.setBlock(exitPad(room), light, Block.UPDATE_CLIENTS);
	}
}
