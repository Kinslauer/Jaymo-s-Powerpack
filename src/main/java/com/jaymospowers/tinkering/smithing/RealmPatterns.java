package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.function.BiPredicate;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Medieval picks (me-sidesmith): a realm's designs are its own. The plate helm a kingdom's knights wear and the war arm
 * its guards carry are forged only by a smith sworn to that realm, or by one holding a <b>captured pattern</b> of it: a
 * blueprint taken from a piece at the Drafting Table ({@link Patterns}), or a copy of one. Which designs a realm owns, and
 * who is sworn to it, is Jaymo's Kingdoms' to say ({@link #sworn}, handed over by reflection); a design no standing realm
 * owns is anyone's. A design the smith may not forge is simply not on the anvil's list.
 */
public final class RealmPatterns {
	/** Set on a blueprint that came from taking a piece apart (and kept on its copies). */
	public static DataComponentType<Boolean> CAPTURED;

	/** Whether this smith may forge this shape ({@link Blueprints#shape}) without a captured pattern. Kingdoms sets it; alone, anyone may. */
	public static BiPredicate<ServerPlayer, String> sworn = (smith, shape) -> true;

	private RealmPatterns() {
	}

	/** A shape a realm can call its own: a heraldic armour piece or a weapon head. */
	public static boolean claimable(Forging.Option option) {
		String shape = Blueprints.shape(option);
		return shape.startsWith("heraldry/") || shape.startsWith("weapon/");
	}

	static void register() {
		CAPTURED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("captured_pattern"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
		Forging.CHECKS.add((smith, anvil, option) -> {
			if (!claimable(option) || smith.isCreative()) {
				return null;
			}
			String shape = Blueprints.shape(option);
			return sworn.test(smith, shape) || captured(smith, shape) ? null
				: Component.translatable("message.jaymospowers.smithing.realm_design", Blueprints.shapeName(shape));
		});
	}

	/** Whether the smith carries a captured pattern of this shape. */
	private static boolean captured(ServerPlayer smith, String shape) {
		Inventory inventory = smith.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (Boolean.TRUE.equals(stack.get(CAPTURED)) && shape.equals(Blueprints.shapeOf(stack))) {
				return true;
			}
		}
		return false;
	}
}
