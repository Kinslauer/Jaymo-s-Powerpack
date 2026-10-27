package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.262, Andy's NOEA picks) Bio-built, a made being: grown, not born, and hungry to be more. They start frail (the
 * first form), and each downed foe they take into themselves ({@link #absorb}: look at it, hold the heritage key close
 * by, and it is consumed) adds to their worth until the body folds into the next of three forms: the second (Andy's
 * "changed look": plated forearms, a duller skin) and the third (plating to the shoulders and the cheeks, nodes alight).
 * Each form is stronger and tougher ({@link #HEALTH}, {@link #DAMAGE}, {@link #ARMOR}), and every blow they land drains a
 * little life back into them ({@link #DRAIN}). In the third form the heritage key, pressed on no foe, buds up to
 * {@link #BUDS} small copies ({@link #bud}): weak, short-lived allies (risen through {@link Thralls}, so they follow
 * and fight for their maker and crumble when their time is up). Not the Symbiote: nothing grabs, no tendrils, no goo;
 * they take only what is already downed and only by standing over it. Flaw: frail at first, and lightning and magic
 * hurt them half again ({@link Heritage#dealt}).
 */
public final class BioBuilt {
	/** The worth (absorbed) that each form begins at: the first at 0. */
	static final int[] FORM_AT = {0, 4, 12};
	/** Each form's max health change (half-hearts), blow share, armour, and knockback resistance. */
	static final double[] HEALTH = {-4.0, 2.0, 8.0}, DAMAGE = {0.0, 0.10, 0.22}, ARMOR = {0.0, 1.0, 3.0}, KNOCK = {0.0, 0.0, 0.2};
	/** The share of each blow they take back as life, by form. */
	static final float[] DRAIN = {0.08F, 0.12F, 0.18F};
	/** The most one blow drains. */
	static final float DRAIN_MAX = 3.0F;
	/** Absorbing: how far the foe may be, how long the hold takes (ticks), the share of its health they take back (at most), and food. */
	static final double ABSORB_REACH = 3.0;
	static final int ABSORB_TICKS = 60, ABSORB_FOOD = 3;
	static final float ABSORB_HEAL_SHARE = 0.5F, ABSORB_HEAL_MAX = 12.0F;
	/** A foe with more max health than this can't be taken in (no dragons). */
	static final float ABSORB_MAX_HEALTH = 150.0F;
	/** A foe's worth: 1, and one more for every this much max health, at most {@link #WORTH_MAX}. */
	static final float WORTH_PER_HEALTH = 20.0F;
	static final int WORTH_MAX = 4;
	/** Lightning and magic hurt them this many times as much (the flaw). */
	static final float BRITTLE = 1.5F;
	/** Budding: copies at most, their life (ticks), health and blow, what it costs the maker (health, hunger), and the wait (ticks). */
	static final int BUDS = 2, BUD_LIFE = 20 * 45, BUD_HUNGER = 4, BUD_COOLDOWN = 20 * 60;
	static final float BUD_HEALTH = 6.0F, BUD_DAMAGE = 2.0F, BUD_COST = 3.0F;
	/** An amber mote of the made body, and the bud's. */
	private static final DustParticleOptions NODE = new DustParticleOptions(0xE8A23C, 0.8F);

	/** Their form now (0: none set, the first; 1; 2). Saved, kept through death, synced to everyone (the look). */
	public static final AttachmentType<Integer> FORM = AttachmentRegistry.create(JaymosKingdoms.id("bio_form"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** All the worth they have taken in. Saved, kept through death. */
	static final AttachmentType<Integer> WORTH = AttachmentRegistry.create(JaymosKingdoms.id("bio_worth"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private static final Map<UUID, HeritageHold.Channel> CHANNELS = new HashMap<>();
	/** When each may bud again (game time). */
	private static final Map<UUID, Long> BUD_READY = new HashMap<>();

	private BioBuilt() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BioBuilt::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player
				&& Heritage.is(player, Heritage.Kind.BIOBUILT)) {
				drain(player, entity, taken);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			CHANNELS.remove(handler.player.getUUID());
			BUD_READY.remove(handler.player.getUUID());
		});
	}

	/** {@code entity}'s form, 0 to 2. */
	public static int form(net.minecraft.world.entity.Entity entity) {
		return Mth.clamp(entity.getAttachedOrElse(FORM, 0), 0, FORM_AT.length - 1);
	}

	/** The form that {@code worth} has earned. */
	static int formFor(int worth) {
		int form = 0;
		for (int i = 1; i < FORM_AT.length; i++) {
			if (worth >= FORM_AT[i]) {
				form = i;
			}
		}
		return form;
	}

	/** Puts their form's strengths on {@code player}, and takes them off anyone who isn't Bio-built. */
	static void attributes(ServerPlayer player, Heritage.Kind kind) {
		boolean bio = kind == Heritage.Kind.BIOBUILT;
		int form = bio ? formFor(player.getAttachedOrElse(WORTH, 0)) : 0;
		if (bio && player.getAttachedOrElse(FORM, -1) != form) {
			player.setAttached(FORM, form);
		} else if (!bio && player.hasAttached(FORM)) {
			player.removeAttached(FORM);
		}
		Skills.modifier(player, Attributes.MAX_HEALTH, "heritage_bio_health", bio ? HEALTH[form] : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_bio_damage", bio ? DAMAGE[form] : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.ARMOR, "heritage_bio_armor", bio ? ARMOR[form] : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, "heritage_bio_knock", bio ? KNOCK[form] : 0.0, AttributeModifier.Operation.ADD_VALUE);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Heritage.is(player, Heritage.Kind.BIOBUILT)) {
				if (!CHANNELS.isEmpty()) {
					CHANNELS.remove(player.getUUID());
				}
				continue;
			}
			LivingEntity foe = HeritageHold.advance(CHANNELS, player, e -> HeritageHold.foe(player, e) && Downed.isDowned(e) && e.getMaxHealth() <= ABSORB_MAX_HEALTH,
				ABSORB_REACH, ABSORB_TICKS, NODE, "message.jaymoskingdoms.bio.absorbing");
			if (foe != null && player.level() instanceof ServerLevel level) {
				absorb(level, player, foe);
			}
		}
	}

	/** What a foe adds to their worth. */
	static int worth(LivingEntity foe) {
		return Mth.clamp(1 + (int)(foe.getMaxHealth() / WORTH_PER_HEALTH), 1, WORTH_MAX);
	}

	/** {@code foe}, downed, is taken into {@code player}: consumed, a share of its health theirs, its worth added; maybe a new form. */
	private static void absorb(ServerLevel level, ServerPlayer player, LivingEntity foe) {
		float heal = Math.min(ABSORB_HEAL_MAX, foe.getMaxHealth() * ABSORB_HEAL_SHARE);
		int worth = player.getAttachedOrElse(WORTH, 0) + worth(foe);
		level.sendParticles(NODE, foe.getX(), foe.getY(0.5), foe.getZ(), 24, 0.35, 0.5, 0.35, 0.04);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.6), player.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		foe.discard();
		player.heal(heal);
		player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + ABSORB_FOOD));
		player.setAttached(WORTH, worth);
		int was = form(player);
		int now = formFor(worth);
		if (now > was) {
			player.setAttached(FORM, now);
			Skills.refresh(player);
			player.heal(player.getMaxHealth());
			level.sendParticles(NODE, player.getX(), player.getY(0.5), player.getZ(), 60, 0.5, 0.8, 0.5, 0.08);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2F, 0.6F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bio.form." + now).withStyle(ChatFormatting.GOLD));
		} else {
			int next = now + 1 < FORM_AT.length ? FORM_AT[now + 1] - worth : 0;
			player.sendOverlayMessage(Component.translatable(next > 0 ? "message.jaymoskingdoms.bio.absorbed" : "message.jaymoskingdoms.bio.absorbed_last", next));
		}
	}

	/** A blow of {@code taken} on {@code target} from {@code player}: a share of it back as life. */
	private static void drain(ServerPlayer player, LivingEntity target, float taken) {
		float back = Math.min(DRAIN_MAX, taken * DRAIN[form(player)]);
		if (back <= 0.0F || player.getHealth() >= player.getMaxHealth() || Downed.isDowned(player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		player.heal(back);
		HeritageHold.pull(level, target, player, NODE, 0.0);
	}

	/** The heritage key pressed: in the third form, on no downed foe, bud copies. */
	static void bud(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		LivingEntity sighted = Arcana.sighted(player);
		if (sighted != null && HeritageHold.foe(player, sighted) && Downed.isDowned(sighted) && player.distanceTo(sighted) <= ABSORB_REACH) {
			// (the hold is taking that foe in, not budding)
			return;
		}
		if (form(player) < FORM_AT.length - 1) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bio.bud_locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		long now = level.getGameTime();
		Long ready = BUD_READY.get(player.getUUID());
		if (ready != null && ready > now && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bio.bud_wait", (ready - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		int want = BUDS - Thralls.count(player.getUUID());
		if (want <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bio.bud_full").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!player.isCreative() && (player.getHealth() <= BUD_COST + 2.0F || player.getFoodData().getFoodLevel() < BUD_HUNGER)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bio.bud_weak").withStyle(ChatFormatting.GRAY));
			return;
		}
		BUD_READY.put(player.getUUID(), now + BUD_COOLDOWN);
		if (!player.isCreative()) {
			player.hurtServer(level, level.damageSources().generic(), BUD_COST);
			player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() - BUD_HUNGER);
		}
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		for (int i = 0; i < want; i++) {
			Vec3 at = Sorcery.groundNear(level, player);
			Mob copy = Thralls.raise(level, EntityTypes.ZOMBIE, at, -1, player.getUUID(), true, BUD_LIFE, player.getLastHurtMob());
			if (copy == null) {
				continue;
			}
			if (copy instanceof Zombie zombie) {
				zombie.setBaby(true);
			}
			copy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BUD_HEALTH);
			copy.setHealth(BUD_HEALTH);
			copy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BUD_DAMAGE);
			copy.setCustomName(Component.translatable("entity.jaymoskingdoms.bio_bud", player.getDisplayName()));
			level.sendParticles(NODE, at.x, at.y + 0.5, at.z, 16, 0.3, 0.4, 0.3, 0.04);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
	}
}
