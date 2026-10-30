package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.313, Andy: "bio-built's lore involves it being created by the mad tinkerer, and they contain DNA from all other
 * heritages except dawn and divine. This allows them to absorb anyone who isnt that race.") Made by the Mad Tinkerer
 * from the blood of every heritage but the Dawnborn and the Divine, they can take in anyone not of those two
 * ({@link #absorbable}: a monster, or a downed or crippled villager, kingdom's folk or player), and the three forms are
 * three different bodies, after Cell (the client's {@code BioForms}): the first hunched, grotesque and tailed, its tail
 * what absorbs (it arcs over the head and down on the foe, swallows it whole, and the bulge flows back down the tail:
 * the client's {@code BioTailEngulf}; so the reach is a tail's, {@link #ABSORB_REACH}); the second big and bulked; the
 * third, Perfect, sleek, the strongest and the steadiest. Each form's size is {@link #SIZE}, and its tag
 * ({@link #FORM_TAG}) tells Jaymo's Powers which Z Fighter strengths it gives.
 * <p>
 * (0.7.262, Andy's NOEA picks) Bio-built, a made being: grown, not born, and hungry to be more. They start frail (the
 * first form), and each downed foe ((0.7.310) or crippled one: a leg broken or cut off) they take into themselves
 * ({@link #absorb}: look at it close by and press the heritage key; (0.7.288) a hand goes to it, pulses run through you
 * both and it shrinks away to nothing, unless you're hurt or press the key again) adds to their worth until the body folds into the next of three forms: the second (Andy's
 * "changed look": plated forearms, a duller skin) and the third (plating to the shoulders and the cheeks, nodes alight).
 * Each form is stronger and tougher ({@link #HEALTH}, {@link #DAMAGE}, {@link #ARMOR}), and every blow they land drains a
 * little life back into them ({@link #DRAIN}). In the third form the heritage key, pressed on no foe, buds up to
 * {@link #BUDS} small copies ({@link #bud}): weak, short-lived allies (risen through {@link Thralls}, so they follow
 * and fight for their maker and crumble when their time is up). Not the Symbiote: no tendrils, no goo; they
 * take only what is already downed or crippled. Flaw: frail at first, and lightning
 * and magic hurt them half again ({@link Heritage#dealt}).
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
	static final double ABSORB_REACH = 4.5;
	/** (0.7.313) The tail holds the foe this far off as it swallows it (blocks). */
	static final double ABSORB_TOUCH = 1.8;
	/** (0.7.313) Each form's size, a share added to their scale: (Andy) the third keeps the second's height. */
	static final double[] SIZE = {0.0, 0.15, 0.15};
	/** (0.7.313) The tag a Bio-built player carries for their form ({@code _0} to {@code _2}), read by Jaymo's Powers. */
	public static final String FORM_TAG = "jaymoskingdoms_bio_form_";
	/**
	 * (0.7.363) The tag for their palette (by variant: 0 the first look, 1 Brassbound, 2 Opaline). With the form's, it
	 * goes with them onto their corpse (Kitchen keeps a dead player's {@code jaymoskingdoms_bio_} tags), so the body keeps
	 * the form they died in.
	 */
	public static final String PALETTE_TAG = "jaymoskingdoms_bio_palette_";
	public static final int ABSORB_TICKS = 100;
	static final int ABSORB_FOOD = 3;
	static final float ABSORB_HEAL_SHARE = 0.5F, ABSORB_HEAL_MAX = 12.0F;
	/** A foe with more max health than this can't be taken in (no dragons). */
	static final float ABSORB_MAX_HEALTH = 150.0F;
	/** A foe's worth: 1, and one more for every this much max health, at most {@link #WORTH_MAX}. */
	static final float WORTH_PER_HEALTH = 20.0F;
	static final int WORTH_MAX = 4;
	/**
	 * (0.7.310) Jaymo's Powers' tags on a foe that can't stand and fight: a broken leg (any, splinted or not), and each
	 * severed leg. Kingdoms never compiles against Powers, so it reads them off the scoreboard tags.
	 */
	static final String CRIPPLED_TAG = "jaymospowers_crippled", NO_LEFT_LEG_TAG = "jaymoskitchen.missing.left_leg",
		NO_RIGHT_LEG_TAG = "jaymoskitchen.missing.right_leg";
	/** Lightning and magic hurt them this many times as much (the flaw). */
	static final float BRITTLE = 1.5F;
	/**
	 * Budding: copies at most, their life (ticks), health and blow, and the wait (ticks). (0.7.310, Andy: "the buds should
	 * use a charge of your absorption") Each bud spends one charge, and every foe absorbed stores one, up to
	 * {@link #CHARGES_MAX}; that is all it costs now (no health, no hunger), so the wait is shorter.
	 */
	static final int BUDS = 2, BUD_LIFE = 20 * 45, BUD_COOLDOWN = 20 * 20, CHARGES_MAX = 4;
	static final float BUD_HEALTH = 6.0F, BUD_DAMAGE = 2.0F;
	/** An amber mote of the made body, and the bud's. */
	private static final DustParticleOptions NODE = new DustParticleOptions(0xE8A23C, 0.8F);

	/** Their form now (0: none set, the first; 1; 2). Saved, kept through death, synced to everyone (the look). */
	public static final AttachmentType<Integer> FORM = AttachmentRegistry.create(JaymosKingdoms.id("bio_form"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** All the worth they have taken in. Saved, kept through death. */
	static final AttachmentType<Integer> WORTH = AttachmentRegistry.create(JaymosKingdoms.id("bio_worth"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** (0.7.310) The absorbed foes stored up to bud with. Saved, lost on death. */
	static final AttachmentType<Integer> CHARGES = AttachmentRegistry.create(JaymosKingdoms.id("bio_charges"),
		builder -> builder.persistent(Codec.INT));

	/** When each may bud again (game time). */
	private static final Map<UUID, Long> BUD_READY = new HashMap<>();

	private BioBuilt() {
	}

	static void register() {
		BioBud.register();
		ServerTickEvents.END_SERVER_TICK.register(BioBuilt::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player
				&& Heritage.is(player, Heritage.Kind.BIOBUILT)) {
				drain(player, entity, taken);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
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
		Skills.modifier(player, Attributes.SCALE, "heritage_bio_scale", bio ? SIZE[form] : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		for (int i = 0; i < FORM_AT.length; i++) {
			if (bio && i == form) {
				player.addTag(FORM_TAG + i);
			} else {
				player.removeTag(FORM_TAG + i);
			}
		}
		HeritageVariants.Variant variant = bio ? HeritageVariants.of(player) : null;
		int palette = variant == HeritageVariants.Variant.BRASSBOUND ? 1 : variant == HeritageVariants.Variant.OPALINE ? 2 : 0;
		for (int i = 0; i < 3; i++) {
			if (bio && i == palette) {
				player.addTag(PALETTE_TAG + i);
			} else {
				player.removeTag(PALETTE_TAG + i);
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!Heritage.is(player, Heritage.Kind.BIOBUILT)) {
				// (a heritage changed mid-absorb lets go)
				if (!Heritage.is(player, Heritage.Kind.RUBBERFOLK) && HeritageHold.channel(player) != null) {
					HeritageHold.stop(player, null);
				}
				continue;
			}
			if (player.level() instanceof ServerLevel level) {
				LivingEntity foe = HeritageHold.step(level, player, BioBuilt::helpless, ABSORB_REACH, ABSORB_TICKS, NODE,
					SoundEvents.AMETHYST_BLOCK_RESONATE, "message.jaymoskingdoms.bio.absorbing", ABSORB_TOUCH);
				if (foe != null) {
					absorb(level, player, foe);
				}
			}
		}
	}

	/**
	 * Whether {@code player} may take {@code foe} in: a foe, or (0.7.313) a person ({@link RubberFolk#person}: a villager,
	 * a kingdom's folk or another player, downed) or one crippled; down or crippled ({@link #helpless}), not too big, and
	 * not of the two bloods they lack ({@link #alien}).
	 */
	static boolean absorbable(ServerPlayer player, LivingEntity foe) {
		return (HeritageHold.foe(player, foe) || person(player, foe)) && helpless(foe) && !alien(foe) && foe.getMaxHealth() <= ABSORB_MAX_HEALTH;
	}

	/** (0.7.313) A villager, a kingdom's folk or another player (not in creative), alive: someone, not something. */
	static boolean person(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive()
			&& (e instanceof ServerPlayer other && !other.isCreative() && !other.isSpectator() || e instanceof Villager || e instanceof Folk);
	}

	/** (0.7.313) Dawnborn or Divine: no blood of theirs went into the Bio-built, so they can't be taken in. */
	static boolean alien(LivingEntity e) {
		Heritage.Kind kind = VillagerHeritage.of(e);
		return kind == Heritage.Kind.DAWNBORN || kind == Heritage.Kind.DIVINE;
	}

	/**
	 * (0.7.310, Andy: "bio android should be able to absorb enemies that are crippled and crawling not just downed")
	 * Whether {@code e} is in no state to get away: downed (lying or crawling), a leg broken or severed, or Crippled by a
	 * hidden blade.
	 */
	static boolean helpless(LivingEntity e) {
		Set<String> tags = e.entityTags();
		return Downed.isDowned(e) || tags.contains(CRIPPLED_TAG) || tags.contains(NO_LEFT_LEG_TAG) || tags.contains(NO_RIGHT_LEG_TAG)
			|| HiddenBlade.crippled(e);
	}

	/**
	 * (0.7.288) The heritage key pressed (a toggle, Andy: "instead of a hold this is a toggle"): absorbing, it lets go; on
	 * a downed or crippled foe close by, it starts taking it in ({@link HeritageHold}); else, in the third form, it buds.
	 */
	static void press(ServerPlayer player) {
		if (!HeritageHold.press(player, e -> absorbable(player, e), ABSORB_REACH, "message.jaymoskingdoms.bio.absorbing")) {
			bud(player);
		}
	}

	/** What a foe adds to their worth: (0.7.313) a person, the most. */
	static int worth(LivingEntity foe) {
		if (foe instanceof net.minecraft.world.entity.player.Player || foe instanceof Villager || foe instanceof Folk) {
			return WORTH_MAX;
		}
		return Mth.clamp(1 + (int)(foe.getMaxHealth() / WORTH_PER_HEALTH), 1, WORTH_MAX);
	}

	/** {@code foe}, downed or crippled, is taken into {@code player}: consumed, a share of its health theirs, its worth added; maybe a new form. */
	private static void absorb(ServerLevel level, ServerPlayer player, LivingEntity foe) {
		float heal = Math.min(ABSORB_HEAL_MAX, foe.getMaxHealth() * ABSORB_HEAL_SHARE);
		int worth = player.getAttachedOrElse(WORTH, 0) + worth(foe);
		level.sendParticles(NODE, foe.getX(), foe.getY(0.5), foe.getZ(), 24, 0.35, 0.5, 0.35, 0.04);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.6), player.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.5F);
		player.heal(heal);
		player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + ABSORB_FOOD));
		player.setAttached(WORTH, worth);
		int charges = Math.min(CHARGES_MAX, player.getAttachedOrElse(CHARGES, 0) + 1);
		player.setAttached(CHARGES, charges);
		take(level, player, foe);
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
			player.sendOverlayMessage(next > 0 ? Component.translatable("message.jaymoskingdoms.bio.absorbed", next)
				: Component.translatable("message.jaymoskingdoms.bio.absorbed_charges", charges, CHARGES_MAX));
		}
	}

	/**
	 * (0.7.313) The foe is gone into them: anything else simply vanishes; a person dies of it (credited to the Bio-built,
	 * so the law and their kin know), leaving no body, as with the Rubber-folk.
	 */
	private static void take(ServerLevel level, ServerPlayer player, LivingEntity foe) {
		if (!person(player, foe)) {
			foe.discard();
			return;
		}
		foe.addTag(RubberFolk.NO_CORPSE_TAG);
		if (!Necromancy.execute(level, foe, player)) {
			foe.setInvulnerableTime(0);
			foe.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
		}
		if (foe.isAlive()) {
			// (something saved them after all (a totem): back to their size, and no body-less death waiting for them later)
			HeritageHold.shrink(foe, 0.0);
			foe.removeTag(RubberFolk.NO_CORPSE_TAG);
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

	/** The heritage key pressed on no downed or crippled foe: in the third form, bud copies. */
	static void bud(ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		LivingEntity sighted = Arcana.sighted(player);
		if (sighted != null && absorbable(player, sighted) && player.distanceTo(sighted) <= ABSORB_REACH) {
			// (the key is taking that foe in, not budding)
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
		int charges = player.getAttachedOrElse(CHARGES, 0);
		if (!player.isCreative()) {
			if (charges <= 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bio.bud_empty").withStyle(ChatFormatting.GRAY));
				return;
			}
			want = Math.min(want, charges);
			player.setAttached(CHARGES, charges - want);
		}
		BUD_READY.put(player.getUUID(), now + BUD_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		for (int i = 0; i < want; i++) {
			Vec3 at = Sorcery.groundNear(level, player);
			// (0.7.310) a small copy of its maker, not a zombie: no rag, nothing in its hands
			Mob copy = Thralls.raise(level, BioBud.TYPE, at, -1, player.getUUID(), true, BUD_LIFE, player.getLastHurtMob());
			if (copy == null) {
				continue;
			}
			copy.setAttached(BioBud.MAKER, player.getUUID());
			copy.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
			copy.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			copy.getAttribute(Attributes.SCALE).setBaseValue(BioBud.SIZE);
			copy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(BUD_HEALTH);
			copy.setHealth(BUD_HEALTH);
			copy.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(BUD_DAMAGE);
			copy.setCustomName(Component.translatable("entity.jaymoskingdoms.bio_bud", player.getDisplayName()));
			level.sendParticles(NODE, at.x, at.y + 0.5, at.z, 16, 0.3, 0.4, 0.3, 0.04);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.7F);
	}
}
