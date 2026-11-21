package com.jaymoskitchen.vintner;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.KingdomsMoney;
import com.jaymoskitchen.brewing.Brew;
import com.jaymoskitchen.brewing.Brewing;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * Selling a bottle: sneak and use a bottle of fermented drink on a villager, a wandering trader and (with Kingdoms) the
 * lords and merchants of a realm or a Hornkin ({@link #BUYERS}). They pay by the brew and the vintage ({@link Vintage#worth}),
 * in the coin of the land (see {@link KingdomsMoney}); the Nether's black wine fetches a lord's price anywhere.
 */
public final class WineTrade {
	/** Who buys wine (entity type tag; Kingdoms' people are listed there as optional ids). */
	public static final TagKey<EntityType<?>> BUYERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("wine_buyers"));

	private WineTrade() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown() || !entity.is(BUYERS)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getMainHandItem();
			Brew brew = Brewing.brewOf(held);
			if (brew == null) {
				return InteractionResult.PASS;
			}
			if (level.isClientSide() || !(player instanceof ServerPlayer seller)) {
				return InteractionResult.SUCCESS;
			}
			long price = Vintage.worth(brew, held);
			held.shrink(1);
			seller.getInventory().add(new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
			KingdomsMoney.pay(seller, price);
			level.playSound(null, entity.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
			seller.sendOverlayMessage(Component.translatable("message.jaymoskitchen.wine.sold", KingdomsMoney.text(seller, price)));
			return InteractionResult.SUCCESS;
		});
	}
}
