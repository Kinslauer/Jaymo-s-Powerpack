package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.66) Costumes, stage 1: design one at the Costume Table ({@link CostumeTableBlock}, the designer screen), sew it
 * (wool, leather, string and the dyes it uses), and put it on by using the costume item. A worn costume lives on the
 * player ({@link #WORN}: kept through death, synced to everyone), over the body in every pose, with the armour under it
 * still protecting but not drawn. Take it off at a table or with {@code /costume off}.
 * <p>
 * Later stages read the worn design: {@link #masked} is the hook for a secret identity ({@link SecretIdentity}, 0.11.70).
 */
public final class Costume {
	/** On a player: the costume they wear (synced to everyone, kept through death). */
	public static final AttachmentType<CostumeDesign> WORN = AttachmentRegistry.create(JaymosPowers.id("worn_costume"),
		builder -> builder.persistent(CostumeDesign.CODEC).copyOnDeath().syncWith(CostumeDesign.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** On a costume item: its design. */
	public static DataComponentType<CostumeDesign> DESIGN;
	public static Block TABLE;
	public static Item COSTUME;

	private Costume() {
	}

	public static void init() {
		DESIGN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("costume_design"),
			DataComponentType.<CostumeDesign>builder().persistent(CostumeDesign.CODEC).networkSynchronized(CostumeDesign.STREAM_CODEC).build());
		ResourceKey<Block> tableKey = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("costume_table"));
		TABLE = Registry.register(BuiltInRegistries.BLOCK, tableKey, new CostumeTableBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).setId(tableKey)));
		item("costume_table", p -> new BlockItem(TABLE, p.useBlockDescriptionPrefix()));
		COSTUME = item("costume", p -> new CostumeItem(p.stacksTo(1).component(DESIGN, CostumeDesign.DEFAULT)));
		CostumeNet.init();
		CostumeTailoring.init();
		SecretIdentity.init();
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			output.accept(TABLE);
			output.accept(CostumeItem.of(CostumeDesign.DEFAULT));
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(Commands.literal("costume")
			.then(Commands.literal("off").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				if (!takeOff(player)) {
					c.getSource().sendFailure(Component.translatable("message.jaymospowers.costume.none"));
					return 0;
				}
				return 1;
			}))));
	}

	private static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
	}

	/** The costume this player wears, or null (on either side). */
	public static @Nullable CostumeDesign worn(Player player) {
		return player.getAttached(WORN);
	}

	/** Whether this player's face is hidden by their costume (for a later secret identity). */
	public static boolean masked(Player player) {
		CostumeDesign worn = worn(player);
		// (0.11.82) a Rig Helm hides the face too
		return worn != null && worn.mask().hidesFace || com.jaymospowers.rig.Rig.helmIdentity(player) != null;
	}

	/** Puts this design on (server): whatever was worn before goes back to the inventory, or drops. */
	public static void wear(ServerPlayer player, CostumeDesign design) {
		CostumeDesign old = worn(player);
		player.setAttached(WORN, design.sanitized());
		if (old != null) {
			player.getInventory().placeItemBackInInventory(CostumeItem.of(old), Prediction.SERVER_ONLY);
		}
		SecretIdentity.update(player);
		rustle(player);
	}

	/** Takes the worn costume off (server) and gives it back; false if none was worn. */
	public static boolean takeOff(ServerPlayer player) {
		CostumeDesign old = worn(player);
		if (old == null) {
			return false;
		}
		player.removeAttached(WORN);
		player.getInventory().placeItemBackInInventory(CostumeItem.of(old), Prediction.SERVER_ONLY);
		SecretIdentity.update(player);
		rustle(player);
		return true;
	}

	private static void rustle(Player player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** The dye colours' names, for tooltips and the designer. */
	public static Component colourName(DyeColor colour) {
		return Component.translatable("color.minecraft." + colour.getName());
	}
}
