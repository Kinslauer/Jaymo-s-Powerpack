package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Which parts of a mob are hurt (under {@link #HURT_BELOW} of their pool), kept on the mob for everyone to see: the
 * client draws the hurt stances (hunched, head swaying, limping, an arm hanging) from it ({@code HitReactionsClient}).
 * Players keep their own wound poses and are left out.
 * <ul>
 * <li><b>The limp:</b> a mob with a hurt leg is slower ({@link #LIMP_SLOW}, more when it is down to a quarter).</li>
 * <li><b>Hurt prey:</b> a wounded deer, gazelle or other skittish grazer ({@link #skittish}) bolts from a player within
 * {@link #FLEE_RANGE}, or from whoever hurt it, and is slowed by its limp: it can be tracked by its blood trail and
 * caught. Left alone and out of sight it settles.</li>
 * </ul>
 */
public final class MobHurtStances {
	/** A part is hurt under this share of its pool (the wound states' halfway mark), and badly hurt under the second. */
	public static final float HURT_BELOW = 0.5F, BADLY_HURT_BELOW = 0.25F;
	/** The share of its speed a hurt leg takes, and a leg down to a quarter. */
	public static final double LIMP_SLOW = 0.25, BAD_LIMP_SLOW = 0.4;
	/** A hurt grazer bolts from a player this close (blocks), and runs this far each time at this much of its speed. */
	public static final double FLEE_RANGE = 24.0;
	public static final int FLEE_DISTANCE = 18, FLEE_HEIGHT = 7;
	public static final double FLEE_SPEED = 1.5;
	/** Ticks between its looking round for something to flee, and between the part health being read again. */
	public static final int FLEE_EVERY = 10, REFRESH_EVERY = 40;
	/** Whoever hurt it counts as a threat this many ticks after. */
	public static final int REMEMBER_ATTACKER = 400;
	/** Names (in the entity's id) of the skittish grazers: deer and gazelles and their kin, from any mod. */
	private static final String[] SKITTISH = {"deer", "gazelle", "antelope", "elk", "stag", "reindeer", "caribou", "impala", "kudu", "moose"};
	private static final Identifier LIMP_ID = JaymosPowers.id("hurt_limp");

	/** The hurt parts, a bit for each {@link BodyPart} ordinal. Synced to everyone who sees the mob; not saved (it is read from the wounds on load). */
	public static final AttachmentType<Integer> HURT = AttachmentRegistry.create(JaymosPowers.id("hurt_parts"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.all()));

	/** Mobs with a hurt part, whose wounds are read again now and then (they mend) and whose prey flees. */
	private static final Set<Mob> TRACKED = Collections.newSetFromMap(new WeakHashMap<>());

	private MobHurtStances() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				refresh(mob);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
	}

	/** The bit for {@code part} in {@link #HURT}. */
	public static int bit(BodyPart part) {
		return 1 << part.ordinal();
	}

	/** The parts of {@code entity} that are hurt (a bit for each {@link BodyPart} ordinal). */
	public static int hurt(LivingEntity entity) {
		return entity.getAttachedOrElse(HURT, 0);
	}

	/** Whether any of its legs is in {@code mask}. */
	public static boolean legHurt(int mask) {
		return (mask & (bit(BodyPart.LEFT_LEG) | bit(BodyPart.RIGHT_LEG) | bit(BodyPart.LEFT_FOOT) | bit(BodyPart.RIGHT_FOOT)
			| bit(BodyPart.FRONT_LEGS) | bit(BodyPart.HIND_LEGS) | bit(BodyPart.LEGS))) != 0;
	}

	/** Whether this is one of the grazers that bolt at the sight of you: a deer, gazelle, antelope and the like. */
	public static boolean skittish(LivingEntity entity) {
		if (!(entity instanceof PathfinderMob) || entity instanceof Enemy || entity.getType().getCategory() != MobCategory.CREATURE) {
			return false;
		}
		String path = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath().toLowerCase(Locale.ROOT);
		for (String name : SKITTISH) {
			if (path.contains(name)) {
				return true;
			}
		}
		return false;
	}

	/** Reads {@code entity}'s parts again: which are hurt, and the limp that goes with a hurt leg. */
	public static void refresh(LivingEntity entity) {
		if (!(entity instanceof Mob mob) || mob.level().isClientSide()) {
			return;
		}
		BodyPlan plan = BodyPlan.of(mob);
		int mask = 0;
		boolean badLeg = false;
		if (plan != null && plan != BodyPlan.SIMPLE && mob.isAlive()) {
			for (int i = 0; i < plan.size(); i++) {
				BodyPart part = plan.part(i);
				float fraction = Wounds.fraction(mob, part);
				if (fraction < HURT_BELOW) {
					mask |= bit(part);
					badLeg |= fraction < BADLY_HURT_BELOW && legHurt(bit(part));
				}
			}
		}
		if (mask != hurt(mob)) {
			if (mask == 0) {
				mob.removeAttached(HURT);
			} else {
				mob.setAttached(HURT, mask);
			}
		}
		if (mask == 0) {
			TRACKED.remove(mob);
		} else {
			TRACKED.add(mob);
		}
		limp(mob, legHurt(mask) ? (badLeg ? BAD_LIMP_SLOW : LIMP_SLOW) : 0.0);
	}

	private static void limp(Mob mob, double slow) {
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		AttributeModifier now = speed.getModifier(LIMP_ID);
		if (slow <= 0.0) {
			if (now != null) {
				speed.removeModifier(LIMP_ID);
			}
		} else if (now == null || now.amount() != -slow) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(LIMP_ID, -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void tick() {
		if (TRACKED.isEmpty()) {
			return;
		}
		for (Mob mob : new ArrayList<>(TRACKED)) {
			if (mob.isRemoved() || !mob.isAlive()) {
				TRACKED.remove(mob);
				continue;
			}
			if ((mob.tickCount + mob.getId()) % REFRESH_EVERY == 0) {
				refresh(mob);
			}
			if ((mob.tickCount + mob.getId()) % FLEE_EVERY == 0 && skittish(mob)) {
				flee((PathfinderMob) mob);
			}
		}
	}

	/** A hurt grazer runs from the nearest player in range, or from whoever hurt it, if it isn't already on the run. */
	private static void flee(PathfinderMob mob) {
		if (mob.isPassenger() || mob.isLeashed() || mob.isNoAi() || !mob.getNavigation().isDone()) {
			return;
		}
		Vec3 threat = null;
		Player near = mob.level().getNearestPlayer(mob, FLEE_RANGE);
		if (near != null && !near.isSpectator() && !near.isCreative()) {
			threat = near.position();
		} else {
			LivingEntity attacker = mob.getLastHurtByMob();
			if (attacker != null && attacker.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < REMEMBER_ATTACKER
				&& mob.distanceToSqr(attacker) < FLEE_RANGE * FLEE_RANGE) {
				threat = attacker.position();
			}
		}
		if (threat == null) {
			return;
		}
		Vec3 to = DefaultRandomPos.getPosAway(mob, FLEE_DISTANCE, FLEE_HEIGHT, threat);
		if (to != null) {
			mob.getNavigation().moveTo(to.x, to.y, to.z, FLEE_SPEED);
		}
	}
}
