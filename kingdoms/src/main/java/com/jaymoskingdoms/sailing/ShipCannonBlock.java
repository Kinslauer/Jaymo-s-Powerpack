package com.jaymoskingdoms.sailing;

import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.18) A ship's gun on its carriage, its muzzle the way it faces. Ram in gunpowder, then round shot (or WeaponMod's
 * cannonball), and touch it off with flint and steel or a fire charge: it fires a {@link ShipShot} that holes ships.
 * Works ashore and aboard, laid as high as its gunner looks; at the helm, a left click fires every loaded gun on the
 * side you look to, a broadside.
 * (AlekiShips, MIT, has ship cannons loaded by hand; ours are our own.)
 */
public final class ShipCannonBlock extends Block {
	public enum Load implements StringRepresentable {
		EMPTY, POWDER, SHOT,
		/** (0.7.490) Loaded with heavy shot, an explosive shell, chain shot, grape. ({@code SHOT} is round shot.) */
		HEAVY, SHELL, CHAIN, GRAPE;

		/** Whether a shot is in her. */
		public boolean loaded() {
			return this.ordinal() >= SHOT.ordinal();
		}

		@Override
		public String getSerializedName() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * How far above level the barrel's laid (degrees): as high or low as its gunner looks, within these, and this with
	 * no one at it; and how far past the block's face the shot leaves it.
	 */
	static final float ELEVATION = 1.5F, LOWEST = -6.0F, HIGHEST = 15.0F;
	static final double MUZZLE = 0.9;
	/** (0.7.490) How hard a ship is shoved back by a gun firing (blocks, taken up at her next move). */
	static final double RECOIL = 0.04;
	/**
	 * (0.7.490) How long (ticks) a gun that's fired stays run back on its carriage ({@link #RECOILED}, its model slid back
	 * along the deck) before it's run out again.
	 */
	static final int RECOIL_TICKS = 12;
	/** Balkon's WeaponMod, whose cannonball it also takes (only by its name, so it needn't be loaded). */
	static final String WEAPONMOD_BALL = "weaponmod:cannonball";

	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final EnumProperty<Load> LOAD = EnumProperty.create("load", Load.class);
	/** (0.7.490) Just fired: run back on its carriage by the recoil (a model slid back), for {@link #RECOIL_TICKS}. */
	public static final BooleanProperty RECOILED = BooleanProperty.create("recoiled");
	private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 10, 14);

	public ShipCannonBlock(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOAD, Load.EMPTY).setValue(RECOILED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LOAD, RECOILED);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Whether this is something it fires: our shot of any kind, or WeaponMod's cannonball. */
	static boolean shot(ItemStack stack) {
		return ShotKind.of(stack) != null;
	}

	/** What {@code player} does to a gun in this state with {@code stack}: the gun's next state, and whether it fires. */
	record Handled(BlockState next, boolean fires) {
	}

