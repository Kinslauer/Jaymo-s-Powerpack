package com.jaymoskitchen.corpse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
 * <p>
 * Andy: "ashpiles should disintegrate into dust if you punch them, and they should resemble mounded piles, adding more
 * for larger burned bodies. like the entrails, not sheets, until it starts to blow away and flatten out." Fresh ash lies
 * heaped (the mound models, {@code kitchen/tools/gen_ash_piles.py}: deeper ash a taller heap with more little heaps
 * round it); once the wind has taken a layer it's {@link #BLOWN} and lies flat, snow-style, as it always did. Punched
 * (anything but a shovel in hand) it crumbles away into dust; a shovel still gathers it.
 */
public class AshPileBlock extends Block {
    public static final IntegerProperty LAYERS = BlockStateProperties.LAYERS;
    /** The wind has started on it: it lies flat instead of heaped. */
    public static final BooleanProperty BLOWN = BooleanProperty.create("blown");
    /** The chance each random tick (about one a minute a block) that the open air takes a layer, at an even breeze; in rain times this. */
    static final float BLOW_CHANCE = 0.35F, RAIN = 2.0F;

    public AshPileBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LAYERS, 1).setValue(BLOWN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS, BLOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int layers = state.getValue(LAYERS);
        if (!state.getValue(BLOWN)) {
            // the heap (as gen_ash_piles.py builds it): its main mound, round the middle
            double r = Math.min(8.0, 3.0 + layers * 0.55);
            return Block.box(8 - r, 0, 8 - r, 8 + r, Math.min(12.0, 1.5 + layers * 1.3), 8 + r);
        }
        return Block.box(0, 0, 0, 16, layers * 2, 16);
    }

    /** Like snow: you sink one layer in, and a single layer doesn't stop you at all. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(BLOWN)) {
            // loose heaped ash gives underfoot
            return Shapes.empty();
        }
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
        level.setBlockAndUpdate(pos, layers > 1 ? state.setValue(LAYERS, layers - 1).setValue(BLOWN, true) : Blocks.AIR.defaultBlockState());
    }

    /** Punched (no shovel in hand): it crumbles into dust and is gone, nothing gathered. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!(level instanceof ServerLevel server) || player.isCreative() || player.getMainHandItem().is(ItemTags.SHOVELS)) {
            return;
        }
        int layers = state.getValue(LAYERS);
        double top = state.getValue(BLOWN) ? layers * 0.125 : Math.min(0.75, 0.1 + layers * 0.08);
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + top * 0.5, pos.getZ() + 0.5,
            10 + layers * 4, 0.3, top * 0.4, 0.3, 0.05);
        server.sendParticles(ParticleTypes.ASH, pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5, 12 + layers * 4, 0.35, 0.15, 0.35, 0.02);
        server.sendParticles(ParticleTypes.WHITE_ASH, pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5, 6 + layers * 2, 0.3, 0.2, 0.3, 0.02);
        server.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.6F, 1.4F);
        server.removeBlock(pos, false);
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
