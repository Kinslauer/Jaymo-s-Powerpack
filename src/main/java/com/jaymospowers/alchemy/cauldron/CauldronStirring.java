package com.jaymospowers.alchemy.cauldron;

import com.jaymospowers.alchemy.brewing.BrewQuality;
import com.jaymospowers.alchemy.brewing.BrewingNet;
import com.jaymospowers.alchemy.cauldron.CauldronBrewing.Brew;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.13) The server half of the stir game: who is stirring which pot, each turn judged on the stir bar, and the
 * finish. The first stick use on an unstirred brew puts the bar up; the turns after that come as packets.
 */
public final class CauldronStirring {
	/** Turns closer together than this are dropped (held use repeats every 4 ticks). */
	public static final int MIN_STROKE_GAP = 6;
	/** A stirrer who hasn't turned in this long gets the bar sent again on their next use. */
	public static final int RESEND_TICKS = 40;
	/** Further than this from the pot and someone else may take over (blocks, squared). */
	public static final double LEAVE_SQ = 10 * 10;

	/** Set by the client: a stick or shovel use on a cauldron whose stir bar is up judges the turn and sends it. */
	public static BiPredicate<Player, BlockPos> clientStir = (player, pos) -> false;

	private record Stirrer(ResourceKey<Level> dimension, BlockPos pos, long lastStroke) {
	}

	private static final Map<UUID, Stirrer> STIRRERS = new HashMap<>();

	private CauldronStirring() {
	}

	/** Called from {@link CauldronBrewing#init}. */
	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STIRRERS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> STIRRERS.clear());
	}

	/** A stick or shovel used on a water cauldron. */
	static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos, @Nullable Brew brew) {
		if (brew == null || brew.stirs() <= 0) {
			return InteractionResult.PASS;
		}
		if (!CauldronBrewing.heated(level, pos)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.cold"));
			return InteractionResult.FAIL;
		}
		long now = level.getGameTime();
		Stirrer own = STIRRERS.get(player.getUUID());
		if (own != null && at(own, level, pos) && now - own.lastStroke() < RESEND_TICKS) {
			// the turns come as packets
			return InteractionResult.SUCCESS;
		}
		for (Map.Entry<UUID, Stirrer> entry : STIRRERS.entrySet()) {
			Stirrer other = entry.getValue();
			if (!entry.getKey().equals(player.getUUID()) && at(other, level, pos) && now - other.lastStroke() < RESEND_TICKS
				&& level.getServer().getPlayerList().getPlayer(entry.getKey()) instanceof ServerPlayer rival
				&& rival.blockPosition().distSqr(pos) <= LEAVE_SQ) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.busy"));
				return InteractionResult.FAIL;
			}
		}
		if (brew.seed() == 0L) {
			brew = brew.withLegacySeed(level.getRandom().nextLong() | 1L);
			CauldronBrewing.set(level, pos, brew);
		}
		// a takeover ends every other stir at this pot
		for (Iterator<Map.Entry<UUID, Stirrer>> it = STIRRERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Stirrer> entry = it.next();
			if (!entry.getKey().equals(player.getUUID()) && at(entry.getValue(), level, pos)) {
				it.remove();
				if (level.getServer().getPlayerList().getPlayer(entry.getKey()) instanceof ServerPlayer replaced) {
					ServerPlayNetworking.send(replaced, BrewingNet.BrewBar.off(pos));
				}
			}
		}
		STIRRERS.put(player.getUUID(), new Stirrer(level.dimension(), pos, now - MIN_STROKE_GAP));
		send(player, pos, brew, -1);
		if (brew.stirs() == CauldronBrewing.STIR_TURNS) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.stir_hint"));
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean at(Stirrer stirrer, ServerLevel level, BlockPos pos) {
		return stirrer.dimension().equals(level.dimension()) && stirrer.pos().equals(pos);
	}

	/** One turn, with the arrow where the stirrer's bar showed it ({@code BrewingNet}). */
	public static void stroke(ServerPlayer player, BlockPos pos, float arrow) {
		ServerLevel level = player.level();
		Stirrer stirrer = STIRRERS.get(player.getUUID());
		if (stirrer == null || !at(stirrer, level, pos) || !Float.isFinite(arrow) || arrow < 0.0F || arrow > 100.0F) {
			return;
		}
		long now = level.getGameTime();
		if (now - stirrer.lastStroke() < MIN_STROKE_GAP) {
			return;
		}
		Brew brew = CauldronBrewing.get(level, pos);
		if (brew == null || brew.stirs() <= 0) {
			return;
		}
		if (!CauldronBrewing.heated(level, pos)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.cold"));
			send(player, pos, brew, -1);
			return;
		}
		int done = CauldronBrewing.STIR_TURNS - brew.stirs();
		int result = BrewQuality.judge(BrewQuality.STIR, arrow, brew.seed(), done, BrewQuality.bonus(player));
		brew = brew.stroke(result);
		STIRRERS.put(player.getUUID(), new Stirrer(stirrer.dimension(), pos, now));
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		switch (result) {
			case BrewQuality.PERFECT -> {
				CauldronBrewing.puff(level, pos, brew.potion(), 6);
				level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.5F, 1.6F);
			}
			case BrewQuality.GOOD -> {
				level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 4, 0.2, 0.0, 0.2, 0.0);
				level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.4F, 1.4F);
			}
			default -> {
				level.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 10, 0.3, 0.0, 0.3, 0.0);
				level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5F, 0.8F);
			}
		}
		for (ItemEntity floating : CauldronBrewing.floating(level, pos)) {
			if (brew.stirs() == 0) {
				floating.discard();
			} else {
				CauldronBrewing.swirl(floating, pos, brew.stirs());
			}
		}
		if (brew.stirs() == 0) {
			brew = brew.stirred();
			CauldronBrewing.set(level, pos, brew);
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.0F);
			CauldronBrewing.puff(level, pos, brew.potion(), 16);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.stirred", brew.potion().getHoverName(),
				brew.grade().displayName()));
			ServerPlayNetworking.send(player, BrewingNet.BrewBar.off(pos));
			STIRRERS.remove(player.getUUID());
			return;
		}
		CauldronBrewing.set(level, pos, brew);
		send(player, pos, brew, result);
	}

	private static void send(ServerPlayer player, BlockPos pos, Brew brew, int last) {
		ServerPlayNetworking.send(player, new BrewingNet.BrewBar(pos, true, BrewQuality.STIR_GAME, CauldronBrewing.STIR_TURNS,
			CauldronBrewing.STIR_TURNS - brew.stirs(), BrewQuality.bonus(player), brew.seed(), last));
	}
}
