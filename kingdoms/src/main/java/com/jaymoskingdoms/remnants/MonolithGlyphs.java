package com.jaymoskingdoms.remnants;

import com.jaymoskingdoms.Factions;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.581) Andy (seven-mods plan, patch 6): rows of glyphs carved round each monolith's foot, one row on each face, in
 * the carved stone of its element. Use a book and quill on them and a row is copied into it, a page each (one row a day
 * from each monolith). The more rows a book holds, the more of them its copier seems to make out, scribbled under each
 * new row: first the remnant's kind, then a heading, then how far, then whether it stirs. What they point at is the next
 * Giant still standing, the nearest other awake monolith. Nothing is said on screen; it is all in the book.
 */
public final class MonolithGlyphs {
	/** The row: on each face of the broad foot, this high over the foot, this far either side of its middle. */
	static final int FACE = 2 * Monoliths.SCALE + Monoliths.SCALE / 2, ROW_Y = Monoliths.SCALE + 1, ROW_HALF = 3;
	/** How near a player must come for an old monolith to be carved (blocks), and how often that is looked at (ticks). */
	static final double CARVE_RANGE = 64.0;
	static final int CARVE_TICKS = 100;
	/** Rows copied before each part of the hint shows: its kind, its heading, how far, whether it stirs. */
	static final int KIND_AT = 2, HEADING_AT = 3, FAR_AT = 4, STIRS_AT = 5;
	static final int NEAR = 400, DAY = 1500;
	static final String KEY = "jaymoskingdoms_glyphs";
	/** The runes the rows are written in. */
	static final String RUNES = "ᚠᚢᚦᚨᚱᚲᚷᚹᚺᚾᛁᛃᛇᛈᛉᛊᛏᛒᛖᛗᛚᛜᛞᛟ";
	static final int ROW_RUNES = 14;

	private static final Set<BlockPos> CARVED = new HashSet<>();

