package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "I do not like these text based loading bars. The executions and tree chopping are the best, show me how youd rework
 * them all in that style." Every progress bar that was a line of {@code |...}, {@code [===---]} or squares on the action bar
 * is sent here instead, and drawn by the client ({@code client.hud.HudBars}) the way the execution bars are:
 * <ul>
 * <li>{@link #hold}: something you keep doing (reviving, lifting, untying): three framed bars under the crosshair, filling
 * in turn, a short caption under them.</li>
 * <li>{@link #gauge}: a level that rises and falls (ink, heat, fuel): one bar in five segments over the hotbar, its label at
 * the left and its number at the right.</li>
 * <li>{@link #marks}: a count (chops into a tree, tugs on a stake): one framed square per mark, red once all are in.</li>
 * <li>{@link #tug}: a contest (Will clash): your gold against their red, the line between them where it stands.</li>
 * </ul>
 * A hold that has the hands ({@code locks}) keeps the mouse from swinging, as the old {@code |...} text did ({@code HeldInput}).
 * <p>Jaymo's Kingdoms and Kitchen don't depend on this mod, so they send theirs through {@link #SHARE} in Fabric Loader's
 * object share: an {@code Object[]} of {player, id, style, fill, caption, number}, {@code style} made by {@link #style}.
 */
public final class HudBar {
	public static final int HOLD = 0, GAUGE = 1, MARKS = 2, TUG = 3;
	/** The colours (a filling one and a bright full one each, the client's {@code HudBars.TINTS}). */
	public static final int RED = 0, GREY = 1, GOLD = 2, PURPLE = 3, AQUA = 4, GREEN = 5, DARK_RED = 6, YELLOW = 7;
	/** Where Kingdoms and Kitchen find the sender. */
	public static final String SHARE = "jaymos:hud_bar";

	/** Server to client: show (or refresh) bar {@code id}. */
	public record Show(String id, int style, float fill, Optional<Component> caption, Optional<Component> number) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(JaymosPowers.id("hud_bar"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Show::id,
			ByteBufCodecs.VAR_INT, Show::style,
			ByteBufCodecs.FLOAT, Show::fill,
			ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), Show::caption,
			ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC), Show::number,
			Show::new);

		@Override
		public Type<Show> type() {
			return TYPE;
		}

		public int kind() {
			return style & 7;
		}

		public int tint() {
			return style >> 3 & 15;
		}

		/** Marks: how many in all. */
		public int count() {
			return style >> 7 & 255;
		}

		public boolean locks() {
			return (style >> 15 & 1) != 0;
		}
	}

	private HudBar() {
	}

	public static int style(int kind, int tint, int count, boolean locks) {
		return kind | tint << 3 | Math.min(255, count) << 7 | (locks ? 1 << 15 : 0);
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
		FabricLoader.getInstance().getObjectShare().put(SHARE, (Consumer<Object[]>) HudBar::shared);
	}

	/** A hold, {@code fill} of the way done. {@code locks}: it has the hands (no swinging till both buttons are let go). */
	public static void hold(ServerPlayer player, String id, float fill, int tint, @Nullable Component caption, boolean locks) {
		send(player, id, style(HOLD, tint, 0, locks), fill, caption, null);
	}

	/** A level, {@code fill} of full, labelled; {@code number} at its right, if any. */
	public static void gauge(ServerPlayer player, String id, float fill, int tint, Component label, @Nullable Component number) {
		send(player, id, style(GAUGE, tint, 0, false), fill, label, number);
	}

	/** {@code filled} of {@code of} marks. */
	public static void marks(ServerPlayer player, String id, int filled, int of, int tint, @Nullable Component caption) {
		send(player, id, style(MARKS, tint, of, false), of <= 0 ? 1.0F : filled / (float) of, caption, null);
	}

	/** A contest: {@code lead} of the bar is yours (0.5 even), {@code them} names the other side. It has the hands. */
	public static void tug(ServerPlayer player, String id, float lead, Component them) {
		send(player, id, style(TUG, GOLD, 0, true), lead, them, null);
	}

	private static void send(ServerPlayer player, String id, int style, float fill, @Nullable Component caption, @Nullable Component number) {
		if (ServerPlayNetworking.canSend(player, Show.TYPE)) {
			ServerPlayNetworking.send(player, new Show(id, style, Math.max(0.0F, Math.min(1.0F, fill)), Optional.ofNullable(caption), Optional.ofNullable(number)));
		}
	}

	/** From Kingdoms or Kitchen: {player, id, style, fill, caption or null, number or null}. */
	private static void shared(Object[] bar) {
		send((ServerPlayer) bar[0], (String) bar[1], (Integer) bar[2], (Float) bar[3], (Component) bar[4], (Component) bar[5]);
	}
}
