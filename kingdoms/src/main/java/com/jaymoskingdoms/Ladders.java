package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.Captains.Meeting;
import com.jaymoskingdoms.Captains.Trait;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.618) Rivals patch 3, <b>the ladder screen</b>: every ladder of captains you know something of (one you've met,
 * one you know a trait of, one sworn to you or sworn to have your head), its leader first and the rest by rank, with what
 * you know of each (blanks for what you don't), who's sworn, whose bodyguard he is, the ground he holds, and how your last
 * meeting went. Opened from the journal ({@code LadderScreen}).
 */
public final class Ladders {
	/** A row: his name (or blanks), its colour, his place, what you know of him, and your history with him. */
	public record Row(Component name, int color, Component place, Component known, Component history) {
	}

	/** A ladder: its name and its rows. */
	public record Ladder(Component name, List<Row> rows) {
	}

	/** Server → player: open the ladder screen. */
	public record Board(List<Ladder> ladders) implements CustomPacketPayload {
		public static final Type<Board> TYPE = new Type<>(JaymosKingdoms.id("ladder_board"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = CustomPacketPayload.codec(Board::write, Board::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(ladders.size());
			for (Ladder l : ladders) {
				ComponentSerialization.STREAM_CODEC.encode(buf, l.name());
				buf.writeVarInt(l.rows().size());
				for (Row r : l.rows()) {
					ComponentSerialization.STREAM_CODEC.encode(buf, r.name());
					buf.writeInt(r.color());
					ComponentSerialization.STREAM_CODEC.encode(buf, r.place());
					ComponentSerialization.STREAM_CODEC.encode(buf, r.known());
					ComponentSerialization.STREAM_CODEC.encode(buf, r.history());
				}
			}
		}

		private static Board read(RegistryFriendlyByteBuf buf) {
			int n = buf.readVarInt();
			List<Ladder> out = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
				int m = buf.readVarInt();
				List<Row> rows = new ArrayList<>();
				for (int j = 0; j < m; j++) {
					rows.add(new Row(ComponentSerialization.STREAM_CODEC.decode(buf), buf.readInt(), ComponentSerialization.STREAM_CODEC.decode(buf),
						ComponentSerialization.STREAM_CODEC.decode(buf), ComponentSerialization.STREAM_CODEC.decode(buf)));
				}
				out.add(new Ladder(name, rows));
			}
			return new Board(out);
		}

		@Override
		public Type<Board> type() {
			return TYPE;
		}
	}

	/** Player → server: the journal's Captains button was pressed. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("ladder_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	static final int GOLD = 0xFFFFC21A, FOE = 0xFFE05050, YOURS = 0xFF66C266, PLAIN = 0xFFFFFFFF, UNKNOWN = 0xFF707080;

	private Ladders() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Board.TYPE, Board.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			ServerPlayNetworking.send(player, board(player));
		});
	}

	/** What {@code player} knows of the ladders. */
	static Board board(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID you = player.getUUID();
		Map<String, List<String>> known = player.getAttachedOrElse(Captains.KNOWN, Map.of());
		List<String> sworn = player.getAttachedOrElse(Captains.SWORN, List.of());
		Map<String, SwornCaptains.Bond> yours = SwornCaptains.bondsOf(server, you);
		Map<String, List<Captain>> byLadder = new LinkedHashMap<>();
		for (Captain c : Captains.all(server).values()) {
			if (!c.fallen()) {
				byLadder.computeIfAbsent(c.tribe(), k -> new ArrayList<>()).add(c);
			}
		}
		List<Ladder> out = new ArrayList<>();
		for (Map.Entry<String, List<Captain>> e : byLadder.entrySet()) {
			List<Captain> cs = e.getValue();
			boolean seen = cs.stream().anyMatch(c -> known.containsKey(c.id()) || sworn.contains(c.id()) || yours.containsKey(c.id())
				|| c.met().containsKey(you.toString()));
			if (!seen) {
				continue;
			}
			cs.sort(Comparator.comparing((Captain c) -> !c.chief()).thenComparing(Comparator.comparingInt(Captain::rank).reversed()));
			List<Row> rows = new ArrayList<>();
			for (Captain c : cs) {
				rows.add(row(server, c, you, known.getOrDefault(c.id(), List.of()), sworn.contains(c.id()), yours.containsKey(c.id())));
			}
			out.add(new Ladder(Rivals.name(e.getKey()), rows));
		}
		return new Board(out);
	}

	private static Row row(MinecraftServer server, Captain c, UUID you, List<String> known, boolean foe, boolean yours) {
		Meeting m = c.met().get(you.toString());
		boolean named = m != null || !known.isEmpty() || foe || yours;
		Component name = named ? Component.literal(c.name()) : Component.translatable("ladder.jaymoskingdoms.unknown");
		int color = yours ? YOURS : foe ? FOE : c.chief() ? GOLD : named ? PLAIN : UNKNOWN;
		MutableComponent place = Component.translatable(c.chief() ? "ladder.jaymoskingdoms.leader" : "ladder.jaymoskingdoms.rank", c.rank());
		if (yours) {
			place.append(" ").append(Component.translatable("ladder.jaymoskingdoms.yours").withStyle(ChatFormatting.GREEN));
		} else if (foe) {
			place.append(" ").append(Component.translatable("ladder.jaymoskingdoms.foe").withStyle(ChatFormatting.RED));
		}
		String boss = CaptainSchemes.bossOf(server, c.id());
		Captain b = boss == null ? null : Captains.get(server, boss);
		if (b != null && named) {
			place.append(" ").append(Component.translatable("ladder.jaymoskingdoms.guard", b.name()));
		}
		int ground = CaptainGround.held(server, c);
		if (ground > 0 && named) {
			place.append(" ").append(Component.translatable("ladder.jaymoskingdoms.ground", ground));
		}
		MutableComponent knows = Component.empty();
		int shown = 0;
		for (String key : c.traits()) {
			Trait t = Trait.parse(key);
			if (t == null) {
				continue;
			}
			if (shown++ > 0) {
				knows.append(", ");
			}
			knows.append(known.contains(key) ? Component.translatable("captain.jaymoskingdoms.trait." + key)
				: Component.translatable("ladder.jaymoskingdoms.blank").withStyle(ChatFormatting.DARK_GRAY));
		}
		Component history = m == null ? Component.translatable("ladder.jaymoskingdoms.never_met")
			: Component.translatable("ladder.jaymoskingdoms.met", m.times(), Component.translatableWithFallback("ladder.jaymoskingdoms.last." + m.last(), m.last()));
		return new Row(name, color, place, knows, history);
	}
}