	/** Loads or lights a gun (null: nothing doing, and they're told what it wants). Uses up what it takes. */
	static @Nullable Handled handle(BlockState state, ItemStack stack, Player player, InteractionHand hand) {
		Load load = state.getValue(LOAD);
		if (stack.is(Items.GUNPOWDER)) {
			if (load != Load.EMPTY) {
				tell(player, load == Load.POWDER ? "cannon.wants_shot" : "cannon.loaded");
				return null;
			}
			stack.consume(1, player);
			return new Handled(state.setValue(LOAD, Load.POWDER), false);
		}
		if (shot(stack)) {
			if (load != Load.POWDER) {
				tell(player, load == Load.EMPTY ? "cannon.powder_first" : "cannon.loaded");
				return null;
			}
			ShotKind kind = ShotKind.of(stack);
			stack.consume(1, player);
			return new Handled(state.setValue(LOAD, kind == null ? Load.SHOT : kind.load()), false);
		}
		if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE)) {
			if (!load.loaded()) {
				tell(player, "cannon.not_loaded");
				return null;
			}
			if (stack.is(Items.FIRE_CHARGE)) {
				stack.consume(1, player);
			} else {
				stack.hurtAndBreak(1, player, hand);
			}
			return new Handled(state.setValue(LOAD, Load.EMPTY).setValue(RECOILED, true), true);
		}
		tell(player, switch (load) {
			case EMPTY -> "cannon.powder_first";
			case POWDER -> "cannon.wants_shot";
			default -> "cannon.loaded";
		});
		return null;
	}

	private static void tell(Player player, String key) {
		if (player instanceof ServerPlayer p) {
			p.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms." + key));
		}
	}

	/** The way its shot leaves it, its muzzle pointing (fx, fz) in the world, laid as {@code gunner} looks. */
	static Vec3 aim(double fx, double fz, @Nullable Player gunner) {
		float elevation = gunner == null ? ELEVATION : net.minecraft.util.Mth.clamp(-gunner.getXRot(), LOWEST, HIGHEST);
		double up = Math.tan(Math.toRadians(elevation));
		return new Vec3(fx, up, fz).normalize();
	}

	/** Fires: the bang, the smoke, and the ball. {@code centre} is its block's middle; {@code ship} doesn't hit itself. */
	static void fire(ServerLevel level, Vec3 centre, double fx, double fz, @Nullable Player gunner, @Nullable Ship ship, ShotKind kind) {
		fireAlong(level, centre, aim(fx, fz, gunner), gunner, ship, kind);
	}

	/** (0.7.42) Fires along {@code dir} (a ship's crew lay their own guns), {@code gunner}'s shot. */
	static void fireAlong(ServerLevel level, Vec3 centre, Vec3 dir, net.minecraft.world.entity.@Nullable Entity gunner, @Nullable Ship ship, ShotKind kind) {
		double flat = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
		double fx = flat < 1.0E-4 ? 0.0 : dir.x / flat, fz = flat < 1.0E-4 ? 0.0 : dir.z / flat;
		Vec3 muzzle = centre.add(fx * MUZZLE, 0.15, fz * MUZZLE);
		ShipShot.fire(level, gunner, ship, muzzle, dir, kind);
		level.playSound(null, muzzle.x, muzzle.y, muzzle.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.5F, 0.6F);
		// (0.7.490) the muzzle flash, a wall of smoke rolling out ahead of her and a puff drifting back off the breech
		level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE9A8), muzzle.x + fx * 0.3, muzzle.y, muzzle.z + fz * 0.3, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x + fx * 0.6, muzzle.y, muzzle.z + fz * 0.6, 22, 0.35, 0.3, 0.35, 0.06);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x + fx * 1.4, muzzle.y + 0.2, muzzle.z + fz * 1.4, 4, 0.2, 0.15, 0.2, 0.01);
		level.sendParticles(ParticleTypes.SMOKE, centre.x - fx * 0.5, centre.y + 0.2, centre.z - fz * 0.5, 6, 0.2, 0.15, 0.2, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.12, 0.12, 0.12, 0.06);
		level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x + fx * 0.8, muzzle.y, muzzle.z + fz * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
		if (ship != null) {
			// (0.7.490) the recoil: she heels a hair the other way
			ship.tow(new Vec3(-fx * RECOIL, 0.0, -fz * RECOIL));
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (!(level instanceof ServerLevel server)) {
			return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.SUCCESS;
		}
		if (stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		Handled done = handle(state, stack, player, hand);
		if (done == null) {
			return InteractionResult.SUCCESS;
		}
		level.setBlock(pos, done.next(), Block.UPDATE_ALL);
		if (done.fires()) {
			level.scheduleTick(pos, this, RECOIL_TICKS);
			Direction f = state.getValue(FACING);
			fire(server, Vec3.atCenterOf(pos), f.getStepX(), f.getStepZ(), player, null, ShotKind.of(state.getValue(LOAD)) == null ? ShotKind.ROUND
				: ShotKind.of(state.getValue(LOAD)));
		} else {
			level.playSound(null, pos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.8F, 0.7F);
		}
		return InteractionResult.SUCCESS;
	}

	/** (0.7.490) A gun set down still run back (off a ship broken up just after she fired) is run out again soon. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
		super.onPlace(state, level, pos, old, moved);
		if (state.getValue(RECOILED) && !level.isClientSide()) {
			level.scheduleTick(pos, this, RECOIL_TICKS);
		}
	}

	/** (0.7.490) The gun's crew run it out again after its recoil. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(RECOILED)) {
			level.setBlock(pos, state.setValue(RECOILED, false), Block.UPDATE_ALL);
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (!level.isClientSide()) {
			tell(player, switch (state.getValue(LOAD)) {
				case EMPTY -> "cannon.powder_first";
				case POWDER -> "cannon.wants_shot";
				default -> "cannon.loaded";
			});
		}
		return InteractionResult.SUCCESS;
	}
}
