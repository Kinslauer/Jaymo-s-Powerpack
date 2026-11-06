package com.jaymospowers.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.21, Kingdoms Rivals patch 8) Shields in a fight, whoever carries them: whether one is raised ({@link #raised}, for
 * {@link ShieldVault}), and knocking one off. Yank on a mob with a shield tears the shield away instead of pulling him
 * ({@code WebPower.yank}), and a stun shakes loose what other mods' shields hang on ({@link #stunned}): Jaymo's Kingdoms'
 * armoured ogres ({@code Ogre.tearShield}) through {@link #TEAR}.
 */
public final class Shields {
	/** A torn-away shield flies off this fast toward whoever tore it (blocks/tick). */
	static final double TEAR_FLING = 0.5;

	/**
	 * Other mods' shields: given the one losing it and who did it (null: a stun), true if a shield came off. Kingdoms adds
	 * its armoured ogres'.
	 */
	public static final List<BiPredicate<LivingEntity, @Nullable LivingEntity>> TEAR = new ArrayList<>();
	/** Other mods' raised shields (a mob holding one up that isn't using an item for it). */
	public static final List<Predicate<LivingEntity>> RAISED = new ArrayList<>();

	private Shields() {
	}

	/** Whether {@code e} has a shield raised just now: a held one in use, or another mod's ({@link #RAISED}). */
	public static boolean raised(LivingEntity e) {
		if (e.isBlocking()) {
			return true;
		}
		for (Predicate<LivingEntity> p : RAISED) {
			if (p.test(e)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * {@code by} tears {@code target}'s shield away (Yank): a mob's held shield flies off toward them; or another mod's comes
	 * off. True if a shield came away (the Yank does nothing more). Never a player's.
	 */
	public static boolean tear(LivingEntity target, LivingEntity by) {
		if (!(target.level() instanceof ServerLevel level)) {
			return false;
		}
		if (target instanceof Mob mob) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack held = mob.getItemInHand(hand);
				if (!held.isEmpty() && held.has(DataComponents.BLOCKS_ATTACKS)) {
					mob.stopUsingItem();
					mob.setItemInHand(hand, ItemStack.EMPTY);
					Vec3 at = mob.getEyePosition().subtract(0.0, 0.4, 0.0);
					ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, held);
					Vec3 toward = by.position().subtract(at).multiply(1.0, 0.0, 1.0).normalize().scale(TEAR_FLING);
					item.setDeltaMovement(toward.x, 0.3, toward.z);
					item.setPickUpDelay(10);
					level.addFreshEntity(item);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.HOSTILE, 0.8F, 1.3F);
					return true;
				}
			}
		}
		for (BiPredicate<LivingEntity, @Nullable LivingEntity> t : TEAR) {
			if (t.test(target, by)) {
				return true;
			}
		}
		return false;
	}

	/** {@code target} was stunned: what other mods' shields hang on shakes loose ({@link #TEAR}, with no one to blame). */
	public static void stunned(LivingEntity target) {
		if (target.level().isClientSide()) {
			return;
		}
		for (BiPredicate<LivingEntity, @Nullable LivingEntity> t : TEAR) {
			if (t.test(target, null)) {
				return;
			}
		}
	}
}
