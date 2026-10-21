package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.42) Sailing, stage 3: the kingdoms at sea. A coastal realm worth {@link #HARBOUR_TREASURY} emeralds or more keeps
 * a harbour (a pier out from its nearest shore), a {@link Pirates#SHIPWRIGHT} on it, and a warship: a frigate (a
 * brigantine while it's worth less than {@link #FRIGATE_TREASURY}; (0.7.111) now Jaki's war trireme) under its
 * {@link Pirates#SEA_CAPTAIN}, crewed by its guards, flying its colours. She patrols off the harbour and engages any
 * pirate near it ({@link Corsairs}); lost, the shipwright builds another in a while. Merchantmen ((0.7.111) Jaki's
 * cargo ships and traders, in its colours) sail from harbour to harbour with goods (its caravans by sea), and run from
 * pirates; a king may ask a player to see one safely in ({@link Quest#ESCORT}).
 */
final class Navies {
	/** A realm keeps a harbour and a warship from this treasury; a frigate from this. */
	static final long HARBOUR_TREASURY = 1000L, FRIGATE_TREASURY = 2000L;
	/** How often harbours are seen to (ticks), and escort quests watched. */
	static final int CHECK_TICKS = 400, WATCH_TICKS = 40;
	/** A lost warship is built again after this long (ticks: two days); she's missing (not where last seen) after this long. */
	static final long REBUILD_TICKS = 48000L, MISSING_TICKS = 20 * 180;
	/** Merchantmen: one out from a harbour at a time, at most every this long (ticks: a day); forgotten after this long out. */
	static final long MERCHANT_GAP = 24000L, MERCHANT_FORGET = 24000L * 3;
	static final float MERCHANT_CHANCE = 0.35F;
	/** To another harbour this far at most (blocks). */
	static final double MERCHANT_RANGE = 1100.0, MERCHANT_LEAST = 120.0;
	/** What a merchantman's voyage earns her realm and the one she trades with (emeralds), and carries to it. */
	static final long VOYAGE_PROFIT = 40L, VOYAGE_TRADE = 20L;
	/** Ships aren't launched with a player this near their mooring. */
	static final double LAUNCH_HIDE = 28.0;
	/** A warship's crew besides her gunners; a merchantman's. */
	static final int NAVY_HANDS = 4, MERCHANT_HANDS = 2;
	/** An escort: the player must be this near the harbour for her to sail; how far a king's escort may send her. */
	static final double ESCORT_START = 48.0;
	/** Reputation with a realm for sinking its ship, and for seeing its merchantman in. */
	static final int REP_SINK = -150, REP_ESCORT = 25;
	/** A shipwright missing this long (ticks) is replaced. */
	static final long WRIGHT_MISSING = 20 * 240;

	/** A realm's harbour: its pier's land end and sea end, its mooring, its warship and merchantman, its shipwright. */
	record Harbour(BlockPos shore, BlockPos pier, BlockPos mooring, Optional<UUID> warship, Optional<BlockPos> seen, long lostAt,
		Optional<UUID> merchant, long merchantAt, Optional<UUID> wright) {
		static final Codec<Harbour> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("shore").forGetter(Harbour::shore),
				BlockPos.CODEC.fieldOf("pier").forGetter(Harbour::pier),
				BlockPos.CODEC.fieldOf("mooring").forGetter(Harbour::mooring),
				UUIDUtil.CODEC.optionalFieldOf("warship").forGetter(Harbour::warship),
				BlockPos.CODEC.optionalFieldOf("seen").forGetter(Harbour::seen),
				Codec.LONG.optionalFieldOf("lost_at", -1L).forGetter(Harbour::lostAt),
				UUIDUtil.CODEC.optionalFieldOf("merchant").forGetter(Harbour::merchant),
				Codec.LONG.optionalFieldOf("merchant_at", -1L).forGetter(Harbour::merchantAt),
				UUIDUtil.CODEC.optionalFieldOf("wright").forGetter(Harbour::wright))
			.apply(i, Harbour::new));

		Harbour withWarship(@Nullable UUID ship, @Nullable BlockPos seen, long lostAt) {
			return new Harbour(this.shore, this.pier, this.mooring, Optional.ofNullable(ship), Optional.ofNullable(seen), lostAt, this.merchant,
				this.merchantAt, this.wright);
		}

		Harbour withMerchant(@Nullable UUID ship, long at) {
			return new Harbour(this.shore, this.pier, this.mooring, this.warship, this.seen, this.lostAt, Optional.ofNullable(ship), at, this.wright);
		}

		Harbour withWright(@Nullable UUID wright) {
			return new Harbour(this.shore, this.pier, this.mooring, this.warship, this.seen, this.lostAt, this.merchant, this.merchantAt,
				Optional.ofNullable(wright));
		}
	}

	/** Every harbour (realm id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, Harbour>> HARBOURS = AttachmentRegistry.create(JaymosKingdoms.id("harbours"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Harbour.CODEC)));

	/** Realm id → ticks its warship has been missing; its shipwright. */
	private static final Map<Integer, Long> MISSING = new HashMap<>(), WRIGHT_GONE = new HashMap<>();

	private Navies() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Navies::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MISSING.clear();
			WRIGHT_GONE.clear();
			PirateCoves.clear();
			Pirates.clear();
		});
	}

	static @Nullable Harbour harbour(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(HARBOURS, Map.of()).get(Integer.toString(realm));
	}

	static void put(MinecraftServer server, int realm, @Nullable Harbour harbour) {
		ServerLevel level = Factions.level(server);
		Map<String, Harbour> all = new HashMap<>(level.getAttachedOrElse(HARBOURS, Map.of()));
		if (harbour == null) {
			all.remove(Integer.toString(realm));
		} else {
			all.put(Integer.toString(realm), harbour);
		}
		level.setAttached(HARBOURS, Map.copyOf(all));
	}

	// ---- seeing to the harbours ------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % WATCH_TICKS == 0) {
			for (ServerPlayer player : level.players()) {
				escorts(level, player);
			}
		}
		if (now % CHECK_TICKS != 0) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			Harbour h = harbour(server, f.id());
			if (h == null) {
				if (f.treasury() >= HARBOUR_TREASURY && level.isPositionEntityTicking(f.seat())) {
					build(level, f);
				}
				continue;
			}
			if (!level.isPositionEntityTicking(h.shore())) {
				continue;
			}
			wright(level, f, h);
			if (f.treasury() >= HARBOUR_TREASURY) {
				warship(level, f, harbour(server, f.id()), now, false);
			}
			trade(level, f, harbour(server, f.id()), now);
		}
	}

	/** Builds {@code f} a harbour: a pier out from the shore nearest its seat to open sea, its shipwright on it. */
	static @Nullable Harbour build(ServerLevel level, Faction f) {
		BlockPos water = PirateCoves.sea(level, f.seat(), PirateCoves.COAST_REACH);
		if (water == null) {
			return null;
		}
		// in from the sea toward the seat: the last water before the shore
		double dx = f.seat().getX() - water.getX(), dz = f.seat().getZ() - water.getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		BlockPos shore = null;
		for (int d = 0; d < len; d++) {
			BlockPos column = water.offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			if (!level.isPositionEntityTicking(column)) {
				return null;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(top.below()).is(FluidTags.WATER)) {
				shore = top;
				break;
			}
		}
		if (shore == null || shore.getY() > level.getSeaLevel() + 3) {
			// (a cliff, or no shore at all: no harbour there)
			return null;
		}
		Direction seaward = Direction.getApproximateNearest(-dx, 0, -dz);
		BlockPos pier = PirateCoves.jetty(level, shore, seaward, seaward.getClockWise());
		Harbour h = new Harbour(shore, pier, water, Optional.empty(), Optional.empty(), -1L, Optional.empty(), -1L, Optional.empty());
		put(level.getServer(), f.id(), h);
		wright(level, f, h);
		JaymosKingdoms.LOGGER.info("{} built a harbour at {} (mooring {})", f.name(), shore, water);
		return harbour(level.getServer(), f.id());
	}

	/** Its shipwright, on the pier: replaced if he's been gone a while. */
	private static void wright(ServerLevel level, Faction f, Harbour h) {
		Entity known = h.wright().map(level::getEntity).orElse(null);
		if (known != null && known.isAlive()) {
			WRIGHT_GONE.remove(f.id());
			return;
		}
		if (h.wright().isPresent() && WRIGHT_GONE.merge(f.id(), (long)CHECK_TICKS, Long::sum) < WRIGHT_MISSING) {
			return;
		}
		WRIGHT_GONE.remove(f.id());
		Folk wright = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (wright == null) {
			return;
		}
		wright.snapTo(h.shore().getX() + 0.5, h.shore().getY(), h.shore().getZ() + 0.5, 0.0F, 0.0F);
		wright.setup(Pirates.SHIPWRIGHT, f, h.shore(), 8);
		level.addFreshEntity(wright);
		put(level.getServer(), f.id(), h.withWright(wright.getUUID()));
	}

	/** Its warship kept track of, and built again (her crew mustered) when she's lost and it's been long enough. */
	static @Nullable Ship warship(ServerLevel level, Faction f, @Nullable Harbour h, long now, boolean force) {
		if (h == null) {
			return null;
		}
		MinecraftServer server = level.getServer();
		if (h.warship().isPresent()) {
			if (level.getEntity(h.warship().get()) instanceof Ship s && !s.isRemoved()) {
				MISSING.remove(f.id());
				if (!s.blockPosition().equals(h.seen().orElse(null))) {
					put(server, f.id(), h.withWarship(s.getUUID(), s.blockPosition(), h.lostAt()));
				}
				return s;
			}
			BlockPos seen = h.seen().orElse(h.mooring());
			if (!force && (!level.isPositionEntityTicking(seen) || MISSING.merge(f.id(), (long)CHECK_TICKS, Long::sum) < MISSING_TICKS)) {
				return null;
			}
			MISSING.remove(f.id());
			put(server, f.id(), h = h.withWarship(null, null, now));
		}
		if (!force && (h.lostAt() >= 0 && now - h.lostAt() < REBUILD_TICKS || !level.isPositionEntityTicking(h.mooring())
			|| Raids.playerNear(level, h.mooring(), LAUNCH_HIDE))) {
			return null;
		}
		RandomSource random = level.getRandom();
		boolean frigate = f.treasury() >= FRIGATE_TREASURY;
		Crew crew = new Crew(Crew.Side.NAVY, f.id(), Pirates.navyShip(random), h.mooring());
		Block colour = Fleet.block(f.color().getName() + "_wool");
		Fleet.Look look = new Fleet.Look(Blocks.OAK_PLANKS, Blocks.OAK_FENCE, Blocks.SPRUCE_LOG, colour,
			f.color() == net.minecraft.world.item.DyeColor.WHITE ? Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.RED) : Blocks.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE));
		Ship ship = launch(level, f, h, frigate ? Fleet.Rig.FRIGATE : Fleet.Rig.WAR_TRIREME, look, crew,
			new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, NAVY_HANDS, 0));
		if (ship != null) {
			put(server, f.id(), h.withWarship(ship.getUUID(), ship.blockPosition(), -1L));
			JaymosKingdoms.LOGGER.info("{} put out its warship {}", f.name(), crew.name);
		}
		return ship;
	}

	/** One of {@code f}'s ships put out at its mooring (or open water near it). */
	private static @Nullable Ship launch(ServerLevel level, Faction f, Harbour h, Fleet.Rig rig, Fleet.Look look, Crew crew, Corsairs.Complement complement) {
		RandomSource random = level.getRandom();
		Direction bow = Direction.getApproximateNearest(h.mooring().getX() - h.shore().getX(), 0, h.mooring().getZ() - h.shore().getZ()).getClockWise();
		for (int tries = 0; tries < 4; tries++) {
			BlockPos water = tries == 0 ? h.mooring() : Fleet.openSea(level, h.mooring(), 24, 4, random);
			if (water != null) {
				Ship ship = Corsairs.launch(level, rig, look, water, bow, crew, f, complement);
				if (ship != null) {
					return ship;
				}
			}
		}
		return null;
	}

	// ---- merchantmen -----------------------------------------------------------------------------------------------

	/** Now and then a merchantman puts out for another realm's harbour with goods (one at a time from each). */
	private static void trade(ServerLevel level, Faction f, @Nullable Harbour h, long now) {
		if (h == null) {
			return;
		}
		if (h.merchant().isPresent()) {
			if (now - h.merchantAt() < MERCHANT_FORGET) {
				return;
			}
			// (out too long: she's given up for lost)
			put(level.getServer(), f.id(), h = h.withMerchant(null, now));
		}
		if (h.merchantAt() >= 0 && now - h.merchantAt() < MERCHANT_GAP || level.getRandom().nextFloat() > MERCHANT_CHANCE) {
			return;
		}
		Faction to = destination(level, f);
		if (to != null) {
			merchant(level, f, to, "");
		}
	}

	/** Another standing realm's harbour, near enough to trade with, not at war with {@code f}. */
	static @Nullable Faction destination(ServerLevel level, Faction f) {
		MinecraftServer server = level.getServer();
		Faction best = null;
		double bestD = MERCHANT_RANGE * MERCHANT_RANGE;
		for (Faction other : Factions.all(server)) {
			if (other.id() == f.id() || !other.type().isRealm() || other.fallen() || harbour(server, other.id()) == null
				|| Relations.atWar(f, other)) {
				continue;
			}
			double d = Raids.horizontal(other.seat(), f.seat());
			if (d >= MERCHANT_LEAST * MERCHANT_LEAST && d < bestD) {
				best = other;
				bestD = d;
			}
		}
		return best;
	}

	/** A merchantman put out from {@code from}'s harbour for {@code to}'s ({@code quest}: an escort's, or ""). */
	static @Nullable Ship merchant(ServerLevel level, Faction from, Faction to, String quest) {
		MinecraftServer server = level.getServer();
		Harbour h = harbour(server, from.id()), there = harbour(server, to.id());
		if (h == null || there == null || !level.isPositionEntityTicking(h.mooring())) {
			return null;
		}
		RandomSource random = level.getRandom();
		Crew crew = new Crew(Crew.Side.MERCHANT, from.id(), Pirates.merchantShip(random), h.mooring());
		crew.bound = there.mooring();
		crew.boundFor = to.id();
		crew.quest = quest;
		Fleet.Look look = new Fleet.Look(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LOG, Fleet.block(from.color().getName() + "_wool"), null);
		Ship ship = launch(level, from, h, random.nextBoolean() ? Fleet.Rig.CARGO_SHIP : Fleet.Rig.TRADER, look, crew, new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, MERCHANT_HANDS, 0));
		if (ship != null) {
			put(server, from.id(), h.withMerchant(ship.getUUID(), level.getGameTime()));
			JaymosKingdoms.LOGGER.info("{} sails from {} for {}", crew.name, from.name(), to.name());
		}
		return ship;
	}

	/** {@code crew}'s merchantman has made her harbour: her goods in, both realms paid, an escort done; she's warped in (gone). */
	static void arrived(ServerLevel level, Ship ship, Crew crew) {
		MinecraftServer server = level.getServer();
		Faction from = Factions.get(server, crew.faction), to = crew.boundFor >= 0 ? Factions.get(server, crew.boundFor) : null;
		if (to != null && Storage.loaded(level, to)) {
			Item[] goods = {Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), Items.WHEAT, Items.SUGAR, Items.LEATHER, Items.PAPER, Items.IRON_INGOT};
			RandomSource random = level.getRandom();
			for (int i = 0; i < 3; i++) {
				Storage.put(level, to, goods[random.nextInt(goods.length)], 8 + random.nextInt(17));
			}
		}
		if (from != null) {
			Factions.update(server, from.id(), x -> x.withTreasury(x.treasury() + VOYAGE_PROFIT));
			Harbour h = harbour(server, from.id());
			if (h != null && h.merchant().isPresent() && h.merchant().get().equals(ship.getUUID())) {
				put(server, from.id(), h.withMerchant(null, level.getGameTime()));
			}
		}
		if (to != null) {
			Factions.update(server, to.id(), x -> x.withTreasury(x.treasury() + VOYAGE_TRADE));
		}
		if (!crew.quest.isEmpty()) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				Quest q = Quests.quest(player, crew.quest);
				if (q != null && q.kind() == Quest.ESCORT && !q.counted()) {
					Quests.put(player, q.withProgress(1));
					Quests.sync(player, true);
					if (from != null) {
						Reputation.change(player, from, REP_ESCORT);
					}
					player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.escort.arrived", crew.name, q.objective().place())
						.withStyle(ChatFormatting.GOLD));
					level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.2F);
				}
			}
		}
		Raids.tell(level, ship.blockPosition(), Component.translatable("message.jaymoskingdoms.ship.harboured", crew.name,
			to == null ? Component.literal("?") : Component.literal(to.name())).withStyle(ChatFormatting.AQUA));
		stow(ship);
	}

	/** A ship that's done (made harbour): her crew and she are gone. */
	static void stow(Ship ship) {
		for (LivingEntity hand : List.copyOf(ship.crewAboard())) {
			hand.discard();
		}
		ship.ejectPassengers();
		ship.discard();
	}

	/** {@code crew}'s ship went down: a warship is built again in a while; a merchantman's escort has failed; her sinker is hated for it. */
	static void shipLost(ServerLevel level, Crew crew, @Nullable ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Harbour h = harbour(server, crew.faction);
		long now = level.getGameTime();
		if (h != null) {
			if (crew.side == Crew.Side.NAVY) {
				put(server, crew.faction, h.withWarship(null, null, now));
			} else if (h.merchant().isPresent()) {
				put(server, crew.faction, h.withMerchant(null, now));
			}
		}
		Faction f = Factions.get(server, crew.faction);
		if (player != null && f != null) {
			Reputation.change(player, f, REP_SINK);
		}
		if (!crew.quest.isEmpty()) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				Quest q = Quests.quest(p, crew.quest);
				if (q != null && q.kind() == Quest.ESCORT && !q.counted()) {
					Quests.abandon(p, q.id());
					p.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.escort.lost", crew.name).withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	// ---- escorts ---------------------------------------------------------------------------------------------------

	/** A king's escort quest: see a merchantman from his harbour safely into another realm's; null if he has no harbour or nowhere to send her. */
	static @Nullable Quest escortQuest(ServerLevel level, Faction f, Quest.Giver who, Quest.Reward reward) {
		MinecraftServer server = level.getServer();
		Harbour h = harbour(server, f.id());
		Faction to = h == null ? null : destination(level, f);
		Harbour there = to == null ? null : harbour(server, to.id());
		if (there == null) {
			return null;
		}
		Quest.Objective o = new Quest.Objective("faction:" + to.id(), 1, 0, Optional.of(there.mooring()), Optional.empty(), "", to.name(), false);
		return new Quest(UUID.randomUUID().toString(), Quest.ESCORT, "king.escort", who, o, reward, 0L);
	}

	/** {@code player}'s escort quests: at the giver's harbour, the merchantman puts out for them. */
	private static void escorts(ServerLevel level, ServerPlayer player) {
		for (Quest q : Quests.log(player)) {
			if (q.kind() != Quest.ESCORT || q.objective().spawned() || q.counted()) {
				continue;
			}
			MinecraftServer server = level.getServer();
			Harbour h = harbour(server, q.giver().faction());
			Faction from = Factions.get(server, q.giver().faction());
			Faction to = q.objective().target().startsWith("faction:") ? Factions.get(server, parse(q.objective().target().substring(8))) : null;
			if (h == null || from == null || to == null || player.level() != level
				|| Raids.horizontal(player.blockPosition(), h.shore()) > ESCORT_START * ESCORT_START) {
				continue;
			}
			Ship ship = merchant(level, from, to, q.id());
			if (ship != null) {
				Quests.put(player, q.withSpawned(Optional.of(ship.getUUID())));
				Quests.sync(player, true);
				player.sendSystemMessage(Component.translatable("quest.jaymoskingdoms.escort.sailing", ship.crew == null ? "" : ship.crew.name, to.name())
					.withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	private static int parse(String id) {
		try {
			return Integer.parseInt(id);
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
