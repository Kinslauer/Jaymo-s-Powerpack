package com.jaymospowers.tinkering;

import com.jaymospowers.JaymosPowers;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * What the dragons leave (Kingdoms' dragons drop these by their ids when a wild one is killed).
 * <p>
 * <b>Dragon bone</b>, one item a breath: ember (fire), rime (frost), venom (acid) and storm. Each is a Tinkering material
 * of its own ({@link TinkerMaterial#EMBER_DRAGON_BONE} and the rest), shaped at the Part Builder like bone, and keeps
 * the breath as its trait ({@link TraitEffects}).
 * <p>
 * <b>Dragon hide</b> makes the dragonhide round shield: a round shield like the Armory's ({@link #SHIELD_DURABILITY}),
 * that cannot burn and, while it is raised, lets no fire of any kind through, from any side, and puts its holder out.
 */
public final class GreatParts {
	static final int SHIELD_DURABILITY = 900;
	/** How quickly the shield comes up (seconds) and the arc it covers against everything that isn't fire. */
	static final float SHIELD_RAISE = 0.15F, SHIELD_ARC = 90.0F;

	public static Item EMBER_BONE, RIME_BONE, VENOM_BONE, STORM_BONE, HIDE, SHIELD;

	private GreatParts() {
	}

	public static void init() {
		EMBER_BONE = bone("ember_dragon_bone", TinkerMaterial.EMBER_DRAGON_BONE);
		RIME_BONE = bone("rime_dragon_bone", TinkerMaterial.RIME_DRAGON_BONE);
		VENOM_BONE = bone("venom_dragon_bone", TinkerMaterial.VENOM_DRAGON_BONE);
		STORM_BONE = bone("storm_dragon_bone", TinkerMaterial.STORM_DRAGON_BONE);
		HIDE = item("dragon_hide", new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("dragonhide_round_shield"));
		SHIELD = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).durability(SHIELD_DURABILITY).rarity(Rarity.RARE)
			.fireResistant().repairable(HIDE).equippableUnswappable(EquipmentSlot.OFFHAND)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(SHIELD_RAISE, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(SHIELD_ARC, Optional.empty(), 0.0F, 1.0F)),
				new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
				Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(EMBER_BONE, RIME_BONE, VENOM_BONE, STORM_BONE, HIDE)) {
				output.accept(item);
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.accept(SHIELD));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GreatParts::shrugOff);
	}

	private static Item bone(String id, TinkerMaterial material) {
		Item item = item(id, new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant());
		TinkerMaterial.own(material, 1, item);
		return item;
	}

	private static Item item(String id, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new Item(properties.setId(key)));
	}

	/** Behind a raised dragonhide shield no fire gets through, whichever side it comes from, and its holder is put out. */
	private static boolean shrugOff(LivingEntity entity, DamageSource source, float amount) {
		if (!source.is(DamageTypeTags.IS_FIRE) || !entity.isBlocking() || !entity.getUseItem().is(SHIELD)) {
			return true;
		}
		entity.clearFire();
		return false;
	}
}
