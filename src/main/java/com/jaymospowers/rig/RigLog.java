package com.jaymospowers.rig;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * The visor's combat log (Andy's Rig picks, ideas only): every blow a Helm wearer gives or takes goes to their own screen
 * ({@link Entry}: whom, how hard), where {@code RigHud} keeps the last few with their body's state from Wounds.
 */
public final class RigLog {
	/** Server → wearer: a blow given ({@code dealt}) to, or taken from, {@code who}, of {@code amount}. */
	public record Entry(boolean dealt, Component who, float amount) implements CustomPacketPayload {
		public static final Type<Entry> TYPE = new Type<>(JaymosPowers.id("rig_log"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Entry::dealt, ComponentSerialization.STREAM_CODEC, Entry::who, ByteBufCodecs.FLOAT, Entry::amount, Entry::new);

		@Override
		public Type<Entry> type() {
			return TYPE;
		}
	}

	private RigLog() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Entry.TYPE, Entry.CODEC);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken <= 0.0F) {
				return;
			}
			Entity by = source.getEntity();
			if (entity instanceof ServerPlayer hurt && Rig.wears(hurt, Rig.Piece.HELM)) {
				send(hurt, new Entry(false, by != null ? by.getDisplayName() : Component.translatable("rig.jaymospowers.hud.log.unknown"), taken));
			}
			if (by instanceof ServerPlayer striker && by != entity && Rig.wears(striker, Rig.Piece.HELM)) {
				send(striker, new Entry(true, entity.getDisplayName(), taken));
			}
		});
	}

	private static void send(ServerPlayer player, Entry entry) {
		if (ServerPlayNetworking.canSend(player, Entry.TYPE)) {
			ServerPlayNetworking.send(player, entry);
		}
	}
}
