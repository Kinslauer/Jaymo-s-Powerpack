package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Eat to learn (Tensura Neo Otherworld's predation, ARR: the idea only, our own code). One of the monster races (the goblin people,
 * the orcs: {@link MonsterBorn#race}) who uses an empty hand on a body lying free, that Jaymo's Kitchen's corpses make ({@link Devouring}),
 * bites into it as a scavenger does. The last bite eats the body, and the one who ate it takes <b>one trick</b> of what it was
 * ({@link #TRICKS}): thick hide of the dead who walk, night eyes of the spider, a spider's venom blood, fire skin of the blaze, gills of
 * the drowned, a slime's bounce, a creeper's blast hide, the phantom's light step. The tricks one can hold grow with their stage
 * ({@link MonsterBorn#stage}): one, and one more a stage; a new one past the most pushes the oldest out. Without Kitchen's corpses,
 * nothing lies to be eaten. Kept in {@link #KNOWN}, kept through death.
 */
public final class Feasting {
	/** A trick: its key (lang {@code trick.jaymoskingdoms.<key>}). */
	public static final String THICK_HIDE = "thick_hide", NIGHT_EYES = "night_eyes", VENOM_BLOOD = "venom_blood", FIRE_SKIN = "fire_skin", GILLS = "gills",
		BOUNCE = "bounce", BLAST_HIDE = "blast_hide", LIGHT_STEP = "light_step";
	/** What each kind of body teaches. */
	static final Map<EntityType<?>, String> TRICKS = Map.ofEntries(
		Map.entry(EntityTypes.ZOMBIE, THICK_HIDE), Map.entry(EntityTypes.HUSK, THICK_HIDE), Map.entry(EntityTypes.ZOMBIE_VILLAGER, THICK_HIDE),
		Map.entry(EntityTypes.SPIDER, NIGHT_EYES), Map.entry(EntityTypes.CAVE_SPIDER, VENOM_BLOOD), Map.entry(EntityTypes.WITCH, VENOM_BLOOD),
		Map.entry(EntityTypes.BLAZE, FIRE_SKIN), Map.entry(EntityTypes.MAGMA_CUBE, FIRE_SKIN),
		Map.entry(EntityTypes.DROWNED, GILLS), Map.entry(EntityTypes.GUARDIAN, GILLS),
		Map.entry(EntityTypes.SLIME, BOUNCE), Map.entry(EntityTypes.CREEPER, BLAST_HIDE), Map.entry(EntityTypes.PHANTOM, LIGHT_STEP),
		Map.entry(EntityTypes.SKELETON, LIGHT_STEP), Map.entry(EntityTypes.ENDERMAN, NIGHT_EYES));
	/** Armour a thick hide adds; the shares of harm a fire skin and a blast hide let through; the fall a light step forgives. */
	static final double HIDE_ARMOUR = 2.0, LIGHT_FALL = 6.0;
	static final float FIRE_TAKEN = 0.3F, BLAST_TAKEN = 0.4F;
	/** Experience a body eaten gives toward {@link MonsterBorn}'s stages. */
	static final int EAT_XP = 4;
	/** The least time between bites (ticks). */
	static final long BITE_GAP = 8L;

	/** The tricks {@code player} has taken, oldest first. Saved, kept through death. */
	static final AttachmentType<List<String>> KNOWN = AttachmentRegistry.create(JaymosKingdoms.id("monster_tricks"),
		b -> b.persistent(Codec.STRING.listOf()).copyOnDeath());
	private static final Map<java.util.UUID, Long> LAST = new java.util.HashMap<>();

	private Feasting() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || hand != InteractionHand.MAIN_HAND || sp.isShiftKeyDown()
				|| !sp.getMainHandItem().isEmpty() || !MonsterBorn.race(sp) || !Devouring.isBody(target) || sp.distanceToSqr(target) > 9.0) {
				return InteractionResult.PASS;
			}
			long now = server.getGameTime();
			Long last = LAST.get(sp.getUUID());
			if (last != null && now - last < BITE_GAP && now >= last) {
				return InteractionResult.SUCCESS;
			}
			LAST.put(sp.getUUID(), now);
			EntityType<?> was = KitchenCompat.sourceType(target);
			boolean gone = Devouring.bite(server, sp, target);
			sp.getFoodData().eat(2, 0.4F);
			server.playSound(null, sp.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
			if (gone) {
				learn(sp, was);
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Feasting::tick);
	}

	/** The tricks {@code player} may hold: one, and one more each stage. */
	static int most(Player player) {
		return 1 + MonsterBorn.stage(player);
	}

	static List<String> known(Player player) {
		return player.getAttachedOrElse(KNOWN, List.of());
	}

	static boolean has(Player player, String trick) {
		return known(player).contains(trick);
	}

	/** {@code player} has eaten a body that was {@code was}: its trick, if it has one and is not known. */
	private static void learn(ServerPlayer player, @Nullable EntityType<?> was) {
		MonsterBorn.grow(player, EAT_XP);
		String trick = was == null ? null : TRICKS.get(was);
		if (trick == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feast.nothing"));
			return;
		}
		if (has(player, trick)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feast.known", Component.translatable("trick.jaymoskingdoms." + trick)));
			return;
		}
		List<String> next = new java.util.ArrayList<>(known(player));
		next.add(trick);
		while (next.size() > most(player)) {
			next.remove(0);
		}
		player.setAttached(KNOWN, List.copyOf(next));
		player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feast.learned", Component.translatable("trick.jaymoskingdoms." + trick)));
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 40 != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			List<String> tricks = known(p);
			if (tricks.isEmpty() && p.getAttached(KNOWN) == null) {
				continue;
			}
			boolean race = MonsterBorn.race(p);
			Skills.modifier(p, Attributes.ARMOR, "trick_hide", race && tricks.contains(THICK_HIDE) ? HIDE_ARMOUR : 0.0, AttributeModifier.Operation.ADD_VALUE);
			Skills.modifier(p, Attributes.SAFE_FALL_DISTANCE, "trick_step", race && tricks.contains(LIGHT_STEP) ? LIGHT_FALL : 0.0, AttributeModifier.Operation.ADD_VALUE);
			if (!race) {
				continue;
			}
			if (tricks.contains(NIGHT_EYES)) {
				p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
			}
			if (tricks.contains(GILLS)) {
				p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 100, 0, true, false, false));
			}
			if (tricks.contains(VENOM_BLOOD)) {
				p.removeEffect(MobEffects.POISON);
			}
		}
	}

	/** What a monster's tricks make of a blow taken: fire and blasts blunted, a slime's bounce to a fall. */
	public static float taken(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof Player p) || known(p).isEmpty() || !MonsterBorn.race(p)) {
			return amount;
		}
		List<String> tricks = known(p);
		if (tricks.contains(FIRE_SKIN) && source.is(DamageTypeTags.IS_FIRE)) {
			amount *= FIRE_TAKEN;
		}
		if (tricks.contains(BLAST_HIDE) && source.is(DamageTypeTags.IS_EXPLOSION)) {
			amount *= BLAST_TAKEN;
		}
		if (tricks.contains(BOUNCE) && source.is(DamageTypes.FALL)) {
			amount = 0.0F;
		}
		return amount;
	}
}
