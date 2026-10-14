package com.jaymoskingdoms;

import com.jaymoskingdoms.mixin.ChestedHorseInvoker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.13) Roaming caravans: our own take on Pillager Caravans, riding on the {@link Caravans} machinery. Now and then,
 * out in the wild near a player, a caravan comes along the road (a {@link Roads King's Road} where one runs that way,
 * else straight across the land), passes him by, and goes on out of sight. Two kinds:
 *
 * <p><b>Pillager caravans (hostile).</b> Pillagers and vindicators leading pack animals (donkeys, mules, or llamas in
 * the cold) whose chests hold what they plundered, the goods of the land they cross ({@link #cargo}). A minor caravan has
 * a caravan master and a few raiders; a major one a captain under the ominous banner, more of them, a ravager with a
 * rider, more animals and a strongbox of riches. They fight whoever comes near, as illagers do, merchants included. Kill
 * the one leading an animal and it's free (its leash drops): tame it and open its chest, or kill it for its load. Break
 * the whole caravan and every realm nearby thinks better of you ({@link #REP_BREAK}). When an illager camp stands near,
 * the caravan is bound there with its plunder.
 *
 * <p><b>Merchant caravans (neutral).</b> A travelling merchant (a wandering trader, who trades as ever, plus a few of
 * the land's goods) with trader llamas and pack mules, and, near a realm, a few of its guards hired as escort (mounted
 * when it is prosperous). They mind their own business. Strike the merchant or his animals and the escort turns on you
 * and the realm that hired them thinks the worse of you ({@link #REP_ROB}); their chests are there for the taking if you
 * win. Pillager caravans fall on them when they meet.
 *
 * <p>Nothing of a roaming caravan is saved but when the next may come ({@link Ledger}): it lives while a player is near
 * and is gone ({@link #DESPAWN_RANGE}) when none is, or when it reaches its goal; its leftovers in unloaded chunks are
 * cleared when they load again, as {@link Caravans}' are. Animals set free belong to the world.
 */
final class RoamingCaravans {
	/** How often caravans are moved on (ticks), and how often a new one may come. */
	static final int TICK = 20, SPAWN_CHECK = 1200;
	/** The chance, each spawn check that one is due, that a caravan comes. */
	static final float SPAWN_CHANCE = 0.2F;
	/** After a minor pillager caravan, a major one, a merchant caravan: none of that kind for this long (ticks). */
	static final long MINOR_COOLDOWN = 24000L, MAJOR_COOLDOWN = 72000L, MERCHANT_COOLDOWN = 18000L;
	/** The share of pillager caravans that are major (when one is due). */
	static final float MAJOR_SHARE = 0.35F;
	/** It comes into the world this far from the player (blocks), and makes for a point this far beyond him. */
	static final int SPAWN_NEAR = 64, SPAWN_FAR = 96;
	static final double TRAVEL = 420.0;
	/** An illager camp (or for merchants, a realm) this near its way becomes its goal. */
	static final double CAMP_GOAL_RANGE = 700.0, REALM_GOAL_RANGE = 900.0;
	/** Gone when no player is this near; gone after this long anyway (ticks). */
	static final double DESPAWN_RANGE = 144.0;
	static final long LONGEST = 24000L;
	/** It makes for a point this far ahead on its way; arrived this close to its goal. */
	static final double WAYPOINT = 24.0, ARRIVE = 16.0;
	/** No nearer its goal for this long (ticks), and it's helped this far along (only when no player is near to see). */
	static final int STUCK_TICKS = 400;
	static final double HOP = 8.0, HOP_HIDE = 32.0;
	/** A pack animal further than this from its leader is brought back to him (the leash would snap at 12), looked to this often (ticks). */
	static final double LEASH_SNAP = 8.0;
	static final int LEASH_TICK = 5;
	/** A minor pillager caravan: raiders, animals; a major one: raiders, animals (and a ravager with a rider, and a strongbox). */
	static final int MINOR_PILLAGERS = 2, MINOR_VINDICATORS = 1, MINOR_ANIMALS = 2;
	static final int MAJOR_PILLAGERS = 3, MAJOR_VINDICATORS = 2, MAJOR_ANIMALS = 4;
	/** A merchant caravan: trader llamas, pack mules, and the escort a realm this near hires out (more when major). */
	static final int MERCHANT_LLAMAS = 2, MERCHANT_MULES = 2, ESCORT_GUARDS = 2;
	static final double ESCORT_RANGE = 640.0;
	/** Rolls of goods in each pack animal's chest. */
	static final int CARGO_ROLLS = 6, STRONGBOX_ROLLS = 5;
	/** Extra trades the merchant offers from his goods. */
	static final int MERCHANT_TRADES = 3;
	/** Reputation: breaking a pillager caravan (with every realm within {@link #REWARD_RANGE}); striking a merchant's caravan. */
	static final int REP_BREAK = 15, REP_ROB = -40;
	static final double REWARD_RANGE = 320.0;
	/** Players this near hear of a caravan coming. */
	static final double TELL_RANGE = 128.0;

	static final String TAG = "jaymoskingdoms.roaming", TAG_OF = "jaymoskingdoms.roaming_of.";

	enum Kind {
		PILLAGER, PILLAGER_MAJOR, MERCHANT;

		boolean hostile() {
			return this != MERCHANT;
		}
	}

	/** When each kind may next come. */
	record Ledger(Map<String, Long> next) {
		static final Ledger EMPTY = new Ledger(Map.of());
		static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("next", Map.of()).forGetter(Ledger::next))
			.apply(i, Ledger::new));
	}

	static final AttachmentType<Ledger> LEDGER = AttachmentRegistry.create(JaymosKingdoms.id("roaming_caravans"),
		builder -> builder.persistent(Ledger.CODEC));

	/** A caravan in the world: its leader, its fighters (illagers, or a merchant's escort), its animals and who leads each. */
	private static final class Group {
		final int id;
		final Kind kind;
		final String stamp;
		final BlockPos goal;
		final long started;
		final @Nullable Integer realm;
		@Nullable UUID leader;
		final List<UUID> fighters = new ArrayList<>();
		final Map<UUID, UUID> animals = new HashMap<>();
		final Set<UUID> hitters = new HashSet<>();
		final Set<UUID> offenders = new HashSet<>();
		double best = Double.MAX_VALUE;
		long improvedAt;
		/** The merchant fell: only his escort is left, fighting. */
		boolean ending;

		Group(int id, Kind kind, String stamp, BlockPos goal, long started, @Nullable Integer realm) {
			this.id = id;
			this.kind = kind;
			this.stamp = stamp;
			this.goal = goal;
			this.started = started;
			this.realm = realm;
			this.improvedAt = started;
		}
	}

	private static final Map<Integer, Group> LIVE = new HashMap<>();
	private static int nextId = 1;

	private RoamingCaravans() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			long now = level.getGameTime();
			if (now % TICK == 0) {
				tick(level, now);
			}
			if (now % LEASH_TICK == 0) {
				for (Group g : List.copyOf(LIVE.values())) {
					tend(level, g);
				}
			}
			if (now % SPAWN_CHECK == 0) {
				maybeSpawn(level, now);
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> loaded(entity));
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> hurt(entity, source));
		ServerLivingEntityEvents.AFTER_DEATH.register(RoamingCaravans::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LIVE.clear());
	}

	// ---- coming ----------------------------------------------------------------------------------------------------

	private static void maybeSpawn(ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		if (random.nextFloat() >= SPAWN_CHANCE) {
			return;
		}
		List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator() && p.isAlive()).toList();
		if (players.isEmpty()) {
			return;
		}
		ServerPlayer player = players.get(random.nextInt(players.size()));
		if (LIVE.values().stream().anyMatch(g -> g.leader != null && level.getEntity(g.leader) instanceof Entity e
			&& e.distanceTo(player) < DESPAWN_RANGE)) {
			return;
		}
		Ledger l = level.getAttachedOrElse(LEDGER, Ledger.EMPTY);
		List<Kind> due = new ArrayList<>();
		for (Kind k : Kind.values()) {
			if (now >= l.next().getOrDefault(k.name(), 0L) && (!k.hostile() || level.getDifficulty() != Difficulty.PEACEFUL)) {
				due.add(k);
			}
		}
		if (due.isEmpty()) {
			return;
		}
		Kind kind;
		if (due.contains(Kind.MERCHANT) && (random.nextBoolean() || due.size() == 1)) {
			kind = Kind.MERCHANT;
		} else if (due.contains(Kind.PILLAGER_MAJOR) && (random.nextFloat() < MAJOR_SHARE || !due.contains(Kind.PILLAGER))) {
			kind = Kind.PILLAGER_MAJOR;
		} else {
			kind = due.contains(Kind.PILLAGER) ? Kind.PILLAGER : Kind.MERCHANT;
		}
		spawn(level, player, kind);
	}

	/** A caravan of {@code kind} comes into sight of {@code player} (and that kind waits its time again). Null if there was no room. */
	static @Nullable String spawn(ServerLevel level, ServerPlayer player, Kind kind) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		BlockPos spot = null;
		Vec3 dir = null;
		for (int tries = 0; tries < 8 && spot == null; tries++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
			double far = SPAWN_NEAR + random.nextInt(SPAWN_FAR - SPAWN_NEAR + 1);
			BlockPos at = BlockPos.containing(player.position().add(dir.scale(far)));
			if (!level.isPositionEntityTicking(at)) {
				continue;
			}
			BlockPos ground = Caravans.ground(level, Vec3.atBottomCenterOf(at));
			if (!level.getFluidState(ground).isEmpty() || !level.getFluidState(ground.below()).isEmpty()
				|| Factions.at(server, level, ground) != null) {
				continue;
			}
			spot = ground;
		}
		if (spot == null) {
			return null;
		}
		// its way: across past the player, sideways a little, on to a point well beyond (or a camp, or a realm, that way)
		Vec3 across = dir.scale(-1.0);
		Vec3 side = new Vec3(-across.z, 0.0, across.x).scale((random.nextDouble() - 0.5) * 32.0);
		BlockPos goal = BlockPos.containing(Vec3.atBottomCenterOf(spot).add(across.scale(SPAWN_FAR + TRAVEL)).add(side));
		Faction toward = toward(server, spot, across, kind);
		if (toward != null) {
			goal = Caravans.start(toward);
		}
		Faction realm = null;
		if (kind == Kind.MERCHANT) {
			realm = nearestRealm(server, spot, ESCORT_RANGE);
		}
		int id = nextId++;
		Group g = new Group(id, kind, TAG_OF + id + "." + Long.toHexString(random.nextLong()), goal, level.getGameTime(),
			realm == null ? null : realm.id());
		LIVE.put(g.id, g);
		Vec3 facing = Vec3.atBottomCenterOf(goal).subtract(Vec3.atBottomCenterOf(spot)).multiply(1.0, 0.0, 1.0);
		facing = facing.length() < 1.0E-3 ? across : facing.normalize();
		Climate climate = Climate.at(level, spot);
		if (kind.hostile()) {
			pillagers(level, g, spot, facing, climate);
		} else {
			merchants(level, g, spot, facing, climate, realm);
		}
		if (g.leader == null) {
			vanish(level, g);
			return null;
		}
		Ledger l = level.getAttachedOrElse(LEDGER, Ledger.EMPTY);
		Map<String, Long> next = new HashMap<>(l.next());
		long now = level.getGameTime();
		next.put(kind.name(), now + switch (kind) {
			case PILLAGER -> MINOR_COOLDOWN;
			case PILLAGER_MAJOR -> MAJOR_COOLDOWN;
			case MERCHANT -> MERCHANT_COOLDOWN;
		});
		if (kind == Kind.PILLAGER_MAJOR) {
			// (no minor one hard on a major one's heels)
			next.merge(Kind.PILLAGER.name(), now + MINOR_COOLDOWN, Math::max);
		}
		level.setAttached(LEDGER, new Ledger(next));
		String key = "message.jaymoskingdoms.roaming." + kind.name().toLowerCase(java.util.Locale.ROOT);
		Component heard = realm != null
			? Component.translatable(key + "_escorted", realm.name()) : Component.translatable(key);
		for (ServerPlayer p : level.players()) {
			if (p.blockPosition().closerThan(spot, TELL_RANGE)) {
				p.sendSystemMessage(heard.copy().withStyle(kind.hostile() ? ChatFormatting.RED : ChatFormatting.GOLD));
			}
		}
		JaymosKingdoms.LOGGER.info("A {} caravan comes near {} at {}, bound for {}", kind, player.getName().getString(), spot.toShortString(),
			goal.toShortString());
		return kind.name();
	}

	/** Where along {@code across} from {@code from} a caravan of {@code kind} is bound: an illager camp for pillagers, a realm for merchants. */
	private static @Nullable Faction toward(MinecraftServer server, BlockPos from, Vec3 across, Kind kind) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen()) {
				continue;
			}
			boolean fits = kind.hostile() ? f.type() == FactionType.BARBARIAN && IllagerCamps.is(server, f.id()) : f.type().isRealm();
			double range = kind.hostile() ? CAMP_GOAL_RANGE : REALM_GOAL_RANGE;
			if (!fits) {
				continue;
			}
			Vec3 to = Vec3.atBottomCenterOf(f.seat()).subtract(Vec3.atBottomCenterOf(from)).multiply(1.0, 0.0, 1.0);
			double d = to.length();
			// (ahead, roughly: within 50 degrees of its way, and far enough to pass the player)
			if (d < SPAWN_FAR + 32.0 || d > range || to.normalize().dot(across) < 0.64) {
				continue;
			}
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	private static @Nullable Faction nearestRealm(MinecraftServer server, BlockPos at, double range) {
		Faction best = null;
		double bestD = range;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !f.type().isRealm()) {
				continue;
			}
			double d = Math.sqrt(f.seat().distSqr(at));
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	/** A pillager caravan: its leader (and his banner), its raiders, its pack animals each led by one of them. */
	private static void pillagers(ServerLevel level, Group g, BlockPos spot, Vec3 facing, Climate climate) {
		RandomSource random = level.getRandom();
		boolean major = g.kind == Kind.PILLAGER_MAJOR;
		Mob leader = illager(level, g, EntityTypes.PILLAGER, spot, facing, 0.0, 0.0);
		if (leader == null) {
			return;
		}
		g.leader = leader.getUUID();
		((PatrollingMonster)leader).setPatrolLeader(true);
		leader.setCustomName(Component.translatable(major ? "entity.jaymoskingdoms.caravan_captain" : "entity.jaymoskingdoms.caravan_master"));
		if (major) {
			leader.setItemSlot(EquipmentSlot.HEAD, Raid.getOminousBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
			leader.setDropChance(EquipmentSlot.HEAD, 1.0F);
		}
		List<Mob> leaders = new ArrayList<>(List.of(leader));
		int pillagers = major ? MAJOR_PILLAGERS : MINOR_PILLAGERS, vindicators = major ? MAJOR_VINDICATORS : MINOR_VINDICATORS;
		int n = 1;
		for (int i = 0; i < pillagers; i++, n++) {
			Mob m = illager(level, g, EntityTypes.PILLAGER, spot, facing, side(n), -back(n));
			if (m != null) {
				leaders.add(m);
			}
		}
		for (int i = 0; i < vindicators; i++, n++) {
			illager(level, g, EntityTypes.VINDICATOR, spot, facing, side(n), -back(n));
		}
		if (major) {
			Mob ravager = illager(level, g, EntityTypes.RAVAGER, spot, facing, 0.0, -back(n + 2));
			Mob rider = illager(level, g, EntityTypes.PILLAGER, spot, facing, 0.0, -back(n + 2));
			if (ravager != null && rider != null) {
				rider.startRiding(ravager);
			}
		}
		int animals = major ? MAJOR_ANIMALS : MINOR_ANIMALS - (random.nextInt(3) == 0 ? 1 : 0);
		for (int i = 0; i < animals; i++) {
			Mob holder = leaders.get(i % leaders.size());
			pack(level, g, holder, climate, major && i == 0, false);
		}
	}

	/** A merchant caravan: the merchant, his llamas and mules, and his escort. */
	private static void merchants(ServerLevel level, Group g, BlockPos spot, Vec3 facing, Climate climate, @Nullable Faction realm) {
		WanderingTrader trader = EntityTypes.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
		if (trader == null) {
			return;
		}
		place(trader, spot, facing);
		trader.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
		trader.setDespawnDelay((int)LONGEST + 2400);
		trader.setPersistenceRequired();
		trader.setCustomName(Component.translatable("entity.jaymoskingdoms.caravan_merchant"));
		tag(trader, g);
		level.addFreshEntity(trader);
		g.leader = trader.getUUID();
		goods(level, trader, climate);
		for (int i = 0; i < MERCHANT_LLAMAS; i++) {
			pack(level, g, trader, climate, false, true);
		}
		for (int i = 0; i < MERCHANT_MULES; i++) {
			pack(level, g, trader, climate, false, false);
		}
		if (realm == null) {
			return;
		}
		boolean mounted = Wealth.atLeast(level.getServer(), realm, Wealth.Tier.PROSPEROUS);
		for (int i = 0; i < ESCORT_GUARDS; i++) {
			BlockPos at = Caravans.ground(level, Vec3.atBottomCenterOf(spot).add(right(facing).scale(side(i + 1))).subtract(facing.scale(back(i + 1))));
			Folk guard = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
			if (guard == null) {
				continue;
			}
			place(guard, at, facing);
			guard.setup(Folk.GUARD, realm, at, 64);
			guard.setTask(Caravans.TASK);
			tag(guard, g);
			level.addFreshEntity(guard);
			if (mounted) {
				AbstractHorse horse = Cavalry.mount(level, guard, realm, true);
				if (horse != null) {
					tag(horse, g);
				}
			}
			g.fighters.add(guard.getUUID());
		}
	}

	private static double side(int n) {
		return n % 2 == 0 ? 2.5 : -2.5;
	}

	private static double back(int n) {
		return (n + 1) / 2 * 3.0;
	}

	private static Vec3 right(Vec3 facing) {
		return new Vec3(-facing.z, 0.0, facing.x);
	}

	private static void place(Entity e, BlockPos at, Vec3 facing) {
		float yaw = (float)(Math.atan2(facing.z, facing.x) * (180.0 / Math.PI)) - 90.0F;
		e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
		e.setYHeadRot(yaw);
	}

	private static void tag(Entity e, Group g) {
		e.addTag(TAG);
		e.addTag(g.stamp);
	}

	/** One of a pillager caravan's illagers (or its ravager), {@code side} and {@code ahead} of the caravan's spot. */
	private static @Nullable Mob illager(ServerLevel level, Group g, EntityType<? extends Mob> type, BlockPos spot, Vec3 facing, double side,
		double ahead) {
		Mob mob = type.create(level, EntitySpawnReason.EVENT);
		if (mob == null) {
			return null;
		}
		BlockPos at = Caravans.ground(level, Vec3.atBottomCenterOf(spot).add(right(facing).scale(side)).add(facing.scale(ahead)));
		place(mob, at, facing);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
			raider.setPatrolLeader(false);
		}
		mob.setPersistenceRequired();
		mob.setCanPickUpLoot(false);
		tag(mob, g);
		level.addFreshEntity(mob);
		g.fighters.add(mob.getUUID());
		return mob;
	}

	/** A pack animal (a llama in the cold or a merchant's, else a donkey or mule) with a loaded chest, on {@code holder}'s leash. */
	private static void pack(ServerLevel level, Group g, Mob holder, Climate climate, boolean strongbox, boolean llama) {
		RandomSource random = level.getRandom();
		boolean cold = climate == Climate.SNOWY || climate == Climate.TAIGA;
		EntityType<? extends AbstractChestedHorse> type = llama
			? EntityTypes.TRADER_LLAMA
			: cold ? EntityTypes.LLAMA : random.nextBoolean() ? EntityTypes.DONKEY : EntityTypes.MULE;
		AbstractChestedHorse animal = type.create(level, EntitySpawnReason.EVENT);
		if (animal == null) {
			return;
		}
		BlockPos at = Caravans.ground(level, holder.position().add(random.nextDouble() * 2.0 - 1.0, 0.0, random.nextDouble() * 2.0 - 1.0));
		animal.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, holder.getYRot(), 0.0F);
		animal.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		animal.setPersistenceRequired();
		animal.setChest(true);
		((ChestedHorseInvoker)animal).jaymoskingdoms$createInventory();
		List<ItemStack> load = cargo(random, climate, g.kind, strongbox);
		int size = animal.getInventorySize();
		for (int i = 0; i < load.size() && i < size; i++) {
			animal.getSlot(AbstractHorse.INVENTORY_SLOT_OFFSET + i).set(load.get(i));
		}
		tag(animal, g);
		level.addFreshEntity(animal);
		animal.setLeashedTo(holder, true);
		g.animals.put(animal.getUUID(), holder.getUUID());
	}

	// ---- their goods --------------------------------------------------------------------------------------------------

	/** One kind of goods: the item and how many to a stack (at least, at most). */
	private record Good(Item item, int least, int most) {
	}

	private static final Good[] PLUNDER = {
		new Good(Items.WHEAT, 8, 24), new Good(Items.BREAD, 4, 12), new Good(Items.POTATO, 6, 16), new Good(Items.CARROT, 6, 16),
		new Good(Items.LEATHER, 3, 8), new Good(Items.IRON_INGOT, 2, 6), new Good(Items.ARROW, 8, 24), new Good(Items.EMERALD, 1, 5),
		new Good(Items.STRING, 4, 10), new Good(Items.WOOL.pick(DyeColor.WHITE), 4, 12), new Good(Items.COAL, 6, 16), new Good(Items.TORCH, 8, 16)};

	private static final Good[] TRADE = {
		new Good(Items.PAPER, 8, 24), new Good(Items.BOOK, 2, 6), new Good(Items.GLASS_BOTTLE, 4, 12), new Good(Items.CANDLE, 2, 6),
		new Good(Items.DYE.pick(DyeColor.RED), 4, 10), new Good(Items.DYE.pick(DyeColor.BLUE), 4, 10), new Good(Items.DYE.pick(DyeColor.YELLOW), 4, 10), new Good(Items.LANTERN, 1, 3),
		new Good(Items.EMERALD, 2, 6), new Good(Items.HONEY_BOTTLE, 2, 5)};

	private static final Good[] RICHES = {
		new Good(Items.EMERALD, 8, 20), new Good(Items.GOLD_INGOT, 3, 9), new Good(Items.IRON_INGOT, 6, 14), new Good(Items.SADDLE, 1, 1),
		new Good(Items.NAME_TAG, 1, 1), new Good(Items.EXPERIENCE_BOTTLE, 3, 8), new Good(Items.GOLDEN_APPLE, 1, 2)};

	/** The land's own goods: what its people grow, gather and make. */
	private static Good[] land(Climate climate) {
		return switch (climate) {
			case JUNGLE -> new Good[] {new Good(Items.COCOA_BEANS, 6, 16), new Good(Items.BAMBOO, 8, 24), new Good(Items.MELON_SLICE, 8, 20),
				new Good(Items.JUNGLE_SAPLING, 1, 4), new Good(Items.GOLD_INGOT, 1, 4), new Good(Items.GLOW_BERRIES, 4, 10)};
			case DESERT, BADLANDS -> new Good[] {new Good(Items.SUGAR_CANE, 8, 24), new Good(Items.CACTUS, 4, 12), new Good(Items.RABBIT_HIDE, 3, 8),
				new Good(Items.GOLD_INGOT, 1, 5), new Good(Items.GOLDEN_CARROT, 2, 6), new Good(Items.DRIED_KELP, 8, 16)};
			case SNOWY, TAIGA -> new Good[] {new Good(Items.COD, 4, 12), new Good(Items.SALMON, 4, 12), new Good(Items.SWEET_BERRIES, 6, 16),
				new Good(Items.COOKED_MUTTON, 3, 8), new Good(Items.SPRUCE_LOG, 8, 16), new Good(Items.RABBIT_HIDE, 3, 8)};
			case SAVANNA -> new Good[] {new Good(Items.ACACIA_LOG, 8, 16), new Good(Items.MELON_SLICE, 6, 16), new Good(Items.PUMPKIN, 2, 6),
				new Good(Items.BEEF, 4, 10), new Good(Items.LEATHER, 4, 10)};
			case SWAMP -> new Good[] {new Good(Items.SLIME_BALL, 2, 6), new Good(Items.LILY_PAD, 4, 10), new Good(Items.BROWN_MUSHROOM, 4, 10),
				new Good(Items.CLAY_BALL, 8, 16), new Good(Items.MANGROVE_PROPAGULE, 1, 4)};
			case CHERRY -> new Good[] {new Good(Items.CHERRY_SAPLING, 1, 3), new Good(Items.HONEYCOMB, 3, 8), new Good(Items.PINK_PETALS, 4, 12),
				new Good(Items.CHERRY_LOG, 8, 16)};
			case PLAINS -> new Good[] {new Good(Items.WHEAT, 12, 32), new Good(Items.APPLE, 4, 10), new Good(Items.BREAD, 6, 12),
				new Good(Items.OAK_LOG, 8, 16), new Good(Items.IRON_INGOT, 2, 5)};
		};
	}

	/** What one pack animal carries: the land's goods and (pillagers) plunder or (merchants) trade goods; a strongbox's riches on top. */
	static List<ItemStack> cargo(RandomSource random, Climate climate, Kind kind, boolean strongbox) {
		List<ItemStack> load = new ArrayList<>();
		Good[] land = land(climate);
		Good[] general = kind.hostile() ? PLUNDER : TRADE;
		for (int i = 0; i < CARGO_ROLLS; i++) {
			load.add(roll(random, random.nextInt(5) < 2 ? land : general));
		}
		if (strongbox) {
			for (int i = 0; i < STRONGBOX_ROLLS; i++) {
				load.add(roll(random, RICHES));
			}
			if (random.nextInt(4) == 0) {
				load.add(new ItemStack(Items.DIAMOND, 1 + random.nextInt(2)));
			}
			if (random.nextInt(10) == 0) {
				load.add(new ItemStack(Items.TOTEM_OF_UNDYING));
			}
		}
		return load;
	}

	private static ItemStack roll(RandomSource random, Good[] goods) {
		Good g = goods[random.nextInt(goods.length)];
		return new ItemStack(g.item(), g.least() + random.nextInt(g.most() - g.least() + 1));
	}

	/** The merchant offers a few of his land's goods besides his usual wares. */
	private static void goods(ServerLevel level, WanderingTrader trader, Climate climate) {
		RandomSource random = level.getRandom();
		Good[] land = land(climate);
		Set<Item> offered = new HashSet<>();
		for (int i = 0; i < MERCHANT_TRADES; i++) {
			Good g = land[random.nextInt(land.length)];
			if (!offered.add(g.item())) {
				continue;
			}
			trader.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1 + random.nextInt(3)), new ItemStack(g.item(), g.most()),
				8, 1, 0.05F));
		}
	}

	// ---- on the way -------------------------------------------------------------------------------------------------

	private static void tick(ServerLevel level, long now) {
		for (Group g : List.copyOf(LIVE.values())) {
			tick(level, g, now);
		}
	}

	private static void tick(ServerLevel level, Group g, long now) {
		// the fighters still standing
		List<Mob> fighters = new ArrayList<>();
		for (UUID id : g.fighters) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive()) {
				fighters.add(m);
			}
		}
		Entity leader = g.leader == null ? null : level.getEntity(g.leader);
		if (leader != null && !leader.isAlive()) {
			leader = null;
		}
		if (g.kind.hostile()) {
			if (fighters.isEmpty()) {
				// (gone with their chunk: those who fell are seen to by died)
				vanish(level, g);
				return;
			}
			if (leader == null) {
				// the next takes the lead
				Mob next = fighters.stream().filter(m -> m instanceof PatrollingMonster && !m.isPassenger()).findFirst().orElse(fighters.getFirst());
				g.leader = next.getUUID();
				if (next instanceof PatrollingMonster p) {
					p.setPatrolLeader(true);
				}
				leader = next;
			}
		} else if (g.ending) {
			// the escort fights on, then goes home
			int left = 0;
			for (Mob m : fighters) {
				if (m.getTarget() == null || !m.getTarget().isAlive()) {
					dismiss(level, m);
				} else {
					left++;
				}
			}
			if (left == 0) {
				LIVE.remove(g.id);
			}
			return;
		}
		if (leader == null || !level.isPositionEntityTicking(leader.blockPosition())) {
			// (gone with its chunk: on its way unseen)
			vanish(level, g);
			return;
		}
		// nobody about, too long on the way, or there: gone
		Entity lead = leader;
		boolean watched = level.players().stream().anyMatch(p -> !p.isSpectator() && p.distanceTo(lead) < DESPAWN_RANGE);
		Vec3 to = Vec3.atBottomCenterOf(g.goal);
		double d = Math.sqrt(leader.distanceToSqr(to.x, leader.getY(), to.z));
		if (!watched || now - g.started > LONGEST || d <= ARRIVE) {
			vanish(level, g);
			return;
		}
		boolean fighting = fighters.stream().anyMatch(m -> m.getTarget() != null && m.getTarget().isAlive());
		if (d < g.best - 0.5 || fighting) {
			g.best = Math.min(g.best, d);
			g.improvedAt = now;
		} else if (now - g.improvedAt > STUCK_TICKS) {
			Entity l = leader;
			if (level.players().stream().noneMatch(p -> p.distanceTo(l) < HOP_HIDE)) {
				hop(level, g, leader, fighters, to);
			}
			g.improvedAt = now;
			g.best = Double.MAX_VALUE;
		}
		// on toward its goal, along the road where there is one
		Vec3 here = leader.position();
		Vec3 road = Roads.ahead(level, here, g.goal, WAYPOINT);
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		Vec3 ahead = road != null ? road : flat.length() > WAYPOINT ? here.add(flat.normalize().scale(WAYPOINT)) : to;
		BlockPos next = BlockPos.containing(ahead);
		if (level.isLoaded(next)) {
			next = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, next);
		}
		if (g.kind.hostile()) {
			for (Mob m : fighters) {
				if (m instanceof PatrollingMonster p) {
					p.setPatrolTarget(next);
				}
			}
		} else {
			WanderingTrader trader = (WanderingTrader)leader;
			trader.setWanderTarget(next);
			trader.setDespawnDelay(Math.max(trader.getDespawnDelay(), 2400));
			Vec3 dir = new Vec3(next.getX() + 0.5 - here.x, 0.0, next.getZ() + 0.5 - here.z);
			Vec3 facing = dir.length() < 1.0E-3 ? Vec3.ZERO : dir.normalize();
			for (int i = 0; i < fighters.size(); i++) {
				if (fighters.get(i) instanceof Folk guard) {
					Caravans.steer(guard, here.add(right(facing).scale(side(i + 1) * 1.4)).add(facing.scale(i == 0 ? 3.0 : -3.0)));
					guard.setHomeTo(guard.blockPosition(), 64);
				}
			}
		}
	}

	/** Its pack animals kept on their leads: one too far is brought back, one whose leader fell goes free. */
	private static void tend(ServerLevel level, Group g) {
		for (Map.Entry<UUID, UUID> e : List.copyOf(g.animals.entrySet())) {
			Entity a = level.getEntity(e.getKey());
			if (!(a instanceof Mob animal) || !animal.isAlive()) {
				g.animals.remove(e.getKey());
				continue;
			}
			Entity holder = level.getEntity(e.getValue());
			if (holder == null || !holder.isAlive()) {
				release(animal);
				g.animals.remove(e.getKey());
				continue;
			}
			if (animal.distanceTo(holder) > LEASH_SNAP) {
				animal.teleportTo(holder.getX(), holder.getY(), holder.getZ());
				animal.getNavigation().stop();
			}
			if (!animal.isLeashed()) {
				animal.setLeashedTo(holder, true);
			}
		}
	}

	/** Helped over what stops it, the whole caravan with it. */
	private static void hop(ServerLevel level, Group g, Entity leader, List<Mob> fighters, Vec3 to) {
		Vec3 here = leader.position();
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		double len = flat.length();
		if (len < 1.0E-3) {
			return;
		}
		Vec3 by = flat.scale(Math.min(HOP, len) / len);
		List<Entity> all = new ArrayList<>(fighters);
		all.add(leader);
		for (UUID id : g.animals.keySet()) {
			Entity a = level.getEntity(id);
			if (a != null) {
				all.add(a);
			}
		}
		for (Entity e : all) {
			if (e.isPassenger() || !e.isAlive()) {
				continue;
			}
			BlockPos at = Caravans.ground(level, e.position().add(by));
			Cavalry.teleport(e, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		}
	}

	// ---- trouble ------------------------------------------------------------------------------------------------------

	private static @Nullable Group of(Entity e) {
		if (!e.entityTags().contains(TAG)) {
			return null;
		}
		for (Group g : LIVE.values()) {
			if (e.entityTags().contains(g.stamp)) {
				return g;
			}
		}
		return null;
	}

	/** One of a caravan's people or animals hurt: a player striking illagers is noted; striking a merchant's caravan is a crime. */
	private static void hurt(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || !(source.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		Group g = of(victim);
		if (g == null) {
			return;
		}
		if (g.kind.hostile()) {
			g.hitters.add(player.getUUID());
			return;
		}
		if (victim instanceof Folk) {
			// (its own guards see to that, as any realm's do)
			return;
		}
		for (UUID id : g.fighters) {
			if (level.getEntity(id) instanceof Mob m && m.isAlive() && (m.getTarget() == null || !m.getTarget().isAlive())) {
				m.setTarget(player);
			}
		}
		if (g.offenders.add(player.getUUID()) && g.realm != null) {
			Faction realm = Factions.get(level.getServer(), g.realm);
			if (realm != null) {
				Reputation.change(player, realm, REP_ROB);
				Economy.noteAttack(level.getServer(), realm, player);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.roaming.merchant_struck", realm.name())
					.withStyle(ChatFormatting.RED));
			}
		}
	}

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Group g = of(entity);
		if (g == null) {
			return;
		}
		if (source.getEntity() instanceof ServerPlayer player && g.kind.hostile()) {
			g.hitters.add(player.getUUID());
		}
		if (!g.kind.hostile() && entity.getUUID().equals(g.leader) && !g.ending) {
			// the merchant fell: his animals go free, his escort fights on and then goes home
			g.ending = true;
			free(level, g);
		}
		if (g.kind.hostile() && g.fighters.contains(entity.getUUID())) {
			boolean anyLeft = g.fighters.stream().anyMatch(id -> !id.equals(entity.getUUID()) && level.getEntity(id) instanceof Mob m && m.isAlive());
			if (!anyLeft) {
				broken(level, g);
			}
		}
	}

	/** Every illager of a pillager caravan fallen: its animals free, and every realm near thinks better of those who did it. */
	private static void broken(ServerLevel level, Group g) {
		if (LIVE.remove(g.id) == null) {
			return;
		}
		free(level, g);
		if (g.hitters.isEmpty()) {
			return;
		}
		MinecraftServer server = level.getServer();
		BlockPos at = null;
		for (UUID id : g.hitters) {
			if (server.getPlayerList().getPlayer(id) instanceof ServerPlayer p) {
				at = p.blockPosition();
				break;
			}
		}
		if (at == null) {
			return;
		}
		List<Faction> near = new ArrayList<>();
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && Math.sqrt(f.seat().distSqr(at)) <= REWARD_RANGE) {
				near.add(f);
			}
		}
		for (UUID id : g.hitters) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			for (Faction f : near) {
				Reputation.change(p, f, REP_BREAK);
			}
			p.sendSystemMessage((near.isEmpty()
				? Component.translatable("message.jaymoskingdoms.roaming.broken")
				: Component.translatable("message.jaymoskingdoms.roaming.broken_thanks",
					String.join(", ", near.stream().map(Faction::name).toList()))).withStyle(ChatFormatting.GREEN));
		}
	}

	/** Its animals let go: they belong to the world now (tame one to open its chest). */
	private static void free(ServerLevel level, Group g) {
		for (UUID id : g.animals.keySet()) {
			if (level.getEntity(id) instanceof Mob animal && animal.isAlive()) {
				release(animal);
			}
		}
		g.animals.clear();
	}

	private static void release(Mob animal) {
		for (String t : List.copyOf(animal.entityTags())) {
			if (t.equals(TAG) || t.startsWith(TAG_OF)) {
				animal.removeTag(t);
			}
		}
	}

	// ---- going ------------------------------------------------------------------------------------------------------

	/** Out of the world: its people, their horses and its animals gone (it went on its way). */
	private static void vanish(ServerLevel level, Group g) {
		LIVE.remove(g.id);
		List<UUID> all = new ArrayList<>(g.fighters);
		if (g.leader != null) {
			all.add(g.leader);
		}
		all.addAll(g.animals.keySet());
		for (UUID id : all) {
			Entity e = level.getEntity(id);
			if (e instanceof Folk f) {
				Caravans.steer(f, null);
				Cavalry.dismiss(f);
			}
			if (e != null && e.entityTags().contains(g.stamp)) {
				Entity vehicle = e.getVehicle();
				if (vehicle != null && vehicle.entityTags().contains(g.stamp)) {
					vehicle.discard();
				}
				e.discard();
			}
		}
	}

	/** A merchant's escort, done: back to their realm. */
	private static void dismiss(ServerLevel level, Mob m) {
		level.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + 1.0, m.getZ(), 10, 0.4, 0.5, 0.4, 0.02);
		if (m instanceof Folk f) {
			Caravans.steer(f, null);
			Cavalry.dismiss(f);
		}
		Entity vehicle = m.getVehicle();
		if (vehicle != null && vehicle.entityTags().contains(TAG)) {
			vehicle.discard();
		}
		m.discard();
	}

	/** One of a roaming caravan's left in a chunk that unloaded (or saved when the server stopped): gone. */
	private static void loaded(Entity entity) {
		if (!entity.entityTags().contains(TAG) || of(entity) != null) {
			return;
		}
		entity.discard();
	}

	// ---- for operators ------------------------------------------------------------------------------------------------

	/** {@code /kingdom roaming}: the caravans about; {@code pillager}, {@code pillager_major}, {@code merchant}: one comes now. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("roaming").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(RoamingCaravans::list);
		for (Kind kind : Kind.values()) {
			root.then(Commands.literal(kind.name().toLowerCase(java.util.Locale.ROOT)).executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				String made = spawn(Factions.level(c.getSource().getServer()), player, kind);
				if (made == null) {
					c.getSource().sendFailure(Component.translatable("commands.jaymoskingdoms.roaming.no_room"));
					return 0;
				}
				c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roaming.spawned", made.toLowerCase(java.util.Locale.ROOT)), true);
				return 1;
			}));
		}
		return root;
	}

	private static int list(CommandContext<CommandSourceStack> c) {
		ServerLevel level = Factions.level(c.getSource().getServer());
		c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roaming.count", LIVE.size()), false);
		for (Group g : LIVE.values()) {
			Entity leader = g.leader == null ? null : level.getEntity(g.leader);
			String where = leader == null ? "?" : leader.blockPosition().toShortString();
			c.getSource().sendSuccess(() -> Component.translatable("commands.jaymoskingdoms.roaming.line", g.kind.name().toLowerCase(java.util.Locale.ROOT),
				where, g.goal.toShortString(), g.fighters.size(), g.animals.size()), false);
		}
		return LIVE.size();
	}
}
