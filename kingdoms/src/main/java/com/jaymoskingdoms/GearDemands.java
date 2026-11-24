package com.jaymoskingdoms;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Gear that asks for skill (the JustLeveling Fork's item requirements, Apache 2.0: the idea only). A two-handed weapon (a greatsword,
 * claymore, war axe: {@code #jaymoskingdoms:two_handed}) swings badly in hands without {@link #MIGHT_NEEDED} Might, and a longbow is
 * not drawn full by an archer without {@link #AGILITY_NEEDED} Agility (an arrow from it lands as a short draw does). The blow of
 * someone who falls short lands at {@link #LEAST} of its strength at nothing, rising evenly to full at the need. Stats are the
 * character's own ({@link Stats}); a mob's weapon asks nothing. The first time it bites, the action bar says why.
 */
public final class GearDemands {
	/** The Might a two-handed weapon asks, and the Agility a longbow asks. */
	static final int MIGHT_NEEDED = 20, AGILITY_NEEDED = 20;
	/** The share of a blow that lands at no Might (or Agility) at all. */
	static final float LEAST = 0.5F;
	/** The least time between the action bar's word on it (ticks). */
	static final long TELL_GAP = 600L;

	private static final java.util.Map<java.util.UUID, Long> TOLD = new java.util.HashMap<>();

	private GearDemands() {
	}

	/** A blow dealt by a character with gear that asks for more than they have lands lighter. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer p)) {
			return amount;
		}
		ItemStack weapon;
		Stats.Stat stat;
		int need;
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			weapon = arrow.getWeaponItem();
			if (weapon == null || !longbow(weapon)) {
				return amount;
			}
			stat = Stats.Stat.AGILITY;
			need = AGILITY_NEEDED;
		} else if (source.getDirectEntity() == p && p.getMainHandItem().is(ClassPerks.TWO_HANDED)) {
			weapon = p.getMainHandItem();
			stat = Stats.Stat.MIGHT;
			need = MIGHT_NEEDED;
		} else {
			return amount;
		}
		int have = Stats.value(p, stat);
		if (have >= need) {
			return amount;
		}
		long now = p.level().getGameTime();
		Long last = TOLD.get(p.getUUID());
		if (last == null || now - last >= TELL_GAP || now < last) {
			TOLD.put(p.getUUID(), now);
			p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.gear.demands." + stat.name().toLowerCase(java.util.Locale.ROOT), weapon.getHoverName(), need)
				.withStyle(ChatFormatting.GRAY));
		}
		return amount * (LEAST + (1.0F - LEAST) * have / need);
	}

	/** Whether {@code stack} is a longbow (any mod's: by its id). */
	static boolean longbow(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("longbow") || path.contains("long_bow");
	}
}
