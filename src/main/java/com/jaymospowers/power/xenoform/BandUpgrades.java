package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.123, Xeno-Band patch 7) What the band has been made into, beyond the aliens it holds ({@link #UPGRADES}): whether it
 * has been reforged ({@link BandForge}), the modules it has learned from plans and the ones fitted in its {@link #SLOTS}
 * slots, and the skills bought with band points. Band points come from mastery ({@link #POINT_EVERY} mastery points each,
 * one more once ascended). Everything is changed from the band screen ({@link Action}; B as yourself opens it).
 */
public final class BandUpgrades {
	// ---- tuning ----
	/** Module slots on a reforged band. */
	public static final int SLOTS = 3;
	/** One band point per this many mastery points, at most {@link #MAX_POINTS} from mastery (one more once ascended). */
	public static final int POINT_EVERY = 30, MAX_POINTS = 8;
	/** Cell: the charge's size and its refill speed, times this. */
	public static final float CELL_FACTOR = 2.0F;
	/** Plating, while no armour is worn: armour points, speed (share), and no freezing. */
	public static final double PLATING_ARMOR = 8.0, PLATING_SPEED = 0.1;
	/** Skills: each rank of Bigger Charge adds this share, each rank of Faster Recharge cuts the refill time to this share. */
	public static final float CHARGE_PER_RANK = 0.25F, RECHARGE_PER_RANK = 0.75F;
	/** Scan Reach: blocks added per rank to {@link XenoformPower#SCAN_REACH}. */
	public static final double REACH_PER_RANK = 8.0;

	/** A module: what it does is in {@link BandUpgrades}' helpers; Steady and Wild can't be fitted together. */
	public enum Module {
		CELL, BREATHER, PLATING, STEADY, WILD;

		public final String id = name().toLowerCase(java.util.Locale.ROOT);

		public static @Nullable Module of(String id) {
			for (Module m : values()) {
				if (m.id.equals(id)) {
					return m;
				}
			}
			return null;
		}

		public Component title() {
			return Component.translatable("band.jaymospowers.module." + id);
		}
	}

	/** A skill bought with band points: its cost per rank and how many ranks there are. */
	public enum Skill {
		QUICK(1, 1), SAVE(2, 1), REACH(1, 2), CHARGE(1, 2), RECHARGE(1, 2);

		public final String id = name().toLowerCase(java.util.Locale.ROOT);
		public final int cost, ranks;

		Skill(int cost, int ranks) {
			this.cost = cost;
			this.ranks = ranks;
		}

		public static @Nullable Skill of(String id) {
			for (Skill s : values()) {
				if (s.id.equals(id)) {
					return s;
				}
			}
			return null;
		}

		public Component title() {
			return Component.translatable("band.jaymospowers.skill." + id);
		}
	}

	/** The band's upgrades: reforged or not, modules learned, modules fitted, skills bought (a skill once per rank). */
	public record Upgrades(boolean reforged, List<String> known, List<String> fitted, List<String> skills) {
		public static final Upgrades NONE = new Upgrades(false, List.of(), List.of(), List.of());
		public static final Codec<Upgrades> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.BOOL.optionalFieldOf("reforged", false).forGetter(Upgrades::reforged),
				Codec.STRING.listOf().optionalFieldOf("known", List.of()).forGetter(Upgrades::known),
				Codec.STRING.listOf().optionalFieldOf("fitted", List.of()).forGetter(Upgrades::fitted),
				Codec.STRING.listOf().optionalFieldOf("skills", List.of()).forGetter(Upgrades::skills))
			.apply(i, Upgrades::new));
		public static final StreamCodec<ByteBuf, Upgrades> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, Upgrades::reforged,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Upgrades::known,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Upgrades::fitted,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Upgrades::skills,
			Upgrades::new);

		public boolean knows(Module m) {
			return known.contains(m.id);
		}

		public boolean fits(Module m) {
			return reforged && fitted.contains(m.id);
		}

		public int rank(Skill s) {
			int n = 0;
			for (String id : skills) {
				if (id.equals(s.id)) {
					n++;
				}
			}
			return n;
		}

		public int spent() {
			int n = 0;
			for (String id : skills) {
				Skill s = Skill.of(id);
				n += s == null ? 0 : s.cost;
			}
			return n;
		}

		Upgrades reforge() {
			return new Upgrades(true, known, fitted, skills);
		}

		Upgrades learn(Module m) {
			return knows(m) ? this : new Upgrades(reforged, plus(known, m.id), fitted, skills);
		}

		Upgrades fitted(List<String> list) {
			return new Upgrades(reforged, known, List.copyOf(list), skills);
		}

		Upgrades buy(Skill s) {
			return new Upgrades(reforged, known, fitted, plus(skills, s.id));
		}

		private static List<String> plus(List<String> list, String id) {
			List<String> out = new ArrayList<>(list);
			out.add(id);
			return List.copyOf(out);
		}
	}

	public static final AttachmentType<Upgrades> UPGRADES = AttachmentRegistry.create(JaymosPowers.id("xenoform_upgrades"),
		builder -> builder.persistent(Upgrades.CODEC).copyOnDeath().syncWith(Upgrades.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final Identifier PLATING_ARMOR_ID = JaymosPowers.id("xenoform_plating_armor");
	private static final Identifier PLATING_SPEED_ID = JaymosPowers.id("xenoform_plating_speed");

	/** The five module plans, by module. */
	public static final java.util.Map<Module, Item> PLANS = new java.util.EnumMap<>(Module.class);

	// ---------------------------------------------------------------- packets

	/** Server → client: open the band screen, with the band points to spend. */
	public record Open(int points) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("xenoform_band_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = ByteBufCodecs.VAR_INT.map(Open::new, Open::points);

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client → server: a button on the band screen ({@code fit}, {@code unfit}, {@code buy}, {@code forget}, {@code wipe}) and what it's about. */
	public record Action(String what, String arg) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(JaymosPowers.id("xenoform_band_action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Action::what,
			ByteBufCodecs.STRING_UTF8, Action::arg,
			Action::new);

		@Override
		public Type<Action> type() {
			return TYPE;
		}
	}

	private BandUpgrades() {
	}

	static void init() {
		for (Module m : Module.values()) {
			PLANS.put(m, plan(m));
		}
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, ctx) -> act(ctx.player(), payload.what(), payload.arg()));
		BandForge.init();
	}

	private static Item plan(Module m) {
		String id = "band_plan_" + m.id;
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, new PlanItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE), m));
	}

	/** A module plan: used by a band wearer, the band learns the module and the plan is spent. */
	public static final class PlanItem extends XenoFinds.TipItem {
		public final Module module;

		PlanItem(Properties properties, Module module) {
			super(properties, "item.jaymospowers.band_plan.tooltip");
			this.module = module;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			if (!(player instanceof ServerPlayer server)) {
				return InteractionResult.SUCCESS;
			}
			if (!XenoformPower.has(server)) {
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.band.plan_no_band").withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			Upgrades up = get(server);
			if (up.knows(module)) {
				server.sendOverlayMessage(Component.translatable("message.jaymospowers.band.plan_known", module.title()).withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			server.setAttached(UPGRADES, up.learn(module));
			player.getItemInHand(hand).shrink(1);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.6F);
			server.sendSystemMessage(Component.translatable(up.reforged() ? "message.jaymospowers.band.plan_learned" : "message.jaymospowers.band.plan_learned_unforged",
				module.title()).withStyle(ChatFormatting.GREEN));
			return InteractionResult.SUCCESS;
		}
	}

	// ---------------------------------------------------------------- reading

	public static Upgrades get(Player player) {
		Upgrades up = player.getAttached(UPGRADES);
		return up == null ? Upgrades.NONE : up;
	}

	public static boolean fits(Player player, Module m) {
		return get(player).fits(m);
	}

	public static int rank(Player player, Skill s) {
		return get(player).rank(s);
	}

	/** Band points earned: from mastery, and one for ascending. */
	public static int earned(Player player) {
		return Math.min(MAX_POINTS, Mastery.points(player) / POINT_EVERY) + (com.jaymospowers.core.Fate.isAscended(player) ? 1 : 0);
	}

	/** Band points left to spend. */
	public static int points(Player player) {
		return Math.max(0, earned(player) - get(player).spent());
	}

	/** The charge's size, times this (Cell, Bigger Charge). */
	public static float capacityFactor(Player player) {
		return (fits(player, Module.CELL) ? CELL_FACTOR : 1.0F) * (1.0F + CHARGE_PER_RANK * rank(player, Skill.CHARGE));
	}

	/** The refill's length, times this (Faster Recharge; Cell's speed comes from its bigger charge refilling in the same time). */
	public static float refillFactor(Player player) {
		return (float)Math.pow(RECHARGE_PER_RANK, rank(player, Skill.RECHARGE));
	}

	public static double scanReach(Player player) {
		return XenoformPower.SCAN_REACH + REACH_PER_RANK * rank(player, Skill.REACH);
	}

	/** Breather: water and the far worlds' thin air are both breathable. */
	public static boolean breathes(Player player) {
		return XenoformPower.has(player) && fits(player, Module.BREATHER);
	}

	/** Steady: no corruption glitches. */
	public static boolean steady(Player player) {
		return fits(player, Module.STEADY);
	}

	/** Wild: the band glitches as if fully corrupted. */
	public static boolean wild(Player player) {
		return fits(player, Module.WILD);
	}

	// ---------------------------------------------------------------- every tick (XenoformPower.tick)

	static void tick(ServerPlayer player) {
		if (breathes(player) && player.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) {
			player.setAirSupply(player.getMaxAirSupply());
		}
		boolean plated = fits(player, Module.PLATING) && !armoured(player);
		modifier(player, Attributes.ARMOR, PLATING_ARMOR_ID, plated ? PLATING_ARMOR : 0.0, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MOVEMENT_SPEED, PLATING_SPEED_ID, plated ? PLATING_SPEED : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (plated && player.getTicksFrozen() > 0) {
			player.setTicksFrozen(0);
		}
	}

	/** Takes Plating's modifiers off (the power lost). */
	static void clear(ServerPlayer player) {
		modifier(player, Attributes.ARMOR, PLATING_ARMOR_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MOVEMENT_SPEED, PLATING_SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static boolean armoured(Player player) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (!player.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static void modifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
		double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier now = instance.getModifier(id);
		if (amount == 0.0) {
			if (now != null) {
				instance.removeModifier(id);
			}
		} else if (now == null || now.amount() != amount) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	// ---------------------------------------------------------------- the band screen

	/** Opens the band screen (B as yourself). */
	static void open(ServerPlayer player) {
		ServerPlayNetworking.send(player, new Open(points(player)));
	}

	private static void act(ServerPlayer player, String what, String arg) {
		if (!XenoformPower.has(player) || XenoformState.transformed(player)) {
			return;
		}
		Upgrades up = get(player);
		switch (what) {
			case "fit" -> {
				Module m = Module.of(arg);
				if (m == null || !up.reforged() || !up.knows(m) || up.fitted().contains(m.id) || up.fitted().size() >= SLOTS) {
					return;
				}
				List<String> list = new ArrayList<>(up.fitted());
				// Steady and Wild pull against each other: fitting one takes the other out
				list.remove(m == Module.STEADY ? Module.WILD.id : m == Module.WILD ? Module.STEADY.id : "");
				list.add(m.id);
				player.setAttached(UPGRADES, up.fitted(list));
				click(player, 1.4F);
			}
			case "unfit" -> {
				List<String> list = new ArrayList<>(up.fitted());
				if (list.remove(arg)) {
					player.setAttached(UPGRADES, up.fitted(list));
					click(player, 0.9F);
				}
			}
			case "buy" -> {
				Skill s = Skill.of(arg);
				if (s == null || up.rank(s) >= s.ranks || points(player) < s.cost) {
					return;
				}
				player.setAttached(UPGRADES, up.buy(s));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
			}
			case "forget" -> {
				XenoformState.Band band = XenoformState.band(player);
				if (!band.has(arg)) {
					return;
				}
				List<String> list = new ArrayList<>(band.unlocked());
				list.remove(arg);
				player.setAttached(XenoformState.BAND, new XenoformState.Band(List.copyOf(list), 0));
				XenoForm form = XenoForms.get(arg);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.band.forgot", form == null ? Component.literal(arg) : form.name())
					.withStyle(ChatFormatting.GRAY));
				click(player, 0.6F);
			}
			case "wipe" -> {
				player.setAttached(XenoformState.BAND, XenoformState.Band.NONE);
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
				player.sendSystemMessage(Component.translatable("message.jaymospowers.band.wiped").withStyle(ChatFormatting.GRAY));
			}
			default -> {
				// (0.18.126, patch 9) the Looks tab
				if (!BandStyle.act(player, what, arg)) {
					return;
				}
			}
		}
		open(player);
	}

	private static void click(ServerPlayer player, float pitch) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6F, pitch);
	}
}
