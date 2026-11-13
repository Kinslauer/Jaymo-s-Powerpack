package com.jaymoskingdoms;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;

/**
 * The bottle of endless smoke: unstopper it and a bank of fog pours out round you, thick enough to hide in (the fog of the
 * spell of the same name, {@link Fields.Kind#FOG}). It is never empty; it needs a minute to draw breath.
 */
public final class SmokeBottle {
	/** The fog's radius (blocks) and life (ticks), and the wait before it can be pulled again (ticks). */
	static final double RADIUS = 6.0;
	static final int LASTS = 800, COOLDOWN = 1200;
	public static Item ITEM;

	private SmokeBottle() {
	}

	static void register() {
		ITEM = Gadgets.item("smoke_bottle", Rarity.UNCOMMON, Bottle::new);
	}

	static final class Bottle extends Item {
		Bottle(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (player instanceof net.minecraft.server.level.ServerPlayer sp && !ItemBonds.works(sp, stack)) {
				return InteractionResult.PASS;
			}
			Vec3 ground = player.position();
			Fields.add(Fields.Kind.FOG, server, ground, RADIUS, 3.0, LASTS, player.getUUID(), null);
			server.playSound(null, player.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0F, 0.6F);
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.smoke_bottle").withStyle(ChatFormatting.GRAY));
		}
	}
}
