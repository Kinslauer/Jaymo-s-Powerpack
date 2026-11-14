package com.jaymoskingdoms;

import com.jaymoskingdoms.nether.AvatarCamps;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "this is something the tinkerer class flame avatar develop and will be seen carrying in the nether ... the lore
 * is they developed it during their struggles in the nether." The Fire Cannon: the Flame Avatars' Tinkerers' gun (drawn
 * as the Firecannon of cleannrooster's Frontier Armaments, MIT, in its red), which burns blaze instead of powder and ball.
 * <ul>
 * <li><b>Loading</b>: hold use for {@link #LOAD} ticks; it takes one blaze rod or one blaze powder out of the pack (the
 * other hand's first), and nothing loads without one.</li>
 * <li><b>A rod</b> goes out as one shot ({@link #lance}): straight as far as {@link #LANCE_RANGE}, {@link #LANCE_DAMAGE}
 * through any armour to the first thing it meets, which it sets alight; a block it meets takes fire.</li>
 * <li><b>Powder</b> goes out as a blast of fire ({@link #blast}): everything within {@link #BLAST_RANGE} in front of the
 * muzzle burns for {@link #BLAST_DAMAGE}, and the ground there is left burning.</li>
 * <li><b>The avatars' Tinkerers</b> ({@link #arm}, {@code nether/AvatarClasses}) carry one with a few charges
 * ({@link MobAmmo.Kind#BLAZE}): a shot at a foe from {@link #NEAREST} to {@link #FURTHEST}, a blast at one nearer, then
 * {@link #AVATAR_RELOAD} ticks loading. One who falls leaves his where he lies.</li>
 * </ul>
 */
public final class FireCannon {
	/** A player holds use this long (ticks) to load it; after a shot or a load it can't be used for {@link #SETTLE}. */
	static final int LOAD = 50, SETTLE = 10;
	/** A rod's shot: how far it flies, its blow (through armour), and how long what it hits burns (seconds). */
	static final double LANCE_RANGE = 40.0;
	static final float LANCE_DAMAGE = 12.0F;
	static final float LANCE_BURN = 5.0F;
	/** Powder's blast: how far out of the muzzle, how wide (the cosine of its half angle), its burn and how long it lasts. */
	static final double BLAST_RANGE = 6.0, BLAST_CONE = 0.8;
	static final float BLAST_DAMAGE = 7.0F;
	static final float BLAST_BURN = 6.0F;
	/** How many patches of ground a blast leaves burning. */
	static final int BLAST_FIRES = 4;
	/** How far the shot throws its shooter's aim up (degrees). */
	static final float KICK = 3.0F;
	/** How many shots one lasts. */
	static final int DURABILITY = 250;
	/** An avatar shoots from this far to this far (blocks), blasts a foe nearer, and is this long loading (plus jitter). */
	static final double NEAREST = 5.0, FURTHEST = 24.0;
	static final int AVATAR_RELOAD = 120, JITTER = 60;
	/** A dead avatar's chance to drop his (over 1: always, and undamaged). */
	static final float DROP = 2.0F;
	/** How often (ticks) each armed avatar is looked at. */
	static final int CHECK = 5;

	static final ResourceKey<DamageType> LANCE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKingdoms.id("fire_lance"));
	static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKingdoms.id("fire_blast"));

	public static Item ITEM;

	/** The avatars carrying one, and when each may fire next (game time). */
	private static final Map<PathfinderMob, Long> GUNNERS = new WeakHashMap<>();

	private FireCannon() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("fire_cannon"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Gun(new Item.Properties().setId(key).durability(DURABILITY).fireResistant()));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && AvatarCamps.avatar(mob) && holds(mob)) {
				GUNNERS.putIfAbsent(mob, 0L);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(FireCannon::tick);
	}

	static boolean loaded(ItemStack stack) {
		return !stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
	}

	/** Whether {@code mob} carries one. */
	public static boolean holds(LivingEntity mob) {
		return ITEM != null && mob.getMainHandItem().is(ITEM);
	}

	// ---- the two shots -----------------------------------------------------------------------------------------------------

	private static Vec3 muzzle(LivingEntity shooter, Vec3 dir) {
		return shooter.getEyePosition().add(dir.scale(1.0)).add(0.0, -0.15, 0.0);
	}

	private static void report(ServerLevel level, LivingEntity shooter, ItemStack gun, Vec3 muzzle) {
		if (!PowersCompat.gunFired(shooter, gun)) {
			level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 14, 0.1, 0.1, 0.1, 0.04);
			level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
		}
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4F,
			0.7F + level.getRandom().nextFloat() * 0.2F);
	}

	/** A blaze rod out of the barrel: one shot along {@code dir}, through armour, setting alight what it meets. */
	private static void lance(ServerLevel level, LivingEntity shooter, ItemStack gun, Vec3 dir, Predicate<LivingEntity> harms) {
		Vec3 from = shooter.getEyePosition();
		Vec3 muzzle = muzzle(shooter, dir);
		Vec3 to = from.add(dir.scale(LANCE_RANGE));
		BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		if (wall.getType() != HitResult.Type.MISS) {
			to = wall.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, from, to, new AABB(from, to).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e != shooter.getVehicle() && harms.test(l), from.distanceToSqr(to));
		Vec3 end = hit != null ? hit.getLocation() : to;
		report(level, shooter, gun, muzzle);
		double far = muzzle.distanceTo(end);
		for (double d = 0.0; d < far; d += 0.6) {
			Vec3 p = muzzle.add(dir.scale(d));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		if (hit != null && hit.getEntity() instanceof LivingEntity victim) {
			victim.igniteForSeconds(LANCE_BURN);
			victim.hurtServer(level, level.damageSources().source(LANCE, shooter), LANCE_DAMAGE);
			level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.0);
		} else if (wall.getType() != HitResult.Type.MISS) {
			kindle(level, wall.getBlockPos().relative(wall.getDirection()));
			level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 4, 0.1, 0.1, 0.1, 0.0);
		}
	}

	/** Blaze powder out of the barrel: a blast of fire over everything close in front of it. */
	private static void blast(ServerLevel level, LivingEntity shooter, ItemStack gun, Vec3 dir, Predicate<LivingEntity> harms) {
		Vec3 muzzle = muzzle(shooter, dir);
		report(level, shooter, gun, muzzle);
		for (int i = 0; i < 40; i++) {
			double out = 0.5 + level.getRandom().nextDouble() * (BLAST_RANGE - 0.5);
			Vec3 p = muzzle.add(dir.scale(out)).add(level.getRandom().triangle(0.0, out * 0.35), level.getRandom().triangle(0.0, out * 0.25),
				level.getRandom().triangle(0.0, out * 0.35));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
		}
		for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(muzzle, muzzle).inflate(BLAST_RANGE),
			e -> e != shooter && e.isAlive() && !e.isSpectator() && e != shooter.getVehicle())) {
			Vec3 toVictim = victim.getBoundingBox().getCenter().subtract(shooter.getEyePosition());
			double d = toVictim.length();
			if (d > BLAST_RANGE + 1.0 || d > 1.0E-4 && toVictim.scale(1.0 / d).dot(dir) < BLAST_CONE || !shooter.hasLineOfSight(victim)
				|| !harms.test(victim)) {
				continue;
			}
			victim.igniteForSeconds(BLAST_BURN);
			victim.hurtServer(level, level.damageSources().source(BLAST, shooter), BLAST_DAMAGE);
		}
		for (int i = 0; i < BLAST_FIRES; i++) {
			double out = 1.5 + level.getRandom().nextDouble() * (BLAST_RANGE - 1.5);
			Vec3 p = muzzle.add(dir.scale(out)).add(level.getRandom().triangle(0.0, 1.0), 0.0, level.getRandom().triangle(0.0, 1.0));
			BlockHitResult ground = level.clip(new ClipContext(p, p.add(0.0, -4.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, shooter));
			if (ground.getType() != HitResult.Type.MISS && !level.clip(new ClipContext(muzzle, p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
				shooter)).getType().equals(HitResult.Type.BLOCK)) {
				kindle(level, ground.getBlockPos().above());
			}
		}
	}

	private static void kindle(ServerLevel level, BlockPos at) {
		if (level.getBlockState(at).isAir() && BaseFireBlock.canBePlacedAt(level, at, net.minecraft.core.Direction.UP)) {
			level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
		}
	}

	// ---- the avatars' Tinkerers --------------------------------------------------------------------------------------------

	/** A Flame Avatar takes up a fire cannon (whatever it held put away) and its few charges. */
	public static void arm(ServerLevel level, PathfinderMob avatar) {
		avatar.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ITEM));
		avatar.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		// (one who's killed leaves his gun where he falls, whole)
		avatar.setDropChance(EquipmentSlot.MAINHAND, DROP);
		MobAmmo.left(avatar, MobAmmo.Kind.BLAZE);
		GUNNERS.put(avatar, level.getGameTime() + level.getRandom().nextInt(JITTER));
	}

	private static void tick(MinecraftServer server) {
		if (GUNNERS.isEmpty() || server.getTickCount() % CHECK != 0) {
			return;
		}
		for (Iterator<Map.Entry<PathfinderMob, Long>> it = GUNNERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<PathfinderMob, Long> e = it.next();
			PathfinderMob avatar = e.getKey();
			if (avatar.isRemoved() || !avatar.isAlive() || !holds(avatar)) {
				it.remove();
				continue;
			}
			if (!(avatar.level() instanceof ServerLevel level) || Downed.isDowned(avatar)) {
				continue;
			}
			long now = level.getGameTime();
			LivingEntity foe = Rulers.target(avatar);
			if (now < e.getValue() || foe == null || !foe.isAlive()) {
				continue;
			}
			double d = avatar.distanceTo(foe);
			boolean near = d < NEAREST;
			if (d > FURTHEST || !avatar.hasLineOfSight(foe) || !near && !MobCombat.aimed(level, avatar, foe)
				|| !MobAmmo.spend(avatar, MobAmmo.Kind.BLAZE)) {
				continue;
			}
			e.setValue(now + AVATAR_RELOAD + level.getRandom().nextInt(JITTER));
			avatar.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			Vec3 dir = new Vec3(foe.getX(), foe.getY(0.5), foe.getZ()).subtract(avatar.getEyePosition()).normalize();
			// (its fire spares its own camp and its kin; whatever else stands in it burns)
			Predicate<LivingEntity> harms = victim -> victim == foe || !AvatarCamps.avatar(victim) && AvatarCamps.prey(avatar, victim);
			if (near) {
				blast(level, avatar, avatar.getMainHandItem(), dir, harms);
			} else {
				lance(level, avatar, avatar.getMainHandItem(), dir, harms);
			}
		}
	}

	// ---- the gun -----------------------------------------------------------------------------------------------------------

	/** The item: loaded by holding use, fired by using it loaded. */
	static final class Gun extends Item {
		Gun(Item.Properties properties) {
			super(properties);
		}

		@Override
		@SuppressWarnings("deprecation")
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
			java.util.function.Consumer<net.minecraft.network.chat.Component> builder, net.minecraft.world.item.TooltipFlag flag) {
			builder.accept(net.minecraft.network.chat.Component.translatable("item.jaymoskingdoms.fire_cannon.tip").withStyle(net.minecraft.ChatFormatting.GRAY));
		}

		private static boolean charge(ItemStack stack) {
			return stack.is(Items.BLAZE_ROD) || stack.is(Items.BLAZE_POWDER);
		}

		/** The first rod or powder in {@code player}'s pack (his other hand first), or empty. */
		private static ItemStack charge(Player player) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (charge(player.getItemInHand(hand))) {
					return player.getItemInHand(hand);
				}
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (charge(stack)) {
					return stack;
				}
			}
			return ItemStack.EMPTY;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return loaded(stack) ? ItemUseAnimation.NONE : ItemUseAnimation.CROSSBOW;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return 72000;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack gun = player.getItemInHand(hand);
			if (!loaded(gun)) {
				if (charge(player).isEmpty() && !player.hasInfiniteMaterials()) {
					return InteractionResult.FAIL;
				}
				player.startUsingItem(hand);
				return InteractionResult.CONSUME;
			}
			if (level instanceof ServerLevel server) {
				boolean powder = gun.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).contains(Items.BLAZE_POWDER);
				gun.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
				// (a player's fire spares nobody but those in creative)
				Predicate<LivingEntity> harms = victim -> !(victim instanceof Player p && p.isCreative());
				if (powder) {
					blast(server, player, gun, player.getLookAngle(), harms);
				} else {
					lance(server, player, gun, player.getLookAngle(), harms);
				}
				player.setXRot(player.getXRot() - KICK);
				player.awardStat(Stats.ITEM_USED.get(this));
				gun.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(gun, SETTLE);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (level instanceof ServerLevel && this.getUseDuration(stack, user) - remaining == LOAD) {
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
			}
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
			if (!(user instanceof Player player) || loaded(stack) || this.getUseDuration(stack, user) - remaining < LOAD) {
				return false;
			}
			ItemStack charge = charge(player);
			if (charge.isEmpty() && !player.hasInfiniteMaterials()) {
				return false;
			}
			if (level instanceof ServerLevel) {
				// (in creative with none in the pack, a rod from nowhere)
				ItemStack one = charge.isEmpty() ? new ItemStack(Items.BLAZE_ROD) : charge.copyWithCount(1);
				charge.consume(1, player);
				stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(one)));
				player.getCooldowns().addCooldown(stack, SETTLE);
			}
			return true;
		}
	}
}
