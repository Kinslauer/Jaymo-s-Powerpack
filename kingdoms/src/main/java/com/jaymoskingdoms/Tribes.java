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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.425) Havens patch 1, the tribes and their grudge (ideas from King's Monsters, ARR, and Shadow of War: our own names
 * and code). Andy: "i just read the one grudge per tribe and it works in perfectly".
 *
 * <p>A <b>tribe</b> is a brood (goblins, orcs) in a region ({@link #REGION} blocks a side): every goblin village and orc
 * camp belongs to its brood's tribe there, and the oldest camp of it is the king's seat. Each tribe keeps <b>one grudge per
 * player</b> ({@link #MOST} at most): killing its folk raises it ({@link #KILL}, a king or warchief {@link #CHIEF}, its
 * pedlar {@link Pedlars#GRUDGE}), and it falls with time ({@link #DAILY}), tribute paid to a chief or the pedlar
 * ({@link #TRIBUTE_EMERALDS}), killing its rival brood's folk in the same region ({@link #RIVAL}), or a truce token
 * ({@link #TOKEN}, from the pedlar: wiped clean and a truce of {@link #TRUCE_DAYS} days in which they don't attack you
 * unless struck). A high grudge ({@link #HUNT_AT}) sends a <b>hunting band</b> after you when you're in their country.
 * You hear the mood only by talk: from the pedlar, or one you question ("What does your king think of me?").
 * <p>Every chief's store holds a map to the king's seat ({@link #seatMap}).
 */
public final class Tribes {
	// ================================================================================================================
	// Tuning
	/** A tribe's country: the brood's camps in a square this many blocks a side. */
	static final int REGION = 1536;
	/** The grudge: its most, and what raises or lowers it. */
	static final int MOST = 100, KILL = 4, CHIEF = 30, RIVAL = -2, DAILY = 3;
	/** Tribute: emeralds for each {@link #TRIBUTE_STEP} of grudge paid off. */
	static final int TRIBUTE_EMERALDS = 3, TRIBUTE_STEP = 10;
	/** A truce token's truce (days), and what it costs at the pedlar (emeralds). */
	static final int TRUCE_DAYS = 3, TOKEN_PRICE = 16;
	/** A hunting band: sent at this grudge, at most once in this long (ticks) for a player, while they're this near the tribe's camps. */
	static final int HUNT_AT = 60;
	static final long HUNT_COOLDOWN = 24000L * 2;
	static final double HUNT_REACH = 520.0;
	/** Its size (a base, one more for each so much grudge), how far off it musters, and how long it hunts before giving up (ticks). */
	static final int HUNT_BASE = 3, HUNT_PER = 25, HUNT_MAX = 7, HUNT_NEAR = 40, HUNT_FAR = 56;
	static final long HUNT_TICKS = 20 * 60 * 5;
	/** How often hunts and seat maps are seen to (ticks). */
	static final int EVERY = 200;
	/** The mood's bands (for talk): at or above each, the next line. */
	static final int MOOD_WARY = 15, MOOD_ANGRY = 40, MOOD_HUNTED = HUNT_AT;
	// ================================================================================================================

	/** The broods with tribes. */
	public static final String GOBLIN = "goblin", ORC = "orc";
	/** Tags: a hunting band's member (by band id after it). */
	static final String HUNTER_TAG = "jaymoskingdoms.tribe_hunter";
	/** A seat map's and a truce token's tribe (custom data). */
	static final String TRIBE_DATA = "jaymoskingdoms_tribe";

	/** One tribe's grudges, truces (until, game time) and last hunts, by player id. */
	record Tribe(Map<String, Integer> grudge, Map<String, Long> truce, Map<String, Long> hunted) {
		static final Tribe EMPTY = new Tribe(Map.of(), Map.of(), Map.of());
		static final Codec<Tribe> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("grudge", Map.of()).forGetter(Tribe::grudge),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("truce", Map.of()).forGetter(Tribe::truce),
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("hunted", Map.of()).forGetter(Tribe::hunted))
			.apply(i, Tribe::new));
	}

	/** On the Overworld: every tribe, by key ({@link #key}). Saved. */
	static final AttachmentType<Map<String, Tribe>> TRIBES = AttachmentRegistry.create(JaymosKingdoms.id("tribes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Tribe.CODEC)));
	/** On the Overworld: the last day the grudges cooled. Saved. */
	static final AttachmentType<Long> COOLED = AttachmentRegistry.create(JaymosKingdoms.id("tribes_cooled"), builder -> builder.persistent(Codec.LONG));
	/** On the Overworld: camps whose store has been given its seat map. Saved. */
	static final AttachmentType<List<Integer>> MAPPED = AttachmentRegistry.create(JaymosKingdoms.id("tribes_mapped"),
		builder -> builder.persistent(Codec.INT.listOf()));

	public static Item TOKEN;

	/** A hunting band out: its tribe, its quarry, when it set out, its members. */
	private record Band(String tribe, UUID quarry, long started, List<UUID> members) {
	}

	private static final Map<UUID, Band> BANDS = new HashMap<>();

	private static final String[] GOBLIN_FIRST = {"Rot", "Grub", "Muck", "Snag", "Bog", "Mold", "Gnash", "Scab", "Wart", "Sludge"};
	private static final String[] GOBLIN_SECOND = {"gut", "tooth", "ear", "nose", "finger", "toe", "belly", "eye"};
	private static final String[] ORC_FIRST = {"Black", "Blood", "Iron", "Skull", "Ash", "Bone", "Grim", "Tusk", "Dread", "Scar"};
	private static final String[] ORC_SECOND = {"maw", "fang", "fist", "hide", "axe", "brand", "spear", "skull"};

	private Tribes() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("truce_token"));
		TOKEN = Registry.register(BuiltInRegistries.ITEM, key, new Token(new Item.Properties().setId(key).stacksTo(1)));
		ServerLivingEntityEvents.AFTER_DEATH.register(Tribes::died);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity.entityTags().contains(HUNTER_TAG) && BANDS.values().stream().noneMatch(b -> b.members().contains(entity.getUUID()))) {
				// (a hunter left from before a restart: the band went home)
				entity.discard();
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % EVERY == 0) {
				tick(server);
			}
		});
	}

	// ---- who belongs to which tribe ----------------------------------------------------------------------------------------

	/** The brood of camp {@code camp} ({@link #GOBLIN}, {@link #ORC}), or null for any other camp. */
	static @Nullable String brood(MinecraftServer server, int camp) {
		return Camps.goblin(server, camp) ? GOBLIN : Camps.orc(server, camp) ? ORC : null;
	}

	/** The tribe of {@code brood} at {@code at}. */
	static String key(String brood, BlockPos at) {
		return brood + "@" + Math.floorDiv(at.getX(), REGION) + "," + Math.floorDiv(at.getZ(), REGION);
	}

	/** Camp {@code camp}'s tribe, or null if it's no goblin village or orc camp (or gone). */
	public static @Nullable String of(MinecraftServer server, int camp) {
		String brood = brood(server, camp);
		Faction f = brood == null ? null : Factions.get(server, camp);
		return f == null ? null : key(brood, f.seat());
	}

	/** The tribe {@code e} belongs to (one of a goblin village's or an orc camp's), or null. */
	public static @Nullable String of(Entity e) {
		MinecraftServer server = e.level().getServer();
		return server != null && e instanceof Worker w && Greenskins.isRole(w.role()) ? of(server, w.factionId()) : null;
	}

	/** The rival brood's tribe in the same country. */
	static String rival(String tribe) {
		return (tribe.startsWith(GOBLIN + "@") ? ORC : GOBLIN) + tribe.substring(tribe.indexOf('@'));
	}

	/** The tribe's standing camps, oldest first (the first is the king's seat). */
	static List<Faction> camps(MinecraftServer server, String tribe) {
		List<Faction> out = new ArrayList<>();
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = f.fallen() ? null : Camps.data(server, f.id());
			if (data != null && data.clearedAt() < 0 && tribe.equals(of(server, f.id()))) {
				out.add(f);
			}
		}
		out.sort(java.util.Comparator.comparingLong(f -> {
			Camps.CampData d = Camps.data(server, f.id());
			return d == null ? Long.MAX_VALUE : d.founded();
		}));
		return out;
	}

	/** The king's seat: the tribe's oldest standing camp, or null if it has none. */
	static @Nullable Faction seat(MinecraftServer server, String tribe) {
		List<Faction> camps = camps(server, tribe);
		return camps.isEmpty() ? null : camps.getFirst();
	}

	/** The tribe's name ("the Rotgut tribe"). */
	static Component name(String tribe) {
		long h = tribe.hashCode() * 0x9E3779B97F4A7C15L;
		boolean goblins = tribe.startsWith(GOBLIN + "@");
		String[] first = goblins ? GOBLIN_FIRST : ORC_FIRST, second = goblins ? GOBLIN_SECOND : ORC_SECOND;
		String word = first[(int) Math.floorMod(h, (long) first.length)] + second[(int) Math.floorMod(h >>> 20, (long) second.length)];
		return Component.translatable("tribe.jaymoskingdoms.name", word);
	}

	// ---- the grudge --------------------------------------------------------------------------------------------------------

	private static Map<String, Tribe> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(TRIBES, Map.of());
	}

	private static Tribe tribe(MinecraftServer server, String tribe) {
		return all(server).getOrDefault(tribe, Tribe.EMPTY);
	}

	private static void put(MinecraftServer server, String key, Tribe tribe) {
		Map<String, Tribe> all = new HashMap<>(all(server));
		all.put(key, tribe);
		Factions.level(server).setAttached(TRIBES, Map.copyOf(all));
	}

	/** {@code player}'s grudge with {@code tribe}. */
	public static int grudge(MinecraftServer server, UUID player, String tribe) {
		return tribe(server, tribe).grudge().getOrDefault(player.toString(), 0);
	}

	/**
	 * {@code player}'s grudge with {@code tribe} moves by {@code by} (kept within {@code -}{@link TribePacts#FAVOUR_MOST} and
	 * {@link #MOST}: (0.7.443) below nothing is the tribe's favour, {@link TribePacts}). A non-human's grudge rises slower.
	 */
	static void add(MinecraftServer server, UUID player, String tribe, int by) {
		if (by > 0 && SwornCaptains.rules(server, tribe, player)) {
			// (0.7.433) your own captain sits its seat
			return;
		}
		if (by > 0) {
			by = TribePacts.grudgeShare(server, player, by);
		}
		Tribe t = tribe(server, tribe);
		int now = Math.max(-TribePacts.FAVOUR_MOST, Math.min(MOST, t.grudge().getOrDefault(player.toString(), 0) + by));
		Map<String, Integer> grudge = new HashMap<>(t.grudge());
		if (now == 0) {
			grudge.remove(player.toString());
		} else {
			grudge.put(player.toString(), now);
		}
		put(server, tribe, new Tribe(Map.copyOf(grudge), t.truce(), t.hunted()));
		TribePacts.favoured(server, tribe, now);
	}

	/** (0.7.443) {@code player}'s grudge with {@code tribe} wiped clean (their favour, if any, kept). */
	static void clear(MinecraftServer server, UUID player, String tribe) {
		int g = grudge(server, player, tribe);
		if (g > 0) {
			add(server, player, tribe, -g);
		}
	}

	/** {@code player} is under truce with {@code tribe}. */
	public static boolean truce(MinecraftServer server, UUID player, String tribe) {
		return Factions.level(server).getGameTime() < tribe(server, tribe).truce().getOrDefault(player.toString(), -1L);
	}

	/** A truce: the grudge wiped clean, and {@link #TRUCE_DAYS} days of peace. */
	static void makeTruce(MinecraftServer server, UUID player, String tribe) {
		clear(server, player, tribe);
		Tribe t = tribe(server, tribe);
		Map<String, Long> truce = new HashMap<>(t.truce());
		truce.put(player.toString(), Factions.level(server).getGameTime() + 24000L * TRUCE_DAYS);
		put(server, tribe, new Tribe(t.grudge(), Map.copyOf(truce), t.hunted()));
	}

	/**
	 * (From {@code McaCompat.foe}) {@code mob}, one of a tribe's, leaves {@code target} be: a player under truce with its
	 * tribe, unless they struck it; and the pedlar's party spares all but whoever struck them ({@link Pedlars#spares}).
	 */
	public static boolean spares(LivingEntity mob, LivingEntity target) {
		if (Pedlars.party(mob)) {
			return Pedlars.spares(mob, target);
		}
		if (!(target instanceof ServerPlayer player) || mob.getLastHurtByMob() == player) {
			return false;
		}
		String tribe = of(mob);
		MinecraftServer server = player.level().getServer();
		// ((0.7.443) and one in its good books)
		return tribe != null && (truce(server, player.getUUID(), tribe) || SwornCaptains.rules(server, tribe, player.getUUID())
			|| grudge(server, player.getUUID(), tribe) <= TribePacts.FRIEND_AT);
	}

	/** Someone died: one of a tribe's at a player's hand raises their grudge (and lowers it with the rival brood's tribe). */
	private static void died(LivingEntity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		String tribe = of(entity);
		if (tribe == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		int by = Pedlars.pedlar(entity) ? Pedlars.GRUDGE : entity instanceof Worker w && Greenskins.chief(w.role()) ? CHIEF : KILL;
		add(server, player.getUUID(), tribe, by);
		add(server, player.getUUID(), rival(tribe), RIVAL);
		if (entity instanceof Worker w) {
			Bestiary.note(player, w.role(), Bestiary.SLAIN);
		}
	}

	/** (From the talk) the mood of {@code tribe} toward {@code player}, as {@code speaker} would put it. */
	static Component mood(ServerPlayer player, String tribe, LivingEntity speaker) {
		MinecraftServer server = player.level().getServer();
		int g = grudge(server, player.getUUID(), tribe);
		String band = g <= TribePacts.ALLY_AT ? "friend" : truce(server, player.getUUID(), tribe) ? "truce" : g >= MOOD_HUNTED ? "hunted" : g >= MOOD_ANGRY ? "angry" : g >= MOOD_WARY ? "wary" : "calm";
		return Component.translatable("dialogue.chat.jaymoskingdoms.tribe.mood." + band + "." + speaker.getRandom().nextInt(3),
			Recollections.nameTo(player, speaker), name(tribe));
	}

	/**
	 * {@code player} may offer tribute to {@code villager}: a tribe's chief or its pedlar, and they bear a grudge, or
	 * (0.7.443) a gift for its favour while it isn't at its most.
	 */
	public static boolean takesTribute(ServerPlayer player, LivingEntity villager) {
		String tribe = of(villager);
		return tribe != null && villager instanceof Worker w && (Greenskins.chief(w.role()) || Pedlars.pedlar(villager))
			&& grudge(player.level().getServer(), player.getUUID(), tribe) > -TribePacts.FAVOUR_MOST;
	}

	/** {@code player} offers tribute to {@code villager}: emeralds from their pack pay the grudge off. What they say. */
	public static Component tribute(ServerPlayer player, LivingEntity villager) {
		String tribe = of(villager);
		MinecraftServer server = player.level().getServer();
		if (tribe == null) {
			return Component.empty();
		}
		int g = grudge(server, player.getUUID(), tribe);
		// ((0.7.443) no grudge left: a gift, toward the tribe's favour)
		boolean gift = g <= 0;
		int want = gift ? TribePacts.GIFT_EMERALDS : (g + TRIBUTE_STEP - 1) / TRIBUTE_STEP * TRIBUTE_EMERALDS;
		int paid = 0;
		var inv = player.getInventory();
		for (int slot = 0; slot < inv.getContainerSize() && paid < want; slot++) {
			ItemStack s = inv.getItem(slot);
			if (s.is(Items.EMERALD)) {
				int take = Math.min(s.getCount(), want - paid);
				s.shrink(take);
				paid += take;
			}
		}
		int steps = paid / TRIBUTE_EMERALDS;
		String line;
		if (gift) {
			if (paid < want) {
				// (short: handed back)
				if (paid > 0) {
					player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, paid), net.minecraft.util.Prediction.SERVER_ONLY);
				}
				line = "none";
			} else {
				add(server, player.getUUID(), tribe, -TribePacts.GIFT_FAVOUR);
				line = "gift";
				player.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.6F);
			}
		} else if (steps <= 0) {
			line = "none";
		} else {
			add(server, player.getUUID(), tribe, -steps * TRIBUTE_STEP);
			line = grudge(server, player.getUUID(), tribe) == 0 ? "paid" : "part";
			player.level().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.6F);
		}
		return Component.translatable("dialogue.chat.jaymoskingdoms.tribe.tribute." + line + "." + villager.getRandom().nextInt(3),
			Recollections.nameTo(player, villager), Component.literal(Integer.toString(want)));
	}

	// ---- every so often: the grudges cool, seat maps go in the stores, hunting bands go out ----------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long day = level.getGameTime() / 24000L;
		Long cooled = level.getAttached(COOLED);
		if (cooled == null || cooled < day) {
			level.setAttached(COOLED, day);
			if (cooled != null) {
				cool(server, (int) Math.min(30, day - cooled));
			}
		}
		maps(level);
		bands(level);
		TribePacts.tick(level);
		Pedlars.tick(level);
	}

	/** Every grudge falls by {@link #DAILY} a day ({@code days} of them). */
	private static void cool(MinecraftServer server, int days) {
		Map<String, Tribe> all = new HashMap<>();
		for (var e : all(server).entrySet()) {
			Map<String, Integer> grudge = new HashMap<>();
			for (var g : e.getValue().grudge().entrySet()) {
				// ((0.7.443) a favour fades too, slower, back toward nothing)
				int left = g.getValue() > 0 ? g.getValue() - DAILY * days : Math.min(0, g.getValue() + TribePacts.FAVOUR_DAILY * days);
				if (left != 0) {
					grudge.put(g.getKey(), left);
				}
			}
			all.put(e.getKey(), new Tribe(Map.copyOf(grudge), e.getValue().truce(), e.getValue().hunted()));
		}
		Factions.level(server).setAttached(TRIBES, Map.copyOf(all));
	}

	/** A map to the king's seat in every chief's store that hasn't one yet (not the king's own). */
	private static void maps(ServerLevel level) {
		MinecraftServer server = level.getServer();
		List<Integer> mapped = level.getAttachedOrElse(MAPPED, List.of());
		Set<Integer> fresh = null;
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			String tribe = camp.fallen() || mapped.contains(camp.id()) ? null : of(server, camp.id());
			Faction seat = tribe == null ? null : seat(server, tribe);
			if (seat == null || seat.id() == camp.id() || !level.isLoaded(camp.seat()) || !Storage.loaded(level, camp)) {
				continue;
			}
			ItemStack left = Storage.put(level, camp, seatMap(level, tribe, seat));
			if (left.isEmpty()) {
				if (fresh == null) {
					fresh = new HashSet<>(mapped);
				}
				fresh.add(camp.id());
			}
		}
		if (fresh != null) {
			level.setAttached(MAPPED, List.copyOf(fresh));
		}
	}

	/** A map with the king's seat of {@code tribe} marked. */
	static ItemStack seatMap(ServerLevel level, String tribe, Faction seat) {
		BlockPos at = seat.seat();
		ItemStack map = MapItem.create(level, at.getX(), at.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, at, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.seat_map", name(tribe)));
		map.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.seat_map.lore"))));
		CompoundTag tag = new CompoundTag();
		tag.putString(TRIBE_DATA, tribe);
		map.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return map;
	}

	/** A truce token of {@code tribe}. */
	static ItemStack token(String tribe) {
		ItemStack stack = new ItemStack(TOKEN);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.truce_token.of", name(tribe)));
		CompoundTag tag = new CompoundTag();
		tag.putString(TRIBE_DATA, tribe);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** Hunting bands: sent after players the tribe hates, in its country; called home when they've hunted long enough. */
	private static void bands(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		for (var it = BANDS.entrySet().iterator(); it.hasNext();) {
			Band band = it.next().getValue();
			ServerPlayer quarry = server.getPlayerList().getPlayer(band.quarry());
			List<PathfinderMob> left = members(level, band);
			if (left.isEmpty() || now - band.started() > HUNT_TICKS || quarry == null || quarry.level() != level) {
				left.forEach(Entity::discard);
				it.remove();
			}
		}
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || BANDS.containsKey(player.getUUID())
				|| Factions.around(server, level, player.blockPosition(), Post.TOWN_RANGE) != null) {
				continue;
			}
			for (var e : all(server).entrySet()) {
				String tribe = e.getKey();
				int g = e.getValue().grudge().getOrDefault(player.getStringUUID(), 0);
				long last = e.getValue().hunted().getOrDefault(player.getStringUUID(), -HUNT_COOLDOWN);
				if (g < HUNT_AT || now - last < HUNT_COOLDOWN || truce(server, player.getUUID(), tribe)) {
					continue;
				}
				Faction from = nearest(server, tribe, player.position());
				if (from == null || from.seat().distToCenterSqr(player.position()) > HUNT_REACH * HUNT_REACH) {
					continue;
				}
				if (hunt(level, tribe, from, player, g)) {
					Tribe t = e.getValue();
					Map<String, Long> hunted = new HashMap<>(t.hunted());
					hunted.put(player.getStringUUID(), now);
					put(server, tribe, new Tribe(t.grudge(), t.truce(), Map.copyOf(hunted)));
					break;
				}
			}
		}
	}

	private static @Nullable Faction nearest(MinecraftServer server, String tribe, Vec3 at) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : camps(server, tribe)) {
			double d = f.seat().distToCenterSqr(at);
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** Musters a hunting band of {@code camp} (of {@code tribe}) out of sight of {@code player}, after them. */
	private static boolean hunt(ServerLevel level, String tribe, Faction camp, ServerPlayer player, int grudge) {
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = HUNT_NEAR + random.nextInt(HUNT_FAR - HUNT_NEAR);
		BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * r, player.getY(), player.getZ() + Math.sin(angle) * r);
		if (!level.isLoaded(column)) {
			return false;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!level.getFluidState(at.below()).isEmpty()) {
			return false;
		}
		boolean goblins = tribe.startsWith(GOBLIN + "@");
		int size = Math.min(HUNT_MAX, HUNT_BASE + grudge / HUNT_PER);
		List<UUID> members = new ArrayList<>();
		// (0.7.431) one of the tribe's captains at its head, if one's free (Captains)
		PathfinderMob captain = Captains.leadBand(level, tribe, camp, at, player.blockPosition());
		if (captain != null) {
			captain.addTag(HUNTER_TAG);
			members.add(captain.getUUID());
			size--;
		}
		for (int i = 0; i < size; i++) {
			int role = goblins ? (i % 3 == 2 ? Goblins.SLINGER : Goblins.GOBLIN) : (i % 3 == 2 ? Orcs.RANGER : Orcs.GRUNT);
			PathfinderMob member = muster(level, camp, role, at.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2), player.blockPosition());
			if (member != null) {
				member.addTag(HUNTER_TAG);
				members.add(member.getUUID());
			}
		}
		if (members.isEmpty()) {
			return false;
		}
		BANDS.put(player.getUUID(), new Band(tribe, player.getUUID(), level.getGameTime(), members));
		Entity first = level.getEntity(members.getFirst());
		if (first != null) {
			level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.6F, goblins ? 1.4F : 0.7F);
		}
		JaymosKingdoms.LOGGER.info("{} sends {} hunters after {}", name(tribe).getString(), members.size(), player.getName().getString());
		return true;
	}

	/** One of {@code camp}'s folk of {@code role}, made at {@code at} (an MCA villager with MCA), keeping to {@code home}. */
	static @Nullable PathfinderMob muster(ServerLevel level, Faction camp, int role, BlockPos column, BlockPos home) {
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		folk.setup(role, camp, home, Raids.RAID_HOME_RADIUS);
		level.addFreshEntity(folk);
		PathfinderMob member = McaCompat.raider(level, folk);
		return member != null ? member : folk;
	}

	private static List<PathfinderMob> members(ServerLevel level, Band band) {
		List<PathfinderMob> out = new ArrayList<>();
		for (UUID id : band.members()) {
			if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive()) {
				out.add(m);
			}
		}
		return out;
	}

	/** {@code mob} is out with a hunting band. */
	static boolean hunter(Entity mob) {
		return mob.entityTags().contains(HUNTER_TAG);
	}

	/**
	 * (From {@code Workers.step}) a hunter between fights: on the trail of their quarry (brain left alone while a foe is
	 * near). True if this was their step.
	 */
	static boolean hunterStep(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (Rulers.target(mob) != null || Rulers.foeNear(level, mob) || McaCompat.busy(mob) || Downed.isDowned(mob)) {
			return false;
		}
		for (Band band : BANDS.values()) {
			if (band.members().contains(mob.getUUID())) {
				ServerPlayer quarry = level.getServer().getPlayerList().getPlayer(band.quarry());
				if (quarry != null && quarry.level() == level) {
					if (mob.tickCount % 40 == 0 || mob.getNavigation().isDone()) {
						mob.getNavigation().moveTo(quarry, 1.1);
					}
					if (mob.tickCount % 200 == 0 && mob.distanceTo(quarry) < 24.0F && mob.getRandom().nextInt(3) == 0) {
						Speech.say(level, mob, Component.translatable("talk.jaymoskingdoms.tribe.hunter." + mob.getRandom().nextInt(4)), Speech.RED, 24.0);
					}
					return true;
				}
			}
		}
		return false;
	}

	// ---- the truce token ------------------------------------------------------------------------------------------------

	/** A truce token: raised in the tribe's sight (used), it buys a truce with them and is gone. */
	static final class Token extends Item {
		Token(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack held = player.getItemInHand(hand);
			if (!(player instanceof ServerPlayer sp)) {
				return InteractionResult.SUCCESS;
			}
			CustomData data = held.get(DataComponents.CUSTOM_DATA);
			String tribe = data == null ? null : data.copyTag().getStringOr(TRIBE_DATA, "");
			if (tribe == null || tribe.isEmpty()) {
				return InteractionResult.PASS;
			}
			boolean seen = !level.getEntitiesOfClass(PathfinderMob.class, sp.getBoundingBox().inflate(16.0), m -> tribe.equals(of(m)) && m.hasLineOfSight(sp))
				.isEmpty();
			if (!seen) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.truce_token.unseen"));
				return InteractionResult.FAIL;
			}
			makeTruce(sp.level().getServer(), sp.getUUID(), tribe);
			held.shrink(1);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst().value(), SoundSource.PLAYERS, 1.0F, 1.2F);
			for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, sp.getBoundingBox().inflate(24.0), m -> tribe.equals(of(m)))) {
				if (m.getTarget() == sp) {
					m.setTarget(null);
				}
			}
			return InteractionResult.SUCCESS;
		}
	}
}
