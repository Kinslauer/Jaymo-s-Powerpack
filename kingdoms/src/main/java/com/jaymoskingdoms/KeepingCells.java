package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The smith's keeping (Andy, 2026-11-16; lore "The old homes and the keeping"). ATi Structures' old home (a lone brick and
 * mud house of the plains and savanna, {@link #OLD_HOME}) has a cell under its floor with a bed, a cauldron and a red light
 * on the wall, and one villager in it: the smith who built the house. His household turned (the husks upstairs); he felt
 * the blight in his own hands, lit the keeping light and lay down under it.
 * <ul>
 * <li><b>Kept</b>: the first time that villager loads he is laid in the cell's bed, asleep, with the blight in him
 * ({@link HollowBlight#BLIGHT}, taken hold). While the light burns he sleeps on, and his blight goes no further. (Before
 * this, ATi placed him half in the floor of a cell too low to stand in, and he mostly suffocated.)</li>
 * <li><b>Found</b>: a player who comes near and can see him (the hatch open) begins the tale {@link #TALE}; using him while he
 * sleeps gets a murmur.</li>
 * <li><b>The light broken</b>: the keeping ends. He wakes coughing with a day left before he turns, opens the hatch and climbs out.</li>
 * <li><b>Cured</b>: the Hollow's Cure, or (his alone) weakness and then a golden apple. Cured awake, he knows who did it and the
 * tale goes on to his thanks. Cured asleep (Andy: "whenever he wakes he doesnt know it was you and wont believe you when you
 * say. so you get nothing"), he sleeps on until the light is broken, and wakes well with nobody to thank.</li>
 * <li><b>Turned</b>: nobody cured him in his day; he rises as one of the dead ({@link BlightSpread}) and the tale ends so.</li>
 * </ul>
 */
public final class KeepingCells {
	/** ATi Structures' old home. */
	static final Identifier OLD_HOME = Identifier.fromNamespaceAndPath("ati_structures", "old_home");
	/** The tale of the smith (data/jaymoskingdoms/tales/the_keeping.json). */
	static final String TALE = "the_keeping";
	/** The marks the tale's waiting steps go on by. */
	static final String WOKE = "keeping_woke", WOKE_CURED = "keeping_woke_cured", CURED = "keeping_cured", CURED_ASLEEP = "keeping_cured_asleep",
		TURNED = "keeping_turned";
	/** How far round a villager the keeping light is looked for (blocks, across; up and down one). */
	static final int LIGHT_REACH = 2;
	/** How far round the light the cell's bed is looked for, and the way out (blocks). */
	static final int BED_REACH = 3, OUT_REACH = 3;
	/** How far into his blight he is held (of a day's turning): taken hold, coughing now and then. */
	static final float HELD = 0.4F;
	/** A player this near him finds him (blocks). */
	static final double FIND = 4.0;
	/** Murmurs in his sleep ({@code keeping.jaymoskingdoms.murmur.<n>}); what he says waking ({@code ...woke.<n>}). */
	static final int MURMURS = 6, WOKE_LINES = 3;
	/** The keeping's colour when it breaks (a faint red, the light's). */
	static final int KEEPING_RED = 0xC04030;
	/** Set on a villager once he has been looked at, kept or not, so nobody is laid down twice. */
	static final String LOOKED_TAG = "jaymoskingdoms.keeping_looked";
	/** Set for good on an old home's smith once he has been laid under his keeping. */
	static final String SMITH_TAG = "jaymoskingdoms.keeping_smith";

	/** On a kept smith: where his keeping light burns. Persistent. */
	static final AttachmentType<BlockPos> KEPT = AttachmentRegistry.create(JaymosKingdoms.id("keeping"),
		builder -> builder.persistent(BlockPos.CODEC));

	/** Villagers just loaded, to look at on the next tick. */
	private static final Set<Villager> FRESH = new HashSet<>();
	/** Kept smiths in loaded chunks. Not kept: they're found again as they load. */
	private static final Set<Villager> KEEPING = new HashSet<>();

	private KeepingCells() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				if (v.hasAttached(KEPT)) {
					KEEPING.add(v);
				} else if (!v.entityTags().contains(LOOKED_TAG)) {
					FRESH.add(v);
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				FRESH.remove(v);
				KEEPING.remove(v);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(KeepingCells::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)
				|| !(world instanceof ServerLevel level) || !(entity instanceof Villager v) || !v.hasAttached(KEPT)) {
				return InteractionResult.PASS;
			}
			return used(level, sp, v);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FRESH.clear();
			KEEPING.clear();
		});
	}

	/** Whether {@code v} is a smith asleep under his keeping. */
	public static boolean kept(Villager v) {
		return v.hasAttached(KEPT);
	}

	// ---- laying him down -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!FRESH.isEmpty()) {
			for (Villager v : new ArrayList<>(FRESH)) {
				FRESH.remove(v);
				if (!v.isRemoved() && v.isAlive() && v.level() instanceof ServerLevel level) {
					look(level, v);
				}
			}
		}
		if (server.getTickCount() % 20 == 3) {
			for (Villager v : new ArrayList<>(KEEPING)) {
				if (v.isRemoved() || !v.isAlive() || !v.hasAttached(KEPT)) {
					KEEPING.remove(v);
					continue;
				}
				hold((ServerLevel) v.level(), v);
			}
		}
	}

	/** A villager loaded for the first time: if he's the smith in an old home's cell, he's laid down under his keeping. */
	private static void look(ServerLevel level, Villager v) {
		BlockPos at = v.blockPosition();
		BlockPos light = null;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-LIGHT_REACH, -1, -LIGHT_REACH), at.offset(LIGHT_REACH, 1, LIGHT_REACH))) {
			if (isLight(level.getBlockState(p))) {
				light = p.immutable();
				break;
			}
		}
		if (light == null) {
			return;
		}
		if (!level.structureManager().getStructureWithPieceAt(light,
			holder -> holder.unwrapKey().map(k -> k.identifier().equals(OLD_HOME)).orElse(false)).isValid()) {
			return;
		}
		v.addTag(LOOKED_TAG);
		BlockPos bed = bed(level, light);
		if (bed == null) {
			return;
		}
		BlightSpread.catchIt(v, new Hollows.Turn(HeritageVariants.Variant.ZOMBIE, 1.0F, "zombie"), null);
		HollowBlight.Blight blight = v.getAttached(HollowBlight.BLIGHT);
		if (blight == null) {
			// (proof against it: there's nothing to keep)
			return;
		}
		long since = level.getGameTime() - (long) (BlightSpread.VILLAGER_TURN * HELD);
		v.setAttached(HollowBlight.BLIGHT, new HollowBlight.Blight(blight.look(), blight.line(), since, blight.told(), false, 0L, 0L));
		v.setAttached(KEPT, light);
		v.addTag(SMITH_TAG);
		v.setPersistenceRequired();
		v.setNoAi(true);
		v.startSleeping(bed);
		KEEPING.add(v);
		JaymosKingdoms.LOGGER.info("An old home's smith ({}) sleeps under his keeping at {}", v.getName().getString(), light.toShortString());
	}

	private static boolean isLight(BlockState state) {
		return state.is(Blocks.REDSTONE_WALL_TORCH) || state.is(Blocks.REDSTONE_TORCH);
	}

	/** The cell's bed near {@code light} (its head, where a sleeper lies), or null. */
	private static @Nullable BlockPos bed(ServerLevel level, BlockPos light) {
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(light.offset(-BED_REACH, -2, -BED_REACH), light.offset(BED_REACH, 1, BED_REACH))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof BedBlock && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD
				&& (best == null || p.distSqr(light) < best.distSqr(light))) {
				best = p.immutable();
			}
		}
		return best;
	}

	// ---- while he sleeps -----------------------------------------------------------------------------------------------

	/** Once a second: the light still burns (he sleeps on, his blight held), or it's gone (he wakes). Players near find him. */
	private static void hold(ServerLevel level, Villager v) {
		BlockPos light = v.getAttached(KEPT);
		if (light == null) {
			return;
		}
		if (level.isLoaded(light) && !isLight(level.getBlockState(light))) {
			wake(level, v, light);
			return;
		}
		HollowBlight.Blight blight = v.getAttached(HollowBlight.BLIGHT);
		if (blight != null) {
			// (the keeping holds it where it was: its clock doesn't run)
			v.setAttached(HollowBlight.BLIGHT, new HollowBlight.Blight(blight.look(), blight.line(), blight.since() + 20L, blight.told(), false, 0L, 0L));
		}
		if (!v.isSleeping()) {
			BlockPos bed = bed(level, light);
			if (bed != null) {
				v.startSleeping(bed);
			}
		}
		// (found once the hatch is open and he can be seen, not through the floor)
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(FIND), p -> !p.isSpectator() && v.hasLineOfSight(p))) {
			Tales.found(level, player, TALE, v);
		}
	}

	/** {@code player} used the sleeping smith: a cure is given (weakness and a golden apple are his own), else he murmurs. */
	private static InteractionResult used(ServerLevel level, ServerPlayer player, Villager v) {
		ItemStack held = player.getMainHandItem();
		if (held.is(BlightCures.CURE)) {
			// (BlightCures gives it; cured() hears of it)
			return InteractionResult.PASS;
		}
		Tales.found(level, player, TALE, v);
		if ((held.is(Items.GOLDEN_APPLE) || held.is(Items.ENCHANTED_GOLDEN_APPLE)) && v.hasEffect(MobEffects.WEAKNESS) && HollowBlight.blighted(v)) {
			if (HollowBlight.cure(v)) {
				if (!player.hasInfiniteMaterials()) {
					held.shrink(1);
				}
				v.removeEffect(MobEffects.WEAKNESS);
				level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 0.9F);
				Fx.burst(level, v.getX(), v.getY() + 0.6, v.getZ(), 0xE8C860, 0.5F, 8, 0.3, 0.2, 0.3);
				BlightSpread.cured(level, v, player);
			}
			return InteractionResult.SUCCESS;
		}
		if (v.isSleeping()) {
			Speech.tell(level, v, player, Component.translatable("keeping.jaymoskingdoms.murmur." + v.getRandom().nextInt(MURMURS)), Speech.WHITE);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	// ---- waking --------------------------------------------------------------------------------------------------------

	/** The keeping light is gone: he wakes, cured already or with a day left to him, and climbs out through the hatch. */
	private static void wake(ServerLevel level, Villager v, BlockPos light) {
		boolean sick = HollowBlight.blighted(v);
		if (sick) {
			// (the keeping is off: his day runs from now)
			HollowBlight.Blight blight = v.getAttached(HollowBlight.BLIGHT);
			v.setAttached(HollowBlight.BLIGHT, new HollowBlight.Blight(blight.look(), blight.line(), level.getGameTime(), blight.told(), false, 0L, 0L));
		}
		Fx.ring(level, Vec3.atCenterOf(light), 1.2F, KEEPING_RED);
		level.playSound(null, light, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.8F, 0.6F);
		release(level, v);
		if (sick) {
			BlightSigns.cough(level, v);
		}
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, v.getBoundingBox().inflate(FIND * 4), p -> !p.isSpectator())) {
			Tales.found(level, player, TALE, v);
			if (sick) {
				Speech.tell(level, v, player, Component.translatable("keeping.jaymoskingdoms.woke." + v.getRandom().nextInt(WOKE_LINES)), Speech.WHITE);
			}
		}
		for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
			Tales.advance(player, TALE, v, sick ? WOKE : WOKE_CURED, v);
		}
	}

	/** He's no longer kept: awake, on his feet, the hatch over the bed open and him up through it. */
	private static void release(ServerLevel level, Villager v) {
		BlockPos light = v.getAttached(KEPT);
		v.removeAttached(KEPT);
		KEEPING.remove(v);
		BlockPos bed = v.getSleepingPos().orElse(light == null ? v.blockPosition() : bed(level, light));
		v.stopSleeping();
		v.setNoAi(false);
		if (bed == null) {
			return;
		}
		// (the hatch: the trapdoors in the ceiling over the bed)
		for (BlockPos p : BlockPos.betweenClosed(bed.offset(-2, 1, -2), bed.offset(2, 3, 2))) {
			BlockState state = level.getBlockState(p);
			if (state.getBlock() instanceof TrapDoorBlock && !state.getValue(TrapDoorBlock.OPEN)) {
				level.setBlock(p.immutable(), state.setValue(TrapDoorBlock.OPEN, true), net.minecraft.world.level.block.Block.UPDATE_ALL);
			}
		}
		level.playSound(null, bed, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.9F);
		Vec3 out = wayOut(level, v, bed);
		v.snapTo(out.x, out.y, out.z, v.getYRot(), 0.0F);
	}

	/** Where he can stand in the house over the cell (the cell is too low to stand in), else on the bed. */
	private static Vec3 wayOut(ServerLevel level, Villager v, BlockPos bed) {
		List<BlockPos> spots = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(bed.offset(-OUT_REACH, 2, -OUT_REACH), bed.offset(OUT_REACH, 4, OUT_REACH))) {
			spots.add(p.immutable());
		}
		spots.sort(Comparator.comparingDouble(p -> p.distSqr(bed)));
		for (BlockPos p : spots) {
			Vec3 at = standAt(level, v, p);
			if (at != null) {
				return at;
			}
		}
		return new Vec3(bed.getX() + 0.5, bed.getY() + 0.5625, bed.getZ() + 0.5);
	}

	private static @Nullable Vec3 standAt(ServerLevel level, Villager v, BlockPos p) {
		VoxelShape here = level.getBlockState(p).getCollisionShape(level, p);
		double top;
		if (here.isEmpty()) {
			if (level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) {
				return null;
			}
			top = p.getY();
		} else {
			double rise = here.max(Direction.Axis.Y);
			if (rise > 0.5) {
				return null;
			}
			top = p.getY() + rise;
		}
		Vec3 at = new Vec3(p.getX() + 0.5, top, p.getZ() + 0.5);
		return level.noCollision(v, v.getDimensions(Pose.STANDING).makeBoundingBox(at)) ? at : null;
	}

	// ---- what the blight's code tells us -------------------------------------------------------------------------------

	/**
	 * (BlightSpread) {@code v} was cured by {@code player}. Asleep, he never knows who; awake, the tale goes on to his thanks.
	 * True if he was asleep under his keeping (so he remembers nobody for it).
	 */
	static boolean cured(ServerLevel level, Villager v, ServerPlayer player) {
		if (!v.entityTags().contains(SMITH_TAG)) {
			return false;
		}
		Tales.found(level, player, TALE, v);
		boolean asleep = v.hasAttached(KEPT);
		for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
			Tales.advance(p, TALE, v, asleep ? CURED_ASLEEP : CURED, v);
		}
		return asleep;
	}

	/** (BlightSpread) {@code v} rose as one of the dead. */
	static void turned(Villager v) {
		if (v.entityTags().contains(SMITH_TAG) && v.level() instanceof ServerLevel level) {
			for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
				Tales.advance(p, TALE, v, TURNED, null);
			}
		}
	}
}
