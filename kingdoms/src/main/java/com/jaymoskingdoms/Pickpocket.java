package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.50) Pickpocketing: sneak up close behind a villager (MCA's too) or one of a settlement's civilians or guards, empty
 * handed, and hold use on them. Your hand goes into their pocket ({@link Reach}: the arm reaches in, for everyone to see)
 * for a while ({@link #holdTicks}): up to {@value #BASE_MAX_TICKS} ticks to start with, longer in bright light, the
 * more armour they wear, and the better their mood (an MCA villager's; a sad or crying one notices less, so does one
 * running scared), shorter in the dark or asleep. Stay behind them, matching their pace, and keep holding till it's done,
 * and you lift something: some of what they carry (a farmer's wheat and bread), or else a few emeralds, nuggets or a
 * snack. Let go early and you just draw your hand back; fall out of reach and they slip away from you.
 *
 * <p>If they turn round on you while your hand's in their pocket, you're caught: they cry thief, and where a faction's
 * law holds it's a theft seen ({@link Theft.Crime#PICKPOCKET}): reputation, and its guards after you. Done clean, anyone
 * else who sees you still counts as a witness; with no one, the mark may miss their purse later
 * ({@link Theft#NOTICE_CHANCE}). A pocket picked is empty for a day. A sleeping mark can be robbed from any side.
 *
 * <p>(0.5.51) Every pocket picked clean is Stealth experience ({@link Skills}): the more you do it, the shorter the hold,
 * and Stealth's perks take more, reach in from the side, and let you slip away when a mark turns on you.
 */
public final class Pickpocket {
	/** The hand stays in the pocket a random while between these (ticks), before light, armour and mood. */
	static final int BASE_MIN_TICKS = 20, BASE_MAX_TICKS = 60;
	/** Light: in pitch dark the while is this share of it, in full light this much (straight line between). */
	static final float DARK_FACTOR = 0.6F, BRIGHT_FACTOR = 1.4F;
	/** Each point of the mark's armour makes it this share longer (full iron, 15: +45%). */
	static final float ARMOR_FACTOR = 0.03F;
	/** An MCA villager's mood (-15 depressed .. +15 overjoyed): each point makes it this share longer (or shorter). */
	static final float MOOD_FACTOR = 0.025F;
	/** A mark running scared, asleep, or a guard (on top of their armour). */
	static final float PANIC_FACTOR = 0.6F, ASLEEP_FACTOR = 0.5F, GUARD_FACTOR = 1.25F;
	/** It never takes less than this (ticks). */
	static final int MIN_TICKS = 10;
	/** You're behind them when their body faces and the line to you are at least this far apart (the cosine of the angle). */
	static final double BEHIND_COS = -0.2;
	/** Once your hand's in, they must turn this far toward you to catch you (a little leeway as they walk). */
	static final double TURN_COS = 0.1;
	/** You must be this close to reach in; your hand's out of their pocket past this. */
	static final double ATTACH_RANGE = 2.0, HOLD_RANGE = 2.75;
	/** Holding use repeats every 4 ticks: no repeat for this long and you've let go. */
	static final int HOLD_GRACE = 8;
	/** How long a picked pocket stays empty (ticks); how soon after one try a player may try again (ticks). */
	static final long EMPTY_TICKS = 24000L, TRY_TICKS = 20L;
	/** From what a mark carries, take this share of a stack (at least one). */
	static final float CARRIED_SHARE = 0.5F;
	/** The progress bar's length. */
	static final int BAR = 12;

	/** What's in a pocket that holds nothing they carry: item, least, most, weight. */
	private record Pocket(Item item, int least, int most, int weight) {
	}

	private static final List<Pocket> POCKETS = List.of(new Pocket(Items.EMERALD, 1, 3, 4), new Pocket(Items.GOLD_NUGGET, 1, 4, 3),
		new Pocket(Items.IRON_NUGGET, 1, 5, 3), new Pocket(Items.BREAD, 1, 2, 3), new Pocket(Items.APPLE, 1, 2, 2),
		new Pocket(Items.STRING, 1, 3, 1), new Pocket(Items.PAPER, 1, 2, 1), new Pocket(Items.FLINT, 1, 1, 1));
	/** Out of all the weights plus this, the pocket is empty. */
	static final int NOTHING_WEIGHT = 3;

	/** Server → players who can see them: {@code playerId}'s hand is in {@code markId}'s pocket (-1: drawn back). */
	public record Reach(int playerId, int markId) implements CustomPacketPayload {
		public static final Type<Reach> TYPE = new Type<>(JaymosKingdoms.id("pickpocket_reach"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reach> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.playerId);
				buf.writeVarInt(p.markId + 1);
			},
			buf -> new Reach(buf.readVarInt(), buf.readVarInt() - 1));

		@Override
		public Type<Reach> type() {
			return TYPE;
		}
	}

	/** A hand in a pocket: whose, since and until when, and when use was last held. */
	private static final class Lift {
		final LivingEntity mark;
		final long start, until;
		long held;

		Lift(LivingEntity mark, long start, long until) {
			this.mark = mark;
			this.start = start;
			this.until = until;
			this.held = start;
		}
	}

	/** A try just over: the mark it was on (still holding use on them does nothing more), and till when. */
	private record Rest(UUID mark, long until) {
	}

	/** Player → the pocket their hand is in. */
	private static final Map<UUID, Lift> LIFTING = new HashMap<>();
	/** Mark → game time their pocket fills again. */
	private static final Map<UUID, Long> PICKED = new HashMap<>();
	/** Player → their last try. */
	private static final Map<UUID, Rest> RESTING = new HashMap<>();

	private Pickpocket() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Reach.TYPE, Reach.CODEC);
		// (registered before Quests, whose sneak-use on a plain villager opens its quests: from behind, it's a pickpocket)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)
				|| !(entity instanceof LivingEntity mark)) {
				return InteractionResult.PASS;
			}
			long now = level.getGameTime();
			Lift lift = LIFTING.get(sp.getUUID());
			if (lift != null && lift.mark == mark) {
				// (still holding use: the hand stays in)
				lift.held = now;
				return InteractionResult.SUCCESS;
			}
			Rest rest = RESTING.get(sp.getUUID());
			if (rest != null && rest.until() > now && rest.mark().equals(mark.getUUID())) {
				// (still holding use after a try: no quests or trades popping up)
				RESTING.put(sp.getUUID(), new Rest(rest.mark(), Math.max(rest.until(), now + HOLD_GRACE)));
				return InteractionResult.SUCCESS;
			}
			if (lift != null || !markable(mark) || !sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty() || sp.isSpectator()
				|| sp.distanceTo(mark) > ATTACH_RANGE || !(mark.isSleeping() || behind(sp, mark, Skills.behindCos(sp, BEHIND_COS)))) {
				return InteractionResult.PASS;
			}
			if (rest != null && rest.until() > now) {
				return InteractionResult.SUCCESS;
			}
			start(sp, level, mark, now);
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Pickpocket::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LIFTING.clear();
			PICKED.clear();
			RESTING.clear();
		});
	}

	/** Has pockets worth picking: a grown villager, or a settlement's folk short of its ruler. */
	private static boolean markable(LivingEntity e) {
		if (!e.isAlive() || e.isBaby()) {
			return false;
		}
		return e instanceof Villager || e instanceof Folk folk && !folk.leader();
	}

	/** {@code player} is behind {@code mark}: the way its body faces and the line to them are at least this far apart. */
	private static boolean behind(ServerPlayer player, LivingEntity mark, double behindCos) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, mark.yBodyRot);
		Vec3 to = player.position().subtract(mark.position()).multiply(1.0, 0.0, 1.0);
		if (to.lengthSqr() < 1.0E-6) {
			return true;
		}
		return facing.dot(to.normalize()) <= behindCos;
	}

	/**
	 * How long {@code player}'s hand must stay in {@code mark}'s pocket (ticks): a random while, longer in the light, on
	 * an armoured mark, a guard or a cheerful one, shorter in the dark, on a sad, scared or sleeping one.
	 */
	static int holdTicks(ServerPlayer player, ServerLevel level, LivingEntity mark, RandomSource random) {
		float ticks = BASE_MIN_TICKS + random.nextInt(BASE_MAX_TICKS - BASE_MIN_TICKS + 1);
		int light = level.getMaxLocalRawBrightness(mark.blockPosition());
		ticks *= Mth.lerp(light / 15.0F, DARK_FACTOR, BRIGHT_FACTOR);
		ticks *= 1.0F + ARMOR_FACTOR * mark.getArmorValue();
		if (mark instanceof Villager v) {
			ticks *= 1.0F + MOOD_FACTOR * McaCompat.mood(v);
			if (v.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING) || McaCompat.panicking(v)) {
				ticks *= PANIC_FACTOR;
			}
		}
		if (mark instanceof Folk folk && folk.combatant() && !Skills.ghost(player)) {
			ticks *= GUARD_FACTOR;
		}
		if (mark.isSleeping()) {
			ticks *= ASLEEP_FACTOR;
		}
		// (0.5.51) the more pockets you've picked, the lighter your hand (Skills: practice, Light Fingers); where they know
		// you for a Rogue, they keep a hand on their purses (Renown)
		ticks = Skills.holdTicks(player, ticks);
		if (Renown.knownRogue(player, law(level, mark))) {
			ticks *= Renown.ROGUE_WARY;
		}
		return Math.max(MIN_TICKS, Math.round(ticks));
	}

	private static void start(ServerPlayer player, ServerLevel level, LivingEntity mark, long now) {
		LIFTING.put(player.getUUID(), new Lift(mark, now, now + holdTicks(player, level, mark, level.getRandom())));
		reach(player, mark.getId());
	}

	/** Tells everyone who can see {@code player} where their hand is ({@code markId}, or -1: drawn back). */
	private static void reach(ServerPlayer player, int markId) {
		Reach packet = new Reach(player.getId(), markId);
		List<ServerPlayer> to = new ArrayList<>(PlayerLookup.tracking(player));
		to.add(player);
		for (ServerPlayer p : to) {
			if (ServerPlayNetworking.canSend(p, Reach.TYPE)) {
				ServerPlayNetworking.send(p, packet);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		if (LIFTING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Lift>> it = LIFTING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Lift lift = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			LivingEntity mark = lift.mark;
			if (!(player.level() instanceof ServerLevel level) || !player.isAlive() || !mark.isAlive() || mark.isRemoved() || mark.level() != level) {
				it.remove();
				end(player, mark, 0L);
				continue;
			}
			long now = level.getGameTime();
			if (now - lift.held > HOLD_GRACE) {
				it.remove();
				end(player, mark, now);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pickpocket.let_go").withStyle(ChatFormatting.GRAY));
			} else if (player.distanceTo(mark) > HOLD_RANGE) {
				it.remove();
				end(player, mark, now);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pickpocket.slipped_away", mark.getDisplayName())
					.withStyle(ChatFormatting.GRAY));
			} else if (!mark.isSleeping() && !behind(player, mark, Skills.turnCos(player, TURN_COS))) {
				it.remove();
				end(player, mark, now);
				caught(player, level, mark, law(level, mark));
			} else if (now >= lift.until) {
				it.remove();
				end(player, mark, now);
				pick(player, level, mark, now);
			} else if ((now - lift.start) % 2 == 0) {
				int filled = Math.round(BAR * (now - lift.start) / (float)(lift.until - lift.start));
				String bar = "|".repeat(filled) + ".".repeat(BAR - filled);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.pickpocket.lifting", mark.getDisplayName(), bar)
					.withStyle(ChatFormatting.GRAY));
			}
		}
	}

	/** {@code player}'s hand is out of {@code mark}'s pocket. */
	private static void end(ServerPlayer player, LivingEntity mark, long now) {
		RESTING.put(player.getUUID(), new Rest(mark.getUUID(), now + TRY_TICKS));
		reach(player, -1);
	}

	/** Whose law holds over {@code mark} (null: none, or a camp's). */
	private static @Nullable Faction law(ServerLevel level, LivingEntity mark) {
		Faction f = mark instanceof Folk folk ? Factions.get(level.getServer(), folk.factionId()) : Theft.lawAt(level, mark.blockPosition());
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	/** Held long enough and never seen by the mark: something lifted. */
	private static void pick(ServerPlayer player, ServerLevel level, LivingEntity mark, long now) {
		RandomSource random = level.getRandom();
		Faction f = law(level, mark);
		boolean fresh = PICKED.getOrDefault(mark.getUUID(), 0L) <= now;
		ItemStack loot = fresh ? loot(player, mark, random) : ItemStack.EMPTY;
		PICKED.put(mark.getUUID(), now + EMPTY_TICKS);
		// (0.5.51) a pocket picked clean is Stealth practice (one already emptied teaches nothing)
		int xp = fresh ? Skills.pickedClean(player, !loot.isEmpty(), mark instanceof Folk folk && folk.combatant(), mark.isSleeping()) : 0;
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

	/** The mark turned and saw: they round on {@code player}, and where a faction's law holds, it's a theft they saw. */
	private static void caught(ServerPlayer player, ServerLevel level, LivingEntity mark, @Nullable Faction f) {
		if (mark.isSleeping()) {
			mark.stopSleeping();
		}
		if (Skills.slipsAway(player, level.getRandom())) {
			// (0.5.51, Slip Away) they caught a hand, but not whose: they cry out, and it's no crime of yours
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
		// (0.5.51, Deep Pockets) another try for each rank at a pocket that turns up empty
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
