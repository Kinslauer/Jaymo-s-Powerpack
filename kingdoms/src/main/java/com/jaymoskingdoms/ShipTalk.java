package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.whaling.Whaling;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.270) Andy: "characters should have context sensitive talk options, for example asking the captain about work
 * should tell the player what their role is on the boat either as harpooner or otherwise."
 * <p>
 * "Talk about work" ({@link Careers#ANSWER}) with one of a ship's crew ({@link Crewmen}) is about the ship, not the town
 * she lies off:
 * <ul>
 * <li>A whaler's captain tells one of his hands their berth ({@link Whalers#berth}: harpooner, deckhand, powder monkey)
 * and what it asks of them, and how the voyage stands. He reckons their lay as the hold stands, lets them change berth
 * while she's fitting out (a harpooner brings irons of their own), and hands his harpooner irons once a voyage.</li>
 * <li>To one who isn't a hand he offers a berth while she's fitting out, or says she's full up.</li>
 * <li>Any other of her crew tells you your berth and theirs, and sends you to the captain.</li>
 * <li>A warship's, merchantman's or pirate's crew say what their ship is about and what they do aboard her.</li>
 * </ul>
 */
public final class ShipTalk {
	/** Below this many irons a harpooner can ask the captain for more; he hands over this many, once a voyage. */
	static final int IRONS_LOW = 2, IRONS_GIVEN = 4;
	/** Ticks to the minute, for "she sails in N minutes". */
	static final long MINUTE = 1200L;

	/** A talk's menu: who's talking, and what each option does. */
	private record Menu(int speaker, List<String> actions) {
	}

	private static final Map<UUID, Menu> MENUS = new HashMap<>();
	/** "ship/player" for each harpooner handed irons this voyage (a ship is new each voyage). */
	private static final Set<String> IRONS = new HashSet<>();

	private ShipTalk() {
	}

	static void register() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> MENUS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MENUS.clear();
			IRONS.clear();
		});
	}

	/** {@code villager} talks work as one of a ship's crew (aboard or waiting in port), not as one of a town. */
	public static boolean offersWork(LivingEntity villager) {
		return Crewmen.tagged(villager) && !villager.isBaby();
	}

	/** "Talk about work" with one of a ship's crew (MCA's menu). */
	public static void talk(ServerPlayer player, LivingEntity speaker) {
		ServerLevel level = player.level();
		Ship ship = speaker.getVehicle() instanceof Ship s && s.crew != null ? s : null;
		Crew.Side side = ship != null ? ship.crew.side : side(speaker);
		if (side == Crew.Side.WHALER) {
			whaler(level, player, speaker, ship);
			return;
		}
		boolean captain = ship != null && speaker.getUUID().equals(ship.crew.captain);
		String name = ship != null ? ship.crew.name : "";
		Component words;
		if (ship == null || side == null) {
			words = Component.translatable("talk.jaymoskingdoms.ship.ashore");
		} else if (captain && side == Crew.Side.MERCHANT) {
			Faction from = Factions.get(level.getServer(), ship.crew.faction);
			Faction to = ship.crew.boundFor < 0 ? null : Factions.get(level.getServer(), ship.crew.boundFor);
			words = Component.translatable("talk.jaymoskingdoms.ship.merchant.captain", name,
				from == null ? Component.translatable("talk.jaymoskingdoms.ship.no_realm") : Component.literal(from.name()),
				to == null ? Component.translatable("talk.jaymoskingdoms.ship.home_port") : Component.literal(to.name()));
		} else if (captain) {
			Faction from = Factions.get(level.getServer(), ship.crew.faction);
			words = Component.translatable("talk.jaymoskingdoms.ship." + side.id() + ".captain", name,
				from == null ? Component.translatable("talk.jaymoskingdoms.ship.no_realm") : Component.literal(from.name()));
		} else {
			words = Component.translatable("talk.jaymoskingdoms.ship." + side.id() + ".hand",
				Component.translatable("talk.jaymoskingdoms.ship.role." + role(speaker, ship)), name);
		}
		Speech.tell(level, speaker, player, words, Speech.WHITE);
	}

	// ---- a whaler -------------------------------------------------------------------------------------------------------

	private static void whaler(ServerLevel level, ServerPlayer player, LivingEntity speaker, @Nullable Ship ship) {
		Whalers.Voyage v = StoryTalk.voyageOf(level, speaker);
		boolean ours = v != null && ship != null && v.ship.equals(ship.getUUID());
		boolean captain = ship != null && speaker.getUUID().equals(ship.crew.captain);
		boolean hand = v != null && v.hands.contains(player.getUUID());
		if (!ours) {
			// waiting in port on her next voyage (or one left from a voyage gone)
			Speech.tell(level, speaker, player, Component.translatable(hand ? "talk.jaymoskingdoms.whaler.ashore_mate" : "talk.jaymoskingdoms.whaler.ashore"),
				Speech.WHITE);
			return;
		}
		if (hand && captain) {
			captainsHand(level, player, speaker, ship, v);
			return;
		}
		if (hand) {
			// one of her hands: your berth, and the captain's the one to ask about your lay
			String berth = Whalers.berth(v, player);
			Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.whaler.mate." + berth,
				Component.translatable("talk.jaymoskingdoms.ship.role." + role(speaker, ship))), Speech.WHITE);
			return;
		}
		if (!fitting(level, v)) {
			Speech.tell(level, speaker, player, Component.translatable(captain ? "talk.jaymoskingdoms.whaler.full" : "talk.jaymoskingdoms.whaler.full_hand",
				ship.crew.name), Speech.WHITE);
			return;
		}
		if (!captain) {
			Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.whaler.see_captain", ship.crew.name), Speech.WHITE);
			return;
		}
		// her captain, taking on hands
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (String berth : List.of(Whalers.HARPOONER, Whalers.DECKHAND, Whalers.MONKEY)) {
			actions.add("sign:" + berth);
			options.add(Component.translatable("talk.jaymoskingdoms.whaler.sign_as", Whalers.berthName(berth)));
			enabled.add(canTake(player, berth));
		}
		actions.add("close");
		options.add(Component.translatable("talk.jaymoskingdoms.work.close"));
		enabled.add(true);
		send(player, speaker, Component.translatable("talk.jaymoskingdoms.whaler.hiring", ship.crew.name, minutesLeft(level, v)), actions, options, enabled);
	}

	/** Her captain, to one of his hands: their berth and what it asks of them, and how the voyage stands. */
	private static void captainsHand(ServerLevel level, ServerPlayer player, LivingEntity speaker, Ship ship, Whalers.Voyage v) {
		String berth = Whalers.berth(v, player);
		Component words = Component.translatable("talk.jaymoskingdoms.whaler.berth." + berth, player.getDisplayName())
			.append(" ").append(stands(level, ship, v));
		List<String> actions = new ArrayList<>();
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		actions.add("lay");
		options.add(Component.translatable("talk.jaymoskingdoms.whaler.ask_lay"));
		enabled.add(true);
		if (berth.equals(Whalers.HARPOONER)) {
			actions.add("irons");
			options.add(Component.translatable("talk.jaymoskingdoms.whaler.ask_irons"));
			enabled.add(irons(player) < IRONS_LOW && !IRONS.contains(ship.getUUID() + "/" + player.getUUID()));
		}
		for (String other : List.of(Whalers.HARPOONER, Whalers.DECKHAND, Whalers.MONKEY)) {
			if (!other.equals(berth)) {
				actions.add("berth:" + other);
				options.add(Component.translatable("talk.jaymoskingdoms.whaler.change_to", Whalers.berthName(other)));
				enabled.add(fitting(level, v) && canTake(player, other));
			}
		}
		actions.add("close");
		options.add(Component.translatable("talk.jaymoskingdoms.work.close"));
		enabled.add(true);
		send(player, speaker, words, actions, options, enabled);
	}

	/** How the voyage stands, in her captain's words. */
	private static Component stands(ServerLevel level, Ship ship, Whalers.Voyage v) {
		if (fitting(level, v)) {
			return Component.translatable("talk.jaymoskingdoms.whaler.stands.fitting", minutesLeft(level, v));
		}
		return switch (ship.crew.mode) {
			case "outward" -> Component.translatable("talk.jaymoskingdoms.whaler.stands.outward");
			case "hunting" -> Component.translatable("talk.jaymoskingdoms.whaler.stands.hunting", v.whales, Whalers.WHALES);
			case "cutting" -> Component.translatable("talk.jaymoskingdoms.whaler.stands.cutting");
			default -> Component.translatable("talk.jaymoskingdoms.whaler.stands.homeward", v.whales);
		};
	}

	/** An answer to a talk about the ship; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Menu menu = MENUS.get(player.getUUID());
		if (menu == null || menu.speaker() != answer.entityId()) {
			return false;
		}
		MENUS.remove(player.getUUID());
		ServerLevel level = player.level();
		if (answer.option() < 0 || answer.option() >= menu.actions().size() || !(level.getEntity(answer.entityId()) instanceof LivingEntity speaker)
			|| !speaker.isAlive() || speaker.distanceTo(player) > Careers.TALK_RANGE || !(speaker.getVehicle() instanceof Ship ship) || ship.crew == null
			|| !speaker.getUUID().equals(ship.crew.captain)) {
			return true;
		}
		Whalers.Voyage v = StoryTalk.voyageOf(level, speaker);
		if (v == null || !v.ship.equals(ship.getUUID())) {
			return true;
		}
		String action = menu.actions().get(answer.option());
		String arg = action.contains(":") ? action.substring(action.indexOf(':') + 1) : "";
		switch (action.contains(":") ? action.substring(0, action.indexOf(':')) : action) {
			case "sign" -> {
				if (fitting(level, v) && canTake(player, arg) && !v.hands.contains(player.getUUID())) {
					Whalers.signOn(v, player, arg);
					Whalers.put(level.getServer(), ship.crew.faction, v);
					StoryTalk.knowEachOther(player, speaker);
					Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.whaler.signed." + arg, ship.crew.name), Speech.WHITE);
				}
			}
			case "berth" -> {
				if (fitting(level, v) && canTake(player, arg) && v.hands.contains(player.getUUID())) {
					Whalers.signOn(v, player, arg);
					Whalers.put(level.getServer(), ship.crew.faction, v);
					Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.whaler.signed." + arg, ship.crew.name), Speech.WHITE);
				}
			}
			case "lay" -> {
				if (v.hands.contains(player.getUUID())) {
					String berth = Whalers.berth(v, player);
					Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.whaler.lay." + berth,
						Whalers.lay(Whalers.worth(v), berth)), Speech.WHITE);
				}
			}
			case "irons" -> {
				String key = ship.getUUID() + "/" + player.getUUID();
				if (v.hands.contains(player.getUUID()) && Whalers.berth(v, player).equals(Whalers.HARPOONER) && !IRONS.contains(key)) {
					IRONS.add(key);
					ItemStack irons = new ItemStack(Whaling.HARPOON_ITEM, IRONS_GIVEN);
					if (!player.addItem(irons)) {
						player.spawnAtLocation(level, irons);
					}
					Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.whaler.irons_given"), Speech.WHITE);
				}
			}
			default -> {
			}
		}
		return true;
	}

	// ---- helpers --------------------------------------------------------------------------------------------------------

	/** She's still fitting out at the harbour (berths can be had or changed). */
	private static boolean fitting(ServerLevel level, Whalers.Voyage v) {
		return level.getGameTime() - v.launched < Whalers.FIT_OUT;
	}

	private static long minutesLeft(ServerLevel level, Whalers.Voyage v) {
		return Math.max(1L, (Whalers.FIT_OUT - (level.getGameTime() - v.launched) + MINUTE - 1) / MINUTE);
	}

	/** Whether her captain takes {@code player} in {@code berth}: a harpooner brings irons; a powder monkey is small. */
	private static boolean canTake(ServerPlayer player, String berth) {
		return switch (berth) {
			case Whalers.HARPOONER -> irons(player) > 0;
			case Whalers.MONKEY -> player.getScale() < StoryTalk.SMALL;
			default -> true;
		};
	}

	private static int irons(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.is(Whaling.HARPOON_ITEM)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Which side's crew {@code mob} is, by their tag (aboard or not), or null. */
	private static Crew.@Nullable Side side(LivingEntity mob) {
		for (Crew.Side side : Crew.Side.values()) {
			if (mob.entityTags().contains(Crewmen.sideTag(side))) {
				return side;
			}
		}
		return null;
	}

	/** What {@code mob} does aboard: gunner, powder monkey, deckhand, or their side's sailor. */
	private static String role(LivingEntity mob, @Nullable Ship ship) {
		int role = Crewmen.role(mob);
		if (role == Pirates.GUNNER || ship != null && ship.crew.gunners.containsValue(mob.getUUID())) {
			return "gunner";
		}
		if (role == Pirates.POWDER_MONKEY) {
			return "monkey";
		}
		if (role == Pirates.DECKHAND) {
			return "deckhand";
		}
		Crew.Side side = ship != null ? ship.crew.side : side(mob);
		return side == Crew.Side.NAVY ? "seaman" : side == Crew.Side.WHALER ? "whaleman" : "hand";
	}

	private static void send(ServerPlayer player, LivingEntity speaker, Component words, List<String> actions, List<Component> options, List<Boolean> enabled) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			Speech.tell(player.level(), speaker, player, words, Speech.WHITE);
			return;
		}
		MENUS.put(player.getUUID(), new Menu(speaker.getId(), List.copyOf(actions)));
		ServerPlayNetworking.send(player, new TalkNet.Ask(speaker.getId(), speaker.getDisplayName(), words, options, enabled));
	}
}
