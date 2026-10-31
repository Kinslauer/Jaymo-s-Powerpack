package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.61) Captives to rescue: villagers held in chains in barbarian, illager, goblin, orc and pirate camps, and now and
 * then in a dungeon (a monster spawner's mossy room), each taken from the nearest settlement. Bound, they can't move and
 * nothing harms them (no monster even sees them); they cry for help when anyone comes near. Right-click one to break
 * their chains: they follow you, and once you've brought them into their home settlement they're home safe, they reward
 * you, and the whole place knows what you did ({@link Deeds}). Leave them too far behind for too long and they make their
 * own way. A camp that's lost its captives takes new ones in a few days.
 */
public final class Captives {
	/** A camp's captives when a player first comes near: how many, and the chance it has any. */
	static final int CAMP_MOST = 2;
	static final float CAMP_CHANCE = 0.8F;
	/** A camp left without captives takes another after this long (ticks), with this chance each time it may. */
	static final long RETAKE_TICKS = 3L * 24000L;
	static final float RETAKE_CHANCE = 0.5F;
	/** The chance a dungeon (a monster spawner on a cobblestone floor) holds a captive. */
	static final float DUNGEON_CHANCE = 0.35F;
	/** Captives are put in when a player comes this near a camp or dungeon (blocks); a camp's are kept within this of its centre. */
	static final double PUT_IN_RANGE = 64.0, CAMP_SPREAD = 9.0;
	/** The settlement a captive was taken from: the nearest within this (blocks); with none, any settlement takes them in. */
	static final double HOME_SEARCH = 1500.0;
	/** A bound captive cries out to anyone this near (blocks), at most this often (ticks). */
	static final double PLEAD_RANGE = 12.0;
	static final long PLEAD_GAP = 160L;
	/** A freed captive keeps this close to their rescuer (blocks), at this pace. */
	static final double FOLLOW_CLOSE = 3.0;
	static final float FOLLOW_SPEED = 0.8F;
	/** Further than this from their rescuer (blocks) for this long (ticks), or still not home after this long, they go their own way. */
	static final double LOST_RANGE = 48.0;
	static final long LOST_TICKS = 600L, ESCORT_TICKS = 3L * 24000L;
	/** How often captives think (ticks); how often camps and dungeons are looked at for new ones. */
	static final int THINK = 20, LOOK = 200;
	/** (0.7.100) Who a captive is at home: one of its folk, one of its guards, or kin of its ruler (rolled when they're taken). */
	static final int FOLK = 0, GUARD = 1, KIN = 2;
	/** The chance a captive is one of the guards, and the ruler's own kin (a settlement with no ruler has none to lose). */
	static final float GUARD_CHANCE = 0.2F, KIN_CHANCE = 0.1F;
	/** Emeralds the family give from their own purse for their return, by standing. */
	static final int[] PURSE_LEAST = {6, 8, 12}, PURSE_MOST = {12, 16, 24};
	/**
	 * What the settlement adds from its treasury, by standing: this much, plus this share of its worth ({@link Wealth#worth}),
	 * up to this; never leaving it with less than {@link #TREASURY_KEPT}. A rich realm pays far more for its own.
	 */
	static final int[] BOUNTY_BASE = {8, 16, 40}, BOUNTY_MOST = {30, 60, 150};
	static final float[] BOUNTY_SHARE = {0.02F, 0.04F, 0.08F};
	static final long TREASURY_KEPT = 20L;
	/** Reputation with the settlement, the one brought home's hearts (MCA), and standing in it for its own members, by standing. */
	static final int[] HOME_REP = {60, 90, 150}, HOME_HEARTS = {50, 60, 80}, HOME_STANDING = {10, 25, 60};
	/** How many of their family come running to thank you, and the hearts each gives. */
	static final int FAMILY = 2, FAMILY_HEARTS = 20;
	static final double FAMILY_RANGE = 24.0;
	/** What a guard hands you (one of these), and what the ruler's kin do (a golden apple and some diamonds). */
	static final List<net.minecraft.world.item.Item> GUARD_GIFTS = List.of(Items.IRON_SWORD, Items.SHIELD, Items.CROSSBOW, Items.IRON_HELMET);
	static final int KIN_DIAMONDS_LEAST = 1, KIN_DIAMONDS_MOST = 3;
	/**
	 * Favours the settlement owes you for each brought home, by standing, and the most it owes at once. While it owes you
	 * one, its guards neither toll nor search you, and the next time they'd arrest you they let it go (that spends one).
	 */
	static final int[] FAVOURS_GIVEN = {1, 1, 2};
	static final int FAVOURS_MOST = 3;
	/** (0.7.340) How near (blocks) a guard of theirs must be to tell you they're letting an arrest go. */
	static final double PARDON_RANGE = 24.0;
	/** (0.7.340) The MCA {@code main} menu answer (our dialogue data): "Speak of the rescue". */
	public static final String ANSWER = "jaymoskingdoms_rescue";

	/**
	 * A captive: the settlement they were taken from (-1: none), who freed them (empty while bound), since when (game time),
	 * and who they are there ({@link #FOLK}, {@link #GUARD}, {@link #KIN}).
	 */
	public record Captive(int home, Optional<UUID> rescuer, long since, int standing) {
		public static final Codec<Captive> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("home", -1).forGetter(Captive::home),
				UUIDUtil.CODEC.optionalFieldOf("rescuer").forGetter(Captive::rescuer),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Captive::since),
				Codec.INT.optionalFieldOf("standing", FOLK).forGetter(Captive::standing))
			.apply(i, Captive::new));

		int standingIndex() {
			return Mth.clamp(standing, FOLK, KIN);
		}
	}

	/** On a villager: they're a captive (bound or being led home). Saved. */
	static final AttachmentType<Captive> CAPTIVE = AttachmentRegistry.create(JaymosKingdoms.id("captive"), builder -> builder.persistent(Captive.CODEC));
	/** On a villager while in chains: synced, so clients know a right-click frees them (and opens no screen). */
	public static final AttachmentType<Boolean> BOUND = AttachmentRegistry.create(JaymosKingdoms.id("bound"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** On a monster spawner: when it was looked at for a captive (so each dungeon is only once). Saved. */
	static final AttachmentType<Long> CHECKED = AttachmentRegistry.create(JaymosKingdoms.id("captive_checked"), builder -> builder.persistent(Codec.LONG));
	/** On the overworld: camp id → when it last took captives (game time). Saved. */
	static final AttachmentType<Map<String, Long>> CAMPS = AttachmentRegistry.create(JaymosKingdoms.id("captive_camps"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** (0.7.100) Per player: settlement id → favours it owes them for those they brought home. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> FAVOURS = AttachmentRegistry.create(JaymosKingdoms.id("captive_favours"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private static final Set<Villager> LOADED = new HashSet<>();
	private static final Set<BlockPos> SPAWNERS = new HashSet<>();
	private static final Map<UUID, Long> PLEADED = new HashMap<>(), LOST = new HashMap<>();

	private Captives() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.hasAttached(CAPTIVE)) {
				LOADED.add(v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				LOADED.remove(v);
			}
		});
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (level != Factions.level(level.getServer())) {
				return;
			}
			for (BlockEntity be : chunk.getBlockEntities().values()) {
				if (be instanceof SpawnerBlockEntity && !be.hasAttached(CHECKED)) {
					SPAWNERS.add(be.getBlockPos().immutable());
				}
			}
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager v) || !bound(v) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				free(server, sp, v);
			}
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Captive c = entity.getAttached(CAPTIVE);
			if (c != null && c.rescuer().isPresent() && entity.level() instanceof ServerLevel level
				&& level.getServer().getPlayerList().getPlayer(c.rescuer().get()) instanceof ServerPlayer p) {
				Post.tidings(p.getUUID(), Component.translatable("tidings.jaymoskingdoms.captive.died", entity.getDisplayName()));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Captives::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			SPAWNERS.clear();
			PLEADED.clear();
			LOST.clear();
		});
	}

	/** {@code entity} is a captive still in chains. */
	public static boolean bound(Entity entity) {
		return Boolean.TRUE.equals(entity.getAttached(BOUND));
	}

	// ---- taking captives -------------------------------------------------------------------------------------------

	/** A villager taken from the settlement nearest {@code at}, put there in chains. */
	static @Nullable Villager take(ServerLevel level, BlockPos at) {
		Vec3 pos = Vec3.atBottomCenterOf(at);
		Villager v = McaCompat.newVillager(level, pos);
		if (v == null) {
			return null;
		}
		v.snapTo(pos.x, pos.y, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		// (0.7.77) set up as a fresh spawn would be: it's here MCA rolls their genes and skin (built bare, they had none)
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.EVENT, null);
		Faction home = nearestSettlement(level.getServer(), at);
		v.setAttached(CAPTIVE, new Captive(home == null ? -1 : home.id(), Optional.empty(), level.getGameTime(), standing(home, level.getRandom())));
		bind(v, true);
		v.setPersistenceRequired();
		level.addFreshEntity(v);
		LOADED.add(v);
		JaymosKingdoms.LOGGER.debug("a captive from {} held at {}", home == null ? "nowhere" : home.name(), at);
		return v;
	}

	/**
	 * (0.7.439) {@code v}, carried off from {@code home} by raiders ({@link BorderClashes}), held in chains where they are,
	 * to be brought home.
	 */
	static void hold(ServerLevel level, Villager v, Faction home) {
		v.setAttached(CAPTIVE, new Captive(home.id(), Optional.empty(), level.getGameTime(), FOLK));
		bind(v, true);
		v.setPersistenceRequired();
		LOADED.add(v);
	}

	/** In chains (they don't move, and nothing harms or even sees them) or out of them. */
	private static void bind(Villager v, boolean chained) {
		v.setNoAi(chained);
		v.setPermanentlyInvulnerable(chained);
		if (chained) {
			v.setAttached(BOUND, true);
		} else {
			v.removeAttached(BOUND);
		}
	}

	/** Who a captive taken from {@code home} is there. */
	private static int standing(@Nullable Faction home, RandomSource random) {
		if (home == null) {
			return FOLK;
		}
		float roll = random.nextFloat();
		if (roll < KIN_CHANCE && home.leader().isPresent()) {
			return KIN;
		}
		return roll < KIN_CHANCE + GUARD_CHANCE ? GUARD : FOLK;
	}

	private static @Nullable Faction nearestSettlement(MinecraftServer server, BlockPos at) {
		Faction best = null;
		double bestD = HOME_SEARCH * HOME_SEARCH;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			double d = f.seat().distSqr(at);
			if (d < bestD) {
				bestD = d;
				best = f;
			}
		}
		return best;
	}

	/** Camps a player has come near take their captives (or, left without, new ones in time). */
	private static void camps(ServerLevel level, long now) {
		MinecraftServer server = level.getServer();
		Map<String, Long> taken = level.getAttachedOrElse(CAMPS, Map.of());
		Map<String, Long> next = null;
		RandomSource random = level.getRandom();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			if (camp.fallen()) {
				continue;
			}
			Camps.CampData data = Camps.data(server, camp.id());
			BlockPos center = data != null ? data.center() : camp.seat();
			if (!level.isLoaded(center) || level.getNearestPlayer(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, PUT_IN_RANGE, false) == null) {
				continue;
			}
			String id = Integer.toString(camp.id());
			Long last = taken.get(id);
			int count;
			if (last == null) {
				count = random.nextFloat() < CAMP_CHANCE ? 1 + random.nextInt(CAMP_MOST) : 0;
			} else if (now - last >= RETAKE_TICKS && held(level, center) == 0) {
				count = random.nextFloat() < RETAKE_CHANCE ? 1 : 0;
			} else {
				continue;
			}
			for (int i = 0; i < count; i++) {
				BlockPos near = center.offset(Mth.nextInt(random, -6, 6), 0, Mth.nextInt(random, -6, 6));
				take(level, Construction.standable(level, near, 4));
			}
			if (next == null) {
				next = new HashMap<>(taken);
			}
			next.put(id, now);
		}
		if (next != null) {
			level.setAttached(CAMPS, Map.copyOf(next));
		}
	}

	/** Captives still in chains round {@code center}. */
	private static int held(ServerLevel level, BlockPos center) {
		return level.getEntitiesOfClass(Villager.class, new AABB(center).inflate(CAMP_SPREAD * 3), Captives::bound).size();
	}

	/** Dungeons a player has come near: each looked at once, and some hold a captive. */
	private static void dungeons(ServerLevel level, long now) {
		RandomSource random = level.getRandom();
		for (BlockPos pos : new ArrayList<>(SPAWNERS)) {
			if (!level.isLoaded(pos)) {
				SPAWNERS.remove(pos);
				continue;
			}
			if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, PUT_IN_RANGE, false) == null) {
				continue;
			}
			SPAWNERS.remove(pos);
			if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner) || spawner.hasAttached(CHECKED)) {
				continue;
			}
			spawner.setAttached(CHECKED, now);
			spawner.setChanged();
			BlockState floor = level.getBlockState(pos.below());
			if (!(floor.is(Blocks.COBBLESTONE) || floor.is(Blocks.MOSSY_COBBLESTONE)) || random.nextFloat() >= DUNGEON_CHANCE) {
				continue;
			}
			BlockPos spot = roomSpot(level, pos, random);
			if (spot != null) {
				take(level, spot);
			}
		}
	}

	/** Open floor in the dungeon round {@code spawner} (not where it stands), or null. */
	private static @Nullable BlockPos roomSpot(ServerLevel level, BlockPos spawner, RandomSource random) {
		List<BlockPos> free = new ArrayList<>();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				if (Math.abs(dx) + Math.abs(dz) < 2) {
					continue;
				}
				BlockPos at = spawner.offset(dx, 0, dz);
				if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
					&& level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
					free.add(at);
				}
			}
		}
		return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
	}

	// ---- freeing and leading home -------------------------------------------------------------------------------------

	/** {@code player} breaks {@code captive}'s chains: they follow them home. */
	static void free(ServerLevel level, ServerPlayer player, Villager captive) {
		Captive c = captive.getAttached(CAPTIVE);
		int home = c == null ? -1 : c.home();
		int standing = c == null ? FOLK : c.standingIndex();
		bind(captive, false);
		captive.setAttached(CAPTIVE, new Captive(home, Optional.of(player.getUUID()), level.getGameTime(), standing));
		PLEADED.remove(captive.getUUID());
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, captive.getX(), captive.getY(), captive.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CRIT, captive.getX(), captive.getY() + 0.8, captive.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
		Faction f = home >= 0 ? Factions.get(level.getServer(), home) : null;
		if (f != null && f.fallen()) {
			f = null;
		}
		// (0.7.340) they tell you themselves where home is: no chat line
		Deeds.freed(level, player, captive, f);
		// (0.7.418) a life's aim: one more freed
		LifeAims.freed(player);
		// (0.7.419) a road deed: a captive's chains broken
		Deeds.done(level.getServer(), "captive.freed");
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		if (now % LOOK == 0) {
			camps(overworld, now);
		}
		if (now % THINK != 0) {
			return;
		}
		dungeons(overworld, now);
		for (Villager v : new ArrayList<>(LOADED)) {
			if (!v.isAlive() || !(v.level() instanceof ServerLevel level)) {
				LOADED.remove(v);
				continue;
			}
			Captive c = v.getAttached(CAPTIVE);
			if (c == null) {
				LOADED.remove(v);
			} else if (c.rescuer().isEmpty()) {
				plead(level, v, now);
			} else {
				lead(level, v, c, now);
			}
		}
	}

	/** A captive in chains cries out to whoever's near. */
	private static void plead(ServerLevel level, Villager v, long now) {
		Player near = level.getNearestPlayer(v, PLEAD_RANGE);
		if (near == null || near.isSpectator()) {
			return;
		}
		// (no AI to turn their head: turned by hand)
		double dx = near.getX() - v.getX(), dz = near.getZ() - v.getZ();
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		v.setYHeadRot(yaw);
		v.setYRot(yaw);
		Long last = PLEADED.get(v.getUUID());
		if (last != null && now - last < PLEAD_GAP) {
			return;
		}
		PLEADED.put(v.getUUID(), now);
		Speech.say(level, v, Component.translatable(Oddities.line("talk.jaymoskingdoms.deed.rescue.plead", level.getRandom())), Speech.YELLOW,
			PLEAD_RANGE + 4.0);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	/** A freed captive follows their rescuer, until they're home (or lost). */
	private static void lead(ServerLevel level, Villager v, Captive c, long now) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(c.rescuer().get());
		Faction here = Theft.lawAt(level, v.blockPosition());
		Faction home = c.home() >= 0 ? Factions.get(level.getServer(), c.home()) : null;
		boolean homeless = home == null || home.fallen();
		if (here != null && (homeless || here.id() == home.id())) {
			if (p != null) {
				// (taken in by a settlement not their own, they're nobody's guard or kin there)
				arrive(level, p, v, here, homeless ? FOLK : c.standingIndex());
			} else {
				v.removeAttached(CAPTIVE);
			}
			return;
		}
		boolean far = p == null || p.level() != level || p.distanceTo(v) > LOST_RANGE;
		if (far) {
			long since = LOST.computeIfAbsent(v.getUUID(), u -> now);
			if (now - since >= LOST_TICKS) {
				lose(v, p);
			}
			return;
		}
		LOST.remove(v.getUUID());
		if (now - c.since() >= ESCORT_TICKS) {
			lose(v, p);
			return;
		}
		if (p.distanceTo(v) > FOLLOW_CLOSE) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(p, false), FOLLOW_SPEED, 2);
		}
	}

	/**
	 * Home: the rescue's done. (0.7.100) Their family pay from their own purse and come running to thank you, the settlement adds
	 * a reward from its treasury (more the richer it is and the more the one brought home matters to it), a guard or the ruler's
	 * kin hand you something of their own, and the settlement owes you a favour.
	 */
	private static void arrive(ServerLevel level, ServerPlayer player, Villager v, Faction home, int standing) {
		v.removeAttached(CAPTIVE);
		// (0.7.439) a haven's child carried off by raiders: home, and the rescue asked for is done
		BorderClashes.home(level.getServer(), v.getUUID());
		Quests.rescued(player, v);
		LOADED.remove(v);
		LOST.remove(v.getUUID());
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		int purse = Mth.nextInt(random, PURSE_LEAST[standing], PURSE_MOST[standing]);
		// (a realm at war with you, or that can't tell who you are behind a mask, thanks you but pays nothing from its
		// treasury and owes you no favour)
		boolean known = !Wars.enemy(player, home) && !Identities.hidden(player, home.id());
		int bounty = known ? bounty(server, home, standing) : 0;
		List<ItemStack> rewards = new ArrayList<>();
		rewards.add(new ItemStack(Items.EMERALD, purse + bounty));
		if (standing == GUARD) {
			rewards.add(new ItemStack(GUARD_GIFTS.get(random.nextInt(GUARD_GIFTS.size()))));
		} else if (standing == KIN) {
			rewards.add(new ItemStack(Items.GOLDEN_APPLE));
			rewards.add(new ItemStack(Items.DIAMOND, Mth.nextInt(random, KIN_DIAMONDS_LEAST, KIN_DIAMONDS_MOST)));
		}
		Deeds.broughtHome(level, player, v, home, rewards, HOME_REP[standing], HOME_HEARTS[standing]);
		Deeds.family(level, player, v, FAMILY, FAMILY_RANGE, FAMILY_HEARTS);
		if (known) {
			Faction.Member member = home.member(player.getUUID());
			if (member != null) {
				KingdomEvents.standing(server, player, home, member.standing() + HOME_STANDING[standing]);
			}
		}
		// (0.7.340) no chat: the reward comes in your next Tidings
		Post.tidings(player.getUUID(), bounty > 0
			? Component.translatable("tidings.jaymoskingdoms.captive.home", v.getDisplayName(), home.name(), purse, bounty)
			: Component.translatable("tidings.jaymoskingdoms.captive.home.purse", v.getDisplayName(), home.name(), purse));
		if (known) {
			// (0.7.340) owed quietly: nothing says so, you find out when the guards wave you through
			owe(player, home, FAVOURS_GIVEN[standing]);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** What {@code home}'s treasury pays for one of {@code standing} brought back, taken out of it now. */
	private static int bounty(MinecraftServer server, Faction home, int standing) {
		long worth = Wealth.worth(server, home);
		int want = (int)Math.min(BOUNTY_MOST[standing], BOUNTY_BASE[standing] + (long)(Math.max(0L, worth) * BOUNTY_SHARE[standing]));
		int[] paid = {0};
		Factions.update(server, home.id(), f -> {
			long can = Math.max(0L, f.treasury() - TREASURY_KEPT);
			paid[0] = (int)Math.min(want, can);
			return paid[0] > 0 ? f.withTreasury(f.treasury() - paid[0]) : f;
		});
		return paid[0];
	}

	// ---- favours -----------------------------------------------------------------------------------------------------

	/** Favours settlement {@code factionId} owes {@code player} (none to a masked stranger: they don't know who it is). */
	public static int owed(Player player, int factionId) {
		if (player instanceof ServerPlayer sp && Identities.hidden(sp, factionId)) {
			return 0;
		}
		return player.getAttachedOrElse(FAVOURS, Map.of()).getOrDefault(Integer.toString(factionId), 0);
	}

	/** {@code f} owes {@code player} {@code more} favours (up to {@link #FAVOURS_MOST}); whether it owes any more than before. */
	private static boolean owe(ServerPlayer player, Faction f, int more) {
		String id = Integer.toString(f.id());
		Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(FAVOURS, Map.of()));
		int had = all.getOrDefault(id, 0);
		int now = Math.min(FAVOURS_MOST, had + more);
		if (now <= had) {
			return false;
		}
		all.put(id, now);
		player.setAttached(FAVOURS, Map.copyOf(all));
		return true;
	}

	/** (Theft) {@code f}'s guards would arrest {@code player}: if it owes them a favour, it's spent and they let it go. */
	static boolean pardon(ServerPlayer player, Faction f) {
		int had = owed(player, f.id());
		if (had <= 0) {
			return false;
		}
		Map<String, Integer> all = new HashMap<>(player.getAttachedOrElse(FAVOURS, Map.of()));
		String id = Integer.toString(f.id());
		if (had == 1) {
			all.remove(id);
		} else {
			all.put(id, had - 1);
		}
		player.setAttached(FAVOURS, Map.copyOf(all));
		// (0.7.340) a guard of theirs nearby says so; nothing tells you how many are left
		if (player.level() instanceof ServerLevel level) {
			LivingEntity guard = nearestGuard(level, player, f);
			if (guard != null) {
				Speech.tell(level, guard, player, Component.translatable(Oddities.line("talk.jaymoskingdoms.deed.rescue.pardon", level.getRandom()),
					Identities.shownName(player), f.name()), Speech.GOLD);
			}
		}
		return true;
	}

	/**
	 * (0.7.340) "Speak of the rescue" ({@link #ANSWER}, MCA's main menu): one of the watch of a town that owes you for one
	 * you brought home. Andy: "you can talk to them about it". They say what it means, never how many are owed.
	 */
	public static boolean canSpeakOf(ServerPlayer player, LivingEntity who) {
		Faction f = Downed.isGuard(who) && who.level() instanceof ServerLevel level ? watchOf(level, who) : null;
		return f != null && owed(player, f.id()) > 0;
	}

	/** What the guard says, asked about the rescue. */
	public static Component speakOf(ServerPlayer player, LivingEntity who) {
		Faction f = who.level() instanceof ServerLevel level ? watchOf(level, who) : null;
		return Component.translatable(Oddities.line("talk.jaymoskingdoms.deed.rescue.owed", who.getRandom()), Recollections.nameTo(player, who),
			f == null ? Component.empty() : f.name());
	}

	/** The settlement {@code guard} keeps watch for: a folk guard's own, else where they stand. */
	private static @Nullable Faction watchOf(ServerLevel level, LivingEntity guard) {
		return guard instanceof Folk folk ? Factions.get(level.getServer(), folk.factionId()) : Theft.lawAt(level, guard.blockPosition());
	}

	/** {@code f}'s guard nearest {@code player}, within {@link #PARDON_RANGE}, or null. */
	private static @Nullable LivingEntity nearestGuard(ServerLevel level, ServerPlayer player, Faction f) {
		LivingEntity best = null;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PARDON_RANGE),
			x -> x.isAlive() && Downed.isGuard(x))) {
			Faction theirs = watchOf(level, e);
			if (theirs != null && theirs.id() == f.id() && (best == null || e.distanceToSqr(player) < best.distanceToSqr(player))) {
				best = e;
			}
		}
		return best;
	}

	/** Left behind (or too long on the road): they go their own way, still grateful. */
	private static void lose(Villager v, @Nullable ServerPlayer rescuer) {
		v.removeAttached(CAPTIVE);
		LOADED.remove(v);
		LOST.remove(v.getUUID());
		if (rescuer != null) {
			Post.tidings(rescuer.getUUID(), Component.translatable("tidings.jaymoskingdoms.captive.lost", v.getDisplayName()));
		}
	}

	// ---- testing -----------------------------------------------------------------------------------------------------

	/** {@code /kingdom captive} (ops): a captive in chains two blocks ahead, taken from the nearest settlement. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("captive").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(Captives::spawnHere);
	}

	private static int spawnHere(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.level();
		Vec3 ahead = player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(2.0));
		Villager v = take(level, Construction.standable(level, BlockPos.containing(ahead), 3));
		if (v == null) {
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("A captive in chains"), false);
		return 1;
	}
}
