package com.jaymoskingdoms.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.305) Jaymo's Equestrian, stage 3: the herd (ideas from Icy's Better Horses, our own code). Every horse is a mare
 * or a stallion ({@link Herd}), and a foal needs one of each. Its owner gives it orders from the command wheel (hold the
 * whistle key while looking at it): follow, stay, wander here, set home, go home ({@link Herd.Order}), once it trusts
 * them enough. An owner's horses come to their aid when something attacks them (a brave horse, one that trusts them),
 * rearing and kicking at the attacker. The roster (the wheel's last slice) lists every horse a player owns, wherever it
 * is, and can call one to them or send it home from anywhere: a horse in an unloaded place is woken for a moment to go.
 * Tuning numbers live at the top.
 */
public final class Herding {
	/** Its owner needs this much trust for it to take orders, and for it to fight for them. */
	public static final float ORDER_TRUST = 40.0F, DEFEND_TRUST = 50.0F;
	/** How close an owner must be to give an order from the wheel. */
	public static final double ORDER_RANGE = 24.0;
	/** A following horse keeps within this of its owner, and is brought along when it falls this far behind. */
	static final double FOLLOW_NEAR = 5.0, FOLLOW_CATCH_UP = 28.0;
	/** A horse told to wander strays this far from its spot at most. */
	public static final double WANDER_RADIUS = 10.0;
	/** Going home: it walks there if it's this close, else it's brought there (out of sight). */
	static final double HOME_WALK = 64.0;
	/** Paces: following, going home, charging an attacker. */
	static final double FOLLOW_SPEED = 1.6, HOME_SPEED = 1.3, DEFEND_SPEED = 2.0;
	/** Defending: how near its owner it must be to hear the fight, how long it keeps at it, its reach and how often it strikes. */
	static final double DEFEND_RANGE = 16.0, DEFEND_REACH = 2.6;
	static final int DEFEND_TICKS = 300, STRIKE_TICKS = 30;
	/** A strike's damage, more per war level above the first; a brave horse fights (bravery this much at least). */
	static final float STRIKE_DAMAGE = 5.0F, STRIKE_PER_WAR_LEVEL = 1.0F;
	static final double DEFEND_BRAVERY = 0.35;
	/** The roster: how long a horse in an unloaded place is woken for, waiting for it to load (ticks). */
	static final int FETCH_TICKS = 200;
	/** Called from the roster: it comes from anywhere, but only once it trusts you this much. */
	public static final float ROSTER_CALL_TRUST = Bonding.WHISTLE_TRUST;

	/** Its sex, orders and home. Saved; every client sees it. */
	public static final AttachmentType<Herd> HERD = AttachmentRegistry.create(JaymosKingdoms.id("herd"),
		builder -> builder.persistent(Herd.CODEC).syncWith(Herd.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Where an owned horse was last seen, for its owner's roster (kept on the Overworld, by horse). */
	public record Stall(UUID owner, String name, String breed, int sex, GlobalPos pos, int order, boolean foal, long seen) {
		public static final Codec<Stall> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.STRING_CODEC.fieldOf("owner").forGetter(Stall::owner),
				Codec.STRING.optionalFieldOf("name", "").forGetter(Stall::name),
				Codec.STRING.optionalFieldOf("breed", "palfrey").forGetter(Stall::breed),
				Codec.INT.optionalFieldOf("sex", 0).forGetter(Stall::sex),
				GlobalPos.CODEC.fieldOf("pos").forGetter(Stall::pos),
				Codec.INT.optionalFieldOf("order", 0).forGetter(Stall::order),
				Codec.BOOL.optionalFieldOf("foal", false).forGetter(Stall::foal),
				Codec.LONG.optionalFieldOf("seen", 0L).forGetter(Stall::seen))
			.apply(i, Stall::new));
	}

	static final AttachmentType<Map<UUID, Stall>> ROSTER = AttachmentRegistry.create(JaymosKingdoms.id("horse_roster"),
		builder -> builder.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Stall.CODEC)));

	/** What the roster can do with a horse. */
	public static final int CALL = 0, SEND_HOME = 1;

	/** A horse that came to its owner's aid: whom it's after, and until when; and when it may strike again. */
	private record Fight(int target, long until) {
	}

	private static final Map<Integer, Fight> FIGHTS = new HashMap<>();
	private static final Map<Integer, Long> STRUCK = new HashMap<>();

	/** A horse woken from an unloaded place by the roster: what's wanted of it, by whom, and till when to wait. */
	private record Fetch(UUID horse, UUID player, int act, ResourceKey<Level> dimension, ChunkPos chunk, long until) {
	}

	private static final List<Fetch> FETCHES = new ArrayList<>();

	private Herding() {
	}

	// ---- packets -----------------------------------------------------------------------------------------------------

	/** Client → server: an order from the command wheel for horse {@code horse} ({@link Herd.Order} ordinal; {@code -1} sets its home here). */
	public record Command(int horse, int order) implements CustomPacketPayload {
		public static final int SET_HOME = -1;
		public static final Type<Command> TYPE = new Type<>(JaymosKingdoms.id("horse_command"));
		public static final StreamCodec<FriendlyByteBuf, Command> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.horse);
			buf.writeVarInt(p.order);
		}, buf -> new Command(buf.readVarInt(), buf.readVarInt()));

		@Override
		public Type<Command> type() {
			return TYPE;
		}
	}

	/** Client → server: show me my horses. */
	public record AskRoster() implements CustomPacketPayload {
		public static final Type<AskRoster> TYPE = new Type<>(JaymosKingdoms.id("ask_horse_roster"));
		public static final StreamCodec<FriendlyByteBuf, AskRoster> CODEC = StreamCodec.unit(new AskRoster());

		@Override
		public Type<AskRoster> type() {
			return TYPE;
		}
	}

	/** One horse on the roster: its name ("" for its breed's), breed, sex, where it is, its order, whether it's a foal, and whether it's about now. */
	public record Entry(UUID id, String name, String breed, int sex, Identifier dimension, BlockPos pos, int order, boolean foal, boolean here) {
		static void write(FriendlyByteBuf buf, Entry e) {
			buf.writeUUID(e.id);
			buf.writeUtf(e.name);
			buf.writeUtf(e.breed);
			buf.writeVarInt(e.sex);
			buf.writeIdentifier(e.dimension);
			buf.writeBlockPos(e.pos);
			buf.writeVarInt(e.order);
			buf.writeBoolean(e.foal);
			buf.writeBoolean(e.here);
		}

		static Entry read(FriendlyByteBuf buf) {
			return new Entry(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readIdentifier(), buf.readBlockPos(), buf.readVarInt(),
				buf.readBoolean(), buf.readBoolean());
		}
	}

	/** Server → client: your horses. */
	public record Roster(List<Entry> horses) implements CustomPacketPayload {
		public static final Type<Roster> TYPE = new Type<>(JaymosKingdoms.id("horse_roster"));
		public static final StreamCodec<FriendlyByteBuf, Roster> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeVarInt(p.horses.size());
			for (Entry e : p.horses) {
				Entry.write(buf, e);
			}
		}, buf -> {
			int n = buf.readVarInt();
			List<Entry> horses = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				horses.add(Entry.read(buf));
			}
			return new Roster(horses);
		});

		@Override
		public Type<Roster> type() {
			return TYPE;
		}
	}

	/** Client → server: from the roster, call horse {@code horse} here ({@link #CALL}) or send it home ({@link #SEND_HOME}). */
	public record RosterAct(UUID horse, int act) implements CustomPacketPayload {
		public static final Type<RosterAct> TYPE = new Type<>(JaymosKingdoms.id("horse_roster_act"));
		public static final StreamCodec<FriendlyByteBuf, RosterAct> CODEC = CustomPacketPayload.codec((p, buf) -> {
			buf.writeUUID(p.horse);
			buf.writeVarInt(p.act);
		}, buf -> new RosterAct(buf.readUUID(), buf.readVarInt()));

		@Override
		public Type<RosterAct> type() {
			return TYPE;
		}
	}

	// ---- setup -------------------------------------------------------------------------------------------------------

	public static void register() {
		JaymosKingdoms.LOGGER.debug("herd attachments: {} {}", HERD, ROSTER);
		PayloadTypeRegistry.serverboundPlay().register(Command.TYPE, Command.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AskRoster.TYPE, AskRoster.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(RosterAct.TYPE, RosterAct.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Roster.TYPE, Roster.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Command.TYPE, (payload, context) -> command(context.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(AskRoster.TYPE, (payload, context) -> sendRoster(context.player()));
		ServerPlayNetworking.registerGlobalReceiver(RosterAct.TYPE, (payload, context) -> rosterAct(context.player(), payload));

		// a horse first loaded gets its sex; an owned one is put on its owner's roster
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractHorse horse && Equestrian.covered(horse)) {
				herd(horse);
				Equine e = horse.getAttached(Equestrian.EQUINE);
				if (e != null) {
					// (a horse from before 0.7.305 gets the draft tag too)
					Equestrian.draftTag(horse, e);
				}
				note(level, horse);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.isAlive()) {
				note(level, horse);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof AbstractHorse horse && Equestrian.covered(horse) && horse.level() instanceof ServerLevel level) {
				forget(level.getServer(), horse.getUUID());
			}
		});
		// something set on a player: their horses come to their aid
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, dealt, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof ServerPlayer player && source.getEntity() instanceof LivingEntity attacker && attacker != player
				&& player.level() instanceof ServerLevel level) {
				defend(level, player, attacker);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(Herding::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FIGHTS.clear();
			STRUCK.clear();
			FETCHES.clear();
		});
	}

	// ---- reading -----------------------------------------------------------------------------------------------------

	/** Its herd data (its sex rolled now if it has none yet, on the server). */
	public static Herd herd(AbstractHorse horse) {
		Herd h = horse.getAttached(HERD);
		if (h == null) {
			h = Herd.NONE;
		}
		if (h.sex() == Herd.UNKNOWN && !horse.level().isClientSide()) {
			h = h.withSex(horse.getRandom().nextBoolean() ? Herd.MARE : Herd.STALLION);
			horse.setAttached(HERD, h);
		}
		return h;
	}

	public static @Nullable Herd peek(Entity e) {
		return e instanceof AbstractHorse horse ? horse.getAttached(HERD) : null;
	}

	/** (From {@code HorseMixin}) two horses of a sex don't make a foal. */
	public static boolean pair(AbstractHorse a, AbstractHorse b) {
		if (!Equestrian.covered(a) || !Equestrian.covered(b)) {
			return true;
		}
		return herd(a).sex() != herd(b).sex();
	}

	/** (From {@link Equestrian#foal}) a foal is a filly or a colt. */
	static void born(AbstractHorse baby, RandomSource random) {
		baby.setAttached(HERD, Herd.NONE.withSex(random.nextBoolean() ? Herd.MARE : Herd.STALLION));
	}

	// ---- orders ------------------------------------------------------------------------------------------------------

	private static void command(ServerPlayer player, Command payload) {
		ServerLevel level = player.level();
		if (!(level.getEntity(payload.horse()) instanceof AbstractHorse horse) || !Equestrian.covered(horse) || !horse.isAlive()
			|| !horse.isTamed() || horse.distanceTo(player) > ORDER_RANGE && player.getVehicle() != horse) {
			return;
		}
		if (!Bonding.owner(horse, player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.order_not_yours", Bonding.name(horse)));
			return;
		}
		if (Bonding.trust(horse, player) < ORDER_TRUST) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.order_wary", Bonding.name(horse), (int)ORDER_TRUST));
			return;
		}
		Herd h = herd(horse);
		if (payload.order() == Command.SET_HOME) {
			horse.setAttached(HERD, h.withHome(Optional.of(GlobalPos.of(level.dimension(), horse.blockPosition()))));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.home_set", Bonding.name(horse)));
			level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.1F);
			note(level, horse);
			return;
		}
		Herd.Order order = Herd.Order.byId(payload.order());
		if (order == Herd.Order.HOME) {
			sendHome(level, horse, player);
			return;
		}
		give(level, horse, order);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.order_given", Bonding.name(horse), Component.translatable(order.key())));
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 0.8F, 1.0F);
	}

	/** {@code horse} now does {@code order} (staying and wandering round where it stands). */
	static void give(ServerLevel level, AbstractHorse horse, Herd.Order order) {
		Optional<BlockPos> anchor = order == Herd.Order.STAY || order == Herd.Order.WANDER ? Optional.of(horse.blockPosition()) : Optional.empty();
		horse.setAttached(HERD, herd(horse).withOrder(order, anchor));
		horse.getNavigation().stop();
		if (order == Herd.Order.FOLLOW || order == Herd.Order.STAY) {
			// it's had its orders: no wandering off with a lead or a love
			horse.setPersistenceRequired();
		}
		note(level, horse);
	}

	/** It sets off home: walking if it's near, else brought there; with no home it says so. */
	static void sendHome(ServerLevel level, AbstractHorse horse, @Nullable ServerPlayer player) {
		Herd h = herd(horse);
		if (h.home().isEmpty()) {
			if (player != null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.no_home", Bonding.name(horse)));
			}
			return;
		}
		GlobalPos home = h.home().get();
		ServerLevel there = level.getServer().getLevel(home.dimension());
		if (there == null) {
			return;
		}
		horse.ejectPassengers();
		if (there == level && horse.blockPosition().closerThan(home.pos(), HOME_WALK)) {
			horse.setAttached(HERD, h.withOrder(Herd.Order.HOME, Optional.of(home.pos())));
			horse.getNavigation().moveTo(home.pos().getX() + 0.5, home.pos().getY(), home.pos().getZ() + 0.5, HOME_SPEED);
		} else {
			bring(horse, there, Vec3.atBottomCenterOf(home.pos()));
			if (horse.isAlive()) {
				AbstractHorse moved = horse;
				if (there != level && there.getEntity(horse.getUUID()) instanceof AbstractHorse found) {
					moved = found;
				}
				moved.setAttached(HERD, herd(moved).withOrder(Herd.Order.WANDER, Optional.of(home.pos())));
				note(there, moved);
			}
		}
		if (player != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.going_home", Bonding.name(horse)));
		}
	}

	/** Moves {@code horse} to {@code at} in {@code level} (out of anyone's sight: a call from far off, a trip home). */
	static void bring(AbstractHorse horse, ServerLevel level, Vec3 at) {
		horse.ejectPassengers();
		horse.stopRiding();
		if (horse.isLeashed()) {
			horse.dropLeash();
		}
		horse.getNavigation().stop();
		horse.teleportTo(level, at.x, at.y, at.z, Set.of(), horse.getYRot(), 0.0F, true);
	}

	/** The goal every horse gets ({@code AbstractHorseMixin}): its owner's orders, and fighting for them; above its own wandering. */
	public static final class OrderGoal extends Goal {
		private final AbstractHorse horse;
		private int repath;

		public OrderGoal(AbstractHorse horse) {
			this.horse = horse;
			setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
		}

		@Override
		public boolean canUse() {
			if (this.horse.isVehicle() || this.horse.isPassenger() || this.horse.isLeashed() || !this.horse.isTamed()) {
				return false;
			}
			if (fightTarget(this.horse) != null) {
				return true;
			}
			Herd h = this.horse.getAttached(HERD);
			if (h == null) {
				return false;
			}
			return switch (h.orderOf()) {
				case FOLLOW -> {
					Player owner = owner(this.horse);
					yield owner != null && this.horse.distanceTo(owner) > FOLLOW_NEAR;
				}
				case STAY, HOME -> true;
				case WANDER -> h.anchor().isPresent() && !h.anchor().get().closerToCenterThan(this.horse.position(), WANDER_RADIUS);
				case NONE -> false;
			};
		}

		@Override
		public boolean canContinueToUse() {
			return canUse();
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			this.horse.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = fightTarget(this.horse);
			if (target != null) {
				fight(this.horse, target);
				return;
			}
			Herd h = this.horse.getAttached(HERD);
			if (h == null) {
				return;
			}
			switch (h.orderOf()) {
				case FOLLOW -> {
					Player owner = owner(this.horse);
					if (owner == null) {
						return;
					}
					if (this.horse.distanceTo(owner) > FOLLOW_CATCH_UP && owner.onGround() && this.horse.level() instanceof ServerLevel level) {
						// fallen far behind: it catches up out of sight, as a tamed wolf does
						Vec3 behind = owner.position().subtract(owner.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(3.0));
						bring(this.horse, level, behind);
						return;
					}
					if (--this.repath <= 0) {
						this.repath = 10;
						this.horse.getNavigation().moveTo(owner, FOLLOW_SPEED);
					}
					this.horse.getLookControl().setLookAt(owner);
				}
				case STAY -> {
					this.horse.getNavigation().stop();
					this.horse.setDeltaMovement(this.horse.getDeltaMovement().multiply(0.0, 1.0, 0.0));
				}
				case WANDER -> h.anchor().ifPresent(at -> {
					if (--this.repath <= 0) {
						this.repath = 20;
						this.horse.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1.0);
					}
				});
				case HOME -> {
					BlockPos home = h.anchor().orElse(null);
					if (home == null || home.closerToCenterThan(this.horse.position(), 3.0)) {
						// there: it grazes about home from now on
						this.horse.setAttached(HERD, h.withOrder(Herd.Order.WANDER, h.home().map(GlobalPos::pos)));
						return;
					}
					if (--this.repath <= 0) {
						this.repath = 20;
						if (!this.horse.getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, HOME_SPEED)
							&& this.horse.level() instanceof ServerLevel level) {
							// no way through: it finds its own way (out of sight)
							bring(this.horse, level, Vec3.atBottomCenterOf(home));
						}
					}
				}
				default -> {
				}
			}
		}
	}

	/** Its owner, if they're about in its world. */
	static @Nullable Player owner(AbstractHorse horse) {
		var ref = horse.getOwnerReference();
		if (ref == null) {
			return null;
		}
		Player p = horse.level().getPlayerByUUID(ref.getUUID());
		return p != null && p.isAlive() && !p.isSpectator() ? p : null;
	}

	// ---- defending -----------------------------------------------------------------------------------------------------

	/** {@code attacker} struck {@code player}: their brave, trusting horses close by (not told to stay) go for it. */
	static void defend(ServerLevel level, ServerPlayer player, LivingEntity attacker) {
		if (attacker instanceof AbstractHorse || attacker.isPassengerOfSameVehicle(player)) {
			return;
		}
		long now = level.getGameTime();
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(DEFEND_RANGE),
			h -> Equestrian.covered(h) && h.isAlive() && h.isTamed() && !h.isBaby() && !h.isVehicle() && Bonding.owner(h, player))) {
			Herd h = herd(horse);
			if (h.orderOf() == Herd.Order.STAY || Bonding.trust(horse, player) < DEFEND_TRUST) {
				continue;
			}
			if (!Equestrian.undead(horse) && Bonding.bravery(horse, Equestrian.equine(horse)) < DEFEND_BRAVERY) {
				continue;
			}
			Fight was = FIGHTS.put(horse.getId(), new Fight(attacker.getId(), now + DEFEND_TICKS));
			if (was == null || was.target != attacker.getId()) {
				level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.2F, 0.9F);
			}
		}
	}

	private static @Nullable LivingEntity fightTarget(AbstractHorse horse) {
		Fight f = FIGHTS.get(horse.getId());
		if (f == null) {
			return null;
		}
		if (f.until < horse.level().getGameTime() || !(horse.level().getEntity(f.target) instanceof LivingEntity target) || !target.isAlive()
			|| target.distanceTo(horse) > DEFEND_RANGE * 2.0) {
			FIGHTS.remove(horse.getId());
			return null;
		}
		return target;
	}

	/** Runs at its target, and rears and strikes at it once it's in reach. */
	private static void fight(AbstractHorse horse, LivingEntity target) {
		if (!(horse.level() instanceof ServerLevel level)) {
			return;
		}
		horse.getLookControl().setLookAt(target);
		double reach = DEFEND_REACH + horse.getBbWidth() * 0.5;
		if (horse.distanceTo(target) > reach) {
			horse.getNavigation().moveTo(target, DEFEND_SPEED);
			return;
		}
		horse.getNavigation().stop();
		long now = level.getGameTime();
		Long next = STRUCK.get(horse.getId());
		if (next != null && next > now) {
			return;
		}
		STRUCK.put(horse.getId(), now + STRIKE_TICKS);
		horse.standIfPossible();
		Bond b = horse.getAttached(Bonding.BOND);
		int war = b == null ? 1 : b.level(Discipline.WAR);
		float damage = STRIKE_DAMAGE + STRIKE_PER_WAR_LEVEL * (war - 1);
		DamageSource source = level.damageSources().mobAttack(horse);
		target.hurtServer(level, source, damage);
		Vec3 away = target.position().subtract(horse.position()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSqr() > 1.0E-4) {
			away = away.normalize();
			target.knockback(0.8, -away.x, -away.z, source, damage);
		}
		level.playSound(null, horse.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}

	// ---- the roster ------------------------------------------------------------------------------------------------------

	private static Map<UUID, Stall> stalls(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ROSTER, Map.of());
	}

	/** Puts down where an owned horse is now (or takes a horse no one owns any more off the roster). */
	static void note(ServerLevel level, AbstractHorse horse) {
		var ref = horse.getOwnerReference();
		Map<UUID, Stall> all = stalls(level.getServer());
		if (!horse.isTamed() || ref == null) {
			if (all.containsKey(horse.getUUID())) {
				forget(level.getServer(), horse.getUUID());
			}
			return;
		}
		Equine e = horse.getAttached(Equestrian.EQUINE);
		Herd h = herd(horse);
		String name = horse.hasCustomName() ? horse.getCustomName().getString() : "";
		Stall stall = new Stall(ref.getUUID(), name, e == null ? Breed.PALFREY.id() : e.breed().id(), h.sex(),
			GlobalPos.of(level.dimension(), horse.blockPosition()), h.order(), horse.isBaby(), level.getGameTime());
		Stall was = all.get(horse.getUUID());
		if (stall.equals(was) || was != null && sameButTime(was, stall) && stall.seen - was.seen < 200L) {
			return;
		}
		Map<UUID, Stall> map = new HashMap<>(all);
		map.put(horse.getUUID(), stall);
		level.getServer().overworld().setAttached(ROSTER, Map.copyOf(map));
	}

	private static boolean sameButTime(Stall a, Stall b) {
		return a.owner.equals(b.owner) && a.name.equals(b.name) && a.breed.equals(b.breed) && a.sex == b.sex && a.order == b.order && a.foal == b.foal
			&& a.pos.dimension().equals(b.pos.dimension()) && a.pos.pos().closerThan(b.pos.pos(), 8.0);
	}

	static void forget(MinecraftServer server, UUID horse) {
		Map<UUID, Stall> all = stalls(server);
		if (all.containsKey(horse)) {
			Map<UUID, Stall> map = new HashMap<>(all);
			map.remove(horse);
			server.overworld().setAttached(ROSTER, Map.copyOf(map));
		}
	}

	/** Sends {@code player} their horses, the nearest first, those about now with their up-to-date place. */
	static void sendRoster(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		List<Entry> list = new ArrayList<>();
		for (Map.Entry<UUID, Stall> e : stalls(server).entrySet()) {
			Stall s = e.getValue();
			if (!s.owner.equals(player.getUUID())) {
				continue;
			}
			ServerLevel level = server.getLevel(s.pos.dimension());
			boolean here = level != null && level.getEntity(e.getKey()) instanceof AbstractHorse h && h.isAlive();
			BlockPos pos = s.pos.pos();
			int order = s.order;
			if (here && level.getEntity(e.getKey()) instanceof AbstractHorse h) {
				pos = h.blockPosition();
				order = herd(h).order();
			}
			list.add(new Entry(e.getKey(), s.name, s.breed, s.sex, s.pos.dimension().identifier(), pos, order, s.foal, here));
		}
		Vec3 at = player.position();
		Identifier dim = player.level().dimension().identifier();
		list.sort((a, b) -> Double.compare(distance(a, dim, at), distance(b, dim, at)));
		ServerPlayNetworking.send(player, new Roster(list));
	}

	private static double distance(Entry e, Identifier dim, Vec3 at) {
		return (e.dimension().equals(dim) ? 0.0 : 1.0E9) + Vec3.atBottomCenterOf(e.pos()).distanceToSqr(at);
	}

	private static void rosterAct(ServerPlayer player, RosterAct payload) {
		MinecraftServer server = player.level().getServer();
		Stall s = stalls(server).get(payload.horse());
		if (s == null || !s.owner.equals(player.getUUID())) {
			return;
		}
		ServerLevel level = server.getLevel(s.pos.dimension());
		if (level == null) {
			return;
		}
		if (level.getEntity(payload.horse()) instanceof AbstractHorse horse) {
			act(player, horse, payload.act());
			sendRoster(player);
			return;
		}
		// not about: wake the place it was last seen for a moment, and see to it when it's loaded
		ChunkPos chunk = ChunkPos.containing(s.pos.pos());
		level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, chunk, 1);
		FETCHES.add(new Fetch(payload.horse(), player.getUUID(), payload.act(), s.pos.dimension(), chunk, level.getGameTime() + FETCH_TICKS));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.roster_fetching"));
	}

	/** What the roster asked of {@code horse}: come to its owner (once it trusts them enough), or go home. */
	private static void act(ServerPlayer player, AbstractHorse horse, int act) {
		if (!Bonding.owner(horse, player) || !horse.isAlive()) {
			return;
		}
		if (horse.isVehicle() && !horse.hasPassenger(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.roster_ridden", Bonding.name(horse)));
			return;
		}
		ServerLevel level = (ServerLevel)horse.level();
		if (act == SEND_HOME) {
			sendHome(level, horse, player);
			return;
		}
		if (Bonding.trust(horse, player) < ROSTER_CALL_TRUST || com.jaymoskingdoms.Stables.boarded(horse)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.whistle_shy"));
			return;
		}
		if (level == player.level() && horse.distanceTo(player) < Bonding.WHISTLE_RANGE) {
			// close enough to hear: it comes as to a whistle
			horse.getNavigation().moveTo(player, Bonding.WHISTLE_SPEED);
		} else {
			Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			Vec3 at = player.position().subtract(look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize().scale(3.0));
			bring(horse, player.level(), at);
		}
		if (player.level().getEntity(horse.getUUID()) instanceof AbstractHorse arrived) {
			arrived.setAttached(HERD, herd(arrived).withOrder(Herd.Order.NONE, Optional.empty()));
			note(player.level(), arrived);
			player.level().sendParticles(ParticleTypes.CLOUD, arrived.getX(), arrived.getY() + 0.5, arrived.getZ(), 8, 0.4, 0.3, 0.4, 0.02);
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.NEUTRAL, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.roster_coming", Bonding.name(horse)));
	}

	// ---- ticking -------------------------------------------------------------------------------------------------------

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		if (!FETCHES.isEmpty() && now % 10 == 0) {
			MinecraftServer server = level.getServer();
			for (Iterator<Fetch> it = FETCHES.iterator(); it.hasNext(); ) {
				Fetch f = it.next();
				if (!f.dimension.equals(level.dimension())) {
					continue;
				}
				ServerPlayer player = server.getPlayerList().getPlayer(f.player);
				if (level.getEntity(f.horse) instanceof AbstractHorse horse) {
					if (player != null) {
						act(player, horse, f.act);
						sendRoster(player);
					}
					it.remove();
				} else if (f.until < now) {
					if (player != null) {
						player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.roster_lost"));
					}
					it.remove();
				}
			}
		}
		if (now % 100 == 0) {
			for (ServerPlayer player : level.players()) {
				for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(128.0),
					h -> Equestrian.covered(h) && h.isAlive() && h.isTamed())) {
					note(level, horse);
				}
			}
		}
		if (now % 1200 == 0) {
			FIGHTS.values().removeIf(f -> f.until < now);
			STRUCK.values().removeIf(t -> t < now);
		}
	}

	/** Its sex shown as a symbol (♀ ♂), for the stats and the roster. */
	public static String symbol(int sex) {
		return sex == Herd.MARE ? "♀" : sex == Herd.STALLION ? "♂" : "";
	}

	/** How far {@code a} is from {@code b} in blocks, flat. */
	public static int flat(BlockPos a, Vec3 b) {
		return Mth.floor(Math.sqrt(Mth.square(a.getX() + 0.5 - b.x) + Mth.square(a.getZ() + 0.5 - b.z)));
	}

	static ResourceKey<Level> dimension(Identifier id) {
		return ResourceKey.create(Registries.DIMENSION, id);
	}
}
