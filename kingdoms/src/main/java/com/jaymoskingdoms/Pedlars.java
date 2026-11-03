package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.425) Havens patch 1: a tribe's <b>pedlar</b>, a goblin trader with two orc guards at his back, walking from one of
 * his tribe's camps to the next. He meets players out in the tribe's country and sells odd wares, a goblin mask, a map to
 * the king's seat, a truce token ({@link Tribes#token}) and the bestiary ({@link Bestiary}); he'll take tribute toward
 * the tribe's grudge and tell you how the tribe takes to you. His party strikes no one who hasn't struck them first, and
 * no greenskin of any tribe sets on them. Killing him raises the tribe's grudge by {@link #GRUDGE}.
 * <p>The party isn't saved: one left over from before a restart is gone when it loads.
 */
public final class Pedlars {
	// ================================================================================================================
	// Tuning
	/** What killing the pedlar adds to his tribe's grudge. */
	public static final int GRUDGE = 25;
	/** Every {@link Tribes#EVERY} ticks, a player out in a tribe's country (this near one of its camps) may meet him (this likely). */
	static final double COUNTRY = 420.0;
	static final float SPAWN_CHANCE = 0.04F;
	/** At most once in this long for a player (ticks). */
	static final long COOLDOWN = 24000L;
	/** Where he turns up (blocks from the player), and how far off a party goes away when nobody's near. */
	static final int SPAWN_NEAR = 40, SPAWN_FAR = 60;
	static final double GONE_RANGE = 160.0;
	/** How long he's out before heading off for good (once nobody's near), in ticks. */
	static final long LIFE = 24000L;
	/** How near a camp counts as there, and how near a player has to be for him to stop and face them. */
	static final double ARRIVE = 20.0, STOP_FOR = 6.0;
	/** Walking pace (his and his guards'), and how far his guards let him get. */
	static final double PACE = 0.55, GUARD_PACE = 0.7, GUARD_SLACK = 5.0;
	/** Prices (emeralds): his odd wares (each), the mask, a seat map, the bestiary. */
	static final int WARES = 3, WARE_PRICE_MIN = 1, WARE_PRICE_MAX = 4, MASK_PRICE = 10, MAP_PRICE = 8, BESTIARY_PRICE = 6;
	// ================================================================================================================

	/** MCA's talk answers: his wares, the tribe's mood (the pedlar's), tribute (a chief's or the pedlar's). */
	public static final String WARES_ANSWER = "jaymoskingdoms_pedlar_wares", MOOD_ANSWER = "jaymoskingdoms_tribe_mood", TRIBUTE_ANSWER = "jaymoskingdoms_tribute";
	static final String PEDLAR_TAG = "jaymoskingdoms.pedlar", GUARD_TAG = "jaymoskingdoms.pedlar_guard";

	/** Odd things a pedlar may carry (item, how many). */
	private record Ware(Item item, int count) {
	}

	private static final Ware[] ODD = {new Ware(Items.RABBIT_FOOT, 1), new Ware(Items.SPIDER_EYE, 4), new Ware(Items.FERMENTED_SPIDER_EYE, 2),
		new Ware(Items.GLOW_INK_SAC, 3), new Ware(Items.PHANTOM_MEMBRANE, 2), new Ware(Items.NAME_TAG, 1), new Ware(Items.LEAD, 2),
		new Ware(Items.SADDLE, 1), new Ware(Items.AMETHYST_SHARD, 4), new Ware(Items.TURTLE_SCUTE, 1), new Ware(Items.BONE_BLOCK, 2),
		new Ware(Items.SUSPICIOUS_STEW, 1), new Ware(Items.FIRE_CHARGE, 3), new Ware(Items.SLIME_BALL, 4)};

	/** One pedlar's party: his tribe, the camp he's walking to, him and his guards, when it set out, who struck them. */
	private static final class Party {
		final String tribe;
		int goal;
		final UUID pedlar;
		final List<UUID> guards;
		final long started;
		final Set<UUID> struck = new HashSet<>();

		Party(String tribe, int goal, UUID pedlar, List<UUID> guards, long started) {
			this.tribe = tribe;
			this.goal = goal;
			this.pedlar = pedlar;
			this.guards = guards;
			this.started = started;
		}

		boolean has(UUID id) {
			return pedlar.equals(id) || guards.contains(id);
		}
	}

	private static final List<Party> PARTIES = new ArrayList<>();
	/** Player → when they last met one (game time). */
	private static final Map<UUID, Long> MET = new HashMap<>();

