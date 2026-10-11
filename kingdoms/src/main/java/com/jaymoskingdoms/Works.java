package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Rotation;
import org.jspecify.annotations.Nullable;

/**
 * Every faction's building works, kept on the Overworld beside the factions (saved with them): the projects under way
 * (half-built buildings resume where they stopped), what it has finished, and the plots its lords have marked.
 */
public final class Works {
	/** A project's stages: the site staked out, the ground readied (dug and filled), the building going up. */
	public static final int STAKE = 0, PREP = 1, BUILD = 2;

	/**
	 * A building under way: which catalogue entry, its plot's lowest north-west corner ({@code origin}, its y the floor),
	 * how it's turned, its stage and how far through it ({@code index}: columns readied, or blocks laid), the materials
	 * already on site ({@code credit}: item id → eighths), its stakes, and the plot it stands on (-1 for none).
	 */
	public record Project(String entry, BlockPos origin, Rotation rotation, int stage, int index, Map<String, Integer> credit, List<BlockPos> stakes,
		int plot, long started) {
		public static final Codec<Project> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("entry").forGetter(Project::entry),
				BlockPos.CODEC.fieldOf("origin").forGetter(Project::origin),
				Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(Project::rotation),
				Codec.INT.optionalFieldOf("stage", STAKE).forGetter(Project::stage),
				Codec.INT.optionalFieldOf("index", 0).forGetter(Project::index),
				Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("credit", Map.of()).forGetter(Project::credit),
				BlockPos.CODEC.listOf().optionalFieldOf("stakes", List.of()).forGetter(Project::stakes),
				Codec.INT.optionalFieldOf("plot", -1).forGetter(Project::plot),
				Codec.LONG.optionalFieldOf("started", 0L).forGetter(Project::started))
			.apply(i, Project::new));

		public Project at(int stage, int index) {
			return new Project(this.entry, this.origin, this.rotation, stage, index, this.credit, this.stakes, this.plot, this.started);
		}

		public Project withCredit(Map<String, Integer> credit) {
			Map<String, Integer> clean = new HashMap<>(credit);
			clean.values().removeIf(n -> n <= 0);
			return new Project(this.entry, this.origin, this.rotation, this.stage, this.index, Map.copyOf(clean), this.stakes, this.plot, this.started);
		}

		public Project withStakes(List<BlockPos> stakes) {
			return new Project(this.entry, this.origin, this.rotation, this.stage, this.index, this.credit, List.copyOf(stakes), this.plot, this.started);
		}

		/** The same building (two projects are the same one when they share an origin and an entry). */
		public boolean same(Project other) {
			return other != null && this.entry.equals(other.entry) && this.origin.equals(other.origin);
		}
	}

	/** A finished building: its entry and its footprint (corners, inclusive). */
	public record Built(String entry, BlockPos min, BlockPos max) {
		public static final Codec<Built> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("entry").forGetter(Built::entry),
				BlockPos.CODEC.fieldOf("min").forGetter(Built::min),
				BlockPos.CODEC.fieldOf("max").forGetter(Built::max))
			.apply(i, Built::new));
	}

	/**
	 * A plot a lord marked for the builders: its corners ({@code min}/{@code max}, x and z inclusive; {@code min}'s y is
	 * the floor it's built at), who marked it, what the lord chose to put on it (if anything), and whether it's built.
	 */
	public record Plot(int id, BlockPos min, BlockPos max, UUID owner, Optional<String> building, boolean done) {
		public static final Codec<Plot> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("id").forGetter(Plot::id),
				BlockPos.CODEC.fieldOf("min").forGetter(Plot::min),
				BlockPos.CODEC.fieldOf("max").forGetter(Plot::max),
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Plot::owner),
				Codec.STRING.optionalFieldOf("building").forGetter(Plot::building),
				Codec.BOOL.optionalFieldOf("done", false).forGetter(Plot::done))
			.apply(i, Plot::new));

		public boolean contains(BlockPos pos) {
			return pos.getX() >= this.min.getX() && pos.getX() <= this.max.getX() && pos.getZ() >= this.min.getZ() && pos.getZ() <= this.max.getZ();
		}

		public int width() {
			return this.max.getX() - this.min.getX() + 1;
		}

		public int depth() {
			return this.max.getZ() - this.min.getZ() + 1;
		}

		public Plot withBuilding(Optional<String> building, boolean done) {
			return new Plot(this.id, this.min, this.max, this.owner, building, done);
		}
	}

	/** One faction's works. */
	public record Site(List<Project> projects, List<Built> built, List<Plot> plots) {
		public static final Site EMPTY = new Site(List.of(), List.of(), List.of());
		public static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
				Project.CODEC.listOf().optionalFieldOf("projects", List.of()).forGetter(Site::projects),
				Built.CODEC.listOf().optionalFieldOf("built", List.of()).forGetter(Site::built),
				Plot.CODEC.listOf().optionalFieldOf("plots", List.of()).forGetter(Site::plots))
			.apply(i, Site::new));

		public Site withProjects(List<Project> projects) {
			return new Site(List.copyOf(projects), this.built, this.plots);
		}

		public Site withBuilt(List<Built> built) {
			return new Site(this.projects, List.copyOf(built), this.plots);
		}

		public Site withPlots(List<Plot> plots) {
			return new Site(this.projects, this.built, List.copyOf(plots));
		}

		/** How many of {@code entry} it has finished. */
		public int count(String entry) {
			int n = 0;
			for (Built b : this.built) {
				if (b.entry().equals(entry)) {
					n++;
				}
			}
			for (Project p : this.projects) {
				if (p.entry().equals(entry)) {
					n++;
				}
			}
			return n;
		}

		public @Nullable Plot plot(int id) {
			for (Plot p : this.plots) {
				if (p.id() == id) {
					return p;
				}
			}
			return null;
		}
	}

	/** Faction id → its works. */
	static final AttachmentType<Map<String, Site>> WORKS = AttachmentRegistry.create(JaymosKingdoms.id("works"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Site.CODEC)));

	private Works() {
	}

	public static Site of(MinecraftServer server, int faction) {
		Map<String, Site> all = server.overworld().getAttached(WORKS);
		return all == null ? Site.EMPTY : all.getOrDefault(Integer.toString(faction), Site.EMPTY);
	}

	public static Map<Integer, Site> all(MinecraftServer server) {
		Map<String, Site> all = server.overworld().getAttachedOrElse(WORKS, Map.of());
		Map<Integer, Site> out = new HashMap<>();
		all.forEach((k, v) -> out.put(Integer.parseInt(k), v));
		return out;
	}

	/** Changes faction {@code faction}'s works; returns them as stored. */
	public static Site update(MinecraftServer server, int faction, UnaryOperator<Site> change) {
		Map<String, Site> all = new HashMap<>(server.overworld().getAttachedOrElse(WORKS, Map.of()));
		Site site = change.apply(all.getOrDefault(Integer.toString(faction), Site.EMPTY));
		all.put(Integer.toString(faction), site);
		server.overworld().setAttached(WORKS, Map.copyOf(all));
		return site;
	}

	/** The project under way that {@code key} belongs to (by origin and entry), as stored now. */
	public static @Nullable Project project(MinecraftServer server, int faction, Project key) {
		for (Project p : of(server, faction).projects()) {
			if (p.same(key)) {
				return p;
			}
		}
		return null;
	}

	/** Replaces the stored copy of {@code project} (matched by origin and entry). */
	public static void putProject(MinecraftServer server, int faction, Project project) {
		update(server, faction, s -> {
			List<Project> list = new ArrayList<>(s.projects());
			list.replaceAll(p -> p.same(project) ? project : p);
			return s.withProjects(list);
		});
	}

	/** The plot (of any faction) at {@code pos}, with its faction's id, or null. */
	public static Map.@Nullable Entry<Integer, Plot> plotAt(MinecraftServer server, BlockPos pos) {
		for (var e : all(server).entrySet()) {
			for (Plot p : e.getValue().plots()) {
				if (p.contains(pos)) {
					return Map.entry(e.getKey(), p);
				}
			}
		}
		return null;
	}
}
