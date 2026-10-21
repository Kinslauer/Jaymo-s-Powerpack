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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
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
	/** The custom data key on a bounty head (the bounty it's for). */
	static final String HEAD_KEY = "jaymoskingdoms_bounty";

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
	/** On a crook made a miniboss: it has been armed (saved, so it isn't armed twice). */
	static final AttachmentType<Boolean> ARMED = AttachmentRegistry.create(JaymosKingdoms.id("bounty_armed"),
		builder -> builder.persistent(Codec.BOOL));

	static Block BOARD;
	static Item BOARD_ITEM;

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
			.strength(2.0F).sound(SoundType.WOOD).ignitedByLava().noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bounty_board"));
		BOARD_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BOARD, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(BOARD_ITEM));
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
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
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
		Faction here = Factions.at(server, level, pos);
		if (here == null) {
			here = Factions.near(server, pos, 96.0);
		}
		Faction court = court(server, here);
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
			if (targets(player).contains(b.target())) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.yourself").withStyle(ChatFormatting.GRAY));
			} else if (b.takenByOther(player, now)) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.taken").withStyle(ChatFormatting.GRAY));
			} else if (all(server).values().stream().filter(o -> o != b && o.takenBy(player, now) && !o.dead()).count() >= MAX_TAKEN) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.full", MAX_TAKEN).withStyle(ChatFormatting.GRAY));
			} else {
				Bounty taken = b.hunted(Optional.of(player.getStringUUID()), now);
				put(server, taken);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bounty.took", Component.literal(b.name()), b.price())
					.withStyle(ChatFormatting.GOLD));
				player.sendSystemMessage(where(level, taken, player.getUUID()).copy().withStyle(ChatFormatting.YELLOW));
				level.playSound(null, at, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.9F);
			}
		} else if (b != null && act.action() == QuestNet.ABANDON && b.takenBy(player, now)) {
			put(server, b.hunted(Optional.empty(), 0L));
		}
		open(level, player, at);
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
		// (0.7.146) Hornkin horns, a bounty on raiders from the Nether
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
			if (had != null && (!level.isLoaded(had) || level.getBlockState(had).is(BOARD) || now - when.getOrDefault(id, 0L) < BOARD_REBUILD)) {
				continue;
			}
			BlockPos spot = boardSpot(level, f.seat());
			if (spot == null) {
				continue;
			}
			int dx = f.seat().getX() - spot.getX(), dz = f.seat().getZ() - spot.getZ();
			Direction facing = Direction.getApproximateNearest((float)dx, 0.0F, (float)dz);
			if (facing.getAxis() == Direction.Axis.Y) {
				facing = Direction.NORTH;
			}
			level.setBlock(spot, BOARD.defaultBlockState().setValue(BountyBoard.FACING, facing), Block.UPDATE_ALL);
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

	/** The nearest open ground {@link #BOARD_NEAR}-{@link #BOARD_FAR} blocks from the bell: firm, dry, not a path or a field. */
	private static @Nullable BlockPos boardSpot(ServerLevel level, BlockPos bell) {
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
					BlockState under = level.getBlockState(top.below());
					if (level.getBlockState(top).isAir() && level.getBlockState(top.above()).isAir() && under.isFaceSturdy(level, top.below(), Direction.UP)
						&& under.getFluidState().isEmpty() && !under.is(Blocks.DIRT_PATH) && !under.is(Blocks.FARMLAND)
						&& !BuiltInRegistries.BLOCK.getKey(under.getBlock()).getPath().contains("path")) {
						return top;
					}
				}
			}
		}
		return null;
	}
}
