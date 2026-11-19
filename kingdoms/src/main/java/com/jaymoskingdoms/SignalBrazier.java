package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * <b>The signal brazier</b>: an iron bowl on a stone pillar. Light it at night, in the open, with a flint and steel and
 * {@link #XP_LEVELS} levels of experience, and it burns until dawn with a column of smoke that can be seen for miles
 * ({@link #SMOKE_RANGE} blocks). Whoever you've hired or who is sworn to you (a hired MCA fighter, a sworn captain, a hired
 * band's hand) and is loaded in the world sees it: the nearest comes to it, on foot if they're within {@link #WALK_RANGE}
 * blocks, else they're at its side after {@link #FAR_TICKS} ticks (a long ride in the dark, told in a puff of dust). If you
 * have no one to call, the flint is spent and nothing answers, but nothing is charged.
 */
public final class SignalBrazier extends Block {
	/** The levels of experience it costs to light. */
	static final int XP_LEVELS = 2;
	/** How far (blocks) the smoke is drawn to players. */
	static final double SMOKE_RANGE = 160.0;
	/** A companion nearer than this walks to it; further, they're brought after {@link #FAR_TICKS}. */
	static final double WALK_RANGE = 64.0;
	static final long FAR_TICKS = 160L, WALK_TICKS = 1800L;
	/** Ticks between its smoke, and its light when lit. */
	static final int SMOKE_EVERY = 10, LIGHT = 15;

	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);

	public static Block BRAZIER;
	public static Item BRAZIER_ITEM;

	/** A companion on his way: who, to where, till when (a walk) or when they arrive (a bringing, {@code far}). */
	private record Called(UUID who, UUID lord, BlockPos to, long by, boolean far) {
	}

	private static final List<Called> CALLED = new ArrayList<>();

	public SignalBrazier(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("signal_brazier"));
		BRAZIER = Registry.register(BuiltInRegistries.BLOCK, key, new SignalBrazier(BlockBehaviour.Properties.of().setId(key)
			.mapColor(MapColor.METAL).sound(SoundType.IRON).strength(3.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion()
			.lightLevel(state -> state.getValue(LIT) ? LIGHT : 0)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("signal_brazier"));
		BRAZIER_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BRAZIER, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(BRAZIER_ITEM));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (!CALLED.isEmpty() && Factions.level(server).getGameTime() % 10 == 0) {
				come(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CALLED.clear());
	}

	// ---- lighting it -------------------------------------------------------------------------------------------------

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (!stack.is(Items.FLINT_AND_STEEL) || state.getValue(LIT)) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!NightTrouble.night(server)) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.signal.day"));
			return InteractionResult.SUCCESS;
		}
		if (!server.canSeeSky(pos.above())) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.signal.covered"));
			return InteractionResult.SUCCESS;
		}
		if (!sp.isCreative() && sp.experienceLevel < XP_LEVELS) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.signal.tired", XP_LEVELS));
			return InteractionResult.SUCCESS;
		}
		if (!sp.isCreative()) {
			sp.giveExperienceLevels(-XP_LEVELS);
		}
		stack.hurtAndBreak(1, sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		server.setBlock(pos, state.setValue(LIT, true), 3);
		server.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		server.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.8F, 0.7F);
		server.scheduleTick(pos, this, SMOKE_EVERY);
		call(server, sp, pos);
		return InteractionResult.SUCCESS;
	}

	/** Its smoke, a column that rises high; and it dies down at dawn. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(LIT)) {
			return;
		}
		if (!NightTrouble.night(level)) {
			level.setBlock(pos, state.setValue(LIT, false), 3);
			level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= SMOKE_RANGE * SMOKE_RANGE) {
				level.sendParticles(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 1, 0.1, 0.0, 0.1, 0.002);
			}
		}
		level.scheduleTick(pos, this, SMOKE_EVERY);
	}

	// ---- who answers -------------------------------------------------------------------------------------------------

	/** Whether {@code e} is a companion of {@code lord}'s: hired, or sworn to them. */
	private static boolean companion(Entity e, ServerPlayer lord) {
		if (!(e instanceof Villager v) || !v.isAlive() || v.isBaby() || Downed.isDowned(v)) {
			return false;
		}
		if (McaCompat.LOADED && v instanceof VillagerEntityMCA mca && Learners.hired(mca) && Followers.takesOrders(v, lord)) {
			return true;
		}
		return SwornCaptains.takesOrders(lord, v) || RivalParties.hiredBy(v, lord);
	}

	/** {@code lord} lit the brazier at {@code pos}: their nearest companion answers. */
	private static void call(ServerLevel level, ServerPlayer lord, BlockPos pos) {
		Vec3 at = Vec3.atBottomCenterOf(pos);
		Villager nearest = null;
		for (Entity e : level.getAllEntities()) {
			if (companion(e, lord) && (nearest == null || e.distanceToSqr(at) < nearest.distanceToSqr(at))) {
				nearest = (Villager)e;
			}
		}
		if (nearest == null) {
			lord.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.signal.none"));
			return;
		}
		boolean far = nearest.distanceToSqr(at) > WALK_RANGE * WALK_RANGE;
		long now = level.getGameTime();
		UUID id = nearest.getUUID();
		CALLED.removeIf(c -> c.who().equals(id));
		CALLED.add(new Called(nearest.getUUID(), lord.getUUID(), pos.immutable(), now + (far ? FAR_TICKS : WALK_TICKS), far));
		lord.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.signal.answered", nearest.getDisplayName()));
		Speech.say(level, nearest, Component.translatable("speech.jaymoskingdoms.signal.seen." + level.getRandom().nextInt(3)), Speech.WHITE, 24.0);
	}

	private static void come(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Iterator<Called> it = CALLED.iterator(); it.hasNext();) {
			Called c = it.next();
			if (!(level.getEntity(c.who()) instanceof Villager v) || !v.isAlive()) {
				it.remove();
				continue;
			}
			Vec3 at = Vec3.atBottomCenterOf(c.to());
			if (c.far()) {
				if (now >= c.by()) {
					BlockPos spot = Construction.standable(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.to().offset(2, 0, 2)), 3);
					if (spot != null) {
						level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY(0.5), v.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
						v.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
						level.sendParticles(ParticleTypes.POOF, v.getX(), v.getY(0.5), v.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
					}
					it.remove();
				}
			} else if (v.distanceToSqr(at) < 9.0 || now >= c.by()) {
				v.getNavigation().stop();
				it.remove();
			} else if (now % 20 == 0) {
				BehaviorUtils.setWalkAndLookTargetMemories(v, c.to(), 1.1F, 2);
			}
		}
	}
}
