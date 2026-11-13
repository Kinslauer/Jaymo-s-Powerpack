package com.jaymospowers.core.wounds;

import com.jaymospowers.core.Dismember;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The dead that get back up (fifth edition's Undead Fortitude, Andy's pick C9). A zombie put down by an ordinary blow
 * gets up again by itself a few seconds later, once, as hurt as anyone getting up. A sure blow keeps it down for the
 * usual while: fire, a blade with Smite on it, a critical hit, or a beheading (which ends it anyway). The way to be sure
 * of one is to finish it where it lies; bare-handed, that is a stamp on its head or its head beaten on the ground
 * ({@code Finishers.Kind#STOMP}, {@code Finishers.Kind#HEAD_SMASH}), Andy's ask, or the old neck snap, picked at random.
 */
public final class UndeadFortitude {
	/** A zombie downed by an ordinary blow is back up this long after (ticks). */
	static final int RISE_AFTER = 20 * 4;
	/** The share of the time an ordinary blow lets it get up at all. */
	static final float CHANCE = 0.75F;
	/** On a zombie that has got up this way once already: it stays down after. */
	static final String SPENT_TAG = "jaymospowers_fortitude_spent";

	/** Server: whether the last blow on each zombie was a sure one, read when it goes down. */
	private static final Map<LivingEntity, Boolean> SURE = new WeakHashMap<>();
	/** Server: zombies that went down by an ordinary blow, and when they'll be up again (game time). */
	private static final Map<LivingEntity, Long> RISING = new WeakHashMap<>();

	private UndeadFortitude() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (zombie(entity) && !Downed.isDowned(entity)) {
				SURE.put(entity, sure(entity, source));
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(UndeadFortitude::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SURE.clear();
			RISING.clear();
		});
	}

	/** Whether {@code entity} is one of the zombies (husks, drowned, zombie villagers and zombified piglins with them). */
	public static boolean zombie(@Nullable LivingEntity entity) {
		return entity instanceof Zombie;
	}

	/** {@code entity} has just gone down from {@code source} ({@link Downed}): an ordinary blow on a zombie, and it'll rise. */
	static void downed(LivingEntity entity, @Nullable DamageSource source) {
		if (!zombie(entity) || source == null || entity.entityTags().contains(SPENT_TAG) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Boolean sure = SURE.remove(entity);
		if (sure == null ? sure(entity, source) : sure) {
			return;
		}
		if (entity.getRandom().nextFloat() >= CHANCE) {
			return;
		}
		RISING.put(entity, level.getGameTime() + RISE_AFTER);
	}

	/** Fire, holy steel, a critical hit, a beheading: a blow that keeps a zombie down. */
	static boolean sure(LivingEntity target, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(Dismember.DECAPITATION) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
			var smite = attacker.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.SMITE);
			if (smite.isPresent() && EnchantmentHelper.getItemEnchantmentLevel(smite.get(), attacker.getMainHandItem()) > 0) {
				return true;
			}
			if (attacker instanceof Player player && critical(player)) {
				return true;
			}
		}
		return false;
	}

	/** Vanilla's own test for a critical hit: falling, off the ground, not climbing, swimming, blind or riding. */
	private static boolean critical(Player player) {
		return player.fallDistance > 0.0 && !player.onGround() && !player.onClimbable() && !player.isInWater()
			&& !player.hasEffect(MobEffects.BLINDNESS) && !player.isPassenger() && player.getAttackStrengthScale(0.5F) > 0.9F;
	}

	private static void tick(MinecraftServer server) {
		if (RISING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<LivingEntity, Long>> it = RISING.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Long> e = it.next();
			LivingEntity zombie = e.getKey();
			if (!zombie.isAlive() || zombie.isRemoved() || !Downed.isDowned(zombie) || !(zombie.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			if (level.getGameTime() < e.getValue() || com.jaymospowers.core.finisher.Finishers.targeted(zombie)
				|| com.jaymospowers.core.Carry.carrierOf(zombie) != null) {
				continue;
			}
			it.remove();
			zombie.addTag(SPENT_TAG);
			Downed.getUp(level, zombie);
			if (!Downed.isDowned(zombie)) {
				level.playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.2F, 0.6F);
			}
		}
	}
}
