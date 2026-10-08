package com.jaymospowers.net;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The core's packets. Powers add their own in their own package (and register them from their init()). */
public final class PowerPayloads {
	private PowerPayloads() {
	}

	/** Client → server: an ability key ({@link com.jaymospowers.api.PowerKey} ordinal) went down or up. */
	public record KeyInput(int key, boolean down) implements CustomPacketPayload {
		public static final Type<KeyInput> TYPE = new Type<>(JaymosPowers.id("key_input"));
		public static final StreamCodec<RegistryFriendlyByteBuf, KeyInput> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.key);
				buf.writeBoolean(p.down);
			},
			buf -> new KeyInput(buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<KeyInput> type() {
			return TYPE;
		}
	}

	/** Client → server: the ascended ability's key went down or up. */
	public record UltimateKey(boolean down) implements CustomPacketPayload {
		public static final Type<UltimateKey> TYPE = new Type<>(JaymosPowers.id("ultimate_key"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UltimateKey> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeBoolean(p.down),
			buf -> new UltimateKey(buf.readBoolean()));

		@Override
		public Type<UltimateKey> type() {
			return TYPE;
		}
	}

	/** Client → server: pick a power on the powers screen (creative only); an empty id clears it. */
	public record ChoosePower(String power) implements CustomPacketPayload {
		public static final Type<ChoosePower> TYPE = new Type<>(JaymosPowers.id("choose_power"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ChoosePower> CODEC = CustomPacketPayload.codec(
			(p, buf) -> buf.writeUtf(p.power),
			buf -> new ChoosePower(buf.readUtf(256)));

		@Override
		public Type<ChoosePower> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(KeyInput.TYPE, KeyInput.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ChoosePower.TYPE, ChoosePower.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UltimateKey.TYPE, UltimateKey.CODEC);
	}
}
