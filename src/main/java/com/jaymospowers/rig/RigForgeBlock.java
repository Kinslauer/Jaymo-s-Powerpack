package com.jaymospowers.rig;

import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.core.fx.MagicFx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rig Forge: where salvage becomes a suit. Over it the suit stands as a turning hologram ({@link RigForgeEntity}).
 * <ul>
 * <li>Salvage in hand, right-click: it is fed in (up to {@link RigForgeEntity#MAX_FED} points) and the hologram's
 * plates for the picked piece light as it goes. Builds spend what was fed first, then salvage carried.</li>
 * <li>Right-click with a free hand: the Forge's screen (client {@code RigForgeScreen}, in the Costume Table's style): a
 * preview of you in the suit, the piece and frame ({@link RigFrame}), the two paints, the cost and Build ({@link #set}).</li>
 * <li>A dye in hand still works: the plates' paint; with Sneak, the trim's. What is worn is repainted too, and what is
 * built next takes it. A water bucket washes both off.</li>
 * <li>A worn-down Rig piece in hand refits it ({@link #REFIT_POINTS}). Creative builds for free.</li>
 * </ul>
 */
public class RigForgeBlock extends Block implements EntityBlock {
	/** What each salvage is worth at the Forge. */
	static final int SALVAGE_POINTS = 3, SCRAP_POINTS = 1;
	/** Refitting a damaged piece to new. */
	static final int REFIT_POINTS = 2;
	/** A Rig limb's salvage and iron in the standard frame. */
	static final int LIMB_POINTS = 4, LIMB_IRON = 3;
	/** The hologram floats this high over the Forge's base (blocks), and the colour of the Forge's lights. */
	public static final double HOLOGRAM_HEIGHT = 1.9;
	static final int FX_COLOR = 0x5FE6FF;

	/** A piece's price: salvage points, then two plain materials. */
	public record Cost(int points, Item first, int firstCount, Item second, int secondCount) {
	}

	/** The standard frame's price for each piece ({@link Rig.Piece} order). */
	static Cost baseCost(Rig.Piece piece) {
		return switch (piece) {
			case HELM -> new Cost(6, Items.IRON_INGOT, 3, Items.REDSTONE, 4);
			case CORE -> new Cost(9, Items.IRON_INGOT, 4, Items.REDSTONE_BLOCK, 1);
			case ARMS -> new Cost(9, Items.IRON_INGOT, 5, Items.GUNPOWDER, 4);
			case THRUSTERS -> new Cost(6, Items.IRON_INGOT, 4, Items.BLAZE_POWDER, 2);
		};
	}

	/** A piece's price in a frame: the salvage and the iron scale; the cheap build also asks for less of the second material. */
	public static Cost cost(Rig.Piece piece, RigFrame frame) {
		Cost base = baseCost(piece);
		return new Cost(frame.points(base.points()), base.first(), frame.iron(base.firstCount()), base.second(),
			frame == RigFrame.SCRAP ? Math.max(1, base.secondCount() / 2) : base.secondCount());
	}

	/** A Rig limb's price in a frame (limb: {@link RigForgeEntity#ARM} or {@link RigForgeEntity#LEG}). */
	public static Cost limbCost(int limb, RigFrame frame) {
		boolean arm = limb == RigForgeEntity.ARM;
		Item second = arm ? Items.REDSTONE : Items.BLAZE_POWDER;
		int secondCount = arm ? 2 : 1;
		return new Cost(frame.points(LIMB_POINTS), Items.IRON_INGOT, frame.iron(LIMB_IRON), second,
			frame == RigFrame.SCRAP ? Math.max(1, secondCount / 2) : secondCount);
	}

	/** What the Forge is set to build costs. */
	static Cost costOf(RigForgeEntity forge) {
		Rig.Piece piece = forge.piece();
		return piece != null ? cost(piece, forge.frame()) : limbCost(forge.limb(), forge.frame());
	}

	/** The salvage points the Forge's pick costs (for the hologram's lights). */
	public static int pointsFor(RigForgeEntity forge) {
		return costOf(forge).points();
	}

	public RigForgeBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new RigForgeEntity(pos, state);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			work(server, stack, pos, state, hit.getDirection());
		}
		return InteractionResult.SUCCESS;
	}

	private static void work(ServerPlayer player, ItemStack held, BlockPos pos, BlockState state, Direction face) {
		RigForgeEntity forge = RigForgeEntity.of(player.level(), pos, state);
		if (held.get(DataComponents.DYE) != null && held.getItem() instanceof DyeItem || held.is(Items.WATER_BUCKET)) {
			paint(player, held, forge, pos);
			return;
		}
		if (held.is(Rig.SALVAGE) || held.is(Rig.SCRAP)) {
			feed(player, held, forge, pos);
			return;
		}
		if (held.getItem() instanceof RigItem && held.isDamaged()) {
			if (!player.isCreative() && points(player) + forge.fed() < REFIT_POINTS) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.refit_missing", REFIT_POINTS).withStyle(ChatFormatting.RED));
				return;
			}
			if (!player.isCreative()) {
				spendPoints(player, forge, REFIT_POINTS);
			}
			held.setDamageValue(0);
			player.level().playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_forge.refit", held.getHoverName()));
			return;
		}
		if (!held.isEmpty()) {
			return;
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new RigNet.ForgeOpen(pos));
	}

	/** How far from the Forge its screen still works (blocks, squared). */
	static final double SCREEN_REACH_SQR = 64.0;

	/** From the Forge's screen: the pick, the paints (each costs one dye of that colour unless it is what the Forge has), and the build. */
	static void set(ServerPlayer player, RigNet.ForgeSet set) {
		BlockPos pos = set.pos();
		if (player.distanceToSqr(Vec3.atCenterOf(pos)) > SCREEN_REACH_SQR || !player.level().isLoaded(pos)) {
			return;
		}
		BlockState state = player.level().getBlockState(pos);
		if (!(state.getBlock() instanceof RigForgeBlock)) {
			return;
		}
		RigForgeEntity forge = RigForgeEntity.of(player.level(), pos, state);
		if (set.selection() != forge.selection() && set.selection() >= 0 && set.selection() < RigForgeEntity.SELECTIONS) {
			forge.setSelection(set.selection());
		}
		RigFrame[] frames = RigFrame.values();
		if (set.frame() >= 0 && set.frame() < frames.length && frames[set.frame()] != forge.frame()) {
			forge.setFrame(frames[set.frame()]);
		}
		for (int pass = 0; pass < 2; pass++) {
			boolean trim = pass == 1;
			int wanted = trim ? set.trim() : set.plates(), has = trim ? forge.trim() : forge.plates();
			if (wanted == RigNet.PAINT_LEAVE || wanted < RigNet.PAINT_LEAVE || wanted >= DyeColor.values().length) {
				continue;
			}
			DyeColor dye = wanted == RigNet.PAINT_WASH ? null : DyeColor.byId(wanted);
			int rgb = dye == null ? RigForgeEntity.NO_PAINT : dye.getTextureDiffuseColor() & 0xFFFFFF;
			if (rgb == has) {
				continue;
			}
			if (dye != null && !player.isCreative() && !takeDye(player, dye)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_forge.no_dye",
					Component.translatable("color.minecraft." + dye.getName())));
				continue;
			}
			forge.setPaint(trim ? forge.plates() : rgb, trim ? rgb : forge.trim());
			repaintWorn(player, trim, rgb);
			player.level().playSound(null, pos, dye == null ? SoundEvents.BUCKET_EMPTY : SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		if (set.build()) {
			build(player, forge, pos);
		}
	}

	/** One dye of {@code colour} out of the pack; false if there is none. */
	private static boolean takeDye(Player player, DyeColor colour) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.getItem() instanceof DyeItem && stack.get(DataComponents.DYE) == colour) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	/** Puts one paint ({@link RigForgeEntity#NO_PAINT}: none) on every Rig piece worn; how many took it. */
	private static int repaintWorn(Player player, boolean trim, int rgb) {
		int pieces = 0;
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			if (piece.item() == null || !worn.is(piece.item())) {
				continue;
			}
			if (trim) {
				if (rgb == RigForgeEntity.NO_PAINT) {
					worn.remove(Rig.TRIM);
				} else {
					worn.set(Rig.TRIM, rgb);
				}
			} else if (rgb == RigForgeEntity.NO_PAINT) {
				worn.remove(DataComponents.DYED_COLOR);
			} else {
				worn.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			}
			pieces++;
		}
		return pieces;
	}

	private static void build(ServerPlayer player, RigForgeEntity forge, BlockPos pos) {
		Rig.Piece piece = forge.piece();
		Cost cost = costOf(forge);
		boolean free = player.isCreative();
		if (!free && (points(player) + forge.fed() < cost.points() || count(player, cost.first()) < cost.firstCount()
			|| count(player, cost.second()) < cost.secondCount())) {
			describe(player, forge);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.missing").withStyle(ChatFormatting.RED));
			return;
		}
		if (!free) {
			spendPoints(player, forge, cost.points());
			take(player, cost.first(), cost.firstCount());
			take(player, cost.second(), cost.secondCount());
		}
		ItemStack made = new ItemStack(piece != null ? piece.item() : forge.limb() == RigForgeEntity.ARM ? Prosthetics.RIG_ARM_ITEM : Prosthetics.RIG_LEG_ITEM);
		made.set(Rig.FRAME, forge.frame().id());
		if (piece != null && forge.plates() != RigForgeEntity.NO_PAINT) {
			made.set(DataComponents.DYED_COLOR, new DyedItemColor(forge.plates()));
		}
		if (piece != null && forge.trim() != RigForgeEntity.NO_PAINT) {
			made.set(Rig.TRIM, forge.trim());
		}
		player.getInventory().placeItemBackInInventory(made, Prediction.SERVER_ONLY);
		player.level().playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.9F);
		player.level().playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
		if (player.level() instanceof ServerLevel level) {
			Vec3 above = Vec3.atBottomCenterOf(pos).add(0.0, HOLOGRAM_HEIGHT, 0.0);
			MagicFx.ring(level, above, null, 0.9F, FX_COLOR, 12);
			MagicFx.blast(level, above, 0.6F, FX_COLOR);
		}
		player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.built", made.getHoverName()).withStyle(ChatFormatting.AQUA));
	}

	/** Feeds the held salvage in, as much as fits. */
	private static void feed(ServerPlayer player, ItemStack held, RigForgeEntity forge, BlockPos pos) {
		int worth = held.is(Rig.SALVAGE) ? SALVAGE_POINTS : SCRAP_POINTS;
		int fed = forge.fed();
		int used = 0;
		while (used < held.getCount() && fed + worth <= RigForgeEntity.MAX_FED) {
			fed += worth;
			used++;
		}
		if (used == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_forge.full"));
			return;
		}
		if (!player.isCreative()) {
			held.shrink(used);
		}
		forge.setFed(fed);
		player.level().playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 0.7F, 1.3F);
		if (player.level() instanceof ServerLevel level) {
			MagicFx.burst(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, FX_COLOR, 0.5F, 6, 0.0, 0.05, 0.0);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_forge.fed", fed, costOf(forge).points()));
	}

	/**
	 * A dye on the Forge: the plates' paint (Sneak: the trim's), also put on every Rig piece you wear; a water bucket washes
	 * both off. What the Forge builds next takes the paint.
	 */
	private static void paint(ServerPlayer player, ItemStack held, RigForgeEntity forge, BlockPos pos) {
		DyeColor dye = held.get(DataComponents.DYE);
		boolean trim = player.isShiftKeyDown();
		int rgb = dye == null ? RigForgeEntity.NO_PAINT : dye.getTextureDiffuseColor() & 0xFFFFFF;
		if (dye == null) {
			forge.setPaint(RigForgeEntity.NO_PAINT, RigForgeEntity.NO_PAINT);
		} else if (trim) {
			forge.setPaint(forge.plates(), rgb);
		} else {
			forge.setPaint(rgb, forge.trim());
		}
		int pieces = 0;
		for (Rig.Piece piece : Rig.Piece.values()) {
			ItemStack worn = player.getItemBySlot(piece.slot);
			if (piece.item() == null || !worn.is(piece.item())) {
				continue;
			}
			if (dye == null) {
				worn.remove(DataComponents.DYED_COLOR);
				worn.remove(Rig.TRIM);
			} else if (trim) {
				worn.set(Rig.TRIM, rgb);
			} else {
				worn.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			}
			pieces++;
		}
		if (dye != null && !player.isCreative()) {
			held.shrink(1);
		}
		player.level().playSound(null, pos, dye == null ? SoundEvents.BUCKET_EMPTY : SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable(dye == null ? "message.jaymospowers.rig_forge.washed" : trim
			? "message.jaymospowers.rig_forge.trim_painted" : "message.jaymospowers.rig_forge.plates_painted", pieces));
	}

	private static void describe(ServerPlayer player, RigForgeEntity forge) {
		Rig.Piece piece = forge.piece();
		Cost cost = costOf(forge);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.selected",
			Component.translatable(piece != null ? "item.jaymospowers.rig_" + piece.id()
				: forge.limb() == RigForgeEntity.ARM ? "item.jaymospowers.rig_arm" : "item.jaymospowers.rig_leg").withStyle(ChatFormatting.AQUA),
			Component.translatable("rig.jaymospowers.frame." + forge.frame().id()).withStyle(ChatFormatting.AQUA),
			cost.points(), points(player) + forge.fed(),
			cost.firstCount(), new ItemStack(cost.first()).getHoverName(), cost.secondCount(), new ItemStack(cost.second()).getHoverName()));
	}

	/** Salvage points carried (main inventory). */
	public static int points(Player player) {
		return count(player, Rig.SALVAGE) * SALVAGE_POINTS + count(player, Rig.SCRAP) * SCRAP_POINTS;
	}

	public static int count(Player player, Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Spends points: what was fed into the Forge first, then carried scrap, then whole salvage (no change given). */
	private static void spendPoints(Player player, RigForgeEntity forge, int points) {
		int fedUsed = Math.min(forge.fed(), points);
		forge.setFed(forge.fed() - fedUsed);
		points -= fedUsed;
		if (points <= 0) {
			return;
		}
		int scrap = Math.min(count(player, Rig.SCRAP), points / SCRAP_POINTS);
		take(player, Rig.SCRAP, scrap);
		int left = points - scrap * SCRAP_POINTS;
		if (left > 0) {
			take(player, Rig.SALVAGE, (left + SALVAGE_POINTS - 1) / SALVAGE_POINTS);
		}
	}

	private static void take(Player player, Item item, int count) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (count <= 0) {
				return;
			}
			if (stack.is(item)) {
				int n = Math.min(count, stack.getCount());
				stack.shrink(n);
				count -= n;
			}
		}
	}
}
