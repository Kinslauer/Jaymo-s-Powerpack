package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.Banner;
import com.jaymoskingdoms.WarData.War;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Banners in war. A sworn player plants their realm's banner (its colour, or the colour of their side's head) in the
 * land of a realm their side is at war with; if nobody of the enemy's side (its fighting folk, or its sworn players)
 * comes within {@link #HOLD_RADIUS} for {@link #HOLD_TICKS}, the chunk is theirs. Only a chunk bordering land their side
 * holds, or on the enemy's frontier (bordering land nobody holds), can be claimed this way. A banner within
 * {@link #SEAT_RANGE} of an enemy seat lays siege to it: held for {@link #SEAT_HOLD_TICKS}, the seat falls
 * ({@link Wars#seatTaken}). Enemy folk near a banner go to tear it down; anyone breaking it ends the claim. Progress shows
 * as a boss bar to players nearby. NPC war parties plant banners the same way ({@link #plantFor}). Saved in {@link WarData}.
 */
final class Banners {
	/** How long a banner must be held to take its chunk, or the seat (ticks). */
	static final long HOLD_TICKS = 20 * 60, SEAT_HOLD_TICKS = 20 * 240;
	/** No enemy folk (or sworn players) within this of the banner, or the holding pauses. */
	static final double HOLD_RADIUS = 16.0;
	/** A banner this near an enemy realm's seat besieges it. */
	static final double SEAT_RANGE = 24.0;
	/** How often banners are looked at (ticks). */
	static final int STEP = 20;
	/** Enemy fighting folk this near a banner go to tear it down; within this reach they do. */
	static final double DEFEND_RANGE = 40.0, BREAK_REACH = 2.5;
	static final double DEFEND_SPEED = 1.1;
	/** At most this many folk are sent to each banner. */
	static final int DEFENDERS_SENT = 3;
	/** The boss bar shows to players this near the banner; news of it is told this far. */
	static final double BAR_RANGE = 64.0, TELL_RANGE = 160.0;
	/** A banner placed by a player is looked for this many ticks after the click. */
	static final int PLACE_DELAY = 1;

	/** A player's click with a banner in hand: where it may have gone. */
	private record Placing(UUID player, BlockPos clicked, BlockPos beside, DyeColor color, long at) {
	}

	/** A folk sent to tear a banner down: the banner, and the post he goes back to. */
	private record Defender(BlockPos banner, BlockPos home, int radius) {
	}

	private static final List<Placing> PLACING = new ArrayList<>();
	/** Folk sent to banners (by UUID). */
	private static final Map<UUID, Defender> DEFENDERS = new HashMap<>();
	/** The boss bar of each banner being held (by position). */
	private static final Map<BlockPos, ServerBossEvent> BARS = new HashMap<>();

	private Banners() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server && player.getItemInHand(hand).getItem() instanceof BannerItem item) {
				PLACING.add(new Placing(sp.getUUID(), hit.getBlockPos().immutable(), hit.getBlockPos().relative(hit.getDirection()).immutable(),
					item.getColor(), server.getGameTime()));
			}
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				Banner banner = at(server.getServer(), pos);
				if (banner != null) {
					torn(server, banner, sp);
				}
			}
		});
	}

	static @Nullable Banner at(MinecraftServer server, BlockPos pos) {
		for (Banner b : WarData.banners(server)) {
			if (b.pos().equals(pos)) {
				return b;
			}
		}
		return null;
	}

	static boolean isBanner(BlockState state) {
		return state.getBlock() instanceof AbstractBannerBlock;
	}

	static @Nullable DyeColor colorOf(BlockState state) {
		return state.getBlock() instanceof AbstractBannerBlock banner ? banner.getColor() : null;
	}

	/** Who holds {@code chunk} (a standing faction), if anyone. */
	static @Nullable Faction owner(MinecraftServer server, ChunkPos chunk) {
		for (Faction f : Factions.all(server)) {
			if (f.claims(chunk)) {
				return f;
			}
		}
		return null;
	}

	/** {@code chunk} borders land {@code side} holds, or lies on its owner's frontier (it borders land nobody holds). */
	static boolean claimable(MinecraftServer server, int side, ChunkPos chunk) {
		int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int[] d : around) {
			Faction next = owner(server, new ChunkPos(chunk.x() + d[0], chunk.z() + d[1]));
			if (next == null || Wars.onSide(server, next.id(), side)) {
				return true;
			}
		}
		return false;
	}

	/** An enemy realm (of {@code side}, in {@code war}) whose seat is within {@link #SEAT_RANGE} of {@code pos}. */
	private static @Nullable Faction besieged(MinecraftServer server, War war, int side, BlockPos pos) {
		int enemy = Wars.otherSide(war, side);
		for (Faction f : Wars.members(server, enemy)) {
			if (Raids.horizontal(f.seat(), pos) <= SEAT_RANGE * SEAT_RANGE) {
				return f;
			}
		}
		return null;
	}

	// ---- planting --------------------------------------------------------------------------------------------------

	/** A banner block appeared where {@code player} clicked: if it's planted for war, it's registered. */
	private static void placed(ServerLevel level, ServerPlayer player, BlockPos pos, DyeColor color) {
		MinecraftServer server = level.getServer();
		Faction mine = Factions.of(server, player.getUUID());
		if (mine == null || !mine.type().isRealm()) {
			return;
		}
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			int side = Wars.sideOf(server, war, mine.id());
			if (side < 0) {
				continue;
			}
			ChunkPos chunk = ChunkPos.containing(pos);
			Faction owner = owner(server, chunk);
			Faction seat = besieged(server, war, side, pos);
			boolean enemyLand = owner != null && Wars.sideOf(server, war, owner.id()) == Wars.otherSide(war, side);
			if (seat == null && !enemyLand) {
				continue;
			}
			Faction head = Factions.get(server, side);
			if (color != mine.color() && color != mine.styleColor(server) && (head == null || color != head.color())) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.banner.colour", mine.name(), mine.color().getName())
					.withStyle(ChatFormatting.RED));
				return;
			}
			String refused = plant(level, war, mine, seat != null ? seat : owner, seat != null, pos, Optional.of(player.getUUID()));
			if (refused != null) {
				player.sendSystemMessage(Component.translatable(refused).withStyle(ChatFormatting.RED));
			}
			return;
		}
	}

	/**
	 * Registers a banner standing at {@code pos} for {@code faction} in {@code war}, in {@code target}'s land (or at its
	 * seat). Returns null when planted, or the key of the reason it can't be.
	 */
	static @Nullable String plant(ServerLevel level, War war, Faction faction, Faction target, boolean seat, BlockPos pos, Optional<UUID> planter) {
		MinecraftServer server = level.getServer();
		int side = Wars.sideOf(server, war, faction.id());
		ChunkPos chunk = ChunkPos.containing(pos);
		for (Banner b : WarData.banners(server)) {
			if (b.war() == war.id()) {
				if (seat ? b.seat() && b.target() == target.id() : !b.seat() && ChunkPos.containing(b.pos()).equals(chunk)) {
					return "message.jaymoskingdoms.banner.already";
				}
			}
		}
		if (!seat && !claimable(server, side, chunk)) {
			return "message.jaymoskingdoms.banner.not_frontier";
		}
		Banner banner = new Banner(pos.immutable(), faction.id(), war.id(), target.id(), seat, 0L, planter);
		List<Banner> banners = new ArrayList<>(WarData.banners(server));
		banners.add(banner);
		WarData.putBanners(server, banners);
		long seconds = (seat ? SEAT_HOLD_TICKS : HOLD_TICKS) / 20L;
		Component news = Component.translatable(seat ? "message.jaymoskingdoms.banner.siege" : "message.jaymoskingdoms.banner.planted", faction.name(),
			target.name(), pos.toShortString(), seconds).withStyle(ChatFormatting.GOLD);
		tell(level, pos, war, news);
		level.playSound(null, pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.5F, seat ? 0.6F : 1.0F);
		return null;
	}

	/** An NPC war party's bearer plants {@code faction}'s banner at (or next to) {@code pos}. Returns where, or null. */
	static @Nullable BlockPos plantFor(ServerLevel level, War war, Faction faction, Faction target, boolean seat, BlockPos pos) {
		Block block = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(faction.styleColor(level.getServer()).getName() + "_banner"));
		if (block == Blocks.AIR) {
			return null;
		}
		for (BlockPos at : new BlockPos[] {pos, pos.north(), pos.south(), pos.east(), pos.west(), pos.above()}) {
			if (level.getBlockState(at).canBeReplaced() && level.getBlockState(at.below()).isFaceSturdy(level, at.below(), net.minecraft.core.Direction.UP)
				&& level.getFluidState(at).isEmpty()) {
				level.setBlock(at, block.defaultBlockState(), Block.UPDATE_ALL);
				if (plant(level, war, faction, target, seat, at, Optional.empty()) == null) {
					return at;
				}
				level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				return null;
			}
		}
		return null;
	}

	// ---- holding -----------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server, ServerLevel level, long now) {
		if (!PLACING.isEmpty()) {
			for (Iterator<Placing> it = PLACING.iterator(); it.hasNext();) {
				Placing p = it.next();
				if (now - p.at() < PLACE_DELAY) {
					continue;
				}
				it.remove();
				ServerPlayer player = server.getPlayerList().getPlayer(p.player());
				if (player == null || player.level() != level) {
					continue;
				}
				for (BlockPos pos : new BlockPos[] {p.beside(), p.clicked()}) {
					if (colorOf(level.getBlockState(pos)) == p.color() && at(server, pos) == null) {
						placed(level, player, pos, p.color());
						break;
					}
				}
			}
		}
		if (now % STEP != 0) {
			return;
		}
		List<Banner> banners = WarData.banners(server);
		if (banners.isEmpty()) {
			return;
		}
		for (Banner b : List.copyOf(banners)) {
			step(server, level, b);
		}
	}

	private static void step(MinecraftServer server, ServerLevel level, Banner b) {
		War war = WarData.war(server, b.war());
		Faction faction = Factions.get(server, b.faction());
		Faction target = Factions.get(server, b.target());
		if (war == null || !war.active() || faction == null || target == null || target.fallen() || faction.fallen()) {
			remove(server, b);
			return;
		}
		int side = Wars.sideOf(server, war, faction.id());
		int enemy = side < 0 ? -1 : Wars.otherSide(war, side);
		if (side < 0 || Wars.sideOf(server, war, target.id()) != enemy) {
			remove(server, b);
			return;
		}
		ServerBossEvent bar = BARS.get(b.pos());
		if (!level.isPositionEntityTicking(b.pos())) {
			if (bar != null) {
				bar.removeAllPlayers();
			}
			return;
		}
		if (!isBanner(level.getBlockState(b.pos()))) {
			torn(level, b, null);
			return;
		}
		if (!b.seat() && !target.claims(ChunkPos.containing(b.pos()))) {
			// (the land changed hands some other way)
			remove(server, b);
			return;
		}
		// the enemy near it: holding pauses, and their fighting folk come to tear it down
		Vec3 center = Vec3.atCenterOf(b.pos());
		boolean contested = false;
		Folk nearest = null;
		double nearestDist = Double.MAX_VALUE;
		for (Folk folk : level.getEntitiesOfClass(Folk.class, new AABB(b.pos()).inflate(DEFEND_RANGE),
			f -> f.isAlive() && f.combatant() && Wars.onSide(server, f.factionId(), enemy))) {
			double d = folk.distanceToSqr(center);
			if (d <= HOLD_RADIUS * HOLD_RADIUS) {
				contested = true;
			}
			if (d <= BREAK_REACH * BREAK_REACH) {
				level.destroyBlock(b.pos(), true, folk);
				torn(level, b, folk);
				return;
			}
			if (d < nearestDist && !DEFENDERS.containsKey(folk.getUUID()) && (folk.getTarget() == null || !folk.getTarget().isAlive())) {
				nearest = folk;
				nearestDist = d;
			}
		}
		long sent = DEFENDERS.values().stream().filter(d -> d.banner().equals(b.pos())).count();
		if (nearest != null && sent < DEFENDERS_SENT) {
			// (his post moves to the banner until it's down)
			DEFENDERS.put(nearest.getUUID(), new Defender(b.pos(), nearest.getHomePosition(), nearest.getHomeRadius()));
			nearest.setHomeTo(b.pos(), 1);
			nearest.getNavigation().moveTo(b.pos().getX() + 0.5, b.pos().getY(), b.pos().getZ() + 0.5, DEFEND_SPEED);
		}
		for (ServerPlayer player : Raids.players(level, b.pos(), HOLD_RADIUS)) {
			Faction theirs = Factions.of(server, player.getUUID());
			if (!player.isCreative() && player.isAlive() && theirs != null && Wars.onSide(server, theirs.id(), enemy)) {
				contested = true;
			}
		}
		long need = b.seat() ? SEAT_HOLD_TICKS : HOLD_TICKS;
		long held = contested ? b.held() : Math.min(need, b.held() + STEP);
		Banner now = b.withHeld(held);
		if (held != b.held()) {
			replace(server, b, now);
		}
		// the bar
		if (bar == null) {
			bar = new ServerBossEvent(UUID.randomUUID(), Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
			BARS.put(b.pos(), bar);
		}
		bar.setName(Component.translatable(contested ? "message.jaymoskingdoms.banner.bar.contested"
			: b.seat() ? "message.jaymoskingdoms.banner.bar.seat" : "message.jaymoskingdoms.banner.bar", faction.name(), target.name()));
		bar.setColor(contested ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
		bar.setProgress(Math.min(1.0F, held / (float)need));
		List<ServerPlayer> near = Raids.players(level, b.pos(), BAR_RANGE);
		for (ServerPlayer player : List.copyOf(bar.getPlayers())) {
			if (!near.contains(player)) {
				bar.removePlayer(player);
			}
		}
		for (ServerPlayer player : near) {
			bar.addPlayer(player);
		}
		if (held >= need) {
			taken(server, level, war, now, side, enemy, faction, target);
		}
	}

	/** The banner was held long enough: the chunk (or the seat) is taken. */
	private static void taken(MinecraftServer server, ServerLevel level, War war, Banner b, int side, int enemy, Faction faction, Faction target) {
		remove(server, b);
		level.playSound(null, b.pos(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.5F, 0.6F);
		List<ServerPlayer> holders = new ArrayList<>();
		for (ServerPlayer player : Raids.players(level, b.pos(), HOLD_RADIUS)) {
			Faction theirs = Factions.of(server, player.getUUID());
			if (theirs != null && Wars.onSide(server, theirs.id(), side)) {
				holders.add(player);
			}
		}
		ServerPlayer planter = b.planter().map(id -> server.getPlayerList().getPlayer(id)).orElse(null);
		if (planter != null) {
			Wars.reward(server, planter, side, enemy, Wars.REP_WAR_CHUNK, 0);
			Reputation.change(planter, target, Wars.REP_WAR_CHUNK_LOST);
		}
		for (ServerPlayer holder : holders) {
			if (holder != planter) {
				Wars.reward(server, holder, side, enemy, Wars.REP_WAR_HOLDER, 0);
			}
		}
		if (b.seat()) {
			KingdomEvents.announce(server, Component.translatable("message.jaymoskingdoms.banner.seat_taken", faction.name(), target.name())
				.withStyle(ChatFormatting.DARK_RED));
			Wars.seatTaken(server, war, side, target);
			return;
		}
		long chunk = ChunkPos.containing(b.pos()).pack();
		Factions.update(server, target.id(), f -> {
			List<Long> claims = new ArrayList<>(f.claims());
			claims.remove(chunk);
			return f.withClaims(claims);
		});
		Factions.update(server, faction.id(), f -> {
			List<Long> claims = new ArrayList<>(f.claims());
			if (!claims.contains(chunk)) {
				claims.add(chunk);
			}
			return f.withClaims(claims);
		});
		Wars.credit(server, war.id(), side, 0, 0, 1, 0);
		tell(level, b.pos(), war, Component.translatable("message.jaymoskingdoms.banner.taken", faction.name(), target.name(), b.pos().toShortString())
			.withStyle(ChatFormatting.GOLD));
	}

	/** The banner is down (broken by {@code by}, or gone): the claim is over. */
	static void torn(ServerLevel level, Banner b, @Nullable Entity by) {
		MinecraftServer server = level.getServer();
		remove(server, b);
		War war = WarData.war(server, b.war());
		Faction faction = Factions.get(server, b.faction());
		if (war == null || faction == null) {
			return;
		}
		tell(level, b.pos(), war, Component.translatable("message.jaymoskingdoms.banner.torn", faction.name(), b.pos().toShortString())
			.withStyle(ChatFormatting.GREEN));
		if (by instanceof ServerPlayer player) {
			Faction theirs = Factions.of(server, player.getUUID());
			int side = theirs == null ? -1 : Wars.sideOf(server, war, theirs.id());
			int bannerSide = Wars.sideOf(server, war, faction.id());
			if (side >= 0 && bannerSide >= 0 && side != bannerSide) {
				Wars.reward(server, player, side, bannerSide, Wars.REP_WAR_RETAKE, 0);
			}
		}
	}

	// ---- bookkeeping -------------------------------------------------------------------------------------------------

	/** Tells the players near {@code pos}, and everyone sworn to either side of {@code war}. */
	static void tell(ServerLevel level, BlockPos pos, War war, Component message) {
		MinecraftServer server = level.getServer();
		List<ServerPlayer> told = new ArrayList<>(Raids.players(level, pos, TELL_RANGE));
		for (ServerPlayer p : Wars.sworn(server, war.attacker().principal())) {
			if (!told.contains(p)) {
				told.add(p);
			}
		}
		for (ServerPlayer p : Wars.sworn(server, war.defender().principal())) {
			if (!told.contains(p)) {
				told.add(p);
			}
		}
		for (ServerPlayer p : told) {
			p.sendSystemMessage(message);
		}
	}

	private static void replace(MinecraftServer server, Banner old, Banner now) {
		List<Banner> banners = new ArrayList<>(WarData.banners(server));
		int i = banners.indexOf(old);
		if (i >= 0) {
			banners.set(i, now);
			WarData.putBanners(server, banners);
		}
	}

	private static void remove(MinecraftServer server, Banner b) {
		List<Banner> banners = new ArrayList<>(WarData.banners(server));
		banners.removeIf(x -> x.pos().equals(b.pos()));
		WarData.putBanners(server, banners);
		ServerBossEvent bar = BARS.remove(b.pos());
		if (bar != null) {
			bar.removeAllPlayers();
		}
		// its defenders go back to their posts
		ServerLevel level = Factions.level(server);
		for (var it = DEFENDERS.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			if (e.getValue().banner().equals(b.pos())) {
				it.remove();
				if (level.getEntity(e.getKey()) instanceof Folk folk) {
					folk.setHomeTo(e.getValue().home(), e.getValue().radius());
				}
			}
		}
	}

	/** {@code war} is over: its banners are just banners now. */
	static void dropWar(MinecraftServer server, int war) {
		for (Banner b : List.copyOf(WarData.banners(server))) {
			if (b.war() == war) {
				remove(server, b);
			}
		}
	}

	/** Banners being held in {@code war}. */
	static List<Banner> of(MinecraftServer server, int war) {
		return WarData.banners(server).stream().filter(b -> b.war() == war).toList();
	}

	/** Sends every folk posted at a banner back to his own post (before the world is saved on shutdown). */
	static void restoreDefenders(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		for (var e : DEFENDERS.entrySet()) {
			if (level.getEntity(e.getKey()) instanceof Folk folk) {
				folk.setHomeTo(e.getValue().home(), e.getValue().radius());
			}
		}
		DEFENDERS.clear();
	}

	static void clear() {
		PLACING.clear();
		DEFENDERS.clear();
		for (ServerBossEvent bar : BARS.values()) {
			bar.removeAllPlayers();
		}
		BARS.clear();
	}
}
