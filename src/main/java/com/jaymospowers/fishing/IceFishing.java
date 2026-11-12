package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Track B's leftover, ice fishing (Advanced Fishing's idea, our own code). Hold use on ice with an {@link #AUGER} and it
 * bores a hole down to the water ({@link #DRILL_TICKS}); the hole stays open {@link #OPEN_DAYS} days before it freezes
 * again. Cast at the hole, or let the hook drop onto the ice beside it, and the hook goes down the hole. Under the ice
 * fish crowd to the light and the air of an open hole: bait near one is noticed {@link #KEEN} times as often.
 */
public final class IceFishing {
	static final int DRILL_TICKS = 50;
	static final int OPEN_DAYS = 3;
	/** Bait this close to a hole is noticed more often, and a hook this close drops in. */
	static final float KEEN = 1.5F;
	static final double KEEN_RANGE = 4.0, DROP_RANGE = 1.8;

	/** Each level's open holes: where, and the game time each freezes again. Saved. */
	public static final AttachmentType<Map<BlockPos, Long>> HOLES = AttachmentRegistry.create(JaymosPowers.id("ice_holes"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING.xmap(IceFishing::unkey, IceFishing::key), Codec.LONG)));

	public static Item AUGER;

	private IceFishing() {
	}

	private static String key(BlockPos pos) {
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static BlockPos unkey(String s) {
		String[] p = s.split(",");
		return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("ice_auger"));
		AUGER = Registry.register(BuiltInRegistries.ITEM, key, new Auger(new Item.Properties().setId(key).durability(120)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(AUGER));
	}

	/** {@code pos} is an ice hole someone cut, still open. */
	public static boolean open(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Long> holes = level.getAttached(HOLES);
		if (holes == null || holes.isEmpty()) {
			return false;
		}
		Long until = holes.get(pos);
		return until != null && until > level.getGameTime();
	}

	/** Bait at {@code pos} is near an open ice hole. */
	static float keen(ServerLevel level, BlockPos pos) {
		Map<BlockPos, Long> holes = level.getAttached(HOLES);
		if (holes == null || holes.isEmpty()) {
			return 1.0F;
		}
		long now = level.getGameTime();
		for (Map.Entry<BlockPos, Long> e : holes.entrySet()) {
			if (e.getValue() > now && e.getKey().closerThan(pos, KEEN_RANGE)) {
				return KEEN;
			}
		}
		return 1.0F;
	}

	/** Bore a hole at {@code pos} (an ice block over water). */
	static boolean drill(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(BlockTags.ICE) || !level.getFluidState(pos.below()).is(FluidTags.WATER)) {
			return false;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
			24, 0.3, 0.1, 0.3, 0.1);
		level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
		level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 0.8F);
		Map<BlockPos, Long> holes = new HashMap<>(level.getAttachedOrElse(HOLES, Map.of()));
		long now = level.getGameTime();
		holes.values().removeIf(until -> until <= now);
		holes.put(pos.immutable(), now + OPEN_DAYS * 24000L);
		level.setAttached(HOLES, holes);
		return true;
	}

	/** After the hook's own tick: a hook lying on the ice beside an open hole drops into it (server side). */
	public static void tick(FishingHook hook) {
		if (!(hook.level() instanceof ServerLevel level) || hook.tickCount % 5 != 0 || !hook.onGround()) {
			return;
		}
		if (!level.getBlockState(hook.blockPosition().below()).is(BlockTags.ICE)) {
			return;
		}
		Map<BlockPos, Long> holes = level.getAttached(HOLES);
		if (holes == null || holes.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Map.Entry<BlockPos, Long> e : holes.entrySet()) {
			BlockPos hole = e.getKey();
			if (e.getValue() > now && net.minecraft.world.phys.Vec3.atCenterOf(hole).distanceToSqr(hook.position()) < DROP_RANGE * DROP_RANGE * 2
				&& level.getFluidState(hole).is(FluidTags.WATER)) {
				hook.setPos(hole.getX() + 0.5, hole.getY() + 0.5, hole.getZ() + 0.5);
				hook.setDeltaMovement(0.0, -0.1, 0.0);
				level.playSound(null, hole, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.3F, 1.2F);
				return;
			}
		}
	}

	/** The ice auger: hold use on ice to bore a fishing hole. */
	static final class Auger extends Item {
		Auger(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Player player = context.getPlayer();
			BlockState state = context.getLevel().getBlockState(context.getClickedPos());
			if (player == null || !state.is(BlockTags.ICE)) {
				return InteractionResult.PASS;
			}
			if (!context.getLevel().getFluidState(context.getClickedPos().below()).is(FluidTags.WATER)) {
				if (!context.getLevel().isClientSide()) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.no_water_under").withStyle(ChatFormatting.GRAY));
				}
				return InteractionResult.FAIL;
			}
			player.startUsingItem(context.getHand());
			return InteractionResult.CONSUME;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.BRUSH;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return DRILL_TICKS;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
			if (!(user instanceof Player player)) {
				return;
			}
			BlockHitResult hit = target(player);
			if (hit == null) {
				user.releaseUsingItem();
				return;
			}
			if (ticksRemaining % 8 == 0) {
				level.playSound(player, hit.getBlockPos(), SoundEvents.GLASS_HIT, SoundSource.BLOCKS, 0.6F, 0.7F);
			}
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (user instanceof Player player && level instanceof ServerLevel server) {
				BlockHitResult hit = target(player);
				if (hit != null && drill(server, hit.getBlockPos())) {
					stack.hurtAndBreak(1, user, user.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND
						? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
				}
			}
			return stack;
		}

		private static BlockHitResult target(Player player) {
			HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, EntitySelector.CAN_BE_PICKED, player.blockInteractionRange());
			if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
				&& player.level().getBlockState(block.getBlockPos()).is(BlockTags.ICE)) {
				return block;
			}
			return null;
		}
	}
}
