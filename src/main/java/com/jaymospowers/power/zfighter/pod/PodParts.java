package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.core.CarryBlock;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.47, Andy) The blocks and items the space pod is built from, one per {@link PodPart}: {@code jaymospowers:pod_keel},
 * {@code pod_fin}, {@code pod_bell}, {@code pod_tank}, {@code pod_plate}, {@code pod_canopy}, {@code pod_mast}. They are
 * carried like a crafting table ({@code CarryBlock.carriable}) and set down onto a pod entity you are looking at
 * ({@link #tryAttach}, called from {@code Carry.release}); a keel set down on the ground becomes a bare pod
 * ({@link KeelBlock}). Hitting a pod with a hammer takes the outermost part off again ({@link #takeOff}); a part that was
 * damaged only comes off as scrap ({@link #itemOf}). Where the parts are made: {@code WeaponParts} (fin, plate, mast, tank
 * blanks), the kiln (bell), an Alchemy infusion (canopy; research {@code pod_canopy}). The models are drawn by
 * tools/gen_pod_parts.py; keep the shapes here and the boxes there the same.
 */
public final class PodParts {
	/** How far from the eyes a carried part can be set onto a pod, and how much slack the aim has around the pod (blocks). */
	public static final double REACH = 4.0, AIM_SLACK = 0.6;
	/** Iron nuggets and glass panes a damaged part is worth. */
	public static final int METAL_SCRAP = 3, GLASS_SCRAP = 2;
	/** Tool tag of the hammers that take a pod apart (the mace is in it too). */
	public static final TagKey<Item> HAMMERS = TagKey.create(Registries.ITEM, JaymosPowers.id("hammers"));
	/** The research that teaches the canopy's infusion. */
	public static final String CANOPY_RESEARCH = "pod_canopy";

	/** What setting a carried block down in front of a pod came to. */
	public enum Attach {
		/** Not a part, or no pod in sight, or nothing to fit: set it down the ordinary way. */
		NONE,
		/** It went onto the pod. */
		DONE,
		/** A pod is in sight but won't take it (wrong order, no free slot): the part stays in the arms. */
		REFUSED
	}

	private static final Map<PodPart, Block> BLOCKS = new EnumMap<>(PodPart.class);
	private static final Map<Block, PodPart> PARTS = new HashMap<>();
	private static boolean ready;
	/** Set while the hammer sets a keel block down, so it doesn't turn straight back into a pod. */
	private static boolean settingKeel;

	private PodParts() {
	}

	/** Registers the seven blocks and items, the creative entries, the hammer and the canopy research. Called once. */
	public static void init() {
		if (ready) {
			return;
		}
		ready = true;
		for (PodPart part : PodPart.ORDER) {
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id(part.blockPath()));
			BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().strength(3.0F).noOcclusion().sound(SoundType.COPPER).setId(key);
			VoxelShape shape = shapeOf(part);
			Block block = Registry.register(BuiltInRegistries.BLOCK, key,
				part == PodPart.KEEL ? new KeelBlock(properties, shape) : new PartBlock(properties, shape));
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id(part.blockPath()));
			Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
			BLOCKS.put(part, block);
			PARTS.put(block, part);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (PodPart part : PodPart.ORDER) {
				output.accept(BLOCKS.get(part));
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			// a pod on a trip, the Patrol's or the raiders', or one with somebody in it, is not for taking apart
			if (!(entity instanceof PodEntity pod) || player.isSpectator() || !player.getItemInHand(hand).is(HAMMERS) || pod.locked()
				|| !pod.getPassengers().isEmpty()) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer hitter) {
				takeOff(server, hitter, pod);
			}
			return InteractionResult.SUCCESS;
		});
		Research.register(new Research.Entry(CANOPY_RESEARCH, Research.Category.INFUSION, () -> BLOCKS.get(PodPart.CANOPY).asItem(),
			EssenceList.of(Essence.CRYSTAL, 8, Essence.AIR, 6), List.of("infusion"), 0, 2, 1));
	}

	// ---------------------------------------------------------------- lookups

	/** The block of a part. */
	public static Block block(PodPart part) {
		return BLOCKS.get(part);
	}

	/** The block item of a part. */
	public static Item item(PodPart part) {
		return BLOCKS.get(part).asItem();
	}

	/** The part this block is, or null. */
	public static @Nullable PodPart partOf(BlockState state) {
		return PARTS.get(state.getBlock());
	}

	/**
	 * What a part is as an item: its block item, or when it was damaged only scrap (three iron nuggets for the metal
	 * ones, two glass panes for the canopy).
	 */
	public static ItemStack itemOf(PodPart part, boolean damaged) {
		if (!damaged) {
			return new ItemStack(item(part));
		}
		return part == PodPart.CANOPY ? new ItemStack(Items.GLASS_PANE, GLASS_SCRAP) : new ItemStack(Items.IRON_NUGGET, METAL_SCRAP);
	}

	private static Component nameOf(PodPart part) {
		return Component.translatable("block.jaymospowers." + part.blockPath());
	}

	// ---------------------------------------------------------------- putting a part on

	/** The pod {@code carrier} is looking at within {@link #REACH}, the nearest one, or null. */
	public static @Nullable PodEntity aimedPod(ServerPlayer carrier) {
		Vec3 eye = carrier.getEyePosition();
		Vec3 end = eye.add(carrier.getLookAngle().scale(REACH));
		PodEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (PodEntity pod : carrier.level().getEntitiesOfClass(PodEntity.class, new AABB(eye, end).inflate(AIM_SLACK + 1.0), p -> p.isAlive() && !p.locked())) {
			AABB box = pod.getBoundingBox().inflate(AIM_SLACK);
			Vec3 at = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
			if (at != null && eye.distanceToSqr(at) < bestDistance) {
				best = pod;
				bestDistance = eye.distanceToSqr(at);
			}
		}
		return best;
	}

	/**
	 * Called when {@code carrier} lets go of {@code carried} (a carried block's display) to set it down: if it is a pod part
	 * and they are looking at a pod, fits it into the pod's first free slot, with the display gone, a clang and sparks.
	 * The keel is the one part that goes down on the ground instead when the pod already has one ({@link Attach#NONE}).
	 */
	public static Attach tryAttach(ServerPlayer carrier, Entity carried) {
		PodPart part = partOf(CarryBlock.stateOf(carried));
		if (part == null) {
			return Attach.NONE;
		}
		PodEntity pod = aimedPod(carrier);
		if (pod == null || part == PodPart.KEEL) {
			return Attach.NONE;
		}
		if (!pod.attach(part, false)) {
			Component message = pod.nextFree(part) < 0
				? Component.translatable("message.jaymospowers.pod.full", nameOf(part))
				: Component.translatable("message.jaymospowers.pod.needs_first", nameOf(PodPart.ORDER.get(PodPart.ORDER.indexOf(part) - 1)));
			carrier.sendOverlayMessage(message.copy().withStyle(ChatFormatting.GRAY));
			return Attach.REFUSED;
		}
		carried.discard();
		ServerLevel level = carrier.level();
		Vec3 at = pod.getBoundingBox().getCenter();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 14, 0.5, 0.7, 0.5, 0.3);
		carrier.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.attached", nameOf(part)).withStyle(ChatFormatting.GOLD));
		return Attach.DONE;
	}

	// ---------------------------------------------------------------- taking one off

	/**
	 * A hammer blow on a pod: its outermost part comes off. A sound one is set down as a block at the nearest free spot
	 * beside the pod (the hitter's hands are full of hammer), or dropped as an item if there is none; a damaged one is only
	 * scrap. The keel only ever comes off last, and takes the pod with it.
	 */
	static void takeOff(ServerLevel level, ServerPlayer hitter, PodEntity pod) {
		int bit = pod.outermost();
		boolean[] wasDamaged = new boolean[1];
		PodPart part = bit == 0 ? null : pod.detach(bit, wasDamaged);
		if (part == null) {
			if (bit == 0) {
				pod.discard();
			}
			return;
		}
		Vec3 at = pod.getBoundingBox().getCenter();
		if (wasDamaged[0]) {
			drop(level, at, itemOf(part, true));
			level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.3F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 0.9F);
			hitter.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.scrap", nameOf(part)).withStyle(ChatFormatting.GRAY));
		} else {
			BlockPos spot = freeSpot(level, pod, hitter);
			if (spot != null) {
				boolean keel = part == PodPart.KEEL;
				settingKeel = keel;
				try {
					level.setBlock(spot, block(part).defaultBlockState(), Block.UPDATE_ALL);
				} finally {
					settingKeel = false;
				}
			} else {
				drop(level, at, itemOf(part, false));
			}
			level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8F, 1.0F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.5F, 1.2F);
			hitter.sendOverlayMessage(Component.translatable(part == PodPart.KEEL ? "message.jaymospowers.pod.keel_last" : "message.jaymospowers.pod.detached",
				nameOf(part)).withStyle(ChatFormatting.GRAY));
		}
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.4, 0.5, 0.4, 0.2);
		if (part == PodPart.KEEL) {
			pod.discard();
		}
	}

	private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
		ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, stack);
		item.setDefaultPickUpDelay();
		level.addFreshEntity(item);
	}

	/** The nearest free, standable spot to {@code near} in a ring one to two blocks out from the pod's feet, or null. */
	private static @Nullable BlockPos freeSpot(ServerLevel level, PodEntity pod, ServerPlayer near) {
		BlockPos centre = pod.blockPosition();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				int ring = Math.max(Math.abs(dx), Math.abs(dz));
				if (ring < 2) {
					continue;
				}
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = centre.offset(dx, dy, dz);
					if (level.getBlockState(pos).canBeReplaced() && level.getFluidState(pos).isEmpty()
						&& level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
						double distance = pos.distToCenterSqr(near.position());
						if (distance < bestDistance) {
							best = pos;
							bestDistance = distance;
						}
					}
				}
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- the blocks

	/** The part's outline, the same boxes tools/gen_pod_parts.py draws (pixels of 16). */
	private static VoxelShape shapeOf(PodPart part) {
		return switch (part) {
			case KEEL -> Shapes.or(Block.box(2, 0, 2, 14, 3, 14), Block.box(6, 3, 6, 10, 11, 10));
			case FIN -> Shapes.or(Block.box(6, 0, 2, 10, 2, 10), Block.box(7, 2, 2, 9, 11, 6));
			case BELL -> Shapes.or(Block.box(4, 0, 4, 12, 3, 12), Block.box(5, 3, 5, 11, 7, 11), Block.box(6, 7, 6, 10, 11, 10));
			case TANK -> Shapes.or(Block.box(4, 0, 4, 12, 13, 12), Block.box(3, 5, 3, 13, 8, 13));
			case PLATE -> Block.box(1, 1, 6, 15, 15, 9);
			case CANOPY -> Shapes.or(Block.box(3, 0, 3, 13, 3, 13), Block.box(4, 3, 4, 12, 9, 12), Block.box(6, 9, 6, 10, 12, 10));
			case MAST -> Shapes.or(Block.box(7, 0, 7, 9, 13, 9), Block.box(6, 13, 6, 10, 16, 10));
		};
	}

	/** A pod part set down as a block. */
	private static class PartBlock extends Block {
		private final VoxelShape shape;

		PartBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
			super(properties);
			this.shape = shape;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return shape;
		}
	}

	/** The keel: set down on the ground it stands up as a bare pod (keel only), the one part that is a pod by itself. */
	private static final class KeelBlock extends PartBlock {
		KeelBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
			super(properties, shape);
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
			if (settingKeel || oldState.is(this) || !(level instanceof ServerLevel server)) {
				return;
			}
			PodEntity pod = new PodEntity(PodEntity.TYPE, server);
			pod.setPos(Vec3.atBottomCenterOf(pos));
			pod.attach(PodPart.KEEL, false);
			server.removeBlock(pos, false);
			server.addFreshEntity(pod);
			server.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7F, 0.9F);
		}
	}
}
