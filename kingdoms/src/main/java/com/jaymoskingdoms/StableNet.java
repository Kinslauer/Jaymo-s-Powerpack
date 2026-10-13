package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.jaymoskingdoms.equestrian.Equine;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/**
 * (0.5.45) The stable screen's packets: a stablehand's or farrier's screen as sections of rows, each row a horse or a
 * ware with its buttons ({@link Open}), and the button the player pressed ({@link Act}). The server decides everything
 * and sends the screen again with the result.
 */
public final class StableNet {
	/** What a button does ({@code arg}: a horse's entity id, a market or tack index). */
	public static final int BOARD = 0, COLLECT = 1, BUY = 2, SELL = 3, SHOE = 4, BUY_TACK = 5, QUESTS = 6;

	/** A button on a row. */
	public record Button(Component text, int action, int arg, boolean active) {
		static void write(RegistryFriendlyByteBuf buf, Button b) {
			ComponentSerialization.STREAM_CODEC.encode(buf, b.text);
			buf.writeVarInt(b.action);
			buf.writeVarInt(b.arg);
			buf.writeBoolean(b.active);
		}

		static Button read(RegistryFriendlyByteBuf buf) {
			return new Button(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
		}
	}

	/** A horse or a ware: its icon (a horse is drawn in its coat), name, a line about it, and what can be done with it. */
	public record Row(ItemStack icon, Optional<Equine> horse, Component label, Component detail, List<Button> buttons) {
		static void write(RegistryFriendlyByteBuf buf, Row r) {
			ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, r.icon);
			buf.writeBoolean(r.horse.isPresent());
			r.horse.ifPresent(e -> Equine.STREAM_CODEC.encode(buf, e));
			ComponentSerialization.STREAM_CODEC.encode(buf, r.label);
			ComponentSerialization.STREAM_CODEC.encode(buf, r.detail);
			buf.writeVarInt(r.buttons.size());
			for (Button b : r.buttons) {
				Button.write(buf, b);
			}
		}

		static Row read(RegistryFriendlyByteBuf buf) {
			ItemStack icon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
			Optional<Equine> horse = buf.readBoolean() ? Optional.of(Equine.STREAM_CODEC.decode(buf)) : Optional.empty();
			Component label = ComponentSerialization.STREAM_CODEC.decode(buf);
			Component detail = ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = buf.readVarInt();
			List<Button> buttons = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				buttons.add(Button.read(buf));
			}
			return new Row(icon, horse, label, detail, buttons);
		}
	}

	/** A heading and its rows ({@code empty} shown when there are none). */
	public record Section(Component heading, Component empty, List<Row> rows) {
		static void write(RegistryFriendlyByteBuf buf, Section s) {
			ComponentSerialization.STREAM_CODEC.encode(buf, s.heading);
			ComponentSerialization.STREAM_CODEC.encode(buf, s.empty);
			buf.writeVarInt(s.rows.size());
			for (Row r : s.rows) {
				Row.write(buf, r);
			}
		}

		static Section read(RegistryFriendlyByteBuf buf) {
			Component heading = ComponentSerialization.STREAM_CODEC.decode(buf);
			Component empty = ComponentSerialization.STREAM_CODEC.decode(buf);
			int n = buf.readVarInt();
			List<Row> rows = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				rows.add(Row.read(buf));
			}
			return new Section(heading, empty, rows);
		}
	}

	/** Server → player: open worker {@code workerId}'s screen ({@code note}: what the last button did, or empty). */
	public record Open(int workerId, Component title, Component subtitle, Component note, List<Section> sections) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("stable_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.workerId);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.title);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.subtitle);
				ComponentSerialization.STREAM_CODEC.encode(buf, p.note);
				buf.writeVarInt(p.sections.size());
				for (Section s : p.sections) {
					Section.write(buf, s);
				}
			},
			buf -> {
				int worker = buf.readVarInt();
				Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component subtitle = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component note = ComponentSerialization.STREAM_CODEC.decode(buf);
				int n = buf.readVarInt();
				List<Section> sections = new ArrayList<>();
				for (int i = 0; i < n; i++) {
					sections.add(Section.read(buf));
				}
				return new Open(worker, title, subtitle, note, sections);
			});

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Player → server: pressed {@code action} ({@code arg}) on worker {@code workerId}'s screen. */
	public record Act(int workerId, int action, int arg) implements CustomPacketPayload {
		public static final Type<Act> TYPE = new Type<>(JaymosKingdoms.id("stable_act"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.workerId);
				buf.writeVarInt(p.action);
				buf.writeVarInt(p.arg);
			},
			buf -> new Act(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Act> type() {
			return TYPE;
		}
	}

	private StableNet() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Act.TYPE, Act.CODEC);
	}
}
