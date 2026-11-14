package com.jaymoskingdoms;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Andy: "call the other a musketoon and give it to the caravan guards, it should fire the cannonballs. long reload time,
 * high damage." A short, fat hand gun (drawn as the Firecannon of cleannrooster's Frontier Armaments, MIT) that throws a
 * {@link Cannonball}.
 * <ul>
 * <li><b>In a player's hands</b>: hold use to load it ({@link #RELOAD} ticks, then let go); it takes one ball
 * ({@link #SHOT}) out of the pack, and nothing loads without one. Use it again to fire. The ball hits for
 * {@link #DAMAGE}, bursts like a wall gun's and leaves a pit ({@link #PIT}).</li>
 * <li><b>The caravans' guards</b> ({@link #arm}): every guard a realm or village hires for its carts and its roaming
 * merchants carries one, with a few balls ({@link MobAmmo.Kind#BALLS}). He fires at a foe in sight from
 * {@link #NEAREST} to {@link #FURTHEST} blocks, then is {@link #GUARD_RELOAD} ticks loading, fighting with the butt
 * meanwhile. His shot spares his own side ({@link Javelins#harms}) and his own realm's ground, as a wall gun's does.</li>
 * </ul>
 */
public final class Musketoon {
	/** A player holds use this long (ticks) to load it. */
	static final int RELOAD = 100;
	/** After a shot (or a load) it can't be used for this long (ticks). */
	static final int SETTLE = 10;
	/** Its ball's blow on a direct hit (a wall gun's is {@link Cannonball#DAMAGE}). */
	static final float DAMAGE = 18.0F;
	/** How far the shot throws its shooter's aim up (degrees), and shoves him back (blocks per tick). */
	static final float KICK = 6.0F;
	static final double RECOIL = 0.35;
	/** How many shots one lasts. */
	static final int DURABILITY = 200;
	/** A guard fires from this far to this far (blocks). */
	static final double NEAREST = 5.0, FURTHEST = 28.0;
	/** A guard is this long loading (ticks), plus up to {@link #JITTER} more. */
	static final int GUARD_RELOAD = 240, JITTER = 80;
	/** A dead guard's chance to drop his (over 1: always, and undamaged). */
	static final float DROP = 2.0F;
	/** How often (ticks) each armed guard is looked at. */
	static final int CHECK = 5;
	/** The pit a hand gun's ball leaves: smaller than a field gun's. */
	static final com.jaymoskingdoms.sailing.Bombardment.Crater PIT = new com.jaymoskingdoms.sailing.Bombardment.Crater(1.6, 1, 1,
		5.0F, com.jaymoskingdoms.sailing.Bombardment.Strip.STRUCTURES, 8, false);

	/** What it's loaded with (WeaponMod's cannonball). */
	public static final TagKey<Item> SHOT = TagKey.create(Registries.ITEM, JaymosKingdoms.id("musketoon_shot"));

	public static Item ITEM;

	/** The guards carrying one, and when each may fire next (game time). */
	private static final Map<PathfinderMob, Long> GUNNERS = new WeakHashMap<>();

	private Musketoon() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("musketoon"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new Gun(new Item.Properties().setId(key).durability(DURABILITY)));
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && mob instanceof Worker && mob.getMainHandItem().is(ITEM)) {
				GUNNERS.putIfAbsent(mob, 0L);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Musketoon::tick);
	}

	static boolean loaded(ItemStack stack) {
		return !stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
	}

	/** Out of {@code shooter}'s hands: the ball, the kick, the noise, and (with Jaymo's Powers) the blast at its muzzle. */
	private static void fire(ServerLevel level, LivingEntity shooter, ItemStack gun, Vec3 dir) {
		Vec3 muzzle = shooter.getEyePosition().add(dir.scale(1.0)).add(0.0, -0.15, 0.0);
		Cannonball.shot(level, shooter, muzzle, dir, DAMAGE, PIT);
		if (!PowersCompat.gunFired(shooter, gun)) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.15, 0.15, 0.15, 0.03);
			level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 5, 0.08, 0.08, 0.08, 0.02);
		}
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
			1.4F, 1.1F + level.getRandom().nextFloat() * 0.2F);
		Deafened.blast(level, muzzle, Deafened.MUZZLE);
	}

	// ---- the caravans' guards ----------------------------------------------------------------------------------------------

	/** A caravan's guard takes up a musketoon (his blade put away) and his few balls. */
	static void arm(ServerLevel level, PathfinderMob guard) {
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ITEM));
		guard.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		// (a guard who's killed leaves his gun where he falls, whole: the only way to come by one)
		guard.setDropChance(EquipmentSlot.MAINHAND, DROP);
		MobAmmo.left(guard, MobAmmo.Kind.BALLS);
		GUNNERS.put(guard, level.getGameTime() + level.getRandom().nextInt(JITTER));
	}

	/** Whether {@code mob} carries one. */
	static boolean holds(LivingEntity mob) {
		return mob.getMainHandItem().is(ITEM);
	}

	private static void tick(MinecraftServer server) {
		if (GUNNERS.isEmpty() || server.getTickCount() % CHECK != 0) {
			return;
		}
		for (Iterator<Map.Entry<PathfinderMob, Long>> it = GUNNERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<PathfinderMob, Long> e = it.next();
			PathfinderMob guard = e.getKey();
			if (guard.isRemoved() || !guard.isAlive() || !holds(guard)) {
				it.remove();
				continue;
			}
			if (!(guard.level() instanceof ServerLevel level) || Downed.isDowned(guard)) {
				continue;
			}
			long now = level.getGameTime();
			LivingEntity foe = Rulers.target(guard);
			if (now < e.getValue() || foe == null || !foe.isAlive()) {
				continue;
			}
			double d = guard.distanceTo(foe);
			if (d < NEAREST || d > FURTHEST || !guard.hasLineOfSight(foe) || !MobCombat.aimed(level, guard, foe)
				|| !MobAmmo.spend(guard, MobAmmo.Kind.BALLS)) {
				continue;
			}
			e.setValue(now + GUARD_RELOAD + level.getRandom().nextInt(JITTER));
			guard.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			// (the ball drops as it flies: aimed a little over him, more the further he is)
			Vec3 from = guard.getEyePosition();
			Vec3 to = new Vec3(foe.getX(), foe.getY(0.5), foe.getZ());
			double flat = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
			double time = flat / Cannonball.SPEED;
			Vec3 dir = to.add(0.0, 0.5 * Cannonball.GRAVITY * time * time, 0.0).subtract(from).normalize();
			fire(level, guard, guard.getMainHandItem(), dir);
		}
	}

	// ---- the gun -----------------------------------------------------------------------------------------------------------

	/** The item: loaded by holding use, fired by using it loaded. */
	static final class Gun extends Item {
		Gun(Item.Properties properties) {
			super(properties);
		}

		/** The first ball in {@code player}'s pack (his other hand first), or empty. */
		private static ItemStack ball(Player player) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (player.getItemInHand(hand).is(SHOT)) {
					return player.getItemInHand(hand);
				}
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(SHOT)) {
					return stack;
				}
			}
			return ItemStack.EMPTY;
		}

		private static ItemStack spare() {
			for (net.minecraft.core.Holder<Item> any : BuiltInRegistries.ITEM.getTagOrEmpty(SHOT)) {
				return new ItemStack(any);
			}
			return new ItemStack(net.minecraft.world.item.Items.IRON_NUGGET);
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
				if (ball(player).isEmpty() && !player.hasInfiniteMaterials()) {
					return InteractionResult.FAIL;
				}
				player.startUsingItem(hand);
				return InteractionResult.CONSUME;
			}
			if (level instanceof ServerLevel server) {
				gun.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
				fire(server, player, gun, player.getLookAngle());
				player.setXRot(player.getXRot() - KICK);
				Vec3 back = player.getLookAngle().scale(-RECOIL);
				player.push(back.x, 0.05, back.z);
				player.syncVelocity = true;
				player.awardStat(Stats.ITEM_USED.get(this));
				gun.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(gun, SETTLE);
			return InteractionResult.SUCCESS;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			int held = this.getUseDuration(stack, user) - remaining;
			if (level instanceof ServerLevel && (held == RELOAD / 3 || held == RELOAD * 2 / 3)) {
				// the ramrod going home
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
			} else if (level instanceof ServerLevel && held == RELOAD) {
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
			if (!(user instanceof Player player) || loaded(stack) || this.getUseDuration(stack, user) - remaining < RELOAD) {
				return false;
			}
			ItemStack ball = ball(player);
			if (ball.isEmpty() && !player.hasInfiniteMaterials()) {
				return false;
			}
			if (level instanceof ServerLevel) {
				// (in creative with none in the pack, a ball from nowhere)
				ItemStack one = ball.isEmpty() ? spare() : ball.copyWithCount(1);
				ball.consume(1, player);
				stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(one)));
				player.getCooldowns().addCooldown(stack, SETTLE);
			}
			return true;
		}
	}
}
