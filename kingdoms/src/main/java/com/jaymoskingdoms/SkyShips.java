package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.BombHatchBlock;
import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.HelmBlock;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Sailing;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.ShipCannonBlock;
import com.jaymoskingdoms.sailing.ShotKind;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Airships that fight (the medieval picks' sky rows), shared by the sky pirates ({@link SkyPirates}) and the realms' patrol
 * airships ({@link AirPatrols}). The way their crews fly them follows Valkyrien Pirates (JSJBDEV, MIT): its pirate ships
 * sail a pattern of helm orders about what they've marked (its "circle" pattern: way on and the helm held over), its gunners
 * serve the guns, and killing the helmsman stops the ship. Our own code, on our own ships ({@link Ship}); no Valkyrien Skies.
 * <ul>
 * <li><b>Her design</b> ({@link #design}): a railed deck under a bag of wool on four posts (as the steam towns' airships are,
 * {@code SteamBuildings#airship}), bigger, with a gun or two each side, a hatch in her deck amidships, her hold's chest and
 * two barrels of stores aft.</li>
 * <li><b>Flying her</b> ({@link #fly}): her captain sets her heading, her cloth and the height she keeps, never lower than
 * {@link #CLEAR} over the ground ahead; {@link #circling} is the heading that takes her round a mark.</li>
 * <li><b>Powder kegs</b> (vp-bombs): a keg (TNT) out of her hold let fall through the hatch ({@link #dropKeg}), its fuse cut
 * for the fall. Her crew drop them over what they've come down on; a player on her deck does it by using the hatch
 * ({@link #hatch}). No keg in her hold, nothing falls.</li>
 * <li><b>Her guns</b> ({@link #charge}): her gunners load from her hold, powder and shot spent, as a ship's crew at sea never
 * have to; out of either, the guns go quiet.</li>
 * </ul>
 */
public final class SkyShips {
	// ---- tuning ----
	/** Her deck (blocks across and along), and the height of the bag's posts over it. */
	static final int WIDTH = 7, LENGTH = 13, POSTS = 3;
	/** {@link Crew#flag}: a sky pirate's airship, a realm's patrol. */
	public static final String PIRATE = "sky", PATROL = "patrol";
	/** A keg's blast (a block of TNT's is 4), its fuse at least and at most (ticks), and the fall it's cut for (blocks a tick, a tick). */
	static final float KEG_POWER = 2.5F;
	static final int KEG_FUSE_LEAST = 12, KEG_FUSE_MOST = 120;
	static final double KEG_GRAVITY = 0.04;
	/** Her gunners lay their guns this far below level at most (degrees), shooting down from the sky. */
	public static final float GUN_LOWEST = -55.0F;
	/** She keeps her keel at least this high over the ground under her and ahead (blocks). */
	static final int CLEAR = 12;
	/** Circling a mark: how far round the ring ahead of her she steers for (radians). */
	static final double LEAD = 0.55;

	private SkyShips() {
	}

	/** One block of her design: where (x across, y up from her deck, z along her, her bow at z = 0) and what. */
	record Placed(BlockPos at, BlockState state) {
	}

	/** Whether {@code crew} flies an airship (a sky pirate's, or a patrol). */
	public static boolean sky(Crew crew) {
		return PIRATE.equals(crew.flag) || PATROL.equals(crew.flag);
	}

	// ---- her design ------------------------------------------------------------------------------------------------------

	/**
	 * The airship ({@link #WIDTH} by {@link #LENGTH}, her bow at z = 0, her wheel aft facing it): a deck of {@code planks}
	 * railed with {@code rail}, {@code guns} guns a side ({@code guns} 0: rails all round), a hatch amidships, her hold's chest
	 * and two barrels aft, a lantern at the bow, and over her on four posts a bag of {@code wool} with a band of {@code band}
	 * round it: cloth enough to lift her ({@code Hull#airworthy}).
	 */
	static List<Placed> design(BlockState planks, BlockState rail, BlockState wool, BlockState band, int guns) {
		List<Placed> out = new ArrayList<>();
		int last = LENGTH - 1, right = WIDTH - 1, mid = WIDTH / 2;
		List<Integer> gunZ = guns >= 2 ? List.of(4, 8) : guns == 1 ? List.of(6) : List.of();
		for (int z = 0; z <= last; z++) {
			for (int x = 0; x <= right; x++) {
				if (z == 0 && (x == 0 || x == right)) {
					continue;
				}
				boolean hatch = x == mid && z == LENGTH / 2;
				out.add(new Placed(new BlockPos(x, 0, z), hatch ? Sailing.BOMB_HATCH.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP)
					: planks));
				if (z != 0 && z != last && x != 0 && x != right) {
					continue;
				}
				if ((x == 0 || x == right) && gunZ.contains(z)) {
					out.add(new Placed(new BlockPos(x, 1, z), Sailing.CANNON.defaultBlockState()
						.setValue(ShipCannonBlock.FACING, x == 0 ? Direction.WEST : Direction.EAST)));
				} else {
					out.add(new Placed(new BlockPos(x, 1, z), rail));
				}
			}
		}
		for (int[] p : new int[][] {{0, 2}, {right, 2}, {0, last - 1}, {right, last - 1}}) {
			for (int y = 2; y <= POSTS + 1; y++) {
				out.add(new Placed(new BlockPos(p[0], y, p[1]), rail));
			}
		}
		out.add(new Placed(new BlockPos(mid, 1, last - 2), Sailing.HELM.defaultBlockState().setValue(HelmBlock.FACING, Direction.NORTH)));
		out.add(new Placed(new BlockPos(1, 1, last - 1), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST)));
		out.add(new Placed(new BlockPos(right - 1, 1, last - 1), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)));
		out.add(new Placed(new BlockPos(right - 1, 1, last - 2), Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)));
		out.add(new Placed(new BlockPos(mid, 1, 1), Blocks.LANTERN.defaultBlockState()));
		for (int y = POSTS + 2; y <= POSTS + 4; y++) {
			int in = y == POSTS + 4 ? 1 : 0;
			for (int x = in; x <= right - in; x++) {
				for (int z = in; z <= last - in; z++) {
					boolean stripe = y == POSTS + 3 && (z == 0 || z == last || x == 0 || x == right);
					out.add(new Placed(new BlockPos(x, y, z), stripe ? band : wool));
				}
			}
		}
		return out;
	}

	/** The turn that takes her design's bow (north) to {@code bow}. */
	private static Rotation turn(Direction bow) {
		for (Rotation r : Rotation.values()) {
			if (r.rotate(Direction.NORTH) == bow) {
				return r;
			}
		}
		return Rotation.NONE;
	}

	/**
	 * Lays {@code design} in the open sky, her deck's middle at {@code middle}, her bow toward {@code bow}; where every block
	 * went is added to {@code where}. Her helm, or null if anything's in the way (nothing is laid then).
	 */
	static @Nullable BlockPos lay(ServerLevel level, List<Placed> design, BlockPos middle, Direction bow, List<BlockPos> where) {
		Rotation turn = turn(bow);
		List<BlockPos> at = new ArrayList<>();
		for (Placed p : design) {
			BlockPos to = middle.offset(p.at().offset(-WIDTH / 2, 0, -LENGTH / 2).rotate(turn));
			BlockState there = level.getBlockState(to);
			if (!Loaded.made(level, to) || !there.isAir() && !there.canBeReplaced() || !there.getFluidState().isEmpty()) {
				return null;
			}
			at.add(to);
		}
		BlockPos helm = null;
		for (int i = 0; i < design.size(); i++) {
			BlockState state = design.get(i).state().rotate(turn);
			level.setBlock(at.get(i), state, Block.UPDATE_CLIENTS);
			if (state.getBlock() instanceof HelmBlock) {
				helm = at.get(i);
			}
		}
		// fences joined to their neighbours
		for (BlockPos p : at) {
			BlockState state = level.getBlockState(p);
			BlockState next = Block.updateFromNeighbourShapes(state, level, p);
			if (next.hasProperty(BlockStateProperties.WATERLOGGED)) {
				next = next.setValue(BlockStateProperties.WATERLOGGED, false);
			}
			if (next != state) {
				level.setBlock(p, next, Block.UPDATE_CLIENTS);
			}
		}
		where.addAll(at);
		return helm;
	}

	/**
	 * {@code design} laid in the sky at {@code middle} and cast off at once with {@code crew}, manned for {@code faction} by
	 * {@code complement}, {@code stores} in her barrels and hold. Null (and nothing left behind) if there was no room for her.
	 */
	static @Nullable Ship launch(ServerLevel level, List<Placed> design, BlockPos middle, Direction bow, Crew crew, Faction faction,
		Corsairs.Complement complement, List<ItemStack> stores) {
		List<BlockPos> where = new ArrayList<>();
		BlockPos helm = lay(level, design, middle, bow, where);
		if (helm == null) {
			return null;
		}
		// her stores into her barrels (her chest is her hold, the share each player finds there)
		List<ItemStack> left = new ArrayList<>();
		for (ItemStack s : stores) {
			left.add(s.copy());
		}
		for (BlockPos p : where) {
			if (level.getBlockState(p).getBlock() instanceof BarrelBlock && level.getBlockEntity(p) instanceof Container barrel) {
				for (int slot = 0; slot < barrel.getContainerSize() && !left.isEmpty(); slot++) {
					barrel.setItem(slot, left.removeFirst());
				}
				barrel.setChanged();
			}
		}
		Ship ship = Fleet.castOff(level, helm, crew);
		if (ship == null || !ship.flying()) {
			if (ship != null) {
				ship.discard();
			}
			for (BlockPos p : where) {
				level.removeBlockEntity(p);
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
			}
			return null;
		}
		Corsairs.man(level, ship, crew, faction, complement);
		return ship;
	}

	// ---- flying her ------------------------------------------------------------------------------------------------------

	/** Her helm for heading {@code yaw} with {@code cloth} set (0 to 1), her keel making for {@code height}, but clear of the ground. */
	static Input fly(ServerLevel level, Ship ship, float yaw, double cloth, double height) {
		double want = Math.max(height, floor(level, ship));
		Input rudder = Fleet.steer(ship, yaw, cloth, false, false);
		boolean up = ship.aloft() < want - 0.15, down = ship.aloft() > want + 0.15;
		return new Input(rudder.forward(), rudder.backward(), rudder.left(), rudder.right(), up, false, down);
	}

	/** The lowest her keel may go here: {@link #CLEAR} over the highest ground under her and ahead. */
	static double floor(ServerLevel level, Ship ship) {
		return SteamAirships.groundAhead(level, ship) + CLEAR;
	}

	/** The heading that takes her round {@code mark} at {@code radius}: toward a point a little ahead of her on that ring. */
	static float circling(Ship ship, Vec3 mark, double radius) {
		double a = Math.atan2(ship.getZ() - mark.z, ship.getX() - mark.x) + LEAD;
		return Fleet.bearing(ship, mark.x + Math.cos(a) * radius, mark.z + Math.sin(a) * radius);
	}

	/** How far she lies from {@code at}, across the ground (blocks). */
	static double flat(Ship ship, Vec3 at) {
		double dx = at.x - ship.getX(), dz = at.z - ship.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- powder kegs -----------------------------------------------------------------------------------------------------

	static boolean keg(ItemStack stack) {
		return stack.is(Items.TNT);
	}

	/** Where her hatch is, in her frame: the bomb hatch in her deck (null: she has none). */
	static @Nullable Vec3 hatchOf(Ship ship) {
		Hull h = ship.hull();
		if (h == null) {
			return null;
		}
		for (Ship.Piece p : h.pieces) {
			if (p.blockState().getBlock() instanceof BombHatchBlock) {
				return new Vec3(p.x(), p.y(), p.z());
			}
		}
		return null;
	}

	/**
	 * A keg out of her hold let fall through her hatch at {@code local} (her frame), lit by {@code by}: its fuse cut to burst
	 * as it lands on the ground under her. False (nothing falls) if her hold has none.
	 */
	static boolean dropKeg(ServerLevel level, Ship ship, Vec3 local, @Nullable LivingEntity by) {
		ItemStack keg = ship.fromHold(SkyShips::keg, 1);
		Vec3 at = ship.toWorld(local.x, local.y - 0.7, local.z);
		level.playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
		if (keg.isEmpty()) {
			level.playSound(null, at.x, at.y + 1.0, at.z, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.9F);
			return false;
		}
		BlockPos column = BlockPos.containing(at);
		double ground = Loaded.made(level, column) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) : at.y - 20.0;
		double fall = Math.max(1.0, at.y - ground);
		int fuse = Mth.clamp(Mth.ceil(Math.sqrt(2.0 * fall / KEG_GRAVITY)) + 3, KEG_FUSE_LEAST, KEG_FUSE_MOST);
		PrimedTnt tnt = new PrimedTnt(level, at.x, at.y, at.z, by);
		// (she carries it on with her way as it leaves her)
		tnt.setDeltaMovement(ship.drift().x, -0.05, ship.drift().z);
		tnt.setFuse(fuse);
		((com.jaymoskingdoms.mixin.hordes.PrimedTntAccessor)tnt).jaymoskingdoms$setExplosionPower(KEG_POWER);
		level.addFreshEntity(tnt);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 0.9F);
		return true;
	}

	/** {@code player} uses the bomb hatch at (x, y, z) in a balloon's deck ({@link BombHatchBlock}): a keg from her hold let fall through it. */
	public static void hatch(ServerLevel level, Ship ship, int x, int y, int z, ServerPlayer player) {
		dropKeg(level, ship, new Vec3(x, y, z), player);
	}

	// ---- her guns ----------------------------------------------------------------------------------------------------------

	/**
	 * A gun's charge from her hold: a gunpowder and a shot of {@code kind} (or, with none of that, any shot there is) spent.
	 * The kind loaded, or null if her hold is out of powder or shot (nothing is taken then).
	 */
	static @Nullable ShotKind charge(Ship ship, ShotKind kind) {
		if (ship.inHold(s -> s.is(Items.GUNPOWDER)) <= 0) {
			return null;
		}
		ShotKind loaded = ship.inHold(s -> s.is(kind.item())) > 0 ? kind : null;
		if (loaded == null) {
			for (ShotKind k : ShotKind.values()) {
				if (ship.inHold(s -> s.is(k.item())) > 0) {
					loaded = k;
					break;
				}
			}
		}
		if (loaded == null) {
			return null;
		}
		ShotKind ball = loaded;
		ship.fromHold(s -> s.is(Items.GUNPOWDER), 1);
		ship.fromHold(s -> s.is(ball.item()), 1);
		return loaded;
	}
}
