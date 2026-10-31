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
		com.jaymospowers.core.Kneel.init();
		com.jaymospowers.core.Crawl.init();
		// (0.18.57) Sneak + the wheel steps through the stances
		com.jaymospowers.core.Stance.init();
		com.jaymospowers.core.Ledge.init();
		// (0.14.3) freerunning, Kingdoms' Stealth tree's second page
		com.jaymospowers.freerun.Freerun.init();
		com.jaymospowers.water.Dives.init();
		// (0.15.5) feathers (stamina) and the dodge, in place of Combat Roll
		com.jaymospowers.stamina.Feathers.init();
		com.jaymospowers.stamina.Dodge.init();
		com.jaymospowers.core.Meditate.init();
		com.jaymospowers.core.SwallowedPower.init();
		com.jaymospowers.core.FlightRoll.init();
		com.jaymospowers.core.LockOn.init();
		// (0.17.67) the guard and the parry, for everyone
		com.jaymospowers.combat.Guard.init();
		com.jaymospowers.combat.AttackTells.init();
		com.jaymospowers.core.HiddenArmor.init();
		com.jaymospowers.core.Dismember.init();
		com.jaymospowers.core.wounds.Wounds.init();
		com.jaymospowers.core.Oddities.init();
		com.jaymospowers.core.OffhandLanterns.init();
		com.jaymospowers.core.Shakes.register();
		com.jaymospowers.core.GroundCracks.register();
		com.jaymospowers.core.UltimateFilm.register();
		com.jaymospowers.core.Soundscape.register();
		com.jaymospowers.core.BeamClash.init();
		com.jaymospowers.core.Impacts.init();
		com.jaymospowers.core.LeafSpiders.init();
		com.jaymospowers.core.PortalSpreadQuiet.init();
		// (0.15.143) giants (the Great Ape, a Behemoth...) reach as far as they've grown
		com.jaymospowers.core.GiantReach.init();
		// (0.17.3) gifted villagers: powers held by those who aren't players (who awakens is Kingdoms')
		com.jaymospowers.core.gifted.GiftedAI.init();
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
		// (0.14.0) Cryokinesis, the ice power
		com.jaymospowers.power.cryo.CryoPower.init();
		// weapons from the Stone Age to the pirates (after Age of Weapons), not a power
		com.jaymospowers.armory.Armory.init();
		com.jaymospowers.armory.TaczGuns.init();
		com.jaymospowers.armory.FlintlockDraw.init();
		com.jaymospowers.tinkering.Tinkering.init();
		// (0.16.3) loose rocks and flint on the ground, and RLCraft's start: knapped flint, plant string, no crafted planks
		com.jaymospowers.ground.GroundRocks.init();
		com.jaymospowers.ground.FirstTools.init();
		// (0.17.14) players throw rocks, as Kingdoms' poor guards and barbarians do
		com.jaymospowers.ground.ThrownRocks.init();
		// (0.18.55) far worlds restart the stone age: veiled ore, first metals, the field anvil, the patrol's skip writ
		com.jaymospowers.power.zfighter.farworld.FarWorlds.init();
		// sneak + use a rock to knap a stone head on a 3x3 grid
		com.jaymospowers.ground.knapping.Knapping.init();
		// the fletching table opens a station: iron and steel arrows, Tinkers arrows, lingering arrows
		com.jaymospowers.tinkering.fletching.Fletching.init();
		// Enchanting System Overhaul (MIT) ported: learn from books, pay items at the table
		com.jaymospowers.enchanting.Enchanting.init();
		com.jaymospowers.alchemy.Alchemy.init();
		// (0.11.66) costumes: design and sew them at the Costume Table
		com.jaymospowers.costume.Costume.init();
		// (0.11.82) the Rig: an AI armour suit, salvaged and forged
		com.jaymospowers.rig.Rig.init();
		// (0.11.89) the Backpack: worn on the back, set down on the ground, torn apart only by strong blasts
		com.jaymospowers.backpack.Backpack.init();
		// (0.17.184) Baubles-style accessory slots (amulet, two rings, belt, head, body, charm) and seven baubles to wear
		com.jaymospowers.baubles.Baubles.init();
		// (0.17.188) Friends & Foes' totems work from the charm slot
		com.jaymospowers.compat.FriendsAndFoesCompat.init();
		// (0.15.58) the Aether's blocks and items (its dimension and the way in are Wind's, power/wind/aether)
		com.jaymospowers.aether.Aether.init();
		// (0.17.115) the 26.4 snapshots' ice: Freezing and its potion, ice balls, the Frostbite, icicles and the ice blocks
		com.jaymospowers.frost.Frost.init();
		// (0.17.116) snow-style layer blocks of every wooden and stone block, cut with the saw or at the stonecutter
		com.jaymospowers.layers.Layers.init();
		// (0.18.115) young for spiders, parrots and our wild beasts, whales and sky creatures
		com.jaymospowers.young.Younglings.init();
		// Kitchen's ragdolls (a crash, a blast...) leave alone anyone a power has hold of
		com.jaymospowers.power.web.KitchenCompat.registerRagdollVetoes();
		com.jaymospowers.power.web.KitchenCompat.registerRagdollHolds();
		com.jaymospowers.power.web.KitchenCompat.registerCorpseBlood();
		com.jaymospowers.power.web.KitchenCompat.registerBrawn();
		registerAttachments();
	}

	/**
	 * Attachment types register when their class first loads, which on a dedicated server can be long after the client
	 * has told the server which synced attachments it knows: any it hadn't loaded yet were never sent (no ascension,
	 * mastery or modes on the client). Loading every class that declares one at startup registers them all, both sides.
	 */
	private static void registerAttachments() {
		String[] holders = {
			"core.Affinity", "core.BeamClash", "core.BlockCluster", "core.Callings", "core.Carry", "core.CarryBlock", "core.Crawl", "core.Dismember", "core.Prosthetics", "core.FlightRoll", "core.Fate", "core.HiddenArmor", "core.Ledge", "core.LockOn", "core.Mastery", "core.Meditate", "core.Powers", "core.Stance", "core.SwallowedPower", "core.wounds.Wounds", "core.wounds.Downed", "core.wounds.Breaks", "core.wounds.Bleeding", "core.wounds.Pain", "core.wounds.SelfRevive", "core.wounds.Battered", "core.wounds.Tripped", "core.wounds.Tending", "core.finisher.Finishers", "core.gifted.Gifted", "combat.Guard",
			"power.arsenal.ArsenalArmory", "power.arsenal.ArsenalPower", "power.arsenal.ArsenalReach", "power.arsenal.ArsenalRelics",
			"power.arsenal.ArsenalStash", "power.arsenal.ArsenalPocket", "power.arsenal.ArsenalEmber", "power.arsenal.HundredBlades", "power.arsenal.ArsenalGates", "power.arsenal.ArsenalVoid",
			"power.arsenal.ArsenalFlintlock", "power.arsenal.ArsenalDamocles", "power.arsenal.ArsenalCannons", "power.arsenal.ArsenalChains", "power.arsenal.ArsenalPlaces", "power.arsenal.ArsenalStanding",
			"power.chrono.ChronoEchoes", "power.chrono.ChronoDirectives", "power.chrono.ChronoSpeed", "power.chrono.ChronoMemory", "power.chrono.ChronoFlow", "power.chrono.future.Future", "power.chrono.future.FutureRuins", "power.chrono.future.FutureOmenPilots", "power.wind.WindKick",
			"power.gravity.GravityPower", "power.gravity.GravityForward", "power.gravity.GravityWalk",
			"power.web.WebAscension", "power.web.WebCounter", "power.web.WebMorph", "power.web.WebBehemoth", "power.web.WebInhabit", "power.web.WebSlam", "power.web.WebBridges", "power.web.WebPower", "power.web.WebSymbiote", "power.web.WebSymbioteBond", "power.web.WebSymbioteCushion", "power.web.WebTendrils", "power.web.WebHeldGrab", "power.web.WebExecutions",
			"power.web.WebWallRun", "power.web.WebParkour", "power.web.WebStrength", "power.web.WebShooters", "power.web.WebSymbioteStrains", "power.web.WebSymbioteCollect", "power.web.WebSymbioteBody",
			"power.web.WebSymbioteMind",
			"power.wind.WindModes", "power.wind.WindPower", "power.wind.SorasLineage", "power.wind.WindStorm", "power.wind.WindTempest",
			"power.wind.WindCyclone", "power.wind.WindJet", "power.wind.aether.WindAether",
			"power.resonance.ResonancePower", "power.resonance.ResonanceHush",
			"power.darktide.DarktideState",
			"power.xenoform.XenoformState", "power.xenoform.XenoBands", "power.xenoform.nadir.NadirRules", "power.xenoform.nadir.NadirStash",
			"power.zfighter.ZFighterPower", "power.zfighter.ZFighterTraining", "power.zfighter.ZFighterGrowth", "power.zfighter.ZFighterLineage", "power.zfighter.ApeRage", "power.zfighter.ApeSmash", "power.zfighter.ApeGrab", "power.zfighter.VillagerApe","power.zfighter.RaceMoves", "power.zfighter.ZFighterGuard", "power.zfighter.ZFighterCombo", "power.zfighter.ZFighterStretchCombo", "power.zfighter.ZFighterDisc", "power.zfighter.ZFighterWaveBeam",
			"power.zfighter.ZFighterTeleport",
			"power.zfighter.ZFighterBranchForms", "power.zfighter.ZFighterEgo", "power.zfighter.ZFighterAndroid", "power.zfighter.ZFighterFrostForms",
			"power.zfighter.ZFighterSites", "power.zfighter.ZFighterFusion", "power.zfighter.ZFighterMasters", "power.zfighter.ZFighterRaids", "power.zfighter.ZFighterTournaments", "power.zfighter.ZFighterCorruption",
			"power.zfighter.ZFighterAura", "power.zfighter.ZFighterSlots", "power.zfighter.ZFighterKiWeapon","power.zfighter.ZFighterGrab", "power.zfighter.ZFighterSidestep",
			"power.zfighter.ZFighterFusionItems", "power.zfighter.ZFighterFiveFusion", "power.zfighter.ZFighterMasterFusion", "power.zfighter.ZFighterFalseSuper", "power.zfighter.ZFighterGoldenApe", "power.zfighter.ZFighterGod", "power.zfighter.ZFighterGodRitual", "power.zfighter.ZFighterInstinct", "power.zfighter.ZFighterLegendary",
			"power.zfighter.ZFighterEarthlingRace", "power.zfighter.ZFighterVerdantMerge", "power.zfighter.ZFighterFrostReserve", "power.zfighter.ZFighterWarriorComeback", "power.zfighter.ZFighterSonicSway", "power.zfighter.ZFighterSelfDestruct", "power.zfighter.ZFighterCreator",
			"power.zfighter.ZFighterLookout", "power.zfighter.ZFighterWishExtras", "power.zfighter.ZFighterSagas", "power.zfighter.ZFighterSideQuests", "power.zfighter.ZFighterAfterlife", "power.zfighter.ZFighterDemonRealm",
			"power.zfighter.pod.PodWrecks", "power.zfighter.sky.Charts", "power.zfighter.farworld.FarWorlds", "power.zfighter.farworld.PatrolWrit",
			"power.zfighter.ZFighterSpace", "power.zfighter.ZFighterPodTrip", "power.zfighter.ZFighterPatrol", "power.zfighter.ZFighterRaiders", "power.zfighter.ZFighterSpaceMissions", "power.zfighter.ZFighterStarts",
			"power.ember.EmberKindle", "power.ember.FlameAvatar", "power.ember.AvatarMoves", "power.heritage.HeritageMoves",
			"power.cryo.CryoPower", "power.cryo.CryoSlide", "power.cryo.CryoBlizzard", "power.cryo.CryoMoves",
			"power.smoke.SmokePower", "power.smoke.SmokeJet", "power.smoke.SmokeVapour", "power.smoke.SmokeSight", "power.smoke.SmokeSmog", "power.smoke.SmokeAshStorm", "power.smoke.SmokeMoves",
			"enchanting.Enchanting", "costume.Costume", "freerun.Freerun", "freerun.FastRun", "freerun.Slide", "stamina.Feathers", "stamina.Dodge", "rig.RigWrecks", "rig.RigCall", "rig.RigMoves", "rig.RigHelm", "backpack.Backpack", "baubles.Baubles", "young.Younglings", "armory.Lash", "armory.FlintlockDraw", "armory.HammerSlam", "armory.KnifeGuard", "aether.structure.OutpostTracker",
			"alchemy.Flux", "alchemy.Corruption", "alchemy.Knowledge", "alchemy.codex.Scans", "alchemy.flux.Cures", "alchemy.cauldron.CauldronBrewing", "alchemy.LabEscape",
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
