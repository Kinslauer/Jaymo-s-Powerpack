package com.jaymospowers.core.wounds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * (0.19.20) Getting a clean rag (docs/Healing-Plan.md section 3). Andy: "allow an inefficient method of purifying rags, to
 * prevent festering, maybe involving water". A dirty rag ({@link MedicineItems#RAG}) may fester the wound it binds; a
 * clean one ({@link MedicineItems#CLEAN_RAG}) dresses the same and never does. Two ways, both slow on purpose:
 * <ul>
 * <li><b>Washing:</b> hold use with rags while standing in water or facing it (without sneaking: sneak + use still
 * treats). One rag every {@link #WASH_TICKS}; {@link #STAYS_DIRTY} of the time it comes out still dirty and is kept.</li>
 * <li><b>Boiling:</b> use rags on a water cauldron with a heat source under it (a fire, soul fire, lit campfire, magma or
 * lava). Up to {@link #RAGS_PER_LEVEL} rags per water level go in, and {@link #BOIL_TICKS} later they all come out clean,
 * always. The water is not used up. If the water or the heat goes first, the rags come out dirty again.</li>
 * </ul>
 * All on the server; a boil is kept in memory only (a stopping server hands the rags out clean).
 */
public final class RagWashing {
	/** (0.19.20) Washing: one rag every this many ticks, and the chance it comes out still dirty. */
	public static final int WASH_TICKS = 80;
	public static final float STAYS_DIRTY = 0.25F;
	/** (0.19.20) How far (blocks) you may face water to wash in it. */
	public static final double WASH_REACH = 3.0;
	/** (0.19.20) Boiling: a batch takes this many ticks, and holds this many rags per water level. */
	public static final int BOIL_TICKS = 400, RAGS_PER_LEVEL = 4;
	/** Washing is a held use this long (ticks); it ends when you let go or run out of rags. */
	public static final int USE_TICKS = 72000;
	static final int BAR = 12;

	/** A batch of rags in a cauldron. */
	private static final class Boil {
		final ResourceKey<Level> dimension;
		final BlockPos pos;
		final UUID owner;
		final int rags;
		int ticks;

		Boil(ResourceKey<Level> dimension, BlockPos pos, UUID owner, int rags) {
			this.dimension = dimension;
			this.pos = pos;
			this.owner = owner;
			this.rags = rags;
		}
	}

	/** The cauldrons with rags in them, by dimension and position. */
	private static final Map<String, Boil> BOILS = new HashMap<>();

	private RagWashing() {
	}

	static void register() {
		UseBlockCallback.EVENT.register(RagWashing::onUseBlock);
		ServerTickEvents.END_SERVER_TICK.register(RagWashing::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// (nothing is lost to a restart: they come out clean, a little early)
			for (Boil boil : new ArrayList<>(BOILS.values())) {
				ServerLevel level = server.getLevel(boil.dimension);
				if (level != null) {
					finish(level, boil, true);
				}
			}
			BOILS.clear();
		});
	}

	private static String key(ResourceKey<Level> dimension, BlockPos pos) {
		return dimension.identifier() + "@" + pos.asLong();
	}

	// ---- washing ----------------------------------------------------------------------------------------------------------

	/** Whether {@code player} stands in water or faces it (within {@link #WASH_REACH}), so a held rag washes. Same on both sides. */
	public static boolean canWash(Player player) {
		if (player.isInWater()) {
			return true;
		}
		HitResult hit = player.pick(WASH_REACH, 0.0F, true);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
			&& player.level().getFluidState(block.getBlockPos()).is(FluidTags.WATER);
	}

	/** Each tick of a held rag ({@link MedicineItems.MedicineItem#onUseTick}): the wash, one rag every {@link #WASH_TICKS}. */
	static void washTick(Level level, LivingEntity entity, ItemStack stack) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer player)) {
			return;
		}
		if (!canWash(player) || player.isShiftKeyDown() || stack.isEmpty()) {
			player.stopUsingItem();
			return;
		}
		int used = player.getTicksUsingItem();
		if (used > 0 && used % 20 == 0) {
			player.swing(player.getUsedItemHand(), net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.25F,
				1.2F + level.getRandom().nextFloat() * 0.4F);
		}
		int into = used % WASH_TICKS;
		if (into != 0) {
			if (used % 2 == 0) {
				int filled = Math.round(BAR * into / (float) WASH_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.washing_bar", "|".repeat(filled) + ".".repeat(BAR - filled))
					.withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (used == 0) {
			return;
		}
		if (level.getRandom().nextFloat() < STAYS_DIRTY) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.wash_failed").withStyle(ChatFormatting.GRAY));
		} else {
			stack.shrink(1);
			player.getInventory().placeItemBackInInventory(new ItemStack(MedicineItems.CLEAN_RAG), Prediction.SERVER_ONLY);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.washed").withStyle(ChatFormatting.GREEN));
			if (stack.isEmpty()) {
				player.stopUsingItem();
			}
		}
		if (level instanceof ServerLevel server) {
			server.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY(0.5), player.getZ(), 6, 0.3, 0.2, 0.3, 0.05);
		}
	}

	// ---- boiling ----------------------------------------------------------------------------------------------------------

	/** Whether a fire, soul fire, lit campfire, magma or lava sits under {@code cauldron}. */
	public static boolean heated(Level level, BlockPos cauldron) {
		BlockState below = level.getBlockState(cauldron.below());
		return below.is(BlockTags.FIRE) || CampfireBlock.isLitCampfire(below) || below.is(Blocks.MAGMA_BLOCK)
			|| below.getFluidState().is(FluidTags.LAVA);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (player.isSpectator() || !stack.is(MedicineItems.RAG) || !state.is(Blocks.WATER_CAULDRON) || !state.hasProperty(LayeredCauldronBlock.LEVEL)) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		String key = key(server.dimension(), pos);
		if (BOILS.containsKey(key)) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.boil_busy").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		if (!heated(level, pos)) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.boil_cold").withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		int rags = Math.min(stack.getCount(), RAGS_PER_LEVEL * state.getValue(LayeredCauldronBlock.LEVEL));
		if (!player.getAbilities().instabuild) {
			stack.shrink(rags);
		}
		BOILS.put(key, new Boil(server.dimension(), pos, serverPlayer.getUUID(), rags));
		server.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.0F);
		serverPlayer.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.boiling", rags).withStyle(ChatFormatting.GRAY));
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (BOILS.isEmpty()) {
			return;
		}
		for (Iterator<Boil> it = BOILS.values().iterator(); it.hasNext();) {
			Boil boil = it.next();
			ServerLevel level = server.getLevel(boil.dimension);
			if (level == null) {
				it.remove();
				continue;
			}
			if (!level.isLoaded(boil.pos)) {
				continue;
			}
			BlockState state = level.getBlockState(boil.pos);
			if (!state.is(Blocks.WATER_CAULDRON) || !heated(level, boil.pos)) {
				// the water or the heat is gone: the rags come out as they went in
				it.remove();
				give(level, boil, MedicineItems.RAG);
				continue;
			}
			boil.ticks++;
			if (boil.ticks % 10 == 0) {
				level.sendParticles(ParticleTypes.BUBBLE_POP, boil.pos.getX() + 0.5, boil.pos.getY() + 0.9, boil.pos.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.02);
			}
			if (boil.ticks % 40 == 0) {
				level.playSound(null, boil.pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.2F, 1.4F);
			}
			if (boil.ticks >= BOIL_TICKS) {
				it.remove();
				finish(level, boil, false);
			}
		}
	}

	/** The batch is done (or the server stopping): clean rags out of the cauldron. */
	private static void finish(ServerLevel level, Boil boil, boolean early) {
		BOILS.remove(key(boil.dimension, boil.pos));
		give(level, boil, MedicineItems.CLEAN_RAG);
		if (!early) {
			level.playSound(null, boil.pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.3F);
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(boil.owner);
			if (owner != null) {
				owner.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.boiled", boil.rags).withStyle(ChatFormatting.GREEN));
			}
		}
	}

	private static void give(ServerLevel level, Boil boil, net.minecraft.world.item.Item item) {
		int left = boil.rags;
		while (left > 0) {
			int n = Math.min(left, item.getDefaultMaxStackSize());
			Block.popResource(level, boil.pos.above(), new ItemStack(item, n));
			left -= n;
		}
	}
}
