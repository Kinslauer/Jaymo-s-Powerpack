package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Workshop (class trees, docs/ClassTrees-Plan.md): the Marksman's tree and the bench the Marksman and the Tinkerer
 * share.
 * <ul>
 * <li><b>Practice</b>: a Marksman reloads a little faster with each level ({@link #loadRate}).</li>
 * <li><b>Steady Aim</b> (3 ranks): standing still and crouched, a shot hits harder each rank, and the aim widens (WeaponMod's
 * aim assist reaches wider, an early gun strays less).</li>
 * <li><b>Speed Load</b>: every {@link #SPEED_LOAD_KILLS} kills with a gun, the next reload is instant, with a spin of the gun
 * ({@link Casting.Gesture#SPIN}).</li>
 * <li><b>Called Shot or Quick Shot</b> (10): sneak and fire, and where the shot strikes counts (the head: a heavy hit; the
 * arms: the foe drops its weapon; the legs: it limps); or a free shot at any foe that swings at you or a friend within
 * {@link #QUICK_SHOT_REACH} blocks, once per short rest.</li>
 * <li><b>Precision</b> (top): a shot on an unaware or crippled foe is always a critical.</li>
 * <li><b>Gunsmith</b> (the bench): guns, barrels and bullets are made only by those who have it ({@link #mayMake}); a
 * Marksman without it (or anyone else) buys them of a weaponsmith ({@link #stock}).</li>
 * <li><b>Gun upgrades</b> (3 ranks): one part a rank fitted at a station, never all four: Muffled (no noise), Long Barrel
 * (further), Choke (a blunderbuss's shot held tight) and Bayonet (a blow with it).</li>
 * <li><b>Trick Shot bolts</b>: arrows turned at a station into grapple, oil, water and shrapnel bolts. Oil sets up fire (an
 * oiled thing burns longer and harder), water sets up lightning (a soaked thing takes more of it).</li>
 * <li><b>Field Kit</b>: a small repair to the gear in hand away from a station (the Marksman's class key, a Tinkerer's sneak
 * and key), on a wait.</li>
 * </ul>
 */
public final class Workshop {
	/** Steady Aim, a rank: this share harder; this many degrees more aim assist; this share less stray. Still: moved less than this for this long. */
	static final float STEADY_DAMAGE = 0.1F, STEADY_DEGREES = 1.5F, STEADY_SPREAD = 0.25F;
	static final double STILL_MOVE = 0.02;
	static final int STILL_TICKS = 10;
	/** Speed Load: every this many gun kills, the next reload is instant. */
	static final int SPEED_LOAD_KILLS = 3;
	/** Called Shot: a head shot's blow times this; the limp (ticks); the head and legs as shares of the body's height. */
	static final float HEAD_SHOT = 1.75F, HEAD_SHARE = 0.75F, LEG_SHARE = 0.35F;
	static final int LIMP_TICKS = 120;
	/** Quick Shot: how near the swing must be (blocks), the shot's blow and speed. */
	static final double QUICK_SHOT_REACH = 10.0, QUICK_SHOT_SPEED = 3.0;
	static final float QUICK_SHOT_DAMAGE = 9.0F;
	/** Precision: the critical's blow times this. */
	static final float PRECISION = 1.5F;
	/** Gun parts: Long Barrel's speed; Choke's pull of a pellet to the aim; the bayonet's blow. */
	static final double LONG_BARREL = 1.35, CHOKE = 0.6;
	static final float BAYONET = 5.0F;
	/** Trick bolts: bolts made a material; oil and water last (ticks); oil's extra burn (s) and fire damage share; lightning on the soaked; shrapnel. */
	static final int BOLTS_PER = 4, OILED_TICKS = 20 * 30, SOAKED_TICKS = 20 * 30;
	static final float OILED_BURN = 6.0F, OILED_FIRE = 1.5F, SOAKED_LIGHTNING = 1.75F, SHRAPNEL_DAMAGE = 5.0F;
	static final double SPLASH = 2.5, SHRAPNEL_RADIUS = 3.0, GRAPPLE_PULL = 1.6;
	/** Field Kit: share of the gear's full durability mended, the wait (ticks). */
	static final float KIT_MEND = 0.15F;
	static final int KIT_COOLDOWN = 20 * 30;
	/** A weaponsmith's guns and shot for the Gunsmith-less: emeralds, how many, uses. */
	static final int HANDGONNE_PRICE = 8, ARQUEBUS_PRICE = 14, FLINTLOCK_PRICE = 18, MUSKET_PRICE = 24, BULLETS_PRICE = 2, BULLETS = 8, STOCK_USES = 4;

	public static final String AIM = "mm_aim", LOAD = "mm_load", CALLED = "mm_called", QUICK = "mm_quick", PRECISE = "mm_precision",
		SMITH = "bench_smith", PARTS = "bench_upgrades", TRICK = "bench_trick", KIT = "bench_kit";
	/** The short-rest pool Quick Shot is spent from ({@link Rests}). */
	public static final String QUICK_POOL = "quick_shot";

	/** A gun's fitted parts (custom data). */
	static final String PARTS_TAG = "jaymoskingdoms_gun_parts";
	static final String MUFFLED = "muffled", LONG = "long_barrel", CHOKED = "choke", BAYONETED = "bayonet";
	/** A trick bolt's kind (custom data). */
	static final String TRICK_TAG = "jaymoskingdoms_trick";
	static final String GRAPPLE = "grapple", OIL = "oil", WATER = "water", SHRAPNEL = "shrapnel";

	private static final Map<UUID, Integer> KILLS = new HashMap<>();
	private static final Set<UUID> PRIMED = new HashSet<>();
	private static final Map<UUID, Vec3> STILL_AT = new HashMap<>();
	private static final Map<UUID, Long> STILL_SINCE = new HashMap<>();
	/** Shots fired from a crouch by a Called Shot Marksman. */
	private static final Set<Entity> CALLED_SHOTS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
	/** Trick bolts in flight: the arrow, its kind, where it was last tick. */
	private static final Map<AbstractArrow, String> TRICKS = new HashMap<>();
	private static final Map<AbstractArrow, Vec3> TRICK_AT = new HashMap<>();
	/** Oiled and soaked things: until when. */
	private static final Map<LivingEntity, Long> OILED = new java.util.WeakHashMap<>(), SOAKED = new java.util.WeakHashMap<>();
	private static final Map<UUID, Long> KIT_READY = new HashMap<>();

	private Workshop() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Workshop::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			KILLS.clear();
			PRIMED.clear();
			STILL_AT.clear();
			STILL_SINCE.clear();
			CALLED_SHOTS.clear();
			TRICKS.clear();
			TRICK_AT.clear();
			OILED.clear();
			SOAKED.clear();
			KIT_READY.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register(Workshop::loaded);
		ServerLivingEntityEvents.AFTER_DEATH.register(Workshop::killed);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Workshop::struck);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND
				|| !Tinkerers.station(level.getBlockState(hit.getBlockPos()))) {
				return InteractionResult.PASS;
			}
			return atStation(sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof Villager smith && !world.isClientSide() && hand == InteractionHand.MAIN_HAND) {
				stock(sp, smith);
			}
			return InteractionResult.PASS;
		});
	}

	// ---- whose and what ------------------------------------------------------------------------------------------------

	static boolean gun(ItemStack stack) {
		return stack.is(ClassPerks.GUNS);
	}

	private static boolean armed(Player player) {
		return gun(player.getMainHandItem()) || gun(player.getOffhandItem());
	}

	/** A shot from {@code player}'s gun or crossbow (not a blade, not a spell). */
	private static boolean shot(DamageSource source, Player player) {
		Entity direct = source.getDirectEntity();
		return direct instanceof Projectile && direct != player && (armed(player) || player.getMainHandItem().is(Items.CROSSBOW));
	}

	/** How many times as fast {@code user} reloads a gun: a Marksman's practice ({@link ClassTrees#practice}), 1 for anyone else. */
	public static double loadRate(LivingEntity user) {
		return user instanceof Player player && Classes.is(player, PlayerClass.MARKSMAN) ? ClassTrees.practice(player, PlayerClass.MARKSMAN) : 1.0;
	}

	/** An early gun's reload, {@code ticks}, as {@code user} does it ({@link #loadRate}). */
	public static int reload(LivingEntity user, int ticks) {
		return Math.max(1, (int) Math.round(ticks / loadRate(user)));
	}

	/** {@code player} has stood still (and crouched) for a moment. */
	static boolean steady(ServerPlayer player) {
		Long since = STILL_SINCE.get(player.getUUID());
		return player.isShiftKeyDown() && since != null && player.level().getGameTime() - since >= STILL_TICKS;
	}

	/** Steady Aim's extra aim assist, in degrees, for {@code player} now. */
	public static double aimDegrees(ServerPlayer player) {
		return steady(player) ? STEADY_DEGREES * ClassTrees.power(player, AIM) : 0.0;
	}

	/** How much of an early gun's stray is left for {@code shooter} (1: all of it). */
	static float spread(LivingEntity shooter) {
		return shooter instanceof ServerPlayer sp && steady(sp) ? Math.max(0.1F, 1.0F - STEADY_SPREAD * ClassTrees.power(sp, AIM)) : 1.0F;
	}

	// ---- the parts on a gun ----------------------------------------------------------------------------------------

	static List<String> parts(ItemStack gun) {
		CustomData data = gun.get(DataComponents.CUSTOM_DATA);
		List<String> out = new ArrayList<>();
		if (data != null) {
			data.copyTag().getList(PARTS_TAG).ifPresent(list -> list.forEach(t -> t.asString().ifPresent(out::add)));
		}
		return out;
	}

	static boolean has(ItemStack gun, String part) {
		return gun(gun) && parts(gun).contains(part);
	}

	/** A Muffled gun's shot makes no noise for anything to hear ({@link Hearing#fired}). */
	public static boolean muffled(ItemStack gun) {
		return has(gun, MUFFLED);
	}

	/** The part {@code material} (in the off hand) fits, or null. */
	private static @Nullable String partFor(ItemStack material, ItemStack gun) {
		if (material.is(ItemTags.WOOL)) {
			return MUFFLED;
		}
		if (material.is(Items.IRON_INGOT)) {
			return LONG;
		}
		if (material.is(Items.COPPER_INGOT) && BuiltInRegistries.ITEM.getKey(gun.getItem()).getPath().contains("blunderbuss")) {
			return CHOKED;
		}
		return material.is(ItemTags.SWORDS) ? BAYONETED : null;
	}

	/** The trick bolt {@code material} (in the off hand) makes, or null. */
	private static @Nullable String trickFor(ItemStack material) {
		if (material.is(Items.TRIPWIRE_HOOK)) {
			return GRAPPLE;
		}
		if (material.is(Items.COAL) || material.is(Items.CHARCOAL)) {
			return OIL;
		}
		if (material.is(Items.POTION) && material.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
			return WATER;
		}
		return material.is(Items.GUNPOWDER) ? SHRAPNEL : null;
	}

	/** Sneak and use at a station: a part onto a gun, or arrows into trick bolts. True when it was one of those. */
	private static boolean atStation(ServerPlayer player) {
		ItemStack held = player.getMainHandItem(), off = player.getOffhandItem();
		if (gun(held) && !off.isEmpty()) {
			String part = partFor(off, held);
			if (part == null || !ClassTrees.check(player, PARTS)) {
				return part != null;
			}
			List<String> parts = parts(held);
			int most = Math.min(3, ClassTrees.has(player, PARTS));
			if (parts.contains(part)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.part_already"));
				return true;
			}
			if (parts.size() >= most) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.part_most", most));
				return true;
			}
			parts.add(part);
			ListTag list = new ListTag();
			parts.forEach(p -> list.add(StringTag.valueOf(p)));
			CustomData.update(DataComponents.CUSTOM_DATA, held, tag -> tag.put(PARTS_TAG, list));
			List<Component> lore = new ArrayList<>();
			parts.forEach(p -> lore.add(Component.translatable("item.jaymoskingdoms.gun_part." + p).withStyle(ChatFormatting.GRAY)));
			held.set(DataComponents.LORE, new ItemLore(lore));
			if (!player.isCreative()) {
				off.shrink(1);
			}
			done(player, Component.translatable("message.jaymoskingdoms.workshop.part_fitted", Component.translatable("item.jaymoskingdoms.gun_part." + part)));
			return true;
		}
		if (held.is(Items.ARROW) && !off.isEmpty()) {
			String trick = trickFor(off);
			if (trick == null || !ClassTrees.check(player, TRICK)) {
				return trick != null;
			}
			int n = Math.min(BOLTS_PER, held.getCount());
			ItemStack bolts = new ItemStack(Items.ARROW, n);
			CustomData.update(DataComponents.CUSTOM_DATA, bolts, tag -> tag.putString(TRICK_TAG, trick));
			bolts.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.trick_bolt." + trick).withStyle(ChatFormatting.RESET));
			bolts.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.trick_bolt." + trick + ".desc").withStyle(ChatFormatting.GRAY))));
			if (!player.isCreative()) {
				held.shrink(n);
				off.shrink(1);
				if (off.isEmpty() && trick.equals(WATER)) {
					player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GLASS_BOTTLE));
				}
			}
			if (!player.getInventory().add(bolts)) {
				player.drop(bolts, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			done(player, Component.translatable("message.jaymoskingdoms.workshop.bolts", n, Component.translatable("item.jaymoskingdoms.trick_bolt." + trick)));
			return true;
		}
		return false;
	}

	private static void done(ServerPlayer player, Component line) {
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
		level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY(1.0), player.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		player.sendOverlayMessage(line);
		ClassTrees.giveEvery(player, PlayerClass.TINKERER, ClassTrees.XP_MADE, "made", 10L);
		ClassTrees.giveEvery(player, PlayerClass.MARKSMAN, ClassTrees.XP_MADE, "made", 10L);
	}

	// ---- Gunsmith ------------------------------------------------------------------------------------------------------

	/** Whether {@code result} is a gun, a barrel or a bullet: made only with Gunsmith. */
	static boolean gunsmithing(ItemStack result) {
		Identifier id = BuiltInRegistries.ITEM.getKey(result.getItem());
		return gun(result) || id.toString().equals("weaponmod:bullet") || id.getPath().endsWith("_barrel") && id.getPath().contains("gun");
	}

	/** Whether {@code player} may take {@code result} out of a grid: anything but a gun or its shot, or a Gunsmith. */
	public static boolean mayMake(Player player, ItemStack result) {
		if (!gunsmithing(result) || player.isCreative() || ClassTrees.has(player, SMITH) > 0) {
			return true;
		}
		if (player instanceof ServerPlayer sp) {
			ClassTrees.check(sp, SMITH);
		}
		return false;
	}

	/** Whether {@code smith} may forge a gun's barrel: a Gunsmith. */
	static boolean mayForge(ServerPlayer smith) {
		return smith.isCreative() || ClassTrees.has(smith, SMITH) > 0;
	}

	/**
	 * A weaponsmith keeps guns and shot for whoever can't make them: a Marksman without Gunsmith, and anyone else, since only a
	 * Gunsmith makes them (as far as their tier allows, {@link TierGate}).
	 */
	private static void stock(ServerPlayer player, Villager smith) {
		if (ClassTrees.has(player, SMITH) > 0 || smith.isBaby()
			|| !smith.getVillagerData().profession().is(VillagerProfession.WEAPONSMITH)) {
			return;
		}
		MerchantOffers offers = smith.getOffers();
		List<ItemStack> wares = new ArrayList<>();
		wares.add(new ItemStack(EarlyGuns.Rung.HANDGONNE.item));
		wares.add(new ItemStack(EarlyGuns.Rung.ARQUEBUS.item));
		BuiltInRegistries.ITEM.getOptional(Identifier.parse("weaponmod:flintlock")).ifPresent(i -> wares.add(new ItemStack(i)));
		BuiltInRegistries.ITEM.getOptional(Identifier.parse("weaponmod:musket")).ifPresent(i -> wares.add(new ItemStack(i)));
		boolean changed = false;
		for (ItemStack ware : wares) {
			if (offers.stream().noneMatch(o -> o.getResult().is(ware.getItem()))) {
				String path = BuiltInRegistries.ITEM.getKey(ware.getItem()).getPath();
				int price = switch (path) {
					case "handgonne" -> HANDGONNE_PRICE;
					case "arquebus" -> ARQUEBUS_PRICE;
					case "flintlock" -> FLINTLOCK_PRICE;
					default -> MUSKET_PRICE;
				};
				offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), ware, STOCK_USES, 5, 0.05F));
				changed = true;
			}
		}
		Item bullet = BuiltInRegistries.ITEM.getOptional(Identifier.parse("weaponmod:bullet")).orElse(Items.IRON_NUGGET);
		if (offers.stream().noneMatch(o -> o.getResult().is(bullet))) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, BULLETS_PRICE), new ItemStack(bullet, BULLETS), STOCK_USES * 4, 2, 0.05F));
			changed = true;
		}
		if (changed) {
			// what is past the Marksman's tier is set aside again
			TierGate.filter(player, smith);
		}
	}

	// ---- shots ---------------------------------------------------------------------------------------------------------

	private static void loaded(Entity entity, ServerLevel level) {
		if (entity instanceof AbstractArrow arrow) {
			CustomData data = arrow.getPickupItemStackOrigin().get(DataComponents.CUSTOM_DATA);
			String trick = data == null ? "" : data.copyTag().getStringOr(TRICK_TAG, "");
			if (!trick.isEmpty()) {
				TRICKS.put(arrow, trick);
				TRICK_AT.put(arrow, arrow.position());
			}
		}
		if (!(entity instanceof Projectile shot) || !(shot.getOwner() instanceof ServerPlayer shooter) || !armed(shooter)) {
			return;
		}
		ItemStack gun = gun(shooter.getMainHandItem()) ? shooter.getMainHandItem() : shooter.getOffhandItem();
		Vec3 v = shot.getDeltaMovement();
		if (has(gun, LONG)) {
			v = v.scale(LONG_BARREL);
		}
		if (has(gun, CHOKED)) {
			// the pellets held to the line of the barrel
			Vec3 aim = shooter.getLookAngle().scale(v.length());
			v = v.lerp(aim, CHOKE);
		}
		if (v != shot.getDeltaMovement()) {
			shot.setDeltaMovement(v);
		}
		if (shooter.isShiftKeyDown() && ClassTrees.has(shooter, CALLED) > 0) {
			CALLED_SHOTS.add(shot);
		}
	}

	/** What the Workshop does to a blow ({@link TreePerks#dealt}). */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (!(source.getEntity() instanceof ServerPlayer player) || target == player || !(target.level() instanceof ServerLevel level)) {
			return amount;
		}
		Entity direct = source.getDirectEntity();
		if (direct == player && has(player.getMainHandItem(), BAYONETED)) {
			// a bayonet's blow
			amount += BAYONET;
		}
		if (OILED.getOrDefault(target, 0L) > level.getGameTime() && source.is(DamageTypeTags.IS_FIRE)) {
			amount *= OILED_FIRE;
		}
		if (SOAKED.getOrDefault(target, 0L) > level.getGameTime() && (source.is(DamageTypeTags.IS_LIGHTNING) || lightningSorcery(source, player))) {
			amount *= SOAKED_LIGHTNING;
			level.sendParticles(Fx.CRACKLE, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
		}
		if (!shot(source, player)) {
			return amount;
		}
		if (steady(player) && ClassTrees.has(player, AIM) > 0) {
			amount *= 1.0F + STEADY_DAMAGE * ClassTrees.power(player, AIM);
		}
		if (direct != null && CALLED_SHOTS.remove(direct)) {
			amount = called(level, player, target, direct, amount);
		}
		if (ClassTrees.has(player, PRECISE) > 0 && (target instanceof Mob && Senses.unaware(target, player) || HiddenBlade.crippled(target))) {
			amount *= PRECISION;
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 16, 0.3, 0.4, 0.3, 0.3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.1F);
		}
		return amount;
	}

	/** A lightning Sorcerer's magic. */
	private static boolean lightningSorcery(DamageSource source, ServerPlayer player) {
		return source.is(DamageTypeTags.WITCH_RESISTANT_TO) && "lightning".equals(Classes.optionOf(player, PlayerClass.SORCERER));
	}

	/** A Called Shot: where on the body it struck. */
	private static float called(ServerLevel level, ServerPlayer player, LivingEntity target, Entity shot, float amount) {
		double h = (shot.getY() - target.getY()) / Math.max(0.1, target.getBbHeight());
		if (h >= HEAD_SHARE) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.called.head"));
			return amount * HEAD_SHOT;
		}
		if (h <= LEG_SHARE) {
			HiddenBlade.cripple(target, LIMP_TICKS);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LIMP_TICKS, 2), player);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.called.leg"));
			return amount;
		}
		if (target instanceof Mob mob && !mob.getMainHandItem().isEmpty()) {
			ItemStack dropped = mob.getMainHandItem().copy();
			mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			mob.spawnAtLocation(level, dropped);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.called.arm"));
		}
		return amount;
	}

	/** A blow landed: Quick Shot answers a swing at the Marksman or a friend; a trick bolt bursts on what it hit. */
	private static void struck(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (source.getDirectEntity() instanceof AbstractArrow arrow && TRICKS.containsKey(arrow)) {
			String trick = TRICKS.remove(arrow);
			TRICK_AT.remove(arrow);
			if (victim.level() instanceof ServerLevel level) {
				trick(level, arrow, trick, victim.position().add(0.0, victim.getBbHeight() * 0.5, 0.0), victim);
			}
		}
		if (!(source.getEntity() instanceof Mob attacker) || source.getDirectEntity() != attacker || !(victim.level() instanceof ServerLevel level)) {
			return;
		}
		for (ServerPlayer marksman : level.getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(QUICK_SHOT_REACH),
			p -> p.isAlive() && ClassTrees.has(p, QUICK) > 0 && loadedGun(p) != null)) {
			if ((marksman == victim || victim instanceof Player || ClassForks.friend(marksman, victim)) && marksman.hasLineOfSight(attacker)
				&& Rests.spend(marksman, QUICK_POOL, 1, 1)) {
				// the gun's own load goes
				unload(loadedGun(marksman));
				Vec3 eye = marksman.getEyePosition().add(0.0, -0.1, 0.0);
				Vec3 dir = attacker.getBoundingBox().getCenter().subtract(eye).normalize();
				GunBall.shot(level, marksman, eye, dir, QUICK_SHOT_SPEED, QUICK_SHOT_DAMAGE);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, eye.x + dir.x, eye.y + dir.y, eye.z + dir.z, 6, 0.1, 0.1, 0.1, 0.02);
				level.playSound(null, marksman.getX(), marksman.getY(), marksman.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.4F);
				marksman.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.quick_shot"));
				return;
			}
		}
	}

	/** The loaded gun in {@code player}'s hands, if any (an early gun, or WeaponMod's). */
	private static @Nullable ItemStack loadedGun(Player player) {
		for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
			if (gun(stack) && (EarlyGuns.loaded(stack) || Javelins.WEAPONMOD_LOADED && WeaponModGuns.reloaded(stack))) {
				return stack;
			}
		}
		return null;
	}

	private static void unload(@Nullable ItemStack gun) {
		if (gun == null) {
			return;
		}
		if (EarlyGuns.loaded(gun)) {
			gun.remove(DataComponents.CHARGED_PROJECTILES);
		} else if (Javelins.WEAPONMOD_LOADED) {
			WeaponModGuns.unload(gun);
		}
	}

	/** A kill: a Marksman's gun kills count to Speed Load. */
	private static void killed(LivingEntity dead, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer player) || !(source.getDirectEntity() instanceof Projectile) || !armed(player)
			|| ClassTrees.has(player, LOAD) == 0) {
			return;
		}
		int n = KILLS.merge(player.getUUID(), 1, Integer::sum);
		if (n >= SPEED_LOAD_KILLS) {
			KILLS.remove(player.getUUID());
			if (PRIMED.add(player.getUUID())) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.speed_load"));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 0.6F, 1.6F);
			}
		}
	}

	/** Whether {@code user}'s next reload is instant (Speed Load). */
	public static boolean primed(LivingEntity user) {
		return user instanceof ServerPlayer && PRIMED.contains(user.getUUID());
	}

	/** Speed Load spent on a reload: the gun spins in the hand. */
	public static void spin(LivingEntity user) {
		if (PRIMED.remove(user.getUUID()) && user.level() instanceof ServerLevel level) {
			Casting.gesture(user, Casting.Gesture.SPIN, 0xC0A060);
			level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
		}
	}

	// ---- trick bolts ---------------------------------------------------------------------------------------------------

	private static void trick(ServerLevel level, AbstractArrow arrow, String trick, Vec3 at, @Nullable LivingEntity hit) {
		Entity owner = arrow.getOwner();
		switch (trick) {
			case GRAPPLE -> {
				if (owner instanceof ServerPlayer shooter && shooter.level() == level) {
					Vec3 pull = at.subtract(shooter.position()).normalize().scale(GRAPPLE_PULL);
					shooter.setDeltaMovement(pull.x, Math.max(0.4, pull.y + 0.3), pull.z);
					shooter.syncVelocity = true;
					shooter.fallDistance = 0.0;
					Fx.stroke(level, shooter.getEyePosition(), at, 0.05F, 0x8A6A3C);
				}
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
			}
			case OIL -> oil(level, at);
			case WATER -> soak(level, at);
			case SHRAPNEL -> {
				level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7F, 1.6F);
				if (owner instanceof ServerPlayer shooter) {
					for (LivingEntity foe : CombatArts.foes(level, shooter, new AABB(at, at).inflate(SHRAPNEL_RADIUS))) {
						if (foe != hit) {
							foe.hurtServer(level, level.damageSources().explosion(arrow, shooter), SHRAPNEL_DAMAGE);
						}
					}
				}
			}
			default -> {
			}
		}
	}

	/** Oil over a splash round {@code at}: who it catches burns longer (and a burning one flares now). */
	static void oil(ServerLevel level, Vec3 at) {
		long until = level.getGameTime() + OILED_TICKS;
		Fx.pool(level, at, (float) SPLASH, 0x2A2418, 60);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH))) {
			OILED.put(e, until);
			if (e.isOnFire()) {
				e.igniteForSeconds(OILED_BURN);
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	/** Water over a splash round {@code at}: it puts out who it catches and leaves them soaked for lightning. */
	static void soak(ServerLevel level, Vec3 at) {
		long until = level.getGameTime() + SOAKED_TICKS;
		Fx.pool(level, at, (float) SPLASH, 0x3A7BD5, 40);
		level.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 30, SPLASH * 0.4, 0.3, SPLASH * 0.4, 0.1);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(SPLASH))) {
			SOAKED.put(e, until);
			e.clearFire();
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	// ---- Field Kit -----------------------------------------------------------------------------------------------------

	/** A small repair to the gear in hand, away from a station (Field Kit). True when the key did that (or tried). */
	static boolean fieldKit(ServerPlayer player) {
		ItemStack gear = player.getMainHandItem();
		if (!gear.isDamageableItem() || gear.getDamageValue() == 0) {
			return false;
		}
		if (!ClassTrees.check(player, KIT)) {
			return true;
		}
		long now = player.level().getGameTime();
		if (KIT_READY.getOrDefault(player.getUUID(), 0L) > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.kit_wait", (KIT_READY.get(player.getUUID()) - now) / 20 + 1));
			return true;
		}
		Repairable repairable = gear.get(DataComponents.REPAIRABLE);
		ItemStack part = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (!s.isEmpty() && s != gear && (repairable != null ? repairable.isValidRepairItem(s) : s.is(Items.IRON_NUGGET))) {
				part = s;
				break;
			}
		}
		if (part.isEmpty() && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.kit_parts"));
			return true;
		}
		if (!player.isCreative()) {
			part.shrink(1);
		}
		gear.setDamageValue(Math.max(0, gear.getDamageValue() - Math.max(1, Math.round(gear.getMaxDamage() * KIT_MEND))));
		KIT_READY.put(player.getUUID(), now + KIT_COOLDOWN);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 0.6F, 1.5F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.kit_done", gear.getHoverName()));
		return true;
	}

	/** The Marksman's class key: the Field Kit. */
	static void key(ServerPlayer player, boolean sneak) {
		if (!fieldKit(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.workshop.kit_nothing"));
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (ClassTrees.has(player, AIM) == 0) {
				continue;
			}
			Vec3 at = player.position(), was = STILL_AT.put(player.getUUID(), at);
			if (was == null || was.distanceToSqr(at) > STILL_MOVE * STILL_MOVE) {
				STILL_SINCE.put(player.getUUID(), player.level().getGameTime());
			}
		}
		if (server.getTickCount() % 10 == 0 && !OILED.isEmpty()) {
			// an oiled thing that catches fire burns long and hard
			for (Map.Entry<LivingEntity, Long> e : OILED.entrySet()) {
				LivingEntity oiled = e.getKey();
				if (e.getValue() > oiled.level().getGameTime() && oiled.isOnFire() && oiled.getRemainingFireTicks() < OILED_BURN * 20) {
					oiled.igniteForSeconds(OILED_BURN);
				}
			}
		}
		if (TRICKS.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<AbstractArrow, String>> it = TRICKS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<AbstractArrow, String> e = it.next();
			AbstractArrow arrow = e.getKey();
			Vec3 was = TRICK_AT.get(arrow);
			if (arrow.isRemoved()) {
				it.remove();
				TRICK_AT.remove(arrow);
			} else if (was != null && arrow.tickCount > 2 && was.distanceToSqr(arrow.position()) < 1.0E-4 && arrow.level() instanceof ServerLevel level) {
				// stuck in the ground or a wall: it bursts there
				it.remove();
				TRICK_AT.remove(arrow);
				trick(level, arrow, e.getValue(), arrow.position(), null);
				// spent: it isn't picked up to be shot again
				arrow.discard();
			} else {
				TRICK_AT.put(arrow, arrow.position());
			}
		}
	}
}
