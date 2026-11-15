package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The medieval picks' shields (Guarding, Team-Abode, CC0: its ideas for the reflect, the instant block, Pummelling, Barbed
 * and the netherite shield, rewritten here for 26.3 and our guard; https://github.com/Team-Abode/guarding).
 * <ul>
 * <li><b>Shields send shots back</b>: a perfect parry ({@link Guard#timing}) with a shield turns an arrow, a trident, a
 * fireball or a thrown potion round in the air and sends it back at whoever loosed it ({@link #reflect}, from
 * {@code ShieldReflectMixin} on the shot's moment of contact, before it can hurt or splash). It is then the parrier's
 * shot. A blade still bats arrows only ({@code Swat}), and a gun's ball is never sent back.</li>
 * <li><b>Shields block at once</b>: a raised shield blocks from its first tick, without vanilla's quarter second
 * ({@code ShieldInstantMixin}); the parry window already starts at the raise.</li>
 * <li><b>Pummelling</b> (shield enchantment, I to III): a parry's shove on the attacker is much harder
 * ({@link #shove}).</li>
 * <li><b>Barbed</b> (shield enchantment): whoever strikes your shield, blocked or parried, takes thorns damage
 * ({@link #barbs}), at a point of the shield's wear.</li>
 * <li><b>The netherite shield</b>: a shield upgraded at the smithing table with netherite. Tougher, fireproof, a little
 * knockback resistance while held, and it takes armour trims (drawn on its face) in place of a banner.</li>
 * </ul>
 * Shields go on the enchanting table now (they had no enchantability), so Pummelling and Barbed are learned and paid for
 * like any other enchantment there ({@code enchanting/EnchantRecipes}).
 */
public final class ShieldCraft {
	// ---- tuning ----
	/** A shot sent back flies this much faster than it came, and at least this fast (blocks a tick). */
	public static final double REFLECT_SPEED = 1.25, REFLECT_MIN_SPEED = 0.8;
	/** It's sent at its shooter's eyes when the shooter is this near (blocks), straight back otherwise. */
	public static final double REFLECT_AIM_RANGE = 48.0;
	/** Pummelling: each level adds this much of the plain shove. */
	public static final double PUMMEL_PER_LEVEL = 0.6;
	/** Barbed: thorns damage dealt to whoever strikes the shield, and the shield's wear for it. */
	public static final float BARBED_DAMAGE = 2.0F;
	public static final int BARBED_WEAR = 1;
	/** The netherite shield: durability, knockback resistance while held, enchantability; the plain shield's enchantability. */
	public static final int NETHERITE_DURABILITY = 672, NETHERITE_ENCHANTABILITY = 15, SHIELD_ENCHANTABILITY = 10;
	public static final double NETHERITE_KNOCKBACK_RESISTANCE = 0.1;

	public static final ResourceKey<Enchantment> PUMMELLING = ResourceKey.create(Registries.ENCHANTMENT, JaymosPowers.id("pummelling"));
	public static final ResourceKey<Enchantment> BARBED = ResourceKey.create(Registries.ENCHANTMENT, JaymosPowers.id("barbed"));

	/** What a reflected shot's contact answers with: the turn itself is already done ({@link #reflect}). */
	public static final ProjectileDeflection REFLECTED = (projectile, by, random, power) -> {
	};

	public static Item NETHERITE_SHIELD;

	/** Set while Barbed's own thorns land, so a barbed shield answering them can't answer back forever. */
	private static boolean barbing;

	private ShieldCraft() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("netherite_shield"));
		NETHERITE_SHIELD = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
			.durability(NETHERITE_DURABILITY)
			.fireResistant()
			.enchantable(NETHERITE_ENCHANTABILITY)
			.repairable(Items.NETHERITE_INGOT)
			.equippableUnswappable(EquipmentSlot.OFFHAND)
			.attributes(ItemAttributeModifiers.builder().add(Attributes.KNOCKBACK_RESISTANCE,
				new AttributeModifier(JaymosPowers.id("netherite_shield"), NETHERITE_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.HAND).build())
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(0.0F, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK),
				Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK)));
		// the plain shield goes on the enchanting table too (its Pummelling and Barbed)
		DefaultItemComponentEvents.MODIFY.register(context -> context.modify(Items.SHIELD,
			(builder, lookup, item) -> builder.set(DataComponents.ENCHANTABLE, new net.minecraft.world.item.enchantment.Enchantable(SHIELD_ENCHANTABILITY))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.SHIELD, NETHERITE_SHIELD));
	}

	/** A shield (anything that blocks and isn't a weapon held up as a guard). */
	public static boolean isShield(ItemStack stack) {
		return stack.has(DataComponents.BLOCKS_ATTACKS) && !Guard.weaponGuard(stack);
	}

	private static int level(ServerLevel level, ResourceKey<Enchantment> key, ItemStack stack) {
		Optional<Holder.Reference<Enchantment>> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key);
		return holder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
	}

	/** The parry's shove, made harder by the raised shield's Pummelling. */
	public static double shove(ServerPlayer player, double shove) {
		ItemStack guard = Guard.raised(player) ? player.getUseItem() : ItemStack.EMPTY;
		if (!isShield(guard)) {
			return shove;
		}
		return shove * (1.0 + PUMMEL_PER_LEVEL * level(player.level(), PUMMELLING, guard));
	}

	/**
	 * {@code projectile} is touching {@code player}: on a perfect parry with a raised shield it is turned and sent back at
	 * whoever loosed it (it becomes the player's shot), and the parry lands. True if it was sent back.
	 */
	public static boolean reflect(ServerPlayer player, Projectile projectile) {
		if (!Guard.raised(player) || !isShield(player.getUseItem()) || Guard.shot(projectile) || projectile.getOwner() == player) {
			return false;
		}
		Vec3 motion = projectile.getDeltaMovement();
		Vec3 from = projectile.position().subtract(motion.scale(3.0));
		if (!inFront(player, from, Guard.PARRY_ANGLE)) {
			return false;
		}
		LivingEntity shooter = projectile.getOwner() instanceof LivingEntity living && living.isAlive() ? living : null;
		if (Guard.timing(player, shooter, true, false, player.getTicksUsingItem()) != Guard.Timing.PERFECT) {
			return false;
		}
		double speed = Math.max(REFLECT_MIN_SPEED, motion.length()) * REFLECT_SPEED;
		Vec3 aim;
		if (shooter != null && shooter.distanceToSqr(player) < REFLECT_AIM_RANGE * REFLECT_AIM_RANGE) {
			Vec3 to = shooter.getEyePosition().subtract(projectile.position());
			// a little high for the drop over the way back
			aim = to.add(0.0, to.horizontalDistance() * (projectile.isNoGravity() ? 0.0 : 0.08), 0.0);
		} else {
			aim = motion.scale(-1.0);
		}
		if (aim.lengthSqr() < 1.0E-6) {
			aim = player.getLookAngle();
		}
		aim = aim.normalize();
		projectile.setDeltaMovement(aim.scale(speed));
		if (projectile instanceof AbstractHurtingProjectile hurting) {
			// a fireball keeps pushing the way it now goes
			hurting.accelerationPower = Math.max(hurting.accelerationPower, 0.1);
		}
		projectile.setYRot((float) (Math.atan2(aim.x, aim.z) * (180.0 / Math.PI)));
		projectile.setXRot((float) (Math.atan2(aim.y, aim.horizontalDistance()) * (180.0 / Math.PI)));
		projectile.setOwner(player);
		projectile.needsSync = true;
		ServerLevel level = player.level();
		level.playSound(null, projectile.getX(), projectile.getY(), projectile.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 1.4F);
		Guard.parried(player, null, from, true);
		return true;
	}

	/** {@code attacker} struck {@code defender}'s shield (blocked or parried): Barbed answers. */
	public static void barbs(LivingEntity defender, @Nullable LivingEntity attacker) {
		if (barbing || attacker == null || attacker == defender || !attacker.isAlive() || !(defender.level() instanceof ServerLevel level)) {
			return;
		}
		ItemStack shield = defender.isUsingItem() ? defender.getUseItem() : ItemStack.EMPTY;
		if (!isShield(shield) || level(level, BARBED, shield) <= 0) {
			return;
		}
		barbing = true;
		try {
			attacker.hurtServer(level, level.damageSources().thorns(defender), BARBED_DAMAGE);
		} finally {
			barbing = false;
		}
		InteractionHand hand = defender.getUsedItemHand();
		shield.hurtAndBreak(BARBED_WEAR, defender, hand);
		level.sendParticles(ParticleTypes.CRIT, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 6, 0.2, 0.2, 0.2, 0.1);
	}

	private static boolean inFront(ServerPlayer player, Vec3 from, double degrees) {
		Vec3 to = from.subtract(player.position());
		Vec3 flat = new Vec3(to.x, 0.0, to.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
		return flat.normalize().dot(look) >= Math.cos(Math.toRadians(degrees));
	}
}
