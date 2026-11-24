package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.beasts.Beasts;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The beasts of the demons' country that have come up into the Nether, ported from From the Shadows Reborn 3.0
 * (Sonmok14 and L_Ender; AGPL v3; credit and file list in licenses/from-the-shadows/CREDITS-nether-beasts.md): their
 * types, sounds, where they spawn, and what they share. They are hostile to every living thing in the Nether (Andy,
 * 2026-11-22), not only to players.
 */
public final class NetherBeasts {
	public static EntityType<Nehemoth> NEHEMOTH;
	public static EntityType<Bulldrogioth> BULLDROGIOTH;
	/** The thorns a Bulldrogioth throws off its shell. */
	public static EntityType<CoralThorn> CORAL_THORN;
	/** What a Bulldrogioth's shell is made into; the crust armour's material ({@link FtsGear}). */
	public static Item CRIMSON_SHELL;

	public static SoundEvent NEHEMOTH_ROAR, NEHEMOTH_ATTACK, NEHEMOTH_BITE, NEHEMOTH_BITE_WARN, NEHEMOTH_CHARGE, NEHEMOTH_DEATH,
		NEHEMOTH_HURT, NEHEMOTH_IDLE, NEHEMOTH_STOMP, NEHEMOTH_BEAM, NEHEMOTH_BEAM_READY, NEHEMOTH_GORE, NEHEMOTH_CRITICAL;
	public static SoundEvent BULLDROGIOTH_ATTACK, BULLDROGIOTH_HURT, BULLDROGIOTH_IDLE;

	/** Where Nehemoths come up: the soul sand valleys and the demon realm (Powers' biomes, named in the tag as optional). */
	static final TagKey<Biome> HAS_NEHEMOTH = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_nehemoth"));

	/** Where Bulldrogioths come up: the Nether's crimson, warped and basalt country (its colour follows the biome). */
	static final TagKey<Biome> HAS_BULLDROGIOTH = TagKey.create(Registries.BIOME, JaymosKingdoms.id("has_bulldrogioth"));

	private NetherBeasts() {
	}

	public static void register() {
		NEHEMOTH_ROAR = sound("nehemoth.roar");
		NEHEMOTH_ATTACK = sound("nehemoth.attack");
		NEHEMOTH_BITE = sound("nehemoth.bite");
		NEHEMOTH_BITE_WARN = sound("nehemoth.bite_warn");
		NEHEMOTH_CHARGE = sound("nehemoth.charge");
		NEHEMOTH_DEATH = sound("nehemoth.death");
		NEHEMOTH_HURT = sound("nehemoth.hurt");
		NEHEMOTH_IDLE = sound("nehemoth.idle");
		NEHEMOTH_STOMP = sound("nehemoth.stomp");
		NEHEMOTH_BEAM = sound("nehemoth.beam");
		NEHEMOTH_BEAM_READY = sound("nehemoth.beam_ready");
		NEHEMOTH_GORE = sound("nehemoth.gore");
		NEHEMOTH_CRITICAL = sound("nehemoth.critical");

		BULLDROGIOTH_ATTACK = sound("bulldrogioth.attack");
		BULLDROGIOTH_HURT = sound("bulldrogioth.hurt");
		BULLDROGIOTH_IDLE = sound("bulldrogioth.idle");
		ResourceKey<Item> shell = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("crimson_shell"));
		CRIMSON_SHELL = Registry.register(BuiltInRegistries.ITEM, shell, new Item(new Item.Properties().setId(shell)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.accept(CRIMSON_SHELL));
		FtsGear.register();
		ResourceKey<EntityType<?>> thorn = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("coral_thorn"));
		CORAL_THORN = Registry.register(BuiltInRegistries.ENTITY_TYPE, thorn, EntityType.Builder.<CoralThorn>of(CoralThorn::new, MobCategory.MISC)
			.sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(10).build(thorn));

		NEHEMOTH = Beasts.type("nehemoth", EntityType.Builder.of(Nehemoth::new, MobCategory.MONSTER).sized(1.25F, 3.65F).fireImmune()
			.clientTrackingRange(9), Nehemoth.createAttributes());
		SpawnPlacements.register(NEHEMOTH, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Nehemoth::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_NEHEMOTH), MobCategory.MONSTER, NEHEMOTH, Nehemoth.SPAWN_WEIGHT, 1, 1);
		BULLDROGIOTH = Beasts.type("bulldrogioth", EntityType.Builder.of(Bulldrogioth::new, MobCategory.MONSTER).sized(2.5F, 2.9F).fireImmune()
			.clientTrackingRange(9), Bulldrogioth.createAttributes());
		SpawnPlacements.register(BULLDROGIOTH, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Bulldrogioth::checkSpawn);
		BiomeModifications.addSpawn(BiomeSelectors.tag(HAS_BULLDROGIOTH), MobCategory.MONSTER, BULLDROGIOTH, Bulldrogioth.SPAWN_WEIGHT, 1, 1);
	}

	/** Whether the beasts may hurt {@code e}: anything alive but their own kin, the Warden, armour stands and players who can't be hurt. */
	static boolean fair(LivingEntity e) {
		if (!e.isAlive() || e instanceof NetherBeast || e instanceof Warden || e instanceof ArmorStand) {
			return false;
		}
		return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
	}

	/** In the Nether they hunt every living thing; anywhere else only what the mob's own goals pick. */
	static boolean prey(LivingEntity target, ServerLevel level) {
		return level.dimension() == Level.NETHER && fair(target);
	}

	/** Their blows tear (Powers' wounds open a cut for a "tearing" attacker); the tag is on only for the blow. */
	static boolean tear(ServerLevel level, LivingEntity by, LivingEntity target, float damage) {
		by.addTag(TEARS);
		try {
			return target.hurtServer(level, level.damageSources().mobAttack(by), damage);
		} finally {
			by.removeTag(TEARS);
		}
	}

	/** Powers' marker for an attacker whose blows open cuts on any flesh they hurt (Bleeding#TEARS). */
	static final String TEARS = "jaymospowers.tears";

	/** A heavy blow takes the shield out of a player's hands for a while. */
	static void breakShield(LivingEntity target, int ticks) {
		if (target instanceof Player player && player.isBlocking()) {
			ItemStack shield = player.getUseItem();
			player.stopUsingItem();
			player.getCooldowns().addCooldown(shield, ticks);
		}
	}

	private static SoundEvent sound(String name) {
		Identifier id = JaymosKingdoms.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
