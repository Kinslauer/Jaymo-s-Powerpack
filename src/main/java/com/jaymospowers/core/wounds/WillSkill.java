package com.jaymospowers.core.wounds;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.183) Jaymo's Kingdoms' Will pages of the Strength tree, worked into our downing, dismemberment and powers. Andy
 * (2026-10-29): "Strength has Combat arts, it needs another tree to combat magic. we can call it Will". Kingdoms holds
 * the ranks and the rest of the tree ({@code com.jaymoskingdoms.Will}); we read them by reflection, as {@link MedicineSkill}
 * does, and without Kingdoms nothing here changes.
 * <ul>
 * <li><b>No Getting Up</b>: a downed foe within {@link #HELD_DOWN_REACH} of a player with it can't rise, by time or by
 * anyone's hands but theirs ({@link #heldDown}). Never a downed player.</li>
 * <li><b>No Mercy</b>: the weaker die instead of going down (Kingdoms decides: {@link #noMercy}).</li>
 * <li><b>Grim Hand</b>: every cut is likelier to take a part off ({@link #severScale}); <b>Last Look</b> hears of each
 * head a player takes ({@link #beheaded}).</li>
 * <li><b>Spellbreaker Blow</b>: a hushed player ({@link #HUSHED_TAG}) has their power keys silenced and whatever power
 * was running ends, as if downed.</li>
 * <li><b>Will Over Flesh</b>: above half health, another player's power moves you {@link #OVER_FLESH_PER_RANK} less per
 * rank ({@link #pushed}): the pushes made while that player's power ticks or answers a key.</li>
 * <li><b>Unflinching</b> (0.18.4): a held action (getting yourself up, a revive, Second Wind's eating) takes one more hit
 * a rank before it's broken off ({@link #holdHits}).</li>
 * </ul>
 */
public final class WillSkill {
	/** No Getting Up: downed foes this near a player with it stay down (Kingdoms makes them shiver within the same). */
	public static final double HELD_DOWN_REACH = 8.0;
	/** Will Over Flesh: per rank, this share less of another's power moving you, above this share of your health. */
	public static final double OVER_FLESH_PER_RANK = 0.3, OVER_FLESH_HEALTH = 0.5;
	/** Kingdoms' tag on a player hushed by a Spellbreaker Blow ({@code Will.HUSHED_TAG}). */
	public static final String HUSHED_TAG = "jaymoskingdoms_hushed";

	private static final @Nullable Method RANK, WEAKER, NO_MERCY, SEVER_SCALE, BEHEADED;

	static {
		Method rank = null, weaker = null, noMercy = null, severScale = null, beheaded = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				rank = Class.forName("com.jaymoskingdoms.Skills").getMethod("rank", Player.class, String.class);
				Class<?> will = Class.forName("com.jaymoskingdoms.Will");
				weaker = will.getMethod("weaker", Player.class, LivingEntity.class);
				noMercy = will.getMethod("noMercy", LivingEntity.class, DamageSource.class);
				severScale = will.getMethod("severScale", Player.class, LivingEntity.class);
				beheaded = will.getMethod("beheaded", Player.class, LivingEntity.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				// (an older Kingdoms: no Will)
			}
		}
		RANK = rank;
		WEAKER = weaker;
		NO_MERCY = noMercy;
		SEVER_SCALE = severScale;
		BEHEADED = beheaded;
	}

	/** Players whose running power has already been broken off for their hush. */
	private static final Set<UUID> BROKEN = new HashSet<>();
	/** The player whose power is acting right now (its tick, or a key it answers), or null. */
	private static @Nullable ServerPlayer actor;
	/** The player bringing a downed one round just now (their own revive isn't held back by their own presence), or null. */
	static @Nullable Player reviver;

	private WillSkill() {
	}

	static void register() {
		Powers.addSilencer(player -> player.entityTags().contains(HUSHED_TAG));
		ServerTickEvents.END_SERVER_TICK.register(WillSkill::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BROKEN.clear());
	}

	/** {@code player}'s rank in the Kingdoms perk {@code key} (0 without Kingdoms). */
	static int rank(@Nullable Entity player, String key) {
		if (RANK == null || !(player instanceof Player p)) {
			return 0;
		}
		try {
			return (int) RANK.invoke(null, p, key);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	private static void tick(MinecraftServer server) {
		// a hush breaks off whatever power was running, once
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean hushed = player.entityTags().contains(HUSHED_TAG);
			if (hushed && BROKEN.add(player.getUUID())) {
				Power power = Powers.of(player);
				if (power != null) {
					power.onDowned(player);
				}
			} else if (!hushed) {
				BROKEN.remove(player.getUUID());
			}
		}
	}

	/**
	 * No Getting Up: whether {@code body} (downed, a mob) is held down by a player with it near, someone other than whoever
	 * is bringing it round. A foe only: a monster, or one a player felled.
	 */
	static boolean heldDown(ServerLevel level, LivingEntity body, @Nullable Entity felledBy) {
		if (RANK == null || body instanceof Player) {
			return false;
		}
		for (Player player : level.players()) {
			if (player == reviver || !player.isAlive() || player.isSpectator() || Downed.isDowned(player)
				|| player.distanceToSqr(body) > HELD_DOWN_REACH * HELD_DOWN_REACH || rank(player, "no_getting_up") == 0) {
				continue;
			}
			if (body instanceof Enemy || felledBy instanceof Player) {
				return true;
			}
		}
		return false;
	}

	/** No Mercy: whether the blow {@code source} kills {@code victim} outright instead of downing it. */
	static boolean noMercy(LivingEntity victim, DamageSource source) {
		if (NO_MERCY == null || !(source.getEntity() instanceof Player)) {
			return false;
		}
		try {
			return (boolean) NO_MERCY.invoke(null, victim, source);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Grim Hand: how much more likely {@code attacker}'s cut is to take a part off {@code target} (1 for no change). */
	public static float severScale(@Nullable LivingEntity attacker, LivingEntity target) {
		if (SEVER_SCALE == null || !(attacker instanceof Player player)) {
			return 1.0F;
		}
		try {
			return (float) SEVER_SCALE.invoke(null, player, target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1.0F;
		}
	}

	/** {@code by} just took {@code target}'s head (Last Look). */
	public static void beheaded(@Nullable LivingEntity by, LivingEntity target) {
		if (BEHEADED == null || !(by instanceof Player player)) {
			return;
		}
		try {
			BEHEADED.invoke(null, player, target);
		} catch (ReflectiveOperationException | RuntimeException e) {
			// (nobody saw)
		}
	}

	/** Unflinching: the hits {@code player}'s held action takes before it's broken off, {@code base} without the perk. */
	public static int holdHits(@Nullable Entity player, int base) {
		return base + rank(player, "unflinching");
	}

	/** Whether {@code player} counts {@code other} weaker (Kingdoms' measure); false without Kingdoms. */
	public static boolean weaker(Player player, LivingEntity other) {
		if (WEAKER == null) {
			return false;
		}
		try {
			return (boolean) WEAKER.invoke(null, player, other);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	// ---- Will Over Flesh ---------------------------------------------------------------------------------------------

	/** {@code player}'s power acts (ticks, or answers a key) while {@code run} runs. */
	public static void acting(ServerPlayer player, Runnable run) {
		ServerPlayer was = actor;
		actor = player;
		try {
			run.run();
		} finally {
			actor = was;
		}
	}

	/**
	 * What {@code entity}'s motion is set to, from {@code motion} (from {@code Entity.setDeltaMovement}): another player's
	 * power moves a Will Over Flesh player less while they're above half health.
	 */
	public static Vec3 pushed(Entity entity, Vec3 motion) {
		ServerPlayer by = actor;
		if (by == null || by == entity || !(entity instanceof ServerPlayer player) || RANK == null
			|| player.getHealth() <= player.getMaxHealth() * OVER_FLESH_HEALTH) {
			return motion;
		}
		int rank = rank(player, "will_over_flesh");
		if (rank == 0) {
			return motion;
		}
		double keep = Math.max(0.0, 1.0 - OVER_FLESH_PER_RANK * rank);
		Vec3 was = player.getDeltaMovement();
		return was.add(motion.subtract(was).scale(keep));
	}
}
