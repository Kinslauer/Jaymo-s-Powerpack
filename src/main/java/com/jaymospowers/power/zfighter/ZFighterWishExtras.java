package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (0.17.60, Andy's NOEA picks) Five more wishes for the Eternal Dragon ({@link ZFighterWishes}); each is refused (the dragon
 * waits for another) when it could change nothing:
 * <ul>
 * <li><b>Remake</b> (re-make character): with Jaymo's Kingdoms the character is unmade the way {@code /kingdom story reset} does
 * it (story, class and heritage are asked again, so the creator opens); a Z Fighter's lineage goes with it and is taken from
 * the new heritage. Without Kingdoms: the look is redone (aura style, ki weapon shape, a new ki colour).</li>
 * <li><b>Racial</b> (reset racial skill): a Z Fighter's lineage extras start over: the Earthling's adrenaline cool-down, the
 * Frost-born's ki reserve, the Warrior-born's comebacks (and a cut tail and a crippled ape grow whole).</li>
 * <li><b>Restat</b> (move stat points): every stat level bought with TP is refunded, in TP at what each cost, and the
 * stats fall to the levels training raised ({@link ZFighterGrowth}); spend them again in the shop.</li>
 * <li><b>Difficulty</b>: a personal dial, stepped each time: normal, harder, brutal, easier, normal... It scales the
 * damage <em>you</em> take ({@link #TAKEN}): Kingdoms' world tiers are earned by feats, not wished for, so this is our own.</li>
 * <li><b>Android undone</b>: the android rebuild is taken back ({@link ZFighterAndroid#undo}).</li>
 * </ul>
 */
final class ZFighterWishExtras {
	/** Difficulty steps (index 0-3): normal, harder, brutal, easier; the damage you take times each. */
	static final float[] TAKEN = {1.0F, 1.35F, 1.75F, 0.75F};
	static final String[] STEPS = {"normal", "harder", "brutal", "easier"};

	/** The difficulty step chosen with the wish (saved, kept through death). */
	public static final AttachmentType<Integer> DIFFICULTY = AttachmentRegistry.create(JaymosPowers.id("zfighter_difficulty"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	private ZFighterWishExtras() {
	}

	private static void say(ServerPlayer player, String key, Object... args) {
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_" + key, args).withStyle(ChatFormatting.GOLD));
	}

	private static void cannot(ServerPlayer player) {
		player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_wish_cannot").withStyle(ChatFormatting.GRAY));
	}

	/** Re-make character. */
	static boolean remake(ServerPlayer player, boolean zfighter) {
		MinecraftServer server = player.level().getServer();
		boolean kingdoms = FabricLoader.getInstance().isModLoaded("jaymoskingdoms");
		if (kingdoms) {
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "kingdom story reset " + player.getStringUUID());
		}
		if (zfighter) {
			// the lineage goes back to the blood the new heritage gives (the power picks it up by itself)
			ZFighterLineage.endApe(player, ZFighterPower.live(player));
			player.removeAttached(ZFighterLineage.LINEAGE);
			player.removeAttached(ZFighterLineage.TAIL_WRAPPED);
			player.removeAttached(ZFighterAura.STYLE);
			player.removeAttached(ZFighterKiWeapon.SHAPE);
			if (!kingdoms) {
				ZFighterPower.rollKiColor(player);
			}
		}
		say(player, kingdoms ? "remade" : zfighter ? "remade_look" : "remade_none");
		return kingdoms || zfighter;
	}

	/** Reset racial skill. */
	static boolean racial(ServerPlayer player, boolean zfighter) {
		if (!zfighter || ZFighterLineage.of(player) == ZFighterLineage.NONE) {
			cannot(player);
			return false;
		}
		player.removeAttached(ZFighterEarthlingRace.LAST);
		player.removeAttached(ZFighterFrostReserve.RESERVE);
		player.removeAttached(ZFighterWarriorComeback.COUNT);
		player.removeAttached(ZFighterWarriorComeback.LAST);
		player.removeAttached(ZFighterLineage.TAIL_CUT);
		player.removeAttached(ZFighterLineage.CRIPPLED);
		ZFighterPower.refresh(player, ZFighterPower.live(player));
		say(player, "racial");
		return true;
	}

	/** Move stat points: refund what was bought, to be spent again. */
	static boolean restat(ServerPlayer player, boolean zfighter) {
		int bought = zfighter ? ZFighterTraining.bought(player) : 0;
		if (bought <= 0) {
			cannot(player);
			return false;
		}
		int refund = bought * ZFighterTraining.STAT_BASE_COST + ZFighterTraining.STAT_COST_STEP * bought * (bought - 1) / 2;
		ZFighterTraining.Data data = ZFighterTraining.of(player);
		ZFighterGrowth.Data trained = ZFighterGrowth.of(player);
		for (int stat = 0; stat < ZFighterTraining.STATS; stat++) {
			data = data.withStat(stat, Math.min(data.stat(stat), trained.trained(stat)));
		}
		player.setAttached(ZFighterTraining.DATA, data.withTp(data.tp() + refund));
		ZFighterPower.refresh(player, ZFighterPower.live(player));
		say(player, "restat", refund);
		return true;
	}

	/** Change difficulty: the next step round. */
	static boolean difficulty(ServerPlayer player) {
		int step = (step(player) + 1) % TAKEN.length;
		player.setAttached(DIFFICULTY, step);
		say(player, "difficulty", Component.translatable("wish.jaymospowers.zfighter.difficulty." + STEPS[step]), Math.round(TAKEN[step] * 100.0F));
		return true;
	}

	/** Undo the android rebuild. */
	static boolean android(ServerPlayer player, boolean zfighter) {
		if (!zfighter || !ZFighterAndroid.android(player)) {
			cannot(player);
			return false;
		}
		ZFighterAndroid.undo(player);
		return true;
	}

	static int step(ServerPlayer player) {
		return Math.floorMod(player.getAttachedOrElse(DIFFICULTY, 0), TAKEN.length);
	}

	/** Part of {@link ZFighterHooks#modifyDamage}: the damage {@code victim} takes, by the difficulty he wished for. */
	static float scaled(LivingEntity victim, float damage) {
		if (victim instanceof ServerPlayer player && damage > 0.0F) {
			int step = step(player);
			return step == 0 ? damage : damage * TAKEN[step];
		}
		return damage;
	}
}
