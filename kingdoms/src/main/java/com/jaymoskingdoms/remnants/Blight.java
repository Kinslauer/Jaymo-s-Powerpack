package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.wild.FeralWolf;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The blight round the Irradiated monolith, the last of the Aether's sick light, and what it does to the living near it.
 * <ul>
 * <li><b>The land.</b> Within {@link #LAND_RADIUS} blocks of an Irradiated monolith (found the way every monolith is,
 * {@link Monoliths}; its structure files are never touched) a player who lingers takes a dose each second, more within
 * {@link #CORE_RADIUS}. Away from it the dose slowly wears off. The terrain itself is left as it is.</li>
 * <li><b>The sickness.</b> Three stages by dose ({@link #STAGE_AT}), shown by the body and the screen, never by numbers: a
 * sour mouth and now and then a swimming head; shaking hands and heavy legs; weakness, a dimming of sight and a rolling
 * stomach. It never kills. Cured by the Potion of Cleansing (its effect) and by a healer's work or a spell of mending
 * ({@link #cure}); honey and wild garlic ease it ({@link #ease}).</li>
 * <li><b>The wolves.</b> A wild wolf that dens within {@link #DEN_RADIUS} blocks of the monolith for {@link #CHANGE_AFTER}
 * rounds of {@link #DEN_TICKS} ticks changes: tougher, quicker, tinted sick green on the client, trailing faint motes.
 * It hunts like a stalking wolf at any hour ({@code WolfStalkers}), and its bite carries {@link #BITE_DOSE} of the
 * sickness. A wolf that is tamed afterwards is only a wolf again.</li>
 * </ul>
 * {@link #nearest} is the one question the ticking charm in Jaymo's Powers asks.
 */
public final class Blight {
	// ---- tuning ----
	/** How far (blocks, horizontally) the land is sick round an Irradiated monolith, and the heart of it. */
	static final double LAND_RADIUS = 56.0, CORE_RADIUS = 24.0;
	/** The dose a second in the land, and in its heart (in place of the first). */
	static final int LAND_DOSE = 1, CORE_DOSE = 3;
	/** The most a body can carry, and how often (seconds) a dose wears off when away from the land, and by how much. */
	static final int MAX_DOSE = 600, WEAR_EVERY = 3, WEAR_BY = 1;
	/** The dose at each stage (1, 2, 3). */
	static final int[] STAGE_AT = {60, 180, 360};
	/** What a blighted wolf's bite adds, and what honey or garlic takes off. */
	static final int BITE_DOSE = 90, EASE_BY = 240;
	/** How long the signs last each time they are refreshed (ticks). */
	static final int SIGN_TICKS = 60;
	/** Per second, the chance of a swimming head by stage (index 0 is stage 1), and how long it lasts (ticks). */
	static final float[] SWIM_CHANCE = {1.0F / 40.0F, 1.0F / 25.0F, 1.0F / 12.0F};
	static final int SWIM_TICKS = 120;
	/** How often (ticks) the land is looked at for players, and the wolves' dens. */
	static final int PLAYER_TICKS = 20, DEN_TICKS = 200;
	/** How far (blocks, horizontally and up and down) a wolf is counted as denning at the monolith. */
	static final double DEN_RADIUS = 40.0, DEN_HEIGHT = 30.0;
	/** Rounds of denning before a wolf changes. */
	static final int CHANGE_AFTER = 12;
	/** What the change gives: extra health, extra bite, and extra speed (a share of its own). */
	static final double WOLF_HEALTH = 10.0, WOLF_BITE = 2.5, WOLF_SPEED = 0.12;
	/** How often (ticks) a changed wolf sheds a mote. */
	static final int MOTE_TICKS = 10;
	/** The sick light's colour. */
	static final int GLOW = 0xA8C23A;
	/** The Potion of Cleansing's effect (Jaymo's Powers). */
	private static final Identifier CLEANSING = Identifier.fromNamespaceAndPath("jaymospowers", "cleansing");
	private static final Identifier WOLF_MODIFIER = JaymosKingdoms.id("blighted_wolf");

	/** On a player: the dose they carry. Saved; not kept through death. */
	public static final AttachmentType<Integer> DOSE = AttachmentRegistry.create(JaymosKingdoms.id("blight_dose"),
		builder -> builder.persistent(Codec.INT));
	/** On a wolf: rounds spent denning at the monolith. Saved. */
	public static final AttachmentType<Integer> DENNED = AttachmentRegistry.create(JaymosKingdoms.id("blight_denned"),
		builder -> builder.persistent(Codec.INT));
	/** On a wolf: changed. Saved, and synced to every client for its tint. */
	public static final AttachmentType<Boolean> BLIGHTED = AttachmentRegistry.create(JaymosKingdoms.id("blighted_wolf"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** The changed wolves now loaded (not saved: they are found again as they load). */
	private static final Set<Mob> PACK = Collections.newSetFromMap(new WeakHashMap<>());
	/** The last stage each player was told of. */
	private static final Map<java.util.UUID, Integer> TOLD = new java.util.HashMap<>();

	private Blight() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Blight::tick);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && blighted(mob)) {
				PACK.add(mob);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				PACK.remove(mob);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && entity instanceof ServerPlayer player && source.getDirectEntity() instanceof Mob mob && blighted(mob)) {
				dose(player, BITE_DOSE);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PACK.clear();
			TOLD.clear();
		});
	}

	// ---- queries ----

	/** Whether {@code mob} is a wolf the blight has changed (and is still wild). */
	public static boolean blighted(@Nullable Entity mob) {
		return mob instanceof Mob m && Boolean.TRUE.equals(m.getAttached(BLIGHTED)) && !(m instanceof TamableAnimal tame && tame.isTame());
	}

	/** The stage (0 to 3) of the dose {@code player} carries. */
	public static int stage(net.minecraft.world.entity.player.Player player) {
		int dose = player.getAttachedOrElse(DOSE, 0);
		int stage = 0;
		for (int i = 0; i < STAGE_AT.length; i++) {
			if (dose >= STAGE_AT[i]) {
				stage = i + 1;
			}
		}
		return stage;
	}

	/** The Irradiated monoliths of the Overworld. */
	private static List<Monoliths.Monolith> sources(ServerLevel overworld) {
		return Monoliths.all(overworld).stream().filter(m -> m.element() == Element.IRRADIATED).toList();
	}

	/**
	 * How near {@code player} is to the blight (blocks): the Irradiated monolith nearest them in the Overworld, or the
	 * nearest changed wolf loaded, whichever is closer. {@link Double#MAX_VALUE} when there is none. Jaymo's Powers' ticking
	 * charm asks this (by reflection), so keep the signature.
	 */
	public static double nearest(ServerPlayer player) {
		double best = Double.MAX_VALUE;
		if (player.level() instanceof ServerLevel level && level == level.getServer().overworld()) {
			for (Monoliths.Monolith m : sources(level)) {
				best = Math.min(best, Math.sqrt(Monoliths.horizontal(m.pos(), player.blockPosition())));
			}
		}
		for (Mob wolf : PACK) {
			if (wolf.isAlive() && wolf.level() == player.level() && blighted(wolf)) {
				best = Math.min(best, wolf.distanceTo(player));
			}
		}
		return best;
	}

	// ---- cures ----

	/** A healer's hands, a spell of mending or a Potion of Cleansing: the sickness lets go. True if there was any. */
	public static boolean cure(ServerPlayer player) {
		if (player.getAttachedOrElse(DOSE, 0) <= 0) {
			return false;
		}
		player.removeAttached(DOSE);
		TOLD.remove(player.getUUID());
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blight.cured").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
		return true;
	}

	/** Honey or wild garlic: some of the sickness comes off (Jaymo's Powers tells us, by reflection). */
	public static void ease(ServerPlayer player) {
		int dose = player.getAttachedOrElse(DOSE, 0);
		if (dose <= 0) {
			return;
		}
		if (dose <= EASE_BY) {
			cure(player);
			return;
		}
		player.setAttached(DOSE, dose - EASE_BY);
		TOLD.remove(player.getUUID());
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blight.eased").withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC));
	}

	private static void dose(ServerPlayer player, int by) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		player.setAttached(DOSE, Math.min(MAX_DOSE, player.getAttachedOrElse(DOSE, 0) + by));
	}

	// ---- the tick ----

	private static void tick(MinecraftServer server) {
		int ticks = server.getTickCount();
		boolean players = ticks % PLAYER_TICKS == 0, dens = ticks % DEN_TICKS == 0, motes = ticks % MOTE_TICKS == 0;
		if (!players && !dens && !motes) {
			return;
		}
		ServerLevel overworld = server.overworld();
		if (players) {
			List<Monoliths.Monolith> land = sources(overworld);
			Holder<MobEffect> cleansing = BuiltInRegistries.MOB_EFFECT.get(CLEANSING).map(h -> (Holder<MobEffect>)h).orElse(null);
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.isAlive() && !player.isSpectator()) {
					look(player, overworld, land, cleansing, ticks / PLAYER_TICKS);
				}
			}
		}
		if (dens) {
			for (Monoliths.Monolith m : sources(overworld)) {
				den(overworld, m);
			}
		}
		if (motes) {
			for (Mob wolf : PACK) {
				if (wolf.isAlive() && wolf.level() instanceof ServerLevel level && blighted(wolf)) {
					level.sendParticles(Fx.miasma(GLOW, 0.5F), wolf.getX(), wolf.getY() + wolf.getBbHeight() * 0.6, wolf.getZ(), 1,
						wolf.getBbWidth() * 0.4, wolf.getBbHeight() * 0.3, wolf.getBbWidth() * 0.4, 0.0);
				}
			}
		}
	}

	/** One second of a player: the land's dose or the wearing off, then the signs. */
	private static void look(ServerPlayer player, ServerLevel overworld, List<Monoliths.Monolith> land, @Nullable Holder<MobEffect> cleansing, int second) {
		if (cleansing != null && player.hasEffect(cleansing) && cure(player)) {
			return;
		}
		int dose = player.getAttachedOrElse(DOSE, 0);
		int add = 0;
		if (player.level() == overworld) {
			for (Monoliths.Monolith m : land) {
				double d = Monoliths.horizontal(m.pos(), player.blockPosition());
				if (d <= CORE_RADIUS * CORE_RADIUS) {
					add = CORE_DOSE;
					break;
				}
				if (d <= LAND_RADIUS * LAND_RADIUS) {
					add = LAND_DOSE;
				}
			}
		}
		if (add > 0) {
			dose(player, add);
			dose = player.getAttachedOrElse(DOSE, 0);
		} else if (dose > 0 && second % WEAR_EVERY == 0) {
			dose = Math.max(0, dose - WEAR_BY);
			if (dose == 0) {
				player.removeAttached(DOSE);
			} else {
				player.setAttached(DOSE, dose);
			}
		}
		if (dose <= 0) {
			TOLD.remove(player.getUUID());
			return;
		}
		int stage = stage(player);
		if (stage == 0) {
			return;
		}
		Integer told = TOLD.get(player.getUUID());
		if (told == null || told < stage) {
			TOLD.put(player.getUUID(), stage);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.blight.stage." + stage).withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
		} else if (told > stage) {
			TOLD.put(player.getUUID(), stage);
		}
		signs(player, stage);
	}

	/** The body's and the screen's signs of {@code stage}. */
	private static void signs(ServerPlayer player, int stage) {
		if (stage >= 2) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SIGN_TICKS, stage >= 3 ? 1 : 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, SIGN_TICKS, 0, true, false, false));
		}
		if (stage >= 3) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SIGN_TICKS, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SIGN_TICKS + 40, 0, true, false, false));
		}
		if (player.getRandom().nextFloat() < SWIM_CHANCE[stage - 1]) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SWIM_TICKS, 0, true, false, false));
			player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.6F, 0.7F);
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(Fx.miasma(GLOW, 0.5F), player.getX(), player.getEyeY() - 0.3, player.getZ(), stage, 0.25, 0.3, 0.25, 0.0);
		}
	}

	/** The wolves denning at one monolith: another round for each, and the change when it is time. */
	private static void den(ServerLevel level, Monoliths.Monolith m) {
		BlockPos at = m.pos();
		if (!level.isLoaded(at)) {
			return;
		}
		AABB box = new AABB(at).inflate(DEN_RADIUS, DEN_HEIGHT, DEN_RADIUS);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, box, wolf -> (wolf instanceof Wolf || wolf instanceof FeralWolf) && wolf.isAlive())) {
			if (mob.getAttached(BLIGHTED) != null || mob.isBaby() || mob instanceof TamableAnimal tame && tame.isTame() || mob.isLeashed()) {
				continue;
			}
			int rounds = mob.getAttachedOrElse(DENNED, 0) + 1;
			if (rounds >= CHANGE_AFTER) {
				change(level, mob);
			} else {
				mob.setAttached(DENNED, rounds);
			}
		}
	}

	/** The change: tougher, bitier, quicker, and sick-lit. */
	private static void change(ServerLevel level, Mob wolf) {
		wolf.setAttached(BLIGHTED, true);
		wolf.removeAttached(DENNED);
		PACK.add(wolf);
		add(wolf, Attributes.MAX_HEALTH, WOLF_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		add(wolf, Attributes.ATTACK_DAMAGE, WOLF_BITE, AttributeModifier.Operation.ADD_VALUE);
		add(wolf, Attributes.MOVEMENT_SPEED, WOLF_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		wolf.setHealth(wolf.getMaxHealth());
		Vec3 p = wolf.position();
		level.sendParticles(Fx.miasma(GLOW, 0.9F), p.x, p.y + wolf.getBbHeight() * 0.5, p.z, 12, 0.4, 0.4, 0.4, 0.0);
		level.playSound(null, wolf.blockPosition(), SoundEvents.WOLF_SOUNDS.get(net.minecraft.world.entity.animal.wolf.WolfSoundVariants.SoundSet.ANGRY).adultSounds().growlSound().value(), SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	private static void add(Mob wolf, Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = wolf.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(WOLF_MODIFIER, amount, operation));
		}
	}
}
