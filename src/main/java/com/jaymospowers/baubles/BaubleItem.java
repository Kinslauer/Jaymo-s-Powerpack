package com.jaymospowers.baubles;

import java.text.DecimalFormat;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * One of our baubles: worn in a {@link Baubles} slot of its kind it gives its bonuses (nowhere else). Right-click it to
 * put it straight into an empty slot that takes it.
 */
public class BaubleItem extends Item {
	/** The most bonuses one bauble may give (each takes its own modifier id per slot). */
	static final int MAX_BONUSES = 3;
	private static final DecimalFormat NUMBER = new DecimalFormat("#.##");

	/** An attribute bonus while worn. */
	public record Bonus(Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
	}

	private final Baubles.Kind kind;
	private final List<Bonus> bonuses;

	public BaubleItem(Properties properties, Baubles.Kind kind, List<Bonus> bonuses) {
		super(properties);
		if (bonuses.size() > MAX_BONUSES) {
			throw new IllegalArgumentException("a bauble gives at most " + MAX_BONUSES + " bonuses");
		}
		this.kind = kind;
		this.bonuses = bonuses;
	}

	public Baubles.Kind kind() {
		return this.kind;
	}

	public List<Bonus> bonuses() {
		return this.bonuses;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer server) {
			if (!Baubles.equipFromHand(server, player.getItemInHand(hand))) {
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.baubles.full"));
				return InteractionResult.FAIL;
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("item.jaymospowers.bauble.slot", Component.translatable("bauble.jaymospowers.slot." + this.kind.id))
			.withStyle(ChatFormatting.GRAY));
		for (Bonus bonus : this.bonuses) {
			boolean percent = bonus.operation() != AttributeModifier.Operation.ADD_VALUE;
			double amount = percent ? bonus.amount() * 100.0 : bonus.amount();
			String key = (amount >= 0 ? "attribute.modifier.plus." : "attribute.modifier.take.") + (percent ? "1" : "0");
			builder.accept(Component.translatable(key, NUMBER.format(Math.abs(amount)), Component.translatable(bonus.attribute().value().getDescriptionId()))
				.withStyle(ChatFormatting.BLUE));
		}
	}
}
