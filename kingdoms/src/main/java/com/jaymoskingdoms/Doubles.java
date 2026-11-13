package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.Creatures;
import com.jaymoskingdoms.creatures.Shapeshifter;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Stolen lives. A shapeshifter takes a villager's place: the real one is carried off and kept tied up (in a dungeon, a cellar
 * dug near a settlement, or buried in a ruin), and a copy of them walks about the village in their place. The copy is good,
 * but it slips on small things: it names the wrong spouse and the wrong dog. The real one, freed, tells you the right ones
 * ({@link #plead}) and leads you home ({@link #leads}); you can catch the copy out (sneak and speak to it, once it has slipped
 * in your hearing), or let the two meet, and it drops its face for a {@link Shapeshifter}.
 *
 * <p>The real one is a {@link Captives} captive, so freeing them, getting them home and the reward are that system's.
 */
public final class Doubles {
	/** A dungeon captive is a stolen life this often. */
	static final float STOLEN_CHANCE = 0.5F;
	/** The copy appears when a player is this close (blocks) to the settlement's seat; it stands this far (blocks) from it. */
	static final double SEAT_RANGE = 72.0, SEAT_SPREAD = 14.0;
	/** The real one near their copy (blocks) unmasks it. */
	static final double MEET = 14.0;
	/** A slip is heard within this (blocks), and not twice in this many ticks by one copy. */
	static final double SLIP_RANGE = 5.0;
	static final long SLIP_GAP = 360L;
	/** A freed stolen life leads while you are within this (blocks) of them, until this close to the seat (blocks). */
	static final double LEAD_RANGE = 16.0, LEAD_UNTIL = 10.0;
	static final int EVERY = 40;

	static final List<String> WIVES = List.of("Mara", "Elsbeth", "Tilda", "Odile", "Wenna", "Bryn", "Hesper", "Ysolde");
	static final List<String> DOGS = List.of("Biscuit", "Rook", "Moss", "Pickle", "Barley", "Tuck", "Nell", "Clod");
	static final List<String> COLOURS = List.of("brown", "black", "white", "grey", "spotted", "yellow");

	record Pending(UUID real, int home, CompoundTag body) {
		static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("real").forGetter(Pending::real),
				Codec.INT.fieldOf("home").forGetter(Pending::home),
				CompoundTag.CODEC.fieldOf("body").forGetter(Pending::body))
			.apply(i, Pending::new));
	}

	/** On the real villager: they are a stolen life. */
	public static final AttachmentType<Boolean> STOLEN = AttachmentRegistry.create(JaymosKingdoms.id("stolen_life"), b -> b.persistent(Codec.BOOL));
	/** On the copy: who they are the copy of. */
	public static final AttachmentType<UUID> COPY = AttachmentRegistry.create(JaymosKingdoms.id("life_copy"), b -> b.persistent(UUIDUtil.CODEC));
	/** On the overworld: copies waiting for a player to come near their village. */
	static final AttachmentType<List<Pending>> PENDING = AttachmentRegistry.create(JaymosKingdoms.id("stolen_pending"),
		b -> b.persistent(Pending.CODEC.listOf()));

	/** Copies that have slipped, and who heard (copy uuid, then players), and when each last slipped. */
	private static final Map<UUID, Set<UUID>> HEARD = new HashMap<>();
	private static final Map<UUID, Long> SLIPPED = new HashMap<>();

	private Doubles() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Doubles::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager copy) || !copy.hasAttached(COPY) || !player.isShiftKeyDown()
				|| !player.getMainHandItem().isEmpty() || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			Set<UUID> heard = HEARD.get(copy.getUUID());
			if (heard == null || !heard.contains(player.getUUID())) {
				return InteractionResult.PASS;
			}
			expose(level, copy);
			return InteractionResult.SUCCESS;
		});
	}

	// ---- the facts ----------------------------------------------------------------------------------------------

	private static int pick(UUID id, int salt, int size) {
		return Math.floorMod((int) (id.getLeastSignificantBits() >>> salt), size);
	}

	/** The real one's spouse, dog and the dog's colour. */
	static String wife(UUID real) {
		return WIVES.get(pick(real, 3, WIVES.size()));
	}

	static String dog(UUID real) {
		return DOGS.get(pick(real, 11, DOGS.size()));
	}

	static String colour(UUID real) {
		return COLOURS.get(pick(real, 19, COLOURS.size()));
	}

	/** What the copy gets wrong: the next name in the list, a different dog, another colour. */
	static String wrongWife(UUID real) {
		return WIVES.get((pick(real, 3, WIVES.size()) + 1) % WIVES.size());
	}

	static String wrongDog(UUID real) {
		return DOGS.get((pick(real, 11, DOGS.size()) + 2) % DOGS.size());
	}

	static String wrongColour(UUID real) {
		return COLOURS.get((pick(real, 19, COLOURS.size()) + 1) % COLOURS.size());
	}

	// ---- stealing -----------------------------------------------------------------------------------------------

	/** A fresh dungeon captive may be a stolen life. */
	public static void maybeStolen(ServerLevel level, @Nullable Villager v) {
		if (v != null && level.getRandom().nextFloat() < STOLEN_CHANCE) {
			steal(level, v);
		}
	}

	/** {@code v}, held captive, is a stolen life: a copy of them is set aside to take their place in their village. */
	public static void steal(ServerLevel level, Villager v) {
		if (v.hasAttached(STOLEN)) {
			return;
		}
		v.setAttached(STOLEN, true);
		Captives.Captive c = v.getAttached(Captives.CAPTIVE);
		if (c == null || c.home() < 0) {
			return;
		}
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!v.saveAsPassenger(out)) {
			return;
		}
		ServerLevel home = level.getServer().overworld();
		List<Pending> list = new ArrayList<>(home.getAttachedOrElse(PENDING, List.of()));
		list.add(new Pending(v.getUUID(), c.home(), out.buildResult()));
		home.setAttached(PENDING, list);
	}


	/** How deep a cellar's floor lies, and half its width (blocks). */
	static final int CELLAR_DEPTH = 8, CELLAR_HALF = 2;

	/**
	 * A cellar dug under the ground near a settlement, with a trapdoor in the turf and a ladder down: a stolen life is kept tied
	 * up in it. True if it was dug.
	 */
	public static boolean cellar(ServerLevel level, BlockPos near) {
		BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, near).below();
		if (!level.getBlockState(top).isSolid() || !level.getFluidState(top.above()).isEmpty() || !level.getBlockState(top.above()).isAir()) {
			return false;
		}
		BlockPos floor = top.below(CELLAR_DEPTH);
		int h = CELLAR_HALF;
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int dz = -h - 1; dz <= h + 3 + 1; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					if (!level.getFluidState(floor.offset(dx, dy, dz)).isEmpty()) {
						return false;
					}
				}
			}
		}
		for (int dx = -h - 1; dx <= h + 1; dx++) {
			for (int dz = -h - 1; dz <= h + 1; dz++) {
				for (int dy = -1; dy <= 3; dy++) {
					boolean wall = dx == -h - 1 || dx == h + 1 || dz == -h - 1 || dz == h + 1 || dy == -1 || dy == 3;
					level.setBlock(floor.offset(dx, dy, dz), (wall ? (dy == -1 ? net.minecraft.world.level.block.Blocks.COBBLESTONE
						: net.minecraft.world.level.block.Blocks.MOSSY_COBBLESTONE) : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 3);
				}
			}
		}
		// the shaft: up the south side, a ladder on its north-facing wall, to a trapdoor in the turf
		int sx = floor.getX(), sz = floor.getZ() + h + 2;
		for (int y = floor.getY(); y <= top.getY(); y++) {
			level.setBlock(new BlockPos(sx, y, sz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		}
		level.setBlock(new BlockPos(sx, floor.getY() + 1, floor.getZ() + h + 1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(new BlockPos(sx, floor.getY(), floor.getZ() + h + 1), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
		for (int y = floor.getY(); y < top.getY(); y++) {
			BlockPos behind = new BlockPos(sx, y, sz + 1);
			if (!level.getBlockState(behind).isSolid()) {
				level.setBlock(behind, net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
			}
			level.setBlock(new BlockPos(sx, y, sz), net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
		}
		level.setBlock(new BlockPos(sx, top.getY(), sz), net.minecraft.world.level.block.Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
		BlockPos in = floor.above();
		level.setBlock(in.offset(-h, 0, -h), net.minecraft.world.level.block.Blocks.BARREL.defaultBlockState(), 3);
		level.setBlock(in.offset(h, 0, -h), net.minecraft.world.level.block.Blocks.HAY_BLOCK.defaultBlockState(), 3);
		level.setBlock(in.offset(0, 2, 0), net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState()
			.setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 3);
		Villager captive = Captives.take(level, in.offset(0, 0, 1));
		if (captive == null) {
			return true;
		}
		steal(level, captive);
		return true;
	}

	/** Villagers buried in a ruin or dungeon (found shut in under the ground) are the ones whose lives were stolen. */
	public static void markBuried(ServerLevel level, Villager v) {
		if (v.hasAttached(STOLEN) || v.hasAttached(Captives.CAPTIVE)) {
			return;
		}
		Faction home = Captives.nearestSettlement(level.getServer(), v.blockPosition());
		if (home == null) {
			return;
		}
		Captives.hold(level, v, home);
		steal(level, v);
	}

	/** The real one pleads with the right facts. True if they spoke. */
	public static boolean plead(ServerLevel level, Villager v) {
		if (!v.hasAttached(STOLEN)) {
			return false;
		}
		UUID id = v.getUUID();
		String key = "talk.jaymoskingdoms.stolen.plead." + level.getRandom().nextInt(3);
		Speech.say(level, v, Component.translatable(key, wife(id), dog(id), colour(id)), Speech.YELLOW, Captives.PLEAD_RANGE + 4.0);
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
		return true;
	}

	/** A freed stolen life walks ahead toward their village while you follow. True if they did. */
	public static boolean leads(ServerLevel level, Villager v, @Nullable Faction home, ServerPlayer rescuer) {
		if (!v.hasAttached(STOLEN) || home == null || rescuer.distanceTo(v) > LEAD_RANGE) {
			return false;
		}
		BlockPos seat = home.seat();
		if (v.blockPosition().distSqr(seat) < LEAD_UNTIL * LEAD_UNTIL) {
			return false;
		}
		BehaviorUtils.setWalkAndLookTargetMemories(v, seat, 0.7F, 3);
		return true;
	}

	/** The real one has come home: whatever wears their face drops it. */
	static void arrived(ServerLevel level, Villager real) {
		if (!real.hasAttached(STOLEN)) {
			return;
		}
		for (Villager copy : level.getEntitiesOfClass(Villager.class, real.getBoundingBox().inflate(96.0), c -> real.getUUID().equals(c.getAttached(COPY)))) {
			expose(level, copy);
		}
	}

	// ---- the copies -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		List<Pending> pending = level.getAttachedOrElse(PENDING, List.of());
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			if (!pending.isEmpty()) {
				pending = place(level, player, pending);
			}
			for (Villager copy : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(48.0), c -> c.hasAttached(COPY))) {
				UUID real = copy.getAttached(COPY);
				if (!level.getEntitiesOfClass(Villager.class, copy.getBoundingBox().inflate(MEET), r -> r.getUUID().equals(real) && r.isAlive()).isEmpty()) {
					expose(level, copy);
				} else if (copy.distanceTo(player) < SLIP_RANGE) {
					slip(level, copy, player, real, now);
				}
			}
		}
		if (now % (EVERY * 30) == 0) {
			HEARD.keySet().removeIf(id -> level.getEntity(id) == null);
			SLIPPED.keySet().removeIf(id -> level.getEntity(id) == null);
		}
	}

	/** Puts the copies whose village {@code player} has come near; returns the ones left waiting. */
	private static List<Pending> place(ServerLevel level, ServerPlayer player, List<Pending> pending) {
		List<Pending> left = new ArrayList<>();
		List<Pending> done = new ArrayList<>();
		for (Pending p : pending) {
			Faction home = Factions.get(level.getServer(), p.home());
			if (home == null || home.fallen()) {
				done.add(p);
				continue;
			}
			BlockPos seat = home.seat();
			if (player.blockPosition().distSqr(seat) > SEAT_RANGE * SEAT_RANGE) {
				left.add(p);
				continue;
			}
			if (make(level, p, seat)) {
				done.add(p);
			} else {
				left.add(p);
			}
		}
		if (!done.isEmpty()) {
			level.setAttached(PENDING, left);
		}
		return left;
	}

	/** Spawns the copy near {@code seat}, from the saved body of the real one. */
	private static boolean make(ServerLevel level, Pending p, BlockPos seat) {
		RandomSourceHolder r = new RandomSourceHolder(level);
		int x = seat.getX() + r.spread(), z = seat.getZ() + r.spread();
		if (!level.hasChunkAt(new BlockPos(x, seat.getY(), z))) {
			return false;
		}
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, seat.getY(), z));
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), p.body()), level,
			EntitySpawnReason.LOAD, e -> {
				e.setUUID(UUID.randomUUID());
				e.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, e.getYRot(), e.getXRot());
				return e;
			});
		if (!(loaded instanceof Villager copy)) {
			return false;
		}
		copy.removeAttached(Captives.CAPTIVE);
		copy.removeAttached(Captives.BOUND);
		copy.removeAttached(STOLEN);
		copy.setNoAi(false);
		copy.setPermanentlyInvulnerable(false);
		copy.setAttached(COPY, p.real());
		copy.setPersistenceRequired();
		level.addFreshEntity(copy);
		return true;
	}

	/** Tiny helper so the spread is drawn from the level's random. */
	private record RandomSourceHolder(ServerLevel level) {
		int spread() {
			return (int) ((level.getRandom().nextDouble() * 2.0 - 1.0) * SEAT_SPREAD);
		}
	}

	/** The copy says a wrong thing in {@code player}'s hearing. */
	private static void slip(ServerLevel level, Villager copy, ServerPlayer player, UUID real, long now) {
		Long last = SLIPPED.get(copy.getUUID());
		if ((last != null && now - last < SLIP_GAP) || level.getRandom().nextFloat() > 0.5F) {
			return;
		}
		SLIPPED.put(copy.getUUID(), now);
		HEARD.computeIfAbsent(copy.getUUID(), k -> new HashSet<>()).add(player.getUUID());
		Component line = level.getRandom().nextBoolean()
			? Component.translatable("talk.jaymoskingdoms.stolen.slip.wife", wrongWife(real))
			: Component.translatable("talk.jaymoskingdoms.stolen.slip.dog", wrongDog(real), wrongColour(real));
		Speech.say(level, copy, line, Speech.WHITE, SLIP_RANGE + 6.0);
	}

	/** The copy drops its face. */
	static void expose(ServerLevel level, Villager copy) {
		Shapeshifter s = Creatures.SHAPESHIFTER.create(level, EntitySpawnReason.EVENT);
		if (s == null) {
			return;
		}
		s.snapTo(copy.getX(), copy.getY(), copy.getZ(), copy.getYRot(), 0.0F);
		s.setCustomName(copy.getName());
		s.setPersistenceRequired();
		HEARD.remove(copy.getUUID());
		SLIPPED.remove(copy.getUUID());
		copy.discard();
		level.addFreshEntity(s);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, s.getX(), s.getY() + 1.0, s.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.HOSTILE, 1.0F, 0.6F);
		Player near = level.getNearestPlayer(s, 24.0);
		if (near != null) {
			s.setTarget(near);
		}
	}

	/** Where a villager lives (their bed), or null: MCA's home, else the vanilla villager's. */
	public static net.minecraft.core.@Nullable GlobalPos homeOf(Villager v) {
		return McaCompat.home(v).orElse(null);
	}

	/** Whether {@code v} is a stolen life's copy (for the tidings and the like). */
	public static boolean isCopy(Entity v) {
		return v.hasAttached(COPY);
	}
}
