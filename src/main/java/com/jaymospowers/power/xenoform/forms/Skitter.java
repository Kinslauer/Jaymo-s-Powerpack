package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.wounds.Tripped;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.113, Scan E) Skitter, the eight-legged: a squat black-brown body, a round hairy abdomen, four long jointed legs
 * down each side and a cluster of red eyes. A body, not a suit like the Web power. 22 health, fists of 4. It climbs
 * walls like a spider ({@code SkitterClimbMixin}: walk into one to climb, sneak to hold still), and with jump held under a ceiling it clings there upside down and
 * crawls along: drawn upside down for everyone, its own view turned over too ({@code SkitterCeiling}).
 * (0.19.62) Standing it keeps its upright shape; prone (the stance wheel's crawl) it drops into a spider that scuttles
 * at {@link #SPIDER_PACE} times a crawl and sticks to whatever it crawls onto, up walls ({@link XenoformState#CLIMB})
 * and along ceilings with no jump held (a jump press lets go of a ceiling); standing up turns it back.
 * Scanned from a spider or cave spider, or Kingdoms' tarantula ({@code XenoDna}).
 * <ul>
 * <li>R, Web Line: a line shot to the block you look at to swing on; R again lets go (keeping the swing).</li>
 * <li>G, Web Tie: the creature you aim at is wrapped in web and knocked down.</li>
 * <li>V, Bite: a poison bite.</li>
 * <li>B, Egg Sac: {@link #BROOD_COUNT} cave spiders hatch round you and fight for you for {@link #BROOD_TICKS} ticks.</li>
 * </ul>
 */
public final class Skitter extends XenoForm {
	// ---- tuning ----
	public static final double CEILING_GRIP = 0.08, CEILING_CRAWL = 0.12;
	public static final double LINE_REACH = 32.0, LINE_PULL = 0.15, LINE_REEL = 0.985;
	public static final int LINE_COOLDOWN = 20;
	public static final double TIE_REACH = 12.0;
	public static final int TIE_TICKS = 80, TIE_COOLDOWN = 200;
	public static final double BITE_REACH = 3.0;
	public static final float BITE_DAMAGE = 4.0F;
	public static final int BITE_POISON = 100, BITE_COOLDOWN = 60;
	public static final int BROOD_COUNT = 3, BROOD_TICKS = 600, BROOD_COOLDOWN = 1200;
	public static final double BROOD_FOLLOW = 4.0, BROOD_SPEED = 1.2, BROOD_GUARD = 16.0;

	public Skitter() {
		super("skitter", 0.9F, 22.0F, 4.0, 0.06, 0.1, 0.0, Items.STRING, 0x3A2A22);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Spider;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.anchor = null;
		live.perched = false;
		live.brood = 0;
		player.removeAttached(XenoformState.CEILING);
		player.removeAttached(XenoformState.CLIMB);
		LET_GO.remove(player.getUUID());
		dismiss(player.level(), live.minions);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (live.anchor != null) {
					live.anchor = null;
					sound(player, SoundEvents.COBWEB_BREAK, 0.6F, 1.4F);
					return;
				}
				if (!ready(player, live, R)) {
					return;
				}
				HitResult hit = player.pick(LINE_REACH, 1.0F, false);
				if (hit.getType() != HitResult.Type.BLOCK) {
					return;
				}
				live.anchor = hit.getLocation();
				live.rope = Math.max(2.0, live.anchor.distanceTo(player.position().add(0.0, player.getBbHeight() * 0.7, 0.0)));
				live.perched = false;
				sound(player, SoundEvents.COBWEB_PLACE, 1.0F, 1.2F);
				cooldown(player, live, R, LINE_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity target = aimed(player, TIE_REACH, e -> true);
				if (target == null) {
					return;
				}
				if (!Tripped.bind(target, player, TIE_TICKS)) {
					target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TIE_TICKS, 5), player);
				}
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, TIE_TICKS, 1), player);
				beam(level, player.getEyePosition(), target.getBoundingBox().getCenter(), ParticleTypes.ITEM_COBWEB, 10);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 20, 0.4, 0.5, 0.4, 0.02);
				sound(player, SoundEvents.COBWEB_PLACE, 1.2F, 0.8F);
				cooldown(player, live, G, TIE_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (!ready(player, live, V)) {
					return;
				}
				LivingEntity target = aimed(player, BITE_REACH, e -> true);
				if (target == null) {
					return;
				}
				target.hurtServer(level, player.damageSources().playerAttack(player), BITE_DAMAGE);
				target.addEffect(new MobEffectInstance(MobEffects.POISON, BITE_POISON, 1), player);
				live.hurt = true;
				sound(player, SoundEvents.SPIDER_HURT, 1.0F, 0.7F);
				cooldown(player, live, V, BITE_COOLDOWN);
			}
			case ABILITY_4 -> {
				if (!ready(player, live, B)) {
					return;
				}
				dismiss(level, live.minions);
				for (int i = 0; i < BROOD_COUNT; i++) {
					double a = i / (double)BROOD_COUNT * Math.PI * 2.0;
					Vec3 at = player.position().add(Math.cos(a) * 1.2, 0.1, Math.sin(a) * 1.2);
					CaveSpider hatchling = summon(level, EntityTypes.CAVE_SPIDER, at, player.getYRot());
					if (hatchling != null) {
						live.minions.add(hatchling.getId());
					}
				}
				if (live.minions.isEmpty()) {
					return;
				}
				live.brood = BROOD_TICKS;
				burst(level, ParticleTypes.ITEM_COBWEB, player.position().add(0.0, 0.3, 0.0), 30, 0.8, 0.05);
				sound(player, SoundEvents.SPIDER_AMBIENT, 1.4F, 1.5F);
				cooldown(player, live, B, BROOD_COOLDOWN);
			}
			default -> {
			}
		}
	}

	/** (0.19.62) Prone, the spider's pace against a man's crawl (the crawl moves at about 0.3 of a walk). */
	public static final float SPIDER_PACE = 2.6F;

	/** (0.19.62) Spiders that let go of a ceiling with jump: they don't grab it again until they've landed. */
	private static final java.util.Set<java.util.UUID> LET_GO = new java.util.HashSet<>();

	/**
	 * (0.19.62) Prone (the stance wheel's crawl): a spider, not a man. Not the downed state's crawl or two broken legs',
	 * which hold it flat as a man.
	 */
	public static boolean crawling(Player player) {
		return player.getAttached(com.jaymospowers.core.Crawl.CRAWLING) != null && !com.jaymospowers.core.wounds.Downed.isDowned(player)
			&& !com.jaymospowers.core.wounds.Breaks.crawlOnly(player);
	}

	/** (0.19.62) The side of the wall it's pressed against, the one nearest where it faces; null if none. */
	private static @org.jspecify.annotations.Nullable Direction wall(ServerPlayer player) {
		AABB box = player.getBoundingBox();
		Direction best = null;
		float bestTurn = Float.MAX_VALUE;
		for (Direction side : Direction.Plane.HORIZONTAL) {
			if (player.level().noCollision(player, box.move(side.getStepX() * 0.05, 0.0, side.getStepZ() * 0.05))) {
				continue;
			}
			float turn = Math.abs(net.minecraft.util.Mth.wrapDegrees(side.toYRot() - player.getYRot()));
			if (turn < bestTurn) {
				bestTurn = turn;
				best = side;
			}
		}
		return best;
	}

	/** A solid underside right over its head. */
	public static boolean ceiling(Player player) {
		AABB box = player.getBoundingBox();
		BlockPos above = BlockPos.containing(player.getX(), box.maxY + 0.3, player.getZ());
		return player.level().getBlockState(above).isFaceSturdy(player.level(), above, Direction.DOWN);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// (0.19.62) prone, it's a spider: it keeps to any surface it crawls onto, ceilings too, with no jump held; a jump
		// press lets go of a ceiling (standing needs room it hasn't got up there), and it won't grab one again until it lands
		boolean prone = crawling(player);
		boolean pressed = player.getLastClientInput().jump();
		if (player.onGround() || !prone) {
			LET_GO.remove(player.getUUID());
		} else if (prone && live.perched && pressed) {
			LET_GO.add(player.getUUID());
		}
		boolean jump = prone ? !LET_GO.contains(player.getUUID()) : pressed;
		Direction climbing = null;
		// clinging to a ceiling: hold jump under one; let go of jump to drop
		if (live.perched) {
			if (!jump || !ceiling(player) || player.isInWater()) {
				live.perched = false;
			} else {
				// (0.20.59) the hold itself is the client's (SkitterCling), so its own movement sticks; this keeps the
				// state everyone draws
				player.resetFallDistance();
			}
		} else if (jump && !player.onGround() && !player.isInWater() && ceiling(player)) {
			live.perched = true;
			live.anchor = null;
			sound(player, SoundEvents.SPIDER_STEP, 0.8F, 1.2F);
		} else if (!player.onGround() && player.onClimbable() && !player.isInWater()) {
			// (0.20.59) on a wall: the climb itself is a spider's, a ladder on both sides ({@code SkitterClimbMixin}),
			// so the client's own movement climbs (the server pushing it up here was overruled by the client)
			climbing = prone ? wall(player) : null;
			player.resetFallDistance();
			if (player.tickCount % 5 == 0 && player.getDeltaMovement().y > 0.05) {
				sound(player, SoundEvents.SPIDER_STEP, 0.5F, 1.3F);
			}
		}
		// (0.19.62) every client draws the crawling spider on the wall while it climbs one
		Integer side = climbing != null ? climbing.get2DDataValue() : null;
		Integer was = player.getAttached(XenoformState.CLIMB);
		if (side != null && !side.equals(was)) {
			player.setAttached(XenoformState.CLIMB, side);
		} else if (side == null && was != null && (player.onGround() || live.perched || !prone || !player.horizontalCollision)) {
			player.removeAttached(XenoformState.CLIMB);
		}
		// every client draws it upside down while it clings (and its own camera turns over)
		if (live.perched != Boolean.TRUE.equals(player.getAttached(XenoformState.CEILING))) {
			if (live.perched) {
				player.setAttached(XenoformState.CEILING, true);
			} else {
				player.removeAttached(XenoformState.CEILING);
			}
		}
		if (live.anchor != null) {
			swing(player, live, level);
		}
		if (live.brood > 0) {
			brood(player, live, level);
		}
	}

	/** On the web line: held to the rope's length round the anchor, so a fall becomes a swing. */
	private static void swing(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		Vec3 anchor = live.anchor;
		Vec3 grip = player.position().add(0.0, player.getBbHeight() * 0.7, 0.0);
		if (player.onGround() && grip.distanceTo(anchor) < live.rope * 0.6 || anchor.distanceTo(grip) > LINE_REACH * 1.5) {
			live.anchor = null;
			return;
		}
		// sneak reels the line in
		if (player.isShiftKeyDown()) {
			live.rope = Math.max(2.0, live.rope * LINE_REEL);
		}
		Vec3 out = grip.subtract(anchor);
		double length = out.length();
		if (length > live.rope) {
			Vec3 dir = out.scale(1.0 / length);
			Vec3 m = player.getDeltaMovement();
			double away = m.dot(dir);
			if (away > 0.0) {
				m = m.subtract(dir.scale(away));
			}
			// back to the rope's length
			m = m.subtract(dir.scale((length - live.rope) * LINE_PULL));
			player.setDeltaMovement(m);
			player.syncVelocity = true;
			player.resetFallDistance();
		}
		if (player.tickCount % 2 == 0) {
			beam(level, grip, anchor, ParticleTypes.ITEM_COBWEB, Math.max(3, (int)(length / 1.5)));
		}
	}

	/** The hatchlings fight for it: never at a player or each other, at what it last fought (or what fought it), else with it. */
	private static void brood(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		if (--live.brood <= 0) {
			dismiss(level, live.minions);
			return;
		}
		LivingEntity foe = null;
		LivingEntity hit = player.getLastHurtMob();
		if (hit != null && hit.isAlive() && player.tickCount - player.getLastHurtMobTimestamp() < 200 && hit.distanceTo(player) < BROOD_GUARD) {
			foe = hit;
		}
		LivingEntity by = player.getLastHurtByMob();
		if (foe == null && by != null && by.isAlive() && by.distanceTo(player) < BROOD_GUARD) {
			foe = by;
		}
		if (foe instanceof Player || foe != null && (spared(player, foe) || foe.entityTags().contains(XenoformState.MINION_TAG))) {
			foe = null;
		}
		for (var it = live.minions.iterator(); it.hasNext();) {
			if (!(level.getEntity(it.next()) instanceof Mob hatchling) || !hatchling.isAlive()) {
				it.remove();
				continue;
			}
			LivingEntity target = hatchling.getTarget();
			if (target instanceof Player || target != null && (spared(player, target) || target.entityTags().contains(XenoformState.MINION_TAG))) {
				hatchling.setTarget(null);
				target = null;
			}
			if (foe != null && target != foe) {
				hatchling.setTarget(foe);
			} else if (target == null && hatchling.tickCount % 10 == 0 && hatchling.distanceToSqr(player) > BROOD_FOLLOW * BROOD_FOLLOW) {
				hatchling.getNavigation().moveTo(player, BROOD_SPEED);
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.perched || live.anchor != null || player.horizontalCollision || player.onClimbable();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.anchor != null;
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.ITEM_COBWEB, player.position().add(0.0, 1.0, 0.0), 50, 1.0, 0.05);
		sound(player, SoundEvents.SPIDER_AMBIENT, 1.8F, 0.5F);
		return true;
	}
}
