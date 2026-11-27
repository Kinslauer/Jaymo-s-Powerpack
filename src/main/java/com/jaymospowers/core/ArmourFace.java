package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.MetalHits;
import com.jaymospowers.core.wounds.BodyPart;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Blows to the head, and the helmet over it. Where a blow lands on the head is read from the lock-on's part when there is
 * one, else from where the striker's aim crosses the body (a mob's blows land on the head now and then,
 * {@link #MOB_HEAD_CHANCE}, since it always looks at the eyes).
 * <ul>
 * <li><b>Open faces</b> ({@link #OPEN_FACE_HELMETS}): a blow on the face (from the front arc, at eye height) skips a helmet
 * with no visor or face cover. The helmet's armour is left out of the blow ({@link #bypass} adds back what it would
 * have soaked) and it guards nothing against severing ({@link Dismember#armourGuard}).</li>
 * <li><b>A helmet knocked off</b> ({@link #knock}): a heavy head blow ({@link #OFF_DAMAGE}) may knock the helmet off onto
 * the ground; a huge one ({@link #BREAK_DAMAGE}) breaks it instead (it stays on, worn out, as armour does at 0).
 * A player's helmet comes off rarely ({@link #PLAYER_SHARE}) and never in creative; bodies of metal (golems, androids)
 * and anything not a mob or a player keep theirs; a helmet that won't come off (binding) stays.</li>
 * </ul>
 */
public final class ArmourFace {
	// ===== the knobs ================================================================================================
	/** Helmets with no visor or face cover (their wearer's face takes a blow on the face whole). */
	public static final TagKey<Item> OPEN_FACE_HELMETS = TagKey.create(Registries.ITEM, JaymosPowers.id("open_face_helmets"));
	/** A mob's blow lands on the head this often when nothing says where. */
	public static final float MOB_HEAD_CHANCE = 0.2F;
	/** The face: the striker within this cosine of straight ahead of the wearer's head (60 degrees either way). */
	public static final double FRONT_COS = 0.5;
	/** Eye height: a player's aim crossing the body this far below, or above, the eyes is on the face. */
	public static final double BELOW_EYES = 0.3, ABOVE_EYES = 0.15;
	/** How far a player's aim is followed to the body. */
	static final double AIM_REACH = 8.0;
	/** What the helmet would have soaked, added back to a blow it is skipped by: this share of the damage per armour point. */
	public static final float BYPASS_PER_POINT = 0.03F;
	/** A head blow of this damage may knock the helmet off, at this chance (less in proportion up to double the damage). */
	public static final float OFF_DAMAGE = 7.0F, OFF_CHANCE = 0.5F;
	/** A head blow of this damage breaks the helmet. */
	public static final float BREAK_DAMAGE = 14.0F;
	/** A player's chance to lose a helmet is this share of a mob's. */
	public static final float PLAYER_SHARE = 0.3F;
	// ================================================================================================================

	/** The last game time a blow skipped the helmet of each wearer. */
	private static final Map<LivingEntity, Long> SKIPPED = new WeakHashMap<>();

	/** Where a blow lands: on the head, and on the face. */
	public record Aim(boolean head, boolean face) {
		static final Aim BODY = new Aim(false, false);
	}

	private ArmourFace() {
	}

	/** Where the blow of {@code attacker} lands on {@code victim}, {@code part} being where the lock-on says (null: nothing says). */
	public static Aim aim(LivingEntity victim, LivingEntity attacker, @Nullable BodyPart part) {
		boolean head;
		double hitY = victim.getEyeY();
		if (part != null) {
			head = part == BodyPart.HEAD;
		} else if (attacker instanceof Player) {
			Vec3 eye = attacker.getEyePosition();
			Optional<Vec3> hit = victim.getBoundingBox().clip(eye, eye.add(attacker.getViewVector(1.0F).scale(AIM_REACH)));
			if (hit.isEmpty()) {
				return Aim.BODY;
			}
			hitY = hit.get().y;
			head = hitY >= victim.getEyeY() - BELOW_EYES;
		} else {
			head = attacker.getRandom().nextFloat() < MOB_HEAD_CHANCE;
		}
		if (!head) {
			return Aim.BODY;
		}
		Vec3 facing = Vec3.directionFromRotation(0.0F, victim.yHeadRot);
		Vec3 toward = new Vec3(attacker.getX() - victim.getX(), 0.0, attacker.getZ() - victim.getZ());
		boolean front = toward.lengthSqr() > 1.0E-6 && facing.dot(toward.normalize()) >= FRONT_COS;
		return new Aim(true, front && hitY <= victim.getEyeY() + ABOVE_EYES);
	}

	/** Whether this blow skips the helmet {@code victim} wears: on the face, and the helmet has no visor. */
	public static boolean bare(LivingEntity victim, Aim aim) {
		if (!aim.face()) {
			return false;
		}
		ItemStack helmet = victim.getItemBySlot(EquipmentSlot.HEAD);
		if (helmet.isEmpty() || !helmet.is(OPEN_FACE_HELMETS)) {
			return false;
		}
		SKIPPED.put(victim, victim.level().getGameTime());
		return true;
	}

	/** Whether a blow skipped {@code victim}'s helmet this tick or the last (what {@link Dismember#armourGuard} asks). */
	static boolean skipped(LivingEntity victim) {
		Long at = SKIPPED.get(victim);
		return at != null && victim.level().getGameTime() - at <= 1L;
	}

	/** The damage a blow gains for skipping {@code victim}'s helmet: what its armour points would have soaked. */
	public static float bypass(LivingEntity victim) {
		return 1.0F + BYPASS_PER_POINT * (float) Dismember.armourOn(victim, Dismember.HEAD);
	}

	/** A head blow of {@code damage} may knock {@code victim}'s helmet off, or break it. */
	public static void knock(ServerLevel level, LivingEntity victim, float damage) {
		if (damage < OFF_DAMAGE || !(victim instanceof Mob || victim instanceof Player) || MetalHits.metalBody(victim)) {
			return;
		}
		ItemStack helmet = victim.getItemBySlot(EquipmentSlot.HEAD);
		if (helmet.isEmpty() || EnchantmentHelper.has(helmet, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
			return;
		}
		float chance = OFF_CHANCE * Math.min(2.0F, damage / OFF_DAMAGE);
		if (victim instanceof Player player) {
			if (player.hasInfiniteMaterials() || player.isSpectator()) {
				return;
			}
			chance *= PLAYER_SHARE;
		}
		if (damage >= BREAK_DAMAGE && ArmourWear.applies(helmet) && !ArmourWear.broken(helmet)) {
			helmet.setDamageValue(helmet.getMaxDamage());
			level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.ITEM_BREAK.value(), victim.getSoundSource(), 0.8F, 0.8F);
			return;
		}
		if (victim.getRandom().nextFloat() >= chance) {
			return;
		}
		victim.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		var dropped = victim.spawnAtLocation(level, helmet, (float) victim.getBbHeight());
		if (dropped != null) {
			dropped.setDeltaMovement(victim.getRandom().nextGaussian() * 0.1, 0.25, victim.getRandom().nextGaussian() * 0.1);
		}
		level.playSound(null, victim.getX(), victim.getEyeY(), victim.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), victim.getSoundSource(), 1.0F,
			Mth.nextFloat(victim.getRandom(), 0.6F, 0.8F));
	}
}
