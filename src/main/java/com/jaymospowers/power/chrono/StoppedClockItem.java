package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Powers;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Stopped Clock: found in your hands after a night's sleep, once you've spent long enough outside time. Used by a
 * Chrono player who can ascend, it is spent and they ascend (Timeless). Anyone else hears it tick, once.
 * Not in any creative tab; {@code /give @s jaymospowers:chrono_stopped_clock} for testing.
 */
public final class StoppedClockItem extends Item {
	public static Item STOPPED_CLOCK;

	public StoppedClockItem(Item.Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("chrono_stopped_clock"));
		STOPPED_CLOCK = Registry.register(BuiltInRegistries.ITEM, key,
			new StoppedClockItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC)));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			if (Powers.has(serverPlayer, ChronoPower.INSTANCE) && Fate.canAscend(serverPlayer)) {
				ItemStack stack = serverPlayer.getItemInHand(hand);
				stack.shrink(1);
				serverLevel.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.2);
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 0.5F);
				Fate.ascend(serverPlayer);
			} else {
				// just a tick: the hands don't move
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.COMPARATOR_CLICK, SoundSource.PLAYERS, 0.8F, 1.2F);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.jaymospowers.chrono_stopped_clock.lore1").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		builder.accept(Component.translatable("item.jaymospowers.chrono_stopped_clock.lore2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
