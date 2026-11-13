package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Coming back in another body (Andy's pick E5). A player who is dead for good (they waited in Limbo, Jaymo's Powers' black box)
 * can be born again if, within ten days, their body is brought to a grove ({@link Groves#isGrove}) and the rite is done over
 * it: by an old druid who keeps the grove (twenty seconds' chanting, unasked) or by anyone with the rite's scroll. The body is
 * a body of the Kitchen's lying free, or a grave ({@link Graves}); keep it fresh ({@link BodyKeeping}) or it gets up before
 * the rite is done. The dead come back in <b>a new blood</b>, drawn at random from the bloods anyone may be born to (never the
 * old one, never one that has to be earned, never a brood's), and wake at the druid's feet, weak and cold. They keep their name,
 * their knowledge, their map, their standing, their class and skills, and what they carried; the old body's gifts are not
 * theirs now (a vampire's thirst, a wolf's curse, the powers of the old life: gone).
 */
public final class Rebirths {
	// ---- tuning ----
	/** The rite works within this many days of the death. */
	static final int DAYS = 10;
	/** The druid chants this long (ticks); sees a body this near (blocks). The scroll's reach for a body (blocks). */
	static final int CHANT = 20 * 20;
	static final double DRUID_REACH = 8.0, SPELL_REACH = 6.0;
	static final int COLOUR = 0x8FD36B;

	private static final Map<UUID, Integer> CHANTING = new HashMap<>();

	/** The body laid out for the rite: a body lying free, or a grave. */
	record Body(@Nullable Entity corpse, @Nullable BlockPos grave) {
	}

	private Rebirths() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Rebirths::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CHANTING.clear());
	}

	private static long today(ServerLevel level) {
		return level.getOverworldClockTime() / 24000L;
	}

	/** The body of {@code owner} within {@code range} of {@code at}, or null. */
	static @Nullable Body bodyOf(ServerLevel level, ServerPlayer owner, Vec3 at, double range) {
		for (Entity e : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(at, at).inflate(range), DeadBodies::isPlayerBody)) {
			if (DeadBodies.owner(e).map(owner.getUUID()::equals).orElse(false)) {
				return new Body(e, null);
			}
		}
		BlockPos grave = Graves.graveOf(level, owner.getUUID(), at, range);
		return grave == null ? null : new Body(null, grave);
	}

	/** The one waiting in Limbo whose body lies near {@code at} and who is still within the days; null if none. */
	private static @Nullable ServerPlayer someoneFor(ServerLevel level, Vec3 at, double range) {
		for (ServerPlayer w : DeathCompat.waitingIn(level)) {
			long died = DeathCompat.diedOn(w);
			if (died >= 0 && today(level) - died <= DAYS && bodyOf(level, w, at, range) != null) {
				return w;
			}
		}
		return null;
	}

	/** The rite of rebirth, read at a grove: false if this is no grove or no one's body lies near. */
	static boolean cast(ServerLevel level, Player caster) {
		if (!(caster instanceof ServerPlayer reader) || !Groves.isGrove(level, caster.blockPosition())) {
			caster.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rebirth.no_grove"));
			return false;
		}
		ServerPlayer who = someoneFor(level, caster.position(), SPELL_REACH);
		if (who == null) {
			return false;
		}
		return perform(level, who, caster.position(), reader.getDisplayName());
	}

	/** The old druids, watching: a body at their feet, the rite begins; when the chant is done, it is done. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (DeathCompat.waitingIn(level).isEmpty()) {
				continue;
			}
			for (Villager druid : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Villager.class),
				v -> v.isAlive() && v.entityTags().contains(Groves.DRUID_TAG))) {
				ServerPlayer who = someoneFor(level, druid.position(), DRUID_REACH);
				if (who == null || !Groves.isGrove(level, druid.blockPosition())) {
					CHANTING.remove(druid.getUUID());
					continue;
				}
				int done = CHANTING.merge(druid.getUUID(), 20, Integer::sum);
				Vec3 at = druid.position();
				if (done == 20) {
					Speech.say(level, druid, Component.translatable("speech.jaymoskingdoms.druid.begins"), COLOUR, 16.0);
					Fx.sigil(level, at.add(0.0, 0.05, 0.0), 3.0F, COLOUR, CHANT + 20);
					level.playSound(null, druid.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.NEUTRAL, 0.6F, 1.4F);
				}
				level.sendParticles(Fx.rune(COLOUR, 0.9F), at.x, at.y + 0.3 + done * 0.003, at.z, 3, 1.0, 0.1, 1.0, 0.0);
				if (done >= CHANT) {
					CHANTING.remove(druid.getUUID());
					perform(level, who, at, druid.getDisplayName());
				}
			}
		}
	}

	/** Gives {@code who} a new body at {@code at}: the old body is used up, the new blood is drawn, the old gifts go. */
	private static boolean perform(ServerLevel level, ServerPlayer who, Vec3 at, Component by) {
		Body body = bodyOf(level, who, at, Math.max(DRUID_REACH, SPELL_REACH) + 2.0);
		if (body == null) {
			return false;
		}
		Entity corpse = body.corpse != null ? body.corpse : Graves.exhume(level, body.grave);
		List<ItemStack> hotbar = corpse == null ? List.of() : DeadBodies.takeHotbar(corpse);
		if (!DeathCompat.reborn(who, level, at.add(0.0, 0.1, 0.0))) {
			return false;
		}
		if (corpse != null) {
			corpse.discard();
		}
		Heritage.Kind was = Heritage.of(who);
		Heritage.Kind now = newBlood(who, was);
		shed(who);
		Heritage.set(who, now, true);
		HeritageVariants.choose(who, now, who.getRandom().nextInt(Math.max(1, HeritageVariants.of(now).size())));
		for (ItemStack stack : new ArrayList<>(hotbar)) {
			if (!who.getInventory().add(stack)) {
				who.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		Fx.blast(level, at.add(0.0, 0.5, 0.0), 3.0F, COLOUR);
		Fx.pool(level, at, 3.0F, COLOUR, 60);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 0.6F);
		who.sendSystemMessage(Component.translatable("message.jaymoskingdoms.rebirth.new_blood", Component.translatable("heritage.jaymoskingdoms." + now.key()))
			.withStyle(ChatFormatting.GREEN));
		JaymosKingdoms.LOGGER.info("{} was born again as {} (was {}), by {}", who.getScoreboardName(), now, was, by.getString());
		return true;
	}

	/** A blood drawn at random: one anyone may be born to, not earned, not a brood's, and not the one they had. */
	static Heritage.Kind newBlood(ServerPlayer who, Heritage.@Nullable Kind was) {
		List<Heritage.Kind> pool = new ArrayList<>();
		for (Heritage.Kind k : Heritage.Kind.playable()) {
			if (!k.earned && !k.brood && k != was) {
				pool.add(k);
			}
		}
		return pool.isEmpty() ? Heritage.Kind.HEARTLANDER : pool.get(who.getRandom().nextInt(pool.size()));
	}

	/** What the old body gave and the new does not: a vampire's thirst, a wolf's curse, the moon's mark, earned bloods, the look. */
	private static void shed(ServerPlayer who) {
		who.removeAttached(Vampires.VAMPIRE);
		who.removeAttached(Vampires.BITTEN);
		who.removeAttached(Vampires.ELDER);
		who.removeAttached(Lycans.CURSE);
		who.removeAttached(Lycans.FORCED);
		who.removeAttached(MoonCurse.CURSED);
		who.removeAttached(Heritage.UNLOCKED);
		who.removeAttached(HeritageVariants.VARIANT);
		who.removeAttached(VampireMask.MASK);
		who.removeAttached(Hemomancy.RESERVOIR);
	}
}
