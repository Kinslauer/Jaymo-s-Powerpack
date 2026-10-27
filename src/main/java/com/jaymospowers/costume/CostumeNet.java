package com.jaymospowers.costume;

import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Costume Table's packets, and what sewing costs. The designer opens from the server ({@link Open}); sewing
 * ({@link Sew}) and taking off ({@link TakeOff}) are checked again here: the table must still be there and in reach, the
 * design is cleaned, and the materials must be in the inventory (outside creative).
 */
public final class CostumeNet {
	/** Sewing a costume: wool, leather and string, plus one dye of each colour it uses. */
	public static final int WOOL = 4, LEATHER = 2, STRING = 1, DYE_EACH = 1;
	/** (0.17.58) The suit editor's pieces (gloves, boots, belt, helmet, fins): this many iron nuggets for each, and one dye for a trim of its own. */
	public static final int NUGGETS_PER_PIECE = 2;
	/** How far (beyond the usual reach) the table may be when the screen's buttons are pressed. */
	private static final double REACH_BUFFER = 2.0;
	/** Each colour's dye item, found once (dyes are told apart by their dye component). */
	private static final Map<DyeColor, ItemStack> DYE_ICONS = Collections.synchronizedMap(new EnumMap<>(DyeColor.class));

	/** Server → client: open the designer for the table at {@code pos}. */
	public record Open(BlockPos pos) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("costume_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = BlockPos.STREAM_CODEC.map(Open::new, Open::pos);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: sew this design at the table at {@code pos}. */
	public record Sew(BlockPos pos, CostumeDesign design) implements CustomPacketPayload {
		public static final Type<Sew> TYPE = new Type<>(JaymosPowers.id("costume_sew"));
		public static final StreamCodec<ByteBuf, Sew> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Sew::pos,
			CostumeDesign.STREAM_CODEC, Sew::design, Sew::new);

		@Override
		public Type<Sew> type() {
			return TYPE;
		}
	}

	/** Client → server: take the worn costume off, at the table at {@code pos}. */
	public record TakeOff(BlockPos pos) implements CustomPacketPayload {
		public static final Type<TakeOff> TYPE = new Type<>(JaymosPowers.id("costume_take_off"));
		public static final StreamCodec<ByteBuf, TakeOff> CODEC = BlockPos.STREAM_CODEC.map(TakeOff::new, TakeOff::pos);

		@Override
		public Type<TakeOff> type() {
			return TYPE;
		}
	}

	/** (0.12.3) Client → server: the mask key (or the designer's Mask button): the costume's head piece off / on. */
	public record ToggleMask() implements CustomPacketPayload {
		public static final Type<ToggleMask> TYPE = new Type<>(JaymosPowers.id("costume_toggle_mask"));
		public static final StreamCodec<ByteBuf, ToggleMask> CODEC = StreamCodec.unit(new ToggleMask());

		@Override
		public Type<ToggleMask> type() {
			return TYPE;
		}
	}

	/** One line of the cost: what counts for it, how many, and an item to show for it. */
	public record Need(Predicate<ItemStack> matches, int count, ItemStack icon) {
		/** How many of these the player has, in the main inventory and offhand. */
		public int have(Player player) {
			int n = 0;
			for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
				if (!stack.isEmpty() && matches.test(stack)) {
					n += stack.getCount();
				}
			}
			ItemStack offhand = player.getOffhandItem();
			if (!offhand.isEmpty() && matches.test(offhand)) {
				n += offhand.getCount();
			}
			return n;
		}
	}

	private CostumeNet() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Sew.TYPE, Sew.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(TakeOff.TYPE, TakeOff.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(ToggleMask.TYPE, ToggleMask.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ToggleMask.TYPE, (payload, ctx) -> Costume.toggleMask(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Sew.TYPE, (payload, ctx) -> sew(ctx.player(), payload.pos(), payload.design()));
		ServerPlayNetworking.registerGlobalReceiver(TakeOff.TYPE, (payload, ctx) -> {
			if (atTable(ctx.player(), payload.pos()) && Costume.takeOff(ctx.player())) {
				ctx.player().sendOverlayMessage(Component.translatable("message.jaymospowers.costume.taken_off"));
			}
		});
	}

	/** What this design costs to sew. */
	public static List<Need> cost(CostumeDesign design) {
		List<Need> out = new ArrayList<>();
		out.add(new Need(s -> s.is(ItemTags.WOOL), WOOL, new ItemStack(Items.WOOL.pick(DyeColor.WHITE))));
		out.add(new Need(s -> s.is(Items.LEATHER), LEATHER, new ItemStack(Items.LEATHER)));
		out.add(new Need(s -> s.is(Items.STRING), STRING, new ItemStack(Items.STRING)));
		out.add(dye(design.primary()));
		if (design.secondary() != design.primary()) {
			out.add(dye(design.secondary()));
		}
		CostumeKit kit = design.kit();
		if (!kit.followsSecondary() && kit.trimColour(design.secondary()) != design.primary() && kit.trimColour(design.secondary()) != design.secondary()) {
			out.add(dye(kit.trimColour(design.secondary())));
		}
		if (kit.pieces() > 0) {
			out.add(new Need(s -> s.is(Items.IRON_NUGGET), NUGGETS_PER_PIECE * kit.pieces(), new ItemStack(Items.IRON_NUGGET)));
		}
		return out;
	}

	private static Need dye(DyeColor colour) {
		Predicate<ItemStack> matches = s -> s.getItem() instanceof DyeItem && s.get(DataComponents.DYE) == colour;
		ItemStack icon = DYE_ICONS.computeIfAbsent(colour, c -> {
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack stack = new ItemStack(item);
				if (matches.test(stack)) {
					return stack;
				}
			}
			return ItemStack.EMPTY;
		});
		return new Need(matches, DYE_EACH, icon);
	}

	private static boolean atTable(ServerPlayer player, BlockPos pos) {
		return player.level().isLoaded(pos) && player.level().getBlockState(pos).is(Costume.TABLE)
			&& player.isWithinBlockInteractionRange(pos, REACH_BUFFER);
	}

	private static void sew(ServerPlayer player, BlockPos pos, CostumeDesign raw) {
		if (!atTable(player, pos)) {
			return;
		}
		CostumeDesign design = raw.sanitized();
		if (!CostumeSets.allowed(player, design)) {
			// (0.11.78) a power's set is only sewn by someone with that power
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.not_your_set"));
			return;
		}
		List<Need> cost = cost(design);
		if (!player.isCreative()) {
			for (Need need : cost) {
				if (need.have(player) < need.count()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.missing"));
					return;
				}
			}
			for (Need need : cost) {
				take(player, need);
			}
		}
		player.getInventory().placeItemBackInInventory(CostumeItem.of(design), Prediction.SERVER_ONLY);
		player.level().playSound(null, pos, SoundEvents.UI_LOOM_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.costume.sewn", CostumeItem.label(design)));
	}

	private static void take(ServerPlayer player, Need need) {
		int left = need.count();
		NonNullList<ItemStack> items = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < items.size() && left > 0; i++) {
			ItemStack stack = items.get(i);
			if (!stack.isEmpty() && need.matches().test(stack)) {
				int n = Math.min(left, stack.getCount());
				stack.shrink(n);
				left -= n;
			}
		}
		ItemStack offhand = player.getOffhandItem();
		if (left > 0 && !offhand.isEmpty() && need.matches().test(offhand)) {
			offhand.shrink(Math.min(left, offhand.getCount()));
		}
		player.getInventory().setChanged();
	}
}
