package com.jaymoskitchen.delight;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;

/**
 * Farmer's Delight's Backstabbing (MIT, by vectorwing; see LICENSE_farmersdelight): a knife enchantment (three levels)
 * that makes a blow struck from behind the victim hit harder. The enchantment itself is data
 * ({@code enchantment/backstabbing.json}); the blow is weighed here, from {@code BackstabMixin}.
 */
public final class Backstabbing {
	public static final ResourceKey<Enchantment> KEY = ResourceKey.create(Registries.ENCHANTMENT, JaymosKitchen.id("backstabbing"));
	/** What a blow from behind is multiplied by at level I, and what each level past it adds. */
	static final float FIRST_LEVEL = 1.4F, PER_LEVEL = 0.2F;
	/** How far behind counts: the dot of the victim's look and the way to the attacker is under this (about a 120 degree arc). */
	static final double BEHIND = -0.5;

	private Backstabbing() {
	}

	/** The damage of a blow, raised if it's a Backstabbing weapon's own strike landing from behind. */
	public static float damage(LivingEntity victim, ServerLevel level, DamageSource source, float amount) {
		if (amount <= 0 || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker) {
			return amount;
		}
		int enchanted = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(KEY)
			.map(e -> EnchantmentHelper.getItemEnchantmentLevel(e, attacker.getWeaponItem())).orElse(0);
		if (enchanted <= 0 || !behind(victim, attacker)) {
			return amount;
		}
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return amount * (FIRST_LEVEL + PER_LEVEL * (enchanted - 1));
	}

	private static boolean behind(LivingEntity victim, LivingEntity attacker) {
		Vec3 to = attacker.position().subtract(victim.position());
		to = new Vec3(to.x, 0.0, to.z).normalize();
		return to.dot(victim.getViewVector(1.0F)) < BEHIND;
	}
}
