package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * What a faction holds of the buildings the world generated (0.5): the village buildings a kingdom took for its seat
 * and storehouse ({@link Seats}), or the Grim Kingdoms structure a fiefdom or camp was seated in ({@link GrimSites}).
 * Those buildings are {@code guarded}: builders never tear them down ({@link Ruins}). A Grim hold also keeps what its
 * builders still have to do there: the pieces to clear of rubble ({@link Debris}), whether that's done, and whether
 * they've raised a second Grim building nearby. Kept on the Overworld, by faction id.
 */
final class Holds {
	/**
	 * {@code guarded}: buildings never torn down; {@code grim}: the Grim structure's id ("" for a village seat);
	 * {@code rubble}: the pieces to clear; {@code founded}: game time; {@code cleared}: the rubble's gone (or there was
	 * none to clear); {@code erected}: its second building is begun.
	 */
	record Hold(List<BoundingBox> guarded, String grim, List<BoundingBox> rubble, long founded, boolean cleared, boolean erected) {
		static final Codec<Hold> CODEC = RecordCodecBuilder.create(i -> i.group(
				BoundingBox.CODEC.listOf().optionalFieldOf("guarded", List.of()).forGetter(Hold::guarded),
				Codec.STRING.optionalFieldOf("grim", "").forGetter(Hold::grim),
				BoundingBox.CODEC.listOf().optionalFieldOf("rubble", List.of()).forGetter(Hold::rubble),
				Codec.LONG.optionalFieldOf("founded", 0L).forGetter(Hold::founded),
				Codec.BOOL.optionalFieldOf("cleared", true).forGetter(Hold::cleared),
				Codec.BOOL.optionalFieldOf("erected", false).forGetter(Hold::erected))
			.apply(i, Hold::new));

		static Hold of(long founded) {
			return new Hold(List.of(), "", List.of(), founded, true, false);
		}

		boolean isGrim() {
			return !this.grim.isEmpty();
		}

		Hold withGuarded(BoundingBox box) {
			List<BoundingBox> all = new ArrayList<>(this.guarded);
			if (!all.contains(box)) {
				all.add(box);
			}
			return new Hold(List.copyOf(all), this.grim, this.rubble, this.founded, this.cleared, this.erected);
		}

		Hold withGrim(String grim, List<BoundingBox> rubble) {
			return new Hold(this.guarded, grim, List.copyOf(rubble), this.founded, rubble.isEmpty(), this.erected);
		}

		Hold withCleared(boolean cleared) {
			return new Hold(this.guarded, this.grim, this.rubble, this.founded, cleared, this.erected);
		}

		Hold withErected(boolean erected) {
			return new Hold(this.guarded, this.grim, this.rubble, this.founded, this.cleared, erected);
		}
	}

	/** Every faction's hold (faction id → it). */
	static final AttachmentType<Map<String, Hold>> HOLDS = AttachmentRegistry.create(JaymosKingdoms.id("holds"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Hold.CODEC)));

	private Holds() {
	}

	static Map<String, Hold> all(MinecraftServer server) {
		return Factions.level(server).getAttachedOrElse(HOLDS, Map.of());
	}

	static @Nullable Hold get(MinecraftServer server, int id) {
		return all(server).get(Integer.toString(id));
	}

	static void put(MinecraftServer server, int id, Hold hold) {
		Map<String, Hold> map = new HashMap<>(all(server));
		map.put(Integer.toString(id), hold);
		Factions.level(server).setAttached(HOLDS, Map.copyOf(map));
	}

	/** Changes faction {@code id}'s hold (a new one, founded now, if it has none). */
	static Hold update(MinecraftServer server, int id, UnaryOperator<Hold> change) {
		Hold old = get(server, id);
		Hold changed = change.apply(old != null ? old : Hold.of(Factions.level(server).getGameTime()));
		put(server, id, changed);
		return changed;
	}

	/** Marks {@code box} as one of faction {@code id}'s buildings, never to be torn down. */
	static void guard(MinecraftServer server, int id, BoundingBox box) {
		update(server, id, h -> h.withGuarded(box));
	}

	/** {@code pos} lies in a building some standing faction holds. */
	static boolean guarded(MinecraftServer server, BlockPos pos) {
		for (var e : all(server).entrySet()) {
			for (BoundingBox box : e.getValue().guarded()) {
				if (box.isInside(pos) && standing(server, e.getKey())) {
					return true;
				}
			}
		}
		return false;
	}

	/** The rectangle ({@code x0..x1}, {@code z0..z1}, widened by {@code gap}) touches a building some standing faction holds. */
	static boolean guarded(MinecraftServer server, int x0, int z0, int x1, int z1, int gap) {
		for (var e : all(server).entrySet()) {
			for (BoundingBox box : e.getValue().guarded()) {
				if (box.intersects(x0 - gap, z0 - gap, x1 + gap, z1 + gap) && standing(server, e.getKey())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean standing(MinecraftServer server, String id) {
		Faction f = Factions.get(server, Integer.parseInt(id));
		return f != null && !f.fallen();
	}
}
