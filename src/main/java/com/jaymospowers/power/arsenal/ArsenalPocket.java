package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
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
 * room in {@code jaymospowers:arsenal_pocket}. Z (or B uncorrupted) steps through a rift into it, and from inside, back
 * out to where you left. The rift you leave by stays open a few seconds and any player who walks into it follows you.
 * The room is yours to build in; its shell can't be broken. The Rift Hoard (the ender chest at the back) is the old
 * Pocket Stash, so everything already stored, and every relic you claim, is still there. The crying obsidian pad by
 * the door takes anyone home.
 */
public final class ArsenalPocket {
	public static final ResourceKey<Level> POCKET = ResourceKey.create(Registries.DIMENSION, JaymosPowers.id("arsenal_pocket"));
	/** The room's inside is (2 * HALF + 1) square and HEIGHT tall; its floor is at FLOOR - 1; rooms sit SPACING apart on x. */
	static final int HALF = 7;
	static final int HEIGHT = 7;
	static final int FLOOR = 64;
	static final int SPACING = 512;
	/** How long the rift you entered by stays open for others, and how close they must walk to it. */
	static final int RIFT_TICKS = 200;
	static final double RIFT_RADIUS = 1.2;
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

	private record Rift(ResourceKey<Level> dimension, Vec3 pos, UUID owner, int room, long until) {
	}

	private static final List<Rift> RIFTS = new ArrayList<>();
	private static final Map<UUID, Long> LAST_PASS = new HashMap<>();

	private ArsenalPocket() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ArsenalPocket::serverTick);
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

	/** Z (or B): into your room, or, from inside any pocket, back out. */
	public static void toggle(ServerPlayer player) {
		if (player.level().dimension() == POCKET) {
			leave(player);
		} else {
			enter(player, room(player), true);
		}
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

	private static void enter(ServerPlayer player, int room, boolean openRift) {
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
		if (openRift) {
			RIFTS.add(new Rift(from.dimension(), player.position(), player.getUUID(), room, from.getGameTime() + RIFT_TICKS));
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
		burst(to, at);
	}

	private static void burst(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1.0, at.z, 40, 0.3, 0.8, 0.3, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_CHEST_OPEN, SoundSource.PLAYERS, 0.7F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.3F);
	}

	// ---------------------------------------------------------------- the rift left behind

	private static void serverTick(MinecraftServer server) {
		if (RIFTS.isEmpty()) {
			return;
		}
		long tick = server.getTickCount();
		for (Iterator<Rift> it = RIFTS.iterator(); it.hasNext(); ) {
			Rift rift = it.next();
			ServerLevel level = server.getLevel(rift.dimension());
			if (level == null || level.getGameTime() > rift.until()) {
				it.remove();
				continue;
			}
			Vec3 p = rift.pos();
			if (tick % 2 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y + 1.0, p.z, 6, 0.15, 0.7, 0.15, 0.01);
				level.sendParticles(ParticleTypes.PORTAL, p.x, p.y + 1.0, p.z, 4, 0.2, 0.8, 0.2, 0.3);
			}
			AABB box = new AABB(p.x - RIFT_RADIUS, p.y - 0.5, p.z - RIFT_RADIUS, p.x + RIFT_RADIUS, p.y + 2.0, p.z + RIFT_RADIUS);
			for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, box)) {
				if (!other.getUUID().equals(rift.owner()) && !other.isSpectator() && other.isAlive()
					&& tick - LAST_PASS.getOrDefault(other.getUUID(), -1000L) > PASS_COOLDOWN) {
					enter(other, rift.room(), false);
				}
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
