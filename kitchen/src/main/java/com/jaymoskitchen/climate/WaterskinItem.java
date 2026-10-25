package com.jaymoskitchen.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * (0.5.25) A leather waterskin ({@link Thirst}): use it on water to fill it ({@link #SIPS} drinks, dirty), hold use to
 * drink one. Boil it over a lit campfire like a bottle and what's in it is clean. Its bar shows how much is left.
 */
public final class WaterskinItem extends Item {
	public static final int SIPS = 5;
	/** Water from a drink of it, dirty or boiled, and the chance dirty water turns your stomach. */
	static final int DIRTY = 4, CLEAN = 6;
	static final float SICK_CHANCE = 0.25F;

	/** What's in it. */
	public record Water(int sips, boolean clean) {
		public static final Water EMPTY = new Water(0, false);
		public static final Codec<Water> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("sips").forGetter(Water::sips),
			Codec.BOOL.fieldOf("clean").forGetter(Water::clean)
		).apply(i, Water::new));
		public static final StreamCodec<ByteBuf, Water> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Water::sips,
			ByteBufCodecs.BOOL, Water::clean,
			Water::new);
	}

	public WaterskinItem(Item.Properties properties) {
		super(properties);
	}

	public static Water water(ItemStack stack) {
		return stack.getOrDefault(Climate.WATERSKIN_WATER, Water.EMPTY);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
		Water water = water(stack);
		if (hit.getType() == HitResult.Type.BLOCK && water.sips() < SIPS) {
			BlockPos pos = hit.getBlockPos();
			if (level.mayInteract(player, pos) && level.getFluidState(pos).is(FluidTags.WATER)) {
				level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.NEUTRAL, 1.0F, 0.8F);
				level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
				// topping up clean water with river water makes it all dirty
				stack.set(Climate.WATERSKIN_WATER, new Water(SIPS, false));
				return InteractionResult.SUCCESS;
			}
		}
		if (water.sips() <= 0) {
			return InteractionResult.PASS;
		}
		return super.use(level, player, hand);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		Water water = water(stack);
		if (water.sips() <= 0) {
			return stack;
		}
		if (entity instanceof ServerPlayer player) {
			Thirst.drink(player, water.clean() ? CLEAN : DIRTY, water.clean(), SICK_CHANCE);
			if (water.clean()) {
				BodyHeat.cool(player, Thirst.CLEAN_COOL / 2.0F);
			}
		}
		if (!(entity instanceof Player player && player.getAbilities().instabuild)) {
			stack.set(Climate.WATERSKIN_WATER, water.sips() <= 1 ? Water.EMPTY : new Water(water.sips() - 1, water.clean()));
		}
		return stack;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return water(stack).sips() > 0;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Mth.clamp(Math.round(13.0F * water(stack).sips() / SIPS), 0, 13);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return water(stack).clean() ? 0x60C0FF : 0x8A8450;
	}
}
