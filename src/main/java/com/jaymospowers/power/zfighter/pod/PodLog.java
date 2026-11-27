package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.zfighter.ZFighterAtlas;
import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.power.zfighter.sky.Charts;
import com.jaymospowers.power.zfighter.sky.PodScope;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * The pilot's logbook: a written book that fills in as you go. The old guidance only ever named the next step (build the keel,
 * fuel it, find the Moon, land, come back); the logbook keeps what has been done, each step in the pilot's own words, the
 * worlds read and landed on, and ends with one line for what comes next. What has been done is {@link #LOG}, a bit for each
 * {@link Step}, noted from the pod's code ({@link #note}); the star charts read and the atlas' landings are read as they are.
 * The book is a plain item ({@link #BOOK}): use it and the server sends its pages ({@link Show}) to open on the client.
 */
public final class PodLog {
	// ---- tuning ----
	/** Lines of a page before the next page begins. */
	static final int PAGE_LINES = 4;

	/** The steps of a pilot's story, in the order the chain asks them; {@code chain} ones are what "next" is chosen from. */
	public enum Step {
		KEEL(true), BUILT(true), FUELED(true), CHARTED(true), LAUNCHED(true), LANDED(true), RETURNED(true),
		FITTED(false), SCOPED(false), DUST(false), HINT(false), BEACON(false), SCANNED(false), CRASHED(false);

		public final boolean chain;

		Step(boolean chain) {
			this.chain = chain;
		}

		int bit() {
			return 1 << ordinal();
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** On the player: a bit for each {@link Step} done. Kept through death. */
	public static final AttachmentType<Integer> LOG = AttachmentRegistry.create(JaymosPowers.id("zfighter_pod_log"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	public static Item BOOK;
	private static boolean ready;

	/** Server → client: open these pages as a book. */
	public record Show(List<Component> pages) implements CustomPacketPayload {
		public static final Type<Show> TYPE = new Type<>(JaymosPowers.id("zfighter_pod_log"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.composite(
			ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Show::pages, Show::new);

		@Override
		public Type<Show> type() {
			return TYPE;
		}
	}

	private PodLog() {
	}

	/** Registers the book, its packet and its creative entry. Called once, from {@link PodTravel#init}. */
	static void init() {
		if (ready) {
			return;
		}
		ready = true;
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("pilots_logbook"));
		BOOK = Registry.register(BuiltInRegistries.ITEM, key, new Book(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		PayloadTypeRegistry.clientboundPlay().register(Show.TYPE, Show.CODEC);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(BOOK));
	}

	/** The logbook: use it to read. */
	private static final class Book extends Item {
		Book(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
			if (player instanceof ServerPlayer server) {
				ServerPlayNetworking.send(server, new Show(pages(server)));
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- what is done

	/** Writes {@code step} into {@code player}'s logbook (once). */
	public static void note(ServerPlayer player, Step step) {
		int log = player.getAttachedOrElse(LOG, 0);
		if ((log & step.bit()) == 0) {
			player.setAttached(LOG, log | step.bit());
		}
	}

	/** Whether {@code step} is in the book: noted, or (for the chart step) read off the star charts. */
	public static boolean done(ServerPlayer player, Step step) {
		if (step == Step.CHARTED && Charts.readMask(player) != 0) {
			return true;
		}
		return (player.getAttachedOrElse(LOG, 0) & step.bit()) != 0;
	}

	/** The first step of the chain still to do, or null when every one is. */
	public static Step next(ServerPlayer player) {
		for (Step step : Step.values()) {
			if (step.chain && !done(player, step)) {
				return step;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- the pages

	/** The book as it stands: each step done as an entry, the worlds read, and one line for what is next. */
	static List<Component> pages(ServerPlayer player) {
		List<MutableComponent> lines = new ArrayList<>();
		for (Step step : Step.values()) {
			if (done(player, step)) {
				lines.add(Component.translatable("book.jaymospowers.pod_log." + step.key()));
			}
		}
		for (int world = 0; world < Charts.WORLDS; world++) {
			if (!Charts.read(player, world)) {
				continue;
			}
			ZFighterAtlas.Pin pin = ZFighterAtlas.of(player, world);
			Component name = Component.translatable("space.jaymospowers.dest." + ZFighterSpace.DESTS[world].key());
			lines.add(pin == null ? Component.translatable("book.jaymospowers.pod_log.world_read", name)
				: Component.translatable("book.jaymospowers.pod_log.world_landed", name, pin.landings()));
		}
		for (int world : PodStardust.pending(player)) {
			lines.add(Component.translatable("book.jaymospowers.pod_log.hint", PodScope.hint(world)));
		}
		Step next = next(player);
		lines.add(next == null ? Component.translatable("book.jaymospowers.pod_log.next_none")
			: Component.translatable("book.jaymospowers.pod_log.next." + next.key()).withStyle(ChatFormatting.DARK_BLUE));
		List<Component> pages = new ArrayList<>();
		MutableComponent page = Component.translatable("book.jaymospowers.pod_log.title").withStyle(ChatFormatting.BOLD).append("\n\n");
		int onPage = 0;
		for (MutableComponent line : lines) {
			if (onPage == PAGE_LINES) {
				pages.add(page);
				page = Component.empty();
				onPage = 0;
			}
			page.append(line).append("\n\n");
			onPage++;
		}
		pages.add(page);
		return pages;
	}
}
