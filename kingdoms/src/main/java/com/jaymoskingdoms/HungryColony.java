package com.jaymoskingdoms;

import com.jaymoskingdoms.nightforms.NightForms;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A colony that drinks (Night's Blood, patch 4). Andy's plan: "The colony's bats latch and drink like BloodBat from whatever you
 * hit or whatever hits you, and their drink comes back to you as mB." With the gift learned and slotted, a colony cast
 * ({@link NightForms}) leaves {@link #BATS} of its bats circling the vampire for {@link #TICKS}. Whatever the vampire hits, or
 * whatever hits them, a free bat flies to and latches on, and sips as a blood bat does ({@link
 * com.jaymoskingdoms.creatures.BloodBat}: a point of harm each {@link #SIP_EVERY} ticks, a wound sometimes); each sip comes back to
 * the vampire as {@link #SIP_MB} mB. A bat lets go after {@link #LATCH_TICKS}. What has no blood to drink (the undead, insects,
 * golems) is left alone, players only where players may fight.
 */
public final class HungryColony {
	/** The escort: how many bats, how long it stays (ticks); how far from the vampire they circle (blocks) and how fast (radians a tick). */
	static final int BATS = 5, TICKS = 600;
	static final double RADIUS = 1.4, TURN = 0.3;
	/** A latched bat: how long it clings (ticks), how often it sips (ticks), the harm of a sip, the odds it opens a wound, and what a sip gives (mB). */
	static final int LATCH_TICKS = 60, SIP_EVERY = 15, SIP_MB = 45;
	static final float SIP_HARM = 1.0F, BITE_BLEEDS = 0.5F;
	/** How fast a bat flies to what it will drink from (blocks a tick), and how near counts as latched. */
	static final double FLY = 0.9, NEAR = 0.9;

	private static final class Escort {
		final UUID owner;
		final long until;
		final List<Bat> bats = new ArrayList<>();
		final Map<Bat, UUID> prey = new HashMap<>();
		final Map<Bat, Integer> clung = new HashMap<>();

		Escort(UUID owner, long until) {
			this.owner = owner;
			this.until = until;
		}
	}

	private static final Map<UUID, Escort> ESCORTS = new HashMap<>();

	private HungryColony() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HungryColony::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken <= 0.0F || ESCORTS.isEmpty()) {
				return;
			}
			// what they hit
			if (source.getEntity() instanceof ServerPlayer by && ESCORTS.containsKey(by.getUUID()) && victim != by) {
				send(by, victim);
			}
			// what hits them
			if (victim instanceof ServerPlayer me && ESCORTS.containsKey(me.getUUID()) && source.getEntity() instanceof LivingEntity striker && striker != me) {
				send(me, striker);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> ESCORTS.clear());
	}

	/** A colony cast by {@code p}: with the gift, an escort stays. */
	public static void begin(ServerPlayer p, ServerLevel level) {
		BloodPage.Blood b = BloodPage.of(p);
		if (b == null || !b.knows(BloodPage.Gift.HUNGRY_COLONY) || !b.slotted(BloodPage.Gift.HUNGRY_COLONY)) {
			return;
		}
		release(p.getUUID());
		Escort e = new Escort(p.getUUID(), level.getGameTime() + TICKS);
		Vec3 at = p.position().add(0.0, p.getBbHeight() * 0.6, 0.0);
		for (int i = 0; i < BATS; i++) {
			Bat bat = NightForms.swarmBat(level, p, at);
			if (bat != null) {
				e.bats.add(bat);
			}
		}
		ESCORTS.put(p.getUUID(), e);
	}

	private static void release(UUID owner) {
		Escort old = ESCORTS.remove(owner);
		if (old != null) {
			for (Bat bat : old.bats) {
				NightForms.untrack(bat);
				bat.discard();
			}
		}
	}

	/** Whether {@code e} has blood to drink: not the undead, insects or golems, and players only where players may fight. */
	private static boolean drinkable(ServerPlayer owner, LivingEntity e) {
		return NightTargets.target(owner, e) && !e.is(EntityTypeTags.UNDEAD) && !e.is(EntityTypeTags.ARTHROPOD) && !(e instanceof AbstractGolem) && !(e instanceof Bat);
	}

	/** A free bat of {@code owner}'s goes to drink from {@code target}. */
	private static void send(ServerPlayer owner, LivingEntity target) {
		Escort e = ESCORTS.get(owner.getUUID());
		if (e == null || !drinkable(owner, target) || e.prey.containsValue(target.getUUID())) {
			return;
		}
		for (Bat bat : e.bats) {
			if (bat.isAlive() && !e.prey.containsKey(bat)) {
				e.prey.put(bat, target.getUUID());
				e.clung.put(bat, -1);
				return;
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (ESCORTS.isEmpty()) {
			return;
		}
		for (Escort e : new ArrayList<>(ESCORTS.values())) {
			ServerPlayer owner = server.getPlayerList().getPlayer(e.owner);
			if (owner == null || !owner.isAlive() || !(owner.level() instanceof ServerLevel level) || level.getGameTime() >= e.until) {
				release(e.owner);
				continue;
			}
			long t = level.getGameTime();
			Vec3 centre = owner.position().add(0.0, owner.getBbHeight() * 0.6, 0.0);
			for (int i = 0; i < e.bats.size(); i++) {
				Bat bat = e.bats.get(i);
				if (!bat.isAlive() || bat.level() != level) {
					e.prey.remove(bat);
					continue;
				}
				UUID preyId = e.prey.get(bat);
				LivingEntity prey = preyId == null ? null : level.getEntity(preyId) instanceof LivingEntity l && l.isAlive() ? l : null;
				if (preyId != null && prey == null) {
					e.prey.remove(bat);
					e.clung.remove(bat);
				}
				if (prey == null) {
					double angle = t * TURN + i * (Math.PI * 2.0 / e.bats.size());
					Vec3 at = centre.add(Math.cos(angle) * RADIUS, 0.4 * Math.sin(t * 0.2 + i), Math.sin(angle) * RADIUS);
					NightForms.place(bat, at, (float) (angle * Mth.RAD_TO_DEG) + 90.0F);
				} else {
					drink(owner, level, e, bat, prey);
				}
			}
		}
	}

	private static void drink(ServerPlayer owner, ServerLevel level, Escort e, Bat bat, LivingEntity prey) {
		int clung = e.clung.getOrDefault(bat, -1);
		Vec3 spot = latchSpot(prey);
		if (clung < 0) {
			// flying to it
			Vec3 to = spot.subtract(bat.position());
			if (to.length() <= NEAR) {
				e.clung.put(bat, 0);
			} else {
				NightForms.place(bat, bat.position().add(to.normalize().scale(FLY)), (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F);
			}
			return;
		}
		NightForms.place(bat, spot, prey.getYRot());
		clung++;
		e.clung.put(bat, clung);
		if (clung % SIP_EVERY == 0 && prey.hurtServer(level, level.damageSources().mobAttack(bat), NightTargets.harm(owner, SIP_HARM))) {
			if (bat.getRandom().nextFloat() < BITE_BLEEDS) {
				PowersCompat.cut(prey);
			}
			VampireBlood.add(owner, SIP_MB);
			level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), prey.getX(), prey.getEyeY() - 0.3, prey.getZ(), 3, 0.1, 0.1, 0.1, 0.0);
			level.playSound(null, bat.getX(), bat.getY(), bat.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.5F, 1.6F);
		}
		if (clung >= LATCH_TICKS) {
			e.prey.remove(bat);
			e.clung.remove(bat);
		}
	}

	/** Where a bat clings to {@code prey}: at the back of the neck, as a blood bat does. */
	private static Vec3 latchSpot(LivingEntity prey) {
		Vec3 back = Vec3.directionFromRotation(0.0F, prey.getYRot()).scale(-0.25);
		Vec3 side = Vec3.directionFromRotation(0.0F, prey.getYRot() + 90.0F).scale(0.3);
		return new Vec3(prey.getX() + back.x + side.x, prey.getEyeY() - 0.5, prey.getZ() + back.z + side.z);
	}

	/** Whether {@code owner} has an escort of bats now. */
	public static boolean has(@Nullable ServerPlayer owner) {
		return owner != null && ESCORTS.containsKey(owner.getUUID());
	}
}
