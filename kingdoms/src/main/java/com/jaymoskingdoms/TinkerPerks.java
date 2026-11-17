package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Tinkerer's tree (class trees, docs/ClassTrees-Plan.md), besides the bench they share with the Marksman ({@link Workshop}).
 * <ul>
 * <li><b>A craftsman's eye</b> (by level, no rank): the world tiers' gear opens early for a Tinkerer alone, Borderlands at
 * {@link #EYE_LEVELS}[0] and so on to Legend ({@link TierGate#gearTier}): what a merchant offers them, the grade their smiths
 * forge to, the gun barrels their anvil takes. Monsters stay at the real world tier.</li>
 * <li><b>Practice</b>: a Tinkerer studies what they watch faster with their level (the notebook's study, {@code MimicStudy}).</li>
 * <li><b>Spare Hands</b> (2 ranks): one more infusion kept per rank, and three more kinds ({@link Tinkerers}): Muffled boots
 * (quieter footfalls, so the foes about notice from less far: wool), a Warded chest (the wearer freezes and burns half as
 * fast: magma cream) and a Keen blade (it slowly mends itself: flint), picked by the material held in the off hand at the
 * station.</li>
 * <li><b>Contraption</b> (5; rank 2 is Educated Guess): sneak and use copper nuggets with gunpowder in the pack and a scrap
 * bundle is built in moments; thrown, it bursts with a flash, a net, a healing mist, water, oil or a bang, or is a dud. With
 * Educated Guess a dud comes back to hand.</li>
 * <li><b>Dismantle</b> (10): sneak and use an empty hand on a trap (a tripwire, its hook, a pressure plate) to take it apart,
 * or on a locked chest to open the lock; golems and clockwork foes take far more from a Tinkerer's blows.</li>
 * <li><b>Iron Pup</b> (12), then <b>Iron Defender</b> or <b>Iron Hound</b> (18): a clockwork dog built at a station (sneak and
 * use it empty-handed with the parts in the pack): it follows, bites and fetches what lies about to you. The Defender stays
 * close, takes more and patches you up; the Hound runs faster, bites harder and scouts the foes about.</li>
 * <li><b>Imitation</b> (top, 22): an ability watched once is studied, no notebook time.</li>
 * </ul>
 */
public final class TinkerPerks {
	/** The craftsman's eye: the Tinkerer levels that open Borderlands, Wildlands, Dread and Legend. */
	static final int[] EYE_LEVELS = {5, 10, 15, 20};
	/** Spare Hands' new infusions: how many of the off hand's material each takes; Muffled's footfall; Warded's slowing of
	 * frost and fire (ticks taken off, every 4 ticks); Keen's mending (a point every so many ticks). */
	static final int NEW_INFUSION_COST = 2;
	static final double MUFFLED_FOOTFALL = 0.5;
	static final int WARDED_THAW = 2, WARDED_QUENCH = 4, KEEN_MEND_EVERY = 100;
	/** Contraption: what it takes (copper nuggets, gunpowder), the wait between builds, the bursts' size and strength. */
	static final int CONTRAPTION_NUGGETS = 4, CONTRAPTION_COOLDOWN = 60, FLASH_TICKS = 60, NET_TICKS = 100, MIST_TICKS = 100;
	static final double BURST = 3.0;
	static final float BANG = 1.5F;
	/** Dismantle: golems and clockwork foes take a Tinkerer's blows this many times over. */
	static final float DISMANTLE = 1.75F;
	/** The Iron Pup: what it takes; its gains; how far it fetches; the Defender's patching and the Hound's scouting. */
	static final int PUP_IRON = 6, PUP_REDSTONE = 2;
	static final double FETCH_REACH = 10.0, SCOUT_REACH = 24.0;
	static final double DEFENDER_ARMOR = 6.0, HOUND_SPEED = 0.1, HOUND_BITE = 3.0;
	static final float PATCH = 2.0F;
	static final int PATCH_EVERY = 120, SCOUT_EVERY = 200, SCOUT_TICKS = 100;
	static final int PUP_COLOUR = 0xB87333;

	public static final String HANDS = "tink_hands", CONTRAPTION = "tink_contraption", DISMANTLE_KEY = "tink_dismantle", PUP = "tink_pup",
		DEFENDER = "tink_defender", HOUND = "tink_hound", IMITATION = "tink_imitation";
	/** The new infusions. */
	static final String MUFFLED = "muffled", WARDED_KIND = "warded", KEEN_KIND = "keen";
	static final String CONTRAPTION_TAG = "jaymoskingdoms_contraption", PUP_TAG = "jaymoskingdoms_iron_pup";

	/** A Tinkerer's Iron Pup. Saved, kept through death. */
	static final AttachmentType<UUID> IRON_PUP = AttachmentRegistry.create(JaymosKingdoms.id("iron_pup"), b -> b.persistent(UUIDUtil.CODEC).copyOnDeath());

	/** Contraptions in flight, and where they were last tick. */
	private static final Map<Snowball, Vec3> THROWN = new HashMap<>();
	/** A pup's fetched item, by pup. */
	private static final Map<UUID, ItemEntity> FETCHING = new HashMap<>();

	private TinkerPerks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(TinkerPerks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			THROWN.clear();
			FETCHING.clear();
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Snowball ball && contraption(ball.getItem())) {
				THROWN.put(ball, ball.position());
			}
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp && sp.isShiftKeyDown() && player.getItemInHand(hand).is(Items.COPPER_NUGGET)
				&& ClassTrees.has(sp, CONTRAPTION) > 0) {
				build(sp, player.getItemInHand(hand));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND
				|| !sp.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (Tinkerers.station(level.getBlockState(pos)) && ClassTrees.has(sp, PUP) > 0) {
				buildPup(sp);
				return InteractionResult.SUCCESS;
			}
			return dismantleTrap(sp, (ServerLevel) level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			// clockwork eats nothing, and makes no puppies
			if (entity instanceof Wolf wolf && pup(wolf) && wolf.isFood(player.getItemInHand(hand))) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	// ---- the craftsman's eye -------------------------------------------------------------------------------------------

	/** The world tier (an ordinal) a Tinkerer's eye has opened for {@code player}'s gear: 0 for anyone else. */
	static int eye(Player player) {
		if (!Classes.is(player, PlayerClass.TINKERER)) {
			return 0;
		}
		int level = ClassTrees.level(player, PlayerClass.TINKERER), tier = 0;
		for (int need : EYE_LEVELS) {
			if (level >= need) {
				tier++;
			}
		}
		return tier;
	}

	// ---- infusions -----------------------------------------------------------------------------------------------------

	/** Spare Hands' infusions: the kind {@code stack} takes with the off hand's material, or "" for none of them. */
	static String spareKind(Player player, ItemStack stack) {
		if (ClassTrees.has(player, HANDS) == 0) {
			return "";
		}
		ItemStack off = player.getOffhandItem();
		if (stack.is(net.minecraft.tags.ItemTags.FOOT_ARMOR) && off.is(net.minecraft.tags.ItemTags.WOOL)) {
			return MUFFLED;
		}
		if (stack.is(net.minecraft.tags.ItemTags.CHEST_ARMOR) && off.is(Items.MAGMA_CREAM)) {
			return WARDED_KIND;
		}
		return stack.is(net.minecraft.tags.ItemTags.SWORDS) && off.is(Items.FLINT) ? KEEN_KIND : "";
	}

	/** One of Spare Hands' kinds (their material comes from the off hand). */
	static boolean spare(String kind) {
		return kind.equals(MUFFLED) || kind.equals(WARDED_KIND) || kind.equals(KEEN_KIND);
	}

	/** Warded and Keen at work, every 4 ticks: a warded wearer freezes and burns half as fast; keen blades mend a little. */
	private static void infusions(ServerPlayer player, long now) {
		if (Tinkerers.infusion(player.getItemBySlot(EquipmentSlot.CHEST)).equals(WARDED_KIND)) {
			if (player.getTicksFrozen() > 0 && (player.isInPowderSnow || player.wasInPowderSnow)) {
				player.setTicksFrozen(Math.max(0, player.getTicksFrozen() - WARDED_THAW));
			}
			if (player.getRemainingFireTicks() > 0) {
				player.setRemainingFireTicks(Math.max(0, player.getRemainingFireTicks() - WARDED_QUENCH));
			}
		}
		if (now % KEEN_MEND_EVERY == 0) {
			mend(player.getMainHandItem());
			mend(player.getOffhandItem());
			for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
				mend(s);
			}
		}
	}

	private static void mend(ItemStack stack) {
		if (stack.isDamaged() && Tinkerers.infusion(stack).equals(KEEN_KIND)) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}

	/** How far {@code player}'s footfalls carry, as a share: Muffled boots halve it. */
	public static double footfall(Player player) {
		return Tinkerers.infusion(player.getItemBySlot(EquipmentSlot.FEET)).equals(MUFFLED) ? MUFFLED_FOOTFALL : 1.0;
	}

	// ---- the notebook ------------------------------------------------------------------------------------------------

	/** A Tinkerer's study ({@code MimicStudy}): faster with their level, and whole at once with Imitation. */
	public static int study(ServerPlayer player, int ticks) {
		if (!Classes.is(player, PlayerClass.TINKERER)) {
			return ticks;
		}
		if (ClassTrees.has(player, IMITATION) > 0) {
			return com.jaymoskingdoms.tinker.MimicStudy.STUDY_TICKS;
		}
		return Math.round(ticks * ClassTrees.practice(player, PlayerClass.TINKERER));
	}

	// ---- blows ---------------------------------------------------------------------------------------------------------

	/** What the Tinkerer's tree does to a blow ({@link TreePerks#dealt}): Dismantle on golems and clockwork. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		Entity by = source.getEntity();
		if (by instanceof ServerPlayer player && player != target && clockwork(target) && ClassTrees.has(player, DISMANTLE_KEY) > 0) {
			amount *= 1.0F + (DISMANTLE - 1.0F) * Math.min(1.0F, ClassTrees.power(player, DISMANTLE_KEY));
			if (target.level() instanceof ServerLevel level) {
				level.sendParticles(ParticleTypes.WAX_OFF, target.getX(), target.getY(0.6), target.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
			}
		}
		return amount;
	}

	/** A golem or a clockwork thing. */
	private static boolean clockwork(LivingEntity e) {
		return e instanceof AbstractGolem || BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().contains("clockwork");
	}

	// ---- Dismantle -----------------------------------------------------------------------------------------------------

	private static boolean dismantleTrap(ServerPlayer player, ServerLevel level, BlockPos pos) {
		Block block = level.getBlockState(pos).getBlock();
		boolean trap = block instanceof TripWireBlock || block instanceof TripWireHookBlock || block instanceof BasePressurePlateBlock
			|| block instanceof com.jaymoskingdoms.creatures.TrapCoverBlock;
		if (!trap || ClassTrees.has(player, DISMANTLE_KEY) == 0) {
			return false;
		}
		level.destroyBlock(pos, true, player);
		level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 0.8F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinker.dismantled"));
		return true;
	}

	/** From the lockpicking: a Tinkerer with Dismantle, sneaking empty-handed at a locked chest, takes the lock apart. */
	static boolean dismantleLock(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty() || ClassTrees.has(player, DISMANTLE_KEY) == 0) {
			return false;
		}
		return Lockpicking.pickBySpirit(level, pos, player, Integer.MAX_VALUE / 4);
	}

	// ---- Contraption ---------------------------------------------------------------------------------------------------

	static boolean contraption(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(CONTRAPTION_TAG, false);
	}

	private static ItemStack contraptionStack() {
		ItemStack out = new ItemStack(Items.SNOWBALL);
		CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> tag.putBoolean(CONTRAPTION_TAG, true));
		out.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.contraption").withStyle(net.minecraft.ChatFormatting.RESET));
		// a bundle of scrap to look at, in hand and in flight
		out.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("bundle"));
		out.set(DataComponents.MAX_STACK_SIZE, 4);
		return out;
	}

	private static void build(ServerPlayer player, ItemStack nuggets) {
		if (player.getCooldowns().isOnCooldown(nuggets)) {
			return;
		}
		ItemStack powder = ItemStack.EMPTY;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(Items.GUNPOWDER)) {
				powder = s;
				break;
			}
		}
		if (!player.isCreative() && (nuggets.getCount() < CONTRAPTION_NUGGETS || powder.isEmpty())) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinker.contraption_parts", CONTRAPTION_NUGGETS));
			return;
		}
		if (!player.isCreative()) {
			nuggets.shrink(CONTRAPTION_NUGGETS);
			powder.shrink(1);
		}
		ItemStack made = contraptionStack();
		if (!player.getInventory().add(made)) {
			player.drop(made, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		player.getCooldowns().addCooldown(new ItemStack(Items.COPPER_NUGGET), CONTRAPTION_COOLDOWN);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
		ClassTrees.giveEvery(player, PlayerClass.TINKERER, ClassTrees.XP_MADE, "made", 10L);
	}

	/** A contraption bursting at {@code at}: one of its seven ends, at random. */
	private static void burst(ServerLevel level, @Nullable Entity owner, Vec3 at) {
		int roll = level.getRandom().nextInt(7);
		ServerPlayer thrower = owner instanceof ServerPlayer sp ? sp : null;
		AABB round = new AABB(at, at).inflate(BURST);
		switch (roll) {
			case 0 -> {
				// a flash: the foes about are blinded
				Fx.blast(level, at, (float) BURST, 0xFFF6E0);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0F, 1.6F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, round, e -> e instanceof Enemy || e instanceof Mob m && m.getTarget() == thrower)) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLASH_TICKS, 0), thrower);
				}
			}
			case 1 -> {
				// a net: the foes about are tangled
				Fx.pool(level, at, (float) BURST, 0xD8D0B8, NET_TICKS);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, round, e -> e instanceof Enemy || e instanceof Mob m && m.getTarget() == thrower)) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, NET_TICKS, 4), thrower);
				}
			}
			case 2 -> {
				// a healing mist: the friends about mend
				level.sendParticles(Fx.miasma(0xF2A0B8, 1.4F), at.x, at.y + 0.5, at.z, 30, BURST * 0.4, 0.4, BURST * 0.4, 0.01);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0F, 1.2F);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, round, e -> Restoration.friend(thrower == null ? e : thrower, e))) {
					e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, MIST_TICKS, 1), thrower);
				}
			}
			case 3 -> Workshop.soak(level, at);
			case 4 -> Workshop.oil(level, at);
			case 5 -> level.explode(owner, at.x, at.y, at.z, BANG, Level.ExplosionInteraction.NONE);
			default -> {
				// a dud: a fizz, and nothing
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.02);
				level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
				if (thrower != null && ClassTrees.has(thrower, CONTRAPTION) >= 2) {
					// Educated Guess: it comes back to hand, to be thrown again
					ItemStack back = contraptionStack();
					if (!thrower.getInventory().add(back)) {
						thrower.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
					}
					thrower.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinker.educated_guess"));
				}
			}
		}
	}

	// ---- the Iron Pup --------------------------------------------------------------------------------------------------

	static boolean pup(Entity e) {
		return e.entityTags().contains(PUP_TAG);
	}

	private static @Nullable Wolf pupOf(ServerPlayer player) {
		UUID id = player.getAttached(IRON_PUP);
		return id != null && player.level().getEntity(id) instanceof Wolf wolf && wolf.isAlive() && pup(wolf) ? wolf : null;
	}

	private static void buildPup(ServerPlayer player) {
		if (pupOf(player) != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinker.pup_have"));
			return;
		}
		if (!player.isCreative() && (player.getInventory().countItem(Items.IRON_INGOT) < PUP_IRON || player.getInventory().countItem(Items.REDSTONE) < PUP_REDSTONE
			|| player.getInventory().countItem(Items.CLOCK) < 1)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinker.pup_parts", PUP_IRON, PUP_REDSTONE));
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (wolf == null) {
			return;
		}
		if (!player.isCreative()) {
			take(player, Items.IRON_INGOT, PUP_IRON);
			take(player, Items.REDSTONE, PUP_REDSTONE);
			take(player, Items.CLOCK, 1);
		}
		Vec3 at = player.position().add(player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize());
		wolf.snapTo(at.x, player.getY(), at.z, player.getYRot(), 0.0F);
		wolf.tame(player);
		wolf.addTag(PUP_TAG);
		wolf.setCustomName(Component.translatable("entity.jaymoskingdoms.iron_pup"));
		// its plating: copper-coloured wolf armour over the works
		ItemStack plate = new ItemStack(Items.WOLF_ARMOR);
		plate.set(DataComponents.DYED_COLOR, new DyedItemColor(PUP_COLOUR));
		wolf.setItemSlot(EquipmentSlot.BODY, plate);
		wolf.setDropChance(EquipmentSlot.BODY, 0.0F);
		wolf.setPersistenceRequired();
		level.addFreshEntity(wolf);
		player.setAttached(IRON_PUP, wolf.getUUID());
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.WAX_ON, at.x, at.y + 0.5, at.z, 16, 0.4, 0.4, 0.4, 0.02);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.tinker.pup_built"));
		ClassTrees.give(player, PlayerClass.TINKERER, ClassTrees.XP_MADE);
	}

	private static void take(ServerPlayer player, net.minecraft.world.item.Item item, int n) {
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (n > 0 && s.is(item)) {
				int d = Math.min(n, s.getCount());
				s.shrink(d);
				n -= d;
			}
		}
	}

	private static void modifier(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String id, double amount) {
		AttributeInstance instance = e.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier key = JaymosKingdoms.id(id);
		if (amount == 0.0) {
			instance.removeModifier(key);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	/** A pup's tick (every few ticks): what it has grown into, fetching, patching, scouting. */
	private static void tickPup(ServerLevel level, ServerPlayer owner, Wolf pup, long now) {
		boolean defender = ClassTrees.has(owner, DEFENDER) > 0, hound = ClassTrees.has(owner, HOUND) > 0;
		modifier(pup, Attributes.ARMOR, "iron_defender", defender ? DEFENDER_ARMOR : 0.0);
		modifier(pup, Attributes.MOVEMENT_SPEED, "iron_hound", hound ? HOUND_SPEED : 0.0);
		modifier(pup, Attributes.ATTACK_DAMAGE, "iron_hound", hound ? HOUND_BITE : 0.0);
		if (now % 80 == 0) {
			level.playSound(null, pup.getX(), pup.getY(), pup.getZ(), SoundEvents.COPPER_GOLEM_STEP, SoundSource.NEUTRAL, 0.4F, 1.6F);
		}
		if (defender && now % PATCH_EVERY == 0 && owner.getHealth() < owner.getMaxHealth() && pup.distanceTo(owner) < 6.0) {
			owner.heal(PATCH);
			level.sendParticles(ParticleTypes.WAX_ON, owner.getX(), owner.getY(0.6), owner.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
			level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.SMITHING_TABLE_USE, SoundSource.NEUTRAL, 0.4F, 1.8F);
		}
		if (hound && now % SCOUT_EVERY == 0) {
			for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, pup.getBoundingBox().inflate(SCOUT_REACH), e -> e instanceof Enemy && e.isAlive())) {
				foe.addEffect(new MobEffectInstance(MobEffects.GLOWING, SCOUT_TICKS, 0, false, false));
			}
		}
		// fetching: what lies about is carried to the owner
		ItemEntity carried = FETCHING.get(pup.getUUID());
		if (carried != null && (carried.isRemoved() || pup.getTarget() != null)) {
			FETCHING.remove(pup.getUUID());
			carried = null;
		}
		if (carried != null) {
			carried.setPickUpDelay(10);
			carried.setPos(pup.getX(), pup.getY() + pup.getBbHeight() * 0.8, pup.getZ());
			carried.setDeltaMovement(Vec3.ZERO);
			if (pup.distanceTo(owner) < 2.0) {
				carried.setPickUpDelay(0);
				FETCHING.remove(pup.getUUID());
			} else {
				pup.getNavigation().moveTo(owner, 1.2);
			}
			return;
		}
		if (pup.getTarget() == null && !pup.isOrderedToSit() && now % 20 == 0) {
			List<ItemEntity> about = level.getEntitiesOfClass(ItemEntity.class, pup.getBoundingBox().inflate(FETCH_REACH),
				i -> i.isAlive() && i.distanceTo(owner) > 3.0 && !FETCHING.containsValue(i));
			if (!about.isEmpty()) {
				ItemEntity item = about.getFirst();
				if (pup.distanceTo(item) < 1.5) {
					FETCHING.put(pup.getUUID(), item);
				} else {
					pup.getNavigation().moveTo(item, 1.2);
				}
			}
		}
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (!THROWN.isEmpty()) {
			for (Iterator<Map.Entry<Snowball, Vec3>> it = THROWN.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<Snowball, Vec3> e = it.next();
				Snowball ball = e.getKey();
				if (ball.isRemoved()) {
					it.remove();
					if (ball.getRemovalReason() == Entity.RemovalReason.DISCARDED && ball.level() instanceof ServerLevel level) {
						burst(level, ball.getOwner(), e.getValue());
					}
				} else {
					e.setValue(ball.position());
				}
			}
		}
		if (server.getTickCount() % 4 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isSpectator()) {
				infusions(player, server.getTickCount());
			}
			if (player.getAttached(IRON_PUP) == null || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			Wolf pup = pupOf(player);
			if (pup != null) {
				tickPup(level, player, pup, server.getTickCount());
			}
		}
	}
}
