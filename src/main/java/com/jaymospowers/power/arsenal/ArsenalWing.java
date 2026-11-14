package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.Guard;
import com.jaymospowers.core.Powers;
import com.jaymospowers.ground.FirstTools;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.arsenal.entity.RiftBlade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * <b>Attendant Blades</b> (a short hold of Sneak + V, once V is yours; Sneak + X for the Void Arsenal, whose Sneak + V
 * is its gates): weapons come out of small rifts and stay,
 * hanging round their holder in a formation that follows them, instead of firing once as Barrage does. Two blades at
 * first, four once every ability is unlocked, six for the Armory and the Void Arsenal. Each goes home on its own timer
 * ({@link #BLADE_TICKS}), as everything the rift gives does. A tap of the same key with the blades out changes the
 * formation ({@link #FAN}, {@link #HALO}, {@link #FLANK}).
 *
 * <p>What they do unasked: when their holder hits something, the next ready blade strikes the same thing (the lock-on
 * target comes first) and is back in its place {@link #RETURN_TICKS} later; and they go for whatever hurt their holder.
 * Until the power is mastered they also pick fights of their own with monsters close by.
 *
 * <p>Orders: a tap of Sneak + B sets every blade on what you look at (your lock-on target first) until it falls or gets
 * away, one blade every {@link #HARRY_GAP} ticks; held, the blades leave their places and fly where you look
 * ({@link #STEER}), cutting what they pass through, and go for whatever is nearest that spot when you let go. Holding the
 * wing's key with the blades out opens the order wheel on the client ({@link Order}): the formations, Whirl (the whole
 * wing spinning close round you for {@link #WHIRL_TICKS}), a mark that spares what you look at, taking a blade into your
 * hand, sending them home, and the big moves ({@link ArsenalWingMoves}: Closing Ring, stepping to a blade). With a guard raised, a blade meets each blow or shot meant for you and goes home for it.
 *
 * <p>G with the blades out and a place free hands the wing what you hold: a summoned weapon (it keeps its timer), a
 * weapon or tool of your own, or one loose rock off the stack. Your own things never go home by themselves and are
 * never lost: they come back to your pack when the wing is sent home, and the wing is saved with you.
 *
 * <p>The whole wing is one attachment ({@link #WING}, synced to everyone); the places come from one formula both sides
 * use ({@link #slot}). A blade on its way to a target is a {@link RiftBlade}; docked ones are drawn by
 * {@code ArsenalWingRenderer}.
 */
public final class ArsenalWing {
	// tuning (ticks)
	/** Sneak + V held this long calls the blades (or sends them home); let go sooner and the formation changes. */
	public static final int HOLD_TICKS = 8;
	public static final int BLADE_TICKS = 1200;
	/** From calling the blades until they can be called again. */
	public static final int COOLDOWN = 400;
	/** The blades come out one after another, this far apart; each takes {@link #EMERGE_TICKS} to slide out. */
	public static final int EMERGE_GAP = 6;
	public static final int EMERGE_TICKS = 9;
	/** A blade that struck is back in its place this much later. */
	public static final int RETURN_TICKS = 30;
	public static final float STRIKE_DAMAGE = 5.0F;
	/** Guarding: one blade this often at whatever hurt the holder in the last {@link #GRUDGE_TICKS}, within {@link #GUARD_RANGE}. */
	public static final int GUARD_GAP = 15;
	public static final int GRUDGE_TICKS = 100;
	public static final double GUARD_RANGE = 16.0;
	/** Unmastered: a blade flies at the nearest monster in {@link #WILD_RANGE} this often, asked or not. */
	public static final int WILD_GAP = 40;
	public static final double WILD_RANGE = 10.0;

	/** Harrying: one blade this often at the marked target, for this much, while it is within {@link #HARRY_RANGE}. */
	public static final int HARRY_GAP = 8;
	public static final float HARRY_DAMAGE = 3.0F;
	public static final double HARRY_RANGE = 32.0;
	/** Steering: how far out the blades fly along your look, how long at most, and what a pass through something does. */
	public static final double STEER_RANGE = 24.0;
	public static final int STEER_MAX = 100;
	public static final int STEER_OUT_TICKS = 8;
	public static final float STEER_DAMAGE = 3.0F;
	public static final int STEER_HIT_GAP = 10;
	/** Whirl: how long, how often it cuts, how far out, and how long before the next. */
	public static final int WHIRL_TICKS = 80;
	public static final int WHIRL_HIT_GAP = 5;
	public static final double WHIRL_RADIUS = 2.2;
	public static final float WHIRL_DAMAGE = 3.0F;
	public static final int WHIRL_COOLDOWN = 300;
	/** What a loose rock off the wing does when it strikes. */
	public static final float ROCK_DAMAGE = 3.0F;

	public static final int FAN = 0, HALO = 1, FLANK = 2, SHAPES = 3;
	/** What the whole wing is doing: hanging in its places, flying where its holder looks, whirling round them, or ringing something ({@link ArsenalWingMoves}). */
	public static final int DOCKED = 0, STEER = 1, WHIRL = 2, RING = 3;
	/** The order wheel's slices after the three formations. */
	public static final int ORDER_WHIRL = 3, ORDER_SPARE = 4, ORDER_TAKE = 5, ORDER_HOME = 6, ORDER_STORM = 7, ORDER_RING = 8, ORDER_STEP = 9, ORDERS = 10;

	/**
	 * One blade: what it is, when it goes home, when it is back in its place ({@code 0}: it never left), and whether it
	 * is its holder's own (never goes home by itself; returned to their pack).
	 */
	public record Blade(ItemStack item, long expires, long back, boolean own) {
		public static final Codec<Blade> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.OPTIONAL_CODEC.fieldOf("item").forGetter(Blade::item),
				Codec.LONG.fieldOf("expires").forGetter(Blade::expires),
				Codec.LONG.optionalFieldOf("back", 0L).forGetter(Blade::back),
				Codec.BOOL.optionalFieldOf("own", false).forGetter(Blade::own))
			.apply(i, Blade::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Blade> STREAM_CODEC = StreamCodec.composite(
			ItemStack.OPTIONAL_STREAM_CODEC, Blade::item,
			ByteBufCodecs.VAR_LONG, Blade::expires,
			ByteBufCodecs.VAR_LONG, Blade::back,
			ByteBufCodecs.BOOL, Blade::own,
			Blade::new);

		Blade away(long until) {
			return new Blade(item, expires, until, own);
		}
	}

	/**
	 * The blades round one holder: when they were called, the formation, what the wing is doing ({@link #DOCKED},
	 * {@link #STEER}, {@link #WHIRL}) and since when, and each blade in its place's order.
	 */
	public record Wing(long start, int shape, int mode, long modeStart, List<Blade> blades) {
		public static final Codec<Wing> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("start").forGetter(Wing::start),
				Codec.INT.optionalFieldOf("shape", FAN).forGetter(Wing::shape),
				Blade.CODEC.listOf().fieldOf("blades").forGetter(Wing::blades))
			.apply(i, (start, shape, blades) -> new Wing(start, shape, DOCKED, 0, blades)));
		public static final StreamCodec<RegistryFriendlyByteBuf, Wing> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Wing::start,
			ByteBufCodecs.VAR_INT, Wing::shape,
			ByteBufCodecs.VAR_INT, Wing::mode,
			ByteBufCodecs.VAR_LONG, Wing::modeStart,
			Blade.STREAM_CODEC.apply(ByteBufCodecs.list()), Wing::blades,
			Wing::new);

		/** How far out of its rift blade {@code i} is at game time {@code now}: 0 not yet, 1 hanging in its place. */
		public float out(int i, double now) {
			Blade blade = blades.get(i);
			double first = (now - start - i * EMERGE_GAP) / EMERGE_TICKS;
			double again = blade.back() == 0 ? 1.0 : (now - blade.back()) / EMERGE_TICKS + 1.0;
			return (float) Mth.clamp(Math.min(first, again), 0.0, 1.0);
		}

		Wing shaped(int shape) {
			return new Wing(start, shape, mode, modeStart, blades);
		}

		Wing doing(int mode, long since) {
			return new Wing(start, shape, mode, since, blades);
		}

		Wing with(List<Blade> blades) {
			return new Wing(start, shape, mode, modeStart, List.copyOf(blades));
		}
	}

	public static final AttachmentType<Wing> WING = AttachmentRegistry.create(JaymosPowers.id("arsenal_wing"),
		builder -> builder.persistent(Wing.CODEC).copyOnDeath().syncWith(Wing.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** When Sneak + V went down ({@code -1}: this hold has done its work already). */
	private static final Map<UUID, Long> PRESSED = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** When the next unasked blade may go, and the swing the last one answered. */
	private static final Map<UUID, Long> NEXT = new HashMap<>();
	private static final Map<UUID, Integer> ANSWERED = new HashMap<>();
	/** When Sneak + B went down ({@code -1}: steering). */
	private static final Map<UUID, Long> ORDERING = new HashMap<>();
	/** What each wing is set on (an entity id), and when its next blade may go. */
	private static final Map<UUID, Integer> HARRIED = new HashMap<>();
	private static final Map<UUID, Long> WHIRL_READY = new HashMap<>();
	/** What each holder's blades leave alone. */
	private static final Map<UUID, Set<UUID>> SPARED = new HashMap<>();
	/** Steering and whirling: when each thing (by entity id) was last cut by this holder's blades. */
	private static final Map<UUID, Map<Integer, Long>> CUT = new HashMap<>();

	/** Client → server: a pick on the order wheel (a formation, or one of the {@code ORDER_} slices). */
	public record Order(int choice) implements CustomPacketPayload {
		public static final Type<Order> TYPE = new Type<>(JaymosPowers.id("arsenal_wing_order"));
		public static final StreamCodec<ByteBuf, Order> CODEC = ByteBufCodecs.VAR_INT.map(Order::new, Order::choice);

		@Override
		public Type<Order> type() {
			return TYPE;
		}
	}

	private ArsenalWing() {
	}

	static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Order.TYPE, Order.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Order.TYPE, (payload, ctx) -> {
			ServerPlayer player = ctx.player();
			if (Powers.has(player, ArsenalPower.INSTANCE) && player.isAlive() && !player.isSpectator()) {
				order(player, payload.choice());
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer player && ward(player, source)));
	}

	/** How many blades answer: two, four once every ability is unlocked, six for the Armory and the Void Arsenal. */
	public static int count(Player player) {
		if (Fate.isAscended(player) || Fate.isCorrupted(player)) {
			return 6;
		}
		return Mastery.isUnlocked(player, PowerKey.ABILITY_4) ? 4 : 2;
	}

	/** The wing's key went down or up. True when it was the wing's (Sneak held as it went down), so Barrage and the cannons leave it be. */
	static boolean key(ServerPlayer player, boolean down) {
		UUID id = player.getUUID();
		if (down) {
			if (!player.isShiftKeyDown() || Mastery.starterOnly(player) || !Mastery.isUnlocked(player, PowerKey.ABILITY_3)) {
				return false;
			}
			PRESSED.put(id, player.level().getGameTime());
			return true;
		}
		Long pressed = PRESSED.remove(id);
		if (pressed == null) {
			return false;
		}
		if (pressed >= 0) {
			// let go early: the next formation, or a word on how to call them
			Wing wing = player.getAttached(WING);
			if (wing == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_hold" + (Fate.isCorrupted(player) ? ".corrupted" : "")).withStyle(ChatFormatting.GRAY));
			} else {
				shape(player, wing, (wing.shape() + 1) % SHAPES);
			}
		}
		return true;
	}

	private static void shape(ServerPlayer player, Wing wing, int shape) {
		player.setAttached(WING, wing.shaped(shape));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_shape." + shape).withStyle(ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** The wing's key held with no blades out: they come. (With blades out the hold opens the order wheel, on the client.) */
	private static void call(ServerPlayer player) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (player.getAttached(WING) != null) {
			return;
		}
		long ready = READY.getOrDefault(player.getUUID(), 0L);
		if (now < ready) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_recharging",
				String.format("%.1f", (ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.3F, 1.6F);
			return;
		}
		boolean corrupted = Fate.isCorrupted(player);
		List<Blade> blades = new ArrayList<>();
		int count = count(player);
		for (int i = 0; i < count; i++) {
			ItemStack item = ArsenalPool.randomBlade(player.getRandom());
			blades.add(new Blade(corrupted ? ArsenalVoid.voidTouched(item) : item, now + BLADE_TICKS + (long) i * EMERGE_GAP, 0, false));
		}
		player.setAttached(WING, new Wing(now, FAN, DOCKED, 0, List.copyOf(blades)));
		READY.put(player.getUUID(), now + COOLDOWN);
		Mastery.used(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.9F, 1.1F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.5F, 0.6F);
	}

	private static void dismiss(ServerPlayer player, Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		for (int i = 0; i < wing.blades().size(); i++) {
			if (wing.out(i, now) > 0.0F) {
				Summoned.dissolveEffects(level, slot(player, wing, i, now, 1.0F));
			}
			home(player, wing.blades().get(i));
		}
		player.removeAttached(WING);
		HARRIED.remove(player.getUUID());
	}

	/** A blade leaving the wing: one of the holder's own goes back into their pack (or at their feet). */
	private static void home(ServerPlayer player, Blade blade) {
		if (blade.own() && !blade.item().isEmpty()) {
			player.getInventory().placeItemBackInInventory(blade.item().copy(), Prediction.SERVER_ONLY);
		}
	}

	/** Every tick for the holder: the hold on Sneak + V, blades going home, and the ones that strike unasked. */
	static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long pressed = PRESSED.get(id);
		// with the blades out the client opens the wheel at HOLD_TICKS; a little sooner here, so the key coming up as
		// the screen opens is never taken for a tap
		boolean out = player.getAttached(WING) != null;
		if (pressed != null && pressed >= 0 && now - pressed >= HOLD_TICKS - (out ? 3 : 0)) {
			PRESSED.put(id, -1L);
			if (player.isAlive() && !player.isSpectator()) {
				call(player);
			}
		}
		Wing wing = player.getAttached(WING);
		if (wing == null || !player.isAlive()) {
			ArsenalWingMoves.clear(player);
			return;
		}
		List<Blade> kept = new ArrayList<>();
		for (int i = 0; i < wing.blades().size(); i++) {
			Blade blade = wing.blades().get(i);
			if (blade.own() || now < blade.expires() || now < blade.back()) {
				kept.add(blade);
			} else {
				Summoned.dissolveEffects(level, slot(player, wing, i, now, 1.0F));
			}
		}
		if (kept.isEmpty()) {
			player.removeAttached(WING);
			HARRIED.remove(id);
			return;
		}
		if (kept.size() != wing.blades().size()) {
			wing = wing.with(kept);
			player.setAttached(WING, wing);
		}

		// Sneak + B held: the blades fly where I look
		Long ordering = ORDERING.get(id);
		if (ordering != null && ordering >= 0 && now - ordering >= HOLD_TICKS && wing.mode() == DOCKED) {
			ORDERING.put(id, -1L);
			wing = wing.doing(STEER, now);
			player.setAttached(WING, wing);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.5F, 1.5F);
		}
		if (wing.mode() == STEER) {
			if (now - wing.modeStart() >= STEER_MAX) {
				commit(player, wing);
			} else {
				cut(player, wing, STEER_DAMAGE, STEER_HIT_GAP, true);
			}
			return;
		}
		if (wing.mode() == RING) {
			ArsenalWingMoves.tickRing(player, wing);
			return;
		}
		if (wing.mode() == WHIRL) {
			if (now - wing.modeStart() >= WHIRL_TICKS) {
				player.setAttached(WING, wing.doing(DOCKED, now));
			} else {
				cut(player, wing, WHIRL_DAMAGE, WHIRL_HIT_GAP, false);
				if ((now - wing.modeStart()) % 6 == 0) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.3F);
				}
			}
			return;
		}

		// my swing: one blade for each hit I land, at my lock-on target if I have one
		LivingEntity struck = player.getLastHurtMob();
		int swing = player.getLastHurtMobTimestamp();
		if (struck != null && player.tickCount - swing <= 1 && ANSWERED.getOrDefault(id, -1) != swing) {
			ANSWERED.put(id, swing);
			LivingEntity locked = LockOn.target(player);
			ArsenalWingMoves.swing(player, wing, locked != null ? locked : struck);
			return;
		}
		Integer harried = HARRIED.get(id);
		if (harried != null) {
			if (!(level.getEntity(harried) instanceof LivingEntity prey) || !fair(player, prey) || prey.distanceToSqr(player) > HARRY_RANGE * HARRY_RANGE) {
				HARRIED.remove(id);
			} else if (now >= NEXT.getOrDefault(id, 0L) && strike(player, wing, prey, HARRY_DAMAGE)) {
				NEXT.put(id, now + HARRY_GAP);
			}
			return;
		}
		if (now < NEXT.getOrDefault(id, 0L)) {
			return;
		}
		LivingEntity foe = grudge(player);
		int gap = GUARD_GAP;
		if (foe == null && count(player) < 4) {
			foe = nearestMonster(player);
			gap = WILD_GAP;
		}
		if (foe != null && strike(player, wing, foe, STRIKE_DAMAGE)) {
			NEXT.put(id, now + gap);
		}
	}

	/** Whatever hurt the holder lately and is still close. */
	private static @Nullable LivingEntity grudge(ServerPlayer player) {
		LivingEntity attacker = player.getLastHurtByMob();
		return attacker != null && player.tickCount - player.getLastHurtByMobTimestamp() < GRUDGE_TICKS
			&& attacker.distanceToSqr(player) < GUARD_RANGE * GUARD_RANGE && fair(player, attacker) ? attacker : null;
	}

	private static @Nullable LivingEntity nearestMonster(ServerPlayer player) {
		LivingEntity best = null;
		double bestDistance = WILD_RANGE * WILD_RANGE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WILD_RANGE),
			e -> e instanceof Enemy && fair(player, e) && player.hasLineOfSight(e))) {
			double distance = living.distanceToSqr(player);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = living;
			}
		}
		return best;
	}

	/** A thing the blades may strike: alive, in this world, not their holder and not the holder's own animal. */
	static boolean fair(ServerPlayer player, LivingEntity target) {
		return target != player && target.isAlive() && target.level() == player.level()
			&& !(target instanceof OwnableEntity owned && owned.getOwner() == player)
			&& !SPARED.getOrDefault(player.getUUID(), Set.of()).contains(target.getUUID());
	}

	/** What the wing is set on (Sneak + B), if it is still there. */
	static @Nullable LivingEntity harried(ServerPlayer player) {
		Integer harried = HARRIED.get(player.getUUID());
		return harried != null && player.level().getEntity(harried) instanceof LivingEntity living && fair(player, living) ? living : null;
	}

	/** The first blade hanging in its place flies at {@code target}. False when none is ready or the target isn't fair. */
	static boolean strike(ServerPlayer player, @Nullable Wing wing, LivingEntity target, float damage) {
		if (wing == null || !fair(player, target)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		for (int i = 0; i < wing.blades().size(); i++) {
			Blade blade = wing.blades().get(i);
			if (wing.out(i, now) < 1.0F || !blade.own() && now + RETURN_TICKS >= blade.expires()) {
				continue;
			}
			boolean corrupted = Fate.isCorrupted(player);
			Vec3 from = slot(player, wing, i, now, 1.0F);
			Vec3 to = target.getBoundingBox().getCenter();
			RiftBlade flying = RiftBlade.create(level, player, blade.item(), from, to, target, 1,
				(FirstTools.rock(blade.item()) ? Math.min(damage, ROCK_DAMAGE) : damage) * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F));
			// like one of the Hundred Blades: it lands whatever my own hit left of the target's guard, and is gone after
			flying.makeSkyBlade(corrupted);
			level.addFreshEntity(flying);
			MagicFx.stroke(level, from, to, 0.1F, corrupted ? 0x2A1840 : MagicFx.VOID_COLOR);
			level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.5F, 1.6F);
			List<Blade> blades = new ArrayList<>(wing.blades());
			blades.set(i, blade.away(now + RETURN_TICKS));
			player.setAttached(WING, wing.with(blades));
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- orders

	/** Sneak + B went down or up with the blades out. True when it was the wing's, so the chains and the sword leave it be. */
	static boolean orderKey(ServerPlayer player, boolean down) {
		UUID id = player.getUUID();
		if (down) {
			if (!player.isShiftKeyDown() || player.getAttached(WING) == null) {
				return false;
			}
			ORDERING.put(id, player.level().getGameTime());
			return true;
		}
		Long pressed = ORDERING.remove(id);
		if (pressed == null) {
			return false;
		}
		Wing wing = player.getAttached(WING);
		if (wing == null) {
			return true;
		}
		if (pressed < 0) {
			if (wing.mode() == STEER) {
				commit(player, wing);
			}
			return true;
		}
		// a tap: set them on what I look at, or call them off
		LivingEntity target = looked(player, HARRY_RANGE);
		if (target == null || Integer.valueOf(target.getId()).equals(HARRIED.get(id))) {
			boolean was = HARRIED.remove(id) != null;
			player.sendOverlayMessage(Component.translatable(was ? "message.jaymospowers.arsenal.wing_called_off" : "message.jaymospowers.arsenal.wing_no_target")
				.withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!fair(player, target)) {
			return true;
		}
		HARRIED.put(id, target.getId());
		NEXT.remove(id);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_harry", target.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.6F, 0.8F);
		return true;
	}

	/** Steering ends: the blades go back to their places, by way of whatever is nearest the spot they were sent to. */
	private static void commit(ServerPlayer player, Wing wing) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 spot = steerPoint(player, 1.0F);
		wing = wing.doing(DOCKED, now);
		// they have a way to come: each is back in its place a moment later
		List<Blade> blades = new ArrayList<>();
		for (Blade blade : wing.blades()) {
			blades.add(blade.back() > now ? blade : blade.away(now + EMERGE_TICKS));
		}
		player.setAttached(WING, wing.with(blades));
		LivingEntity best = null;
		double bestDistance = 3.0 * 3.0;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(spot, 6.0, 6.0, 6.0), e -> fair(player, e))) {
			double distance = living.getBoundingBox().getCenter().distanceToSqr(spot);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = living;
			}
		}
		if (best != null) {
			HARRIED.put(player.getUUID(), best.getId());
		}
	}

	/**
	 * The blades cut what they touch as they fly (steering: anything fair; whirling: only what is a foe), each thing
	 * once every {@code gap} ticks.
	 */
	private static void cut(ServerPlayer player, Wing wing, float damage, int gap, boolean anything) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Map<Integer, Long> last = CUT.computeIfAbsent(player.getUUID(), id -> new HashMap<>());
		last.values().removeIf(at -> now - at > 200);
		boolean corrupted = Fate.isCorrupted(player);
		DamageSource source = player.damageSources().playerAttack(player);
		for (int i = 0; i < wing.blades().size(); i++) {
			if (wing.blades().get(i).back() > now) {
				continue;
			}
			Vec3 at = slot(player, wing, i, now, 1.0F);
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(at, 1.8, 1.8, 1.8),
				e -> fair(player, e) && (anything || foe(player, e)))) {
				if (now - last.getOrDefault(living.getId(), -1000L) < gap) {
					continue;
				}
				last.put(living.getId(), now);
				if (living.hurtServer(level, source, damage * (corrupted ? ArsenalVoid.DAMAGE_MULTIPLIER : 1.0F))) {
					Vec3 away = living.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
					if (!anything && away.lengthSqr() > 1.0E-4) {
						away = away.normalize().scale(0.45);
						living.push(away.x, 0.15, away.z);
					}
					level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.2);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.5F, 1.4F);
				}
			}
		}
	}

	/** Something the blades go for unasked while whirling: a monster, or whatever is after their holder. */
	static boolean foe(ServerPlayer player, LivingEntity living) {
		return living instanceof Enemy || living instanceof Mob mob && mob.getTarget() == player || living == player.getLastHurtByMob()
			|| living == LockOn.target(player);
	}

	/** A pick on the order wheel. */
	private static void order(ServerPlayer player, int choice) {
		Wing wing = player.getAttached(WING);
		if (wing == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		switch (choice) {
			case FAN, HALO, FLANK -> shape(player, wing, choice);
			case ORDER_WHIRL -> {
				long ready = WHIRL_READY.getOrDefault(id, 0L);
				if (wing.mode() != DOCKED) {
					return;
				}
				if (now < ready) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_recharging",
						String.format("%.1f", (ready - now) / 20.0)).withStyle(ChatFormatting.GRAY));
					return;
				}
				WHIRL_READY.put(id, now + WHIRL_COOLDOWN);
				player.setAttached(WING, wing.doing(WHIRL, now));
			}
			case ORDER_SPARE -> {
				LivingEntity target = looked(player, HARRY_RANGE);
				if (target == null) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.arsenal.wing_no_target").withStyle(ChatFormatting.GRAY));
					return;
				}
				Set<UUID> spared = SPARED.computeIfAbsent(id, key -> new HashSet<>());
				boolean nowSpared = spared.add(target.getUUID());
				if (!nowSpared) {
					spared.remove(target.getUUID());
				} else if (Integer.valueOf(target.getId()).equals(HARRIED.get(id))) {
					HARRIED.remove(id);
				}
				player.sendOverlayMessage(Component.translatable(nowSpared ? "message.jaymospowers.arsenal.wing_spared" : "message.jaymospowers.arsenal.wing_unspared",
					target.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			case ORDER_TAKE -> take(player, wing);
			case ORDER_HOME -> dismiss(player, wing);
			case ORDER_STORM -> ArsenalStorm.start(player, now + ArsenalStorm.WHEEL_TICKS);
			case ORDER_RING -> ArsenalWingMoves.ring(player, wing);
			case ORDER_STEP -> ArsenalWingMoves.step(player, wing);
			default -> {
			}
		}
	}

	/** The first blade hanging in its place comes down into the holder's hand (a summoned one as a summon, on its own timer). */
	private static void take(ServerPlayer player, Wing wing) {
		long now = player.level().getGameTime();
		for (int i = 0; i < wing.blades().size(); i++) {
			Blade blade = wing.blades().get(i);
			if (wing.out(i, now) < 1.0F) {
				continue;
			}
			ItemStack stack = blade.item().copy();
			if (!blade.own() && !Summoned.is(stack)) {
				stack.set(Summoned.TYPE, new Summoned(player.getUUID(), blade.expires(), player.getRandom().nextInt()));
			}
			Summoned.dissolveEffects(player.level(), slot(player, wing, i, now, 1.0F));
			player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			if (!blade.own()) {
				ArsenalPower.capSummons(player);
			}
			List<Blade> blades = new ArrayList<>(wing.blades());
			blades.remove(i);
			if (blades.isEmpty()) {
				player.removeAttached(WING);
				HARRIED.remove(player.getUUID());
			} else {
				player.setAttached(WING, wing.with(blades));
			}
			return;
		}
	}

	/**
	 * G with the blades out: what the holder has in hand joins them, if there is a place free. A summoned weapon keeps
	 * its timer; a weapon or tool of their own, or one loose rock off the stack, stays until the wing goes home. True
	 * when something went up (so nothing is sent back this press).
	 */
	static boolean give(ServerPlayer player) {
		Wing wing = player.getAttached(WING);
		ItemStack held = player.getMainHandItem();
		if (wing == null || held.isEmpty() || wing.blades().size() >= count(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		Summoned summoned = Summoned.of(held);
		Blade blade;
		if (summoned != null) {
			if (!summoned.ownedBy(player.getUUID())) {
				return false;
			}
			blade = new Blade(held.copy(), summoned.expires(), now + EMERGE_TICKS, false);
			held.setCount(0);
		} else if (FirstTools.rock(held)) {
			blade = new Blade(held.split(1), Long.MAX_VALUE, now + EMERGE_TICKS, true);
		} else if (held.getMaxStackSize() == 1) {
			blade = new Blade(held.copy(), Long.MAX_VALUE, now + EMERGE_TICKS, true);
			held.setCount(0);
		} else {
			return false;
		}
		List<Blade> blades = new ArrayList<>(wing.blades());
		blades.add(blade);
		player.setAttached(WING, wing.with(blades));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.3F);
		return true;
	}

	/**
	 * Ward: a guard raised and a blade hanging ready, and whatever was coming at the holder from someone meets the blade
	 * instead. That blade goes home for it (one of their own, back to the pack). True when the blow was met.
	 */
	private static boolean ward(ServerPlayer player, DamageSource source) {
		Wing wing = player.getAttached(WING);
		if (wing == null || source.getEntity() == null || source.getEntity() == player || !Guard.raised(player)) {
			return false;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		for (int i = 0; i < wing.blades().size(); i++) {
			if (wing.out(i, now) < 1.0F) {
				continue;
			}
			Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
			Vec3 toward = from.position().subtract(player.position());
			Vec3 at = player.getEyePosition().add(toward.lengthSqr() < 1.0E-4 ? player.getLookAngle() : toward.normalize().scale(0.9));
			MagicFx.stroke(level, slot(player, wing, i, now, 1.0F), at, 0.1F, MagicFx.VOID_COLOR);
			level.sendParticles(MagicFx.CRACKLE, at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.1);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.9F, 1.5F);
			home(player, wing.blades().get(i));
			List<Blade> blades = new ArrayList<>(wing.blades());
			blades.remove(i);
			if (blades.isEmpty()) {
				player.removeAttached(WING);
				HARRIED.remove(player.getUUID());
			} else {
				player.setAttached(WING, wing.with(blades));
			}
			return true;
		}
		return false;
	}

	/** What the holder is looking at within {@code range} (their lock-on target first). */
	static @Nullable LivingEntity looked(ServerPlayer player, double range) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null) {
			return locked;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(range));
		BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5),
			e -> e instanceof LivingEntity && e.isPickable() && !e.isSpectator() && e.isAlive() && e != player, eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	/** Where steered blades fly to: the first block along the look, or the end of {@link #STEER_RANGE} (both sides). */
	public static Vec3 steerPoint(Player player, float partialTick) {
		Vec3 eye = player.getEyePosition(partialTick);
		Vec3 end = eye.add(player.getViewVector(partialTick).scale(STEER_RANGE));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(player.getViewVector(partialTick).scale(0.4));
	}

	/**
	 * Where blade {@code i} hangs at game time {@code now}, turning with its holder's view: a fan over the shoulders,
	 * a slow ring at chest height, or a column down each side.
	 */
	public static Vec3 slot(Player player, Wing wing, int i, double now, float partialTick) {
		int n = wing.blades().size();
		if (wing.mode() == WHIRL) {
			double angle = (now - wing.modeStart()) * 0.6 + i * Mth.TWO_PI / n;
			return player.getPosition(partialTick).add(Math.cos(angle) * WHIRL_RADIUS, 1.0 + Math.sin(angle * 0.5 + i) * 0.35, Math.sin(angle) * WHIRL_RADIUS);
		}
		if (wing.mode() == RING) {
			Vec3 ringed = ArsenalWingMoves.ringSlot(player, wing, i, now, partialTick);
			if (ringed != null) {
				return ringed;
			}
		}
		if (wing.mode() == STEER) {
			// a small ring round the spot I look at, flown out to from their places
			Vec3 look = player.getViewVector(partialTick);
			Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
			side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
			Vec3 over = side.cross(look).normalize();
			double angle = now * 0.3 + i * Mth.TWO_PI / n;
			Vec3 there = steerPoint(player, partialTick).add(side.scale(Math.cos(angle) * 0.6)).add(over.scale(Math.sin(angle) * 0.6));
			double mix = Mth.clamp((now - wing.modeStart()) / STEER_OUT_TICKS, 0.0, 1.0);
			return mix >= 1.0 ? there : slot(player, wing.doing(DOCKED, 0), i, now, partialTick).lerp(there, mix * mix * (3.0 - 2.0 * mix));
		}
		float yaw = player.getViewYRot(partialTick) * Mth.DEG_TO_RAD;
		Vec3 forward = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
		double across, up, ahead;
		switch (wing.shape()) {
			case HALO -> {
				double angle = now * 0.04 + i * Mth.TWO_PI / n;
				across = Math.cos(angle) * 1.5;
				ahead = Math.sin(angle) * 1.5;
				up = 1.3;
			}
			case FLANK -> {
				int row = i / 2;
				across = (i % 2 == 0 ? -1.1 : 1.1) - (i % 2 == 0 ? 1 : -1) * row * 0.15;
				ahead = 0.3 - row * 0.6;
				up = 1.1 + row * 0.35;
			}
			default -> {
				double t = n == 1 ? 0.0 : i / (n - 1.0) * 2.0 - 1.0;
				across = t * (0.6 + 0.2 * n);
				ahead = -0.8 + 0.25 * Math.abs(t);
				up = 2.2 - 0.55 * Math.abs(t);
			}
		}
		up += Math.sin(now * 0.08 + i * 1.7) * 0.06;
		return player.getPosition(partialTick).add(right.scale(across)).add(forward.scale(ahead)).add(0.0, up, 0.0);
	}

	/** The holder died: the rift's blades go home, as every summon does; their own stay with them. */
	static void onDeath(ServerPlayer player) {
		Wing wing = player.getAttached(WING);
		if (wing == null) {
			return;
		}
		List<Blade> own = wing.blades().stream().filter(Blade::own).toList();
		HARRIED.remove(player.getUUID());
		if (own.isEmpty()) {
			player.removeAttached(WING);
		} else {
			player.setAttached(WING, wing.doing(DOCKED, 0).with(own));
		}
	}

	/** The power went away: the blades with it (their holder's own back in the pack). */
	static void onLost(ServerPlayer player) {
		UUID id = player.getUUID();
		PRESSED.remove(id);
		READY.remove(id);
		NEXT.remove(id);
		ANSWERED.remove(id);
		ORDERING.remove(id);
		HARRIED.remove(id);
		WHIRL_READY.remove(id);
		SPARED.remove(id);
		CUT.remove(id);
		ArsenalWingMoves.onLost(player);
		Wing wing = player.getAttached(WING);
		if (wing != null) {
			wing.blades().forEach(blade -> home(player, blade));
		}
		player.removeAttached(WING);
	}
}
