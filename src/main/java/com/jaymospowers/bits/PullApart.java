package com.jaymospowers.bits;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.ground.GroundRocks;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "deconstruct simple items in the inventory, for example you should be able to pull wool apart into 4 string, and
 * recraft it the same way (cobblestone, leather): it will give more ways to acquire early materials". Ctrl + right-click a
 * stack in any container screen (not the creative inventory) pulls one apart into its parts; Ctrl + Shift + right-click
 * pulls the whole stack. Each one comes apart into exactly what its 2x2 grid recipe takes, so the grid puts it back
 * together (4 string make white wool, 4 rocks make their block, 4 leather scraps and resin make leather).
 * A gesture and not a grid recipe because one wool or one leather alone in the grid already makes rags
 * ({@code recipe/medicine}). The client sends only the slot; the server checks it against its own copy of the menu.
 * The table is shown in JEI ({@code client/jei/PullApartJei}).
 */
public final class PullApart {
	/** What a thing pulls apart into: this many of that part. */
	public record Parts(Item part, int count) {
		public ItemStack stack(int count) {
			return new ItemStack(part, count);
		}
	}

	/** Client to server: pull apart the stack in this slot (its menu slot number) of the menu with this id, one or all of it. */
	public record Pull(int containerId, int slot, boolean all) implements CustomPacketPayload {
		public static final Type<Pull> TYPE = new Type<>(JaymosPowers.id("pull_apart"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pull> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.containerId);
				buf.writeVarInt(p.slot);
				buf.writeBoolean(p.all);
			},
			buf -> new Pull(buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Pull> type() {
			return TYPE;
		}
	}

	/** The wools, by colour, for their ids. */
	private static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
		"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	/** Each loose rock kind's block (the block its 4 rocks craft into, recipe/*_from_*_rocks). */
	private static final Map<GroundRocks.Variant, String> ROCK_BLOCKS = Map.ofEntries(
		Map.entry(GroundRocks.Variant.STONE, "cobblestone"), Map.entry(GroundRocks.Variant.MOSSY, "mossy_cobblestone"),
		Map.entry(GroundRocks.Variant.GRANITE, "granite"), Map.entry(GroundRocks.Variant.DIORITE, "diorite"),
		Map.entry(GroundRocks.Variant.ANDESITE, "andesite"), Map.entry(GroundRocks.Variant.DEEPSLATE, "cobbled_deepslate"),
		Map.entry(GroundRocks.Variant.TUFF, "tuff"), Map.entry(GroundRocks.Variant.SANDSTONE, "sandstone"),
		Map.entry(GroundRocks.Variant.RED_SANDSTONE, "red_sandstone"), Map.entry(GroundRocks.Variant.NETHERRACK, "netherrack"),
		Map.entry(GroundRocks.Variant.BLACKSTONE, "blackstone"), Map.entry(GroundRocks.Variant.BASALT, "basalt"));

	private static @Nullable Map<Item, Parts> table;

	private PullApart() {
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Pull.TYPE, Pull.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Pull.TYPE, (payload, ctx) ->
			ctx.server().execute(() -> pull(ctx.player(), payload.containerId(), payload.slot(), payload.all())));
	}

	/**
	 * Everything that pulls apart, in the order JEI lists them. Built on first use (every mod's items are registered by
	 * then); a part from a mod that isn't loaded (Kitchen's leather scraps) leaves its row out.
	 */
	public static Map<Item, Parts> table() {
		if (table == null) {
			Map<Item, Parts> t = new LinkedHashMap<>();
			for (String colour : COLOURS) {
				put(t, "minecraft:" + colour + "_wool", Items.STRING, 4);
			}
			for (GroundRocks.Variant variant : GroundRocks.Variant.values()) {
				Block rock = GroundRocks.ROCKS.get(variant);
				String block = ROCK_BLOCKS.get(variant);
				if (rock != null && block != null) {
					put(t, "minecraft:" + block, rock.asItem(), 4);
				}
			}
			put(t, "minecraft:leather", BuiltInRegistries.ITEM.getOptional(Identifier.parse("jaymoskitchen:leather_scraps")).orElse(null), 4);
			put(t, "minecraft:snow_block", Items.SNOWBALL, 4);
			put(t, "minecraft:clay", Items.CLAY_BALL, 4);
			put(t, "minecraft:dripstone_block", Items.POINTED_DRIPSTONE, 4);
			put(t, "minecraft:honeycomb_block", Items.HONEYCOMB, 4);
			put(t, "minecraft:glowstone", Items.GLOWSTONE_DUST, 4);
			put(t, "minecraft:quartz_block", Items.QUARTZ, 4);
			put(t, "minecraft:amethyst_block", Items.AMETHYST_SHARD, 4);
			table = Collections.unmodifiableMap(t);
		}
		return table;
	}

	private static void put(Map<Item, Parts> t, String whole, @Nullable Item part, int count) {
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(whole)).orElse(null);
		if (item != null && part != null && part != Items.AIR) {
			t.put(item, new Parts(part, count));
		}
	}

	/** What this stack pulls apart into, or null if it doesn't. */
	public static @Nullable Parts of(ItemStack stack) {
		return stack.isEmpty() ? null : table().get(stack.getItem());
	}

	private static void pull(ServerPlayer player, int containerId, int number, boolean all) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu.containerId != containerId || number < 0 || number >= menu.slots.size() || !menu.getCarried().isEmpty()) {
			return;
		}
		Slot slot = menu.getSlot(number);
		ItemStack stack = slot.getItem();
		Parts parts = of(stack);
		// a slot you can both take from and put into: not a crafting or furnace output, not a locked slot
		if (parts == null || !slot.mayPickup(player) || !slot.mayPlace(stack)) {
			return;
		}
		ItemStack taken = slot.remove(all ? stack.getCount() : 1);
		if (taken.isEmpty()) {
			return;
		}
		slot.setChanged();
		sound(player, taken);
		int left = taken.getCount() * parts.count();
		int most = parts.stack(1).getMaxStackSize();
		// the whole stack pulled: the first handful of parts goes where it lay
		if (!slot.hasItem() && slot.mayPlace(parts.stack(1))) {
			int here = Math.min(left, Math.min(most, slot.getMaxStackSize(parts.stack(1))));
			slot.set(parts.stack(here));
			left -= here;
		}
		while (left > 0) {
			int n = Math.min(left, most);
			player.getInventory().placeItemBackInInventory(parts.stack(n), Prediction.SERVER_ONLY);
			left -= n;
		}
		menu.broadcastChanges();
	}

	/** The thing's own sound as it comes apart: wool's soft tear, stone's crunch... */
	private static void sound(ServerPlayer player, ItemStack whole) {
		SoundEvent sound = whole.getItem() instanceof BlockItem block
			? block.getBlock().defaultBlockState().getSoundType().getBreakSound() : SoundEvents.ARMOR_EQUIP_LEATHER.value();
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.5F,
			0.9F + player.getRandom().nextFloat() * 0.2F);
	}
}
