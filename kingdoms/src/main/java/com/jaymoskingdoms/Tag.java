package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.9) A real game of tag with a village child ({@link Approaches}' "wanna play tag?"). You're it first: the child
 * runs from you, dodging away whenever you come near, and you tag them by catching up (or punching, which doesn't hurt
 * them, or right-clicking them). Then they're it: they count to three and chase you until they tag you back, and so on.
 * Nobody can tag straight back. The game ends after a while (the child is tired and warms to you by how many tags there
 * were), at nightfall, or if you wander off.
 */
final class Tag {
	/** How long a game lasts (ticks), and how often the child thinks (ticks). */
	static final int GAME_TICKS = 20 * 90, EVERY = 4;
	/** A tag lands this close (blocks); they keep running while you're within this; you've wandered off past this. */
	static final double TAG_REACH = 1.7, RUN_FROM = 9.0, QUIT_RANGE = 28.0;
	/** How far each dodge takes them (blocks), and their pace running off and chasing. */
	static final int DODGE = 9;
	static final float RUN_SPEED = 0.95F, CHASE_SPEED = 0.9F;
	/** After a tag: the child counts this long before chasing (ticks); no tagging straight back for this long. */
	static final int COUNT_TICKS = 50, GRACE_TICKS = 30;
	/** Hearts for a game, per tag, at most this many. */
	static final int HEARTS_PER_TAG = 1, MOST_HEARTS = 6;
	/** The chance each think that they giggle or taunt. */
	static final float GIGGLE = 0.05F, TAUNT = 0.02F;
	static final double SPEECH_RANGE = 16.0;
	/** Taunts there are (lang {@code talk.jaymoskingdoms.tag.taunt.N}). */
	static final int TAUNTS = 4;

	private static final class Game {
		final PathfinderMob child;
		final UUID player;
		final long until;
		/** The child is it (chasing), not the player. */
		boolean childIt;
		long since;
		int tags;

		Game(PathfinderMob child, UUID player, long now) {
			this.child = child;
			this.player = player;
			this.until = now + GAME_TICKS;
			this.since = now;
		}
	}

	/** Games under way, by the child. */
	private static final Map<UUID, Game> GAMES = new HashMap<>();

	private Tag() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Tag::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> GAMES.clear());
		// a punch or a poke on the child you're chasing is a tag (and never hurts them)
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> world.isClientSide() ? InteractionResult.PASS : touched(player, entity));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> world.isClientSide() ? InteractionResult.PASS : touched(player, entity));
	}

	/** {@code child} asked {@code player} to play, and they said yes: the player's it. */
	static void start(ServerLevel level, PathfinderMob child, ServerPlayer player) {
		for (Iterator<Game> it = GAMES.values().iterator(); it.hasNext(); ) {
			if (it.next().player.equals(player.getUUID())) {
				it.remove();
			}
		}
		GAMES.put(child.getUUID(), new Game(child, player.getUUID(), level.getGameTime()));
		run(child, player);
	}

	/** {@code mob} is playing tag just now (other work leaves it be). */
	static boolean playing(Entity mob) {
		return GAMES.containsKey(mob.getUUID());
	}

	private static InteractionResult touched(net.minecraft.world.entity.player.Player player, Entity entity) {
		Game g = GAMES.get(entity.getUUID());
		if (g == null || !g.player.equals(player.getUUID())) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel level && !g.childIt
			&& level.getGameTime() - g.since >= GRACE_TICKS && sp.distanceTo(entity) <= TAG_REACH + 1.5) {
			tagged(level, g, sp);
		}
		return InteractionResult.SUCCESS;
	}

	private static void tick(MinecraftServer server) {
		if (GAMES.isEmpty()) {
			return;
		}
		for (Iterator<Game> it = GAMES.values().iterator(); it.hasNext(); ) {
			Game g = it.next();
			if (!(g.child.level() instanceof ServerLevel level) || level.getGameTime() % EVERY != 0) {
				continue;
			}
			if (!step(level, g, server.getPlayerList().getPlayer(g.player))) {
				it.remove();
			}
		}
	}

	/** One think of the child's; false once the game's over. */
	private static boolean step(ServerLevel level, Game g, @Nullable ServerPlayer player) {
		PathfinderMob child = g.child;
		if (!child.isAlive() || child.hurtTime > 0 || Frights.afraid(child.getUUID()) || child.getTarget() != null) {
			return false;
		}
		long now = level.getGameTime();
		if (player == null || !player.isAlive() || player.level() != level || player.distanceTo(child) > QUIT_RANGE) {
			say(level, child, "talk.jaymoskingdoms.child.ignored");
			return false;
		}
		if (Homes.bedtime(level)) {
			say(level, child, "talk.jaymoskingdoms.tag.bedtime");
			return false;
		}
		if (now >= g.until) {
			int hearts = Math.min(MOST_HEARTS, Approaches.PLAY_HEARTS + g.tags * HEARTS_PER_TAG);
			McaCompat.hearts(child, player, hearts);
			say(level, child, g.tags > 0 ? "talk.jaymoskingdoms.tag.done" : "talk.jaymoskingdoms.tag.done.slow");
			child.getNavigation().stop();
			return false;
		}
		double d = child.distanceTo(player);
		if (!g.childIt) {
			if (d <= TAG_REACH && now - g.since >= GRACE_TICKS) {
				tagged(level, g, player);
			} else if (d < RUN_FROM) {
				run(child, player);
			}
			if (level.getRandom().nextFloat() < TAUNT) {
				say(level, child, "talk.jaymoskingdoms.tag.taunt." + level.getRandom().nextInt(TAUNTS));
			}
		} else if (now - g.since < COUNT_TICKS) {
			// (counting, eyes on you)
			child.getNavigation().stop();
			child.getLookControl().setLookAt(player);
			if (now - g.since + EVERY >= COUNT_TICKS) {
				say(level, child, "talk.jaymoskingdoms.child.0.yes");
			}
		} else if (d <= TAG_REACH) {
			child.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			g.childIt = false;
			g.since = now;
			g.tags++;
			say(level, child, "talk.jaymoskingdoms.tag.got_you");
			level.playSound(null, child.getX(), child.getY(), child.getZ(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.5F);
			run(child, player);
		} else {
			chase(child, player);
		}
		if (level.getRandom().nextFloat() < GIGGLE) {
			level.playSound(null, child.getX(), child.getY(), child.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.6F, 1.6F);
		}
		return true;
	}

	/** The player caught the child: now the child's it. */
	private static void tagged(ServerLevel level, Game g, ServerPlayer player) {
		g.childIt = true;
		g.since = level.getGameTime();
		g.tags++;
		g.child.getNavigation().stop();
		level.playSound(null, g.child.getX(), g.child.getY(), g.child.getZ(), SoundEvents.VILLAGER_HURT, SoundSource.NEUTRAL, 0.5F, 1.7F);
		say(level, g.child, "talk.jaymoskingdoms.tag.caught");
	}

	/** Off away from the player, to a spot it can reach (a straight line if there's none). */
	private static void run(PathfinderMob child, ServerPlayer player) {
		Vec3 to = LandRandomPos.getPosAway(child, DODGE, 4, player.position());
		if (to == null) {
			to = child.position().subtract(player.position()).normalize().scale(DODGE).add(child.position());
		}
		walk(child, BlockPos.containing(to), RUN_SPEED);
	}

	private static void chase(PathfinderMob child, ServerPlayer player) {
		if (child instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, player, CHASE_SPEED, 0);
		} else {
			child.getNavigation().moveTo(player, 1.25);
		}
	}

	private static void walk(PathfinderMob mob, BlockPos to, float speed) {
		if (mob instanceof Villager v) {
			BehaviorUtils.setWalkAndLookTargetMemories(v, to, speed, 0);
		} else {
			mob.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 1.3);
		}
	}

	private static void say(ServerLevel level, Entity child, String key) {
		Speech.say(level, child, Component.translatable(key), Speech.WHITE, SPEECH_RANGE);
	}
}
