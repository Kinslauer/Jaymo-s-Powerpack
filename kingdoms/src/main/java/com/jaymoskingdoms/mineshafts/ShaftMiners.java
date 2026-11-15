package com.jaymoskingdoms.mineshafts;

import com.jaymoskingdoms.Builders;
import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.jaymoskingdoms.Professions;
import com.jaymoskingdoms.QuakeRescue;
import com.jaymoskingdoms.Storage;
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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (medieval picks, mm-miners) A settlement within {@link #RANGE} of a mineshaft sends {@link #MINERS} of its miners into
 * it ({@link Professions#MINER}, a pick in hand), once, the first time a player is near both. They work its upper part
 * (its shallowest piece and what lies within {@link #WORKED_DEPTH} under it): they hang lanterns where it is dark, push
 * the carts standing on its rails along, and pick the ore they can see off its walls (never a block with water or lava
 * against it). Like the settlement's own miners ({@code Gatherers}), they carry what they dig to its storehouse when
 * their arms are full or night falls, and walk back to the shaft; where the shaft has no way up that they can find, their
 * load goes up the shaft to the storehouse without them. The deeper rooms are left as they
 * were, and their dwellers too ({@link ShaftDwellers} keep fewer to the worked part). A quake may bring the ceiling down
 * on one ({@link ShaftCollapse}): their settlement asks for them to be dug out ({@link QuakeRescue}).
 */
public final class ShaftMiners {
	// ---- tuning ----
	/** How often the shafts round the players are looked over (ticks), and how many chunks round each. */
	static final int EVERY = 400, LOOK_CHUNKS = 3;
	/** How far from a settlement's seat a shaft may be to be worked from it (blocks), and how many miners it sends. */
	static final double RANGE = 160.0;
	static final int MINERS = 2;
	/** How far a miner works from where they started (blocks); how deep under it the worked part goes. */
	static final int WORK_RANGE = 24, WORKED_DEPTH = 10;
	/** How often a miner does a piece of work (ticks), and how far they look for it (blocks). */
	static final int WORK_EVERY = 60, REACH = 6;
	/** A lantern is hung where the block light is under this. */
	static final int LIGHT_BELOW = 7;
	/** How hard a miner pushes a cart. */
	static final double PUSH = 0.35;
	/** They carry this many items before taking them to the storehouse (as {@code Gatherers}' miners do). */
	static final int ARMFUL = 48;
	/** How far they walk at a stretch (blocks), how near the depot counts as there, and how long with no nearer before the load goes up without them (ticks). */
	static final double LEG = 24.0, DEPOT_REACH = 3.5, WALK_SPEED = 0.65;
	static final int STUCK_TICKS = 480;

	/** On a shaft miner: their shaft (its start chunk), their settlement, and where they work from. Saved. */
	public record Post(long shaft, int faction, long spot) {
		static final Codec<Post> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("shaft").forGetter(Post::shaft),
				Codec.INT.fieldOf("faction").forGetter(Post::faction),
				Codec.LONG.fieldOf("spot").forGetter(Post::spot))
			.apply(i, Post::new));
	}

	static final AttachmentType<Post> POST = AttachmentRegistry.create(JaymosKingdoms.id("shaft_miner"), b -> b.persistent(Post.CODEC));
	/** On a level: the shafts a settlement has sent miners into (their start chunks). Saved. */
	static final AttachmentType<List<Long>> MANNED = AttachmentRegistry.create(JaymosKingdoms.id("shafts_manned"), b -> b.persistent(Codec.LONG.listOf()));
	/** On a shaft miner: what they have dug and carry for the storehouse. Saved. */
	static final AttachmentType<List<ItemStack>> LOAD = AttachmentRegistry.create(JaymosKingdoms.id("shaft_miner_load"),
		b -> b.persistent(ItemStack.CODEC.listOf()));

	/** A miner on the way to the storehouse (true) or back (false), the nearest they've come, and when they last came nearer. */
	private static final class Trip {
		boolean out = true;
		double best = Double.MAX_VALUE;
		long nearerAt;
	}

	private static final Map<UUID, Trip> TRIPS = new HashMap<>();

	/** The shaft miners about now. */
	private static final Map<UUID, Villager> ABOUT = new HashMap<>();

	private ShaftMiners() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.hasAttached(POST)) {
				ABOUT.put(v.getUUID(), v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> ABOUT.remove(entity.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ABOUT.clear();
			TRIPS.clear();
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			long now = level.getGameTime();
			if (now % EVERY == 0L && !level.players().isEmpty()) {
				look(level);
			}
			if (now % WORK_EVERY == 0L && !ABOUT.isEmpty()) {
				for (Villager v : List.copyOf(ABOUT.values())) {
					if (v.level() == level && v.isAlive()) {
						work(level, v);
					}
				}
			}
		});
	}

	/** The shaft miners about in {@code level}. */
	static List<Villager> miners(ServerLevel level) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : ABOUT.values()) {
			if (v.level() == level && v.isAlive()) {
				out.add(v);
			}
		}
		return out;
	}

	/** Whether {@code pos} is in a shaft's worked part: near a miner, no deeper than {@link #WORKED_DEPTH} under where they work. */
	static boolean worked(ServerLevel level, BlockPos pos) {
		for (Villager v : ABOUT.values()) {
			if (v.level() != level) {
				continue;
			}
			BlockPos spot = BlockPos.of(v.getAttached(POST).spot());
			if (Math.abs(spot.getX() - pos.getX()) <= WORK_RANGE * 2 && Math.abs(spot.getZ() - pos.getZ()) <= WORK_RANGE * 2
				&& pos.getY() >= spot.getY() - WORKED_DEPTH) {
				return true;
			}
		}
		return false;
	}

	private static void look(ServerLevel level) {
		Set<Long> seen = new HashSet<>();
		var structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			int px = player.blockPosition().getX() >> 4, pz = player.blockPosition().getZ() >> 4;
			for (int cx = px - LOOK_CHUNKS; cx <= px + LOOK_CHUNKS; cx++) {
				for (int cz = pz - LOOK_CHUNKS; cz <= pz + LOOK_CHUNKS; cz++) {
					if (!level.hasChunk(cx, cz)) {
						continue;
					}
					for (StructureStart start : level.structureManager().startsForStructure(cx, cz,
							(Structure s) -> structures.wrapAsHolder(s).is(Mineshafts.SHAFTS))) {
						long key = start.getChunkPos().pack();
						if (seen.add(key)) {
							man(level, start, key);
						}
					}
				}
			}
		}
	}

	/** A settlement near {@code start} sends its miners in, if none has yet. */
	private static void man(ServerLevel level, StructureStart start, long key) {
		List<Long> manned = level.getAttachedOrElse(MANNED, List.<Long>of());
		if (manned.contains(key) || start.getPieces().isEmpty()) {
			return;
		}
		BlockPos middle = start.getBoundingBox().getCenter();
		Faction f = Factions.around(level.getServer(), level, new BlockPos(middle.getX(), level.getSeaLevel(), middle.getZ()), RANGE);
		if (f == null || f.fallen()) {
			return;
		}
		// the shallowest piece: where the work starts
		StructurePiece top = start.getPieces().get(0);
		for (StructurePiece piece : start.getPieces()) {
			if (piece.getBoundingBox().maxY() > top.getBoundingBox().maxY()) {
				top = piece;
			}
		}
		BlockPos spot = floor(level, top.getBoundingBox());
		if (spot == null) {
			return;
		}
		List<Long> next = new ArrayList<>(manned);
		next.add(key);
		level.setAttached(MANNED, next);
		for (int i = 0; i < MINERS; i++) {
			Vec3 at = Vec3.atBottomCenterOf(spot);
			Villager v = McaCompat.newVillager(level, at);
			if (v == null) {
				continue;
			}
			v.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
			v.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.EVENT, null);
			v.setVillagerData(v.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(Professions.MINER)));
			v.setVillagerXp(Math.max(1, v.getVillagerXp()));
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			v.setPersistenceRequired();
			v.setHomeTo(spot, WORK_RANGE);
			v.setAttached(POST, new Post(key, f.id(), spot.asLong()));
			level.addFreshEntity(v);
			ABOUT.put(v.getUUID(), v);
		}
	}

	/** A floor to stand on inside {@code box} (feet and head open, footing under, not under the sky), nearest its middle. */
	static @Nullable BlockPos floor(ServerLevel level, BoundingBox box) {
		BlockPos middle = box.getCenter();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.isLoaded(at) && open(level, at) && open(level, at.above()) && level.getBlockState(at.below()).isSolid() && !level.canSeeSky(at)) {
				double d = at.distSqr(middle);
				if (d < bestD) {
					bestD = d;
					best = at.immutable();
				}
			}
		}
		return best;
	}

	static boolean open(ServerLevel level, BlockPos at) {
		return level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getFluidState(at).isEmpty();
	}

	/** A piece of a miner's work: a lantern where it is dark, a cart pushed along, or ore off the wall. */
	private static void work(ServerLevel level, Villager v) {
		Post post = v.getAttached(POST);
		if (post == null || !level.isLoaded(v.blockPosition())) {
			return;
		}
		RandomSource random = v.getRandom();
		BlockPos spot = BlockPos.of(post.spot());
		if (!v.hasHome()) {
			v.setHomeTo(spot, WORK_RANGE);
		}
		if (TRIPS.containsKey(v.getUUID()) || count(v) >= ARMFUL || count(v) > 0 && !level.isBrightOutside()) {
			carry(level, v, post, spot);
			return;
		}
		if (v.blockPosition().distSqr(spot) > WORK_RANGE * WORK_RANGE && v.getNavigation().isDone()) {
			v.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0.6);
		}
		switch (random.nextInt(3)) {
			case 0 -> light(level, v, random);
			case 1 -> cart(level, v);
			default -> ore(level, v, random);
		}
	}

	/** How many things they carry for the storehouse. */
	private static int count(Villager v) {
		int n = 0;
		for (ItemStack s : v.getAttachedOrElse(LOAD, List.<ItemStack>of())) {
			n += s.getCount();
		}
		return n;
	}

	/** Their load to the storehouse and back, a stretch at a time; with no way up from the shaft, it goes up without them. */
	private static void carry(ServerLevel level, Villager v, Post post, BlockPos spot) {
		Faction f = Factions.get(level.getServer(), post.faction());
		if (f == null || f.fallen()) {
			v.removeAttached(LOAD);
			TRIPS.remove(v.getUUID());
			return;
		}
		Trip trip = TRIPS.computeIfAbsent(v.getUUID(), id -> {
			Trip t = new Trip();
			t.nearerAt = level.getGameTime();
			return t;
		});
		BlockPos depot = Builders.depot(f);
		BlockPos to = trip.out ? depot : spot;
		Vec3 goal = Vec3.atBottomCenterOf(to);
		double d = v.position().distanceTo(goal);
		if (trip.out ? d <= DEPOT_REACH : d <= WORK_RANGE) {
			if (trip.out) {
				unload(level, f, v, depot);
				back(v, trip, level.getGameTime());
			} else {
				TRIPS.remove(v.getUUID());
			}
			return;
		}
		if (d < trip.best - 1.0) {
			trip.best = d;
			trip.nearerAt = level.getGameTime();
		} else if (level.getGameTime() - trip.nearerAt > STUCK_TICKS) {
			if (trip.out && level.isLoaded(depot)) {
				// no way up they can find: the load goes up the shaft to the storehouse without them
				unload(level, f, v, null);
				TRIPS.remove(v.getUUID());
			} else if (!trip.out) {
				TRIPS.remove(v.getUUID());
			}
			return;
		}
		if (v.getNavigation().isDone()) {
			Vec3 leg = goal.subtract(v.position());
			if (leg.length() > LEG) {
				leg = leg.normalize().scale(LEG);
			}
			Vec3 at = v.position().add(leg);
			v.getNavigation().moveTo(at.x, at.y, at.z, WALK_SPEED);
		}
	}

	private static void back(Villager v, Trip trip, long now) {
		trip.out = false;
		trip.best = Double.MAX_VALUE;
		trip.nearerAt = now;
	}

	/** Their load into the storehouse ({@code Storage.put}, as {@code Gatherers}' miners do); what won't fit is set down at the depot, or (sent up) kept. */
	private static void unload(ServerLevel level, Faction f, Villager v, @Nullable BlockPos depot) {
		List<ItemStack> kept = new ArrayList<>();
		for (ItemStack stack : v.getAttachedOrElse(LOAD, List.<ItemStack>of())) {
			ItemStack left = Storage.put(level, f, stack);
			if (!left.isEmpty()) {
				if (depot != null) {
					Block.popResource(level, depot, left);
				} else {
					kept.add(left);
				}
			}
		}
		if (kept.isEmpty()) {
			v.removeAttached(LOAD);
		} else {
			v.setAttached(LOAD, kept);
		}
		if (depot != null) {
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, depot, SoundEvents.BARREL_CLOSE, SoundSource.NEUTRAL, 0.5F, 1.0F);
		}
	}

	/** A lantern hung (or set down) where it is dark near them. */
	private static void light(ServerLevel level, Villager v, RandomSource random) {
		for (int tries = 0; tries < 8; tries++) {
			BlockPos at = v.blockPosition().offset(random.nextInt(REACH * 2 + 1) - REACH, random.nextInt(4) - 1, random.nextInt(REACH * 2 + 1) - REACH);
			if (!level.getBlockState(at).isAir() || level.getBrightness(LightLayer.BLOCK, at) >= LIGHT_BELOW || level.canSeeSky(at)
				|| !Mineshafts.in(level, at)) {
				continue;
			}
			BlockState hung = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
			BlockState stood = Blocks.LANTERN.defaultBlockState();
			BlockState lantern = hung.canSurvive(level, at) ? hung : stood.canSurvive(level, at) && !open(level, at.below()) ? stood : null;
			if (lantern != null) {
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.setBlock(at, lantern, Block.UPDATE_ALL);
				level.playSound(null, at, SoundEvents.LANTERN_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
				return;
			}
		}
	}

	/** A cart standing on the rails near them pushed on, away from them. */
	private static void cart(ServerLevel level, Villager v) {
		for (AbstractMinecart cart : level.getEntitiesOfClass(AbstractMinecart.class, new AABB(v.blockPosition()).inflate(REACH),
				c -> c.getDeltaMovement().horizontalDistanceSqr() < 0.0004)) {
			BlockPos rail = cart.blockPosition();
			if (!(level.getBlockState(rail).getBlock() instanceof BaseRailBlock) && !(level.getBlockState(rail.below()).getBlock() instanceof BaseRailBlock)) {
				continue;
			}
			Vec3 away = cart.position().subtract(v.position()).multiply(1.0, 0.0, 1.0);
			if (away.lengthSqr() < 0.01) {
				away = Vec3.directionFromRotation(0.0F, v.getYRot());
			}
			away = away.normalize().scale(PUSH);
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			cart.push(away.x, 0.0, away.z);
			level.playSound(null, cart.blockPosition(), SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, 0.4F, 1.0F);
			return;
		}
	}

	/** Ore they can see in the wall near them (loaded, with no water or lava against it), picked off and carried for the storehouse. */
	private static void ore(ServerLevel level, Villager v, RandomSource random) {
		BlockPos feet = v.blockPosition();
		for (int tries = 0; tries < 12; tries++) {
			BlockPos at = feet.offset(random.nextInt(REACH + 1) - REACH / 2, random.nextInt(4) - 1, random.nextInt(REACH + 1) - REACH / 2);
			if (!level.isLoaded(at)) {
				continue;
			}
			BlockState s = level.getBlockState(at);
			if (!ore(s)) {
				continue;
			}
			boolean seen = false, wet = false;
			for (Direction d : Direction.values()) {
				BlockPos n = at.relative(d);
				if (!level.isLoaded(n)) {
					wet = true;
					break;
				}
				seen |= level.getBlockState(n).isAir();
				wet |= !level.getFluidState(n).isEmpty();
			}
			if (!seen || wet) {
				continue;
			}
			v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			List<ItemStack> load = new ArrayList<>(v.getAttachedOrElse(LOAD, List.<ItemStack>of()));
			load.addAll(Block.getDrops(s, level, at, null, v, v.getMainHandItem()));
			v.setAttached(LOAD, load);
			level.destroyBlock(at, false, v);
			return;
		}
	}

	static boolean ore(BlockState s) {
		return s.is(net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags.ORES) || s.is(BlockTags.IRON_ORES) || s.is(BlockTags.COPPER_ORES)
			|| s.is(BlockTags.GOLD_ORES);
	}

	/** The ceiling came down on {@code v}: shut in, and their settlement asks for them to be dug out. */
	static void trapped(ServerLevel level, Villager v) {
		Post post = v.getAttached(POST);
		Faction f = post == null ? null : Factions.get(level.getServer(), post.faction());
		if (f != null && !f.fallen()) {
			QuakeRescue.shutIn(level, f, v);
		}
	}
}
