package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.229) Death magic, the eighth Sorcerer element ({@link ForbiddenArts}). It works on the dead and the dying: violet
 * and ash, with the soul's wail.
 * <ul>
 * <li><b>The Death Sorcerer:</b> {@code J} imbues their blade as any Sorcerer's, and every blow it lands <b>blights</b>
 * (no healing at all, {@link #blighted}, until it wears off or healing magic lifts it); a burst withers too. Sneak and
 * {@code J}: looking at the downed, <b>Reap</b> (dead at once, and some of their life comes back to you as mana); at the
 * living, a death bolt; at nothing living but a body lying near (Jaymo's Kitchen's), <b>Raise the Fallen</b>: the
 * corpse gets up as your thrall ({@link Thralls}) a few minutes. A monster rises as itself, a person or a horse as the
 * undead. Withering hurts them half as much ({@link ClassPerks}).</li>
 * <li><b>Spells</b> for scrolls and staffs ({@link Arcana#cast}): <b>Blight</b> (who you look at withers, and won't heal),
 * <b>Whispers</b> (a body tells you how it died, and how long ago), <b>Death's Door</b> (the next time you fall, you get
 * straight back up, a little less alive till you next sleep).</li>
 * </ul>
 * Healing magic is its answer: every Restoration heal lifts a blight first ({@link PowersCompat#mend}).
 */
public final class Necromancy {
	// ---- the Sorcerer's ----
	/** How long a death blade's blow blights (ticks); a burst's or a bolt's; its wither (ticks) and the burst's damage. */
	static final int BLADE_BLIGHT = 60, BURST_BLIGHT = 200, BURST_WITHER = 60;
	static final float BURST_DAMAGE = 2.0F;
	/** Reap: how far, the mana it gives back, its cost. */
	static final double REAP_REACH = 6.0;
	static final float REAP_MANA = 25.0F;
	/** Raise the Fallen: how far a body may lie, how long a thrall stands (ticks), the most at once, its cooldown (ticks). */
	static final double RAISE_REACH = 8.0;
	static final int RAISE_LIFE = 20 * 180, MAX_THRALLS = 3, RAISE_COOLDOWN = 20 * 20;

	// ---- the spells (times a caster's power) ----
	/** Blight: how long (ticks), its wither's strength. */
	static final int BLIGHT_TICKS = 200, BLIGHT_WITHER = 1;
	/** Whispers: how far the body may lie; how many deaths are remembered for it, and how long (ticks). */
	static final double WHISPER_REACH = 6.0;
	static final int DEATHS_KEPT = 512;
	/** Death's Door: how long the ward lasts (ticks), the share of health you get up with, the health it takes till you sleep. */
	static final int DOOR_TICKS = 20 * 60 * 5;
	static final float DOOR_SHARE = 0.25F;
	static final double DOOR_TOLL = -4.0;

	static final int VIOLET = 0x5B2A86, ASH = 0x3A3A3A;
	static final DustParticleOptions VIOLET_MOTE = new DustParticleOptions(VIOLET, 1.0F), ASH_MOTE = new DustParticleOptions(ASH, 1.2F);
	static final net.minecraft.resources.Identifier DOOR_TOLL_ID = JaymosKingdoms.id("deaths_door");

	/** Blighted bodies: until when (game time). */
	private static final Map<LivingEntity, Long> BLIGHTED = new WeakHashMap<>();
	/** Death's Door warded: until when, by player. */
	private static final Map<UUID, Long> DOOR = new HashMap<>();
	private static final Map<UUID, Long> RAISE_READY = new HashMap<>();

	/** A death remembered for Whispers: what died, when, and how. */
	private record Death(EntityType<?> type, long at, Component how) {
	}

	private static final Deque<Death> DEATHS = new ArrayDeque<>();

	private Necromancy() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Necromancy::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BLIGHTED.clear();
			DOOR.clear();
			RAISE_READY.clear();
			DEATHS.clear();
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && !(entity instanceof Player)) {
				DEATHS.addLast(new Death(entity.getType(), level.getGameTime(), entity.getCombatTracker().getDeathMessage()));
				while (DEATHS.size() > DEATHS_KEPT) {
					DEATHS.removeFirst();
				}
			}
			BLIGHTED.remove(entity);
		});
		// Death's Door, without Jaymo's Powers' downing: the killing blow leaves you standing
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && warded(player)) {
				openDoor(player);
				return false;
			}
			return true;
		});
		// the toll is paid with a night's sleep
		EntitySleepEvents.STOP_SLEEPING.register((entity, bed) -> {
			var health = entity.getAttribute(Attributes.MAX_HEALTH);
			if (health != null && health.removeModifier(DOOR_TOLL_ID)) {
				if (entity instanceof ServerPlayer player) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.death.door_healed").withStyle(ChatFormatting.GRAY));
				}
			}
		});
	}

	// ---- who ---------------------------------------------------------------------------------------------------------

	/** A Death Sorcerer. */
	static boolean necromancer(Player player) {
		return Classes.is(player, PlayerClass.SORCERER) && "death".equals(Classes.of(player).option());
	}

	// ---- the blight --------------------------------------------------------------------------------------------------

	/** Whether {@code entity} is blighted now: no healing reaches it ({@code LivingEntityHealMixin}). */
	public static boolean blighted(LivingEntity entity) {
		Long until = BLIGHTED.get(entity);
		return until != null && until > entity.level().getGameTime();
	}

	static void blight(LivingEntity entity, int ticks) {
		if (!entity.isAlive() || Restoration.undead(entity)) {
			return;
		}
		long until = entity.level().getGameTime() + ticks;
		BLIGHTED.merge(entity, until, Math::max);
	}

	/** Healing magic lifts a blight (a puff of ash). */
	static void cleanse(LivingEntity entity) {
		if (BLIGHTED.remove(entity) != null && entity.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.ASH, entity.getX(), entity.getY(0.6), entity.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
		}
	}

	/** A death blade's blow: a short blight. */
	static void bladeBlow(LivingEntity target) {
		blight(target, BLADE_BLIGHT);
	}

	/** A death burst on {@code target} (a blade's, or a thrown bolt's): a long blight, a wither and a bite of it now. */
	static void burst(ServerLevel level, LivingEntity by, LivingEntity target) {
		blight(target, BURST_BLIGHT);
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, BURST_WITHER, 0), by);
		target.hurtServer(level, level.damageSources().indirectMagic(by, by), BURST_DAMAGE);
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.6), target.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
	}

	// ---- the Sorcerer's sneak and J ------------------------------------------------------------------------------------

	/** Sneak and {@code J}: Reap the downed, else a death bolt at the living (false: let {@link Spellcraft} throw it), else raise a body. */
	static boolean key(ServerPlayer player, ServerLevel level) {
		LivingEntity target = Arcana.sighted(player);
		if (target != null && Downed.isDowned(target) && player.distanceTo(target) <= REAP_REACH) {
			reap(player, level, target);
			return true;
		}
		if (target != null) {
			return false;
		}
		Entity corpse = corpseInSight(player, level, RAISE_REACH);
		if (corpse != null) {
			raise(player, level, corpse);
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
		return true;
	}

	private static void reap(ServerPlayer player, ServerLevel level, LivingEntity target) {
		ItemStack key = Arcana.BONE_STAFF.getDefaultInstance();
		if (player.getCooldowns().isOnCooldown(key) || !Mana.spend(player, Mana.cost("reap"))) {
			return;
		}
		player.getCooldowns().addCooldown(key, Spellcraft.BOLT_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Sorcery.beam(level, player, target.getBoundingBox().getCenter(), VIOLET_MOTE);
		if (!execute(level, target, player)) {
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), Float.MAX_VALUE);
		}
		// the life it had left comes to you
		Mana.set(player, Math.min(Mana.max(player), Mana.current(player) + REAP_MANA));
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.5), target.getZ(), 16, 0.3, 0.4, 0.3, 0.04);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.8F);
		ForbiddenArts.cast(player, level, false, 0);
	}

	private static java.lang.invoke.@Nullable MethodHandle execute;
	private static boolean lookedForExecute;

	/** Jaymo's Powers' {@code Downed.execute}: the downed die as a finisher kills them. False without Powers. */
	private static boolean execute(ServerLevel level, LivingEntity target, Player by) {
		if (!lookedForExecute) {
			lookedForExecute = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					execute = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.core.wounds.Downed"), "execute",
						java.lang.invoke.MethodType.methodType(void.class, ServerLevel.class, LivingEntity.class, Player.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.debug("Jaymo's Powers has no Downed.execute", e);
				}
			}
		}
		if (execute == null) {
			return false;
		}
		try {
			execute.invoke(level, target, by);
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	/** The body (Jaymo's Kitchen's, not a player's) nearest where {@code player} looks, within {@code reach}. */
	static @Nullable Entity corpseInSight(Player player, ServerLevel level, double reach) {
		if (!KitchenCompat.loaded()) {
			return null;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		return level.getEntities(player, player.getBoundingBox().inflate(reach), KitchenCompat::isRaisableCorpse).stream()
			.filter(c -> {
				Vec3 to = c.getBoundingBox().getCenter().subtract(eye);
				return to.length() <= reach && to.normalize().dot(look) > 0.85;
			})
			.min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	/** Raise the Fallen: the body gets up as {@code player}'s thrall. */
	private static void raise(ServerPlayer player, ServerLevel level, Entity corpse) {
		long now = level.getGameTime();
		if (RAISE_READY.getOrDefault(player.getUUID(), 0L) > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.restoration.cooldown",
				Component.translatable("message.jaymoskingdoms.death.raise"), (RAISE_READY.get(player.getUUID()) - now + 19) / 20));
			return;
		}
		if (Thralls.count(player.getUUID()) >= MAX_THRALLS) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.death.too_many", MAX_THRALLS));
			return;
		}
		EntityType<? extends Mob> type = risesAs(KitchenCompat.sourceType(corpse), Climate.at(level, corpse.blockPosition()));
		if (type == null) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.death.wont_rise"));
			return;
		}
		if (!Mana.spend(player, Mana.cost("raise"))) {
			return;
		}
		if (Thralls.raise(level, type, corpse.position(), -1, player.getUUID(), true, RAISE_LIFE, null) == null) {
			return;
		}
		Sorcery.beam(level, player, corpse.position().add(0.0, 0.3, 0.0), VIOLET_MOTE);
		corpse.discard();
		RAISE_READY.put(player.getUUID(), now + RAISE_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0F, 0.7F);
		ForbiddenArts.cast(player, level, false, ForbiddenArts.RAISING_CORRUPTION);
	}

	/**
	 * What a body of {@code type} rises as for a Death Sorcerer: a people's dead and horses as the necromancer's own undead
	 * ({@link Sorcery#risenAs}); any other of the game's monsters and beasts as itself; bosses and golems never.
	 */
	@SuppressWarnings("unchecked")
	static @Nullable EntityType<? extends Mob> risesAs(@Nullable EntityType<?> type, Climate climate) {
		if (type == null || type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN
			|| type == EntityTypes.ELDER_GUARDIAN || type == EntityTypes.IRON_GOLEM || type == EntityTypes.SNOW_GOLEM) {
			return null;
		}
		EntityType<? extends Mob> undead = Sorcery.risenAs(type, climate);
		if (undead != null) {
			return undead;
		}
		MobCategory category = type.getCategory();
		boolean vanilla = "minecraft".equals(type.builtInRegistryHolder().key().identifier().getNamespace());
		return vanilla && (category == MobCategory.MONSTER || category == MobCategory.CREATURE) ? (EntityType<? extends Mob>)type : null;
	}

	// ---- the spells ----------------------------------------------------------------------------------------------------

	/** Blight: who you look at withers, and won't heal. */
	static boolean blightSpell(ServerLevel level, Player player, float power) {
		LivingEntity target = Arcana.sighted(player);
		if (target == null) {
			return false;
		}
		Sorcery.beam(level, player, target.getBoundingBox().getCenter(), VIOLET_MOTE);
		blight(target, (int) (BLIGHT_TICKS * power));
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, (int) (BLIGHT_TICKS * power), BLIGHT_WITHER), player);
		level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.4, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6F, 0.7F);
		return true;
	}

	/** Whispers: the body you look at tells how it died, and how long ago. */
	static boolean whispers(ServerLevel level, Player player) {
		Entity corpse = corpseInSight(player, level, WHISPER_REACH);
		if (corpse == null) {
			return false;
		}
		EntityType<?> type = KitchenCompat.sourceType(corpse);
		long since = KitchenCompat.sinceDeath(corpse);
		long died = level.getGameTime() - since;
		Death found = null;
		if (type != null && since != Long.MAX_VALUE) {
			for (Death d : DEATHS) {
				if (d.type() == type && Math.abs(d.at() - died) <= 5) {
					found = d;
				}
			}
		}
		level.sendParticles(ParticleTypes.SOUL, corpse.getX(), corpse.getY() + 0.4, corpse.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		if (found == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.death.whispers_silent").withStyle(ChatFormatting.DARK_PURPLE));
		} else {
			long minutes = Math.max(1L, since / 1200L);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.death.whispers", found.how(), minutes)
				.withStyle(ChatFormatting.DARK_PURPLE));
		}
		return true;
	}

	/** Death's Door: a ward for a while; the next time you fall, you get straight back up. */
	static boolean deathsDoor(ServerLevel level, Player player, float power) {
		if (!(player instanceof ServerPlayer sp)) {
			return false;
		}
		DOOR.put(sp.getUUID(), level.getGameTime() + (long) (DOOR_TICKS * power));
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 1.0F, 0.6F);
		sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.death.door_open").withStyle(ChatFormatting.DARK_PURPLE));
		return true;
	}

	static boolean warded(ServerPlayer player) {
		Long until = DOOR.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	/** The door opens: up again with a share of your health, and less of you till you sleep. */
	private static void openDoor(ServerPlayer player) {
		DOOR.remove(player.getUUID());
		var health = player.getAttribute(Attributes.MAX_HEALTH);
		if (health != null && !health.hasModifier(DOOR_TOLL_ID)) {
			health.addPermanentModifier(new AttributeModifier(DOOR_TOLL_ID, DOOR_TOLL, AttributeModifier.Operation.ADD_VALUE));
		}
		if (!PowersCompat.rekindle(player, DOOR_SHARE)) {
			player.setHealth(Math.max(1.0F, player.getMaxHealth() * DOOR_SHARE));
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.5, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 0.5F);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.death.door_used").withStyle(ChatFormatting.DARK_PURPLE));
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void serverTick(MinecraftServer server) {
		long tick = server.getTickCount();
		// Death's Door, with Jaymo's Powers: the downed get straight back up
		if (!DOOR.isEmpty() && tick % 5 == 0) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (warded(player) && Downed.isDowned(player)) {
					openDoor(player);
				}
			}
			DOOR.values().removeIf(until -> until < Factions.level(server).getGameTime());
		}
		// the blighted give off ash
		if (!BLIGHTED.isEmpty() && tick % 10 == 0) {
			for (Map.Entry<LivingEntity, Long> e : new ArrayList<>(BLIGHTED.entrySet())) {
				LivingEntity body = e.getKey();
				if (!body.isAlive() || e.getValue() <= body.level().getGameTime()) {
					BLIGHTED.remove(body);
				} else if (body.level() instanceof ServerLevel level) {
					level.sendParticles(ASH_MOTE, body.getX(), body.getY(0.6), body.getZ(), 2, 0.25, 0.35, 0.25, 0.0);
				}
			}
		}
	}
}
