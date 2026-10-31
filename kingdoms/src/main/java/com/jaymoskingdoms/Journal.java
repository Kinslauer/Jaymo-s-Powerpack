package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * (0.5.49) The traveller's journal: a book bound with a map (crafted from the two). Carry one and the journal key (M) or
 * using it opens its three pages: your quests, a map of the land about you with your quests and the settlements on it,
 * and what each settlement thinks of you. Without one in your inventory the key does nothing but remind you.
 */
public final class Journal {
	/** Settlements this far from the player (blocks) are written in; further off, only those that know your name. */
	static final double PLACES_RANGE = 3000.0;
	/** The most settlements written in. */
	static final int MAX_PLACES = 48;

	public static Item JOURNAL;

	/** One quest as the journal shows it. */
	public record Entry(boolean royal, boolean ready, Component title, Component giver, Component text, Component objective, Optional<BlockPos> target) {
	}

	/** One settlement: its name, kind ({@link FactionType} ordinal), colour, seat, and (if it rates you) your reputation and its word. */
	public record Place(String name, int type, int color, BlockPos seat, boolean rated, int reputation, String word) {
	}

	/** Server → player: open the journal. {@code here}: the player stands in the settlements' world (markers belong on the map). */
	public record Book(List<Entry> entries, List<Place> places, boolean here) implements CustomPacketPayload {
		public static final Type<Book> TYPE = new Type<>(JaymosKingdoms.id("journal_book"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Book> CODEC = CustomPacketPayload.codec(Book::write, Book::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(entries.size());
			for (Entry e : entries) {
				buf.writeBoolean(e.royal);
				buf.writeBoolean(e.ready);
				ComponentSerialization.STREAM_CODEC.encode(buf, e.title);
				ComponentSerialization.STREAM_CODEC.encode(buf, e.giver);
				ComponentSerialization.STREAM_CODEC.encode(buf, e.text);
				ComponentSerialization.STREAM_CODEC.encode(buf, e.objective);
				buf.writeBoolean(e.target.isPresent());
				e.target.ifPresent(buf::writeBlockPos);
			}
			buf.writeVarInt(places.size());
			for (Place p : places) {
				buf.writeUtf(p.name);
				buf.writeVarInt(p.type);
				buf.writeInt(p.color);
				buf.writeBlockPos(p.seat);
				buf.writeBoolean(p.rated);
				buf.writeInt(p.reputation);
				buf.writeUtf(p.word);
			}
			buf.writeBoolean(here);
		}

		private static Book read(RegistryFriendlyByteBuf buf) {
			int n = buf.readVarInt();
			List<Entry> entries = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				boolean royal = buf.readBoolean(), ready = buf.readBoolean();
				Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component giver = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component text = ComponentSerialization.STREAM_CODEC.decode(buf);
				Component objective = ComponentSerialization.STREAM_CODEC.decode(buf);
				Optional<BlockPos> target = buf.readBoolean() ? Optional.of(buf.readBlockPos()) : Optional.empty();
				entries.add(new Entry(royal, ready, title, giver, text, objective, target));
			}
			n = buf.readVarInt();
			List<Place> places = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				places.add(new Place(buf.readUtf(), buf.readVarInt(), buf.readInt(), buf.readBlockPos(), buf.readBoolean(), buf.readInt(), buf.readUtf()));
			}
			return new Book(entries, places, buf.readBoolean());
		}

		@Override
		public Type<Book> type() {
			return TYPE;
		}
	}

	/** Player → server: the journal key was pressed. */
	public record Ask() implements CustomPacketPayload {
		public static final Type<Ask> TYPE = new Type<>(JaymosKingdoms.id("journal_ask"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ask> CODEC = StreamCodec.unit(new Ask());

		@Override
		public Type<Ask> type() {
			return TYPE;
		}
	}

	/** The journal itself: using it opens it. */
	static final class JournalItem extends Item {
		JournalItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (player instanceof ServerPlayer sp) {
				open(sp);
			}
			return InteractionResult.SUCCESS;
		}
	}

	private Journal() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("journal"));
		JOURNAL = Registry.register(BuiltInRegistries.ITEM, key, new JournalItem(new Item.Properties().setId(key).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, JOURNAL));
		PayloadTypeRegistry.clientboundPlay().register(Book.TYPE, Book.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Ask.TYPE, Ask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Ask.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (carries(player)) {
				open(player);
			} else {
				player.sendOverlayMessage(Component.translatable("journal.jaymoskingdoms.need").withStyle(ChatFormatting.GRAY));
			}
		});
	}

	/** Whether {@code player} has a journal anywhere in their inventory. */
	public static boolean carries(Player player) {
		return player.getInventory().contains(stack -> stack.is(JOURNAL));
	}

	/** Writes up {@code player}'s journal and sends it to them to open. */
	static void open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, Book.TYPE)) {
			return;
		}
		ServerLevel level = player.level();
		var server = level.getServer();
		List<Entry> entries = new ArrayList<>();
		for (Quest q : Quests.log(player)) {
			Faction f = Factions.get(server, q.giver().faction());
			Component giver = f == null ? Component.literal(q.giver().name())
				: Component.translatable("journal.jaymoskingdoms.giver", q.giver().name(), f.name());
			boolean ready = Quests.ready(player, q);
			Optional<BlockPos> target = ready ? f == null ? Optional.empty() : Optional.of(f.seat()) : q.objective().pos();
			entries.add(new Entry(q.royal(), ready, Quests.title(q), giver, Quests.text(q), Quests.objective(level, player, q), target));
		}
		// (0.7.220) your jobs' orders
		entries.addAll(Careers.journal(player));
		// (0.7.431) your sworn foes among the tribes' captains, and the captains you know something of
		entries.addAll(Captains.journal(player));
		// (0.7.31) the weather foretold, first
		Component weather = DisasterRelief.journal(level, player);
		if (weather != null) {
			entries.add(0, new Entry(false, false, Component.translatable("journal.jaymoskingdoms.forecast"),
				Component.translatable("journal.jaymoskingdoms.forecast.by"), weather, Component.translatable("journal.jaymoskingdoms.forecast.objective"),
				Optional.empty()));
		}
		Map<String, Integer> known = player.getAttachedOrElse(Reputation.REPUTATION, Map.of());
		List<Place> places = new ArrayList<>();
		for (Faction f : Factions.all(server).stream()
			.filter(f -> !f.fallen() && (known.containsKey(Integer.toString(f.id())) || f.seat().distToCenterSqr(player.position()) < PLACES_RANGE * PLACES_RANGE))
			.sorted(Comparator.comparingDouble(f -> f.seat().distToCenterSqr(player.position())))
			.limit(MAX_PLACES).toList()) {
			boolean rated = f.type() != FactionType.BARBARIAN;
			int rep = rated ? Reputation.get(player, f.id()) : 0;
			places.add(new Place(f.name(), f.type().ordinal(), f.styleColor(server).getTextureDiffuseColor(), f.seat(), rated, rep, rated ? Reputation.word(rep) : ""));
		}
		ServerPlayNetworking.send(player, new Book(entries, places, level == Factions.level(server)));
	}
}
