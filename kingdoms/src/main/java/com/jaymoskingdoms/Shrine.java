package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * (0.7.462) The shrine: a small stone altar with candles. Hold use on it for {@link #PRAY_TICKS} (five seconds) to pray: once
 * a Minecraft day for each player, every part of the body heals a little ({@link #PRAYER_HEAL}) and festering is cleared,
 * with a soft chime and motes of light. The block gets a use every few ticks while the button is held, so the prayer counts
 * from the first use and is lost if the uses stop for {@link #BREAK_TICKS}, if you walk off, or if you turn to another block.
 * The day you last prayed is kept on the player ({@link #PRAYED}), through death.
 */
public final class Shrine extends Block {
	/** (0.7.462) How long to hold use (ticks), the longest pause between uses before the prayer is broken, how far you may stand. */
	static final int PRAY_TICKS = 100, BREAK_TICKS = 10;
	static final double REACH = 4.5;
	/** (0.7.462) The heal each part gets (health points), and the altar's glow. */
	static final float PRAYER_HEAL = 3.0F;
	static final int LIGHT = 6;
	/** (0.7.462) One Minecraft day, in ticks. */
	static final long DAY = 24000L;

	/** The day (overworld clock time / 24000) a player last prayed. Saved, kept through death. */
	static final AttachmentType<Long> PRAYED = AttachmentRegistry.create(JaymosKingdoms.id("shrine_prayed"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

	/** A prayer begun: where, when it began and when it was last held (game time). */
	private record Prayer(BlockPos pos, long began, long last) {
	}

	private static final Map<UUID, Prayer> PRAYERS = new HashMap<>();

	public Shrine(BlockBehaviour.Properties properties) {
		super(properties);
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PRAYERS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PRAYERS.clear());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		long today = Math.floorDiv(server.getOverworldClockTime(), DAY);
		Long last = sp.getAttached(PRAYED);
		if (last != null && last == today) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.quiet"));
			return InteractionResult.SUCCESS;
		}
		if (sp.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > REACH * REACH) {
			return InteractionResult.PASS;
		}
		long now = server.getGameTime();
		Prayer prayer = PRAYERS.get(sp.getUUID());
		if (prayer == null || !prayer.pos().equals(pos) || now - prayer.last() > BREAK_TICKS) {
			prayer = new Prayer(pos.immutable(), now, now);
			server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5F, 0.8F);
		}
		long held = now - prayer.began();
		if (held >= PRAY_TICKS) {
			PRAYERS.remove(sp.getUUID());
			pray(server, sp, pos, today);
			return InteractionResult.SUCCESS;
		}
		PRAYERS.put(sp.getUUID(), new Prayer(prayer.pos(), prayer.began(), now));
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.praying", (PRAY_TICKS - held + 19) / 20));
		if (held % 20 < BREAK_TICKS) {
			server.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.15, 0.1, 0.15, 0.01);
		}
		return InteractionResult.SUCCESS;
	}

	/** The prayer answered: a small heal on every part, festering cleared, a chime and motes. */
	private static void pray(ServerLevel level, ServerPlayer player, BlockPos pos, long today) {
		player.setAttached(PRAYED, today);
		if (PowersCompat.healEveryPart(player, PRAYER_HEAL) < 0.0F) {
			player.heal(PRAYER_HEAL);
		}
		PowersCompat.easeFever(player);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8F, 1.1F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 0.8, player.getZ(), 12, 0.4, 0.5, 0.4, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 10, 0.2, 0.3, 0.2, 0.02);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shrine.answered"));
	}
}
