package com.jaymoskitchen.stomach;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The food journal: opened, it lists the foods you've eaten and the ones you haven't tried yet (from {@link Variety},
 * synced to you), nothing else. The screen is the client's ({@link #openScreen}, set by the client at init).
 */
public class FoodJournalItem extends Item {
	/** Opens the journal's screen (the client sets it; nothing on a dedicated server). */
	public static Runnable openScreen = () -> {
	};

	public FoodJournalItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			openScreen.run();
		}
		return InteractionResult.SUCCESS;
	}
}
