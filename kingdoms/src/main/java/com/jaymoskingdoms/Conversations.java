package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Andy 2026-11-19, clip: "the npcs should stop moving and return their message from the separate cam. the convo ends
 * when it ends and only then do they resume their tasks") A talk with an MCA villager is a cutscene
 * ({@link com.jaymoskingdoms.client.DialogueCinematic}): from the moment their talk menu opens until the client says the
 * talk is over (their reply has been said and read, {@link DialogueCue.Done}), the villager stands where they are,
 * turned to you, and goes back to their work only after. MCA shuts its menu as soon as a chat, joke or story is picked,
 * which used to let them walk off mid-sentence; now the hold outlasts the menu for the reply.
 *
 * <p>As in every cutscene (standing ruling): the speaker stops to talk, and the player is held and can't be hurt while it
 * runs. It breaks off if either of them is hurt by something, walks or is carried off, or falls out of play.
 */
public final class Conversations {
	/** A talk with no word from the client in this long ends anyway (ticks): the longest reply shot, and some. */
	static final int REPLY_TICKS = 20 * 16;
	/** Farther apart than this (blocks), the talk is over. */
	static final double RANGE = 10.0;
	/** A menu opened and shut with nothing said in it ends the talk after this long (ticks: time for the menu to show). */
	static final int MENU_GRACE = 10;

	private static final Identifier HOLD = JaymosKingdoms.id("talk_hold");

	private static final class Talk {
		final Mob villager;
		final long started;
		long until;
		/** Something was said in it (a pick, a reply): the talk outlasts the menu for the reply. */
		boolean said;

		Talk(Mob villager, long started) {
			this.villager = villager;
			this.started = started;
			this.until = started + REPLY_TICKS;
		}
	}

	/** Player → the one they're talking to. */
	private static final Map<UUID, Talk> TALKS = new HashMap<>();

	private Conversations() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Conversations::tick);
		// (the player is out of harm's way while the cutscene runs)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player
			&& TALKS.containsKey(player.getUUID()) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)));
		// (the villager is hurt: they break off to see to it)
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob mob && !TALKS.isEmpty()) {
				breakOff(mob);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> end(handler.player, false));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TALKS.clear());
	}

	/**
	 * {@code player} is talking to {@code villager}: their menu opened, or ({@code said}) a line was said in it. Holds both
	 * till it's over.
	 */
	public static void begin(ServerPlayer player, Entity villager, boolean said) {
		if (!(villager instanceof Mob mob) || !mob.isAlive() || mob.level() != player.level()) {
			return;
		}
		long now = mob.level().getGameTime();
		Talk talk = TALKS.get(player.getUUID());
		if (talk != null && talk.villager == mob) {
			talk.until = Math.max(talk.until, now + REPLY_TICKS);
			talk.said |= said;
			return;
		}
		if (talk != null) {
			end(player, true);
		}
		for (Talk other : TALKS.values()) {
			if (other.villager == mob) {
				// (one talk at a time: they're already busy with someone)
				return;
			}
		}
		Talk fresh = new Talk(mob, now);
		fresh.said = said;
		TALKS.put(player.getUUID(), fresh);
		hold(mob, true);
		hold(player, true);
		still(mob, player);
	}

	/** The client says the talk with {@code villagerId} is over (the reply shot ended, or the menu was left). */
	static void done(ServerPlayer player, int villagerId) {
		Talk talk = TALKS.get(player.getUUID());
		if (talk != null && (villagerId < 0 || talk.villager.getId() == villagerId)) {
			end(player, false);
		}
	}

	private static void tick(MinecraftServer server) {
		if (TALKS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Talk>> it = TALKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Talk> e = it.next();
			Talk talk = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Mob mob = talk.villager;
			boolean over = player == null || !player.isAlive() || !mob.isAlive() || mob.isRemoved() || mob.level() != player.level()
				|| mob.distanceTo(player) > RANGE || mob.getTarget() != null || mob.isPassenger() || mob.isInWater()
				|| PowersCompat.outOfPlay(player) || PowersCompat.outOfPlay(mob);
			long now = mob.level().getGameTime();
			if (!over && !talk.said && now - talk.started > MENU_GRACE) {
				// (the menu left with nothing said in it: nothing to wait for)
				over = !inMenu(mob, player);
			}
			if (!over && now > talk.until) {
				// (still in their menu: the talk goes on as long as it's open)
				over = !inMenu(mob, player);
				if (!over) {
					talk.until = now + REPLY_TICKS;
				}
			}
			if (over) {
				it.remove();
				release(player, mob, true);
				continue;
			}
			still(mob, player);
		}
	}

	/** Whether MCA's menu on {@code mob} is open for {@code player}. */
	private static boolean inMenu(Mob mob, ServerPlayer player) {
		return McaCompat.interactingWith(mob, player);
	}

	/** Whatever talk {@code mob} is in is broken off. */
	private static void breakOff(Mob mob) {
		for (Iterator<Map.Entry<UUID, Talk>> it = TALKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Talk> e = it.next();
			if (e.getValue().villager == mob) {
				it.remove();
				release(mob.level().getServer().getPlayerList().getPlayer(e.getKey()), mob, true);
			}
		}
	}

	private static void end(ServerPlayer player, boolean tell) {
		Talk talk = TALKS.remove(player.getUUID());
		if (talk != null) {
			release(player, talk.villager, tell);
		}
	}

	/** Both let go; {@code tell}: the client's cutscene is ended from here. */
	private static void release(@Nullable ServerPlayer player, Mob mob, boolean tell) {
		hold(mob, false);
		if (player != null) {
			hold(player, false);
			if (tell) {
				DialogueCue.end(player, mob);
			}
		}
	}

	/** The villager stands where they are, turned to the player. */
	private static void still(Mob mob, ServerPlayer player) {
		mob.getNavigation().stop();
		Vec3 motion = mob.getDeltaMovement();
		mob.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);
		if (mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
			mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (mob.getBrain().checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) {
			mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
		}
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
	}

	/** Held still (no walking, no jumping) while the talk runs, or let go. */
	private static void hold(LivingEntity entity, boolean on) {
		for (AttributeInstance a : new AttributeInstance[] {entity.getAttribute(Attributes.MOVEMENT_SPEED), entity.getAttribute(Attributes.JUMP_STRENGTH)}) {
			if (a == null) {
				continue;
			}
			if (on) {
				a.addOrUpdateTransientModifier(new AttributeModifier(HOLD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				a.removeModifier(HOLD);
			}
		}
	}
}
