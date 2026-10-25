package com.jaymoskingdoms.whaling;

import com.jaymoskingdoms.Faction;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Theft;
import com.jaymoskingdoms.Wars;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.206) A boiling oil pot for the walls: an iron pot on a little brazier, tipped over the battlements onto whoever's
 * at the foot of them. Set it on the wall's edge facing out (it pours the way you face when you place it). Fill it with
 * whale oil (a bottle a measure, {@link #MOST_OIL} measures), light its brazier with flint and steel or a fire charge, and
 * after about {@link #HEAT_TICKS} the oil boils (each measure anew). Use it with an empty hand to tip a measure down the wall: boiling, it scalds
 * everyone in a {@link #WIDE}-wide column below ({@link #SCALD}, and sets them alight); cold, it only slicks and slows them.
 * <p>
 * On a settlement's land its folk keep it: when foes stand below (monsters, raiders, folk and sworn players of a realm at
 * war with it, players it wants for their crimes) and a guard or grown villager is near the pot, they light it and,
 * once it boils, tip it on them.
 */
public final class OilPotBlock extends Block {
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static final BooleanProperty HOT = BooleanProperty.create("hot");
	public static final int MOST_OIL = 4;
	public static final IntegerProperty OIL = IntegerProperty.create("oil", 0, MOST_OIL);
	/** How long the oil takes to boil, and how often (ticks) a filled pot's keepers look for foes below. */
	static final int HEAT_TICKS = 200, WATCH_TICKS = 20;
	/** How far down it pours (blocks), how wide, and what it does: the scald, seconds alight, and a cold pour's slowing (ticks). */
	static final int DROP = 24, WIDE = 3;
	static final float SCALD = 8.0F, ALIGHT = 5.0F, COLD_SCALD = 1.0F;
	static final int SLICK_TICKS = 100;
	/** How near the pot a keeper must stand (blocks). */
	static final double KEEPER_REACH = 4.0;

	public OilPotBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(HOT, false)
			.setValue(OIL, 0));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, LIT, HOT, OIL);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
	}

	@Override
	protected InteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (held.is(Whaling.WHALE_OIL)) {
			if (state.getValue(OIL) >= MOST_OIL) {
				return InteractionResult.FAIL;
			}
			if (level instanceof ServerLevel server) {
				player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
				server.setBlock(pos, state.setValue(OIL, state.getValue(OIL) + 1), Block.UPDATE_ALL);
				server.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8F, 0.8F);
				server.scheduleTick(pos, this, WATCH_TICKS);
			}
			return InteractionResult.SUCCESS;
		}
		if ((held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE)) && !state.getValue(LIT)) {
			if (level instanceof ServerLevel server) {
				if (held.is(Items.FLINT_AND_STEEL)) {
					held.hurtAndBreak(1, player, hand);
				} else {
					held.consume(1, player);
				}
				light(server, pos, state);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (state.getValue(OIL) == 0) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			pour(server, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	private void light(ServerLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.scheduleTick(pos, this, WATCH_TICKS);
	}

	/** A measure tipped down the wall. */
	void pour(ServerLevel level, BlockPos pos, BlockState state) {
		boolean hot = state.getValue(HOT);
		BlockPos over = pos.relative(state.getValue(FACING));
		int floor = over.getY();
		for (int i = 0; i < DROP && !level.getBlockState(new BlockPos(over.getX(), floor - 1, over.getZ())).isSolid(); i++) {
			floor--;
		}
		AABB column = new AABB(over.getX() - WIDE / 2, floor, over.getZ() - WIDE / 2, over.getX() + WIDE / 2 + 1, over.getY() + 1,
			over.getZ() + WIDE / 2 + 1);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, column, LivingEntity::isAlive)) {
			if (hot) {
				e.hurtServer(level, level.damageSources().hotFloor(), SCALD);
				e.igniteForSeconds(ALIGHT);
			} else {
				e.hurtServer(level, level.damageSources().generic(), COLD_SCALD);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLICK_TICKS, 2));
			}
		}
		for (int y = over.getY(); y >= floor; y -= 2) {
			level.sendParticles(hot ? ParticleTypes.FALLING_LAVA : ParticleTypes.FALLING_HONEY, over.getX() + 0.5, y + 0.5, over.getZ() + 0.5,
				6, 0.6, 0.6, 0.6, 0.0);
		}
		if (hot) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, over.getX() + 0.5, floor + 0.5, over.getZ() + 0.5, 20, 1.0, 0.4, 1.0, 0.02);
		}
		level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.playSound(null, over.getX(), floor, over.getZ(), hot ? SoundEvents.LAVA_EXTINGUISH : SoundEvents.HONEY_BLOCK_SLIDE,
			SoundSource.BLOCKS, 1.2F, 0.8F);
		// (the next measure needs boiling again)
		int oil = state.getValue(OIL) - 1;
		level.setBlock(pos, state.setValue(OIL, oil).setValue(HOT, false).setValue(LIT, state.getValue(LIT) && oil > 0), Block.UPDATE_ALL);
	}

	/** It boils, and its keepers watch the foot of the wall. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// (one look in HEAT_TICKS / WATCH_TICKS finds it boiling: about HEAT_TICKS on average)
		if (state.getValue(LIT) && !state.getValue(HOT) && state.getValue(OIL) > 0 && random.nextInt(HEAT_TICKS / WATCH_TICKS) == 0) {
			state = state.setValue(HOT, true);
			level.setBlock(pos, state, Block.UPDATE_ALL);
			level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.8F, 0.6F);
		}
		if (state.getValue(OIL) == 0) {
			return;
		}
		this.keep(level, pos, state);
		level.scheduleTick(pos, this, WATCH_TICKS);
	}

	/** On a settlement's land, with foes below and a keeper by: lit if it isn't, tipped once it boils. */
	private void keep(ServerLevel level, BlockPos pos, BlockState state) {
		Faction f = Theft.lawAt(level, pos);
		if (f == null) {
			return;
		}
		BlockPos over = pos.relative(state.getValue(FACING));
		AABB below = new AABB(over.getX() - WIDE / 2, over.getY() - DROP, over.getZ() - WIDE / 2, over.getX() + WIDE / 2 + 1, over.getY(),
			over.getZ() + WIDE / 2 + 1);
		if (level.getEntitiesOfClass(LivingEntity.class, below, e -> e.isAlive() && foe(level, f, e)).isEmpty()) {
			return;
		}
		Mob keeper = keeper(level, f, pos);
		if (keeper == null) {
			return;
		}
		keeper.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		keeper.getLookControl().setLookAt(over.getX() + 0.5, over.getY() - 2.0, over.getZ() + 0.5);
		if (!state.getValue(LIT)) {
			this.light(level, pos, state);
		} else if (state.getValue(HOT)) {
			this.pour(level, pos, state);
		}
	}

	/** Someone the settlement {@code f} would tip boiling oil on. */
	private static boolean foe(ServerLevel level, Faction f, LivingEntity e) {
		if (e instanceof Enemy) {
			return true;
		}
		if (e instanceof Folk folk) {
			return Wars.enemies(level.getServer(), folk.factionId(), f.id());
		}
		if (e instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator() && (Wars.enemy(player, f) || Theft.wanted(player, f.id()));
		}
		return false;
	}

	/** One of the settlement's guards, or a grown, waking villager, standing by the pot. */
	private static @Nullable Mob keeper(ServerLevel level, Faction f, BlockPos pos) {
		AABB by = new AABB(pos).inflate(KEEPER_REACH);
		List<Folk> guards = level.getEntitiesOfClass(Folk.class, by, folk -> folk.isAlive() && folk.combatant() && folk.factionId() == f.id());
		if (!guards.isEmpty()) {
			return guards.getFirst();
		}
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, by, v -> v.isAlive() && !v.isBaby() && !v.isSleeping()
			&& !v.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY));
		return folk.isEmpty() ? null : folk.getFirst();
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3, pos.getY() + 0.15,
			pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
		if (state.getValue(HOT) && random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0.0, 0.04, 0.0);
			if (random.nextInt(4) == 0) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.3F, 1.4F,
					false);
			}
		}
	}
}
