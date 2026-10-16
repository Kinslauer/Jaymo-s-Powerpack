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
import net.minecraft.ChatFormatting;
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
	/** Emeralds a captive's family gives for their return. */
	static final int REWARD_LEAST = 3, REWARD_MOST = 8;

	/** A captive: the settlement they were taken from (-1: none), who freed them (empty while bound), and since when (game time). */
	public record Captive(int home, Optional<UUID> rescuer, long since) {
		public static final Codec<Captive> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.optionalFieldOf("home", -1).forGetter(Captive::home),
				UUIDUtil.CODEC.optionalFieldOf("rescuer").forGetter(Captive::rescuer),
				Codec.LONG.optionalFieldOf("since", 0L).forGetter(Captive::since))
			.apply(i, Captive::new));
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
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.captive.died", entity.getDisplayName()).withStyle(ChatFormatting.RED));
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
		// (0.7.76) set up as a fresh spawn would be: it's here MCA rolls their genes and skin (built bare, they had none)
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), net.minecraft.world.entity.EntitySpawnReason.EVENT, null);
		Faction home = nearestSettlement(level.getServer(), at);
		v.setAttached(CAPTIVE, new Captive(home == null ? -1 : home.id(), Optional.empty(), level.getGameTime()));
		bind(v, true);
		v.setPersistenceRequired();
		level.addFreshEntity(v);
		LOADED.add(v);
		JaymosKingdoms.LOGGER.debug("a captive from {} held at {}", home == null ? "nowhere" : home.name(), at);
		return v;
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
		bind(captive, false);
		captive.setAttached(CAPTIVE, new Captive(home, Optional.of(player.getUUID()), level.getGameTime()));
		PLEADED.remove(captive.getUUID());
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, captive.getX(), captive.getY(), captive.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		level.sendParticles(ParticleTypes.CRIT, captive.getX(), captive.getY() + 0.8, captive.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
		Faction f = home >= 0 ? Factions.get(level.getServer(), home) : null;
		if (f != null && f.fallen()) {
			f = null;
		}
		Deeds.freed(level, player, captive, f);
		player.sendSystemMessage(f != null
			? Component.translatable("message.jaymoskingdoms.captive.freed", captive.getDisplayName(), f.name()).withStyle(ChatFormatting.GREEN)
			: Component.translatable("message.jaymoskingdoms.captive.freed.nowhere", captive.getDisplayName()).withStyle(ChatFormatting.GREEN));
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
				arrive(level, p, v, here);
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

	/** Home: the rescue's done. */
	private static void arrive(ServerLevel level, ServerPlayer player, Villager v, Faction home) {
		v.removeAttached(CAPTIVE);
		LOADED.remove(v);
		LOST.remove(v.getUUID());
		ItemStack reward = new ItemStack(Items.EMERALD, Mth.nextInt(level.getRandom(), REWARD_LEAST, REWARD_MOST));
		Deeds.broughtHome(level, player, v, home, reward);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.captive.home", v.getDisplayName(), home.name()).withStyle(ChatFormatting.GOLD));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Left behind (or too long on the road): they go their own way, still grateful. */
	private static void lose(Villager v, @Nullable ServerPlayer rescuer) {
		v.removeAttached(CAPTIVE);
		LOADED.remove(v);
		LOST.remove(v.getUUID());
		if (rescuer != null) {
			rescuer.sendSystemMessage(Component.translatable("message.jaymoskingdoms.captive.lost", v.getDisplayName()).withStyle(ChatFormatting.GRAY));
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
