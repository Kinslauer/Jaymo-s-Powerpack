package com.jaymospowers.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Jaymo's Kitchen's waterskins (optional), read and sipped without touching Kitchen's classes: the water in one is the data
 * component {@code jaymoskitchen:waterskin_water}, a record whose first part is the sips left. Darktide's water charge
 * ({@code DarktideVessels}) draws on them as a canteen.
 */
public final class KitchenWaterskins {
	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("jaymoskitchen");
	private static final Identifier WATER = Identifier.fromNamespaceAndPath("jaymoskitchen", "waterskin_water");

	private KitchenWaterskins() {
	}

	/** The sips of water left in {@code stack}, if it is a Kitchen waterskin (0 otherwise). */
	public static int sips(ItemStack stack) {
		Object water = water(stack);
		if (water == null) {
			return 0;
		}
		try {
			Object sips = water.getClass().getRecordComponents()[0].getAccessor().invoke(water);
			return sips instanceof Integer n ? n : 0;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** Takes one sip out of {@code stack}: true if there was one. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static boolean sip(ItemStack stack) {
		Object water = water(stack);
		int sips = sips(stack);
		if (water == null || sips <= 0) {
			return false;
		}
		try {
			RecordComponent[] parts = water.getClass().getRecordComponents();
			Object[] args = new Object[parts.length];
			Class<?>[] types = new Class<?>[parts.length];
			for (int i = 0; i < parts.length; i++) {
				types[i] = parts[i].getType();
				Method get = parts[i].getAccessor();
				args[i] = get.invoke(water);
			}
			args[0] = sips - 1;
			Constructor<?> make = water.getClass().getDeclaredConstructor(types);
			Object less = make.newInstance(args);
			stack.set((DataComponentType)type(), less);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	private static @Nullable Object water(ItemStack stack) {
		DataComponentType<?> type = type();
		return type == null || stack.isEmpty() ? null : stack.get(type);
	}

	private static @Nullable DataComponentType<?> type() {
		return LOADED ? BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(WATER) : null;
	}
}
