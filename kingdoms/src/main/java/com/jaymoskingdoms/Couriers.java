package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.46) Townsfolk carry things for you, after Village Life's {@code /vgive} (remade, not copied: that mod is All Rights
 * Reserved). {@code /kingdom deliver <player>} in a realm that doesn't hold you in ill repute: the nearest villager free
 * to go takes what's in your hand and walks it to that player (anywhere within
 * {@link #RANGE} blocks), hands it over and says who it's from. If they can't reach them in time, they bring it back to
 * you; if they're killed on the way, it drops where they fall.
 */
final class Couriers {
	/** How often couriers are thought about (ticks). */
	static final int EVERY = 10;
	/** How far from you a courier is found; how far off the one it's for may be; how near is handing it over. */
	static final double FIND = 24.0, RANGE = 256.0, HAND_OVER = 2.5;
	/** The realm whose townsfolk you ask: its seat within this; you need at least this reputation with it. */
	static final double REALM_RANGE = 128.0;
	static final int LEAST_REP = 0;
	/** How long each leg may take (ticks); their pace (a brisk walk). */
	static final int LEG_TICKS = 2400;
	static final float PACE = 0.7F;
	/** Farther than this, they walk a stage of this length at a time. */
	static final double STAGE = 32.0;
	/** The courier's thanks: MCA hearts with the sender for the errand. */
	static final int HEARTS = 1;
	/** Lines (lang {@code courier.jaymoskingdoms.<kind>.N}). */
	static final int LINES = 3;
	static final double SPEECH_RANGE = 12.0;

	private static final class Errand {
		final ItemStack stack;
		final UUID from, to;
		boolean back;
		long legStarted;

		Errand(ItemStack stack, UUID from, UUID to, long now) {
			this.stack = stack;
			this.from = from;
			this.to = to;
			this.legStarted = now;
		}
	}

	private static final Map<UUID, Errand> ERRANDS = new HashMap<>();

	private Couriers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Couriers::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(Couriers::died);
		// (the server going down: everything still out goes back to whoever sent it, or drops where its courier is)
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			ServerLevel level = Factions.level(server);
			for (Map.Entry<UUID, Errand> e : ERRANDS.entrySet()) {
				Entity courier = level.getEntity(e.getKey());
				ServerPlayer sender = server.getPlayerList().getPlayer(e.getValue().from);
				if (sender != null) {
					give(sender, e.getValue().stack);
				} else if (courier != null) {
					courier.spawnAtLocation(level, e.getValue().stack);
				}
			}
			ERRANDS.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("deliver").then(Commands.argument("player", EntityArgument.player()).executes(Couriers::deliver)))));
	}

	/** {@code v} is out on an errand. */
	static boolean carrying(Villager v) {
		return ERRANDS.containsKey(v.getUUID());
	}

	private static int deliver(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer sender = c.getSource().getPlayerOrException();
		ServerPlayer to = EntityArgument.getPlayer(c, "player");
		ServerLevel level = sender.level();
		ItemStack hand = sender.getMainHandItem();
		if (hand.isEmpty()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_empty"));
			return 0;
		}
		if (to == sender || to.level() != level || to.distanceTo(sender) > RANGE) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_far"));
			return 0;
		}
		Faction f = Factions.around(level.getServer(), level, sender.blockPosition(), REALM_RANGE);
		if (f == null || !f.type().isRealm()) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_nobody"));
			return 0;
		}
		if (Reputation.get(sender, f.id()) < LEAST_REP) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_refused", f.name()));
			return 0;
		}
		Villager courier = courier(level, sender);
		if (courier == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.courier_nobody"));
			return 0;
		}
		ItemStack stack = hand.copy();
		hand.setCount(0);
		Errand e = new Errand(stack, sender.getUUID(), to.getUUID(), level.getGameTime());
		ERRANDS.put(courier.getUUID(), e);
		courier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		say(level, courier, "off");
		walk(level, courier, to);
		McaCompat.hearts(courier, sender, HEARTS);
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.courier_sent", courier.getDisplayName(), stack.getHoverName(),
			to.getDisplayName()), false);
		return 1;
	}

	/** The nearest grown villager about you who's free to go. */
	private static @Nullable Villager courier(ServerLevel level, ServerPlayer sender) {
		long t = Royals.timeOfDay(level);
		Villager best = null;
		double bestDist = Double.MAX_VALUE;
		for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(sender.blockPosition()).inflate(FIND),
			v -> v.isAlive() && !v.isBaby() && Routines.free(v, t))) {
			double d = v.distanceToSqr(sender);
			if (d < bestDist) {
				best = v;
				bestDist = d;
			}
		}
		return best;
	}

	private static void tick(MinecraftServer server) {
		if (ERRANDS.isEmpty()) {
			return;
		}
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % EVERY != 0) {
			return;
		}
		Iterator<Map.Entry<UUID, Errand>> it = ERRANDS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Errand> entry = it.next();
			Errand e = entry.getValue();
			ServerPlayer sender = server.getPlayerList().getPlayer(e.from);
			if (!(level.getEntity(entry.getKey()) instanceof Villager courier) || !courier.isAlive()) {
				// (gone where we can't follow: back to whoever sent it)
				if (sender != null) {
					give(sender, e.stack);
					sender.sendSystemMessage(Component.translatable("message.jaymoskingdoms.courier_lost", e.stack.getHoverName()).withStyle(ChatFormatting.GRAY));
				}
				it.remove();
				continue;
			}
			ServerPlayer goal = server.getPlayerList().getPlayer(e.back ? e.from : e.to);
			boolean late = now - e.legStarted > LEG_TICKS;
			if (goal == null || goal.level() != level || late || courier.distanceTo(goal) > RANGE * 1.5) {
				if (!e.back) {
					// (couldn't find them: home with it)
					e.back = true;
					e.legStarted = now;
					say(level, courier, "missed");
					continue;
				}
				// (nor the sender: they leave it where they stand)
				courier.spawnAtLocation(level, e.stack);
				it.remove();
				continue;
			}
			if (courier.distanceTo(goal) <= HAND_OVER) {
				courier.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				give(goal, e.stack);
				level.playSound(null, goal.getX(), goal.getY(), goal.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
				if (e.back) {
					say(level, courier, "returned");
				} else {
					say(level, courier, "handed");
					Component sentBy = sender != null ? sender.getDisplayName() : Component.literal("?");
					goal.sendSystemMessage(Component.translatable("message.jaymoskingdoms.courier_brought", courier.getDisplayName(), e.stack.getHoverName(), sentBy)
						.withStyle(ChatFormatting.GOLD));
					if (sender != null) {
						sender.sendSystemMessage(Component.translatable("message.jaymoskingdoms.courier_done", courier.getDisplayName(), goal.getDisplayName())
							.withStyle(ChatFormatting.GRAY));
					}
				}
				it.remove();
				continue;
			}
			if (now % 20 == 0 || courier.getNavigation().isDone()) {
				walk(level, courier, goal);
			}
		}
	}

	/** Toward {@code goal}: straight to them when near, else a stage of the way (a villager's path only reaches so far). */
	private static void walk(ServerLevel level, Villager courier, ServerPlayer goal) {
		if (courier.distanceTo(goal) <= STAGE) {
			BehaviorUtils.setWalkAndLookTargetMemories(courier, goal, PACE, 1);
			return;
		}
		Vec3 dir = goal.position().subtract(courier.position()).multiply(1, 0, 1).normalize();
		BlockPos ahead = BlockPos.containing(courier.position().add(dir.scale(STAGE)));
		ahead = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead);
		BehaviorUtils.setWalkAndLookTargetMemories(courier, Construction.standable(level, ahead, 3), PACE, 1);
	}

	/** A courier killed on the way drops what they carried. */
	private static void died(LivingEntity dead, DamageSource source) {
		Errand e = ERRANDS.remove(dead.getUUID());
		if (e != null && dead.level() instanceof ServerLevel level) {
			dead.spawnAtLocation(level, e.stack);
		}
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack) && !stack.isEmpty()) {
			player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	private static void say(ServerLevel level, Villager v, String kind) {
		Speech.say(level, v, Component.translatable("courier.jaymoskingdoms." + kind + "." + level.getRandom().nextInt(LINES)), Speech.WHITE, SPEECH_RANGE);
	}
}
