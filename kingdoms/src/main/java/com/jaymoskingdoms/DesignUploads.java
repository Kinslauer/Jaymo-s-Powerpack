package com.jaymoskingdoms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.488) Schematic files sent up from a player's own computer: the mason's table's screen ({@code MasonScreen},
 * {@code DesignUploadClient}) reads a {@code .nbt}, {@code .schem} or {@code .litematic} from its {@code schematics}
 * folder and sends it in pieces (a packet carries at most 32 KB); once the last piece is in, the server reads it and keeps it
 * as one of the player's designs ({@link Designs#saveUploaded}). Nothing in the file is run: it's only read as NBT, within a
 * size quota.
 */
public final class DesignUploads {
	/** The most bytes one piece carries. */
	public static final int PIECE = 30000;
	/** The most pieces one file may come in (about 1.9 MB compressed). */
	public static final int MAX_PIECES = 64;
	/** The most NBT a file may unpack to (bytes counted by the reader). */
	static final long NBT_QUOTA = 64L * 1024L * 1024L;
	/** A file still coming in is dropped after this long without a piece (ticks). */
	static final int STALE_TICKS = 20 * 60;

	/**
	 * Client to server: piece {@code index} of {@code total} of a file to keep as design {@code name}, sent from the
	 * screen of the mason's table at {@code table} (the design is then drawn on a plan and the screen shown again).
	 */
	public record Piece(String name, int index, int total, byte[] data, BlockPos table) implements CustomPacketPayload {
		public static final Type<Piece> TYPE = new Type<>(JaymosKingdoms.id("design_upload"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Piece> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeUtf(p.name, 64);
			buf.writeVarInt(p.index);
			buf.writeVarInt(p.total);
			buf.writeByteArray(p.data);
			buf.writeBlockPos(p.table);
		}, buf -> new Piece(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PIECE), buf.readBlockPos()));

		@Override
		public Type<Piece> type() {
			return TYPE;
		}
	}

	/** A file coming in. */
	private static final class Pending {
		final String name;
		final int total;
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int next;
		int lastTick;

		Pending(String name, int total) {
			this.name = name;
			this.total = total;
		}
	}

	private static final Map<UUID, Pending> PENDING = new HashMap<>();

	private DesignUploads() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Piece.TYPE, Piece.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Piece.TYPE, (payload, context) -> receive(context.player(), payload));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PENDING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	private static void receive(ServerPlayer player, Piece piece) {
		int now = player.level().getServer().getTickCount();
		UUID id = player.getUUID();
		if (piece.total() < 1 || piece.total() > MAX_PIECES) {
			PENDING.remove(id);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.design.upload_big", PIECE * MAX_PIECES / 1024).withStyle(ChatFormatting.RED));
			return;
		}
		Pending p = PENDING.get(id);
		if (piece.index() == 0) {
			p = new Pending(piece.name(), piece.total());
			PENDING.put(id, p);
		} else if (p == null || now - p.lastTick > STALE_TICKS || !p.name.equals(piece.name()) || p.total != piece.total() || p.next != piece.index()) {
			// out of step: start again
			PENDING.remove(id);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.design.upload_broken").withStyle(ChatFormatting.RED));
			return;
		}
		p.bytes.writeBytes(piece.data());
		p.next++;
		p.lastTick = now;
		if (p.next < p.total) {
			return;
		}
		PENDING.remove(id);
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(new ByteArrayInputStream(p.bytes.toByteArray()), NbtAccounter.create(NBT_QUOTA));
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.info("{}'s schematic {} couldn't be read: {}", player.getGameProfile().name(), p.name, e.toString());
			MasonTable.uploaded(player, piece.table(), () -> {
				MasonTable.tell(player, Component.translatable("message.jaymoskingdoms.design.unreadable").withStyle(ChatFormatting.RED));
				return null;
			});
			return;
		}
		String name = p.name.toLowerCase(java.util.Locale.ROOT);
		MasonTable.uploaded(player, piece.table(), () -> {
			String saved = Designs.saveUploaded(player, name, root);
			if (saved != null) {
				JaymosKingdoms.LOGGER.info("{} uploaded design {}", player.getGameProfile().name(), saved);
			}
			return saved;
		});
	}
}
