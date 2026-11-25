package com.jaymospowers.dino;

import com.jaymospowers.dino.entity.Species;
import com.jaymospowers.dino.item.TranqItems;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.ItemLike;

public final class DinoItems {
	public static Item RAW_DINO_MEAT, COOKED_DINO_MEAT, RAW_PRIME_MEAT, COOKED_PRIME_MEAT;
	public static Item DINO_BONE, DINO_HIDE, TYRANNOSAURUS_TOOTH, RAPTOR_CLAW, TRICERATOPS_HORN, VENOM_SAC, NARCOTIC;
	public static Item TRANQ_DART, TRANQ_RIFLE;
	private static final Map<Species, Item> SKULLS = new EnumMap<>(Species.class);
	private static final Map<Species, Item> SADDLES = new EnumMap<>(Species.class);
	private static final Map<Species, Item> SKELETONS = new EnumMap<>(Species.class);
	private static final Map<Species, Item> EGGS = new EnumMap<>(Species.class);
	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Dino.id("dino"));

	private DinoItems() {
	}

	private static Item register(String name, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Dino.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	private static Item plain(String name) {
		return register(name, Item::new);
	}

	private static Item food(String name, int nutrition, float saturation) {
		return register(name, p -> new Item(p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(),
			Consumables.defaultFood().build())));
	}

	private static Item.Properties saddleProps(Item.Properties p) {
		HolderGetter<EntityType<?>> types = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
		return p.stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.SADDLE)
			.setEquipSound(SoundEvents.HORSE_SADDLE)
			.setAllowedEntities(types.getOrThrow(DinoTags.DINOSAURS))
			.setEquipOnInteract(true)
			.setCanBeSheared(true)
			.setShearingSound(SoundEvents.SADDLE_UNEQUIP)
			.build());
	}

	public static Item skull(Species s) {
		return SKULLS.get(s);
	}

	public static Item saddle(Species s) {
		return SADDLES.get(s);
	}

	public static boolean isDinoSaddle(ItemStack stack) {
		for (Item item : SADDLES.values()) {
			if (stack.is(item)) {
				return true;
			}
		}
		return false;
	}

	public static Item skeleton(Species s) {
		return SKELETONS.get(s);
	}

	public static Item egg(Species s) {
		return EGGS.get(s);
	}

	public static void init() {
		RAW_DINO_MEAT = food("raw_dino_meat", 3, 0.3F);
		COOKED_DINO_MEAT = food("cooked_dino_meat", 8, 0.8F);
		RAW_PRIME_MEAT = food("raw_prime_meat", 4, 0.4F);
		COOKED_PRIME_MEAT = food("cooked_prime_meat", 12, 1.0F);
		DINO_BONE = plain("dino_bone");
		DINO_HIDE = plain("dino_hide");
		TYRANNOSAURUS_TOOTH = plain("tyrannosaurus_tooth");
		RAPTOR_CLAW = plain("raptor_claw");
		TRICERATOPS_HORN = plain("triceratops_horn");
		VENOM_SAC = plain("dino_venom_sac");
		NARCOTIC = plain("narcotic");
		TRANQ_DART = register("tranq_dart", TranqItems.Dart::new);
		TRANQ_RIFLE = register("tranq_rifle", p -> new TranqItems.Rifle(p.durability(384)));
		for (Species s : Species.values()) {
			SKULLS.put(s, register(s.id + "_skull", p -> new BlockItem(DinoBlocks.skull(s), p.useItemDescriptionPrefix().rarity(Rarity.UNCOMMON).stacksTo(16))));
			SKELETONS.put(s, register(s.id + "_skeleton", p -> new TranqItems.SkeletonKit(s, p.rarity(Rarity.UNCOMMON).stacksTo(16))));
			EGGS.put(s, register(s.id + "_spawn_egg", p -> new SpawnEggItem(p.spawnEgg(DinoEntities.dinosaur(s)))));
			if (s.rideable()) {
				SADDLES.put(s, register(s.id + "_saddle", p -> new Item(saddleProps(p))));
			}
		}
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.dino"))
			.icon(() -> new ItemStack(skull(Species.TYRANNOSAURUS)))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(TAB).register(out -> {
			for (ItemLike item : new ItemLike[] {TRANQ_RIFLE, TRANQ_DART, NARCOTIC, RAW_DINO_MEAT, COOKED_DINO_MEAT, RAW_PRIME_MEAT, COOKED_PRIME_MEAT,
				DINO_BONE, DINO_HIDE, TYRANNOSAURUS_TOOTH, RAPTOR_CLAW, TRICERATOPS_HORN, VENOM_SAC}) {
				out.accept(item);
			}
			for (Species s : Species.values()) {
				if (s.rideable()) {
					out.accept(saddle(s));
				}
			}
			for (Species s : Species.values()) {
				out.accept(skull(s));
			}
			for (Species s : Species.values()) {
				out.accept(skeleton(s));
			}
			for (Species s : Species.values()) {
				out.accept(egg(s));
			}
		});
	}
}
