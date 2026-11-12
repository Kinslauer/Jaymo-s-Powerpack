package com.jaymospowers.compat;

import com.jaymospowers.armory.ThrownWeapon;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.api.spell.event.SpellEvents;
import net.spell_engine.entity.SpellProjectile;
import net.spell_engine.internals.casting.SpellCast;
import net.spell_engine.internals.cost.Ammo;
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
 * <p>
 * Andy: "anything that gives you free projectiles should be removed, the spear throw removes your spear, this should
 * throw up to three knives if you have them, but only ever what you have". So:
 * <ul>
 * <li>Rogues &amp; Warriors' Shattering Throw throws the weapon itself, as Impale does.</li>
 * <li>Fan of Knives no longer conjures its blades: each one is a real knife of yours, the held one first and then
 * knives and daggers from your pack ({@link #isKnife}), {@link #FAN_MAX} at most and never more than you carry. Each
 * lands to be picked up as above.</li>
 * <li>Archers' Rain of Arrows drops one of your arrows (pack or worn quiver) for each one that falls, as many as you
 * carry up to its usual count, and won't cast with none.</li>
 * <li>Archers' Barrage won't cast without its three arrows ({@link #BARRAGE}).</li>
 * </ul>
 */
final class SpellThrows {
	/** The spells that throw the weapon itself. */
	static final Set<Identifier> THROWS = Set.of(Identifier.fromNamespaceAndPath("rpg_series", "impale"),
		Identifier.fromNamespaceAndPath("rogues", "throw"));
	/** The spell that throws your own knives, and the most it throws. */
	static final Identifier FAN_OF_KNIVES = Identifier.fromNamespaceAndPath("rpg_series", "fan_of_knives");
	static final int FAN_MAX = 3;
	/** The spell whose falling arrows are your own. */
	static final Identifier RAIN_OF_ARROWS = Identifier.fromNamespaceAndPath("archers", "rain_of_arrows");
	/**
	 * Archers' Barrage looses {@link #BARRAGE_ARROWS} arrows from your bow, each drawn from your arrows, but Spell Engine
	 * makes one up for any shot you have no arrow left for. It now won't cast without that many.
	 */
	static final Identifier BARRAGE = Identifier.fromNamespaceAndPath("archers", "barrage");
	static final int BARRAGE_ARROWS = 3;
	/** Knives set aside for the rest of a fan that haven't flown after this long (ticks) go back to their owner. */
	static final int FAN_HOLD = 40;
	/** What a fan throws besides the held blade: knives and daggers, by id. */
	private static final Pattern KNIFE = Pattern.compile("(^|[_.])(dagger|knife|kunai|tanto|stiletto)($|[_.])");
	/** Blades a fan never takes from the pack: one-of-a-kind ones (Sora's Dagger is a lineage weapon, Ashfang a relic). */
	private static final Pattern NEVER_THROWN = Pattern.compile("soras_dagger|ashfang|hidden_blade");
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

	/** A fan's knives still to fly, and when they were taken (game time). */
	private record FanHold(Deque<ItemStack> knives, boolean creative, long since) {
	}

	private static final Map<UUID, FanHold> FANS = new HashMap<>();
	private static @Nullable Field impactHistory;
	private static boolean impactHistoryLooked;

	private SpellThrows() {
	}

	static void init() {
		SpellEvents.PROJECTILE_SHOOT.register(event -> {
			Identifier id = idOf(event.spellEntry());
			if (FAN_OF_KNIVES.equals(id) && event.caster() instanceof Player player && player.level() instanceof ServerLevel) {
				fan(event.projectile(), event.mutableLaunchProperties(), player, event.sequenceIndex());
			} else {
				launched(event.projectile(), event.caster(), id);
			}
		});
		SpellEvents.PROJECTILE_FALL.register(event -> {
			if (RAIN_OF_ARROWS.equals(idOf(event.spellEntry())) && event.caster() instanceof Player player && player.level() instanceof ServerLevel) {
				rain(event.projectile(), event.mutableLaunchProperties(), player, event.sequenceIndex());
			}
		});
		SpellEvents.CASTING_ATTEMPT.PRE.register(args -> {
			int needs = arrowsNeeded(idOf(args.spell()));
			return needs > 0 && !args.caster().hasInfiniteMaterials() && arrowsCarried(args.caster(), needs) < needs
				? SpellCast.Attempt.failMissingItem(new SpellCast.Attempt.MissingItemInfo(ARROWS)) : null;
		});
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

	private static @Nullable Identifier idOf(net.minecraft.core.Holder<Spell> spell) {
		return spell.unwrapKey().map(key -> key.identifier()).orElse(null);
	}

	/**
	 * One blade of a Fan of Knives. The first takes every knife the fan will throw (the held one, then the pack's) and
	 * cuts the fan down to that many; each blade then carries one of them and shows it.
	 */
	private static void fan(SpellProjectile spell, Spell.LaunchProperties launch, Player player, int sequence) {
		UUID id = player.getUUID();
		boolean creative = player.hasInfiniteMaterials();
		if (sequence == 0) {
			FanHold old = FANS.remove(id);
			if (old != null) {
				giveBack(player, old);
			}
			Deque<ItemStack> knives = takeKnives(player, Math.min(FAN_MAX, 1 + Math.max(0, launch.extra_launch_count)), creative);
			if (knives.isEmpty()) {
				// nothing to throw (an empty hand): the conjured blade never flies
				spell.discard();
				launch.extra_launch_count = 0;
				return;
			}
			launch.extra_launch_count = knives.size() - 1;
			ItemStack first = knives.removeFirst();
			if (!knives.isEmpty()) {
				FANS.put(id, new FanHold(knives, creative, player.level().getGameTime()));
			}
			carry(spell, player, first, creative);
			return;
		}
		FanHold hold = FANS.get(id);
		if (hold == null || hold.knives().isEmpty()) {
			spell.discard();
			return;
		}
		ItemStack knife = hold.knives().removeFirst();
		if (hold.knives().isEmpty()) {
			FANS.remove(id);
		}
		carry(spell, player, knife, hold.creative());
	}

	/** {@code spell} flies as {@code knife}, which lands where it ends. */
	private static void carry(SpellProjectile spell, Player player, ItemStack knife, boolean creative) {
		spell.setItemStackModel(knife);
		FLYING.add(new Flight(spell, player, knife, creative));
	}

	/**
	 * Up to {@code count} knives out of {@code player}'s hands and pack, the held blade first, each one worn a little as a
	 * thrown weapon is. In creative, copies of the held blade, nothing taken.
	 */
	private static Deque<ItemStack> takeKnives(Player player, int count, boolean creative) {
		Deque<ItemStack> knives = new ArrayDeque<>();
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return knives;
		}
		if (creative) {
			for (int i = 0; i < count; i++) {
				knives.add(held.copyWithCount(1));
			}
			return knives;
		}
		knives.add(take(player, held));
		List<ItemStack> pack = player.getInventory().getNonEquipmentItems();
		int selected = player.getInventory().getSelectedSlot();
		// the same kind as the held blade first, then any other knife
		for (int pass = 0; pass < 2 && knives.size() < count; pass++) {
			for (int slot = 0; slot < pack.size() && knives.size() < count; slot++) {
				ItemStack stack = pack.get(slot);
				if (slot == selected || stack.isEmpty() || !isKnife(stack) || (pass == 0) != ItemStack.isSameItem(stack, knives.getFirst())) {
					continue;
				}
				while (!stack.isEmpty() && knives.size() < count) {
					knives.add(take(player, stack));
				}
			}
		}
		return knives;
	}

	/** One off {@code stack}, worn by a throw. */
	private static ItemStack take(Player player, ItemStack stack) {
		ItemStack one = stack.split(1);
		one.hurtWithoutBreaking(WEAR, player);
		return one;
	}

	/** A knife or dagger a fan may throw from the pack. */
	static boolean isKnife(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return KNIFE.matcher(path).find() && !NEVER_THROWN.matcher(path).find();
	}

	/** Knives a fan set aside but never threw go back to the pack, or drop at their owner's feet. */
	private static void giveBack(Player player, FanHold hold) {
		if (hold.creative()) {
			return;
		}
		for (ItemStack knife : hold.knives()) {
			if (!player.isRemoved() && player.getInventory().add(knife)) {
				continue;
			}
			if (!knife.isEmpty() && player.level() instanceof ServerLevel level) {
				player.spawnAtLocation(level, knife);
			}
		}
	}

	/** What Rain of Arrows spends: anything in the arrows tag (Spell Engine finds it in the pack and in a worn quiver). */
	private static final Ammo.Searched ARROWS = new Ammo.Searched(ItemTags.ARROWS, null);

	/** Arrows a spell needs carried before it casts (none for any other spell). */
	private static int arrowsNeeded(@Nullable Identifier spell) {
		if (RAIN_OF_ARROWS.equals(spell)) {
			return 1;
		}
		return BARRAGE.equals(spell) ? BARRAGE_ARROWS : 0;
	}

	/** How many arrows {@code player} carries, counting no further than {@code most}. */
	private static int arrowsCarried(Player player, int most) {
		int found = 0;
		for (Ammo.Source source : Ammo.findSources(player, ARROWS, most)) {
			found += source.found();
		}
		return found;
	}

	/**
	 * One arrow of a Rain of Arrows. The first takes an arrow for every one that will fall, as many as the caster carries
	 * up to the spell's count, and cuts the rain down to that many.
	 */
	private static void rain(SpellProjectile spell, Spell.LaunchProperties launch, Player player, int sequence) {
		if (sequence != 0 || player.hasInfiniteMaterials()) {
			return;
		}
		int want = 1 + Math.max(0, launch.extra_launch_count);
		int taken = 0;
		for (Ammo.Source source : Ammo.findSources(player, ARROWS, want)) {
			if (source.isContainer()) {
				taken += Ammo.takeFromContainer(source.itemStack(), ARROWS, source.found());
			} else {
				source.itemStack().shrink(source.found());
				taken += source.found();
			}
		}
		if (taken <= 0) {
			spell.discard();
			launch.extra_launch_count = 0;
			return;
		}
		launch.extra_launch_count = Math.min(launch.extra_launch_count, taken - 1);
	}

	private static void tick(MinecraftServer server) {
		if (!FANS.isEmpty()) {
			long now = server.overworld().getGameTime();
			FANS.entrySet().removeIf(entry -> {
				if (now - entry.getValue().since() < FAN_HOLD) {
					return false;
				}
				Player player = server.getPlayerList().getPlayer(entry.getKey());
				if (player != null) {
					giveBack(player, entry.getValue());
				}
				return true;
			});
		}
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