	private MonolithGlyphs() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(MonolithGlyphs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> CARVED.clear());
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level)) {
				return InteractionResult.PASS;
			}
			ItemStack held = sp.getItemInHand(hand);
			if (!held.is(Items.WRITABLE_BOOK) || level != Factions.level(level.getServer())) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			Monoliths.Monolith m = Monoliths.nearest(level, pos, Monoliths.PART_RADIUS + 2);
			if (m == null || !Monoliths.part(m.pos(), pos) || !level.getBlockState(pos).is(glyph(m.element()))) {
				return InteractionResult.PASS;
			}
			copy(sp, level, held, m, pos);
			return InteractionResult.SUCCESS;
		});
	}

	/** The carved stone of {@code element}'s glyphs (the Aether's own, through Jaymo's Powers, else vanilla). */
	static Block glyph(Element element) {
		String id;
		Block vanilla;
		switch (element) {
			case HIGHFIELD -> {
				id = "holystone_keystone";
				vanilla = Blocks.CHISELED_QUARTZ_BLOCK;
			}
			case MAGNETIC -> {
				id = "";
				vanilla = Blocks.CHISELED_DEEPSLATE;
			}
			case ARCTIC -> {
				id = "chiseled_packed_ice_bricks";
				vanilla = Blocks.CHISELED_TUFF;
			}
			default -> {
				id = "faded_holystone_keystone";
				vanilla = Blocks.CHISELED_STONE_BRICKS;
			}
		}
		return id.isEmpty() ? vanilla : BuiltInRegistries.BLOCK.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", id)).orElse(vanilla);
	}

	// ---- carving ---------------------------------------------------------------------------------------------------------------

	/** (From {@link Monoliths#raise}, and for older ones when a player comes near) the four rows, carved into its own stone. */
	static void carve(ServerLevel level, Monoliths.Monolith m) {
		CARVED.add(m.pos());
		if (!m.ours()) {
			return;
		}
		BlockState glyph = glyph(m.element()).defaultBlockState();
		RandomSource random = RandomSource.create(m.pos().asLong());
		BlockPos foot = m.pos();
		int y = foot.getY() + ROW_Y;
		for (int face = 0; face < 4; face++) {
			for (int along = -ROW_HALF; along <= ROW_HALF; along++) {
				if (random.nextInt(3) == 0) {
					continue;
				}
				int x = face == 0 ? FACE : face == 1 ? -FACE : along;
				int z = face == 2 ? FACE : face == 3 ? -FACE : along;
				BlockPos p = new BlockPos(foot.getX() + x, y, foot.getZ() + z);
				if (m.element().builtOf(level.getBlockState(p))) {
					level.setBlock(p, glyph, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static boolean carved(ServerLevel level, Monoliths.Monolith m) {
		Block glyph = glyph(m.element());
		BlockPos foot = m.pos();
		int y = foot.getY() + ROW_Y;
		for (int along = -ROW_HALF; along <= ROW_HALF; along++) {
			if (level.getBlockState(new BlockPos(foot.getX() + FACE, y, foot.getZ() + along)).is(glyph)) {
				return true;
			}
		}
		return false;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CARVE_TICKS != 0) {
			return;
		}
		ServerLevel level = Factions.level(server);
		for (ServerPlayer player : level.players()) {
			Monoliths.Monolith m = Monoliths.nearest(level, player.blockPosition(), CARVE_RANGE);
			if (m != null && !CARVED.contains(m.pos()) && level.isLoaded(m.pos())) {
				if (carved(level, m)) {
					CARVED.add(m.pos());
				} else {
					carve(level, m);
				}
			}
		}
	}

	// ---- copying ---------------------------------------------------------------------------------------------------------------

	private static void copy(ServerPlayer sp, ServerLevel level, ItemStack book, Monoliths.Monolith m, BlockPos at) {
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		CompoundTag data = book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		CompoundTag mine = data.getCompoundOrEmpty(KEY);
		ListTag copied = mine.getListOrEmpty("copied");
		String today = m.pos().asLong() + "@" + day;
		for (Tag t : copied) {
			if (t.asString().orElse("").equals(today)) {
				level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 0.6F);
				return;
			}
		}
		WritableBookContent content = book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
		if (content.pages().size() >= WritableBookContent.MAX_PAGES) {
			return;
		}
		int rows = mine.getIntOr("rows", 0) + 1;
		copied.add(StringTag.valueOf(today));
		while (copied.size() > 64) {
			copied.removeFirst();
		}
		mine.putInt("rows", rows);
		mine.put("copied", copied);
		data.put(KEY, mine);
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
		List<Filterable<String>> pages = new ArrayList<>(content.pages());
		pages.add(Filterable.passThrough(page(level, m, at, rows)));
		book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(pages));
		level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
		level.playSound(null, at, SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.7F, 0.9F);
		level.sendParticles(ParticleTypes.ENCHANT, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.6);
	}

	/** A row of {@code m}'s glyphs as copied at {@code at}, and the copier's guesses under it, as many as {@code rows} allow. */
	static String page(ServerLevel level, Monoliths.Monolith m, BlockPos at, int rows) {
		StringBuilder row = new StringBuilder();
		RandomSource random = RandomSource.create(m.pos().asLong() ^ at.asLong());
		for (int i = 0; i < ROW_RUNES; i++) {
			row.append(RUNES.charAt(random.nextInt(RUNES.length())));
			if (i % 4 == 3) {
				row.append(' ');
			}
		}
		StringBuilder page = new StringBuilder(row.toString().trim());
		Monoliths.Monolith next = next(level, m);
		if (rows < KIND_AT) {
			return page.toString();
		}
		page.append("\n\n");
		if (next == null) {
			page.append("(the same marks, over and over. Nothing left to point at?)");
			return page.toString();
		}
		page.append("(it speaks of ").append(kind(next.element())).append(", I think)");
		if (rows >= HEADING_AT) {
			page.append("\n(and the sign for ").append(heading(m.pos(), next.pos())).append(")");
		}
		if (rows >= FAR_AT) {
			double d = Math.sqrt(Monoliths.horizontal(m.pos(), next.pos()));
			page.append("\n(").append(d < NEAR ? "near. a morning's walk" : d < DAY ? "a day's walk, perhaps" : "many days' walk").append(")");
		}
		if (rows >= STIRS_AT) {
			page.append("\n(").append(Monoliths.giantOf(level, next) != null ? "it is awake" : "it sleeps, and waits").append(")");
		}
		return page.toString();
	}

	/** The nearest other monolith whose Giant still stands, or null. */
	static Monoliths.@Nullable Monolith next(ServerLevel level, Monoliths.Monolith from) {
		Monoliths.Monolith best = null;
		double bestD = Double.MAX_VALUE;
		for (Monoliths.Monolith m : Monoliths.all(level)) {
			if (m.state() != Monoliths.AWAKE || m.pos().equals(from.pos())) {
				continue;
			}
			double d = Monoliths.horizontal(m.pos(), from.pos());
			if (d < bestD) {
				best = m;
				bestD = d;
			}
		}
		return best;
	}

	private static String kind(Element e) {
		return switch (e) {
			case HIGHFIELD -> "the high grass";
			case MAGNETIC -> "the stone that pulls";
			case ARCTIC -> "frost";
			case IRRADIATED -> "the sick green light";
		};
	}

	/** Which way {@code to} lies from {@code from}, as the old marks would put it. */
	private static String heading(BlockPos from, BlockPos to) {
		double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
		// 0 = east (sunrise), going round to the south (noon), west (sunset), north (the cold star)
		int octant = Math.floorMod((int)Math.round(Math.atan2(dz, dx) / (Math.PI / 4.0)), 8);
		return switch (octant) {
			case 0 -> "the sunrise";
			case 1 -> "between the sunrise and the noon sun";
			case 2 -> "the noon sun";
			case 3 -> "between the noon sun and the sunset";
			case 4 -> "the sunset";
			case 5 -> "between the sunset and the cold star";
			case 6 -> "the cold star";
			default -> "between the cold star and the sunrise";
		};
	}
}
