package com.jaymospowers.tinkering;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.15.x) What the melee weapons and big tools do that isn't mining or a hit's extra damage: the kama's and scythe's
 * Harvest (mature crops are picked and replanted, by breaking or right mouse), Shears (shearing mobs, leaves and webs that
 * drop themselves), the dagger's Dual Wield (right mouse swings it from the off hand), the battlesign's Bonking, and the
 * shoves of Padded and Knockback.
 */
public final class ToolActions {
	/** Padded: the share of a hit's sideways knockback that is left. */
	static final double PADDED_KEEP = 0.25;
	/** Knockback: sideways push a level adds to a hit, scaled by the target's own resistance. */
	static final double KNOCKBACK_PUSH = 0.5;
	/** Dual Wield: the off-hand swing's reach comes from the player; its push, and its cost in damage points of durability. */
	static final double OFFHAND_PUSH = 0.4;
	/** Bonking: ticks the guard must be held up before letting go bonks, reach (blocks), the push (out, and up), damage as a share of
	 * the sign's attack, and the ticks before it can bonk again. */
	static final int BONK_CHARGE_TICKS = 12, BONK_COOLDOWN_TICKS = 40;
	static final double BONK_REACH = 3.5, BONK_PUSH = 1.6, BONK_LIFT = 0.35;
	static final float BONK_DAMAGE_SHARE = 0.5F;
	/** Shears: blocks that drop themselves when cut with a tool that has them (leaves are the {@code leaves} tag). */
	private static final Set<Block> SHEARED = Set.of(Blocks.COBWEB, Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.HANGING_ROOTS, Blocks.DEAD_BUSH,
		Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.NETHER_SPROUTS,
		Blocks.WEEPING_VINES, Blocks.WEEPING_VINES_PLANT, Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.CAVE_VINES,
		Blocks.CAVE_VINES_PLANT);

	/** When each player's off-hand dagger and battlesign can swing again (game time). */
	private static final Map<UUID, Long> OFFHAND_READY = new HashMap<>(), BONK_READY = new HashMap<>();

	private ToolActions() {
	}

