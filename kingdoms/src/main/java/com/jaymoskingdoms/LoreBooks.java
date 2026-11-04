package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes, Orc war journals (the idea after RPG Lore's lore books, MIT; the code and the words are ours). Orcs and
 * goblins leave their writing about, and finding it is how a player learns what they were never told:
 * <ul>
 * <li><b>War journals</b> ({@link WarJournal}) drop now and then from a goblin or an orc a player kills (a chief or a captain
 * far more often; each tribe scribbles more or less than the next), lie in the camp stores ({@link #camp}) and are found in a
 * broken greenskin cart ({@link #cart}). An orc's is boasts of raids, brawls, chiefs broken and who is coming next; a goblin's
 * is a sly ledger, grudges, escape plans and maps scribbled in the margins. Each is built from the real world about it (the
 * tribe's name, its captains, its camps, the realms nearby) in the brood's dialect ({@link Dialects}), with a few fixed
 * fragments between ({@link #FRAGMENTS} for each brood) that only ever hint at the remnants, the monoliths and the Aether, and
 * never say.</li>
 * <li><b>Data books</b>: any {@code data/<ns>/lore/*.json} is a book too: {@code {"title", "author"?, "pages": [...], "drop"?}}
 * where the drop holds filters ({@code entities}, {@code broods}, {@code biomes}, {@code dimensions}, {@code chief},
 * {@code chance}, {@code camp}) so more can be added later without code.</li>
 * </ul>
 * Every book carries its id in custom data ({@link #KEY}), which is how the {@link LoreCodex} keeps one of each.
 */
public final class LoreBooks {
	// ---- tuning ----
	/** The chance a goblin or an orc killed by a player drops a journal: a rank-and-file one, a chief (king, warchief), a captain. */
	static final double DROP_FOLK = 0.03, DROP_CHIEF = 0.30, DROP_CAPTAIN = 0.20;
	/** How much more or less each tribe writes (the drop chance is scaled by 1 plus or minus this). */
	static final double TRIBE_SPREAD = 0.5;
	/** A camp's store: the chance each day a journal is added, and how many may lie there at once. */
	static final double CAMP_DAILY = 0.15;
	static final int CAMP_MAX = 1, CAMP_DAYS_AT_MOST = 6;
	/** One in this many camp stockings is a data book (if any suit that camp's brood) instead of a made journal. */
	static final int CAMP_DATA_ONE_IN = 3;
	/** The chance a greenskin cart broken open had a journal in it. */
	static final double CART = 0.5;
	/** How many fixed fragments each brood has (lang keys {@code journal.frag.0 ...}), and how many a journal carries. */
	public static final int FRAGMENTS = 14, FRAGMENTS_PER_JOURNAL = 4;
	/** How many variants each kind of made page has (lang keys {@code journal.<kind>.0 ...}) and each cover. */
	static final int VARIANTS = 3, COVERS = 2;
	/** The realms a journal may name are those within this many blocks. */
	static final int REALM_RANGE = 1800;
	/** The page kinds each brood writes; a journal has a cover, three of these, and fragments between. */
	static final List<String> ORC_PAGES = List.of("raid", "brawl", "broken", "next"), GOBLIN_PAGES = List.of("ledger", "grudge", "escape", "map");

	/** Custom data on a lore book: its id. */
	public static final String KEY = "jaymoskingdoms_lore";

	/** The data books' drop filters; an empty list means "any". */
	record Drop(List<String> entities, List<String> broods, List<String> biomes, List<Identifier> dimensions, Optional<Boolean> chief, double chance, boolean camp) {
		static final Codec<Drop> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.listOf().optionalFieldOf("entities", List.of()).forGetter(Drop::entities),
			Codec.STRING.listOf().optionalFieldOf("broods", List.of()).forGetter(Drop::broods),
			Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(Drop::biomes),
			Identifier.CODEC.listOf().optionalFieldOf("dimensions", List.of()).forGetter(Drop::dimensions),
			Codec.BOOL.optionalFieldOf("chief").forGetter(Drop::chief),
			Codec.doubleRange(0.0, 1.0).optionalFieldOf("chance", 0.02).forGetter(Drop::chance),
			Codec.BOOL.optionalFieldOf("camp", false).forGetter(Drop::camp)).apply(i, Drop::new));

		boolean matchesBrood(@Nullable String brood) {
			return this.broods.isEmpty() || brood != null && this.broods.contains(brood);
		}

		boolean matches(ServerLevel level, LivingEntity mob, @Nullable String brood, boolean leader) {
			if (!matchesBrood(brood) || !this.dimensions.isEmpty() && !this.dimensions.contains(level.dimension().identifier())
				|| this.chief.isPresent() && this.chief.get() != leader) {
				return false;
			}
			if (!this.entities.isEmpty() && this.entities.stream().noneMatch(e -> entity(e, mob.getType()))) {
				return false;
			}
			if (this.biomes.isEmpty()) {
				return true;
			}
			Holder<Biome> biome = level.getBiome(mob.blockPosition());
			for (String b : this.biomes) {
				if (b.startsWith("#")) {
					Identifier tag = Identifier.tryParse(b.substring(1));
					if (tag != null && biome.is(TagKey.create(Registries.BIOME, tag))) {
						return true;
					}
				} else if (biome.unwrapKey().map(k -> k.identifier().toString().equals(b)).orElse(false)) {
					return true;
				}
			}
			return false;
		}

		private static boolean entity(String filter, EntityType<?> type) {
			if (filter.startsWith("#")) {
				Identifier tag = Identifier.tryParse(filter.substring(1));
				return tag != null && type.builtInRegistryHolder().is(TagKey.create(Registries.ENTITY_TYPE, tag));
			}
			return filter.equals(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		}
	}

	/** A data book: its title, its author (if any), its pages (text components or plain strings) and where it turns up (if anywhere). */
	record Lore(Component title, Optional<String> author, List<Component> pages, Optional<Drop> drop) {
		static final Codec<Lore> CODEC = RecordCodecBuilder.create(i -> i.group(
			ComponentSerialization.CODEC.fieldOf("title").forGetter(Lore::title),
			Codec.STRING.optionalFieldOf("author").forGetter(Lore::author),
			ComponentSerialization.CODEC.listOf().fieldOf("pages").forGetter(Lore::pages),
			Drop.CODEC.optionalFieldOf("drop").forGetter(Lore::drop)).apply(i, Lore::new));
	}

	private static Map<Identifier, Lore> books = Map.of();

	private LoreBooks() {
	}

	public static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKingdoms.id("lore"),
			new SimpleJsonResourceReloadListener<>(Lore.CODEC, FileToIdConverter.json("lore")) {
				@Override
				protected void apply(Map<Identifier, Lore> loaded, ResourceManager manager, ProfilerFiller profiler) {
					books = Map.copyOf(loaded);
				}
			});
		ServerLivingEntityEvents.AFTER_DEATH.register(LoreBooks::died);
	}

	// ---- a book's identity ---------------------------------------------------------------------------------------------------

	/** The id a lore book carries, or empty if it isn't one. */
	public static String idOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? "" : data.copyTag().getStringOr(KEY, "");
	}

	/** Marks {@code stack} as the lore book {@code id}. */
	public static void tag(ItemStack stack, String id) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putString(KEY, id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/** A journal or a lore book lying in a store. */
	static boolean isLore(ItemStack stack) {
		return !idOf(stack).isEmpty();
	}

	/** (0.7.509) The lore book {@code path} under this mod's namespace (a patron's, say), or empty if it isn't loaded. */
	static ItemStack book(String path) {
		Identifier id = JaymosKingdoms.id(path);
		Lore lore = books.get(id);
		return lore == null ? ItemStack.EMPTY : dataBook(id, lore);
	}

	private static ItemStack dataBook(Identifier id, Lore lore) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> pages = new ArrayList<>();
		for (Component page : lore.pages()) {
			pages.add(Filterable.passThrough(page));
		}
		// (0.7.490) a written book's title is capped (32): a longer one could not be sent or saved, so it is cut, and the full one goes on the name
		String title = lore.title().getString();
		String shortTitle = StringUtil.truncateStringIfNecessary(title, WrittenBookContent.TITLE_MAX_LENGTH, false);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(shortTitle), lore.author().orElse(""), 0, pages, true));
		if (!shortTitle.equals(title)) {
			book.set(DataComponents.CUSTOM_NAME, lore.title());
		}
		tag(book, id.toString());
		return book;
	}

	// ---- where they turn up --------------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer killer) || entity instanceof ServerPlayer
			|| Pedlars.pedlar(entity)) {
			return;
		}
		RandomSource random = level.getRandom();
		String brood = Dialects.of(entity);
		boolean captain = Captains.is(entity);
		boolean leader = captain || entity instanceof Worker w && Greenskins.chief(w.role());
		if (brood != null) {
			String tribe = entity instanceof Worker w ? Tribes.of(level.getServer(), w.factionId()) : Tribes.of(entity);
			double chance = (captain ? DROP_CAPTAIN : leader ? DROP_CHIEF : DROP_FOLK) * tribeFactor(tribe);
			if (random.nextDouble() < chance) {
				entity.spawnAtLocation(level, journal(level, entity.blockPosition(), tribe, brood));
				LoreCodex.offer(killer);
			}
		}
		for (Map.Entry<Identifier, Lore> e : books.entrySet()) {
			Drop drop = e.getValue().drop().orElse(null);
			if (drop != null && drop.matches(level, entity, brood, leader) && random.nextDouble() < drop.chance()) {
				entity.spawnAtLocation(level, dataBook(e.getKey(), e.getValue()));
				LoreCodex.offer(killer);
			}
		}
	}

	/** How much this tribe writes: from half again fewer to half again more journals, the same for a tribe every time. */
	static double tribeFactor(@Nullable String tribe) {
		if (tribe == null) {
			return 1.0;
		}
		return 1.0 + TRIBE_SPREAD * (Math.floorMod(tribe.hashCode() * 31, 201) - 100) / 100.0;
	}

	/** (Daily, from a camp's economy) now and then a journal, or a data book that suits, is left in the camp's store. */
	static void camp(ServerLevel level, Faction camp, boolean orc, int days) {
		if (!Storage.loaded(level, camp)) {
			return;
		}
		RandomSource random = level.getRandom();
		String brood = orc ? Dialects.ORC : Dialects.GOBLIN;
		for (int d = 0; d < Math.min(days, CAMP_DAYS_AT_MOST); d++) {
			if (random.nextDouble() >= CAMP_DAILY || Storage.count(level, camp, LoreBooks::isLore) >= CAMP_MAX) {
				continue;
			}
			ItemStack book = null;
			if (random.nextInt(CAMP_DATA_ONE_IN) == 0) {
				List<Map.Entry<Identifier, Lore>> suit = new ArrayList<>();
				for (Map.Entry<Identifier, Lore> e : books.entrySet()) {
					Drop drop = e.getValue().drop().orElse(null);
					if (drop != null && drop.camp() && drop.matchesBrood(brood)) {
						suit.add(e);
					}
				}
				if (!suit.isEmpty()) {
					Map.Entry<Identifier, Lore> pick = suit.get(random.nextInt(suit.size()));
					book = dataBook(pick.getKey(), pick.getValue());
				}
			}
			if (book == null) {
				book = journal(level, camp.seat(), Tribes.of(level.getServer(), camp.id()), brood);
			}
			Storage.put(level, camp, book);
		}
	}

	/** A greenskin cart is broken open at {@code at}: now and then a journal lies among its goods. */
	static void cart(ServerLevel level, Faction owner, BlockPos at) {
		String tribe = owner.type() == FactionType.BARBARIAN ? Tribes.of(level.getServer(), owner.id()) : null;
		String brood = tribe == null ? null : Dialects.ofTribe(tribe);
		if (brood != null && level.getRandom().nextDouble() < CART) {
			Block.popResource(level, at, journal(level, at, tribe, brood));
		}
	}

	// ---- the journal ---------------------------------------------------------------------------------------------------------

	/**
	 * A war journal written by {@code brood} near {@code at}, of {@code tribe} (the region's tribe if none): a cover, three pages
	 * of its brood's kinds and {@link #FRAGMENTS_PER_JOURNAL} fixed fragments between them.
	 */
	public static ItemStack journal(ServerLevel level, BlockPos at, @Nullable String tribe, String brood) {
		long seed = level.getRandom().nextLong();
		RandomSource random = RandomSource.create(seed);
		MinecraftServer server = level.getServer();
		boolean orc = Dialects.ORC.equals(brood);
		String own = tribe != null ? tribe : Tribes.key(orc ? Tribes.ORC : Tribes.GOBLIN, at);
		Heritage.Kind kind = orc ? Heritage.Kind.ORC : Heritage.Kind.GOBLIN;
		Component tribeName = Tribes.name(own);
		// the real world about the writer: his captains, his camps, the realms in reach
		List<String> captains = new ArrayList<>();
		for (Captain c : Captains.of(server, own)) {
			captains.add(c.name());
		}
		List<Faction> camps = Tribes.camps(server, own);
		List<Faction> realms = new ArrayList<>();
		if (level == Factions.level(server)) {
			for (Faction f : Factions.all(server)) {
				if (f.type() != FactionType.BARBARIAN && !f.fallen() && f.seat().distSqr(at) <= (double)REALM_RANGE * REALM_RANGE) {
					realms.add(f);
				}
			}
			realms.sort(Comparator.comparingDouble(f -> f.seat().distSqr(at)));
		}
		String capA = pick(captains, 0, random, kind), capB = pick(captains, 1, random, kind);
		Component somewhere = Dialects.trBrood(brood, "journal.somewhere");
		Faction realm0 = realms.isEmpty() ? null : realms.get(0), realm1 = realms.size() < 2 ? realm0 : realms.get(1);
		Object name0 = realm0 == null ? somewhere : realm0.name(), name1 = realm1 == null ? somewhere : realm1.name();
		Object camp0 = camps.isEmpty() ? somewhere : camps.get(0).name(), campOther = camps.size() < 2 ? somewhere : camps.get(1 + random.nextInt(camps.size() - 1)).name();
		Component rival = Tribes.name(Tribes.rival(own));
		Component dir = realm0 == null ? somewhere : Component.translatable("lore.jaymoskingdoms.dir." + direction(at, realm0.seat()));
		Object blocks = realm0 == null ? "?" : Integer.valueOf(Math.max(50, (int)Math.round(Math.sqrt(realm0.seat().distSqr(at)) / 50.0) * 50));

		List<Component> pages = new ArrayList<>();
		pages.add(Dialects.trBrood(brood, "journal.cover." + random.nextInt(COVERS), tribeName, capA));
		List<String> kinds = new ArrayList<>(orc ? ORC_PAGES : GOBLIN_PAGES);
		if (realm0 == null) {
			kinds.remove("map"); // (0.7.490) no realm in reach: a map with no way and no distance would read broken
		}
		Collections.shuffle(kinds, new java.util.Random(seed));
		List<Integer> frags = new ArrayList<>();
		for (int i = 0; i < FRAGMENTS; i++) {
			frags.add(i);
		}
		Collections.shuffle(frags, new java.util.Random(seed ^ 0x5DEECE66DL));
		for (int i = 0; i < 3; i++) {
			Object[] args = switch (kinds.get(i)) {
				case "raid" -> new Object[] {name0, tribeName};
				case "brawl" -> new Object[] {capA, capB};
				case "broken" -> new Object[] {rival, capA};
				case "next" -> new Object[] {name1, capA};
				case "ledger" -> new Object[] {name0, capA};
				case "grudge" -> new Object[] {capA, campOther};
				case "escape" -> new Object[] {camp0, name0};
				default -> new Object[] {name0, dir, blocks};
			};
			pages.add(Dialects.trBrood(brood, "journal." + kinds.get(i) + "." + random.nextInt(VARIANTS), args));
			pages.add(Dialects.trBrood(brood, "journal.frag." + frags.get(i)));
		}
		pages.add(Dialects.trBrood(brood, "journal.frag." + frags.get(FRAGMENTS_PER_JOURNAL)));
		return WarJournal.make(brood, Long.toString(seed & Long.MAX_VALUE, 36) + "@" + own, pages);
	}

	private static String pick(List<String> captains, int index, RandomSource random, Heritage.Kind kind) {
		return index < captains.size() ? captains.get(index) : Greenskins.name(kind, random.nextLong());
	}

	/** Which way {@code to} lies from {@code from}: n, ne, e ... (north is -z). */
	static String direction(BlockPos from, BlockPos to) {
		double angle = Math.toDegrees(Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX()));
		String[] names = {"e", "se", "s", "sw", "w", "nw", "n", "ne"};
		return names[Math.floorMod((int)Math.round(angle / 45.0), 8)];
	}
}
