package com.jaymoskitchen.horse;

import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * The horse drop: saddlebags (worn in the horse armor slot, they give a horse a donkey's worth of storage, and the
 * goods stay in the bags when they come off), and horse-drawn vehicles in every wood, after NiftyCarts: the wagon
 * (a chest and a seat), the coach (six seats), the supply cart, the animal cart and the plow, plus the wheel they're built with.
 */
public final class Saddlebags {
	public static Item SADDLEBAG;
	public static Item WHEEL;
	public static Item WAGON_ITEM;
	public static EntityType<CartEntity> WAGON;
	public static EntityType<CartEntity> COACH;
	public static EntityType<CartEntity> SUPPLY_CART;
	public static EntityType<CartEntity> ANIMAL_CART;
	public static EntityType<CartEntity> PLOW;
	/** The NiftyCarts carts' items: kind, then wood. */
	public static final Map<CartKind, Map<String, Item>> CART_ITEMS = new EnumMap<>(CartKind.class);
	public static MenuType<PlowMenu> PLOW_MENU;

	private Saddlebags() {
	}

	public static void register() {
		HolderGetter<EntityType<?>> entityTypes = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.ENTITY_TYPE);
		SADDLEBAG = item("saddlebag", SaddlebagItem::new, new Item.Properties()
			.stacksTo(1)
			.component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.BODY)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAllowedEntities(entityTypes.getOrThrow(EntityTypeTags.CAN_WEAR_HORSE_ARMOR))
				.setDamageOnHurt(false)
				.setCanBeSheared(true)
				.setShearingSound(SoundEvents.HORSE_ARMOR_UNEQUIP)
				.build()));
		WHEEL = item("wheel", Item::new, new Item.Properties());

		WAGON = cart("wagon", 1.8F, 1.6F);
		COACH = cart("coach", 2.2F, 1.8F);
		SUPPLY_CART = cart("supply_cart", 1.5F, 1.4F);
		ANIMAL_CART = cart("animal_cart", 1.3F, 1.4F);
		PLOW = cart("plow", 1.3F, 1.4F);
		for (CartKind kind : CartKind.values()) {
			if (!kind.nifty) {
				continue;
			}
			EntityType<CartEntity> type = type(kind);
			Map<String, Item> byWood = new LinkedHashMap<>();
			for (String wood : NiftyCarts.WOODS) {
				// (the oak wagon keeps the original wagon's id)
				String id = kind == CartKind.WAGON && wood.equals("oak") ? "wagon" : wood + "_" + kind.id;
				byWood.put(wood, item(id, props -> new CartItem(type, wood, props), new Item.Properties().stacksTo(1)));
			}
			CART_ITEMS.put(kind, byWood);
		}
		WAGON_ITEM = CART_ITEMS.get(CartKind.WAGON).get("oak");

		PLOW_MENU = Registry.register(BuiltInRegistries.MENU, JaymosKitchen.id("plow"), new MenuType<>(PlowMenu::new, FeatureFlags.VANILLA_SET));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			// (stacks are made here: items can't be stacked at registration, before components are bound)
			List<Item> items = new ArrayList<>(List.of(SADDLEBAG, WHEEL));
			for (Map<String, Item> byWood : CART_ITEMS.values()) {
				items.addAll(byWood.values());
			}
			for (Item item : items) {
				output.accept(new ItemStack(item));
			}
		});
	}

	public static EntityType<CartEntity> type(CartKind kind) {
		return switch (kind) {
			case WAGON -> WAGON;
			case COACH -> COACH;
			case SUPPLY_CART -> SUPPLY_CART;
			case ANIMAL_CART -> ANIMAL_CART;
			case PLOW -> PLOW;
		};
	}

	/** The item a cart of this kind and wood breaks back into. */
	public static Item cartItem(CartKind kind, String wood) {
		Map<String, Item> byWood = CART_ITEMS.get(kind);
		return byWood.getOrDefault(wood, byWood.get("oak"));
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	private static EntityType<CartEntity> cart(String name, float width, float height) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
			EntityType.Builder.<CartEntity>of(CartEntity::new, MobCategory.MISC)
				.sized(width, height)
				.clientTrackingRange(10)
				.build(key));
	}

	/** The saddlebags this horse is wearing, or null. */
	public static @Nullable ItemStack wearing(AbstractHorse horse) {
		ItemStack body = horse.getItemBySlot(EquipmentSlot.BODY);
		return body.is(SADDLEBAG) ? body : null;
	}
}
