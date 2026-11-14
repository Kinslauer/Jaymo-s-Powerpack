package com.jaymospowers.alchemy.flux;

import com.jaymospowers.alchemy.Corruption;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Purifying Draught: drunk like a potion, it takes {@link #CURE} lingering corruption out of the drinker (and all the
 * fleeting kind). It does nothing for the deep kind. The glass bottle is handed back. Drunk while the gods of the
 * elements have the drinker's power, it purifies it (ElementalCorruption#penance); drunk by a corrupted Cryokinesis user
 * at a lit campfire, it thaws their corruption (CryoCorruption#thaw).
 */
public class PurifyingDraughtItem extends Item {
	/** Lingering corruption removed per bottle. */
	public static final int CURE = 15;

	public PurifyingDraughtItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (level instanceof ServerLevel && entity instanceof ServerPlayer player) {
			Corruption.State state = Corruption.of(player);
			Corruption.add(player, Corruption.Kind.LINGERING, -CURE);
			Corruption.add(player, Corruption.Kind.FLEETING, -state.fleeting());
			player.sendSystemMessage(Component.translatable("message.jaymospowers.draught.drunk"), true);
			// drunk while the gods of the elements have the power: penance (Elemental Corruption)
			com.jaymospowers.elemental.ElementalCorruption.penance(player);
			// a corrupted Cryokinesis user warmed by a campfire is thawed of it
			com.jaymospowers.power.cryo.CryoCorruption.thaw(player);
		}
		return left;
	}
}
