package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Wild magic (fifth edition's wild surge): now and then a cast goes off into something else. It happens to a Sorcerer of the wild
 * bloodline on one cast in {@link #WILD_ONE_IN}, and to anyone who casts in a cursed place (the land of a great lair,
 * {@link LairLand}) on one in {@link #CURSED_ONE_IN}. What it becomes is a roll on a table of {@link #SURGES} things: you glow, sheep fall
 * out of the sky, you turn blue, a fireball lands at your feet, everyone near is healed, you float, and more. None of it kills
 * outright, and the cast itself still happens. A wild Sorcerer's imbue and bolt take a different element each time.
 */
public final class WildMagic {
	public static final String WILD = "wild";
	/** One cast in this many surges: for the wild bloodline, and in a cursed place. */
	static final int WILD_ONE_IN = 6, CURSED_ONE_IN = 8;
	/** No second surge within this long (ticks). */
	static final int REST = 60;
	/** What a wild Sorcerer's blade or bolt may be. */
	static final List<String> ELEMENTS = List.of("fire", "water", "earth", "air", "lightning", "ice");
	/** The effects: how many there are on the table. */
	static final int SURGES = 13;
	static final int GLOW_TICKS = 20 * 60, SHEEP = 5, SHEEP_HEIGHT = 10, HEAL_RADIUS = 8, HEAL = 6, FLOAT_TICKS = 20 * 5, FALL_TICKS = 20 * 12;
	static final int BLUE_TICKS = 20 * 90, SHRINK_TICKS = 20 * 30, SPEED_TICKS = 20 * 10, FLOWER_RADIUS = 4;
	static final float FIREBALL = 1.2F, SHOCK = 3.0F;
	static final double SHRINK = -0.6;
	static final String BLUE_TEAM = "jaymoskingdoms_blue";
	static final Identifier SHRINK_ID = JaymosKingdoms.id("wild_shrink");
	static final int COLOUR = 0xD070F0;

	private static final List<java.util.function.BiPredicate<ServerLevel, BlockPos>> PLACES = new ArrayList<>();

	/** Makes a place cursed for casting too (the land of a great lair is already). */
	public static void addCursedPlace(java.util.function.BiPredicate<ServerLevel, BlockPos> place) {
		PLACES.add(place);
	}

	/** Whether a cast at {@code pos} is in a cursed place. */
	static boolean cursed(ServerLevel level, BlockPos pos) {
		if (LairLand.inLand(level, pos)) {
			return true;
		}
		for (java.util.function.BiPredicate<ServerLevel, BlockPos> p : PLACES) {
			if (p.test(level, pos)) {
				return true;
			}
		}
		return false;
	}

	private record Undo(long when, Runnable run) {
	}

	private static final List<Undo> UNDO = new ArrayList<>();
	private static final java.util.Map<UUID, Long> LAST = new java.util.HashMap<>();

	private WildMagic() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WildMagic::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			UNDO.clear();
			LAST.clear();
		});
		// a restart in the middle of a blue spell must not leave anyone blue
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.player;
			PlayerTeam team = server.getScoreboard().getPlayersTeam(p.getScoreboardName());
			if (team != null && team.getName().equals(BLUE_TEAM)) {
				server.getScoreboard().removePlayerFromTeam(p.getScoreboardName(), team);
			}
		});
	}

	/** Whether {@code player} is a Sorcerer of the wild bloodline. */
	public static boolean wild(ServerPlayer player) {
		return WILD.equals(Classes.optionOf(player, Classes.PlayerClass.SORCERER));
	}

	/** The element a wild Sorcerer's blade or bolt takes this time. */
	static String element(RandomSource random) {
		return ELEMENTS.get(random.nextInt(ELEMENTS.size()));
	}

	/** A cast has just been made (by any means): maybe it surges. */
	static void cast(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		if (now - LAST.getOrDefault(player.getUUID(), -1000L) < REST) {
			return;
		}
		boolean cursed = cursed(level, player.blockPosition());
		if (!wild(player) && !cursed) {
			return;
		}
		RandomSource r = level.getRandom();
		if (!(wild(player) && r.nextInt(WILD_ONE_IN) == 0) && !(cursed && r.nextInt(CURSED_ONE_IN) == 0)) {
			return;
		}
		LAST.put(player.getUUID(), now);
		surge(level, player, r.nextInt(SURGES));
	}

	/** Surge number {@code which} of the table, on {@code player}. */
	static void surge(ServerLevel level, ServerPlayer player, int which) {
		RandomSource r = level.getRandom();
		Vec3 at = player.position();
		Fx.burst(level, at.x, at.y + 1.0, at.z, COLOUR, 1.0F, 16, 0.5, 0.8, 0.5);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ILLUSIONER_PREPARE_MIRROR, SoundSource.PLAYERS, 0.8F, 1.6F);
		switch (which) {
			case 0 -> player.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, true));
			case 1 -> {
				for (int i = 0; i < SHEEP; i++) {
					Sheep sheep = EntityTypes.SHEEP.create(level, EntitySpawnReason.EVENT);
					if (sheep != null) {
						sheep.snapTo(at.x + (r.nextDouble() - 0.5) * 4.0, at.y + SHEEP_HEIGHT + r.nextInt(4), at.z + (r.nextDouble() - 0.5) * 4.0, r.nextFloat() * 360.0F, 0.0F);
						sheep.setColor(DyeColor.byId(r.nextInt(16)));
						level.addFreshEntity(sheep);
					}
				}
			}
			case 2 -> blue(level, player);
			case 3 -> {
				level.explode(null, at.x, at.y, at.z, FIREBALL, false, Level.ExplosionInteraction.NONE);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 30, 0.5, 0.2, 0.5, 0.08);
				player.igniteForSeconds(3.0F);
			}
			case 4 -> {
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(HEAL_RADIUS), LivingEntity::isAlive)) {
					e.heal(HEAL);
					level.sendParticles(ParticleTypes.HEART, e.getX(), e.getY(1.0), e.getZ(), 4, 0.3, 0.4, 0.3, 0.0);
				}
			}
			case 5 -> {
				player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, FLOAT_TICKS, 1, false, true, true));
				later(level, FLOAT_TICKS, () -> player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FALL_TICKS, 0, false, false, true)));
			}
			case 6 -> swap(level, player);
			case 7 -> {
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setVisualOnly(true);
					bolt.snapTo(at.x, at.y, at.z);
					level.addFreshEntity(bolt);
				}
				player.hurtServer(level, level.damageSources().lightningBolt(), Math.min(SHOCK, Math.max(0.0F, player.getHealth() - 2.0F)));
			}
			case 8 -> flowers(level, player.blockPosition(), r);
			case 9 -> shrink(level, player);
			case 10 -> player.addEffect(new MobEffectInstance(MobEffects.SPEED, SPEED_TICKS, 2, false, true, true));
			case 11 -> Mana.set(player, Mana.max(player));
			default -> {
				for (int i = 0; i < 3; i++) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.SHEEP_AMBIENT, SoundSource.NEUTRAL, 1.0F, 0.8F + i * 0.25F);
				}
				level.sendParticles(ParticleTypes.NOTE, at.x, at.y + 2.0, at.z, 8, 0.8, 0.3, 0.8, 1.0);
			}
		}
	}

	private static void later(ServerLevel level, int ticks, Runnable run) {
		UNDO.add(new Undo(level.getGameTime() + ticks, run));
	}

	private static void tick(MinecraftServer server) {
		if (UNDO.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (Undo u : List.copyOf(UNDO)) {
			if (now >= u.when()) {
				UNDO.remove(u);
				u.run().run();
			}
		}
	}

	/** You turn blue: an outline of blue light for a while (not if you already wear a team's colour). */
	private static void blue(ServerLevel level, ServerPlayer player) {
		Scoreboard board = level.getServer().getScoreboard();
		if (board.getPlayersTeam(player.getScoreboardName()) == null) {
			PlayerTeam team = board.getPlayerTeam(BLUE_TEAM);
			if (team == null) {
				team = board.addPlayerTeam(BLUE_TEAM);
				team.setColor(java.util.Optional.of(net.minecraft.world.scores.TeamColor.BLUE));
			}
			board.addPlayerToTeam(player.getScoreboardName(), team);
			String name = player.getScoreboardName();
			later(level, BLUE_TICKS, () -> {
				PlayerTeam t = board.getPlayersTeam(name);
				if (t != null && t.getName().equals(BLUE_TEAM)) {
					board.removePlayerFromTeam(name, t);
				}
			});
		}
		player.addEffect(new MobEffectInstance(MobEffects.GLOWING, BLUE_TICKS, 0, false, false, true));
		level.sendParticles(Fx.mote(0x3060FF, 1.2F), player.getX(), player.getY(1.0), player.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
	}

	private static void swap(ServerLevel level, ServerPlayer player) {
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8.0), e -> e != player && e.isAlive() && !(e instanceof ServerPlayer));
		if (near.isEmpty()) {
			return;
		}
		LivingEntity other = near.get(level.getRandom().nextInt(near.size()));
		Vec3 a = player.position(), b = other.position();
		player.teleportTo(level, b.x, b.y, b.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		other.teleportTo(a.x, a.y, a.z);
		level.sendParticles(Fx.GLYPH, a.x, a.y + 1.0, a.z, 10, 0.3, 0.5, 0.3, 0.3);
		level.sendParticles(Fx.GLYPH, b.x, b.y + 1.0, b.z, 10, 0.3, 0.5, 0.3, 0.3);
	}

	private static void flowers(ServerLevel level, BlockPos centre, RandomSource r) {
		var flowers = List.of(Blocks.POPPY, Blocks.DANDELION, Blocks.CORNFLOWER, Blocks.OXEYE_DAISY, Blocks.AZURE_BLUET, Blocks.ALLIUM);
		for (BlockPos p : BlockPos.betweenClosed(centre.offset(-FLOWER_RADIUS, -1, -FLOWER_RADIUS), centre.offset(FLOWER_RADIUS, 1, FLOWER_RADIUS))) {
			if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).is(Blocks.GRASS_BLOCK) && r.nextInt(3) == 0) {
				level.setBlock(p, flowers.get(r.nextInt(flowers.size())).defaultBlockState(), 3);
			}
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, centre.getX() + 0.5, centre.getY() + 1.0, centre.getZ() + 0.5, 20, 2.0, 0.5, 2.0, 0.0);
	}

	private static void shrink(ServerLevel level, ServerPlayer player) {
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale == null || scale.hasModifier(SHRINK_ID)) {
			return;
		}
		scale.addTransientModifier(new AttributeModifier(SHRINK_ID, SHRINK, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		later(level, SHRINK_TICKS, () -> scale.removeModifier(SHRINK_ID));
	}
}
