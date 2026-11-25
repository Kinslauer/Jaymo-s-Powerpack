package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jspecify.annotations.Nullable;

/**
 * A fighting folk's belt pouch: the few real things it carries to keep going in a fight (fighting AI patch 2, bm-heal: a folk
 * mends from its own gear, {@link BreakOff}). Filled once, the first time anything looks in it (as {@link MobAmmo} fills a
 * quiver: the same as at its spawn, but with its side and leanings settled), from what its kind would carry:
 * a realm's soldiers bread and often a linen bandage, a camp's (barbarians, orcs, goblins) dried meat, sea folk dried kelp; one
 * who leans MEDIC ({@link Comrades.Leaning#MEDIC}) a bandage or two more; now and then a potion of healing. Saved on the folk.
 * What's in it is dropped when it dies, and taken with its weapons when a player disarms it downed ({@code Downed.disarm}).
 */
public final class BeltPouch {
	/** Slots in the pouch. */
	static final int SLOTS = 3;
	/** A realm soldier's bread (least, most), and the chance of a linen bandage. */
	static final int REALM_BREAD_MIN = 1, REALM_BREAD_MAX = 3;
	static final float REALM_BANDAGE_CHANCE = 0.5F;
	/** A camp fighter's dried meat (least, most). */
	static final int CAMP_MEAT_MIN = 1, CAMP_MEAT_MAX = 2;
	/** Sea folk's dried kelp (least, most). */
	static final int SEA_KELP_MIN = 2, SEA_KELP_MAX = 4;
	/** One who leans MEDIC carries this many bandages (least, most). */
	static final int MEDIC_BANDAGES_MIN = 1, MEDIC_BANDAGES_MAX = 2;
	/** The chance of a potion of healing. */
	static final float POTION_CHANCE = 0.04F;
	/** Bandages (Jaymo's Powers', when it's there): a realm's, a camp's. */
	static final String REALM_BANDAGE = "jaymospowers:linen_bandage", CAMP_BANDAGE = "jaymospowers:rag";

	/** What's in the pouch (present once filled, even empty). */
	public static final AttachmentType<List<ItemStack>> POUCH = AttachmentRegistry.create(JaymosKingdoms.id("belt_pouch"),
		builder -> builder.persistent(ItemStack.OPTIONAL_CODEC.listOf()));

	private BeltPouch() {
	}

	/** From {@link BreakOff#register}. */
	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Folk folk && folk.level() instanceof ServerLevel level) {
				for (ItemStack s : take(folk)) {
					folk.spawnAtLocation(level, s);
				}
			}
		});
	}

	/** What's in {@code folk}'s pouch, filled the first time (a fighting folk's; null for one that doesn't fight). */
	private static @Nullable List<ItemStack> held(Folk folk) {
		List<ItemStack> held = folk.getAttached(POUCH);
		if (held == null && folk.combatant()) {
			// (as it was from its spawn: so one that dies or is looted before it ever looked in it still had it)
			held = fill(folk, folk.getRandom());
		}
		return held;
	}

	private static List<ItemStack> fill(Folk folk, RandomSource random) {
		List<ItemStack> pouch = new ArrayList<>();
		String role = Folk.roleName(folk.role());
		boolean sea = role.startsWith("pirate") || role.equals("sea_captain") || role.equals("powder_monkey");
		boolean camp = folk.style() == FactionType.BARBARIAN || role.startsWith("orc") || role.startsWith("goblin") || role.equals("barbarian")
			|| role.equals("chief");
		if (sea) {
			pouch.add(new ItemStack(Items.DRIED_KELP, between(random, SEA_KELP_MIN, SEA_KELP_MAX)));
		} else if (camp) {
			Item meat = com.jaymoskingdoms.nightfall.Nightfall.DRIED_MEAT != null ? com.jaymoskingdoms.nightfall.Nightfall.DRIED_MEAT : Items.COOKED_MUTTON;
			pouch.add(new ItemStack(meat, between(random, CAMP_MEAT_MIN, CAMP_MEAT_MAX)));
		} else {
			pouch.add(new ItemStack(Items.BREAD, between(random, REALM_BREAD_MIN, REALM_BREAD_MAX)));
		}
		boolean realm = !sea && !camp;
		int bandages = Comrades.leanings(folk).has(Comrades.Leaning.MEDIC) ? between(random, MEDIC_BANDAGES_MIN, MEDIC_BANDAGES_MAX) : 0;
		if (realm && random.nextFloat() < REALM_BANDAGE_CHANCE) {
			bandages++;
		}
		Item bandage = bandages > 0 ? item(realm ? REALM_BANDAGE : CAMP_BANDAGE) : null;
		if (bandage != null) {
			pouch.add(new ItemStack(bandage, bandages));
		}
		if (pouch.size() < SLOTS && random.nextFloat() < POTION_CHANCE) {
			pouch.add(PotionContents.createItemStack(Items.POTION, Potions.HEALING));
		}
		folk.setAttached(POUCH, pouch);
		return pouch;
	}

	private static int between(RandomSource random, int least, int most) {
		return least + random.nextInt(most - least + 1);
	}

	private static @Nullable Item item(String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
	}

	/**
	 * {@code mob}'s pouch as a container (a folk's; null for anyone else or one with none). Whatever is taken from it or put back
	 * is saved on the folk as it changes.
	 */
	static @Nullable SimpleContainer open(Mob mob) {
		if (!(mob instanceof Folk folk)) {
			return null;
		}
		List<ItemStack> held = held(folk);
		if (held == null) {
			return null;
		}
		SimpleContainer pouch = new SimpleContainer(SLOTS) {
			@Override
			public void setChanged() {
				super.setChanged();
				List<ItemStack> now = new ArrayList<>();
				for (int i = 0; i < this.getContainerSize(); i++) {
					if (!this.getItem(i).isEmpty()) {
						now.add(this.getItem(i).copy());
					}
				}
				folk.setAttached(POUCH, now);
			}
		};
		for (int i = 0; i < Math.min(SLOTS, held.size()); i++) {
			// (straight into its slots: not written back while it's being filled)
			pouch.getItems().set(i, held.get(i).copy());
		}
		return pouch;
	}

	/** Empties {@code folk}'s pouch: what was in it (a player taking it from them downed, or dropped as they die). */
	public static List<ItemStack> take(Mob mob) {
		List<ItemStack> held = mob instanceof Folk folk ? held(folk) : null;
		if (held == null || held.isEmpty()) {
			return List.of();
		}
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack s : held) {
			if (!s.isEmpty()) {
				out.add(s.copy());
			}
		}
		mob.setAttached(POUCH, List.of());
		return out;
	}
}
