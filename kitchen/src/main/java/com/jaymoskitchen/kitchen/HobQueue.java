package com.jaymoskitchen.kitchen;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.cooking.Kitchen;
import com.jaymoskitchen.cooking.Quality;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.blay09.mods.cookingforblockheads.block.OvenBlock;
import net.blay09.mods.cookingforblockheads.block.entity.OvenBlockEntity;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dishes finishing on an oven after their minigame: each takes its own time (see {@link Dish#hobTicks()}), bubbling,
 * sizzling or baking meanwhile, then goes to the cook if they're close by, or pops out on top of the oven. Saved with
 * the oven (so a reload doesn't lose them); several can be cooking at once; breaking the oven drops them.
 */
public final class HobQueue {
	/** A dish on the oven: the finished stack, its quality, the game time it's ready, and who cooked it. */
	public record Pending(ItemStack stack, Quality quality, long readyTime, UUID cook) {
		public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.CODEC.fieldOf("stack").forGetter(Pending::stack),
			Codec.STRING.xmap(HobQueue::quality, Quality::name).fieldOf("quality").forGetter(Pending::quality),
			Codec.LONG.fieldOf("ready").forGetter(Pending::readyTime),
			UUIDUtil.CODEC.fieldOf("cook").forGetter(Pending::cook)
		).apply(i, Pending::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pending> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Pending::stack,
			ByteBufCodecs.STRING_UTF8.map(HobQueue::quality, Quality::name), Pending::quality,
			ByteBufCodecs.VAR_LONG, Pending::readyTime,
			UUIDUtil.STREAM_CODEC, Pending::cook,
			Pending::new);
	}

	/**
	 * What's cooking on the oven, oldest first. Saved with the oven, and synced to clients so it shows on the rack inside
	 * (see the client's OvenRendererMixin). Always replaced as a whole (never changed in place), so every change syncs.
	 */
	public static final AttachmentType<List<Pending>> PENDING = AttachmentRegistry.create(JaymosKitchen.id("oven_pending"),
		builder -> builder.persistent(Pending.CODEC.listOf())
			.syncWith(Pending.STREAM_CODEC.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.all()));

	/** How close (squared) the cook must be for a ready dish to go straight into their pockets. */
	private static final double DELIVER_DISTANCE_SQ = 16.0 * 16.0;

	private HobQueue() {
	}

	/** Loads the class so the attachment type is registered before any oven is read from disk. */
	public static void register() {
	}

	private static Quality quality(String name) {
		try {
			return Quality.valueOf(name);
		} catch (IllegalArgumentException e) {
			return Quality.GOOD;
		}
	}

	public static List<Pending> pending(OvenBlockEntity oven) {
		List<Pending> list = oven.getAttached(PENDING);
		return list == null ? List.of() : list;
	}

	/** The dish of each entry cooking on the oven, oldest first (an entry that isn't one of ours is left out). */
	public static List<Dish> pendingDishes(OvenBlockEntity oven) {
		List<Dish> dishes = new ArrayList<>();
		for (Pending p : pending(oven)) {
			Dish dish = ModItems.dishOf(p.stack().getItem());
			if (dish != null) {
				dishes.add(dish);
			}
		}
		return dishes;
	}

	private static void set(OvenBlockEntity oven, List<Pending> list) {
		if (list.isEmpty()) {
			oven.removeAttached(PENDING);
		} else {
			oven.setAttached(PENDING, List.copyOf(list));
		}
		oven.setChanged();
	}

	/** Puts a finished dish on the oven for {@code ticks} game ticks; it goes to {@code cook} when it's done. */
	public static void add(OvenBlockEntity oven, ServerLevel level, ItemStack stack, Quality quality, UUID cook, int ticks) {
		List<Pending> list = new ArrayList<>(pending(oven));
		list.add(new Pending(stack.copy(), quality, level.getGameTime() + ticks, cook));
		set(oven, list);
	}

	/** "M:SS" for a number of ticks. */
	public static String clock(long ticks) {
		long seconds = Math.max(0, (ticks + 19) / 20);
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	/** One line per dish on the oven: its name and how long it has left (e.g. for a sneak-click readout). */
	public static List<Component> describe(OvenBlockEntity oven) {
		List<Component> lines = new ArrayList<>();
		Level level = oven.getLevel();
		long now = level == null ? 0 : level.getGameTime();
		for (Pending p : pending(oven)) {
			lines.add(Component.translatable("message.jaymoskitchen.hob_pending", p.stack().getHoverName(),
				clock(p.readyTime() - now)));
		}
		return lines;
	}

	private enum Look {
		POT, PAN, BAKE
	}

	private static Look look(ItemStack stack) {
		Dish dish = ModItems.dishOf(stack.getItem());
		if (dish == null) {
			return Look.BAKE;
		}
		return switch (dish.method()) {
			case SEQUENCE, STIR -> Look.POT;
			case GRILL, SEAR, CHOP -> Look.PAN;
			case OVEN, SMOKE, ROAST -> Look.BAKE;
		};
	}

	/** Server, every tick the oven ticks: show what's cooking, and serve whatever's done. */
	public static void serverTick(Level world, BlockPos pos, OvenBlockEntity oven) {
		if (!(world instanceof ServerLevel level)) {
			return;
		}
		List<Pending> list = pending(oven);
		if (list.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		boolean pot = false, pan = false, bake = false;
		for (Pending p : list) {
			switch (look(p.stack())) {
				case POT -> pot = true;
				case PAN -> pan = true;
				case BAKE -> bake = true;
			}
		}
		effects(level, pos, now, pot, pan, bake);

		List<Pending> left = null;
		for (Pending p : list) {
			if (now >= p.readyTime()) {
				if (left == null) {
					left = new ArrayList<>(list);
				}
				left.remove(p);
			}
		}
		if (left == null) {
			return;
		}
		// take them off the oven first, then hand them over
		set(oven, left);
		for (Pending p : list) {
			if (now >= p.readyTime()) {
				deliver(level, pos, p);
			}
		}
	}

	private static void effects(ServerLevel level, BlockPos pos, long now, boolean pot, boolean pan, boolean bake) {
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		if (now % 4 == 0) {
			if (pot) {
				level.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.35, z, 2, 0.15, 0.03, 0.15, 0.01);
				level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 1, 0.1, 0.05, 0.1, 0.01);
			}
			if (pan) {
				level.sendParticles(ParticleTypes.SMOKE, x, y + 0.25, z, 2, 0.15, 0.03, 0.15, 0.01);
			}
		}
		if (bake && now % 10 == 0) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof OvenBlock) {
				Direction front = state.getValue(OvenBlock.FACING);
				double fx = pos.getX() + 0.5 + front.getStepX() * 0.55;
				double fz = pos.getZ() + 0.5 + front.getStepZ() * 0.55;
				level.sendParticles(ParticleTypes.SMOKE, fx, pos.getY() + 0.85, fz, 1, 0.15, 0.02, 0.15, 0.005);
			}
		}
		if (now % 60 == 0) {
			if (pot) {
				level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.5F, 0.9F);
			} else if (pan) {
				level.playSound(null, pos, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 1.2F);
			} else {
				level.playSound(null, pos, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5F, 1.0F);
			}
		}
	}

	private static void deliver(ServerLevel level, BlockPos pos, Pending p) {
		ItemStack stack = p.stack().copy();
		// named before it's handed over: once it's in the inventory this stack is empty
		Component name = stack.getHoverName();
		ServerPlayer cook = level.getServer().getPlayerList().getPlayer(p.cook());
		if (cook != null && cook.isAlive() && cook.level() == level
			&& cook.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= DELIVER_DISTANCE_SQ) {
			Kitchen.serve(cook, stack, level, pos, p.quality());
			cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.ready", name));
			return;
		}
		Block.popResource(level, pos.above(), stack);
		level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 0.8F);
		if (cook != null) {
			cook.sendOverlayMessage(Component.translatable("message.jaymoskitchen.ready_on_oven", name));
		}
	}

	/** The oven's being removed: whatever was cooking on it drops where it stood. */
	public static void dropAll(Level level, BlockPos pos, OvenBlockEntity oven) {
		List<Pending> list = pending(oven);
		if (list.isEmpty() || level == null || level.isClientSide()) {
			return;
		}
		oven.removeAttached(PENDING);
		for (Pending p : list) {
			Block.popResource(level, pos, p.stack().copy());
		}
	}
}
