package com.jaymospowers.compat;

import com.jaymospowers.armory.ThrownWeapon;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.entity.SpellProjectile;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.58) Andy, on the Iron Spear's Impale (RPG Series): "using this should actually throw your spear". Spell Engine's
 * Impale only flies a picture of the held weapon and leaves it in your hand. Now the spear itself goes: it leaves the
 * main hand as the spell flies (the spell still does its own damage and knockback, scaled by the charge), and where the
 * spell ends the spear carries on as a real thrown weapon ({@link ThrownWeapon}) that lies where it lands for its
 * thrower to pick up, as a trident does:
 * <ul>
 * <li>struck into a block: it sticks there;</li>
 * <li>struck into a creature: it glances off and drops at its feet (it hits nothing a second time);</li>
 * <li>flown its full range: it keeps its speed and falls in an arc.</li>
 * </ul>
 * There is only ever one spear: it's out of the hand before the spell flies and back in the world the tick the spell
 * ends (or in its thrower's pack, or at their feet, if the spell ended somewhere unloaded or the server stops first). In
 * creative it stays in hand, and the thrown one is creative-only pickup, like a trident. Loaded only with Spell Engine
 * ({@link SpellEngineCompat}).
 */
final class SpellThrows {
	/** The spells that throw the weapon itself. */
	static final Set<Identifier> THROWS = Set.of(Identifier.fromNamespaceAndPath("rpg_series", "impale"));
	/** A spell still flying after this long (ticks) gives its weapon up where it is (one that never got into the world). */
	static final int MAX_FLIGHT = 600;
	/** Glancing off a creature: how much of its speed it throws back, and how high it pops. */
	static final double GLANCE_BACK = 0.05, GLANCE_UP = 0.15;
	/** Durability a throw costs, as a held throwing weapon's does. */
	static final int WEAR = 1;

	/** A spell in flight with the weapon it carries. */
	private static final class Flight {
		final SpellProjectile spell;
		final LivingEntity thrower;
		final ItemStack weapon;
		final boolean creative;
		Vec3 pos;
		Vec3 motion;
		int hits;
		int age;

		Flight(SpellProjectile spell, LivingEntity thrower, ItemStack weapon, boolean creative) {
			this.spell = spell;
			this.thrower = thrower;
			this.weapon = weapon;
			this.creative = creative;
			this.pos = spell.position();
			this.motion = spell.getDeltaMovement();
			this.hits = hits(spell);
		}
	}

	private static final List<Flight> FLYING = new ArrayList<>();
	private static @Nullable Field impactHistory;
	private static boolean impactHistoryLooked;

	private SpellThrows() {
	}

	static void init() {
		SpellEvents.PROJECTILE_SHOOT.register(event -> launched(event.projectile(), event.caster(), event.spellEntry().unwrapKey()
			.map(key -> key.identifier()).orElse(null)));
		ServerTickEvents.END_SERVER_TICK.register(SpellThrows::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(SpellThrows::stopping);
	}

	private static void launched(SpellProjectile spell, LivingEntity caster, @Nullable Identifier id) {
		if (id == null || !THROWS.contains(id) || !(caster.level() instanceof ServerLevel)) {
			return;
		}
		ItemStack held = caster.getMainHandItem();
		if (held.isEmpty()) {
			return;
		}
		boolean creative = caster instanceof Player player && player.hasInfiniteMaterials();
		ItemStack weapon = held.copyWithCount(1);
		if (!creative) {
			// the spell's model was taken from this stack when it was made; it's left as it is, the hand just lets go
			if (held.getCount() > 1) {
				caster.setItemInHand(InteractionHand.MAIN_HAND, held.copyWithCount(held.getCount() - 1));
			} else {
				caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
			weapon.hurtWithoutBreaking(WEAR, caster instanceof Player player ? player : null);
		}
		FLYING.add(new Flight(spell, caster, weapon, creative));
	}

	private static void tick(MinecraftServer server) {
		if (FLYING.isEmpty()) {
			return;
		}
		for (Iterator<Flight> it = FLYING.iterator(); it.hasNext();) {
			Flight flight = it.next();
			SpellProjectile spell = flight.spell;
			flight.age++;
			if (!spell.isRemoved() && flight.age < MAX_FLIGHT) {
				flight.pos = spell.position();
				flight.motion = spell.getDeltaMovement();
				flight.hits = hits(spell);
				continue;
			}
			it.remove();
			boolean struck = hits(spell) > flight.hits;
			Vec3 at = spell.isRemoved() ? spell.position() : flight.pos;
			if (!spell.isRemoved()) {
				spell.discard();
			}
			Vec3 motion = struck ? flight.motion.scale(-GLANCE_BACK).add(0.0, GLANCE_UP, 0.0) : flight.motion;
			land(flight, (ServerLevel) spell.level(), at, motion, struck);
		}
	}

	/** The weapon carries on from where the spell ended, or goes back to its thrower if it can't. */
	private static void land(Flight flight, ServerLevel level, Vec3 at, Vec3 motion, boolean struck) {
		if (!level.isLoaded(BlockPos.containing(at))) {
			giveBack(flight);
			return;
		}
		ThrownWeapon thrown = new ThrownWeapon(level, flight.thrower, flight.weapon);
		thrown.setPos(at);
		thrown.setDeltaMovement(motion);
		double flat = motion.horizontalDistance();
		if (motion.lengthSqr() > 1.0E-6) {
			thrown.setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
			thrown.setXRot((float) (Mth.atan2(motion.y, flat) * Mth.RAD_TO_DEG));
			thrown.yRotO = thrown.getYRot();
			thrown.xRotO = thrown.getXRot();
		}
		if (struck) {
			thrown.spent();
		}
		if (flight.creative) {
			thrown.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
		}
		if (!level.addFreshEntity(thrown)) {
			giveBack(flight);
		}
	}

	/** Into the thrower's pack, or at their feet. */
	private static void giveBack(Flight flight) {
		if (flight.creative) {
			return;
		}
		ItemStack weapon = flight.weapon;
		if (flight.thrower instanceof Player player && !player.isRemoved() && player.getInventory().add(weapon)) {
			return;
		}
		if (!weapon.isEmpty() && flight.thrower.level() instanceof ServerLevel level) {
			flight.thrower.spawnAtLocation(level, weapon);
		}
	}

	/** Nothing is left in the air when the world is saved. */
	private static void stopping(MinecraftServer server) {
		for (Flight flight : FLYING) {
			SpellProjectile spell = flight.spell;
			if (!spell.isRemoved()) {
				spell.discard();
			}
			land(flight, (ServerLevel) spell.level(), flight.pos, Vec3.ZERO, false);
		}
		FLYING.clear();
	}

	/** How many creatures the spell has struck so far (Spell Engine keeps them in a protected set). */
	private static int hits(SpellProjectile spell) {
		if (!impactHistoryLooked) {
			impactHistoryLooked = true;
			try {
				Field field = SpellProjectile.class.getDeclaredField("impactHistory");
				field.setAccessible(true);
				impactHistory = field;
			} catch (ReflectiveOperationException | RuntimeException e) {
				impactHistory = null;
			}
		}
		if (impactHistory != null) {
			try {
				return impactHistory.get(spell) instanceof Set<?> set ? set.size() : 0;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return 0;
			}
		}
		return 0;
	}
}
