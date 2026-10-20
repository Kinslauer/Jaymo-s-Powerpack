package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
import net.minecraft.network.codec.ByteBufCodecs;
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
 * <p>
 * (0.12.3) The mask button (Andy: "costume mask button. all costumes should have a button"): the mask key (' by
 * default) or the designer's Mask button takes the costume's head piece off and puts it back ({@link #toggleMask}),
 * the rest of the costume staying on. With it off ({@link #UNMASKED}, synced to everyone) the head piece isn't drawn,
 * the face shows and a face-hiding mask hides nobody. A costume put on starts with its mask on.
 */
public final class Costume {
	/** On a player: the costume they wear (synced to everyone, kept through death). */
	public static final AttachmentType<CostumeDesign> WORN = AttachmentRegistry.create(JaymosPowers.id("worn_costume"),
		builder -> builder.persistent(CostumeDesign.CODEC).copyOnDeath().syncWith(CostumeDesign.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** (0.12.3) On a player: their costume's head piece is off (synced to everyone: they see the face). Cleared with the costume. */
	public static final AttachmentType<Boolean> UNMASKED = AttachmentRegistry.create(JaymosPowers.id("costume_unmasked"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** (0.12.3) The mask key can't flip the mask again sooner than this (ticks). */
	static final int MASK_COOLDOWN = 10;
	/** The sound of the mask coming off and going on: pitches. */
	static final float MASK_OFF_PITCH = 1.3F, MASK_ON_PITCH = 1.1F;
	/** (server) When each player last flipped their mask (game time). */
	private static final Map<UUID, Long> LAST_MASK = new ConcurrentHashMap<>();

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
			}))
			// (0.12.3) the mask key's, as a command
			.then(Commands.literal("mask").executes(c -> {
				toggleMask(c.getSource().getPlayerOrException());
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
		// (0.11.82) a Rig Helm hides the face too
		return faceHidden(player) || com.jaymospowers.rig.Rig.helmIdentity(player) != null;
	}

	/** (0.12.3) Whether this player's costume's head piece is off just now (either side). */
	public static boolean unmasked(Player player) {
		return Boolean.TRUE.equals(player.getAttached(UNMASKED));
	}

	/** (0.12.3) Whether the costume itself hides the face: a face-hiding mask that is on (either side). */
	public static boolean faceHidden(Player player) {
		CostumeDesign worn = worn(player);
		return worn != null && worn.mask().hidesFace && !unmasked(player);
	}

	/**
	 * (0.13.36) Whether this player has a hood up: the worn costume's head piece is a hood ({@link CostumeDesign.Mask#isHood})
	 * and on, with the face not hidden (either side).
	 */
	public static boolean hooded(Player player) {
		CostumeDesign worn = worn(player);
		return worn != null && worn.mask().isHood() && !unmasked(player) && !faceHidden(player);
	}

	/**
	 * (0.12.3) The mask key (server): the worn costume's head piece comes off, or goes back on. A costume without one,
	 * or none at all, with a Rig Helm on: the Helm's faceplate flips instead.
	 */
	public static void toggleMask(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		long now = player.level().getGameTime();
		Long last = LAST_MASK.get(player.getUUID());
		if (last != null && now - last >= 0 && now - last < MASK_COOLDOWN) {
			return;
		}
		LAST_MASK.put(player.getUUID(), now);
		CostumeDesign worn = worn(player);
		if (worn == null || worn.mask() == CostumeDesign.Mask.NONE) {
			if (com.jaymospowers.rig.Rig.wears(player, com.jaymospowers.rig.Rig.Piece.HELM)) {
				com.jaymospowers.rig.RigHelm.toggleFace(player);
				return;
			}
			if (com.jaymospowers.power.web.WebSymbiote.suitShown(player)) {
				// (0.13.23) the symbiote's suit: its mask comes off (or flows back over the face)
				com.jaymospowers.power.web.WebSymbiote.toggleMask(player);
				return;
			}
			player.sendOverlayMessage(Component.translatable(worn == null ? "message.jaymospowers.costume.none" : "message.jaymospowers.costume.no_mask"));
			return;
		}
		boolean off = !unmasked(player);
		if (off) {
			player.setAttached(UNMASKED, true);
		} else {
			player.removeAttached(UNMASKED);
		}
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.7F,
			off ? MASK_OFF_PITCH : MASK_ON_PITCH);
		Component piece = Component.translatable("costume.jaymospowers.mask." + worn.mask().getSerializedName());
		player.sendOverlayMessage(Component.translatable(off ? "message.jaymospowers.costume.mask_off" : "message.jaymospowers.costume.mask_on", piece));
		// the face is out (or hidden again): who you're seen as follows
		SecretIdentity.update(player);
	}

	/** Puts this design on (server): whatever was worn before goes back to the inventory, or drops. */
	public static void wear(ServerPlayer player, CostumeDesign design) {
		CostumeDesign old = worn(player);
		player.setAttached(WORN, design.sanitized());
		// (0.12.3) a costume goes on with its mask on
		player.removeAttached(UNMASKED);
		if (old != null) {
			player.getInventory().placeItemBackInInventory(CostumeItem.of(old), Prediction.SERVER_ONLY);
		}
		SecretIdentity.update(player);
		rustle(player);
	}

	/** Takes the worn costume off (server) and gives it back; false if none was worn. */
	/** Takes the worn costume off and hands back its item (for the caller to drop), or null if none. */
	public static @Nullable ItemStack strip(ServerPlayer player) {
		CostumeDesign old = worn(player);
		if (old == null) {
			return null;
		}
		player.removeAttached(WORN);
		player.removeAttached(UNMASKED);
		SecretIdentity.update(player);
		return CostumeItem.of(old);
	}

	public static boolean takeOff(ServerPlayer player) {
		CostumeDesign old = worn(player);
		if (old == null) {
			return false;
		}
		player.removeAttached(WORN);
		player.removeAttached(UNMASKED);
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
