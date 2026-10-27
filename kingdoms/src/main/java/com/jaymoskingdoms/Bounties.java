package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.125) Bounties. Andy: "Add a bounty board in town for criminal npcs and players. this bounty board should list the
 * npc or player name and their bounty price below. if the bounty is taken give cords to a general location for npcs
 * where you can find them and if downed can be carried back to the kingdom but, if killed can take their head and bring
 * it back to the kingdom for a lesser reward. Bounties will be brought to the king for the rewards. higher bounties are
 * stronger and can have powerful legendary weapons making them a miniboss fight."
 * <ul>
 * <li><b>Who.</b> A player seen doing a medium or heavy crime in a settlement ({@link Theft#commit}): the price grows
 * with each ({@link #PLAYER_MEDIUM}, {@link #PLAYER_HEAVY}). Masked, the bounty is on the mask ({@link Identities}: a
 * costume's name, or a bandit's or pirate's, {@link Bandits}), and whoever is found under it answers for it. A crooked
 * villager seen at a crime ({@code Crooks}, which calls {@link #crime} with its price).</li>
 * <li><b>Where.</b> Each bounty is posted with the realm that holds the settlement (its {@link #court}: a kingdom, or a
 * village's liege). Every settlement puts up a {@link BountyBoard} by its bell ({@link #boards}); right-clicked, it lists
 * its court's bounties: the name, the price, whether they're dangerous. Taking one tells you, roughly, where the target
 * was last seen ({@link #ROUGH} blocks either way): ask the board again for fresher word.</li>
 * <li>(0.7.168) <b>Notices.</b> The board is {@link BountyBoard#WIDTH} by {@link BountyBoard#HEIGHT} blocks with a notice
 * spot on each. A villager of the town walks over with a notice in hand and pins up an open bounty ({@link #post}); a
 * hunter pulls one off, like an item off a frame, to take the bounty and carry the notice ({@link #pull}), and pins it
 * back up to give the hunt up ({@link #pin}).</li>
 * <li><b>Bringing them in.</b> Downed (Jaymo's Powers' Wounds), the target can be carried; set down within
 * {@link #CLAIM_RANGE} of the court's ruler, a word with the ruler pays the bounty in full. A crook is taken to the
 * cells; a player is fined what they carry (up to the price), forgiven their wanted time there, and got back on their
 * feet. Killed, the target leaves a head ({@link #head}) worth {@link #HEAD_SHARE} of the price, handed to the ruler.
 * Either way the hunter earns reputation with the realm.</li>
 * <li><b>Minibosses.</b> A crook with a bounty of {@link #BOSS_PRICE} or more, once someone has taken it, is ready when
 * found: a relic weapon ({@link Relics}, royal from {@link #ROYAL_PRICE}), more health and armour, a boss bar, and it
 * fights whoever hunts it instead of running. Bring it in and its relic is the hunter's too.</li>
 * </ul>
 */
public final class Bounties {
	// ---- tuning ----
	/** A seen medium and heavy crime adds this to a player's bounty (emeralds); the most one can grow to. */
	static final int PLAYER_MEDIUM = 6, PLAYER_HEAVY = 15, MOST = 320;
	/** From this price a crook is a miniboss once hunted; from this its relic is a royal one. */
	static final int BOSS_PRICE = 40, ROYAL_PRICE = 90;
	/** A miniboss's health: this much, and this much per emerald of its price, at most the cap; its armour. */
	static final double BOSS_HEALTH = 60.0, BOSS_HEALTH_PER = 1.5, BOSS_HEALTH_CAP = 240.0, BOSS_ARMOR = 10.0;
	/** A miniboss's blow: this much, and one more per this many emeralds, at most the cap; how often, how far it reaches. */
	static final float BOSS_DAMAGE = 6.0F, BOSS_DAMAGE_CAP = 16.0F;
	static final int BOSS_DAMAGE_PER = 25, BOSS_SWING_TICKS = 20;
	static final double BOSS_REACH = 2.6, BOSS_SIGHT = 24.0, BOSS_BAR_RANGE = 40.0;
	static final float BOSS_SPEED = 0.7F;
	/** How far off the coordinates a taken bounty gives may be (blocks, each way), rounded to this. */
	static final int ROUGH = 40, ROUND = 10;
	/** A downed target this close to the court's ruler can be claimed. */
	static final double CLAIM_RANGE = 8.0;
	/** A head pays this share of the price (rounded up). */
	static final float HEAD_SHARE = 0.5F;
	/** Reputation the hunter earns with the court: one per this many emeralds, at least the floor. */
	static final int REP_PER = 4, REP_MIN = 3;
	/** A bounty no one has acted on is taken down after this long; a taken one is open to others again after this (ticks). */
	static final long EXPIRE = 24000L * 10, TAKEN_FOR = 24000L * 3;
	/** A hunter may hold this many bounties at once. */
	static final int MAX_TAKEN = 3;
	/** How often the boards are checked and targets' last spots noted (ticks); how long a missing board is left down. */
	static final int EVERY = 100;
	static final long BOARD_REBUILD = 24000L * 3;
	/** Where a settlement's board goes: this far from its bell (blocks). */
	static final int BOARD_NEAR = 3, BOARD_FAR = 8;
	/**
	 * (0.7.197) How far ahead of a board, and how far to either side of its middle, the open ground in front of it is
	 * counted (blocks); what facing the bell is worth in those blocks; and a board in town showing less open ground than
	 * this is looked over once per server run for a better spot.
	 */
	static final int BOARD_VIEW = 8, BOARD_VIEW_SIDE = 2, BOARD_BELL_BONUS = 4, BOARD_OPEN_ENOUGH = 20;
	/** (0.7.197) A board already up is only moved for a spot that sees at least this many more open blocks. */
	static final int BOARD_TURN_GAIN = 6;
	/** The custom data key on a bounty head (the bounty it's for). */
	static final String HEAD_KEY = "jaymoskingdoms_bounty";
	/** (0.7.168) The custom data key on a bounty notice (the bounty it's for). */
	static final String NOTICE_KEY = "jaymoskingdoms_notice";
	/** (0.7.168) Each check ({@link #EVERY}), a board with a bare spot and an open bounty not up yet gets a villager to post it at this chance. */
	static final float POST_CHANCE = 0.3F;
	/** The villager comes from this near the board (blocks), walks at this speed, pins within this reach, gives up after this (ticks). */
	static final double POST_RANGE = 32.0, POST_REACH = 2.0;
	static final float POST_SPEED = 0.55F;
	static final long POST_TIMEOUT = 20L * 40;
	/** How often a posting villager is steered (ticks); the chance they call out the new bounty. */
	static final int POST_STEER = 5;
	static final float POST_SAY_CHANCE = 0.5F;

	/** One bounty: who (a villager's UUID, or a player's or a mask's stand-in), shown as; for which court; the price; where last seen; on what. */
	record Bounty(String target, boolean player, boolean masked, String name, int court, int price, BlockPos last, String crime, long posted,
		Optional<String> hunter, long taken, boolean dead, boolean boss) {
		static final Codec<Bounty> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("target").forGetter(Bounty::target),
				Codec.BOOL.optionalFieldOf("player", false).forGetter(Bounty::player),
				Codec.BOOL.optionalFieldOf("masked", false).forGetter(Bounty::masked),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Bounty::name),
				Codec.INT.fieldOf("court").forGetter(Bounty::court),
				Codec.INT.fieldOf("price").forGetter(Bounty::price),
				BlockPos.CODEC.fieldOf("last").forGetter(Bounty::last),
				Codec.STRING.optionalFieldOf("crime", "theft").forGetter(Bounty::crime),
				Codec.LONG.fieldOf("posted").forGetter(Bounty::posted),
				Codec.STRING.optionalFieldOf("hunter").forGetter(Bounty::hunter),
				Codec.LONG.optionalFieldOf("taken", 0L).forGetter(Bounty::taken),
				Codec.BOOL.optionalFieldOf("dead", false).forGetter(Bounty::dead),
				Codec.BOOL.optionalFieldOf("boss", false).forGetter(Bounty::boss))
			.apply(i, Bounty::new));

		String key() {
			return Bounties.key(court, target);
		}

		Bounty seen(BlockPos at) {
			return new Bounty(target, player, masked, name, court, price, at.immutable(), crime, posted, hunter, taken, dead, boss);
		}

		Bounty hunted(Optional<String> by, long at) {
			return new Bounty(target, player, masked, name, court, price, last, crime, posted, by, at, dead, boss);
		}

		Bounty slain(long at) {
			return new Bounty(target, player, masked, name, court, price, last, crime, at, hunter, taken, true, boss);
		}

		boolean takenBy(ServerPlayer p, long now) {
			return hunter.isPresent() && hunter.get().equals(p.getStringUUID()) && now - taken < TAKEN_FOR;
		}

		boolean takenByAnyone(long now) {
			return hunter.isPresent() && now - taken < TAKEN_FOR;
		}

		boolean takenByOther(ServerPlayer p, long now) {
			return hunter.isPresent() && !hunter.get().equals(p.getStringUUID()) && now - taken < TAKEN_FOR;
		}
	}

	/** Every bounty posted (court/target → bounty), kept on the Overworld. */
	static final AttachmentType<Map<String, Bounty>> ALL = AttachmentRegistry.create(JaymosKingdoms.id("bounties"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Bounty.CODEC)));
	/** Each settlement's board (faction id → where it was put up, and when), kept on the Overworld. */
	static final AttachmentType<Map<String, BlockPos>> BOARDS = AttachmentRegistry.create(JaymosKingdoms.id("bounty_boards"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, BlockPos.CODEC)));
	static final AttachmentType<Map<String, Long>> BOARDS_AT = AttachmentRegistry.create(JaymosKingdoms.id("bounty_boards_at"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** (0.7.168) On each level: the notices pinned to boards (a board block's {@link BlockPos#asLong} → the bounty's key). */
	static final AttachmentType<Map<String, String>> PINNED = AttachmentRegistry.create(JaymosKingdoms.id("bounty_notices"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));
	/** (0.7.168) On each level: every board put up (its bottom middle block), whoever built it, for villagers to post to. */
	static final AttachmentType<List<BlockPos>> BOARD_SPOTS = AttachmentRegistry.create(JaymosKingdoms.id("bounty_board_spots"),
		builder -> builder.persistent(BlockPos.CODEC.listOf()));
	/** On a crook made a miniboss: it has been armed (saved, so it isn't armed twice). */
	static final AttachmentType<Boolean> ARMED = AttachmentRegistry.create(JaymosKingdoms.id("bounty_armed"),
		builder -> builder.persistent(Codec.BOOL));

	static Block BOARD;
	static Item BOARD_ITEM;
	/** (0.7.168) A wanted notice off the board: which bounty, who, the price, where they were seen. */
	static Item NOTICE_ITEM;

	/** A villager on their way to pin {@code key} up at {@code slot} of the board at {@code anchor}, until {@code until}. */
	private record Errand(UUID villager, BlockPos anchor, BlockPos slot, String key, long until) {
	}

	/** Villager → the notice they're taking to the board. */
	private static final Map<UUID, Errand> ERRANDS = new HashMap<>();

	/** Player → the board they're reading. */
	private static final Map<UUID, BlockPos> READING = new HashMap<>();
	/** Miniboss → its bar; → when it may strike next. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Map<UUID, Long> SWING = new HashMap<>();

	private Bounties() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("bounty_board"));
		BOARD = Registry.register(BuiltInRegistries.BLOCK, key, new BountyBoard(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion().pushReaction(PushReaction.IMMOVEABLE).setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bounty_board"));
		BOARD_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BOARD, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(BOARD_ITEM));
		ResourceKey<Item> noticeKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bounty_notice"));
		NOTICE_ITEM = Registry.register(BuiltInRegistries.ITEM, noticeKey, new Item(new Item.Properties().setId(noticeKey).stacksTo(1)));
		TownNotices.register();
		// (0.7.168) a punched notice comes off the board, like an item off a frame, and the board stays
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			BlockState state = world.getBlockState(pos);
			if (player.isSpectator() || !state.is(BOARD) || !BountyBoard.pinned(state)) {
				return InteractionResult.PASS;
			}
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp) {
				pull(level, sp, pos);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Bounties::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Bounties::died);
		// (0.7.126) crooks seen at a crime are posted; one the court has sentenced is off the board
		Crooks.SEEN.add(Bounties::crime);
		Arrests.SENTENCED.add(Bounties::sentenced);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			return claim(level, sp, entity) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READING.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel level = Factions.level(server);
			for (Errand e : ERRANDS.values()) {
				if (level.getEntity(e.villager()) instanceof Villager v) {
					emptyHand(v);
				}
			}
			ERRANDS.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ERRANDS.clear();
			READING.clear();
			BARS.clear();
			SWING.clear();
		});
	}

	// ---- the store -----------------------------------------------------------------------------------------------------

	static String key(int court, String target) {
		return court + "/" + target;
	}

	static Map<String, Bounty> all(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ALL, Map.of());
	}

	private static void put(MinecraftServer server, Bounty b) {
		Map<String, Bounty> map = new HashMap<>(all(server));
		map.put(b.key(), b);
		server.overworld().setAttached(ALL, Map.copyOf(map));
	}

	private static void remove(MinecraftServer server, String key) {
		Map<String, Bounty> map = new HashMap<>(all(server));
		if (map.remove(key) != null) {
			server.overworld().setAttached(ALL, Map.copyOf(map));
		}
	}

	/**
	 * The realm that answers for {@code f}'s law: a kingdom or fiefdom itself, a village its liege's (when it has one),
	 * else the village; null for a camp.
	 */
	static @Nullable Faction court(MinecraftServer server, @Nullable Faction f) {
		if (f == null || f.type() == FactionType.BARBARIAN) {
			return null;
		}
		if (f.type().isRealm()) {
			return f;
		}
		Faction liege = Relations.overlord(server, f);
		return liege != null && liege.type().isRealm() && !liege.fallen() ? liege : f;
	}

	// ---- posting ---------------------------------------------------------------------------------------------------------

	/**
	 * A crook was seen at a crime against {@code faction}: a bounty of {@code emeralds} on them there (the higher of it and
	 * what's on them already). {@code Crooks} calls this for every crime of a crook's that someone sees.
	 */
	/** (0.7.126, from {@link Arrests}) The court has sentenced {@code crook}: their bounties are off the board (an executed one leaves no head to claim). */
	public static void sentenced(LivingEntity crook, int faction, String sentence) {
		if (!(crook.level() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		Map<String, Bounty> map = new HashMap<>(all(server));
		if (map.values().removeIf(b -> b.target().equals(crook.getStringUUID()))) {
			server.overworld().setAttached(ALL, Map.copyOf(map));
		}
	}

	public static void crime(LivingEntity crook, int faction, int emeralds) {
		if (!(crook.level() instanceof ServerLevel level) || emeralds <= 0) {
			return;
		}
		MinecraftServer server = level.getServer();
		Faction court = court(server, Factions.get(server, faction));
		if (court == null) {
			return;
		}
		String target = crook.getStringUUID();
		Bounty old = all(server).get(key(court.id(), target));
		int price = Math.min(MOST, Math.max(emeralds, old == null || old.dead() ? 0 : old.price()));
		long now = level.getGameTime();
		Bounty b = old == null || old.dead()
			? new Bounty(target, false, false, crook.getDisplayName().getString(), court.id(), price, crook.blockPosition(), "crook", now,
				Optional.empty(), 0L, false, price >= BOSS_PRICE)
			: new Bounty(target, false, false, crook.getDisplayName().getString(), court.id(), price, crook.blockPosition(), old.crime(), now,
				old.hunter(), old.taken(), false, old.boss() || price >= BOSS_PRICE);
		put(server, b);
	}

	/** (Theft) {@code player} was seen at a medium or heavy crime ({@code cry}: its kind) against {@code f}: their bounty there grows. */
	static void crimeBy(ServerPlayer player, ServerLevel level, Faction f, boolean heavy, String cry) {
		MinecraftServer server = level.getServer();
		Faction court = court(server, f);
		if (court == null) {
			return;
		}
		// (masked, the bounty is on the mask; Identities works out whose face is under it)
		String persona = Identities.persona(player, f.id());
		String target = Identities.suspectId(player, f.id()).toString();
		Component shown = Identities.nameTo(player, f.id());
		Bounty old = all(server).get(key(court.id(), target));
		int add = heavy ? PLAYER_HEAVY : PLAYER_MEDIUM;
		int price = Math.min(MOST, (old == null || old.dead() ? 0 : old.price()) + add);
		long now = level.getGameTime();
		Optional<String> hunter = old == null || old.dead() ? Optional.empty() : old.hunter();
		long taken = old == null || old.dead() ? 0L : old.taken();
		put(server, new Bounty(target, true, persona != null, shown.getString(), court.id(), price, player.blockPosition(), cry, now, hunter, taken, false, false));
		if (old == null || old.dead()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.posted", court.name(), shown, price).withStyle(ChatFormatting.DARK_RED));
		}
	}

	// ---- whose bounty is whose -----------------------------------------------------------------------------------------

	/** Every target id {@code player} answers for: their own, their bandit mask's, the mask they wear, and any mask known to be theirs. */
	static Set<String> targets(ServerPlayer player) {
		Set<String> out = new HashSet<>();
		out.add(player.getStringUUID());
		out.add(Identities.standIn(Bandits.key(player)).toString());
		String worn = Identities.key(player);
		if (worn != null) {
			out.add(Identities.standIn(worn).toString());
		}
		for (var e : Identities.all(player.level().getServer()).entrySet()) {
			if (e.getValue().links().containsValue(player.getStringUUID())) {
				out.add(Identities.standIn(e.getKey()).toString());
			}
		}
		return out;
	}

	/** The bounty {@code body} answers for with {@code court}, or null. */
	private static @Nullable Bounty on(MinecraftServer server, LivingEntity body, int court) {
		Map<String, Bounty> all = all(server);
		if (body instanceof ServerPlayer p) {
			for (String t : targets(p)) {
				Bounty b = all.get(key(court, t));
				if (b != null && !b.dead()) {
					return b;
				}
			}
			return null;
		}
		Bounty b = all.get(key(court, body.getStringUUID()));
		return b != null && !b.dead() ? b : null;
	}

	/** Every live bounty on {@code body}, with any court. */
	private static List<Bounty> onAnywhere(MinecraftServer server, LivingEntity body) {
		Set<String> ids = body instanceof ServerPlayer p ? targets(p) : Set.of(body.getStringUUID());
		List<Bounty> out = new ArrayList<>();
		for (Bounty b : all(server).values()) {
			if (!b.dead() && ids.contains(b.target())) {
				out.add(b);
			}
		}
		return out;
	}

	// ---- the board -------------------------------------------------------------------------------------------------------

	/** {@code player} reads the board at {@code pos}: its court's bounties, on the quest screen. */
	static void open(ServerLevel level, ServerPlayer player, BlockPos pos) {
		MinecraftServer server = level.getServer();
		Faction court = courtAt(level, pos);
		Component title = Component.translatable("bounty.jaymoskingdoms.board", court == null ? Component.translatable("bounty.jaymoskingdoms.nowhere") : court.name());
		READING.put(player.getUUID(), pos.immutable());
		if (court == null) {
			send(player, new QuestNet.Open(QuestNet.BOARD, title, List.of(), Component.translatable("bounty.jaymoskingdoms.no_court")));
			return;
		}
		long now = level.getGameTime();
		List<Bounty> list = new ArrayList<>();
		for (Bounty b : all(server).values()) {
			if (b.court() == court.id()) {
				list.add(b);
			}
		}
		list.sort(Comparator.comparingInt(Bounty::price).reversed());
		List<QuestNet.View> views = new ArrayList<>();
		for (Bounty b : list) {
			views.add(view(level, player, court, b, now));
		}
		send(player, new QuestNet.Open(QuestNet.BOARD, title, views, Component.translatable("bounty.jaymoskingdoms.none", court.name())));
	}

	/** The court whose bounties the board at {@code pos} shows (the realm whose land it's on, or the nearest), or null. */
	static @Nullable Faction courtAt(ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		Faction here = Factions.at(server, level, pos);
		if (here == null) {
			here = Factions.near(server, pos, 96.0);
		}
		return court(server, here);
	}

	private static QuestNet.View view(ServerLevel level, ServerPlayer player, Faction court, Bounty b, long now) {
		Component name = Component.literal(b.name());
		Component title = Component.translatable(b.dead() ? "bounty.jaymoskingdoms.title.dead" : b.masked() ? "bounty.jaymoskingdoms.title.masked"
			: "bounty.jaymoskingdoms.title", name, b.price());
		int head = headPrice(b);
		Component ruler = rulerName(level, court);
		Component text = Component.translatable("bounty.jaymoskingdoms.text." + (b.player() ? "player" : "crook"), name,
				Component.translatable("bounty.jaymoskingdoms.crime." + b.crime()), ruler, b.price(), head)
			.append(b.boss() ? Component.literal(" ").append(Component.translatable("bounty.jaymoskingdoms.text.boss")) : Component.empty());
		Component objective;
		if (b.dead()) {
			objective = Component.translatable("bounty.jaymoskingdoms.objective.dead", ruler, head);
		} else if (b.takenBy(player, now)) {
			objective = where(level, b, player.getUUID());
		} else if (b.takenByOther(player, now)) {
			String hunter = b.hunter().orElse("");
			ServerPlayer h = level.getServer().getPlayerList().getPlayer(UUID.fromString(hunter));
			objective = Component.translatable("bounty.jaymoskingdoms.objective.taken", h == null ? Component.translatable("bounty.jaymoskingdoms.someone")
				: Identities.shownName(h));
		} else {
			objective = Component.translatable("bounty.jaymoskingdoms.objective.offer");
		}
		List<ItemStack> rewards = new ArrayList<>();
		for (int left = b.dead() ? head : b.price(); left > 0 && rewards.size() < 5; left -= 64) {
			rewards.add(new ItemStack(Items.EMERALD, Math.min(64, left)));
		}
		List<Component> extras = new ArrayList<>();
		if (!b.dead()) {
			extras.add(Component.translatable("bounty.jaymoskingdoms.extra.head", head));
		}
		int state = b.takenBy(player, now) || b.dead() ? QuestNet.ACTIVE : QuestNet.OFFER;
		return new QuestNet.View(b.key(), state, b.boss(), title, text, objective, rewards, extras);
	}

	/** Roughly where {@code b}'s target was last seen (the same rough spot for the same hunter and spot). */
	private static Component where(ServerLevel level, Bounty b, UUID hunter) {
		BlockPos at = b.last();
		if (b.player()) {
			ServerPlayer p = target(level.getServer(), b);
			if (p != null) {
				if (p.level() != Factions.level(level.getServer())) {
					return Component.translatable("bounty.jaymoskingdoms.objective.away");
				}
				at = p.blockPosition();
			}
		}
		RandomSource r = RandomSource.create(hunter.getLeastSignificantBits() ^ at.asLong() ^ b.target().hashCode());
		int x = Math.round((at.getX() + r.nextInt(ROUGH * 2 + 1) - ROUGH) / (float)ROUND) * ROUND;
		int z = Math.round((at.getZ() + r.nextInt(ROUGH * 2 + 1) - ROUGH) / (float)ROUND) * ROUND;
		return Component.translatable("bounty.jaymoskingdoms.objective.where", x, z);
	}

	/** The online player a player bounty is on (wearing the mask it's on, or known to), or null. */
	private static @Nullable ServerPlayer target(MinecraftServer server, Bounty b) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (targets(p).contains(b.target())) {
				return p;
			}
		}
		return null;
	}

	private static Component rulerName(ServerLevel level, Faction court) {
		Entity ruler = court.leader().map(id -> level.getServer().overworld().getEntity(id)).orElse(null);
		return ruler != null ? ruler.getDisplayName() : Component.translatable("bounty.jaymoskingdoms.ruler", court.name());
	}

	private static int headPrice(Bounty b) {
		return Mth.ceil(b.price() * HEAD_SHARE);
	}

	private static void send(ServerPlayer player, QuestNet.Open open) {
		if (ServerPlayNetworking.canSend(player, QuestNet.Open.TYPE)) {
			ServerPlayNetworking.send(player, open);
		}
	}

	/** (Quests) A choice on the board's screen: take a bounty, or give it up. */
	static void act(ServerPlayer player, QuestNet.Act act) {
		BlockPos at = READING.get(player.getUUID());
		if (act.action() == QuestNet.CLOSE) {
			READING.remove(player.getUUID());
			return;
		}
		if (at == null || !(player.level() instanceof ServerLevel level) || player.distanceToSqr(Vec3.atCenterOf(at)) > 64.0) {
			return;
		}
		MinecraftServer server = level.getServer();
		Bounty b = all(server).get(act.questId());
		long now = level.getGameTime();
		if (b != null && act.action() == QuestNet.ACCEPT && !b.dead()) {
			take(level, player, b, at);
		} else if (b != null && act.action() == QuestNet.ABANDON && b.takenBy(player, now)) {
			put(server, b.hunted(Optional.empty(), 0L));
			tearUp(player, b.key());
		}
		open(level, player, at);
	}

	/**
	 * {@code player} takes {@code b} (from the list, or pulling its notice off the board at {@code at}): they're told
	 * roughly where the target was seen and handed the notice, and it comes off any board it was pinned to. False if
	 * they can't (it's on them, someone else has it, or their hands are full of bounties).
	 */
	private static boolean take(ServerLevel level, ServerPlayer player, Bounty b, BlockPos at) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		if (targets(player).contains(b.target())) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.yourself").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (b.takenByOther(player, now)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.taken").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (all(server).values().stream().filter(o -> !o.key().equals(b.key()) && o.takenBy(player, now) && !o.dead()).count() >= MAX_TAKEN) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.full", MAX_TAKEN).withStyle(ChatFormatting.GRAY));
			return false;
		}
		Bounty taken = b.hunted(Optional.of(player.getStringUUID()), now);
		put(server, taken);
		unpinAll(level, b.key());
		tearUp(player, b.key());
		give(player, notice(level, taken, player.getUUID()));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.took", Component.literal(b.name()), b.price())
			.withStyle(ChatFormatting.GOLD));
		player.sendSystemMessage(where(level, taken, player.getUUID()).copy().withStyle(ChatFormatting.YELLOW));
		level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.9F);
		return true;
	}

	// ---- (0.7.168) notices on the board ----------------------------------------------------------------------------------

	private static String spot(BlockPos pos) {
		return Long.toString(pos.asLong());
	}

	static Map<String, String> pinned(ServerLevel level) {
		return level.getAttachedOrElse(PINNED, Map.of());
	}

	/** The notice for {@code b}: named for the target, with the price and the crime, and (for a hunter) where they were seen. */
	static ItemStack notice(ServerLevel level, Bounty b, @Nullable UUID hunter) {
		ItemStack stack = new ItemStack(NOTICE_ITEM);
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.bounty_notice.of", Component.literal(b.name()))
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.RED)));
		List<Component> lore = new ArrayList<>();
		lore.add(Component.translatable("bounty.jaymoskingdoms.notice.price", b.price(), headPrice(b)));
		lore.add(Component.translatable("bounty.jaymoskingdoms.notice.crime", Component.translatable("bounty.jaymoskingdoms.crime." + b.crime())));
		if (hunter != null) {
			lore.add(where(level, b, hunter));
		}
		stack.set(DataComponents.LORE, new ItemLore(lore.stream()
			.map(c -> (Component)c.copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY))).toList()));
		CompoundTag tag = new CompoundTag();
		tag.putString(NOTICE_KEY, b.key());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	static @Nullable String noticeOf(ItemStack stack) {
		CustomData data = stack.is(NOTICE_ITEM) || stack.is(TownNotices.ITEM) ? stack.get(DataComponents.CUSTOM_DATA) : null;
		return data == null ? null : data.copyTag().getString(NOTICE_KEY).orElse(null);
	}

	/** The notices for {@code key} in {@code player}'s pack are gone (the bounty given up, paid, or taken afresh). */
	private static void tearUp(ServerPlayer player, String key) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (key.equals(noticeOf(inv.getItem(i)))) {
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
	}

	/** Pins {@code key}'s notice on the board block at {@code pos}. */
	private static void setNotice(ServerLevel level, BlockPos pos, String key) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BOARD)) {
			return;
		}
		Map<String, String> map = new HashMap<>(pinned(level));
		map.put(spot(pos), key);
		level.setAttached(PINNED, Map.copyOf(map));
		level.setBlock(pos, state.setValue(BountyBoard.NOTICE, TownNotices.is(key) || MerchantOrders.is(key) ? BountyBoard.TOWN : BountyBoard.WANTED), Block.UPDATE_CLIENTS);
	}

	/** Whatever notice is on the board block at {@code pos} comes off. */
	private static void unpin(ServerLevel level, BlockPos pos) {
		Map<String, String> map = pinned(level);
		if (map.containsKey(spot(pos))) {
			Map<String, String> m2 = new HashMap<>(map);
			m2.remove(spot(pos));
			level.setAttached(PINNED, Map.copyOf(m2));
		}
		BlockState state = level.getBlockState(pos);
		if (state.is(BOARD) && BountyBoard.pinned(state)) {
			level.setBlock(pos, state.setValue(BountyBoard.NOTICE, BountyBoard.BARE), Block.UPDATE_CLIENTS);
		}
	}

	/** {@code key}'s notice comes off every board it's on. */
	private static void unpinAll(ServerLevel level, String key) {
		for (var e : List.copyOf(pinned(level).entrySet())) {
			if (e.getValue().equals(key)) {
				unpin(level, BlockPos.of(Long.parseLong(e.getKey())));
			}
		}
	}

	/** (BountyBoard) {@code player} pulls the notice off the board block at {@code pos}: they take its bounty. */
	static void pull(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BOARD) || !BountyBoard.pinned(state)) {
			return;
		}
		String key = pinned(level).get(spot(pos));
		if (key != null && MerchantOrders.is(key)) {
			// (0.7.256) the merchant orders sheet stays up: it's read
			MerchantOrders.open(level, player, BountyBoard.anchor(pos, state));
			level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
			return;
		}
		if (key != null && TownNotices.is(key)) {
			if (TownNotices.pull(level, player, key)) {
				unpin(level, pos);
				level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
			}
			return;
		}
		Bounty b = key == null ? null : all(level.getServer()).get(key);
		if (b == null || b.dead()) {
			// (out of date: it comes down for nothing)
			unpin(level, pos);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.stale").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (take(level, player, b, pos)) {
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
	}

	/** (BountyBoard) {@code player} pins the notice {@code stack} on the bare board block at {@code pos}: they give its hunt up. */
	static void pin(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack stack) {
		MinecraftServer server = level.getServer();
		String key = noticeOf(stack);
		if (key != null && TownNotices.is(key)) {
			// (a town notice goes up again on any board, for whoever wants to read it)
			stack.shrink(1);
			setNotice(level, pos, key);
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
			return;
		}
		Bounty b = key == null ? null : all(server).get(key);
		long now = level.getGameTime();
		if (b == null || b.dead()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.stale_notice").withStyle(ChatFormatting.GRAY));
			return;
		}
		Faction court = courtAt(level, pos);
		if (court == null || court.id() != b.court()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.not_here").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (b.takenByOther(player, now)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.taken").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (b.takenBy(player, now)) {
			put(server, b.hunted(Optional.empty(), 0L));
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.gave_up", Component.literal(b.name())).withStyle(ChatFormatting.GRAY));
		}
		stack.shrink(1);
		unpinAll(level, key);
		setNotice(level, pos, key);
		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
	}

	/** (BountyBoard) A board went up at {@code anchor}: villagers will post to it. */
	static void boardUp(ServerLevel level, BlockPos anchor) {
		List<BlockPos> spots = level.getAttachedOrElse(BOARD_SPOTS, List.of());
		if (!spots.contains(anchor)) {
			List<BlockPos> s2 = new ArrayList<>(spots);
			s2.add(anchor.immutable());
			level.setAttached(BOARD_SPOTS, List.copyOf(s2));
		}
	}

	// ---- bringing them in ----------------------------------------------------------------------------------------------

	/**
	 * {@code player} speaks to {@code entity}: if it rules a court with a bounty {@code player} can claim (a downed target
	 * lying near, or the target's head in hand), it's paid. False if there was nothing to claim (the click goes on as usual).
	 */
	static boolean claim(ServerLevel level, ServerPlayer player, Entity entity) {
		if (!(entity instanceof LivingEntity ruler) || !ruler.isAlive()) {
			return false;
		}
		MinecraftServer server = level.getServer();
		Faction court = null;
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.leader().isPresent() && f.leader().get().equals(ruler.getUUID()) && court(server, f) == f) {
				court = f;
				break;
			}
		}
		if (court == null) {
			return false;
		}
		ItemStack held = player.getMainHandItem();
		// (0.7.147) Hornkin horns, a bounty on raiders from the Nether
		if (held.is(com.jaymoskingdoms.nether.NetherTrophies.HORNKIN_HORN)) {
			int horns = held.getCount();
			int emeralds = horns * com.jaymoskingdoms.nether.NetherTrophies.HORN_BOUNTY;
			held.shrink(horns);
			for (int left = emeralds; left > 0; left -= 64) {
				give(player, new ItemStack(Items.EMERALD, Math.min(64, left)));
			}
			Reputation.change(player, court, Math.max(1, horns / 4));
			Speech.tell(level, ruler, player, Component.translatable("talk.jaymoskingdoms.bounty.horns", horns, emeralds), Speech.GOLD);
			level.playSound(null, ruler.getX(), ruler.getY(), ruler.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);
			return true;
		}
		// a head in hand
		CustomData data = held.is(Items.PLAYER_HEAD) ? held.get(DataComponents.CUSTOM_DATA) : null;
		String headOf = data == null ? null : data.copyTag().getString(HEAD_KEY).orElse(null);
		if (headOf != null) {
			Bounty b = all(server).get(headOf);
			if (b != null && b.court() == court.id()) {
				held.shrink(1);
				pay(level, player, ruler, court, b, headPrice(b), "head");
				return true;
			}
		}
		// a downed target brought before them
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, ruler.getBoundingBox().inflate(CLAIM_RANGE),
			e -> e != player && e.isAlive() && Downed.isDowned(e))) {
			Bounty b = on(server, body, court.id());
			if (b == null) {
				continue;
			}
			pay(level, player, ruler, court, b, b.price(), "alive");
			if (body instanceof ServerPlayer target) {
				sentence(level, target, court, b);
			} else {
				// the relic a miniboss fought with is the hunter's
				if (Boolean.TRUE.equals(body.getAttached(ARMED)) && !body.getMainHandItem().isEmpty()) {
					give(player, body.getMainHandItem().copy());
				}
				Speech.say(level, ruler, Component.translatable("talk.jaymoskingdoms.bounty.cells." + level.getRandom().nextInt(3), body.getDisplayName()),
					Speech.GOLD, 16.0);
				forgetBoss(body);
				body.discard();
			}
			return true;
		}
		return false;
	}

	private static void pay(ServerLevel level, ServerPlayer player, LivingEntity ruler, Faction court, Bounty b, int emeralds, String how) {
		remove(level.getServer(), b.key());
		tearUp(player, b.key());
		unpinAll(level, b.key());
		for (int left = emeralds; left > 0; left -= 64) {
			give(player, new ItemStack(Items.EMERALD, Math.min(64, left)));
		}
		Reputation.change(player, court, Math.max(REP_MIN, emeralds / REP_PER));
		Speech.tell(level, ruler, player, Component.translatable("talk.jaymoskingdoms.bounty." + how + "." + level.getRandom().nextInt(3),
			Component.literal(b.name()), emeralds), Speech.GOLD);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.paid", emeralds, Component.literal(b.name()))
			.withStyle(ChatFormatting.GREEN));
		level.playSound(null, ruler.getX(), ruler.getY(), ruler.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.8F);
	}

	/** A player brought in alive: fined what they carry (up to the price), forgiven their wanted time there, got back on their feet. */
	private static void sentence(ServerLevel level, ServerPlayer target, Faction court, Bounty b) {
		int fine = Math.min(b.price(), Purse.count(target, Items.EMERALD));
		if (fine > 0) {
			Purse.take(target, Items.EMERALD, fine);
		}
		Map<String, Long> wanted = new HashMap<>(target.getAttachedOrElse(Theft.WANTED, Map.of()));
		if (wanted.remove(Integer.toString(court.id())) != null) {
			target.setAttached(Theft.WANTED, Map.copyOf(wanted));
		}
		target.addTag(Downed.REVIVE_TAG);
		target.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.sentenced", court.name(), fine).withStyle(ChatFormatting.DARK_RED));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ---- killed: the head ----------------------------------------------------------------------------------------------

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		forgetBoss(entity);
		// (a player's head only when another player killed them, so a bandit's fall off a cliff costs no bounty)
		if (entity instanceof ServerPlayer && !(source.getEntity() instanceof Player)) {
			return;
		}
		MinecraftServer server = level.getServer();
		// (a player's head is the face they died in: the mask's bounties if masked, their own if not)
		String worn = entity instanceof ServerPlayer p ? Identities.key(p) : null;
		String face = worn != null ? Identities.standIn(worn).toString() : entity.getStringUUID();
		for (Bounty b : onAnywhere(server, entity)) {
			if (!b.target().equals(face)) {
				continue;
			}
			put(server, b.slain(level.getGameTime()));
			entity.spawnAtLocation(level, head(entity, b));
		}
	}

	/** The head that pays for {@code b}: a player's own face, anyone else's a plain head, named for whose it is. */
	static ItemStack head(LivingEntity of, Bounty b) {
		ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
		if (of instanceof ServerPlayer p && !b.masked()) {
			stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(p.getGameProfile()));
		}
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.bounty_head", Component.literal(b.name()))
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.RED)));
		CompoundTag tag = new CompoundTag();
		tag.putString(HEAD_KEY, b.key());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	// ---- the ticking: boards, last seen, minibosses --------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (!BARS.isEmpty()) {
			bosses(level);
		}
		if (!ERRANDS.isEmpty() && server.getTickCount() % POST_STEER == 0) {
			errands(level);
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		long now = level.getGameTime();
		Map<String, Bounty> all = all(server);
		Map<String, Bounty> changed = new HashMap<>(all);
		boolean dirty = false;
		for (Bounty b : all.values()) {
			if (now - b.posted() > (b.dead() ? EXPIRE / 3 : EXPIRE) && !b.takenByAnyone(now)) {
				changed.remove(b.key());
				dirty = true;
				continue;
			}
			if (b.player() || b.dead()) {
				continue;
			}
			Entity e = level.getEntity(UUID.fromString(b.target()));
			if (e instanceof LivingEntity crook && crook.isAlive()) {
				if (crook.blockPosition().distManhattan(b.last()) > 8) {
					changed.put(b.key(), b.seen(crook.blockPosition()));
					dirty = true;
				}
				if (b.boss() && b.hunter().isPresent() && crook instanceof Mob mob) {
					arm(level, mob, b);
				}
			}
		}
		if (dirty) {
			server.overworld().setAttached(ALL, Map.copyOf(changed));
		}
		boards(level, now);
		post(level, now);
	}

	// ---- minibosses ------------------------------------------------------------------------------------------------------

	/** A hunted crook with a big bounty is ready for whoever comes: a relic, health, armour, a bar, and it fights. */
	private static void arm(ServerLevel level, Mob crook, Bounty b) {
		if (!Boolean.TRUE.equals(crook.getAttached(ARMED))) {
			crook.setAttached(ARMED, true);
			RandomSource random = level.getRandom();
			ItemStack relic = ItemStack.EMPTY;
			for (int i = 0; i < 12 && (relic.isEmpty() || relic.is(Items.BOW) || relic.is(Items.CROSSBOW)); i++) {
				relic = Relics.make(level, Relics.roll(random, true, b.price() >= ROYAL_PRICE), crook.getDisplayName().getString());
			}
			if (relic.isEmpty() || relic.is(Items.BOW) || relic.is(Items.CROSSBOW)) {
				relic = new ItemStack(Items.IRON_SWORD);
			}
			crook.setItemSlot(EquipmentSlot.MAINHAND, relic);
			crook.setDropChance(EquipmentSlot.MAINHAND, 2.0F);
			crook.setPersistenceRequired();
			double health = Math.min(BOSS_HEALTH_CAP, BOSS_HEALTH + b.price() * BOSS_HEALTH_PER);
			var max = crook.getAttribute(Attributes.MAX_HEALTH);
			if (max != null) {
				max.setBaseValue(health);
			}
			var armor = crook.getAttribute(Attributes.ARMOR);
			if (armor != null) {
				armor.setBaseValue(BOSS_ARMOR);
			}
			crook.setHealth(crook.getMaxHealth());
		}
		BARS.computeIfAbsent(crook.getUUID(), id -> new ServerBossEvent(id, crook.getDisplayName(), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.NOTCHED_10));
	}

	private static void bosses(ServerLevel level) {
		long now = level.getGameTime();
		for (var it = BARS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			ServerBossEvent bar = e.getValue();
			if (!(level.getEntity(e.getKey()) instanceof Mob boss) || !boss.isAlive()) {
				bar.removeAllPlayers();
				it.remove();
				SWING.remove(e.getKey());
				continue;
			}
			bar.setProgress(boss.getHealth() / boss.getMaxHealth());
			for (ServerPlayer p : List.copyOf(bar.getPlayers())) {
				if (p.level() != level || p.distanceTo(boss) > BOSS_BAR_RANGE) {
					bar.removePlayer(p);
				}
			}
			if (Downed.isDowned(boss)) {
				continue;
			}
			ServerPlayer foe = null;
			double best = BOSS_SIGHT * BOSS_SIGHT;
			for (ServerPlayer p : level.players()) {
				double d = p.distanceToSqr(boss);
				if (d < best && p.isAlive() && !p.isSpectator() && !p.isCreative() && !Downed.isDowned(p) && boss.hasLineOfSight(p)) {
					best = d;
					foe = p;
				}
			}
			if (foe == null) {
				continue;
			}
			bar.addPlayer(foe);
			boss.getLookControl().setLookAt(foe);
			if (boss.distanceTo(foe) <= BOSS_REACH) {
				if (now >= SWING.getOrDefault(boss.getUUID(), 0L)) {
					SWING.put(boss.getUUID(), now + BOSS_SWING_TICKS);
					boss.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
					Bounty b = bountyOn(level.getServer(), boss);
					int price = b == null ? BOSS_PRICE : b.price();
					foe.hurtServer(level, level.damageSources().mobAttack(boss), Math.min(BOSS_DAMAGE_CAP, BOSS_DAMAGE + price / (float)BOSS_DAMAGE_PER));
				}
			} else if (boss instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(foe, true), BOSS_SPEED, 1);
			} else {
				boss.getNavigation().moveTo(foe, 1.1);
			}
		}
	}

	private static @Nullable Bounty bountyOn(MinecraftServer server, LivingEntity crook) {
		for (Bounty b : all(server).values()) {
			if (b.target().equals(crook.getStringUUID()) && !b.dead()) {
				return b;
			}
		}
		return null;
	}

	private static void forgetBoss(LivingEntity e) {
		ServerBossEvent bar = BARS.remove(e.getUUID());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		SWING.remove(e.getUUID());
	}

	// ---- the boards in town ----------------------------------------------------------------------------------------------

	/** Each settlement whose bell is loaded and that has no board (or lost it {@link #BOARD_REBUILD} ago) puts one up by its bell. */
	private static void boards(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		Map<String, BlockPos> boards = level.getAttachedOrElse(BOARDS, Map.of());
		Map<String, Long> when = level.getAttachedOrElse(BOARDS_AT, Map.of());
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			String id = Integer.toString(f.id());
			BlockPos had = boards.get(id);
			Placing best = null;
			if (had != null) {
				if (!level.isLoaded(had)) {
					continue;
				}
				BlockState there = level.getBlockState(had);
				if (there.is(BOARD)) {
					Direction faces = there.getValue(BountyBoard.FACING);
					if (BountyBoard.isAnchor(there) && BountyBoard.complete(level, had, faces)) {
						// (0.7.197) Andy: "the bounty board shouldn't be facing blocks so close like this when there is a wide
						// open square in front of it." A board facing a wall is moved once to a spot that sees clearly more.
						int seen = LOOKED_OVER.add(id) ? openness(level, had, faces) : BOARD_OPEN_ENOUGH;
						if (seen >= BOARD_OPEN_ENOUGH) {
							continue;
						}
						best = boardSpot(level, f.seat());
						if (best == null || openness(level, best.spot(), best.facing()) < seen + BOARD_TURN_GAIN) {
							continue;
						}
						for (int c = 0; c < BountyBoard.WIDTH; c++) {
							for (int r = 0; r < BountyBoard.HEIGHT; r++) {
								level.setBlock(BountyBoard.part(had, faces, c, r), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
							}
						}
					}
					// (0.7.168) a board from before it was several blocks: taken down and put up again whole
					level.removeBlock(had, false);
				} else if (now - when.getOrDefault(id, 0L) < BOARD_REBUILD) {
					continue;
				}
			}
			if (best == null) {
				best = boardSpot(level, f.seat());
			}
			if (best == null) {
				continue;
			}
			BlockPos spot = best.spot();
			Direction facing = best.facing();
			LOOKED_OVER.add(id);
			level.setBlock(spot, BountyBoard.partState(BOARD, facing, 1, 0), Block.UPDATE_ALL);
			BountyBoard.build(level, spot, BOARD, facing);
			Map<String, BlockPos> b2 = new HashMap<>(boards);
			b2.put(id, spot);
			boards = Map.copyOf(b2);
			Map<String, Long> w2 = new HashMap<>(when);
			w2.put(id, now);
			when = Map.copyOf(w2);
			level.setAttached(BOARDS, boards);
			level.setAttached(BOARDS_AT, when);
			JaymosKingdoms.LOGGER.debug("bounty board for {} at {}", f.name(), spot);
		}
	}

	/** The way a board at {@code spot} faces to look at the bell. */
	private static Direction toward(BlockPos bell, BlockPos spot) {
		int dx = bell.getX() - spot.getX(), dz = bell.getZ() - spot.getZ();
		Direction facing = Direction.getApproximateNearest((float)dx, 0.0F, (float)dz);
		return facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
	}

	/** Firm, dry ground that isn't a path or a field. */
	private static boolean firm(ServerLevel level, BlockPos ground) {
		BlockState under = level.getBlockState(ground);
		return under.isFaceSturdy(level, ground, Direction.UP) && under.getFluidState().isEmpty() && !under.is(Blocks.DIRT_PATH)
			&& !under.is(Blocks.FARMLAND) && !BuiltInRegistries.BLOCK.getKey(under.getBlock()).getPath().contains("path");
	}

	/** (0.7.168) The whole board fits at {@code anchor} facing {@code facing}: open air, firm ground under it, room to stand before it. */
	private static boolean fits(ServerLevel level, BlockPos anchor, Direction facing) {
		for (int c = 0; c < BountyBoard.WIDTH; c++) {
			for (int r = 0; r < BountyBoard.HEIGHT; r++) {
				if (!level.getBlockState(BountyBoard.part(anchor, facing, c, r)).isAir()) {
					return false;
				}
			}
			BlockPos front = BountyBoard.part(anchor, facing, c, 0).relative(facing);
			if (!firm(level, BountyBoard.part(anchor, facing, c, 0).below()) || !level.getBlockState(front).isAir()
				|| !level.getBlockState(front.above()).isAir()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * (0.7.197) How much open ground lies before a board at {@code anchor} facing {@code facing}: lanes from
	 * {@link #BOARD_VIEW_SIDE} left to right of its middle, each counted out to {@link #BOARD_VIEW} blocks until a block
	 * a person couldn't walk through stops it.
	 */
	private static int openness(ServerLevel level, BlockPos anchor, Direction facing) {
		Direction side = BountyBoard.right(facing);
		int open = 0;
		for (int lane = -BOARD_VIEW_SIDE; lane <= BOARD_VIEW_SIDE; lane++) {
			BlockPos p = anchor.relative(side, lane);
			for (int d = 1; d <= BOARD_VIEW; d++) {
				p = p.relative(facing);
				if (!clear(level, p) || !clear(level, p.above())) {
					break;
				}
				open++;
			}
		}
		return open;
	}

	private static boolean clear(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
	}

	/** Where a settlement's board goes up, and the way it faces. */
	private record Placing(BlockPos spot, Direction facing) {}

	/** The ids of the settlements whose board was checked for facing the open this server run. */
	private static final Set<String> LOOKED_OVER = new HashSet<>();

	/**
	 * Open ground {@link #BOARD_NEAR}-{@link #BOARD_FAR} blocks from the bell where the whole board fits. (0.7.197) Of
	 * those, the spot and facing with the most open ground before it ({@link #openness}), a little for facing the bell
	 * and less the farther from it; it used to be the nearest spot, always facing the bell, even with a wall right ahead.
	 */
	private static @Nullable Placing boardSpot(ServerLevel level, BlockPos bell) {
		Placing best = null;
		int bestScore = Integer.MIN_VALUE;
		for (int r = BOARD_NEAR; r <= BOARD_FAR; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bell.offset(dx, 0, dz));
					if (Math.abs(top.getY() - bell.getY()) > 3) {
						continue;
					}
					Direction bellward = toward(bell, top);
					for (Direction facing : Direction.Plane.HORIZONTAL) {
						if (!fits(level, top, facing)) {
							continue;
						}
						int score = openness(level, top, facing) + (facing == bellward ? BOARD_BELL_BONUS : 0) - r;
						if (score > bestScore) {
							bestScore = score;
							best = new Placing(top, facing);
						}
					}
				}
			}
		}
		return best;
	}

	// ---- (0.7.168) villagers posting notices ---------------------------------------------------------------------------

	/**
	 * Each board in loaded, ticking land with a bare spot may get a notice: an open bounty of its court that isn't up
	 * anywhere yet, carried over by one of the town's villagers ({@link #errands}). Stale notices come down.
	 */
	private static void post(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		Map<String, Bounty> all = all(server);
		// stale notices: the bounty paid, gone, dead or taken
		for (var e : List.copyOf(pinned(level).entrySet())) {
			BlockPos pos = BlockPos.of(Long.parseLong(e.getKey()));
			if (!level.isLoaded(pos)) {
				continue;
			}
			Bounty b = all.get(e.getValue());
			BlockState state = level.getBlockState(pos);
			boolean over = MerchantOrders.is(e.getValue()) ? state.is(BOARD) && !BountyBoard.anchor(pos, state).equals(MerchantOrders.boardOf(e.getValue()))
				: TownNotices.is(e.getValue()) ? TownNotices.expired(e.getValue(), now) : b == null || b.dead() || b.takenByAnyone(now);
			if (over || !state.is(BOARD) || !BountyBoard.pinned(state)) {
				unpin(level, pos);
			}
		}
		Set<String> up = new HashSet<>(pinned(level).values());
		for (Errand e : ERRANDS.values()) {
			up.add(e.key());
		}
		List<BlockPos> spots = level.getAttachedOrElse(BOARD_SPOTS, List.of());
		List<BlockPos> keep = new ArrayList<>();
		RandomSource random = level.getRandom();
		for (BlockPos anchor : spots) {
			if (!level.isLoaded(anchor)) {
				keep.add(anchor);
				continue;
			}
			BlockState state = level.getBlockState(anchor);
			if (!state.is(BOARD) || !BountyBoard.isAnchor(state)) {
				continue;
			}
			keep.add(anchor);
			if (!level.isPositionEntityTicking(anchor) || level.isDarkOutside() || random.nextFloat() >= POST_CHANCE
				|| ERRANDS.values().stream().anyMatch(e -> e.anchor().equals(anchor))) {
				continue;
			}
			Direction facing = state.getValue(BountyBoard.FACING);
			List<BlockPos> bare = new ArrayList<>();
			int town = 0;
			String sheet = MerchantOrders.sheetKey(anchor);
			for (int c = 0; c < BountyBoard.WIDTH; c++) {
				for (int r = 0; r < BountyBoard.HEIGHT; r++) {
					BlockPos p = BountyBoard.part(anchor, facing, c, r);
					BlockState s = level.getBlockState(p);
					if (s.is(BOARD) && !BountyBoard.pinned(s)) {
						bare.add(p);
					} else if (s.is(BOARD) && s.getValue(BountyBoard.NOTICE) == BountyBoard.TOWN && !sheet.equals(pinned(level).get(spot(p)))) {
						town++;
					}
				}
			}
			Faction court = bare.isEmpty() ? null : courtAt(level, anchor);
			if (court == null) {
				continue;
			}
			// (0.7.256) every town board gets a merchant orders sheet first, and it stays up
			if (!up.contains(sheet) && random.nextFloat() < MerchantOrders.SHEET_CHANCE) {
				Villager clerk = poster(level, anchor);
				if (clerk != null) {
					clerk.setItemSlot(EquipmentSlot.MAINHAND, MerchantOrders.sheet(sheet));
					BlockPos slot = bare.get(random.nextInt(bare.size()));
					ERRANDS.put(clerk.getUUID(), new Errand(clerk.getUUID(), anchor.immutable(), slot.immutable(), sheet, now + POST_TIMEOUT));
					up.add(sheet);
				}
				continue;
			}
			Bounty next = null;
			for (Bounty b : all.values()) {
				if (b.court() == court.id() && !b.dead() && !b.takenByAnyone(now) && !up.contains(b.key()) && (next == null || b.price() > next.price())) {
					next = b;
				}
			}
			// (0.7.168) no bounty to post: now and then a town notice instead, an errand or a bit of news, a few at a time
			boolean news = next == null && town < TownNotices.MOST && random.nextFloat() < TownNotices.CHANCE;
			Villager v = next == null && !news ? null : poster(level, anchor);
			String key = v == null ? null : next != null ? next.key() : TownNotices.pick(v, up, now, random);
			if (key == null) {
				continue;
			}
			v.setItemSlot(EquipmentSlot.MAINHAND, next != null ? notice(level, next, null) : TownNotices.item(level, key));
			BlockPos slot = bare.get(random.nextInt(bare.size()));
			ERRANDS.put(v.getUUID(), new Errand(v.getUUID(), anchor.immutable(), slot.immutable(), key, now + POST_TIMEOUT));
			up.add(key);
		}
		if (keep.size() != spots.size()) {
			level.setAttached(BOARD_SPOTS, List.copyOf(keep));
		}
	}

	/** A grown villager near the board, awake, hands free, not on an errand, not wanted themselves; null if none. */
	private static @Nullable Villager poster(ServerLevel level, BlockPos anchor) {
		MinecraftServer server = level.getServer();
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, new AABB(anchor).inflate(POST_RANGE),
			v -> v.isAlive() && !v.isBaby() && !v.isSleeping() && v.getMainHandItem().isEmpty() && !Downed.isDowned(v)
				&& !ERRANDS.containsKey(v.getUUID()) && bountyOn(server, v) == null);
		return folk.isEmpty() ? null : folk.get(level.getRandom().nextInt(folk.size()));
	}

	/** Steers each posting villager to the board; there, they pin the notice up with a swing of the arm. */
	private static void errands(ServerLevel level) {
		long now = level.getGameTime();
		Map<String, Bounty> all = all(level.getServer());
		for (var it = ERRANDS.values().iterator(); it.hasNext();) {
			Errand e = it.next();
			Villager v = level.getEntity(e.villager()) instanceof Villager found ? found : null;
			BlockState state = level.getBlockState(e.slot());
			Bounty b = all.get(e.key());
			boolean orders = MerchantOrders.is(e.key());
			boolean town = TownNotices.is(e.key()) || orders;
			boolean over = orders ? false : town ? TownNotices.expired(e.key(), now) : b == null || b.dead() || b.takenByAnyone(now);
			if (v == null || !v.isAlive() || now > e.until() || v.isSleeping() || Downed.isDowned(v) || !state.is(BOARD)
				|| BountyBoard.pinned(state) || over || !v.getMainHandItem().is(town ? TownNotices.ITEM : NOTICE_ITEM)) {
				if (v != null) {
					emptyHand(v);
				}
				it.remove();
				continue;
			}
			Vec3 at = Vec3.atCenterOf(e.slot());
			double dx = v.getX() - at.x, dz = v.getZ() - at.z;
			if (dx * dx + dz * dz <= POST_REACH * POST_REACH && Math.abs(v.getY() - e.slot().getY()) < 3.0) {
				v.getLookControl().setLookAt(at);
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				emptyHand(v);
				it.remove();
				setNotice(level, e.slot(), e.key());
				level.playSound(null, e.slot(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
				if (town) {
					if (level.getRandom().nextFloat() < POST_SAY_CHANCE) {
						Speech.say(level, v, orders ? MerchantOrders.callOut(level.getRandom()) : TownNotices.callOut(e.key(), level.getRandom()), Speech.WHITE, 12.0);
					}
				} else if (level.getRandom().nextFloat() < POST_SAY_CHANCE) {
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.bounty.posting." + level.getRandom().nextInt(3),
						Component.literal(b.name()), b.price()), Speech.WHITE, 12.0);
				}
			} else {
				Direction facing = state.getValue(BountyBoard.FACING);
				BlockPos stand = BountyBoard.part(e.anchor(), facing, state.getValue(BountyBoard.COLUMN), 0).relative(facing);
				BehaviorUtils.setWalkAndLookTargetMemories(v, stand, POST_SPEED, 0);
			}
		}
	}

	/** The notice a villager was carrying is put away. */
	private static void emptyHand(Villager v) {
		if (v.getMainHandItem().is(NOTICE_ITEM) || v.getMainHandItem().is(TownNotices.ITEM)) {
			v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
	}
}