	static void init() {
		// Shears: a block cut with the tool drops itself, as it would for shears
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			BlockState state = context.getOptional(LootContextParams.BLOCK_STATE);
			if (state == null || !(context.getOptional(LootContextParams.TOOL) instanceof ItemStack tool)
				|| !(tool.getItem() instanceof TinkerTool) || ToolStats.isBroken(tool)) {
				return;
			}
			ToolStats.Stats stats = ToolStats.stats(tool);
			if (stats == null || stats.level(Trait.SHEARS) <= 0 || !state.is(BlockTags.LEAVES) && !SHEARED.contains(state.getBlock())) {
				return;
			}
			Item self = state.getBlock().asItem();
			if (self != Items.AIR && drops.stream().noneMatch(drop -> drop.is(self))) {
				drops.add(new ItemStack(self));
			}
		});
	}

	// --- Harvest ---

	/** A crop that has finished growing (wheat, carrots, nether wart, cocoa...). */
	public static boolean matureCrop(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.isMaxAge(state);
		}
		if (state.getBlock() instanceof NetherWartBlock) {
			return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
		}
		return state.getBlock() instanceof CocoaBlock && state.getValue(CocoaBlock.AGE) >= CocoaBlock.MAX_AGE;
	}

	/** A crop of any age: what Harvest leaves standing until it's grown. */
	public static boolean crop(BlockState state) {
		return state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock || state.getBlock() instanceof CocoaBlock;
	}

	private static BlockState replanted(BlockState state) {
		if (state.getBlock() instanceof CropBlock crop) {
			return crop.getStateForAge(0);
		}
		return state.getBlock() instanceof NetherWartBlock ? state.setValue(NetherWartBlock.AGE, 0) : state.setValue(CocoaBlock.AGE, 0);
	}

	/**
	 * Picks a grown crop: its drops come out, one of its seeds is spent to replant it (no seed in the drops, no replant), and
	 * the tool wears one point. False if it isn't a grown crop.
	 */
	static boolean harvest(ServerLevel level, ServerPlayer player, ItemStack tool, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!matureCrop(state) || ToolStats.isBroken(tool)) {
			return false;
		}
		List<ItemStack> drops = Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool);
		Item seed = state.getCloneItemStack(level, pos, false).getItem();
		boolean replant = false;
		for (Iterator<ItemStack> it = drops.iterator(); it.hasNext() && !replant;) {
			ItemStack drop = it.next();
			if (drop.is(seed)) {
				drop.shrink(1);
				if (drop.isEmpty()) {
					it.remove();
				}
				replant = true;
			}
		}
		if (replant) {
			level.setBlock(pos, replanted(state), Block.UPDATE_ALL);
			state.spawnAfterBreak(level, pos, tool, true);
			level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
		} else {
			level.destroyBlock(pos, false);
		}
		for (ItemStack drop : drops) {
			Block.popResource(level, pos, drop);
		}
		tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		TraitEffects.afterMine(tool, level, player);
		return true;
	}

	/**
	 * Right mouse with a Harvest tool on a grown crop (or the soil under one): it and every grown crop in the tool's area are
	 * picked and replanted. Sneak to leave them.
	 */
	static InteractionResult useOn(UseOnContext context, ToolType type) {
		Player player = context.getPlayer();
		ItemStack tool = context.getItemInHand();
		Level level = context.getLevel();
		if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
			BlockPos clicked = context.getClickedPos();
			return player != null && !player.isShiftKeyDown() && !ToolStats.isBroken(tool) && (matureCrop(level.getBlockState(clicked))
				|| matureCrop(level.getBlockState(clicked.above()))) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (player.isShiftKeyDown() || ToolStats.isBroken(tool)) {
			return InteractionResult.PASS;
		}
		BlockPos base = context.getClickedPos();
		if (!matureCrop(level.getBlockState(base))) {
			base = base.above();
			if (!matureCrop(level.getBlockState(base))) {
				return InteractionResult.PASS;
			}
		}
		boolean any = harvest(serverLevel, serverPlayer, tool, base);
		for (BlockPos pos : ToolAreas.blocks(serverLevel, serverPlayer, type.aoe, base, level.getBlockState(base), Direction.UP).blocks()) {
			if (ToolStats.isBroken(tool)) {
				break;
			}
			any |= harvest(serverLevel, serverPlayer, tool, pos);
		}
		if (any) {
			serverPlayer.swing(context.getHand(), tool.getAttackAnimation(), false);
		}
		return any ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	// --- Shears ---

	/** Right mouse on a sheep (or a mooshroom, snow golem...) that's ready: shears it. */
	static InteractionResult shear(ItemStack tool, Player player, LivingEntity target, InteractionHand hand) {
		ToolStats.Stats stats = ToolStats.stats(tool);
		if (stats == null || stats.level(Trait.SHEARS) <= 0 || ToolStats.isBroken(tool) || !(target instanceof Shearable shearable)
			|| !shearable.readyForShearing()) {
			return InteractionResult.PASS;
		}
		if (player.level() instanceof ServerLevel level) {
			shearable.shear(level, SoundSource.PLAYERS, tool);
			tool.hurtAndBreak(1, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	// --- Dual Wield ---

	/**
	 * A dagger held in the off hand: right mouse swings it at what you're looking at (the usual reach), on its own cooldown of
	 * the dagger's own speed. It hits like the dagger's own attack, with its enchantments and traits, and pushes a little.
	 */
	static InteractionResult offhandSwing(Level level, Player player, ItemStack dagger) {
		ToolStats.Stats stats = ToolStats.stats(dagger);
		if (stats == null || ToolStats.isBroken(dagger)) {
			return InteractionResult.PASS;
		}
		Entity target = lookedAt(player, player.entityInteractionRange());
		if (!(target instanceof LivingEntity living)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		long now = level.getGameTime();
		if (now < OFFHAND_READY.getOrDefault(player.getUUID(), 0L)) {
			return InteractionResult.FAIL;
		}
		OFFHAND_READY.put(player.getUUID(), now + Math.max(1, Math.round(20.0F / Math.max(0.5F, stats.attackSpeed()))));
		var source = serverLevel.damageSources().playerAttack(player);
		float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) - mainHandAttack(player) + attackOf(dagger);
		damage = EnchantmentHelper.modifyDamage(serverLevel, dagger, living, source, Math.max(0.0F, damage));
		if (living.hurtServer(serverLevel, source, damage)) {
			shove(living, player.position(), OFFHAND_PUSH);
			dagger.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
			TraitEffects.afterHit(dagger, living, player);
		}
		return InteractionResult.SUCCESS;
	}

	/** The attack damage a tool's own attribute modifiers add (what it adds to the player's base 1). */
	private static float attackOf(ItemStack stack) {
		float out = 0.0F;
		for (ItemAttributeModifiers.Entry entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				out += (float) entry.modifier().amount();
			}
		}
		return out;
	}

	/** What the main hand's item adds to the player's attack right now (so the off-hand swing doesn't count it). */
	private static float mainHandAttack(Player player) {
		return attackOf(player.getMainHandItem());
	}

	// --- Bonking ---

	/** Letting go of a held-up battlesign after {@link #BONK_CHARGE_TICKS}: bonks what you're looking at away. */
	static boolean bonk(ItemStack sign, Level level, LivingEntity user, int heldTicks) {
		if (!(level instanceof ServerLevel serverLevel) || !(user instanceof Player player) || heldTicks < BONK_CHARGE_TICKS
			|| ToolStats.isBroken(sign)) {
			return false;
		}
		long now = level.getGameTime();
		if (now < BONK_READY.getOrDefault(player.getUUID(), 0L) || !(lookedAt(player, BONK_REACH) instanceof LivingEntity target)) {
			return false;
		}
		BONK_READY.put(player.getUUID(), now + BONK_COOLDOWN_TICKS);
		var source = serverLevel.damageSources().playerAttack(player);
		target.hurtServer(serverLevel, source, EnchantmentHelper.modifyDamage(serverLevel, sign, target, source,
			Math.max(1.0F, attackOf(sign) * BONK_DAMAGE_SHARE)));
		shove(target, player.position(), BONK_PUSH);
		target.setDeltaMovement(target.getDeltaMovement().add(0.0, BONK_LIFT * (1.0 - resistance(target)), 0.0));
		target.syncVelocity = true;
		level.playSound(null, target.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		sign.hurtAndBreak(2, player, user.getUsedItemHand());
		return true;
	}

	// --- shoves ---

	/** After a hit: Padded keeps most of the hit's knockback off, Knockback adds some. */
	static void afterHit(ToolStats.Stats stats, LivingEntity target, LivingEntity attacker) {
		if (stats.level(Trait.PADDED) > 0) {
			Vec3 motion = target.getDeltaMovement();
			target.setDeltaMovement(motion.x * PADDED_KEEP, motion.y, motion.z * PADDED_KEEP);
			target.syncVelocity = true;
		}
		if (stats.level(Trait.KNOCKBACK) > 0) {
			shove(target, attacker.position(), KNOCKBACK_PUSH * stats.level(Trait.KNOCKBACK));
		}
	}

	private static double resistance(LivingEntity entity) {
		return Math.min(1.0, entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
	}

	/** Pushes {@code target} sideways, away from {@code from}, a little less if it resists. */
	static void shove(LivingEntity target, Vec3 from, double strength) {
		Vec3 away = target.position().subtract(from).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() < 1.0E-4) {
			return;
		}
		Vec3 push = away.normalize().scale(strength * (1.0 - resistance(target)));
		target.setDeltaMovement(target.getDeltaMovement().add(push));
		target.syncVelocity = true;
	}

	/** The entity under the crosshair within {@code reach} blocks, or null. */
	private static Entity lookedAt(Player player, double reach) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F).scale(reach);
		AABB box = player.getBoundingBox().expandTowards(look).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look), box,
			entity -> !entity.isSpectator() && entity.isPickable() && entity != player, reach * reach);
		return hit == null ? null : hit.getEntity();
	}
}
