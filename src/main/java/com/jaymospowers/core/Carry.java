package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.gravity.GravityWeight;
import com.jaymospowers.power.web.KitchenCompat;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.WindFlight;
import com.jaymospowers.power.wind.WindPower;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Carrying (anyone, any power): sneak + right-click a player or a small animal ({@link #CARRYABLE}, or any baby
 * animal) to pick it up. One at a time, and not with a Kitchen corpse in your arms. While carrying, right-click drops
 * it and sneak + right-click throws it a short way ahead (the same controls as Kitchen's corpses). A carried player
 * gets free with one jump unless they can't act ({@link #canStruggle}); an animal stays until let go.
 * <p>
 * There's no vanilla riding: every tick the carried body is put at the carrier's feet (the server moves animals; a
 * carried player's own client moves them, and every client draws the body in the hold, see {@code CarryPose}). Each
 * pickup rolls a hold ({@link #FIREMAN}, {@link #BRIDAL}; in Wind flight also {@link #SHOULDER}, {@link #BACK}).
 * <p>
 * A Gravity user ({@link GravityWeight}) carries any mob but a boss, with no slowdown; one too big for the arms goes up
 * {@link #OVERHEAD}. A carried mob's AI is on hold ({@code GravityCarriedMobMixin}).
 * <p>
 * A Wind user can also pick up a villager ({@link #VILLAGERS}: vanilla, MCA, Kingdoms folk) while off the ground, in
 * flight or mid-jump, and fly off with them like anything else they carry ({@link #windCarries}). On the ground, sneak +
 * right-click on a villager stays what it was (trades, quests, pickpocketing).
 * <p>
 * Single blocks can be carried too, held in front in both arms ({@link #BLOCK}, see {@link CarryBlock}).
 * <p>
 * So can a severed head or limb ({@link SeveredPart}), dangling from one hand ({@link #PART}): right-click it with an
 * empty hand (or sneak + right-click), the same controls to drop or throw it.
 * <p>
 * (0.14.1) And anyone downed ({@link Downed}), whatever they are. A downed player can't pick anything up, nor struggle free.
 * <p>
 * (0.17.7) Andy: "Picking players and mobs up is too sensitive, and players should not be able to pick up horses and
 * cows". A pick-up is a deliberate lift now: sneak + <i>hold</i> right-click for {@link #LIFT_TICKS} (a bar fills); let go
 * sooner, or look away, and nothing happens. Only a severed part and a Wind user's grab in the air stay a click. Holding
 * plain right-click on someone downed is the revive ({@link Downed}), and holding left-click executes them
 * ({@code Finishers}), so none of the three can be mistaken for another. Nobody lifts a big animal or anything bigger than a
 * person by hand ({@link #tooBig}): a downed horse or cow stays where it lies (Gravity still can).
 */
public final class Carry {
	/** The holds: over one shoulder, across both arms, held up by the shoulder at your side, slung over your back. */
	public static final int FIREMAN = 0, BRIDAL = 1, SHOULDER = 2, BACK = 3;
	/** Gravity only: a big mob held up above the head, weightless. Never rolled; kept through take-off and landing. */
	public static final int OVERHEAD = 4;
	/** A block ({@link CarryBlock}) held in both arms in front. Never rolled, and kept through take-off and landing. */
	public static final int BLOCK = 5;
	/** A severed head or limb ({@link SeveredPart}) dangling from the right hand. Never rolled, kept in flight. */
	public static final int PART = 6;
	/** (0.15.114) A severed part lying in a body is found this far past reach (blocks), its box grown this much to aim at. */
	static final double PART_REACH_SLACK = 0.5, PART_PICK_INFLATE = 0.15;
	/** On foot only the first two are rolled; in flight any of the four. */
	public static final int FOOT_HOLDS = 2, FLIGHT_HOLDS = 4;
	/** Weight: the carrier walks this much slower (share of their speed) with a person, or with a small animal. */
	static final double PLAYER_WEIGHT = 0.15, ANIMAL_WEIGHT = 0.05, BLOCK_WEIGHT = 0.12;
	/** A Wind user flying with someone: top speed × {@code FLIGHT_SPEED}, flight hunger × {@code FLIGHT_HUNGER}. */
	public static final double FLIGHT_SPEED = 0.8;
	public static final float FLIGHT_HUNGER = 1.3F;
	/** Further apart than this (blocks, plus {@code LEASH_PER_SPEED} per block/tick the carrier moves) and it ends. */
	static final double LEASH = 4.0, LEASH_PER_SPEED = 6.0;
	/** The carrier moving this far in one tick has been teleported: the carry ends. */
	static final double TELEPORT_JUMP = 8.0;
	/** A thrown body ragdolls this long (ticks). */
	public static final int THROWN_TICKS = 20;
	/** After any release, a player's movement isn't checked for this long (ticks), so they aren't kicked for it. */
	static final int RELEASE_GRACE_TICKS = 40;
	/** (0.17.7) How long sneak + use is held on someone to lift them (ticks), and the gap between use's repeats (every 4 ticks) that still counts as held. */
	public static final int LIFT_TICKS = 12, LIFT_GRACE = 8;
	/** (0.17.7) The lift's bar length, and how far past reach they may slip while it fills (blocks). */
	static final int LIFT_BAR = 8;
	static final double LIFT_SLACK = 0.5;
	/**
	 * (0.17.18) Andy: "you can lift players from way too far away. make it within 1 block". The most open air between your
	 * body and theirs (hitbox to hitbox, blocks) for a lift to start; it may grow by {@link #LIFT_SLACK} while the bar fills.
	 */
	static final double LIFT_GAP = 1.0;
	/** (0.17.7) Anything wider or taller than this standing (blocks) is too big to lift by hand: a person fits, a horse or an ogre doesn't. */
	static final float MAX_LIFT_WIDTH = 1.0F, MAX_LIFT_HEIGHT = 2.2F;
	/** After being let go, how long before the same one can be picked up again (ticks). */
	static final int REGRAB_TICKS = 20;
	/** Let go: a drop carries on with the carrier's momentum, nudged ahead; a throw goes this fast ahead and up (b/t). */
	static final double DROP_AHEAD = 0.12, DROP_UP = 0.08, THROW_AHEAD = 0.5, THROW_UP = 0.32;
	/** One jump frees a carried player: they spring off up and back (b/t). */
	static final double ESCAPE_UP = 0.42, ESCAPE_BACK = 0.3;
	/** A struggle that can't work (stunned, say) can't be retried for this long (ticks). */
	static final int STRUGGLE_COOLDOWN = 10;
	/** Below this jump strength (webbed down, say) you can't jump free. */
	static final double MIN_JUMP = 0.05;
	/**
	 * Where a body is let go, per hold, from the carrier's feet: {right, up, ahead} (blocks). If there's no room
	 * there, it's let go at the carrier's feet instead.
	 */
	private static final double[][] RELEASE_AT = {
		{0.35, 0.5, 0.25},
		{0.0, 0.4, 0.7},
		{0.8, -0.2, 0.0},
		{0.0, 0.3, -0.6},
		{0.0, 1.4, 0.7},
		{0.0, 0.6, 1.25},
		{0.36, 0.5, 0.3},
	};
	/** Overhead, it's let go this much further ahead per block of its width, so it clears you. */
	static final double OVERHEAD_RELEASE_PER_WIDTH = 0.5;
	/** Thrown from overhead it's heavy: it goes this fast ahead and up (b/t), a short, low heave. */
	static final double HEAVE_AHEAD = 0.38, HEAVE_UP = 0.18;
	static final Identifier WEIGHT_ID = JaymosPowers.id("carry_weight");

	/** Small animals anyone can pick up (wolves, cats, chickens...). Babies of any animal count too. */
	public static final TagKey<EntityType<?>> CARRYABLE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("carryable"));
	/** People a Wind user can carry off (any {@link AbstractVillager} counts too, so MCA's villagers do). */
	public static final TagKey<EntityType<?>> VILLAGERS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("villagers"));
	/** Wind's villager grab runs before other mods' sneak-use on villagers (quests, pickpockets), but only in the air. */
	private static final Identifier WIND_GRAB_PHASE = JaymosPowers.id("wind_grab");

	/** On a carrier: who they're carrying (entity id) and how. */
	public record Carrying(int target, int hold) {
		public static final StreamCodec<ByteBuf, Carrying> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Carrying::target,
			ByteBufCodecs.VAR_INT, Carrying::hold,
			Carrying::new);
	}

	/** On a carrier (synced to everyone, who draw the hold). Never saved: a relog ends it. */
	public static final AttachmentType<Carrying> CARRYING = AttachmentRegistry.create(JaymosPowers.id("carry_carrying"),
		builder -> builder.syncWith(Carrying.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On the one being carried: the carrier's entity id (synced to everyone). */
	public static final AttachmentType<Integer> CARRIED_BY = AttachmentRegistry.create(JaymosPowers.id("carry_carried_by"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** On a thrown body: when it was thrown (game time); it ragdolls for {@link #THROWN_TICKS}. Synced to everyone. */
	public static final AttachmentType<Long> THROWN = AttachmentRegistry.create(JaymosPowers.id("carry_thrown"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Client → server: the carrier lets go (right-click), or throws ({@code toss}: sneak + right-click). */
	public record Release(boolean toss) implements CustomPacketPayload {
		public static final Type<Release> TYPE = new Type<>(JaymosPowers.id("carry_release"));
		public static final StreamCodec<ByteBuf, Release> CODEC = ByteBufCodecs.BOOL.map(Release::new, Release::toss);

		@Override
		public Type<Release> type() {
			return TYPE;
		}
	}

	/** Client → server: a carried player jumps to get free. */
	public record Struggle() implements CustomPacketPayload {
		public static final Type<Struggle> TYPE = new Type<>(JaymosPowers.id("carry_struggle"));
		public static final StreamCodec<ByteBuf, Struggle> CODEC = StreamCodec.unit(new Struggle());

		@Override
		public Type<Struggle> type() {
			return TYPE;
		}
	}

	/**
	 * Server → client: you're now carried by this entity (or -1: you're free). Sent ahead of the release's teleport, so
	 * the client stops holding itself to the carrier before it's put down.
	 */
	public record Self(int carrier) implements CustomPacketPayload {
		public static final Type<Self> TYPE = new Type<>(JaymosPowers.id("carry_self"));
		public static final StreamCodec<ByteBuf, Self> CODEC = ByteBufCodecs.VAR_INT.map(Self::new, Self::carrier);

		@Override
		public Type<Self> type() {
			return TYPE;
		}
	}

	private enum Let {
		/** Set down where it is, no push (the carrier is gone, or they came apart). */
		SET_DOWN,
		/** (0.13.11) A carried block put into the spot the carrier is looking at ({@link Entry#placeAt}). */
		PLACE,
		DROP,
		THROW,
		ESCAPE
	}

	private static final class Entry {
		final UUID carrier;
		final Entity carried;
		int hold;
		boolean flying;
		Vec3 lastCarrierPos;
		/** Only a Gravity user could pick this up: if they stop being one, it's dropped. */
		final boolean gravityOnly;
		/** (0.13.11) Where a carried block is being put ({@link Let#PLACE}). */
		net.minecraft.core.@Nullable BlockPos placeAt;
		/** Only a Wind user could pick this up (a villager): if they stop being one, it's dropped. */
		final boolean windOnly;

		Entry(UUID carrier, Entity carried, int hold, boolean flying, Vec3 at, boolean gravityOnly, boolean windOnly) {
			this.carrier = carrier;
			this.carried = carried;
			this.hold = hold;
			this.flying = flying;
			this.lastCarrierPos = at;
			this.gravityOnly = gravityOnly;
			this.windOnly = windOnly;
		}
	}

	/** Carrier → what they carry. */
	private static final Map<UUID, Entry> CARRIES = new HashMap<>();
	/** Released players → until when their movement isn't checked (game time). */
	private static final Map<UUID, Long> GRACE = new HashMap<>();
	/** Let go → not before this game time can it be picked up again. Also a failed struggle's cooldown. */
	private static final Map<UUID, Long> REGRAB = new HashMap<>();
	private static final Map<UUID, Long> STRUGGLE_READY = new HashMap<>();

	/** (0.17.7) A lift being held: on whom, since when, and when use last came. */
	private static final class Lift {
		final Entity target;
		final long start;
		long last;

		Lift(Entity target, long start) {
			this.target = target;
			this.start = start;
			this.last = start;
		}
	}

	/** Player → the lift they're holding. Not saved. */
	private static final Map<UUID, Lift> LIFTING = new HashMap<>();

	private Carry() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Release.TYPE, Release.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Struggle.TYPE, Struggle.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Self.TYPE, Self.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Release.TYPE, (payload, ctx) -> release(ctx.player(), payload.toss()));
		ServerPlayNetworking.registerGlobalReceiver(Struggle.TYPE, (payload, ctx) -> struggle(ctx.player()));
		UseEntityCallback.EVENT.register(Carry::onUseEntity);
		UseEntityCallback.EVENT.addPhaseOrdering(WIND_GRAB_PHASE, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(WIND_GRAB_PHASE, (player, level, hand, target, hit) ->
			windCarries(player, target) ? onUseEntity(player, level, hand, target, hit) : InteractionResult.PASS);
		// a carried player's hands are held: no attacking, using, building or breaking
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isCarried(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> isCarried(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> isCarried(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> isCarried(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !isCarried(player));
		ServerTickEvents.END_SERVER_TICK.register(Carry::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> clear());
		CarryBlock.init();
	}

	// ---- queries (both sides, from the synced attachments)

	/** Someone is carrying this entity. */
	public static boolean isCarried(Entity entity) {
		return entity.getAttached(CARRIED_BY) != null;
	}

	/** This player is carrying someone (or something). */
	public static boolean isCarrying(Entity entity) {
		return entity.getAttached(CARRYING) != null;
	}

	/** Whoever is carrying this entity, if anyone (either side). */
	public static @Nullable Player carrierOf(Entity entity) {
		Integer id = entity.getAttached(CARRIED_BY);
		return id != null && entity.level().getEntity(id) instanceof Player player ? player : null;
	}

	/** The two are carrier and carried (either way round): they don't shove each other apart. */
	public static boolean linked(Entity a, Entity b) {
		Integer byA = a.getAttached(CARRIED_BY);
		if (byA != null && byA == b.getId()) {
			return true;
		}
		Integer byB = b.getAttached(CARRIED_BY);
		return byB != null && byB == a.getId();
	}

	/** Mid-throw: still ragdolling from being thrown. */
	public static boolean isThrown(Entity entity) {
		Long thrown = entity.getAttached(THROWN);
		return thrown != null && entity.level().getGameTime() - thrown < THROWN_TICKS;
	}

	/**
	 * Whether this could be picked up at all: a player, a small animal in the tag, a baby animal, or someone downed. (0.17.7)
	 * Never a grown animal out of the tag, downed or not (a horse, a cow), nor anything else {@link #tooBig}.
	 */
	public static boolean canBeCarried(Entity entity) {
		if (!entity.isAlive() || entity.isSpectator()) {
			return false;
		}
		if (entity instanceof Player || entity instanceof SeveredPart) {
			return true;
		}
		if (!(entity instanceof LivingEntity) || tooBig(entity)) {
			return false;
		}
		if (entity.getType().builtInRegistryHolder().is(CARRYABLE) || entity instanceof Animal animal && animal.isBaby()) {
			return true;
		}
		return Downed.isDowned(entity) && !(entity instanceof Animal);
	}

	/** (0.17.7) Too big for anyone's arms: wider or taller standing than {@link #MAX_LIFT_WIDTH} by {@link #MAX_LIFT_HEIGHT}. */
	public static boolean tooBig(Entity entity) {
		var size = entity.getType().getDimensions();
		return size.width() > MAX_LIFT_WIDTH || size.height() > MAX_LIFT_HEIGHT;
	}

	/**
	 * Whether this player could pick this up: what anyone can ({@link #canBeCarried}), or for a Gravity user any mob
	 * but a boss ({@link GravityWeight#liftable}), with an empty hand (so sneak-feeding, shearing and the like still
	 * work on them).
	 */
	public static boolean canBeCarriedBy(Player player, Entity entity) {
		return canBeCarried(entity) || GravityWeight.lightens(player) && player.getMainHandItem().isEmpty() && GravityWeight.liftable(entity)
			|| windCarries(player, entity);
	}

	/** A villager: vanilla's, a wandering trader, MCA's, or a Kingdoms settlement's folk. Carried as a person. */
	public static boolean isVillager(Entity entity) {
		return entity instanceof AbstractVillager || entity.getType().builtInRegistryHolder().is(VILLAGERS);
	}

	/** Whether this is carried in a person's holds (limp limbs), rather than cradled like an animal. */
	public static boolean person(@Nullable Entity entity) {
		return entity instanceof Player || entity != null && isVillager(entity);
	}

	/**
	 * Whether a Wind user can pick this villager up: off the ground (flying, or mid-jump), and the villager standing
	 * (not asleep in bed, or lying downed).
	 */
	public static boolean windCarries(Player player, Entity entity) {
		return Powers.of(player) == WindPower.INSTANCE && (!player.onGround() || flying(player))
			&& isVillager(entity) && entity.isAlive() && entity instanceof LivingEntity living && !living.isSleeping()
			&& living.getPose() == Pose.STANDING;
	}

	/** Whether this carrier is up in Wind flight (it changes which holds can be rolled). */
	public static boolean flying(Player player) {
		Integer state = player.getAttached(WindPower.FLIGHT);
		WindFlight flight = state == null ? null : WindFlight.byId(state);
		return flight != null && flight.flying();
	}

	/**
	 * Server: whether a carried player can act enough to jump free. Not while stunned or ragdolled: Gravity's held
	 * stun, held up by Lift, on the end of a web, still flying from a throw, webbed down (no jump), asleep.
	 */
	public static boolean canStruggle(ServerPlayer player) {
		// (0.15.8) lying limp from a crash is as helpless as being stunned
		if (!player.isAlive() || player.isSleeping() || Downed.isDowned(player) || KitchenCompat.isLimp(player)) {
			return false;
		}
		long now = player.level().getGameTime();
		GravityPower.Held held = player.getAttached(GravityPower.HELD);
		if (held != null && now < held.stunUntil()) {
			return false;
		}
		if (player.getAttached(GravityPower.LIFTED) != null || player.getAttached(WebPower.HELD) != null
			|| player.getAttached(WebPower.HITCH) != null || isThrown(player)) {
			return false;
		}
		AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump != null && jump.getValue() < MIN_JUMP) {
			return false;
		}
		// Time Flow's hit-stun
		return !com.jaymospowers.power.chrono.ChronoFlow.stunned(player);
	}

	/** Server: a carried (or just released) player moves however the carry puts them, without being kicked for it. */
	public static boolean relaxesMovement(ServerPlayer player) {
		if (isCarried(player)) {
			return true;
		}
		Long until = GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() < until;
	}

	// ---- picking up

	private static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (isCarried(player) || Downed.isDowned(player)) {
			return InteractionResult.FAIL;
		}
		Carrying already = player.getAttached(CARRYING);
		if (already != null && already.target() == target.getId()) {
			// (the same click seen twice)
			return InteractionResult.SUCCESS;
		}
		if (!(target instanceof SeveredPart) && (player.isShiftKeyDown() || player.getMainHandItem().isEmpty())) {
			// (0.15.114) Andy: "heads from decaptiations should be grabable like they are otherwise": a head that came to
			// rest in (or in front of) a body, its corpse or a finisher's, is what's taken, not the body
			SeveredPart part = partInView(player, target, hit);
			if (part != null) {
				target = part;
			}
		}
		// a severed part is taken with a plain right-click too, as long as the hand is empty
		boolean grab = player.isShiftKeyDown() || target instanceof SeveredPart && player.getMainHandItem().isEmpty();
		if (hand != InteractionHand.MAIN_HAND || !grab || player.isSpectator() || !canBeCarriedBy(player, target)) {
			return InteractionResult.PASS;
		}
		if (isCarrying(player) || KitchenCompat.isCarryingCorpse(player) || player.isPassenger() || player.isSleeping()) {
			return InteractionResult.PASS;
		}
		if (isCarried(target) || isCarrying(target) || target.isPassenger() || target.isVehicle()) {
			return InteractionResult.PASS;
		}
		if (!(target instanceof SeveredPart) && !windCarries(player, target) && liftGap(player, target) > LIFT_GAP) {
			// (0.17.18) too far off to lift: step in close first
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server && player instanceof ServerPlayer carrier) {
			Long ready = REGRAB.get(target.getUUID());
			if (ready != null && server.getGameTime() < ready) {
				return InteractionResult.FAIL;
			}
			if (target instanceof SeveredPart || windCarries(player, target)) {
				// a head or a limb is light, and a Wind user snatches someone up on the fly
				pickUp(carrier, target);
			} else {
				// (0.17.7) everyone and everything else is lifted with a hold (see lifts)
				long now = server.getGameTime();
				Lift lift = LIFTING.get(carrier.getUUID());
				if (lift != null && lift.target == target) {
					lift.last = now;
				} else {
					LIFTING.put(carrier.getUUID(), new Lift(target, now));
				}
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** (0.17.7) Each player's sneak + use held on someone: the bar fills, and full, they're picked up. */
	private static void lifts(MinecraftServer server) {
		if (LIFTING.isEmpty()) {
			return;
		}
		for (var it = LIFTING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Lift lift = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			Entity target = lift.target;
			if (player == null || !player.isAlive() || target.isRemoved() || !target.isAlive() || target.level() != player.level()) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			if (now - lift.last > LIFT_GRACE || !player.isShiftKeyDown() || liftGap(player, target) > LIFT_GAP + LIFT_SLACK) {
				// let go too soon, stood up, or they slipped away: nothing
				it.remove();
				continue;
			}
			long held = now - lift.start;
			if (held >= LIFT_TICKS) {
				it.remove();
				if (!isCarrying(player) && !isCarried(player) && !Downed.isDowned(player) && !KitchenCompat.isCarryingCorpse(player)
					&& !isCarried(target) && !isCarrying(target) && !target.isPassenger() && !target.isVehicle() && canBeCarriedBy(player, target)) {
					pickUp(player, target);
				}
				continue;
			}
			if (held >= 2) {
				int filled = Math.round(LIFT_BAR * held / (float) LIFT_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.carry.lift", target.getDisplayName(),
					"|".repeat(filled) + ".".repeat(LIFT_BAR - filled)).withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** (0.17.18) The open air between two hitboxes (blocks): 0 when they touch or overlap. */
	static double liftGap(Entity a, Entity b) {
		AABB x = a.getBoundingBox(), y = b.getBoundingBox();
		double dx = Math.max(0.0, Math.max(x.minX - y.maxX, y.minX - x.maxX));
		double dy = Math.max(0.0, Math.max(x.minY - y.maxY, y.minY - x.maxY));
		double dz = Math.max(0.0, Math.max(x.minZ - y.maxZ, y.minZ - x.maxZ));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/**
	 * (0.15.114) A severed part in line with {@code player}'s look and within reach, lying inside {@code clicked}'s box or
	 * nearer than where the click met it; null if none.
	 */
	static @Nullable SeveredPart partInView(Player player, Entity clicked, @Nullable EntityHitResult hit) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(player.entityInteractionRange() + PART_REACH_SLACK));
		double clickedAt = hit != null ? hit.getLocation().distanceToSqr(eye) : clicked.distanceToSqr(eye);
		AABB body = clicked.getBoundingBox().inflate(PART_REACH_SLACK);
		SeveredPart best = null;
		double bestAt = Double.MAX_VALUE;
		for (SeveredPart part : player.level().getEntitiesOfClass(SeveredPart.class, new AABB(eye, end).inflate(1.0), p -> !isCarried(p))) {
			var at = part.getBoundingBox().inflate(PART_PICK_INFLATE).clip(eye, end);
			if (at.isEmpty()) {
				continue;
			}
			double d = at.get().distanceToSqr(eye);
			if (d < bestAt && (d <= clickedAt || body.intersects(part.getBoundingBox()))) {
				best = part;
				bestAt = d;
			}
		}
		return best;
	}

	static void pickUp(ServerPlayer carrier, Entity target) {
		boolean flying = flying(carrier);
		boolean light = GravityWeight.lightens(carrier);
		boolean block = CarryBlock.is(target);
		boolean part = target instanceof SeveredPart;
		boolean overhead = light && !block && !part && GravityWeight.overhead(target);
		int hold = part ? PART : block ? BLOCK : overhead ? OVERHEAD : carrier.getRandom().nextInt(flying ? FLIGHT_HOLDS : FOOT_HOLDS);
		Entry entry = new Entry(carrier.getUUID(), target, hold, flying, carrier.position(), !block && !canBeCarried(target) && !windCarries(carrier, target),
			!block && !canBeCarried(target) && windCarries(carrier, target));
		CARRIES.put(carrier.getUUID(), entry);
		carrier.setAttached(CARRYING, new Carrying(target.getId(), hold));
		target.setAttached(CARRIED_BY, carrier.getId());
		target.removeAttached(THROWN);
		target.stopRiding();
		target.resetFallDistance();
		if (target instanceof ServerPlayer player) {
			player.stopUsingItem();
			ServerPlayNetworking.send(player, new Self(carrier.getId()));
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.carry.carried", carrier.getDisplayName()).withStyle(ChatFormatting.GRAY));
		} else if (target instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		carrier.sendOverlayMessage(Component.translatable(part ? "message.jaymospowers.carry.part" : block ? (CarryBlock.holdsContents(target) ? "message.jaymospowers.carry.container" : "message.jaymospowers.carry.block")
			: overhead ? "message.jaymospowers.carry.lifting" : "message.jaymospowers.carry.carrying")
			.withStyle(ChatFormatting.GRAY));
		AttributeInstance speed = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
		// a Gravity user feels no weight at all, and a head or a limb weighs nothing much
		if (speed != null && !light && !part) {
			speed.addOrUpdateTransientModifier(new AttributeModifier(WEIGHT_ID, -(person(target) ? PLAYER_WEIGHT : block ? BLOCK_WEIGHT : ANIMAL_WEIGHT),
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
		ServerLevel level = carrier.level();
		level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
		level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.5F, 0.6F);
		if (overhead) {
			// hauled up, and its weight drops away
			level.playSound(null, carrier.getX(), carrier.getY(), carrier.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.6F, 0.6F);
			level.playSound(null, carrier.getX(), carrier.getY() + 2.0, carrier.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.35F, 1.6F);
		}
	}

	// ---- letting go

	private static void release(ServerPlayer carrier, boolean toss) {
		Entry entry = CARRIES.get(carrier.getUUID());
		if (entry == null) {
			return;
		}
		if (!toss && CarryBlock.is(entry.carried)) {
			// (0.18.12) a space pod part looked at a pod goes onto the pod (the display is gone then, so nothing is put in the world)
			switch (com.jaymospowers.power.zfighter.pod.PodParts.tryAttach(carrier, entry.carried)) {
				case DONE -> {
					end(carrier, entry, Let.SET_DOWN);
					return;
				}
				case REFUSED -> {
					return;
				}
				default -> {
				}
			}
			// (0.13.11) a block goes straight into the free spot looked at; with no room there, it stays in your arms
			net.minecraft.core.BlockPos spot = CarryBlock.placeSpot(carrier, entry.carried);
			if (spot == null) {
				carrier.sendOverlayMessage(Component.translatable("message.jaymospowers.carry.no_room").withStyle(net.minecraft.ChatFormatting.GRAY));
				return;
			}
			entry.placeAt = spot;
			end(carrier, entry, Let.PLACE);
			return;
		}
		// a chest is only ever set down, never thrown
		end(carrier, entry, toss && !CarryBlock.holdsContents(entry.carried) ? Let.THROW : Let.DROP);
	}

	private static void struggle(ServerPlayer player) {
		Entry entry = null;
		for (Entry e : CARRIES.values()) {
			if (e.carried == player) {
				entry = e;
				break;
			}
		}
		if (entry == null) {
			// out of step: tell the client it's free
			ServerPlayNetworking.send(player, new Self(-1));
			return;
		}
		long now = player.level().getGameTime();
		if (now < STRUGGLE_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		if (!canStruggle(player)) {
			STRUGGLE_READY.put(player.getUUID(), now + STRUGGLE_COOLDOWN);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.carry.cant_struggle").withStyle(ChatFormatting.GRAY));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F, 0.5F);
			return;
		}
		end(player.level().getServer().getPlayerList().getPlayer(entry.carrier), entry, Let.ESCAPE);
	}

	/** The player {@code carrier} has in their arms, if it's a player. */
	public static @Nullable ServerPlayer carriedPlayer(ServerPlayer carrier) {
		Entry entry = CARRIES.get(carrier.getUUID());
		return entry != null && entry.carried instanceof ServerPlayer player ? player : null;
	}

	/** The carrier loses their grip: whatever they hold drops with their momentum, as if let go. */
	public static void slip(ServerPlayer carrier) {
		Entry entry = CARRIES.get(carrier.getUUID());
		if (entry != null) {
			end(carrier, entry, Let.DROP);
		}
	}

	/** Ends a carry: forgets it, lifts the weight, and puts the body down the way it was let go. */
	private static void end(@Nullable ServerPlayer carrier, Entry entry, Let how) {
		CARRIES.remove(entry.carrier);
		Entity carried = entry.carried;
		if (carrier != null) {
			carrier.removeAttached(CARRYING);
			AttributeInstance speed = carrier.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(WEIGHT_ID);
			}
		}
		carried.removeAttached(CARRIED_BY);
		if (carried.isRemoved() || !carried.isAlive() || !(carried.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		REGRAB.put(carried.getUUID(), now + REGRAB_TICKS);
		Vec3 at = carried.position();
		Vec3 velocity = Vec3.ZERO;
		if (carrier != null && carrier.level() == level && how != Let.SET_DOWN) {
			float yaw = carrier.getYRot() * Mth.DEG_TO_RAD;
			Vec3 ahead = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
			Vec3 right = new Vec3(-Mth.cos(yaw), 0.0, -Mth.sin(yaw));
			Vec3 momentum = carrier.position().subtract(entry.lastCarrierPos);
			if (momentum.lengthSqr() > TELEPORT_JUMP * TELEPORT_JUMP) {
				momentum = Vec3.ZERO;
			}
			double[] offset = RELEASE_AT[Math.clamp(entry.hold, 0, RELEASE_AT.length - 1)];
			double forward = offset[2] + (entry.hold == OVERHEAD ? carried.getBbWidth() * OVERHEAD_RELEASE_PER_WIDTH : 0.0);
			at = carrier.position().add(right.scale(offset[0])).add(0.0, offset[1], 0.0).add(ahead.scale(forward));
			AABB box = carried.getBoundingBox().move(at.subtract(carried.position()));
			if (!level.noCollision(carried, box)) {
				at = carrier.position();
			}
			velocity = switch (how) {
				case DROP -> momentum.add(ahead.scale(DROP_AHEAD)).add(0.0, DROP_UP, 0.0);
				case THROW -> entry.hold == OVERHEAD
					? momentum.multiply(1.0, 0.0, 1.0).add(ahead.scale(HEAVE_AHEAD)).add(0.0, HEAVE_UP, 0.0)
					: momentum.multiply(1.0, 0.0, 1.0).add(ahead.scale(THROW_AHEAD)).add(0.0, THROW_UP, 0.0);
				case ESCAPE -> momentum.multiply(1.0, 0.0, 1.0).add(ahead.scale(-ESCAPE_BACK)).add(0.0, ESCAPE_UP, 0.0);
				default -> Vec3.ZERO;
			};
			if (how == Let.ESCAPE) {
				at = carrier.position().add(ahead.scale(-0.4)).add(0.0, 0.2, 0.0);
				if (!level.noCollision(carried, carried.getBoundingBox().move(at.subtract(carried.position())))) {
					at = carrier.position();
				}
			}
		}
		if (how == Let.THROW) {
			carried.setAttached(THROWN, now);
		}
		carried.resetFallDistance();
		if (CarryBlock.is(carried)) {
			// it goes back into the world: into the spot looked at, or as a falling block ahead of the carrier
			at = how == Let.PLACE && entry.placeAt != null && carrier != null
				? CarryBlock.place(level, carried, carrier, entry.placeAt)
				: CarryBlock.land(level, carried, carrier != null && carrier.level() == level && how != Let.SET_DOWN ? carrier : null, velocity);
		} else if (carried instanceof ServerPlayer player) {
			GRACE.put(player.getUUID(), now + RELEASE_GRACE_TICKS);
			ServerPlayNetworking.send(player, new Self(-1));
			player.connection.teleport(new PositionMoveRotation(at, velocity, player.getYRot(), player.getXRot()), Set.of());
		} else {
			carried.setPos(at.x, at.y, at.z);
			carried.setDeltaMovement(velocity);
			carried.syncVelocity = true;
		}
		if (how == Let.THROW && entry.hold == OVERHEAD) {
			// a heave: a heavy grunt of a swing, and the weight coming back as it leaves your hands
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 0.55F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.5F, 0.7F);
		}
		switch (how) {
			case THROW -> level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 0.8F);
			case DROP -> level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.6F, 1.0F);
			case ESCAPE -> level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.6F, 1.2F);
			default -> {
			}
		}
	}

	// ---- every tick

	private static void tick(MinecraftServer server) {
		lifts(server);
		if (!CARRIES.isEmpty()) {
			for (Entry entry : List.copyOf(CARRIES.values())) {
				tickCarry(server, entry);
			}
		}
		if (!GRACE.isEmpty() || !REGRAB.isEmpty() || !STRUGGLE_READY.isEmpty()) {
			long now = server.overworld().getGameTime();
			GRACE.values().removeIf(until -> until < now);
			REGRAB.values().removeIf(until -> until < now);
			STRUGGLE_READY.values().removeIf(until -> until < now);
		}
	}

	private static void tickCarry(MinecraftServer server, Entry entry) {
		ServerPlayer carrier = server.getPlayerList().getPlayer(entry.carrier);
		Entity carried = entry.carried;
		if (carrier == null || carried.isRemoved() || !carried.isAlive()) {
			end(carrier, entry, Let.SET_DOWN);
			return;
		}
		if (!carrier.isAlive() || carrier.isSpectator() || carrier.isSleeping() || carrier.isPassenger()
			|| entry.gravityOnly && !GravityWeight.lightens(carrier) || entry.windOnly && Powers.of(carrier) != WindPower.INSTANCE) {
			end(carrier, entry, Let.DROP);
			return;
		}
		Vec3 here = carrier.position();
		double moved = here.distanceTo(entry.lastCarrierPos);
		if (carried.level() != carrier.level() || moved > TELEPORT_JUMP
			|| carried.position().distanceTo(here) > LEASH + moved * LEASH_PER_SPEED
			|| carried.isPassenger() || carried instanceof Player p && (p.isSleeping() || p.isSpectator())) {
			// teleported, changed dimension, or came apart some other way
			end(carrier, entry, Let.SET_DOWN);
			return;
		}
		entry.lastCarrierPos = here;
		carried.resetFallDistance();
		if (!(carried instanceof Player) && !(carried instanceof SeveredPart)) {
			// (a severed part holds itself in the hand, see SeveredPart#tick) the server holds animals at the carrier's feet (each client draws them in the hold)
			carried.setPos(here.x, here.y, here.z);
			carried.setDeltaMovement(Vec3.ZERO);
			carried.setYRot(carrier.getYRot());
			if (carried instanceof LivingEntity living) {
				living.yBodyRot = carrier.yBodyRot;
			}
			if (carried instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		// take off and every hold is open (a new roll); land with a flight-only hold and it shifts to one for walking
		boolean flying = flying(carrier);
		if (flying != entry.flying && entry.hold != OVERHEAD && entry.hold != BLOCK && entry.hold != PART) {
			entry.flying = flying;
			if (flying) {
				entry.hold = carrier.getRandom().nextInt(FLIGHT_HOLDS);
			} else if (entry.hold >= FOOT_HOLDS) {
				entry.hold = carrier.getRandom().nextInt(FOOT_HOLDS);
			}
			carrier.setAttached(CARRYING, new Carrying(carried.getId(), entry.hold));
			carrier.level().playSound(null, here.x, here.y, here.z, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F, 1.1F);
		}
	}

	/** Server stopping: forget everything. */
	private static void clear() {
		for (Entry entry : new ArrayList<>(CARRIES.values())) {
			entry.carried.removeAttached(CARRIED_BY);
		}
		CARRIES.clear();
		LIFTING.clear();
		GRACE.clear();
		REGRAB.clear();
		STRUGGLE_READY.clear();
	}
}
