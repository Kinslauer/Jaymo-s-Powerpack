package com.jaymospowers.armory;

import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.tinkering.WeaponParts;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

/**
 * (0.20.18) The patrons' weapons (Kingdoms 0.7.518: each champion drops its patron's iron head). Put together at the Tool
 * Station like the kings' weapons; each has one ability, stronger for that patron's sworn and cold (nothing) for one who turned
 * from the patron and is in its trials. The numbers are here; the hits are {@link PatronWeaponHits}, the item {@link PatronWeaponItem}.
 */
public final class PatronWeapons {
	/** The weapon, its head (a {@link WeaponParts} part) and the Kingdoms patron key whose sworn it serves best. */
	public enum Kind {
		HEARTHGUARD("hearthguard", WeaponParts.HEARTHGUARD_HEAD, "hearthkeeper", 5.0F, -2.8F),
		ANVIL_HAMMER("anvil_fathers_hammer", WeaponParts.ANVIL_HAMMER_HEAD, "anvil_father", 8.0F, -3.3F),
		TIDECALLER("tidecaller", WeaponParts.TIDECALLER_HEAD, "deep_mother", 5.0F, -2.7F),
		HUNTSMANS_SPEAR("huntsmans_spear", WeaponParts.HUNTSMANS_SPEAR_TIP, "green_huntsman", 4.0F, -2.5F),
		GRAVELIGHT("gravelight", WeaponParts.GRAVELIGHT_BLADE, "lantern_warden", 6.0F, -3.0F),
		SUNBRAND("sunbrand", WeaponParts.SUNBRAND_BLADE, "crowned_sun", 5.0F, -2.4F),
		ASHFANG("ashfang", WeaponParts.ASHFANG_BLADE, "ash_whisperer", 2.5F, -1.6F),
		PLAIN_WORD("plain_word", WeaponParts.PLAIN_WORD_BLADE, "truth", 6.5F, -2.9F);

		public final String id, head, patron;
		final float damage, speed;

		Kind(String id, String head, String patron, float damage, float speed) {
			this.id = id;
			this.head = head;
			this.patron = patron;
			this.damage = damage;
			this.speed = speed;
		}
	}

	// ---- Hearthguard: Kindled Blow (on hit)
	/** At most this often (ticks) per wielder, a hit heals the wielder and the players near (blocks; sworn farther) by this much (sworn: times). */
	static final int HEARTH_GAP = 40;
	static final double HEARTH_RANGE = 6.0, HEARTH_RANGE_SWORN = 8.0;
	static final float HEARTH_HEAL = 1.5F, HEARTH_SWORN = 2.0F;
	// ---- Anvil-Father's Hammer: Sunder (on hit)
	/** Each hit takes this much armour off for this long, up to this many stacks (sworn: more). */
	static final double SUNDER_ARMOR = 2.0;
	static final int SUNDER_TICKS = 100, SUNDER_MOST = 3, SUNDER_MOST_SWORN = 5;
	// ---- Tidecaller: Undertow (use)
	/** Pulls mobs within this far (sworn: farther; times this much in water or rain) in this hard, slowed this long; the wait (sworn: shorter). */
	static final double UNDERTOW_RANGE = 8.0, UNDERTOW_RANGE_SWORN = 10.0, UNDERTOW_PULL = 1.1;
	static final float UNDERTOW_WET = 1.5F;
	static final int UNDERTOW_SLOW = 60, UNDERTOW_COOLDOWN = 300, UNDERTOW_COOLDOWN_SWORN = 200;
	// ---- Huntsman's Spear: Marked Prey (on hit)
	/** The mark's length; the share of a hit's damage added to hits on the marked (beasts: more); sworn values. */
	static final int MARK_TICKS = 200;
	static final float MARK_BONUS = 0.15F, MARK_BEAST = 0.30F, MARK_BONUS_SWORN = 0.25F, MARK_BEAST_SWORN = 0.45F;
	// ---- Gravelight: Soul Flame (on hit)
	/** The undead burn this long and take this much more (by night: more; sworn: times this), and the sworn heal this much for each. */
	static final int GRAVE_FIRE = 100;
	static final float GRAVE_BONUS = 4.0F, GRAVE_NIGHT = 6.0F, GRAVE_SWORN = 1.5F, GRAVE_HEAL = 1.0F;
	// ---- Sunbrand: Sun's Judgement (use)
	/** Reaches this far; strikes after this long; the beam's harm (sworn: more; by day under open sky: times this); fire and blindness; the wait. */
	static final double SUN_RANGE = 16.0;
	static final int SUN_WINDUP = 15, SUN_FIRE = 80, SUN_BLIND = 40, SUN_COOLDOWN = 400, SUN_COOLDOWN_SWORN = 280;
	static final float SUN_DAMAGE = 8.0F, SUN_DAMAGE_SWORN = 11.0F, SUN_DAY = 1.5F;
	// ---- Ashfang: Smokestep (use)
	/** The leap back (blocks/tick and up); how long unseen (sworn: longer); the window for the cut and its multiplier; the wait. */
	static final double SMOKE_BACK = 1.0, SMOKE_UP = 0.25, SMOKE_LOSE_RANGE = 6.0;
	static final int SMOKE_INVIS = 60, SMOKE_INVIS_SWORN = 100, SMOKE_WINDOW = 80, SMOKE_COOLDOWN = 240, SMOKE_COOLDOWN_SWORN = 160;
	static final float SMOKE_BACKSTAB = 1.75F;
	// ---- The Plain Word: Unmasking (on hit)
	/** Beneficial effects stripped for each hit (sworn: more); how long the unmasked invisible glow. */
	static final int UNMASK_EACH = 1, UNMASK_EACH_SWORN = 2, UNMASK_GLOW = 100;

	public static Item HEARTHGUARD, ANVIL_FATHERS_HAMMER, TIDECALLER, HUNTSMANS_SPEAR, GRAVELIGHT, SUNBRAND, ASHFANG, PLAIN_WORD;

	/** Where the client says who is looking at a tooltip (so a cold weapon says so); set by the client. */
	public static Supplier<Player> VIEWER = () -> null;

	private PatronWeapons() {
	}

	/** Item properties, for {@code Armory.item}. */
	static Item.Properties props(Kind kind, Item.Properties p) {
		return p.sword(ToolMaterial.DIAMOND, kind.damage, kind.speed).rarity(Rarity.EPIC).fireResistant();
	}

	/** 1 when sworn to the weapon's patron (Kingdoms tier 1 or more), 0 when not (or a mob), -1 while under that patron's trials. */
	public static float sworn(LivingEntity wielder, Kind kind) {
		return wielder instanceof Player player ? Math.signum(KingdomsCompat.blessing(player, "patron_weapon/" + kind.patron)) : 0.0F;
	}

	public static void init() {
		PatronWeaponHits.init();
	}

	public static List<Item> all() {
		return List.of(HEARTHGUARD, ANVIL_FATHERS_HAMMER, TIDECALLER, HUNTSMANS_SPEAR, GRAVELIGHT, SUNBRAND, ASHFANG, PLAIN_WORD);
	}
}
