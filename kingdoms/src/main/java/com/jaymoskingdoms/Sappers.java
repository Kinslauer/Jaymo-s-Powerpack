package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (am-sappers, medieval picks; Armed Mobs' sappers, an idea only) A war party's sappers. Its rammers carry a log up to
 * the besieged's gate (the barricade the war chest shut across the keep's door, {@link WarChest#shutGate}) or, with no
 * gate, to the nearest door, and beat it in. Its keg man carries real powder kegs, bought out of his realm's treasury
 * at the muster ({@link #KEG_COST} each): he digs in under the wall beside the gate (or by a door), sets one in the hole
 * and lights it, and runs. A keg man with none left is a soldier like the rest; no blast but the kegs he carries. Mob
 * griefing off, they stand with the rest. What they break is kept for the war chest's masons ({@link Sieges#broken}).
 */
final class Sappers {
	/** Rammers in a party, and how many kegs its keg man carries (if his realm can pay this much each, emeralds). */
	static final int RAMMERS = 2, KEGS = 2;
	static final long KEG_COST = 6;
	/** A swing of the ram every this many ticks; swings to break a plain gate or door, and an iron one. */
	static final int RAM_SWING = 20, RAM_HITS = 12, RAM_HITS_STRONG = 30;
	/** How near (blocks) a sapper works from, and how far round its goal he looks for a door. */
	static final double WORK_REACH = 2.6;
	static final int DOOR_SEARCH = 20, DOOR_HEIGHT = 4;
	/** Ticks to dig each of the hole's two blocks, the keg's fuse, and how far he runs from it. */
	static final int DIG_TICKS = 60, DIG_DEPTH = 2, FUSE = 80;
	static final double RUN_FROM = 10.0;
	/** What the blast took is looked for this far round the keg, this long (ticks) after it went up. */
	static final int BLAST_REACH = 4, BLAST_LOOK = 10;
	/** How often a sapper with nothing to work on looks again (ticks). */
	static final int LOOK_EVERY = 60;
	/** How fast he goes about it. */
	static final double SPEED = 0.9;

	/** One sapper: his ram or kegs, what he's at, and how far he's got. */
	static final class Sapper {
		final UUID id;
		final boolean ram;
		int kegs;
		@Nullable BlockPos aim, stand;
		int work, dug;
		long lookAt, blowAt = -1;
		final Map<Long, BlockState> before = new HashMap<>();
		/** What he carried in his off hand before (his shield), given back when it's over. */
		ItemStack offhand = ItemStack.EMPTY;

		Sapper(UUID id, boolean ram, int kegs) {
			this.id = id;
			this.ram = ram;
			this.kegs = kegs;
		}

		boolean armed() {
			return this.ram || this.kegs > 0;
		}
	}

	private Sappers() {
	}

	/** At the muster: {@link #RAMMERS} of {@code party}'s guards take up a log, and (if {@code from} can pay) one the kegs. */
	static void muster(ServerLevel level, WarParties.Party party, Faction from) {
		List<PathfinderMob> picked = new ArrayList<>();
		for (UUID id : party.soldiers) {
			if (level.getEntity(id) instanceof PathfinderMob m && m instanceof Worker w && w.role() == Folk.GUARD && !party.crew.contains(id)
				&& !m.entityTags().contains(WarParties.CASTER_TAG) && !m.isPassenger()) {
				picked.add(m);
			}
		}
		int kegs = (int)Math.min(KEGS, from.treasury() / KEG_COST);
		for (int i = 0; i < picked.size() && i < RAMMERS + (kegs > 0 ? 1 : 0); i++) {
			PathfinderMob m = picked.get(picked.size() - 1 - i);
			boolean ram = i < RAMMERS;
			Sapper s = new Sapper(m.getUUID(), ram, ram ? 0 : kegs);
			party.sappers.add(s);
			s.offhand = m.getItemBySlot(EquipmentSlot.OFFHAND).copy();
			m.setItemSlot(EquipmentSlot.OFFHAND, ram ? new ItemStack(Items.SPRUCE_LOG) : new ItemStack(Items.TNT, kegs));
			if (!ram) {
				long cost = kegs * KEG_COST;
				Factions.update(level.getServer(), from.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - cost)));
				// (kept in the world's books, so a restart mid-siege gives them back)
				SiegeScars.kegs(from.id(), cost);
			}
		}
	}

	/** {@code mob} is one of {@code party}'s sappers at his work (a wave on, his ram or a keg in hand): the party walks him, not his brain. */
	static boolean busy(WarParties.Party party, PathfinderMob mob) {
		if (party.phase != WarParties.Phase.SIEGE || party.lull || party.sappers.isEmpty() || !(mob.level() instanceof ServerLevel level)
			|| !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return false;
		}
		for (Sapper s : party.sappers) {
			if (s.id.equals(mob.getUUID())) {
				return s.armed() && s.aim != null || s.blowAt >= 0;
			}
		}
		return false;
	}

	/** One tick of {@code party}'s sappers in a wave at {@code goal}. */
	static void drive(ServerLevel level, WarParties.Party party, Faction from, BlockPos goal, long now) {
		if (party.sappers.isEmpty() || !level.getGameRules().get(GameRules.MOB_GRIEFING)) {
			return;
		}
		Castle.Keep keep = party.target >= 0 ? Factions.keep(level.getServer(), party.target) : null;
		for (Sapper s : party.sappers) {
			if (s.blowAt >= 0 && now >= s.blowAt + FUSE + BLAST_LOOK) {
				// the keg has gone up: what it took is kept for the masons, and he's free for the next
				blasted(level, s);
			}
			if (!(level.getEntity(s.id) instanceof PathfinderMob m) || !m.isAlive() || Downed.isDowned(m)) {
				continue;
			}
			if (s.blowAt >= 0 && s.aim != null) {
				// a keg lit: away from it, until it's gone up
				Vec3 away = m.position().subtract(Vec3.atCenterOf(s.aim)).multiply(1.0, 0.0, 1.0);
				if (away.lengthSqr() < RUN_FROM * RUN_FROM) {
					Vec3 to = m.position().add(away.lengthSqr() < 0.01 ? new Vec3(RUN_FROM, 0, 0) : away.normalize().scale(RUN_FROM));
					m.getNavigation().moveTo(to.x, to.y, to.z, SPEED * 1.3);
				}
				continue;
			}
			if (!s.armed()) {
				continue;
			}
			if (Rulers.target(m) != null) {
				continue;
			}
			if (s.aim == null || !still(level, party, s)) {
				s.aim = null;
				s.work = 0;
				s.dug = 0;
				if (now < s.lookAt) {
					continue;
				}
				s.lookAt = now + LOOK_EVERY;
				aim(level, party, s, keep, goal, m);
				if (s.aim == null) {
					continue;
				}
			}
			BlockPos stand = s.stand != null ? s.stand : s.aim;
			if (m.distanceToSqr(Vec3.atBottomCenterOf(stand)) > WORK_REACH * WORK_REACH) {
				if (m.getNavigation().isDone()) {
					m.getNavigation().moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, SPEED);
				}
				continue;
			}
			m.getNavigation().stop();
			m.getLookControl().setLookAt(Vec3.atCenterOf(s.aim));
			if (s.ram) {
				ram(level, party, s, m, now);
			} else {
				dig(level, party, s, m, now);
			}
		}
	}

	/** What {@code s} works at is still there to work at. */
	private static boolean still(ServerLevel level, WarParties.Party party, Sapper s) {
		BlockState state = level.getBlockState(s.aim);
		if (s.ram) {
			return party.gate.containsKey(s.aim.asLong()) ? !state.isAir() : state.is(BlockTags.WOODEN_DOORS);
		}
		return true;
	}

	/** The next thing for {@code s}: the nearest of the gate's barricade still up, else the nearest door; a keg man, the wall beside it. */
	private static void aim(ServerLevel level, WarParties.Party party, Sapper s, Castle.@Nullable Keep keep, BlockPos goal, PathfinderMob m) {
		s.stand = null;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (long at : party.gate.keySet()) {
			BlockPos pos = BlockPos.of(at);
			if (!level.getBlockState(pos).isAir() && m.distanceToSqr(Vec3.atCenterOf(pos)) < bestD) {
				best = pos;
				bestD = m.distanceToSqr(Vec3.atCenterOf(pos));
			}
		}
		if (s.ram) {
			s.aim = best != null ? best : door(level, party, goal, m);
			return;
		}
		if (keep != null) {
			// beside the keep's door, at the foot of its wall: dig in under it (each keg on the other side from the last)
			Direction in = direction(keep.gate(), keep.hall());
			Direction across = in.getClockWise();
			int side = s.kegs % 2 == 0 ? 1 : -1;
			s.stand = keep.gate().relative(in, 1).relative(across, 4 * side);
			s.aim = s.stand.below();
			return;
		}
		BlockPos door = door(level, party, goal, m);
		if (door != null) {
			Direction out = direction(door, m.blockPosition());
			s.stand = door.relative(out);
			s.aim = s.stand.below();
		}
	}

	private static Direction direction(BlockPos from, BlockPos to) {
		return Direction.getApproximateNearest(to.getX() - from.getX(), 0, to.getZ() - from.getZ());
	}

	/** The nearest wooden door (its lower half) round {@code goal} (a homestead's: only on its own patch), or null. */
	private static @Nullable BlockPos door(ServerLevel level, WarParties.Party party, BlockPos goal, PathfinderMob m) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		int r = party.home != null ? Homesteads.RADIUS : DOOR_SEARCH, h = party.home != null ? Homesteads.HEIGHT : DOOR_HEIGHT;
		for (BlockPos pos : BlockPos.betweenClosed(goal.offset(-r, -h, -r), goal.offset(r, h, r))) {
			if (!Loaded.made(level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.is(BlockTags.WOODEN_DOORS) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
				double d = m.distanceToSqr(Vec3.atCenterOf(pos));
				if (d < bestD) {
					best = pos.immutable();
					bestD = d;
				}
			}
		}
		return best;
	}

	/** A swing of the ram; enough of them and it's in. */
	private static void ram(ServerLevel level, WarParties.Party party, Sapper s, PathfinderMob m, long now) {
		if (now % RAM_SWING != 0) {
			return;
		}
		BlockState state = level.getBlockState(s.aim);
		boolean iron = state.is(Blocks.IRON_BARS);
		int hits = iron ? RAM_HITS_STRONG : RAM_HITS;
		m.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		level.playSound(null, s.aim, iron ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F,
			0.8F + level.getRandom().nextFloat() * 0.2F);
		s.work++;
		level.destroyBlockProgress(m.getId(), s.aim, Math.min(9, s.work * 10 / hits));
		if (s.work < hits) {
			return;
		}
		level.destroyBlockProgress(m.getId(), s.aim, -1);
		if (!party.gate.containsKey(s.aim.asLong())) {
			// a door: both halves kept for the masons
			Sieges.broken(level, s.aim, state);
			Sieges.broken(level, s.aim.above(), level.getBlockState(s.aim.above()));
		}
		level.playSound(null, s.aim, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 0.9F);
		// (a homestead's door drops, nobody mends it; a seat's is put back by its masons)
		level.destroyBlock(s.aim, party.home != null, m);
		s.aim = null;
		s.work = 0;
	}

	/** The keg man digs his hole ({@link #DIG_DEPTH} deep), sets a keg in it and lights it. */
	private static void dig(ServerLevel level, WarParties.Party party, Sapper s, PathfinderMob m, long now) {
		BlockPos at = s.aim.below(s.dug);
		BlockState state = level.getBlockState(at);
		if (s.dug < DIG_DEPTH && !state.isAir() && state.getFluidState().isEmpty() && state.getDestroySpeed(level, at) >= 0.0F) {
			m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			level.playSound(null, at, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.8F, 0.8F);
			// (he's seen to once a party step, WarParties#TICK ticks)
			s.work += WarParties.TICK;
			level.destroyBlockProgress(m.getId(), at, Math.min(9, s.work * 10 / DIG_TICKS));
			if (s.work >= DIG_TICKS) {
				level.destroyBlockProgress(m.getId(), at, -1);
				Sieges.broken(level, at, state);
				level.destroyBlock(at, party.home != null, m);
				s.work = 0;
				s.dug++;
			}
			return;
		}
		// the hole dug (or as deep as it goes): a keg in it, lit
		BlockPos keg = s.aim.below(Math.max(0, s.dug - 1));
		s.before.clear();
		for (BlockPos pos : BlockPos.betweenClosed(keg.offset(-BLAST_REACH, -BLAST_REACH, -BLAST_REACH), keg.offset(BLAST_REACH, BLAST_REACH, BLAST_REACH))) {
			BlockState there = level.getBlockState(pos);
			if (!there.isAir() && there.getFluidState().isEmpty()) {
				s.before.put(pos.asLong(), there);
			}
		}
		PrimedTnt tnt = new PrimedTnt(level, keg.getX() + 0.5, keg.getY(), keg.getZ() + 0.5, m);
		tnt.setFuse(FUSE);
		level.addFreshEntity(tnt);
		level.playSound(null, keg, SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
		s.kegs--;
		SiegeScars.kegs(party.faction, -KEG_COST);
		s.blowAt = now;
		s.aim = keg;
		s.dug = 0;
		s.work = 0;
		m.setItemSlot(EquipmentSlot.OFFHAND, s.kegs > 0 ? new ItemStack(Items.TNT, s.kegs) : ItemStack.EMPTY);
	}

	/** After a keg went up: what it took is kept for the masons. */
	private static void blasted(ServerLevel level, Sapper s) {
		for (Map.Entry<Long, BlockState> e : s.before.entrySet()) {
			BlockPos pos = BlockPos.of(e.getKey());
			if (level.getBlockState(pos).isAir()) {
				Sieges.broken(level, pos, e.getValue());
			}
		}
		s.before.clear();
		s.blowAt = -1;
		s.aim = null;
	}

	/** The siege over: the sappers put down their logs and what kegs they have left go back into the realm's treasury. */
	static void end(ServerLevel level, WarParties.Party party) {
		long back = 0, carried = 0;
		for (Sapper s : party.sappers) {
			carried += s.kegs * KEG_COST;
			if (level.getEntity(s.id) instanceof PathfinderMob m && m.isAlive()) {
				// the log or kegs put down, his shield taken up again
				m.setItemSlot(EquipmentSlot.OFFHAND, s.offhand);
				back += s.kegs * KEG_COST;
			}
			if (s.aim != null) {
				level.destroyBlockProgress(level.getEntity(s.id) != null ? level.getEntity(s.id).getId() : 0, s.aim, -1);
			}
		}
		party.sappers.clear();
		SiegeScars.kegs(party.faction, -carried);
		long refund = back;
		if (refund > 0 && Factions.get(level.getServer(), party.faction) != null) {
			Factions.update(level.getServer(), party.faction, f -> f.withTreasury(f.treasury() + refund));
		}
	}
}
