package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.AABB;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

/**
 * The realms that work steel (Andy: "some kingdoms should even have acess to steel armor and weaponry. only the
 * absolute richest and warmongers"). A <b>steel realm</b> ({@link #steel}) is a wealthy kingdom ({@link Wealth.Tier#WEALTHY})
 * that is either the richest kingdom in the world or a warmonger: of the Conquest creed, or waging a war it declared. A
 * fiefdom goes by its overlord.
 * <p>
 * Only a steel realm's smiths make steel ({@link #smelt}): at their workstation ({@link Jobs}), with Jaymo's Powers in,
 * {@link #IRON_PER_STEEL} iron ingots and a coal into a steel ingot, while the stores keep {@link #IRON_KEEP} iron for
 * everything else and hold under {@link #STEEL_KEEP} steel. Only its guards wear steel: the Armory's steel plate, the
 * blackened Templar and steel Ranger pieces ({@link GuardKit}), and steel blades ({@link #blade}), each paid for in steel
 * ingots (or bought from the treasury as any piece is).
 * <p>
 * The working of steel is the realm's <b>steel blueprint</b> ({@link #BLUEPRINT}; Andy: "a specific crafting blueprint
 * gatekept by the kingdom who has it. it is very valuable and closely kept so the information is highly sought after").
 * One of the realm's smiths keeps it in their pocket ({@link #keep}). Never sold or copied; a player gets it only by
 * lifting it ({@link Pickpocket}) or off the smith's body, and the realm draws up a new one for its smith only after
 * {@link #RECOPY}. A player knows steelmaking ({@link #knows}: Jaymo's Powers' anvil hammers steel only for them) while
 * they carry one, or while they are sworn to a steel realm.
 */
public final class SteelRealms {
	/** Iron ingots a steel ingot takes (and a coal); iron the stores keep back from it; the steel they keep in hand. */
	static final int IRON_PER_STEEL = 2, IRON_KEEP = 16, STEEL_KEEP = 24;
	/** A steel blade lasts this many times its iron kin's uses, and hits this much harder. */
	static final float BLADE_USES = 1.6F;
	static final double BLADE_DAMAGE = 1.0;
	/** How long a realm's standing holds before it's looked at again (ticks). */
	static final long FRESH = 1200L;
	/** How often each steel realm's blueprint is looked for (ticks); how long before a lost one is drawn up again (three days). */
	static final long KEEP_EVERY = 2400L, RECOPY = 72000L;
	/** The realm a blueprint is kept by (custom data). */
	static final String REALM = "jaymoskingdoms_realm";
	/** Marks a steel blade (custom data). */
	static final String MARK = "jaymoskingdoms_steel";

	private record Seen(boolean steel, long at) {
	}

	private static final Map<Integer, Seen> SEEN = new HashMap<>();
	/** Each realm's next chance to draw up its blueprint (game time). */
	private static final Map<Integer, Long> NEXT_COPY = new HashMap<>();

	/** The steel blueprint. */
	public static Item BLUEPRINT;
	private static long richestAt = Long.MIN_VALUE;
	private static int richest = -1;

