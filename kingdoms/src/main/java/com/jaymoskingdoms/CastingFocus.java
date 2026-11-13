package com.jaymoskingdoms;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * A caster's focus: the holy symbol of a Cleric (and a Paladin), the sprig of a Druid, the seal of a Warlock. In a hand of the
 * class that owns it, a tap casts the spell it is turned to (as a Mage's staff does, {@link Spellcraft#castFocus}) and sneaking
 * turns it to the next. A holy symbol is also raised: held down past {@link #RAISE_TICKS} it turns the undead
 * ({@link Clerics#raised}). In anyone else's hand it does nothing but serve as a focus for spoken magic ({@link SpellParts}).
 */
public final class CastingFocus extends Item {
	/** How long a symbol must be held up before the undead feel it (ticks); a shorter press is a tap. */
	public static final int RAISE_TICKS = 10;
	private static final int FOREVER = 72000;
	private final boolean raises;

	CastingFocus(Item.Properties properties, boolean raises) {
		super(properties);
		this.raises = raises;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player.isShiftKeyDown() || !this.raises) {
			if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
				if (player.isShiftKeyDown()) {
					Spellcraft.turnFocus(sp);
				} else {
					Spellcraft.castFocus(sp, server, player.getItemInHand(hand));
				}
			}
			return InteractionResult.SUCCESS;
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.TOOT_HORN;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return FOREVER;
	}

	@Override
	public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
		if (user instanceof ServerPlayer sp && level instanceof ServerLevel server) {
			Clerics.raised(sp, server, FOREVER - remaining);
		}
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
		if (user instanceof ServerPlayer sp && level instanceof ServerLevel server) {
			int held = FOREVER - remaining;
			if (held < RAISE_TICKS) {
				Spellcraft.castFocus(sp, server, stack);
			}
			Clerics.lowered(sp);
		}
		return true;
	}
}
