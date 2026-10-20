package com.jaymospowers.armory;

import com.jaymospowers.power.darktide.DarktideStatus;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * (0.13.32) What JMB's Better Weapons (MIT, by jmb19905; github.com/jmb05/BetterWeapons) gives its weapons, on the
 * ones we already forge (Andy: "can you make these compatible with our tinkers?"; the pack already had seven of its
 * eight weapons, so only what they do came across, in our own code):
 * <ul>
 * <li><b>Stun:</b> a cudgel or a flanged mace now and then stuns what it hits ({@link DarktideStatus#stun}: a mob stops
 * thinking, a player loses their movement keys), with a clang and stars.</li>
 * <li><b>Dismount:</b> a glaive, or WeaponMod's halberd, now and then hooks a rider off its mount and leaves them
 * staggering.</li>
 * <li><b>Staff block:</b> a quarterstaff held up (right mouse) blocks blows from the front like a shield, over a
 * narrower arc, wearing the staff. A wind sorcerer's right mouse stays the Staff Vault.</li>
 * </ul>
 * All of it rides on the item itself, so a weapon forged at the Tool Station does the same.
 */
public final class ArmoryHits {
	/** Chance a hit stuns: cudgels, flanged maces; and for how long (ticks). */
	static final float CUDGEL_STUN_CHANCE = 0.2F, MACE_STUN_CHANCE = 0.25F;
	static final int STUN_TICKS = 30;
	/** Chance a hit on a rider knocks them off; then this long (ticks) slowed at this level. */
	static final float DISMOUNT_CHANCE = 0.3F;
	static final int DISMOUNT_SLOW_TICKS = 20, DISMOUNT_SLOW_LEVEL = 1;
	/** Staff block: seconds to raise it (the round shield 0.15), and how wide it covers (degrees; the round shield 90). */
	static final float STAFF_RAISE = 0.2F, STAFF_ARC = 70.0F;

	private ArmoryHits() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
				|| !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			ItemStack held = attacker.getMainHandItem();
			float stun = stunChance(held);
			if (stun > 0.0F && attacker.getRandom().nextFloat() < stun && entity.isAlive()) {
				DarktideStatus.stun(entity, STUN_TICKS);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35F, 1.7F);
				level.sendParticles(ParticleTypes.CRIT, entity.getX(), entity.getEyeY() + 0.3, entity.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
			}
			if (entity.isPassenger() && dismounts(held) && attacker.getRandom().nextFloat() < DISMOUNT_CHANCE) {
				entity.stopRiding();
				entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DISMOUNT_SLOW_TICKS, DISMOUNT_SLOW_LEVEL), attacker);
				level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
			}
		});
	}

	/** The chance {@code held} stuns with a hit (0: never). */
	static float stunChance(ItemStack held) {
		Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
		if (!id.getNamespace().equals("jaymospowers")) {
			return 0.0F;
		}
		return id.getPath().endsWith("_cudgel") ? CUDGEL_STUN_CHANCE : id.getPath().endsWith("_flanged_mace") ? MACE_STUN_CHANCE : 0.0F;
	}

	/** Whether {@code held} can hook a rider off: our glaives, WeaponMod's halberds ({@code weaponmod:halberd.iron}). */
	static boolean dismounts(ItemStack held) {
		Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
		return id.getNamespace().equals("jaymospowers") && id.getPath().endsWith("_glaive")
			|| id.getNamespace().equals("weaponmod") && id.getPath().startsWith("halberd");
	}
}
