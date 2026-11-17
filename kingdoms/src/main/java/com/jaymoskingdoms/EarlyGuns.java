package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The guns before the flintlock (the idea from Knights &amp; Heraldry; the guns, numbers, art and code are ours), and the
 * gun ladder they begin. Three rungs: the <b>handgonne</b> (an iron tube on a pole, touched off with a match), the
 * <b>arquebus</b> (a matchlock: a lever drops the match into the pan), then Balkon's WeaponMod musket.
 * <ul>
 * <li><b>In a player's hands</b> ({@link Gun}): hold use to load ({@link Rung#load} ticks, then let go); it takes one
 * pinch of gunpowder and one ball ({@link #SHOT}) out of the pack, and nothing loads without both. Use it again to fire:
 * the arquebus at once, the handgonne after the match has been held to the touch-hole for {@link Rung#touch} ticks
 * (let go before and it doesn't fire). Its {@link GunBall} hits for {@link Rung#damage}. Both are fired by a burning
 * match, so neither fires in the rain or under water: it fizzles and stays loaded.</li>
 * <li><b>The ladder</b>: each barrel is forged on Jaymo's Tinkering's Smithing Anvil and put on its stock at the Tool
 * Station ({@code tinkering/WeaponParts}); the anvil refuses the handgonne's below the {@link Rung#tier} world tier
 * Borderlands, the arquebus's below Wildlands ({@link #gate}). The musket waits for Dread (the item tags
 * {@code jaymoskingdoms:tier_gate/*}).</li>
 * <li><b>The realms' guards</b> (Andy: "each tier of realm in terms of wealth has guards equipped with one. now the
 * richest would be considered 1000 gold"): about one guard in {@link Muskets#SHARE} of every standing realm carries the
 * gun its purse runs to ({@link #issue}): handgonnes in a poor kingdom and in every fiefdom, arquebuses in a prosperous
 * kingdom, muskets in a wealthy one ({@link Wealth}). A gunner fires at a foe in sight within his gun's range, a ball
 * from his pouch each time ({@link MobAmmo.Kind#SHOT}), then is a long while loading; his match is no better in the
 * rain than a player's. A realm that grows richer or poorer changes its guards' guns ({@link #KEEP}).</li>
 * </ul>
 */
public final class EarlyGuns {
	/** The two guns: what each is in a player's hands, and in a guard's. */
	public enum Rung {
		HANDGONNE(1, 120, 10, 9.0F, 2.2, 5.0F, 8.0F, 0.3, 120, 0.8F, 5.0F, 4.0, 14.0, 200),
		ARQUEBUS(2, 80, 0, 14.0F, 3.0, 2.0F, 5.0F, 0.2, 180, 1.2F, 7.0F, 4.0, 20.0, 160);

		/** The world tier (an ordinal) its barrel can be forged from. */
		final int tier;
		/** A player holds use this long (ticks) to load it; loaded, this long to touch it off (0: it fires at once). */
		final int load, touch;
		/** Its ball's blow from a player, how fast the ball flies (blocks a tick) and how wide it may stray (degrees). */
		final float damage;
		final double speed;
		final float spread;
		/** How far the shot throws its shooter's aim up (degrees), and shoves him back (blocks per tick). */
		final float kick;
		final double recoil;
		/** How many shots one lasts, and the pitch of its report. */
		final int durability;
		final float pitch;
		/** From a guard: his ball's blow, the range he fires at (blocks), and how long he is loading (ticks, plus up to {@link #JITTER}). */
		final float guardDamage;
		final double nearest, furthest;
		final int guardReload;

		Item item;

		Rung(int tier, int load, int touch, float damage, double speed, float spread, float kick, double recoil, int durability, float pitch,
			float guardDamage, double nearest, double furthest, int guardReload) {
			this.tier = tier;
			this.load = load;
			this.touch = touch;
			this.damage = damage;
			this.speed = speed;
			this.spread = spread;
			this.kick = kick;
			this.recoil = recoil;
			this.durability = durability;
			this.pitch = pitch;
			this.guardDamage = guardDamage;
			this.nearest = nearest;
			this.furthest = furthest;
			this.guardReload = guardReload;
		}

		String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Rung of(ItemStack stack) {
			for (Rung rung : values()) {
				if (stack.is(rung.item)) {
					return rung;
				}
			}
			return null;
		}
	}

	/** What a realm hands its gunners. */
	enum Issue {
		NONE, HANDGONNE, ARQUEBUS, MUSKET
	}

	/** After a shot (or a load) a gun can't be used for this long (ticks). */
	static final int SETTLE = 10;
	/** A realm's gunners keep the gun they have until its worth falls below this share of the bar it was handed out at. */
	static final double KEEP = 0.8;
	/** How often a guard's gun is looked over (ticks), and how often each gunner is looked at for a shot. */
	static final int ISSUE_CHECK = 100, CHECK = 5;
	/** A guard's loading takes up to this much longer (ticks). */
	static final int JITTER = 60;
	/** A dead gunner's chance to drop his gun (none: the ladder is climbed at the anvil). */
	static final float DROP = 0.0F;
	/** The tag on a guard whose gun is his realm's issue. */
	static final String ISSUED = "jaymoskingdoms.gunner";

	/** What they're loaded with, beside the powder: a musket ball (WeaponMod's), or an iron nugget. */
	public static final TagKey<Item> SHOT = TagKey.create(Registries.ITEM, JaymosKingdoms.id("gun_shot"));

	/** The gunners, and when each may fire next (game time). */
	private static final Map<PathfinderMob, Long> GUNNERS = new WeakHashMap<>();
	/** When each guard's issue is next looked over (game time). */
	private static final Map<PathfinderMob, Long> LOOKED = new WeakHashMap<>();

	private EarlyGuns() {
	}

	static void register() {
		for (Rung rung : Rung.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(rung.id()));
			rung.item = Registry.register(BuiltInRegistries.ITEM, key, new Gun(rung, new Item.Properties().setId(key).durability(rung.durability)
				.component(DataComponents.LORE, new ItemLore(List.of(
					Component.translatable("item.jaymoskingdoms." + rung.id() + ".hint").withStyle(ChatFormatting.GRAY))))));
		}
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && gunner(mob)) {
				GUNNERS.putIfAbsent(mob, 0L);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(EarlyGuns::tick);
		gate();
	}

	static boolean loaded(ItemStack stack) {
		return !stack.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty();
	}

	/** Whether {@code mob} has a handgonne or an arquebus in hand. */
	static boolean holds(LivingEntity mob) {
		return Rung.of(mob.getMainHandItem()) != null;
	}

	/** Whether {@code mob} is a gunner: an early gun in hand, or (a guard of a wealthy realm) the musket he was issued. */
	static boolean gunner(LivingEntity mob) {
		return holds(mob) || mob.entityTags().contains(ISSUED) && mob.getMainHandItem().is(Muskets.musket());
	}

	/** A soaked gunner's powder (Jaymo's Powers' Soaked) is damp: this share of his shots fizzle out of the rain too. */
	static final float DAMP_FIZZLE = 0.5F;

	/** A match burns in neither rain nor water, and damp powder often won't take (decided on the server). */
	private static boolean wet(LivingEntity shooter) {
		// (class trees) a Marksman's Dry Powder takes in rain, standing water or a soaking
		if (WarExtras.dryPowder(shooter)) {
			return false;
		}
		return shooter.isInWaterOrRain()
			|| shooter.level() instanceof ServerLevel && PowersCompat.soaked(shooter) && shooter.getRandom().nextFloat() < DAMP_FIZZLE;
	}

	/** {@code dir}, strayed by up to about {@code degrees}. */
	private static Vec3 stray(Vec3 dir, float degrees, RandomSource random) {
		double s = Math.tan(Math.toRadians(degrees));
		return dir.add(random.triangle(0.0, s), random.triangle(0.0, s), random.triangle(0.0, s)).normalize();
	}

	/** Out of {@code shooter}'s hands: the ball, the noise, and (with Jaymo's Powers) the blast at its muzzle. */
	private static void fire(ServerLevel level, LivingEntity shooter, ItemStack gun, Rung rung, Vec3 aim, float damage) {
		// (class trees) a Marksman's Steady Aim: less stray
		Vec3 dir = stray(aim, rung.spread * Workshop.spread(shooter), level.getRandom());
		boolean muffled = Workshop.muffled(gun);
		Vec3 eye = shooter.getEyePosition().add(0.0, -0.1, 0.0);
		Vec3 muzzle = eye.add(dir);
		GunBall.shot(level, shooter, eye, dir, rung.speed, damage);
		if (!PowersCompat.gunFired(shooter, gun)) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.12, 0.12, 0.12, 0.03);
			level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.06, 0.06, 0.06, 0.02);
		}
		// (class trees) a Muffled barrel: a cough, not a bang
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
			shooter instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE, muffled ? 0.15F : 1.1F, rung.pitch + level.getRandom().nextFloat() * 0.2F);
		if (!muffled) {
			Deafened.blast(level, muzzle, Deafened.MUZZLE);
		}
	}

	/** The match hisses out: nothing fires. */
	private static void fizzle(ServerLevel level, LivingEntity shooter) {
		Vec3 at = shooter.getEyePosition().add(shooter.getLookAngle().scale(0.6));
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y - 0.2, at.z, 5, 0.05, 0.05, 0.05, 0.01);
		level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
		if (shooter instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable(player.isInWaterOrRain() ? "message.jaymoskingdoms.early_gun.wet"
				: "message.jaymoskingdoms.early_gun.damp"));
		}
	}

	// ---- the realms' gunners -----------------------------------------------------------------------------------------------

	/**
	 * The gun {@code guard} should carry for {@code f}: none unless he is one of the {@link Muskets#SHARE} picked (by who
	 * he is, so always the same men) of a standing realm; then by its wealth. A fiefdom's riches go to its overlord, so
	 * its gunners carry handgonnes. A gun in hand is kept down to {@link #KEEP} of its bar.
	 */
	static Issue issue(MinecraftServer server, @Nullable Faction f, PathfinderMob guard) {
		if (f == null || !f.type().isRealm() || f.fallen() || Math.floorMod(guard.getUUID().hashCode(), Muskets.SHARE) != 0) {
			return Issue.NONE;
		}
		if (f.type() != FactionType.KINGDOM) {
			return Issue.HANDGONNE;
		}
		long worth = Wealth.worth(server, f);
		ItemStack held = guard.getMainHandItem();
		if (worth >= Wealth.WEALTHY || held.is(Muskets.musket()) && worth >= Wealth.WEALTHY * KEEP) {
			return Issue.MUSKET;
		}
		if (worth >= Wealth.PROSPEROUS || held.is(Rung.ARQUEBUS.item) && worth >= Wealth.PROSPEROUS * KEEP) {
			return Issue.ARQUEBUS;
		}
		return Issue.HANDGONNE;
	}

	/** From {@code Workers.step}: one of a realm's MCA guards, his gun looked over every {@link #ISSUE_CHECK} ticks. */
	static void garrison(ServerLevel level, PathfinderMob guard, int factionId) {
		// (a caravan's guard keeps his musketoon)
		long now = level.getGameTime();
		if (Musketoon.holds(guard) || now < LOOKED.getOrDefault(guard, 0L)) {
			return;
		}
		LOOKED.put(guard, now + ISSUE_CHECK);
		hand(level, guard, issue(level.getServer(), Factions.get(level.getServer(), factionId), guard));
	}

	/**
	 * {@code guard} is handed the gun of {@code issue} (his blade put away), or gives back the one he had. One of our own
	 * folk gets his musket from {@link Muskets}, which fires it too.
	 */
	static void hand(ServerLevel level, PathfinderMob guard, Issue issue) {
		Item want = switch (issue) {
			case HANDGONNE -> Rung.HANDGONNE.item;
			case ARQUEBUS -> Rung.ARQUEBUS.item;
			case MUSKET -> guard instanceof Folk ? null : Muskets.musket();
			case NONE -> null;
		};
		boolean has = guard instanceof Folk ? holds(guard) : gunner(guard);
		if (want == null) {
			if (has && issue == Issue.NONE) {
				guard.removeTag(ISSUED);
				if (guard instanceof Folk folk) {
					folk.rearm(level);
				} else {
					// (MCA arms its guard again when it finds his hand empty)
					guard.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
			}
			return;
		}
		if (has && guard.getMainHandItem().is(want)) {
			return;
		}
		guard.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(want));
		guard.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		guard.setDropChance(EquipmentSlot.MAINHAND, DROP);
		guard.addTag(ISSUED);
		MobAmmo.left(guard, MobAmmo.Kind.SHOT);
		GUNNERS.put(guard, level.getGameTime() + level.getRandom().nextInt(JITTER));
	}

	private static void tick(MinecraftServer server) {
		if (GUNNERS.isEmpty() || server.getTickCount() % CHECK != 0) {
			return;
		}
		for (Iterator<Map.Entry<PathfinderMob, Long>> it = GUNNERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<PathfinderMob, Long> e = it.next();
			PathfinderMob guard = e.getKey();
			if (guard.isRemoved() || !guard.isAlive() || !gunner(guard)) {
				it.remove();
				continue;
			}
			Rung rung = Rung.of(guard.getMainHandItem());
			// (a folk musketeer's shots are Muskets')
			if (!(guard.level() instanceof ServerLevel level) || Downed.isDowned(guard) || rung == null && guard instanceof Folk) {
				continue;
			}
			long now = level.getGameTime();
			LivingEntity foe = Rulers.target(guard);
			if (now < e.getValue() || foe == null || !foe.isAlive()) {
				continue;
			}
			double d = guard.distanceTo(foe);
			if (d < (rung == null ? Muskets.NEAREST : rung.nearest) || d > (rung == null ? Muskets.FURTHEST : rung.furthest)
				|| rung != null && wet(guard) || !guard.hasLineOfSight(foe) || !MobCombat.aimed(level, guard, foe)) {
				continue;
			}
			if (rung == null) {
				int wait = FrontierPerks.reload(guard, Muskets.RELOAD) + level.getRandom().nextInt(Muskets.JITTER);
				e.setValue(now + wait);
				RangedTactics.reload(guard, wait);
				Muskets.fire(level, guard, foe);
				continue;
			}
			if (!MobAmmo.spend(guard, MobAmmo.Kind.SHOT)) {
				continue;
			}
			int wait = FrontierPerks.reload(guard, rung.guardReload) + level.getRandom().nextInt(JITTER);
			e.setValue(now + wait);
			RangedTactics.reload(guard, wait);
			guard.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			// (the ball drops as it flies: aimed a little over him, more the further he is)
			Vec3 from = guard.getEyePosition();
			Vec3 to = new Vec3(foe.getX(), foe.getY(0.5), foe.getZ());
			double flat = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
			double time = flat / rung.speed;
			fire(level, guard, guard.getMainHandItem(), rung, to.add(0.0, 0.5 * GunBall.GRAVITY * time * time, 0.0).subtract(from).normalize(),
				rung.guardDamage);
		}
	}

	// ---- the ladder at Jaymo's Tinkering's Smithing Anvil (reached by reflection: Kingdoms runs without Powers) -------------

	/** The anvil refuses a barrel to a smith whose world tier hasn't reached its gun. */
	private static void gate() {
		if (!FabricLoader.getInstance().isModLoaded("jaymospowers")) {
			return;
		}
		try {
			Class<?> forging = Class.forName("com.jaymospowers.tinkering.smithing.Forging");
			Class<?> check = Class.forName("com.jaymospowers.tinkering.smithing.Forging$Check");
			Method id = Class.forName("com.jaymospowers.tinkering.smithing.Forging$Option").getMethod("id");
			Object proxy = Proxy.newProxyInstance(EarlyGuns.class.getClassLoader(), new Class<?>[] {check}, (self, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return switch (method.getName()) {
						case "hashCode" -> System.identityHashCode(self);
						case "equals" -> self == args[0];
						default -> "EarlyGuns.gate";
					};
				}
				// refuse(ServerPlayer smith, SmithingAnvilEntity anvil, Option option)
				ServerPlayer smith = (ServerPlayer) args[0];
				String option = (String) id.invoke(args[2]);
				for (Rung rung : Rung.values()) {
					// (class trees: a Tinkerer's eye opens the tiers early, TierGate.gearTier)
					if (option.contains("weapon/" + rung.id() + "_barrel") && !smith.isCreative() && TierGate.gearTier(smith) < rung.tier) {
						return Component.translatable("message.jaymoskingdoms.early_gun.tier." + rung.id());
					}
					// (class trees) and only a Gunsmith forges a gun's barrel
					if (option.contains("weapon/" + rung.id() + "_barrel") && !Workshop.mayForge(smith)) {
						return Component.translatable("message.jaymoskingdoms.workshop.gunsmith");
					}
				}
				return null;
			});
			@SuppressWarnings("unchecked")
			List<Object> checks = (List<Object>) forging.getField("CHECKS").get(null);
			checks.add(proxy);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
			JaymosKingdoms.LOGGER.warn("EarlyGuns: couldn't reach Jaymo's Tinkering's forging checks; the gun barrels stay open", e);
		}
	}

	// ---- the gun -----------------------------------------------------------------------------------------------------------

	/** The item: loaded with powder and ball by holding use, fired by using it loaded. */
	static final class Gun extends Item {
		private final Rung rung;

		Gun(Rung rung, Item.Properties properties) {
			super(properties);
			this.rung = rung;
		}

		/** The first of {@code what} in {@code player}'s pack (his other hand first), or empty. */
		private static ItemStack find(Player player, java.util.function.Predicate<ItemStack> what) {
			for (InteractionHand hand : InteractionHand.values()) {
				if (what.test(player.getItemInHand(hand))) {
					return player.getItemInHand(hand);
				}
			}
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (what.test(stack)) {
					return stack;
				}
			}
			return ItemStack.EMPTY;
		}

		private static ItemStack powder(Player player) {
			return find(player, stack -> stack.is(Items.GUNPOWDER));
		}

		private static ItemStack ball(Player player) {
			return find(player, stack -> stack.is(SHOT));
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return loaded(stack) ? ItemUseAnimation.NONE : ItemUseAnimation.CROSSBOW;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			// (class trees) a Gunslinger's match comes down twice as fast just after the draw
			return loaded(stack) ? Math.round(this.rung.touch * WarExtras.draw(user)) : 72000;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack gun = player.getItemInHand(hand);
			if (!loaded(gun)) {
				if ((powder(player).isEmpty() || ball(player).isEmpty()) && !player.hasInfiniteMaterials()) {
					return InteractionResult.FAIL;
				}
				player.startUsingItem(hand);
				return InteractionResult.CONSUME;
			}
			if (wet(player)) {
				if (level instanceof ServerLevel server) {
					fizzle(server, player);
				}
				player.getCooldowns().addCooldown(gun, SETTLE);
				return InteractionResult.FAIL;
			}
			if (this.rung.touch > 0) {
				// the match comes down to the touch-hole
				if (level instanceof ServerLevel) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.5F, 1.6F);
				}
				player.startUsingItem(hand);
				return InteractionResult.CONSUME;
			}
			this.shoot(level, player, gun, hand);
			return InteractionResult.SUCCESS;
		}

		private void shoot(Level level, Player player, ItemStack gun, InteractionHand hand) {
			if (level instanceof ServerLevel server) {
				gun.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
				fire(server, player, gun, this.rung, player.getLookAngle(), this.rung.damage);
				player.setXRot(player.getXRot() - this.rung.kick);
				Vec3 back = player.getLookAngle().scale(-this.rung.recoil);
				player.push(back.x, 0.05, back.z);
				player.syncVelocity = true;
				player.awardStat(Stats.ITEM_USED.get(this));
				gun.hurtAndBreak(1, player, hand);
			}
			player.getCooldowns().addCooldown(gun, SETTLE);
		}

		/** The touch held long enough: the handgonne goes off. */
		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (user instanceof Player player && loaded(stack)) {
				if (wet(player)) {
					if (level instanceof ServerLevel server) {
						fizzle(server, player);
					}
				} else {
					this.shoot(level, player, stack, player.getUsedItemHand());
				}
			}
			return stack;
		}

		@Override
		public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
			if (loaded(stack) || !(level instanceof ServerLevel)) {
				return;
			}
			int held = this.getUseDuration(stack, user) - remaining;
			if (Workshop.primed(user) && user instanceof Player && this.loadNow(stack, level, (Player) user)) {
				// (class trees) Speed Load: loaded at once, with a spin of the gun
				Workshop.spin(user);
				user.stopUsingItem();
				return;
			}
			// (quicker in the Ranger's harness: FrontierPerks)
			int load = Workshop.reload(user, FrontierPerks.reload(user, this.rung.load));
			if (held == load / 3 || held == load * 2 / 3) {
				// the powder down, then the ball rammed home
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.7F, 0.6F);
			} else if (held == load) {
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
			}
		}

		@Override
		public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
			if (!(user instanceof Player player) || loaded(stack) || this.getUseDuration(stack, user) - remaining < Workshop.reload(user, FrontierPerks.reload(user, this.rung.load))) {
				return false;
			}
			return this.loadNow(stack, level, player);
		}

		/** Powder and ball into {@code stack}, out of {@code player}'s pack; false if they haven't both. */
		private boolean loadNow(ItemStack stack, Level level, Player player) {
			if (loaded(stack)) {
				return false;
			}
			ItemStack powder = powder(player), ball = ball(player);
			if ((powder.isEmpty() || ball.isEmpty()) && !player.hasInfiniteMaterials()) {
				return false;
			}
			if (level instanceof ServerLevel) {
				// (in creative with none in the pack, a ball from nowhere)
				ItemStack one = ball.isEmpty() ? new ItemStack(Items.IRON_NUGGET) : ball.copyWithCount(1);
				powder.consume(1, player);
				ball.consume(1, player);
				stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.ofNonEmpty(List.of(one)));
				player.getCooldowns().addCooldown(stack, SETTLE);
			}
			return true;
		}
	}
}
