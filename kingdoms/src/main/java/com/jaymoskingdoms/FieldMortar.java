package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Bombardment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A field mortar: a short iron tube on a four-legged stand that lobs a shell high over a wall (drawn as the Mortar of
 * cleannrooster's Frontier Armaments, MIT). Siege gear, beside the cannons.
 * <ul>
 * <li><b>A realm's war party</b> rich enough to keep guns brings one beside its field gun ({@link SiegeTrain}): two
 * soldiers carry it behind the march, set it down {@link SiegeTrain#MORTAR_STANDOFF} short of the enemy's seat, and the
 * first of them works it. It turns to its mark and drops a shell on it from {@link #NEAREST} to {@link #FURTHEST}
 * blocks; with no foe in reach it shells the seat. It carries {@link #SHELLS} shells and no more.</li>
 * <li><b>A player's</b> ({@link #ITEM}, set down on the ground, saved): use it with a {@link #SHELL} to load it, then
 * use it again to fire, the way you face; look up to drop the shell nearer ({@link #MIN_ELEVATION} to
 * {@link #MAX_ELEVATION} degrees: level or lower throws furthest). Every shot spends a shell.</li>
 * </ul>
 * The shell is a {@link Cannonball} thrown at {@link #SPEED}: {@link #DAMAGE} to what it lands on, a burst round it and a
 * pit ({@link #PIT}). Anyone can break a mortar with {@link #HEALTH} worth of blows and carry it off.
 */
public final class FieldMortar extends Entity {
	/** A crew's shells land from this far to this far (blocks). */
	public static final double NEAREST = 10.0, FURTHEST = 44.0;
	/** Between a crew's shots (ticks), plus up to {@link #JITTER} more; a player's is ready again after {@link #SETTLE}. */
	static final int RELOAD = 140, JITTER = 60, SETTLE = 30;
	/** How far it's turned per tick (degrees), and how close to its mark it must be to fire. */
	static final float TRAVERSE = 5.0F, ON_MARK = 6.0F;
	/** How wide of its mark a shell may fall (blocks). */
	static final double SPREAD = 2.5, SEAT_SPREAD = 6.0;
	/** Its operator works it from this near (blocks). */
	static final double WORK_REACH = 5.0;
	/** How often it looks for a mark (ticks). */
	static final int MARK_CHECK = 20;
	/** Where the shell leaves the tube: this high over its foot (blocks). */
	static final double MUZZLE_HEIGHT = 1.1;
	/** How fast the shell leaves (blocks per tick; it falls as a cannonball does), and how long it may fly (ticks). */
	static final double SPEED = 1.2;
	static final int SHELL_LIFE = 220;
	/** The shell's blow on what it lands on, and the pit it leaves. */
	static final float DAMAGE = 16.0F;
	static final Bombardment.Crater PIT = new Bombardment.Crater(2.2, 2, 2, 6.0F, Bombardment.Strip.STRUCTURES, 12, false);
	/** The shells a war party's mortar carries. */
	static final int SHELLS = 12;
	/** How steeply the tube can be laid (degrees up from level). */
	static final float MIN_ELEVATION = 45.0F, MAX_ELEVATION = 85.0F;
	/** Blows it takes from players before it comes apart. */
	static final float HEALTH = 12.0F;
	/** The entity event that kicks its tube on the client; the kick lasts this long (ticks). */
	static final byte FIRED = 60;
	public static final int KICK_TICKS = 8;

	private static final EntityDataAccessor<Float> ELEVATION = SynchedEntityData.defineId(FieldMortar.class, EntityDataSerializers.FLOAT);

	public static EntityType<FieldMortar> TYPE;
	public static Item ITEM, SHELL;

	final Haul haul = new Haul();
	/** Set down by a player: saved, and worked by hand. */
	private boolean placed;
	private boolean loaded;
	private int shells = SHELLS;
	private long next;
	private float damage;
	private @Nullable LivingEntity mark;
	/** The enemy seat a crew's is set down before, to shell when no foe is in reach; null on the march. */
	@Nullable BlockPos seat;

	/** Client side: ticks since it last fired. */
	public int sinceFired = KICK_TICKS;

	public FieldMortar(EntityType<? extends FieldMortar> type, Level level) {
		super(type, level);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("field_mortar"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<FieldMortar>of(FieldMortar::new, MobCategory.MISC)
			.noLootTable()
			.sized(1.2F, 1.0F)
			.clientTrackingRange(10)
			.updateInterval(2)
			.build(key));
		ResourceKey<Item> item = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("field_mortar"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, item, new Kit(new Item.Properties().setId(item).stacksTo(1)));
		ResourceKey<Item> shell = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("mortar_shell"));
		SHELL = Registry.register(BuiltInRegistries.ITEM, shell, new Item(new Item.Properties().setId(shell).stacksTo(16)));
	}

	/** The tube's lay, degrees up from level. */
	public float elevation() {
		return this.entityData.get(ELEVATION);
	}

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			if (this.sinceFired < KICK_TICKS) {
				this.sinceFired++;
			}
			return;
		}
		if (this.placed) {
			return;
		}
		if (this.haul.to != null) {
			this.haul.step(level, this);
			return;
		}
		PathfinderMob operator = this.haul.operator(level);
		if (operator == null || this.shells <= 0 || operator.distanceToSqr(this) > WORK_REACH * WORK_REACH) {
			return;
		}
		long now = level.getGameTime();
		if ((now + this.getId()) % MARK_CHECK == 0) {
			this.mark = SiegeTrain.mark(level, operator, this.position(), NEAREST, FURTHEST);
		}
		LivingEntity mark = this.mark != null && this.mark.isAlive() ? this.mark : null;
		if (mark == null && this.seat == null) {
			return;
		}
		Vec3 at = mark != null ? mark.position() : Vec3.atCenterOf(this.seat);
		double dx = at.x - this.getX(), dz = at.z - this.getZ();
		if (mark == null && dx * dx + dz * dz < NEAREST * NEAREST) {
			return;
		}
		float want = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
		this.setYRot(this.getYRot() + Mth.clamp(Mth.wrapDegrees(want - this.getYRot()), -TRAVERSE, TRAVERSE));
		if (now >= this.next && Math.abs(Mth.wrapDegrees(want - this.getYRot())) < ON_MARK) {
			this.next = now + RELOAD + level.getRandom().nextInt(JITTER);
			this.shells--;
			double spread = mark != null ? SPREAD : SEAT_SPREAD;
			Vec3 fall = at.add(level.getRandom().triangle(0.0, spread), 0.0, level.getRandom().triangle(0.0, spread));
			Vec3 muzzle = this.position().add(0.0, MUZZLE_HEIGHT, 0.0);
			double fx = fall.x - muzzle.x, fz = fall.z - muzzle.z;
			double flat = Math.max(1.0, Math.sqrt(fx * fx + fz * fz));
			double angle = lob(flat, fall.y - muzzle.y);
			this.fire(level, operator, new Vec3(fx / flat * Math.cos(angle), Math.sin(angle), fz / flat * Math.cos(angle)), (float) (angle * Mth.RAD_TO_DEG));
		}
	}

	/** The angle (radians) that lobs a shell {@code flat} blocks off and {@code up} blocks higher: the high arc, or 45 degrees if out of reach. */
	static double lob(double flat, double up) {
		double v = SPEED, g = Cannonball.GRAVITY;
		double root = v * v * v * v - g * (g * flat * flat + 2.0 * up * v * v);
		return root < 0.0 ? Math.PI / 4.0 : Math.atan((v * v + Math.sqrt(root)) / (g * flat));
	}

	/** A shell out of the tube along {@code dir} (a unit vector), the tube laid at {@code elevation} degrees. */
	private void fire(ServerLevel level, LivingEntity shooter, Vec3 dir, float elevation) {
		this.entityData.set(ELEVATION, elevation);
		Vec3 muzzle = this.position().add(0.0, MUZZLE_HEIGHT, 0.0).add(dir.scale(0.6));
		Cannonball.shell(level, shooter, muzzle, dir.scale(SPEED), DAMAGE, PIT, SHELL_LIFE);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.15, 0.15, 0.15, 0.03);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 5, 0.08, 0.08, 0.08, 0.02);
		level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 1.6F, 1.5F);
		Deafened.blast(level, muzzle, Deafened.MUZZLE);
		level.broadcastEntityEvent(this, FIRED);
	}

	/** A player at it: a shell in the hand loads it; loaded, it fires the way he faces. A crew's own is left to them. */
	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (!(this.level() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		if (player.isSpectator() || !this.haul.crew(level).isEmpty()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		long now = level.getGameTime();
		if (!this.loaded) {
			if (!held.is(SHELL)) {
				return InteractionResult.PASS;
			}
			held.consume(1, player);
			this.loaded = true;
			level.playSound(null, this.getX(), this.getY() + 1.0, this.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.NEUTRAL, 1.0F, 0.5F);
			return InteractionResult.SUCCESS;
		}
		if (now < this.next) {
			return InteractionResult.PASS;
		}
		this.next = now + SETTLE;
		this.loaded = false;
		this.setYRot(player.getYRot());
		float elevation = Mth.clamp(-player.getXRot(), MIN_ELEVATION, MAX_ELEVATION);
		Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
		double cos = Math.cos(elevation * Mth.DEG_TO_RAD), sin = Math.sin(elevation * Mth.DEG_TO_RAD);
		this.fire(level, player, new Vec3(forward.x * cos, sin, forward.z * cos), elevation);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == FIRED) {
			this.sinceFired = 0;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(ITEM);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (this.isRemoved() || !(source.getEntity() instanceof Player player) || player.isSpectator()) {
			return false;
		}
		this.damage += player.isCreative() ? HEALTH : amount;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_HURT, SoundSource.NEUTRAL, 0.8F, 1.4F);
		if (this.damage >= HEALTH) {
			// it comes apart to be carried off: the mortar, and the shell in it
			if (!player.isCreative()) {
				level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.3, this.getZ(), new ItemStack(ITEM)));
				if (this.loaded) {
					level.addFreshEntity(new ItemEntity(level, this.getX(), this.getY() + 0.3, this.getZ(), new ItemStack(SHELL)));
				}
			}
			this.discard();
		}
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return this.placed;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder entityData) {
		entityData.define(ELEVATION, 65.0F);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		this.placed = input.getBooleanOr("placed", false);
		this.loaded = input.getBooleanOr("loaded", false);
		this.damage = input.getFloatOr("damage", 0.0F);
		this.entityData.set(ELEVATION, input.getFloatOr("elevation", 65.0F));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		output.putBoolean("placed", this.placed);
		output.putBoolean("loaded", this.loaded);
		output.putFloat("damage", this.damage);
		output.putFloat("elevation", this.elevation());
	}

	/** The mortar carried: use it on the ground to set it down, facing as you face. */
	static final class Kit extends Item {
		Kit(Item.Properties properties) {
			super(properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
			java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
			builder.accept(net.minecraft.network.chat.Component.translatable("item.jaymoskingdoms.field_mortar.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			if (!(context.getLevel() instanceof ServerLevel level)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.below()).isFaceSturdy(level, at.below(),
				net.minecraft.core.Direction.UP)) {
				return InteractionResult.FAIL;
			}
			FieldMortar mortar = new FieldMortar(TYPE, level);
			mortar.placed = true;
			mortar.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, context.getPlayer() == null ? 0.0F : context.getPlayer().getYRot(), 0.0F);
			level.addFreshEntity(mortar);
			level.playSound(null, at, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
			context.getItemInHand().consume(1, context.getPlayer());
			return InteractionResult.SUCCESS;
		}
	}
}
