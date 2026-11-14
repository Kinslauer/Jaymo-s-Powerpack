package com.jaymospowers.combat;

import com.jaymospowers.compat.BetterCombatWeapons;
import com.jaymospowers.core.ArmourKind;
import com.jaymospowers.core.LockOnRegions;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Two things a weapon's blow answers to (Andy's picks from Knights &amp; Heraldry, ideas only: all rights reserved, so
 * nothing of it is copied; the numbers, names and code are ours). Both read the weapon's kind from {@link WeaponKind}.
 * <ul>
 * <li><b>Its distance</b> ({@link #measure}): every kind of weapon hits hardest in its own band of its reach
 * ({@link WeaponKind#near} to {@link WeaponKind#far}); a blow from the heart of the band does {@link #HEART_BONUS},
 * one from too close or too far loses force, down to {@link #OFF_DISTANCE} once it is {@link #FALLOFF} of the reach
 * outside. Only a swing counts ({@link #begin}: a player's attack or stab; a mob's when it fights with its weapon's
 * reach, {@link #SWING_TAG}): the scripted blows of Weapon Forms and the powers keep their own numbers. The distance
 * is from the striker's eyes to the nearest of the body struck.</li>
 * <li><b>The armour it meets</b> ({@link #meetArmour}): a cut, a thrust and a blunt blow ({@link WeaponKind.Blow}) each
 * do their own share against padding, mail and plate ({@link ArmourKind}), before the armour takes its own share off.
 * The piece asked is the one over where the blow lands when a lock-on region says where ({@link LockOnRegions}), else
 * the chest piece; a bare place or a broken piece changes nothing. Since armour wears by the damage it is asked to stop,
 * a blow that crushes plate wears it faster too, and one that mail turns wears it less.</li>
 * </ul>
 * Both multiply the blow's damage before armour and the wounds see it, so class perks, ripostes, Weapon Forms and the
 * lock-on's own head bonus stack with them as they stack with each other. Nothing is said: a ring in the striker's ear
 * at the heart of the distance, a dull thud off it, a clank and sparks when armour turns a blow, a crunch when it fails.
 */
public final class Blows {
	// ===== the knobs ================================================================================================
	/** A blow at the heart of its weapon's distance does this many times its damage. */
	public static final float HEART_BONUS = 1.1F;
	/** The heart: this share of the band, about its middle. */
	public static final float HEART_WIDTH = 0.3F;
	/** The least of its damage a blow keeps, however far off its distance. */
	public static final float OFF_DISTANCE = 0.7F;
	/** This far outside its band (a share of the weapon's reach) a blow is down to {@link #OFF_DISTANCE}. */
	public static final float FALLOFF = 0.3F;
	/** A blow kept to this share or less by its distance lands with a dull thud. */
	static final float DULL = 0.9F;
	/** Armour that lets this many times the damage through has failed (a crunch); this share or less, it turned the blow (a clank). */
	static final float BITES = 1.2F, TURNED = 0.8F;
	// ================================================================================================================

	/**
	 * On a mob while it strikes with its weapon at that weapon's reach (Jaymo's Kingdoms' fighters, which swing a weapon's
	 * own moves): its blows answer to their distance as a player's do. A mob that only pokes from arm's length never has it.
	 */
	public static final String SWING_TAG = "jaymospowers.weapon_swing";

	/** Whoever is in the middle of a swing on the server (a player's attack or stab), and the game time it began. */
	private static final Map<LivingEntity, Long> SWINGING = new WeakHashMap<>();
	/** The last swing measured, till the blow lands (or the next one is asked). */
	private static @Nullable Measured measured;

	private record Measured(DamageSource source, float factor) {
	}

	private Blows() {
	}

	/** {@code attacker}'s swing begins (server side only counts): what it strikes before {@link #end} is measured. */
	public static void begin(LivingEntity attacker) {
		if (!attacker.level().isClientSide()) {
			SWINGING.put(attacker, attacker.level().getGameTime());
		}
	}

	public static void end(LivingEntity attacker) {
		if (!attacker.level().isClientSide()) {
			SWINGING.remove(attacker);
		}
	}

	/** (a swing something cut short never ends: it is over with its tick all the same) */
	private static boolean swinging(LivingEntity attacker) {
		Long began = SWINGING.get(attacker);
		return began != null && began == attacker.level().getGameTime();
	}

	// ---- the distance ----------------------------------------------------------------------------------------------

	/** The damage of a swing on {@code victim}, as its distance leaves it (from {@code LivingEntity.hurtServer}). */
	public static float measure(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0.0F || !(source.getDirectEntity() instanceof LivingEntity attacker) || source.getEntity() != attacker || attacker == victim
			|| !(swinging(attacker) || attacker.entityTags().contains(SWING_TAG))) {
			return damage;
		}
		ItemStack weapon = source.getWeaponItem();
		WeaponKind kind = weapon == null ? null : WeaponKind.of(weapon);
		if (kind == null) {
			return damage;
		}
		double reach = reach(attacker, weapon);
		if (reach <= 0.0) {
			return damage;
		}
		double gap = Math.sqrt(victim.getBoundingBox().distanceToSqr(attacker.getEyePosition()));
		float factor = atDistance(kind, (float)(gap / reach));
		measured = new Measured(source, factor);
		return damage * factor;
	}

	/** How far {@code attacker} reaches with {@code weapon} (blocks). */
	static double reach(LivingEntity attacker, ItemStack weapon) {
		if (attacker instanceof Player player) {
			double reach = BetterCombatWeapons.reach(player, weapon);
			if (reach > 0.0) {
				return reach;
			}
			AttackRange range = weapon.get(DataComponents.ATTACK_RANGE);
			return range != null ? range.effectiveMaxRange(player) : player.entityInteractionRange();
		}
		double reach = BetterCombatWeapons.mobReach(weapon);
		return reach > 0.0 ? reach : BetterCombatWeapons.MOB_REACH;
	}

	/** The share of its damage a {@code kind} of weapon does from {@code share} of its reach away. */
	public static float atDistance(WeaponKind kind, float share) {
		float off = share < kind.near ? kind.near - share : share > kind.far ? share - kind.far : 0.0F;
		if (off > 0.0F) {
			return Mth.lerp(Math.min(1.0F, off / FALLOFF), 1.0F, OFF_DISTANCE);
		}
		float middle = (kind.near + kind.far) * 0.5F;
		float half = (kind.far - kind.near) * 0.5F * HEART_WIDTH;
		return Math.abs(share - middle) <= half ? HEART_BONUS : 1.0F;
	}

	// ---- the armour --------------------------------------------------------------------------------------------------

	/**
	 * The damage of a blow landing on {@code victim}, as the armour over where it lands leaves it (from
	 * {@code LivingEntity.getDamageAfterArmorAbsorb}, before the armour's own share comes off). The blow is landing for
	 * certain here, so this is also where both halves make themselves heard.
	 */
	public static float meetArmour(LivingEntity victim, DamageSource source, float damage) {
		Measured swing = measured;
		measured = null;
		if (damage <= 0.0F || !(victim.level() instanceof ServerLevel level) || !(source.getDirectEntity() instanceof LivingEntity attacker)
			|| source.getEntity() != attacker || attacker == victim) {
			return damage;
		}
		if (swing != null && swing.source() == source) {
			tellDistance(level, victim, attacker, swing.factor());
		}
		if (source.is(DamageTypeTags.BYPASSES_ARMOR)) {
			return damage;
		}
		ItemStack weapon = source.getWeaponItem();
		WeaponKind kind = weapon == null ? null : WeaponKind.of(weapon);
		if (kind == null) {
			return damage;
		}
		ArmourKind armour = ArmourKind.worn(victim, struck(victim, source));
		float factor = kind.against(armour);
		if (factor >= BITES || factor <= TURNED) {
			tellArmour(level, victim, attacker, armour, factor >= BITES);
		}
		return damage * factor;
	}

	/** The armour slot over where this blow lands: the lock-on's region when the striker aims through one, else the chest. */
	static EquipmentSlot struck(LivingEntity victim, DamageSource source) {
		BodyPlan plan = BodyPlan.of(victim);
		BodyPart part = plan == null ? null : LockOnRegions.aimedPart(victim, plan, source);
		if (part == null) {
			return EquipmentSlot.CHEST;
		}
		return switch (part) {
			case HEAD -> EquipmentSlot.HEAD;
			case LEFT_LEG, RIGHT_LEG, FRONT_LEGS, HIND_LEGS, LEGS -> EquipmentSlot.LEGS;
			case LEFT_FOOT, RIGHT_FOOT -> EquipmentSlot.FEET;
			default -> EquipmentSlot.CHEST;
		};
	}

	// ---- what is heard and seen ------------------------------------------------------------------------------------

	private static void tellDistance(ServerLevel level, LivingEntity victim, LivingEntity attacker, float factor) {
		Vec3 at = victim.getBoundingBox().getCenter();
		if (factor >= HEART_BONUS) {
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.25, 0.2, 0.3);
			if (attacker instanceof ServerPlayer striker) {
				// a clean ring, in the striker's ears only
				striker.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ARROW_HIT_PLAYER),
					SoundSource.PLAYERS, striker.getX(), striker.getEyeY(), striker.getZ(), 0.35F, 1.4F, striker.getRandom().nextLong()));
			}
		} else if (factor <= DULL) {
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_WEAK, attacker.getSoundSource(), 0.9F, 0.7F);
		}
	}

	private static void tellArmour(ServerLevel level, LivingEntity victim, LivingEntity attacker, ArmourKind armour, boolean bites) {
		Vec3 at = victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0);
		Vec3 line = at.subtract(attacker.getEyePosition());
		SoundEvent sound;
		float volume = 1.0F, pitch;
		int sparks;
		switch (armour) {
			case PLATE -> {
				sound = bites ? SoundEvents.ANVIL_LAND : SoundEvents.ARMOR_EQUIP_IRON.value();
				volume = bites ? 0.25F : 1.0F;
				pitch = bites ? 1.6F : 0.7F;
				sparks = bites ? 6 : 4;
			}
			case MAIL -> {
				sound = bites ? SoundEvents.CHAIN_BREAK : SoundEvents.CHAIN_HIT;
				pitch = bites ? 1.1F : 1.3F;
				sparks = bites ? 0 : 3;
			}
			default -> {
				sound = SoundEvents.WOOL_HIT;
				pitch = 0.8F;
				sparks = 0;
			}
		}
		level.playSound(null, at.x, at.y, at.z, sound, victim.getSoundSource(), volume, pitch);
		if (sparks > 0) {
			ClashFx.sparks(level, at, line, sparks, 0.6F);
		} else if (bites) {
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.2, 0.25, 0.2, 0.3);
		}
	}
}
