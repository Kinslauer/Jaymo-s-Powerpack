package com.jaymoskingdoms.astronomy;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import java.util.BitSet;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Per-player set of discovered stars, kept on the server and synced to the tracking client. */
public class DiscoveryAttachment {

	/** Codec for a bit set: stored as a list of the long[] backing array. */
	static final Codec<BitSet> BIT_SET_CODEC = Codec.LONG.listOf().xmap(
			longs -> BitSet.valueOf(longs.stream().mapToLong(Long::longValue).toArray()),
			bitset -> Arrays.stream(bitset.toLongArray()).boxed().collect(Collectors.toList())
	);

	/** Stream codec for syncing the discovered stars to the client. */
	static final StreamCodec<RegistryFriendlyByteBuf, BitSet> BIT_SET_STREAM = new StreamCodec<>() {
		@Override
		public void encode(RegistryFriendlyByteBuf buf, BitSet value) {
			long[] longs = value.toLongArray();
			buf.writeVarInt(longs.length);
			for (long l : longs) {
				buf.writeLong(l);
			}
		}

		@Override
		public BitSet decode(RegistryFriendlyByteBuf buf) {
			int count = buf.readVarInt();
			long[] longs = new long[count];
			for (int i = 0; i < count; i++) {
				longs[i] = buf.readLong();
			}
			return BitSet.valueOf(longs);
		}
	};

	/** The attachment type for per-player discovered stars. */
	public static final AttachmentType<BitSet> DISCOVERY = AttachmentRegistry.create(
			JaymosKingdoms.id("astronomy_discovery"),
			builder -> builder.persistent(BIT_SET_CODEC).syncWith(BIT_SET_STREAM, AttachmentSyncPredicate.targetOnly())
	);

	/** Get the discovered stars bit set for a player (empty if none yet). */
	public static BitSet getDiscovered(Player player) {
		return player.getAttachedOrElse(DISCOVERY, new BitSet());
	}

	/** Set the discovered stars bit set for a player (call on server). */
	public static void setDiscovered(Player player, BitSet discovered) {
		player.setAttached(DISCOVERY, discovered);
	}
}