package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Sense in the dark: a web slinger sees faintly where the light is low, a dim night sight and never full brightness
 * (the client lifts the lightmap a little, see {@code WebDarkSenseMixin}). On by default; the switch is the power
 * screen's fourth slot, and it is kept with the player (through death).
 */
public final class WebDarkSense {
	/** The faintest the sight gets lifted to, in the darkest places (night vision's own scale: 1 is full). */
	public static final float STRENGTH = 0.3F;
	/** Light at or above this (0-15, sky and blocks together) needs no help; the lift fades in below it. */
	public static final int DARK_BELOW = 8;

	/** Set when the slinger switched it off (nothing set: on). Synced to the owner for the client's lift. */
	public static final AttachmentType<Boolean> OFF = AttachmentRegistry.create(JaymosPowers.id("web_dark_sense_off"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	/** Client to server: flip it. */
	public record Toggle() implements CustomPacketPayload {
		public static final Type<Toggle> TYPE = new Type<>(JaymosPowers.id("web_dark_sense"));
		public static final StreamCodec<ByteBuf, Toggle> CODEC = StreamCodec.unit(new Toggle());

		@Override
		public Type<Toggle> type() {
			return TYPE;
		}
	}

	private WebDarkSense() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (!Powers.has(player, WebPower.INSTANCE)) {
				return;
			}
			boolean on = !on(player);
			if (on) {
				player.removeAttached(OFF);
			} else {
				player.setAttached(OFF, true);
			}
			player.sendOverlayMessage(Component.translatable(on ? "message.jaymospowers.web_dark_sense_on" : "message.jaymospowers.web_dark_sense_off")
				.withStyle(ChatFormatting.GRAY));
		});
	}

	/** Whether this slinger's dark sense is switched on (it is, unless they turned it off). */
	public static boolean on(Player player) {
		return !player.getAttachedOrElse(OFF, false);
	}
}
