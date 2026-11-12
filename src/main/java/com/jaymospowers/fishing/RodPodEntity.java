package com.jaymospowers.fishing;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Prediction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Track B's leftover, the rod pod and bite alarm (Advanced Fishing's idea, our own code). Set it down facing the water and
 * rest up to {@link #RODS} baited rods on it: each is cast out ahead ({@link #CAST_LEAST} to {@link #CAST_MOST} blocks)
 * and fished like a held rod (the same fish, the same baits, {@link FishBites#step}). A nibble clicks the alarm; a fish
 * taking the bait sets it ringing, lights its head and gives a full redstone signal for {@link #ALARM_TICKS}: come and
 * pick up that rod with an empty hand in time and you strike, and the fight is on.
 */
public class RodPodEntity extends BlockEntity {
	public static final int RODS = 3;
	static final int CAST_LEAST = 3, CAST_MOST = 9;
	static final int ALARM_TICKS = 160;
	static final int CHECK_EVERY = 4, BEEP_EVERY = 6;

	private final ItemStack[] rods = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
	private final @Nullable Vec3[] bait = new Vec3[RODS];
	private int ringing = -1;
	private long ringsUntil;
	private @Nullable UUID taker;

	public RodPodEntity(BlockPos pos, BlockState state) {
		super(FishingBlocks.ROD_POD_ENTITY, pos, state);
	}

	public ItemStack rod(int slot) {
		return rods[slot];
	}

	public @Nullable Vec3 baitAt(int slot) {
		return bait[slot];
	}

	/** The rod whose alarm is ringing, or -1. */
	public int ringingSlot() {
		return ringing;
	}

	public boolean ringing() {
		return ringing >= 0;
	}

	private Object key(int slot) {
		return "pod:" + worldPosition.getX() + "," + worldPosition.getY() + "," + worldPosition.getZ() + "," + slot;
	}

	private Direction facing() {
		return getBlockState().getValue(FishingBlocks.FACING);
	}

	/** Where a rod in {@code slot} casts to: the farthest water ahead within reach, its bait under a float or on the bed. */
	private @Nullable Vec3 cast(Level level, int slot, Tackle.Rod rod) {
		Direction f = facing();
		Direction side = f.getClockWise();
		int across = slot - 1;
		for (int d = CAST_MOST; d >= CAST_LEAST; d--) {
			for (int dy = 0; dy >= -3; dy--) {
				BlockPos at = worldPosition.relative(f, d).relative(side, across * 2).above(dy);
				if (!level.getFluidState(at).is(FluidTags.WATER) || level.getFluidState(at.above()).is(FluidTags.WATER)) {
					continue;
				}
				double top = LureDepth.surface(level, at);
				double y = rod.bottom() ? LureDepth.bottom(level, at) : Math.max(LureDepth.bottom(level, at), top - FishBites.FLOAT_DEPTH);
				return new Vec3(at.getX() + 0.5, y, at.getZ() + 0.5);
			}
		}
		return null;
	}

	InteractionResult useWith(ItemStack stack, Player player, InteractionHand hand) {
		if (!HookBait.isRod(stack)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		for (int i = 0; i < RODS; i++) {
			if (rods[i].isEmpty()) {
				Vec3 to = cast(level, i, Tackle.rodOf(stack));
				if (to == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.pod_no_water").withStyle(ChatFormatting.GRAY));
					return InteractionResult.SUCCESS;
				}
				rods[i] = stack.split(1);
				bait[i] = to;
				level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.5F, 0.8F);
				if (level instanceof ServerLevel server) {
					server.sendParticles(ParticleTypes.SPLASH, to.x, to.y + 0.5, to.z, 6, 0.1, 0.0, 0.1, 0.1);
				}
				if (rods[i].get(HookBait.BAIT) == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.pod_bare").withStyle(ChatFormatting.GRAY));
				}
				changed();
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.SUCCESS;
	}

	InteractionResult useEmpty(Player player) {
		if (level == null || level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (ringing >= 0 && player instanceof ServerPlayer angler && strike(angler)) {
			return InteractionResult.SUCCESS;
		}
		for (int i = RODS - 1; i >= 0; i--) {
			if (!rods[i].isEmpty()) {
				player.getInventory().placeItemBackInInventory(rods[i], Prediction.SERVER_ONLY);
				rods[i] = ItemStack.EMPTY;
				bait[i] = null;
				FishBites.forget(key(i));
				if (ringing == i) {
					stopRinging();
				}
				changed();
				return InteractionResult.SUCCESS;
			}
		}
		return InteractionResult.PASS;
	}

	/** Picking up the ringing rod is the strike: the rod's in hand, its hook is where the bait lay, and the fish is on. */
	private boolean strike(ServerPlayer player) {
		int slot = ringing;
		Entity e = taker == null || !(level instanceof ServerLevel server) ? null : server.getEntity(taker);
		Vec3 at = bait[slot];
		if (!(e instanceof LivingEntity fish) || !fish.isAlive() || at == null || FishingReal.fight(player).fighting()
			|| !player.getMainHandItem().isEmpty() || player.fishing != null) {
			return false;
		}
		ItemStack rod = rods[slot];
		rods[slot] = ItemStack.EMPTY;
		bait[slot] = null;
		stopRinging();
		player.setItemInHand(InteractionHand.MAIN_HAND, rod);
		FishingHook hook = new FishingHook(player, level, 0, 0);
		hook.snapTo(at.x, Math.max(at.y, LureDepth.surface(level, BlockPos.containing(at)) - 0.1), at.z, 0.0F, 0.0F);
		hook.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(hook);
		FishingReal.startFight(player, FishBites.kindOf(fish), fish, hook);
		changed();
		return true;
	}

	private void stopRinging() {
		ringing = -1;
		taker = null;
		if (level != null) {
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock(), null);
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, RodPodEntity pod) {
		if (!(level instanceof ServerLevel server)) {
			return;
		}
		long now = level.getGameTime();
		if (pod.ringing >= 0) {
			if (now >= pod.ringsUntil) {
				// nobody came: the fish drops the bait and goes
				FishBites.forget(pod.key(pod.ringing));
				pod.stopRinging();
				pod.changed();
			} else if (now % BEEP_EVERY == 0) {
				server.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 1.9F);
				// the fish runs with the bait but stays on it
				Vec3 at = pod.bait[pod.ringing];
				if (pod.taker != null && at != null && server.getEntity(pod.taker) instanceof LivingEntity fish) {
					FishBites.swimTo(fish, at);
				}
			}
			return;
		}
		if (now % CHECK_EVERY != 0) {
			return;
		}
		for (int i = 0; i < RODS; i++) {
			ItemStack rod = pod.rods[i];
			Vec3 at = pod.bait[i];
			if (rod.isEmpty() || at == null) {
				continue;
			}
			Item onHook = baitOn(rod);
			if (onHook == null || onHook == FishingGear.FISHING_MAGNET) {
				continue;
			}
			Tackle.Rod kind = Tackle.rodOf(rod);
			FishBites.Line line = new FishBites.Line(pod.key(i), server, at, kind, onHook, false, kind.bottom());
			switch (FishBites.step(line)) {
				case NIBBLE -> server.playSound(null, pos, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 0.4F, 1.2F);
				case TAKEN -> {
					LivingEntity fish = FishBites.took(line.key());
					Tackle.Bait b = Tackle.baitOf(onHook);
					if (b == null || !b.lure() || server.getRandom().nextFloat() < b.snap()) {
						rod.remove(HookBait.BAIT);
					}
					if (fish != null) {
						pod.ringing = i;
						pod.taker = fish.getUUID();
						pod.ringsUntil = now + ALARM_TICKS;
						server.updateNeighborsAt(pos, state.getBlock(), null);
						server.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 10, 0.2, 0.0, 0.2, 0.2);
						pod.changed();
						return;
					}
				}
				case PICKED -> {
					rod.remove(HookBait.BAIT);
					pod.changed();
				}
				default -> {
				}
			}
		}
	}

	private static @Nullable Item baitOn(ItemStack rod) {
		net.minecraft.resources.Identifier id = HookBait.BAIT == null ? null : rod.get(HookBait.BAIT);
		return id == null ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (level != null) {
			for (int i = 0; i < RODS; i++) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), rods[i]);
				FishBites.forget(key(i));
			}
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < RODS; i++) {
			rods[i] = input.read("rod" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
			bait[i] = input.read("bait" + i, Vec3.CODEC).orElse(null);
		}
		ringing = input.getIntOr("ringing", -1);
		ringsUntil = input.getLongOr("rings_until", 0L);
		if (level != null && level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		for (int i = 0; i < RODS; i++) {
			output.store("rod" + i, ItemStack.OPTIONAL_CODEC, rods[i]);
			if (bait[i] != null) {
				output.store("bait" + i, Vec3.CODEC, bait[i]);
			}
		}
		output.putInt("ringing", ringing);
		output.putLong("rings_until", ringsUntil);
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(out);
		return out.buildResult();
	}
}