	private Pedlars() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if ((entity.entityTags().contains(PEDLAR_TAG) || entity.entityTags().contains(GUARD_TAG)) && find(entity.getUUID()) == null) {
				// (one left from before a restart: gone on his way)
				entity.discard();
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Party p = party(entity) ? find(entity.getUUID()) : null;
			if (p != null && source.getEntity() instanceof LivingEntity attacker && !p.has(attacker.getUUID())) {
				// (whoever struck one of them is a foe to all three)
				p.struck.add(attacker.getUUID());
				if (entity.level() instanceof ServerLevel level) {
					for (PathfinderMob m : members(level, p)) {
						if (m.getTarget() == null) {
							m.setTarget(attacker);
						}
					}
				}
			}
		});
	}

	private static @Nullable Party find(UUID id) {
		for (Party p : PARTIES) {
			if (p.has(id)) {
				return p;
			}
		}
		return null;
	}

	/** {@code e} is a tribe's pedlar. */
	public static boolean pedlar(Entity e) {
		return e.entityTags().contains(PEDLAR_TAG);
	}

	/** {@code e} is a pedlar or one of his guards. */
	public static boolean party(Entity e) {
		return e.entityTags().contains(PEDLAR_TAG) || e.entityTags().contains(GUARD_TAG);
	}

	/** {@code mob}, of a pedlar's party, leaves {@code target} be unless they struck one of the party. */
	public static boolean spares(LivingEntity mob, LivingEntity target) {
		Party p = find(mob.getUUID());
		return p != null && !p.struck.contains(target.getUUID());
	}

	// ---- turning up, walking on, going away ---------------------------------------------------------------------------

	/** (From {@link Tribes}' tick) parties go away when they're done; a player out in a tribe's country may meet one. */
	static void tick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long now = level.getGameTime();
		for (var it = PARTIES.iterator(); it.hasNext();) {
			Party p = it.next();
			List<PathfinderMob> left = members(level, p);
			boolean watched = left.stream().anyMatch(m -> level.getNearestPlayer(m, GONE_RANGE) != null);
			if (left.isEmpty() || !watched || now - p.started > LIFE * 2) {
				left.forEach(Entity::discard);
				it.remove();
			}
		}
		for (ServerPlayer player : level.players()) {
			if (player.isCreative() || player.isSpectator() || !player.isAlive() || now - MET.getOrDefault(player.getUUID(), -COOLDOWN) < COOLDOWN
				|| level.getRandom().nextFloat() >= SPAWN_CHANCE || Factions.around(server, level, player.blockPosition(), Post.TOWN_RANGE) != null
				|| PARTIES.stream().anyMatch(p -> members(level, p).stream().anyMatch(m -> m.distanceTo(player) < GONE_RANGE))) {
				continue;
			}
			Faction from = nearestCamp(server, player.position());
			if (from != null && meet(level, player, from)) {
				MET.put(player.getUUID(), now);
			}
		}
	}

	/** The nearest standing goblin village or orc camp within {@link #COUNTRY} of {@code at}, or null. */
	private static @Nullable Faction nearestCamp(MinecraftServer server, Vec3 at) {
		Faction best = null;
		double bestD = COUNTRY * COUNTRY;
		for (Faction f : Factions.all(server, FactionType.BARBARIAN)) {
			if (f.fallen() || Tribes.of(server, f.id()) == null) {
				continue;
			}
			Camps.CampData data = Camps.data(server, f.id());
			double d = f.seat().distToCenterSqr(at);
			if (data != null && data.clearedAt() < 0 && d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** A pedlar and his guards out of sight of {@code player}, one of {@code camp}'s, on his way to another of its tribe's camps. */
	private static boolean meet(ServerLevel level, ServerPlayer player, Faction camp) {
		String tribe = Tribes.of(level.getServer(), camp.id());
		if (tribe == null) {
			return false;
		}
		RandomSource random = level.getRandom();
		double angle = random.nextDouble() * Math.PI * 2.0;
		int r = SPAWN_NEAR + random.nextInt(SPAWN_FAR - SPAWN_NEAR);
		BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * r, player.getY(), player.getZ() + Math.sin(angle) * r);
		if (!level.isLoaded(column)) {
			return false;
		}
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
		if (!level.getFluidState(ground.below()).isEmpty()) {
			return false;
		}
		PathfinderMob pedlar = Tribes.muster(level, camp, Goblins.GOBLIN, ground, ground);
		if (pedlar == null) {
			return false;
		}
		pedlar.addTag(PEDLAR_TAG);
		List<UUID> guards = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			PathfinderMob guard = Tribes.muster(level, camp, Orcs.GRUNT, ground.offset(i == 0 ? 2 : -2, 0, 1), ground);
			if (guard != null) {
				guard.addTag(GUARD_TAG);
				guards.add(guard.getUUID());
			}
		}
		stock(level, pedlar, tribe);
		PARTIES.add(new Party(tribe, next(level.getServer(), tribe, camp.id(), pedlar.position()), pedlar.getUUID(), guards, level.getGameTime()));
		JaymosKingdoms.LOGGER.info("A pedlar of {} turns up near {}", Tribes.name(tribe).getString(), player.getName().getString());
		return true;
	}

	/** The tribe's camp to walk to next: the nearest other than {@code from} (or {@code from} if it has no other). */
	private static int next(MinecraftServer server, String tribe, int from, Vec3 at) {
		int best = from;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Tribes.camps(server, tribe)) {
			double d = f.seat().distToCenterSqr(at);
			if (f.id() != from && d < bestD) {
				best = f.id();
				bestD = d;
			}
		}
		return best;
	}

	/** His goods: a few odd wares, a goblin mask, a seat map, a truce token, the bestiary. */
	private static void stock(ServerLevel level, PathfinderMob pedlar, String tribe) {
		if (!(pedlar instanceof Villager v)) {
			return;
		}
		RandomSource random = level.getRandom();
		MerchantOffers offers = v.getOffers();
		offers.clear();
		List<Ware> odd = new ArrayList<>(List.of(ODD));
		for (int i = 0; i < WARES && !odd.isEmpty(); i++) {
			Ware w = odd.remove(random.nextInt(odd.size()));
			offers.add(sell(new ItemStack(w.item(), w.count()), WARE_PRICE_MIN + random.nextInt(WARE_PRICE_MAX - WARE_PRICE_MIN + 1), 3));
		}
		if (Disguise.MASK != null) {
			offers.add(sell(new ItemStack(Disguise.MASK), MASK_PRICE, 1));
		}
		Faction seat = Tribes.seat(level.getServer(), tribe);
		if (seat != null) {
			offers.add(sell(Tribes.seatMap(level, tribe, seat), MAP_PRICE, 1));
		}
		offers.add(sell(Tribes.token(tribe), Tribes.TOKEN_PRICE, 1));
		if (Bestiary.ITEM != null) {
			offers.add(sell(new ItemStack(Bestiary.ITEM), BESTIARY_PRICE, 1));
		}
	}

	private static MerchantOffer sell(ItemStack what, int emeralds, int uses) {
		return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), what, uses, 0, 0.05F);
	}

	private static List<PathfinderMob> members(ServerLevel level, Party p) {
		List<PathfinderMob> out = new ArrayList<>();
		if (level.getEntity(p.pedlar) instanceof PathfinderMob m && m.isAlive()) {
			out.add(m);
		}
		for (UUID id : p.guards) {
			if (level.getEntity(id) instanceof PathfinderMob m && m.isAlive()) {
				out.add(m);
			}
		}
		return out;
	}

	/**
	 * (From {@code Workers.step}) one of a pedlar's party between fights: he walks to his tribe's next camp (stopping for a
	 * player near), his guards keep at his back. True if this was their step.
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		Party p = find(mob.getUUID());
		if (p == null || Rulers.target(mob) != null || Rulers.foeNear(level, mob) || McaCompat.busy(mob) || Downed.isDowned(mob)) {
			return false;
		}
		if (mob.tickCount % 100 == 0) {
			// (wherever they are on the road is home for now: MCA's brain won't drag them back)
			mob.setHomeTo(mob.blockPosition(), Raids.RAID_HOME_RADIUS);
		}
		if (!p.pedlar.equals(mob.getUUID())) {
			Entity pedlar = level.getEntity(p.pedlar);
			if (!(pedlar instanceof PathfinderMob boss) || !boss.isAlive()) {
				return false;
			}
			if (mob.distanceTo(boss) > GUARD_SLACK) {
				if (mob.tickCount % 20 == 0 || mob.getNavigation().isDone()) {
					mob.getNavigation().moveTo(boss, GUARD_PACE);
				}
			} else {
				mob.getNavigation().stop();
			}
			return true;
		}
		ServerPlayer near = (ServerPlayer) level.getNearestPlayer(mob, STOP_FOR);
		if (near != null || mob instanceof Villager v && v.getTradingPlayer() != null) {
			// (a customer: he stops and faces them)
			mob.getNavigation().stop();
			if (near != null) {
				mob.getLookControl().setLookAt(near, 30.0F, 30.0F);
			}
			return true;
		}
		Faction goal = Factions.get(level.getServer(), p.goal);
		if (goal == null || goal.fallen()) {
			p.goal = next(level.getServer(), p.tribe, p.goal, mob.position());
			return true;
		}
		BlockPos to = goal.seat();
		if (to.distToCenterSqr(mob.position()) < ARRIVE * ARRIVE) {
			// (there: on to the next)
			p.goal = next(level.getServer(), p.tribe, p.goal, mob.position());
			return true;
		}
		if (mob.tickCount % 40 == 0 || mob.getNavigation().isDone()) {
			Vec3 at = mob.position(), toward = Vec3.atBottomCenterOf(to).subtract(at);
			// (a leg at a time: the whole way is past what a path reaches)
			Vec3 leg = toward.length() > 32.0 ? at.add(toward.normalize().scale(32.0)) : Vec3.atBottomCenterOf(to);
			mob.getNavigation().moveTo(leg.x, leg.y, leg.z, PACE);
		}
		return true;
	}

	// ---- his talk -------------------------------------------------------------------------------------------------------

	/** (From MCA's menu, "Show me your wares") his trading screen. */
	public static void trade(ServerPlayer player, LivingEntity pedlar) {
		if (pedlar instanceof Villager v && v.getTradingPlayer() == null) {
			v.setTradingPlayer(player);
			v.openTradingScreen(player, v.getDisplayName(), 1);
		}
	}

	/** (From MCA's menu, "How does the tribe take to me?") the tribe's mood, as the pedlar puts it. */
	public static Component mood(ServerPlayer player, LivingEntity pedlar) {
		String tribe = Tribes.of(pedlar);
		return tribe == null ? Component.empty() : Tribes.mood(player, tribe, pedlar);
	}
}
