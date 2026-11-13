package com.jaymoskingdoms;

import com.jaymoskingdoms.nightfall.NightBleeder;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.entity.monster.illager.Illusioner;
import net.minecraft.world.entity.monster.illager.Pillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The illager tongue (Andy, 2026-11-13: "make the illagers interactable like MCA, but only once you learn their language.
 * it can be acquired through books or paintings / time spent in their structures"). Nobody grows up speaking it, and no
 * village teacher knows it: it is learned bit by bit, as points from 0 to {@link #FLUENT}, from three things.
 * <ul>
 * <li><b>Their writings</b>, found as loot in their holds and on their dead: a torn page ({@link #SCRAWL}), a ledger
 * ({@link #LEDGER}) and an evoker's grimoire ({@link #GRIMOIRE}). Each is studied by holding use on it for a few seconds
 * and is spent; each teaches only so far ({@link #SCRAWL_TO}, {@link #LEDGER_TO}, the grimoire all the way).</li>
 * <li><b>Their banners and paintings</b>: sneak and use an empty hand on an ominous banner where it hangs, or on one of
 * their paintings (hung in their holds the first time someone walks in, {@link #hang}), and keep still for a few seconds.
 * Each banner (by where it hangs) teaches once, up to {@link #BANNER_TO}; each painting (by its picture) teaches once.</li>
 * <li><b>Time in their structures</b> (every Illager Structures place, the pillager outposts and the woodland mansions,
 * and the illager camps' land): a point a minute, and another while illagers are about to be overheard.</li>
 * </ul>
 * What illagers say ({@link Speech}, through {@link Languages#heard}) comes out as their gibberish until you know some
 * of the tongue, then partly translated ({@link #partial}: the client's {@code IllagerWords} picks out the words you
 * know, the same words every time, more as you learn), and whole once fluent. From {@link #TALK_FROM} they can be
 * talked to ({@link IllagerTalk}). The level shows on the H menu's Character page.
 */
public final class IllagerTongue {
	/** Points: speaking it; talking at all (halting); bluffing (good command); the whole scale. */
	public static final int FLUENT = 100, TALK_FROM = 50, BLUFF_FROM = 75;
	/** The labels' steps (lang {@code illager_tongue.jaymoskingdoms.level.<n>}): none, a few words, scraps, halting, good, fluent. */
	public static final int[] STEPS = {0, 1, 25, 50, 75, 100};
	/** What each writing teaches, and how far it can take you. */
	static final int SCRAWL_POINTS = 3, SCRAWL_TO = 40, LEDGER_POINTS = 8, LEDGER_TO = 70, GRIMOIRE_POINTS = 20;
	/** A banner studied, how far banners can take you; a painting studied. */
	static final int BANNER_POINTS = 4, BANNER_TO = 35, PAINTING_POINTS = 6;
	/** How long a writing takes to study (ticks), and a banner or painting (ticks, keeping still and crouched). */
	static final int READ_TICKS = 60, STUDY_TICKS = 80;
	/** How far you may stand from what you study (blocks). */
	static final double STUDY_REACH = 5.0;
	/** Time in their structures: how often it counts (ticks), and the extra point's range for illagers about (blocks). */
	static final int IMMERSION_TICKS = 20 * 60, IMMERSION_POINTS = 1, OVERHEARD_POINTS = 1;
	static final double OVERHEARD_RANGE = 24.0;
	/** Writings on their dead and in their chests: the chances. */
	static final float PILLAGER_DROP = 0.06F, VINDICATOR_DROP = 0.05F, EVOKER_DROP = 0.25F;
	static final float CHEST_SCRAWL = 0.6F, CHEST_LEDGER = 0.25F, CHEST_GRIMOIRE = 0.05F, MANSION_GRIMOIRE = 0.2F;
	/** Paintings hung in a structure the first time a player is inside it: at least, plus up to; tries a painting. */
	static final int PAINTINGS = 1, PAINTINGS_SPREAD = 2, PAINTING_TRIES = 60;
	/** Their chatter: how often players are looked round for a speaker (ticks), the chance one speaks, a speaker's rest (ticks). */
	static final int CHATTER_TICKS = 100, CHATTER_REST = 20 * 45;
	static final float CHATTER_CHANCE = 0.2F;
	static final double CHATTER_RANGE = 18.0;
	/** Lines of chatter per kind (lang {@code illager_tongue.jaymoskingdoms.chatter.<kind>.<n>}). */
	static final Map<String, Integer> CHATTER = Map.of("pillager", 6, "vindicator", 6, "evoker", 5, "warlord", 4);
	/** Their speech bubbles' colour. */
	public static final int COLOR = 0xB8C4A0;
	/** The words their gibberish is made of. */
	static final String[] SYLLABLES = {"ra", "gha", "vok", "esh", "kul", "dra", "ish", "mor", "taz", "ug", "ohr", "zek", "vel", "grim", "ash",
		"kar", "ur", "sk", "ith", "hask"};
	/** The client reads this key as a line to be partly translated ({@code IllagerWords}); its English text is the fallback, the gibberish. */
	public static final String PARTIAL = "illager_tongue.jaymoskingdoms.partial";
	/** Their paintings (painting variants {@code jaymoskingdoms:illager_<name>}; not in the placeable tag, so only hung by us). */
	static final List<String> PAINTINGS_LIST = List.of("illager_raid", "illager_warlord", "illager_rite", "illager_glyphs", "illager_march",
		"illager_banner");

	/** On a player: their points in the tongue. Saved, kept through death, synced to them alone (the H menu shows it). */
	public static final AttachmentType<Integer> POINTS = AttachmentRegistry.create(JaymosKingdoms.id("illager_tongue"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));
	/** On a player: what they have studied ({@code b:<dim>:<pos>} a banner, {@code p:<variant>} a painting). Saved, kept through death. */
	static final AttachmentType<List<String>> STUDIED = AttachmentRegistry.create(JaymosKingdoms.id("illager_studied"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());
	/** On the Overworld: the structures paintings have been hung in ({@code <dim>|<structure>|<start chunk>}). Saved. */
	static final AttachmentType<List<String>> HUNG = AttachmentRegistry.create(JaymosKingdoms.id("illager_paintings_hung"),
		builder -> builder.persistent(Codec.STRING.listOf()));
	/** The most studied banners kept per player (oldest forgotten first). */
	static final int STUDIED_MOST = 400;

	public static @Nullable Item SCRAWL, LEDGER, GRIMOIRE;

	/** Player → what they are studying now. */
	private record Study(String key, int points, int to, @Nullable BlockPos block, int entity, Vec3 at, int started) {
	}

	private static final Map<UUID, Study> STUDYING = new HashMap<>();
	/** Speaker → when they may next chatter (server tick). */
	private static final Map<Entity, Integer> RESTING = new WeakHashMap<>();

	private IllagerTongue() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("illager tongue attachments: {} {} {}", POINTS, STUDIED, HUNG);
		SCRAWL = item("illager_scrawl", Rarity.COMMON, SCRAWL_POINTS, SCRAWL_TO);
		LEDGER = item("illager_ledger", Rarity.UNCOMMON, LEDGER_POINTS, LEDGER_TO);
		GRIMOIRE = item("illager_grimoire", Rarity.RARE, GRIMOIRE_POINTS, FLUENT);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(SCRAWL);
			output.accept(LEDGER);
			output.accept(GRIMOIRE);
		});
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> table.unwrapKey().ifPresent(key -> loot(key.identifier(), context.getRandom(), drops)));
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
				|| !(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp)
				|| !(level.getBlockEntity(hit.getBlockPos()) instanceof BannerBlockEntity banner) || !ominous(level, banner)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			beginStudy(sp, "b:" + level.dimension().identifier() + ":" + pos.asLong(), BANNER_POINTS, BANNER_TO, pos, -1, "banner");
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
				|| world.isClientSide() || !(player instanceof ServerPlayer sp) || !(entity instanceof Painting painting)) {
				return InteractionResult.PASS;
			}
			String variant = ours(painting);
			if (variant == null) {
				return InteractionResult.PASS;
			}
			beginStudy(sp, "p:" + variant, PAINTING_POINTS, FLUENT, null, painting.getId(), "painting");
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(IllagerTongue::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STUDYING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STUDYING.clear();
			RESTING.clear();
		});
	}

	/** A writing: held use for {@link #READ_TICKS}, then studied and spent. */
	private static Item item(String name, Rarity rarity, int points, int to) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new Writing(new Item.Properties().setId(key).stacksTo(16).rarity(rarity), points, to));
	}

	/** A page, ledger or grimoire in their hand. */
	static final class Writing extends Item {
		final int points, to;

		Writing(Item.Properties properties, int points, int to) {
			super(properties);
			this.points = points;
			this.to = to;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return READ_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.NONE;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (!level.isClientSide() && remaining % 20 == 0) {
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 0.9F + user.getRandom().nextFloat() * 0.2F);
			}
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (!(user instanceof ServerPlayer player)) {
				return stack;
			}
			int before = points(player);
			if (before >= this.to) {
				// (nothing in it you don't already know: kept)
				player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.read.known").withStyle(ChatFormatting.GRAY));
				return stack;
			}
			learn(player, Math.min(this.points, this.to - before), "read");
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
			return stack;
		}
	}

	// ---- who speaks it -------------------------------------------------------------------------------------------------

	/** Whether {@code e} speaks the illager tongue: a living illager (pillager, vindicator, evoker, illusioner), not the undead Night Bleeder. */
	public static boolean speaker(@Nullable Entity e) {
		return e instanceof AbstractIllager && !(e instanceof NightBleeder) && e.isAlive();
	}

	/** What kind of speaker {@code e} is, for its lines: warlord, evoker (and illusioner), pillager, vindicator. */
	static String kind(Entity e) {
		Illagers.Tag tag = Illagers.tag(e);
		if (tag != null && tag.role() == Illagers.WARLORD) {
			return "warlord";
		}
		if (e instanceof Evoker || e instanceof Illusioner) {
			return "evoker";
		}
		return e instanceof Pillager ? "pillager" : "vindicator";
	}

	// ---- how much you know ---------------------------------------------------------------------------------------------

	/** {@code player}'s points in the tongue (creative and spectators know it all). */
	public static int points(Player player) {
		if (player.isCreative() || player.isSpectator()) {
			return FLUENT;
		}
		return Math.min(FLUENT, player.getAttachedOrElse(POINTS, 0));
	}

	/** The step {@code points} is at ({@link #STEPS}): 0 none .. 5 fluent. */
	public static int step(int points) {
		int s = 0;
		while (s + 1 < STEPS.length && points >= STEPS[s + 1]) {
			s++;
		}
		return s;
	}

	/** How well one knows it, in words ("Halting (54%)"). */
	public static Component describe(int points) {
		return Component.translatable("illager_tongue.jaymoskingdoms.describe", Component.translatable("illager_tongue.jaymoskingdoms.level." + step(points)),
			Math.min(FLUENT, points));
	}

	/** {@code player} learns {@code n} points ({@code how}: read, banner, painting, time). Told over the hotbar, never in chat. */
	static void learn(ServerPlayer player, int n, String how) {
		int before = player.getAttachedOrElse(POINTS, 0);
		if (n <= 0 || before >= FLUENT) {
			return;
		}
		int after = Math.min(FLUENT, before + FeatsTrade.linguistPoints(player, n));
		player.setAttached(POINTS, after);
		if (!how.equals("time")) {
			Skills.grant(player, Skills.Pillar.SCHOLAR, 1);
		}
		if (step(after) > step(before)) {
			player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.step", describe(after)).withStyle(ChatFormatting.GOLD));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.8F);
		} else if (!how.equals("time")) {
			player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.learned." + how, describe(after)).withStyle(ChatFormatting.AQUA));
		}
	}

	// ---- what you hear -------------------------------------------------------------------------------------------------

	/**
	 * {@code words} from {@code speaker}, an illager, as {@code player} hears them: whole once fluent; with none of the
	 * tongue, their gibberish; between, a line the client part-translates ({@link #partial}).
	 */
	public static Component heard(ServerPlayer player, Entity speaker, Component words) {
		int p = points(player);
		if (p >= FLUENT) {
			return words;
		}
		long seed = speaker.getUUID().hashCode() * 31L + words.toString().hashCode();
		Component garbled = garble(seed);
		return p <= 0 ? garbled : partial(p, words, garbled);
	}

	/** A line the client shows partly translated: the words {@code points} in 100 covers, the rest gibberish ({@code IllagerWords}). */
	static Component partial(int points, Component words, Component garbled) {
		return Component.translatable(PARTIAL, Integer.toString(points), words, garbled);
	}

	/** A line of their gibberish. */
	public static Component garble(long seed) {
		RandomSource random = RandomSource.create(seed);
		StringBuilder line = new StringBuilder();
		int count = 3 + random.nextInt(5);
		for (int w = 0; w < count; w++) {
			String word = word(random, 1 + random.nextInt(3));
			if (w == 0) {
				word = Character.toUpperCase(word.charAt(0)) + word.substring(1);
			}
			line.append(w == 0 ? "" : random.nextInt(6) == 0 ? ", " : " ").append(word);
		}
		line.append(random.nextInt(3) == 0 ? "!" : ".");
		return Component.literal(line.toString()).withStyle(ChatFormatting.ITALIC);
	}

	/** A word of gibberish, {@code syllables} long (also the client's, for the words you don't know). */
	public static String word(RandomSource random, int syllables) {
		StringBuilder w = new StringBuilder();
		for (int s = 0; s < syllables; s++) {
			w.append(SYLLABLES[random.nextInt(SYLLABLES.length)]);
		}
		return w.toString();
	}

	/**
	 * Whether one who knows {@code points} in 100 of the tongue knows {@code word}: each word has its own mark from its
	 * spelling, so the same words come first every time and the known ones only grow; short words come sooner.
	 */
	public static boolean knowsWord(String word, int points) {
		String w = word.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}']", "");
		if (w.isEmpty()) {
			return true;
		}
		int mark = Math.floorMod(w.hashCode() * 0x9E3779B1, 100);
		if (w.length() <= 3) {
			mark /= 2;
		}
		return mark < points;
	}

	// ---- their writings ------------------------------------------------------------------------------------------------

	private static void loot(Identifier id, RandomSource random, List<ItemStack> drops) {
		String path = id.getPath();
		boolean ours = id.getNamespace().equals(JaymosKingdoms.MOD_ID);
		if (id.getNamespace().equals("minecraft") && path.equals("chests/pillager_outpost")
			|| ours && path.startsWith("chests/illager_sites/")) {
			addChance(drops, random, SCRAWL, CHEST_SCRAWL, 1 + random.nextInt(2));
			addChance(drops, random, LEDGER, CHEST_LEDGER, 1);
			addChance(drops, random, GRIMOIRE, CHEST_GRIMOIRE, 1);
		} else if (id.getNamespace().equals("minecraft") && path.equals("chests/woodland_mansion")) {
			addChance(drops, random, SCRAWL, CHEST_SCRAWL, 2);
			addChance(drops, random, LEDGER, CHEST_LEDGER * 2, 1);
			addChance(drops, random, GRIMOIRE, MANSION_GRIMOIRE, 1);
		} else if (id.getNamespace().equals("minecraft") && path.equals("entities/pillager")) {
			addChance(drops, random, SCRAWL, PILLAGER_DROP, 1);
		} else if (id.getNamespace().equals("minecraft") && path.equals("entities/vindicator")) {
			addChance(drops, random, random.nextInt(4) == 0 ? LEDGER : SCRAWL, VINDICATOR_DROP, 1);
		} else if (id.getNamespace().equals("minecraft") && (path.equals("entities/evoker") || path.equals("entities/illusioner"))) {
			addChance(drops, random, random.nextInt(3) == 0 ? GRIMOIRE : LEDGER, EVOKER_DROP, 1);
		}
	}

	private static void addChance(List<ItemStack> drops, RandomSource random, @Nullable Item item, float chance, int count) {
		if (item != null && random.nextFloat() < chance) {
			drops.add(new ItemStack(item, count));
		}
	}

	// ---- their banners and paintings -----------------------------------------------------------------------------------

	/** Whether {@code banner} is theirs: the ominous banner's own pattern. */
	static boolean ominous(ServerLevel level, BannerBlockEntity banner) {
		ItemStack theirs = Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
		BannerPatternLayers layers = theirs.get(DataComponents.BANNER_PATTERNS);
		return layers != null && !layers.layers().isEmpty() && layers.equals(banner.getPatterns());
	}

	/** {@code painting}'s variant, if it is one of theirs. */
	static @Nullable String ours(Painting painting) {
		Optional<ResourceKey<PaintingVariant>> key = painting.getVariant().unwrapKey();
		if (key.isEmpty() || !key.get().identifier().getNamespace().equals(JaymosKingdoms.MOD_ID)) {
			return null;
		}
		String path = key.get().identifier().getPath();
		return PAINTINGS_LIST.contains(path) ? path : null;
	}

	private static void beginStudy(ServerPlayer player, String key, int points, int to, @Nullable BlockPos block, int entity, String what) {
		if (player.getAttachedOrElse(STUDIED, List.<String>of()).contains(key)) {
			player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.study.done." + what).withStyle(ChatFormatting.GRAY));
			return;
		}
		if (points(player) >= to) {
			player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.study.nothing." + what).withStyle(ChatFormatting.GRAY));
			return;
		}
		STUDYING.put(player.getUUID(), new Study(key, points, to, block, entity, player.position(), player.level().getServer().getTickCount()));
		player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.study.start." + what).withStyle(ChatFormatting.GRAY));
	}

	/** Each tick: studies going on (kept crouched, still and near, or given up). */
	private static void studies(MinecraftServer server) {
		int now = server.getTickCount();
		for (var it = STUDYING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Study> e = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Study s = e.getValue();
			if (player == null) {
				it.remove();
				continue;
			}
			Vec3 target = s.block() != null ? Vec3.atCenterOf(s.block())
				: player.level().getEntity(s.entity()) instanceof Painting p && p.isAlive() ? p.position() : null;
			if (target == null || !player.isShiftKeyDown() || player.position().distanceToSqr(s.at()) > 1.0
				|| player.getEyePosition().distanceTo(target) > STUDY_REACH + 1.0) {
				player.sendOverlayMessage(Component.translatable("illager_tongue.jaymoskingdoms.study.broken").withStyle(ChatFormatting.GRAY));
				it.remove();
				continue;
			}
			int done = now - s.started();
			if (done < STUDY_TICKS) {
				if (done % 20 == 0) {
					int bars = done * 10 / STUDY_TICKS;
					player.sendOverlayMessage(Component.literal("▮".repeat(bars) + "▯".repeat(10 - bars)).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			it.remove();
			List<String> studied = new ArrayList<>(player.getAttachedOrElse(STUDIED, List.<String>of()));
			studied.add(s.key());
			while (studied.size() > STUDIED_MOST) {
				studied.remove(0);
			}
			player.setAttached(STUDIED, List.copyOf(studied));
			learn(player, Math.min(s.points(), s.to() - points(player)), s.block() != null ? "banner" : "painting");
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
	}

	// ---- time in their structures --------------------------------------------------------------------------------------

	/** Whether a structure is one of theirs: every Illager Structures place, a pillager outpost, a woodland mansion. */
	static boolean theirs(Holder<Structure> structure) {
		Optional<ResourceKey<Structure>> key = structure.unwrapKey();
		if (key.isEmpty()) {
			return false;
		}
		Identifier id = key.get().identifier();
		return id.getNamespace().equals(JaymosKingdoms.MOD_ID) ? id.getPath().startsWith(IllagerSites.PREFIX)
			: id.getNamespace().equals("minecraft") && (id.getPath().equals("pillager_outpost") || id.getPath().equals("mansion"));
	}

	/** The structure of theirs {@code player} stands in, or null. */
	static @Nullable StructureStart within(ServerLevel level, BlockPos pos) {
		StructureStart start = level.structureManager().getStructureWithPieceAt(pos, IllagerTongue::theirs);
		return start != null && start.isValid() ? start : null;
	}

	/** Whether {@code pos} is on an illager camp's land. */
	static boolean onCampLand(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.at(server, level, pos);
		return f != null && !f.fallen() && IllagerCamps.KIND.equals(Camps.kind(server, f.id()));
	}

	private static void tick(MinecraftServer server) {
		studies(server);
		int now = server.getTickCount();
		if (now % CHATTER_TICKS == 0) {
			chatter(server, now);
		}
		if (now % IMMERSION_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			StructureStart start = within(level, player.blockPosition());
			if (start != null) {
				hang(level, start);
			}
			if (start == null && !onCampLand(level, player.blockPosition()) || player.isCreative() || points(player) >= FLUENT) {
				continue;
			}
			int n = IMMERSION_POINTS;
			if (!level.getEntitiesOfClass(AbstractIllager.class, player.getBoundingBox().inflate(OVERHEARD_RANGE), IllagerTongue::speaker).isEmpty()) {
				n += OVERHEARD_POINTS;
			}
			learn(player, n, "time");
		}
	}

	/**
	 * The first time a player is inside one of their structures, a few of their paintings go up on its walls (indoors,
	 * where one fits). Once per structure, ever.
	 */
	static void hang(ServerLevel level, StructureStart start) {
		ServerLevel overworld = level.getServer().overworld();
		Identifier structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
		String key = level.dimension().identifier() + "|" + structure + "|" + start.getChunkPos().pack();
		List<String> hung = overworld.getAttachedOrElse(HUNG, List.<String>of());
		if (hung.contains(key)) {
			return;
		}
		List<String> next = new ArrayList<>(hung);
		next.add(key);
		overworld.setAttached(HUNG, List.copyOf(next));
		var variants = level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT);
		RandomSource random = level.getRandom();
		BoundingBox box = start.getBoundingBox();
		boolean mansion = structure != null && structure.getPath().equals("mansion");
		int count = PAINTINGS + random.nextInt(PAINTINGS_SPREAD + 1) + (mansion ? 2 : 0);
		Set<String> used = new HashSet<>();
		for (int i = 0; i < count; i++) {
			String name = PAINTINGS_LIST.get(random.nextInt(PAINTINGS_LIST.size()));
			if (!used.add(name)) {
				continue;
			}
			Optional<Holder.Reference<PaintingVariant>> variant = variants.get(ResourceKey.create(Registries.PAINTING_VARIANT, JaymosKingdoms.id(name)));
			if (variant.isEmpty()) {
				continue;
			}
			for (int t = 0; t < PAINTING_TRIES; t++) {
				BlockPos pos = new BlockPos(box.minX() + random.nextInt(box.getXSpan()), box.minY() + random.nextInt(box.getYSpan()),
					box.minZ() + random.nextInt(box.getZSpan()));
				if (!level.isLoaded(pos) || !level.getBlockState(pos).isAir() || level.canSeeSky(pos) || !level.structureManager().structureHasPieceAt(pos, start)) {
					continue;
				}
				Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(random);
				if (!level.getBlockState(pos.relative(facing.getOpposite())).isSolidRender()) {
					continue;
				}
				Painting painting = new Painting(level, pos, facing, variant.get());
				if (painting.survives()) {
					level.addFreshEntity(painting);
					break;
				}
			}
		}
	}

	// ---- their chatter -------------------------------------------------------------------------------------------------

	/** Now and then an illager near a player, not fighting, says something to its own (in their tongue). */
	private static void chatter(MinecraftServer server, int now) {
		Set<Entity> spoke = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			for (AbstractIllager illager : level.getEntitiesOfClass(AbstractIllager.class, player.getBoundingBox().inflate(CHATTER_RANGE), IllagerTongue::speaker)) {
				if (spoke.contains(illager) || illager.getTarget() != null || Downed.isDowned(illager) || illager.isSilent()
					|| RESTING.getOrDefault(illager, 0) > now || level.getRandom().nextFloat() > CHATTER_CHANCE) {
					continue;
				}
				spoke.add(illager);
				RESTING.put(illager, now + CHATTER_REST + level.getRandom().nextInt(CHATTER_REST));
				String kind = kind(illager);
				int lines = CHATTER.getOrDefault(kind, 1);
				Speech.say(level, illager, Component.translatable("illager_tongue.jaymoskingdoms.chatter." + kind + "." + level.getRandom().nextInt(lines)), COLOR, CHATTER_RANGE);
				level.playSound(null, illager.getX(), illager.getY(), illager.getZ(), illager instanceof Evoker ? SoundEvents.EVOKER_AMBIENT
					: illager instanceof Pillager ? SoundEvents.PILLAGER_AMBIENT : SoundEvents.VINDICATOR_AMBIENT, SoundSource.HOSTILE, 0.8F, 1.0F);
				break;
			}
		}
	}

	/** (DeckOfFates' forgetting) all of the tongue gone. */
	static void forget(ServerPlayer player) {
		player.removeAttached(POINTS);
		player.removeAttached(STUDIED);
	}
}
