package com.jaymoskingdoms;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.477) Andy (2026-11-04), after Special Golems (ideas only, none of its files): "a magically attuned kingdom using golems
 * like these for everyday things". A town golem is our own rune golem (a stone body with a carved core in its chest, drawn
 * by {@code TownGolemModel}), in one of five kinds, each with a day's work:
 * <ul>
 * <li>{@link Kind#CLAY} the <b>porter</b>: picks up what lies about the town and puts it away in the nearest chest or barrel.</li>
 * <li>{@link Kind#FROST} the <b>frost tender</b>: puts out fires and burning folk, and pelts monsters with snow.</li>
 * <li>{@link Kind#GILT} the <b>lamp warden</b>: glows through the night and burns monsters (undead twice over) with a beam.</li>
 * <li>{@link Kind#VERDANT} the <b>mender</b>: knits the hurt of the town's folk, and once in a while keeps one from dying
 * ({@link Golemworks#sparedByMender}).</li>
 * <li>{@link Kind#LAPIS} the <b>scribe</b>: stands still by the seat, slows and weakens monsters, and engraves: an enchanted
 * item given to it gains a level of one of its enchantments, past the table's limit, and can never be enchanted again.</li>
 * </ul>
 * An attuned realm keeps its own ({@link Golemworks}); anyone who knows Golemancy can make one on the infusion altar and set
 * it down where it should work. Its maker can sneak and use it with an empty hand to fold it back into its item.
 */
public class TownGolem extends AbstractGolem {
	public static EntityType<TownGolem> TYPE;

	public enum Kind {
		CLAY("clay", 40.0, 0.28, 0.85F, 0xE9D6B0),
		FROST("frost", 30.0, 0.26, 0.72F, 0x8FD0F0),
		GILT("gilt", 160.0, 0.18, 1.15F, 0xFFF07A),
		VERDANT("verdant", 90.0, 0.24, 0.95F, 0x9DFFB0),
		LAPIS("lapis", 120.0, 0.0, 1.0F, 0x5A7FE0);

		public final String id;
		final double health, speed;
		final float scale;
		final int colour;
		public Item item;

		Kind(String id, double health, double speed, float scale, int colour) {
			this.id = id;
			this.health = health;
			this.speed = speed;
			this.scale = scale;
			this.colour = colour;
		}

		public static Kind byId(int i) {
			Kind[] all = values();
			return all[Math.floorMod(i, all.length)];
		}

		/** What mends it, a handful at a time. */
		Item mend() {
			return switch (this) {
				case CLAY -> Items.CLAY_BALL;
				case FROST -> Items.SNOWBALL;
				case GILT -> Items.GOLD_INGOT;
				case VERDANT -> Items.EMERALD;
				case LAPIS -> Items.LAPIS_LAZULI;
			};
		}
	}

	// ---- tuning ----
	/** How far from its home it works: a realm's golem round its seat, one set down round where it was set. */
	static final int REALM_RANGE = 40, SET_RANGE = 16;
	/** How often it looks for work (ticks). */
	static final int LOOK_EVERY = 20;
	/** Porter: a thing must have lain this long first (ticks), with no player this near; how near it reaches; a chest this near home. */
	static final int LYING_TICKS = 1200;
	static final double PLAYER_CLEAR = 8.0, REACH = 1.8, CHEST_RANGE = 20.0;
	/** Frost tender: fires this near (blocks), snowballs at monsters this near, one every this many ticks. */
	static final int FIRE_SEARCH = 10;
	static final double SNOW_RANGE = 10.0;
	static final int SNOW_EVERY = 25;
	/** Lamp warden: its beam's reach, harm (doubled on the undead), and how often. */
	static final double BEAM_RANGE = 16.0;
	static final float BEAM_HARM = 6.0F;
	static final int BEAM_EVERY = 30;
	/** Mender: hurt folk this near get Regeneration II this long, each at most this often (ticks). */
	static final double MEND_RANGE = 10.0;
	static final int MEND_TICKS = 100, MEND_EVERY = 400;
	/** Scribe: its slowing beam's reach and how often; engravings a day (a realm's) or before it needs lapis blocks (one set down). */
	static final double SCRIBE_RANGE = 12.0;
	static final int SCRIBE_EVERY = 20, REALM_INK = 3, SET_INK = 6;
	/** Scribe: what an engraving costs: lapis from the hand that doesn't hold the item, and emeralds at a realm's scribe. */
	static final int ENGRAVE_LAPIS = 8, ENGRAVE_EMERALDS = 6;
	/** The custom-data key that marks an engraved item. */
	public static final String ENGRAVED = "jaymoskingdoms_engraved";

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(TownGolem.class, EntityDataSerializers.INT);
	/** Synced: a lamp warden's light is up (night) / a scribe still has ink. */
	private static final EntityDataAccessor<Boolean> LIT = SynchedEntityData.defineId(TownGolem.class, EntityDataSerializers.BOOLEAN);

	/** Its realm (-1: one set down by a player). */
	private int faction = -1;
	private @Nullable UUID owner;
	private int ink = SET_INK;
	private long inkDay = -1;
	private int cooldown;
	/** Porter: what it carries, and what it's after. */
	private ItemStack carried = ItemStack.EMPTY;
	private @Nullable ItemEntity fetching;
	private @Nullable BlockPos goal;

	public TownGolem(EntityType<? extends TownGolem> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("town_golem"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<TownGolem>of(TownGolem::new, MobCategory.MISC)
			.sized(1.0F, 2.2F).clientTrackingRange(10).build(key));
		FabricDefaultAttributeRegistry.register(TYPE, createGolemAttributes());
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(kind.id + "_golem"));
			kind.item = Registry.register(BuiltInRegistries.ITEM, itemKey, new Builder(new Item.Properties().setId(itemKey).stacksTo(1)
				.rarity(Rarity.UNCOMMON), kind));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> {
			for (Kind kind : Kind.values()) {
				output.accept(kind.item);
			}
		});
	}

	public static AttributeSupplier.Builder createGolemAttributes() {
		return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60.0).add(Attributes.MOVEMENT_SPEED, 0.25)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.FOLLOW_RANGE, 24.0)
			.add(Attributes.ARMOR, 4.0);
	}

	/** A new golem of {@code kind}, working round {@code home}: for realm {@code faction} (-1 none), set down by {@code owner}. */
	static @Nullable TownGolem make(ServerLevel level, Kind kind, BlockPos home, int faction, @Nullable UUID owner) {
		TownGolem golem = TYPE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (golem == null) {
			return null;
		}
		golem.setKind(kind);
		golem.faction = faction;
		golem.owner = owner;
		golem.ink = faction >= 0 ? REALM_INK : SET_INK;
		golem.setHomeTo(home, faction >= 0 ? REALM_RANGE : SET_RANGE);
		golem.setPersistenceRequired();
		return golem;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		super.defineSynchedData(entityData);
		entityData.define(KIND, 0);
		entityData.define(LIT, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8) {
			@Override
			public boolean canUse() {
				return TownGolem.this.kind() != Kind.LAPIS && TownGolem.this.goal == null && TownGolem.this.fetching == null && super.canUse();
			}
		});
		this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
	}

	public Kind kind() {
		return Kind.byId(this.entityData.get(KIND));
	}

	void setKind(Kind kind) {
		this.entityData.set(KIND, kind.ordinal());
		this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.health);
		this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed);
		this.getAttribute(Attributes.SCALE).setBaseValue(kind.scale);
		this.setHealth(this.getMaxHealth());
		this.refreshDimensions();
	}

	public boolean lit() {
		return this.entityData.get(LIT);
	}

	public int faction() {
		return this.faction;
	}

	/** The porter shows its basket while it carries something. */
	public boolean carrying() {
		return !this.carried.isEmpty();
	}

	public @Nullable UUID owner() {
		return this.owner;
	}

	/** The scribe stands where it's put (no speed, no stroll, and it can't be shoved). */
	@Override
	public boolean isPushable() {
		return this.kind() != Kind.LAPIS && super.isPushable();
	}

	// ---- work ----------------------------------------------------------------------------------------------------

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.cooldown > 0) {
			this.cooldown--;
		}
		if (this.kind() == Kind.GILT) {
			this.entityData.set(LIT, level.isDarkOutside());
		}
		switch (this.kind()) {
			case CLAY -> this.porter(level);
			case FROST -> this.frost(level);
			case GILT -> this.lamp(level);
			case VERDANT -> this.mender(level);
			case LAPIS -> this.scribe(level);
		}
	}

	private BlockPos home() {
		return this.hasHome() ? this.getHomePosition() : this.blockPosition();
	}

	private double homeRange() {
		return this.hasHome() ? this.getHomeRadius() : SET_RANGE;
	}

	private void porter(ServerLevel level) {
		if (!this.carried.isEmpty()) {
			if (this.goal == null || this.tickCount % 100 == 0) {
				this.goal = this.nearestChest(level);
				if (this.goal == null) {
					// nowhere to put it: set it down at home
					if (this.tickCount % 100 == 0) {
						this.dropCarried(level);
					}
					return;
				}
			}
			if (this.distanceToSqr(Vec3.atCenterOf(this.goal)) <= (REACH + 1.0) * (REACH + 1.0)) {
				Container box = HopperBlockEntity.getContainerAt(level, this.goal);
				if (box != null) {
					ItemStack left = HopperBlockEntity.addItem(null, box, this.carried.copy(), Direction.UP);
					this.carried = left;
					level.playSound(null, this.goal, SoundEvents.BUNDLE_INSERT, SoundSource.NEUTRAL, 0.8F, 0.8F);
				}
				if (!this.carried.isEmpty()) {
					this.dropCarried(level);
				}
				this.goal = null;
				this.getNavigation().stop();
			} else if (this.tickCount % 10 == 0) {
				this.getNavigation().moveTo(this.goal.getX() + 0.5, this.goal.getY(), this.goal.getZ() + 0.5, 1.0);
			}
			return;
		}
		if (this.fetching != null && (!this.fetching.isAlive() || this.fetching.getItem().isEmpty())) {
			this.fetching = null;
		}
		if (this.fetching == null && this.tickCount % LOOK_EVERY == 0) {
			BlockPos home = this.home();
			double range = this.homeRange();
			this.fetching = level.getEntitiesOfClass(ItemEntity.class, new AABB(home).inflate(range), e -> e.isAlive() && !e.hasPickUpDelay()
					&& e.getAge() >= LYING_TICKS && level.getNearestPlayer(e, PLAYER_CLEAR) == null)
				.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
		}
		if (this.fetching != null) {
			if (this.distanceToSqr(this.fetching) <= REACH * REACH) {
				this.carried = this.fetching.getItem().copy();
				this.fetching.discard();
				this.fetching = null;
				this.playSound(SoundEvents.ITEM_PICKUP, 0.6F, 0.7F);
			} else if (this.tickCount % 10 == 0) {
				this.getNavigation().moveTo(this.fetching, 1.0);
			}
		}
	}

	private @Nullable BlockPos nearestChest(ServerLevel level) {
		BlockPos home = this.home();
		int r = (int)CHEST_RANGE;
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(home.offset(-r, -6, -r), home.offset(r, 6, r))) {
			var state = level.getBlockState(p);
			if (!state.is(Blocks.CHEST) && !state.is(Blocks.BARREL)) {
				continue;
			}
			double d = this.distanceToSqr(Vec3.atCenterOf(p));
			if (d < bestD) {
				Container box = HopperBlockEntity.getContainerAt(level, p);
				if (box != null && hasRoom(box, this.carried)) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	private static boolean hasRoom(Container box, ItemStack stack) {
		for (int i = 0; i < box.getContainerSize(); i++) {
			ItemStack in = box.getItem(i);
			if (in.isEmpty() || ItemStack.isSameItemSameComponents(in, stack) && in.getCount() < in.getMaxStackSize()) {
				return true;
			}
		}
		return false;
	}

	private void dropCarried(ServerLevel level) {
		if (!this.carried.isEmpty()) {
			ItemEntity drop = this.spawnAtLocation(level, this.carried);
			if (drop != null) {
				drop.setPickUpDelay(40);
			}
			this.carried = ItemStack.EMPTY;
		}
	}

	private void frost(ServerLevel level) {
		if (this.tickCount % LOOK_EVERY == 0) {
			// burning folk nearby: put them out
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(8.0),
				e -> e.isOnFire() && e != this && !(e instanceof Enemy))) {
				e.clearFire();
				this.puff(level, e.position().add(0, e.getBbHeight() * 0.5, 0));
			}
			if (this.goal == null) {
				this.goal = this.nearestFire(level);
			}
		}
		if (this.goal != null) {
			if (!level.getBlockState(this.goal).is(BlockTags.FIRE)) {
				this.goal = null;
			} else if (this.distanceToSqr(Vec3.atCenterOf(this.goal)) <= 9.0) {
				level.removeBlock(this.goal, false);
				this.puff(level, Vec3.atCenterOf(this.goal));
				level.playSound(null, this.goal, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.2F);
				this.goal = null;
			} else if (this.tickCount % 10 == 0) {
				this.getNavigation().moveTo(this.goal.getX() + 0.5, this.goal.getY(), this.goal.getZ() + 0.5, 1.1);
			}
			return;
		}
		Mob foe = this.nearestFoe(level, SNOW_RANGE);
		if (foe != null && this.cooldown == 0) {
			this.cooldown = SNOW_EVERY;
			this.getLookControl().setLookAt(foe);
			Snowball ball = new Snowball(level, this, new ItemStack(Items.SNOWBALL));
			double dx = foe.getX() - this.getX(), dz = foe.getZ() - this.getZ();
			double dy = foe.getEyeY() - 1.1 - ball.getY();
			ball.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.2, dz, 1.6F, 8.0F);
			level.addFreshEntity(ball);
			this.playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.0F, 0.4F / (this.random.nextFloat() * 0.4F + 0.8F));
		}
	}

	private @Nullable BlockPos nearestFire(ServerLevel level) {
		BlockPos from = this.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(from.offset(-FIRE_SEARCH, -4, -FIRE_SEARCH), from.offset(FIRE_SEARCH, 4, FIRE_SEARCH))) {
			if (level.getBlockState(p).is(BlockTags.FIRE) && this.isWithinHome(p)) {
				double d = p.distSqr(from);
				if (d < bestD) {
					bestD = d;
					best = p.immutable();
				}
			}
		}
		return best;
	}

	private void puff(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 4, 0.3, 0.3, 0.3, 0.01);
	}

	private void lamp(ServerLevel level) {
		if (this.lit() && this.tickCount % 8 == 0) {
			level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + this.getBbHeight() * 0.62, this.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
		}
		Mob foe = this.nearestFoe(level, BEAM_RANGE);
		if (foe == null || this.cooldown > 0) {
			return;
		}
		this.cooldown = BEAM_EVERY;
		this.getLookControl().setLookAt(foe);
		boolean undead = foe.is(EntityTypeTags.UNDEAD);
		this.beam(level, foe, new DustParticleOptions(0xFFE36A, 1.2F));
		foe.hurtServer(level, this.damageSources().indirectMagic(this, this), undead ? BEAM_HARM * 2.0F : BEAM_HARM);
		if (undead) {
			foe.igniteForSeconds(3.0F);
		}
		level.playSound(null, this.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 0.6F, 1.6F);
	}

	private void mender(ServerLevel level) {
		if (this.tickCount % 40 != 0) {
			return;
		}
		long now = level.getGameTime();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(MEND_RANGE),
			e -> e.isAlive() && e.getHealth() < e.getMaxHealth() && this.watches(e))) {
			Long last = e.getAttached(Golemworks.MENDED);
			if (last != null && now - last < MEND_EVERY) {
				continue;
			}
			e.setAttached(Golemworks.MENDED, now);
			e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MEND_TICKS, 1), this);
			this.beam(level, e, new DustParticleOptions(0x7FF09A, 1.0F));
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.3, 0.4, 0.3, 0.0);
			return;
		}
	}

	private void scribe(ServerLevel level) {
		long day = Deeds.today(level.getServer());
		if (this.faction >= 0 && day != this.inkDay) {
			this.inkDay = day;
			this.ink = REALM_INK;
		}
		this.entityData.set(LIT, this.ink > 0);
		if (this.cooldown > 0) {
			return;
		}
		Mob foe = this.nearestFoe(level, SCRIBE_RANGE);
		if (foe == null) {
			return;
		}
		this.cooldown = SCRIBE_EVERY;
		this.getLookControl().setLookAt(foe);
		this.beam(level, foe, new DustParticleOptions(0x5A7FE0, 1.0F));
		// a scribe with no ink left is weaker
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, this.ink > 0 ? 1 : 0), this);
		if (this.ink > 0) {
			foe.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
		}
	}

	private @Nullable Mob nearestFoe(ServerLevel level, double range) {
		List<Mob> foes = level.getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(range),
			m -> m.isAlive() && m instanceof Enemy && !m.isInvisible() && this.hasLineOfSight(m) && !(m instanceof TownGolem));
		return foes.stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
	}

	/** A line of motes from its core to {@code to}. */
	private void beam(ServerLevel level, Entity to, ParticleOptions mote) {
		Vec3 from = this.position().add(0, this.getBbHeight() * 0.6, 0);
		Vec3 end = to.position().add(0, to.getBbHeight() * 0.5, 0);
		Vec3 step = end.subtract(from);
		int n = Math.max(4, (int)(step.length() * 3));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.add(step.scale(i / (double)n));
			level.sendParticles(mote, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/** Whom it looks after: its maker and their party and pets, the town's folk, other golems, and players in good standing with its realm. */
	boolean watches(LivingEntity e) {
		if (e == this || e instanceof Enemy) {
			return false;
		}
		if (e instanceof TownGolem || e instanceof net.minecraft.world.entity.animal.golem.IronGolem || Deeds.friendly(e)) {
			return true;
		}
		if (e instanceof OwnableEntity pet && pet.getOwnerReference() != null && this.owner != null
			&& this.owner.equals(pet.getOwnerReference().getUUID())) {
			return true;
		}
		if (e instanceof ServerPlayer player) {
			if (this.owner != null) {
				ServerPlayer maker = player.level().getServer().getPlayerList().getPlayer(this.owner);
				return player.getUUID().equals(this.owner) || maker != null && Parties.allied(maker, player);
			}
			return this.faction >= 0 && Reputation.get(player, this.faction) >= 0;
		}
		return false;
	}

	// ---- hands-on ------------------------------------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		// its maker folds it back up
		if (stack.isEmpty() && player.isShiftKeyDown() && this.owner != null && this.owner.equals(player.getUUID())) {
			this.dropCarried(level);
			ItemStack item = new ItemStack(this.kind().item);
			if (!player.getInventory().add(item)) {
				this.spawnAtLocation(level, item);
			}
			level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 0.8F);
			this.discard();
			return InteractionResult.SUCCESS;
		}
		if (stack.is(this.kind().mend()) && this.getHealth() < this.getMaxHealth()) {
			this.heal((float)(this.kind().health / 8.0));
			stack.consume(1, player);
			this.playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.2F);
			return InteractionResult.SUCCESS;
		}
		if (this.kind() == Kind.LAPIS) {
			if (stack.is(Items.LAPIS_BLOCK) && this.faction < 0) {
				this.ink++;
				stack.consume(1, player);
				this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.0F);
				return InteractionResult.SUCCESS;
			}
			if (stack.has(DataComponents.ENCHANTMENTS) && !stack.getEnchantments().isEmpty() && player instanceof ServerPlayer sp) {
				this.engrave(level, sp, hand, stack);
				return InteractionResult.SUCCESS;
			}
		}
		return super.mobInteract(player, hand);
	}

	/** Engraves {@code stack}: one of its enchantments a level up, past the table's limit, and never enchanted again. */
	private void engrave(ServerLevel level, ServerPlayer player, InteractionHand hand, ItemStack stack) {
		if (engraved(stack)) {
			player.sendSystemMessage(Component.translatable("golem.jaymoskingdoms.scribe.already"), true);
			return;
		}
		if (this.ink <= 0) {
			player.sendSystemMessage(Component.translatable(this.faction >= 0 ? "golem.jaymoskingdoms.scribe.dry" : "golem.jaymoskingdoms.scribe.dry_set"), true);
			return;
		}
		ItemStack lapis = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
		int emeralds = this.faction >= 0 ? ENGRAVE_EMERALDS : 0;
		if (!player.isCreative() && (!lapis.is(Items.LAPIS_LAZULI) || lapis.getCount() < ENGRAVE_LAPIS
			|| emeralds > 0 && Purse.count(player, Items.EMERALD) < emeralds)) {
			player.sendSystemMessage(Component.translatable("golem.jaymoskingdoms.scribe.price", ENGRAVE_LAPIS, emeralds), true);
			return;
		}
		ItemEnchantments.Mutable next = new ItemEnchantments.Mutable(stack.getEnchantments());
		List<Holder<Enchantment>> on = List.copyOf(next.keySet());
		Holder<Enchantment> pick = on.get(this.random.nextInt(on.size()));
		next.set(pick, next.getLevel(pick) + 1);
		stack.set(DataComponents.ENCHANTMENTS, next.toImmutable());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(ENGRAVED, true));
		ItemLore lore = stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		stack.set(DataComponents.LORE, lore.withLineAdded(Component.translatable("golem.jaymoskingdoms.scribe.lore")
			.withStyle(net.minecraft.ChatFormatting.DARK_AQUA)));
		if (!player.isCreative()) {
			lapis.shrink(ENGRAVE_LAPIS);
			if (emeralds > 0) {
				Purse.take(player, Items.EMERALD, emeralds);
			}
		}
		this.ink--;
		this.entityData.set(LIT, this.ink > 0);
		level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.5, 0.6, 0.5, 0.6);
		level.playSound(null, this.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.NEUTRAL, 1.0F, 0.8F);
		player.sendSystemMessage(Component.translatable("golem.jaymoskingdoms.scribe.done", Enchantment.getFullname(pick, next.getLevel(pick))), true);
	}

	/** {@code stack} has been engraved by a scribe (and can't be enchanted again). */
	public static boolean engraved(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(ENGRAVED, false);
	}

	// ---- body ------------------------------------------------------------------------------------------------------

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		this.dropCarried(level);
		Item bits = switch (this.kind()) {
			case CLAY -> Items.CLAY_BALL;
			case FROST -> Items.SNOWBALL;
			case GILT -> Items.GOLD_NUGGET;
			case VERDANT -> Items.EMERALD;
			case LAPIS -> Items.LAPIS_LAZULI;
		};
		this.spawnAtLocation(level, new ItemStack(bits, 2 + this.random.nextInt(4)));
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return null;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return this.kind() == Kind.FROST ? SoundEvents.SNOW_GOLEM_HURT : SoundEvents.STONE_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return this.kind() == Kind.FROST ? SoundEvents.SNOW_GOLEM_DEATH : SoundEvents.STONE_BREAK;
	}

	@Override
	protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		this.playSound(this.kind() == Kind.FROST ? SoundEvents.SNOW_STEP : SoundEvents.STONE_STEP, 0.8F, 0.7F);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("kind", this.kind().ordinal());
		output.putInt("faction", this.faction);
		output.putInt("ink", this.ink);
		output.putLong("ink_day", this.inkDay);
		if (this.owner != null) {
			output.putString("owner", this.owner.toString());
		}
		if (this.hasHome()) {
			output.putLong("home", this.getHomePosition().asLong());
			output.putInt("home_range", this.getHomeRadius());
		}
		if (!this.carried.isEmpty()) {
			output.store("carried", ItemStack.CODEC, this.carried);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(KIND, input.getIntOr("kind", 0));
		this.faction = input.getIntOr("faction", -1);
		this.ink = input.getIntOr("ink", SET_INK);
		this.inkDay = input.getLongOr("ink_day", -1L);
		this.owner = input.getString("owner").map(UUID::fromString).orElse(null);
		Optional<Long> home = input.getLong("home");
		home.ifPresent(h -> this.setHomeTo(BlockPos.of(h), input.getIntOr("home_range", SET_RANGE)));
		this.carried = input.read("carried", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	/** The golem's heart, made on the altar: set it down on a block and it stands up and gets to work there. */
	static final class Builder extends Item {
		final Kind kind;

		Builder(Item.Properties properties, Kind kind) {
			super(properties);
			this.kind = kind;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			Player player = context.getPlayer();
			TownGolem golem = make(level, this.kind, at, -1, player != null ? player.getUUID() : null);
			if (golem == null) {
				return InteractionResult.FAIL;
			}
			float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
			golem.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0F);
			golem.yHeadRot = yaw;
			golem.yBodyRot = yaw;
			if (!level.noCollision(golem)) {
				return InteractionResult.FAIL;
			}
			level.addFreshEntity(golem);
			level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
			level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.5);
			context.getItemInHand().consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}
}
