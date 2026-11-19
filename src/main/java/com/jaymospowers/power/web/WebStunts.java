package com.jaymospowers.power.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Stunts pay: flips, corkscrews and long runs of swinging without touching the ground count toward the web's mastery,
 * the swung-distance tally the ascension is counted in ({@link WebAscension#addSwung}). Each trick in the air
 * ({@link WebTricks}) and each strike out of a swing ({@link WebAirStrikes}) is worth {@link #TRICK_BLOCKS} blocks of
 * swinging; a different one from those already in the chain is worth more and more ({@link #CHAIN_STEP} each, up to
 * {@link #CHAIN_MAX} times), and the same one again only a {@link #REPEAT_SHARE}. A chain runs on while the tricks keep
 * coming ({@link #CHAIN_TICKS}), ends when the feet are on the ground, and pays at most {@link #CHAIN_CAP} blocks. A run
 * in the air pays too: every {@link #RUN_MARK} blocks covered without touching down is worth {@link #RUN_BASE} blocks
 * times the marks so far, up to {@link #RUN_MAX}.
 */
public final class WebStunts {
	// ================================================================================================================
	// Tuning
	/** What a trick is worth (blocks of swinging), how much more each different one in the chain is, and the most it multiplies. */
	static final double TRICK_BLOCKS = 12.0, CHAIN_STEP = 0.5, CHAIN_MAX = 3.0;
	/** The same trick again, as a share of its worth. */
	static final double REPEAT_SHARE = 0.25;
	/** A chain ends this many ticks after its last trick, or after this many ticks on the ground. Most it pays in all. */
	static final int CHAIN_TICKS = 100, GROUND_TICKS = 6;
	static final double CHAIN_CAP = 300.0;
	/** The air run: a mark every this many blocks, paying this much times the marks so far, at most this much each. */
	static final double RUN_MARK = 80.0, RUN_BASE = 10.0, RUN_MAX = 50.0;
	/** A tick's movement longer than this is a teleport, not a run. */
	static final double MAX_STEP = 6.0;
	/** The swing counts for the run this long (ticks) after the last web, so a hop between two swings keeps it going. */
	static final int SWING_GRACE = 20;
	// ================================================================================================================

	private static final class Live {
		final List<Integer> kinds = new ArrayList<>();
		int lastKind = -1;
		long lastTrick = Long.MIN_VALUE;
		double paid;
		Vec3 lastPos;
		double run;
		int marks;
		int groundTicks;
		long lastSwung = Long.MIN_VALUE;
	}

	private static final Map<ServerPlayer, Live> LIVE = new WeakHashMap<>();

	private WebStunts() {
	}

	private static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player, p -> new Live());
	}

	/** Whether this is a trick or strike that pays: the ones in the air, not the landings. */
	private static boolean pays(int kind) {
		return kind == WebPose.FRONT_FLIP || kind == WebPose.CORKSCREW || kind == WebPose.CARTWHEEL || kind == WebPose.TWIRL
			|| kind == WebPose.GIANT || kind == WebPose.BACK_FLIP || kind >= WebPose.RISING_BLOW && kind <= WebPose.SPIN_STRIKE;
	}

	/** A trick or a strike was accepted: it counts toward the chain, and pays. */
	static void trick(ServerPlayer player, int kind) {
		if (!pays(kind) || player.onGround()) {
			return;
		}
		Live live = live(player);
		long now = player.level().getGameTime();
		if (now - live.lastTrick > CHAIN_TICKS) {
			live.kinds.clear();
			live.lastKind = -1;
			live.paid = 0.0;
		}
		live.lastTrick = now;
		boolean fresh = !live.kinds.contains(kind);
		double worth;
		if (fresh) {
			live.kinds.add(kind);
			worth = TRICK_BLOCKS * Math.min(CHAIN_MAX, 1.0 + CHAIN_STEP * (live.kinds.size() - 1));
		} else {
			worth = kind == live.lastKind ? TRICK_BLOCKS * REPEAT_SHARE : TRICK_BLOCKS;
		}
		live.lastKind = kind;
		worth = Math.min(worth, CHAIN_CAP - live.paid);
		if (worth <= 0.0) {
			return;
		}
		live.paid += worth;
		WebAscension.addSwung(player, worth);
		if (fresh && live.kinds.size() >= 3) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_stunt.chain", live.kinds.size()).withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
				0.4F, 1.2F + 0.15F * live.kinds.size());
		}
	}

	/** Every tick, for a web slinger: the run in the air, and a chain that ends on the ground. */
	static void tick(ServerPlayer player, boolean swinging) {
		Live live = live(player);
		long now = player.level().getGameTime();
		Vec3 pos = player.position();
		double step = live.lastPos == null ? 0.0 : pos.distanceTo(live.lastPos);
		live.lastPos = pos;
		if (swinging) {
			live.lastSwung = now;
		}
		if (!player.isAlive() || player.isSpectator()) {
			reset(live);
			return;
		}
		if (player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()) {
			if (++live.groundTicks >= GROUND_TICKS) {
				reset(live);
			}
			return;
		}
		live.groundTicks = 0;
		if (step > MAX_STEP || player.getAbilities().flying || player.isFallFlying()) {
			return;
		}
		if (swinging || now - live.lastSwung <= SWING_GRACE) {
			live.run += step;
			while (live.run >= (live.marks + 1) * RUN_MARK) {
				live.marks++;
				WebAscension.addSwung(player, Math.min(RUN_MAX, RUN_BASE * live.marks));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_stunt.run", (int)live.run).withStyle(ChatFormatting.GRAY));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.4F, 1.6F);
			}
		}
	}

	private static void reset(Live live) {
		live.run = 0.0;
		live.marks = 0;
		live.kinds.clear();
		live.lastKind = -1;
		live.paid = 0.0;
		live.lastTrick = Long.MIN_VALUE;
	}
}