	private SteelRealms() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("steel_blueprint"));
		BLUEPRINT = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)));
		net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)
			.register(output -> output.accept(BLUEPRINT));
		ServerTickEvents.END_SERVER_TICK.register(SteelRealms::keep);
		// (the smith's body gives up what was in its pocket)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager v && entity.level() instanceof ServerLevel level) {
				SimpleContainer bag = v.getInventory();
				for (int i = 0; i < bag.getContainerSize(); i++) {
					if (bag.getItem(i).is(BLUEPRINT)) {
						v.spawnAtLocation(level, bag.removeItemNoUpdate(i));
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SEEN.clear();
			NEXT_COPY.clear();
			richestAt = Long.MIN_VALUE;
			richest = -1;
		});
	}

	/** Whether {@code f} (or the realm it serves) works steel (see the class note). */
	static boolean steel(MinecraftServer server, @Nullable Faction f) {
		Faction realm = f == null ? null : WalledTowns.realm(server, f);
		if (realm == null || realm.fallen() || realm.type() != FactionType.KINGDOM) {
			return false;
		}
		long now = Factions.level(server).getGameTime();
		Seen seen = SEEN.get(realm.id());
		if (seen != null && now - seen.at() < FRESH && now >= seen.at()) {
			return seen.steel();
		}
		boolean steel = Wealth.of(server, realm) == Wealth.Tier.WEALTHY && (richest(server, now) == realm.id() || warmonger(server, realm));
		SEEN.put(realm.id(), new Seen(steel, now));
		return steel;
	}

	/** The richest kingdom standing ({@link Wealth#worth}), or -1. */
	private static int richest(MinecraftServer server, long now) {
		if (now - richestAt < FRESH && now >= richestAt) {
			return richest;
		}
		long best = Long.MIN_VALUE;
		int id = -1;
		for (Faction k : Factions.all(server, FactionType.KINGDOM)) {
			if (!k.fallen()) {
				long w = Wealth.worth(server, k);
				if (w > best) {
					best = w;
					id = k.id();
				}
			}
		}
		richest = id;
		richestAt = now;
		return id;
	}

	/** Of the Conquest creed, or at the head of a war it declared. */
	private static boolean warmonger(MinecraftServer server, Faction realm) {
		if (GreatWars.creed(server, realm) == GreatWars.Creed.CONQUEST) {
			return true;
		}
		for (WarData.War w : Wars.warsOf(server, realm.id())) {
			if (w.attacker().principal() == realm.id()) {
				return true;
			}
		}
		return false;
	}

	/** Every so often: a steel realm whose smiths have no blueprint draws one up for the first of them, when it may. */
	private static void keep(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % KEEP_EVERY != 37) {
			return;
		}
		for (Faction f : Factions.all(server, FactionType.KINGDOM)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat()) || !steel(server, f)) {
				continue;
			}
			List<Villager> smiths = level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(Economy.POPULATION_RANGE),
				v -> v.isAlive() && !v.isBaby() && smith(v));
			if (smiths.isEmpty() || smiths.stream().anyMatch(v -> v.getInventory().hasAnyMatching(s -> s.is(BLUEPRINT)))
				|| now < NEXT_COPY.getOrDefault(f.id(), Long.MIN_VALUE)) {
				continue;
			}
			if (smiths.get(0).getInventory().addItem(blueprint(f)).isEmpty()) {
				NEXT_COPY.put(f.id(), now + RECOPY);
			}
		}
	}

	private static boolean smith(Villager v) {
		var trade = v.getVillagerData().profession();
		return trade.is(VillagerProfession.ARMORER) || trade.is(VillagerProfession.TOOLSMITH) || trade.is(VillagerProfession.WEAPONSMITH);
	}

	/** {@code realm}'s steel blueprint, its name on it. */
	static ItemStack blueprint(Faction realm) {
		ItemStack out = new ItemStack(BLUEPRINT);
		CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> tag.putInt(REALM, realm.id()));
		out.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymoskingdoms.steel_blueprint.kept", realm.name())
			.withStyle(ChatFormatting.GRAY))));
		return out;
	}

	/** Whether {@code player} knows steelmaking: carrying a blueprint, or sworn to a steel realm. */
	static boolean knows(ServerPlayer player) {
		if (player.getInventory().hasAnyMatching(s -> s.is(BLUEPRINT))) {
			return true;
		}
		MinecraftServer server = player.level().getServer();
		Faction mine = Factions.of(server, player.getUUID());
		return mine != null && steel(server, mine);
	}

	/** Jaymo's Powers' steel ingot, or null without it. */
	static @Nullable Item ingot() {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", "steel_ingot")).orElse(null);
	}

	/** A steel realm's smith's batch ({@link Jobs}): iron and coal into steel (see the class note). True if one was made. */
	static boolean smelt(ServerLevel level, Faction f, Villager smith) {
		Item steel = ingot();
		if (steel == null || !steel(level.getServer(), f) || Storage.count(level, f, s -> s.is(Armory.STEEL_INGOTS)) >= STEEL_KEEP
			|| Storage.count(level, f, Items.IRON_INGOT) < IRON_KEEP + IRON_PER_STEEL
			|| Storage.count(level, f, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL)) == 0) {
			return false;
		}
		Storage.take(level, f, Items.IRON_INGOT, IRON_PER_STEEL);
		Storage.take(level, f, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL), 1);
		if (Storage.put(level, f, steel, 1) > 0) {
			Storage.put(level, f, Items.IRON_INGOT, IRON_PER_STEEL);
			return false;
		}
		level.playSound(null, smith.blockPosition(), net.minecraft.sounds.SoundEvents.BLASTFURNACE_FIRE_CRACKLE, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.9F);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, smith.getX(), smith.getY() + 1.0, smith.getZ(), 3, 0.3, 0.1, 0.3, 0.0);
		return true;
	}

	/** {@code iron}, an iron blade, made a steel one: named so, lasting longer and hitting a little harder. */
	static ItemStack blade(ItemStack iron) {
		ItemStack out = iron.copy();
		if (steelBlade(out)) {
			return out;
		}
		out.set(DataComponents.ITEM_NAME, Component.translatable("item.jaymoskingdoms.steel_blade", iron.getItem().getDefaultInstance().getItemName()));
		Integer uses = iron.get(DataComponents.MAX_DAMAGE);
		if (uses != null) {
			out.set(DataComponents.MAX_DAMAGE, Math.round(uses * BLADE_USES));
		}
		ItemAttributeModifiers mods = iron.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, mods.withModifierAdded(Attributes.ATTACK_DAMAGE,
			new AttributeModifier(JaymosKingdoms.id("steel_blade"), BLADE_DAMAGE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
		CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> tag.putBoolean(MARK, true));
		return out;
	}

	/** Whether {@code stack} is a steel blade ({@link #blade}). */
	static boolean steelBlade(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBooleanOr(MARK, false);
	}
}
