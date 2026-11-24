package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (village mods patch 4, ideas from Bountiful: our own code, names and art) The terms a piece of town board work is
 * taken on ({@link TownNotices} errands and {@link BoardWork}), kept per quest on the player who took it:
 * <ul>
 * <li><b>a deadline</b>: some notices give so many days ({@link #dated}). Brought back in the first half of them it pays
 * {@link #EARLY_SHARE} more; past the last, the poster gives up on you, the quest comes off your log and their town thinks
 * a little less of you ({@link #MISSED_REPUTATION}). Your copy of the notice yellows once the early time is past.</li>
 * <li><b>a second ask</b>: some want goods besides ({@link #also}), and pay part in goods.</li>
 * </ul>
 * Taking one gives you your copy of the notice: held, it shows how far along you are; when the work is done, a toast says
 * so. Used on the town's board with the notice or the goods in hand, it is handed in there when its poster is away
 * ({@link #handIn}).
 */
public final class BoardTerms {
	// ---- tuning ----
	/** The chance a notice gives a deadline; the days it gives, at least, and one more for every this many stars. */
	static final float DEADLINE_CHANCE = 0.35F;
	static final int DEADLINE_DAYS = 1, DAYS_PER_STARS = 2;
	/** What finishing in the first half of the time adds to the pay; what missing it costs with the poster's town. */
	static final float EARLY_SHARE = 0.25F;
	static final int MISSED_REPUTATION = 4;
	/** The chance a notice asks for goods besides, and how much of the usual errand's count it asks. */
	static final float MIXED_CHANCE = 0.35F;
	static final float ALSO_SHARE = 0.5F;
	/** What a mixed ask pays in goods: these, the one dearest that's worth at most this share of the coin. */
	static final List<String> GOODS = List.of("minecraft:bread*6", "minecraft:arrow*16", "minecraft:torch*16", "minecraft:iron_ingot*3",
		"minecraft:honey_bottle*2", "minecraft:lantern*2", "minecraft:golden_carrot*4", "minecraft:cooked_beef*6", "minecraft:book*2");
	static final float GOODS_SHARE = 0.5F;
	/** A poster this close to their board is about: the work goes to them, not into the board. */
	static final double AWAY = 24.0;
	/** How often (ticks) the held notice and the deadlines are looked at; how often the papers' looks are. */
	static final int TICKS = 10, LOOK_TICKS = 100;
	/** How long the "work's done" toast shows (ms). */
	public static final long TOAST_MS = 5000L;

	/** Paper looks, by stars: plain, sealed, vellum, gilt ({@link #paper}); a yellowed copy adds this. */
	static final String OLD = "_old";

	/** One quest's terms: when it's due and until when it's early (0: no deadline), the goods it asks besides ({@code id*n}), its paper. */
	record Terms(long due, long early, List<String> also, String paper) {
		static final Codec<Terms> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.optionalFieldOf("due", 0L).forGetter(Terms::due),
				Codec.LONG.optionalFieldOf("early", 0L).forGetter(Terms::early),
				Codec.STRING.listOf().optionalFieldOf("also", List.of()).forGetter(Terms::also),
				Codec.STRING.optionalFieldOf("paper", "plain").forGetter(Terms::paper))
			.apply(i, Terms::new));
	}

	/** Per player: quest id → its terms. */
	static final AttachmentType<Map<String, Terms>> TERMS = AttachmentRegistry.create(JaymosKingdoms.id("board_terms"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Terms.CODEC)).copyOnDeath());

	/** Server → client: a toast, the work on a notice done. */
	public record Toast(Component title, Component text) implements CustomPacketPayload {
		public static final Type<Toast> TYPE = new Type<>(JaymosKingdoms.id("board_toast"));
		static final StreamCodec<RegistryFriendlyByteBuf, Toast> CODEC = StreamCodec.composite(ComponentSerialization.STREAM_CODEC, Toast::title,
			ComponentSerialization.STREAM_CODEC, Toast::text, Toast::new);

		@Override
		public Type<Toast> type() {
			return TYPE;
		}
	}

	/** Per player: the board quests last seen done (a toast when one joins). */
	private static final Map<UUID, Set<String>> READY = new HashMap<>();

	private BoardTerms() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Toast.TYPE, Toast.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(BoardTerms::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}

	// ---- on the notice -----------------------------------------------------------------------------------------------------

	/** {@code key} with a deadline maybe added ({@code |d<days>}), longer for harder work. */
	static String dated(String key, int stars, RandomSource random) {
		return random.nextFloat() < DEADLINE_CHANCE ? key + "|d" + (DEADLINE_DAYS + stars / DAYS_PER_STARS + random.nextInt(2)) : key;
	}

	/** The days a notice's {@code key} gives, or 0. */
	static int days(String key) {
		String[] p = key.split("\\|");
		for (int i = 4; i < p.length; i++) {
			if (p[i].startsWith("d")) {
				try {
					return Math.max(0, Integer.parseInt(p[i].substring(1)));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}

	/** The paper a notice of {@code stars} is on: plain to two stars, sealed at three, vellum at four, gilt at five. */
	static String paper(int stars) {
		return stars >= 5 ? "gilt" : stars == 4 ? "vellum" : stars == 3 ? "sealed" : "plain";
	}

	/** The colour a notice's name is in, on its paper. */
	static ChatFormatting colour(String paper) {
		return switch (paper.replace(OLD, "")) {
			case "sealed" -> ChatFormatting.YELLOW;
			case "vellum" -> ChatFormatting.AQUA;
			case "gilt" -> ChatFormatting.GOLD;
			default -> ChatFormatting.WHITE;
		};
	}

	/** The line a notice with a deadline adds to its words. */
	static Component deadlineLine(int days) {
		return Component.translatable("notice.jaymoskingdoms.deadline", days);
	}

	// ---- taking it on ------------------------------------------------------------------------------------------------------

	/** A second ask for a notice on {@code poster}'s board, or null: some of one of their town's goods other than {@code not}. */
	static @Nullable String also(List<String> pool, String not, RandomSource random) {
		List<String> free = pool.stream().filter(s -> !s.substring(0, s.lastIndexOf('*')).equals(not)).toList();
		if (free.isEmpty() || random.nextFloat() >= MIXED_CHANCE) {
			return null;
		}
		return free.get(random.nextInt(free.size()));
	}

	/** {@code spec} ({@code id*n}): the id, and n. */
	private static String id(String spec) {
		int star = spec.lastIndexOf('*');
		return star < 0 ? spec : spec.substring(0, star);
	}

	private static int n(String spec) {
		int star = spec.lastIndexOf('*');
		try {
			return star < 0 ? 1 : Integer.parseInt(spec.substring(star + 1));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
	}

	/** What the second ask {@code spec} is worth (milli). */
	static long alsoWorth(MinecraftServer server, @Nullable String spec) {
		return spec == null ? 0L : Economy.worth(server, id(spec)) * n(spec);
	}

	/** Goods to pay part of {@code milli} in, for a mixed ask (and so a little less coin): the dearest worth no more than {@link #GOODS_SHARE} of it; or null. */
	static @Nullable String goods(MinecraftServer server, long milli) {
		String best = null;
		long bestWorth = 0;
		for (String spec : GOODS) {
			long w = Economy.worth(server, id(spec)) * n(spec);
			if (w <= milli * GOODS_SHARE && w > bestWorth) {
				best = spec;
				bestWorth = w;
			}
		}
		return best;
	}

	/**
	 * {@code player} has taken {@code q} off a board, from the notice {@code key} on {@code paper}, asking {@code also}
	 * besides (or null): its terms are kept, and they get their copy of the notice.
	 */
	static void taken(ServerLevel level, ServerPlayer player, Quest q, String key, String paper, @Nullable String also) {
		int days = days(key);
		long now = level.getGameTime();
		Terms t = new Terms(days > 0 ? now + days * 24000L : 0L, days > 0 ? now + days * 12000L : 0L, also == null ? List.of() : List.of(also), paper);
		Map<String, Terms> all = new HashMap<>(player.getAttachedOrElse(TERMS, Map.of()));
		all.put(q.id(), t);
		player.setAttached(TERMS, Map.copyOf(all));
		ItemStack copy = copy(q, t, days, now);
		if (!player.getInventory().add(copy)) {
			player.drop(copy, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	static @Nullable Terms of(ServerPlayer player, String id) {
		return player.getAttachedOrElse(TERMS, Map.of()).get(id);
	}

	/** Quest {@code id} is off {@code player}'s log: its terms go, and so does their copy of the notice. */
	static void forget(ServerPlayer player, String id) {
		Map<String, Terms> all = player.getAttachedOrElse(TERMS, Map.of());
		if (all.containsKey(id)) {
			Map<String, Terms> m = new HashMap<>(all);
			m.remove(id);
			player.setAttached(TERMS, Map.copyOf(m));
		}
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(TownNotices.ITEM) && id.equals(Quests.questOf(s))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		Set<String> ready = READY.get(player.getUUID());
		if (ready != null) {
			ready.remove(id);
		}
	}

	/** The player's copy of a notice they've taken: the quest's title on its paper, its terms, and the quest it's for. */
	private static ItemStack copy(Quest q, Terms t, int days, long now) {
		ItemStack stack = new ItemStack(TownNotices.ITEM);
		String look = t.paper() + (t.early() > 0 && now > t.early() ? OLD : "");
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.town_notice.of", Quests.title(q))
			.withStyle(style -> style.withItalic(false).withColor(colour(t.paper()))));
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("notice.jaymoskingdoms.copy"));
		if (days > 0) {
			lines.add(deadlineLine(days));
		}
		stack.set(DataComponents.LORE, new ItemLore(lines.stream()
			.map(line -> (Component)line.copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))).toList()));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(look), List.of()));
		CompoundTag tag = new CompoundTag();
		tag.putString(Quests.KEY, q.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	// ---- the second ask ---------------------------------------------------------------------------------------------------

	private static int count(ServerPlayer player, Item item) {
		if (item == Items.AIR) {
			return 0;
		}
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item) && Quests.questOf(s) == null) {
				n += s.getCount();
			}
		}
		return n;
	}

	/** {@code player} carries everything {@code q} asks besides (true if it asks nothing besides). */
	static boolean alsoCarried(ServerPlayer player, Quest q) {
		Terms t = of(player, q.id());
		if (t == null) {
			return true;
		}
		for (String spec : t.also()) {
			if (count(player, item(id(spec))) < n(spec)) {
				return false;
			}
		}
		return true;
	}

	/** What {@code q} asks besides is taken from {@code player}'s pack. */
	static void takeAlso(ServerPlayer player, Quest q) {
		Terms t = of(player, q.id());
		if (t == null) {
			return;
		}
		for (String spec : t.also()) {
			Item item = item(id(spec));
			int left = n(spec);
			Inventory inv = player.getInventory();
			for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(item) && Quests.questOf(s) == null) {
					int m = Math.min(left, s.getCount());
					s.shrink(m);
					left -= m;
				}
			}
		}
	}

	/** {@code objective} with what {@code q} asks besides, and how much of it {@code player} has. */
	static Component withAlso(ServerPlayer player, Quest q, Component objective) {
		Terms t = of(player, q.id());
		if (t == null || t.also().isEmpty()) {
			return objective;
		}
		MutableComponent out = objective.copy();
		for (String spec : t.also()) {
			Item item = item(id(spec));
			out.append(Component.translatable("notice.jaymoskingdoms.also", new ItemStack(item).getHoverName(), Math.min(count(player, item), n(spec)), n(spec)));
		}
		return out;
	}

	/** Whether {@code item} is something {@code q} asks to be brought (its goods, or its second ask). */
	private static boolean asks(ServerPlayer player, Quest q, Item item) {
		String id = BuiltInRegistries.ITEM.getKey(item).toString();
		if (q.kind() == Quest.GATHER && Quests.itemId(q.objective().target()).equals(id)) {
			return true;
		}
		Terms t = of(player, q.id());
		return t != null && t.also().stream().anyMatch(s -> id(s).equals(id));
	}

	// ---- brought back ------------------------------------------------------------------------------------------------------

	/** What {@code q} brought back now adds to {@code pay} for being early (0 if it has no deadline, or it's late), said to the player. */
	static long earlyBonus(ServerLevel level, ServerPlayer player, Quest q, long pay) {
		Terms t = of(player, q.id());
		if (t == null || t.early() <= 0 || level.getGameTime() > t.early()) {
			return 0L;
		}
		player.sendSystemMessage(Component.translatable("notice.jaymoskingdoms.early").withStyle(ChatFormatting.GOLD));
		return Math.round(pay * EARLY_SHARE);
	}

	/** Board work: a notice's errand, or work rated in stars. */
	static boolean board(Quest q) {
		return q.template().startsWith("town.") || BoardWork.kind(q.template()) != null;
	}

	/**
	 * {@code player} uses the board at {@code anchor} with {@code held}: their copy of a notice, or goods some work of
	 * theirs asks. Done work of the board's own town is handed in there, if its poster is away. False if it's nothing
	 * to do with the board's work (the board goes on as it would).
	 */
	static boolean handIn(ServerLevel level, ServerPlayer player, BlockPos anchor, ItemStack held) {
		MinecraftServer server = level.getServer();
		String paperFor = held.is(TownNotices.ITEM) ? Quests.questOf(held) : null;
		Faction town = Factions.around(server, level, anchor, Villages.VILLAGE_RADIUS);
		Quest q = null;
		if (paperFor != null) {
			q = Quests.quest(player, paperFor);
			if (q == null) {
				return false;
			}
		} else if (!held.isEmpty() && town != null) {
			for (Quest c : Quests.log(player)) {
				if (board(c) && c.giver().faction() == town.id() && asks(player, c, held.getItem()) && Quests.ready(player, c)) {
					q = c;
					break;
				}
			}
			if (q == null) {
				return false;
			}
		} else {
			return false;
		}
		if (town == null || town.id() != q.giver().faction()) {
			player.sendSystemMessage(Component.translatable("notice.jaymoskingdoms.not_here").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!Quests.ready(player, q) || QuestJobs.undone(level, player, q)) {
			player.sendOverlayMessage(Quests.objective(level, player, q));
			return true;
		}
		if (q.kind() == Quest.CAPTURE) {
			// (a criminal is handed over to whoever posted it, not left tied to a post)
			player.sendSystemMessage(Component.translatable("notice.jaymoskingdoms.capture", q.giver().name()).withStyle(ChatFormatting.GRAY));
			return true;
		}
		Entity poster = Factions.level(server).getEntity(q.giver().uuid());
		if (poster instanceof LivingEntity g && g.isAlive() && g.level() == level && g.distanceToSqr(Vec3.atCenterOf(anchor)) < AWAY * AWAY) {
			player.sendSystemMessage(Component.translatable("notice.jaymoskingdoms.about", q.giver().name()).withStyle(ChatFormatting.GRAY));
			return true;
		}
		Quests.turnInAtBoard(level, player, town, q);
		level.playSound(null, anchor, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 0.8F);
		return true;
	}

	// ---- each tick ---------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICKS != 0) {
			return;
		}
		boolean look = server.getTickCount() % LOOK_TICKS == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			Map<String, Terms> all = player.getAttachedOrElse(TERMS, Map.of());
			List<Quest> log = Quests.log(player);
			// (terms of quests that are gone: forgotten)
			for (String id : List.copyOf(all.keySet())) {
				if (log.stream().noneMatch(q -> q.id().equals(id))) {
					forget(player, id);
				}
			}
			// (past due: the poster gives up on you)
			for (Quest q : log) {
				Terms t = all.get(q.id());
				if (t != null && t.due() > 0 && now > t.due()) {
					missed(level, player, q);
				}
			}
			// (done: a toast; the first look at a player only notes what's done already)
			Set<String> was = READY.get(player.getUUID());
			Set<String> ready = new HashSet<>();
			for (Quest q : Quests.log(player)) {
				if (board(q) && Quests.ready(player, q)) {
					ready.add(q.id());
					if (was != null && !was.contains(q.id()) && ServerPlayNetworking.canSend(player, Toast.TYPE)) {
						ServerPlayNetworking.send(player, new Toast(Component.translatable("notice.jaymoskingdoms.toast"), Quests.title(q)));
					}
				}
			}
			READY.put(player.getUUID(), ready);
			// (held, the copy shows how far along it is)
			for (ItemStack held : List.of(player.getMainHandItem(), player.getOffhandItem())) {
				String id = held.is(TownNotices.ITEM) ? Quests.questOf(held) : null;
				Quest q = id == null ? null : Quests.quest(player, id);
				if (q != null) {
					player.sendOverlayMessage(progress(level, player, q, now));
					break;
				}
			}
			if (look) {
				yellow(player, now);
			}
		}
	}

	/** The held copy's line: the objective, and the time left if it has a deadline. */
	private static Component progress(ServerLevel level, ServerPlayer player, Quest q, long now) {
		MutableComponent line = Quests.objective(level, player, q).copy();
		Terms t = of(player, q.id());
		if (t != null && t.due() > 0) {
			long left = Math.max(0L, t.due() - now);
			line.append(Component.translatable(now <= t.early() ? "notice.jaymoskingdoms.left.early" : "notice.jaymoskingdoms.left",
				left / 24000L, left % 24000L / 1000L));
		}
		return line;
	}

	/** Past the early time, a player's copies of their notices have yellowed. */
	private static void yellow(ServerPlayer player, long now) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			String id = s.is(TownNotices.ITEM) ? Quests.questOf(s) : null;
			Terms t = id == null ? null : of(player, id);
			if (t == null || t.early() <= 0 || now <= t.early()) {
				continue;
			}
			CustomModelData data = s.get(DataComponents.CUSTOM_MODEL_DATA);
			if (data == null || data.strings().isEmpty() || !data.strings().get(0).endsWith(OLD)) {
				s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(t.paper() + OLD), List.of()));
			}
		}
	}

	/** {@code q}'s time is up: off the log, the board's work it set out cleared, and its town thinks less of the one who let it go. */
	private static void missed(ServerLevel level, ServerPlayer player, Quest q) {
		Faction f = Factions.get(level.getServer(), q.giver().faction());
		Quests.drop(player, q.id());
		if (q.kind() >= Quest.PACK) {
			QuestJobs.over(level, player, q);
		}
		if (f != null) {
			Reputation.change(player, f, -MISSED_REPUTATION);
		}
		player.sendSystemMessage(Component.translatable("notice.jaymoskingdoms.missed", Quests.title(q), MISSED_REPUTATION, f == null ? "" : f.name())
			.withStyle(ChatFormatting.RED));
		Quests.sync(player, true);
	}
}
