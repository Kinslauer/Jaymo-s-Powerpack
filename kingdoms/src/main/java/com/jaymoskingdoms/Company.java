package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Coinage;
import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.jaymoskingdoms.sailing.ShotKind;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Village mods patch 9 (Andy's picks rc2_company, rc2_kinds, rc2_groups, rc2_patrol, rc2_supply, rc2_ships, rc2_war; the
 * idea from Recruits, whose code is not used): a company of your own.
 * <ul>
 * <li><b>Raising it.</b> Any grown villager who likes you ({@link #SIGN_HEARTS} hearts) can be signed on: sneak and use
 * coin on them. They leave their work and become MCA's hired help (so they follow, stay and fight), paid
 * {@link #wage} a day out of your purse at dawn (the first day's at once). A day you can't pay costs them
 * {@link #UNPAID_MORALE} morale, a day paid gives {@link #PAID_MORALE}; at none they walk, back to their old work. A
 * soldier who leaves, or is let go ({@code /company dismiss}), goes back to the job they had.</li>
 * <li><b>Kinds by kit</b> ({@link Kind}): what they carry makes them a shield, a blade, a spear, a bow, a crossbow, or a
 * rider (on a horse: lead one to them on a lead and use the lead on them). In a formation shields stand in front,
 * blades and then spears behind them, bows and then crossbows at the back ({@link Formations}); riders keep out of the
 * ranks and ride at foes, couching a spear as they come ({@code SpearForms.charge}).</li>
 * <li><b>Groups.</b> {@code /company group <name>} puts the soldier you look at (or everyone of yours close by) in a
 * named group. The group key, or the button on the orders screen, steps through all of them, each named group and
 * each kind; orders and formations then go only to that one ({@link #heeds}).</li>
 * <li><b>Patrol routes.</b> The surveyor's rod in its Patrol mode marks up to {@link #ROUTE_MOST} points for the group
 * picked; the Patrol order walks that group round them in a loop ({@link #route}). Hold keeps a post until called.</li>
 * <li><b>Supplies.</b> In its Supply mode the rod marks a chest or barrel as the company's stores. A hurt soldier near
 * one eats from it or drinks a potion from it, each takes a day's ration at dawn (a fed day lifts morale, a hungry one
 * lowers it), and one whose blade or armour is nearly worn out comes to you and says so; the town smiths mend them
 * as they mend the guards ({@link SmithMending}).</li>
 * <li><b>Ship crews.</b> Tell soldiers to Hold while you and they are aboard your ship and they take stations on her.
 * They man her guns against pirates and any ship of a realm at war with yours (loading with powder from you, aboard),
 * and go over the side at a foe's crew when the ships close.</li>
 * <li><b>War.</b> A lord's company counts toward their realm's army ({@link Wars#army}), and marches and fights with
 * them when they join a war party ({@link WarParties#join}).</li>
 * </ul>
 * Needs MCA.
 */
public final class Company {
	// ---- tuning ----
	/** The hearts a villager needs for you to sign them on, and the most soldiers one player keeps. */
	static final int SIGN_HEARTS = Strangers.TRUSTED_HEARTS, MOST = 24;
	/** A day's wage (emeralds' worth), and one more for every this many steps they've learned ({@link Learners}). */
	static final int WAGE = 1, WAGE_STEPS = 4;
	/** Unpaid days charged at once when you come back (the rest they let go). */
	static final int OWED_MOST = 3;
	/** Morale: a new soldier's, the most, a paid day, a fed day, an unpaid day, a hungry day, and below this they grumble. */
	static final int MORALE_START = 70, MORALE_MOST = 100, PAID_MORALE = 10, FED_MORALE = 5, UNPAID_MORALE = 35, HUNGRY_MORALE = 10, LOW_MORALE = 30;
	/** MCA's contract, kept this long ahead while they're paid (ticks). */
	static final int CONTRACT = 24000 * 3;
	/** How often soldiers are seen to (ticks). */
	static final int EVERY = 20;
	/** A soldier finds the stores this near (blocks); a hurt one (below this share of health) eats from them this often (ticks). */
	static final double SUPPLY_REACH = 32.0, HURT_BELOW = 0.5;
	static final long EAT_GAP = 20L * 20;
	/** Stores marked by one player, and points on one route. */
	static final int SUPPLY_MOST = 4, ROUTE_MOST = 12;
	/** Gear with this share of its durability gone is worn out; a soldier says so this often (ticks), and comes this near. */
	static final float WORN = 0.8F;
	static final long WORN_GAP = 20L * 120;
	static final double WORN_COME = 48.0;
	/** Their walking pace. */
	static final float WALK = 0.6F;
	/** Guns: the range a foe is fought at, the least gap between shots, the time to load; how near a foe's hand is to go over to him. */
	static final double GUN_RANGE = 64.0, BOARD_REACH = 6.0;
	static final int GUN_GAP = 30, RELOAD = 80;
	/** A lord's company marching with them gathers from this near (blocks). */
	static final double MARCH_RANGE = 32.0;
	/** Signing on and orders reach this far (blocks). */
	static final double REACH = 6.0, GROUP_NEAR = 8.0;
	/** Lines per kind of thing they say. */
	static final int LINES = 3;

	/** What a soldier is, by kit. */
	public enum Kind {
		SHIELD, BLADE, SPEAR, BOW, CROSSBOW, RIDER;

		String id() {
			return this.name().toLowerCase(Locale.ROOT);
		}
	}

	/** On a soldier: whose, morale, the last day paid, their group, the job they left. Saved. */
	public record Soldier(String owner, int morale, long paid, String group, String job) {
		static final Codec<Soldier> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("owner").forGetter(Soldier::owner),
			Codec.INT.optionalFieldOf("morale", MORALE_START).forGetter(Soldier::morale),
			Codec.LONG.optionalFieldOf("paid", 0L).forGetter(Soldier::paid),
			Codec.STRING.optionalFieldOf("group", "").forGetter(Soldier::group),
			Codec.STRING.optionalFieldOf("job", "").forGetter(Soldier::job)).apply(i, Soldier::new));

		Soldier with(int morale, long paid) {
			return new Soldier(this.owner, Math.max(0, Math.min(MORALE_MOST, morale)), paid, this.group, this.job);
		}

		Soldier inGroup(String group) {
			return new Soldier(this.owner, this.morale, this.paid, group, this.job);
		}

		boolean of(Player player) {
			return this.owner.equals(player.getUUID().toString());
		}
	}

	public static final AttachmentType<Soldier> SOLDIER = AttachmentRegistry.create(JaymosKingdoms.id("company_soldier"),
		builder -> builder.persistent(Soldier.CODEC));

	/** One player's company as the world keeps it: how many, their routes (group → points) and their stores. */
	record Roll(int size, Map<String, List<Long>> routes, List<Long> supply) {
		static final Roll EMPTY = new Roll(0, Map.of(), List.of());
		static final Codec<Roll> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("size", 0).forGetter(Roll::size),
			Codec.unboundedMap(Codec.STRING, Codec.LONG.listOf()).optionalFieldOf("routes", Map.of()).forGetter(Roll::routes),
			Codec.LONG.listOf().optionalFieldOf("supply", List.of()).forGetter(Roll::supply)).apply(i, Roll::new));
	}

	/** Every player's company (owner → roll), on the Overworld. */
	static final AttachmentType<Map<String, Roll>> ROLLS = AttachmentRegistry.create(JaymosKingdoms.id("company_rolls"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Roll.CODEC)));

	/** Client → server: step the group your orders go to. */
	public record Pick() implements CustomPacketPayload {
		public static final Type<Pick> TYPE = new Type<>(JaymosKingdoms.id("company_pick"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = StreamCodec.unit(new Pick());

		@Override
		public Type<Pick> type() {
			return TYPE;
		}
	}

	/** Loaded soldiers; the group each player's orders go to ("" for all, {@code kind:<id>} for a kind); ships' gunners and loading. */
	private static final Map<UUID, Villager> LOADED = new HashMap<>();
	private static final Map<UUID, String> PICKED = new HashMap<>();
	private static final Map<UUID, Map<Long, UUID>> GUNNERS = new HashMap<>();
	private static final Map<Long, Long> LOADING = new HashMap<>();
	private static final Map<UUID, Long> FIRED = new HashMap<>();
	/** When each soldier last ate, last spoke of worn gear; when each player was last told they had no powder. */
	private static final Map<UUID, Long> ATE = new HashMap<>(), WORN_SAID = new HashMap<>(), NO_POWDER = new HashMap<>();

	private Company() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
		if (!McaCompat.LOADED) {
			return;
		}
		ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> pick(context.player()));
		UseEntityCallback.EVENT.register(Company::use);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v && v.hasAttached(SOLDIER)) {
				LOADED.put(v.getUUID(), v);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity.getUUID()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Soldier s = entity.getAttached(SOLDIER);
			if (s != null && entity.level() instanceof ServerLevel level) {
				resize(level.getServer(), s.owner(), -1);
				LOADED.remove(entity.getUUID());
				ServerPlayer owner = owner(level.getServer(), s);
				if (owner != null) {
					owner.sendSystemMessage(Component.translatable("message.jaymoskingdoms.company.fallen", entity.getDisplayName()).withStyle(ChatFormatting.GRAY));
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LOADED.clear();
			PICKED.clear();
			GUNNERS.clear();
			LOADING.clear();
			FIRED.clear();
			ATE.clear();
			WORN_SAID.clear();
			NO_POWDER.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(Company::tick);
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("company")
			.executes(c -> show(c.getSource()))
			.then(Commands.literal("group")
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> group(c, StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("ungroup").executes(c -> group(c, "")))
			.then(Commands.literal("select")
				.then(Commands.literal("all").executes(c -> select(c.getSource(), "")))
				.then(Commands.argument("name", StringArgumentType.word()).executes(c -> select(c.getSource(), StringArgumentType.getString(c, "name")))))
			.then(Commands.literal("route").then(Commands.literal("clear").executes(c -> clearRoute(c.getSource().getPlayerOrException()))))
			.then(Commands.literal("dismiss").executes(c -> dismiss(c.getSource())))));
	}

	// ---- who they are -------------------------------------------------------------------------------------------------

	/** {@code e} is in someone's company. */
	public static boolean soldier(@Nullable Entity e) {
		return e != null && e.hasAttached(SOLDIER);
	}

	/** {@code e} is in {@code player}'s company. */
	public static boolean ownedBy(Entity e, Player player) {
		Soldier s = e.getAttached(SOLDIER);
		return s != null && s.of(player);
	}

	/** What {@code m} fights as, by what they carry (anyone: {@link Formations} ranks everyone this way). */
	public static Kind kind(Mob m) {
		if (m.getVehicle() instanceof AbstractHorse) {
			return Kind.RIDER;
		}
		ItemStack main = m.getMainHandItem();
		if (main.getItem() instanceof CrossbowItem) {
			return Kind.CROSSBOW;
		}
		if (main.getItem() instanceof ProjectileWeaponItem) {
			return Kind.BOW;
		}
		if (main.is(ItemTags.SPEARS) || main.is(Items.TRIDENT)) {
			return Kind.SPEAR;
		}
		return m.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) ? Kind.SHIELD : Kind.BLADE;
	}

	/** A soldier's day's wage, in emeralds' worth. */
	static int wage(Villager v) {
		return WAGE + Learners.steps(v.getAttachedOrElse(Learners.WINS, 0)) / WAGE_STEPS;
	}

	private static @Nullable ServerPlayer owner(MinecraftServer server, Soldier s) {
		try {
			return server.getPlayerList().getPlayer(UUID.fromString(s.owner()));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	// ---- the roll -----------------------------------------------------------------------------------------------------

	private static Map<String, Roll> rolls(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(ROLLS, Map.of());
	}

	static Roll roll(MinecraftServer server, String owner) {
		return rolls(server).getOrDefault(owner, Roll.EMPTY);
	}

	private static void putRoll(MinecraftServer server, String owner, Roll roll) {
		Map<String, Roll> all = new HashMap<>(rolls(server));
		all.put(owner, roll);
		Factions.level(server).setAttached(ROLLS, Map.copyOf(all));
	}

	private static void resize(MinecraftServer server, String owner, int by) {
		Roll r = roll(server, owner);
		putRoll(server, owner, new Roll(Math.max(0, r.size() + by), r.routes(), r.supply()));
	}

	/** (rc2_war) The soldiers in the companies of {@code f}'s lords (and its ruler, if a player rules it). */
	static int strength(MinecraftServer server, Faction f) {
		int n = 0;
		for (Map.Entry<java.util.UUID, Faction.Member> e : f.members().entrySet()) {
			if (Wars.isLord(f, e.getKey()) || f.leader().isPresent() && f.leader().get().equals(e.getKey())) {
				n += roll(server, e.getKey().toString()).size();
			}
		}
		return n;
	}

	// ---- signing on, a horse, letting go ----------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || sp.isSpectator()
			|| !(entity instanceof Villager v) || !v.isAlive()) {
			return InteractionResult.PASS;
		}
		ItemStack held = sp.getMainHandItem();
		if (held.is(Items.LEAD) && ownedBy(v, sp) && !v.isPassenger()) {
			return mount(server, v, sp) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}
		if (!sp.isShiftKeyDown() || !Coinage.isMoney(held) || soldier(v)) {
			return InteractionResult.PASS;
		}
		if (v.isBaby() || McaBridge.mercenary(v) || Downed.isGuard(v) || Rulers.is(v) || Crewmen.tagged(v) || Drifters.is(v)
			|| v.hasAttached(RivalParties.PARTY) || v.hasAttached(Loans.TAKEN)) {
			return InteractionResult.PASS;
		}
		if (!Languages.understands(sp, v)) {
			Languages.shrug(sp, v);
			return InteractionResult.SUCCESS;
		}
		if (McaBridge.hearts(v, sp) < SIGN_HEARTS) {
			say(server, v, sp, "unknown");
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.hearts", SIGN_HEARTS).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		MinecraftServer ms = server.getServer();
		String me = sp.getUUID().toString();
		if (roll(ms, me).size() >= MOST) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.full", MOST).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		long pay = Money.worth(wage(v));
		if (!Money.canPay(sp, pay)) {
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.short", Money.text(sp, pay)).withStyle(ChatFormatting.GRAY));
			return InteractionResult.SUCCESS;
		}
		String job = McaBridge.signOn(v, CONTRACT);
		if (job == null) {
			return InteractionResult.PASS;
		}
		Money.charge(sp, pay);
		v.setAttached(SOLDIER, new Soldier(me, MORALE_START, Production.day(ms), "", job));
		LOADED.put(v.getUUID(), v);
		resize(ms, me, 1);
		say(server, v, sp, "signed");
		sp.sendSystemMessage(Component.translatable("message.jaymoskingdoms.company.signed", v.getDisplayName(), Money.text(sp, pay)).withStyle(ChatFormatting.GOLD), true);
		server.playSound(null, v.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	/** {@code player} hands their soldier a horse on a lead: the soldier mounts it and rides as a rider. */
	private static boolean mount(ServerLevel level, Villager v, ServerPlayer player) {
		for (AbstractHorse horse : level.getEntitiesOfClass(AbstractHorse.class, player.getBoundingBox().inflate(12.0),
			h -> h.isAlive() && h.isTamed() && h.getLeashHolder() == player && h.getPassengers().isEmpty())) {
			horse.dropLeash();
			if (v.startRiding(horse, true, false)) {
				say(level, v, player, "mounted");
				return true;
			}
		}
		return false;
	}

	/** {@code v} leaves the company: their old work back, their order dropped. {@code why}: what they say on going. */
	static void leave(ServerLevel level, Villager v, Soldier s, String why) {
		ServerPlayer owner = owner(level.getServer(), s);
		if (owner != null) {
			say(level, v, owner, why);
			owner.sendSystemMessage(Component.translatable("message.jaymoskingdoms.company." + why, v.getDisplayName()).withStyle(ChatFormatting.GRAY));
		}
		v.removeAttached(SOLDIER);
		v.removeAttached(Followers.ORDER);
		v.removeAttached(Ship.SEAT);
		if (v.getVehicle() instanceof Ship) {
			v.stopRiding();
		}
		McaBridge.release(v, s.job());
		LOADED.remove(v.getUUID());
		resize(level.getServer(), s.owner(), -1);
	}

	private static void say(ServerLevel level, Entity v, ServerPlayer to, String what) {
		Speech.sayTo(level, v, to, Component.translatable("speech.jaymoskingdoms.company." + what + "." + level.getRandom().nextInt(LINES)), Speech.WHITE);
	}

	// ---- groups -------------------------------------------------------------------------------------------------------

	/**
	 * (rc2_groups) {@code v} takes an order {@code player} gives to everyone near: always, unless the player has picked a
	 * group, when only that group's soldiers do.
	 */
	public static boolean heeds(Entity v, ServerPlayer player) {
		String picked = PICKED.get(player.getUUID());
		if (picked == null || picked.isEmpty()) {
			return true;
		}
		Soldier s = v.getAttached(SOLDIER);
		if (s == null || !s.of(player) || !(v instanceof Mob m)) {
			return false;
		}
		return picked.startsWith("kind:") ? picked.equals("kind:" + kind(m).id()) : picked.equals(s.group());
	}

	/** The groups {@code player}'s loaded soldiers make: "" (all), each named group, each kind. */
	private static List<String> groups(ServerPlayer player) {
		TreeSet<String> named = new TreeSet<>(), kinds = new TreeSet<>();
		for (Villager v : LOADED.values()) {
			Soldier s = v.getAttached(SOLDIER);
			if (s != null && s.of(player) && v.isAlive()) {
				if (!s.group().isEmpty()) {
					named.add(s.group());
				}
				kinds.add("kind:" + kind(v).id());
			}
		}
		List<String> out = new ArrayList<>();
		out.add("");
		out.addAll(named);
		out.addAll(kinds);
		return out;
	}

	/** The key or the screen's button: the next group. */
	private static void pick(ServerPlayer player) {
		List<String> groups = groups(player);
		int at = groups.indexOf(PICKED.getOrDefault(player.getUUID(), ""));
		String next = groups.get((at + 1) % groups.size());
		PICKED.put(player.getUUID(), next);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.picked", groupName(next)).withStyle(ChatFormatting.GOLD));
	}

	static Component groupName(String group) {
		if (group.isEmpty()) {
			return Component.translatable("gui.jaymoskingdoms.company.all");
		}
		return group.startsWith("kind:") ? Component.translatable("gui.jaymoskingdoms.company.kind." + group.substring(5)) : Component.literal(group);
	}

	private static int group(CommandContext<CommandSourceStack> c, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		List<Villager> who = new ArrayList<>();
		Villager aimed = aimed(player);
		if (aimed != null) {
			who.add(aimed);
		} else {
			who.addAll(player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(GROUP_NEAR), v -> ownedBy(v, player)));
		}
		for (Villager v : who) {
			v.setAttached(SOLDIER, v.getAttached(SOLDIER).inGroup(name));
		}
		player.sendSystemMessage(Component.translatable(name.isEmpty() ? "message.jaymoskingdoms.company.ungrouped" : "message.jaymoskingdoms.company.grouped",
			who.size(), name).withStyle(ChatFormatting.GOLD));
		return who.size();
	}

	private static int select(CommandSourceStack source, String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		String g = name.isEmpty() ? "" : groups(player).contains("kind:" + name) ? "kind:" + name : name;
		PICKED.put(player.getUUID(), g);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.picked", groupName(g)).withStyle(ChatFormatting.GOLD));
		return 1;
	}

	/** The soldier of {@code player}'s under their crosshair, within {@link #REACH}. */
	private static @Nullable Villager aimed(ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getViewVector(1.0F).scale(REACH));
		EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, eye, end,
			player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0), e -> e instanceof Villager && ownedBy(e, player), REACH * REACH);
		return hit != null && hit.getEntity() instanceof Villager v ? v : null;
	}

	private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Roll r = roll(source.getServer(), player.getUUID().toString());
		int near = 0, morale = 0;
		long wages = 0;
		for (Villager v : LOADED.values()) {
			Soldier s = v.getAttached(SOLDIER);
			if (s != null && s.of(player)) {
				near++;
				morale += s.morale();
				wages += Money.worth(wage(v));
			}
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.company.show", r.size(), near, near == 0 ? 0 : morale / near,
			Money.text(player, wages), groupName(PICKED.getOrDefault(player.getUUID(), "")), r.supply().size()).withStyle(ChatFormatting.GOLD));
		return r.size();
	}

	private static int dismiss(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Villager v = aimed(player);
		if (v == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.none").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		leave(player.level(), v, v.getAttached(SOLDIER), "dismissed");
		return 1;
	}

	// ---- routes and stores (the surveyor's rod) ---------------------------------------------------------------------

	/** (rc2_patrol) The rod, in Patrol mode, used on {@code pos}: the next point of the picked group's route. */
	static boolean mark(ServerPlayer player, BlockPos pos) {
		MinecraftServer server = player.level().getServer();
		String me = player.getUUID().toString(), group = PICKED.getOrDefault(player.getUUID(), "");
		Roll r = roll(server, me);
		List<Long> route = new ArrayList<>(r.routes().getOrDefault(group, List.of()));
		if (route.size() >= ROUTE_MOST) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.route_full", ROUTE_MOST).withStyle(ChatFormatting.GRAY));
			return false;
		}
		route.add(pos.above().asLong());
		Map<String, List<Long>> routes = new HashMap<>(r.routes());
		routes.put(group, List.copyOf(route));
		putRoll(server, me, new Roll(r.size(), Map.copyOf(routes), r.supply()));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.route_point", route.size(), groupName(group)).withStyle(ChatFormatting.YELLOW));
		player.level().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.5F, 1.2F);
		return true;
	}

	/** The picked group's route forgotten. */
	static int clearRoute(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String me = player.getUUID().toString(), group = PICKED.getOrDefault(player.getUUID(), "");
		Roll r = roll(server, me);
		Map<String, List<Long>> routes = new HashMap<>(r.routes());
		routes.remove(group);
		putRoll(server, me, new Roll(r.size(), Map.copyOf(routes), r.supply()));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.route_cleared", groupName(group)).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/** (rc2_supply) The rod, in Supply mode, used on a chest or barrel: it is (or no longer is) the company's stores. */
	static boolean toggleSupply(ServerPlayer player, BlockPos pos) {
		if (!(player.level().getBlockEntity(pos) instanceof Container)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.supply_not").withStyle(ChatFormatting.GRAY));
			return false;
		}
		MinecraftServer server = player.level().getServer();
		String me = player.getUUID().toString();
		Roll r = roll(server, me);
		List<Long> supply = new ArrayList<>(r.supply());
		boolean off = supply.remove(Long.valueOf(pos.asLong()));
		if (!off) {
			if (supply.size() >= SUPPLY_MOST) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.supply_full", SUPPLY_MOST).withStyle(ChatFormatting.GRAY));
				return false;
			}
			supply.add(pos.asLong());
		}
		putRoll(server, me, new Roll(r.size(), r.routes(), List.copyOf(supply)));
		player.sendOverlayMessage(Component.translatable(off ? "message.jaymoskingdoms.company.supply_off" : "message.jaymoskingdoms.company.supply_on")
			.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/**
	 * (rc2_patrol, {@link Followers}) The route {@code v} walks on a Patrol order: their named group's, else their kind's,
	 * else the one marked for all; null if none has two points.
	 */
	static @Nullable List<BlockPos> route(Villager v) {
		Soldier s = v.getAttached(SOLDIER);
		if (s == null || !(v.level() instanceof ServerLevel level)) {
			return null;
		}
		Map<String, List<Long>> routes = roll(level.getServer(), s.owner()).routes();
		List<Long> r = !s.group().isEmpty() && routes.containsKey(s.group()) ? routes.get(s.group())
			: routes.getOrDefault("kind:" + kind(v).id(), routes.get(""));
		if (r == null || r.size() < 2) {
			return null;
		}
		return r.stream().map(BlockPos::of).toList();
	}

	/** Takes one thing {@code want} matches from the stores of {@code s}'s owner within {@link #SUPPLY_REACH} of {@code v}. */
	private static ItemStack fromStores(ServerLevel level, Villager v, Soldier s, java.util.function.Predicate<ItemStack> want) {
		for (long at : roll(level.getServer(), s.owner()).supply()) {
			BlockPos pos = BlockPos.of(at);
			if (!level.isLoaded(pos) || v.distanceToSqr(Vec3.atCenterOf(pos)) > SUPPLY_REACH * SUPPLY_REACH
				|| !(level.getBlockEntity(pos) instanceof Container box)) {
				continue;
			}
			for (int i = 0; i < box.getContainerSize(); i++) {
				ItemStack stack = box.getItem(i);
				if (!stack.isEmpty() && want.test(stack)) {
					ItemStack one = stack.split(1);
					box.setChanged();
					return one;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private static boolean food(ItemStack stack) {
		return stack.has(DataComponents.FOOD);
	}

	private static boolean potion(ItemStack stack) {
		PotionContents p = stack.get(DataComponents.POTION_CONTENTS);
		return stack.is(Items.POTION) && p != null && p.hasEffects();
	}

	// ---- the tick -----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		showRoutes(server);
		if (LOADED.isEmpty()) {
			return;
		}
		long day = Production.day(server);
		Map<Ship, List<Villager>> aboard = new HashMap<>();
		for (Iterator<Villager> it = new ArrayList<>(LOADED.values()).iterator(); it.hasNext();) {
			Villager v = it.next();
			Soldier s = v.getAttached(SOLDIER);
			if (s == null || !v.isAlive() || v.isRemoved() || !(v.level() instanceof ServerLevel level)) {
				LOADED.remove(v.getUUID());
				continue;
			}
			if (!McaBridge.mercenary(v)) {
				// (MCA ended the contract its own way: they're no longer ours)
				v.removeAttached(SOLDIER);
				LOADED.remove(v.getUUID());
				resize(server, s.owner(), -1);
				continue;
			}
			ServerPlayer owner = owner(server, s);
			if (s.paid() < day && owner != null) {
				s = payday(level, v, s, owner, day);
				if (s.morale() <= 0) {
					leave(level, v, s, "deserted");
					continue;
				}
			}
			eat(level, v, s);
			worn(level, v, owner);
			ride(level, v);
			if (v.getVehicle() instanceof Ship ship && ship.crew == null) {
				aboard.computeIfAbsent(ship, k -> new ArrayList<>()).add(v);
			}
		}
		aboard.forEach((ship, hands) -> crew((ServerLevel)ship.level(), ship, hands));
		GUNNERS.keySet().removeIf(id -> aboard.keySet().stream().noneMatch(sh -> sh.getUUID().equals(id)));
	}

	/** Dawn's wage and ration for the days since {@code s} was last paid. Returns the soldier as now. */
	private static Soldier payday(ServerLevel level, Villager v, Soldier s, ServerPlayer owner, long day) {
		int morale = s.morale();
		long pay = Money.worth(wage(v));
		int owed = (int)Math.min(OWED_MOST, day - s.paid());
		boolean unpaid = false;
		for (int i = 0; i < owed; i++) {
			if (Money.charge(owner, pay)) {
				morale += PAID_MORALE;
				McaBridge.keepOn(v, CONTRACT);
			} else {
				morale -= UNPAID_MORALE;
				unpaid = true;
			}
		}
		// a day's ration from the stores
		ItemStack ration = fromStores(level, v, s, Company::food);
		morale += ration.isEmpty() ? -HUNGRY_MORALE : FED_MORALE;
		Soldier now = s.with(morale, day);
		v.setAttached(SOLDIER, now);
		if (unpaid) {
			owner.sendSystemMessage(Component.translatable("message.jaymoskingdoms.company.unpaid", v.getDisplayName(), Money.text(owner, pay)).withStyle(ChatFormatting.RED));
		}
		if (now.morale() > 0 && now.morale() < LOW_MORALE && owner.distanceToSqr(v) < 32.0 * 32.0) {
			say(level, v, owner, ration.isEmpty() ? "hungry" : "grumble");
		}
		return now;
	}

	/** A hurt soldier near the stores eats from them, or drinks a potion from them. */
	private static void eat(ServerLevel level, Villager v, Soldier s) {
		long now = level.getGameTime();
		if (v.getHealth() >= v.getMaxHealth() * HURT_BELOW || Comrades.target(v) != null || now - ATE.getOrDefault(v.getUUID(), Long.MIN_VALUE / 2) < EAT_GAP) {
			return;
		}
		ItemStack got = fromStores(level, v, s, st -> potion(st) || food(st));
		if (got.isEmpty()) {
			return;
		}
		ATE.put(v.getUUID(), now);
		PotionContents p = got.get(DataComponents.POTION_CONTENTS);
		if (potion(got) && p != null) {
			p.forEachEffect(v::addEffect, 1.0F);
			level.playSound(null, v.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
		} else {
			FoodProperties f = got.get(DataComponents.FOOD);
			v.heal(f == null ? 2.0F : Math.max(2.0F, f.nutrition()));
			level.playSound(null, v.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
		}
		v.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
	}

	/** A soldier whose blade or armour is nearly worn out comes to their owner and says so (the smiths mend it: {@link SmithMending}). */
	private static void worn(ServerLevel level, Villager v, @Nullable ServerPlayer owner) {
		long now = level.getGameTime();
		if (owner == null || owner.level() != level || Comrades.target(v) != null || now - WORN_SAID.getOrDefault(v.getUUID(), Long.MIN_VALUE / 2) < WORN_GAP) {
			return;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET}) {
			ItemStack stack = v.getItemBySlot(slot);
			if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() * WORN) {
				WORN_SAID.put(v.getUUID(), now);
				if (owner.distanceToSqr(v) < WORN_COME * WORN_COME) {
					v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(owner, WALK, 2));
					Speech.sayTo(level, v, owner, Component.translatable("speech.jaymoskingdoms.company.worn." + level.getRandom().nextInt(LINES),
						stack.getHoverName()), Speech.WHITE);
				}
				return;
			}
		}
	}

	/** A rider couches a spear as they come at their foe (as a realm's mounted guards do, {@code Cavalry}). */
	private static void ride(ServerLevel level, Villager v) {
		if (!(v.getVehicle() instanceof AbstractHorse horse) || level.getGameTime() % com.jaymoskingdoms.forms.SpearForms.NPC_CHECK_TICKS >= EVERY) {
			return;
		}
		ItemStack held = v.getMainHandItem();
		LivingEntity foe = Comrades.target(v);
		if (foe != null && (held.is(ItemTags.SPEARS) || held.is(Items.TRIDENT))) {
			com.jaymoskingdoms.forms.SpearForms.charge(level, v, horse, foe);
		}
	}

	/** While {@code player}'s rod is in Patrol mode, the picked group's route shows in sparks. */
	private static void showRoutes(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (Surveyor.ROD == null || !p.isHolding(Surveyor.ROD) || Surveyor.mode(p) != Surveyor.Mode.PATROL) {
				continue;
			}
			List<Long> route = roll(server, p.getUUID().toString()).routes().getOrDefault(PICKED.getOrDefault(p.getUUID(), ""), List.of());
			for (long at : route) {
				BlockPos pos = BlockPos.of(at);
				if (pos.distSqr(p.blockPosition()) < 96 * 96) {
					p.level().sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.0);
				}
			}
		}
	}

	// ---- (rc2_ships) your ship's crew -------------------------------------------------------------------------------

	/**
	 * (Followers' Hold) {@code v}, standing aboard {@code player}'s ship with them, takes a station on her where they
	 * stand. True if they did (then the order is the ship's, not a spot ashore).
	 */
	static boolean station(Villager v, ServerPlayer player) {
		if (!ownedBy(v, player) || v.isPassenger()) {
			return false;
		}
		Ship ship = Helmsmen.shipOf(player);
		if (ship == null || Ships.aboard(v) != ship) {
			return false;
		}
		if (ship.takeAboard(v, ship.toLocal(v.position()))) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.company.station", v.getDisplayName()).withStyle(ChatFormatting.GOLD));
			return true;
		}
		return false;
	}

	/** A ship's foe for {@code owner}'s crew: a pirate, or a ship of a realm at war with theirs. */
	private static boolean foe(MinecraftServer server, @Nullable ServerPlayer owner, Ship other) {
		Crew c = other.crew;
		if (c == null || other.foundering()) {
			return false;
		}
		if (c.side == Crew.Side.PIRATE) {
			return true;
		}
		Faction f = Factions.get(server, c.faction);
		return owner != null && f != null && Wars.enemy(owner, f);
	}

	/** {@code hands} (soldiers riding {@code ship}, a player's) man her guns against a foe in range, and board one that closes. */
	private static void crew(ServerLevel level, Ship ship, List<Villager> hands) {
		MinecraftServer server = level.getServer();
		Soldier first = hands.getFirst().getAttached(SOLDIER);
		ServerPlayer owner = first == null ? null : owner(server, first);
		Ship foe = null;
		double best = GUN_RANGE * GUN_RANGE;
		for (Ship other : level.getEntitiesOfClass(Ship.class, ship.getBoundingBox().inflate(GUN_RANGE), s -> s != ship && foe(server, owner, s))) {
			double d = other.distanceToSqr(ship);
			if (d < best) {
				best = d;
				foe = other;
			}
		}
		Map<Long, UUID> gunners = GUNNERS.computeIfAbsent(ship.getUUID(), k -> new HashMap<>());
		Map<UUID, Villager> byId = new HashMap<>();
		for (Villager h : hands) {
			byId.put(h.getUUID(), h);
		}
		gunners.values().removeIf(id -> !byId.containsKey(id));
		// every gun to a hand: the one with fewest guns
		for (long gun : ship.guns()) {
			if (gunners.containsKey(gun)) {
				continue;
			}
			Villager pick = null;
			int fewest = Integer.MAX_VALUE;
			for (Villager h : hands) {
				int n = (int)gunners.values().stream().filter(h.getUUID()::equals).count();
				if (n < fewest) {
					fewest = n;
					pick = h;
				}
			}
			if (pick != null) {
				gunners.put(gun, pick.getUUID());
			}
		}
		long now = level.getGameTime();
		boolean ownerAboard = owner != null && (owner.getVehicle() == ship || Ships.aboard(owner) == ship);
		for (Map.Entry<Long, UUID> e : gunners.entrySet()) {
			long gun = e.getKey();
			Villager gunner = byId.get(e.getValue());
			if (gunner == null || !ship.hasGun(gun)) {
				continue;
			}
			long key = gun ^ ship.getUUID().getLeastSignificantBits();
			if (!ship.gunLoaded(gun)) {
				Long ready = LOADING.get(key);
				if (ready == null) {
					LOADING.put(key, now + RELOAD);
				} else if (now >= ready) {
					LOADING.remove(key);
					// powder from their captain's own stores, aboard
					if (ownerAboard && (owner.hasInfiniteMaterials() || takePowder(owner))) {
						ship.loadGun(gun, ShotKind.ROUND);
					} else if (owner != null && foe != null && now - NO_POWDER.getOrDefault(owner.getUUID(), Long.MIN_VALUE / 2) > 20L * 60) {
						NO_POWDER.put(owner.getUUID(), now);
						Speech.sayTo(level, gunner, owner, Component.translatable("speech.jaymoskingdoms.company.powder." + level.getRandom().nextInt(LINES)), Speech.WHITE);
					}
				}
				continue;
			}
			if (foe == null || now - FIRED.getOrDefault(ship.getUUID(), Long.MIN_VALUE / 2) < GUN_GAP) {
				continue;
			}
			Vec3 dir = Corsairs.lay(level, ship, gun, foe);
			if (dir != null && ship.fireGunAlong(level, gun, dir, gunner)) {
				FIRED.put(ship.getUUID(), now);
				gunner.getLookControl().setLookAt(foe.getX(), foe.getY() + 2.0, foe.getZ());
			}
		}
		if (foe == null) {
			return;
		}
		// boarding: a hand with no gun goes over at the nearest of her crew within reach
		for (Villager h : hands) {
			if (gunners.containsValue(h.getUUID())) {
				continue;
			}
			LivingEntity near = null;
			double nd = BOARD_REACH * BOARD_REACH;
			for (LivingEntity c : foe.crewAboard()) {
				double d = c.distanceToSqr(h);
				if (d < nd) {
					nd = d;
					near = c;
				}
			}
			if (near != null) {
				h.stopRiding();
				h.removeAttached(Ship.SEAT);
				h.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, near);
			}
		}
	}

	private static boolean takePowder(ServerPlayer owner) {
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			ItemStack stack = owner.getInventory().getItem(i);
			if (stack.is(Items.GUNPOWDER)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	// ---- (rc2_war) marching with a lord -----------------------------------------------------------------------------

	/** {@code player} joined a war party: if they're a lord of their realm (or rule it), their company near marches and fights with them. */
	static void march(ServerLevel level, ServerPlayer player) {
		if (!McaCompat.LOADED) {
			return;
		}
		Faction mine = Factions.of(level.getServer(), player.getUUID());
		if (mine == null || !(Wars.isLord(mine, player.getUUID()) || mine.leader().isPresent() && mine.leader().get().equals(player.getUUID()))) {
			return;
		}
		int n = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(MARCH_RANGE), v -> ownedBy(v, player) && !v.isPassenger())) {
			Followers.take(v, player, Followers.FIGHT);
			n++;
		}
		if (n > 0) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.company.march", n).withStyle(ChatFormatting.GOLD));
		}
	}
}
