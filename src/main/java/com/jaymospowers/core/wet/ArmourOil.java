package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.ArmourKind;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Armour oil (Harness 5; Lore.md, "The armourers' wider makes": "left wet it rusts, oiled it stays bright"). A flask of
 * fat or fish oil: used, it oils every piece of iron armour its user wears, in the four slots and in Jaymo's Kingdoms'
 * harness, and oiled armour gathers no new rust ({@link Rust}) for {@link #OILED_TICKS}. It takes no rust off (Andy:
 * "nothing removes rust"): looking after plate keeps it bright, and leaving it alone costs only its shine and in the end
 * its wear.
 */
public final class ArmourOil {
	/** How many oilings a flask holds, and how long one keeps the rust off (ticks; three days). */
	static final int USES = 8, OILED_TICKS = 72000;

	public static Item ARMOUR_OIL;

	private ArmourOil() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("armour_oil"));
		ARMOUR_OIL = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).durability(USES)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(ARMOUR_OIL));
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack flask = player.getItemInHand(hand);
			if (!flask.is(ARMOUR_OIL) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				oil(server, player, flask, hand);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Oils everything rustable {@code player} wears; a dab of the flask used if there was anything to oil. */
	private static void oil(ServerLevel level, Player player, ItemStack flask, net.minecraft.world.InteractionHand hand) {
		List<ItemStack> worn = new ArrayList<>();
		for (EquipmentSlot slot : ArmourGrime.SLOTS) {
			worn.add(player.getItemBySlot(slot));
		}
		worn.addAll(ArmourKind.harness.apply(player, "all"));
		long until = level.getGameTime() + OILED_TICKS;
		int oiled = 0;
		for (ItemStack piece : worn) {
			if (Rust.rusts(piece)) {
				piece.set(Rust.OILED_UNTIL, until);
				oiled++;
			}
		}
		if (oiled == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.armour_oil.nothing"));
			return;
		}
		flask.hurtAndBreak(1, player, hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEYCOMB_WAX_ON, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.armour_oil.oiled", oiled));
	}
}
