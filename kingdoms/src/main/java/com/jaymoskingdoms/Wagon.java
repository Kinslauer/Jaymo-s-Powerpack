package com.jaymoskingdoms;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.14) A horse-drawn wagon: a caravan's storage cart ({@link #CART}, its load of barrels, hay or ore showing what
 * it carries) or a wealthy king's carriage ({@link #CARRIAGE}, a cabin in his colours with him inside). It has no legs
 * of its own: it's hitched to a horse (by UUID) and trails behind it on its shafts, swinging round on turns the way a
 * trailer does. It doesn't block anyone; a player can strike it (a cart broken open spills its load: robbing a
 * caravan, {@link Caravans#robbed}) or right-click it to see what it carries.
 */
public final class Wagon extends Entity {
	public static final int CART = 0, CARRIAGE = 1;
	/** (0.5.26) Its caravan when it's no caravan's: none (a carriage), or a war party's spoils cart ({@link WarParties}). */
	static final int NO_CARAVAN = -1, WAR_CART = -2;
	/** What a cart's load looks like: nothing, stone and ore, food, a bit of everything. */
	public static final int EMPTY = 0, STONE = 1, FOOD = 2, GOODS = 3;
	/** How far behind its horse's middle its own middle trails (a cart, a carriage). */
	static final double CART_HITCH = 2.9, CARRIAGE_HITCH = 3.3;
	/** Blows a cart takes from players before it breaks open (a carriage never does). */
	static final float CART_HEALTH = 24.0F;
	/** Left without its horse this long (ticks), a caravan's cart is done ({@link Caravans}); a carriage just waits. */
	static final int LOST_HORSE_TICKS = 100;

	public static EntityType<Wagon> TYPE;

	private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(Wagon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(Wagon.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> LOAD = SynchedEntityData.defineId(Wagon.class, EntityDataSerializers.INT);

	private @Nullable UUID horse;
	private int faction = -1;
	/** The caravan it belongs to (-1: none, a carriage), and its place in it. */
	private int caravan = -1;
	private int slot;
	private float damage;
	private int horseLost;
	private @Nullable Entity horseCache;

	/** Client side: its wheels' turn (radians) now and last tick. */
	public float wheel, wheelO;

	public Wagon(EntityType<? extends Wagon> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("wagon"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Wagon>of(Wagon::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.9F, 1.6F)
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(key));
	}

	/** A new wagon of {@code kind} for {@code faction}, hitched to {@code horse}, set down behind it. */
	static Wagon hitch(ServerLevel level, int kind, Faction faction, Entity horse, int caravan, int slot) {
		Wagon w = new Wagon(TYPE, level);
		w.entityData.set(KIND, kind);
		w.entityData.set(COLOR, faction.styleColor(level.getServer()).getId());
		w.faction = faction.id();
		w.horse = horse.getUUID();
		w.horseCache = horse;
		w.caravan = caravan;
		w.slot = slot;
		Vec3 back = Vec3.directionFromRotation(0.0F, horse.getYRot()).scale(-w.hitch());
		w.snapTo(horse.getX() + back.x, horse.getY(), horse.getZ() + back.z, horse.getYRot(), 0.0F);
		level.addFreshEntity(w);
		return w;
	}

	public int kind() {
		return this.entityData.get(KIND);
	}

	public int colorId() {
		return this.entityData.get(COLOR);
	}

	public int load() {
		return this.entityData.get(LOAD);
	}

	void setLoad(int look) {
		this.entityData.set(LOAD, look);
	}

	int faction() {
		return this.faction;
	}

	int caravan() {
		return this.caravan;
	}

	int slot() {
		return this.slot;
	}

	@Nullable UUID horseId() {
		return this.horse;
	}

	/** Hitched to another horse (a carriage's new one). */
	void rehitch(Entity horse) {
		this.horse = horse.getUUID();
		this.horseCache = horse;
		this.horseLost = 0;
	}

	/** Its horse, if it's about. */
	@Nullable Entity horse() {
		if (this.horseCache != null && !this.horseCache.isRemoved()) {
			return this.horseCache;
		}
		this.horseCache = this.horse != null && this.level() instanceof ServerLevel level ? level.getEntity(this.horse) : null;
		return this.horseCache;
	}

	int horseLost() {
		return this.horseLost;
	}

	private double hitch() {
		return this.kind() == CARRIAGE ? CARRIAGE_HITCH : CART_HITCH;
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			// its wheels turn with the ground it covers
			this.wheelO = this.wheel;
			double moved = Math.sqrt(Mth.square(this.getX() - this.xo) + Mth.square(this.getZ() - this.zo));
			this.wheel += (float)(moved / 0.55);
			return;
		}
		Entity horse = this.horse();
		if (horse == null || !horse.isAlive()) {
			this.horseLost++;
			if (this.horseLost >= LOST_HORSE_TICKS && this.caravan != NO_CARAVAN) {
				Caravans.cartLost(level, this);
			}
			return;
		}
		this.horseLost = 0;
		this.trail(horse);
	}

	/**
	 * Drawn along behind {@code horse}. Its own tick does this; a caravan does it too for a cart left just outside where
	 * the world is ticking (at the edge of a player's range) while its horse is inside, so it isn't left standing.
	 */
	void trail(Entity horse) {
		// it trails its horse: pulled along its own length toward the hitch, so it swings round behind on a turn
		Vec3 here = this.position();
		Vec3 to = horse.position();
		Vec3 flat = new Vec3(to.x - here.x, 0.0, to.z - here.z);
		double len = flat.length();
		if (len > 8.0) {
			// (left behind: set down behind its horse)
			Vec3 back = Vec3.directionFromRotation(0.0F, horse.getYRot()).scale(-this.hitch());
			this.snapTo(to.x + back.x, to.y, to.z + back.z, horse.getYRot(), 0.0F);
			return;
		}
		if (len > 1.0E-3) {
			Vec3 dir = flat.scale(1.0 / len);
			Vec3 at = new Vec3(to.x - dir.x * this.hitch(), Mth.lerp(0.35, here.y, to.y), to.z - dir.z * this.hitch());
			float yaw = (float)(Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
			this.setPos(at.x, at.y, at.z);
			this.setYRot(yaw);
		}
		for (Entity rider : this.getPassengers()) {
			rider.setYRot(this.getYRot());
			if (rider instanceof LivingEntity living) {
				living.setYBodyRot(this.getYRot());
			}
		}
	}

	// ---- its passenger (the king in his carriage) ---------------------------------------------------------------------

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.kind() == CARRIAGE && this.getPassengers().isEmpty();
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		return new Vec3(0.0, 0.4, -0.45).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
	}

	@Override
	public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
		Vec3 side = new Vec3(1.6, 0.0, -0.4).yRot(-this.getYRot() * Mth.DEG_TO_RAD);
		return this.position().add(side);
	}

	// ---- players ------------------------------------------------------------------------------------------------------

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved() || this.kind() != CART || !(source.getEntity() instanceof ServerPlayer player) || player.isSpectator()) {
			return false;
		}
		this.damage += amount;
		level.playSound(null, this.blockPosition(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.NEUTRAL, 0.6F, 1.2F);
		level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.0, this.getZ(), 6, 0.5, 0.4, 0.5, 0.1);
		Caravans.struck(level, this, player);
		if (this.damage >= CART_HEALTH) {
			Caravans.robbed(level, this, player);
		}
		return true;
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer sp && this.level() instanceof ServerLevel level) {
			Component what = this.kind() == CART ? Caravans.describe(level, this)
				: Component.translatable("message.jaymoskingdoms.carriage", Caravans.nameOf(level.getServer(), this.faction));
			sp.sendSystemMessage(what.copy().withStyle(ChatFormatting.GRAY));
		}
		return InteractionResult.SUCCESS;
	}

	// ---- saving ------------------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(KIND, CART);
		entityData.define(COLOR, -1);
		entityData.define(LOAD, EMPTY);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.entityData.set(KIND, input.getIntOr("kind", CART));
		this.entityData.set(COLOR, input.getIntOr("color", -1));
		this.entityData.set(LOAD, input.getIntOr("load", EMPTY));
		this.horse = input.read("horse", UUIDUtil.CODEC).orElse(null);
		this.faction = input.getIntOr("faction", -1);
		this.caravan = input.getIntOr("caravan", -1);
		this.slot = input.getIntOr("slot", 0);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putInt("kind", this.kind());
		output.putInt("color", this.colorId());
		output.putInt("load", this.load());
		if (this.horse != null) {
			output.store("horse", UUIDUtil.CODEC, this.horse);
		}
		output.putInt("faction", this.faction);
		output.putInt("caravan", this.caravan);
		output.putInt("slot", this.slot);
	}
}
