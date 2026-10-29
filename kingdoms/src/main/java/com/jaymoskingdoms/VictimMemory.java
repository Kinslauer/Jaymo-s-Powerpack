package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.332) Victim memory. A player bitten by a vampire player, asleep ({@link SleepingPrey}) or awake, keeps who bit
 * them ({@link #BITER}, kept, forgotten after {@link #FORGET_TICKS}). The next time they wake from sleep, the server sends
 * {@link Flash} and their screen shows a blurred dark-red silhouette of the biter for a few seconds
 * ({@code VictimClient}), drawn from the biter's skin, with no name. A newer bite replaces the older.
 */
public final class VictimMemory {
	/** How long a bite is remembered (ticks: a week). */
	static final long FORGET_TICKS = 168000L;

	/** Who bit them, and when (game time). */
	record Biter(UUID id, long at) {
		static final Codec<Biter> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(Biter::id),
			Codec.LONG.fieldOf("at").forGetter(Biter::at)).apply(i, Biter::new));
	}

	/** On the victim: the last vampire player to bite them. Kept. */
	static final AttachmentType<Biter> BITER = AttachmentRegistry.create(JaymosKingdoms.id("biter"), builder -> builder.persistent(Biter.CODEC));

	/** Server to victim, on waking: show this player's silhouette (their UUID; the client finds the skin). */
	public record Flash(UUID biter) implements CustomPacketPayload {
		public static final Type<Flash> TYPE = new Type<>(JaymosKingdoms.id("victim_flash"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flash> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Flash::biter, Flash::new);

		@Override
		public Type<Flash> type() {
			return TYPE;
		}
	}

	private VictimMemory() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Flash.TYPE, Flash.CODEC);
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Biter b = player.getAttached(BITER);
				if (b != null) {
					player.removeAttached(BITER);
					if (player.level().getGameTime() - b.at() < FORGET_TICKS && ServerPlayNetworking.canSend(player, Flash.TYPE)) {
						ServerPlayNetworking.send(player, new Flash(b.id()));
					}
				}
			}
		});
	}

	/** {@code biter} (a vampire player) drank from {@code victim}. */
	static void bitten(ServerPlayer victim, ServerPlayer biter) {
		victim.setAttached(BITER, new Biter(biter.getUUID(), victim.level().getGameTime()));
	}
}
