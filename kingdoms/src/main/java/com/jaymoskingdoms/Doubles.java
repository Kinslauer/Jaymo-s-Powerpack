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
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.world.entity.LivingEntity;
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
	static final float STOLEN_CHANCE = 0.15F;
	/** A settlement loses no more than one of its own to a shapeshifter in this long (ticks: ten days). */
	static final long TAKEN_GAP = 240000L;
	/** One copy in this many is a Sylvan changeling under the face, not a shapeshifter (fixed by whose face it is). */
	static final int CHANGELING_IN = 3;
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
	/** A copy struck down to this share of its health drops its face; it comes out of it at least this hale. */
	static final float PAIN_SLIP = 0.5F, PAIN_LEAST = 0.3F;
	/** Unmasked with nobody named who found it out, it turns on the nearest player within this (blocks). */
	static final double UNMASK_SEEN = 24.0;

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

	/** On the overworld: when each settlement (by id) last had a life stolen. */
	static final AttachmentType<Map<String, Long>> TAKEN = AttachmentRegistry.create(JaymosKingdoms.id("stolen_taken"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));

	/** Copies that have slipped, and who heard (copy uuid, then players), and when each last slipped. */
	private static final Map<UUID, Set<UUID>> HEARD = new HashMap<>();
	private static final Map<UUID, Long> SLIPPED = new HashMap<>();

	private Doubles() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Doubles::tick);
		ShapeshifterAlarm.register();
		BorrowedFaces.register();
		// struck hard enough (or struck down), the face slips: the pain shows what it is
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Villager copy && copy.hasAttached(COPY) && copy.isAlive() && copy.level() instanceof ServerLevel level
				&& (copy.getHealth() <= copy.getMaxHealth() * PAIN_SLIP || Downed.isDowned(copy))) {
				expose(level, copy, source.getEntity() instanceof LivingEntity by ? by : null);
			}
		});
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Villager copy && copy.hasAttached(COPY) && copy.level() instanceof ServerLevel level) {
				expose(level, copy, source.getEntity() instanceof LivingEntity by ? by : null);
				return false;
			}
			return true;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager copy) || !copy.hasAttached(COPY) || !player.isShiftKeyDown()
				|| !player.getMainHandItem().isEmpty() || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			Set<UUID> heard = HEARD.get(copy.getUUID());
			if (heard == null || !heard.contains(player.getUUID())) {
				return InteractionResult.PASS;
			}
			expose(level, copy, player);
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

	/** Whose life a shapeshifter can wear: an MCA villager's, never a plain one's (the copy is always one of MCA's people). */
	public static boolean stealable(Villager v) {
		return McaCompat.isMca(v);
	}

	/** Whether a life can be stolen from {@code home} now: it has one, and none was taken from it in the last {@link #TAKEN_GAP}. */
	public static boolean mayTake(ServerLevel level, @Nullable Faction home) {
		if (home == null) {
			return false;
		}
		ServerLevel over = level.getServer().overworld();
		Long last = over.getAttachedOrElse(TAKEN, Map.of()).get(Integer.toString(home.id()));
		return last == null || over.getGameTime() - last >= TAKEN_GAP;
	}

	/**
	 * {@code v}, held captive, is a stolen life: a copy of them is set aside to take their place in their village. Only an
	 * MCA villager ({@link #stealable}), and only if their settlement has lost nobody lately ({@link #mayTake}).
	 */
	public static void steal(ServerLevel level, Villager v) {
		if (v.hasAttached(STOLEN) || !stealable(v)) {
			return;
		}
		Captives.Captive c = v.getAttached(Captives.CAPTIVE);
		if (c == null || !mayTake(level, Factions.get(level.getServer(), c.home()))) {
			return;
		}
		v.setAttached(STOLEN, true);
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!v.saveAsPassenger(out)) {
			return;
		}
		ServerLevel home = level.getServer().overworld();
		Map<String, Long> taken = new HashMap<>(home.getAttachedOrElse(TAKEN, Map.of()));
		taken.put(Integer.toString(c.home()), home.getGameTime());
		home.setAttached(TAKEN, taken);
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
		if (v.hasAttached(STOLEN) || v.hasAttached(Captives.CAPTIVE) || !stealable(v)) {
			return;
		}
		Faction home = Captives.nearestSettlement(level.getServer(), v.blockPosition());
		if (!mayTake(level, home)) {
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

	/** The real one has come home ({@code rescuer} brought them): whatever wears their face drops it. */
	static void arrived(ServerLevel level, Villager real, @Nullable ServerPlayer rescuer) {
		if (!real.hasAttached(STOLEN)) {
			return;
		}
		for (Villager copy : level.getEntitiesOfClass(Villager.class, real.getBoundingBox().inflate(96.0), c -> real.getUUID().equals(c.getAttached(COPY)))) {
			expose(level, copy, rescuer);
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
				if (!stealable(copy)) {
					// (a copy of a plain villager, from before copies were only ever MCA's people)
					copy.discard();
					continue;
				}
				UUID real = copy.getAttached(COPY);
				if (!level.getEntitiesOfClass(Villager.class, copy.getBoundingBox().inflate(MEET), r -> r.getUUID().equals(real) && r.isAlive()).isEmpty()) {
					// (it turns on the player there, who most likely brought the real one)
					expose(level, copy, player);
				} else if (Downed.isDowned(copy)) {
					expose(level, copy, null);
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
		Set<Integer> homes = new HashSet<>();
		for (Pending p : pending) {
			Faction home = Factions.get(level.getServer(), p.home());
			// (one copy a settlement: any more waiting for the same one are let go)
			if (home == null || home.fallen() || !homes.add(p.home())) {
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
		if (!com.jaymoskingdoms.Loaded.made(level, new BlockPos(x, seat.getY(), z))) {
			return false;
		}
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, seat.getY(), z));
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), p.body()), level,
			EntitySpawnReason.LOAD, e -> {
				e.setUUID(UUID.randomUUID());
				e.snapTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, e.getYRot(), e.getXRot());
				return e;
			});
		if (!(loaded instanceof Villager copy) || !stealable(copy)) {
			// (nothing to wear: it is let go)
			return true;
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

	/**
	 * The copy drops its face ({@code by}: whoever found it out, if anyone; else the nearest player who saw it). It comes out
	 * as hurt as the copy was, and at once fights or flees ({@link Shapeshifter#unmasked}); whoever saw it reacts
	 * ({@link ShapeshifterAlarm}). Every way a copy is found out comes here: a slip caught out, the real one met or brought
	 * home, a moonbeam, and a blow that hurts it badly or downs it.
	 */
	static void expose(ServerLevel level, Villager copy, @Nullable LivingEntity by) {
		if (copy.isRemoved() || !copy.hasAttached(COPY)) {
			return;
		}
		Doubts.raise(level, copy.blockPosition());
		if (Math.floorMod((int) (copy.getAttached(COPY).getMostSignificantBits() >>> 13), CHANGELING_IN) == 0 && stealable(copy)) {
			changeling(level, copy, by);
			return;
		}
		Shapeshifter s = Creatures.SHAPESHIFTER.create(level, EntitySpawnReason.EVENT);
		if (s == null) {
			return;
		}
		UUID real = copy.getAttached(COPY);
		Component wore = copy.getName();
		float hale = Math.max(PAIN_LEAST, Math.min(1.0F, copy.getHealth() / copy.getMaxHealth()));
		s.snapTo(copy.getX(), copy.getY(), copy.getZ(), copy.getYRot(), 0.0F);
		s.setCustomName(wore);
		s.setPersistenceRequired();
		HEARD.remove(copy.getUUID());
		SLIPPED.remove(copy.getUUID());
		copy.removeAttached(COPY);
		copy.discard();
		level.addFreshEntity(s);
		s.setHealth(s.getMaxHealth() * hale);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, s.getX(), s.getY() + 1.0, s.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, s.getX(), s.getY(), s.getZ(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.HOSTILE, 1.0F, 0.6F);
		if (by == null || by instanceof Player p && (p.isSpectator() || p.distanceTo(s) > UNMASK_SEEN)) {
			Player near = level.getNearestPlayer(s, UNMASK_SEEN);
			by = near != null && !near.isSpectator() ? near : by;
		}
		s.unmasked(level, by);
		ShapeshifterAlarm.seen(level, s, wore, by, real);
	}

	/**
	 * The copy was a Sylvan changeling: the stolen face comes off and it stands there as itself, one of the wood's folk with a
	 * face, hair and name of its own. It is a person, not a monster: it never fights, it runs from whoever found it out, and
	 * the villagers who saw cry out.
	 */
	private static void changeling(ServerLevel level, Villager copy, @Nullable LivingEntity by) {
		Component wore = copy.getName();
		HEARD.remove(copy.getUUID());
		SLIPPED.remove(copy.getUUID());
		copy.removeAttached(COPY);
		copy.setHealth(Math.max(copy.getHealth(), copy.getMaxHealth() * PAIN_LEAST));
		McaCompat.ownFace(copy);
		VillagerHeritage.born(copy, Heritage.Kind.SYLVAN, null);
		HeritageVariants.set(copy, HeritageVariants.Variant.CHANGELING);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, copy.getX(), copy.getY() + 1.0, copy.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, copy.getX(), copy.getY(), copy.getZ(), SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundSource.NEUTRAL, 1.0F, 1.3F);
		Frights.scare(level, copy, by != null ? by.position() : copy.position());
		for (Villager w : level.getEntitiesOfClass(Villager.class, copy.getBoundingBox().inflate(UNMASK_SEEN),
			w -> w != copy && w.isAlive() && !w.isBaby() && !w.isSleeping() && !isCopy(w) && w.hasLineOfSight(copy))) {
			Speech.say(level, w, Component.translatable("talk.jaymoskingdoms.shapeshifter.guards.2"), Speech.RED, UNMASK_SEEN);
			break;
		}
		Speech.say(level, copy, Component.translatable("talk.jaymoskingdoms.changeling.found." + level.getRandom().nextInt(3), wore), Speech.WHITE, UNMASK_SEEN);
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
