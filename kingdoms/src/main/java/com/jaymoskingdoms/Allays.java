package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Allays and the Sylvan (Andy, 2026-11-26: "allays and sylvans should have some kind of synergy, and they should provide
 * benefits when you free them, and work around sylvan camps"; lore: "The caged singers").
 * <ul>
 * <li><b>Caged.</b> The allays the illagers keep: in vanilla pillager outposts and woodland mansions (tagged when they
 * load, if they're shut in), in our illager camps' cages ({@link IllagerCamps}) and the sites' boxes ({@link IllagerSites}).
 * An allay is freed when its cage is opened so it can fly out ({@link #enclosed}); the player nearest is its freer.</li>
 * <li><b>Freed.</b> The freer gains {@link #FREE_REP} with every Sylvan (and mixed) haven, and a Sylvan villager close by
 * thanks them. After {@link #FRIEND_AT} they're an allay-friend: a Sylvan haven's gate lets them in
 * ({@link HavenGates#admitted}) and its allays will go with them. A Sylvan freer is chosen at once (the allay likes them
 * without being handed anything). One nobody has bonded within {@link #FLY_HOME_TICKS} flies to the nearest Sylvan haven
 * and lives there.</li>
 * <li><b>Haven allays.</b> Every Sylvan haven keeps {@link #HAVEN_START} allays, and one more for each freed allay that
 * flew to it, at most {@link #HAVEN_MOST}. They carry the loose items lying in the haven to its storage (never what a
 * player dropped), and at night
 * hum round its giant glowcaps. While a haven keeps them its glow fades half as fast when it's left
 * ({@link GlowWoodHavens}); dark for half a season, they leave. Only a Sylvan or an allay-friend can bond with one.</li>
 * <li><b>A Sylvan's allay.</b> An allay that likes a Sylvan catches them: fall damage is halved while it's within
 * {@link #CATCH_RANGE}.</li>
 * </ul>
 * Vanilla allays throughout: no new mob or art.
 */
public final class Allays {
	/** A caged allay (shared with {@link IllagerSites#CAGED_TAG}); one broken out; one living at a haven (and its haven). */
	static final String CAGED_TAG = IllagerSites.CAGED_TAG, FREED_TAG = "jaymoskingdoms.freed_allay";
	static final String HAVEN_TAG = "jaymoskingdoms.haven_allay", HAVEN_OF = "jaymoskingdoms.haven_allay_";
	/** Reputation with each Sylvan haven for freeing one, and how many freed make an allay-friend. */
	static final int FREE_REP = 15, FRIEND_AT = 3;
	/** A freed allay nobody has bonded flies home after this long (ticks). */
	static final long FLY_HOME_TICKS = 1200L;
	/** How often (ticks) the caged are looked at, and how near (blocks) a player must be to be the freer. */
	static final int CAGE_CHECK = 40;
	static final double FREER_RANGE = 16.0;
	/** A cage: the open cells counted out from the allay, and how far out an opening is. */
	static final int CAGE_CELLS = 400, CAGE_REACH = 8;
	/** A haven's allays: the start, the most, how near a player must be for them to arrive, how far they keep from the seat. */
	static final int HAVEN_START = 2, HAVEN_MOST = 6;
	static final long HAVEN_CHECK = 400L;
	static final double PLAYER_RANGE = 72.0, HOME_RANGE = 48.0;
	/** Work: how often (ticks) a haven allay looks for something to do, how far it looks for a dropped item, how near it takes it. */
	static final int WORK_TICKS = 20;
	static final double ITEM_REACH = 14.0, TAKE_REACH = 1.8, DROP_REACH = 2.5;
	/** A dropped thing is fetched only once it has lain this long (ticks), and never one a player dropped (their own, or a body's). */
	static final int LIE_TICKS = 600;
	/** A dark haven's allays leave after this long (half a season). */
	static final long LEAVE_DARK = GlowWoodHavens.SEASON_TICKS / 2;
	/** How near (blocks) a Sylvan's allay catches their fall, and what's left of the damage. */
	static final double CATCH_RANGE = 10.0;
	static final float CATCH_LEFT = 0.5F;
	/** The allays' own blue, for their motes. */
	static final int BLUE = 0x7FD8FF;

	/** On a player: how many allays they've freed. Saved, kept through death. */
	static final AttachmentType<Integer> FREED = AttachmentRegistry.create(JaymosKingdoms.id("allays_freed"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** On the Overworld: haven id → freed allays that flew to it. Saved. */
	static final AttachmentType<Map<String, Integer>> JOINED = AttachmentRegistry.create(JaymosKingdoms.id("haven_allays_joined"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)));

	/** Allays loaded since the last tick, looked at the next (a cage is checked once the chunk is up). */
	private static final List<Allay> ARRIVED = new ArrayList<>();
	/** Loaded caged allays, and the ones seen shut in (only those pay out: a cage that never held can't be "broken"). */
	private static final Set<Allay> CAGED = new HashSet<>();
	private static final Set<UUID> SEEN_SHUT = new HashSet<>();
	/** Loaded haven allays. */
	private static final Set<Allay> HAVEN = new HashSet<>();
	/** A freed allay → when it was freed (it flies home if nobody bonds it). */
	private static final Map<UUID, Long> FREED_AT = new HashMap<>();
	/** A fall being caught (its smaller hurt goes through). */
	private static final Set<UUID> CATCHING = new HashSet<>();

	private Allays() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Allay allay) {
				ARRIVED.add(allay);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Allay allay) {
				CAGED.remove(allay);
				HAVEN.remove(allay);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Allays::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ARRIVED.clear();
			CAGED.clear();
			SEEN_SHUT.clear();
			HAVEN.clear();
			FREED_AT.clear();
			CATCHING.clear();
		});
		// a haven allay goes only with a Sylvan or an allay-friend
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || !(entity instanceof Allay allay) || !allay.entityTags().contains(HAVEN_TAG)
				|| !(player instanceof ServerPlayer sp) || welcome(sp)) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.allay.shy").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			return InteractionResult.FAIL;
		});
		// a haven allay killed: a freed one that had flown to its haven is gone for good
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Allay allay && entity.level() instanceof ServerLevel level) {
				Integer home = homeOf(allay);
				if (home != null) {
					leaveHaven(level.getServer(), allay, home);
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Allays::allowDamage);
	}

	// ---- who they take to ------------------------------------------------------------------------------------------

	/** How many allays {@code player} has freed. */
	static int freed(Player player) {
		return player.getAttachedOrElse(FREED, 0);
	}

	/** An allay-friend: one who has freed {@link #FRIEND_AT} of them. */
	static boolean friend(Player player) {
		return freed(player) >= FRIEND_AT;
	}

	/** Whether a haven's allays will go with {@code player}. */
	static boolean welcome(Player player) {
		return Heritage.is(player, Heritage.Kind.SYLVAN) || friend(player);
	}

	/** Whether {@code f} is a haven of the Sylvan (or a mixed one, with its Sylvan houses). */
	static boolean sylvanHaven(MinecraftServer server, Faction f) {
		if (f.type() == FactionType.BARBARIAN || f.fallen()) {
			return false;
		}
		String haven = Havens.of(server, f);
		return haven.equals(Heritage.Kind.SYLVAN.key()) || haven.equals(Havens.MIXED);
	}

	// ---- the tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!ARRIVED.isEmpty()) {
			List<Allay> arrived = List.copyOf(ARRIVED);
			ARRIVED.clear();
			for (Allay allay : arrived) {
				if (allay.isAlive() && allay.level() instanceof ServerLevel level) {
					arrive(level, allay);
				}
			}
		}
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		if (now % CAGE_CHECK == 0) {
			cages(now);
			flyHome(server, now);
		}
		if (now % WORK_TICKS == 0) {
			for (Allay allay : List.copyOf(HAVEN)) {
				if (allay.isAlive() && allay.level() instanceof ServerLevel level) {
					work(level, allay, now);
				}
			}
		}
		if (now % HAVEN_CHECK == 0) {
			havens(overworld, now);
		}
	}

	/** A loaded allay: a caged or haven one is followed; one shut in at an outpost or a mansion is caged from now on. */
	private static void arrive(ServerLevel level, Allay allay) {
		Set<String> tags = allay.entityTags();
		if (tags.contains(HAVEN_TAG)) {
			HAVEN.add(allay);
			return;
		}
		if (tags.contains(CAGED_TAG)) {
			CAGED.add(allay);
			if (enclosed(level, allay.blockPosition())) {
				SEEN_SHUT.add(allay.getUUID());
			}
			return;
		}
		if (tags.contains(FREED_TAG) || allay.getBrain().hasMemoryValue(MemoryModuleType.LIKED_PLAYER)) {
			return;
		}
		BlockPos at = allay.blockPosition();
		boolean held = level.structureManager().getStructureWithPieceAt(at,
			holder -> holder.is(BuiltinStructures.PILLAGER_OUTPOST) || holder.is(BuiltinStructures.WOODLAND_MANSION)).isValid();
		if (held && enclosed(level, at)) {
			allay.addTag(CAGED_TAG);
			allay.setPersistenceRequired();
			CAGED.add(allay);
			SEEN_SHUT.add(allay.getUUID());
		}
	}

	// ---- caged and freed ---------------------------------------------------------------------------------------------

	/** Each caged allay with a player near: shut in still, or freed. */
	private static void cages(long now) {
		Iterator<Allay> it = CAGED.iterator();
		List<Allay> out = new ArrayList<>();
		while (it.hasNext()) {
			Allay allay = it.next();
			if (!allay.isAlive() || !allay.entityTags().contains(CAGED_TAG)) {
				it.remove();
				continue;
			}
			if (!(allay.level() instanceof ServerLevel level) || level.getNearestPlayer(allay, FREER_RANGE) == null) {
				continue;
			}
			if (enclosed(level, allay.blockPosition())) {
				SEEN_SHUT.add(allay.getUUID());
			} else if (SEEN_SHUT.contains(allay.getUUID())) {
				out.add(allay);
			}
		}
		for (Allay allay : out) {
			CAGED.remove(allay);
			ServerLevel level = (ServerLevel)allay.level();
			Player nearest = level.getNearestPlayer(allay.getX(), allay.getY(), allay.getZ(), FREER_RANGE, p -> !p.isSpectator() && p.isAlive());
			freed(level, allay, nearest instanceof ServerPlayer sp ? sp : null, now);
		}
	}

	/**
	 * Whether the allay at {@code start} is shut in: the open cells reachable from it (air and the like, open doors and
	 * gates) run out before {@link #CAGE_CELLS} and never reach {@link #CAGE_REACH} away. Unloaded ground counts as shut.
	 */
	static boolean enclosed(ServerLevel level, BlockPos start) {
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		Set<BlockPos> seen = new HashSet<>();
		open.add(start);
		seen.add(start);
		while (!open.isEmpty()) {
			BlockPos p = open.poll();
			if (Math.abs(p.getX() - start.getX()) > CAGE_REACH || Math.abs(p.getY() - start.getY()) > CAGE_REACH
				|| Math.abs(p.getZ() - start.getZ()) > CAGE_REACH || seen.size() > CAGE_CELLS) {
				return false;
			}
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
				BlockPos q = p.relative(d);
				if (seen.contains(q)) {
					continue;
				}
				if (!level.hasChunkAt(q)) {
					return true;
				}
				if (passable(level, q)) {
					seen.add(q);
					open.add(q);
				}
			}
		}
		return true;
	}

	/** What an allay gets through: nothing in the way, or a door, gate or hatch standing open. */
	private static boolean passable(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getCollisionShape(level, pos).isEmpty()) {
			return true;
		}
		boolean opens = state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock;
		return opens && state.isPathfindable(PathComputationType.AIR);
	}

	/** {@code allay} is out of its cage; {@code freer}, if anyone was near, is thanked for it. */
	static void freed(ServerLevel level, Allay allay, @Nullable ServerPlayer freer, long now) {
		allay.removeTag(CAGED_TAG);
		allay.addTag(FREED_TAG);
		SEEN_SHUT.remove(allay.getUUID());
		FREED_AT.put(allay.getUUID(), now);
		level.sendParticles(Fx.mote(BLUE, 0.7F), allay.getX(), allay.getY() + 0.4, allay.getZ(), 18, 0.4, 0.4, 0.4, 0.02);
		level.playSound(null, allay.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 1.0F, 1.2F);
		if (freer == null) {
			return;
		}
		int count = freed(freer) + 1;
		freer.setAttached(FREED, count);
		MinecraftServer server = level.getServer();
		for (Faction f : Factions.all(server)) {
			if (sylvanHaven(server, f)) {
				Reputation.change(freer, f, FREE_REP);
			}
		}
		if (count == FRIEND_AT) {
			freer.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.allay.friend").withColor(BLUE));
		}
		// a Sylvan is chosen there and then
		if (Heritage.is(freer, Heritage.Kind.SYLVAN)) {
			allay.getBrain().setMemory(MemoryModuleType.LIKED_PLAYER, freer.getUUID());
		}
		thank(level, allay, freer);
	}

	/** A Sylvan villager close by (a fellow captive, a passer-by) thanks the freer. */
	private static void thank(ServerLevel level, Allay allay, ServerPlayer freer) {
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, allay.getBoundingBox().inflate(24.0),
			v -> v.isAlive() && !v.isBaby() && VillagerHeritage.of(v) == Heritage.Kind.SYLVAN)) {
			double d = v.distanceToSqr(freer);
			if (d < bestD) {
				best = v;
				bestD = d;
			}
		}
		if (best != null) {
			int line = level.getRandom().nextInt(4);
			Speech.tell(level, best, freer, Component.translatable("talk.jaymoskingdoms.allay.thanks." + line), BLUE);
		}
	}

	/** Freed allays nobody has bonded fly to the nearest Sylvan haven once their time is up. */
	private static void flyHome(MinecraftServer server, long now) {
		Iterator<Map.Entry<UUID, Long>> it = FREED_AT.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Long> e = it.next();
			Entity entity = null;
			for (ServerLevel level : server.getAllLevels()) {
				entity = level.getEntity(e.getKey());
				if (entity != null) {
					break;
				}
			}
			if (!(entity instanceof Allay allay) || !allay.isAlive() || allay.getBrain().hasMemoryValue(MemoryModuleType.LIKED_PLAYER)) {
				it.remove();
				continue;
			}
			if (now - e.getValue() < FLY_HOME_TICKS) {
				continue;
			}
			it.remove();
			Faction home = nearestHaven(server, allay.blockPosition());
			if (home == null || joined(server, home.id()) >= HAVEN_MOST - HAVEN_START) {
				// nowhere with room: it stays wild where it is
				continue;
			}
			addJoined(server, home.id(), 1);
			ServerLevel level = (ServerLevel)allay.level();
			level.sendParticles(Fx.mote(BLUE, 0.7F), allay.getX(), allay.getY() + 0.4, allay.getZ(), 14, 0.3, 0.3, 0.3, 0.05);
			level.playSound(null, allay.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
			allay.discard();
		}
	}

	/** The nearest Sylvan haven that isn't dark. */
	private static @Nullable Faction nearestHaven(MinecraftServer server, BlockPos pos) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (!sylvanHaven(server, f) || GlowWoodHavens.glowOf(server, f.id()).dark() >= 0L) {
				continue;
			}
			double d = f.seat().distSqr(pos);
			if (d < bestD) {
				best = f;
				bestD = d;
			}
		}
		return best;
	}

	// ---- the havens' allays ------------------------------------------------------------------------------------------

	static int joined(MinecraftServer server, int haven) {
		return Factions.level(server).getAttachedOrElse(JOINED, Map.of()).getOrDefault(Integer.toString(haven), 0);
	}

	private static void addJoined(MinecraftServer server, int haven, int by) {
		ServerLevel level = Factions.level(server);
		Map<String, Integer> map = new HashMap<>(level.getAttachedOrElse(JOINED, Map.of()));
		int n = Math.max(0, map.getOrDefault(Integer.toString(haven), 0) + by);
		if (n == 0) {
			map.remove(Integer.toString(haven));
		} else {
			map.put(Integer.toString(haven), n);
		}
		level.setAttached(JOINED, Map.copyOf(map));
	}

	/** The haven a haven allay lives at, or null. */
	static @Nullable Integer homeOf(Allay allay) {
		for (String tag : allay.entityTags()) {
			if (tag.startsWith(HAVEN_OF)) {
				try {
					return Integer.parseInt(tag.substring(HAVEN_OF.length()));
				} catch (NumberFormatException e) {
					return null;
				}
			}
		}
		return null;
	}

	/** {@code allay} no longer lives at haven {@code home} (bonded, dead, or the haven gone); a freed one is lost to it. */
	private static void leaveHaven(MinecraftServer server, Allay allay, int home) {
		allay.removeTag(HAVEN_TAG);
		allay.removeTag(HAVEN_OF + home);
		HAVEN.remove(allay);
		addJoined(server, home, -1);
	}

	/** Whether {@code f}'s woods keep allays now (they slow its glow fading). */
	static boolean keeping(ServerLevel level, Faction f) {
		BlockPos seat = f.seat();
		return !level.getEntitiesOfClass(Allay.class, new AABB(seat).inflate(HOME_RANGE), a -> a.isAlive() && a.entityTags().contains(HAVEN_OF + f.id())).isEmpty();
	}

	/** Each Sylvan haven with a player near: one more allay comes if it keeps too few; a dark one's leave in time. */
	private static void havens(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		for (Faction f : Factions.all(server)) {
			if (!sylvanHaven(server, f) || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			BlockPos seat = f.seat();
			List<Allay> kept = level.getEntitiesOfClass(Allay.class, new AABB(seat).inflate(HOME_RANGE * 1.5),
				a -> a.isAlive() && a.entityTags().contains(HAVEN_OF + f.id()));
			GlowWoodHavens.Glow glow = GlowWoodHavens.glowOf(server, f.id());
			if (glow.dark() >= 0L) {
				if (now - glow.dark() >= LEAVE_DARK) {
					for (Allay a : kept) {
						level.sendParticles(Fx.mote(BLUE, 0.7F), a.getX(), a.getY() + 0.4, a.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
						HAVEN.remove(a);
						a.discard();
					}
					addJoined(server, f.id(), -joined(server, f.id()));
				}
				continue;
			}
			int want = Math.min(HAVEN_MOST, HAVEN_START + joined(server, f.id()));
			if (kept.size() >= want || level.getNearestPlayer(seat.getX(), seat.getY(), seat.getZ(), PLAYER_RANGE, false) == null) {
				continue;
			}
			come(level, f, glow);
		}
	}

	/** One allay comes to live at {@code f}: by one of its giants, or over its green. */
	private static void come(ServerLevel level, Faction f, GlowWoodHavens.Glow glow) {
		RandomSource random = level.getRandom();
		BlockPos near = glow.giants().isEmpty() ? f.seat() : glow.giants().get(random.nextInt(glow.giants().size())).pos();
		BlockPos at = null;
		for (int i = 0; i < 10 && at == null; i++) {
			BlockPos column = near.offset(random.nextInt(13) - 6, 0, random.nextInt(13) - 6);
			BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
			BlockPos air = ground.above(1 + random.nextInt(3));
			if (level.isEmptyBlock(air) && level.isEmptyBlock(air.above())) {
				at = air;
			}
		}
		if (at == null) {
			return;
		}
		Allay allay = EntityTypes.ALLAY.create(level, EntitySpawnReason.EVENT);
		if (allay == null) {
			return;
		}
		allay.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
		allay.setPersistenceRequired();
		allay.addTag(HAVEN_TAG);
		allay.addTag(HAVEN_OF + f.id());
		level.addFreshEntity(allay);
		level.sendParticles(Fx.mote(BLUE, 0.7F), allay.getX(), allay.getY() + 0.4, allay.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
	}

	/**
	 * A haven allay's work: carry what it holds to the haven's storage; else fetch a loose item lying in the haven; else
	 * (at night) hum round a giant glowcap. One who has bonded with someone leaves the haven.
	 */
	private static void work(ServerLevel level, Allay allay, long now) {
		MinecraftServer server = level.getServer();
		Integer home = homeOf(allay);
		Faction f = home == null ? null : Factions.get(server, home);
		if (f == null || f.fallen()) {
			allay.removeTag(HAVEN_TAG);
			if (home != null) {
				allay.removeTag(HAVEN_OF + home);
			}
			HAVEN.remove(allay);
			return;
		}
		if (allay.getBrain().hasMemoryValue(MemoryModuleType.LIKED_PLAYER)) {
			leaveHaven(server, allay, home);
			return;
		}
		BlockPos seat = f.seat();
		BlockPos depot = f.holdings().depot().orElse(seat);
		ItemStack held = allay.getMainHandItem();
		if (!held.isEmpty()) {
			if (allay.position().distanceTo(Vec3.atCenterOf(depot)) <= DROP_REACH + 1.0) {
				deliver(level, allay, f, depot);
			} else {
				go(allay, Vec3.atCenterOf(depot.above()));
			}
			return;
		}
		if (allay.position().distanceTo(Vec3.atCenterOf(seat)) > HOME_RANGE) {
			go(allay, Vec3.atCenterOf(seat.above(2)));
			return;
		}
		ItemEntity loose = null;
		double bestD = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, allay.getBoundingBox().inflate(ITEM_REACH),
			i -> i.isAlive() && !i.hasPickUpDelay() && !i.getItem().isEmpty() && i.getAge() >= LIE_TICKS && !(i.getOwner() instanceof Player)
				&& i.distanceToSqr(Vec3.atCenterOf(seat)) <= HOME_RANGE * HOME_RANGE)) {
			double d = item.distanceToSqr(allay);
			if (d < bestD) {
				loose = item;
				bestD = d;
			}
		}
		if (loose != null) {
			if (Math.sqrt(bestD) <= TAKE_REACH) {
				allay.setItemInHand(InteractionHand.MAIN_HAND, loose.getItem().copy());
				loose.discard();
				level.playSound(null, allay.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.8F, 1.0F);
			} else {
				go(allay, loose.position().add(0.0, 0.5, 0.0));
			}
			return;
		}
		// nothing to carry: at night, the glowcaps
		RandomSource random = level.getRandom();
		if (level.isDarkOutside() && random.nextInt(8) == 0) {
			GlowWoodHavens.Glow glow = GlowWoodHavens.glowOf(server, f.id());
			if (!glow.giants().isEmpty()) {
				BlockPos giant = glow.giants().get(random.nextInt(glow.giants().size())).pos();
				go(allay, Vec3.atCenterOf(giant.offset(random.nextInt(7) - 3, 4 + random.nextInt(5), random.nextInt(7) - 3)));
			}
		}
		if (level.isDarkOutside() && random.nextInt(6) == 0) {
			level.sendParticles(Fx.mote(BLUE, 0.5F), allay.getX(), allay.getY() + 0.4, allay.getZ(), 4, 0.25, 0.25, 0.25, 0.01);
			level.playSound(null, allay.blockPosition(), SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 0.6F, 0.9F + random.nextFloat() * 0.3F);
		}
	}

	/** Into the haven's storage: what it holds, and what it has picked up besides; what won't fit is set down at the depot. */
	private static void deliver(ServerLevel level, Allay allay, Faction f, BlockPos depot) {
		List<ItemStack> carried = new ArrayList<>();
		carried.add(allay.getMainHandItem().copy());
		allay.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		carried.addAll(allay.getInventory().removeAllItems());
		for (ItemStack stack : carried) {
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack left = Storage.put(level, f, stack);
			if (!left.isEmpty()) {
				ItemEntity dropped = new ItemEntity(level, depot.getX() + 0.5, depot.getY() + 1.0, depot.getZ() + 0.5, left);
				// set down to stay: not fetched again at once
				dropped.setPickUpDelay(20 * 60 * 5);
				level.addFreshEntity(dropped);
			}
		}
		level.playSound(null, allay.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.8F, 1.0F);
	}

	private static void go(Allay allay, Vec3 to) {
		allay.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 1.0F, 1));
	}

	// ---- a Sylvan's fall ---------------------------------------------------------------------------------------------

	/** A Sylvan with an allay of theirs near takes half of a fall. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !source.is(DamageTypeTags.IS_FALL) || CATCHING.contains(player.getUUID())
			|| !(player.level() instanceof ServerLevel level) || !Heritage.is(player, Heritage.Kind.SYLVAN)) {
			return true;
		}
		UUID id = player.getUUID();
		List<Allay> theirs = level.getEntitiesOfClass(Allay.class, player.getBoundingBox().inflate(CATCH_RANGE),
			a -> a.isAlive() && a.getBrain().getMemory(MemoryModuleType.LIKED_PLAYER).equals(Optional.of(id)));
		if (theirs.isEmpty()) {
			return true;
		}
		Allay allay = theirs.getFirst();
		level.sendParticles(Fx.mote(BLUE, 0.6F), player.getX(), player.getY() + 0.8, player.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.9F, 1.3F);
		go(allay, player.position().add(0.0, 1.5, 0.0));
		CATCHING.add(id);
		try {
			player.hurtServer(level, source, amount * CATCH_LEFT);
		} finally {
			CATCHING.remove(id);
		}
		return false;
	}
}
