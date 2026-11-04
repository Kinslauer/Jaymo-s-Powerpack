package com.jaymoskingdoms;

import com.jaymoskingdoms.journal.RungJournal;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes, Orc war journals: the <b>Lore Codex</b>, a cloth-bound book that keeps every lore book you have found, one
 * of each: war journals ({@link WarJournal}), data books ({@link LoreBooks}), the school manuals and the captains' orders, and,
 * without taking them from you, the Rung Journal and the Bestiary (which it always shows as they stand now). Using it files
 * every such book in your pack into it (a book it already holds stays in your pack) and opens its reading screen.
 * <p>
 * What it holds lives on the player ({@link #SHELF}, saved, kept through death), so the codex is soul-bound: it never drops
 * where you fall (it is taken out of the pack as you die, unless keepInventory holds) and comes back with you on respawn. A
 * player is given one the first time a journal drops for them ({@link #offer}); another is easily made.
 */
public final class LoreCodex extends Item {
	// ---- tuning ----
	/** How many books it holds at most. */
	static final int MAX_ENTRIES = 300;
	/** The ids of the books it shows live, rebuilt for the reader each time. */
	static final String LIVE_RUNG = "live:rung_journal", LIVE_BESTIARY = "live:bestiary";

	/** A filed book: its id, its title and its pages (none for a live one, whose pages are written when the codex opens). */
	public record Entry(String id, Component title, List<Component> pages) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Entry::id),
			ComponentSerialization.CODEC.fieldOf("title").forGetter(Entry::title),
			ComponentSerialization.CODEC.listOf().fieldOf("pages").forGetter(Entry::pages)).apply(i, Entry::new));

		boolean live() {
			return this.id.startsWith("live:");
		}
	}

	/** Server → player: open the codex with these books. */
	public record Open(List<Entry> entries) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosKingdoms.id("lore_codex_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(Open::write, Open::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(this.entries.size());
			for (Entry e : this.entries) {
				buf.writeUtf(e.id());
				ComponentSerialization.STREAM_CODEC.encode(buf, e.title());
				buf.writeVarInt(e.pages().size());
				for (Component page : e.pages()) {
					ComponentSerialization.STREAM_CODEC.encode(buf, page);
				}
			}
		}

		private static Open read(RegistryFriendlyByteBuf buf) {
			int n = buf.readVarInt();
			List<Entry> entries = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				String id = buf.readUtf();
				Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
				int pageCount = buf.readVarInt();
				List<Component> pages = new ArrayList<>();
				for (int p = 0; p < pageCount; p++) {
					pages.add(ComponentSerialization.STREAM_CODEC.decode(buf));
				}
				entries.add(new Entry(id, title, pages));
			}
			return new Open(entries);
		}

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	public static Item ITEM;
	/** On a player: the books filed, in the order they were. Saved, kept through death. */
	static final AttachmentType<List<Entry>> SHELF = AttachmentRegistry.create(JaymosKingdoms.id("lore_codex_shelf"),
		builder -> builder.persistent(Entry.CODEC.listOf()).copyOnDeath());
	/** On a player: whether they have been given a codex (so a lost one is not replaced for free), and whether theirs was taken as they died. Saved, kept through death. */
	static final AttachmentType<Boolean> GIVEN = AttachmentRegistry.create(JaymosKingdoms.id("lore_codex_given"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	static final AttachmentType<Boolean> DUE = AttachmentRegistry.create(JaymosKingdoms.id("lore_codex_due"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** Players whose codex was taken at the moment of death and who are looked at next tick: one that is still alive gets it back. */
	private static final Set<UUID> CHECK = new HashSet<>();

	private LoreCodex(Properties properties) {
		super(properties);
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("lore codex attachments: {} {} {}", SHELF, GIVEN, DUE);
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("lore_codex"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new LoreCodex(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, ITEM));
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		// soul-bound: out of the pack as its owner dies (so it is not dropped), back on respawn
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && !player.level().getGameRules().get(GameRules.KEEP_INVENTORY) && take(player)) {
				player.setAttached(DUE, true);
				CHECK.add(player.getUUID());
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!CHECK.isEmpty()) {
				for (UUID id : List.copyOf(CHECK)) {
					ServerPlayer player = server.getPlayerList().getPlayer(id);
					if (player != null && player.isAlive()) {
						// (the death did not come: it is theirs again)
						restore(player);
					}
				}
				CHECK.clear();
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> restore(newPlayer));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restore(handler.player));
	}

	/** Takes every codex out of {@code player}'s pack; whether there was one. */
	private static boolean take(ServerPlayer player) {
		boolean any = false;
		Inventory pack = player.getInventory();
		for (int i = 0; i < pack.getContainerSize(); i++) {
			if (pack.getItem(i).is(ITEM)) {
				pack.setItem(i, ItemStack.EMPTY);
				any = true;
			}
		}
		return any;
	}

	/** Gives {@code player} their codex back if it was taken as they died. */
	private static void restore(ServerPlayer player) {
		// (0.7.490) not to a dead one (rejoined on the death screen): their pack is not carried over, so DUE waits for the respawn
		if (ITEM != null && player.isAlive() && Boolean.TRUE.equals(player.getAttached(DUE))) {
			player.removeAttached(DUE);
			if (!player.getInventory().contains(stack -> stack.is(ITEM))) {
				player.getInventory().placeItemBackInInventory(new ItemStack(ITEM), net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
	}

	/** (A journal dropped for {@code player}) the first time, a codex comes with it, as if the writing had told them where to keep it. */
	static void offer(ServerPlayer player) {
		if (ITEM == null || Boolean.TRUE.equals(player.getAttached(GIVEN))) {
			return;
		}
		player.setAttached(GIVEN, true);
		if (!player.getInventory().contains(stack -> stack.is(ITEM))) {
			player.getInventory().placeItemBackInInventory(new ItemStack(ITEM), net.minecraft.util.Prediction.SERVER_ONLY);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.codex.given").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			file(sp);
			open(sp);
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.1F);
		}
		return InteractionResult.SUCCESS;
	}

	// ---- filing --------------------------------------------------------------------------------------------------------------

	/** The id of a book the codex shows live (the Rung Journal, the Bestiary), or empty. */
	private static String liveId(ItemStack stack) {
		return stack.is(RungJournal.ITEM) ? LIVE_RUNG : Bestiary.ITEM != null && stack.is(Bestiary.ITEM) ? LIVE_BESTIARY : "";
	}

	/** Files every lore book in {@code player}'s pack that the codex does not hold yet, and tells them how it went. */
	static void file(ServerPlayer player) {
		List<Entry> shelf = new ArrayList<>(player.getAttachedOrElse(SHELF, List.of()));
		Set<String> held = new HashSet<>();
		for (Entry e : shelf) {
			held.add(e.id());
		}
		int filed = 0, doubles = 0;
		boolean full = false;
		Inventory pack = player.getInventory();
		for (int i = 0; i < pack.getContainerSize(); i++) {
			ItemStack stack = pack.getItem(i);
			String live = liveId(stack);
			boolean book = stack.is(Items.WRITTEN_BOOK) || stack.is(WarJournal.ITEM);
			String id = !live.isEmpty() ? live : book ? LoreBooks.idOf(stack) : "";
			if (id.isEmpty()) {
				continue;
			}
			if (held.contains(id)) {
				if (live.isEmpty()) {
					doubles++;
				}
				continue;
			}
			if (shelf.size() >= MAX_ENTRIES) {
				full = true;
				continue;
			}
			if (!live.isEmpty()) {
				shelf.add(new Entry(id, Component.translatable(id.equals(LIVE_RUNG) ? "item.jaymoskingdoms.rung_journal" : "item.jaymoskingdoms.bestiary"), List.of()));
			} else {
				WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
				List<Component> pages = new ArrayList<>();
				if (content != null) {
					for (Filterable<Component> page : content.pages()) {
						pages.add(page.raw());
					}
				}
				Component title = stack.get(DataComponents.CUSTOM_NAME);
				// (0.7.490) a captain's orders filed unread still teach what reading them would
				Captains.readOrders(player, stack);
				// (0.7.490) one copy filed: the rest of a stack stays in the pack, as a book already held does
				pack.setItem(i, stack.copyWithCount(stack.getCount() - 1));
				shelf.add(new Entry(id, title != null ? title : Component.literal(content == null ? "?" : content.title().raw()), pages));
			}
			held.add(id);
			filed++;
		}
		if (filed > 0) {
			player.setAttached(SHELF, List.copyOf(shelf));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.codex.filed", filed).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		} else if (full) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.codex.full").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		} else if (doubles > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.codex.double", doubles).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** Sends {@code player} their codex to read, the live books written up as they stand now. */
	static void open(ServerPlayer player) {
		if (!ServerPlayNetworking.canSend(player, Open.TYPE)) {
			return;
		}
		List<Entry> out = new ArrayList<>();
		for (Entry e : player.getAttachedOrElse(SHELF, List.of())) {
			if (e.live()) {
				List<Component> pages = new ArrayList<>();
				for (Filterable<Component> page : e.id().equals(LIVE_RUNG) ? RungJournal.pages(player) : Bestiary.pages(player)) {
					pages.add(page.raw());
				}
				out.add(new Entry(e.id(), e.title(), pages));
			} else {
				out.add(e);
			}
		}
		ServerPlayNetworking.send(player, new Open(out));
	}

	/** Whether {@code player} has filed the book {@code id}. */
	public static boolean holds(ServerPlayer player, @Nullable String id) {
		for (Entry e : player.getAttachedOrElse(SHELF, List.of())) {
			if (e.id().equals(id)) {
				return true;
			}
		}
		return false;
	}
}
