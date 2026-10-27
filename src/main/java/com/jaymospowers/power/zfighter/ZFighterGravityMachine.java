package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.60, Andy's NOEA picks) The Gravity Machine: a block that sets a room round it to heavy gravity, the way a Gravity
 * user's Immense Field does ({@link com.jaymospowers.power.gravity.GravityTrainingField}) but for anyone, any time it has
 * fuel. The room is a cube {@link #SIZES} blocks a side centred on the machine; the weight is {@link #POWERS} times Earth's.
 * <ul>
 * <li><b>Set it:</b> right-click with an empty hand to step the weight (1x is off, nothing burns); sneak and right-click
 * with empty hands to step the size.</li>
 * <li><b>Fuel it:</b> right-click with coal or charcoal ({@link #COAL}), a coal block, a blaze rod or blaze powder, redstone
 * or a redstone block. Fuel is in seconds of the lightest setting, up to {@link #FUEL_MAX}; the heavier and bigger the
 * room, the faster it burns ({@link #BURN}).</li>
 * <li><b>Inside:</b> everyone (not spectators, not armour stands) is slowed, jumps lower and falls faster: the gravity,
 * jump and speed attributes up to {@link #GRAVITY_CAP} times, then a pull down on top for the rest. A Z Fighter trains
 * under it as under heavy gravity ({@link ZFighterTraining#heavyGravity}) once it is {@link #HEAVY_AT}x or more.</li>
 * </ul>
 * One cube, one block entity ({@link Entity}); who is affected is kept in memory and put right a moment after they leave.
 */
public final class ZFighterGravityMachine {
	/** The room's sides (blocks), the weights (times) one right click steps through. */
	static final int[] SIZES = {5, 10, 15, 20, 25};
	static final int[] POWERS = {1, 2, 5, 10, 20, 30, 50};
	/** Seconds of fuel a machine holds at most, and what each fuel gives (seconds at the burn rate 1). */
	static final float FUEL_MAX = 3600.0F;
	static final float COAL = 100.0F, COAL_BLOCK = 900.0F, BLAZE_ROD = 300.0F, BLAZE_POWDER = 150.0F, REDSTONE = 40.0F, REDSTONE_BLOCK = 360.0F;
	/** Fuel burnt a second: weight times size times this, but at least {@link #BURN_MIN}. */
	static final float BURN = 0.01F, BURN_MIN = 0.5F;
	/** The weight from which a Z Fighter counts it as heavy gravity for training. */
	static final int HEAVY_AT = 5;
	/** The gravity attribute can take this many times; the rest of the weight is a pull down (blocks a tick squared per extra time). */
	static final int GRAVITY_CAP = 12;
	static final double PULL_PER_X = 0.04, PULL_MAX = 0.9;
	/** Someone is put right this many ticks after the last time a machine weighed on him; machines look every this many ticks. */
	static final int LINGER = 12, LOOK_EVERY = 5;
	static final int COLOR = 0x8A6CFF;

	private static final Identifier SPEED_ID = JaymosPowers.id("gravity_machine_speed");
	private static final Identifier JUMP_ID = JaymosPowers.id("gravity_machine_jump");
	private static final Identifier GRAVITY_ID = JaymosPowers.id("gravity_machine_gravity");

	public static final BooleanProperty LIT = BlockStateProperties.LIT;
	public static Block BLOCK;
	public static Item ITEM;
	public static BlockEntityType<Entity> TYPE;

	/** Who is under a machine right now: the entity, the weight, when the last machine looked (game time). */
	private record Weighed(LivingEntity entity, int power, long until) {
	}

	private static final Map<UUID, Weighed> WEIGHED = new HashMap<>();

	private ZFighterGravityMachine() {
	}

	static void init() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("gravity_machine"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new MachineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL)
			.strength(5.0F, 12.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(state -> state.getValue(LIT) ? 9 : 0).setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("gravity_machine"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(BLOCK, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()
			.rarity(Rarity.UNCOMMON)));
		TYPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, JaymosPowers.id("gravity_machine"), new BlockEntityType<>(Entity::new, Set.of(BLOCK)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ITEM));
		ServerTickEvents.END_SERVER_TICK.register(ZFighterGravityMachine::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WEIGHED.clear());
	}

	/** Sets a freshly placed machine's size and weight (indices into {@link #SIZES} and {@link #POWERS}); used by the Lookout's gravity room. */
	static void setUp(ServerLevel level, BlockPos pos, int size, int power) {
		if (level.getBlockEntity(pos) instanceof Entity machine) {
			machine.size = Math.floorMod(size, SIZES.length);
			machine.power = Math.floorMod(power, POWERS.length);
			machine.setChanged();
		}
	}

	static float fuelValue(ItemStack stack) {
		if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) {
			return COAL;
		}
		if (stack.is(Items.COAL_BLOCK)) {
			return COAL_BLOCK;
		}
		if (stack.is(Items.BLAZE_ROD)) {
			return BLAZE_ROD;
		}
		if (stack.is(Items.BLAZE_POWDER)) {
			return BLAZE_POWDER;
		}
		if (stack.is(Items.REDSTONE)) {
			return REDSTONE;
		}
		return stack.is(Items.REDSTONE_BLOCK) ? REDSTONE_BLOCK : 0.0F;
	}

	// ---------------------------------------------------------------- who is weighed

	/** The weight (times) a machine puts on {@code entity} right now, 1 for none. */
	public static int weight(LivingEntity entity) {
		Weighed w = WEIGHED.get(entity.getUUID());
		return w != null && w.until() > entity.level().getGameTime() ? w.power() : 1;
	}

	/** Whether {@code player} stands in a machine's room at {@link #HEAVY_AT} times or more (heavy gravity for training). */
	public static boolean heavy(ServerPlayer player) {
		return !WEIGHED.isEmpty() && weight(player) >= HEAVY_AT;
	}

	private static void weigh(LivingEntity entity, int power, long now) {
		Weighed was = WEIGHED.get(entity.getUUID());
		if (was != null && was.until() > now && was.power() > power) {
			return;
		}
		WEIGHED.put(entity.getUUID(), new Weighed(entity, power, now + LINGER));
		if (was == null || was.power() != power || was.entity() != entity) {
			modifiers(entity, power);
		}
	}

	/** The weight on an entity: slower, lower jumps, more gravity (the attribute's share; the pull is the machine's). */
	private static void modifiers(LivingEntity entity, int power) {
		double soft = 1.0 / Math.sqrt(power);
		set(entity, Attributes.MOVEMENT_SPEED, SPEED_ID, Math.max(-0.9, soft - 1.0));
		set(entity, Attributes.JUMP_STRENGTH, JUMP_ID, Math.max(-0.95, 1.0 / Math.pow(power, 0.6) - 1.0));
		set(entity, Attributes.GRAVITY, GRAVITY_ID, Math.min(power, GRAVITY_CAP) - 1.0);
	}

	private static void set(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount) {
		AttributeInstance instance = entity.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0.0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	private static void tick(MinecraftServer server) {
		if (WEIGHED.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Iterator<Weighed> it = WEIGHED.values().iterator(); it.hasNext(); ) {
			Weighed w = it.next();
			if (w.until() <= now || w.entity().isRemoved()) {
				if (!w.entity().isRemoved()) {
					modifiers(w.entity(), 1);
				}
				it.remove();
			}
		}
	}

	// ---------------------------------------------------------------- the block

	static final class MachineBlock extends Block implements EntityBlock {
		MachineBlock(BlockBehaviour.Properties properties) {
			super(properties);
			registerDefaultState(stateDefinition.any().setValue(LIT, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
			builder.add(LIT);
		}

		@Override
		public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new Entity(pos, state);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			return level.isClientSide() || type != TYPE ? null : (BlockEntityTicker<T>)(BlockEntityTicker<Entity>)Entity::serverTick;
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			float fuel = fuelValue(stack);
			if (fuel <= 0.0F) {
				return InteractionResult.TRY_WITH_EMPTY_HAND;
			}
			if (!level.isClientSide() && level.getBlockEntity(pos) instanceof Entity machine) {
				if (machine.fuel >= FUEL_MAX - 1.0F) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_gravity_full").withStyle(ChatFormatting.GRAY));
				} else {
					machine.fuel = Math.min(FUEL_MAX, machine.fuel + fuel);
					stack.consume(1, player);
					machine.setChanged();
					level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 0.8F);
					player.sendOverlayMessage(machine.status());
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (!level.isClientSide() && level.getBlockEntity(pos) instanceof Entity machine) {
				if (player.isShiftKeyDown()) {
					machine.size = (machine.size + 1) % SIZES.length;
				} else {
					machine.power = (machine.power + 1) % POWERS.length;
				}
				machine.setChanged();
				level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.8F, 0.7F + 0.1F * machine.power);
				player.sendOverlayMessage(machine.status());
			}
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- the block entity

	/** One machine: its size and weight (indices), its fuel (seconds), and what it does each tick. */
	public static final class Entity extends BlockEntity {
		int size = 1;
		int power = 0;
		float fuel = 0.0F;

		public Entity(BlockPos pos, BlockState state) {
			super(TYPE, pos, state);
		}

		int side() {
			return SIZES[size];
		}

		int times() {
			return POWERS[power];
		}

		boolean running() {
			return fuel > 0.0F && times() > 1;
		}

		AABB room() {
			Vec3 c = Vec3.atCenterOf(getBlockPos());
			double half = side() / 2.0;
			return new AABB(c.x - half, c.y - half, c.z - half, c.x + half, c.y + half, c.z + half);
		}

		Component status() {
			int seconds = Math.round(fuel);
			return Component.translatable("message.jaymospowers.zfighter_gravity_status", side(), times(), seconds / 60, String.format("%02d", seconds % 60))
				.withStyle(ChatFormatting.LIGHT_PURPLE);
		}

		static void serverTick(Level level, BlockPos pos, BlockState state, Entity machine) {
			if (!(level instanceof ServerLevel server)) {
				return;
			}
			boolean running = machine.running();
			if (running) {
				machine.fuel = Math.max(0.0F, machine.fuel - Math.max(BURN_MIN, machine.times() * machine.side() * BURN) / 20.0F);
				if (machine.fuel == 0.0F) {
					server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
					machine.setChanged();
				}
			}
			if (running != state.getValue(LIT)) {
				server.setBlock(pos, state.setValue(LIT, running), Block.UPDATE_ALL);
			}
			if (!running) {
				return;
			}
			long now = server.getGameTime();
			int times = machine.times();
			if (now % LOOK_EVERY == 0) {
				List<LivingEntity> inside = server.getEntitiesOfClass(LivingEntity.class, machine.room(), e -> e.isAlive() && !e.isSpectator()
					&& !(e instanceof ArmorStand) && !(e instanceof Player p && p.isCreative() && p.getAbilities().flying));
				for (LivingEntity e : inside) {
					weigh(e, times, now);
				}
			}
			// the pull beyond what the gravity attribute holds, on whatever is in the air
			if (times > GRAVITY_CAP) {
				double pull = Math.min(PULL_MAX, PULL_PER_X * (times - GRAVITY_CAP));
				for (LivingEntity e : server.getEntitiesOfClass(LivingEntity.class, machine.room(), x -> x.isAlive() && !x.isSpectator()
					&& !x.onGround() && !x.isNoGravity() && !(x instanceof Player p && p.getAbilities().flying))) {
					e.addDeltaMovement(new Vec3(0.0, -pull, 0.0));
					e.syncVelocity = true;
				}
			}
			if (now % 10 == 0) {
				machine.outline(server);
			}
		}

		/** The room's edges, in purple motes (the corners and the middle of each edge). */
		private void outline(ServerLevel level) {
			AABB r = room();
			DustParticleOptions dust = new DustParticleOptions(COLOR, 1.2F);
			double[] xs = {r.minX, (r.minX + r.maxX) / 2.0, r.maxX};
			double[] ys = {r.minY, (r.minY + r.maxY) / 2.0, r.maxY};
			double[] zs = {r.minZ, (r.minZ + r.maxZ) / 2.0, r.maxZ};
			for (double x : xs) {
				for (double y : ys) {
					for (double z : zs) {
						int edges = (x == xs[1] ? 0 : 1) + (y == ys[1] ? 0 : 1) + (z == zs[1] ? 0 : 1);
						if (edges >= 2) {
							level.sendParticles(dust, false, false, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
						}
					}
				}
			}
		}

		@Override
		protected void saveAdditional(ValueOutput output) {
			super.saveAdditional(output);
			output.putInt("size", size);
			output.putInt("power", power);
			output.putFloat("fuel", fuel);
		}

		@Override
		protected void loadAdditional(ValueInput input) {
			super.loadAdditional(input);
			size = Math.floorMod(input.getIntOr("size", 1), SIZES.length);
			power = Math.floorMod(input.getIntOr("power", 0), POWERS.length);
			fuel = Mth.clamp(input.getFloatOr("fuel", 0.0F), 0.0F, FUEL_MAX);
		}
	}
}
