package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Umbrellas and parasols among the people (Reference Mods patch 4; the items are Jaymo's Powers', read here by the item
 * tag and the model-data contract in its {@code Umbrellas}: flag 0 open, colours 0 and 1 canopy and panel, string 0 the
 * panel style).
 * <ul>
 * <li><b>Wealthy realms</b> ({@link Wealth.Tier#WEALTHY}; a fiefdom's riches are its overlord's): their women carry a
 * lace parasol in the realm's colours, its coat of arms' field for the canopy and its tincture for the heraldic panel,
 * the panel's shape one of four by the arms. Poorer realms don't. In a prosperous or wealthy realm a quarter of the
 * men carry a plain umbrella in the same colours.</li>
 * <li><b>Mohknee</b>: the women of its villages carry an oiled-paper parasol with blossom painting on bamboo ribs, no
 * matter the realm's wealth. Barbarian camps carry none (Andy: "only women in civilization").</li>
 * <li>Carried in the off hand: opened out of doors in the rain (and a parasol in the sun), furled and hung on the arm
 * indoors.</li>
 * <li><b>Sun</b>: a vampire (or anyone the sun weakens) can walk in the day under an open one ({@link #shade}); it wears.</li>
 * </ul>
 */
public final class Parasols {
	/** Every umbrella and parasol (Jaymo's Powers' item tag). */
	static final TagKey<Item> UMBRELLAS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("jaymospowers", "umbrellas"));
	/** How often the people are looked at (ticks); how close to a seat they're watched. */
	static final int EVERY = 20;
	static final double WATCHED = 96.0;
	/** In a prosperous or wealthy realm, one man in this many carries an umbrella. */
	static final int MAN_ONE_IN = 4;
	/** The sun wears an umbrella held against it a point of durability this often (ticks). */
	static final long WEAR_TICKS = 100L;
	/** The papers of Mohknee's parasols (RGB): cream, rose, pale blue, sage, lilac. */
	static final int[] PAPERS = {0xF2E6C4, 0xF4CFD2, 0xCFE0EC, 0xD3E0C3, 0xDCD0EA};

	private static final Map<UUID, Long> WORN = new HashMap<>();

	private Parasols() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Parasols::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> WORN.clear());
	}

	// ---- the sun and the rain ----

	/** The open umbrella {@code who} holds (either hand), or empty. */
	static ItemStack openIn(LivingEntity who) {
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			ItemStack stack = who.getItemBySlot(slot);
			if (stack.is(UMBRELLAS) && isOpen(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** {@code who} holds an open umbrella: the rain doesn't reach them. */
	public static boolean keepsRain(LivingEntity who) {
		return !openIn(who).isEmpty();
	}

	/**
	 * Call where the sun has reached {@code who}: true if an open umbrella shades them from it (and wears it a little).
	 */
	public static boolean shade(LivingEntity who) {
		ItemStack umbrella = openIn(who);
		if (umbrella.isEmpty()) {
			return false;
		}
		long now = who.level().getGameTime();
		Long last = WORN.get(who.getUUID());
		if (last == null || now - last >= WEAR_TICKS) {
			WORN.put(who.getUUID(), now);
			umbrella.hurtAndBreak(1, who, who.getItemBySlot(EquipmentSlot.MAINHAND) == umbrella ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		}
		return true;
	}

	static boolean isOpen(ItemStack stack) {
		CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
		return data != null && Boolean.TRUE.equals(data.getBoolean(0));
	}

	static void setOpen(ItemStack stack, boolean open) {
		CustomModelData data = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.EMPTY);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(data.floats(), List.of(open), data.strings(), data.colors()));
	}

	/** {@code v} carries an umbrella or parasol (on the arm or open), whichever hand. */
	static boolean owns(LivingEntity v) {
		return v.getItemBySlot(EquipmentSlot.OFFHAND).is(UMBRELLAS) || v.getItemBySlot(EquipmentSlot.MAINHAND).is(UMBRELLAS);
	}

	// ---- who carries one ----

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		if (level.getGameTime() % EVERY != 5) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || !level.isPositionEntityTicking(f.seat())) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, new AABB(f.seat()).inflate(WATCHED), v -> v.isAlive() && !v.isBaby())) {
				ItemStack held = v.getItemBySlot(EquipmentSlot.OFFHAND);
				if (held.isEmpty()) {
					give(server, level, v, f);
				} else if (held.is(UMBRELLAS)) {
					weather(level, v, held);
				}
			}
		}
	}

	/** Out of doors, an umbrella is opened in the rain and a parasol in the sun too; indoors it's furled. */
	private static void weather(ServerLevel level, Villager v, ItemStack held) {
		boolean outdoors = level.canSeeSky(v.blockPosition().above());
		boolean rain = level.isRainingAt(v.blockPosition());
		boolean sturdy = BuiltInRegistries.ITEM.getKey(held.getItem()).getPath().equals("umbrella");
		boolean sun = !sturdy && level.isBrightOutside() && !level.isRaining();
		boolean open = outdoors && (rain || sun);
		if (open != isOpen(held)) {
			setOpen(held, open);
		}
	}

	private static void give(MinecraftServer server, ServerLevel level, Villager v, Faction f) {
		if (f.is(FactionType.BARBARIAN)) {
			return;
		}
		boolean woman = McaCompat.female(v);
		int roll = Math.floorMod(v.getUUID().hashCode(), 12);
		ItemStack stack = null;
		if (woman && com.jaymoskingdoms.seas.Mohknee.on(level, v.blockPosition())) {
			stack = stack(level, "paper_parasol", PAPERS[roll % PAPERS.length], 0xFFFFFF, "");
		} else {
			Faction realm = f.overlord() >= 0 ? Factions.get(server, f.overlord()) : f;
			if (realm == null || !realm.type().isRealm()) {
				return;
			}
			Wealth.Tier tier = Wealth.of(server, realm);
			Outfits.Heraldry arms = Outfits.heraldry(realm, server);
			if (woman && tier == Wealth.Tier.WEALTHY) {
				stack = stack(level, "parasol", Outfits.rgb(arms.field()), Outfits.rgb(arms.charge()), String.valueOf(Math.floorMod(arms.ordinary(), 4)));
			} else if (!woman && tier != Wealth.Tier.POOR && roll % MAN_ONE_IN == 0) {
				stack = stack(level, "umbrella", Outfits.rgb(arms.field()), Outfits.rgb(arms.charge()), "");
			}
		}
		if (stack != null) {
			v.setItemSlot(EquipmentSlot.OFFHAND, stack);
			v.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
		}
	}

	/** A furled umbrella of Powers' {@code id} with the canopy and panel colours and the panel style, or null without Powers. */
	private static @Nullable ItemStack stack(ServerLevel level, String id, int canopy, int panel, String style) {
		Item item = BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", id)).orElse(null);
		if (item == null) {
			return null;
		}
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(false), style.isEmpty() ? List.of() : List.of(style),
			List.of(canopy, panel)));
		return stack;
	}
}
