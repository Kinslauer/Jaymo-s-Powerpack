package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Holding a foe for you, and being pinned (docs/Pawn-Plan.md row F2; after the pawns who grab a monster so you can
 * strike it in Dragon's Dogma, a commercial game: ideas only).
 * <ul>
 * <li><b>Friend</b>: a companion who guards or goes for the biggest ({@link Comrades.Leaning#GUARD},
 * {@link Comrades.Leaning#BIGGEST}), close to its foe with you near, may get it from behind and hold it still for
 * {@link #HOLD_TICKS}: it can't walk or strike while you hit it ("hold.grab").</li>
 * <li><b>Foe</b>: a raider with a mate on you may pin you ("hold.pin"); a second can join, for longer. Pinned, you
 * can't walk or jump, but you can still swing: hit one of them and they let go, or work free with jumps
 * ({@link #STRUGGLE}). Your companions go for whoever holds you ({@link Comrades}' guard leaning sees them on you).</li>
 * </ul>
 * Any blow on a holder makes it let go. Only someone no bigger than a person is held ({@link #fits}).
 */
public final class Grapples {
	/** Close enough to grab (blocks); the player is near the foe within this (blocks); a mate on the same player within this joins a pin. */
	static final double REACH = 2.2, LEADER_NEAR = 8.0, JOIN = 6.0;
	/** How often fighters are looked at (ticks); a companion's hold lasts this long; a pin this, and this more for a second holder (ticks). */
	static final int CHECK = 10, HOLD_TICKS = 80, PIN_TICKS = 60, PIN_MORE = 40;
	/** One jump while pinned takes this much off the pin (ticks). */
	static final int STRUGGLE = 12;
	/** A holder doesn't grab again for this long; someone just let go isn't grabbed again for this long (ticks). */
	static final int GAP = 400, AGAIN = 200;
	/** Chance a check turns into a grab, for a companion and for a raider. */
	static final float GRAB_CHANCE = 0.25F, PIN_CHANCE = 0.2F;
	/** No bigger than a person: wide and tall at most this (blocks). */
	static final double MAX_WIDTH = 1.0, MAX_HEIGHT = 2.3;
	/** How far behind (and to the side, for a second) a holder stands (blocks). */
	static final double BEHIND = 0.7;

	private static final Identifier HELD = JaymosKingdoms.id("held");

	private static final class Hold {
		final LivingEntity held;
		final float yaw;
		final List<Mob> holders = new ArrayList<>();
		int ticks;
		int max;
		boolean jumpWas;

		Hold(LivingEntity held, int max) {
			this.held = held;
			this.max = max;
			this.yaw = held.getYRot();
		}
	}

	private static final Map<LivingEntity, Hold> HOLDS = new IdentityHashMap<>();
	private static final Map<Mob, Hold> HOLDING = new IdentityHashMap<>();
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private Grapples() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Grapples::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HOLDS.clear();
			HOLDING.clear();
			NEXT.clear();
		});
	}

	/** {@code e} is holding someone. */
	public static boolean busy(Entity e) {
		return e instanceof Mob m && HOLDING.containsKey(m);
	}

	/** {@code e} is held. */
	public static boolean held(Entity e) {
		return e instanceof LivingEntity l && HOLDS.containsKey(l);
	}

	/** No bigger than a person, and free to be held. */
	static boolean fits(LivingEntity e) {
		return e.isAlive() && !Downed.isDowned(e) && e.getBbWidth() <= MAX_WIDTH && e.getBbHeight() <= MAX_HEIGHT && !e.isPassenger()
			&& !e.isVehicle();
	}

	private static void tick(MinecraftServer server) {
		if (!HOLDS.isEmpty()) {
			for (Hold h : new ArrayList<>(HOLDS.values())) {
				step(h);
			}
		}
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		long now = server.overworld().getGameTime();
		NEXT.values().removeIf(t -> t < now);
		for (ServerLevel level : server.getAllLevels()) {
			for (Mob m : Comrades.fighters(level)) {
				look(level, m, now);
			}
		}
	}

	/** Whether {@code m} gets hold of its foe now. */
	private static void look(ServerLevel level, Mob m, long now) {
		if (HOLDING.containsKey(m) || HOLDS.containsKey(m) || Comrades.busy(m) || NEXT.containsKey(m.getUUID()) || m.hurtTime > 0
			|| !Temperaments.humanoid(m)) {
			return;
		}
		LivingEntity t = Comrades.target(m);
		if (t == null || NEXT.containsKey(t.getUUID()) || m.distanceToSqr(t) > REACH * REACH || !fits(t)) {
			return;
		}
		String side = Comrades.side(m);
		if (side.startsWith("p:")) {
			Comrades.Leanings l = Comrades.leanings(m);
			ServerPlayer player = Comrades.playerOf(level, side);
			if (!(t instanceof Mob) || HOLDS.containsKey(t) || !l.has(Comrades.Leaning.GUARD) && !l.has(Comrades.Leaning.BIGGEST)
				|| player == null || player.distanceToSqr(t) > LEADER_NEAR * LEADER_NEAR || m.getRandom().nextFloat() >= GRAB_CHANCE) {
				return;
			}
			start(level, m, t, HOLD_TICKS);
			Callouts.say(m, "hold.grab", t, true, t.getDisplayName());
			return;
		}
		if (!(t instanceof ServerPlayer player) || player.isCreative() || player.isSpectator() || !WorldTiers.hostile(m)) {
			return;
		}
		Hold pinned = HOLDS.get(player);
		if (pinned != null) {
			if (pinned.holders.size() < 2 && m.getRandom().nextFloat() < PIN_CHANCE * 2.0F) {
				join(pinned, m);
				Callouts.say(m, "hold.pin", player, false, player.getDisplayName());
			}
			return;
		}
		boolean mate = !level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(JOIN),
			o -> o != m && o.isAlive() && !Downed.isDowned(o) && Comrades.target(o) == player && Comrades.side(o).equals(side)).isEmpty();
		if (!mate || m.getRandom().nextFloat() >= PIN_CHANCE) {
			return;
		}
		start(level, m, player, PIN_TICKS);
		Callouts.say(m, "hold.pin", player, true, player.getDisplayName());
	}

	private static void start(ServerLevel level, Mob holder, LivingEntity held, int max) {
		Hold h = new Hold(held, max);
		HOLDS.put(held, h);
		join(h, holder);
		weigh(held, true);
		if (held instanceof Mob mob) {
			mob.getNavigation().stop();
			Comrades.stand(mob);
		}
		if (held instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pinned", holder.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
		level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 0.9F, 0.7F);
		level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.HOSTILE, 0.6F, 0.6F);
	}

	private static void join(Hold h, Mob holder) {
		if (!h.holders.isEmpty()) {
			h.max += PIN_MORE;
		}
		h.holders.add(holder);
		HOLDING.put(holder, h);
		Comrades.stand(holder);
	}

	/** One tick of a hold: the held kept still, the holders kept in place behind them. */
	private static void step(Hold h) {
		LivingEntity held = h.held;
		long now = held.level().getGameTime();
		h.holders.removeIf(holder -> {
			boolean off = !holder.isAlive() || holder.isRemoved() || Downed.isDowned(holder) || holder.hurtTime > 0 || holder.level() != held.level()
				|| holder.distanceToSqr(held) > 9.0;
			if (off) {
				HOLDING.remove(holder);
				NEXT.put(holder.getUUID(), now + GAP);
			}
			return off;
		});
		if (h.holders.isEmpty() || !held.isAlive() || held.isRemoved() || Downed.isDowned(held) || ++h.ticks >= h.max) {
			free(h, now);
			return;
		}
		if (held instanceof ServerPlayer player) {
			boolean jump = player.getLastClientInput().jump();
			if (jump && !h.jumpWas) {
				h.ticks += STRUGGLE;
			}
			h.jumpWas = jump;
		} else if (held instanceof Mob mob) {
			mob.getNavigation().stop();
			Comrades.stand(mob);
		}
		Vec3 v = held.getDeltaMovement();
		held.setDeltaMovement(0.0, Math.min(0.0, v.y), 0.0);
		for (int i = 0; i < h.holders.size(); i++) {
			Mob holder = h.holders.get(i);
			float angle = (h.yaw + 180.0F + (i == 0 ? 0.0F : 70.0F)) * Mth.DEG_TO_RAD;
			Vec3 at = held.position().add(-Mth.sin(angle) * BEHIND, 0.0, Mth.cos(angle) * BEHIND);
			if (holder.level().noCollision(holder, holder.getBoundingBox().move(at.subtract(holder.position())))) {
				holder.setPos(at.x, at.y, at.z);
			}
			holder.getNavigation().stop();
			if (holder instanceof Villager v2) {
				v2.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
			Comrades.stand(holder);
			float face = (float) (Mth.atan2(held.getZ() - holder.getZ(), held.getX() - holder.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			holder.setYRot(face);
			holder.yBodyRot = face;
			holder.yHeadRot = face;
			if (h.ticks % 20 == 0) {
				holder.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			}
		}
	}

	private static void free(Hold h, long now) {
		HOLDS.remove(h.held);
		weigh(h.held, false);
		NEXT.put(h.held.getUUID(), now + AGAIN);
		for (Mob holder : h.holders) {
			HOLDING.remove(holder);
			NEXT.put(holder.getUUID(), now + GAP);
		}
		h.holders.clear();
		if (h.held.isAlive() && h.held.level() instanceof ServerLevel level) {
			level.playSound(null, h.held.getX(), h.held.getY(), h.held.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.HOSTILE, 0.5F, 1.2F);
		}
	}

	/** Held: no walking, no jumping (or that taken off again). */
	private static void weigh(LivingEntity e, boolean on) {
		for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
			AttributeInstance a = e.getAttribute(attribute);
			if (a == null) {
				continue;
			}
			if (on) {
				a.addOrUpdateTransientModifier(new AttributeModifier(HELD, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				a.removeModifier(HELD);
			}
		}
	}
}
