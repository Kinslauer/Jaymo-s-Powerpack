package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.49) Pickpocketing: sneak up behind a villager (MCA's too) or one of a settlement's civilians or guards, empty
 * handed, and use them. You lift something from their pockets: some of what they carry (a farmer's wheat and bread), or
 * else a few emeralds, nuggets or a snack. A sleeping mark can be robbed from any side, and is easier.
 *
 * <p>The mark may feel your hand ({@link #CATCH_CHANCE}): they turn and cry thief, and where a faction's law holds it's a
 * theft seen ({@link Theft.Crime#PICKPOCKET}): reputation, and its guards after you. Done clean, anyone else who sees
 * you still counts as a witness; with no one, the mark may miss their purse later ({@link Theft#NOTICE_CHANCE}). A pocket
 * picked is empty for a day.
 *
 * <p>(0.5.50) Every pocket picked clean is Stealth experience ({@link Skills}): the more you do it, the less marks feel
 * your hand, and Stealth's perks take more, from the side, and let you slip away when they do feel it.
 */
public final class Pickpocket {
	/** How likely the mark is to feel your hand: awake, asleep, a guard. */
	static final float CATCH_CHANCE = 0.25F, CATCH_ASLEEP = 0.1F, CATCH_GUARD = 0.5F;
	/** You're behind them when their gaze and the line to you are at least this far apart (the cosine of the angle). */
	static final double BEHIND_COS = -0.2;
	/** How long a picked pocket stays empty (ticks); how soon a player may try again (ticks). */
	static final long EMPTY_TICKS = 24000L, TRY_TICKS = 10L;
	/** From what a mark carries, take this share of a stack (at least one). */
	static final float CARRIED_SHARE = 0.5F;

	/** What's in a pocket that holds nothing they carry: item, least, most, weight. */
	private record Pocket(Item item, int least, int most, int weight) {
	}

	private static final List<Pocket> POCKETS = List.of(new Pocket(Items.EMERALD, 1, 3, 4), new Pocket(Items.GOLD_NUGGET, 1, 4, 3),
		new Pocket(Items.IRON_NUGGET, 1, 5, 3), new Pocket(Items.BREAD, 1, 2, 3), new Pocket(Items.APPLE, 1, 2, 2),
		new Pocket(Items.STRING, 1, 3, 1), new Pocket(Items.PAPER, 1, 2, 1), new Pocket(Items.FLINT, 1, 1, 1));
	/** Out of all the weights plus this, the pocket is empty. */
	static final int NOTHING_WEIGHT = 3;

	/** Mark → game time their pocket fills again. */
	private static final Map<UUID, Long> PICKED = new HashMap<>();
	/** Player → game time they may try again. */
	private static final Map<UUID, Long> NEXT_TRY = new HashMap<>();

	private Pickpocket() {
	}

	static void register() {
		// (registered before Quests, whose sneak-use on a plain villager opens its quests: from behind, it's a pickpocket)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !(entity instanceof LivingEntity mark) || !markable(mark) || !sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()
				|| sp.isSpectator() || !(mark.isSleeping() || behind(sp, mark, Skills.behindCos(sp, BEHIND_COS)))) {
				return InteractionResult.PASS;
			}
			long now = level.getGameTime();
			if (NEXT_TRY.getOrDefault(sp.getUUID(), 0L) > now) {
				return InteractionResult.SUCCESS;
			}
			NEXT_TRY.put(sp.getUUID(), now + TRY_TICKS);
			pick(sp, level, mark, now);
			return InteractionResult.SUCCESS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PICKED.clear();
			NEXT_TRY.clear();
		});
	}

	/** Has pockets worth picking: a grown villager, or a settlement's folk short of its ruler. */
	private static boolean markable(LivingEntity e) {
		if (!e.isAlive() || e.isBaby()) {
			return false;
		}
		return e instanceof Villager || e instanceof Folk folk && !folk.leader();
	}

	/** {@code player} is behind {@code mark}: out of its sight. */
	private static boolean behind(ServerPlayer player, LivingEntity mark, double behindCos) {
		Vec3 look = mark.getViewVector(1.0F).multiply(1.0, 0.0, 1.0);
		Vec3 to = player.position().subtract(mark.position()).multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-6 || to.lengthSqr() < 1.0E-6) {
			return false;
		}
		return look.normalize().dot(to.normalize()) <= behindCos;
	}

	private static void pick(ServerPlayer player, ServerLevel level, LivingEntity mark, long now) {
		RandomSource random = level.getRandom();
		boolean guard = mark instanceof Folk folk && folk.combatant(), asleep = mark.isSleeping();
		// (0.5.50) the more pockets you've picked, the lighter your hand (Skills: practice, Light Fingers; a Ghost robs guards like anyone)
		float catchChance = Skills.catchChance(player, guard && !Skills.ghost(player) ? CATCH_GUARD : asleep ? CATCH_ASLEEP : CATCH_CHANCE);
		Faction f = mark instanceof Folk folk ? Factions.get(level.getServer(), folk.factionId()) : Theft.lawAt(level, mark.blockPosition());
		if (f != null && (f.fallen() || f.type() == FactionType.BARBARIAN)) {
			f = null;
		}
		if (random.nextFloat() < catchChance) {
			caught(player, level, mark, f);
			return;
		}
		boolean fresh = PICKED.getOrDefault(mark.getUUID(), 0L) <= now;
		ItemStack loot = fresh ? loot(player, mark, random) : ItemStack.EMPTY;
		PICKED.put(mark.getUUID(), now + EMPTY_TICKS);
		// (0.5.50) a pocket picked clean is Stealth practice (one already emptied teaches nothing)
		int xp = fresh ? Skills.pickedClean(player, !loot.isEmpty(), guard, asleep) : 0;
		Component gained = xp > 0 ? Component.translatable("message.jaymoskingdoms.pickpocket.xp", xp).withStyle(ChatFormatting.DARK_GREEN) : Component.empty();
		if (loot.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pickpocket.empty", mark.getDisplayName()).withStyle(ChatFormatting.GRAY).append(gained));
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pickpocket.lifted", loot.getCount(), loot.getHoverName(),
				mark.getDisplayName()).withStyle(ChatFormatting.GRAY).append(gained));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.4F);
			if (!player.getInventory().add(loot) && !loot.isEmpty()) {
				player.drop(loot, false, Prediction.SERVER_ONLY);
			}
		}
		if (f != null) {
			// (the mark didn't feel it, but anyone else watching did; unseen, they may miss it later)
			List<LivingEntity> seen = new ArrayList<>(Theft.witnesses(level, f, player));
			seen.remove(mark);
			Theft.commit(player, level, f, Theft.Crime.PICKPOCKET, null, true, seen);
		}
	}

	/** The mark felt it: they round on {@code player}, and where a faction's law holds, it's a theft they saw. */
	private static void caught(ServerPlayer player, ServerLevel level, LivingEntity mark, @Nullable Faction f) {
		if (mark.isSleeping()) {
			mark.stopSleeping();
		}
		if (Skills.slipsAway(player, level.getRandom())) {
			// (0.5.50, Slip Away) they felt it, but can't tell whose hand it was: they cry out, and it's no crime of yours
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.pickpocket.slipped", mark.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			Speech.say(level, mark, Component.translatable("message.jaymoskingdoms.theft.cry.pickpocket." + level.getRandom().nextInt(Theft.SHOUT_VARIANTS)),
				Speech.YELLOW, Theft.SHOUT_RANGE);
			return;
		}
		if (mark instanceof Mob mob) {
			mob.getLookControl().setLookAt(player);
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.pickpocket.caught", mark.getDisplayName()).withStyle(ChatFormatting.RED));
		if (f != null && !Wars.enemy(player, f)) {
			List<LivingEntity> seen = new ArrayList<>(Theft.witnesses(level, f, player));
			seen.remove(mark);
			seen.addFirst(mark);
			Theft.commit(player, level, f, Theft.Crime.PICKPOCKET, null, false, seen);
		} else {
			// (no law here: they only shout)
			Speech.say(level, mark, Component.translatable("message.jaymoskingdoms.theft.cry.pickpocket." + level.getRandom().nextInt(Theft.SHOUT_VARIANTS)),
				Speech.RED, Theft.SHOUT_RANGE);
			level.playSound(null, mark.getX(), mark.getY(), mark.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, 1.0F);
		}
	}

	/** What {@code mark} has on them: a share of something they carry, or else a pocket's worth (or nothing). */
	private static ItemStack loot(ServerPlayer player, LivingEntity mark, RandomSource random) {
		if (mark instanceof Villager v) {
			SimpleContainer bag = v.getInventory();
			List<Integer> full = new ArrayList<>();
			for (int slot = 0; slot < bag.getContainerSize(); slot++) {
				if (!bag.getItem(slot).isEmpty()) {
					full.add(slot);
				}
			}
			if (!full.isEmpty()) {
				ItemStack stack = bag.getItem(full.get(random.nextInt(full.size())));
				int take = Math.max(1, Math.round(stack.getCount() * Skills.carriedShare(player, CARRIED_SHARE)));
				bag.setChanged();
				return stack.split(take);
			}
		}
		int total = NOTHING_WEIGHT;
		for (Pocket p : POCKETS) {
			total += p.weight();
		}
		// (0.5.50, Deep Pockets) another try for each rank at a pocket that turns up empty
		for (int tries = Skills.pocketTries(player); tries > 0; tries--) {
			int roll = random.nextInt(total);
			for (Pocket p : POCKETS) {
				roll -= p.weight();
				if (roll < 0) {
					return new ItemStack(p.item(), p.least() + random.nextInt(p.most() - p.least() + 1));
				}
			}
		}
		return ItemStack.EMPTY;
	}
}
