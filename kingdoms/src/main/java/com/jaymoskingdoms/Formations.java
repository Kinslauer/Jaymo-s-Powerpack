package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Formations and commands (medieval pick gb-formations), and the shield wall (me-shieldwall; lore "The realms' ways":
 * "the militia lock shields in a wall with the archers behind"). From the orders screen ({@code FollowersScreen}) or the
 * quick-order keys ({@code QuickOrderKeys}) a player puts everyone of theirs near ({@link Followers#takesOrders}) into a
 * {@link #LINE} (a shield wall in front of them, archers in the rank behind), a {@link #WEDGE} (behind them, its point
 * at their back: a leader of theirs takes it, {@link Ranks#leads}), a {@link #SQUARE} (round them, facing out, the bows
 * inside) or a {@link #COLUMN} (two abreast behind). Then:
 * <ul>
 * <li>{@link #PLACE}: the formation goes where they point and holds there, facing the way they look.</li>
 * <li>{@link #HOLD}: it holds where it stands; {@link #FOLLOW}: it comes with them again.</li>
 * <li>{@link #CHARGE}: everyone breaks ranks and goes for the foes ahead; they fall in again when none is left.</li>
 * <li>{@link #SET_ON}: the one looked at (or the nearest of them) goes for whoever the player points at.</li>
 * <li>{@link #BREAK}: ranks broken, they follow as before.</li>
 * </ul>
 * In ranks a fighter meets what comes at their place and doesn't chase past {@link #ENGAGE}. In a line the front rank
 * with shields turn to the nearest foe and lock their shields up ({@link MobShields#snap}) until it's at their blade;
 * the archers behind shoot over them. {@link #drill} is the same drill for a realm's war parties ({@link WarParties}):
 * a squad in column behind its leader on the march, a shield wall at the siege. Formations are kept in memory, as war
 * parties are: after a restart one's ranks go about their day.
 */
public final class Formations {
	public static final int LINE = 0, WEDGE = 1, SQUARE = 2, COLUMN = 3;
	/** The orders besides the four shapes ({@link Give#kind}). */
	public static final int PLACE = 4, HOLD = 5, FOLLOW = 6, CHARGE = 7, SET_ON = 8, BREAK = 9, CYCLE = 10, POINT = 11;
	static final String[] SHAPES = {"line", "wedge", "square", "column"};
	/** Between two in a rank (blocks); the archers' rank stands this far behind the last of the shields. */
	static final double SPACING = 1.6, BACK_RANK = 2.6;
	/** A rank holds this many abreast before another forms behind it. */
	static final int LINE_WIDTH = 9;
	/** Following, a line forms this far in front of its player, a wedge or column this far behind. */
	static final double LEAD_OFFSET = 3.0;
	/** How far a player can point a formation to, and a target. */
	static final double PLACE_REACH = 64.0, MARK_REACH = 48.0;
	/** A fighter in ranks takes on a foe within this of their place; one farther off they let come. */
	static final double ENGAGE = 3.5;
	/** A line turns to a foe this near; its shields go up for one this near, and down to strike one this near. */
	static final double WALL_SIGHT = 16.0, SHIELD_RANGE = 10.0, STRIKE = 2.6;
	/** An archer shooting keeps this near their place; a fighter is at their place this near (blocks). */
	static final double ARCHER_LEASH = 2.0, AT_PLACE = 0.9;
	/** Charging, each looks for a foe this near. */
	static final double CHARGE_RANGE = 24.0;
	/** Paces: falling in, and charging. */
	static final float WALK = 0.7F, RUN = 1.0F;
	/** How often ranks are kept (ticks), and how long a locked shield is held each time. */
	static final int EVERY = 5, SHIELD_TICKS = 15;
	/** A place's ground is looked for at these heights from its own (a step up at most: never onto a roof), then this far along its rank (blocks). */
	static final int[] HEIGHTS = {0, -1, 1, -2, -3};
	static final double[] SHIFTS = {0.0, 1.0, -1.0, 2.0, -2.0};
	/** Home squads: how often they're kept (ticks), how many follow a leader, how near him they're gathered and how near a player he must be; moving faster than this (squared, a tick) he's walking. */
	static final int HOME_EVERY = 10, HOME_SQUAD = 5;
	static final double HOME_NEAR = 12.0, HOME_ROUND = 48.0, MOVING = 0.003;

	/** One player's formation. */
	static final class Formation {
		final UUID by;
		int shape;
		final List<Villager> members = new ArrayList<>();
		/** Holding a place ({@link #anchor}, facing {@link #yaw}), else following. */
		boolean held, charging;
		Vec3 anchor = Vec3.ZERO;
		float yaw;
		/** Members sent at someone ({@link #SET_ON}): free of their places while that one stands. */
		final Set<UUID> loosed = new HashSet<>();

		Formation(UUID by, int shape) {
			this.by = by;
			this.shape = shape;
		}
	}

	/** Client → server: an order of formation ({@link #LINE} to {@link #POINT}); {@code target}, the villager the screen is on (-1 for none). */
	public record Give(int kind, int target) implements CustomPacketPayload {
		public static final Type<Give> TYPE = new Type<>(JaymosKingdoms.id("formation_order"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Give> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Give::kind, ByteBufCodecs.VAR_INT, Give::target, Give::new);

		@Override
		public Type<Give> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Formation> FORMED = new HashMap<>();
	private static final Map<UUID, Formation> MEMBER_OF = new HashMap<>();

	private Formations() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Give.TYPE, Give.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(Formations::homeSquads);
		if (!McaCompat.LOADED) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(Give.TYPE, (payload, context) -> give(context.player(), payload.kind(), payload.target()));
		ServerTickEvents.END_SERVER_TICK.register(Formations::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FORMED.clear();
			MEMBER_OF.clear();
		});
	}

	/** (Followers#take) {@code v} was given another order: out of their ranks. */
	static void leave(Villager v) {
		Formation f = MEMBER_OF.remove(v.getUUID());
		if (f != null) {
			f.members.remove(v);
			f.loosed.remove(v.getUUID());
		}
	}

	/** (QuickOrders' Go in a fight) {@code player}'s ranks break to take a foe each, and fall in again after. */
	static void loose(ServerPlayer player) {
		Formation f = FORMED.get(player.getUUID());
		if (f != null) {
			f.charging = true;
		}
	}

	// ---- orders ------------------------------------------------------------------------------------------------------

	private static void give(ServerPlayer player, int kind, int target) {
		if (kind < LINE || kind > POINT || !player.isAlive() || player.isSpectator() || !TreeLocks.check(player, TreeLocks.Lock.ORDERS)) {
			return;
		}
		// (class trees) an order given: the Warlord's
		ClassTrees.giveEvery(player, Classes.PlayerClass.WARLORD, ClassTrees.XP_ORDER, "order", 100L);
		ServerLevel level = player.level();
		Formation f = FORMED.get(player.getUUID());
		if (kind == POINT) {
			kind = mark(player) != null ? SET_ON : PLACE;
		}
		if (kind == CYCLE) {
			kind = f == null ? LINE : (f.shape + 1) % SHAPES.length;
		}
		if (kind == SET_ON) {
			setOn(level, player, f, target);
			return;
		}
		if (kind <= COLUMN || f == null && (kind == PLACE || kind == HOLD)) {
			f = form(level, player, kind <= COLUMN ? kind : LINE, f);
			if (f == null) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.followers.none").withStyle(ChatFormatting.GRAY));
				return;
			}
		}
		if (f == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.formation.none").withStyle(ChatFormatting.GRAY));
			return;
		}
		String said = "order.form";
		switch (kind) {
			case PLACE -> {
				HitResult hit = player.pick(PLACE_REACH, 1.0F, false);
				if (!(hit instanceof BlockHitResult block) || hit.getType() == HitResult.Type.MISS) {
					return;
				}
				f.anchor = Vec3.atBottomCenterOf(block.getBlockPos().relative(block.getDirection()));
				f.yaw = player.getYRot();
				f.held = true;
				said = "order.place";
			}
			case HOLD -> {
				f.held = true;
				said = "order.place";
			}
			case FOLLOW -> f.held = false;
			case CHARGE -> {
				f.charging = true;
				charge(level, f);
				said = "order.charge";
			}
			case BREAK -> {
				for (Villager v : List.copyOf(f.members)) {
					Followers.take(v, player, Followers.FOLLOW);
				}
				FORMED.remove(player.getUUID());
				return;
			}
			default -> {
			}
		}
		Villager speaker = speaker(f, player);
		if (speaker != null) {
			Callouts.say(speaker, said, player, true);
		}
	}

	/** {@code player}'s people near fall into {@code shape} (joining {@code f}, if they have one): null if nobody would. */
	private static @Nullable Formation form(ServerLevel level, ServerPlayer player, int shape, @Nullable Formation f) {
		List<Villager> yours = new ArrayList<>();
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Presence.reach(player, Followers.ALL_RANGE)),
			v -> !v.isBaby() && !v.isSleeping() && !Downed.isDowned(v) && Followers.takesOrders(v, player))) {
			if (!Languages.understands(player, v)) {
				Languages.shrug(player, v);
				continue;
			}
			if (Possession.held(v) || Murmur.ignores(v, player) || Commandments.refusesCommand(v, player, "follow")) {
				continue;
			}
			yours.add(v);
		}
		if (f == null) {
			if (yours.isEmpty()) {
				return null;
			}
			f = new Formation(player.getUUID(), shape);
			f.anchor = player.position();
			f.yaw = player.getYRot();
			FORMED.put(player.getUUID(), f);
		}
		f.shape = shape;
		for (Villager v : yours) {
			if (!f.members.contains(v)) {
				// (out of any other order, free to walk: their place is kept for them)
				Followers.take(v, player, Followers.FREE);
				f.members.add(v);
				MEMBER_OF.put(v.getUUID(), f);
			}
		}
		sort(f.members);
		return f.members.isEmpty() ? null : f;
	}

	/** Leaders first (a wedge's point, a line's middle), then the shields, then the rest of the blades, the bows last. */
	private static void sort(List<? extends Mob> members) {
		members.sort(Comparator.comparingInt(Formations::order));
	}

	private static int order(Mob m) {
		if (archer(m)) {
			return 3;
		}
		if (Ranks.leads(m)) {
			return 0;
		}
		return m.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) ? 1 : 2;
	}

	/** {@code m} fights with a bow or crossbow. */
	static boolean archer(Mob m) {
		return m.getMainHandItem().getItem() instanceof ProjectileWeaponItem;
	}

	/** Who answers an order: a leader in the ranks, else the one nearest. */
	private static @Nullable Villager speaker(Formation f, ServerPlayer player) {
		Villager best = null;
		for (Villager v : f.members) {
			if (Ranks.leads(v)) {
				return v;
			}
			if (best == null || v.distanceToSqr(player) < best.distanceToSqr(player)) {
				best = v;
			}
		}
		return best;
	}

	/** Whoever {@code player} points at, within {@link #MARK_REACH}, if they're a fair foe ({@link #fair}); null if not. */
	private static @Nullable LivingEntity mark(ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getViewVector(1.0F).scale(MARK_REACH));
		HitResult block = player.pick(MARK_REACH, 1.0F, false);
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && !Followers.takesOrders(e, player), eye.distanceToSqr(end));
		return hit != null && hit.getEntity() instanceof LivingEntity l && fair(player, l) ? l : null;
	}

	/**
	 * Whether {@code player}'s people may be set on {@code e}: a monster; anyone already fighting them or theirs; a realm's
	 * soldier whose realm is at war with theirs. Never a villager, a guard, someone's pet or another player otherwise
	 * (a player only when the server allows fighting and the two are at it already).
	 */
	static boolean fair(ServerPlayer player, LivingEntity e) {
		if (Followers.takesOrders(e, player)) {
			return false;
		}
		LivingEntity aims = e instanceof Mob m ? Comrades.target(m) : null;
		boolean fighting = player.getLastHurtMob() == e || player.getLastHurtByMob() == e
			|| aims == player || aims != null && Followers.takesOrders(aims, player);
		if (e instanceof net.minecraft.world.entity.player.Player) {
			return fighting && player.level().isPvpAllowed();
		}
		if (fighting) {
			return true;
		}
		if (e instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerReference() != null) {
			return false;
		}
		if (e instanceof Worker w && w.factionId() >= 0) {
			MinecraftServer server = player.level().getServer();
			Faction mine = Factions.of(server, player.getUUID()), theirs = Factions.get(server, w.factionId());
			return mine != null && theirs != null && Relations.atWar(mine, theirs);
		}
		return e instanceof Enemy && !(e instanceof Villager);
	}

	/** Whether {@code v} will go at {@code foe} on {@code player}'s word: the same refusals an order to form meets. */
	private static boolean heeds(Villager v, ServerPlayer player, LivingEntity foe) {
		return !Possession.held(v) && !Murmur.ignores(v, player) && !Commandments.refusesCommand(v, player, "follow") && !Commandments.spares(v, foe);
	}

	/** The one looked at in the screen ({@code target}), else the nearest of {@code player}'s, goes for whoever they point at. */
	private static void setOn(ServerLevel level, ServerPlayer player, @Nullable Formation f, int target) {
		LivingEntity foe = mark(player);
		if (foe == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.formation.no_mark").withStyle(ChatFormatting.GRAY));
			return;
		}
		Villager sent = null;
		if (target >= 0 && level.getEntity(target) instanceof Villager v && Followers.takesOrders(v, player) && v.distanceToSqr(player) < Followers.ALL_RANGE * Followers.ALL_RANGE) {
			sent = v;
		} else {
			List<Villager> pool = f != null ? f.members : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(Followers.ALL_RANGE),
				v -> !v.isBaby() && !Downed.isDowned(v) && Followers.takesOrders(v, player));
			for (Villager v : pool) {
				if (sent == null || v.distanceToSqr(foe) < sent.distanceToSqr(foe)) {
					sent = v;
				}
			}
		}
		if (sent == null || !Languages.understands(player, sent) || !heeds(sent, player, foe)) {
			return;
		}
		Comrades.aim(sent, foe);
		Formation in = MEMBER_OF.get(sent.getUUID());
		if (in != null) {
			in.loosed.add(sent.getUUID());
		}
		Callouts.say(sent, "order.seton", player, true);
	}

	/** Every member goes for the nearest foe ahead of the formation. */
	private static void charge(ServerLevel level, Formation f) {
		for (Villager v : f.members) {
			LivingEntity foe = foeNear(level, v.position(), CHARGE_RANGE, v);
			if (foe != null) {
				Comrades.aim(v, foe);
			}
		}
	}

	// ---- keeping ranks -----------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (FORMED.isEmpty() || server.getTickCount() % EVERY != 0) {
			return;
		}
		for (Iterator<Formation> it = FORMED.values().iterator(); it.hasNext();) {
			Formation f = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(f.by);
			f.members.removeIf(v -> {
				boolean gone = !v.isAlive() || v.isRemoved() || player == null || v.level() != player.level() || !Followers.takesOrders(v, player);
				if (gone) {
					MEMBER_OF.remove(v.getUUID());
				}
				return gone;
			});
			if (player == null || f.members.isEmpty()) {
				for (Villager v : f.members) {
					MEMBER_OF.remove(v.getUUID());
				}
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			if (!f.held) {
				f.yaw = player.getYRot();
				Vec3 ahead = facing(f.yaw);
				f.anchor = switch (f.shape) {
					case LINE -> player.position().add(ahead.scale(LEAD_OFFSET));
					case SQUARE -> player.position();
					default -> player.position().subtract(ahead.scale(LEAD_OFFSET));
				};
			}
			boolean fighting = false;
			for (Villager v : f.members) {
				fighting |= Comrades.target(v) != null;
			}
			if (f.charging && !fighting) {
				f.charging = false;
			}
			f.loosed.removeIf(id -> f.members.stream().noneMatch(v -> v.getUUID().equals(id) && Comrades.target(v) != null));
			List<Villager> keeping = new ArrayList<>();
			for (Villager v : f.members) {
				if (!f.charging && !f.loosed.contains(v.getUUID())) {
					keeping.add(v);
				}
			}
			drill(level, keeping, f.shape, f.anchor, f.yaw, false, player.position());
		}
	}

	/**
	 * Home squads (me-ranks): near players, each leader among a realm's guards at home ({@link Ranks#leads}) heads the
	 * guards of his realm about him, up to {@link #HOME_SQUAD}: in column behind him while he walks his round, in a line
	 * before him where he stands (at the gate). Anyone fighting is left to it.
	 */
	private static void homeSquads(MinecraftServer server) {
		if (server.getTickCount() % HOME_EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			Set<UUID> taken = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				for (PathfinderMob leader : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(HOME_ROUND),
					m -> homeGuard(m) && Ranks.leads(m))) {
					if (!taken.add(leader.getUUID()) || Comrades.target(leader) != null) {
						continue;
					}
					int faction = ((Worker) leader).factionId();
					List<PathfinderMob> squad = new ArrayList<>();
					for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, leader.getBoundingBox().inflate(HOME_NEAR),
						m -> m != leader && homeGuard(m) && ((Worker) m).factionId() == faction && !Ranks.leads(m) && Comrades.target(m) == null)) {
						if (squad.size() < HOME_SQUAD && taken.add(m.getUUID())) {
							squad.add(m);
						}
					}
					if (squad.isEmpty()) {
						continue;
					}
					boolean moving = leader.getDeltaMovement().horizontalDistanceSqr() > MOVING || leader.getNavigation().isInProgress();
					float yaw = leader.getYRot();
					Vec3 ahead = facing(yaw);
					Vec3 anchor = moving ? leader.position().subtract(ahead.scale(SPACING)) : leader.position().add(ahead.scale(SPACING));
					drill(level, squad, moving ? COLUMN : LINE, anchor, yaw, false, leader.position());
				}
			}
		}
	}

	/** A realm's guard or archer at home: on duty for a standing realm, not out with a war party, nobody's follower, in no player's ranks. */
	private static boolean homeGuard(PathfinderMob m) {
		if (!m.isAlive() || m.isBaby() || m.isSleeping() || m.isPassenger() || Downed.isDowned(m) || !(m instanceof Worker w)
			|| w.role() != Folk.GUARD && w.role() != Folk.ARCHER || m.hasAttached(Ranks.RANK) || inRanks(m) || WarParties.marching(m.getUUID())) {
			return false;
		}
		if (m instanceof Villager v && (McaCompat.following(v) != null || v.hasAttached(Followers.ORDER))) {
			return false;
		}
		return Ranks.of(m) >= 0;
	}

	/** The way {@code yaw} faces, flat. */
	static Vec3 facing(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vec3(-Math.sin(r), 0.0, Math.cos(r));
	}

	/** The yaw that faces from {@code from} to {@code to}. */
	static float yawTo(Vec3 from, Vec3 to) {
		return (float)Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
	}

	/** A place in ranks: where, which way to face, and whether it's in a line's front rank (the shield wall). */
	record Place(Vec3 at, Vec3 face, boolean front) {
	}

	/**
	 * The places of {@code members} (in their order) in {@code shape} about {@code anchor}, facing {@code yaw}: a line
	 * with its blades in front and its bows in a rank behind, a wedge with the first at its point, a hollow square facing
	 * out with the bows inside, a column two abreast.
	 */
	static List<Place> places(List<? extends Mob> members, int shape, Vec3 anchor, float yaw) {
		Vec3 ahead = facing(yaw), right = new Vec3(-ahead.z, 0.0, ahead.x);
		List<Place> out = new ArrayList<>();
		int bows = 0;
		for (Mob m : members) {
			if (archer(m)) {
				bows++;
			}
		}
		int blades = members.size() - bows;
		switch (shape) {
			case LINE -> {
				int ranks = Math.max(1, (blades + LINE_WIDTH - 1) / LINE_WIDTH);
				for (int i = 0; i < members.size(); i++) {
					boolean bow = i >= blades;
					int j = bow ? i - blades : i;
					int perRank = bow ? Math.min(bows, LINE_WIDTH) : Math.min(blades, LINE_WIDTH);
					int rank = j / LINE_WIDTH, file = j % LINE_WIDTH;
					int inRank = bow ? Math.min(perRank, bows - rank * LINE_WIDTH) : Math.min(perRank, blades - rank * LINE_WIDTH);
					double x = (file - (inRank - 1) / 2.0) * SPACING;
					double z = bow ? -(ranks - 1) * SPACING - BACK_RANK - rank * SPACING : -rank * SPACING;
					out.add(new Place(anchor.add(right.scale(x)).add(ahead.scale(z)), ahead, !bow && rank == 0));
				}
			}
			case WEDGE -> {
				for (int i = 0; i < members.size(); i++) {
					int k = (i + 1) / 2;
					double side = i == 0 ? 0.0 : i % 2 == 1 ? -1.0 : 1.0;
					out.add(new Place(anchor.add(right.scale(side * k * SPACING * 0.8)).add(ahead.scale(-k * SPACING)), ahead, i < 3));
				}
			}
			case SQUARE -> {
				double half = Math.max(1.0, Math.ceil(blades / 4.0)) * SPACING / 2.0 + SPACING / 2.0;
				for (int i = 0; i < members.size(); i++) {
					if (i >= blades) {
						int j = i - blades;
						Vec3 at = anchor.add(right.scale((j % 2 - 0.5) * SPACING * 0.8)).add(ahead.scale(((j / 2) - (bows - 1) / 4.0) * SPACING * 0.8));
						out.add(new Place(at, ahead, false));
						continue;
					}
					double t = i * 4.0 / Math.max(1, blades);
					int edge = (int)t;
					double u = (t - edge) * 2.0 - 1.0;
					double x = switch (edge) {
						case 0 -> u * half;
						case 1 -> half;
						case 2 -> -u * half;
						default -> -half;
					};
					double z = switch (edge) {
						case 0 -> half;
						case 1 -> -u * half;
						case 2 -> -half;
						default -> u * half;
					};
					Vec3 off = right.scale(x).add(ahead.scale(z));
					out.add(new Place(anchor.add(off), off.lengthSqr() < 1.0E-4 ? ahead : off.normalize(), true));
				}
			}
			default -> {
				for (int i = 0; i < members.size(); i++) {
					out.add(new Place(anchor.add(right.scale((i % 2 == 0 ? -0.5 : 0.5) * SPACING)).add(ahead.scale(-(i / 2) * SPACING)), ahead, false));
				}
			}
		}
		return out;
	}

	/**
	 * Keeps {@code members} in {@code shape} about {@code anchor} facing {@code yaw} (a line turns to a foe near it):
	 * each walks to their place, meets a foe that comes within {@link #ENGAGE} of it and lets one farther off come; a
	 * line's front rank lock shields against a foe near; a bow keeps near their place while shooting. {@code steer}:
	 * walk them by their navigation (a war party on the march, its villagers' brains held), else by their brains.
	 */
	static void drill(ServerLevel level, List<? extends PathfinderMob> members, int shape, Vec3 anchor, float yaw, boolean steer) {
		drill(level, members, shape, anchor, yaw, steer, null);
	}

	/** {@link #drill}; a place nobody can stand at sends its fighter to {@code fallback} (the leader's place if null). */
	static void drill(ServerLevel level, List<? extends PathfinderMob> members, int shape, Vec3 anchor, float yaw, boolean steer, @Nullable Vec3 fallback) {
		if (members.isEmpty()) {
			return;
		}
		LivingEntity foe = shape == LINE ? foeNear(level, anchor, WALL_SIGHT, members.getFirst()) : null;
		if (foe != null && foe.position().distanceToSqr(anchor) > 1.0) {
			yaw = yawTo(anchor, foe.position());
		}
		List<? extends PathfinderMob> sorted = new ArrayList<>(members);
		sort(sorted);
		List<Place> places = places(sorted, shape, anchor, yaw);
		Vec3 back = fallback != null ? fallback : sorted.getFirst().position();
		for (int i = 0; i < sorted.size(); i++) {
			keep(level, sorted.get(i), places.get(i), foe, shape == LINE && places.get(i).front(), steer, back);
		}
	}

	/**
	 * (WarParties, at the siege) {@code squad}'s shield wall before the seat: only its shields and its bows hold the
	 * line; its other blades go on with the assault, and anyone already fighting keeps at it.
	 */
	static void wall(ServerLevel level, List<? extends PathfinderMob> squad, Vec3 anchor, float yaw) {
		List<PathfinderMob> holding = new ArrayList<>();
		for (PathfinderMob m : squad) {
			if (Comrades.target(m) == null && (archer(m) || m.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS))) {
				holding.add(m);
			}
		}
		drill(level, holding, LINE, anchor, yaw, false);
	}

	/** Whether a shot from {@code owner}, a soldier, passes {@code target} by: one of their own side (ProjectileAlliesMixin). */
	public static boolean spares(@Nullable Entity owner, Entity target) {
		if (!(owner instanceof Mob m) || !(target instanceof LivingEntity e) || owner == target || !(inRanks(m) || Ranks.of(m) >= 0)) {
			return false;
		}
		return Comrades.side(e).equals(Comrades.side(m));
	}

	private static void keep(ServerLevel level, PathfinderMob m, Place place, @Nullable LivingEntity foe, boolean wall, boolean steer, Vec3 fallback) {
		if (Downed.isDowned(m) || m.isPassenger() || m.isSleeping()) {
			return;
		}
		Vec3 at = ground(level, place, fallback);
		boolean bow = archer(m);
		LivingEntity target = Comrades.target(m);
		if (target != null && !bow && target.position().distanceToSqr(at) > ENGAGE * ENGAGE && !engaged(m, target)) {
			// (holding the line: they let it come to them; one already at blows with it keeps at it)
			Comrades.stand(m);
			target = null;
		}
		double d = m.position().distanceToSqr(at);
		boolean shield = wall && m.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS);
		LivingEntity against = target != null ? target : foe;
		if (shield && against != null) {
			double fd = m.distanceToSqr(against);
			if (fd < SHIELD_RANGE * SHIELD_RANGE && fd > STRIKE * STRIKE) {
				MobShields.snap(m, SHIELD_TICKS);
			}
		}
		if (target != null && (!bow || d <= ARCHER_LEASH * ARCHER_LEASH)) {
			// (meeting it at their place, or shooting from it)
			return;
		}
		if (d > AT_PLACE * AT_PLACE) {
			walk(m, at, steer);
			return;
		}
		if (m instanceof Villager v && v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
		if (steer) {
			m.getNavigation().stop();
		}
		if (against != null) {
			m.getLookControl().setLookAt(against);
		} else {
			Vec3 look = m.getEyePosition().add(place.face().scale(4.0));
			m.getLookControl().setLookAt(look.x, look.y, look.z);
		}
	}

	/** Whether {@code m} is at blows with {@code target}: it's on them, or one of the two has just struck the other. */
	private static boolean engaged(Mob m, LivingEntity target) {
		return m.distanceToSqr(target) <= ENGAGE * ENGAGE || m.getLastHurtByMob() == target || m.getLastHurtMob() == target;
	}

	/**
	 * Where to stand for {@code place}: ground near its own height ({@link #HEIGHTS}; never up onto a roof), else
	 * the nearest such spot along its rank ({@link #SHIFTS}), else {@code fallback}.
	 */
	private static Vec3 ground(ServerLevel level, Place place, Vec3 fallback) {
		Vec3 along = new Vec3(-place.face().z, 0.0, place.face().x);
		for (double s : SHIFTS) {
			Vec3 p = place.at().add(along.scale(s));
			BlockPos feet = standable(level, BlockPos.containing(p));
			if (feet != null) {
				return new Vec3(p.x, feet.getY(), p.z);
			}
		}
		return fallback;
	}

	/** A spot near {@code at}'s height with a floor and room for a body (two blocks clear), or null. */
	private static @Nullable BlockPos standable(ServerLevel level, BlockPos at) {
		if (!level.hasChunkAt(at)) {
			return null;
		}
		for (int dy : HEIGHTS) {
			BlockPos feet = at.above(dy);
			if (!level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return feet;
			}
		}
		return null;
	}

	private static void walk(PathfinderMob m, Vec3 to, boolean steer) {
		float pace = Comrades.target(m) != null ? RUN : WALK;
		if (!steer && m instanceof Villager v) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, pace, 0));
		} else {
			m.getNavigation().moveTo(to.x, to.y, to.z, pace + 0.3);
		}
	}

	/** The nearest foe of {@code of}'s side within {@code radius} of {@code around}: a monster, or one set on {@code of}'s side; null if none. */
	static @Nullable LivingEntity foeNear(ServerLevel level, Vec3 around, double radius, Mob of) {
		String side = Comrades.side(of);
		LivingEntity best = null;
		double bestD = radius * radius;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(around, radius * 2.0, radius, radius * 2.0),
			e -> e.isAlive() && !e.isSpectator() && !Comrades.side(e).equals(side))) {
			boolean hostile = e instanceof Enemy && !(e instanceof Villager)
				|| e instanceof Mob mob && Comrades.target(mob) != null && Comrades.side(Comrades.target(mob)).equals(side);
			if (!hostile) {
				continue;
			}
			double d = e.position().distanceToSqr(around);
			if (d < bestD && (d < 36.0 || of.hasLineOfSight(e))) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Whether {@code e} keeps ranks in a player's formation just now. */
	public static boolean inRanks(Entity e) {
		return MEMBER_OF.containsKey(e.getUUID());
	}
}
