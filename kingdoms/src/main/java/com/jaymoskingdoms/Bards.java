package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.nightfall.Nightfall;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A tavern's bard. One villager in a standing tavern holds the lute ({@code Nightfall.LUTE}) and plays one of the Medieval
 * Music pack's tracks as a positional, streamed sound that fades out with distance (heard up to {@link #HEAR} blocks,
 * the sound events' own {@code attenuation_distance}), and rests {@link #REST_TICKS} between songs. Sneak and use on the
 * bard with a coin in your purse ({@link #TIP}) and they stop and play another. Without the music pack the sounds
 * resolve to nothing. The bard is picked only when a player is within {@link #HEAR} blocks of the tavern.
 */
public final class Bards {
	/** How far the music carries (blocks; also the sound events' attenuation distance), and how often taverns are looked at (ticks). */
	static final double HEAR = 16.0;
	static final int SCAN_EVERY = 40;
	/** The rest between songs (ticks), and a tip: a coin's worth. */
	static final int REST_TICKS = 400;
	static final long TIP = Money.worth(1);
	/** The bard's songs (sound events {@code bard.<name>}) and how long each plays (ticks). */
	static final String[] SONGS = {"bonfire", "now_we_feast", "celebration", "tam_lin"};
	static final int[] LENGTH = {1974, 4067, 2861, 5230};

	/** One tavern's bard: who, the game time they may play again, and the song last played. */
	private static final class Bard {
		UUID villager;
		long free;
		int song = -1;

		Bard(UUID villager) {
			this.villager = villager;
		}
	}

	/** Each tavern's bard, by the tavern's faction and its low corner. */
	private static final Map<String, Bard> BARDS = new HashMap<>();

	private Bards() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Bards::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> BARDS.clear());
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !(player instanceof ServerPlayer sp)
				|| !(entity instanceof Villager villager) || !(world instanceof ServerLevel level) || !holdsLute(villager)) {
				return InteractionResult.PASS;
			}
			Bard bard = bardOf(villager);
			if (bard == null || !Money.canPay(sp, TIP)) {
				return InteractionResult.PASS;
			}
			Money.charge(sp, TIP);
			stop(level, villager, bard);
			play(level, villager, bard, level.getGameTime());
			return InteractionResult.SUCCESS;
		});
	}

	private static boolean holdsLute(Villager villager) {
		return villager.isAlive() && villager.getItemBySlot(EquipmentSlot.MAINHAND).is(Nightfall.LUTE);
	}

	private static Bard bardOf(Villager villager) {
		for (Bard bard : BARDS.values()) {
			if (bard.villager.equals(villager.getUUID())) {
				return bard;
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_EVERY != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		if (level.players().isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Faction faction : Factions.all(server)) {
			if (faction.fallen() || !faction.type().isRealm() && faction.type() != FactionType.VILLAGE) {
				continue;
			}
			for (Works.Built built : Works.of(server, faction.id()).built()) {
				if (!built.entry().equals(Planner.TAVERN) || built.ruined() != 0) {
					continue;
				}
				AABB box = new AABB(Vec3.atLowerCornerOf(built.min()), Vec3.atLowerCornerOf(built.max()).add(1, 1, 1)).inflate(Remedies.TAVERN_REACH);
				if (!listening(level, box)) {
					continue;
				}
				String key = faction.id() + ":" + built.min().asLong();
				Bard bard = BARDS.get(key);
				Villager villager = bard == null ? null : find(level, bard.villager, box);
				if (villager == null) {
					villager = pick(level, box);
					if (villager == null) {
						continue;
					}
					bard = new Bard(villager.getUUID());
					BARDS.put(key, bard);
				}
				if (now >= bard.free) {
					play(level, villager, bard, now);
				}
			}
		}
	}

	/** A player is within {@link #HEAR} blocks of {@code box}. */
	private static boolean listening(ServerLevel level, AABB box) {
		return !level.getEntitiesOfClass(ServerPlayer.class, box.inflate(HEAR), p -> !p.isSpectator()).isEmpty();
	}

	/** The bard {@code id} if they still stand in {@code box} holding the lute. */
	private static Villager find(ServerLevel level, UUID id, AABB box) {
		Entity entity = level.getEntity(id);
		return entity instanceof Villager v && holdsLute(v) && box.contains(v.position()) ? v : null;
	}

	/** A villager in {@code box}: the one already holding a lute, or else one who is given it. */
	private static Villager pick(ServerLevel level, AABB box) {
		List<Villager> folk = level.getEntitiesOfClass(Villager.class, box, v -> v.isAlive() && !v.isBaby());
		for (Villager v : folk) {
			if (holdsLute(v) && bardOf(v) == null) {
				return v;
			}
		}
		for (Villager v : folk) {
			if (bardOf(v) == null && v.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
				v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Nightfall.LUTE));
				v.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
				return v;
			}
		}
		return null;
	}

	private static void play(ServerLevel level, Villager villager, Bard bard, long now) {
		int next;
		do {
			next = level.getRandom().nextInt(SONGS.length);
		} while (next == bard.song && SONGS.length > 1);
		bard.song = next;
		bard.free = now + LENGTH[next] + REST_TICKS;
		level.playSound(null, villager.getX(), villager.getEyeY(), villager.getZ(), Holder.direct(event(next)), SoundSource.RECORDS, 1.0F, 1.0F);
	}

	/** Stops the song that sounds, for those who can hear it. */
	private static void stop(ServerLevel level, Villager villager, Bard bard) {
		if (bard.song < 0) {
			return;
		}
		ClientboundStopSoundPacket packet = new ClientboundStopSoundPacket(event(bard.song).location(), SoundSource.RECORDS);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, villager.getBoundingBox().inflate(HEAR * 2.0))) {
			p.connection.send(packet);
		}
	}

	private static SoundEvent event(int song) {
		return SoundEvent.createVariableRangeEvent(JaymosKingdoms.id("bard." + SONGS[song]));
	}
}
