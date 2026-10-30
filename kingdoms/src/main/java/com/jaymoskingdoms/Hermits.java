package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.62) The villager living on a floating island (Moog's Voyager Structures' large floating island puts one there)
 * is a hermit wizard, not a stray: they wear the wizard's robe, hat and staff ({@link CourtWizards}' look), cast as a
 * court wizard does when something comes for them, and have a reason to be up there ({@link #STORY}): a scholar
 * stranded by their own spell, a mage who left the courts for quiet, the warden whose spell keeps the island aloft, or
 * a court wizard in exile. They tell it when a player first comes near each day (aloud over their head), give a
 * visitor a spell scroll once a day for the climb, tell more of it in MCA's chat, and unmake skills (sneak and use
 * them) as any court wizard does. No kingdom claims them: they're never promoted, drafted or counted.
 * (0.7.215) They also love Jaymo's Powers' alchemy and can't stop talking about it ({@link HermitAlchemy}).
 * (0.7.367) They watch the sky, and sell the star charts a pod pilot needs, one at a time ({@link StarFolk}).
 * <p>
 * Which structures make hermits is the structure tag {@code jaymoskingdoms:sky_hermitage}. MCA's villagers only (the
 * pack always has MCA; a plain villager is left as it is).
 */
public final class Hermits {
	/** A hermit's faction: none (a court wizard's is their kingdom's). */
	public static final int FACTION = -2;
	public static final TagKey<Structure> SKY = TagKey.create(Registries.STRUCTURE, JaymosKingdoms.id("sky_hermitage"));
	/** How often a villager with no work is checked for standing on a floating island (ticks). */
	static final int CHECK_TICKS = 1200;
	/** How far a hermit wanders from where they were found. */
	static final int HOME_RADIUS = 10;
	/** Their stories (lang {@code talk.jaymoskingdoms.hermit.<story>.<n>}, {@code dialogue.chat.jaymoskingdoms.hermit_<story>/<n>}). */
	static final String[] STORY = {"scholar", "retreat", "warden", "exile"};
	/** Greetings per story. */
	static final int GREETINGS = 3;
	/** How near (and in sight) a player must come to be greeted; how far the words carry. */
	static final double GREET_RANGE = 10.0, SPEECH_RANGE = 14.0;
	/** A hermit greets (and gifts) each player once in this long (ticks: a day). */
	static final long GREET_TICKS = 24000L;
	/** Falling this far, they catch themselves with a slow-fall spell (blocks); it lasts this long (ticks). */
	static final double FALL_CATCH = 2.5;
	static final int SLOW_FALL_TICKS = 100;
	/** How often chatting with them (MCA's menu) brings their story up instead of small talk. */
	public static final float CHAT_CHANCE = 0.7F;
	/** (0.7.367) How many star charts a hermit has for sale at once, cheapest trip first ({@link StarFolk}). */
	public static final int CHARTS_OFFERED = 1;
	/** A purple for their words. */
	static final int COLOR = 0xC9A0FF;

	/** "hermit/player" → when they last met (game time). */
	private static final Map<String, Long> MET = new HashMap<>();

	private Hermits() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MET.clear());
	}

	public static boolean is(Worker w) {
		return w.role() == Sorcery.WIZARD && w.factionId() == FACTION;
	}

	public static boolean is(@Nullable Entity entity) {
		return entity instanceof Worker w && is(w);
	}

	/** Whether {@code mob} (with no work) is due a look at where it stands, from {@link Workers#RECONCILE_TICKS}' step. */
	static boolean due(PathfinderMob mob) {
		// (a second after they're loaded, then once a minute)
		return !mob.isBaby() && mob.tickCount % CHECK_TICKS == Workers.RECONCILE_TICKS;
	}

	/** {@code pos} (or the block under it) is in a piece of a floating island. */
	static boolean onSkyIsland(ServerLevel level, BlockPos pos) {
		return level.structureManager().getStructureWithPieceAt(pos, SKY).isValid()
			|| level.structureManager().getStructureWithPieceAt(pos.below(), SKY).isValid();
	}

	/** The story {@code mob} tells (the same every time, from who they are). */
	public static String story(Entity mob) {
		return STORY[Math.floorMod(mob.getUUID().hashCode(), STORY.length)];
	}

	/** {@code mob} just became a hermit: their home where they stand, a shimmer. */
	static void settled(ServerLevel level, PathfinderMob mob) {
		mob.setPersistenceRequired();
		level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 1.2, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.6);
		JaymosKingdoms.LOGGER.debug("{} is a hermit on a floating island ({})", mob.getName().getString(), story(mob));
	}

	/** A hermit's own step (from {@link Workers#step}, before the court wizard's fighting): a fall caught, a visitor greeted. */
	static void step(ServerLevel level, Enlisted w) {
		PathfinderMob mob = w.mob();
		if (mob.fallDistance > FALL_CATCH && !mob.hasEffect(MobEffects.SLOW_FALLING)) {
			// (off the edge: a word and they drift down like a feather)
			mob.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0, false, false));
			level.sendParticles(ParticleTypes.ENCHANT, mob.getX(), mob.getY() + 1.0, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.4);
		}
		if (mob.tickCount % 20 != 0 || mob.isSleeping() || McaCompat.busy(mob) || mob.getTarget() != null) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(GREET_RANGE),
			p -> p.isAlive() && !p.isSpectator() && mob.hasLineOfSight(p))) {
			String key = mob.getUUID() + "/" + player.getUUID();
			Long met = MET.get(key);
			if (met != null && now - met < GREET_TICKS) {
				continue;
			}
			MET.put(key, now);
			greet(level, mob, player);
			return;
		}
	}

	/** Their story, aloud, and a scroll for the one who climbed up. */
	private static void greet(ServerLevel level, PathfinderMob mob, ServerPlayer player) {
		mob.getNavigation().stop();
		mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
		String story = story(mob);
		// (0.7.215) someone who has started on alchemy gets an excited word instead of the story
		Component alchemy = HermitAlchemy.greeting(player, level.getRandom());
		Speech.say(level, mob, alchemy != null ? alchemy
			: Component.translatable("talk.jaymoskingdoms.hermit." + story + "." + level.getRandom().nextInt(GREETINGS)), COLOR, SPEECH_RANGE);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, 0.8F, 0.9F);
		ItemStack gift = Arcana.scrollFor(Sorcery.WIZARD, level.getRandom());
		if (!player.getInventory().add(gift)) {
			player.drop(gift, false, Prediction.SERVER_ONLY);
		}
		mob.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hermit.gift", mob.getDisplayName()).withColor(COLOR));
	}
}
