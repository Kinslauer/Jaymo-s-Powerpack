package com.jaymospowers;

import com.jaymospowers.core.PowerEvents;
import com.jaymospowers.power.arsenal.ArsenalPower;
import com.jaymospowers.power.chrono.ChronoPower;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.WindPower;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JaymosPowers implements ModInitializer {
	public static final String MOD_ID = "jaymospowers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PowerEvents.register();
		com.jaymospowers.core.BlockCluster.register();
		com.jaymospowers.core.Carry.init();
		com.jaymospowers.core.Crawl.init();
		com.jaymospowers.core.Ledge.init();
		// (0.14.3) freerunning, Kingdoms' Stealth tree's second page
		com.jaymospowers.freerun.Freerun.init();
		com.jaymospowers.core.Meditate.init();
		com.jaymospowers.core.FlightRoll.init();
		com.jaymospowers.core.HiddenArmor.init();
		com.jaymospowers.core.Dismember.init();
		com.jaymospowers.core.wounds.Wounds.init();
		com.jaymospowers.core.Oddities.init();
		com.jaymospowers.core.OffhandLanterns.init();
		com.jaymospowers.core.Shakes.register();
		com.jaymospowers.core.BeamClash.init();
		com.jaymospowers.core.Impacts.init();
		com.jaymospowers.core.LeafSpiders.init();
		com.jaymospowers.core.PortalSpreadQuiet.init();
		// each power registers itself (and its items, entities, packets) from its own package
		WindPower.init();
		ArsenalPower.init();
		WebPower.init();
		ChronoPower.init();
		GravityPower.init();
		com.jaymospowers.power.resonance.ResonancePower.init();
		com.jaymospowers.power.darktide.DarktidePower.init();
		com.jaymospowers.power.xenoform.XenoformPower.init();
		com.jaymospowers.power.zfighter.ZFighterPower.init();
		com.jaymospowers.power.smoke.SmokePower.init();
		// weapons from the Stone Age to the pirates (after Age of Weapons), not a power
		com.jaymospowers.armory.Armory.init();
		com.jaymospowers.armory.TaczGuns.init();
		com.jaymospowers.tinkering.Tinkering.init();
		// Enchanting System Overhaul (MIT) ported: learn from books, pay items at the table
		com.jaymospowers.enchanting.Enchanting.init();
		// (0.11.66) costumes: design and sew them at the Costume Table
		com.jaymospowers.costume.Costume.init();
		// (0.11.82) the Rig: an AI armour suit, salvaged and forged
		com.jaymospowers.rig.Rig.init();
		// (0.11.89) the Backpack: worn on the back, set down on the ground, torn apart only by strong blasts
		com.jaymospowers.backpack.Backpack.init();
		registerAttachments();
	}

	/**
	 * Attachment types register when their class first loads, which on a dedicated server can be long after the client
	 * has told the server which synced attachments it knows: any it hadn't loaded yet were never sent (no ascension,
	 * mastery or modes on the client). Loading every class that declares one at startup registers them all, both sides.
	 */
	private static void registerAttachments() {
		String[] holders = {
			"core.Affinity", "core.BeamClash", "core.BlockCluster", "core.Callings", "core.Carry", "core.CarryBlock", "core.Crawl", "core.Dismember", "core.FlightRoll", "core.Fate", "core.HiddenArmor", "core.Ledge", "core.Mastery", "core.Meditate", "core.Powers", "core.wounds.Wounds", "core.wounds.Downed", "core.wounds.Breaks", "core.wounds.Bleeding", "core.wounds.Pain",
			"power.arsenal.ArsenalArmory", "power.arsenal.ArsenalPower", "power.arsenal.ArsenalReach", "power.arsenal.ArsenalRelics",
			"power.arsenal.ArsenalStash", "power.arsenal.ArsenalPocket", "power.arsenal.HundredBlades", "power.arsenal.ArsenalGates", "power.arsenal.ArsenalVoid",
			"power.arsenal.ArsenalFlintlock", "power.arsenal.ArsenalDamocles", "power.arsenal.ArsenalCannons", "power.arsenal.ArsenalChains",
			"power.chrono.ChronoEchoes", "power.chrono.ChronoSpeed", "power.chrono.ChronoMemory", "power.chrono.ChronoFlow", "power.chrono.future.Future", "power.chrono.future.FutureRuins", "power.wind.WindKick",
			"power.gravity.GravityPower", "power.gravity.GravityForward", "power.gravity.GravityWalk",
			"power.web.WebAscension", "power.web.WebCounter", "power.web.WebMorph", "power.web.WebBehemoth", "power.web.WebInhabit", "power.web.WebSlam", "power.web.WebBridges", "power.web.WebPower", "power.web.WebSymbiote", "power.web.WebSymbioteBond", "power.web.WebSymbioteCushion", "power.web.WebTendrils",
			"power.web.WebWallRun", "power.web.WebParkour", "power.web.WebStrength", "power.web.WebShooters", "power.web.WebSymbioteStrains",
			"power.wind.WindModes", "power.wind.WindPower", "power.wind.WindStorm", "power.wind.WindTempest",
			"power.wind.WindCyclone", "power.wind.WindJet",
			"power.resonance.ResonancePower", "power.resonance.ResonanceHush",
			"power.darktide.DarktideState",
			"power.xenoform.XenoformState",
			"power.zfighter.ZFighterPower", "power.zfighter.ZFighterTraining", "power.zfighter.ZFighterGrowth", "power.zfighter.ZFighterLineage", "power.zfighter.VillagerApe","power.zfighter.RaceMoves", "power.zfighter.ZFighterGuard", "power.zfighter.ZFighterCombo", "power.zfighter.ZFighterStretchCombo", "power.zfighter.ZFighterDisc", "power.zfighter.ZFighterWaveBeam",
			"power.zfighter.ZFighterSites", "power.zfighter.ZFighterFusion", "power.zfighter.ZFighterMasters", "power.zfighter.ZFighterRaids", "power.zfighter.ZFighterTournaments", "power.zfighter.ZFighterCorruption",
			"power.smoke.SmokePower", "power.smoke.SmokeJet", "power.smoke.SmokeVapour", "power.smoke.SmokeSight", "power.smoke.SmokeSmog", "power.smoke.SmokeAshStorm", "power.smoke.SmokeMoves",
			"enchanting.Enchanting", "costume.Costume", "freerun.Freerun", "rig.RigWrecks", "rig.RigCall", "rig.RigMoves", "rig.RigHelm", "backpack.Backpack",
		};
		for (String holder : holders) {
			try {
				Class.forName("com.jaymospowers." + holder, true, JaymosPowers.class.getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
		}
	}
}
