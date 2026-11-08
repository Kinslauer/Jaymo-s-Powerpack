package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy, after a playtest where a vampire fed in a crowded house and "noone was reacting as if anything was wrong":
 * "villagers and guards can/will equip torches and fight back if they see them". Whoever sees a vampire of the world's
 * people ({@link Vampires}, not a vampire player) bite someone takes alarm. One of them cries out what it is. Guards, a
 * realm's fighting folk and brave villagers ({@link Temperaments#fearless}) grab a torch (in the main hand if it's empty
 * or holds no weapon, else the off hand) and go for it; their blows burn it ({@link Vampires#weakness}). The rest run
 * ({@link Frights#scare}) and call the guards: every guard within {@link #CALL_RANGE} comes, torch in hand. A defender
 * gives up after {@link #DEFEND_TICKS} or when the vampire's well away, and puts the torch back down.
 */
public final class VampireAlarm {
	/** How far a feeding is seen (blocks, in sight), and how far a cry for the guards carries (blocks). */
	static final double WITNESS_RANGE = 16.0, CALL_RANGE = 32.0;
	/** A defender: how long it keeps after the vampire (ticks), how far it chases before giving up (blocks), its pace. */
	static final int DEFEND_TICKS = 20 * 30;
	static final double CHASE_RANGE = 28.0;
	static final float VILLAGER_DEFEND_SPEED = 0.9F;
	static final double FOLK_DEFEND_SPEED = 1.3;
	/** A villager's torch blow: its reach (blocks), damage (before the torch's own weakness), how often (ticks). */
	static final double BLOW_REACH = 2.3;
	static final float TORCH_BLOW = 2.0F;
	static final int BLOW_EVERY = 20;
	/** How often defenders are moved along (ticks); how long between cries about the same vampire (ticks); lines of each cry. */
	static final int STEP_EVERY = 5;
	static final long CRY_GAP = 20L * 8;
	static final int CRY_LINES = 12, CALL_LINES = 4, TORCH_LINES = 4;
	/** How far bystanders who aren't villagers (a realm's other folk) run (blocks). */
	static final double FOLK_RUN = 14.0;

	/** A defender after a vampire: which, until when, where its torch went and what that hand held before, its next blow. */
	private static final class Defense {
		final UUID vampire;
		long until;
		@Nullable EquipmentSlot slot;
		ItemStack before = ItemStack.EMPTY;
		long nextBlow;

		Defense(UUID vampire, long until) {
			this.vampire = vampire;
			this.until = until;
		}
	}

	private static final Map<Mob, Defense> DEFENDERS = new WeakHashMap<>();
	/** When each vampire was last cried out about (game time). */
	private static final Map<UUID, Long> CRIED = new HashMap<>();

	private VampireAlarm() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VampireAlarm::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Mob m : new ArrayList<>(DEFENDERS.keySet())) {
				standDown(m);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			DEFENDERS.clear();
			CRIED.clear();
		});
	}

	/** {@code e} is after a vampire with a torch just now. */
	public static boolean defending(LivingEntity e) {
		return e instanceof Mob m && DEFENDERS.containsKey(m);
	}

	/** {@code vampire} just bit {@code victim}: whoever sees it takes alarm. */
	static void seen(ServerLevel level, PathfinderMob vampire, LivingEntity victim) {
		long now = level.getGameTime();
		boolean cried = now - CRIED.getOrDefault(vampire.getUUID(), Long.MIN_VALUE / 2) < CRY_GAP;
		boolean called = false;
		for (LivingEntity w : level.getEntitiesOfClass(LivingEntity.class, vampire.getBoundingBox().inflate(WITNESS_RANGE),
			e -> e != vampire && e != victim && witness(e) && e.hasLineOfSight(vampire))) {
			if (defender(w)) {
				defend(level, (Mob)w, vampire, now, !cried);
				cried = true;
				continue;
			}
			if (w instanceof Villager v) {
				// (they run, home if it's near, and hide a while)
				Frights.scare(level, v, vampire.position());
				if (!cried) {
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.vampire.cry." + level.getRandom().nextInt(CRY_LINES), vampire.getDisplayName()),
						Speech.RED, 24.0);
					cried = true;
				} else if (!called) {
					Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.vampire_alarm.guards." + level.getRandom().nextInt(CALL_LINES)), Speech.RED, 24.0);
				}
				called = true;
			} else if (w instanceof PathfinderMob folk) {
				Vec3 away = folk.position().subtract(vampire.position()).multiply(1.0, 0.0, 1.0);
				away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(FOLK_RUN);
				folk.getNavigation().moveTo(folk.getX() + away.x, folk.getY(), folk.getZ() + away.z, FOLK_DEFEND_SPEED);
				called = true;
			}
		}
		if (called) {
			// (the guards come, seen it or not)
			for (LivingEntity g : level.getEntitiesOfClass(LivingEntity.class, vampire.getBoundingBox().inflate(CALL_RANGE),
				e -> e != vampire && e != victim && witness(e) && (Downed.isGuard(e) || e instanceof Folk f && f.combatant()))) {
				defend(level, (Mob)g, vampire, now, false);
			}
		}
		if (cried) {
			CRIED.put(vampire.getUUID(), now);
		}
	}

	/** One of the world's people who can take alarm: awake, grown, standing, not a vampire. */
	private static boolean witness(LivingEntity e) {
		return (e instanceof Villager || e instanceof Folk) && e.isAlive() && !e.isBaby() && !e.isSleeping() && !Vampires.isVampire(e)
			&& !Downed.isDowned(e) && !Lycans.changed(e);
	}

	/** Those who'd fight it: guards, a realm's fighting folk, the brave. */
	private static boolean defender(LivingEntity e) {
		return Downed.isGuard(e) || e instanceof Folk f && f.combatant() || Temperaments.fearless(e);
	}

	/** {@code m} grabs a torch and goes for {@code vampire}. */
	private static void defend(ServerLevel level, Mob m, PathfinderMob vampire, long now, boolean shout) {
		Defense d = DEFENDERS.get(m);
		if (d != null && d.vampire.equals(vampire.getUUID())) {
			d.until = now + DEFEND_TICKS;
			return;
		}
		if (d != null) {
			standDown(m);
		}
		d = new Defense(vampire.getUUID(), now + DEFEND_TICKS);
		ItemStack main = m.getMainHandItem();
		if (!Vampires.torch(main) && !Vampires.torch(m.getOffhandItem())) {
			d.slot = main.isEmpty() || !main.has(net.minecraft.core.component.DataComponents.WEAPON) ? EquipmentSlot.MAINHAND
				: m.getOffhandItem().isEmpty() ? EquipmentSlot.OFFHAND : null;
			if (d.slot != null) {
				d.before = m.getItemBySlot(d.slot).copy();
				m.setItemSlot(d.slot, new ItemStack(Items.TORCH));
				m.setDropChance(d.slot, 0.0F);
			}
		}
		DEFENDERS.put(m, d);
		level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.2F);
		if (shout) {
			Speech.say(level, m, Component.translatable("talk.jaymoskingdoms.vampire_alarm.torches." + level.getRandom().nextInt(TORCH_LINES), vampire.getDisplayName()),
				Speech.RED, 24.0);
		}
	}

	/** {@code m} gives up: the torch goes back down and the hand holds what it held. */
	private static void standDown(Mob m) {
		Defense d = DEFENDERS.remove(m);
		if (d == null) {
			return;
		}
		if (d.slot != null && Vampires.torch(m.getItemBySlot(d.slot))) {
			m.setItemSlot(d.slot, d.before);
		}
		if (m instanceof Folk folk && folk.getTarget() != null && folk.getTarget().getUUID().equals(d.vampire)) {
			folk.setTarget(null);
		}
		if (m instanceof Villager v) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}

	private static void tick(MinecraftServer server) {
		if (DEFENDERS.isEmpty() || server.getTickCount() % STEP_EVERY != 0) {
			return;
		}
		for (Map.Entry<Mob, Defense> e : new ArrayList<>(DEFENDERS.entrySet())) {
			Mob m = e.getKey();
			Defense d = e.getValue();
			if (!(m.level() instanceof ServerLevel level)) {
				DEFENDERS.remove(m);
				continue;
			}
			long now = level.getGameTime();
			LivingEntity vampire = level.getEntity(d.vampire) instanceof LivingEntity l ? l : null;
			if (!m.isAlive() || m.isRemoved() || Downed.isDowned(m) || now > d.until || vampire == null || !vampire.isAlive()
				|| vampire.isInvisible() || vampire.distanceTo(m) > CHASE_RANGE) {
				standDown(m);
				continue;
			}
			if (m instanceof Folk folk && folk.combatant()) {
				// (they fight it their own way, the torch in hand)
				folk.setTarget(vampire);
				continue;
			}
			if (m instanceof Villager v) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(vampire, true), VILLAGER_DEFEND_SPEED, 1);
			} else {
				m.getNavigation().moveTo(vampire, FOLK_DEFEND_SPEED);
			}
			if (m.distanceTo(vampire) <= BLOW_REACH && now >= d.nextBlow && m.hasLineOfSight(vampire)) {
				d.nextBlow = now + BLOW_EVERY;
				m.getLookControl().setLookAt(vampire, 30.0F, 30.0F);
				InteractionHand hand = Vampires.torch(m.getOffhandItem()) && !Vampires.torch(m.getMainHandItem()) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
				m.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				vampire.hurtServer(level, level.damageSources().mobAttack(m), TORCH_BLOW);
			}
		}
	}
}
