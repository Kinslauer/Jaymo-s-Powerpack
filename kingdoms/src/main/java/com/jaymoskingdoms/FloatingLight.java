package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The floating light: a small glass lantern with a spark in it. Use it and the spark leaves the glass and follows you, a
 * hand's breadth over your shoulder, lighting what you walk through; use it again and it comes home. The light is a block of
 * light that moves with the spark and fades by itself if it is not renewed (a crash cannot leave one behind).
 */
public final class FloatingLight {
	/** How often the spark moves its light (ticks), how long an unrenewed light lasts (ticks), the spark's pace (blocks a tick), and the light's brightness. */
	static final int EVERY = 4, FADES = 24, LIGHT = 13;
	static final double PACE = 0.35;
	/** Where it floats: to the side and up from the eyes (blocks). */
	static final double SIDE = 0.7, UP = 0.55, BEHIND = 0.2;
	static final String LIT = "jaymos_lit";
	public static Item ITEM;
	public static Block BLOCK;

	private record Spark(Vec3 at, BlockPos lit) {
	}

	private static final Map<UUID, Spark> SPARKS = new HashMap<>();

	private FloatingLight() {
	}

	static void register() {
		BLOCK = Gadgets.block("floating_light", LightBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.NONE).noCollision().noOcclusion().replaceable()
			.air().instabreak().pushReaction(PushReaction.POPPED).lightLevel(state -> LIGHT));
		ITEM = Gadgets.item("floating_light", Rarity.UNCOMMON, Lantern::new);
		ServerTickEvents.END_SERVER_TICK.register(FloatingLight::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> SPARKS.clear());
	}

	private static boolean lit(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(LIT, false);
	}

	static final class Lantern extends Item {
		Lantern(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			boolean on = !lit(stack);
			if (level instanceof ServerLevel server) {
				CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(LIT, on));
				server.playSound(null, player.blockPosition(), on ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.PLAYERS, 0.8F, on ? 1.5F : 1.0F);
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		public boolean isFoil(ItemStack stack) {
			return lit(stack);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
			builder.accept(Component.translatable("tooltip.jaymoskingdoms.floating_light").withStyle(ChatFormatting.GRAY));
		}
	}

	/** The light itself: nothing to see or touch, a glow that goes out unless renewed. */
	static final class LightBlock extends Block {
		LightBlock(BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected RenderShape getRenderShape(BlockState state) {
			return RenderShape.INVISIBLE;
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
			return Shapes.empty();
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			level.removeBlock(pos, false);
		}
	}

	private static boolean carries(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ITEM) && lit(stack)) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Spark old = SPARKS.get(player.getUUID());
			if (!player.isAlive() || player.isSpectator() || !carries(player)) {
				SPARKS.remove(player.getUUID());
				continue;
			}
			ServerLevel level = player.level();
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
			Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
			Vec3 want = player.getEyePosition().add(right.scale(SIDE)).add(0.0, UP, 0.0).subtract(flat.scale(BEHIND));
			Vec3 at = old == null || old.at().distanceTo(want) > 8.0 ? want : old.at().add(want.subtract(old.at()).scale(Math.min(1.0, PACE * EVERY / Math.max(0.01, old.at().distanceTo(want)))));
			BlockPos pos = BlockPos.containing(at);
			if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).is(BLOCK)) {
				pos = BlockPos.containing(player.getEyePosition().add(0.0, 0.4, 0.0));
				at = Vec3.atCenterOf(pos);
			}
			if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(BLOCK)) {
				level.setBlock(pos, BLOCK.defaultBlockState(), 3);
				level.scheduleTick(pos, BLOCK, FADES);
			}
			level.sendParticles(Fx.mote(0xFFF2B8, 0.55F), at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
			SPARKS.put(player.getUUID(), new Spark(at, pos));
		}
	}

	/** The lit things this player carries, for tests. */
	static List<UUID> following() {
		return List.copyOf(SPARKS.keySet());
	}
}
