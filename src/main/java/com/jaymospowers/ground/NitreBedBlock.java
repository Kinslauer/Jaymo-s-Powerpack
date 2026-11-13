package com.jaymospowers.ground;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Andy: "yeah add it" (the saltpetre bed). How saltpetre was really made before anyone mined it: a heap of earth,
 * straw and dung kept under a roof, fed, turned now and then, until the nitre grows white on it; then the nitre earth is
 * scraped off and boiled down (smelted, {@code recipe/saltpetre_from_nitre_earth*.json}).
 * <ul>
 * <li><b>Feeding</b>: use manure (Jaymo's Kitchen's), rotten flesh, bone meal or wheat (straw) on it ({@link #FEEDS});
 * droppings and scraps that land on top of it are worked in on their own, so a bed under a barn feeds itself.</li>
 * <li><b>Growing</b>: while fed and roofed it ripens a stage now and then ({@link #RIPEN_CHANCE}), each stage eating one
 * measure of feed. Turned with a shovel it ripens twice as fast for the next stage. Under open sky it doesn't grow, and
 * rain falling on it washes a stage back out: the real reason nitre beds stood in sheds.</li>
 * <li><b>Harvest</b>: ripe, a shovel scrapes off {@link #EARTH_MIN}-{@link #EARTH_MAX} nitre earth, and the bed starts
 * again on what feed it has left. A bed nobody tends simply stops; nothing is lost.</li>
 * </ul>
 */
public final class NitreBedBlock extends Block {
	public static final int MAX_RIPE = 7, MAX_FILL = 8;
	public static final IntegerProperty RIPE = IntegerProperty.create("ripe", 0, MAX_RIPE);
	public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, MAX_FILL);
	public static final BooleanProperty TURNED = BooleanProperty.create("turned");
	/** A random tick's chance (about once a minute a block) to ripen a stage; twice this when turned. */
	static final float RIPEN_CHANCE = 0.2F;
	/** Nitre earth a ripe bed gives to the shovel. */
	static final int EARTH_MIN = 3, EARTH_MAX = 5;
	/** What feeds a bed and how many measures it's worth. Kitchen's manure goes by id, so the bed works without Kitchen. */
	static final Map<Identifier, Integer> FEEDS = Map.of(
		Identifier.fromNamespaceAndPath("jaymoskitchen", "manure"), 3,
		Identifier.withDefaultNamespace("rotten_flesh"), 1,
		Identifier.withDefaultNamespace("bone_meal"), 1,
		Identifier.withDefaultNamespace("wheat"), 1);

	public NitreBedBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(RIPE, 0).setValue(FILL, 0).setValue(TURNED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(RIPE, FILL, TURNED);
	}

	/** How many measures this stack feeds a bed, 0 if none. */
	static int feedOf(ItemStack stack) {
		return stack.isEmpty() ? 0 : FEEDS.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()), 0);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		int feed = feedOf(stack);
		if (feed > 0) {
			if (state.getValue(FILL) >= MAX_FILL) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				level.setBlock(pos, state.setValue(FILL, Math.min(MAX_FILL, state.getValue(FILL) + feed)), Block.UPDATE_ALL);
				stack.consume(1, player);
				level.playSound(null, pos, SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 0.8F, 0.9F);
				level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(ItemTags.SHOVELS)) {
			if (state.getValue(RIPE) >= MAX_RIPE) {
				if (!level.isClientSide()) {
					int n = EARTH_MIN + level.getRandom().nextInt(EARTH_MAX - EARTH_MIN + 1);
					Block.popResourceFromFace(level, pos, hit.getDirection(), new ItemStack(PowderMinerals.NITRE_EARTH, n));
					level.setBlock(pos, state.setValue(RIPE, 0).setValue(TURNED, false), Block.UPDATE_ALL);
					stack.hurtAndBreak(1, player, hand);
					level.playSound(null, pos, SoundEvents.SHOVEL_FLATTEN.value(), SoundSource.BLOCKS, 1.0F, 0.8F);
					level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
				}
				return InteractionResult.SUCCESS;
			}
			if (!state.getValue(TURNED) && state.getValue(FILL) > 0) {
				if (!level.isClientSide()) {
					level.setBlock(pos, state.setValue(TURNED, true), Block.UPDATE_ALL);
					stack.hurtAndBreak(1, player, hand);
					level.playSound(null, pos, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
					if (level instanceof ServerLevel server) {
						server.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 6, 0.3, 0.05, 0.3, 0.0);
					}
				}
				return InteractionResult.SUCCESS;
			}
		}
		return super.useItemOn(stack, state, level, pos, player, hand, hit);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState now = this.workIn(state, level, pos);
		BlockPos above = pos.above();
		if (level.isRainingAt(above)) {
			// rain on an open bed leaches the nitre back out
			if (now.getValue(RIPE) > 0) {
				now = now.setValue(RIPE, now.getValue(RIPE) - 1);
			}
		} else if (!level.canSeeSky(above) && now.getValue(FILL) > 0 && now.getValue(RIPE) < MAX_RIPE
			&& random.nextFloat() < (now.getValue(TURNED) ? 2.0F * RIPEN_CHANCE : RIPEN_CHANCE)) {
			now = now.setValue(RIPE, now.getValue(RIPE) + 1).setValue(FILL, now.getValue(FILL) - 1).setValue(TURNED, false);
		}
		if (now != state) {
			level.setBlock(pos, now, Block.UPDATE_CLIENTS);
		}
	}

	/** Works in the droppings and scraps lying on top of the bed, as far as it has room. */
	private BlockState workIn(BlockState state, ServerLevel level, BlockPos pos) {
		int fill = state.getValue(FILL);
		for (ItemEntity lying : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos.above()), ItemEntity::isAlive)) {
			ItemStack stack = lying.getItem();
			int feed = feedOf(stack);
			while (feed > 0 && !stack.isEmpty() && fill < MAX_FILL) {
				fill = Math.min(MAX_FILL, fill + feed);
				stack.shrink(1);
			}
			if (stack.isEmpty()) {
				lying.discard();
			} else {
				lying.setItem(stack);
			}
		}
		return fill == state.getValue(FILL) ? state : state.setValue(FILL, fill);
	}

	/** A fed bed gives off a faint white dust now and then. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(FILL) > 0 && random.nextInt(12) == 0) {
			level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(), 0.0, 0.01, 0.0);
		}
	}
}
