package com.jaymospowers.armory;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.phys.Vec3;

/**
 * (Havens patch 5, spoils) The kings' own weapons. Jaymo's Kingdoms drops a king's iron head when he falls (a goblin
 * king's, an orc king's, the Ogre King's); put together at the Tool Station like any forged weapon
 * ({@code tinkering/WeaponParts#KINGS}), it's his weapon in your hands. None is shaped, cast or forged anew.
 * <ul>
 * <li><b>Gutting Hook</b> (goblin king): quick, and each hit hooks the foe a step toward you.</li>
 * <li><b>Warchief's Cleaver</b> (orc king): heavy, and it knocks a raised shield aside for a while.</li>
 * <li><b>Ogre King's Maul</b> (Ogre King): very slow and very heavy; it throws the struck back and leaves them reeling.</li>
 * </ul>
 */
public final class KingWeapons {
	/** Gutting Hook: damage over the hand and attack speed; how hard each hit drags the foe toward you. */
	static final float HOOK_DAMAGE = 3.0F, HOOK_SPEED = -2.0F;
	static final double HOOK_PULL = 0.55;
	/** Warchief's Cleaver: damage and speed; how long a struck shield stays down (seconds). */
	static final float CLEAVER_DAMAGE = 6.0F, CLEAVER_SPEED = -3.0F, CLEAVER_SHIELD_BREAK = 6.0F;
	/** Ogre King's Maul: damage and speed; how far it throws the struck, and how long and hard they reel (ticks, Slowness level). */
	static final float MAUL_DAMAGE = 9.0F, MAUL_SPEED = -3.4F, MAUL_THROW = 1.4F;
	static final int MAUL_REEL_TICKS = 50, MAUL_REEL = 1;

	public static Item GUTTING_HOOK, WARCHIEF_CLEAVER, OGRE_KINGS_MAUL;

	private KingWeapons() {
	}

	/** Item properties, for {@code Armory.item}. */
	static Item.Properties hook(Item.Properties p) {
		return p.sword(ToolMaterial.IRON, HOOK_DAMAGE, HOOK_SPEED).rarity(Rarity.RARE);
	}

	static Item.Properties cleaver(Item.Properties p) {
		return p.sword(ToolMaterial.IRON, CLEAVER_DAMAGE, CLEAVER_SPEED).rarity(Rarity.RARE)
			.component(DataComponents.WEAPON, new Weapon(1, CLEAVER_SHIELD_BREAK));
	}

	static Item.Properties maul(Item.Properties p) {
		return p.sword(ToolMaterial.IRON, MAUL_DAMAGE, MAUL_SPEED).rarity(Rarity.RARE);
	}

	/** Which king's weapon: its hit and its tooltip line. */
	enum Kind { HOOK, CLEAVER, MAUL }

	/** A king's weapon. */
	static final class KingWeapon extends Item {
		private final Kind kind;

		KingWeapon(Item.Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
			super.postHurtEnemy(stack, target, attacker);
			if (!(attacker.level() instanceof ServerLevel level) || !target.isAlive()) {
				return;
			}
			switch (this.kind) {
				case HOOK -> {
					Vec3 pull = attacker.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
					if (pull.lengthSqr() > 1.0E-4) {
						pull = pull.normalize().scale(HOOK_PULL);
						target.push(pull.x, 0.1, pull.z);
						target.needsSync = true;
						level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8F, 1.3F);
					}
				}
				case MAUL -> {
					target.knockback(MAUL_THROW, attacker.getX() - target.getX(), attacker.getZ() - target.getZ(), attacker.damageSources().generic(), MAUL_DAMAGE);
					target.needsSync = true;
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, MAUL_REEL_TICKS, MAUL_REEL), attacker);
					level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.9F, 0.7F);
				}
				case CLEAVER -> {
					// its shield break is the Weapon component's
				}
			}
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("item.jaymospowers.king_weapon." + this.kind.name().toLowerCase(java.util.Locale.ROOT))
				.withStyle(ChatFormatting.GRAY));
		}
	}

	/** Every king's weapon (for tags and the tab). */
	public static List<Item> all() {
		return List.of(GUTTING_HOOK, WARCHIEF_CLEAVER, OGRE_KINGS_MAUL);
	}
}
