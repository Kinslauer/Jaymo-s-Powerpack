package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;

/**
 * The barbarians: a camp's raiders ({@link Folk#BARBARIAN}: axes and swords, hide armour, war paint in their camp's
 * colour) and its chief ({@link Folk#CHIEF}: a mini-boss with a red boss bar, who rallies when hurt). They attack
 * players, every other faction's folk, villagers and iron golems on sight; a kingdom's (or fiefdom's) fighting folk
 * attack them on sight in turn. Their alerts only call their own side (see {@link Folk#sameSide}: camps of a kind are
 * allied with each other, so barbarians help barbarians). They are at war with the illager camps ({@link Illagers}), and
 * fall on any illager they see.
 * <p>(0.7.148) Andy: "Is there a way to affiliate with the barbarians? If not they should also be MCA and become non
 * hostile when you are negative rep."
 * <ul>
 * <li>An <b>outlaw</b> ({@link #outlaw}: someone a standing kingdom, fiefdom or village thinks ill of, below
 * {@link #OUTLAW_BELOW}) is spared by a barbarian camp's people, raiders, chief and shamans alike, on its ground and on
 * its raids: the realms' enemy is their friend. Not by a camp they've wronged: each barbarian they kill costs them
 * with his camp ({@link KingdomEvents}), and at {@link Reputation#HOSTILE} or below it hunts them as before. Seen
 * through a bandit mask or a costume, it's that identity's name that counts ({@link Reputation#get}).</li>
 * <li>With Minecraft Comes Alive, a camp's raiders at home are MCA villagers ({@link #mayBecomeVillager},
 * {@code McaBridge.convertRaider}): MCA guards in the camp's hides and axes, with their own names, faces, heritage
 * and talk, enlisted to the camp as {@link Folk#BARBARIAN}s. They fight through MCA's guard brain
 * ({@code McaGuardFoesMixin}, {@link McaCompat#foe}) with the same {@link #prey}. The chief, and the war bands sent
 * on raids, stay folk.</li>
 * </ul>
 */
public final class Barbarians {
	/** A raider: health, blows, pace. */
	static final double BARBARIAN_HEALTH = 30.0, BARBARIAN_DAMAGE = 4.5, BARBARIAN_SPEED = 0.32;
	/** The chief: health, blows, how hard he is to shove, and his armour's toughness. */
	static final double CHIEF_HEALTH = 160.0, CHIEF_DAMAGE = 8.0, CHIEF_KNOCKBACK_RESIST = 0.6, CHIEF_TOUGHNESS = 2.0;
	/** How far they spot prey (and kingdom folk spot them). */
	static final double SIGHT = 24.0;
	/** One raider in this many wears a hide cap. */
	static final int CAP_ONE_IN = 3;
	/** Hide (leather dyed a raw brown), and the chief's darker furs. */
	static final int HIDE = 0x7A5230, DARK_FUR = 0x3B2A20;
	/** (0.7.148) An outlaw: a standing realm thinks less of them than this. How long that's taken as known (ticks). */
	static final int OUTLAW_BELOW = 0, OUTLAW_CHECK_TICKS = 20;
	/** (0.7.148) An outlaw this near a camp that spares them is told so (blocks), at most this often per camp (ticks: a day). */
	static final double WELCOME_RANGE = 40.0;
	static final long WELCOME_GAP = 24000L;

	/** Player → {the tick it was looked at, 1 if an outlaw then}. */
	private static final Map<UUID, long[]> OUTLAWS = new HashMap<>();
	/** Player/camp → when they were last told the camp spares them. */
	private static final Map<String, Long> WELCOMED = new HashMap<>();

	private Barbarians() {
	}

