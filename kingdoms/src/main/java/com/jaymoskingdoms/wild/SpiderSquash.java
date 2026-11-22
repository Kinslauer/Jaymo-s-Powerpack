package com.jaymoskingdoms.wild;

import com.jaymoskingdoms.Downed;
import com.jaymoskingdoms.PowersCompat;
import com.jaymoskingdoms.wild.nest.BroodQueen;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Andy: "spiders need to be way easier to kill, even walking over them should squash them, a jump and land squashing the
 * bigger ones, and a single punch like i was doing should be enough otherwise." Our spiders are small ({@link SpiderMinds}
 * shrinks them), and now they die like small things do:
 * <ul>
 * <li><b>Small</b> ({@link Tier#WALK}: no taller than {@link #WALK_HEIGHT}, so a spider at its usual half size, a cave
 * spider, a spiny spider, a spiderling or a young one) is squashed by anyone who walks over it.</li>
 * <li><b>Bigger</b> ({@link Tier#JUMP}: a netcaster, a mother, a tarantula, a spider at the game's own size) is squashed
 * by a jump that lands on it (a fall of at least {@link #JUMP_FALL}). Small ones are too.</li>
 * <li>Blows ({@link #dealt}), a punch included, from a player or anyone else (a barbarian's blade, a guard's spear, an
 * arrow): a small one dies to one ({@link #SMALL_BLOWS}); Andy: "the bigger ones can take more than one hit, the special
 * kinds like the tarantula take 5": a bigger one takes at most {@link #BIG_BLOWS}, and a bigger special kind (a tarantula,
 * a netcaster, a mother) at most {@link #SPECIAL_BLOWS}. A harder blow than that share still takes its own toll.</li>
 * </ul>
 * The brood queen and anything as big ({@link #JUMP_HEIGHT}, {@link #JUMP_WIDTH}, {@link #MAX_HEALTH}) is fought as
 * before. Whoever squashes must stand on real feet: a player or one of the folk, or a beast of some size (a cow, a horse),
 * at least {@link #FOOT_HEIGHT} tall, up and not down or carried. Monsters don't squash their own side's spiders. A
 * squashed spider dies crushed (Powers' and Kitchen's tag), so Kitchen bursts it into a few loose legs and no body.
 */
public final class SpiderSquash {
	// ================================================================================================================
	// Tuning
	/** A spider this tall or shorter (blocks) is squashed underfoot; this wide or narrower too. */
	static final float WALK_HEIGHT = 0.5F, WALK_WIDTH = 1.0F;
	/** A spider this tall or shorter (blocks), this wide or narrower, and frailer than this, is squashed by a landing jump. */
	static final float JUMP_HEIGHT = 1.0F, JUMP_WIDTH = 1.5F, MAX_HEALTH = 60.0F;
	/** Blows that kill, at most: a small spider, a bigger one, a bigger special kind (tarantula, netcaster, mother). */
	static final int SMALL_BLOWS = 1, BIG_BLOWS = 2, SPECIAL_BLOWS = 5;
	/** A landing squashes from a fall this long (blocks; a jump from flat ground falls about 1.25). */
	static final double JUMP_FALL = 0.5;
	/** Walking over: the walker's middle must be over the spider (its footprint widened by this, blocks), and moving at
	 * least this fast (blocks a tick; sneaking is about 0.04). */
	static final double WALK_REACH = 0.15, WALK_SPEED = 0.02;
	/** A landing's feet may be this far below the spider's bottom or above it (blocks). */
	static final double LAND_BELOW = 0.6, LAND_ABOVE = 0.3;
	/** Who squashes: at least this tall (blocks; a player sneaking is 1.5, a sheep 1.3, a wolf 0.85). */
	static final float FOOT_HEIGHT = 1.0F;
	// ================================================================================================================

	/** Kitchen's (and Powers' Crushing's) tag for a death that crushes. */
	static final String CRUSHED_TAG = "jaymoskitchen.crushed";
	private static final boolean KITCHEN = FabricLoader.getInstance().isModLoaded("jaymoskitchen");

	/** How a spider can be squashed. */
	public enum Tier {
		NONE, WALK, JUMP
	}

	/** Every loaded spider (and tarantula) that can be squashed. */
	private static final Set<LivingEntity> SPIDERS = Collections.newSetFromMap(new WeakHashMap<>());

	private SpiderSquash() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Spider || entity instanceof Tarantula) {
				SPIDERS.add((LivingEntity) entity);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> SPIDERS.remove(entity));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (SPIDERS.isEmpty()) {
				return;
			}
			for (LivingEntity spider : new ArrayList<>(SPIDERS)) {
				if (spider.isRemoved() || !spider.isAlive()) {
					SPIDERS.remove(spider);
				} else if (spider.level() instanceof ServerLevel level && tier(spider) == Tier.WALK) {
					walkedOver(level, spider);
				}
			}
		});
	}

	/** How {@code body} can be squashed (by its size now: the scale attribute counts). */
	public static Tier tier(LivingEntity body) {
		if (!(body instanceof Spider || body instanceof Tarantula)) {
			return Tier.NONE;
		}
		float h = body.getBbHeight(), w = body.getBbWidth();
		if (h <= WALK_HEIGHT && w <= WALK_WIDTH) {
			return Tier.WALK;
		}
		return h <= JUMP_HEIGHT && w <= JUMP_WIDTH && body.getMaxHealth() < MAX_HEALTH ? Tier.JUMP : Tier.NONE;
	}

	/** Whether {@code body} is a spider small enough to squash (and to die to a few blows). */
	public static boolean squashable(LivingEntity body) {
		return tier(body) != Tier.NONE;
	}

	/**
	 * (From {@code mixin/wild/SpiderBlowMixin}) A blow someone lands on a squashable spider takes at least its share of
	 * the spider's whole health ({@link #blows}): a small one dies to it.
	 */
	public static float dealt(LivingEntity self, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof LivingEntity by) || spiderKind(by)) {
			return amount;
		}
		int blows = blows(self);
		if (blows <= 0) {
			return amount;
		}
		return Math.max(amount, blows == 1 ? lethal(self) : self.getMaxHealth() / blows + 0.01F);
	}

	/** How many blows kill {@code body} at most, or 0 if it isn't a spider that dies to blows. */
	static int blows(LivingEntity body) {
		return switch (tier(body)) {
			case NONE -> 0;
			case WALK -> SMALL_BLOWS;
			case JUMP -> body instanceof Tarantula || switch (SpiderKinds.of(body)) {
				case MOTHER, NETCASTER -> true;
				default -> false;
			} ? SPECIAL_BLOWS : BIG_BLOWS;
		};
	}

	/** (From {@code mixin/wild/SpiderLandingMixin}) {@code lander} came down from a fall of {@code fall} blocks: it squashes every spider under its feet. */
	public static void landed(LivingEntity lander, double fall) {
		if (fall < JUMP_FALL || SPIDERS.isEmpty() || !(lander.level() instanceof ServerLevel level) || !stomper(lander)) {
			return;
		}
		AABB feet = feet(lander).expandTowards(0.0, -LAND_BELOW, 0.0).expandTowards(0.0, LAND_ABOVE, 0.0);
		for (LivingEntity spider : level.getEntitiesOfClass(LivingEntity.class, feet, e -> e.isAlive() && squashable(e))) {
			squash(level, spider, lander);
		}
	}

	/** A small spider: anyone walking with their middle over it squashes it. */
	private static void walkedOver(ServerLevel level, LivingEntity spider) {
		AABB box = spider.getBoundingBox();
		for (LivingEntity walker : level.getEntitiesOfClass(LivingEntity.class, box.inflate(1.0, 0.0, 1.0).expandTowards(0.0, 0.6, 0.0),
			e -> e != spider && e.isAlive() && !e.isSpectator())) {
			if (!walker.onGround() || !stomper(walker)) {
				continue;
			}
			double feet = walker.getY();
			if (feet < box.minY - 0.1 || feet > box.maxY + 0.1
				|| walker.getX() < box.minX - WALK_REACH || walker.getX() > box.maxX + WALK_REACH
				|| walker.getZ() < box.minZ - WALK_REACH || walker.getZ() > box.maxZ + WALK_REACH) {
				continue;
			}
			// (only someone walking onto it: one standing still that a spider runs under isn't stamping)
			// (a player's own movement, as their client last sent it)
			if (walker.getKnownMovement().horizontalDistance() < WALK_SPEED) {
				continue;
			}
			squash(level, spider, walker);
			return;
		}
	}

	/** {@code spider} is squashed by {@code by}: it dies crushed (Kitchen bursts it into legs), at {@code by}'s hand. */
	static void squash(ServerLevel level, LivingEntity spider, LivingEntity by) {
		DamageSource source = by instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(by);
		spider.addTag(CRUSHED_TAG);
		spider.setInvulnerableTime(0);
		spider.hurtServer(level, source, lethal(spider));
		if (!spider.isDeadOrDying()) {
			spider.removeTag(CRUSHED_TAG);
			return;
		}
		SPIDERS.remove(spider);
		if (!KITCHEN) {
			// (Kitchen squelches as it bursts the body; without it, the squelch is ours)
			level.playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 1.0F, 0.6F);
		}
	}

	/** Enough to kill {@code body} through whatever eases a blow. */
	private static float lethal(LivingEntity body) {
		return (body.getHealth() + body.getAbsorptionAmount()) * 3.0F + 4.0F;
	}

	/** Whether {@code who} squashes spiders underfoot: on its feet, big enough, not down or carried, not a monster. */
	static boolean stomper(LivingEntity who) {
		return !spiderKind(who) && !(who instanceof Enemy) && !who.isSpectator() && who.getBbHeight() >= FOOT_HEIGHT
			&& !who.isPassenger() && (who.getPose() == Pose.STANDING || who.getPose() == Pose.CROUCHING)
			&& !(who instanceof Player player && player.getAbilities().flying)
			&& !Downed.isDowned(who) && !PowersCompat.outOfPlay(who);
	}

	/** A spider of any size, or the brood queen: they don't squash or one-shot their own. */
	private static boolean spiderKind(LivingEntity who) {
		return who instanceof Spider || who instanceof Tarantula || who instanceof BroodQueen;
	}

	/** The bottom slice of {@code who}'s box: where its feet come down. */
	private static AABB feet(LivingEntity who) {
		AABB box = who.getBoundingBox();
		return new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ);
	}
}
