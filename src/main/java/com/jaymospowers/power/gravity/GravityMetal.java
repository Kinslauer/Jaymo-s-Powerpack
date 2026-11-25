package com.jaymospowers.power.gravity;

import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Iron answers first: the X-Men mod's Magnetism worked into Gravity (Andy: "16:30 is where the magnetism power is shown,
 * i'd like to work that into gravity"). Gravity is weight, and metal is the heaviest thing most hands ever hold, so a
 * Gravity user feels it apart from the rest:
 * <ul>
 * <li><b>Tear Loose</b> (sneak + right click while Lift holds a creature): the metal weapon or tool in its hands is torn
 * out and flies to your feet, and a mob scrambles after it, as after a fist parry's disarm. Nothing metal: "No metal in
 * reach".</li>
 * <li><b>Heavy metal</b>: R at a metal block tears it loose faster, with the metal touching it ({@link #metalBlock}).</li>
 * <li><b>Scrap</b>: nuggets and netherite scrap gather and fly with the rocks, and hit harder ({@link #scrap}).</li>
 * </ul>
 * It is not Polestar's lodestone pull, which drags whoever wears the iron: weight takes what is held and leaves the holder.
 */
final class GravityMetal {
	private static final String TABLE = "gravity_metal";
	private static final int TEAR = 0;

	// ===== the knobs ================================================================================================
	/** Tear Loose: the cooldown (ticks; the video's 14 seconds). */
	static final int TEAR_COOLDOWN = 280;
	/** The torn item's flight to your feet: ticks per block, never shorter or longer than these (ticks). */
	static final double TEAR_FLIGHT_PER_BLOCK = 1.2;
	static final int TEAR_FLIGHT_MIN = 6, TEAR_FLIGHT_MAX = 16;
	/** How long before anyone (you or the mob going after it) can take the torn item up (ticks). */
	static final int TEAR_PICKUP_DELAY = 10;
	/** A player's weapon lands this far past them, away from you (blocks), and nobody can take it up for this long (ticks). */
	static final double TEAR_PLAYER_FLING = 5.0;
	static final int TEAR_PLAYER_PICKUP_DELAY = 40;
	/** An item's fall per tick (vanilla), for aiming its flight. */
	static final double ITEM_GRAVITY = 0.04;
	/** Heavy metal: a struggle at a metal block goes this many times faster, and takes up to this many more metal pieces. */
	static final int METAL_RIP_SPEED = 2;
	static final int METAL_EXTRA_PIECES = 4;
	/** Scrap among the rocks: a ball or ring made mostly of it hits this many times as hard as one of stone. */
	static final float SCRAP_HEFT = 2.0F;
	// ================================================================================================================

	/** What metal tools, weapons and armour are mended with: anything mended with one of these is metal. */
	private static final Item[] INGOTS = {Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.NETHERITE_INGOT};
	/** The scrap that flies with the rocks. */
	private static final Set<Item> SCRAP = Set.of(Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_NUGGET, Items.NETHERITE_SCRAP);
	/** Metal blocks that don't sound like metal. */
	private static final Set<Block> METAL_BLOCKS = Set.of(Blocks.RAW_IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_COPPER_BLOCK, Blocks.IRON_DOOR,
		Blocks.IRON_TRAPDOOR, Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
	/** What metal blocks (vanilla's and most mods') sound like. */
	private static final Set<SoundType> METAL_SOUNDS = Set.of(SoundType.METAL, SoundType.IRON, SoundType.ANVIL, SoundType.CHAIN, SoundType.COPPER,
		SoundType.COPPER_BULB, SoundType.COPPER_GRATE, SoundType.NETHERITE_BLOCK, SoundType.LANTERN, SoundType.HEAVY_CORE);

	private GravityMetal() {
	}

	/** A metal weapon, tool or piece of armour (mended with an iron, gold, copper or netherite ingot), shears, a bucket, a flint and steel. */
	static boolean metal(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(Items.SHEARS) || stack.is(Items.BUCKET) || stack.is(Items.FLINT_AND_STEEL)) {
			return true;
		}
		Repairable repairable = stack.get(DataComponents.REPAIRABLE);
		if (repairable == null) {
			return false;
		}
		for (Item ingot : INGOTS) {
			if (repairable.isValidRepairItem(new ItemStack(ingot))) {
				return true;
			}
		}
		return false;
	}

	/** Nuggets and netherite scrap. */
	static boolean scrap(ItemStack stack) {
		return SCRAP.contains(stack.getItem());
	}

	/** How hard a ball or ring made mostly of {@code item} (an item id) hits, against one of stone. */
	static float heft(int item) {
		return SCRAP.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(item)) ? SCRAP_HEFT : 1.0F;
	}

	/** A block of metal: by its sound (iron, gold, copper, netherite, anvils, chains, rails, lanterns...), or a cauldron, a raw-ore block, an iron door. */
	static boolean metalBlock(BlockState state) {
		return METAL_SOUNDS.contains(state.getSoundType()) || METAL_BLOCKS.contains(state.getBlock()) || state.getBlock() instanceof AbstractCauldronBlock;
	}

	/**
	 * R at a metal block: the metal touching it, nearest first (not the aimed block itself), as far as {@code max} blocks,
	 * each one that can be taken.
	 */
	static List<BlockPos> touchingMetal(ServerLevel level, BlockPos aimed, int max, ServerPlayer player) {
		List<BlockPos> found = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		seen.add(aimed);
		open.add(aimed);
		while (!open.isEmpty() && found.size() < max) {
			BlockPos at = open.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = at.relative(direction).immutable();
				if (found.size() >= max || !seen.add(next) || !metalBlock(level.getBlockState(next))
					|| !com.jaymospowers.core.BlockCluster.takeableAny(level, next, player)) {
					continue;
				}
				found.add(next);
				open.add(next);
			}
		}
		return found;
	}

	/**
	 * Tear Loose: sneak + right click while Lift holds {@code target}. True if the metal in its hands was torn out (Lift
	 * lets go); false and a word why if not (the hold stays).
	 */
	static boolean tear(ServerPlayer player, LivingEntity target) {
		if (!PickKit.ready(TABLE, player, TEAR)) {
			say(player, "cooling");
			return false;
		}
		InteractionHand hand = metalHand(target);
		if (hand == null) {
			say(player, "no_metal");
			return false;
		}
		// a mob's gear that never drops (a champion's) holds; so does the grip of a player you may not harm
		if (target instanceof Player other && !player.canHarmPlayer(other) || target instanceof Mob mob
			&& mob.getDropChances().byEquipment(hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND) <= 0.0F) {
			say(player, "held_fast");
			return false;
		}
		ServerLevel level = player.level();
		ItemStack held = target.getItemInHand(hand);
		target.stopUsingItem();
		target.setItemInHand(hand, ItemStack.EMPTY);
		// a mob's flies to your feet; a player's is flung away from them (Andy, 2026-11-25), never into your hands
		Vec3 from = target.getEyePosition().subtract(0.0, 0.4, 0.0);
		Vec3 away = target.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		Vec3 to = target instanceof Player
			? target.position().add(away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(TEAR_PLAYER_FLING)).add(0.0, 0.3, 0.0)
			: player.position().add(0.0, 0.3, 0.0);
		Vec3 gap = to.subtract(from);
		int ticks = Mth.clamp((int)Math.round(gap.length() * TEAR_FLIGHT_PER_BLOCK), TEAR_FLIGHT_MIN, TEAR_FLIGHT_MAX);
		ItemEntity item = new ItemEntity(level, from.x, from.y, from.z, held);
		item.setDeltaMovement(gap.x / ticks, (gap.y + 0.5 * ITEM_GRAVITY * ticks * ticks) / ticks, gap.z / ticks);
		item.setPickUpDelay(target instanceof Player ? TEAR_PLAYER_PICKUP_DELAY : TEAR_PICKUP_DELAY);
		level.addFreshEntity(item);
		if (target instanceof Mob mob) {
			// it scrambles to get it back
			com.jaymospowers.combat.PassiveBlocks.scrambleFor(mob, item);
		}
		PickKit.spend(TABLE, player, TEAR, TEAR_COOLDOWN);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.sendParticles(MagicFx.VOID, from.x, from.y, from.z, 16, 0.2, 0.2, 0.2, 0.08);
		return true;
	}

	/** The hand {@code holder} holds metal in (the main hand first), or null. */
	private static @Nullable InteractionHand metalHand(LivingEntity holder) {
		if (metal(holder.getMainHandItem())) {
			return InteractionHand.MAIN_HAND;
		}
		return metal(holder.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
	}

	private static void say(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.gravity_metal." + key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}
}
