package com.jaymoskingdoms;

import com.jaymoskingdoms.WarData.War;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * NPC war parties, as the barbarians' {@link Raids} but between realms: while a war is on, each side now and then sends
 * a party of soldiers against a realm of the other side that has a player near its seat (so it's seen). The party
 * musters on the enemy's land, on its frontier toward the attacker where it can; its bearer plants their banner on the
 * first chunk it can claim and leaves {@link #BANNER_GUARDS} to hold it; the rest march on the seat, plant a banner
 * there (a siege) and fight round it for {@link #SIEGE_TICKS}; then the survivors withdraw. Its size comes from the
 * army of the realm that sends it. A party is mustered for the march: it isn't on the realm's roster, and its dead
 * aren't replaced. Parties aren't saved: soldiers left after a restart go home.
 *
 * <p>(0.5.28) <b>Marching with a party.</b> A player sworn to a realm at war can join its side's party in the field with
 * {@code /kingdom war join}; with none out, standing at their realm's seat, they call one up there, and it marches the
 * whole way to the nearest enemy seat with them ({@link #join}).
 *
 * <p>(0.5.28) <b>The spoils cart.</b> Every party takes a horse-drawn storage cart (a {@link Wagon} with its carter), or
 * it doesn't march. When its siege is over, the party sacks the seat ({@link #seize}): a share of the enemy's treasury
 * and goods from its storehouse go into the cart, which hauls them home into the realm's storehouse and treasury; the
 * players who marched get a cut and the realm's thanks ({@link #deliver}). Break the cart open, or kill its horse or
 * carter, and the spoils spill on the ground for the taking ({@link #cartBroken}). If the soldiers fall, a laden cart
 * goes on home alone.
 */
final class WarParties {
	/** How often each side of a war considers sending a party (ticks), the chance it does, and the least time between two (ticks). */
	static final int CHECK_TICKS = 200;
	static final float PARTY_CHANCE = 0.15F;
	static final long COOLDOWN_TICKS = 6000L;
	/** A party goes against a realm with a player this near its seat. */
	static final double PLAYER_RANGE = 96.0;
	/** The party: a base, one more per this many of the army (guards and archers), at most this many. */
	static final int PARTY_BASE = 3, PARTY_PER = 3, PARTY_MAX = 9;
	/** Soldiers left to hold the frontier banner (the party keeps at least {@code MARCHERS_KEPT} marching). */
	static final int BANNER_GUARDS = 2, MARCHERS_KEPT = 2;
	/** Where it musters: this far from the enemy's seat toward the attacker (the first loaded place that's claimable, else any loaded one). */
	static final int[] MUSTER_DISTANCE = {104, 96, 88, 112, 80, 72, 64, 120, 56, 48};
	/** How often parties are driven (ticks); their pace; waypoints this far ahead. */
	static final int TICK = 10;
	static final double MARCH_SPEED = 1.0, WAYPOINT = 16.0;
	/** It has arrived at the seat this near it, or after this long (ticks). */
	static final double ARRIVE_RANGE = 16.0;
	static final long MARCH_TICKS = 20 * 120;
	/** The siege: soldiers keep within this of their banner, for this long (ticks). */
	static final int SIEGE_RADIUS = 14, HOLD_RADIUS = 6;
	static final long SIEGE_TICKS = 20 * 180;
	/** Withdrawing: a soldier is gone this near his own seat, or once no player is this near him; the whole party after this long (ticks). */
	static final double HOME_REACH = 16.0, VANISH_RANGE = 48.0;
	static final long PARTY_MAX_TICKS = 20 * 60 * 10;
	/** What soldiers' {@link Folk#task} says. */
	static final String TASK = "war";
	/** (0.5.28) A party called up by a player: it musters within this of their realm's seat; its march may take this long, the whole outing this long (ticks). */
	static final double CALL_RANGE = 64.0;
	static final long JOINED_MARCH_TICKS = 20 * 60 * 8, JOINED_MAX_TICKS = 20 * 60 * 40;
	/** The spoils: this share of the enemy's treasury, and up to this many stacks of goods from its storehouse. */
	static final float SPOILS_SHARE = 0.3F;
	/** Without a cart, the emeralds each soldier can carry home from a sack. */
	static final int SOLDIER_CARRIES = 8;
	static final int SPOILS_STACKS = 9;
	/** Its spoils cart keeps this far behind the party's lead on the march, and this far back from the siege. */
	static final double CART_BEHIND = 6.0, CART_SIEGE_BACK = 12.0;
	/** Marching soldiers wait for their cart when they're this far ahead of it; it's steered this far ahead at a time. */
	static final double CART_WAIT = 20.0, CART_WAYPOINT = 18.0;
	static final String CART_TAG = "jaymoskingdoms.war_cart";
	/** The players' cut of the spoils' emeralds (percent, shared among them), and the reputation each gains. */
	static final int PLAYERS_CUT = 25, SPOILS_REPUTATION = 15;

	enum Phase {
		MARCH, SIEGE, RETURN
	}

	static final class Party {
		final int war, side, faction, target;
		final List<UUID> soldiers = new ArrayList<>();
		final Set<UUID> holders = new HashSet<>();
		Phase phase = Phase.MARCH;
		final long started;
		long phaseAt;
		boolean frontierPlanted, seatPlanted;
		/** (0.5.28) The players marching with it, and what it carries home. */
		final Set<UUID> players = new HashSet<>();
		final List<ItemStack> spoils = new ArrayList<>();
		long spoilsEmeralds;
		@Nullable BlockPos lastAt, rally;
		/** Its spoils cart: carter, horse and wagon; and whether it was broken open. */
		@Nullable UUID carter, horse, wagon;
		boolean cartBroken;
		/** Where its cart last got on, and when (to help it over what stops it). */
		@Nullable Vec3 cartMark;
		long cartMarkAt;

		boolean laden() {
			return !this.spoils.isEmpty() || this.spoilsEmeralds > 0;
		}

		Party(int war, int side, int faction, int target, long now) {
			this.war = war;
			this.side = side;
			this.faction = faction;
			this.target = target;
			this.started = now;
			this.phaseAt = now;
		}
	}

	/** "war|side" → its party in the field; and when each last sent one. */
	private static final Map<String, Party> ACTIVE = new HashMap<>();
	private static final Map<String, Long> LAST = new HashMap<>();
	/** Realms whose soldier fell (the roster's loss is undone next tick: parties aren't on it). */
	private static final List<Integer> UNDO = new ArrayList<>();
	/** Soldiers loaded with no party to belong to (sent home next tick). */
	private static final List<Folk> STRAYS = new ArrayList<>();
	/** Spoils carts (carter, horse, wagon) loaded with no party (sent away next tick). */
	private static final List<Entity> STRAY_CARTS = new ArrayList<>();

	private WarParties() {
	}

	private static String key(int war, int side) {
		return war + "|" + side;
	}

	static boolean isSoldier(Folk folk) {
		return folk.task().equals(TASK);
	}

	/** A party of {@code war} is in the field. */
	static boolean fighting(int war) {
		return ACTIVE.values().stream().anyMatch(p -> p.war == war);
	}

	// ---- sending -------------------------------------------------------------------------------------------------------

	private static void consider(MinecraftServer server, ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		for (War war : WarData.wars(server)) {
			if (!war.active()) {
				continue;
			}
			for (int side : new int[] {war.attacker().principal(), war.defender().principal()}) {
				String key = key(war.id(), side);
				if (ACTIVE.containsKey(key) || now - LAST.getOrDefault(key, -COOLDOWN_TICKS) < COOLDOWN_TICKS || random.nextFloat() >= Tempers.warChance(server, side, PARTY_CHANCE)) {
					continue;
				}
				send(level, war, side, false);
			}
		}
	}

	/** {@code side} of {@code war} sends a party, if there's a target with a player near. Returns the party's target, or null. */
	static @Nullable Faction send(ServerLevel level, War war, int side, boolean anywhere) {
		MinecraftServer server = level.getServer();
		int enemy = Wars.otherSide(war, side);
		List<Faction> ours = Wars.members(server, side);
		if (ours.isEmpty()) {
			return null;
		}
		Faction target = null, from = null;
		double best = Double.MAX_VALUE;
		for (Faction t : Wars.members(server, enemy)) {
			if (!level.isPositionEntityTicking(t.seat()) || (!anywhere && !Raids.playerNear(level, t.seat(), PLAYER_RANGE))) {
				continue;
			}
			for (Faction f : ours) {
				double d = Raids.horizontal(f.seat(), t.seat());
				if (d < best) {
					best = d;
					target = t;
					from = f;
				}
			}
		}
		if (target == null || ACTIVE.containsKey(key(war.id(), side))) {
			return null;
		}
		return start(level, war, side, from, target, null) != null ? target : null;
	}

	/** Sends {@code from}'s party against {@code target}: from {@code muster} (its own seat, for a party a player calls), else on the enemy's frontier. */
	private static @Nullable Party start(ServerLevel level, War war, int side, Faction from, Faction target, @Nullable BlockPos musterAt) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos muster = musterAt != null ? musterAt : muster(level, side, Wars.otherSide(war, side), from, target);
		if (muster == null) {
			return null;
		}
		long now = level.getGameTime();
		Party party = new Party(war.id(), side, from.id(), target.id(), now);
		ACTIVE.put(key(war.id(), side), party);
		LAST.put(key(war.id(), side), now);
		int army = from.holdings().count(Folk.GUARD) + from.holdings().count(Folk.ARCHER);
		int size = Math.min(PARTY_MAX, PARTY_BASE + army / PARTY_PER) + Tempers.warExtra(from);
		for (int i = 0; i < size; i++) {
			Folk soldier = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (soldier == null) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, muster.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3));
			soldier.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			soldier.setup(Folk.GUARD, from, target.seat(), Raids.RAID_HOME_RADIUS);
			level.addFreshEntity(soldier);
			soldier.setTask(TASK);
			// (0.5.14) a prosperous realm's soldiers ride, some of them
			if (i % Cavalry.PARTY_RIDER_ONE_IN == 0 && Wealth.atLeast(server, from, Wealth.Tier.PROSPEROUS)) {
				Cavalry.mount(level, soldier, from, true);
			}
			party.soldiers.add(soldier.getUUID());
		}
		if (party.soldiers.isEmpty() || !hitchCart(level, party, from, muster, target)) {
			// (no spoils cart, no march)
			for (UUID id : party.soldiers) {
				if (level.getEntity(id) instanceof Folk soldier) {
					Cavalry.dismiss(soldier);
					soldier.discard();
				}
			}
			ACTIVE.remove(key(war.id(), side));
			return null;
		}
		Banners.tell(level, target.seat(), war, Component.translatable("message.jaymoskingdoms.war.party", from.name(), target.name())
			.withStyle(ChatFormatting.RED));
		for (ServerPlayer player : Raids.players(level, target.seat(), Raids.ANNOUNCE_RANGE)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 1.1F);
		}
		Treasuries.wages(server, from, party.soldiers.size());
		JaymosKingdoms.LOGGER.info("{} sends {} soldiers against {} from {}", from.name(), party.soldiers.size(), target.name(), muster);
		return party;
	}

	// ---- players marching with a party ------------------------------------------------------------------------------

	/**
	 * {@code player} marches with their side's party: the one in the field, or (at their realm's seat, with none out) one
	 * called up there to march on the nearest enemy seat. Returns what to tell them, and whether they joined.
	 */
	record Joined(boolean ok, Component message) {
	}

	static Joined join(ServerLevel level, ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Faction mine = Factions.of(server, player.getUUID());
		// (0.5.29) sent by a warlord's decree to lead his party: his realm's, sworn or not
		int led = Quests.warbandRealm(player);
		if (led >= 0 && Factions.get(server, led) != null) {
			mine = Factions.get(server, led);
		}
		if (mine == null) {
			return new Joined(false, Component.translatable("message.jaymoskingdoms.war.join_unsworn"));
		}
		for (War war : WarData.wars(server)) {
			int side = war.active() ? Wars.sideOf(server, war, mine.id()) : -1;
			if (side < 0) {
				continue;
			}
			Party out = ACTIVE.get(key(war.id(), side));
			if (out != null && out.phase != Phase.RETURN) {
				out.players.add(player.getUUID());
				Faction target = Factions.get(server, out.target);
				Folk lead = out.soldiers.stream().map(level::getEntity).filter(e -> e instanceof Folk).map(e -> (Folk)e).findFirst().orElse(null);
				BlockPos at = lead != null ? lead.blockPosition() : out.lastAt != null ? out.lastAt : target != null ? target.seat() : player.blockPosition();
				return new Joined(true, Component.translatable("message.jaymoskingdoms.war.joined", name(Factions.get(server, out.faction)), name(target), at.getX(), at.getZ()));
			}
			if (out != null) {
				continue;
			}
			if (!mine.type().isRealm() || player.distanceToSqr(Vec3.atCenterOf(mine.seat())) > CALL_RANGE * CALL_RANGE) {
				return new Joined(false, Component.translatable("message.jaymoskingdoms.war.join_seat", mine.name()));
			}
			Faction target = null;
			double best = Double.MAX_VALUE;
			for (Faction t : Wars.members(server, Wars.otherSide(war, side))) {
				double d = Raids.horizontal(mine.seat(), t.seat());
				if (d < best) {
					best = d;
					target = t;
				}
			}
			if (target == null) {
				continue;
			}
			BlockPos muster = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
			Party party = start(level, war, side, mine, target, muster);
			if (party == null) {
				continue;
			}
			party.players.add(player.getUUID());
			return new Joined(true, Component.translatable("message.jaymoskingdoms.war.called", mine.name(), target.name(), target.seat().getX(), target.seat().getZ()));
		}
		return new Joined(false, Component.translatable("message.jaymoskingdoms.war.join_none", mine.name()));
	}

	/** Its siege over, a party with players in it takes the enemy's riches: a share of its treasury and goods from its storehouse. */
	private static void seize(ServerLevel level, Party party, Faction target) {
		MinecraftServer server = level.getServer();
		boolean cart = cart(level, party) != null;
		long share = Math.round(target.treasury() * SPOILS_SHARE);
		// (no cart to haul it: only what the soldiers can carry, and no goods)
		long emeralds = cart ? share : Math.min(share, (long)party.soldiers.size() * SOLDIER_CARRIES);
		if (emeralds > 0) {
			Factions.update(server, target.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - emeralds)));
			party.spoilsEmeralds += emeralds;
		}
		int goods = 0;
		if (cart && Storage.loaded(level, target)) {
			for (int i = 0; i < SPOILS_STACKS; i++) {
				List<ItemStack> contents = Storage.contents(level, target);
				if (contents.isEmpty()) {
					break;
				}
				ItemStack pick = contents.get(level.getRandom().nextInt(contents.size()));
				ItemStack taken = Storage.take(level, target, x -> ItemStack.isSameItemSameComponents(x, pick), pick.getMaxStackSize());
				if (!taken.isEmpty()) {
					goods += taken.getCount();
					party.spoils.add(taken);
				}
			}
		}
		tellPlayers(level, party, Component.translatable(cart ? "message.jaymoskingdoms.war.seized" : "message.jaymoskingdoms.war.seized_no_cart",
			target.name(), emeralds, goods).withStyle(ChatFormatting.GOLD));
		// (0.5.29) a warlord's war-party decree done
		for (UUID id : party.players) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				Quests.sacked(p, party.faction, target);
			}
		}
	}

	/** The spoils are home: goods into the storehouse (what won't fit, at the seat), emeralds into the treasury; the players their cut. */
	private static void deliver(ServerLevel level, Party party, @Nullable Faction from) {
		if (from == null || party.spoils.isEmpty() && party.spoilsEmeralds == 0) {
			return;
		}
		MinecraftServer server = level.getServer();
		boolean stored = Storage.loaded(level, from);
		long inCoin = 0;
		for (ItemStack stack : party.spoils) {
			ItemStack left = stored ? Storage.put(level, from, stack) : stack;
			if (left.isEmpty()) {
				continue;
			}
			if (level.isLoaded(from.seat())) {
				net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, from.seat().getX() + 0.5,
					from.seat().getY() + 1.0, from.seat().getZ() + 0.5, left);
				level.addFreshEntity(item);
			} else {
				inCoin += left.getCount() / 8;
			}
		}
		List<ServerPlayer> marched = party.players.stream().map(id -> server.getPlayerList().getPlayer(id)).filter(p -> p != null).toList();
		long cut = marched.isEmpty() ? 0 : party.spoilsEmeralds * PLAYERS_CUT / 100;
		long toTreasury = party.spoilsEmeralds - cut + inCoin;
		Factions.update(server, from.id(), f -> f.withTreasury(f.treasury() + toTreasury));
		for (ServerPlayer p : marched) {
			int each = (int)(cut / marched.size());
			if (each > 0) {
				ItemStack pay = new ItemStack(net.minecraft.world.item.Items.EMERALD, each);
				if (!p.getInventory().add(pay)) {
					p.drop(pay, false, net.minecraft.util.Prediction.SERVER_ONLY);
				}
			}
			Reputation.change(p, from, SPOILS_REPUTATION);
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.war.spoils_home", from.name(), toTreasury, each).withStyle(ChatFormatting.GOLD));
		}
		party.spoils.clear();
		party.spoilsEmeralds = 0;
	}

	// ---- the spoils cart ---------------------------------------------------------------------------------------------

	/** {@code from}'s spoils cart for {@code party}, set down at {@code muster} behind the soldiers. False if it can't be had. */
	private static boolean hitchCart(ServerLevel level, Party party, Faction from, BlockPos muster, Faction target) {
		double dx = muster.getX() - target.seat().getX(), dz = muster.getZ() - target.seat().getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		BlockPos spot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
			muster.offset(Mth.floor(dx / len * CART_BEHIND), 0, Mth.floor(dz / len * CART_BEHIND)));
		float yaw = (float)(Math.atan2(-dz, -dx) * (180.0 / Math.PI)) - 90.0F;
		Folk carter = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (carter == null) {
			return false;
		}
		carter.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0F);
		carter.setup(Caravans.CARTER, from, spot, 64);
		carter.setTask(Caravans.TASK);
		level.addFreshEntity(carter);
		// (tagged once in the world, so its own loading doesn't count it a stray)
		carter.addTag(CART_TAG);
		Entity horse = Cavalry.mount(level, carter, from, true);
		if (horse == null) {
			carter.discard();
			return false;
		}
		horse.addTag(CART_TAG);
		horse.setYRot(yaw);
		Wagon wagon = Wagon.hitch(level, Wagon.CART, from, horse, Wagon.WAR_CART, 0);
		wagon.addTag(CART_TAG);
		wagon.setLoad(Wagon.EMPTY);
		party.carter = carter.getUUID();
		party.horse = horse.getUUID();
		party.wagon = wagon.getUUID();
		return true;
	}

	/** Its spoils cart, whole and in the world; else null. */
	private static @Nullable Wagon cart(ServerLevel level, Party party) {
		return party.wagon != null && level.getEntity(party.wagon) instanceof Wagon w && !w.isRemoved() ? w : null;
	}

	/** The cart follows the party: behind its lead on the march, back from the fighting at the siege, home after. */
	private static void driveCart(ServerLevel level, String key, Party party, List<Folk> soldiers, @Nullable Faction from, @Nullable Faction target) {
		Wagon wagon = cart(level, party);
		if (wagon == null) {
			return;
		}
		Entity carter = party.carter == null ? null : level.getEntity(party.carter);
		Entity horse = party.horse == null ? null : level.getEntity(party.horse);
		if (!(carter instanceof Folk driver) || !driver.isAlive() || horse == null || !horse.isAlive()) {
			// (its carter or horse fell: the cart is left where it stands, broken open)
			Caravans.cartLost(level, wagon);
			return;
		}
		Caravans.remount(driver, horse);
		wagon.setLoad(party.laden() ? Wagon.GOODS : Wagon.EMPTY);
		Vec3 to;
		if (party.phase == Phase.RETURN || target == null) {
			if (from == null) {
				dismissCart(level, party);
				return;
			}
			boolean home = horse.distanceToSqr(Vec3.atCenterOf(from.seat())) < HOME_REACH * HOME_REACH;
			if (home || !Raids.playerNear(level, horse.blockPosition(), VANISH_RANGE)) {
				// home (or gone on out of sight, to get there unseen) with what it carries
				deliver(level, party, from);
				dismissCart(level, party);
				return;
			}
			to = Vec3.atBottomCenterOf(from.seat());
		} else {
			BlockPos toward = party.phase == Phase.SIEGE && party.rally != null ? party.rally : null;
			Folk lead = null;
			for (Folk s : soldiers) {
				if (!party.holders.contains(s.getUUID()) && (lead == null
					|| s.distanceToSqr(Vec3.atCenterOf(target.seat())) < lead.distanceToSqr(Vec3.atCenterOf(target.seat())))) {
					lead = s;
				}
			}
			Vec3 front = toward != null ? Vec3.atBottomCenterOf(toward) : lead != null ? lead.position() : horse.position();
			Vec3 back = from != null ? Vec3.atBottomCenterOf(from.seat()).subtract(front) : horse.position().subtract(front);
			back = new Vec3(back.x, 0.0, back.z);
			double len = back.length();
			to = len < 1.0E-3 ? front : front.add(back.scale((toward != null ? CART_SIEGE_BACK : CART_BEHIND) / len));
		}
		// a point on the way at a time (a path that far is more than a carter can find)
		Vec3 here = horse.position();
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		double far = flat.length();
		if (far > CART_WAYPOINT) {
			BlockPos column = BlockPos.containing(here.add(flat.scale(CART_WAYPOINT / far)));
			to = Vec3.atBottomCenterOf(level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column);
		}
		// (a wall, a roof, a ravine: stuck a while short of where it's headed, it's helped over)
		long now = level.getGameTime();
		if (party.cartMark == null || horse.position().distanceToSqr(party.cartMark) > 4.0) {
			party.cartMark = horse.position();
			party.cartMarkAt = now;
		} else if (now - party.cartMarkAt > Caravans.STUCK_TICKS && horse.position().distanceTo(to) > 4.0) {
			Caravans.hop(level, driver, horse, wagon, to);
			party.cartMark = null;
		}
		driver.setHomeTo(driver.blockPosition(), 64);
		Caravans.steer(driver, to);
	}

	/** The cart, its horse and carter go (home, or away). */
	private static void dismissCart(ServerLevel level, Party party) {
		for (UUID id : new UUID[] {party.carter, party.horse, party.wagon}) {
			Entity e = id == null ? null : level.getEntity(id);
			if (e != null) {
				if (e instanceof Folk f) {
					Caravans.steer(f, null);
				}
				level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 8, 0.4, 0.5, 0.4, 0.02);
				e.discard();
			}
		}
		party.carter = party.horse = party.wagon = null;
	}

	private static @Nullable Party partyOf(Wagon wagon) {
		for (Party party : ACTIVE.values()) {
			if (wagon.getUUID().equals(party.wagon)) {
				return party;
			}
		}
		return null;
	}

	/** A spoils cart broke (struck open, or its horse or carter fell): its spoils spill where it stands. */
	static void cartBroken(ServerLevel level, Wagon wagon) {
		Party party = partyOf(wagon);
		if (party == null) {
			return;
		}
		party.cartBroken = true;
		BlockPos at = wagon.blockPosition();
		for (ItemStack stack : party.spoils) {
			net.minecraft.world.level.block.Block.popResource(level, at, stack);
		}
		long emeralds = party.spoilsEmeralds;
		while (emeralds > 0) {
			int n = (int)Math.min(64, emeralds);
			net.minecraft.world.level.block.Block.popResource(level, at, new ItemStack(net.minecraft.world.item.Items.EMERALD, n));
			emeralds -= n;
		}
		tellPlayers(level, party, Component.translatable(party.laden() ? "message.jaymoskingdoms.war.spoils_spilled" : "message.jaymoskingdoms.war.cart_lost",
			at.getX(), at.getZ()).withStyle(ChatFormatting.RED));
		party.spoils.clear();
		party.spoilsEmeralds = 0;
		party.carter = party.horse = party.wagon = null;
	}

	/** What a spoils cart carries, for a player who looks. */
	static Component describe(ServerLevel level, Wagon wagon) {
		Party party = partyOf(wagon);
		String whose = name(Factions.get(level.getServer(), party != null ? party.faction : wagon.faction()));
		if (party == null || !party.laden()) {
			return Component.translatable("message.jaymoskingdoms.war.cart_empty", whose);
		}
		int goods = party.spoils.stream().mapToInt(ItemStack::getCount).sum();
		return Component.translatable("message.jaymoskingdoms.war.cart", whose, party.spoilsEmeralds, goods);
	}

	private static void tellPlayers(ServerLevel level, Party party, Component message) {
		for (UUID id : party.players) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				p.sendSystemMessage(message);
			}
		}
	}

	/** Loaded open ground on {@code target}'s land toward {@code from}: a claimable frontier chunk if there is one. */
	private static @Nullable BlockPos muster(ServerLevel level, int side, int enemy, Faction from, Faction target) {
		MinecraftServer server = level.getServer();
		double dx = from.seat().getX() - target.seat().getX(), dz = from.seat().getZ() - target.seat().getZ();
		double len = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
		BlockPos fallback = null;
		for (int distance : MUSTER_DISTANCE) {
			double d = Math.min(distance, Math.max(8.0, len - 8.0));
			BlockPos column = target.seat().offset(Mth.floor(dx / len * d), 0, Mth.floor(dz / len * d));
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			if (!level.getFluidState(at.below()).isEmpty() || !level.getFluidState(at).isEmpty()) {
				continue;
			}
			ChunkPos chunk = ChunkPos.containing(at);
			Faction owner = Banners.owner(server, chunk);
			if (owner != null && Wars.onSide(server, owner.id(), enemy) && Banners.claimable(server, side, chunk)) {
				return at;
			}
			if (fallback == null) {
				fallback = at;
			}
		}
		return fallback;
	}

	// ---- driving -------------------------------------------------------------------------------------------------------

	static void tick(MinecraftServer server, ServerLevel level, long now) {
		for (int faction : UNDO) {
			Factions.update(server, faction, f -> f.withHoldings(f.holdings().adjust(Folk.GUARD, 1).adjustVacancy(Folk.GUARD, -1)));
		}
		UNDO.clear();
		if (!STRAYS.isEmpty()) {
			List<Folk> strays = List.copyOf(STRAYS);
			STRAYS.clear();
			for (Folk stray : strays) {
				if (stray.isAlive()) {
					vanish(level, stray);
				}
			}
		}
		if (!STRAY_CARTS.isEmpty()) {
			List<Entity> strays = List.copyOf(STRAY_CARTS);
			STRAY_CARTS.clear();
			for (Entity stray : strays) {
				stray.discard();
			}
		}
		if (now % CHECK_TICKS == 0) {
			consider(server, level, now);
		}
		if (now % TICK != 0 || ACTIVE.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Party> e : List.copyOf(ACTIVE.entrySet())) {
			drive(level, e.getKey(), e.getValue(), now);
		}
	}

	private static void drive(ServerLevel level, String key, Party party, long now) {
		MinecraftServer server = level.getServer();
		War war = WarData.war(server, party.war);
		Faction from = Factions.get(server, party.faction);
		Faction target = Factions.get(server, party.target);
		List<Folk> soldiers = new ArrayList<>();
		for (UUID id : party.soldiers) {
			if (level.getEntity(id) instanceof Folk folk && folk.isAlive()) {
				soldiers.add(folk);
			}
		}
		if (soldiers.isEmpty()) {
			Wagon wagon = cart(level, party);
			if (wagon != null && party.laden() && from != null) {
				// the soldiers fallen, the laden cart goes on home alone
				party.phase = Phase.RETURN;
				driveCart(level, key, party, soldiers, from, target);
				return;
			}
			ACTIVE.remove(key);
			if (wagon != null) {
				dismissCart(level, party);
			} else if (!party.cartBroken && party.laden()) {
				// (the cart out of sight: it gets home unseen)
				deliver(level, party, from);
			}
			if (party.phase != Phase.RETURN && target != null && war != null) {
				Banners.tell(level, target.seat(), war, Component.translatable("message.jaymoskingdoms.war.party_beaten", name(from), target.name())
					.withStyle(ChatFormatting.GREEN));
			}
			return;
		}
		party.lastAt = soldiers.getFirst().blockPosition();
		boolean joined = !party.players.isEmpty();
		if (war == null || !war.active() || from == null || from.fallen() || target == null || target.fallen()
			|| now - party.started > (joined ? JOINED_MAX_TICKS : PARTY_MAX_TICKS)) {
			withdraw(party, soldiers, from, now);
		}
		driveCart(level, key, party, soldiers, from, target);
		switch (party.phase) {
			case MARCH -> {
				if (!party.frontierPlanted) {
					plantFrontier(level, war, party, soldiers, from, target);
				}
				boolean arrived = now - party.phaseAt > (joined ? JOINED_MARCH_TICKS : MARCH_TICKS);
				Wagon wagon = cart(level, party);
				Vec3 seat = Vec3.atCenterOf(target.seat());
				for (Folk soldier : soldiers) {
					if (party.holders.contains(soldier.getUUID())) {
						continue;
					}
					if (soldier.getTarget() == null) {
						if (wagon != null && soldier.distanceToSqr(wagon) > CART_WAIT * CART_WAIT
							&& soldier.distanceToSqr(seat) < wagon.distanceToSqr(seat)) {
							// (too far ahead of the cart: they wait for it)
							soldier.getNavigation().stop();
						} else {
							walk(level, soldier, target.seat());
						}
					}
					arrived |= soldier.distanceToSqr(Vec3.atCenterOf(target.seat())) < ARRIVE_RANGE * ARRIVE_RANGE;
				}
				if (arrived) {
					party.phase = Phase.SIEGE;
					party.phaseAt = now;
					Economy.noteAttack(server, target, soldiers.getFirst());
					Folk bearer = soldiers.stream().filter(s -> !party.holders.contains(s.getUUID()))
						.min((a, b) -> Double.compare(a.distanceToSqr(Vec3.atCenterOf(target.seat())), b.distanceToSqr(Vec3.atCenterOf(target.seat()))))
						.orElse(soldiers.getFirst());
					BlockPos at = bearer.blockPosition();
					if (Raids.horizontal(at, target.seat()) > Banners.SEAT_RANGE * Banners.SEAT_RANGE) {
						at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, target.seat().offset(4, 0, 4));
					}
					BlockPos planted = Banners.plantFor(level, war, from, target, true, at);
					party.seatPlanted = planted != null;
					BlockPos rally = planted != null ? planted : target.seat();
					party.rally = rally;
					for (Folk soldier : soldiers) {
						if (!party.holders.contains(soldier.getUUID())) {
							soldier.setHomeTo(rally, SIEGE_RADIUS);
						}
					}
					Banners.tell(level, target.seat(), war, Component.translatable("message.jaymoskingdoms.war.party_arrives", from.name(), target.name())
						.withStyle(ChatFormatting.RED));
					tellPlayers(level, party, Component.translatable("message.jaymoskingdoms.war.party_arrives", from.name(), target.name())
						.withStyle(ChatFormatting.RED));
				}
			}
			case SIEGE -> {
				Economy.noteAttack(server, target, soldiers.getFirst());
				if (now - party.phaseAt > SIEGE_TICKS) {
					seize(level, party, target);
					withdraw(party, soldiers, from, now);
				}
			}
			case RETURN -> {
				for (Folk soldier : soldiers) {
					boolean home = from != null && soldier.distanceToSqr(Vec3.atCenterOf(from.seat())) < HOME_REACH * HOME_REACH;
					if (home || !Raids.playerNear(level, soldier.blockPosition(), VANISH_RANGE)) {
						vanish(level, soldier);
						party.soldiers.remove(soldier.getUUID());
					} else if (soldier.getTarget() == null && from != null) {
						walk(level, soldier, from.seat());
					}
				}
				party.soldiers.removeIf(id -> !(level.getEntity(id) instanceof Folk));
				if (party.soldiers.isEmpty() && cart(level, party) == null) {
					// (the cart out of sight gets home unseen; a broken one's spoils were spilled, and what's left the soldiers carried)
					deliver(level, party, from);
					ACTIVE.remove(key);
				}
			}
		}
	}

	/** The bearer plants the party's banner on the first claimable chunk of the enemy's land he stands on, and some stay to hold it. */
	private static void plantFrontier(ServerLevel level, War war, Party party, List<Folk> soldiers, Faction from, Faction target) {
		MinecraftServer server = level.getServer();
		int enemy = Wars.otherSide(war, party.side);
		for (Folk bearer : soldiers) {
			ChunkPos chunk = ChunkPos.containing(bearer.blockPosition());
			Faction owner = Banners.owner(server, chunk);
			if (owner == null || !Wars.onSide(server, owner.id(), enemy) || !Banners.claimable(server, party.side, chunk)) {
				continue;
			}
			BlockPos planted = Banners.plantFor(level, war, from, owner, false, bearer.blockPosition());
			party.frontierPlanted = true;
			if (planted == null) {
				return;
			}
			int keep = Math.min(BANNER_GUARDS, soldiers.size() - MARCHERS_KEPT);
			for (Folk guard : soldiers) {
				if (keep <= 0) {
					break;
				}
				guard.setHomeTo(planted, HOLD_RADIUS);
				party.holders.add(guard.getUUID());
				keep--;
			}
			return;
		}
	}

	private static void withdraw(Party party, List<Folk> soldiers, @Nullable Faction from, long now) {
		if (party.phase == Phase.RETURN) {
			return;
		}
		party.phase = Phase.RETURN;
		party.phaseAt = now;
		for (Folk soldier : soldiers) {
			if (from != null) {
				soldier.setHomeTo(from.seat(), Folk.HOME_RADIUS);
			}
		}
	}

	private static void walk(ServerLevel level, Folk soldier, BlockPos to) {
		if (!soldier.getNavigation().isDone() && level.getGameTime() % (TICK * 4) != 0) {
			return;
		}
		double dx = to.getX() + 0.5 - soldier.getX(), dz = to.getZ() + 0.5 - soldier.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		BlockPos goal = to;
		if (len > WAYPOINT) {
			BlockPos column = BlockPos.containing(soldier.getX() + dx / len * WAYPOINT, soldier.getY(), soldier.getZ() + dz / len * WAYPOINT);
			goal = level.isLoaded(column) ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column) : column;
		}
		soldier.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, MARCH_SPEED);
	}

	private static void vanish(ServerLevel level, Folk soldier) {
		Cavalry.dismiss(soldier);
		level.sendParticles(ParticleTypes.POOF, soldier.getX(), soldier.getY() + 1.0, soldier.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		soldier.discard();
	}

	private static String name(@Nullable Faction f) {
		return f == null ? "?" : f.name();
	}

	// ---- events ------------------------------------------------------------------------------------------------------

	/** A soldier fell: off his party, and his realm doesn't count the loss (he was never on its roster). */
	static void soldierDied(Folk folk) {
		if (!isSoldier(folk)) {
			return;
		}
		UNDO.add(folk.factionId());
		for (Party party : ACTIVE.values()) {
			party.soldiers.remove(folk.getUUID());
			party.holders.remove(folk.getUUID());
		}
	}

	/** A soldier loaded: if his party is over (or was lost to a restart), he goes home next tick. */
	static void loaded(Entity entity) {
		if (entity.entityTags().contains(CART_TAG)) {
			for (Party party : ACTIVE.values()) {
				if (entity.getUUID().equals(party.carter) || entity.getUUID().equals(party.horse) || entity.getUUID().equals(party.wagon)) {
					return;
				}
			}
			STRAY_CARTS.add(entity);
			return;
		}
		if (entity instanceof Folk folk && isSoldier(folk)) {
			for (Party party : ACTIVE.values()) {
				if (party.soldiers.contains(folk.getUUID())) {
					return;
				}
			}
			STRAYS.add(folk);
		}
	}

	static void clear() {
		ACTIVE.clear();
		LAST.clear();
		UNDO.clear();
		STRAYS.clear();
		STRAY_CARTS.clear();
	}
}
