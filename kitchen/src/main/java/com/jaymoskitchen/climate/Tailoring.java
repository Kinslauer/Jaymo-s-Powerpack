package com.jaymoskitchen.climate;

import com.jaymoskitchen.KingdomsMoney;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/**
 * The tailor route to a lining (Andy's TFC Hardrock picks: "sew ... at a loom or by a tailor villager"). The village tailor is
 * the shepherd, the villager at the loom. Hold a piece of armour or clothes in your main hand and the cloth (or fur) in the
 * other, and use them on a grown shepherd: they sew the lining in for a fee, in the coin of the land (emeralds without
 * Kingdoms), {@link #fee}. They take what a loom takes and also pelts, which a loom can't: wolf, bear and wild furs.
 */
public final class Tailoring {
	/** What a tailor asks (emeralds' worth) for a wool, rabbit hide or leather lining, and for each fur. */
	static final int PLAIN_FEE = 1, WOLF_FEE = 2, WILD_FEE = 3, BEAR_FEE = 4;

	private Tailoring() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !(entity instanceof Villager tailor) || tailor.isBaby()
				|| !tailor.getVillagerData().profession().is(VillagerProfession.SHEPHERD)) {
				return InteractionResult.PASS;
			}
			ItemStack piece = player.getMainHandItem();
			ItemStack cloth = player.getOffhandItem();
			Lining lining = Lining.of(cloth);
			if (lining == null || !Warmth.linable(piece) || piece.get(Warmth.LINING) == lining) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer server) {
				long price = KingdomsMoney.worth(fee(lining));
				if (!KingdomsMoney.charge(server, price)) {
					server.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tailor.cant_pay", KingdomsMoney.text(server, price)));
					return InteractionResult.SUCCESS;
				}
				piece.set(Warmth.LINING, lining);
				if (!player.getAbilities().instabuild) {
					cloth.shrink(1);
				}
				level.playSound(null, tailor.blockPosition(), SoundEvents.VILLAGER_WORK_SHEPHERD, SoundSource.NEUTRAL, 1.0F, 1.0F);
				server.sendOverlayMessage(Component.translatable("message.jaymoskitchen.tailor.sewn", KingdomsMoney.text(server, price)));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** The tailor's fee for sewing {@code lining}, in emeralds' worth. */
	static int fee(Lining lining) {
		return switch (lining) {
			case WOLF_FUR -> WOLF_FEE;
			case WILD_FUR -> WILD_FEE;
			case BEAR_FUR -> BEAR_FEE;
			default -> PLAIN_FEE;
		};
	}
}
