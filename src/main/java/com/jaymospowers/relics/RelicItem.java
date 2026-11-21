package com.jaymospowers.relics;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A relic ({@link Relics}): it works from a relic belt's pouch. Click a relic onto another of the same kind and level to
 * merge them into one a level up.
 */
public class RelicItem extends Item {
	private final Relics.Kind kind;

	public RelicItem(Properties properties, Relics.Kind kind) {
		super(properties);
		this.kind = kind;
	}

	public Relics.Kind kind() {
		return this.kind;
	}

	@Override
	public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carried) {
		if (action != ClickAction.PRIMARY || !other.is(this) || Relics.level(other) != Relics.level(self) || Relics.level(self) >= Relics.MOST_LEVEL
			|| !slot.allowModification(player)) {
			return false;
		}
		self.set(Relics.LEVEL, Relics.level(self) + 1);
		carried.set(ItemStack.EMPTY);
		player.level().playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	@Override
	public Component getName(ItemStack stack) {
		return Component.translatable("item.jaymospowers.relic.leveled", super.getName(stack), Component.translatable("enchantment.level." + Relics.level(stack)));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		lines(this.kind, Relics.level(stack), builder);
		builder.accept(Component.translatable("tooltip.jaymospowers.relic.how").withStyle(ChatFormatting.DARK_GRAY));
	}

	/** What a relic of {@code kind} at {@code level} does, and when. */
	static void lines(Relics.Kind kind, int level, Consumer<Component> builder) {
		builder.accept(Component.translatable("tooltip.jaymospowers.relic." + kind.key(), Relics.amount(kind, level)).withStyle(ChatFormatting.BLUE));
		if (kind.when != Relics.When.ALWAYS) {
			builder.accept(Component.translatable("tooltip.jaymospowers.relic.when." + kind.when.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
