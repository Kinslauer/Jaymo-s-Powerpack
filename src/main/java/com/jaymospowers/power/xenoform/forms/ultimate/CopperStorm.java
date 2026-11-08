package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoDouble;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.forms.Duplex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (0.21.37) Duplex's Ultimate, Copper Storm: the copper golem grown to about 1.6 blocks in patina-green plate, a coil wound under its
 * rod's ball tip, spikes on its crown, shoulders and back and blue-white seams, with a health bar of its own on top of Duplex's. Its
 * model is {@code UltimateCopperStorm}. Its doubles come out stronger and charged; everything it does to creatures spares you, yours,
 * pets, teams and players pvp protects.
 * <ul>
 * <li>R, Storm Split: two doubles step out at once, up to {@link #MAX_DOUBLES}, each with {@link #DOUBLE_HEALTH} health and charged
 * for {@link #SPLIT_CHARGE_TICKS} ticks.</li>
 * <li>G, Bolt Rally: your doubles swarm the creature you aim at, {@link #RALLY_REACH} blocks away; the first to reach it deals
 * {@link #RALLY_DAMAGE} lightning damage with a spark burst. Sneak + G is still Fetch.</li>
 * <li>V, Arc Switch: trade places with the aimed double, and a line of sparks between the two spots shocks everything on it for
 * {@link #ARC_DAMAGE}. Sneak + V (the double's eyes) stays as it is.</li>
 * <li>B, Overcharge Merge: doubles within {@link #MERGE_RANGE} run back into you and heal {@link #MERGE_HEAL} each; each also gives you
 * a few seconds of Speed II and Strength, and three or more shock everything around you.</li>
 * <li>C, Copper Storm: visual-only lightning strikes every double, everything within {@link #STORM_RADIUS} of one takes
 * {@link #STORM_DAMAGE}, and the doubles are charged for {@link #CHARGE_TICKS} ticks.</li>
 * </ul>
 */
public final class CopperStorm extends XenoUltimate {
	// ---- tuning ----
	/** Its size: about 1.6 blocks tall (the golem's 0.55 before it). */
	public static final float SCALE = 0.9F;
	/** Storm Split: doubles at once, doubles at most, a double's health, how long a new double stays charged, cooldown (Duplex's). */
	public static final int SPLIT_AT_ONCE = 2, MAX_DOUBLES = 6, SPLIT_CHARGE_TICKS = 200;
	public static final float DOUBLE_HEALTH = 10.0F;
	/** Bolt Rally: reach, damage of the first strike, how long a rallied creature is watched, how close (past its edge) a double must get. */
	public static final double RALLY_REACH = 24.0, RALLY_STRIKE_RANGE = 1.5;
	public static final float RALLY_DAMAGE = 4.0F;
	public static final int RALLY_WATCH_TICKS = 200;
	/** Arc Switch: how far (blocks) either side of the line a creature is caught, and the line's damage. */
	public static final double ARC_WIDTH = 0.8;
	public static final float ARC_DAMAGE = 5.0F;
	/** Overcharge Merge: heal per double, seconds of buff per double (and at most), how many doubles make the shock ring, its radius and damage. */
	public static final float MERGE_HEAL = 4.0F, RING_DAMAGE = 3.0F;
	public static final double MERGE_RANGE = 24.0, RING_RADIUS = 4.0;
	private static final int BOLT_HEIGHT = 16;
	public static final int MERGE_BUFF_TICKS = 60, MERGE_BUFF_CAP = 300, RING_NEEDS = 3;
	/** Copper Storm: the shock round each double (radius, damage), C's cooldown (20 s), how long the charge lasts, how many doubles come with it. */
	public static final double STORM_RADIUS = 3.0;
	public static final float STORM_DAMAGE = 6.0F;
	public static final int STORM_COOLDOWN = 400, CHARGE_TICKS = 400, STORM_STARTING = 3;
	/** Screen shake (degrees, and how far it's felt) for the change-in and the storm. */
	public static final float ENTER_SHAKE = 1.2F, STORM_SHAKE = 1.0F;
	public static final double SHAKE_RANGE = 20.0;

	/** A creature the doubles were sent at, until one reaches it (or the watch runs out). */
	private static final class Rally {
		final int target;
		final long until;

		Rally(int target, long until) {
			this.target = target;
			this.until = until;
		}
	}

	/** What each Copper Storm has under way (server thread only). */
	/** When each player's Overcharge Merge buff ends, so it can be taken off when the Ultimate does (0.21.37). */
	private static final Map<UUID, Long> MERGE_ENDS = new HashMap<>();
	private static final Map<UUID, List<Rally>> RALLIES = new HashMap<>();

	public CopperStorm(XenoForm base) {
		super(base, SCALE, 12.0F, 5.0, 0.1, 0.3, 0.5);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.COPPER_BLOCK.asList().get(0), Items.LIGHTNING_ROD.asList().get(0), Items.COPPER_BULB.asList().get(0), Items.HONEYCOMB,
			Items.COPPER_GOLEM_STATUE.asList().get(0)};
	}

	/** The change-in film zooms on these in turn (model part names): the belt, both arms, the head, the rod with its ball tip. */
	@Override
	public String[] morphParts() {
		return new String[] {"belt", "right_arm+left_arm", "head", "rod+knob"};
	}

	// ---- entering, leaving ----

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// a full set of doubles comes with it (the golem's own were popped by the change-in)
		while (Duplex.loyal(player, live).size() < STORM_STARTING && live.doubles.size() < MAX_DOUBLES) {
			XenoDouble twin = Duplex.spawnDouble(player, live, live.doubles.size() % 2 == 1);
			if (twin == null) {
				break;
			}
			strengthen(twin, CHARGE_TICKS);
		}
		live.chargedUntil = level.getGameTime() + CHARGE_TICKS;
		if (!live.doubles.isEmpty()) {
			Duplex.announce(player, 1 + live.doubles.size());
		}
		bolt(level, player.position());
		burst(level, ParticleTypes.ELECTRIC_SPARK, player.position().add(0.0, 1.4, 0.0), 40, 0.4, 0.25);
		sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 1.4F);
		sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 1.0F, 0.6F);
		Shakes.heavy(player, player.position(), SHAKE_RANGE, ENTER_SHAKE);
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		RALLIES.remove(player.getUUID());
		Long mergeEnd = MERGE_ENDS.remove(player.getUUID());
		if (mergeEnd != null && player.level().getGameTime() < mergeEnd) {
			player.removeEffect(MobEffects.SPEED);
			player.removeEffect(MobEffects.STRENGTH);
		}
		Duplex.closeEyes(player, live);
		live.chargedUntil = 0L;
		ServerLevel level = player.level();
		// every double goes with the storm, so none is left in the world untracked (0.21.37)
		live.doubles.removeIf(id -> !(level.getEntity(id) instanceof XenoDouble twin) || !twin.isAlive());
		boolean popped = !live.doubles.isEmpty();
		for (XenoDouble twin : Duplex.loyal(player, live)) {
			twin.removeEffect(MobEffects.SPEED);
			twin.removeEffect(MobEffects.STRENGTH);
			twin.removeEffect(MobEffects.GLOWING);
			twin.pop(level);
		}
		live.doubles.clear();
		if (popped && player.isAlive()) {
			Duplex.announce(player, 1 + live.doubles.size());
		}
	}

	// ---- the doubles' own hunting, and the strikes of Bolt Rally ----

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		base.tick(player, live); // the golem's own: eyes, and idle doubles joining a fight
		ServerLevel level = player.level();
		List<Rally> rallies = RALLIES.get(player.getUUID());
		if (rallies != null) {
			long now = level.getGameTime();
			List<XenoDouble> doubles = Duplex.loyal(player, live);
			for (Iterator<Rally> it = rallies.iterator(); it.hasNext();) {
				Rally rally = it.next();
				if (!(level.getEntity(rally.target) instanceof LivingEntity target) || !target.isAlive() || now > rally.until) {
					it.remove();
					continue;
				}
				for (XenoDouble twin : doubles) {
					double reach = RALLY_STRIKE_RANGE + target.getBbWidth() * 0.5;
					if (twin.distanceToSqr(target) <= reach * reach) {
						if (harmable(player, target)) {
							target.hurtServer(level, level.damageSources().lightningBolt(), RALLY_DAMAGE);
						}
						Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
						burst(level, ParticleTypes.ELECTRIC_SPARK, at, 24, 0.35, 0.2);
						sound(player, SoundEvents.COPPER_BULB_TURN_ON, 1.0F, 1.8F);
						it.remove();
						break;
					}
				}
			}
			if (rallies.isEmpty()) {
				RALLIES.remove(player.getUUID());
			}
		}
		if (player.tickCount % 6 == 0) {
			// sparks crawl along the belt
			double a = player.tickCount * 0.35;
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(a) * 0.35, player.getY() + 0.55, player.getZ() + Math.sin(a) * 0.35, 1, 0.02,
				0.02, 0.02, 0.0);
		}
	}

	/** What you hit, your doubles go for. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		base.onDealtDamage(player, live, target);
	}

	// ---- keys ----

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R) && stormSplit(player, live)) {
					cooldown(player, live, R, Duplex.SPLIT_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && (player.isShiftKeyDown() ? Duplex.fetch(player, live) : boltRally(player, live))) {
					cooldown(player, live, G, Duplex.RALLY_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (live.eyes >= 0) {
					// V again: back behind your own eyes
					Duplex.closeEyes(player, live);
					cooldown(player, live, V, Duplex.EYES_COOLDOWN);
					return;
				}
				if (player.isShiftKeyDown()) {
					if (ready(player, live, V) && Duplex.openEyes(player, live)) {
						cooldown(player, live, V, Duplex.EYES_COOLDOWN);
					}
					return;
				}
				if (ready(player, live, V) && arcSwitch(player, live)) {
					cooldown(player, live, V, Duplex.SWITCH_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && overchargeMerge(player, live)) {
					cooldown(player, live, B, Duplex.MERGE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	// ---- R: Storm Split ----

	private static boolean stormSplit(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		live.doubles.removeIf(id -> !(level.getEntity(id) instanceof XenoDouble twin) || !twin.isAlive());
		int made = 0;
		for (int i = 0; i < SPLIT_AT_ONCE && live.doubles.size() < MAX_DOUBLES; i++) {
			XenoDouble twin = Duplex.spawnDouble(player, live, i % 2 == 1);
			if (twin == null) {
				break;
			}
			strengthen(twin, SPLIT_CHARGE_TICKS);
			made++;
		}
		if (made == 0) {
			return false;
		}
		Duplex.announce(player, 1 + live.doubles.size());
		sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 1.0F, 1.2F);
		sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.8F);
		return true;
	}

	/** A storm double: more health than the golem's, and charged for {@code ticks}. */
	private static void strengthen(XenoDouble twin, int ticks) {
		AttributeInstance health = twin.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) {
			health.setBaseValue(DOUBLE_HEALTH);
		}
		twin.setHealth(DOUBLE_HEALTH);
		Duplex.charge(twin, ticks);
	}

	// ---- G: Bolt Rally ----

	private static boolean boltRally(ServerPlayer player, XenoformPower.Live live) {
		List<XenoDouble> doubles = Duplex.loyal(player, live);
		if (doubles.isEmpty()) {
			return false;
		}
		ServerLevel level = player.level();
		LivingEntity target = aimed(player, RALLY_REACH, e -> !Duplex.mine(player, e) && !(e instanceof XenoDouble) && harmable(player, e));
		for (XenoDouble twin : doubles) {
			if (twin.busy()) {
				continue;
			}
			twin.setTarget(target);
			if (target == null) {
				twin.getNavigation().moveTo(player, XenoDouble.FOLLOW_SPEED);
			}
		}
		if (target != null) {
			RALLIES.computeIfAbsent(player.getUUID(), id -> new ArrayList<>()).add(new Rally(target.getId(), level.getGameTime() + RALLY_WATCH_TICKS));
			burst(level, ParticleTypes.ELECTRIC_SPARK, target.position().add(0.0, target.getBbHeight() + 0.3, 0.0), 14, 0.25, 0.08);
			sound(player, SoundEvents.COPPER_GOLEM_STEP, 1.0F, 1.6F);
		} else {
			sound(player, SoundEvents.COPPER_GOLEM_STEP, 1.0F, 0.9F);
		}
		return true;
	}

	// ---- V: Arc Switch ----

	private static boolean arcSwitch(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		LivingEntity aimedAt = Duplex.aimedDouble(player);
		XenoDouble pick = aimedAt instanceof XenoDouble twin ? twin : null;
		if (pick == null) {
			double best = -1.0;
			XenoDouble chosen = null;
			for (XenoDouble twin : Duplex.loyal(player, live)) {
				double d = twin.distanceToSqr(player);
				if (d > best && d < Duplex.SWITCH_REACH * Duplex.SWITCH_REACH) {
					best = d;
					chosen = twin;
				}
			}
			pick = chosen;
		}
		final XenoDouble target = pick;
		if (target == null) {
			return false;
		}
		Vec3 mine = player.position();
		Vec3 theirs = pick.position();
		// room for the storm's taller body at the double's spot (0.21.37)
		if (!level.noCollision(player, player.getBoundingBox().move(theirs.subtract(mine)))) {
			return false;
		}
		player.teleportTo(level, theirs.x, theirs.y, theirs.z, Set.of(), player.getYRot(), player.getXRot(), false);
		pick.teleportTo(mine.x, mine.y, mine.z);
		pick.getNavigation().stop();
		player.resetFallDistance();
		// the arc between the two spots
		Vec3 from = mine.add(0.0, 1.0, 0.0);
		Vec3 to = theirs.add(0.0, 1.0, 0.0);
		beam(level, from, to, ParticleTypes.ELECTRIC_SPARK, Math.max(4, (int)(from.distanceTo(to) * 2.0)));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(ARC_WIDTH + 1.0),
			e -> e != player && e != target && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !Duplex.mine(player, e) && !spared(player, e)
			&& harmable(player, e))) {
			if (e.getBoundingBox().inflate(ARC_WIDTH).clip(from, to).isPresent()) {
				e.hurtServer(level, level.damageSources().lightningBolt(), ARC_DAMAGE);
				burst(level, ParticleTypes.ELECTRIC_SPARK, e.position().add(0.0, e.getBbHeight() * 0.5, 0.0), 10, 0.3, 0.12);
			}
		}
		burst(level, ParticleTypes.ELECTRIC_SPARK, mine.add(0.0, 1.0, 0.0), 14, 0.3, 0.1);
		burst(level, ParticleTypes.ELECTRIC_SPARK, theirs.add(0.0, 1.0, 0.0), 14, 0.3, 0.1);
		sound(player, SoundEvents.COPPER_BULB_TURN_ON, 1.0F, 1.4F);
		sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5F, 2.0F);
		return true;
	}

	// ---- B: Overcharge Merge ----

	private static boolean overchargeMerge(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		int merged = 0;
		for (Iterator<Integer> it = live.doubles.iterator(); it.hasNext();) {
			if (!(level.getEntity(it.next()) instanceof XenoDouble twin) || !twin.isAlive()) {
				it.remove();
				continue;
			}
			if (twin.distanceToSqr(player) > MERGE_RANGE * MERGE_RANGE) {
				continue;
			}
			beam(level, twin.position().add(0.0, 1.0, 0.0), player.position().add(0.0, 1.0, 0.0), ParticleTypes.ELECTRIC_SPARK,
				Math.max(2, (int)(twin.distanceTo(player) * 2.0)));
			twin.pop(level);
			it.remove();
			merged++;
		}
		if (merged == 0) {
			return false;
		}
		player.heal(MERGE_HEAL * merged);
		int buff = Math.min(MERGE_BUFF_CAP, MERGE_BUFF_TICKS * merged);
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, buff, 1, false, true));
		player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, buff, 0, false, true));
		MERGE_ENDS.put(player.getUUID(), level.getGameTime() + buff);
		Duplex.announce(player, 1 + live.doubles.size());
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		burst(level, ParticleTypes.HAPPY_VILLAGER, at, 6 * merged, 0.4, 0.02);
		if (merged >= RING_NEEDS) {
			for (LivingEntity e : around(player, RING_RADIUS)) {
				if (!Duplex.mine(player, e) && !(e instanceof XenoDouble) && harmable(player, e)) {
					e.hurtServer(level, level.damageSources().lightningBolt(), RING_DAMAGE);
				}
			}
			ring(level, ParticleTypes.ELECTRIC_SPARK, player.position(), RING_RADIUS, 28);
			ring(level, ParticleTypes.ELECTRIC_SPARK, player.position(), RING_RADIUS * 0.5, 14);
			sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.6F);
			Shakes.heavy(player, player.position(), SHAKE_RANGE * 0.5, STORM_SHAKE * 0.6F);
		}
		sound(player, SoundEvents.COPPER_GOLEM_SPAWN, 1.0F, 0.7F);
		return true;
	}

	// ---- C: Copper Storm ----

	/** C: lightning (for show) on every double, a shock round each, and the charge. A set of doubles comes first if there are none. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (!ready(player, live, C)) {
			return;
		}
		ServerLevel level = player.level();
		live.doubles.removeIf(id -> !(level.getEntity(id) instanceof XenoDouble twin) || !twin.isAlive());
		while (Duplex.loyal(player, live).size() < STORM_STARTING && live.doubles.size() < MAX_DOUBLES) {
			XenoDouble twin = Duplex.spawnDouble(player, live, live.doubles.size() % 2 == 1);
			if (twin == null) {
				break;
			}
			strengthen(twin, CHARGE_TICKS);
		}
		List<XenoDouble> doubles = Duplex.loyal(player, live);
		if (doubles.isEmpty()) {
			return;
		}
		List<LivingEntity> shocked = new ArrayList<>();
		for (XenoDouble twin : doubles) {
			bolt(level, twin.position());
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, twin.getBoundingBox().inflate(STORM_RADIUS),
				e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !Duplex.mine(player, e) && !spared(player, e)
					&& harmable(player, e) && e.distanceToSqr(twin) <= STORM_RADIUS * STORM_RADIUS)) {
				if (!shocked.contains(e)) {
					shocked.add(e);
				}
			}
			Duplex.charge(twin, CHARGE_TICKS);
			burst(level, ParticleTypes.ELECTRIC_SPARK, twin.position().add(0.0, 1.0, 0.0), 30, 0.5, 0.2);
		}
		for (LivingEntity e : shocked) {
			e.hurtServer(level, level.damageSources().lightningBolt(), STORM_DAMAGE);
		}
		live.chargedUntil = level.getGameTime() + CHARGE_TICKS;
		Duplex.announce(player, 1 + live.doubles.size());
		sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0F, 1.3F);
		Shakes.heavy(player, player.position(), SHAKE_RANGE, STORM_SHAKE);
		cooldown(player, live, C, STORM_COOLDOWN);
	}

	/** A bolt for show: a column of sparks and a thunderclap, with no lightning entity, so no copper is scraped and no rod is lit (0.21.37). */
	private static void bolt(ServerLevel level, Vec3 at) {
		Vec3 top = at.add(0.0, BOLT_HEIGHT, 0.0);
		beam(level, top, at, ParticleTypes.ELECTRIC_SPARK, BOLT_HEIGHT * 2);
		beam(level, top, at, ParticleTypes.END_ROD, BOLT_HEIGHT);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0F, 1.2F);
	}

	/** A player the attacker may not hurt (pvp off, a teammate) is never shocked. */
	private static boolean harmable(ServerPlayer player, Entity e) {
		return !(e instanceof ServerPlayer other) || player.canHarmPlayer(other);
	}
}
