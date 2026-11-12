package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Talk on the road (docs/Pawn-Plan.md rows D2, D3 and B3; after the pawns who remark on the land, on their master and on
 * what they learned in other worlds in Dragon's Dogma, a commercial game: ideas only). Words over their heads
 * ({@link Callouts}), never chat. One remark per player at most every {@link #GAP} (a companion told at the table to talk
 * more or less halves or doubles it, {@link TableTalk#chatter}), none while you sneak.
 * <ul>
 * <li><b>The world</b> (D2): a new kind of land ("land.forest", "land.desert"...), a ruin, a tripwire or pressure plate
 * close by ("land.trap"), a town glad or grim (its folk's MCA mood).</li>
 * <li><b>You</b> (D3): badly hurt, starving, in new armour, back on your feet.</li>
 * <li><b>What they know</b> (B3): a companion who saw a quest of a kind through (with you, or with whoever had them
 * before) tells you which way its end lies, once a quest. A learned one ({@link #LEARNED} steps, a close bond, or a
 * tongue-speaker) now and then passes on a lore tip, the kind the loading screen gives.</li>
 * <li><b>A close bond</b> (G1): a greeting when you come back after a while away ({@link #AWAY}).</li>
 * <li><b>The foe</b>: a band on its camp's ground warns you off ("This is our wood, outlander"); one whose camp your
 * quest is about knows who sent you; in a fight they taunt a badly hurt player, a broken shield or armour piece, and the
 * last arrow.</li>
 * </ul>
 */
public final class Remarks {
	/** Ticks between two remarks to one player; between two of one kind. */
	static final int GAP = 1200, KIND_GAP = 6000;
	/** Looked over this often (ticks); companions this near (blocks) talk. */
	static final int EVERY = 40;
	static final double NEAR = 16.0;
	/** Hurt is under this share of health; starving at or under this food. */
	static final float HURT = 0.35F;
	static final int STARVING = 6;
	/** Steps learned ({@link Learners}) to give lore tips; chance a quiet spell brings one. */
	static final int LEARNED = 4;
	static final float TIP_CHANCE = 0.15F;
	/** The loading screen's tips ({@code gui.jaymoskingdoms.story.hint.N}). */
	static final int TIPS = 140;
	/** A close companion greets you after this long apart (ticks). */
	static final int AWAY = 12000;
	/** A band warns you off this near its camp (blocks); once per camp per player per this long (ticks). */
	static final double CAMP_GROUND = 40.0;
	static final int WARN_GAP = 6000;
	/** Armour and shields this worn count as broken (share of durability gone). */
	static final float BROKEN = 0.9F;
	/** Kinds of structure that count as a ruin. */
	private static final String[] RUINS = {"ruin", "abandoned", "tomb", "crypt", "ancient", "pyramid", "temple", "lost", "sunken", "trail"};

	/** On a companion: quest templates it has seen through. Saved. */
	public static final AttachmentType<List<String>> QUESTS = AttachmentRegistry.create(JaymosKingdoms.id("comrade_quests"),
		builder -> builder.persistent(Codec.STRING.listOf()));

	/** What each player has had said to them lately. */
	private static final class Heard {
		long last = -GAP;
		final Map<String, Long> kinds = new HashMap<>();
		String land = "";
		int armour;
		boolean wasDowned;
		final Set<String> told = new HashSet<>();
		final Map<UUID, Long> seen = new HashMap<>();
	}

	private static final Map<UUID, Heard> HEARD = new HashMap<>();

	private Remarks() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Remarks::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HEARD.clear());
	}

	/** (Quests) {@code player} turned in a quest: their companions by remember its kind. */
	static void questDone(ServerPlayer player, Quest q) {
		for (Mob m : Bonds.companions(player, 32.0)) {
			List<String> known = new ArrayList<>(m.getAttachedOrElse(QUESTS, List.of()));
			known.remove(q.template());
			known.add(q.template());
			while (known.size() > 24) {
				known.remove(0);
			}
			m.setAttached(QUESTS, List.copyOf(known));
		}
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		if (now % EVERY != 0) {
			return;
		}
		long time = server.overworld().getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}
			Heard h = HEARD.computeIfAbsent(player.getUUID(), u -> new Heard());
			friends(player, h, time);
			foes(player, h, time);
		}
		HEARD.keySet().removeIf(u -> server.getPlayerList().getPlayer(u) == null);
	}

	// ---- your companions -----------------------------------------------------------------------------------------------

	private static void friends(ServerPlayer player, Heard h, long time) {
		ServerLevel level = player.level();
		boolean downed = Downed.isDowned(player);
		int armour = armour(player);
		String land = land(level, player.blockPosition());
		List<Mob> near = new ArrayList<>();
		for (Mob m : Bonds.companions(player, NEAR)) {
			if (Comrades.target(m) == null && !Comrades.busy(m)) {
				near.add(m);
			}
		}
		if (near.isEmpty() || downed) {
			h.wasDowned |= downed;
			h.armour = armour;
			h.land = land;
			return;
		}
		Mob speaker = near.get(player.getRandom().nextInt(near.size()));
		// back on your feet: said at once
		if (h.wasDowned) {
			h.wasDowned = false;
			Callouts.say(speaker, "you.up", player, true, player.getDisplayName());
			h.last = time;
		}
		// a close companion who hasn't seen you in a while
		for (Mob m : near) {
			Long seen = h.seen.put(m.getUUID(), time);
			if (seen != null && time - seen > AWAY && Bonds.close(m)) {
				Callouts.say(m, "bond.greet", player, true, player.getDisplayName());
				h.last = time;
			}
		}
		boolean armourNew = h.armour != 0 && armour != 0 && armour != h.armour;
		h.armour = armour;
		boolean landNew = !land.isEmpty() && !land.equals(h.land) && !h.land.isEmpty();
		h.land = land.isEmpty() ? h.land : land;
		if (player.isShiftKeyDown() || time - h.last < gap(speaker)) {
			return;
		}
		String kind = null;
		Object[] args = {player.getDisplayName()};
		if (player.getHealth() < player.getMaxHealth() * HURT) {
			kind = "you.hurt";
		} else if (player.getFoodData().getFoodLevel() <= STARVING) {
			kind = "you.hungry";
		} else if (armourNew) {
			kind = "you.armour";
		} else if (landNew) {
			kind = "land." + land;
		} else if (trap(level, player.blockPosition())) {
			kind = "land.trap";
		} else if (ruin(level, player.blockPosition(), h)) {
			kind = "land.ruin";
		} else {
			String town = town(level, player);
			if (town != null && h.told.add("town:" + town.substring(town.indexOf(':') + 1))) {
				kind = town.substring(0, town.indexOf(':'));
			}
		}
		if (kind == null) {
			Object[] quest = quest(level, player, near, h);
			if (quest != null) {
				speaker = (Mob)quest[0];
				kind = "quest.way";
				args = new Object[] {quest[1], quest[2]};
			}
		}
		if (kind == null) {
			Mob learned = learned(near, player);
			if (learned != null && player.getRandom().nextFloat() < TIP_CHANCE) {
				speaker = learned;
				kind = "tip";
				args = new Object[] {Component.translatable("gui.jaymoskingdoms.story.hint." + (1 + player.getRandom().nextInt(TIPS)))};
			}
		}
		if (kind == null || time - h.kinds.getOrDefault(kind, -(long)KIND_GAP) < KIND_GAP && !kind.equals("quest.way")) {
			return;
		}
		if (Callouts.say(speaker, kind, player, false, args)) {
			h.last = time;
			h.kinds.put(kind, time);
		}
	}

	/** The gap before {@code speaker} talks again: halved or doubled by what they were told at the table. */
	private static int gap(Mob speaker) {
		int c = TableTalk.chatter(speaker);
		return c > 0 ? GAP / 2 : c < 0 ? GAP * 2 : GAP;
	}

	/** What's worn, as one number (0: nothing). */
	private static int armour(ServerPlayer player) {
		int sum = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				sum = sum * 31 + worn.getItem().hashCode();
			}
		}
		return sum;
	}

	/** The kind of land at {@code pos} (by its biome's name), or "". */
	static String land(ServerLevel level, BlockPos pos) {
		String b = level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		if (b.contains("desert") || b.contains("badlands")) {
			return "desert";
		}
		if (b.contains("swamp") || b.contains("mangrove")) {
			return "swamp";
		}
		if (b.contains("jungle")) {
			return "jungle";
		}
		if (b.contains("frozen") || b.contains("snow") || b.contains("ice") || b.contains("grove")) {
			return "snow";
		}
		if (b.contains("peak") || b.contains("mountain") || b.contains("slope") || b.contains("hill")) {
			return "mountain";
		}
		if (b.contains("ocean") || b.contains("beach")) {
			return "coast";
		}
		if (b.contains("forest") || b.contains("taiga") || b.contains("birch") || b.contains("wood")) {
			return "forest";
		}
		if (b.contains("plains") || b.contains("meadow") || b.contains("savanna")) {
			return "plains";
		}
		return "";
	}

	/** A tripwire or a pressure plate close by. */
	private static boolean trap(ServerLevel level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-5, -2, -5), at.offset(5, 2, 5))) {
			BlockState s = level.getBlockState(p);
			if (s.is(Blocks.TRIPWIRE) || s.is(BlockTags.PRESSURE_PLATES)) {
				return true;
			}
		}
		return false;
	}

	/** In a ruin not remarked on before. */
	private static boolean ruin(ServerLevel level, BlockPos at, Heard h) {
		Seats.Found found = Seats.structureAt(level, at, holder -> holder.unwrapKey().map(k -> {
			String path = k.identifier().getPath();
			for (String r : RUINS) {
				if (path.contains(r)) {
					return true;
				}
			}
			return false;
		}).orElse(false));
		if (found.status() != Seats.Status.FOUND || found.start() == null) {
			return false;
		}
		return h.told.add("ruin:" + found.start().getChunkPos().pack());
	}

	/** "town.glad:id" or "town.grim:id" for a settlement whose folk are glad or grim, else null. */
	private static @Nullable String town(ServerLevel level, ServerPlayer player) {
		Faction f = Factions.at(level.getServer(), level, player.blockPosition());
		if (f == null || f.type() == FactionType.BARBARIAN) {
			return null;
		}
		int sum = 0, n = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(32.0), v -> v.isAlive() && !v.isBaby())) {
			sum += McaCompat.mood(v);
			n++;
		}
		if (n < 3) {
			return null;
		}
		float mood = (float)sum / n;
		return mood >= 5.0F ? "town.glad:" + f.id() : mood <= -5.0F ? "town.grim:" + f.id() : null;
	}

	/** A companion that saw a quest of this kind through: [speaker, direction, blocks], once a quest. */
	private static Object @Nullable [] quest(ServerLevel level, ServerPlayer player, List<Mob> near, Heard h) {
		for (Quest q : Quests.log(player)) {
			if (q.objective().pos().isEmpty() || h.told.contains("quest:" + q.id())) {
				continue;
			}
			for (Mob m : near) {
				if (m.getAttachedOrElse(QUESTS, List.of()).contains(q.template())) {
					h.told.add("quest:" + q.id());
					BlockPos to = q.objective().pos().get();
					Vec3 d = Vec3.atCenterOf(to).subtract(player.position());
					Direction dir = Direction.getApproximateNearest(d.x, 0.0, d.z);
					int blocks = (int)Math.sqrt(d.x * d.x + d.z * d.z);
					return new Object[] {m, Component.translatable("comrades.jaymoskingdoms.dir." + dir.getSerializedName()), blocks};
				}
			}
		}
		return null;
	}

	/** A learned companion: steps enough, a close bond, or a tongue-speaker. */
	private static @Nullable Mob learned(List<Mob> near, ServerPlayer player) {
		for (Mob m : near) {
			if (m.getAttachedOrElse(Learners.WINS, 0) > 0 && Learners.steps(m.getAttachedOrElse(Learners.WINS, 0)) >= LEARNED
				|| Bonds.of(m, player.getUUID()) >= Bonds.CLOSE || Specialties.is(m, Specialties.Specialty.TONGUE)) {
				return m;
			}
		}
		return null;
	}

	// ---- the foe -------------------------------------------------------------------------------------------------------

	private static void foes(ServerPlayer player, Heard h, long time) {
		ServerLevel level = player.level();
		String mine = Comrades.side(player);
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20.0),
			m -> m.isAlive() && !Downed.isDowned(m) && Temperaments.humanoid(m) && FoeLore.keeps(Comrades.side(m))
				&& !Comrades.side(m).equals(mine) && m.hasLineOfSight(player))) {
			LivingEntity t = Comrades.target(m);
			if (t == player) {
				taunt(m, player);
				continue;
			}
			if (t != null || Ambushes.hiding(m)) {
				continue;
			}
			String side = Comrades.side(m);
			Camps.CampData camp = camp(level, side);
			if (camp != null && player.blockPosition().closerThan(camp.center(), CAMP_GROUND)) {
				Long warned = h.kinds.get("ours:" + side);
				if ((warned == null || time - warned > WARN_GAP) && Callouts.say(m, "foe.ours", player, false)) {
					h.kinds.put("ours:" + side, time);
				}
			}
			for (Quest q : Quests.log(player)) {
				if (q.objective().pos().isPresent() && q.objective().pos().get().closerThan(m.blockPosition(), 64.0)
					&& h.told.add("headed:" + q.id() + ":" + side)) {
					Callouts.say(m, "foe.headed", player, false, q.giver().name());
					break;
				}
			}
		}
	}

	/** The camp of a band side ("f:id"), or null. */
	private static Camps.@Nullable CampData camp(ServerLevel level, String side) {
		if (!side.startsWith("f:")) {
			return null;
		}
		try {
			return Camps.data(level.getServer(), Integer.parseInt(side.substring(2)));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** A foe fighting {@code player} taunts what they see. */
	private static void taunt(Mob m, ServerPlayer player) {
		if (player.getHealth() < player.getMaxHealth() * HURT) {
			Callouts.say(m, "taunt.hurt", player, false, player.getDisplayName());
		} else if (broken(player)) {
			Callouts.say(m, "taunt.broken", player, false, player.getDisplayName());
		} else if (lastArrow(player)) {
			Callouts.say(m, "taunt.arrow", player, false, player.getDisplayName());
		}
	}

	/** A shield or armour piece nearly worn through. */
	private static boolean broken(ServerPlayer player) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR || slot == EquipmentSlot.SADDLE) {
				continue;
			}
			ItemStack s = player.getItemBySlot(slot);
			boolean counts = slot.isArmor() || s.getItem() instanceof ShieldItem;
			if (counts && s.isDamageableItem() && s.getDamageValue() >= s.getMaxDamage() * BROKEN) {
				return true;
			}
		}
		return false;
	}

	/** Holding a bow with one arrow left. */
	private static boolean lastArrow(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		if (player.isCreative() || !(held.getItem() instanceof ProjectileWeaponItem)) {
			return false;
		}
		ItemStack ammo = player.getProjectile(held);
		if (ammo.isEmpty() || ammo.getCount() != 1) {
			return false;
		}
		int total = 0;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (ItemStack.isSameItem(s, ammo)) {
				total += s.getCount();
			}
		}
		return total == 1;
	}
}
