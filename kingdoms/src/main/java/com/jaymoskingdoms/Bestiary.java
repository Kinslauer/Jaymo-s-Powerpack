package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.425) Havens patch 1: the <b>bestiary</b>, a greasy book the pedlar sells ({@link Pedlars}). One page for each kind of
 * goblin and orc, filled in by what you've done: a page is a smudge until you've met that kind; killing one writes a hint
 * of how they fight, and one of them talking under questioning ({@link Questioning}) writes a hint of their ways. Hints
 * only, never plain lore (Andy's rule), each line {@code bestiary.jaymoskingdoms.<kind>.<n>}.
 * <p>Hunter's Road: after those, a page for each member of every monster kind ({@link MonsterKinds}), by what you've done:
 * the kind's members you have not met are one smudged page for the kind; once met (it hurt you, or you hurt it) the member
 * has a page of its own with a hint; a kill adds a line of how it fights ({@link #SLAIN}); a line of what it fears ({@link #FEARED})
 * comes when someone tells you ({@link #told}, {@link #toldOfKind}) or when you beat it with the right oil on
 * ({@link #learnFear}). Lines are {@code bestiary.jaymoskingdoms.kind.<kind>.<n>}, hints only. At most {@link #MEMBER_PAGES_MOST}
 * member pages are written, so the book stays under the page limit.
 */
public final class Bestiary extends Item {
	/** What you've learned of a kind (bits): one slain, one talked. */
	public static final int SLAIN = 1, TOLD = 2;
	/** What you've learned of a monster kind's member (bits): met it (SLAIN and TOLD are as above; {@link #FEARED} is {@link #TOLD}). */
	public static final int MET = 4, FEARED = TOLD;
	/** The most pages written for monster kinds' members that you have met. */
	static final int MEMBER_PAGES_MOST = 60;
	/** The notes key of a member of a monster kind: its entity type's id (the vampires, who are people and not a type, are {@link #VAMPIRE}). */
	static final String MEMBER = "m:", VAMPIRE = "jaymoskingdoms:vampire";
	static final String TITLE = "Greasy Bestiary";

	/** The kinds, in page order, and the role each is for. */
	private static final String[] KINDS = {"goblin", "slinger", "goblin_king", "grunt", "ranger", "shaman", "warchief"};
	private static final int[] ROLES = {Goblins.GOBLIN, Goblins.SLINGER, Goblins.KING, Orcs.GRUNT, Orcs.RANGER, Orcs.SHAMAN, Orcs.WARCHIEF};

	public static Item ITEM;
	/** On a player: what they've learned, by kind. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> NOTES = AttachmentRegistry.create(JaymosKingdoms.id("bestiary_notes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	/** On a player: what their tales have taught them of a beast, by its name ({@link #learned}). Saved, kept through death. */
	static final AttachmentType<Map<String, String>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("bestiary_learned"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath());
	/** At most this many learned pages are kept. */
	static final int LEARNED_MOST = 24;
	/** Between two things learned of one kind, as kept. */
	static final String NOTE_GAP = "\n\n";

	private Bestiary(Properties properties) {
		super(properties);
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof Player)) {
				meet(player, entity, MET);
			} else if (entity instanceof ServerPlayer player && source.getEntity() != null && !(source.getEntity() instanceof Player)) {
				meet(player, source.getEntity(), MET);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && !(entity instanceof Player)) {
				meet(player, entity, MET | SLAIN);
			}
		});
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bestiary"));
		WrittenBookContent blank = new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, List.of(), true);
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Bestiary(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.WRITTEN_BOOK_CONTENT, blank)
			.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true))));
	}

	/** {@code player} learned {@code how} ({@link #SLAIN}, {@link #TOLD}) of the kind that has {@code role} (none for others). */
	public static void note(ServerPlayer player, int role, int how) {
		for (int i = 0; i < ROLES.length; i++) {
			if (ROLES[i] == role) {
				Map<String, Integer> notes = player.getAttachedOrElse(NOTES, Map.of());
				int was = notes.getOrDefault(KINDS[i], 0);
				write(player, KINDS[i], was, how);
				return;
			}
		}
	}

	/** Adds {@code how} to what {@code player} has of {@code key}, and says so quietly if they carry the book. */
	private static void write(ServerPlayer player, String key, int was, int how) {
		if ((was & how) == how) {
			return;
		}
		Map<String, Integer> now = new HashMap<>(player.getAttachedOrElse(NOTES, Map.of()));
		now.put(key, was | how);
		player.setAttached(NOTES, Map.copyOf(now));
		if (ITEM != null && player.getInventory().contains(new ItemStack(ITEM))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bestiary.noted").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	/** The notes key of what {@code e} is a member of among the monster kinds, or null when it is none (and no player). */
	private static @Nullable String memberOf(Entity e) {
		EntityType<?> type = e.getType();
		if (type != net.minecraft.world.entity.EntityTypes.PLAYER && !MonsterKinds.of(type).isEmpty()) {
			return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
		}
		return Vampires.isVampire(e) && !VampirePlayers.dhampir(e) ? VAMPIRE : null;
	}

	/** {@code player} did {@code how} ({@link #MET}, {@link #SLAIN}) with {@code e}, if it is one of a monster kind's members. */
	private static void meet(ServerPlayer player, Entity e, int how) {
		String id = memberOf(e);
		if (id != null) {
			write(player, MEMBER + id, player.getAttachedOrElse(NOTES, Map.of()).getOrDefault(MEMBER + id, 0), how);
		}
	}

	/**
	 * Something taught {@code player} what {@code type} fears (an herbalist, a hunter or an elder in talk, a study, or, called
	 * by the oils, beating one with the right oil on): its page shows the line from now on. A kind not yet met is met by it.
	 */
	public static void learnFear(ServerPlayer player, EntityType<?> type) {
		learnFear(player, BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
	}

	/** {@link #learnFear(ServerPlayer, EntityType)} for the type with this id ({@link #VAMPIRE} for vampires). */
	public static void learnFear(ServerPlayer player, String id) {
		write(player, MEMBER + id, player.getAttachedOrElse(NOTES, Map.of()).getOrDefault(MEMBER + id, 0), MET | FEARED);
	}

	/** The talk line hook: someone in talk tells {@code player} what {@code type} fears (the same as {@link #learnFear(ServerPlayer, EntityType)}). */
	public static void told(ServerPlayer player, EntityType<?> type) {
		learnFear(player, type);
	}

	/**
	 * The talk line hook for a whole kind: someone in talk tells {@code player} what one member of {@code kind} that they
	 * have met but not yet been told of fears. Returns whether there was one to tell (false: they know them all, or have met none).
	 */
	public static boolean toldOfKind(ServerPlayer player, String kind) {
		Map<String, Integer> notes = player.getAttachedOrElse(NOTES, Map.of());
		for (String id : members(kind)) {
			int known = notes.getOrDefault(MEMBER + id, 0);
			if ((known & MET) != 0 && (known & FEARED) == 0) {
				learnFear(player, id);
				return true;
			}
		}
		return false;
	}

	/** Like {@link #toldOfKind} over every kind. */
	public static boolean toldOfAny(ServerPlayer player) {
		for (String kind : MonsterKinds.ALL) {
			if (toldOfKind(player, kind)) {
				return true;
			}
		}
		return false;
	}

	/** The ids of the members of {@code kind}, in id order: its tag's entity types (not players) and, for the cursed, the vampires. */
	static List<String> members(String kind) {
		List<String> ids = new ArrayList<>();
		BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(MonsterKinds.tag(kind)).forEach(h -> {
			if (h.value() != net.minecraft.world.entity.EntityTypes.PLAYER) {
				ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(h.value()).toString());
			}
		});
		if (MonsterKinds.CURSED.equals(kind)) {
			ids.add(VAMPIRE);
		}
		java.util.Collections.sort(ids);
		return ids;
	}

	/**
	 * ({@link TaleWays#learnt}, {@link TaleWays#prepared}) A tale taught {@code player} this of the kind of beast called
	 * {@code name}: it has pages of its own from now on, a note to a page. Written in the book quietly: it's found there.
	 */
	static void learned(ServerPlayer player, String name, String note) {
		Map<String, String> was = player.getAttachedOrElse(LEARNED, Map.of());
		String had = was.getOrDefault(name, "");
		if (had.contains(note) || !was.containsKey(name) && was.size() >= LEARNED_MOST) {
			return;
		}
		Map<String, String> now = new HashMap<>(was);
		now.put(name, had.isEmpty() ? note : had + NOTE_GAP + note);
		player.setAttached(LEARNED, Map.copyOf(now));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ItemStack held = sp.getItemInHand(hand);
			held.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, pages(sp), true));
			sp.containerMenu.broadcastChanges();
			sp.openItemGui(held, hand);
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	/** A first page, then one for each kind as far as {@code player} has learned it. */
	static List<Filterable<Component>> pages(ServerPlayer player) {
		Map<String, Integer> notes = player.getAttachedOrElse(NOTES, Map.of());
		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(Filterable.passThrough(Component.translatable("bestiary.jaymoskingdoms.first").withStyle(ChatFormatting.BLACK)));
		for (String kind : KINDS) {
			int known = notes.getOrDefault(kind, 0);
			MutableComponent page = Component.empty();
			if (known == 0) {
				page.append(Component.translatable("bestiary.jaymoskingdoms.unknown").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			} else {
				page.append(Component.translatable("bestiary.jaymoskingdoms." + kind).withStyle(ChatFormatting.BOLD)).append("\n\n");
				page.append(Component.translatable("bestiary.jaymoskingdoms." + kind + ".0").withStyle(ChatFormatting.BLACK));
				if ((known & SLAIN) != 0) {
					page.append("\n\n").append(Component.translatable("bestiary.jaymoskingdoms." + kind + ".1").withStyle(ChatFormatting.DARK_RED));
				}
				if ((known & TOLD) != 0) {
					page.append("\n\n").append(Component.translatable("bestiary.jaymoskingdoms." + kind + ".2").withStyle(ChatFormatting.DARK_BLUE));
				}
			}
			pages.add(Filterable.passThrough(page));
		}
		pages(notes, pages);
		// (what tales have taught of other beasts: a page for each thing learned, under the kind's name)
		for (Map.Entry<String, String> e : new java.util.TreeMap<>(player.getAttachedOrElse(LEARNED, Map.of())).entrySet()) {
			for (String note : e.getValue().split(NOTE_GAP)) {
				pages.add(Filterable.passThrough(Component.empty().append(Component.literal(e.getKey()).withStyle(ChatFormatting.BOLD)).append("\n\n")
					.append(Component.literal(note).withStyle(ChatFormatting.DARK_RED))));
			}
		}
		return pages;
	}

	/** The pages of the monster kinds: a smudge for the kind's members not met, then a page for each met, as far as {@link #MEMBER_PAGES_MOST}. */
	private static void pages(Map<String, Integer> notes, List<Filterable<Component>> pages) {
		int written = 0;
		for (String kind : MonsterKinds.ALL) {
			boolean smudge = false;
			for (String id : members(kind)) {
				int known = notes.getOrDefault(MEMBER + id, 0);
				if ((known & MET) == 0) {
					smudge = true;
					continue;
				}
				if (written >= MEMBER_PAGES_MOST) {
					continue;
				}
				written++;
				Component name = VAMPIRE.equals(id) ? Component.translatable("bestiary.jaymoskingdoms.vampire")
					: BuiltInRegistries.ENTITY_TYPE.getOptional(net.minecraft.resources.Identifier.parse(id)).map(EntityType::getDescription).orElse(Component.literal(id));
				String line = "bestiary.jaymoskingdoms.kind." + kind + ".";
				MutableComponent page = Component.empty().append(name.copy().withStyle(ChatFormatting.BOLD)).append("\n\n")
					.append(Component.translatable(line + "0").withStyle(ChatFormatting.BLACK));
				if ((known & SLAIN) != 0) {
					page.append("\n\n").append(Component.translatable(line + "1").withStyle(ChatFormatting.DARK_RED));
				}
				if ((known & FEARED) != 0) {
					page.append("\n\n").append(Component.translatable(line + "2").withStyle(ChatFormatting.DARK_BLUE));
				}
				pages.add(Filterable.passThrough(page));
			}
			if (smudge) {
				pages.add(Filterable.passThrough(Component.translatable("bestiary.jaymoskingdoms.unknown").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
			}
		}
	}
}
