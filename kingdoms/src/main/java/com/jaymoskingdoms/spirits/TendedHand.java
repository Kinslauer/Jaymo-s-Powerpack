package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.275) A hand left behind. Andy linked Mage Hand (CurseForge, SkylorBeck, GPL-3.0: ideas only, our own code and look):
 * "can this be implemented with our mana spirits?" A keeper casts <b>Leave a hand</b> ({@link HandMoves}) at a chest, barrel
 * or any other container in reach, and one of their spirit's hands stays there, hovering over it, after they walk away:
 * <ul>
 * <li><b>Gathers</b>: picks up what lies on the ground within {@link #GATHER_RANGE} and puts it in the container. If the
 * container already holds something, it only gathers the kinds it holds (so a chest of cobblestone collects cobblestone).</li>
 * <li><b>Guards</b>: strikes every monster that comes within {@link #GUARD_RANGE} of it (never an ally of its keeper).</li>
 * <li><b>Tends</b> ({@link #FARMS}): harvests ripe crops within {@link #FARM_RANGE}, replants them, sows bare farmland from
 * seeds in the container, and feeds unripe crops the bone meal it finds there; the harvest goes in the container.</li>
 * </ul>
 * It draws a little of its keeper's mana ({@link #UPKEEP} a second, more for each blow), and rests (dim, still) while they are
 * away, out of mana or burnt out. A keeper has one hand to leave, two from bond {@link #TWO_HANDS_BOND} and three from level
 * {@link #THREE_HANDS_LEVEL}; leaving one more takes back the oldest, and casting at a hand's own container calls it home.
 * Where they are is kept on the keeper ({@link #LEFT}), so a hand whose keeper has called it back (or released their spirit)
 * fades the next time it looks. Only the attuned see the hand; anyone sees what it carries float.
 */
public final class TendedHand extends Entity {
	/** How far it gathers, guards and farms from its container (blocks; farming is square, two up and down). */
	static final double GATHER_RANGE = 10.0, GUARD_RANGE = 8.0;
	static final int FARM_RANGE = 6, FARM_DEPTH = 2;
	/** Whether the hands farm (Mage Hand's gold hand). */
	static final boolean FARMS = true;
	/** How fast it flies (blocks a tick), and how near counts as there. */
	static final double SPEED = 0.35, THERE = 0.7;
	/** How high it hovers over its container at rest, and how far it bobs. */
	static final double REST_HIGH = 1.3, BOB = 0.12;
	/** Ticks between its looks round for work. */
	static final int LOOK_EVERY = 10;
	/** A blow: damage at level 1 (scaled like every move, {@link Spirits#damageScale}), ticks between blows, how hard it throws back. */
	static final float BLOW_DAMAGE = 4.0F;
	static final int BLOW_EVERY = 20;
	static final double BLOW_KNOCK = 0.6;
	/** Mana: its upkeep a second, a blow, a harvest or a sowing; it rests rather than take its keeper below {@code FLOOR}. */
	static final float UPKEEP = 0.1F, BLOW_COST = 1.0F, TEND_COST = 0.25F, FLOOR = 10.0F;
	/** Ticks between two feeds of bone meal. */
	static final int MEAL_EVERY = 100;
	/** How many hands a keeper may leave: one, two from this bond, three from this level. */
	static final int TWO_HANDS_BOND = 60, THREE_HANDS_LEVEL = 15;
	/** Ticks a task may run before it is given up (an item it can't reach, a foe that hides). */
	static final int GIVE_UP = 200;

	/** The containers a keeper's hands are left at, oldest first. Saved and kept through death. */
	public static final AttachmentType<List<GlobalPos>> LEFT = AttachmentRegistry.create(JaymosKingdoms.id("spirit_left_hands"),
		builder -> builder.persistent(GlobalPos.CODEC.listOf()).copyOnDeath());

	public static EntityType<TendedHand> TYPE;

	/** What the hand is doing, for the look: resting, reaching, carrying, striking. */
	public static final int REST = 0, REACH = 1, CARRY = 2, STRIKE = 3;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TendedHand.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> ACT = SynchedEntityData.defineId(TendedHand.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> AWAKE = SynchedEntityData.defineId(TendedHand.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<ItemStack> HELD = SynchedEntityData.defineId(TendedHand.class, EntityDataSerializers.ITEM_STACK);
	/** The tick of its last blow (the look's punch). */
	private static final EntityDataAccessor<Integer> BLOW = SynchedEntityData.defineId(TendedHand.class, EntityDataSerializers.INT);

	private enum Task { REST, FETCH, RETURN, STRIKE, HARVEST, SOW, FEED }

	private @Nullable UUID keeper;
	private BlockPos home = BlockPos.ZERO;
	/** What it carries to the container (the first shows in its hand). */
	private final List<ItemStack> load = new ArrayList<>();
	private Task task = Task.REST;
	private @Nullable UUID aim;
	private @Nullable BlockPos spot;
	private int taskTicks, lastBlow = -BLOW_EVERY, lastMeal = -MEAL_EVERY;

	public TendedHand(EntityType<? extends TendedHand> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	/** Registers the entity type and the keeper's list (from {@link HandMoves#register}). */
	static void register() {
		JaymosKingdoms.LOGGER.debug("tended hands: {}", LEFT);
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("tended_hand"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<TendedHand>of(TendedHand::new, MobCategory.MISC)
			.noLootTable()
			.fireImmune()
			.sized(0.5F, 0.5F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
	}

	// ---- leaving one and calling it back -----------------------------------------------------------------------------

	/** How many hands {@code state}'s spirit may leave. */
	static int hands(SpiritState state) {
		return state.level() >= THREE_HANDS_LEVEL ? 3 : state.bond() >= TWO_HANDS_BOND ? 2 : 1;
	}

	/**
	 * {@code player} casts at the container at {@code pos}: their hand there goes home, or a new one is left (the oldest
	 * taken back if they have no hand to spare). False if there is no container there.
	 */
	static boolean leave(ServerPlayer player, SpiritState state, BlockPos pos) {
		ServerLevel level = (ServerLevel)player.level();
		if (HopperBlockEntity.getContainerAt(level, pos) == null) {
			return false;
		}
		GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
		List<GlobalPos> left = new ArrayList<>(player.getAttachedOrElse(LEFT, List.of()));
		TendedHand there = at(level, player.getUUID(), pos);
		if (left.remove(at) || there != null) {
			player.setAttached(LEFT, List.copyOf(left));
			if (there != null) {
				there.fade(level);
			}
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.spirit.hand_home"));
			return true;
		}
		while (left.size() >= hands(state)) {
			GlobalPos old = left.remove(0);
			ServerLevel oldLevel = level.getServer().getLevel(old.dimension());
			TendedHand gone = oldLevel == null ? null : at(oldLevel, player.getUUID(), old.pos());
			if (gone != null) {
				gone.fade(oldLevel);
			}
		}
		left.add(at);
		player.setAttached(LEFT, List.copyOf(left));
		TendedHand hand = new TendedHand(TYPE, level);
		hand.keeper = player.getUUID();
		hand.home = pos.immutable();
		hand.entityData.set(KIND, state.kind());
		hand.entityData.set(AWAKE, true);
		Vec3 from = player.getEyePosition().add(player.getLookAngle());
		hand.setPos(from.x, from.y, from.z);
		level.addFreshEntity(hand);
		Mana.sendSeen(level, Mana.MOTE, from.x, from.y, from.z, 12, 0.2, 0.2, 0.2, 0.03);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.3F);
		player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymoskingdoms.spirit.hand_left", left.size(), hands(state)));
		return true;
	}

	/** {@code keeper}'s hand at {@code pos}, if it is loaded. */
	private static @Nullable TendedHand at(ServerLevel level, UUID keeper, BlockPos pos) {
		for (TendedHand hand : level.getEntitiesOfClass(TendedHand.class, new AABB(pos).inflate(GATHER_RANGE + 4.0))) {
			if (keeper.equals(hand.keeper) && hand.home.equals(pos)) {
				return hand;
			}
		}
		return null;
	}

	/** Lets go of what it carries (into the container, or onto it) and fades. */
	private void fade(ServerLevel level) {
		this.unload(level);
		for (ItemStack stack : this.load) {
			ItemEntity drop = new ItemEntity(level, this.getX(), this.getY(), this.getZ(), stack);
			level.addFreshEntity(drop);
		}
		this.load.clear();
		Mana.sendSeen(level, Mana.MOTE, this.getX(), this.getY(), this.getZ(), 10, 0.2, 0.2, 0.2, 0.03);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.7F);
		this.discard();
	}

	// ---- its work ----------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(KIND, 0);
		builder.define(ACT, REST);
		builder.define(AWAKE, false);
		builder.define(HELD, ItemStack.EMPTY);
		builder.define(BLOW, -100);
	}

	public int kind() {
		return this.entityData.get(KIND);
	}

	/** {@link #REST}, {@link #REACH}, {@link #CARRY} or {@link #STRIKE}. */
	public int act() {
		return this.entityData.get(ACT);
	}

	/** Whether it is at work (its keeper near enough to the world and able to pay), not resting dim. */
	public boolean awake() {
		return this.entityData.get(AWAKE);
	}

	/** What it carries, for the look. */
	public ItemStack held() {
		return this.entityData.get(HELD);
	}

	/** Ticks since its last blow. */
	public int sinceBlow() {
		return this.tickCount - this.entityData.get(BLOW);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		if (this.keeper == null) {
			this.discard();
			return;
		}
		if (this.tickCount % LOOK_EVERY == 0 && !this.look(level)) {
			return;
		}
		this.taskTicks++;
		this.fly(level);
	}

	/** Looks round: whether it still has a keeper and a container, whether it can work, and what to do. False if it faded. */
	private boolean look(ServerLevel level) {
		MinecraftServer server = level.getServer();
		ServerPlayer player = server.getPlayerList().getPlayer(this.keeper);
		Container container = HopperBlockEntity.getContainerAt(level, this.home);
		if (container == null) {
			if (player != null) {
				List<GlobalPos> left = new ArrayList<>(player.getAttachedOrElse(LEFT, List.of()));
				left.remove(GlobalPos.of(level.dimension(), this.home));
				player.setAttached(LEFT, List.copyOf(left));
			}
			this.fade(level);
			return false;
		}
		SpiritState state = player == null ? null : Spirits.state(player);
		if (player != null && (state == null || !player.getAttachedOrElse(LEFT, List.<GlobalPos>of()).contains(GlobalPos.of(level.dimension(), this.home)))) {
			// called back, or the spirit released
			this.fade(level);
			return false;
		}
		boolean awake = player != null && state != null && Mana.canPay(player, FLOOR);
		this.entityData.set(AWAKE, awake);
		if (!awake) {
			this.rest(level, container);
			return true;
		}
		if (this.tickCount % 20 == 0) {
			Mana.drain(player, UPKEEP);
		}
		if (this.taskTicks > GIVE_UP) {
			this.task = Task.REST;
		}
		// a foe near the container comes first, then what it carries, then the field, then what lies about
		LivingEntity foe = this.task == Task.STRIKE && this.target(level) instanceof LivingEntity living ? living : null;
		if (foe == null || !this.guarded(player, foe)) {
			foe = this.nearestFoe(level, player);
			if (foe != null) {
				this.start(Task.STRIKE, foe.getUUID(), null);
			} else if (this.task == Task.STRIKE) {
				this.task = Task.REST;
			}
		}
		if (this.task == Task.STRIKE) {
			return true;
		}
		if (!this.load.isEmpty()) {
			if (this.task != Task.RETURN && this.task != Task.SOW && this.task != Task.FEED) {
				this.start(Task.RETURN, null, null);
			}
			return true;
		}
		if (this.task == Task.FETCH && this.target(level) instanceof ItemEntity item && item.isAlive() && this.wanted(container, item)) {
			return true;
		}
		if (this.task == Task.HARVEST && this.spot != null && ripe(level.getBlockState(this.spot))) {
			return true;
		}
		this.task = Task.REST;
		if (FARMS && this.tend(level, player, container)) {
			return true;
		}
		ItemEntity item = this.nearestItem(level, container);
		if (item != null) {
			this.start(Task.FETCH, item.getUUID(), null);
		}
		return true;
	}

	private void start(Task task, @Nullable UUID aim, @Nullable BlockPos spot) {
		this.task = task;
		this.aim = aim;
		this.spot = spot;
		this.taskTicks = 0;
	}

	/** Resting: puts down what it carries if it can, and hovers. */
	private void rest(ServerLevel level, Container container) {
		this.unload(level);
		this.task = Task.REST;
	}

	private @Nullable Entity target(ServerLevel level) {
		return this.aim == null ? null : level.getEntity(this.aim);
	}

	private Vec3 above() {
		return Vec3.atBottomCenterOf(this.home).add(0.0, REST_HIGH, 0.0);
	}

	/** Whether {@code e} is a foe of {@code player}'s for the hand to strike: a monster near the container, not their ally. */
	private boolean guarded(ServerPlayer player, LivingEntity e) {
		return e.isAlive() && e instanceof Enemy && !HandMoves.ally(player, e) && e.distanceToSqr(this.above()) <= GUARD_RANGE * GUARD_RANGE;
	}

	private @Nullable LivingEntity nearestFoe(ServerLevel level, ServerPlayer player) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(this.home).inflate(GUARD_RANGE), e -> this.guarded(player, e))) {
			double d = e.distanceToSqr(this);
			if (d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** Whether the hand would gather {@code item} into {@code container}: anything for an empty one, else only the kinds it holds. */
	private boolean wanted(Container container, ItemEntity item) {
		if (item.hasPickUpDelay() || item.getItem().isEmpty()) {
			return false;
		}
		boolean empty = container.isEmpty();
		ItemStack stack = item.getItem();
		return (empty || container.hasAnyMatching(s -> ItemStack.isSameItem(s, stack))) && fits(container, stack);
	}

	/** Whether some of {@code stack} would fit in {@code container}. */
	private static boolean fits(Container container, ItemStack stack) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack in = container.getItem(i);
			if (in.isEmpty() && container.canPlaceItem(i, stack)
				|| ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	private @Nullable ItemEntity nearestItem(ServerLevel level, Container container) {
		ItemEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(this.home).inflate(GATHER_RANGE), i -> i.isAlive() && this.wanted(container, i))) {
			if (this.claimed(level, item.getUUID())) {
				continue;
			}
			double d = item.distanceToSqr(this);
			if (d < bestD) {
				best = item;
				bestD = d;
			}
		}
		return best;
	}

	/** Whether another hand is already after {@code id}. */
	private boolean claimed(ServerLevel level, UUID id) {
		for (TendedHand other : level.getEntitiesOfClass(TendedHand.class, this.getBoundingBox().inflate(GATHER_RANGE * 2.0))) {
			if (other != this && id.equals(other.aim)) {
				return true;
			}
		}
		return false;
	}

	// ---- the field ---------------------------------------------------------------------------------------------------

	private static boolean ripe(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
	}

	/** Finds field work near the container: a ripe crop, bare farmland to sow, an unripe crop to feed. True if it found some. */
	private boolean tend(ServerLevel level, ServerPlayer player, Container container) {
		if (!Mana.canPay(player, FLOOR + TEND_COST)) {
			return false;
		}
		BlockPos bare = null, unripe = null;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -FARM_DEPTH; dy <= FARM_DEPTH; dy++) {
			for (int dx = -FARM_RANGE; dx <= FARM_RANGE; dx++) {
				for (int dz = -FARM_RANGE; dz <= FARM_RANGE; dz++) {
					p.setWithOffset(this.home, dx, dy, dz);
					BlockState state = level.getBlockState(p);
					if (ripe(state)) {
						if (!this.claimedSpot(level, p)) {
							this.start(Task.HARVEST, null, p.immutable());
							return true;
						}
					} else if (state.getBlock() instanceof CropBlock) {
						if (unripe == null) {
							unripe = p.immutable();
						}
					} else if (state.isAir() && bare == null && level.getBlockState(p.below()).getBlock() instanceof FarmlandBlock) {
						bare = p.immutable();
					}
				}
			}
		}
		if (bare != null && !this.claimedSpot(level, bare)) {
			ItemStack seed = take(container, s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof CropBlock);
			if (!seed.isEmpty()) {
				this.load.add(seed);
				this.start(Task.SOW, null, bare);
				return true;
			}
		}
		if (unripe != null && this.tickCount - this.lastMeal >= MEAL_EVERY) {
			ItemStack meal = take(container, s -> s.is(Items.BONE_MEAL));
			if (!meal.isEmpty()) {
				this.lastMeal = this.tickCount;
				this.load.add(meal);
				this.start(Task.FEED, null, unripe);
				return true;
			}
		}
		return false;
	}

	/** Whether another hand is already working {@code pos}. */
	private boolean claimedSpot(ServerLevel level, BlockPos pos) {
		for (TendedHand other : level.getEntitiesOfClass(TendedHand.class, this.getBoundingBox().inflate(FARM_RANGE * 3.0))) {
			if (other != this && pos.equals(other.spot) && other.task != Task.REST) {
				return true;
			}
		}
		return false;
	}

	/** One item matching {@code test} out of {@code container}, or empty. */
	private static ItemStack take(Container container, java.util.function.Predicate<ItemStack> test) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (test.test(container.getItem(i))) {
				ItemStack one = container.removeItem(i, 1);
				container.setChanged();
				return one;
			}
		}
		return ItemStack.EMPTY;
	}

	// ---- flying and doing --------------------------------------------------------------------------------------------

	/** Each tick: flies toward the task, and does it on getting there. */
	private void fly(ServerLevel level) {
		Vec3 goal;
		Entity target = null;
		switch (this.task) {
			case FETCH, STRIKE -> {
				target = this.target(level);
				if (target == null || !target.isAlive()) {
					this.task = Task.REST;
					goal = this.above();
				} else {
					goal = target.position().add(0.0, target.getBbHeight() * (this.task == Task.STRIKE ? 0.6 : 0.3), 0.0);
				}
			}
			case HARVEST, SOW, FEED -> goal = this.spot == null ? this.above() : Vec3.atBottomCenterOf(this.spot).add(0.0, 0.6, 0.0);
			case RETURN -> goal = Vec3.atBottomCenterOf(this.home).add(0.0, 1.1, 0.0);
			default -> {
				double bob = this.awake() ? BOB * Mth.sin(this.tickCount * 0.08F) : -0.4;
				goal = this.above().add(0.0, bob, 0.0);
			}
		}
		Vec3 here = this.position();
		Vec3 way = goal.subtract(here);
		double d = way.length();
		Vec3 next = d <= SPEED ? goal : here.add(way.scale(SPEED / d));
		this.setPos(next.x, next.y, next.z);
		if (way.horizontalDistanceSqr() > 1.0E-3) {
			this.setYRot((float)(Mth.atan2(-way.x, way.z) * Mth.RAD_TO_DEG));
		}
		boolean there = d <= THERE;
		int act = switch (this.task) {
			case STRIKE -> STRIKE;
			case FETCH, HARVEST -> REACH;
			default -> this.load.isEmpty() ? REST : CARRY;
		};
		this.entityData.set(ACT, act);
		this.entityData.set(HELD, this.load.isEmpty() ? ItemStack.EMPTY : this.load.get(0).copyWithCount(1));
		if (!there && !(this.task == Task.STRIKE && d <= 1.6)) {
			return;
		}
		ServerPlayer player = this.keeper == null ? null : level.getServer().getPlayerList().getPlayer(this.keeper);
		switch (this.task) {
			case FETCH -> {
				if (target instanceof ItemEntity item) {
					this.load.add(item.getItem().copy());
					item.discard();
					Mana.sendSeen(level, Mana.MOTE, item.getX(), item.getY(), item.getZ(), 4, 0.1, 0.1, 0.1, 0.02);
					this.start(Task.RETURN, null, null);
				}
			}
			case RETURN -> {
				this.unload(level);
				this.task = Task.REST;
			}
			case STRIKE -> {
				if (target instanceof LivingEntity foe && player != null && this.tickCount - this.lastBlow >= BLOW_EVERY && Mana.canPay(player, FLOOR + BLOW_COST)) {
					this.strike(level, player, foe);
				}
			}
			case HARVEST -> {
				if (this.spot != null && player != null) {
					this.harvest(level, player, this.spot);
				}
				this.start(this.load.isEmpty() ? Task.REST : Task.RETURN, null, null);
			}
			case SOW -> {
				if (this.spot != null && player != null) {
					this.sow(level, player, this.spot);
				}
				this.start(this.load.isEmpty() ? Task.REST : Task.RETURN, null, null);
			}
			case FEED -> {
				if (this.spot != null && !this.load.isEmpty() && this.load.get(0).is(Items.BONE_MEAL)
					&& BoneMealItem.growCrop(this.load.get(0), level, this.spot)) {
					BoneMealItem.addGrowthParticles(level, this.spot, 15);
					if (this.load.get(0).isEmpty()) {
						this.load.remove(0);
					}
				}
				this.start(this.load.isEmpty() ? Task.REST : Task.RETURN, null, null);
			}
			default -> {
			}
		}
	}

	/** Puts what it carries in its container; what won't fit it keeps hold of. */
	private void unload(ServerLevel level) {
		if (this.load.isEmpty()) {
			return;
		}
		Container container = HopperBlockEntity.getContainerAt(level, this.home);
		if (container == null) {
			return;
		}
		boolean put = false;
		for (int i = this.load.size() - 1; i >= 0; i--) {
			ItemStack stack = this.load.get(i);
			int before = stack.getCount();
			ItemStack left = HopperBlockEntity.addItem(null, container, stack, Direction.UP);
			put |= left.getCount() < before;
			if (left.isEmpty()) {
				this.load.remove(i);
			} else {
				this.load.set(i, left);
			}
		}
		if (put) {
			level.playSound(null, this.home, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.6F, 1.1F);
			Mana.sendSeen(level, Mana.MOTE, this.home.getX() + 0.5, this.home.getY() + 1.0, this.home.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.02);
		}
	}

	private void strike(ServerLevel level, ServerPlayer player, LivingEntity foe) {
		SpiritState state = Spirits.state(player);
		float damage = BLOW_DAMAGE * (state == null ? 1.0F : Spirits.damageScale(state));
		DamageSource source = level.damageSources().indirectMagic(this, player);
		foe.hurtServer(level, source, damage);
		foe.knockback(BLOW_KNOCK, this.getX() - foe.getX(), this.getZ() - foe.getZ(), source, 0.0F);
		Mana.drain(player, BLOW_COST);
		this.lastBlow = this.tickCount;
		this.entityData.set(BLOW, this.tickCount);
		level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(), 6, 0.2, 0.2, 0.2, 0.2);
		Mana.sendSeen(level, Mana.MOTE, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(), 6, 0.2, 0.2, 0.2, 0.05);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.4F);
	}

	/** Harvests the ripe crop at {@code pos}: its drops in the load, replanted from its own seed (or the container's). */
	private void harvest(ServerLevel level, ServerPlayer player, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
			return;
		}
		List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null));
		boolean replanted = false;
		for (ItemStack drop : drops) {
			if (drop.getItem() instanceof BlockItem b && b.getBlock() == crop && !drop.isEmpty()) {
				drop.shrink(1);
				replanted = true;
				break;
			}
		}
		if (!replanted) {
			Container container = HopperBlockEntity.getContainerAt(level, this.home);
			replanted = container != null && !take(container, s -> s.getItem() instanceof BlockItem b && b.getBlock() == crop).isEmpty();
		}
		level.levelEvent(2001, pos, Block.getId(state));
		level.setBlock(pos, replanted ? crop.getStateForAge(0) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		for (ItemStack drop : drops) {
			if (!drop.isEmpty()) {
				this.load.add(drop);
			}
		}
		Mana.drain(player, TEND_COST);
	}

	/** Sows the seed it carries on the bare farmland at {@code pos}. */
	private void sow(ServerLevel level, ServerPlayer player, BlockPos pos) {
		if (this.load.isEmpty() || !(this.load.get(0).getItem() instanceof BlockItem b) || !(b.getBlock() instanceof CropBlock crop)) {
			return;
		}
		BlockState planted = crop.defaultBlockState();
		if (!level.getBlockState(pos).isAir() || !planted.canSurvive(level, pos)) {
			return;
		}
		level.setBlock(pos, planted, Block.UPDATE_ALL);
		level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 0.8F, 1.0F);
		this.load.get(0).shrink(1);
		if (this.load.get(0).isEmpty()) {
			this.load.remove(0);
		}
		Mana.drain(player, TEND_COST);
	}

	// ---- saved --------------------------------------------------------------------------------------------------------

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.keeper = input.read("Keeper", UUIDUtil.CODEC).orElse(null);
		this.home = input.read("Home", BlockPos.CODEC).orElse(this.blockPosition());
		this.entityData.set(KIND, input.getIntOr("Kind", 0));
		this.load.clear();
		this.load.addAll(input.read("Load", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()).stream().filter(s -> !s.isEmpty()).toList());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.storeNullable("Keeper", UUIDUtil.CODEC, this.keeper);
		output.store("Home", BlockPos.CODEC, this.home);
		output.putInt("Kind", this.kind());
		output.store("Load", ItemStack.OPTIONAL_CODEC.listOf(), List.copyOf(this.load));
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 64.0 * 64.0;
	}
}
