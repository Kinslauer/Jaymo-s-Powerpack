package com.jaymospowers.alchemy.gear;

import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Knowledge;
import com.jaymospowers.alchemy.Phials;
import com.jaymospowers.alchemy.flux.FluxEffects;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * The Spellbinding Cloth: hold it in one hand and an enchanted item in the other, and use it. The item's first enchantment
 * is stripped off and unravels into phials of the essences it was made of ({@link EssenceEnchanting#makeup}): {@link #RETURN} of what
 * granting that level would have cost, rounded down. Each use wears the cloth ({@code durability 8}) and leaves flux.
 * Needs the {@code spellbinding} research.
 */
public class SpellbindingClothItem extends Item {
	/** The share of the enchantment's essence cost that comes back. */
	public static final double RETURN = 0.5;
	/** Flux left in the chunk by one stripping. */
	public static final int FLUX = 2;

	public SpellbindingClothItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack cloth = player.getItemInHand(hand);
		ItemStack target = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return target.isEnchanted() ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!Knowledge.knows(sp, AlchemyGear.SPELLBINDING)) {
			sp.sendOverlayMessage(RodItem.text("not_understood"));
			return InteractionResult.FAIL;
		}
		if (!target.isEnchanted()) {
			sp.sendOverlayMessage(RodItem.text("nothing_to_strip"));
			return InteractionResult.FAIL;
		}
		Holder<Enchantment> first = target.getEnchantments().keySet().iterator().next();
		int enchantLevel = target.getEnchantments().getLevel(first);
		EnchantmentHelper.updateEnchantments(target, m -> m.set(first, 0));
		EssenceList back = EssenceEnchanting.cost(first, enchantLevel).scaled(RETURN);
		for (Map.Entry<Essence, Integer> en : back.amounts().entrySet()) {
			for (int left = en.getValue(); left > 0; left -= Phials.SIZE) {
				sp.getInventory().placeItemBackInInventory(Phials.of(en.getKey(), left), Prediction.SERVER_ONLY);
			}
		}
		FluxEffects.burst(server, sp.blockPosition(), FLUX);
		server.playSound(null, sp.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8F, 1.6F);
		server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, sp.getX(), sp.getEyeY(), sp.getZ(), 20, 0.4, 0.3, 0.4, 0.5);
		cloth.hurtAndBreak(1, sp, hand);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
		TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.spellbinding_cloth").withStyle(ChatFormatting.GRAY));
	}
}
