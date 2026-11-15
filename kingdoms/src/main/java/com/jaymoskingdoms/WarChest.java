package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * (am-warchest, am-upgrades, medieval picks; Armed Mobs' war chest, an idea only) A besieged realm's treasury is its war
 * chest (Lore, "The realms' ways": the war chest pays for a seat's defence). When the siege begins the keep's door is
 * barricaded ({@link #shutGate}); after each wave its defenders are seen to out of it ({@link #resupply}: the wounded
 * bound up, the archers' quivers filled, the worst of the breaches mended). And one sworn to the realm (or its
 * sovereign, or a player who stood with it, {@link Sieges}) may spend it, on the steward's card ({@link #audience}):
 * more guards (levies, paid off when it's over), the walls mended, arrows, an iron gate, and a draught for the
 * defenders (they fight harder and take less for a while). Nothing is free: each comes out of the treasury.
 */
final class WarChest {
	/** Tag on a guard taken on for a siege ({@link WarParties#loaded} pays off one whose siege is over). */
	static final String LEVY_TAG = "jaymoskingdoms.siege_levy";
	static final String LEVY_TASK = "levy";
	/** What each costs the treasury (silver): a levy, a mending, arrows all round, the iron gate, the draught. */
	static final long LEVY_COST = 8, MEND_COST = 4, ARROWS_COST = 3, GATE_COST = 20, DRAUGHT_COST = 10;
	/** Levies taken on at a time, and at most in a siege; they keep this near the seat. */
	static final int LEVY_HIRE = 2, LEVY_MOST = 8, LEVY_RADIUS = 24;
	/** Blocks put back by a mending; arrows to each archer; the draught's length (ticks). */
	static final int MEND_BLOCKS = 12, ARROWS = 16;
	static final int DRAUGHT_TICKS = 20 * 120;
	/** After each wave, out of the chest: this costs it, mends this many blocks and gives each archer this many arrows. */
	static final long RESUPPLY_COST = 3;
	static final int RESUPPLY_MENDS = 6, RESUPPLY_ARROWS = 8;
	/** The barricade across the keep's door: this high. */
	static final int GATE_HIGH = 2;

	private WarChest() {
	}

	// ---- the gate ----------------------------------------------------------------------------------------------------

	/** The siege on: the keep's door barricaded (a timber barricade; iron once the gate's paid for). */
	static void shutGate(ServerLevel level, WarParties.Party party, Faction target) {
		Castle.Keep keep = Factions.keep(level.getServer(), target.id());
		if (keep == null || !Loaded.made(level, keep.gate())) {
			return;
		}
		Direction in = Direction.getApproximateNearest(keep.hall().getX() - keep.gate().getX(), 0, keep.hall().getZ() - keep.gate().getZ());
		if (in.getAxis().isVertical()) {
			return;
		}
		BlockPos door = keep.gate().relative(in, 2);
		Direction across = in.getClockWise();
		List<BlockPos> set = new ArrayList<>();
		for (int i = -1; i <= 1; i++) {
			for (int y = 0; y < GATE_HIGH; y++) {
				BlockPos pos = door.relative(across, i).above(y);
				BlockState there = level.getBlockState(pos);
				if (there.canBeReplaced() && there.getFluidState().isEmpty()) {
					party.gate.put(pos.asLong(), there);
					SiegeScars.gate(pos, there);
					set.add(pos);
				}
			}
		}
		place(level, set, party.strongGate ? Blocks.IRON_BARS.defaultBlockState() : Blocks.SPRUCE_FENCE.defaultBlockState());
		level.playSound(null, door, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
	}

	private static void place(ServerLevel level, List<BlockPos> at, BlockState block) {
		for (BlockPos pos : at) {
			level.setBlock(pos, block, Block.UPDATE_ALL);
		}
		for (BlockPos pos : at) {
			level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_CLIENTS);
		}
	}

	/** (am-upgrades) The gate paid for: what's left of the barricade is iron. */
	private static void strengthen(ServerLevel level, WarParties.Party party) {
		party.strongGate = true;
		List<BlockPos> at = new ArrayList<>();
		for (long l : party.gate.keySet()) {
			BlockPos pos = BlockPos.of(l);
			if (level.getBlockState(pos).is(Blocks.SPRUCE_FENCE)) {
				at.add(pos);
			}
		}
		place(level, at, Blocks.IRON_BARS.defaultBlockState());
		if (!at.isEmpty()) {
			level.playSound(null, at.getFirst(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 1.0F);
		}
	}

	/** The siege over: the barricade down, the door as it was (where its land isn't loaded, once it is: {@link SiegeScars}). */
	static void openGate(ServerLevel level, WarParties.Party party) {
		for (Map.Entry<Long, BlockState> e : party.gate.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			boolean loaded = Loaded.made(level, pos);
			if (loaded && SiegeScars.barricade(level.getBlockState(pos))) {
				level.setBlock(pos, e.getValue(), Block.UPDATE_ALL);
			}
			SiegeScars.gateDown(pos, e.getValue(), !loaded);
		}
		party.gate.clear();
		party.strongGate = false;
	}

	/** The siege over: the levies paid off and gone home. */
	static void payOff(ServerLevel level, WarParties.Party party) {
		for (UUID id : party.levies) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive()) {
				McaCompat.vanish(e);
			}
		}
		party.levies.clear();
	}

	// ---- between the waves -------------------------------------------------------------------------------------------

	/** A wave beaten off: the defenders bound up, and (as the chest runs to it) their quivers filled and the breaches mended. */
	static void resupply(ServerLevel level, WarParties.Party party, Faction target) {
		BlockPos goal = WarParties.goal(level.getServer(), party);
		if (goal == null) {
			return;
		}
		List<Mob> defenders = Sieges.defenders(level, party, target, goal);
		for (Mob m : defenders) {
			if (!Downed.isDowned(m)) {
				m.setHealth(m.getMaxHealth());
			}
		}
		Faction now = Factions.get(level.getServer(), target.id());
		if (now == null || now.treasury() < RESUPPLY_COST) {
			return;
		}
		pay(level.getServer(), target, RESUPPLY_COST);
		arrows(defenders, RESUPPLY_ARROWS);
		repair(level, party, RESUPPLY_MENDS);
	}

	private static void arrows(List<Mob> defenders, int each) {
		for (Mob m : defenders) {
			if (m instanceof Worker w && w.role() == Folk.ARCHER) {
				MobAmmo.give(m, MobAmmo.Kind.ARROWS, each);
			}
		}
	}

	/** Up to {@code most} of what the siege broke put back (where nothing's in the way). How many were. */
	static int repair(ServerLevel level, WarParties.Party party, int most) {
		int done = 0;
		List<Map.Entry<Long, BlockState>> todo = new ArrayList<>(party.breaches.entrySet());
		// from the ground up, so what stands on something has it to stand on
		todo.sort((a, b) -> Integer.compare(BlockPos.of(a.getKey()).getY(), BlockPos.of(b.getKey()).getY()));
		for (Map.Entry<Long, BlockState> e : todo) {
			if (done >= most) {
				break;
			}
			BlockPos pos = BlockPos.of(e.getKey());
			party.breaches.remove(e.getKey());
			boolean loaded = Loaded.made(level, pos);
			// (one whose land isn't loaded is mended when it is)
			SiegeScars.breachDone(pos, e.getValue(), !loaded);
			if (loaded && SiegeScars.mend(level, pos, e.getValue())) {
				done++;
			}
		}
		if (done > 0 && !todo.isEmpty()) {
			BlockPos at = BlockPos.of(todo.getFirst().getKey());
			level.playSound(null, at, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.9F);
		}
		return done;
	}

	// ---- the steward's card ------------------------------------------------------------------------------------------

	/**
	 * (KingdomEvents.audience) {@code player} speaks to {@code folk} (the ruler) of {@code faction} while its seat is
	 * besieged: one sworn to it, its sovereign, or one who stood with it gets the war chest's card. False for anyone else
	 * (the audience goes on as ever).
	 */
	static boolean audience(ServerPlayer player, LivingEntity folk, Faction faction) {
		WarParties.Party party = Sieges.of(faction.id());
		if (party == null || party.phase != WarParties.Phase.SIEGE || party.wave == 0 || !Rulers.is(folk) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		MinecraftServer server = level.getServer();
		if (faction.member(player.getUUID()) == null && !Crowns.rules(server, faction, player.getUUID()) && Sieges.side(party, player) != Sieges.DEFEND) {
			return false;
		}
		open(level, player, folk, party, faction.id());
		return true;
	}

	private static void open(ServerLevel level, ServerPlayer player, LivingEntity folk, WarParties.Party party, int id) {
		Faction f = Factions.get(level.getServer(), id);
		if (f == null) {
			return;
		}
		long chest = f.treasury();
		Component words = Component.translatable("speech.jaymoskingdoms.warchest.open", Money.text(f, Money.worth(chest)));
		List<Menus.Choice> choices = new ArrayList<>();
		choices.add(choice(f, "levy", LEVY_COST * LEVY_HIRE, () -> levies(level, party) + LEVY_HIRE <= LEVY_MOST, sp -> hire(level, party, f, sp)));
		choices.add(choice(f, "mend", MEND_COST, () -> !party.breaches.isEmpty(), sp -> repair(level, party, MEND_BLOCKS)));
		choices.add(choice(f, "arrows", ARROWS_COST, () -> true, sp -> arrows(defenders(level, party, f), ARROWS)));
		choices.add(choice(f, "gate", GATE_COST, () -> !party.strongGate && !party.gate.isEmpty(), sp -> strengthen(level, party)));
		choices.add(choice(f, "draught", DRAUGHT_COST, () -> true, sp -> draught(level, defenders(level, party, f))));
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.warchest.leave"), sp -> {
		}));
		Speech.tell(level, folk, player, words, Speech.GOLD);
		Menus.ask(player, folk, words, choices);
	}

	/**
	 * A line on the card: what it's for and what it costs; picked, it's paid for and done, and the card comes back. Whether
	 * it can be had ({@code can}, the purse) is asked again when it's picked, the card may be stale by then.
	 */
	private static Menus.Choice choice(Faction f, String what, long cost, java.util.function.BooleanSupplier can, Consumer<ServerPlayer> act) {
		Component label = Component.translatable("speech.jaymoskingdoms.warchest." + what, Money.text(f, Money.worth(cost)));
		return Menus.Choice.of(label, can.getAsBoolean() && f.treasury() >= cost, sp -> {
			if (!(sp.level() instanceof ServerLevel level)) {
				return;
			}
			Faction now = Factions.get(level.getServer(), f.id());
			WarParties.Party party = Sieges.of(f.id());
			if (now == null || now.treasury() < cost || party == null || party.phase != WarParties.Phase.SIEGE || !can.getAsBoolean()) {
				return;
			}
			pay(level.getServer(), now, cost);
			act.accept(sp);
			level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
			PathfinderMob ruler = Rulers.of(level, now);
			if (ruler != null && ruler.distanceTo(sp) <= Menus.REACH) {
				open(level, sp, ruler, party, now.id());
			}
		});
	}

	/** The levies still standing ({@code party}'s list, the fallen dropped). */
	private static int levies(ServerLevel level, WarParties.Party party) {
		party.levies.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity e) || !e.isAlive());
		return party.levies.size();
	}

	private static void pay(MinecraftServer server, Faction f, long cost) {
		Factions.update(server, f.id(), x -> x.withTreasury(Math.max(0L, x.treasury() - cost)));
	}

	private static List<Mob> defenders(ServerLevel level, WarParties.Party party, Faction f) {
		BlockPos goal = WarParties.goal(level.getServer(), party);
		return goal == null ? List.of() : Sieges.defenders(level, party, f, goal);
	}

	/** (am-upgrades) The draught: the defenders fight harder and take less for a while. */
	private static void draught(ServerLevel level, List<Mob> defenders) {
		for (Mob m : defenders) {
			m.addEffect(new MobEffectInstance(MobEffects.STRENGTH, DRAUGHT_TICKS, 0));
			m.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DRAUGHT_TICKS, 0));
		}
	}

	/** {@link #LEVY_HIRE} more guards taken on at the hall, kept to the seat until the siege is over. */
	private static void hire(ServerLevel level, WarParties.Party party, Faction f, ServerPlayer by) {
		Castle.Keep keep = Factions.keep(level.getServer(), f.id());
		BlockPos goal = WarParties.goal(level.getServer(), party);
		BlockPos from = keep != null ? keep.hall() : f.seat();
		if (goal == null) {
			return;
		}
		for (int i = 0; i < LEVY_HIRE; i++) {
			BlockPos at = keep != null ? from : level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				from.offset(level.getRandom().nextInt(5) - 2, 0, level.getRandom().nextInt(5) - 2));
			float yaw = level.getRandom().nextFloat() * 360.0F;
			PathfinderMob levy = null;
			if (f.type().isRealm()) {
				Worker w = McaCompat.escort(level, f, at, yaw, LEVY_TASK, LEVY_RADIUS);
				levy = w == null ? null : w.mob();
			}
			if (levy == null) {
				Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
				if (folk == null) {
					continue;
				}
				folk.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
				folk.setup(Folk.GUARD, f, goal, LEVY_RADIUS);
				level.addFreshEntity(folk);
				folk.setTask(LEVY_TASK);
				levy = folk;
			}
			levy.addTag(LEVY_TAG);
			levy.setHomeTo(goal, LEVY_RADIUS);
			party.levies.add(levy.getUUID());
		}
		level.playSound(null, from, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.0F, 1.0F);
		for (Iterator<UUID> it = party.levies.iterator(); it.hasNext();) {
			Entity e = level.getEntity(it.next());
			if (e != null && !e.isAlive()) {
				it.remove();
			}
		}
	}
}
