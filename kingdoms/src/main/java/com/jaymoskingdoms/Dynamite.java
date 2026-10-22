package com.jaymoskingdoms;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.107) Dynamite: a stick of it, lit and thrown. A pirate {@link Pirates#BOMBARDIER} lobs it over whatever's between
 * him and his foe (the idea from Zombie More's dynamite zombie; nothing of its code or art is used), and anyone can throw
 * one ({@link #ITEM}: the bombardiers drop a few, or craft them). It arcs, bounces, rolls to a stop and sizzles: the fuse
 * gives {@link #FUSE} ticks from the throw, time to run. Then it bursts like a {@link Cannonball} (hurting all within
 * {@link #BURST_RADIUS}, never its thrower's own side when a folk threw it) and leaves blocks whole, so a landing party
 * doesn't level the village it raids. Water puts the fuse out. Hit a lit stick and it flies off where you're looking,
 * and is yours: bat it back at the man who threw it. Never saved.
 * <p>(0.7.156) Andy: "change dynamite to just the balkon recipe and design". There's one dynamite in the pack: Balkon's
 * WeaponMod's ({@code weaponmod:dynamite}, MIT). With WeaponMod loaded the bombardiers hold, drop and throw its stick
 * ({@link #stick()}), ours is off the creative tab and its recipe, and an old stick of ours crafts into Balkon's. Without
 * WeaponMod ours stands in, drawn and crafted as Balkon's (string over two gunpowder makes two; its texture, MIT).
 */
public final class Dynamite extends Projectile implements ItemSupplier {
	/** The fuse, from the throw (ticks); the sizzle gets louder over its last {@link #HURRY} ticks. */
	static final int FUSE = 50, HURRY = 20;
	/** How fast it falls (blocks per tick, per tick), how much speed a bounce keeps, and how slow it rests. */
	static final double GRAVITY = 0.045, BOUNCE = 0.35, FRICTION = 0.6, REST = 0.04;
	/** Its burst: a blow at its heart falling off to {@link #BURST_SHARE} of it at this radius, and the shove. */
	static final float DAMAGE = 12.0F, BURST_SHARE = 0.25F;
	static final double BURST_RADIUS = 3.5, KNOCKBACK = 1.1;
	/** A direct hit in flight only knocks: this much, then it drops at the victim's feet. */
	static final float BONK = 1.0F;
	/** A player's throw (blocks per tick) and its loft; how hard a bat sends it back. */
	static final float THROW_SPEED = 0.9F, THROW_LOFT = 0.15F;
	static final double BAT_SPEED = 0.8;
	/** Between a player's throws (ticks). */
	static final int THROW_COOLDOWN = 10;
	/** A bombardier drops this many sticks at most (and none one time in {@link #DROP_NONE_ONE_IN}). */
	static final int DROP_MOST = 2, DROP_NONE_ONE_IN = 3;

	public static EntityType<Dynamite> TYPE;
	/** Ours: only the stand-in for Balkon's when WeaponMod isn't loaded (use {@link #stick()}). */
	public static Item ITEM;
	/** Balkon's WeaponMod's dynamite, the pack's one stick. */
	static final Identifier BALKON = Identifier.fromNamespaceAndPath(Javelins.WEAPONMOD, "dynamite");
	private static @Nullable Item balkon;

	private int fuse = FUSE;
	private boolean bonked;

	public Dynamite(EntityType<? extends Dynamite> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		this.setNoGravity(true);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("dynamite"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<Dynamite>of(Dynamite::new, MobCategory.MISC)
			.noLootTable()
			.sized(0.3F, 0.3F)
			.clientTrackingRange(8)
			.updateInterval(1)
			.build(key));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("dynamite"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new StickItem(new Item.Properties().setId(itemKey).stacksTo(16)));
		if (!Javelins.WEAPONMOD_LOADED) {
			CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.WIND_CHARGE, ITEM));
		}
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Folk folk && folk.role() == Pirates.BOMBARDIER && folk.level() instanceof ServerLevel level
				&& level.getRandom().nextInt(DROP_NONE_ONE_IN) > 0) {
				folk.spawnAtLocation(level, new ItemStack(stick(), 1 + level.getRandom().nextInt(DROP_MOST)));
			}
		});
	}

	/** The pack's dynamite stick: Balkon's when WeaponMod is loaded, else ours. */
	public static Item stick() {
		if (!Javelins.WEAPONMOD_LOADED) {
			return ITEM;
		}
		if (balkon == null) {
			balkon = BuiltInRegistries.ITEM.getOptional(BALKON).orElse(ITEM);
		}
		return balkon;
	}

	/** A lit stick thrown by {@code thrower} from his hand, along {@code velocity}. */
	static Dynamite light(ServerLevel level, LivingEntity thrower, Vec3 velocity) {
		Dynamite stick = new Dynamite(TYPE, level);
		stick.setOwner(thrower);
		stick.setPos(thrower.getX(), thrower.getEyeY() - 0.2, thrower.getZ());
		stick.setDeltaMovement(velocity);
		level.addFreshEntity(stick);
		level.playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 0.8F, 1.4F);
		return stick;
	}

	/**
	 * The throw that lands a stick at {@code target} from {@code from} under {@link #GRAVITY}: a lob that takes
	 * {@code ticks} to get there (a longer throw flies higher).
	 */
	static Vec3 lob(Vec3 from, Vec3 target, int ticks) {
		Vec3 d = target.subtract(from);
		return new Vec3(d.x / ticks, d.y / ticks + 0.5 * GRAVITY * ticks, d.z / ticks);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
	}

	@Override
	public ItemStack getItem() {
		return new ItemStack(stick());
	}

	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) {
			return;
		}
		Vec3 pos = this.position();
		if (!level.getFluidState(BlockPos.containing(pos)).isEmpty()) {
			// doused
			level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y + 0.1, pos.z, 6, 0.1, 0.1, 0.1, 0.01);
			level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.6F, 1.6F);
			this.spawnAtLocation(level, new ItemStack(stick()));
			this.discard();
			return;
		}
		if (--this.fuse <= 0) {
			this.burst(level);
			return;
		}
		Vec3 move = this.getDeltaMovement().add(0.0, -GRAVITY, 0.0);
		Vec3 next = pos.add(move);
		HitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (!this.bonked && move.lengthSqr() > REST * REST) {
			Entity owner = this.getOwner();
			EntityHitResult struck = ProjectileUtil.getEntityHitResult(level, this, pos, next, new AABB(pos, next).inflate(0.4),
				e -> e != owner && e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), 0.3F);
			if (struck != null) {
				// it knocks him and drops at his feet
				this.bonked = true;
				struck.getEntity().hurtServer(level, this.damageSources().thrown(this, owner), BONK);
				this.setPos(struck.getLocation());
				this.setDeltaMovement(move.multiply(-0.1, 0.0, -0.1));
				return;
			}
		}
		if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
			Direction face = block.getDirection();
			Vec3 at = block.getLocation().add(face.getStepX() * 0.16, face.getStepY() * 0.01, face.getStepZ() * 0.16);
			move = switch (face.getAxis()) {
				case Y -> new Vec3(move.x * FRICTION, -move.y * BOUNCE, move.z * FRICTION);
				case X -> new Vec3(-move.x * BOUNCE, move.y, move.z);
				case Z -> new Vec3(move.x, move.y, -move.z * BOUNCE);
			};
			if (face == Direction.UP && Math.abs(move.y) < 0.1) {
				move = new Vec3(move.x, 0.0, move.z);
				if (move.horizontalDistanceSqr() < REST * REST) {
					move = Vec3.ZERO;
				}
			}
			if (move.lengthSqr() > 0.01) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 0.4F, 1.6F);
			}
			this.setPos(at);
		} else {
			this.setPos(next);
		}
		this.setDeltaMovement(move);
		// the fuse sputters (faster as it burns down)
		Vec3 tip = this.position().add(0.0, 0.25, 0.0);
		level.sendParticles(ParticleTypes.SMALL_FLAME, tip.x, tip.y, tip.z, 1, 0.02, 0.02, 0.02, 0.005);
		if (this.fuse <= HURRY || this.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, tip.x, tip.y, tip.z, 1, 0.03, 0.03, 0.03, 0.01);
		}
		if (this.fuse % (this.fuse <= HURRY ? 4 : 10) == 0) {
			level.playSound(null, tip.x, tip.y, tip.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.25F, 2.0F);
		}
	}

	private void burst(ServerLevel level) {
		Vec3 at = this.position().add(0.0, 0.15, 0.0);
		Entity owner = this.getOwner();
		DamageSource source = this.damageSources().explosion(this, owner);
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST_RADIUS),
			e -> e.isAlive() && !e.isSpectator())) {
			double d = victim.getBoundingBox().getCenter().distanceTo(at);
			if (d > BURST_RADIUS || owner instanceof Folk folk && (victim == folk || !Javelins.harms(folk, victim))) {
				continue;
			}
			if (victim instanceof Player p && p.isCreative()) {
				continue;
			}
			float share = (float) (1.0 - d / BURST_RADIUS);
			victim.hurtServer(level, source, DAMAGE * (BURST_SHARE + (1.0F - BURST_SHARE) * share));
			double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
			Vec3 away = victim.position().subtract(at);
			if (resist > 0.0 && away.lengthSqr() > 1.0E-4) {
				Vec3 push = away.normalize().scale(KNOCKBACK * share * resist);
				victim.push(push.x, 0.35 * share * resist, push.z);
				victim.syncVelocity = true;
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.3, 0.2, 0.3, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 14, 0.5, 0.4, 0.5, 0.04);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, 0.3, 0.2, 0.3, 0.06);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.8F, 1.1F);
		this.discard();
	}

	/** Hit, it flies off where the hitter looks, and is his now. */
	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity hitter = source.getEntity();
		if (!(hitter instanceof LivingEntity batter) || source.getDirectEntity() != hitter) {
			return false;
		}
		Vec3 look = batter.getLookAngle();
		this.setDeltaMovement(look.x * BAT_SPEED, Math.max(0.25, look.y * BAT_SPEED + 0.2), look.z * BAT_SPEED);
		this.setOwner(batter);
		this.bonked = false;
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 1.2F);
		return true;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean shouldRenderAtSqrDistance(double distance) {
		return distance < 96.0 * 96.0;
	}

	/** A stick of dynamite: use it to light it and throw it. */
	static final class StickItem extends Item {
		StickItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (level instanceof ServerLevel server) {
				Vec3 look = player.getLookAngle();
				light(server, player, look.scale(THROW_SPEED).add(0.0, THROW_LOFT, 0.0).add(player.getDeltaMovement()));
			}
			ItemStack held = player.getItemInHand(hand);
			player.getCooldowns().addCooldown(held, THROW_COOLDOWN);
			held.consume(1, player);
			return InteractionResult.SUCCESS;
		}
	}
}
