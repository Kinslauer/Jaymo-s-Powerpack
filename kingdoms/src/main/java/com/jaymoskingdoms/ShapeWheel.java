package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The wheel of shapes: hold the Druid's class key to open a ring of the shapes known, pick one with the mouse, star favourites
 * (right click) so they sit at the top; let go on a slice, or tap the key, to take the shape. With sneak held as the key is
 * let go, the slice calls a wild beast of that kind instead ({@link BeastCall}). The client draws it
 * (client {@code ShapeWheelScreen}); here are the packets and what the server answers.
 */
public final class ShapeWheel {
	/** What a pick does. */
	public static final int TAKE = 0, CALL = 1, STAR = 2;

	/** One slice: the shape, whether this Druid's rank keeps them from it, whether it is a favourite. */
	public record Slice(String form, boolean locked, boolean star) {
	}

	/** Player → server: the class key is held long enough; send the wheel. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("shape_wheel_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** Server → player: the shapes known, the favourites first. {@code open}: the wheel should open (else it only refreshes). */
	public record Data(List<Slice> slices, boolean open) implements CustomPacketPayload {
		public static final Type<Data> TYPE = new Type<>(JaymosKingdoms.id("shape_wheel"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeBoolean(p.open);
			buf.writeVarInt(p.slices.size());
			for (Slice s : p.slices) {
				buf.writeUtf(s.form);
				buf.writeBoolean(s.locked);
				buf.writeBoolean(s.star);
			}
		}, buf -> {
			boolean open = buf.readBoolean();
			int n = buf.readVarInt();
			List<Slice> slices = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				slices.add(new Slice(buf.readUtf(), buf.readBoolean(), buf.readBoolean()));
			}
			return new Data(slices, open);
		});

		@Override
		public Type<Data> type() {
			return TYPE;
		}
	}

	/** Player → server: a slice picked ({@link #TAKE}, {@link #CALL}) or starred ({@link #STAR}). */
	public record Pick(String form, int what) implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("shape_wheel_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeUtf(p.form);
				buf.writeVarInt(p.what);
			}, buf -> new Pick(buf.readUtf(), buf.readVarInt()));

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	private ShapeWheel() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Data.TYPE, Data.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, context) -> send(context.player(), true));
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> pick(context.player(), payload));
	}

	/** The wheel's slices for {@code player}: favourites first, then by name. */
	static List<Slice> slices(ServerPlayer player) {
		List<String> stars = player.getAttachedOrElse(Druids.FAVOURITES, List.<String>of());
		List<Slice> out = new ArrayList<>();
		for (String form : Druids.allKnown(player)) {
			out.add(new Slice(form, !BeastForms.allowed(player, form), stars.contains(form)));
		}
		out.sort(Comparator.comparing((Slice s) -> !s.star));
		return out;
	}

	private static void send(ServerPlayer player, boolean open) {
		if (!Classes.is(player, Classes.PlayerClass.DRUID) || !player.isAlive() || Druids.shaped(player)) {
			return;
		}
		ServerPlayNetworking.send(player, new Data(slices(player), open));
	}

	private static void pick(ServerPlayer player, Pick pick) {
		if (!Classes.is(player, Classes.PlayerClass.DRUID) || !player.isAlive() || player.isSpectator() || !Druids.allKnown(player).contains(pick.form)) {
			return;
		}
		switch (pick.what) {
			case TAKE -> Druids.pick(player, pick.form);
			case CALL -> BeastCall.call(player, pick.form);
			case STAR -> {
				List<String> stars = new ArrayList<>(player.getAttachedOrElse(Druids.FAVOURITES, List.<String>of()));
				if (!stars.remove(pick.form)) {
					stars.add(pick.form);
				}
				player.setAttached(Druids.FAVOURITES, List.copyOf(stars));
				send(player, false);
			}
			default -> {
			}
		}
	}
}
