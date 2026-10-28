package com.jaymoskitchen.horse;

import com.jaymoskitchen.corpse.CorpseEntity;
import com.jaymoskitchen.JaymosKitchen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PositionPath;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A horse-drawn vehicle (see {@link CartKind}): the wagon, coach, supply cart, animal cart and plow, after NiftyCarts,
 * in every wood. Ride a (tamed) horse up to one and right-click it to hitch up, or bring a horse on a lead and
 * right-click it; right-click again from the saddle to unhitch. Hitched, it trails behind the horse on a fixed pole,
 * climbing blocks as it goes, and keeps the horse from wandering off while nobody's riding it. NiftyCarts' carts pitch
 * toward their puller, and rest tipped forward on their shafts when unhitched.
 *
 * <p>A player can also take one by the shafts (right-click its front end) and pull it themselves; a supply cart only
 * while it's empty.
 *
 * <p>The plow: right-click lowers or raises its arms (sneak + right-click for its three hoe slots). Lowered, it tills
 * the 3x3 patch behind it as it goes, wearing down the hoes; one wooden hoe leaves it patchy, three netherite ones
 * nearly solid. The animal cart: animals climb in when they bump into it (two at most); sneak + right-click lets them
 * out.
 *
 * <p>The coach: six seats on three benches, filled in the order people climb in (right-click). It can't be pulled by
 * hand, and when Jaymo's Kingdoms is installed only a draft horse (one tagged {@value #DRAFT_TAG}) will take the
 * traces. Like a boat, the wagon's bench and the coach's free seats also take in any peaceful living mob that bumps
 * into them (see {@link #loadMobs}); sneak + right-click lets them all out.
 */
public class CartEntity extends Entity implements Container, MenuProvider {
	/** Entity id of the horse (or player) pulling it, or -1. */
	private static final EntityDataAccessor<Integer> HITCH = SynchedEntityData.defineId(CartEntity.class,
		EntityDataSerializers.INT);
	/** Wobble after being hit. */
	private static final EntityDataAccessor<Integer> HURT = SynchedEntityData.defineId(CartEntity.class,
		EntityDataSerializers.INT);
	/** The wood it's made of (NiftyCarts' carts). */
	private static final EntityDataAccessor<String> WOOD = SynchedEntityData.defineId(CartEntity.class,
		EntityDataSerializers.STRING);
	/** The wagon: someone has its chest open (its lid opens). */
	private static final EntityDataAccessor<Boolean> CHEST_OPEN = SynchedEntityData.defineId(CartEntity.class,
		EntityDataSerializers.BOOLEAN);
	/** The plow: arms down, tilling. */
	/** Ticks left of tipping the load out onto the ground (the cart rolls over onto its side and back). */
	private static final EntityDataAccessor<Integer> TIP = SynchedEntityData.defineId(CartEntity.class,
		EntityDataSerializers.INT);
	public static final int TIP_TICKS = 36;
	private static final EntityDataAccessor<Boolean> LOWERED = SynchedEntityData.defineId(CartEntity.class,
		EntityDataSerializers.BOOLEAN);
	/** What clients draw on board: the plow's hoes (one per arm), or the supply cart's top four kinds of cargo. */
	private static final List<EntityDataAccessor<ItemStack>> SHOWN = List.of(
		SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.ITEM_STACK),
		SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.ITEM_STACK),
		SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.ITEM_STACK),
		SynchedEntityData.defineId(CartEntity.class, EntityDataSerializers.ITEM_STACK));
	public static final int SHOWN_COUNT = 4;
	/** How well each kind of hoe tills; anything else counts as {@link #OTHER_HOE}. */
	private static final Map<Item, Float> HOE_STRENGTH = Map.of(Items.WOODEN_HOE, 1.0F, Items.STONE_HOE, 1.5F,
		Items.COPPER_HOE, 1.75F, Items.GOLDEN_HOE, 2.0F, Items.IRON_HOE, 2.0F, Items.DIAMOND_HOE, 3.0F, Items.NETHERITE_HOE, 4.0F);
	private static final float OTHER_HOE = 1.5F;
	/** The plow's arms, raised and lowered (radians, as NiftyCarts has them), and how fast they swing (per tick). */
	public static final float ARMS_RAISED = Mth.PI / 2.5F, ARMS_LOWERED = Mth.PI / 4.0F;
	private static final float ARM_SPEED = 0.06F;
	/** NiftyCarts' carts rest tipped this far forward (degrees) with nothing pulling them. */
	private static final float RESTING_PITCH = 25.0F;

	private static final Identifier TOWING = JaymosKitchen.id("towing");
	/** Hits to break it (with anything; creative breaks it at once). */
	private static final int HITS_TO_BREAK = 3;
	/** Further than this from its horse (teleported, stuck behind a wall...) and it comes unhitched. */
	private static final double SNAP_DISTANCE = 6.0;
	/** The plow tills a 3x3 patch centred this far behind it (under the hoes on its arms). */
	private static final double TILL_BEHIND = 1.5;
	/** Jaymo's Kingdoms (not a dependency: only looked for by id) tags every draft-breed horse with this. */
	public static final String DRAFT_TAG = "jaymoskingdoms.draft";
	private static final String KINGDOMS_MOD = "jaymoskingdoms";
	/** The coach's seats: three benches of two. */
	public static final int COACH_SEATS = 6;
	/**
	 * Mobs that bump into the wagon's bench or the coach's seats must fit on a seat: narrower than the cart and no more
	 * than this much footprint (width times height; the animal cart's limit).
	 */
	private static final float MAX_SEAT_FOOTPRINT = 1.5F;
	/** After letting the mobs out, a wagon or coach takes in no more for this long (ticks), so they can wander off. */
	private static final int RELOAD_DELAY = 100;
	/**
	 * Coach seats, in model pixels: how far ahead of the entity's middle each bench's seat is (first bench at the front),
	 * and how far each of its two sitters is from the middle line. The model's benches (NiftyCartModels.coach) match.
	 */
	private static final double[] COACH_BENCH_AHEAD = {14.5, 0.5, -13.5};
	private static final double COACH_SEAT_SIDE = 5.0;

	private final NonNullList<ItemStack> items;
	/** Server: the horse it's hitched to, kept across saves and found again once it's loaded. */
	private @Nullable UUID hitchUuid;
	private int hitchSearch;
	private int hits;
	/** Server: the game time before which a wagon or coach takes in no more mobs (they were just let out). */
	private long noLoadUntil;
	/** Server: the inventory changed, so what's shown on board needs working out again. */
	private boolean shownDirty = true;
	/** Client: wheel spin, and the plow's arms, for drawing. */
	private float wheelRot, wheelRotO;
	private float armAngle = ARMS_RAISED, armAngleO = ARMS_RAISED;
	/** Client: the wagon chest's lid, 0 shut to 1 open. Server: how many have the chest open. */
	private float lid, lidO;
	private int viewers;

	public CartEntity(EntityType<? extends CartEntity> type, Level level) {
		super(type, level);
		this.items = NonNullList.withSize(kind().slots, ItemStack.EMPTY);
		if (kind().tilts) {
			setXRot(RESTING_PITCH);
		}
	}

	public CartKind kind() {
		EntityType<?> type = getType();
		if (type == Saddlebags.COACH) {
			return CartKind.COACH;
		}
		if (type == Saddlebags.SUPPLY_CART) {
			return CartKind.SUPPLY_CART;
		}
		if (type == Saddlebags.ANIMAL_CART) {
			return CartKind.ANIMAL_CART;
		}
		return type == Saddlebags.PLOW ? CartKind.PLOW : CartKind.WAGON;
	}

	public boolean isWagon() {
		return kind() == CartKind.WAGON;
	}

	public String wood() {
		return this.entityData.get(WOOD);
	}

	public void setWood(String wood) {
		this.entityData.set(WOOD, NiftyCarts.isWood(wood) ? wood : "oak");
	}

	/** From the puller's middle to the cart's middle. */
	private double poleLength(Entity puller) {
		return kind().spacing + 0.5 * puller.getBbWidth();
	}

	/** Wheel radius, in blocks. */
	public float wheelRadius() {
		return kind().wheelRadius / 16.0F;
	}

	/** Slows whoever's pulling it by this much: a player hauling it by hand. A horse keeps its own pace. */
	private double drag(Entity puller) {
		return puller instanceof Player ? kind().handDrag : 0.0;
	}

	/** Too heavy to pull by hand: a loaded supply cart, or a coach (always). */
	private boolean tooHeavyByHand() {
		return kind() == CartKind.COACH || kind() == CartKind.SUPPLY_CART && !isEmpty();
	}

	/**
	 * Whether this horse may pull this cart. Only the coach is picky: with Jaymo's Kingdoms installed it takes a draft
	 * horse (tagged {@value #DRAFT_TAG}) and nothing else; without Kingdoms any horse will do.
	 */
	private boolean mayPull(AbstractHorse horse) {
		return kind() != CartKind.COACH || !FabricLoader.getInstance().isModLoaded(KINGDOMS_MOD)
			|| horse.entityTags().contains(DRAFT_TAG);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(HITCH, -1);
		builder.define(HURT, 0);
		builder.define(WOOD, "oak");
		builder.define(LOWERED, false);
		builder.define(TIP, 0);
		builder.define(CHEST_OPEN, false);
		for (EntityDataAccessor<ItemStack> shown : SHOWN) {
			builder.define(shown, ItemStack.EMPTY);
		}
	}

	/** The horse or player pulling it. */
	public @Nullable LivingEntity puller() {
		int id = this.entityData.get(HITCH);
		return id >= 0 && level().getEntity(id) instanceof LivingEntity puller ? puller : null;
	}

	public int hurtTime() {
		return this.entityData.get(HURT);
	}

	public float wheelRot(float partialTicks) {
		return Mth.lerp(partialTicks, wheelRotO, wheelRot);
	}

	/** The wagon chest's lid this frame, eased as vanilla's chests are (0 shut, 1 open). */
	public float lidOpenness(float partialTicks) {
		float open = 1.0F - Mth.lerp(partialTicks, lidO, lid);
		return 1.0F - open * open * open;
	}

	public boolean lowered() {
		return this.entityData.get(LOWERED);
	}

	/** The plow's arm angle this frame (radians: up is bigger). */
	public float armAngle(float partialTicks) {
		return Mth.lerp(partialTicks, armAngleO, armAngle);
	}

	/** What's drawn on board in place {@code i}: the plow's hoe on arm i, or one of the supply cart's cargo piles. */
	public ItemStack shown(int i) {
		return this.entityData.get(SHOWN.get(i));
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		// hitched, each side works out where it is from the horse; the client's own (smoother) answer wins
		return new InterpolationHandler.NoOpInterpolationHandler() {
			@Override
			public boolean interpolateTo(@Nullable PositionPath position, float yRot, float xRot, boolean hasRotation) {
				return CartEntity.this.puller() != null;
			}
		};
	}

	@Override
	public void tick() {
		super.tick();
		if (this.entityData.get(HURT) > 0 && !level().isClientSide()) {
			this.entityData.set(HURT, this.entityData.get(HURT) - 1);
		}
		int tip = this.entityData.get(TIP);
		if (tip > 0 && !level().isClientSide()) {
			this.entityData.set(TIP, tip - 1);
			if (tip == TIP_TICKS - 6) {
				// rolled far enough over: everything slides out onto the ground beside it
				spillLoad();
			}
		}
		LivingEntity puller = puller();
		if (level() instanceof ServerLevel level) {
			puller = serverHitch(level, puller);
		}
		if (puller != null) {
			follow(puller);
			if (kind() == CartKind.PLOW && lowered() && level() instanceof ServerLevel level
				&& (getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo) > 0.0004) {
				till(level, puller);
			}
		} else {
			Vec3 motion = getDeltaMovement();
			setDeltaMovement(motion.x * 0.5, onGround() ? 0.0 : motion.y - 0.08, motion.z * 0.5);
			move(MoverType.SELF, getDeltaMovement());
			if (kind().tilts) {
				// resting on its shafts
				setXRot(RESTING_PITCH);
			}
		}
		if (kind() == CartKind.ANIMAL_CART && !level().isClientSide()) {
			loadAnimals();
		} else if ((kind() == CartKind.WAGON || kind() == CartKind.COACH) && !level().isClientSide()) {
			loadMobs();
		}
		if (level().isClientSide()) {
			// roll the wheels by how far it went forward
			float yaw = getYRot() * Mth.DEG_TO_RAD;
			double forward = (getX() - xo) * -Mth.sin(yaw) + (getZ() - zo) * Mth.cos(yaw);
			wheelRotO = wheelRot;
			wheelRot += (float) (forward / wheelRadius());
			armAngleO = armAngle;
			armAngle = Mth.approach(armAngle, lowered() ? ARMS_LOWERED : ARMS_RAISED, ARM_SPEED);
			lidO = lid;
			lid = Mth.approach(lid, this.entityData.get(CHEST_OPEN) ? 1.0F : 0.0F, 0.1F);
		} else if (shownDirty) {
			shownDirty = false;
			updateShown();
		}
	}

	/** Server: tells clients what to draw on board (the plow's hoes, the supply cart's cargo). */
	private void updateShown() {
		ItemStack[] shown = new ItemStack[SHOWN_COUNT];
		java.util.Arrays.fill(shown, ItemStack.EMPTY);
		if (kind() == CartKind.PLOW) {
			for (int i = 0; i < items.size(); i++) {
				shown[i] = items.get(i).copy();
			}
		} else if (kind() == CartKind.SUPPLY_CART) {
			// the most-stacked kinds of cargo, blocks first, a pile for every quarter of the cart they fill (NiftyCarts)
			Map<Item, Integer> totals = new LinkedHashMap<>();
			Map<Item, ItemStack> examples = new LinkedHashMap<>();
			for (ItemStack stack : items) {
				if (!stack.isEmpty()) {
					totals.merge(stack.getItem(), 1, Integer::sum);
					examples.putIfAbsent(stack.getItem(), stack);
				}
			}
			List<Map.Entry<Item, Integer>> ranked = new ArrayList<>(totals.entrySet());
			ranked.sort(Comparator.<Map.Entry<Item, Integer>>comparingInt(e -> e.getKey() instanceof BlockItem ? 0 : 1)
				.thenComparingInt(e -> -e.getValue()));
			int quarter = items.size() / SHOWN_COUNT;
			int pos = 0;
			for (Map.Entry<Item, Integer> entry : ranked) {
				int piles = Math.max(1, (entry.getValue() + quarter / 2) / quarter);
				for (int n = 1; n <= piles && pos < SHOWN_COUNT; n++) {
					ItemStack pile = examples.get(entry.getKey()).copy();
					pile.setCount(Math.min(pile.getMaxStackSize(), entry.getValue() / n));
					shown[pos++] = pile;
				}
				if (pos >= SHOWN_COUNT) {
					break;
				}
			}
		}
		for (int i = 0; i < SHOWN_COUNT; i++) {
			if (!ItemStack.matches(this.entityData.get(SHOWN.get(i)), shown[i])) {
				this.entityData.set(SHOWN.get(i), shown[i]);
			}
		}
	}

	/** The animal cart: small animals that bump into it climb in (two at most). */
	private void loadAnimals() {
		for (Entity entity : level().getEntities(this, getBoundingBox().inflate(0.2, -0.01, 0.2), EntitySelector.pushableBy(this))) {
			MobCategory category = entity.getType().getCategory();
			boolean water = category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
				|| category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
			if (entity instanceof LivingEntity && !(entity instanceof Player) && !water && !entity.isPassenger()
				&& !entity.hasPassenger(this) && canAddPassenger(entity) && entity.getBbWidth() < getBbWidth()
				&& entity.getBbWidth() * entity.getBbHeight() < 1.5F) {
				if (entity instanceof TamableAnimal tamable) {
					tamable.setInSittingPose(true);
				}
				entity.startRiding(this);
			}
		}
	}

	/**
	 * The wagon's bench and the coach's free seats: a living mob that bumps into the cart climbs aboard, as into a boat.
	 * Villagers, animals, tamed pets and other folk are welcome; not players, hostile monsters ({@link Enemy}), water
	 * creatures or horses, nor anything angry at someone, on a lead or already carrying a rider, nor anything bigger than
	 * a seat ({@link #MAX_SEAT_FOOTPRINT}). Once aboard they stay until a player sneak-right-clicks the cart (see
	 * {@link #tip}). Nothing is taken in while the cart is tipping them out.
	 */
	private void loadMobs() {
		if (this.entityData.get(TIP) > 0 || level().getGameTime() < noLoadUntil || getPassengers().size() >= seats()) {
			return;
		}
		for (Entity entity : level().getEntities(this, getBoundingBox().inflate(0.2, -0.01, 0.2), EntitySelector.pushableBy(this))) {
			if (!(entity instanceof Mob mob) || !mob.isAlive() || mob instanceof Enemy || mob instanceof AbstractHorse
				|| mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || mob.getTarget() != null) {
				continue;
			}
			MobCategory category = mob.getType().getCategory();
			boolean water = category == MobCategory.WATER_CREATURE || category == MobCategory.WATER_AMBIENT
				|| category == MobCategory.UNDERGROUND_WATER_CREATURE || category == MobCategory.AXOLOTLS;
			if (!water && canAddPassenger(mob) && mob.getBbWidth() < getBbWidth()
				&& mob.getBbWidth() * mob.getBbHeight() < MAX_SEAT_FOOTPRINT) {
				if (mob instanceof TamableAnimal tamable) {
					tamable.setInSittingPose(true);
				}
				mob.startRiding(this);
				if (getPassengers().size() >= seats()) {
					return;
				}
			}
		}
	}

	/** How many riders it seats: the coach six, the wagon's bench one, the animal cart two. */
	private int seats() {
		return switch (kind()) {
			case COACH -> COACH_SEATS;
			case WAGON -> 1;
			case ANIMAL_CART -> 2;
			default -> 0;
		};
	}

	/** Whether any mob (not a player, not a body) is aboard. */
	private boolean carriesMobs() {
		return getPassengers().stream().anyMatch(p -> !(p instanceof Player) && !(p instanceof CorpseEntity));
	}

	/**
	 * Server: finds its horse after a reload; lets go if the puller's gone, too far, or (a player) can't pull it
	 * any more; keeps the puller slowed, and a riderless horse standing still.
	 */
	private @Nullable LivingEntity serverHitch(ServerLevel level, @Nullable LivingEntity puller) {
		if (puller == null && hitchUuid != null) {
			if (level.getEntity(hitchUuid) instanceof AbstractHorse found && found.isAlive()) {
				this.entityData.set(HITCH, found.getId());
				puller = found;
			} else if (++hitchSearch > 600) {
				// 30 s and it hasn't turned up
				hitchUuid = null;
			}
		}
		if (puller == null) {
			return null;
		}
		boolean gone = !puller.isAlive() || puller.distanceToSqr(this) > SNAP_DISTANCE * SNAP_DISTANCE * 4.0
			|| distanceToSqr(puller.position()) > Math.pow(poleLength(puller) + SNAP_DISTANCE - 2.0, 2);
		if (puller instanceof Player player) {
			if (!gone && tooHeavyByHand()) {
				player.sendOverlayMessage(Component.translatable(kind() == CartKind.COACH
					? "message.jaymoskitchen.cart.too_heavy_coach" : "message.jaymoskitchen.cart.too_heavy"));
				gone = true;
			}
			gone |= player.isSpectator() || player.isPassenger() || hasPassenger(player);
		}
		if (gone) {
			unhitch(level);
			return null;
		}
		AttributeInstance speed = puller.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && drag(puller) != 0.0 && !speed.hasModifier(TOWING)) {
			speed.addTransientModifier(new AttributeModifier(TOWING, drag(puller), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		if (puller instanceof AbstractHorse horse && horse.getControllingPassenger() == null) {
			// hitched up and nobody in the saddle: it waits rather than wandering off with the cart
			horse.getNavigation().stop();
		}
		return puller;
	}

	/**
	 * The plow: tills the 3x3 patch behind it, a hoe's use per block. How thoroughly depends on its hoes: each block
	 * has a 1 - 0.85^(strength^1.3) chance a tick, strength adding up over the hoes (wood 1 ... netherite 4), so one
	 * wooden hoe leaves it patchy (15%) and three netherite ones nearly solid (98%).
	 */
	private void till(ServerLevel level, LivingEntity puller) {
		boolean free = puller instanceof Player player && player.getAbilities().instabuild
			|| puller.getControllingPassenger() instanceof Player rider && rider.getAbilities().instabuild;
		float strength = free ? 12.0F : 0.0F;
		for (ItemStack hoe : items) {
			if (!hoe.isEmpty()) {
				strength += HOE_STRENGTH.getOrDefault(hoe.getItem(), OTHER_HOE);
			}
		}
		if (strength <= 0.0F) {
			return;
		}
		float chance = 1.0F - (float) Math.pow(0.85, Math.pow(strength, 1.3));
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		double forwardX = -Mth.sin(yaw), forwardZ = Mth.cos(yaw);
		double centreX = getX() - forwardX * TILL_BEHIND, centreZ = getZ() - forwardZ * TILL_BEHIND;
		for (int across = -1; across <= 1; across++) {
			for (int along = -1; along <= 1; along++) {
				if (random.nextFloat() > chance) {
					// patchy work: it misses bits, and may catch them next tick
					continue;
				}
				double x = centreX + forwardZ * across + forwardX * along;
				double z = centreZ - forwardX * across + forwardZ * along;
				for (double dy : new double[] {-0.5, 0.5, -1.5}) {
					BlockPos pos = BlockPos.containing(x, getY() + dy, z);
					if (tillOne(level, pos)) {
						if (!free && !wearHoe(level)) {
							return;
						}
						break;
					}
				}
			}
		}
	}

	/** Tills one block if it can be (with open air above), as a hoe would. */
	private static boolean tillOne(ServerLevel level, BlockPos pos) {
		if (!level.getBlockState(pos.above()).isAir()) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		BlockState tilled;
		if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH)) {
			tilled = Blocks.FARMLAND.defaultBlockState();
		} else if (state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT)) {
			tilled = Blocks.DIRT.defaultBlockState();
			if (state.is(Blocks.ROOTED_DIRT)) {
				Block.popResource(level, pos.above(), new ItemStack(Items.HANGING_ROOTS));
			}
		} else {
			return false;
		}
		level.setBlock(pos, tilled, Block.UPDATE_ALL_IMMEDIATE);
		level.playSound(null, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 0.6F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		return true;
	}

	/** Takes a use off one of the hoes (shared out evenly between them); false once there are none left. */
	private boolean wearHoe(ServerLevel level) {
		List<ItemStack> hoes = items.stream().filter(stack -> !stack.isEmpty()).toList();
		if (hoes.isEmpty()) {
			return false;
		}
		hoes.get(random.nextInt(hoes.size())).hurtAndBreak(1, level, null, broken -> play(level, SoundEvents.ITEM_BREAK.value(), 1.0F));
		shownDirty = true;
		return true;
	}

	/** Trails behind its puller at the end of its pole, turning to face it (NiftyCarts' carts pitching toward it). */
	private void follow(LivingEntity puller) {
		Vec3 hitch = puller.position();
		double dx = hitch.x - getX();
		double dz = hitch.z - getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		if (distance < 1.0E-4) {
			float yaw = puller.getYRot() * Mth.DEG_TO_RAD;
			dx = -Mth.sin(yaw);
			dz = Mth.cos(yaw);
			distance = 1.0;
		}
		double nx = dx / distance, nz = dz / distance;
		double targetX = hitch.x - nx * poleLength(puller);
		double targetZ = hitch.z - nz * poleLength(puller);
		double fall = onGround() ? -0.08 : getDeltaMovement().y - 0.08;
		move(MoverType.SELF, new Vec3(targetX - getX(), fall, targetZ - getZ()));
		setDeltaMovement(0.0, onGround() ? 0.0 : Math.max(fall, -2.0), 0.0);
		setYRot((float) (Mth.atan2(-nx, nz) * Mth.RAD_TO_DEG));
		if (kind().tilts) {
			setXRot((float) (-Mth.atan2(hitch.y - getY(), poleLength(puller)) * Mth.RAD_TO_DEG));
		}
	}

	@Override
	public float maxUpStep() {
		// wheels ride up a block
		return 1.0F;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (!(level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		LivingEntity current = puller();
		// pulling it by hand: let go
		if (current == player) {
			unhitch(level);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cart.let_go"));
			return InteractionResult.SUCCESS_SERVER;
		}
		// from the saddle: hitch up, or unhitch
		if (player.getVehicle() instanceof AbstractHorse ridden) {
			if (current == ridden) {
				if (kind() == CartKind.PLOW && !clickedFront(location)) {
					// from the saddle: the back of the plow raises and lowers it, the front unhitches
					return toggleArms(level, player);
				}
				unhitch(level);
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cart.unhitched"));
				return InteractionResult.SUCCESS_SERVER;
			}
			return tryHitch(level, player, ridden);
		}
		// leading a horse on a lead: hitch it up
		if (current == null) {
			for (AbstractHorse led : level.getEntitiesOfClass(AbstractHorse.class, getBoundingBox().inflate(10.0),
				horse -> horse.getLeashHolder() == player)) {
				InteractionResult result = tryHitch(level, player, led);
				if (led.getLeashHolder() == player && puller() == led) {
					led.dropLeash();
				}
				return result;
			}
		}
		// the front end: take it by the shafts
		if (current == null && clickedFront(location)) {
			if (tooHeavyByHand()) {
				player.sendOverlayMessage(Component.translatable(kind() == CartKind.COACH
					? "message.jaymoskitchen.cart.too_heavy_coach" : "message.jaymoskitchen.cart.too_heavy"));
				return InteractionResult.SUCCESS_SERVER;
			}
			this.entityData.set(HITCH, player.getId());
			hitchUuid = null;
			play(level, SoundEvents.WOOD_HIT, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cart.grabbed"));
			return InteractionResult.SUCCESS_SERVER;
		}
		switch (kind()) {
			case COACH -> {
				if (player.isSecondaryUseActive()) {
					// let the mobs out (it has nothing to open)
					if (carriesMobs()) {
						tip(level);
					}
				} else if (canAddPassenger(player)) {
					player.startRiding(this);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			case PLOW -> {
				// lower or raise it (sneak to get at the hoes)
				if (!player.isSecondaryUseActive()) {
					return toggleArms(level, player);
				}
			}
			case ANIMAL_CART -> {
				if (player.isSecondaryUseActive()) {
					// tip it over: everything in it spills out
					tip(level);
				} else if (canAddPassenger(player)) {
					player.startRiding(this);
				}
				return InteractionResult.SUCCESS_SERVER;
			}
			case WAGON -> {
				if (player.isSecondaryUseActive() && carriesMobs()) {
					// mobs on the bench: let them out (without any, sneaking opens the chest)
					tip(level);
					return InteractionResult.SUCCESS_SERVER;
				}
				// the bench
				if (!player.isSecondaryUseActive() && getPassengers().isEmpty()) {
					player.startRiding(this);
					return InteractionResult.SUCCESS_SERVER;
				}
			}
			default -> {
			}
		}
		if (kind() == CartKind.SUPPLY_CART && player.isSecondaryUseActive()
			&& getPassengers().stream().anyMatch(p -> p instanceof CorpseEntity)) {
			// tip it over: the bodies spill out (its stored goods stay put)
			tip(level);
			return InteractionResult.SUCCESS_SERVER;
		}
		player.openMenu(this);
		if (kind() == CartKind.PLOW) {
			play(level, SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F);
		} else if (kind() == CartKind.SUPPLY_CART) {
			play(level, SoundEvents.CHEST_OPEN, 1.0F);
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	/** Starts rolling the cart over onto its side (see TIP); the load spills out partway over. */
	private void tip(ServerLevel level) {
		if (this.entityData.get(TIP) > 0) {
			return;
		}
		this.entityData.set(TIP, TIP_TICKS);
		play(level, SoundEvents.WOOD_FALL, 0.8F);
	}

	/** Bodies and animals (and the wagon's and coach's mobs) out onto the ground on the cart's right, the way it tips: clear of it, not under it. */
	private void spillLoad() {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
		for (Entity passenger : List.copyOf(getPassengers())) {
			if (passenger instanceof CorpseEntity corpse) {
				corpse.tipOut(this, right);
			} else if (!(passenger instanceof Player)) {
				passenger.stopRiding();
				passenger.setPos(getX() + right.x * (getBbWidth() * 0.5 + 0.7), getY() + 0.5, getZ() + right.z * (getBbWidth() * 0.5 + 0.7));
				passenger.setDeltaMovement(right.scale(0.2).add(0.0, 0.2, 0.0));
				if (kind() == CartKind.WAGON || kind() == CartKind.COACH) {
					// (a pet that was sat down to ride gets up again; the wagon and coach wait a while before taking more in)
					if (passenger instanceof TamableAnimal tamable) {
						tamable.setInSittingPose(false);
					}
					noLoadUntil = level().getGameTime() + RELOAD_DELAY;
				}
			}
		}
		if (level() instanceof ServerLevel level) {
			play(level, SoundEvents.WOOD_HIT, 0.9F);
		}
	}

	/** Client: how far over it has rolled to tip its load out (degrees, 0 upright). */
	public float tipAngle(float partialTicks) {
		int tip = this.entityData.get(TIP);
		if (tip <= 0) {
			return 0.0F;
		}
		float t = (TIP_TICKS - tip + partialTicks) / TIP_TICKS;
		// over quickly, a moment on its side, then rocked back upright
		float over = t < 0.25F ? t / 0.25F : t < 0.5F ? 1.0F : 1.0F - (t - 0.5F) / 0.5F;
		over = Mth.clamp(over, 0.0F, 1.0F);
		return 70.0F * (over * over * (3.0F - 2.0F * over));
	}

	/** Whether a click (relative to the cart) landed on its front end, by the shafts. */
	private boolean clickedFront(Vec3 location) {
		float yaw = getYRot() * Mth.DEG_TO_RAD;
		return location.x * -Mth.sin(yaw) + location.z * Mth.cos(yaw) > getBbWidth() * 0.3;
	}

	private InteractionResult toggleArms(ServerLevel level, Player player) {
		boolean lower = !lowered();
		this.entityData.set(LOWERED, lower);
		play(level, lower ? SoundEvents.ROOTED_DIRT_PLACE : SoundEvents.WOOD_PLACE, lower ? 0.8F : 1.1F);
		player.sendOverlayMessage(Component.translatable(!lower ? "message.jaymoskitchen.plow.raised"
			: isEmpty() && !player.getAbilities().instabuild ? "message.jaymoskitchen.plow.no_hoes" : "message.jaymoskitchen.plow.lowered"));
		return InteractionResult.SUCCESS_SERVER;
	}

	private InteractionResult tryHitch(ServerLevel level, Player player, AbstractHorse horse) {
		String problem = null;
		if (puller() != null) {
			problem = "already_hitched";
		} else if (!mayPull(horse)) {
			problem = "draft_only";
		} else if (!horse.isTamed() || horse.isBaby()) {
			problem = "need_tame";
		} else if (!level.getEntitiesOfClass(CartEntity.class, horse.getBoundingBox().inflate(SNAP_DISTANCE),
			cart -> cart.puller() == horse).isEmpty()) {
			problem = "horse_busy";
		}
		if (problem != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cart." + problem));
			return InteractionResult.SUCCESS_SERVER;
		}
		this.entityData.set(HITCH, horse.getId());
		hitchUuid = horse.getUUID();
		play(level, SoundEvents.LEAD_TIED, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.cart.hitched"));
		return InteractionResult.SUCCESS_SERVER;
	}

	private void unhitch(ServerLevel level) {
		LivingEntity puller = puller();
		if (puller != null) {
			AttributeInstance speed = puller.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(TOWING);
			}
		}
		this.entityData.set(HITCH, -1);
		hitchUuid = null;
		play(level, SoundEvents.LEAD_UNTIED, 1.0F);
	}

	private void play(ServerLevel level, SoundEvent sound, float pitch) {
		level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 1.0F, pitch);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isRemoved() || !(source.getEntity() instanceof Player player)) {
			return false;
		}
		this.entityData.set(HURT, 10);
		play(level, SoundEvents.WOOD_HIT, 1.0F);
		if (player.getAbilities().instabuild || ++hits >= HITS_TO_BREAK) {
			unhitch(level);
			Containers.dropContents(level, this, this);
			if (!player.getAbilities().instabuild) {
				spawnAtLocation(level, new ItemStack(Saddlebags.cartItem(kind(), wood())));
			}
			play(level, SoundEvents.WOOD_BREAK, 1.0F);
			ejectPassengers();
			discard();
		}
		return true;
	}

	@Override
	public @Nullable ItemStack getPickResult() {
		return new ItemStack(Saddlebags.cartItem(kind(), wood()));
	}

	@Override
	public boolean isPickable() {
		return !isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// --- riders ---

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		if (passenger instanceof CorpseEntity corpse) {
			return takesCorpse(corpse);
		}
		return switch (kind()) {
			case WAGON -> getPassengers().isEmpty();
			case COACH -> getPassengers().size() < COACH_SEATS;
			// live animals take a cow's room each, beside any bodies
			case ANIMAL_CART -> getPassengers().size() < 2 && roomUsed() + 3 <= corpseRoom();
			default -> false;
		};
	}

	/**
	 * Room for bodies, in small-animal units (a cow-sized body takes 3): the animal cart holds two cows or six small
	 * animals, the supply cart three cows or nine small ones. The other carts take none.
	 */
	public int corpseRoom() {
		return switch (kind()) {
			case ANIMAL_CART -> 6;
			case SUPPLY_CART -> 9;
			default -> 0;
		};
	}

	/** Room taken: each body by its size, each live animal (or player) as a cow. */
	private int roomUsed() {
		int used = 0;
		for (Entity passenger : getPassengers()) {
			used += passenger instanceof CorpseEntity corpse ? corpse.cartRoom() : 3;
		}
		return used;
	}

	/** Whether this body fits in (a thrown one landing, or one loaded). */
	public boolean takesCorpse(CorpseEntity corpse) {
		int room = corpse.cartRoom();
		return room > 0 && corpseRoom() > 0 && !hasPassenger(corpse) && roomUsed() + room <= corpseRoom();
	}

	/**
	 * Where a body lies in the bed (cart-local: sideways, ahead, up, and its turn from lengthwise), from what's loaded.
	 * Big bodies lie lengthwise side by side on the floor, head to tail; a third big one goes across the top. Small
	 * ones fill the floor around them, two across and three along, then pile on top of whatever's there, each at its
	 * own angle. So the load looks different for every mix.
	 */
	private double[] corpseSlot(CorpseEntity corpse) {
		List<CorpseEntity> bigs = new java.util.ArrayList<>(), smalls = new java.util.ArrayList<>();
		for (Entity passenger : getPassengers()) {
			if (passenger instanceof CorpseEntity c) {
				(c.cartRoom() >= 3 ? bigs : smalls).add(c);
			}
		}
		final double floor = 2.0 / 16.0, layer = 0.32;
		final double[] sides = {-0.22, 0.22}, aheads = {0.32, -0.02, -0.36};
		int big = bigs.indexOf(corpse);
		if (big >= 0) {
			if (big < 2) {
				// lengthwise, side by side (in the middle if it's the only body), head to tail
				double side = bigs.size() == 1 && smalls.isEmpty() ? 0.0 : sides[big];
				return new double[] {side, -0.02, floor, big == 0 ? 90.0 : 270.0};
			}
			// a third: across the top of the other two
			return new double[] {0.0, -0.02, floor + layer, 0.0};
		}
		// small ones: the floor cells beside the big bodies
		List<double[]> cells = new java.util.ArrayList<>();
		boolean[] columnTaken = {bigs.size() >= 1, bigs.size() >= 2};
		for (int col = 0; col < 2; col++) {
			for (double ahead : aheads) {
				if (!columnTaken[col]) {
					cells.add(new double[] {sides[col], ahead, floor});
				}
			}
		}
		int i = smalls.indexOf(corpse);
		java.util.Random seeded = new java.util.Random(corpse.poseSeed());
		double turn = seeded.nextDouble() * 360.0;
		if (i < cells.size()) {
			double[] cell = cells.get(i);
			return new double[] {cell[0], cell[1], cell[2], turn};
		}
		// piled on top: over the bigs (one layer up, two if there's already a third on top), or over other smalls
		int over = i - cells.size();
		double height = floor + layer * (bigs.isEmpty() ? 1 : bigs.size() >= 3 ? 2 : 1);
		double side = sides[over % 2] * 0.8 + (seeded.nextDouble() - 0.5) * 0.08;
		double ahead = aheads[(over / 2) % 3] + (seeded.nextDouble() - 0.5) * 0.1;
		return new double[] {side, ahead, height + 0.06 * (over / 6), turn};
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		if (passenger instanceof CorpseEntity corpse) {
			// lying in the bed, which tips with the cart: up and ahead from the axle, turned with it
			double[] slot = corpseSlot(corpse);
			double axle = NiftyCarts.WHEEL_RADIUS / 16.0;
			float pitch = getXRot() * Mth.DEG_TO_RAD;
			Vec3 at = new Vec3(slot[0], axle + slot[2] * Mth.cos(pitch) - slot[1] * Mth.sin(pitch),
				slot[2] * Mth.sin(pitch) + slot[1] * Mth.cos(pitch));
			return at.yRot(-getYRot() * Mth.DEG_TO_RAD);
		}
		if (kind() == CartKind.ANIMAL_CART) {
			// NiftyCarts: one rider in the middle, or two one behind the other
			double along = -0.1;
			if (getPassengers().size() > 1) {
				along = getPassengers().indexOf(passenger) == 0 ? 0.2 : -0.6;
				if (!(passenger instanceof Player)) {
					along += 0.2;
				}
			}
			Vec3 forward = getLookAngle().scale(along + Mth.sin(getXRot() * Mth.DEG_TO_RAD) * 0.7);
			return new Vec3(forward.x, (dimensions.height() - 0.5) * scale + forward.y, forward.z);
		}
		if (kind() == CartKind.COACH) {
			// bench by bench from the front, two to a bench: the model's seats are 8 px above the axle
			int seat = Math.max(0, getPassengers().indexOf(passenger));
			float size = NiftyCarts.WAGON_SCALE / 16.0F;
			double side = (seat % 2 == 0 ? -1.0 : 1.0) * COACH_SEAT_SIDE * size;
			Vec3 spot = new Vec3(side, NiftyCarts.WHEEL_RADIUS * size + 8.0 * size, COACH_BENCH_AHEAD[Math.min(seat / 2, 2)] * size);
			return spot.yRot(-getYRot() * Mth.DEG_TO_RAD);
		}
		// the bench at the front of the wagon: 8 px above the axle and 10.5 ahead of it (a size up), tipping with it
		float size = NiftyCarts.WAGON_SCALE / 16.0F;
		double up = 8.0 * size, ahead = 10.5 * size, axle = NiftyCarts.WHEEL_RADIUS * size;
		float pitch = getXRot() * Mth.DEG_TO_RAD;
		Vec3 seat = new Vec3(0.0, axle + up * Mth.cos(pitch) - ahead * Mth.sin(pitch), up * Mth.sin(pitch) + ahead * Mth.cos(pitch));
		return seat.yRot(-getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
		super.positionRider(passenger, moveFunction);
		if (passenger instanceof CorpseEntity corpse && hasPassenger(passenger)) {
			// lying the way its slot says, turning with the cart
			float yaw = getYRot() + (float) corpseSlot(corpse)[3];
			passenger.setYRot(yaw);
			passenger.yRotO = yaw;
			return;
		}
		if ((kind() == CartKind.WAGON || kind() == CartKind.COACH) && hasPassenger(passenger)
			&& passenger instanceof LivingEntity living && !(passenger instanceof Player)) {
			// mobs sit facing forward
			living.setYBodyRot(getYRot());
			passenger.setYRot(getYRot());
			passenger.setYHeadRot(getYRot());
			return;
		}
		if (kind() == CartKind.COACH && hasPassenger(passenger) && passenger instanceof Player) {
			// passengers look about, but not much further than over their shoulders
			float turn = Mth.wrapDegrees(passenger.getYRot() - getYRot());
			float clamped = Mth.clamp(turn, -105.0F, 105.0F);
			passenger.yRotO += clamped - turn;
			passenger.setYRot(passenger.getYRot() + clamped - turn);
			passenger.setYHeadRot(passenger.getYRot());
			return;
		}
		if (kind() == CartKind.ANIMAL_CART && hasPassenger(passenger) && passenger instanceof LivingEntity living) {
			// riders face forward, give or take; two animals sit sideways, facing opposite ways
			living.setYBodyRot(getYRot());
			float turn = Mth.wrapDegrees(passenger.getYRot() - getYRot());
			float clamped = Mth.clamp(turn, -105.0F, 105.0F);
			passenger.yRotO += clamped - turn;
			passenger.setYRot(passenger.getYRot() + clamped - turn);
			passenger.setYHeadRot(passenger.getYRot());
			if (!(passenger instanceof Player) && getPassengers().size() > 1) {
				int side = passenger.getId() % 2 == 0 ? 90 : 270;
				living.setYBodyRot(living.yBodyRot + side);
				passenger.setYHeadRot(passenger.getYHeadRot() + side);
			}
		}
	}

	// --- the inventory ---

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		shownDirty = true;
		return ContainerHelper.removeItem(items, slot, count);
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		shownDirty = true;
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		stack.limitSize(getMaxStackSize(stack));
		shownDirty = true;
	}

	@Override
	public void setChanged() {
		shownDirty = true;
	}

	@Override
	public void startOpen(ContainerUser user) {
		if (kind() == CartKind.WAGON && !isRemoved() && viewers++ == 0 && level() instanceof ServerLevel level) {
			this.entityData.set(CHEST_OPEN, true);
			level.playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 0.9F + random.nextFloat() * 0.1F);
		}
	}

	@Override
	public void stopOpen(ContainerUser user) {
		if (kind() == CartKind.WAGON && !isRemoved() && viewers > 0 && --viewers == 0 && level() instanceof ServerLevel level) {
			this.entityData.set(CHEST_OPEN, false);
			level.playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5F, 0.9F + random.nextFloat() * 0.1F);
		}
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return kind() != CartKind.PLOW || stack.is(ItemTags.HOES);
	}

	@Override
	public boolean stillValid(Player player) {
		return !isRemoved() && player.isWithinEntityInteractionRange(this, 4.0);
	}

	@Override
	public void clearContent() {
		items.clear();
		shownDirty = true;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable(Saddlebags.cartItem(kind(), wood()).getDescriptionId());
	}

	@Override
	public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return switch (kind()) {
			case WAGON -> ChestMenu.threeRows(containerId, inventory, this);
			case SUPPLY_CART -> ChestMenu.sixRows(containerId, inventory, this);
			case PLOW -> new PlowMenu(containerId, inventory, this);
			case ANIMAL_CART, COACH -> null;
		};
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		ContainerHelper.saveAllItems(output, items);
		output.storeNullable("Hitch", UUIDUtil.CODEC, hitchUuid);
		output.putBoolean("Lowered", lowered());
		output.putString("Wood", wood());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		ContainerHelper.loadAllItems(input, items);
		hitchUuid = input.read("Hitch", UUIDUtil.CODEC).orElse(null);
		this.entityData.set(LOWERED, input.getBooleanOr("Lowered", false));
		setWood(input.getStringOr("Wood", "oak"));
		shownDirty = true;
	}
}
