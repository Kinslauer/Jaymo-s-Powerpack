package com.jaymoskingdoms.lands;

import com.jaymoskingdoms.JaymosKingdoms;
import java.lang.reflect.Method;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Salt on the tongue: food cured with salt (Kitchen's salting, {@code Curing}) and the salty dishes ({@link #SALTY}) leave the
 * eater thirsty, {@link #THIRST} off their water, through Kitchen's {@code Thirst.parch}. Salt keeps meat; the sea in it has to
 * be paid for. Without Kitchen nothing is salted and nothing happens.
 */
public final class SaltyFood {
	/** Water points lost eating a salted bite (Kitchen's water runs 0 to 20). */
	static final int THIRST = 3;
	/** Dishes that are salty by themselves. */
	public static final TagKey<Item> SALTY = TagKey.create(Registries.ITEM, JaymosKingdoms.id("salty_foods"));

	private static @Nullable Method parch;
	private static boolean looked;
	private static @Nullable DataComponentType<?> cured, preserved;

	private SaltyFood() {
	}

	/** {@code eater} has just begun to take in {@code food} (the stack still whole). */
	public static void ate(LivingEntity eater, ItemStack food) {
		if (!(eater instanceof ServerPlayer player) || player.getAbilities().instabuild || !salty(food)) {
			return;
		}
		Method method = method();
		if (method != null) {
			try {
				method.invoke(null, player, THIRST);
			} catch (ReflectiveOperationException | RuntimeException e) {
				parch = null;
			}
		}
	}

	/** Whether {@code food} was salted, or is a salty dish. */
	static boolean salty(ItemStack food) {
		if (food.is(SALTY)) {
			return true;
		}
		look();
		if (preserved == null || food.get(preserved) == null) {
			return false;
		}
		// food kept before the ways of keeping were told apart counts as salted
		Object how = cured == null ? null : food.get(cured);
		return how == null || "salt".equals(how);
	}

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		cured = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "cured")).orElse(null);
		preserved = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "preserved")).orElse(null);
	}

	private static @Nullable Method method() {
		if (parch == null && !lookedForParch) {
			lookedForParch = true;
			try {
				parch = Class.forName("com.jaymoskitchen.climate.Thirst").getMethod("parch", ServerPlayer.class, int.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				parch = null;
			}
		}
		return parch;
	}

	private static boolean lookedForParch;
}
