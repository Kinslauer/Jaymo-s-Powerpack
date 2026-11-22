package com.jaymoskitchen.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Ash left where a burnt-away corpse lay: 1-8 layers (a zombie a small heap, a ghast or giant a big pile),
 * placed snow-style on the ground where it fell. Shaped like its layers, so it doesn't stand as a full block.
 * <p>
 * Andy: "the ashes should blow away in the wind unless collected". Ash under the open sky loses a layer now and then,
 * blown off as a puff of ash (Jaymo's Kingdoms carries the puff along its wind), sooner the harder the wind blows
 * ({@code Winds.strength}, read by reflection; an even breeze without Kingdoms) and sooner still in the rain, until
 * nothing is left. Under a roof it stays. Broken, it's gathered as an ash pile item, as before.
 */
public class AshPileBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    /** The chance each random tick (about one a minute a block) that the open air takes a layer, at an even breeze; in rain times this. */
    static final float BLOW_CHANCE = 0.35F, RAIN = 2.0F;

    public AshPileBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Block.box(0, 0, 0, 16, state.getValue(LAYERS) * 2, 16);
    }

    /** Like snow: you sink one layer in, and a single layer doesn't stop you at all. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int layers = state.getValue(LAYERS);
        return layers <= 1 ? Shapes.empty() : Block.box(0, 0, 0, 16, (layers - 1) * 2, 16);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** The open air takes a layer now and then, harder in wind and rain; the last layer goes and leaves bare ground. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.canSeeSky(pos.above())) {
            return;
        }
        float chance = BLOW_CHANCE * wind(level, pos) * (level.isRainingAt(pos.above()) ? RAIN : 1.0F);
        if (random.nextFloat() >= chance) {
            return;
        }
        int layers = state.getValue(LAYERS);
        level.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + layers * 0.125, pos.getZ() + 0.5, 6 + layers * 2, 0.4, 0.1, 0.4, 0.02);
        level.setBlockAndUpdate(pos, layers > 1 ? state.setValue(LAYERS, layers - 1) : Blocks.AIR.defaultBlockState());
    }

    private static java.lang.reflect.@org.jspecify.annotations.Nullable Method strength;
    private static boolean looked;

    /** How hard Jaymo's Kingdoms' wind blows here (about 0.45 to 1.5), or 1 without it. */
    private static float wind(Level level, BlockPos pos) {
        if (!looked) {
            looked = true;
            try {
                strength = Class.forName("com.jaymoskingdoms.sailing.Winds").getMethod("strength", Level.class, double.class, double.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                strength = null;
            }
        }
        if (strength == null) {
            return 1.0F;
        }
        try {
            return ((Number) strength.invoke(null, level, pos.getX() + 0.5, pos.getZ() + 0.5)).floatValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1.0F;
        }
    }
}
