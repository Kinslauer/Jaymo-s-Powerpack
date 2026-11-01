package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.29) Training a power, all in one place: the pace past the four abilities (the Mastery ranks), the spots and
 * gear that make meditating and shadowboxing count for more, and the training items themselves. Every tuning number for
 * it is a constant at the top of this class.
 *
 * <h2>Mastery ranks</h2>
 * Once all four abilities are learned, mastery keeps growing through five ranks, Adept to Paragon ({@link #RANK_AT}).
 * Each rank is a permanent stat jump in the power's own direction ({@link #PERKS}: Wind gets quicker feet, Gravity heavier
 * blows and a firmer stance, and so on), and is played with the pack's {@link Milestones} fanfare.
 *
 * <h2>Training spots</h2>
 * Meditating (or shadowboxing) where your power belongs pays {@link #SPOT_PACE} times as often and lets more practice
 * through ({@link #SPOT_ALLOWANCE_TICKS}): Wind in the cold or up on a peak, Darktide in water or rain at night, Cryo on
 * ice or snow, Web among cobwebs, Smoke by a lit campfire, Chrono with a Training Hourglass in hand, Resonance with a
 * Tuning Fork (or by a bell), Arsenal with a weapon in hand (drills), Gravity and Z Fighters in a Weighted Gi.
 * Training never skips the floor: it only lets practice reach the allowance faster.
 */
public final class Training {
	// ---- Mastery ranks (points earned after all four abilities; one point a minute of play, practice up to 3x) ----
	public static final int RANKS = 5;
	/** Rank points needed for each rank (cumulative; index 0 = no rank): about 8, 12, 16, 20 and 24 days of play alone. */
	public static final int[] RANK_AT = {0, 160, 400, 720, 1120, 1600};
	public static final String[] RANK_KEYS = {"none", "adept", "expert", "master", "grandmaster", "paragon"};

	/** One stat a rank adds to, {@code perRank} times the rank. */
	record Perk(Holder<Attribute> attribute, double perRank, AttributeModifier.Operation op) {
	}

	private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;
	private static final AttributeModifier.Operation PCT = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
	/** Each power's rank perks, by its id path; anything not listed takes {@link #DEFAULT_PERKS}. At Paragon these are x5. */
	static final Map<String, List<Perk>> PERKS = Map.ofEntries(
		Map.entry("gravity", List.of(new Perk(Attributes.ATTACK_DAMAGE, 0.04, PCT), new Perk(Attributes.KNOCKBACK_RESISTANCE, 0.04, ADD))),
		Map.entry("wind", List.of(new Perk(Attributes.MOVEMENT_SPEED, 0.02, PCT), new Perk(Attributes.SAFE_FALL_DISTANCE, 0.6, ADD))),
		Map.entry("web", List.of(new Perk(Attributes.ATTACK_DAMAGE, 0.03, PCT), new Perk(Attributes.SAFE_FALL_DISTANCE, 0.6, ADD),
			new Perk(Attributes.JUMP_STRENGTH, 0.02, PCT))),
		Map.entry("chrono", List.of(new Perk(Attributes.ATTACK_SPEED, 0.03, PCT), new Perk(Attributes.MOVEMENT_SPEED, 0.01, PCT))),
		Map.entry("darktide", List.of(new Perk(Attributes.OXYGEN_BONUS, 1.0, ADD), new Perk(Attributes.WATER_MOVEMENT_EFFICIENCY, 0.06, ADD),
			new Perk(Attributes.ATTACK_DAMAGE, 0.03, PCT))),
		Map.entry("xenoform", List.of(new Perk(Attributes.ARMOR, 1.0, ADD), new Perk(Attributes.MAX_HEALTH, 1.0, ADD))),
		Map.entry("smoke", List.of(new Perk(Attributes.MOVEMENT_SPEED, 0.015, PCT), new Perk(Attributes.SNEAKING_SPEED, 0.04, ADD))),
		Map.entry("cryo", List.of(new Perk(Attributes.ARMOR_TOUGHNESS, 0.5, ADD), new Perk(Attributes.MAX_HEALTH, 1.0, ADD))),
		Map.entry("resonance", List.of(new Perk(Attributes.ATTACK_DAMAGE, 0.03, PCT), new Perk(Attributes.ARMOR, 0.5, ADD))),
		Map.entry("arsenal", List.of(new Perk(Attributes.ATTACK_DAMAGE, 0.04, PCT), new Perk(Attributes.ATTACK_SPEED, 0.02, PCT))),
		Map.entry("zfighter", List.of(new Perk(Attributes.MAX_HEALTH, 1.0, ADD), new Perk(Attributes.ATTACK_DAMAGE, 0.03, PCT))));
	static final List<Perk> DEFAULT_PERKS = List.of(new Perk(Attributes.MAX_HEALTH, 1.0, ADD), new Perk(Attributes.ATTACK_DAMAGE, 0.02, PCT));
	/** Every stat any perk touches (to take them all off a player who loses their power). */
	private static final List<Holder<Attribute>> PERK_ATTRIBUTES = List.of(Attributes.ATTACK_DAMAGE, Attributes.KNOCKBACK_RESISTANCE,
		Attributes.MOVEMENT_SPEED, Attributes.SAFE_FALL_DISTANCE, Attributes.JUMP_STRENGTH, Attributes.ATTACK_SPEED, Attributes.OXYGEN_BONUS,
		Attributes.WATER_MOVEMENT_EFFICIENCY, Attributes.ARMOR, Attributes.MAX_HEALTH, Attributes.SNEAKING_SPEED, Attributes.ARMOR_TOUGHNESS);
	/** How often the rank perks are put back on (ticks): after a respawn, a power change, a rank. */
	static final int PERK_REFRESH = 100;

	// ---- training spots ----
	/** A training spot pays meditation practice this share of the usual interval (0.5 = twice as often). */
	public static final float SPOT_PACE = 0.5F;
	/** And refills one more practice allowance point every this many ticks while you train there. */
	public static final int SPOT_ALLOWANCE_TICKS = 600;
	/** Wind: the cold, or this high up. */
	static final double PEAK_Y = 150.0;
	/** Web: this many cobwebs within {@link #NEAR} blocks. */
	static final int COBWEBS = 3;
	/** How near a campfire, bell or cobweb must be (blocks). */
	static final int NEAR = 3;

	// ---- training gear ----
	/** Weighted Gi: slower, lower jumps; a Z Fighter trains this much faster in it (stacking with the ankle weights). */
	static final double GI_SLOW = -0.1, GI_JUMP = -0.08;
	public static final float GI_RATE = 1.5F;

	public static final ResourceKey<EquipmentAsset> GI_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosPowers.id("weighted_gi"));
	public static Item WEIGHTED_GI, TRAINING_HOURGLASS, TUNING_FORK;

	/** Which spot each player was last told they're training in (so the line shows once a session, not every point). */
	private static final Map<UUID, String> TOLD = new HashMap<>();

	private Training() {
	}

	public static void init() {
		ResourceKey<Item> giKey = key("weighted_gi");
		ItemAttributeModifiers gi = ItemAttributeModifiers.builder()
			.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(JaymosPowers.id("weighted_gi_speed"), GI_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				EquipmentSlotGroup.CHEST)
			.add(Attributes.JUMP_STRENGTH, new AttributeModifier(JaymosPowers.id("weighted_gi_jump"), GI_JUMP, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
				EquipmentSlotGroup.CHEST)
			.build();
		WEIGHTED_GI = Registry.register(BuiltInRegistries.ITEM, giKey, new Item(new Item.Properties().setId(giKey).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(net.minecraft.core.component.DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST)
				.setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER)
				.setAsset(GI_ASSET)
				.setAllowedEntities(EntityTypes.PLAYER)
				.build())
			.attributes(gi)));
		ResourceKey<Item> glassKey = key("training_hourglass");
		TRAINING_HOURGLASS = Registry.register(BuiltInRegistries.ITEM, glassKey, new Hourglass(new Item.Properties().setId(glassKey).stacksTo(1)));
		ResourceKey<Item> forkKey = key("tuning_fork");
		TUNING_FORK = Registry.register(BuiltInRegistries.ITEM, forkKey, new TuningFork(new Item.Properties().setId(forkKey).stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(WEIGHTED_GI);
			output.accept(TRAINING_HOURGLASS);
			output.accept(TUNING_FORK);
		});
	}

	private static ResourceKey<Item> key(String name) {
		return ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
	}

	// ---- Mastery ranks ---------------------------------------------------------------------------------------------

	/** The rank {@code points} rank points reach (0-{@link #RANKS}). */
	public static int rankFor(int points) {
		int rank = 0;
		while (rank < RANKS && points >= RANK_AT[rank + 1]) {
			rank++;
		}
		return rank;
	}

	public static Component rankName(int rank) {
		return Component.translatable("training.jaymospowers.rank." + RANK_KEYS[Math.max(0, Math.min(RANKS, rank))]);
	}

	private static List<Perk> perks(Power power) {
		return PERKS.getOrDefault(power.id().getPath(), DEFAULT_PERKS);
	}

	private static Identifier perkId(Holder<Attribute> attribute) {
		return JaymosPowers.id("mastery_rank_" + attribute.unwrapKey().map(k -> k.identifier().getPath()).orElse("stat"));
	}

	/** Puts {@code player}'s rank perks on them as they stand (none without a power or a rank). Cheap; called now and then. */
	static void applyPerks(ServerPlayer player) {
		Power power = Powers.of(player);
		int rank = power == null ? 0 : Mastery.rank(player);
		Map<Holder<Attribute>, Perk> wanted = new HashMap<>();
		if (rank > 0) {
			for (Perk perk : perks(power)) {
				wanted.put(perk.attribute(), perk);
			}
		}
		for (Holder<Attribute> attribute : PERK_ATTRIBUTES) {
			AttributeInstance instance = player.getAttribute(attribute);
			if (instance == null) {
				continue;
			}
			Identifier id = perkId(attribute);
			Perk perk = wanted.get(attribute);
			if (perk == null) {
				if (instance.getModifier(id) != null) {
					instance.removeModifier(id);
				}
				continue;
			}
			double amount = perk.perRank() * rank;
			AttributeModifier now = instance.getModifier(id);
			if (now == null || now.amount() != amount || now.operation() != perk.op()) {
				instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, perk.op()));
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** Every tick for every player, power or not: perks kept in step now and then. */
	static void tick(ServerPlayer player) {
		if (player.tickCount % PERK_REFRESH == 7) {
			applyPerks(player);
		}
	}

	/** What a rank's perks come to, for the chat line: "+8% Attack Damage, +0.08 Knockback Resistance". */
	static Component describePerks(Power power, int rank) {
		MutableComponent line = Component.empty();
		boolean first = true;
		for (Perk perk : perks(power)) {
			if (!first) {
				line.append(", ");
			}
			first = false;
			double amount = perk.perRank() * rank;
			String value = perk.op() == ADD ? trim(amount) : trim(amount * 100.0) + "%";
			line.append("+" + value + " ").append(Component.translatable(perk.attribute().value().getDescriptionId()));
		}
		return line;
	}

	private static String trim(double v) {
		return v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
	}

	/** The fanfare for reaching {@code rank}. */
	static void announceRank(ServerPlayer player, Power power, int rank) {
		applyPerks(player);
		Component name = rankName(rank).copy().withStyle(style -> style.withColor(TextColor.fromRgb(power.color())));
		Component title = Component.translatable("training.jaymospowers.rank_title");
		Component subtitle = Component.translatable("training.jaymospowers.rank_subtitle", name, power.name());
		Component chat = Component.translatable("training.jaymospowers.rank_chat", name, power.name(), describePerks(power, rank));
		Milestones.fanfare(player, title, subtitle, chat, power.color(), rank >= RANKS ? Milestones.GRAND : Milestones.MAJOR);
	}

	// ---- training spots --------------------------------------------------------------------------------------------

	/** The training spot {@code player} is meditating in for their power, as a lang key suffix, or null for none. */
	public static @Nullable String spot(ServerPlayer player) {
		Power power = Powers.of(player);
		if (power == null) {
			return null;
		}
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		return switch (power.id().getPath()) {
			case "wind" -> com.jaymospowers.power.wind.WindCold.isCold(player) || player.getY() >= PEAK_Y ? "cold" : null;
			case "gravity", "zfighter" -> weighted(player) ? "weighted" : null;
			case "darktide" -> level.isDarkOutside() && (player.isInWaterOrRain()) ? "tide" : null;
			case "cryo" -> player.isInPowderSnow || icy(level.getBlockState(at.below())) || com.jaymospowers.power.wind.WindCold.isCold(player) ? "ice" : null;
			case "web" -> count(level, at, state -> state.is(Blocks.COBWEB)) >= COBWEBS ? "cobwebs" : null;
			case "smoke" -> count(level, at, CampfireBlock::isLitCampfire) > 0 ? "campfire" : null;
			case "chrono" -> holding(player, TRAINING_HOURGLASS) ? "hourglass" : null;
			case "resonance" -> holding(player, TUNING_FORK) || count(level, at, state -> state.is(Blocks.BELL)) > 0 ? "tuning" : null;
			case "arsenal" -> armed(player) ? "drills" : null;
			default -> null;
		};
	}

	/** Weighted Gi, or the heavy ankle weights, worn. */
	public static boolean weighted(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(WEIGHTED_GI)
			|| player.getItemBySlot(EquipmentSlot.FEET).is(com.jaymospowers.power.zfighter.ZFighterItems.HEAVY_TRAINING_WEIGHTS);
	}

	/** How much faster a Z Fighter trains for the Gi on his chest (1 without). */
	public static float giRate(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(WEIGHTED_GI) ? GI_RATE : 1.0F;
	}

	private static boolean icy(BlockState state) {
		return state.is(BlockTags.ICE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SNOW);
	}

	private static boolean holding(Player player, Item item) {
		return item != null && (player.getMainHandItem().is(item) || player.getOffhandItem().is(item));
	}

	private static boolean armed(Player player) {
		ItemStack hand = player.getMainHandItem();
		return hand.is(ItemTags.SWORDS) || hand.is(ItemTags.AXES) || hand.is(ItemTags.SPEARS) || hand.is(ItemTags.WEAPON_ENCHANTABLE);
	}

	private static int count(Level level, BlockPos at, java.util.function.Predicate<BlockState> test) {
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-NEAR, -1, -NEAR), at.offset(NEAR, 2, NEAR))) {
			if (test.test(level.getBlockState(pos))) {
				found++;
			}
		}
		return found;
	}

	/** A training point paid in a spot: its own motes, and the first time this session a line over the hotbar. */
	static void trained(ServerPlayer player, String spot) {
		if (!spot.equals(TOLD.put(player.getUUID(), spot))) {
			player.sendOverlayMessage(Component.translatable("training.jaymospowers.spot." + spot).withStyle(ChatFormatting.AQUA));
		}
		ParticleOptions mote = switch (spot) {
			case "cold", "ice" -> ParticleTypes.SNOWFLAKE;
			case "tide" -> ParticleTypes.BUBBLE_POP;
			case "cobwebs" -> ParticleTypes.WHITE_ASH;
			case "campfire" -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
			case "hourglass" -> ParticleTypes.END_ROD;
			case "tuning" -> ParticleTypes.NOTE;
			case "weighted" -> ParticleTypes.CLOUD;
			default -> ParticleTypes.CRIT;
		};
		player.level().sendParticles(mote, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.5, 0.5, 0.5, 0.02);
	}

	static void forget(UUID id) {
		TOLD.remove(id);
	}

	// ---- items -----------------------------------------------------------------------------------------------------

	/** Chrono's Training Hourglass: hold it while meditating. Use it to turn it over (a soft trickle). */
	static final class Hourglass extends Item {
		Hourglass(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.SAND_FALL, SoundSource.PLAYERS, 0.6F, 1.4F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
			return InteractionResult.SUCCESS;
		}
	}

	/** Resonance's Tuning Fork: hold it while meditating. Use it to strike it (a clear ring). */
	static final class TuningFork extends Item {
		TuningFork(Item.Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
			player.getCooldowns().addCooldown(player.getItemInHand(hand), 20);
			return InteractionResult.SUCCESS;
		}
	}
}
