package com.jaymospowers.ground;

import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.Wounds;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.29) Andy, on Gemini's GregTech start: punching a tree "should deal slight hand damage". Jaymo's Kingdoms already
 * makes a bare hand useless on wood (no drops, no chop); here it also hurts: the first blow at a log with an empty main
 * hand drives a splinter into that hand's arm ({@link #SPLINTER} of a health point, through Wounds so the H screen shows
 * it on the arm), and so does every {@link #EVERY} ticks of keeping at it. Creative players and anyone holding anything
 * are spared.
 */
public final class Splinters {
	/** What one splinter takes from the punching arm (health points). */
	public static final float SPLINTER = 0.5F;
	/** While the punching goes on: another splinter every this many ticks. */
	public static final int EVERY = 30;
	/** A held punch counts as going on for this long after the last swing seen at the log (ticks). */
	static final int GRACE = 10;

	/** Who is punching which log, and when they next take a splinter. */
	private record Punch(BlockPos pos, long next) {
	}

	private static final Map<UUID, Punch> PUNCHING = new HashMap<>();

	private Splinters() {
	}

	public static void init() {
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && bare(sp) && server.getBlockState(pos).is(BlockTags.LOGS)) {
				long now = server.getServer().getTickCount();
				Punch was = PUNCHING.get(sp.getUUID());
				if (was == null || now >= was.next()) {
					splinter(sp);
					PUNCHING.put(sp.getUUID(), new Punch(pos.immutable(), now + EVERY));
				} else {
					PUNCHING.put(sp.getUUID(), new Punch(pos.immutable(), was.next()));
				}
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Splinters::tick);
	}

	private static void tick(MinecraftServer server) {
		if (PUNCHING.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		PUNCHING.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			Punch punch = entry.getValue();
			if (player == null || !bare(player) || !(player.level().getBlockState(punch.pos()).is(BlockTags.LOGS)) || !aimsAt(player, punch.pos())) {
				return true;
			}
			if (!player.isSwinging()) {
				// (swings come in every few ticks while a block is being dug; a pause this long means the hand stopped)
				return now >= punch.next() + GRACE;
			}
			if (now >= punch.next()) {
				splinter(player);
				entry.setValue(new Punch(punch.pos(), now + EVERY));
			}
			return false;
		});
	}

	/** An empty main hand, in survival. */
	private static boolean bare(Player player) {
		return !player.isCreative() && !player.isSpectator() && player.getMainHandItem().isEmpty();
	}

	/** Whether the player is still looking at {@code pos}, within reach. */
	private static boolean aimsAt(ServerPlayer player, BlockPos pos) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
	}

	private static void splinter(ServerPlayer player) {
		BodyPart arm = player.getMainArm() == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		Wounds.hurtPart(player, arm, SPLINTER, null);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.4F, 1.4F);
	}
}
