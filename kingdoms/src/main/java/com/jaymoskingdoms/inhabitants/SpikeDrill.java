package com.jaymoskingdoms.inhabitants;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The spike drill, after Inhabitants': three Impaler spikes turned by copper and redstone, mining as an iron pickaxe does.
 * The longer it runs the faster it bites, and the hotter it gets. Past its limit it burns the hand that holds it and
 * stops until it has cooled; it cools by itself while it rests, and a snowball dropped on it in the inventory cools it
 * at once.
 *
 * <p>Its state rides on its custom model data, so the client mines at the speed the server does and its look follows its
 * heat (items/spike_drill.json): floats 0 heat and 1 spin; flags 0 overheated and 1 running.
 */
public class SpikeDrill extends Item {
	/** Heat: its limit; what each block mined adds; what it sheds each second at rest; what a snowball takes off. */
	static final float MAX_HEAT = 120.0F, HEAT_PER_BLOCK = 8.0F, COOL_PER_SECOND = 5.0F, SNOWBALL_COOLS = 40.0F;
	/** Overheated, it won't bite until it is down to this; it is out of the hand this long (ticks), and burns this much. */
	static final float RESTART_HEAT = 60.0F, BURN = 2.0F;
	static final int OVERHEAT_COOLDOWN = 40;
	/** Spin: up one per block mined, to this; down one each second it rests. Each step adds this share of its speed. */
	static final float MAX_SPIN = 4.0F, SPIN_SPEEDUP = 0.3F;
	/** How fast it bites while overheated (a bare hand's speed). */
	static final float STALLED_SPEED = 1.0F;

	static final CustomModelData COLD = data(0.0F, 0.0F, false);

	public SpikeDrill(Properties properties) {
		super(properties);
	}

	static CustomModelData data(float heat, float spin, boolean overheated) {
		return new CustomModelData(List.of(heat, spin), List.of(overheated, spin > 0.0F), List.of(), List.of());
	}

	static float heat(ItemStack stack) {
		return value(stack, 0);
	}

	static float spin(ItemStack stack) {
		return value(stack, 1);
	}

	static boolean overheated(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data != null && Boolean.TRUE.equals(data.getBoolean(0));
	}

	private static float value(ItemStack stack, int index) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		Float value = data == null ? null : data.getFloat(index);
		return value == null ? 0.0F : value;
	}

	private static void set(ItemStack stack, float heat, float spin, boolean overheated) {
		stack.set(DataComponents.CUSTOM_MODEL_DATA, data(Mth.clamp(heat, 0.0F, MAX_HEAT), Mth.clamp(spin, 0.0F, MAX_SPIN), overheated));
	}

	@Override
	public float getDestroySpeed(ItemStack stack, BlockState state) {
		float speed = super.getDestroySpeed(stack, state);
		if (speed <= 1.0F) {
			return speed;
		}
		return overheated(stack) ? STALLED_SPEED : speed * (1.0F + SPIN_SPEEDUP * spin(stack));
	}

	@Override
	public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
		boolean mined = super.mineBlock(stack, level, state, pos, miner);
		if (!(level instanceof ServerLevel server) || state.getDestroySpeed(level, pos) == 0.0F || overheated(stack)) {
			return mined;
		}
		float spin = spin(stack);
		float heat = heat(stack) + HEAT_PER_BLOCK;
		server.playSound(null, pos, spin <= 0.0F ? ImpalerGear.DRILL_START : ImpalerGear.DRILL_DIG, SoundSource.PLAYERS, 0.8F,
			0.9F + 0.05F * spin);
		if (heat >= MAX_HEAT) {
			set(stack, MAX_HEAT, 0.0F, true);
			this.overheat(server, stack, miner);
		} else {
			set(stack, heat, spin + 1.0F, false);
		}
		return mined;
	}

	/** Too hot: it stalls with a hiss, and burns the hand. */
	private void overheat(ServerLevel level, ItemStack stack, LivingEntity miner) {
		level.playSound(null, miner.getX(), miner.getY(), miner.getZ(), ImpalerGear.DRILL_STOP, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, miner.getX(), miner.getY(), miner.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, miner.getX(), miner.getY(1.0), miner.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
		miner.hurtServer(level, level.damageSources().hotFloor(), BURN);
		if (miner instanceof Player player) {
			player.getCooldowns().addCooldown(stack, OVERHEAT_COOLDOWN);
		}
	}

	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		if (owner.tickCount % 20 != 0) {
			return;
		}
		float heat = heat(stack), spin = spin(stack);
		if (heat <= 0.0F && spin <= 0.0F) {
			return;
		}
		if (spin > 0.0F && spin - 1.0F <= 0.0F && !overheated(stack)) {
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), ImpalerGear.DRILL_STOP, SoundSource.PLAYERS, 0.6F, 1.0F);
		}
		float cooler = heat - COOL_PER_SECOND;
		set(stack, cooler, spin - 1.0F, overheated(stack) && cooler > RESTART_HEAT);
	}

	/** A snowball dropped on it cools it. */
	@Override
	public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (!other.is(Items.SNOWBALL) || heat(stack) <= 0.0F || !slot.allowModification(player)) {
			return false;
		}
		float cooler = heat(stack) - SNOWBALL_COOLS;
		set(stack, cooler, spin(stack), overheated(stack) && cooler > RESTART_HEAT);
		other.shrink(1);
		player.playSound(SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.6F);
		return true;
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return heat(stack) > 0.0F || super.isBarVisible(stack);
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		float heat = heat(stack);
		return heat > 0.0F ? Math.round(13.0F * heat / MAX_HEAT) : super.getBarWidth(stack);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		float heat = heat(stack);
		if (heat <= 0.0F) {
			return super.getBarColor(stack);
		}
		// amber to red as it nears its limit
		return Mth.hsvToRgb(Math.max(0.0F, 0.12F * (1.0F - heat / MAX_HEAT)), 1.0F, 1.0F);
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, tooltip, flag);
		tooltip.accept(Component.translatable("item.jaymoskingdoms.spike_drill.cools").withStyle(net.minecraft.ChatFormatting.GRAY));
		tooltip.accept(Component.translatable("item.jaymoskingdoms.spike_drill.heat", Math.round(heat(stack)), Math.round(MAX_HEAT))
			.withStyle(net.minecraft.ChatFormatting.GRAY));
		if (overheated(stack)) {
			tooltip.accept(Component.translatable("item.jaymoskingdoms.spike_drill.overheated").withStyle(net.minecraft.ChatFormatting.RED));
		}
	}
}
