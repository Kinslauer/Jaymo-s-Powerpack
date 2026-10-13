package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.44) Jaymo's Equestrian, stage 2: bond and tack. A horse trusts each of its riders on their own account
 * ({@link Bond}): brushing, feeding, watering and riding it raise that trust; spurring it on (sprinting), letting it go
 * hungry or thirsty, leaving it out in the rain without a blanket and hurting it lower it. Below {@link #BUCK_BELOW} it
 * bucks now and then (so a stranger on your war horse is soon on the ground); from {@link #WHISTLE_TRUST} it comes when
 * you whistle (a key). It learns four {@link Discipline}s by being ridden, each to level 5, and with them tricks: the
 * rear, the sliding stop, the side-pass, the war kick and kneeling for an armoured rider (the trick key). An owned horse
 * gets hungry and thirsty (it grazes, eats from a hay bale and drinks from water close by when left alone; a hungry
 * one stops learning, a thirsty one tires twice as fast), and its unshod hooves wear down on stone and roads. Its tack
 * (saddles, bridle, blanket, barding) goes on in the tack screen ({@link TackMenu}). Tuning numbers live at the top.
 */
public final class Bonding {
	// ---- trust -------------------------------------------------------------------------------------------------------
	/** What a new owner starts at; what the owner of a horse tamed before 0.5.44 starts at. */
	public static final float TAME_TRUST = 30.0F, LEGACY_TRUST = 50.0F;
	/** Trust from a brushing (once every {@link #BRUSH_COOLDOWN} ticks), a feed, a drink, a hoof pick, and a minute's easy riding. */
	public static final float BRUSH_TRUST = 4.0F, FEED_TRUST = 1.5F, WATER_TRUST = 2.0F, PICK_TRUST = 1.0F, RIDE_TRUST = 1.0F;
	/** Trust lost per second of spurring (sprinting), per minute starving or parched, per minute in the rain without a blanket, per blow from its people. */
	public static final float SPUR_TRUST = 0.15F, STARVE_TRUST = 1.0F, RAIN_TRUST = 0.3F, HURT_TRUST = 10.0F;
	/** Under this it may buck its rider: the odds every 10 s at no trust at all. */
	public static final float BUCK_BELOW = 20.0F, BUCK_ODDS = 0.4F;
	/** It comes when you whistle from this much trust, from this far, at this pace; it shows you its stats from {@link #STATS_TRUST}. */
	public static final float WHISTLE_TRUST = 60.0F, STATS_TRUST = 40.0F;
	static final double WHISTLE_RANGE = 96.0, WHISTLE_SPEED = 2.0;
	static final int WHISTLE_TICKS = 600;
	static final int BRUSH_COOLDOWN = 2400, SHINE_TICKS = 24000;

	// ---- disciplines -------------------------------------------------------------------------------------------------
	/** Experience per block walked or trotted (trail), galloped (racing; sprinting counts half again), per jump, per blow struck or taken in a fight. */
	public static final float TRAIL_XP = 1.0F, RACING_XP = 1.0F, JUMP_XP = 6.0F, WAR_HIT_XP = 4.0F, WAR_HURT_XP = 2.0F;
	/** Per level above the first: more stamina (trail), top speed (racing), jump (jumping), courage (war). */
	public static final double TRAIL_STAMINA = 0.08, RACING_SPEED = 0.025, JUMPING_JUMP = 0.04, WAR_COURAGE = 0.06;
	/** The levels the tricks come at: the rear (any discipline), the sliding stop (racing), the side-pass (trail), the war kick (war), kneeling (war or trail). */
	public static final int REAR_LEVEL = 2, SLIDE_LEVEL = 3, SIDE_PASS_LEVEL = 3, KICK_LEVEL = 4, KNEEL_WAR = 3, KNEEL_TRAIL = 4;
	/** A rider wearing this much armour can only climb onto a horse that kneels for them (it knows how, or trusts them this much). */
	public static final int KNEEL_ARMOUR = 20;
	public static final float KNEEL_TRUST = 80.0F;
	/** The war kick and the rear: reach, damage, knockback. */
	static final double KICK_REACH = 3.0, REAR_REACH = 5.0;
	static final float KICK_DAMAGE = 7.0F;
	static final double KICK_KNOCKBACK = 1.6, REAR_KNOCKBACK = 1.0;
	static final int TRICK_COOLDOWN = 40;

	// ---- needs ---------------------------------------------------------------------------------------------------------
	/** Needs are seen to every this many ticks; at most this long is caught up at once (a horse left in an unloaded stable). */
	static final int NEEDS_TICKS = 100, CATCH_UP = 48000;
	/** Hunger and thirst lost a minute standing about; how much more each gait costs (halt, walk, trot, canter, gallop; sprint). */
	public static final float HUNGER_RATE = 0.8F, THIRST_RATE = 1.2F;
	static final float[] GAIT_APPETITE = {1.0F, 1.5F, 2.0F, 3.0F, 4.0F};
	static final float SPRINT_APPETITE = 5.0F;
	/** Out in the cold without a blanket it eats this much more; in the heat it drinks this much more (a desert horse half as much). */
	public static final float COLD_APPETITE = 1.5F, HEAT_THIRST = 1.5F, DESERT_THIRST = 0.5F;
	/** What it gains a minute left alone: grazing grass, at a hay bale, drinking at water (or a full cauldron), out in the rain. */
	public static final float GRAZE = 3.0F, HAY = 12.0F, DRINK = 12.0F, RAIN_DRINK = 3.0F;
	/** Below this it's hungry (stops learning) or thirsty (tires twice as fast); a starving horse runs this much slower. */
	public static final float HUNGRY = 20.0F, THIRSTY = 20.0F;
	public static final double STARVING_SPEED = 0.85, THIRSTY_DRAIN = 2.0;
	/** What each feed fills (oats also give back a quarter of its stamina). */
	static final Map<net.minecraft.world.item.Item, Float> FEED = new HashMap<>();

	// ---- feet ------------------------------------------------------------------------------------------------------------
	/** Blocks on hard ground per point of hoof worn (unshod) and of shoe worn (shod); hooves regrow this much a minute at rest; a hoof pick restores this much. */
	static final float HOOF_WEAR = 30.0F, SHOE_WEAR = 40.0F, HOOF_REGROW = 0.5F, HOOF_PICK = 35.0F;
	/** Unshod on hard ground; lame (hooves under {@link #LAME}) anywhere; shod on a path. */
	public static final double UNSHOD_SPEED = 0.92, LAME_SPEED = 0.8, PATH_SPEED = 1.05;
	public static final float LAME = 25.0F;

	// ---- saddles ---------------------------------------------------------------------------------------------------------
	/** The racing saddle's speed, and how far a fall at the canter or faster may throw you from it (and how likely). */
	public static final double RACING_SADDLE_SPEED = 1.10;
	static final float RACING_FALL = 3.5F, RACING_THROW = 0.5F;
	/** Without a bridle a horse at the canter or faster turns at most this much a tick. */
	public static final float UNBRIDLED_TURN = 12.0F;

	/** Its trust, training, needs and feet. Saved; synced (its rider's HUD and screens show it). */
	public static final AttachmentType<Bond> BOND = AttachmentRegistry.create(JaymosKingdoms.id("bond"),
		builder -> builder.persistent(Bond.CODEC).syncWith(Bond.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Its bridle and blanket. Saved; synced (drawn on it). */
	public static final AttachmentType<Tack> TACK = AttachmentRegistry.create(JaymosKingdoms.id("tack"),
		builder -> builder.persistent(Tack.CODEC).syncWith(Tack.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** Until when it kneels for a rider (game time; synced, not saved). */
	public static final AttachmentType<Long> KNEEL = AttachmentRegistry.create(JaymosKingdoms.id("kneel"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	public static ExtendedMenuType<TackMenu, Integer> TACK_MENU;

	/** Horses called by a whistle: horse id → the player it's coming to, and until when. */
	private record Call(UUID player, long until) {
	}

	private static final Map<Integer, Call> CALLED = new HashMap<>();
	/** Horse id → the highest it's fallen since it left the ground (for the racing saddle); horse ids in the air (for jumps). */
	private static final Map<Integer, Float> FALLING = new HashMap<>();
	private static final Set<Integer> AIRBORNE = new HashSet<>();
	/** Player → game time they may do a trick again. */
	private static final Map<UUID, Long> TRICKED = new HashMap<>();

	private Bonding() {
	}

	// ---- packets ---------------------------------------------------------------------------------------------------------

	/** Client → server: the whistle key. */
	public record Whistle() implements CustomPacketPayload {
		public static final Type<Whistle> TYPE = new Type<>(JaymosKingdoms.id("whistle"));
		public static final StreamCodec<FriendlyByteBuf, Whistle> CODEC = StreamCodec.unit(new Whistle());

		@Override
		public Type<Whistle> type() {
			return TYPE;
		}
	}

	/** Client → server: the trick key ({@code back}: the rider holds back, asking for the kick). */
	public record Trick(boolean back) implements CustomPacketPayload {
		public static final Type<Trick> TYPE = new Type<>(JaymosKingdoms.id("trick"));
		public static final StreamCodec<FriendlyByteBuf, Trick> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeBoolean(p.back),
			buf -> new Trick(buf.readBoolean()));

		@Override
		public Type<Trick> type() {
			return TYPE;
		}
	}

	/** Client → server: open the tack screen of horse {@code horse}. */
	public record OpenTack(int horse) implements CustomPacketPayload {
		public static final Type<OpenTack> TYPE = new Type<>(JaymosKingdoms.id("open_tack"));
		public static final StreamCodec<FriendlyByteBuf, OpenTack> CODEC = CustomPacketPayload.codec((p, buf) -> buf.writeVarInt(p.horse),
			buf -> new OpenTack(buf.readVarInt()));

		@Override
		public Type<OpenTack> type() {
			return TYPE;
		}
	}

	/** Server → rider: your horse is now at this gait (a sliding stop). */
	public record RiderGait(int horse, int gait) implements CustomPacketPayload {
		public static final Type<RiderGait> TYPE = new Type<>(JaymosKingdoms.id("rider_gait"));
		public static final StreamCodec<FriendlyByteBuf, RiderGait> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.horse);
			buf.writeVarInt(p.gait);
		}, buf -> new RiderGait(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<RiderGait> type() {
			return TYPE;
		}
	}

	// ---- setup -----------------------------------------------------------------------------------------------------------

	public static void register() {
		Tackroom.register();
		FEED.put(Items.WHEAT, 8.0F);
		FEED.put(Items.SUGAR, 3.0F);
		FEED.put(Items.HAY_BLOCK, 40.0F);
		FEED.put(Items.APPLE, 8.0F);
		FEED.put(Items.CARROT, 8.0F);
		FEED.put(Items.RED_MUSHROOM, 3.0F);
		FEED.put(Items.GOLDEN_CARROT, 25.0F);
		FEED.put(Items.GOLDEN_APPLE, 30.0F);
		FEED.put(Items.ENCHANTED_GOLDEN_APPLE, 60.0F);
		FEED.put(Tackroom.OATS, 20.0F);
		TACK_MENU = Registry.register(BuiltInRegistries.MENU, JaymosKingdoms.id("tack"), new ExtendedMenuType<>(TackMenu::new, ByteBufCodecs.VAR_INT));

		PayloadTypeRegistry.serverboundPlay().register(Whistle.TYPE, Whistle.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Trick.TYPE, Trick.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(OpenTack.TYPE, OpenTack.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RiderGait.TYPE, RiderGait.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Whistle.TYPE, (payload, context) -> whistle(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(Trick.TYPE, (payload, context) -> trick(context.player(), payload.back()));
		ServerPlayNetworking.registerGlobalReceiver(OpenTack.TYPE, (payload, context) -> openTack(context.player(), payload.horse()));

		UseEntityCallback.EVENT.register(Bonding::use);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
			if (taken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (entity instanceof AbstractHorse horse && Equestrian.covered(horse)) {
				if (source.getEntity() instanceof ServerPlayer player && player != horse.getControllingPassenger()
					&& (owner(horse, player) || bond(horse).knows(player.getUUID()))) {
					// struck by its own people
					addTrust(horse, player, -HURT_TRUST);
				}
				if (horse.getControllingPassenger() instanceof ServerPlayer rider && source.getEntity() instanceof LivingEntity attacker && attacker != rider) {
					train(level, horse, rider, Discipline.WAR, WAR_HURT_XP);
				}
			} else if (source.getEntity() instanceof ServerPlayer rider && rider.getVehicle() instanceof AbstractHorse horse && Equestrian.covered(horse)
				&& horse.getControllingPassenger() == rider && entity != horse) {
				train(level, horse, rider, Discipline.WAR, WAR_HIT_XP);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(Bonding::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CALLED.clear();
			FALLING.clear();
			AIRBORNE.clear();
			TRICKED.clear();
		});
	}

	// ---- reading ----------------------------------------------------------------------------------------------------------

	/** Its bond (a new one if it has none; the owner of a horse tamed before 0.5.44 starts well in with it). */
	public static Bond bond(AbstractHorse horse) {
		Bond b = horse.getAttached(BOND);
		if (b != null) {
			return b;
		}
		b = Bond.NEW.at(horse.level().getGameTime());
		EntityReference<LivingEntity> owner = horse.getOwnerReference();
		if (horse.isTamed() && owner != null) {
			b = b.withTrust(owner.getUUID(), LEGACY_TRUST);
		}
		if (!horse.level().isClientSide()) {
			horse.setAttached(BOND, b);
		}
		return b;
	}

	public static @Nullable Bond peek(Entity e) {
		return e instanceof AbstractHorse horse ? horse.getAttached(BOND) : null;
	}

	public static Tack tack(AbstractHorse horse) {
		Tack t = horse.getAttached(TACK);
		return t == null ? Tack.NONE : t;
	}

	public static float trust(AbstractHorse horse, Player player) {
		return bond(horse).trustOf(player.getUUID());
	}

	public static boolean owner(AbstractHorse horse, Player player) {
		EntityReference<LivingEntity> owner = horse.getOwnerReference();
		return owner != null && owner.getUUID().equals(player.getUUID());
	}

	/**
	 * One whose needs count: tamed by a player (not wild, nor a kingdom's), and alive (the undead don't eat). Clients
	 * aren't told who owns a horse, so there any tamed one counts (it only steers what the client expects to happen).
	 */
	static boolean kept(AbstractHorse horse) {
		return horse.isTamed() && !Equestrian.undead(horse) && (horse.level().isClientSide() || horse.getOwnerReference() != null);
	}

	/** It has hooves to wear down (not an elephant, nor the dead). */
	static boolean hooved(AbstractHorse horse) {
		return !(horse instanceof WarElephant) && !Equestrian.undead(horse);
	}

	/** Its stamina, more for its trail level. */
	public static double capacity(AbstractHorse horse, Equine e) {
		Bond b = horse.getAttached(BOND);
		return e.capacity() * (1.0 + TRAIL_STAMINA * ((b == null ? 1 : b.level(Discipline.TRAIL)) - 1));
	}

	/** Its top speed, more for its racing level and a racing saddle. */
	public static double topSpeed(AbstractHorse horse, Equine e) {
		Bond b = horse.getAttached(BOND);
		double speed = e.topSpeed() * (1.0 + RACING_SPEED * ((b == null ? 1 : b.level(Discipline.RACING)) - 1));
		if (horse.getItemBySlot(EquipmentSlot.SADDLE).is(Tackroom.RACING_SADDLE)) {
			speed *= RACING_SADDLE_SPEED;
		}
		return speed;
	}

	/** Its nerve, more for its war level. */
	public static double bravery(AbstractHorse horse, Equine e) {
		Bond b = horse.getAttached(BOND);
		return Math.min(1.0, e.bravery() + WAR_COURAGE * ((b == null ? 1 : b.level(Discipline.WAR)) - 1));
	}

	/** Its jump, higher for its jumping level. */
	public static double jump(AbstractHorse horse, Equine e) {
		Bond b = horse.getAttached(BOND);
		return e.jumpStrength() + JUMPING_JUMP * ((b == null ? 1 : b.level(Discipline.JUMPING)) - 1);
	}

	/** How its feet and belly slow it on {@code under}. */
	public static double feet(AbstractHorse horse, BlockState under) {
		Bond b = horse.getAttached(BOND);
		if (b == null) {
			return 1.0;
		}
		double f = 1.0;
		// (only a kept horse's needs ever fall, and the client can't tell who owns a horse)
		if (b.hunger() <= 0.0F) {
			f *= STARVING_SPEED;
		}
		if (!hooved(horse)) {
			return f;
		}
		if (b.shod()) {
			if (under.is(Blocks.DIRT_PATH)) {
				f *= PATH_SPEED;
			}
		} else if (b.hooves() < LAME) {
			f *= LAME_SPEED;
		} else if (hard(under)) {
			f *= UNSHOD_SPEED;
		}
		return f;
	}

	/** Stone, roads and the like: what wears down hooves. */
	static boolean hard(BlockState under) {
		if (under.is(Blocks.DIRT_PATH) || under.is(Blocks.GRAVEL)) {
			return true;
		}
		if (under.is(BlockTags.PLANKS) || under.is(BlockTags.LOGS) || under.is(BlockTags.WOODEN_SLABS) || under.is(BlockTags.WOODEN_STAIRS)) {
			return false;
		}
		return under.getBlock().defaultDestroyTime() >= 1.2F;
	}

	/** It knows the trick a rider of this much armour needs to mount it, or trusts them enough to kneel anyway. */
	public static boolean kneels(AbstractHorse horse, Player player) {
		Bond b = bond(horse);
		return b.level(Discipline.WAR) >= KNEEL_WAR || b.level(Discipline.TRAIL) >= KNEEL_TRAIL || b.trustOf(player.getUUID()) >= KNEEL_TRUST;
	}

	/** (From {@code AbstractHorseMixin}, client and server) it side-passes: full speed sideways at a walk, from trail level 3. */
	public static boolean sidePass(AbstractHorse horse, Gait gait) {
		Bond b = horse.getAttached(BOND);
		return b != null && b.level(Discipline.TRAIL) >= SIDE_PASS_LEVEL && gait.ordinal() <= Gait.WALK.ordinal();
	}

	/** (From {@code AbstractHorseMixin}, client and server) without a bridle, a horse at speed turns only so fast. */
	public static float turn(AbstractHorse horse, float wanted, Gait gait) {
		if (gait.ordinal() < Gait.CANTER.ordinal() || !tack(horse).bridle().isEmpty()) {
			return wanted;
		}
		float now = horse.getYRot();
		float delta = net.minecraft.util.Mth.wrapDegrees(wanted - now);
		return now + net.minecraft.util.Mth.clamp(delta, -UNBRIDLED_TURN, UNBRIDLED_TURN);
	}

	// ---- changing ---------------------------------------------------------------------------------------------------------

	static void addTrust(AbstractHorse horse, Player player, float delta) {
		Bond b = bond(horse);
		float was = b.trustOf(player.getUUID());
		float now = Math.max(0.0F, Math.min(Bond.FULL, was + delta));
		if (now != was || !b.knows(player.getUUID())) {
			horse.setAttached(BOND, b.withTrust(player.getUUID(), now));
		}
		if (player instanceof ServerPlayer sp && was < WHISTLE_TRUST && now >= WHISTLE_TRUST) {
			sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.horse_comes", name(horse)), true);
		}
	}

	/** (From {@code AbstractHorseMixin}) it's just been tamed by {@code player}. */
	public static void tamed(AbstractHorse horse, Player player) {
		if (Equestrian.covered(horse) && !horse.level().isClientSide()) {
			Bond b = bond(horse);
			if (b.trustOf(player.getUUID()) < TAME_TRUST) {
				horse.setAttached(BOND, b.withTrust(player.getUUID(), TAME_TRUST));
			}
		}
	}

	/** {@code horse} learns {@code xp} of {@code d} (not while hungry); it tells its rider when it reaches a level. */
	static void train(ServerLevel level, AbstractHorse horse, ServerPlayer rider, Discipline d, float xp) {
		Bond b = bond(horse);
		if (xp <= 0.0F || kept(horse) && b.hunger() < HUNGRY) {
			return;
		}
		int before = b.level(d);
		b = b.withXp(d, b.xp(d) + xp);
		horse.setAttached(BOND, b);
		int after = b.level(d);
		if (after > before) {
			rider.sendSystemMessage(Component.translatable("message.jaymoskingdoms.horse_level", name(horse),
				Component.translatable(d.key()), after), true);
			level.playSound(null, horse.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.6F, 1.4F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, horse.getX(), horse.getY() + horse.getBbHeight(), horse.getZ(), 10, 0.5, 0.4, 0.5, 0.0);
			if (d == Discipline.JUMPING) {
				Equine e = Equestrian.equine(horse);
				AttributeInstance jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
				if (jump != null) {
					jump.setBaseValue(jump(horse, e));
				}
			}
		}
	}

	public static Component name(AbstractHorse horse) {
		if (horse.hasCustomName()) {
			return horse.getCustomName();
		}
		Equine e = horse.getAttached(Equestrian.EQUINE);
		return e == null ? horse.getName() : Component.translatable(e.breed().key());
	}

	// ---- care ------------------------------------------------------------------------------------------------------------

	/** Brushing, watering, picking hooves and shoeing a horse; and an armoured rider needing it to kneel. */
	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (!(entity instanceof AbstractHorse horse) || !Equestrian.covered(horse) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!horse.isTamed() || horse.isBaby()) {
			return InteractionResult.PASS;
		}
		if (stack.is(Items.BRUSH)) {
			if (level instanceof ServerLevel server) {
				brush(server, horse, player, stack, hand);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Items.WATER_BUCKET) && kept(horse)) {
			if (level instanceof ServerLevel server) {
				Bond b = bond(horse);
				if (b.thirst() < Bond.FULL - 10.0F) {
					addTrust(horse, player, WATER_TRUST);
				}
				horse.setAttached(BOND, bond(horse).withNeeds(b.hunger(), Bond.FULL));
				player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
				server.playSound(null, horse.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 1.0F, 0.8F);
				server.sendParticles(ParticleTypes.SPLASH, horse.getX(), horse.getEyeY(), horse.getZ(), 12, 0.3, 0.2, 0.3, 0.0);
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Tackroom.HOOF_PICK) && hooved(horse)) {
			if (level instanceof ServerLevel server) {
				Bond b = bond(horse);
				if (b.hooves() < Bond.FULL - 5.0F) {
					addTrust(horse, player, PICK_TRUST);
				}
				horse.setAttached(BOND, bond(horse).withFeet(b.hooves() + HOOF_PICK, b.shoes()));
				stack.hurtAndBreak(1, player, hand);
				server.playSound(null, horse.blockPosition(), SoundEvents.BRUSH_GRAVEL, SoundSource.PLAYERS, 1.0F, 1.2F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hooves", Math.round(bond(horse).hooves())));
			}
			return InteractionResult.SUCCESS;
		}
		if (stack.is(Tackroom.HORSESHOES) && hooved(horse)) {
			if (level instanceof ServerLevel server) {
				Bond b = bond(horse);
				if (b.shoes() > Bond.FULL / 2.0F) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.already_shod"));
				} else {
					horse.setAttached(BOND, b.withFeet(Math.max(b.hooves(), Bond.FULL * 0.75F), Bond.FULL));
					stack.consume(1, player);
					server.playSound(null, horse.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.5F, 1.6F);
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.shod", name(horse)));
				}
			}
			return InteractionResult.SUCCESS;
		}
		// climbing up: a rider in heavy armour needs it to kneel
		if (stack.isEmpty() && !player.isSecondaryUseActive() && !horse.isVehicle() && player.getArmorValue() >= KNEEL_ARMOUR) {
			if (!kneels(horse, player)) {
				// (refused on the client, so the server never hears of it: the client says why)
				if (level.isClientSide()) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.too_heavy", name(horse)));
				}
				return InteractionResult.FAIL;
			}
			if (!level.isClientSide()) {
				horse.setAttached(KNEEL, level.getGameTime() + 30L);
			}
		}
		return InteractionResult.PASS;
	}

	private static void brush(ServerLevel level, AbstractHorse horse, Player player, ItemStack stack, InteractionHand hand) {
		Bond b = bond(horse);
		long now = level.getGameTime();
		if (now - b.brushed() < BRUSH_COOLDOWN && b.brushed() != 0L) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.already_brushed", name(horse)));
			return;
		}
		horse.setAttached(BOND, b.withBrush(now + SHINE_TICKS, now));
		addTrust(horse, player, BRUSH_TRUST);
		stack.hurtAndBreak(1, player, hand);
		level.playSound(null, horse.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.WAX_OFF, horse.getX(), horse.getY() + horse.getBbHeight() * 0.7, horse.getZ(), 14, 0.5, 0.3, 0.5, 0.0);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.brushed", name(horse), Math.round(trust(horse, player))));
	}

	/**
	 * (From {@code AbstractHorseMixin}) {@code player} feeds it {@code stack}. A kept horse that's peckish eats any feed
	 * whatever vanilla thinks (true: eaten); golden food still goes to vanilla, for breeding. Null: leave it to vanilla.
	 */
	public static @Nullable Boolean feed(AbstractHorse horse, Player player, ItemStack stack) {
		Float fill = FEED.get(stack.getItem());
		if (fill == null || !Equestrian.covered(horse) || !kept(horse) || horse.isBaby() || stack.is(Items.GOLDEN_CARROT) || stack.is(Items.GOLDEN_APPLE)
			|| stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			return null;
		}
		Bond b = bond(horse);
		if (b.hunger() >= Bond.FULL - 10.0F) {
			return null;
		}
		if (!horse.level().isClientSide()) {
			ate(horse, player, stack, fill);
			horse.heal(fill / 8.0F);
		}
		return true;
	}

	/** (From {@code AbstractHorseMixin}) vanilla had it eat {@code stack}: it fills it too. */
	public static void ateVanilla(AbstractHorse horse, Player player, ItemStack stack) {
		Float fill = FEED.get(stack.getItem());
		if (fill != null && Equestrian.covered(horse) && kept(horse) && !horse.level().isClientSide()) {
			ate(horse, player, stack, fill);
		}
	}

	private static void ate(AbstractHorse horse, Player player, ItemStack stack, float fill) {
		Bond b = bond(horse);
		if (b.hunger() < Bond.FULL - 5.0F) {
			addTrust(horse, player, FEED_TRUST);
			b = bond(horse);
		}
		horse.setAttached(BOND, b.withNeeds(b.hunger() + fill, b.thirst()));
		horse.playSound(SoundEvents.HORSE_EAT, 1.0F, 1.0F);
		if (stack.is(Tackroom.OATS)) {
			// oats: a quarter of its wind back
			Equine e = Equestrian.equine(horse);
			Stride s = Equestrian.stride(horse);
			double cap = capacity(horse, e);
			double stamina = s.stamina() < 0.0F ? cap : s.stamina();
			horse.setAttached(Equestrian.STRIDE, new Stride(s.gait(), s.sprint(), (float)Math.min(cap, stamina + cap * 0.25), s.blown(), s.at()));
		}
	}

	// ---- whistle and tricks ----------------------------------------------------------------------------------------------

	private static void whistle(ServerPlayer player) {
		ServerLevel level = player.level();
		level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1.2F, 1.9F);
		AbstractHorse best = null;
		boolean shy = false;
		double bestDist = Double.MAX_VALUE;
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(WHISTLE_RANGE),
			h -> Equestrian.covered(h) && h.isAlive() && h.isTamed() && owner(h, player) && !h.isVehicle()
				// (0.5.47) a boarded horse stays in its stall till it's paid for
				&& !com.jaymoskingdoms.Stables.boarded(h))) {
			if (trust(horse, player) < WHISTLE_TRUST || horse.isLeashed()) {
				shy = true;
				continue;
			}
			double d = horse.distanceToSqr(player);
			if (d < bestDist) {
				bestDist = d;
				best = horse;
			}
		}
		if (best == null) {
			player.sendOverlayMessage(Component.translatable(shy ? "message.jaymoskingdoms.whistle_shy" : "message.jaymoskingdoms.whistle_none"));
			return;
		}
		CALLED.put(best.getId(), new Call(player.getUUID(), level.getGameTime() + WHISTLE_TICKS));
		best.playSound(SoundEvents.HORSE_AMBIENT, 1.5F, 1.0F);
		best.getNavigation().moveTo(player, WHISTLE_SPEED);
	}

	private static void trick(ServerPlayer player, boolean back) {
		if (!(player.getVehicle() instanceof AbstractHorse horse) || !Equestrian.covered(horse) || horse.getControllingPassenger() != player) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long until = TRICKED.get(player.getUUID());
		if (until != null && until > now) {
			return;
		}
		Bond b = bond(horse);
		Stride s = Equestrian.stride(horse);
		Gait gait = s.gaitOf();
		if (gait.ordinal() >= Gait.CANTER.ordinal()) {
			if (b.level(Discipline.RACING) < SLIDE_LEVEL) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trick_slide_locked", SLIDE_LEVEL));
				return;
			}
			if (tack(horse).bridle().isEmpty()) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trick_needs_bridle"));
				return;
			}
			// the sliding stop
			TRICKED.put(player.getUUID(), now + TRICK_COOLDOWN);
			horse.setAttached(Equestrian.STRIDE, new Stride(Gait.HALT.ordinal(), false, s.stamina(), s.blown(), s.at()));
			ServerPlayNetworking.send(player, new RiderGait(horse.getId(), Gait.HALT.ordinal()));
			horse.setDeltaMovement(horse.getDeltaMovement().multiply(0.25, 1.0, 0.25));
			horse.syncVelocity = true;
			BlockState under = level.getBlockState(horse.getBlockPosBelowThatAffectsMyMovement());
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), horse.getX(), horse.getY() + 0.1, horse.getZ(), 30, 0.6, 0.1, 0.6, 0.15);
			level.playSound(null, horse.blockPosition(), SoundEvents.GRAVEL_BREAK, SoundSource.NEUTRAL, 1.0F, 0.8F);
			return;
		}
		Vec3 ahead = Vec3.directionFromRotation(0.0F, horse.getYRot());
		if (back && b.level(Discipline.WAR) >= KICK_LEVEL) {
			// the war kick: both hind hooves at whatever's behind
			TRICKED.put(player.getUUID(), now + TRICK_COOLDOWN);
			Vec3 behind = horse.position().subtract(ahead.scale(horse.getBbWidth() * 0.5 + 1.0));
			boolean hit = false;
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(behind, behind).inflate(KICK_REACH * 0.5, 1.0, KICK_REACH * 0.5),
				t -> t.isAlive() && t != horse && t != player && !t.isPassengerOfSameVehicle(player))) {
				target.hurtServer(level, level.damageSources().mobAttack(horse), KICK_DAMAGE);
				target.knockback(KICK_KNOCKBACK, ahead.x, ahead.z, level.damageSources().mobAttack(horse), KICK_DAMAGE);
				hit = true;
			}
			level.playSound(null, horse.blockPosition(), hit ? SoundEvents.HORSE_ANGRY : SoundEvents.HORSE_BREATHE, SoundSource.NEUTRAL, 1.2F, 0.9F);
			return;
		}
		int best = 0;
		for (Discipline d : Discipline.values()) {
			best = Math.max(best, b.level(d));
		}
		if (best < REAR_LEVEL) {
			player.sendSystemMessage(Component.translatable(back ? "message.jaymoskingdoms.trick_kick_locked" : "message.jaymoskingdoms.trick_rear_locked",
				back ? KICK_LEVEL : REAR_LEVEL), true);
			return;
		}
		// the rear: what's in front backs off
		TRICKED.put(player.getUUID(), now + TRICK_COOLDOWN);
		horse.standIfPossible();
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.4F, 1.0F);
		Vec3 front = horse.position().add(ahead.scale(REAR_REACH * 0.5));
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(front, front).inflate(REAR_REACH * 0.5, 1.5, REAR_REACH * 0.5),
			t -> t.isAlive() && t instanceof Enemy)) {
			target.knockback(REAR_KNOCKBACK, -ahead.x, -ahead.z, level.damageSources().mobAttack(horse), 0.0F);
			if (target instanceof PathfinderMob mob) {
				Vec3 away = target.position().add(ahead.scale(8.0));
				mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
			}
		}
	}

	private static void openTack(ServerPlayer player, int id) {
		if (!(player.level().getEntity(id) instanceof AbstractHorse horse) || !Equestrian.covered(horse) || !horse.isTamed() || horse.isBaby()
			|| player.distanceToSqr(horse) > 64.0 || horse.isVehicle() && !horse.hasPassenger(player)) {
			return;
		}
		player.openMenu(new ExtendedMenuProvider<Integer>() {
			@Override
			public Integer getScreenOpeningData(ServerPlayer serverPlayer) {
				return horse.getId();
			}

			@Override
			public Component getDisplayName() {
				return Component.translatable("container.jaymoskingdoms.tack", horse.getDisplayName());
			}

			@Override
			public AbstractContainerMenu createMenu(int menuId, Inventory inventory, Player menuPlayer) {
				return new TackMenu(menuId, inventory, horse);
			}
		});
	}

	// ---- ticking ---------------------------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 10 == 0 && !CALLED.isEmpty()) {
			CALLED.entrySet().removeIf(entry -> {
				Call call = entry.getValue();
				if (!(level.getEntity(entry.getKey()) instanceof AbstractHorse horse) || horse.isVehicle() || call.until < now) {
					return true;
				}
				Player player = level.getPlayerByUUID(call.player);
				if (player == null || player.level() != level || horse.distanceToSqr(player) < 9.0) {
					horse.getNavigation().stop();
					return true;
				}
				horse.getNavigation().moveTo(player, WHISTLE_SPEED);
				return false;
			});
		}
		if (now % NEEDS_TICKS != 0) {
			return;
		}
		Set<AbstractHorse> seen = new HashSet<>();
		for (ServerPlayer player : level.players()) {
			seen.addAll(level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(128.0), h -> Equestrian.covered(h) && h.isAlive()));
		}
		for (AbstractHorse horse : seen) {
			needs(level, horse, now);
			Bond b = horse.getAttached(BOND);
			if (b != null && b.shine() > now && now % (NEEDS_TICKS * 2) == 0) {
				level.sendParticles(ParticleTypes.WAX_OFF, horse.getX(), horse.getY() + horse.getBbHeight() * 0.8, horse.getZ(), 2, 0.4, 0.2, 0.4, 0.0);
			}
		}
		if (now % 1200 == 0) {
			TRICKED.values().removeIf(t -> t < now);
			FALLING.keySet().removeIf(id -> !(level.getEntity(id) instanceof AbstractHorse h) || !h.isVehicle());
			AIRBORNE.removeIf(id -> !(level.getEntity(id) instanceof AbstractHorse h) || !h.isVehicle());
		}
	}

	/** Its hunger and thirst since it was last seen to: what it spends, what it finds, what it holds against its people. */
	private static void needs(ServerLevel level, AbstractHorse horse, long now) {
		if (!kept(horse)) {
			return;
		}
		Bond b = bond(horse);
		long elapsed = b.at() == 0L ? NEEDS_TICKS : Math.min(CATCH_UP, now - b.at());
		if (elapsed <= 0L) {
			return;
		}
		float minutes = elapsed / 1200.0F;
		Equine e = Equestrian.equine(horse);
		boolean ridden = horse.getControllingPassenger() instanceof Player;
		float work = 1.0F;
		if (ridden) {
			Stride s = Equestrian.stride(horse);
			work = s.sprint() ? SPRINT_APPETITE : GAIT_APPETITE[Math.min(s.gaitOf().ordinal(), GAIT_APPETITE.length - 1)];
		}
		boolean blanket = !tack(horse).blanket().isEmpty();
		float appetite = appetite(e.breed()) * (Equestrian.cold(horse) && !blanket ? COLD_APPETITE : 1.0F);
		float thirsting = (e.breed() == Breed.DESERT_HORSE ? DESERT_THIRST : Equestrian.hot(horse) ? HEAT_THIRST : 1.0F);
		float hunger = b.hunger() - HUNGER_RATE * appetite * work * minutes;
		float thirst = b.thirst() - THIRST_RATE * thirsting * work * minutes;
		float hooves = b.hooves();
		BlockPos feet = horse.blockPosition();
		boolean raining = level.isRainingAt(feet.above((int)Math.ceil(horse.getBbHeight())));
		if (!ridden) {
			BlockState below = level.getBlockState(feet.below());
			BlockState at = level.getBlockState(feet);
			if (below.is(Blocks.GRASS_BLOCK) || at.is(Blocks.SHORT_GRASS) || at.is(Blocks.TALL_GRASS)) {
				hunger += GRAZE * minutes;
			}
			if (near(level, feet, st -> st.is(Blocks.HAY_BLOCK))) {
				hunger += HAY * minutes;
			}
			if (horse.isInWater() || near(level, feet, st -> st.is(Blocks.WATER) || st.is(Blocks.WATER_CAULDRON))) {
				thirst += DRINK * minutes;
			}
			hooves += HOOF_REGROW * minutes;
		}
		if (raining) {
			thirst += RAIN_DRINK * minutes;
		}
		Bond next = b.withNeeds(hunger, thirst).withFeet(hooves, b.shoes()).at(now);
		// what it holds against its people
		float grudge = 0.0F;
		if (next.hunger() <= 0.0F || next.thirst() <= 0.0F) {
			grudge += STARVE_TRUST * minutes;
		}
		if (raining && !blanket && !horse.isInWater()) {
			grudge += RAIN_TRUST * minutes;
		}
		if (grudge > 0.0F) {
			for (UUID player : next.trust().keySet()) {
				next = next.withTrust(player, next.trustOf(player) - grudge);
			}
		}
		horse.setAttached(BOND, next);
	}

	/** How much more (or less) than an ordinary horse its breed eats. */
	static float appetite(Breed breed) {
		return switch (breed) {
			case DESTRIER, WAR_ELEPHANT, WOOLLY_MAMMOTH -> 2.0F;
			case DRAFT -> 1.5F;
			case STEPPE_PONY -> 0.6F;
			default -> 1.0F;
		};
	}

	private static boolean near(ServerLevel level, BlockPos feet, java.util.function.Predicate<BlockState> what) {
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 0, 1))) {
			if (what.test(level.getBlockState(pos))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * (From {@link Equestrian}, every few ticks for each ridden horse) what riding does: it learns by the gait it goes at
	 * and the jumps it takes; it comes to trust an easy rider and resents the spurs; it may buck a rider it doesn't trust;
	 * its hooves or shoes wear on hard ground; and a fall from a racing saddle may throw its rider.
	 */
	static void ride(ServerLevel level, AbstractHorse horse, ServerPlayer rider, Gait gait, boolean sprint, boolean going, double blocks, long elapsed) {
		Bond b = bond(horse);
		RandomSource random = horse.getRandom();
		// learning
		if (going) {
			switch (gait) {
				case WALK, TROT -> train(level, horse, rider, Discipline.TRAIL, (float)blocks * TRAIL_XP);
				case CANTER -> {
					train(level, horse, rider, Discipline.TRAIL, (float)blocks * TRAIL_XP * 0.5F);
					train(level, horse, rider, Discipline.RACING, (float)blocks * RACING_XP * 0.5F);
				}
				case GALLOP -> train(level, horse, rider, Discipline.RACING, (float)blocks * RACING_XP * (sprint ? 1.5F : 1.0F));
				default -> {
				}
			}
		}
		boolean inAir = !horse.onGround() && !horse.isInWater();
		if (inAir && !AIRBORNE.contains(horse.getId()) && horse.getDeltaMovement().y > 0.2) {
			AIRBORNE.add(horse.getId());
			train(level, horse, rider, Discipline.JUMPING, JUMP_XP);
		} else if (!inAir) {
			AIRBORNE.remove(horse.getId());
		}
		// a bad landing from a racing saddle
		if (inAir) {
			FALLING.merge(horse.getId(), (float)horse.fallDistance, Math::max);
		} else {
			Float fell = FALLING.remove(horse.getId());
			if (fell != null && fell >= RACING_FALL && gait.ordinal() >= Gait.CANTER.ordinal()
				&& horse.getItemBySlot(EquipmentSlot.SADDLE).is(Tackroom.RACING_SADDLE) && random.nextFloat() < RACING_THROW) {
				throwOff(horse, rider, "message.jaymoskingdoms.racing_throw");
				return;
			}
		}
		// trust
		b = bond(horse);
		float minutes = elapsed / 1200.0F;
		float trust = b.trustOf(rider.getUUID());
		Stride s = Equestrian.stride(horse);
		if (sprint) {
			addTrust(horse, rider, -SPUR_TRUST * elapsed / 20.0F);
		} else if (going && !s.blown() && (!kept(horse) || b.hunger() >= HUNGRY && b.thirst() >= THIRSTY)) {
			addTrust(horse, rider, RIDE_TRUST * minutes);
		}
		if (horse.isTamed() && trust < BUCK_BELOW && random.nextFloat() < (BUCK_BELOW - trust) / BUCK_BELOW * BUCK_ODDS * elapsed / 200.0F) {
			horse.standIfPossible();
			throwOff(horse, rider, "message.jaymoskingdoms.horse_bucks");
			return;
		}
		// feet
		if (going && hooved(horse) && blocks > 0.0) {
			BlockState under = level.getBlockState(horse.getBlockPosBelowThatAffectsMyMovement());
			if (hard(under)) {
				b = bond(horse);
				if (b.shod()) {
					float shoes = b.shoes() - (float)blocks / SHOE_WEAR;
					horse.setAttached(BOND, b.withFeet(b.hooves(), shoes));
					if (shoes <= 0.0F) {
						rider.sendSystemMessage(Component.translatable("message.jaymoskingdoms.shoes_worn", name(horse)), true);
						level.playSound(null, horse.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
					}
				} else {
					float hooves = b.hooves() - (float)blocks / HOOF_WEAR;
					horse.setAttached(BOND, b.withFeet(hooves, 0.0F));
					if (b.hooves() >= LAME && hooves < LAME) {
						rider.sendSystemMessage(Component.translatable("message.jaymoskingdoms.lame", name(horse)), true);
					}
				}
			}
		}
	}

	static void throwOff(AbstractHorse horse, ServerPlayer rider, String message) {
		rider.stopRiding();
		Vec3 back = Vec3.directionFromRotation(0.0F, horse.getYRot()).scale(-0.4);
		rider.push(back.x, 0.3, back.z);
		rider.syncVelocity = true;
		horse.playSound(SoundEvents.HORSE_ANGRY, 1.2F, 1.0F);
		rider.sendSystemMessage(Component.translatable(message, name(horse)), true);
	}

	/** Its tack (bridle, blanket) as dropped items, when it dies. */
	static void dropTack(ServerLevel level, AbstractHorse horse) {
		Tack t = tack(horse);
		if (!t.bridle().isEmpty()) {
			horse.spawnAtLocation(level, t.bridle().copy());
		}
		if (!t.blanket().isEmpty()) {
			horse.spawnAtLocation(level, t.blanket().copy());
		}
		horse.setAttached(TACK, Tack.NONE);
	}

}