	/** Defines the barbarian and chief roles (call once at init). */
	static void init() {
		Folk.defineRole(new Folk.Role(Folk.BARBARIAN, "barbarian", true, false, null, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BARBARIAN_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BARBARIAN_DAMAGE);
			folk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(BARBARIAN_SPEED);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
			Item[] weapons = {Items.STONE_AXE, Items.IRON_AXE, Items.STONE_SWORD, Items.IRON_SWORD, Items.STONE_AXE};
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[folk.getRandom().nextInt(weapons.length)]));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, HIDE));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, HIDE));
			if (folk.getRandom().nextInt(CAP_ONE_IN) == 0) {
				folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, DARK_FUR));
			}
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.barbarian_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(Folk.CHIEF, "chief", true, true, BossEvent.BossBarColor.RED, (folk, faction) -> {
			folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(CHIEF_HEALTH);
			folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(CHIEF_DAMAGE);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(CHIEF_KNOCKBACK_RESIST);
			folk.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(CHIEF_TOUGHNESS);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, DARK_FUR));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, faction.color().getTextureDiffuseColor() & 0xFFFFFF));
			folk.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, DARK_FUR));
			// (0.5.9) a chief goes by his own name (Slavic or Norse, now and then with a byname), the same every time
			folk.setCustomName(Component.literal(Names.chief(folk.getUUID().getLeastSignificantBits() ^ faction.id())));
		}));
	}

	/** (0.7.148) A camp's MCA villager raider's added health, to a raider's. */
	static final Identifier HEALTH = JaymosKingdoms.id("barbarian_health");

	/** (0.7.148) Telling outlaws the camps spare them; forgetting who was told when the server stops. */
	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Barbarians::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OUTLAWS.clear();
			WELCOMED.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> OUTLAWS.remove(handler.player.getUUID()));
	}

	private static ItemStack dyed(Item item, int rgb) {
		ItemStack stack = new ItemStack(item);
		stack.set(net.minecraft.core.component.DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	/** It's a camp's: a raider, the chief, or one of a camp's builders. */
	public static boolean isBarbarian(Folk folk) {
		return folk.style() == FactionType.BARBARIAN || folk.role() == Folk.BARBARIAN || folk.role() == Folk.CHIEF;
	}

	/** (0.7.148) An MCA villager raider of a camp (not a folk). */
	public static boolean isCampVillager(Entity e) {
		return !(e instanceof Folk) && e instanceof Worker w && w.role() == Folk.BARBARIAN;
	}

	/** It fights for a camp (a raider or the chief). */
	public static boolean isRaider(Folk folk) {
		return folk.role() == Folk.BARBARIAN || folk.role() == Folk.CHIEF;
	}

	/**
	 * The barbarians' extra goals (called from {@link Folk#registerGoals}): raiders and chiefs hunt players, villagers,
	 * golems and every other faction's folk; a realm's or village's fighting folk hunt barbarians.
	 */
	static void addGoals(Folk folk, GoalSelector goals, GoalSelector targets) {
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, LivingEntity.class, true,
			(target, level) -> (isRaider(folk) || Sorcery.campCaster(folk)) && prey(folk.factionId(), target)));
		// ((0.7.148) and a camp's MCA villagers)
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, LivingEntity.class, true,
			(target, level) -> folk.combatant() && !isBarbarian(folk) && (target instanceof Folk other ? isBarbarian(other) : isCampVillager(target))));
	}

	/** What {@code camp}'s raiders attack on sight: (0.7.148) not an outlaw it spares ({@link #spares}). */
	static boolean prey(int camp, LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator() && !spares(player, camp);
		}
		if (target instanceof Folk other) {
			return !isBarbarian(other);
		}
		if (isCampVillager(target)) {
			// (0.7.148) a camp's MCA villager: one of their own
			return false;
		}
		// (and illagers: the camps' old feud)
		return target instanceof Villager || target instanceof IronGolem || target instanceof net.minecraft.world.entity.monster.illager.AbstractIllager
			|| Illagers.isCampIllager(target);
	}

	// ---- outlaws (0.7.148) -------------------------------------------------------------------------------------------

	/** {@code camp} (a plain barbarian camp, not an orc, goblin, illager or pirate one) lets {@code player} be: an outlaw it has no grudge with. */
	public static boolean spares(ServerPlayer player, int camp) {
		MinecraftServer server = player.level().getServer();
		return server != null && plainCamp(server, camp) && outlaw(player) && Reputation.get(player, camp) > Reputation.HOSTILE;
	}

	/** {@code camp} is a barbarian camp of the plain kind (a chief and his raiders). */
	static boolean plainCamp(MinecraftServer server, int camp) {
		Faction f = Factions.get(server, camp);
		return f != null && f.type() == FactionType.BARBARIAN && Camps.BARBARIAN.equals(Camps.kind(server, camp));
	}

	/** Some standing kingdom, fiefdom or village thinks ill of {@code player} (below {@link #OUTLAW_BELOW}); looked up once a second. */
	public static boolean outlaw(ServerPlayer player) {
		long now = player.level().getGameTime();
		long[] seen = OUTLAWS.get(player.getUUID());
		if (seen != null && now - seen[0] >= 0 && now - seen[0] < OUTLAW_CHECK_TICKS) {
			return seen[1] != 0;
		}
		boolean outlaw = false;
		for (Faction f : Factions.all(player.level().getServer())) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN && Reputation.get(player, f.id()) < OUTLAW_BELOW) {
				outlaw = true;
				break;
			}
		}
		OUTLAWS.put(player.getUUID(), new long[] {now, outlaw ? 1 : 0});
		return outlaw;
	}

	/** Every couple of seconds: an outlaw who comes near a camp that spares them is told so, once a day per camp. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		ServerLevel overworld = server.overworld();
		List<Faction> camps = Factions.all(server, FactionType.BARBARIAN);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.level() != overworld || player.isCreative() || player.isSpectator()) {
				continue;
			}
			for (Faction camp : camps) {
				if (camp.fallen() || Raids.horizontal(camp.seat(), player.blockPosition()) > WELCOME_RANGE * WELCOME_RANGE || !spares(player, camp.id())) {
					continue;
				}
				String key = player.getUUID() + "/" + camp.id();
				long now = overworld.getGameTime();
				Long last = WELCOMED.get(key);
				if (last == null || now - last >= WELCOME_GAP || now < last) {
					WELCOMED.put(key, now);
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.barbarian.spared", camp.name()).withStyle(ChatFormatting.GOLD));
				}
			}
		}
	}

	// ---- with MCA (0.7.148) ------------------------------------------------------------------------------------------

	/** A camp's villager raider is as hard to kill as a folk one ({@link #BARBARIAN_HEALTH}). */
	static void harden(LivingEntity mob) {
		AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && health.getModifier(HEALTH) == null && BARBARIAN_HEALTH > health.getBaseValue()) {
			health.addPermanentModifier(new AttributeModifier(HEALTH, BARBARIAN_HEALTH - health.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
			mob.setHealth(mob.getMaxHealth());
		}
	}

	/** With MCA, {@code folk} (a camp's raider at home, at peace) is ready to become one of MCA's villagers. */
	static boolean mayBecomeVillager(ServerLevel level, Folk folk) {
		if (folk.role() != Folk.BARBARIAN || !folk.task().isEmpty() || folk.getTarget() != null || folk.isPassenger()
			|| Downed.isDowned(folk)) {
			return false;
		}
		Faction camp = Factions.get(level.getServer(), folk.factionId());
		return camp != null && !camp.fallen() && plainCamp(level.getServer(), camp.id()) && level.dimension() == Level.OVERWORLD;
	}
}
