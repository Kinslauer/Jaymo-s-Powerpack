package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sneak attack (fifth edition's Rogue feature, Andy's pick C5). A Thief's light blade or bow blow on a foe who is busy with
 * a friend of theirs (it is after someone else on their side), or caught unaware ({@link Senses#unaware}), does a
 * burst of extra damage: {@code blow x} {@link #MULT} {@code +} {@link #FLAT}. Once a second ({@link #EVERY} ticks).
 */
public final class ThiefSneak {
	/** The burst: the blow times this, plus this much more. */
	static final float MULT = 1.5F, FLAT = 2.0F;
	/** Once in this many ticks. */
	static final long EVERY = 20L;

	private static final Map<UUID, Long> LAST = new HashMap<>();

	private ThiefSneak() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST.clear());
	}

	/** {@code target} taking {@code amount} from {@code source}: a Thief's sneak attack, if it is one. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer thief) || target == thief || !Classes.is(thief, PlayerClass.THIEF)
			|| !(target.level() instanceof ServerLevel level) || Senses.cooling(target, source)) {
			return amount;
		}
		boolean blade = source.getDirectEntity() == thief && HiddenBlade.weapon(thief).is(ClassPerks.LIGHT_BLADES);
		boolean bow = source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() instanceof BowItem;
		if (!blade && !bow || !busy(target, thief) && !(target instanceof Mob && Senses.unaware(target, thief))) {
			return amount;
		}
		long now = level.getGameTime();
		Long last = LAST.get(thief.getUUID());
		if (last != null && now - last < EVERY) {
			return amount;
		}
		LAST.put(thief.getUUID(), now);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.3F);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.25, 0.3, 0.25, 0.3);
		return amount * MULT + FLAT;
	}

	/** {@code target} is busy with a friend of {@code thief}'s: it is after another player, their pet, or one of the town's. */
	static boolean busy(LivingEntity target, ServerPlayer thief) {
		if (!(target instanceof Mob mob) || !(mob.getTarget() instanceof LivingEntity prey) || prey == thief) {
			return false;
		}
		return prey instanceof Player || prey instanceof Villager || prey instanceof Folk
			|| prey instanceof TamableAnimal pet && pet.getOwnerReference() != null && thief.getUUID().equals(pet.getOwnerReference().getUUID())
			|| prey instanceof net.minecraft.world.entity.animal.golem.IronGolem;
	}
}
