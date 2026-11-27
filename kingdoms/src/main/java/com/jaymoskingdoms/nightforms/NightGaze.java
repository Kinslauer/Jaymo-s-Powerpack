package com.jaymoskingdoms.nightforms;

import com.jaymoskingdoms.BloodPage;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.NightTargets;
import com.jaymoskingdoms.Saves;
import com.jaymoskingdoms.Servants;
import com.jaymoskingdoms.Will;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The stare. Once a vampire player has fixed their eyes (the stare on the skill bar, {@link NightForms}; it lasts {@link #ARM_TICKS}), holding
 * the gaze on a weak-willed person ({@link Will#weaker}: a villager, a folk, an illager, a skeleton or a zombie weaker than them)
 * for {@link #NEEDED} ticks makes it walk to them, and it stands by them while the gaze holds. Each second of it costs {@link #COST} mB
 * of blood. It ends when the gaze breaks, the gauge runs dry, or after {@link #HOLD_MAX} ticks. A tamed horse or wolf is held as easily
 * as a villager (it has no will to weigh), and a drink from one held ({@link #holds}) binds a servant.
 */
public final class NightGaze {
	// ---- tuning ----
	/** How long the fixed eyes last (ticks), how far they reach (blocks), how long the gaze must hold (ticks), and the longest one person is held (ticks). */
	static final int ARM_TICKS = 600, NEEDED = 24, HOLD_MAX = 300;
	static final double RANGE = 14.0, STOP_AT = 1.8;
	/** How long the gaze may wander before it is broken (ticks). */
	static final int LOOSE_TICKS = 10;
	/** The blood a second of it costs (mB). */
	static final int COST = 100;

	/**
	 * A longer hold (Night's Blood, patch 4, "the stare's hold lasts much longer, and the held follow you and stand with you in a
	 * fight"): with the gift slotted, one person is held up to this long (ticks), less as their will is stronger (their health
	 * against the gazer's, up to {@link #WILL_SHORTENS} of it, and their own resistance to being charmed, {@link Saves}).
	 */
	static final int LONG_HOLD_MAX = 2400;
	static final float WILL_SHORTENS = 0.5F;
	/** How recent a blow must be for the held to take up the fight (ticks), and how far they may stray from the gazer in one (blocks). */
	static final int FIGHT_MEMORY = 100;
	static final double FIGHT_LEASH = 12.0;

	/** One fixed gaze: the person's id, the ticks it has held, and when it was last on them. */
	private static final class Fix {
		final int mob;
		int held;
		long last;

		Fix(int mob, long last) {
			this.mob = mob;
			this.last = last;
		}
	}

	private static final Map<UUID, Long> ARMED = new HashMap<>();
	private static final Map<UUID, Fix> FIXES = new HashMap<>();

	private NightGaze() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NightGaze::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ARMED.clear();
			FIXES.clear();
		});
	}

	/** {@code player} fixes their eyes. */
	static void arm(ServerPlayer player) {
		ARMED.put(player.getUUID(), player.level().getGameTime() + ARM_TICKS);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightform.stare").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	/** Whether {@code player} has their eyes fixed. */
	static boolean armed(ServerPlayer player) {
		return ARMED.containsKey(player.getUUID());
	}

	/**
	 * Whether {@code player}'s gaze holds {@code target} now: their eyes are fixed and have been on it long enough that it walks
	 * to them ({@link #NEEDED}). A drink from one so held is the calm drink that binds a servant ({@link com.jaymoskingdoms.Servants}).
	 */
	public static boolean holds(ServerPlayer player, net.minecraft.world.entity.Entity target) {
		Fix fix = FIXES.get(player.getUUID());
		return fix != null && fix.mob == target.getId() && fix.held >= NEEDED && ARMED.containsKey(player.getUUID());
	}

	/** Their eyes soften, and whoever they held is let go. */
	static void disarm(UUID id, @Nullable MinecraftServer server) {
		ARMED.remove(id);
		Fix fix = FIXES.remove(id);
		if (fix != null && server != null) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && p.level().getEntity(fix.mob) instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
	}

	/** A person: not a beast. */
	private static boolean humanoid(LivingEntity e) {
		return e instanceof AbstractVillager || e instanceof Folk || e instanceof AbstractIllager || e instanceof AbstractSkeleton || e instanceof Zombie;
	}

	/** A tamed horse or wolf: it has no will to hold against the gaze ({@link com.jaymoskingdoms.Servants#beast}). */
	private static boolean willing(LivingEntity e) {
		return com.jaymoskingdoms.Servants.willing(e);
	}

	private static void tick(MinecraftServer server) {
		if (ARMED.isEmpty() || server.getTickCount() % 2 != 0) {
			return;
		}
		for (Map.Entry<UUID, Long> e : new ArrayList<>(ARMED.entrySet())) {
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !(p.level() instanceof ServerLevel level) || !p.isAlive() || level.getGameTime() > e.getValue() || NightForms.blood(p) <= 0
				|| !NightForms.vampire(p) || !NightForms.form(p).isEmpty()) {
				disarm(e.getKey(), server);
				continue;
			}
			long now = level.getGameTime();
			Mob looked = lookedAt(level, p);
			Fix fix = FIXES.get(p.getUUID());
			if (looked != null) {
				if (fix == null || fix.mob != looked.getId()) {
					if (fix != null && level.getEntity(fix.mob) instanceof Mob old) {
						old.getNavigation().stop();
					}
					fix = new Fix(looked.getId(), now);
					FIXES.put(p.getUUID(), fix);
				}
				fix.held += 2;
				fix.last = now;
			}
			if (fix == null) {
				continue;
			}
			if (looked == null && now - fix.last > LOOSE_TICKS || fix.held > holdMax(p, level.getEntity(fix.mob))) {
				if (level.getEntity(fix.mob) instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				FIXES.remove(p.getUUID());
				continue;
			}
			if (fix.held >= NEEDED && level.getEntity(fix.mob) instanceof Mob mob && mob.isAlive()) {
				walk(p, level, mob, fix);
			}
		}
	}

	/** Whether {@code p} has the longer hold learned and slotted. */
	public static boolean longHold(ServerPlayer p) {
		BloodPage.Blood b = BloodPage.of(p);
		return b != null && b.knows(BloodPage.Gift.LONG_HOLD) && b.slotted(BloodPage.Gift.LONG_HOLD);
	}

	/** The longest {@code held} is held by {@code p}: {@link #HOLD_MAX}, or with the longer hold, more, less for a strong will. */
	static int holdMax(ServerPlayer p, net.minecraft.world.entity.@Nullable Entity held) {
		if (!longHold(p) || !(held instanceof LivingEntity target)) {
			return HOLD_MAX;
		}
		float will = Mth.clamp(target.getMaxHealth() / Math.max(1.0F, p.getMaxHealth()), 0.0F, 1.0F);
		float kept = (1.0F - WILL_SHORTENS * will) * Saves.lasting(target, Saves.Kind.CHARM);
		return Math.max(HOLD_MAX, Math.round(LONG_HOLD_MAX * kept));
	}

	/** The one the gazer is fighting (the last they struck, or the last that struck them), if they are in one. */
	private static @Nullable LivingEntity fight(ServerPlayer p, Mob mob) {
		LivingEntity foe = p.getLastHurtMob();
		if (foe == null || p.tickCount - p.getLastHurtMobTimestamp() > FIGHT_MEMORY) {
			foe = p.getLastHurtByMob();
			if (foe != null && p.tickCount - p.getLastHurtByMobTimestamp() > FIGHT_MEMORY) {
				foe = null;
			}
		}
		return foe != null && foe != mob && NightTargets.target(p, foe) && !Servants.thrall(foe) ? foe : null;
	}

	/** The person walks to the gazer and stands (and with the longer hold, stands with them in a fight). */
	private static void walk(ServerPlayer p, ServerLevel level, Mob mob, Fix fix) {
		LivingEntity foe = longHold(p) && mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? fight(p, mob) : null;
		if (foe != null && mob.distanceTo(p) <= FIGHT_LEASH) {
			mob.setTarget(foe);
			if (fix.held % 20 == 0 || fix.held == NEEDED) {
				NightForms.spend(p, COST);
			}
			return;
		}
		mob.setTarget(null);
		if (mob.distanceTo(p) > STOP_AT) {
			mob.getNavigation().moveTo(p.getX(), p.getY(), p.getZ(), 1.0);
		} else {
			mob.getNavigation().stop();
		}
		mob.getLookControl().setLookAt(p, 30.0F, 30.0F);
		if (fix.held % 20 == 0 || fix.held == NEEDED) {
			NightForms.spend(p, COST);
			level.sendParticles(com.jaymoskingdoms.Fx.mote(NightForms.BLOOD, 0.5F), mob.getX(), mob.getEyeY(), mob.getZ(), 3, 0.15, 0.1, 0.15, 0.0);
		}
	}

	/** The weak-willed person under the gaze, if any. */
	private static @Nullable Mob lookedAt(ServerLevel level, ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(RANGE));
		Mob best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(eye, end).inflate(1.0), m -> m.isAlive() && (humanoid(m) && Will.weaker(p, m) || willing(m)))) {
			Optional<Vec3> hit = m.getBoundingBox().inflate(0.3).clip(eye, end);
			if (hit.isPresent()) {
				double d = hit.get().distanceToSqr(eye);
				if (d < bestDistance && p.hasLineOfSight(m)) {
					best = m;
					bestDistance = d;
				}
			}
		}
		return best;
	}
}
