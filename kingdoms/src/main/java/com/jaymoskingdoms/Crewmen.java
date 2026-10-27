package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Deck;
import com.jaymoskingdoms.sailing.Ship;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.242) Andy (2026-10-27): "i need all the ship crew to integrate with MCA, it doesnt make sense i have fully kitted
 * knights all silent and a stoic brick for a captain. they need at the very least a personality." With Minecraft Comes
 * Alive, every ship's crew (a pirate's captain, gunners, deckhands and powder monkeys; a warship's sea captain and
 * knights; a merchantman's and a whaler's hands) are MCA villagers: names, faces, heritage, personality, mood and
 * temperament of their own, and MCA's talk when you speak to them. They keep their folk's gear and toughness
 * ({@code McaBridge.crewman}), and are enlisted to their side in their old role, off every roster as before.
 *
 * <p>Aboard, they ride her at their stations ({@link Ship#SEAT}) and walk her deck ({@code Corsairs.deckwork}) as the
 * folk did, so MCA's brain waits while they're aboard ({@link #step}, from {@code Workers.step}). In its place: they
 * take their foes as the folk did (her captain's foe's crew, a boarder, whoever hurt them, a cove's prey), fire their
 * pistols and bows, swing at anyone within reach, look at whoever talks to them, and talk among themselves and to
 * players about the deck ({@link #chatter}). MCA's screen shows which ship they serve and what as ({@code Standing}),
 * and her captain's nametag reads "Captain" once you know his name. A cove's pirate captain keeps his boss bar.
 * Ashore (a whaleboat's hands, or set down when she's anchored), they live as villagers.
 *
 * <p>Without MCA, the crews stay {@link Folk}. With it, folk crews already at sea in a world are each replaced where
 * they stand by a villager of their blood ({@link #convert}).
 */
public final class Crewmen {
	/** The entity tag every crew villager carries (kept in their save): a ship's crewman, not any town's. */
	public static final String TAG = "jaymoskingdoms.crewman";
	/** (0.7.246) Her one knight, 0.7.246-0.7.258 (since 0.7.259 there are none: the tag is taken off). */
	static final String KNIGHT = TAG + ".knight";
	/** (0.7.246) How often (ticks) a crew is looked over: no armour, no guards, weapons stowed while all's calm. */
	static final int DRESS_EVERY = 100;
	/** (0.7.259) How long (ticks) after their last foe they put their weapons away. */
	static final int CALM_TICKS = 400;
	/** (0.7.246) One deck line in this many is said to a hand of hers near, by their berth; and how many there are. */
	static final int MATE_ONE_IN = 2, TO_HAND_LINES = 6;
	/** (0.7.246) A hand's cry on running to a downed shipmate, and words to a player they got up. */
	static final int MAN_DOWN_LINES = 4, UP_LINES = 4;
	/** How far they take a foe of their own (a boarder, a cove's prey) aboard her; how far they chase a foe with their eyes before letting go. */
	static final double SIGHT = 16.0, LET_GO = 40.0;
	/** How long (ticks) whoever hurt them stays their foe. */
	static final int HURT_MEMORY = 200;
	/** A blow: how far they reach, and how often they swing (ticks). */
	static final double REACH = 2.6;
	static final int SWING_GAP = 20;
	/** A bow: from this far to this far, this often (ticks, plus up to the jitter). */
	static final double BOW_NEAREST = 3.0, BOW_FURTHEST = 24.0;
	static final int BOW_RELOAD = 50, BOW_JITTER = 30;
	/** Talk about the deck: a player this near, one line a ship at most this often (ticks, plus up to the jitter); how far it carries. */
	static final double CHAT_RANGE = 10.0, CHAT_CARRY = 14.0;
	static final int CHAT_GAP = 300, CHAT_JITTER = 400;
	/** How many lines each side's hands, each side's captain, and each temperament have (lang keys 0..n-1). */
	static final int HAND_LINES = 8, CAPTAIN_LINES = 4;
	/** One line in this many is their temperament's rather than their trade's. */
	static final int TEMPER_ONE_IN = 3;
	/** A newcomer aboard is welcomed by her captain (once per player per ship while they're aboard). */
	static final double WELCOME_RANGE = 6.0;
	/**
	 * (0.7.242) Home from a whaling voyage, each hand's chance to go ashore for good with their pay: to start with, more
	 * for each whale taken (up to the most), less for her captain (times this); never below the least or above the most.
	 */
	static final float LEAVE_BASE = 0.3F, LEAVE_PER_WHALE = 0.08F, LEAVE_WHALES_MOST = 0.3F, LEAVE_CAPTAIN = 0.5F, LEAVE_LEAST = 0.05F,
		LEAVE_MOST = 0.9F;
	/** Hands who sign on again wait about the harbour (keeping within this of its shore) and are found within this of her mooring. */
	static final int PORT_HOME = 24;
	static final double PORT_RANGE = 72.0;
	/** Lines for signing on again, and for going ashore (lang keys 0..n-1). */
	static final int SIGN_LINES = 4;

	/** Each crewman's next swing and next shot (game time); each ship's next line; who her captain has welcomed. */
	private static final Map<LivingEntity, long[]> READY = new WeakHashMap<>();
	private static final Map<UUID, Long> NEXT_LINE = new HashMap<>();
	private static final Map<UUID, java.util.Set<UUID>> WELCOMED = new HashMap<>();
	/** A cove's villager captain's boss bar. */
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	private Crewmen() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			READY.clear();
			LAST_FOE.clear();
			NEXT_LINE.clear();
			WELCOMED.clear();
			for (ServerBossEvent bar : BARS.values()) {
				bar.removeAllPlayers();
			}
			BARS.clear();
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> dropBar(entity.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> dropBar(entity.getUUID()));
	}

	private static void dropBar(UUID id) {
		ServerBossEvent bar = BARS.remove(id);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** {@code e} is a crew villager (aboard or not). */
	public static boolean tagged(Entity e) {
		return !(e instanceof Folk) && e.entityTags().contains(TAG);
	}

	/** {@code e}'s role and side as one of a ship's crew ({@link Worker}), or -1. */
	static int role(Entity e) {
		return e instanceof Worker w ? w.role() : -1;
	}

	static int faction(Entity e) {
		return e instanceof Worker w ? w.factionId() : -1;
	}

	/** (0.7.242) The tag that says which side's crew they are (a whaler's hand waits in port for her next voyage). */
	static String sideTag(Crew.Side side) {
		return TAG + "." + side.id();
	}

	/** Whether a {@code side}'s {@code role} goes aboard as a sailor (MCA's fisherman) rather than a fighting man (MCA's guard). */
	static boolean sailor(Crew.Side side, int role) {
		return side == Crew.Side.MERCHANT || side == Crew.Side.WHALER || role == Pirates.POWDER_MONKEY;
	}

	/**
	 * {@code folk}, set up for its role and not yet in the world (or one of a crew already aboard), as an MCA villager
	 * at its place; null without MCA or if one couldn't be made (then the folk sails).
	 */
	static @Nullable PathfinderMob villager(ServerLevel level, Folk folk, Crew.Side side) {
		// (0.7.259) Andy: "I dont want any guards on these ships ... all the sailors should double as both. just picking up
		// their weapons when the time comes": every one a sailor (MCA's fisherman) in no armour, their weapons stowed
		PathfinderMob v = McaCompat.crewman(level, folk);
		if (v != null) {
			v.addTag(TAG);
			v.addTag(sideTag(side));
		}
		return v;
	}

	// ---- (0.7.246) seeing to the downed aboard ---------------------------------------------------------------------

	/** A hand seeing to a downed shipmate: who, and how long they've knelt by them (ticks). Not saved. */
	private static final class Care {
		final UUID body;
		int ticks;

		Care(UUID body) {
			this.body = body;
		}
	}

	/** Who's seeing to whom aboard, by the helper. */
	private static final Map<UUID, Care> CARE = new HashMap<>();
	/** How close (blocks, on her deck) a hand kneels by the one they're getting up; how long it takes (ticks). */
	static final double CARE_REACH = 1.6;
	static final int CARE_TICKS = 120;

	/** Whether {@code hand} is off getting a shipmate up (Corsairs.deckwork leaves their walk to {@link #care}). */
	static boolean caring(LivingEntity hand) {
		return CARE.containsKey(hand.getUUID());
	}

	/**
	 * (From Corsairs.deckwork, every tick) Andy: "the ship crew should rush to revive each other, including the player".
	 * Anyone downed on her deck (one of hers, or a player she isn't hostile to) has the nearest free hand (never her
	 * captain, nor a gunner while she's fighting) run to them along her deck, kneel and get them up, as a town's
	 * villagers do ashore ({@link Downed}).
	 */
	static void care(ServerLevel level, Ship ship, Crew crew, Deck deck, List<LivingEntity> aboard, boolean fighting) {
		java.util.Set<UUID> downed = new java.util.HashSet<>();
		Map<UUID, LivingEntity> bodies = new HashMap<>();
		for (LivingEntity e : aboard) {
			if (Downed.isDowned(e) && e.isAlive()) {
				downed.add(e.getUUID());
				bodies.put(e.getUUID(), e);
			}
		}
		for (ServerPlayer p : Corsairs.playersAboard(level, ship)) {
			if (Downed.isDowned(p) && p.isAlive() && !p.isSpectator() && !(crew.side == Crew.Side.PIRATE ? Pirates.prey(crew.faction, p)
				: Reputation.get(p, crew.faction) <= Reputation.HOSTILE)) {
				downed.add(p.getUUID());
				bodies.put(p.getUUID(), p);
			}
		}
		// whoever was seeing to someone no longer down (or gone) goes back to work
		java.util.Set<UUID> tended = new java.util.HashSet<>();
		for (LivingEntity hand : aboard) {
			Care c = CARE.get(hand.getUUID());
			if (c == null) {
				continue;
			}
			if (!downed.contains(c.body) || Downed.isDowned(hand)) {
				stopCaring(hand);
			} else {
				tended.add(c.body);
			}
		}
		CARE.keySet().removeIf(id -> aboard.stream().noneMatch(e -> e.getUUID().equals(id)));
		for (UUID id : downed) {
			if (tended.contains(id)) {
				continue;
			}
			LivingEntity body = bodies.get(id);
			Vec3 at = local(ship, body);
			LivingEntity best = null;
			double bestD = Double.MAX_VALUE;
			for (LivingEntity hand : aboard) {
				Vec3 seat = hand.getAttached(Ship.SEAT);
				if (seat == null || hand == body || Downed.isDowned(hand) || caring(hand) || hand.getUUID().equals(crew.captain)
					|| fighting && crew.gunners.containsValue(hand.getUUID())) {
					continue;
				}
				double d = seat.distanceToSqr(at);
				if (d < bestD) {
					bestD = d;
					best = hand;
				}
			}
			if (best != null) {
				CARE.put(best.getUUID(), new Care(id));
				Speech.say(level, best, Component.translatable("speech.jaymoskingdoms.crew.man_down." + level.getRandom().nextInt(MAN_DOWN_LINES)),
					Speech.YELLOW, CHAT_CARRY);
			}
		}
		for (LivingEntity hand : aboard) {
			Care c = CARE.get(hand.getUUID());
			Vec3 seat = hand.getAttached(Ship.SEAT);
			if (c == null || seat == null) {
				continue;
			}
			LivingEntity body = bodies.get(c.body);
			Vec3 at = local(ship, body);
			Crew.Walk walk = crew.walks.computeIfAbsent(hand.getUUID(), k -> new Crew.Walk());
			if (seat.distanceToSqr(at) > CARE_REACH * CARE_REACH) {
				hand.setShiftKeyDown(false);
				c.ticks = 0;
				if (walk.way.isEmpty()) {
					// a run along her deck to the place nearest them
					Vec3 to = deck.nearest(at);
					List<Vec3> way = to == null ? null : deck.path(seat, to);
					if (way == null || way.isEmpty()) {
						if (to == null || to.distanceToSqr(at) > CARE_REACH * CARE_REACH) {
							// no way to them on deck: someone else may have one
							stopCaring(hand);
						}
						continue;
					}
					walk.way = new java.util.ArrayList<>(way);
					walk.pace = Corsairs.RUN_PACE;
				}
				continue;
			}
			// at their side: kneeling, hands busy
			walk.way.clear();
			hand.setShiftKeyDown(true);
			if (hand instanceof net.minecraft.world.entity.Mob mob) {
				mob.getLookControl().setLookAt(body.getX(), body.getY() + 0.2, body.getZ());
			}
			if (++c.ticks % 25 == 0) {
				hand.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
			}
			if (c.ticks >= CARE_TICKS) {
				// (Powers gets them up on their next tick, and sees to the bandaging)
				body.addTag(Downed.REVIVE_TAG);
				body.addTag(Downed.BANDAGED_TAG);
				level.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.8, body.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
				if (body instanceof ServerPlayer p) {
					Speech.sayTo(level, hand, p, Component.translatable("speech.jaymoskingdoms.crew.up." + level.getRandom().nextInt(UP_LINES),
						StoryTalk.shipmate(level, p, hand) ? StoryTalk.berth(p, hand) : Component.translatable("story.jaymoskingdoms.berth.friend")), Speech.YELLOW);
				}
				stopCaring(hand);
			}
		}
	}

	/** Where {@code e} is on her deck, in her frame: a crewman's station, anyone else where they stand. */
	private static Vec3 local(Ship ship, LivingEntity e) {
		Vec3 seat = e.getVehicle() == ship ? e.getAttached(Ship.SEAT) : null;
		return seat != null ? seat : ship.toLocal(e.position());
	}

	private static void stopCaring(LivingEntity hand) {
		if (CARE.remove(hand.getUUID()) != null) {
			hand.setShiftKeyDown(false);
		}
	}

	// ---- (0.7.246) dressed for the sea ---------------------------------------------------------------------------------

	/** (0.7.259) Their weapons while they're about their work: stowed, out of their hands, till there's a fight. */
	static final AttachmentType<List<ItemStack>> STOWED = AttachmentRegistry.create(JaymosKingdoms.id("crew_stowed"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));
	/** (0.7.259) The last tick each one had a foe (they stow their weapons a while after). */
	private static final Map<UUID, Long> LAST_FOE = new HashMap<>();

	/** (0.7.259) {@code mob}'s main and off hand put away (stowed), their hands empty; nothing if they're already stowed. */
	static void stow(LivingEntity mob) {
		ItemStack main = mob.getMainHandItem(), off = mob.getOffhandItem();
		if (main.isEmpty() && off.isEmpty() || mob.hasAttached(STOWED)) {
			return;
		}
		mob.setAttached(STOWED, List.of(main.copy(), off.copy()));
		mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
	}

	/** (0.7.259) {@code mob} takes up their stowed weapons; true if they had any. */
	static boolean arm(LivingEntity mob) {
		List<ItemStack> kit = mob.getAttached(STOWED);
		if (kit == null) {
			return false;
		}
		mob.removeAttached(STOWED);
		if (kit.size() > 0 && mob.getMainHandItem().isEmpty()) {
			mob.setItemSlot(EquipmentSlot.MAINHAND, kit.get(0).copy());
		}
		if (kit.size() > 1 && mob.getOffhandItem().isEmpty()) {
			mob.setItemSlot(EquipmentSlot.OFFHAND, kit.get(1).copy());
		}
		mob.playSound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), 0.8F, 1.0F);
		return true;
	}

	/**
	 * (0.7.259) A crew from before (guards, some in plate): out of their armour, worn and packed, sailors by trade, their
	 * weapons stowed unless there's a fight on.
	 */
	private static void dress(PathfinderMob mob, boolean fighting) {
		mob.removeTag(KNIGHT);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && !mob.getItemBySlot(slot).isEmpty()) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		McaCompat.toSea(mob);
		if (!fighting) {
			stow(mob);
		}
	}

	/**
	 * (From {@link Folk#customServerAiStep}) With MCA, one of a crew already aboard (a world from before) is replaced at
	 * their station by a villager of their blood in their gear, and her captain's, gunners' and walks' records follow.
	 * True if so (the folk is gone).
	 */
	static boolean convert(ServerLevel level, Folk folk) {
		if (!McaCompat.LOADED || !(folk.getVehicle() instanceof Ship ship) || ship.crew == null || Downed.isDowned(folk)) {
			return false;
		}
		Crew crew = ship.crew;
		Vec3 seat = folk.getAttached(Ship.SEAT);
		if (seat == null) {
			return false;
		}
		PathfinderMob v = villager(level, folk, crew.side);
		if (v == null) {
			return false;
		}
		UUID was = folk.getUUID();
		float health = folk.getHealth() / folk.getMaxHealth();
		folk.stopRiding();
		folk.discard();
		v.setHealth(Math.max(1.0F, v.getMaxHealth() * health));
		if (!ship.takeAboard(v, seat)) {
			v.discard();
			return true;
		}
		UUID now = v.getUUID();
		if (was.equals(crew.captain)) {
			crew.captain = now;
			Faction f = Factions.get(level.getServer(), crew.faction);
			if (f != null && f.leader().map(was::equals).orElse(false)) {
				// a cove's captain is its leader
				Factions.update(level.getServer(), f.id(), x -> x.withLeader(java.util.Optional.of(now)));
			}
		}
		crew.gunners.replaceAll((gun, who) -> who.equals(was) ? now : who);
		crew.walks.remove(was);
		return true;
	}

	// ---- (0.7.242) home from a voyage ----------------------------------------------------------------------------------

	/**
	 * Andy: "they should be able to decide to continue as a whaler or leave with their riches when we return to land."
	 * {@code ship}'s crew villagers come ashore at {@code shore}, each with their lay of the catch in their pack: each
	 * decides, by their temperament, the catch and their rank, whether to sign on again (they wait about the harbour,
	 * still her crew, and go out with her next voyage: {@link #waiting}) or go ashore for good (discharged, an ordinary
	 * villager of the town). Before she's laid up ({@code Whalers.home}).
	 */
	static void payOff(ServerLevel level, Ship ship, Crew crew, BlockPos shore, int lay, int whales) {
		RandomSource random = level.getRandom();
		int stayed = 0, left = 0;
		for (LivingEntity e : List.copyOf(ship.crewAboard())) {
			if (!(e instanceof PathfinderMob mob) || !tagged(mob) || !(mob instanceof Worker w)) {
				continue;
			}
			boolean captain = mob.getUUID().equals(crew.captain);
			boolean leaves = random.nextFloat() < leaveChance(mob, whales, captain);
			mob.stopRiding();
			crew.walks.remove(mob.getUUID());
			BlockPos near = shore.offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			BlockPos ground = ShutIn.openGround(level, near);
			Vec3 at = Vec3.atBottomCenterOf(ground != null ? ground : shore);
			mob.snapTo(at.x, at.y, at.z, mob.getYRot(), 0.0F);
			mob.getNavigation().stop();
			ItemStack pay = McaCompat.stow(mob, new ItemStack(Items.EMERALD, lay));
			if (!pay.isEmpty()) {
				mob.spawnAtLocation(level, pay);
			}
			if (leaves) {
				mob.removeTag(TAG);
				mob.removeTag(sideTag(crew.side));
				if (w instanceof Enlisted enlisted) {
					enlisted.discharge();
				}
				left++;
			} else {
				mob.setHomeTo(shore, PORT_HOME);
				stayed++;
			}
			Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.crew." + (leaves ? "leaves." : "signs_on.") + random.nextInt(SIGN_LINES), crew.name),
				Speech.YELLOW, CHAT_CARRY);
			Standing.update(level, w);
		}
		if (stayed + left > 0) {
			Raids.tell(level, shore, Component.translatable("message.jaymoskingdoms.whaler.paid_off", crew.name, stayed, left)
				.withStyle(net.minecraft.ChatFormatting.AQUA));
		}
	}

	/** How likely {@code mob} is to go ashore for good after a voyage that took {@code whales}. */
	static float leaveChance(PathfinderMob mob, int whales, boolean captain) {
		float chance = LEAVE_BASE + Math.min(LEAVE_WHALES_MOST, whales * LEAVE_PER_WHALE);
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t != null) {
			chance += switch (t) {
				// a full purse is all a greedy one went to sea for; the timid and the gloomy have had enough of it
				case GREEDY -> 0.3F;
				case TIMID -> 0.25F;
				case GLOOMY -> 0.2F;
				case HOTHEADED, SPITEFUL -> 0.1F;
				case DUTIFUL -> -0.25F;
				case BRAVE -> -0.15F;
				case CHEERFUL, DEVOUT -> -0.1F;
				default -> 0.0F;
			};
		}
		if (captain) {
			chance *= LEAVE_CAPTAIN;
		}
		return net.minecraft.util.Mth.clamp(chance, LEAVE_LEAST, LEAVE_MOST);
	}

	/**
	 * One of {@code side}'s crew of {@code role} for {@code faction} waiting about the harbour round {@code home} for
	 * her next voyage (signed on again, {@link #payOff}), or null: they go aboard her before anyone new is taken on.
	 */
	static @Nullable PathfinderMob waiting(ServerLevel level, int faction, Crew.Side side, int role, BlockPos home) {
		String tag = sideTag(side);
		PathfinderMob best = null;
		double bestD = Double.MAX_VALUE;
		for (PathfinderMob m : level.getEntitiesOfClass(PathfinderMob.class, new net.minecraft.world.phys.AABB(home).inflate(PORT_RANGE),
			m -> m.isAlive() && !m.isPassenger() && tagged(m) && m.entityTags().contains(tag) && role(m) == role && faction(m) == faction
				&& !Downed.isDowned(m))) {
			double d = m.distanceToSqr(Vec3.atCenterOf(home));
			if (d < bestD) {
				best = m;
				bestD = d;
			}
		}
		if (best != null && best.isSleeping()) {
			best.stopSleeping();
		}
		return best;
	}

	// ---- aboard -----------------------------------------------------------------------------------------------------

	/**
	 * A crew villager's server step (from {@code Workers.step}, before their brain): aboard her (or in one of her boats),
	 * their brain waits (true) and they do as her crew; set down ashore, they live as villagers (false).
	 */
	static boolean step(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (mob.tickCount % Standing.EVERY == 0) {
			Standing.update(level, w);
		}
		if (!mob.isPassenger()) {
			return false;
		}
		if (Downed.isDowned(mob) || !(mob.getVehicle() instanceof Ship ship) || ship.crew == null) {
			// downed, or pulling a whaleboat: nothing else
			return true;
		}
		Crew crew = ship.crew;
		long now = level.getGameTime();
		if ((mob.tickCount + mob.getId()) % DRESS_EVERY == 0) {
			Long had = LAST_FOE.get(mob.getUUID());
			dress(mob, crew.target >= 0 || had != null && now - had < CALM_TICKS);
			if (crew.side == Crew.Side.WHALER) {
				// (0.7.246) her own hands aboard and her crew know each other: no introductions among shipmates
				for (ServerPlayer p : Corsairs.playersAboard(level, ship)) {
					if (StoryTalk.shipmate(level, p, mob)) {
						StoryTalk.knowEachOther(p, mob);
					}
				}
			}
		}
		LivingEntity foe = foe(level, mob, w, ship, crew);
		if (foe != null) {
			// (0.7.259) weapons up
			LAST_FOE.put(mob.getUUID(), now);
			arm(mob);
			fight(level, mob, w, foe, now);
		} else if (McaCompat.busy(mob)) {
			// someone's talking to them: they look at them
			Player near = level.getNearestPlayer(mob, 8.0);
			if (near != null) {
				mob.getLookControl().setLookAt(near, 30.0F, 30.0F);
			}
		} else if ((mob.tickCount + mob.getId()) % 20 == 0) {
			chatter(level, mob, w, ship, crew, now);
		}
		if (w.role() == Pirates.CAPTAIN && mob.tickCount % 5 == 0 && Pirates.isCoveCaptain(mob)) {
			// a cove's captain is its boss: his bar to those who've struck him
			ServerBossEvent bar = BARS.computeIfAbsent(mob.getUUID(), id -> new ServerBossEvent(id,
				Component.translatable("entity.jaymoskingdoms.crew_captain_titled", mob.getDisplayName(), crew.name), BossEvent.BossBarColor.RED,
				BossEvent.BossBarOverlay.NOTCHED_10));
			bar.setProgress(mob.getHealth() / mob.getMaxHealth());
			BossBars.show(level, mob, bar);
		}
		return true;
	}

	/** Who they're fighting: the one they had, whoever hurt them, a soul aboard her captain's foe, or one of their own seeing. */
	private static @Nullable LivingEntity foe(ServerLevel level, PathfinderMob mob, Worker w, Ship ship, Crew crew) {
		LivingEntity had = mob.getTarget();
		if (had != null && (!had.isAlive() || had.isRemoved() || had.distanceToSqr(mob) > LET_GO * LET_GO
			|| had instanceof Player p && (p.isCreative() || p.isSpectator()))) {
			mob.setTarget(null);
			had = null;
		}
		if (had != null || !combatant(w.role())) {
			return had;
		}
		if ((mob.tickCount + mob.getId()) % 10 != 0) {
			return null;
		}
		LivingEntity hurt = mob.getLastHurtByMob();
		if (hurt != null && hurt.isAlive() && mob.tickCount - mob.getLastHurtByMobTimestamp() < HURT_MEMORY && !friend(level, w, crew, hurt)) {
			mob.setTarget(hurt);
			return hurt;
		}
		LivingEntity best = null;
		if (crew.target >= 0 && level.getEntity(crew.target) instanceof Ship enemy) {
			best = nearest(level, mob, w, crew, enemy.getBoundingBox().inflate(2.0), Pirates.PISTOL_FURTHEST);
		}
		if (best == null) {
			best = nearest(level, mob, w, crew, mob.getBoundingBox().inflate(SIGHT), SIGHT);
		}
		if (best != null) {
			mob.setTarget(best);
		}
		return best;
	}

	private static @Nullable LivingEntity nearest(ServerLevel level, PathfinderMob mob, Worker w, Crew crew, net.minecraft.world.phys.AABB box, double range) {
		LivingEntity best = null;
		double bestD = range * range;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != mob && e.isAlive() && !e.isSpectator())) {
			double d = e.distanceToSqr(mob);
			if (d < bestD && hostile(level, w, crew, e) && mob.hasLineOfSight(e)) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** Whether a crewman of {@code crew} (role and side {@code w}) fights {@code e} on sight. */
	static boolean hostile(ServerLevel level, Worker w, Crew crew, LivingEntity e) {
		if (e instanceof ServerPlayer p) {
			if (p.isCreative()) {
				return false;
			}
			return crew.side == Crew.Side.PIRATE ? Pirates.prey(crew.faction, p) : Reputation.get(p, crew.faction) <= Reputation.HOSTILE;
		}
		return Corsairs.enemyCrew(level, w.factionId(), Pirates.isPirate(w.role()), e);
	}

	/** Whether {@code e} is on their side (their own ship's, or an ally's): never their foe for hurting them by mistake. */
	private static boolean friend(ServerLevel level, Worker w, Crew crew, LivingEntity e) {
		int theirs = faction(e);
		if (theirs < 0) {
			return false;
		}
		return theirs == w.factionId() || Relations.between(level.getServer(), theirs, w.factionId()) == Relation.ALLIED;
	}

	/** Whether {@code role} fights (a powder monkey runs powder, and nothing else). */
	static boolean combatant(int role) {
		return role != Pirates.POWDER_MONKEY;
	}

	/** At their foe: pistols for a pirate's captain and gunners (and shouts), a bow if they hold one, and their blade at arm's length. */
	private static void fight(ServerLevel level, PathfinderMob mob, Worker w, LivingEntity foe, long now) {
		mob.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		int role = w.role();
		long[] ready = READY.computeIfAbsent(mob, m -> new long[] {now, now + level.getRandom().nextInt(BOW_JITTER + 1)});
		double d = mob.distanceTo(foe);
		if (d <= REACH + foe.getBbWidth() * 0.5 && now >= ready[0]) {
			ready[0] = now + SWING_GAP;
			mob.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			mob.doHurtTarget(level, foe);
			return;
		}
		if (Pirates.isPirate(role)) {
			Pirates.crewFight(level, mob, role);
			return;
		}
		ItemStack held = mob.getMainHandItem();
		if ((held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem) && now >= ready[1] && d >= BOW_NEAREST && d <= BOW_FURTHEST
			&& mob.hasLineOfSight(foe)) {
			ready[1] = now + BOW_RELOAD + level.getRandom().nextInt(BOW_JITTER + 1);
			loose(level, mob, held, foe);
		}
	}

	/** An arrow at {@code foe} (as a folk archer looses one). */
	private static void loose(ServerLevel level, PathfinderMob mob, ItemStack bow, LivingEntity foe) {
		ItemStack arrows = new ItemStack(Items.ARROW);
		AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, arrows, 1.0F, bow);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double dx = foe.getX() - mob.getX(), dz = foe.getZ() - mob.getZ();
		double dy = foe.getY(1.0 / 3.0) - arrow.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		Javelins.mark(arrow);
		Projectile.spawnProjectileUsingShoot(arrow, level, arrows, dx, dy + flat * 0.2, dz, 1.6F, 4.0F);
		mob.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
	}

	// ---- talk ---------------------------------------------------------------------------------------------------------

	/**
	 * Talk about her deck while a player is near (one line a ship every so often, and nobody talks over a fight): her
	 * captain welcomes a newcomer aboard; otherwise someone says something of their trade and side, or of their
	 * temperament.
	 */
	private static void chatter(ServerLevel level, PathfinderMob mob, Worker w, Ship ship, Crew crew, long now) {
		if (crew.target >= 0 || mob.isSleeping()) {
			return;
		}
		boolean captain = mob.getUUID().equals(crew.captain);
		RandomSource random = level.getRandom();
		if (captain) {
			java.util.Set<UUID> welcomed = WELCOMED.computeIfAbsent(ship.getUUID(), k -> new java.util.HashSet<>());
			List<ServerPlayer> aboard = Corsairs.playersAboard(level, ship);
			welcomed.retainAll(aboard.stream().map(Entity::getUUID).toList());
			for (ServerPlayer p : aboard) {
				if (!welcomed.contains(p.getUUID()) && p.distanceTo(mob) < WELCOME_RANGE * 2.0 && !hostile(level, w, crew, p)) {
					welcomed.add(p.getUUID());
					// (0.7.246) one of her own hands: welcomed back aboard by their berth, not as a guest
					Component welcome = StoryTalk.shipmate(level, p, mob)
						? Component.translatable("speech.jaymoskingdoms.crew.welcome.hand", crew.name, StoryTalk.berth(p, mob))
						: Component.translatable("speech.jaymoskingdoms.crew.welcome." + crew.side.id(), crew.name);
					Speech.sayTo(level, mob, p, welcome, Speech.YELLOW);
					NEXT_LINE.put(ship.getUUID(), now + CHAT_GAP);
					return;
				}
			}
		}
		Player near = level.getNearestPlayer(mob, CHAT_RANGE);
		Long next = NEXT_LINE.get(ship.getUUID());
		if (near == null || near.isSpectator() || next != null && now < next) {
			return;
		}
		if (next == null) {
			// (the first of her crew to see someone waits a little: not all at once)
			NEXT_LINE.put(ship.getUUID(), now + random.nextInt(CHAT_JITTER));
			return;
		}
		NEXT_LINE.put(ship.getUUID(), now + CHAT_GAP + random.nextInt(CHAT_JITTER));
		if (near instanceof ServerPlayer hand && random.nextInt(MATE_ONE_IN) == 0 && StoryTalk.shipmate(level, hand, mob)) {
			// (0.7.246) a word to one of her own hands, by their berth
			mob.getLookControl().setLookAt(hand, 30.0F, 30.0F);
			Speech.sayTo(level, mob, hand, Component.translatable("speech.jaymoskingdoms.crew.to_hand." + random.nextInt(TO_HAND_LINES),
				StoryTalk.berth(hand, mob), crew.name), Speech.WHITE);
			return;
		}
		Component line;
		Temperaments.Temperament t = Temperaments.of(mob);
		if (t != null && random.nextInt(TEMPER_ONE_IN) == 0) {
			line = Component.translatable("speech.jaymoskingdoms.crew.temper." + t.key());
		} else if (captain) {
			line = Component.translatable("speech.jaymoskingdoms.crew.captain." + crew.side.id() + "." + random.nextInt(CAPTAIN_LINES), crew.name);
		} else {
			String trade = w.role() == Pirates.POWDER_MONKEY ? "monkey" : crew.side.id();
			line = Component.translatable("speech.jaymoskingdoms.crew." + trade + "." + random.nextInt(HAND_LINES), crew.name);
		}
		mob.getLookControl().setLookAt(near, 30.0F, 30.0F);
		Speech.say(level, mob, line, Speech.WHITE, CHAT_CARRY);
	}

	// ---- what MCA's screen shows ------------------------------------------------------------------------------------

	/** Their lines for MCA's screen: the ship they serve and what as, her captain's name, and what she's about. */
	static Standing.@Nullable Lines lines(ServerLevel level, PathfinderMob mob, Worker w) {
		if (!mob.isPassenger() && mob.entityTags().contains(sideTag(Crew.Side.WHALER))) {
			// (0.7.242) signed on again, waiting in port for her next voyage
			Faction f = Factions.get(level.getServer(), w.factionId());
			return new Standing.Lines(List.of(Component.translatable("standing.jaymoskingdoms.crew.in_port", f == null ? Component.literal("?") : Component.literal(f.name()))
				.withStyle(net.minecraft.ChatFormatting.GOLD)), List.of(Component.translatable("standing.jaymoskingdoms.crew.in_port.hover")));
		}
		if (!(mob.getVehicle() instanceof Ship ship) || ship.crew == null) {
			return null;
		}
		Crew crew = ship.crew;
		List<Component> lines = new java.util.ArrayList<>(), hovers = new java.util.ArrayList<>();
		boolean captain = mob.getUUID().equals(crew.captain);
		Faction f = Factions.get(level.getServer(), crew.faction);
		Component side = f == null ? Component.translatable("standing.jaymoskingdoms.crew.side." + crew.side.id())
			: Component.literal(f.name());
		Component as = Component.translatable("standing.jaymoskingdoms.crew.role." + (captain ? "captain" : roleKey(w.role(), crew.side, crew.gunners.containsValue(mob.getUUID()))));
		lines.add(Component.translatable("standing.jaymoskingdoms.crew", as, crew.name).withStyle(net.minecraft.ChatFormatting.GOLD));
		hovers.add(Component.translatable("standing.jaymoskingdoms.crew.hover." + crew.side.id(), side));
		if (!captain && crew.captain != null && level.getEntity(crew.captain) instanceof LivingEntity c && c.isAlive()) {
			lines.add(Component.translatable("standing.jaymoskingdoms.crew.captain", c.getDisplayName()));
			hovers.add(Component.empty());
		}
		String doing = crew.target >= 0 ? "fighting" : switch (crew.mode) {
			case "chase", "engage" -> "fighting";
			case "run" -> "running";
			case "raid" -> "raid";
			case "voyage" -> "voyage";
			case "fitting" -> "fitting";
			default -> crew.side == Crew.Side.WHALER ? "whaling" : "patrol";
		};
		lines.add(Component.translatable("standing.jaymoskingdoms.duty", Component.translatable("standing.jaymoskingdoms.crew.doing." + doing))
			.withStyle(net.minecraft.ChatFormatting.WHITE));
		hovers.add(Component.empty());
		return new Standing.Lines(List.copyOf(lines), List.copyOf(hovers));
	}

	/** (For MCA's chat AI, {@link ChatPrompts}) who they are aboard: "" for anyone who isn't one of a crew aboard her. */
	static String prompt(LivingEntity e) {
		if (!tagged(e) || !(e.getVehicle() instanceof Ship ship) || ship.crew == null) {
			return "";
		}
		Crew crew = ship.crew;
		boolean captain = e.getUUID().equals(crew.captain);
		String as = captain ? "captain" : switch (roleKey(role(e), crew.side, crew.gunners.containsValue(e.getUUID()))) {
			case "gunner" -> "a gunner";
			case "monkey" -> "a powder monkey (a ship's boy who runs powder to the guns)";
			case "deckhand" -> "a deckhand";
			case "seaman" -> "an able seaman (a sailor who takes up arms when there's a fight)";
			case "whaleman" -> "a whaleman";
			default -> "a hand";
		};
		Faction f = e.level().getServer() == null ? null : Factions.get(e.level().getServer(), crew.faction);
		String of = f == null ? "" : " of " + f.name();
		String what = switch (crew.side) {
			case PIRATE -> "a pirate ship" + of + ", that preys on any ship that isn't a pirate";
			case NAVY -> "a warship" + of + ", that hunts pirates and the realm's enemies";
			case MERCHANT -> "a merchantman" + of + ", carrying goods between realms' harbours";
			case WHALER -> "a whaler" + of + ", out hunting great whales for their oil";
		};
		return "You serve as " + as + " aboard " + crew.name + ", " + what + ". You live at sea and are aboard her now.";
	}

	/** Her captain's title on his nametag. */
	static @Nullable Component title(PathfinderMob mob) {
		return mob.getVehicle() instanceof Ship ship && ship.crew != null && mob.getUUID().equals(ship.crew.captain)
			? Component.translatable("standing.jaymoskingdoms.crew.title") : null;
	}

	private static String roleKey(int role, Crew.Side side, boolean gunner) {
		if (role == Pirates.GUNNER || gunner) {
			return "gunner";
		}
		if (role == Pirates.POWDER_MONKEY) {
			return "monkey";
		}
		if (role == Pirates.DECKHAND) {
			return "deckhand";
		}
		return switch (side) {
			// (0.7.259) no marines: her sailors fight
			case NAVY -> "seaman";
			case WHALER -> "whaleman";
			default -> "hand";
		};
	}
}
