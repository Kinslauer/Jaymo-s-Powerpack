package com.jaymospowers.baubles.artifacts;

import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.baubles.Baubles;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * One of the Artifacts baubles: a {@link BaubleItem} (its attribute bonuses work as every bauble's do) plus a line saying
 * what it does, since most of them work by {@link ArtifactEffects} rather than by attributes. One that fits more than one
 * slot (by its tags) names them all.
 */
public class ArtifactItem extends BaubleItem {
	private final String id;
	private final Baubles.Kind[] also;

	public ArtifactItem(Item.Properties properties, String id, Baubles.Kind kind, Baubles.Kind[] also, List<Bonus> bonuses) {
		super(properties, kind, bonuses);
		this.id = id;
		this.also = also;
	}

	@Override
	protected Component slotName() {
		if (this.also.length == 0) {
			return super.slotName();
		}
		var name = Component.translatable("bauble.jaymospowers.slot." + this.kind().id).copy();
		for (Baubles.Kind kind : this.also) {
			name.append(Component.literal(" / ")).append(Component.translatable("bauble.jaymospowers.slot." + kind.id));
		}
		return name;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.artifact." + this.id).withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
