package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/**
 * What a player has switched off in the symbiote's Settings screen (opened from its Status page). The screen keeps its
 * own copy in the client's config folder and sends the lot here as a bit mask ({@link SetOff}); the server keeps it on
 * the player ({@link #OFF}, saved, kept through death). Anything on the server that the screen can switch off asks
 * {@link #off}: the fidgets, the jealousy and the requests of {@code WebSymbioteMind}/{@code WebSymbioteMemory} must
 * check {@code off(player, Setting.FIDGETS)}, {@code Setting.JEALOUSY} and {@code Setting.REQUESTS} before they act; the
 * one-time hints ({@code WebSymbioteBody#hint}) do. {@link Setting#EDGE} (the screen-edge membrane) is drawn on the client.
 */
public final class WebSymbioteSettings {
	/** The things that can be switched off. The bit of each is {@code 1 << ordinal()}: keep the order. */
	public enum Setting {
		FIDGETS, JEALOUSY, REQUESTS, HINTS, EDGE;

		public int bit() {
			return 1 << ordinal();
		}
	}

	/** The switched-off settings as a bit mask. Saved, kept through death; the server's copy only. */
	public static final AttachmentType<Integer> OFF = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_settings_off"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Client to server: the whole mask of switched-off settings. */
	public record SetOff(int mask) implements CustomPacketPayload {
		public static final Type<SetOff> TYPE = new Type<>(JaymosPowers.id("web_symbiote_settings"));
		public static final StreamCodec<ByteBuf, SetOff> CODEC = ByteBufCodecs.VAR_INT.map(SetOff::new, SetOff::mask);

		@Override
		public Type<SetOff> type() {
			return TYPE;
		}
	}

	private WebSymbioteSettings() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(SetOff.TYPE, SetOff.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SetOff.TYPE, (payload, ctx) -> {
			int mask = payload.mask() & ((1 << Setting.values().length) - 1);
			if (mask == 0) {
				ctx.player().removeAttached(OFF);
			} else {
				ctx.player().setAttached(OFF, mask);
			}
		});
	}

	/** {@code setting} is switched off for this player (always false for anyone who hasn't touched the screen). */
	public static boolean off(Player player, Setting setting) {
		return (player.getAttachedOrElse(OFF, 0) & setting.bit()) != 0;
	}
}
