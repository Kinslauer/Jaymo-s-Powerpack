package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.SeaRoutes;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.jaymoskingdoms.seas.Mohknee;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Someone else at the helm (Andy, 2026-11-12, on the two days' sail to Mohknee: "can there be an MCA captain or
 * companion that can pilot the ship for you? thats just too long to be sailing manually").
 * <ul>
 * <li><b>A hired sea captain.</b> A sailor between voyages ({@link Crewmen}, waiting about a harbour) takes
 * {@link #HIRE_COST} in coin on the Interact page ("Hire as Captain") and follows you for {@link #HIRED_TICKS}; he
 * doesn't sign on to another ship meanwhile.
 One sailor in {@link #CROSSED_IN} has made the eastern crossing and says so before you pay: he alone can be told to
 * make for the land in the east (Lore: nobody on the home continent has a map of it), and lies off its shore when he raises it.</li>
 * <li><b>One of your own.</b> A companion, a hired hand, family, or anyone who trusts you ({@link Strangers}) can be
 * told the same. They carry less sail than a captain, and less again in a blow.</li>
 * </ul>
 * Cast your ship off as always, then "Take the Helm" on their Interact page while you're aboard her: they go to her
 * wheel and ask for a course (the heading she's on, a point of the compass, or another ship to follow). They hold it
 * along a line, working round islands by {@link SeaRoutes} and coming back to the line after, with you free to walk
 * the deck, fish, fight or sleep in a berth. They heave to and say so when land lies across the course, when you're no
 * longer aboard, and when told to; they'll drop her anchor for you, or hand the wheel back. Taking the wheel yourself
 * overrides them while you hold it.
 */
public final class Helmsmen implements Ship.Helmsmanship {
	/** What a sailor ashore asks to captain your ship (emeralds' worth), and how long he stays hired (six days). */
	static final int HIRE_COST = 12;
	static final long HIRED_TICKS = 24000L * 6L;
	/** The sail a captain carries, a companion, and a companion in a storm. */
	static final double CAPTAIN_SAIL = 1.0, HAND_SAIL = 0.75, HAND_STORM_SAIL = 0.4, STORM_SAIL = 0.7;
	/** He looks about him this often (ticks); how far ahead along the line he steers for (blocks). */
	static final int THINK = 20;
	static final double AIM = 160.0;
	/** Looks with no way ahead before he heaves to for land. */
	static final int STUCK_LOOKS = 8;
	/** His owner must be this near her (blocks) or he heaves to; a ship to follow is found within this, and kept this far astern of. */
	static final double OWNER_NEAR = 24.0, FOLLOW_FIND = 160.0, FOLLOW_ASTERN = 28.0, FOLLOW_LOST = 260.0;
	/** You talk to him from this near; he comes to the wheel from this far. */
	static final double CALL_RANGE = 16.0;
	/** One sailor in this many has made the eastern crossing. */
	static final int CROSSED_IN = 3;
	static final String CAPTAIN_TAG = "jaymoskingdoms.sea_captain";
	public static final String HELM = "helm", HIRE = "hire_helm";

	/** Her helmsman's orders: who he is, whose he is, his course, and the line he holds (a point on it and its heading). */
	public record Order(String hand, String owner, String course, float yaw, double x, double z, String follow) {
		static final Codec<Order> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("hand").forGetter(Order::hand),
			Codec.STRING.fieldOf("owner").forGetter(Order::owner),
			Codec.STRING.fieldOf("course").forGetter(Order::course),
			Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Order::yaw),
			Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(Order::x),
			Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(Order::z),
			Codec.STRING.optionalFieldOf("follow", "").forGetter(Order::follow)).apply(i, Order::new));

		Order with(String course) {
			return new Order(this.hand, this.owner, course, this.yaw, this.x, this.z, this.follow);
		}
	}

	/** Courses: along a line; the same, to Mohknee's shore; after another ship; lying to. */
	static final String HOLD = "hold", FAR = "far", FOLLOW = "follow", HOVE = "hove";

	/** On a ship: her helmsman's orders. Saved. */
	public static final AttachmentType<Order> ORDER = AttachmentRegistry.create(JaymosKingdoms.id("helm_order"),
		builder -> builder.persistent(Order.CODEC));
	/** On a hired sea captain: who hired him, and until when (game time). Saved. */
	static final AttachmentType<String> HIRED_BY = AttachmentRegistry.create(JaymosKingdoms.id("sea_captain_of"),
		builder -> builder.persistent(Codec.STRING));
	static final AttachmentType<Long> HIRED_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("sea_captain_until"),
		builder -> builder.persistent(Codec.LONG));

	/** Not saved, per ship: his chart work, the helm he last set, and how long he's had no way ahead. */
	private static final class Watch {
		final Crew chart = new Crew(Crew.Side.MERCHANT, -1, "", BlockPos.ZERO);
		float wantYaw;
		double wantSail;
		int stuck;
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();

	/** A talk's menu: who's talking, and what each option does. */
	private record Menu(int speaker, List<String> actions) {
	}

	private static final Map<UUID, Menu> MENUS = new HashMap<>();

	private Helmsmen() {
	}

	static void register() {
		Ship.helmsmanship = new Helmsmen();
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MENUS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MENUS.clear();
			WATCHES.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Helmsmen::contracts);
	}

	// ---- who ---------------------------------------------------------------------------------------------------------------

	/** A sea captain {@code player} has hired, his time not yet up. */
	static boolean hiredBy(Entity v, ServerPlayer player) {
		return v.entityTags().contains(CAPTAIN_TAG) && player.getStringUUID().equals(v.getAttachedOrElse(HIRED_BY, ""));
	}

	/** Hired as someone's sea captain now (he signs on to no other ship: {@link Crewmen#waiting}). */
	static boolean hired(Entity v) {
		return v.entityTags().contains(CAPTAIN_TAG);
	}

	/** Whether {@code v} would take a wheel for {@code player}: their hired captain, their companion, or one who trusts them. */
	static boolean serves(ServerPlayer player, LivingEntity v) {
		if (!(v instanceof Mob) || v.isBaby() || !v.isAlive() || Downed.isDowned(v) || !Temperaments.humanoid(v)) {
			return false;
		}
		if (hiredBy(v, player) || player.getUUID().equals(Bonds.lordOf(v))) {
			return true;
		}
		return v instanceof Villager villager && !Crewmen.tagged(v) && (McaCompat.family(villager, player)
			|| player.getUUID().equals(McaCompat.following(villager)) && McaCompat.LOADED && McaBridge.involved(villager, player));
	}

	/** A hired captain, or one who has sailed before (one of a ship's crew): he carries full sail. */
	private static boolean seaman(Entity v) {
		return hired(v) || Crewmen.tagged(v);
	}

	/** The ship {@code player} has (at her wheel or on her deck) that a helmsman of theirs could take: one with no crew of her own, on the water. */
	static @Nullable Ship shipOf(ServerPlayer player) {
		Ship ship = Ships.aboard(player);
		return ship != null && ship.crew == null && !ship.foundering() && !ship.flying() && ship.hull() != null && ship.hull().helm >= 0 ? ship : null;
	}

	/** "Take the Helm" shows on {@code v}'s Interact page. */
	static boolean offersHelm(ServerPlayer player, LivingEntity v) {
		Ship ship = shipOf(player);
		return ship != null && serves(player, v) && v.distanceTo(player) <= CALL_RANGE && (v.getVehicle() == null || v.getVehicle() == ship);
	}

	/** "Hire as Captain" shows on {@code v}'s Interact page: an honest sailor ashore between voyages, nobody's captain yet. */
	static boolean offersHire(ServerPlayer player, LivingEntity v) {
		return v instanceof Mob && Crewmen.tagged(v) && !v.entityTags().contains(Crewmen.sideTag(Crew.Side.PIRATE)) && !v.isPassenger() && !v.isBaby()
			&& !hired(v) && !Downed.isDowned(v);
	}

	// ---- hiring ------------------------------------------------------------------------------------------------------------

	/** One sailor in {@link #CROSSED_IN} has made the eastern crossing and can find Mohknee again (which, by who he is). */
	static boolean crossed(Entity v) {
		return Crewmen.tagged(v) && Math.floorMod(v.getUUID().hashCode(), CROSSED_IN) == 0;
	}

	/** {@code player} pressed "Hire as Captain" on {@code v}: what he's sailed, and his price. */
	static void hire(ServerPlayer player, LivingEntity v) {
		ServerLevel level = player.level();
		if (!offersHire(player, v)) {
			return;
		}
		Component words = Component.translatable(crossed(v) ? "talk.jaymoskingdoms.helm.hire.ask.crossed" : "talk.jaymoskingdoms.helm.hire.ask", HIRE_COST);
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			Speech.tell(level, v, player, words, Speech.WHITE);
			return;
		}
		MENUS.put(player.getUUID(), new Menu(v.getId(), List.of("hire", "close")));
		ServerPlayNetworking.send(player, new TalkNet.Ask(v.getId(), v.getDisplayName(), words,
			List.of(Component.translatable("talk.jaymoskingdoms.helm.option.hire", HIRE_COST), Component.translatable("talk.jaymoskingdoms.work.close")),
			List.of(Money.canPay(player, Money.worth(HIRE_COST)), true)));
	}

	private static void hired(ServerPlayer player, LivingEntity v) {
		ServerLevel level = player.level();
		if (!offersHire(player, v) || v.distanceTo(player) > CALL_RANGE || !Money.charge(player, Money.worth(HIRE_COST))) {
			return;
		}
		v.addTag(CAPTAIN_TAG);
		v.setAttached(HIRED_BY, player.getStringUUID());
		v.setAttached(HIRED_UNTIL, level.getGameTime() + HIRED_TICKS);
		if (v instanceof Villager villager) {
			McaCompat.follow(villager, player);
		}
		Introductions.learn(player, v);
		Speech.tell(level, v, player, Component.translatable("talk.jaymoskingdoms.helm.hire.done"), Speech.WHITE);
	}

	/** A hired captain's time runs out ashore (never at a wheel): he goes back to waiting on a ship. */
	private static void contracts(MinecraftServer server) {
		if (server.getTickCount() % 200 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			long now = level.getGameTime();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0), Helmsmen::hired)) {
				if (m.isPassenger() || now < m.getAttachedOrElse(HIRED_UNTIL, 0L)) {
					continue;
				}
				ServerPlayer owner = ownerOf(server, m.getAttachedOrElse(HIRED_BY, ""));
				m.removeTag(CAPTAIN_TAG);
				m.removeAttached(HIRED_BY);
				m.removeAttached(HIRED_UNTIL);
				if (m instanceof Villager villager) {
					McaCompat.stopFollowing(villager);
				}
				if (owner != null && owner.distanceTo(m) < 32.0F) {
					Speech.tell(level, m, owner, Component.translatable("talk.jaymoskingdoms.helm.hire.over"), Speech.WHITE);
				}
			}
		}
	}

	private static @Nullable ServerPlayer ownerOf(MinecraftServer server, String id) {
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(id));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---- the talk ----------------------------------------------------------------------------------------------------------

	/** {@code player} pressed "Take the Helm" on {@code v}: he goes to the wheel (if he isn't there) and asks for his orders. */
	static void talk(ServerPlayer player, LivingEntity v) {
		ServerLevel level = player.level();
		Ship ship = shipOf(player);
		if (ship == null || !offersHelm(player, v)) {
			return;
		}
		Order order = ship.getAttached(ORDER);
		boolean his = order != null && order.hand().equals(v.getStringUUID());
		if (order != null && !his && helmsman(level, ship, order) != null) {
			Speech.tell(level, v, player, Component.translatable("talk.jaymoskingdoms.helm.taken"), Speech.WHITE);
			return;
		}
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (String course : List.of("as_she_goes", "east", "west", "north", "south")) {
			actions.add(course);
			options.add(Component.translatable("talk.jaymoskingdoms.helm.option." + course));
			enabled.add(true);
		}
		if (crossed(v) && !Mohknee.on(level, ship.blockPosition())) {
			actions.add(FAR);
			options.add(Component.translatable("talk.jaymoskingdoms.helm.option.far"));
			enabled.add(true);
		}
		actions.add(FOLLOW);
		options.add(Component.translatable("talk.jaymoskingdoms.helm.option.follow"));
		enabled.add(chase(level, ship) != null);
		if (his) {
			for (String act : List.of(HOVE, "anchor", "relieve")) {
				actions.add(act);
				options.add(Component.translatable("talk.jaymoskingdoms.helm.option." + act));
				enabled.add(!act.equals(HOVE) || !HOVE.equals(order.course()));
			}
		}
		actions.add("close");
		options.add(Component.translatable("talk.jaymoskingdoms.work.close"));
		enabled.add(true);
		Component words = Component.translatable(his ? "talk.jaymoskingdoms.helm.ask.again" : seaman(v) ? "talk.jaymoskingdoms.helm.ask.captain"
			: "talk.jaymoskingdoms.helm.ask.hand");
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			Speech.tell(level, v, player, words, Speech.WHITE);
			return;
		}
		MENUS.put(player.getUUID(), new Menu(v.getId(), List.copyOf(actions)));
		ServerPlayNetworking.send(player, new TalkNet.Ask(v.getId(), v.getDisplayName(), words, options, enabled));
	}

	/** An answer to a helmsman's question; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Menu menu = MENUS.get(player.getUUID());
		if (menu == null || menu.speaker() != answer.entityId()) {
			return false;
		}
		MENUS.remove(player.getUUID());
		ServerLevel level = player.level();
		if (answer.option() >= 0 && answer.option() < menu.actions().size() && "hire".equals(menu.actions().get(answer.option()))) {
			if (level.getEntity(answer.entityId()) instanceof LivingEntity sailor) {
				hired(player, sailor);
			}
			return true;
		}
		Ship ship = shipOf(player);
		if (answer.option() < 0 || answer.option() >= menu.actions().size() || ship == null
			|| !(level.getEntity(answer.entityId()) instanceof LivingEntity v) || !offersHelm(player, v)) {
			return true;
		}
		String action = menu.actions().get(answer.option());
		Order order = ship.getAttached(ORDER);
		boolean his = order != null && order.hand().equals(v.getStringUUID());
		switch (action) {
			case "close" -> {
			}
			case "relieve" -> {
				if (his) {
					relieve(ship, v, player);
					say(level, v, player, "relieved");
				}
			}
			case "anchor" -> {
				if (his) {
					if (Math.abs(ship.way()) > 0.05) {
						ship.setAttached(ORDER, order.with(HOVE));
						say(level, v, player, "anchor.way");
					} else if (ship.dropAnchor()) {
						say(level, v, player, "anchor.down");
					} else {
						say(level, v, player, "anchor.no_room");
					}
				}
			}
			case HOVE -> {
				if (his) {
					ship.setAttached(ORDER, order.with(HOVE));
					say(level, v, player, "hove");
				}
			}
			default -> {
				if (order != null && !his && helmsman(level, ship, order) != null) {
					return true;
				}
				float yaw = switch (action) {
					case FAR -> Fleet.bearing(ship, Mohknee.CENTRE_X, Mohknee.CENTRE_Z);
					case "east" -> -90.0F;
					case "west" -> 90.0F;
					case "north" -> 180.0F;
					case "south" -> 0.0F;
					default -> ship.getYRot();
				};
				Ship chased = action.equals(FOLLOW) ? chase(level, ship) : null;
				if (action.equals(FOLLOW) && chased == null) {
					say(level, v, player, "follow.none");
					return true;
				}
				if (!his && !post(ship, v)) {
					say(level, v, player, "no_wheel");
					return true;
				}
				ship.setAttached(ORDER, new Order(v.getStringUUID(), player.getStringUUID(), chased != null ? FOLLOW : action.equals(FAR) ? FAR : HOLD, yaw, ship.getX(), ship.getZ(),
					chased != null ? chased.getStringUUID() : ""));
				WATCHES.remove(ship.getUUID());
				if (chased != null) {
					Speech.tell(level, v, player, Component.translatable("talk.jaymoskingdoms.helm.follow",
						chased.crew != null ? chased.crew.name : ""), Speech.WHITE);
				} else {
					say(level, v, player, "course." + action);
				}
			}
		}
		return true;
	}

	private static void say(ServerLevel level, LivingEntity v, @Nullable ServerPlayer player, String line) {
		Speech.tell(level, v, player, Component.translatable("talk.jaymoskingdoms.helm." + line), Speech.WHITE);
	}

	// ---- at the wheel ------------------------------------------------------------------------------------------------------

	/** Where her helmsman stands, in her frame: behind her wheel. */
	private static @Nullable Vec3 stand(Ship ship) {
		Hull h = ship.hull();
		if (h == null || h.helm < 0) {
			return null;
		}
		Ship.Piece p = h.pieces.get(h.helm);
		return new Vec3(p.x(), p.y(), p.z() - 1.0);
	}

	/** {@code v} goes to her wheel. */
	private static boolean post(Ship ship, LivingEntity v) {
		Vec3 stand = stand(ship);
		if (stand == null) {
			return false;
		}
		if (v.getVehicle() == ship) {
			v.setAttached(Ship.SEAT, stand);
			return true;
		}
		v.stopRiding();
		if (v instanceof Mob mob) {
			mob.getNavigation().stop();
		}
		if (!ship.takeAboard(v, stand)) {
			return false;
		}
		if (McaCompat.LOADED && v instanceof Villager villager) {
			// (he stays at her wheel: MCA's Follow would have him leave it to come after you about her deck)
			McaBridge.moveState(villager, 1, null);
		}
		return true;
	}

	/** He steps away from her wheel: on her deck, his own man again; her sails come in. */
	private static void relieve(Ship ship, @Nullable LivingEntity v, @Nullable ServerPlayer owner) {
		ship.removeAttached(ORDER);
		WATCHES.remove(ship.getUUID());
		if (v != null && v.getVehicle() == ship) {
			v.stopRiding();
			if (owner != null && McaCompat.LOADED && v instanceof Villager villager) {
				McaBridge.moveState(villager, 2, owner);
			}
		}
	}

	private static @Nullable LivingEntity helmsman(ServerLevel level, Ship ship, Order order) {
		for (Entity e : ship.getPassengers()) {
			if (e instanceof LivingEntity living && living.isAlive() && e.getStringUUID().equals(order.hand())) {
				return living;
			}
		}
		return null;
	}

	/** The nearest other ship under a crew of her own to follow. */
	private static @Nullable Ship chase(ServerLevel level, Ship ship) {
		Ship best = null;
		double bestD = FOLLOW_FIND * FOLLOW_FIND;
		for (Ship other : Ships.in(level)) {
			double d = other.distanceToSqr(ship);
			if (other != ship && other.crew != null && !other.foundering() && !other.flying() && d < bestD) {
				best = other;
				bestD = d;
			}
		}
		return best;
	}

	@Override
	public boolean posted(Ship ship) {
		return ship.hasAttached(ORDER);
	}

	@Override
	public @Nullable Input helm(ServerLevel level, Ship ship) {
		Order order = ship.getAttached(ORDER);
		if (order == null) {
			return null;
		}
		LivingEntity hand = helmsman(level, ship, order);
		if (hand == null || Downed.isDowned(hand) || ship.flying()) {
			// (gone from her wheel: killed, struck down, taken off her)
			relieve(ship, null, null);
			return null;
		}
		Watch watch = WATCHES.computeIfAbsent(ship.getUUID(), k -> {
			Watch w = new Watch();
			w.wantYaw = ship.getYRot();
			return w;
		});
		if ((level.getGameTime() + ship.getId()) % THINK == 0) {
			think(level, ship, order, hand, watch);
		}
		return Fleet.steer(ship, watch.wantYaw, watch.wantSail, false, false);
	}

	private static void think(ServerLevel level, Ship ship, Order order, LivingEntity hand, Watch watch) {
		ServerPlayer owner = ownerOf(level.getServer(), order.owner());
		boolean near = owner != null && owner.level() == level && owner.distanceToSqr(ship) < square(ship.hull().radius + OWNER_NEAR);
		if (HOVE.equals(order.course()) || !near) {
			// hove to: told to, or his owner has gone over the side (or out of the world); he waits for them
			watch.wantSail = 0.0;
			watch.wantYaw = ship.getYRot();
			watch.stuck = 0;
			return;
		}
		boolean storm = level.isThundering();
		double sail = seaman(hand) ? storm ? STORM_SAIL : CAPTAIN_SAIL : storm ? HAND_STORM_SAIL : HAND_SAIL;
		BlockPos goal;
		float want;
		if (FOLLOW.equals(order.course())) {
			Ship chased = chased(level, order);
			if (chased == null || chased.distanceToSqr(ship) > FOLLOW_LOST * FOLLOW_LOST) {
				ship.setAttached(ORDER, order.with(HOVE));
				say(level, hand, owner, "follow.lost");
				return;
			}
			goal = chased.blockPosition();
			want = Fleet.bearing(ship, chased.getX(), chased.getZ());
			double off = Math.sqrt(chased.distanceToSqr(ship));
			if (off < FOLLOW_ASTERN) {
				// close astern of her: no nearer
				sail = 0.0;
			} else if (off < FOLLOW_ASTERN * 2.0) {
				sail = Math.min(sail, 0.4);
			}
		} else if (FAR.equals(order.course()) && Mohknee.shoreAhead(level, ship.getX(), ship.getZ())) {
			// the far shore under her bow: he lies off it, and where she goes in is her owner's to say
			ship.setAttached(ORDER, order.with(HOVE));
			say(level, hand, owner, "landfall");
			return;
		} else {
			// along his line: as far along it as she's come, and the mark well ahead of that (so she comes back to it round an island)
			double fx = -Mth.sin(order.yaw() * Mth.DEG_TO_RAD), fz = Mth.cos(order.yaw() * Mth.DEG_TO_RAD);
			double along = (ship.getX() - order.x()) * fx + (ship.getZ() - order.z()) * fz + AIM;
			goal = BlockPos.containing(order.x() + fx * along, ship.getY(), order.z() + fz * along);
			want = Fleet.bearing(ship, goal.getX() + 0.5, goal.getZ() + 0.5);
		}
		if (sail > 0.0) {
			Float course = SeaRoutes.steer(level, ship, watch.chart, goal);
			if (course != null) {
				want = course;
			}
		}
		Float clear = Fleet.heading(level, ship, want);
		boolean moving = Math.abs(ship.way()) > 0.03;
		if (sail > 0.0 && (clear == null || !moving && ship.sailsSet() > 0.3)) {
			watch.stuck++;
		} else {
			watch.stuck = Math.max(0, watch.stuck - 1);
		}
		if (watch.stuck > STUCK_LOOKS) {
			// land across his course and no way round it he can see: he'll not put her ashore on his own word
			watch.stuck = 0;
			SeaRoutes.forget(watch.chart);
			ship.setAttached(ORDER, order.with(HOVE));
			say(level, hand, owner, "land");
			return;
		}
		watch.wantYaw = clear != null ? clear : want;
		watch.wantSail = clear != null ? sail : 0.15;
	}

	private static @Nullable Ship chased(ServerLevel level, Order order) {
		try {
			return level.getEntity(UUID.fromString(order.follow())) instanceof Ship s && !s.isRemoved() && !s.foundering() ? s : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static double square(double d) {
		return d * d;
	}
}
