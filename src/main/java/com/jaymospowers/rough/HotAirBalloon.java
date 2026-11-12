package com.jaymospowers.rough;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A hot air balloon (Andy's TFC Hardrock picks, "a rideable wicker basket and envelope with a burner (fuel bar), carried by
 * our wind"; our own entity, drawn from vanilla blocks by {@code HotAirBalloonRenderer}). Set it down with the item, feed the
 * burner coal, charcoal or wood, and step in. Hold <b>jump</b> to fire the burner and rise; let go and it cools and sinks
 * slowly; hold <b>back</b> to open the vent and sink faster. It cannot be steered: the wind carries it, and the wind
 * blows a different way at different heights ({@link #wind}), so the way to go somewhere is to climb or sink until
 * you find the layer that goes there. Every second of burner burns {@link #BURN} ticks of fuel; a rider sees the fuel as
 * a bar. Break it with a hand and it comes back as the item with its fuel gone.
 */
public class HotAirBalloon extends Entity {
	/** The basket's size. */
	static final float WIDTH = 2.0F, HEIGHT = 1.4F;
	/** Fuel held (ticks of burner), and what each fuel is worth: coal, charcoal, a log, planks, a stick. */
	public static final int MAX_FUEL = 6000;
	static final int COAL = 1200, LOG = 600, PLANKS = 150, STICK = 40;
	/** Lift a tick with the burner on, the sink with it off, the vent's extra sink, the fastest rise and fall (blocks a tick). */
	static final double LIFT = 0.012, SINK = 0.004, VENT = 0.012, UP_MAX = 0.22, DOWN_MAX = 0.28;
	/** Fuel spent a tick of burning. */
	static final int BURN = 2;
	/** The wind: a layer's depth (blocks), how fast it blows (blocks a tick), how quickly the balloon takes it up (a share a tick). */
	static final double LAYER = 28.0, WIND_SPEED = 0.07, PICKUP = 0.02;
	/** How high the balloon can climb above the build limit's reach before the air is too thin for the burner. */
	static final int CEILING_GAP = 8;

	public static EntityType<HotAirBalloon> TYPE;
	public static Item ITEM;

	private static final EntityDataAccessor<Integer> FUEL = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BURNING = SynchedEntityData.defineId(HotAirBalloon.class, EntityDataSerializers.BOOLEAN);

	public HotAirBalloon(EntityType<? extends HotAirBalloon> type, Level level) {
		super(type, level);
	}

	public static net.minecraft.sounds.SoundEvent BURNER;

	public static void init() {
		BURNER = Registry.register(BuiltInRegistries.SOUND_EVENT, JaymosPowers.id("rough.balloon.burner"),
			net.minecraft.sounds.SoundEvent.createVariableRangeEvent(JaymosPowers.id("rough.balloon.burner")));
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("hot_air_balloon"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<HotAirBalloon>of(HotAirBalloon::new, MobCategory.MISC)
			.noLootTable().sized(WIDTH, HEIGHT).clientTrackingRange(12).updateInterval(1).build(key));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("hot_air_balloon"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BalloonItem(new Item.Properties().setId(itemKey).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(ITEM));
	}

	/** The wind at height {@code y}: a unit heading that turns slowly with the hours and differs from layer to layer. */
	public static Vec3 wind(Level level, double y) {
		double layer = y / LAYER;
		int low = (int) Math.floor(layer);
		double between = layer - low;
		double hours = level.getGameTime() / 9000.0;
		Vec3 a = heading(low, hours), b = heading(low + 1, hours);
		return a.scale(1.0 - between).add(b.scale(between));
	}

	private static Vec3 heading(int layer, double hours) {
		double angle = Math.sin(layer * 1.7 + hours * 0.9) * Math.PI + layer * 2.399 + Math.cos(layer * 0.6 + hours * 0.4) * 1.1;
		return new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
	}

	public int fuel() {
		return this.entityData.get(FUEL);
	}

	public boolean burning() {
		return this.entityData.get(BURNING);
	}

	/** How much fuel {@code stack} puts in the burner; 0 if it doesn't burn. */
	static int worth(ItemStack stack) {
		if (stack.is(ItemTags.COALS)) {
			return COAL;
		}
		if (stack.is(ItemTags.LOGS_THAT_BURN)) {
			return LOG;
		}
		if (stack.is(ItemTags.PLANKS)) {
			return PLANKS;
		}
		return stack.is(Items.STICK) ? STICK : 0;
	}

	@Override
	public void tick() {
		super.tick();
		Level level = level();
		Vec3 motion = getDeltaMovement();
		boolean burn = false, vent = false;
		if (!level.isClientSide() && getFirstPassenger() instanceof ServerPlayer rider) {
			Input input = rider.getLastClientInput();
			burn = input.jump() && fuel() > 0 && getY() < level.getMaxY() - CEILING_GAP;
			vent = input.backward();
		}
		if (!level.isClientSide()) {
			if (burn) {
				entityData.set(FUEL, Math.max(0, fuel() - BURN));
			}
			if (burning() != burn) {
				entityData.set(BURNING, burn);
				if (burn) {
					level.playSound(null, getX(), getY(), getZ(), BURNER, SoundSource.NEUTRAL, 1.0F, 1.0F);
				}
			}
			double vy = motion.y;
			if (burn) {
				vy = Math.min(UP_MAX, vy + LIFT);
			} else {
				// cooling air: sinks toward a slow fall, faster with the vent open
				vy = Math.max(-(vent ? DOWN_MAX : DOWN_MAX * 0.4), vy - (vent ? VENT : SINK));
			}
			// the wind of this height, taken up slowly
			Vec3 want = onGround() ? Vec3.ZERO : wind(level, getY()).scale(WIND_SPEED);
			double vx = Mth.lerp(PICKUP, motion.x, want.x), vz = Mth.lerp(PICKUP, motion.z, want.z);
			setDeltaMovement(vx, vy, vz);
			move(MoverType.SELF, getDeltaMovement());
			if (onGround() || verticalCollision) {
				setDeltaMovement(getDeltaMovement().multiply(0.6, 0.0, 0.6));
			}
			resetFallDistance();
			if (getFirstPassenger() instanceof ServerPlayer rider && tickCount % 5 == 0) {
				rider.sendOverlayMessage(bar());
			}
			if (burn && tickCount % 30 == 0) {
				level.playSound(null, getX(), getY() + 1.0, getZ(), BURNER, SoundSource.NEUTRAL, 0.8F, 1.0F);
			}
			Vec3 heading = getDeltaMovement();
			if (heading.horizontalDistanceSqr() > 1.0E-4) {
				setYRot((float) (Mth.atan2(heading.z, heading.x) * Mth.RAD_TO_DEG) - 90.0F);
			}
		} else if (burning()) {
			level.addParticle(ParticleTypes.FLAME, getX() + (random.nextDouble() - 0.5) * 0.4, getY() + 1.9, getZ() + (random.nextDouble() - 0.5) * 0.4,
				0.0, 0.05, 0.0);
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.SMOKE, getX(), getY() + 2.1, getZ(), 0.0, 0.04, 0.0);
			}
		}
	}

	/** The fuel as a bar of blocks, the way the rider sees it. */
	Component bar() {
		int cells = 20, filled = Math.round((float) fuel() / MAX_FUEL * cells);
		ChatFormatting colour = fuel() > MAX_FUEL / 2 ? ChatFormatting.GOLD : fuel() > MAX_FUEL / 5 ? ChatFormatting.YELLOW : ChatFormatting.RED;
		return Component.literal("▉".repeat(filled)).withStyle(colour)
			.append(Component.literal("▉".repeat(cells - filled)).withStyle(ChatFormatting.DARK_GRAY));
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return passenger instanceof Player && getPassengers().size() < 2;
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		double side = getPassengers().size() > 1 ? (getPassengers().indexOf(passenger) == 0 ? -0.4 : 0.4) : 0.0;
		float radians = getYRot() * Mth.DEG_TO_RAD;
		moveFunction.accept(passenger, getX() + Mth.cos(radians) * side, getY() + 0.35, getZ() + Mth.sin(radians) * side);
	}

	@Override
	public @Nullable LivingEntity getControllingPassenger() {
		return getFirstPassenger() instanceof LivingEntity living ? living : null;
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		return position().add(1.6, 0.1, 0.0);
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return true;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || !(source.getEntity() instanceof Player)) {
			return false;
		}
		ejectPassengers();
		spawnAtLocation(level, new ItemStack(ITEM));
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WOOL_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
		discard();
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(level() instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack held = player.getItemInHand(hand);
		int worth = worth(held);
		if (worth > 0 && !player.isSecondaryUseActive()) {
			if (fuel() >= MAX_FUEL) {
				player.sendOverlayMessage(bar());
				return InteractionResult.SUCCESS_SERVER;
			}
			entityData.set(FUEL, Math.min(MAX_FUEL, fuel() + worth));
			held.consume(1, player);
			server.playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 0.5F, 1.4F);
			player.sendOverlayMessage(bar());
			return InteractionResult.SUCCESS_SERVER;
		}
		if (!player.isPassenger() && getPassengers().size() < 2) {
			player.startRiding(this);
			player.sendOverlayMessage(bar());
			return InteractionResult.SUCCESS_SERVER;
		}
		return InteractionResult.PASS;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(FUEL, 0);
		builder.define(BURNING, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		entityData.set(FUEL, Mth.clamp(input.getIntOr("fuel", 0), 0, MAX_FUEL));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("fuel", fuel());
	}

	/** The item: set the balloon down on the ground (its basket on the block you aim at). */
	static final class BalloonItem extends Item {
		BalloonItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			HotAirBalloon balloon = new HotAirBalloon(TYPE, server);
			balloon.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			balloon.setYRot(context.getRotation());
			if (!server.noCollision(balloon, balloon.getBoundingBox())) {
				return InteractionResult.FAIL;
			}
			server.addFreshEntity(balloon);
			server.playSound(null, at, SoundEvents.WOOL_PLACE, SoundSource.NEUTRAL, 1.0F, 0.8F);
			if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
				context.getItemInHand().shrink(1);
			}
			return InteractionResult.SUCCESS_SERVER;
		}
	}
}
