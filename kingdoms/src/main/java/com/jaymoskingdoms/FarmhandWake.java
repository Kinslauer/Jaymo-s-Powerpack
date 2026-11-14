package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "the farm start should start you waking up from the bed, with all your gear in a chest nearby. you should be
 * greeted by the family, theyll tell you they found you and took you in." The Family farmhand ({@link FarmhandHome})
 * wakes lying in one of the house's beds ({@link BedRest#lay}, awake), everything they set out with put away in a chest
 * beside it, and the family come over and tell them, a line at a time over their heads ({@link Speech}), how they found
 * them out past the fields and took them in.
 * <p>
 * Andy (2026-11-13): "you should speak with the villager as you wake up in the bed, the letterboxing from waking up should
 * stay for a speaking scene with him as you wake and he tells you all this. panning to the chest at the end." The
 * greeting is a film ({@link Shot}, the client's {@code FarmWakeFilmClient}): black bars from the moment the eyes open,
 * the camera from the pillow on whoever is talking (the farmer, the child for its one line), and at the end a pan to the
 * gear chest, after which they get up beside the bed facing it. Andy's rules for our films hold: the view keeps the
 * plain FOV, nothing can hurt the player while it runs ({@link #harm}), and the farmer walks over and stands still to
 * talk. Backspace skips it (what's left is put in chat).
 */
public final class FarmhandWake {
	/** How long the eyes take to open once the loading screen is gone (ticks of blindness). */
	static final int EYES_TICKS = 50;
	/** From waking to the first word (ticks). */
	static final int GREET_DELAY = 40;
	/** Between one line and the next: a base, plus a little per letter, so each can be read (ticks). */
	static final int LINE_BASE_TICKS = 40;
	static final float LINE_PER_CHAR_TICKS = 1.3F;
	/** How near the family come to talk (blocks), at what pace, how often they're steered (ticks). */
	static final double GREET_REACH = 2.5, GREET_SPEED = 0.6;
	static final int STEER_EVERY = 10;
	/** How far round the bed a gear chest may go (blocks), and how many chests at most. */
	static final int CHEST_REACH = 2, MAX_CHESTS = 2;
	/** Given up on if the player isn't seen in the world in this long (ticks). */
	static final int GIVE_UP_TICKS = 20 * 60 * 5;
	/** How near the speaker must be to start a line (blocks), and the longest a line waits for them to get there (ticks). */
	static final double SPEAK_REACH = 3.5;
	static final int ARRIVE_WAIT = 100;
	/** The pan to the gear chest at the end of the film, and its hold (ticks). */
	static final int CHEST_TICKS = 70;

	/**
	 * The greeting, in order: who says it ({@link #FARMER}: the first grown one, {@link #CHILD}: a child, skipped with none)
	 * and the line's key under {@code story.jaymoskingdoms.farm_wake.}. Andy: "he tells you all this": the farmer says it
	 * all, and the child only chimes in. {@link #NOTICE} is the farmer noticing a blood not their own (said kindly: it never
	 * costs anything), skipped for one of their own kind.
	 */
	private static final int FARMER = 0, CHILD = 2;
	private static final String NOTICE = "notice";
	private static final int[] WHO = {FARMER, FARMER, FARMER, FARMER, FARMER, FARMER, FARMER, CHILD, FARMER};
	private static final String[] LINES = {"awake", "found", "carried", NOTICE, "chest", "welcome", "work", "child", "rest"};

	/**
	 * Server → client: the wake film's next shot. {@link #SPEAK}: the camera on {@code entity}, talking (the first one starts
	 * the film); {@link #CHEST}: a pan to {@code at} (the gear chest) held for {@code ticks}; {@link #END}: the film is over.
	 */
	public record Shot(int kind, int entity, Vec3 at, int ticks) implements CustomPacketPayload {
		public static final int SPEAK = 0, CHEST = 1, END = 2;
		public static final Type<Shot> TYPE = new Type<>(JaymosKingdoms.id("farm_wake_shot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Shot> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Shot::kind,
			ByteBufCodecs.VAR_INT, Shot::entity, Vec3.STREAM_CODEC, Shot::at, ByteBufCodecs.VAR_INT, Shot::ticks, Shot::new);

		@Override
		public Type<Shot> type() {
			return TYPE;
		}
	}

	/** Client → server: the player skipped the wake film. */
	public record Skip() implements CustomPacketPayload {
		public static final Type<Skip> TYPE = new Type<>(JaymosKingdoms.id("farm_wake_skip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Skip> CODEC = StreamCodec.unit(new Skip());

		@Override
		public Type<Skip> type() {
			return TYPE;
		}
	}

	private static final class Wake {
		final BlockPos bed, spot;
		final List<UUID> grown = new ArrayList<>(), young = new ArrayList<>();
		final long since;
		boolean stowed;
		long seenAt = -1, nextAt;
		int line;
		/** The gear chest (the first, if two), or null with none placed. */
		@Nullable BlockPos chest;
		/** Whether the greeting is shown as a film (their game has it), and whether it's at the pan to the chest. */
		boolean film, panning;
		/** Who is talking just now (they stand still for it), and since when the next line has waited for them to come over. */
		@Nullable UUID speaking;
		long waitingSince = -1;
		/** Their blood isn't the family's: the farmer says so, kindly. */
		boolean otherBlood;

		Wake(BlockPos bed, BlockPos spot, long since) {
			this.bed = bed;
			this.spot = spot;
			this.since = since;
		}
	}

	private static final Map<UUID, Wake> WAKES = new HashMap<>();
	/** The family members talking to someone just woken: {@link FarmFamily#step} leaves them to us meanwhile. */
	private static final Set<UUID> GREETERS = new HashSet<>();

	private FarmhandWake() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Shot.TYPE, Shot.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Skip.TYPE, Skip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Skip.TYPE, (payload, context) -> skip(context.player()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(FarmhandWake::harm);
		ServerTickEvents.END_SERVER_TICK.register(FarmhandWake::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> drop(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WAKES.clear();
			GREETERS.clear();
		});
	}

	/** {@code player} is taken in by {@code folk} and will wake in the bed whose head is {@code bed} (beside {@code spot}). */
	static void expect(ServerPlayer player, BlockPos bed, BlockPos spot, List<Villager> folk) {
		Wake w = new Wake(bed, spot, player.level().getServer().getTickCount());
		for (Villager v : folk) {
			(v.isBaby() ? w.young : w.grown).add(v.getUUID());
		}
		w.otherBlood = Prejudice.degree(Heritage.of(player)) > 0;
		drop(player.getUUID());
		WAKES.put(player.getUUID(), w);
	}

	/** Whether {@code player} wakes to the family's greeting (so the house doesn't greet them behind the loading screen too). */
	static boolean expected(ServerPlayer player) {
		return WAKES.containsKey(player.getUUID());
	}

	/** Andy: no one can be hurt in a cutscene: nothing harms a player while their wake film runs. */
	private static boolean harm(LivingEntity victim, DamageSource source, float amount) {
		if (WAKES.isEmpty() || !(victim instanceof ServerPlayer)) {
			return true;
		}
		Wake w = WAKES.get(victim.getUUID());
		return w == null || !w.film || w.seenAt < 0;
	}

	/** Whether {@code mob} is greeting a farmhand just woken (so the farm round leaves them be). */
	static boolean greeting(Entity mob) {
		return !GREETERS.isEmpty() && GREETERS.contains(mob.getUUID());
	}

	/**
	 * The life story has kitted {@code player} out ({@link LifeStory}): if they're a farmhand waking in a bed, all of it
	 * goes in a chest beside the bed and they're laid in it.
	 */
	static void stow(ServerPlayer player) {
		Wake w = WAKES.get(player.getUUID());
		if (w == null || w.stowed) {
			return;
		}
		w.stowed = true;
		ServerLevel level = player.level();
		Inventory inv = player.getInventory();
		int slot = 0;
		for (int chests = 0; chests < MAX_CHESTS; chests++) {
			while (slot < inv.getContainerSize() && inv.getItem(slot).isEmpty()) {
				slot++;
			}
			if (slot >= inv.getContainerSize()) {
				break;
			}
			ChestBlockEntity chest = placeChest(level, w);
			if (chest == null) {
				break;
			}
			if (w.chest == null) {
				w.chest = chest.getBlockPos();
			}
			int into = 0;
			for (; slot < inv.getContainerSize() && into < chest.getContainerSize(); slot++) {
				ItemStack stack = inv.getItem(slot);
				if (!stack.isEmpty()) {
					chest.setItem(into++, inv.removeItemNoUpdate(slot));
				}
			}
			chest.setChanged();
		}
		inv.setChanged();
		// (Andy: "any of the items you spawn with get duplicated, as they are on your person and in the chest") the kit was
		// handed out with a slot packet each (Inventory.placeItemBackInInventory), which the inventory menu never counts as
		// sent; taken out again the same tick, the menu saw nothing change and never told their game, so it kept showing the
		// whole kit on them. The menu is sent over whole, so they hold what the server says they do: nothing.
		player.inventoryMenu.sendAllDataToRemote();
		if (player.containerMenu != player.inventoryMenu) {
			player.containerMenu.sendAllDataToRemote();
		}
		lay(player, w);
	}

	/** A chest on the floor beside the bed (not where they'd stand), facing it; null with no room. */
	private static @Nullable ChestBlockEntity placeChest(ServerLevel level, Wake w) {
		BlockState bed = level.getBlockState(w.bed);
		BlockPos foot = bed.getBlock() instanceof BedBlock ? w.bed.relative(bed.getValue(BedBlock.FACING).getOpposite()) : w.bed;
		for (BlockPos half : List.of(w.bed, foot)) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				ChestBlockEntity chest = chestAt(level, half.relative(d), d.getOpposite(), w);
				if (chest != null) {
					return chest;
				}
			}
		}
		for (BlockPos p : BlockPos.betweenClosed(w.bed.offset(-CHEST_REACH, 0, -CHEST_REACH), w.bed.offset(CHEST_REACH, 0, CHEST_REACH))) {
			ChestBlockEntity chest = chestAt(level, p.immutable(), Direction.NORTH, w);
			if (chest != null) {
				return chest;
			}
		}
		return null;
	}

	private static @Nullable ChestBlockEntity chestAt(ServerLevel level, BlockPos at, Direction facing, Wake w) {
		if (at.equals(w.spot) || !level.getBlockState(at).isAir()
			|| !level.getBlockState(at.below()).isFaceSturdy(level, at.below(), Direction.UP)) {
			return null;
		}
		level.setBlock(at, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_ALL);
		return level.getBlockEntity(at) instanceof ChestBlockEntity chest ? chest : null;
	}

	/** Into the bed, awake, if they're by it and it's free. */
	private static void lay(ServerPlayer player, Wake w) {
		if (player.isSleeping() || !player.isAlive() || player.blockPosition().distSqr(w.bed) > 16.0) {
			return;
		}
		BlockState state = player.level().getBlockState(w.bed);
		if (state.getBlock() instanceof BedBlock && !state.getValue(BedBlock.OCCUPIED)) {
			BedRest.lay(player, w.bed);
		}
	}

	private static void drop(UUID player) {
		Wake w = WAKES.remove(player);
		if (w != null) {
			w.grown.forEach(GREETERS::remove);
			w.young.forEach(GREETERS::remove);
		}
	}

	private static void tick(MinecraftServer server) {
		if (WAKES.isEmpty()) {
			return;
		}
		long now = server.getTickCount();
		for (Iterator<Map.Entry<UUID, Wake>> it = WAKES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Wake> e = it.next();
			Wake w = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null || !player.isAlive() || w.seenAt < 0 && now - w.since > GIVE_UP_TICKS) {
				if (player != null && w.film && w.seenAt >= 0) {
					ServerPlayNetworking.send(player, new Shot(Shot.END, -1, Vec3.ZERO, 0));
				}
				w.grown.forEach(GREETERS::remove);
				w.young.forEach(GREETERS::remove);
				it.remove();
				continue;
			}
			if (!w.stowed) {
				continue;
			}
			if (StoryGround.unseen(player)) {
				// (behind the loading screen: kept lying in the bed, whatever the arrival did to them)
				lay(player, w);
				continue;
			}
			ServerLevel level = player.level();
			if (w.seenAt < 0) {
				w.seenAt = now;
				w.nextAt = now + GREET_DELAY;
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EYES_TICKS, 0, false, false));
				if (w.grown.isEmpty() && w.young.isEmpty()) {
					player.sendSystemMessage(Component.translatable("story.jaymoskingdoms.farm_wake.alone").withStyle(ChatFormatting.GRAY));
					it.remove();
					continue;
				}
				GREETERS.addAll(w.grown);
				GREETERS.addAll(w.young);
				// (the bars come in as the eyes open, on the farmer coming over)
				w.film = ServerPlayNetworking.canSend(player, Shot.TYPE);
				Entity farmer = speaker(level, w, FARMER);
				if (w.film) {
					ServerPlayNetworking.send(player, new Shot(Shot.SPEAK, farmer == null ? -1 : farmer.getId(), Vec3.ZERO, 0));
				}
			}
			if (now % STEER_EVERY == 0) {
				steer(level, player, w);
			}
			hold(level, player, w);
			if (now < w.nextAt) {
				continue;
			}
			if (w.panning) {
				// the pan to the chest has run: up beside the bed, facing it
				finish(player, w);
				w.grown.forEach(GREETERS::remove);
				w.young.forEach(GREETERS::remove);
				it.remove();
				continue;
			}
			if (!say(level, player, w, now)) {
				if (w.film && w.chest != null) {
					w.panning = true;
					w.speaking = null;
					w.nextAt = now + CHEST_TICKS;
					ServerPlayNetworking.send(player, new Shot(Shot.CHEST, -1, Vec3.atCenterOf(w.chest), CHEST_TICKS));
					continue;
				}
				finish(player, w);
				w.grown.forEach(GREETERS::remove);
				w.young.forEach(GREETERS::remove);
				it.remove();
			}
		}
	}

	/** The film is over (or skipped): up out of the bed onto the floor beside it, facing the gear chest, and the bars go. */
	private static void finish(ServerPlayer player, Wake w) {
		if (!w.film) {
			return;
		}
		ServerPlayNetworking.send(player, new Shot(Shot.END, -1, Vec3.ZERO, 0));
		if (w.chest == null || !player.isSleeping()) {
			return;
		}
		player.stopSleepInBed(true, true);
		Vec3 stand = Vec3.atBottomCenterOf(w.spot);
		Vec3 to = Vec3.atCenterOf(w.chest).subtract(stand.add(0.0, player.getEyeHeight(), 0.0));
		float yaw = (float)Math.toDegrees(Math.atan2(-to.x, to.z));
		float pitch = (float)Math.toDegrees(-Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
		player.connection.teleport(stand.x, stand.y, stand.z, yaw, pitch);
	}

	/** (Backspace in the film) The rest of the greeting goes in chat, and they're up facing the chest. */
	private static void skip(ServerPlayer player) {
		Wake w = WAKES.get(player.getUUID());
		if (w == null || !w.film || w.seenAt < 0) {
			return;
		}
		ServerLevel level = player.level();
		if (!w.panning) {
			while (w.line < LINES.length) {
				int i = w.line++;
				Entity speaker = speaker(level, w, WHO[i]);
				Component words = words(player, LINES[i], w);
				if (speaker != null && words != null) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", speaker.getDisplayName(), words)
						.withStyle(ChatFormatting.GRAY));
				}
			}
		}
		finish(player, w);
		drop(player.getUUID());
	}

	/** The family come over to the bed and look at whoever woke in it (whoever is talking stays where they stopped). */
	private static void steer(ServerLevel level, ServerPlayer player, Wake w) {
		for (List<UUID> ids : List.of(w.grown, w.young)) {
			for (UUID id : ids) {
				if (id.equals(w.speaking)) {
					continue;
				}
				if (level.getEntity(id) instanceof PathfinderMob mob && mob.isAlive()) {
					if (mob.distanceTo(player) > GREET_REACH) {
						mob.getNavigation().moveTo(player, GREET_SPEED);
					} else {
						mob.getNavigation().stop();
					}
					mob.getLookControl().setLookAt(player);
				}
			}
		}
	}

	/** Andy: the speaker stops to talk: whoever is saying a line stands still, looking at the one in the bed. */
	private static void hold(ServerLevel level, ServerPlayer player, Wake w) {
		if (w.speaking == null || !(level.getEntity(w.speaking) instanceof PathfinderMob mob) || !mob.isAlive()) {
			return;
		}
		mob.getNavigation().stop();
		mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		mob.getLookControl().setLookAt(player);
	}

	/** The words of greeting line {@code key}, or null for one this player doesn't get. */
	private static @Nullable Component words(ServerPlayer player, String key, Wake w) {
		if (NOTICE.equals(key)) {
			// (the house noticing your blood, said kindly: it never costs anything)
			return w.otherBlood ? Component.translatable("talk.jaymoskingdoms.farm_family.notice", Identities.shownName(player)) : null;
		}
		return Component.translatable("story.jaymoskingdoms.farm_wake." + key);
	}

	/** The next line of the greeting said, or false once it's all been said. */
	private static boolean say(ServerLevel level, ServerPlayer player, Wake w, long now) {
		while (w.line < LINES.length) {
			int i = w.line;
			Entity speaker = speaker(level, w, WHO[i]);
			Component words = words(player, LINES[i], w);
			if (speaker == null || words == null) {
				w.line++;
				continue;
			}
			if (speaker.distanceTo(player) > SPEAK_REACH && (w.waitingSince < 0 || now - w.waitingSince < ARRIVE_WAIT)) {
				// (still coming over to the bed: they talk once they're there)
				if (w.waitingSince < 0) {
					w.waitingSince = now;
				}
				if (speaker instanceof PathfinderMob mob) {
					mob.getNavigation().moveTo(player, GREET_SPEED);
				}
				w.nextAt = now + 5;
				return true;
			}
			w.line++;
			w.waitingSince = -1;
			w.speaking = speaker.getUUID();
			if (speaker instanceof PathfinderMob mob) {
				mob.getNavigation().stop();
			}
			if (w.film) {
				ServerPlayNetworking.send(player, new Shot(Shot.SPEAK, speaker.getId(), Vec3.ZERO, 0));
			}
			Speech.sayPlainly(level, speaker, player, words, Speech.WHITE);
			if (ServerPlayNetworking.canSend(player, Speech.Said.TYPE)) {
				// (and in chat, for whoever looked away)
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.says", speaker.getDisplayName(), words)
					.withStyle(ChatFormatting.GRAY));
			}
			w.nextAt = now + LINE_BASE_TICKS + (int)(words.getString().length() * LINE_PER_CHAR_TICKS);
			return true;
		}
		return false;
	}

	private static @Nullable Entity speaker(ServerLevel level, Wake w, int who) {
		if (who == CHILD) {
			return first(level, w.young, 0);
		}
		Entity grown = first(level, w.grown, 0);
		// (no one grown about: the child says it all)
		return grown != null ? grown : first(level, w.young, 0);
	}

	/** The {@code skip}+1th of {@code ids} that's alive here, or null. */
	private static @Nullable Entity first(ServerLevel level, List<UUID> ids, int skip) {
		for (UUID id : ids) {
			Entity e = level.getEntity(id);
			if (e != null && e.isAlive() && skip-- == 0) {
				return e;
			}
		}
		return null;
	}
}
