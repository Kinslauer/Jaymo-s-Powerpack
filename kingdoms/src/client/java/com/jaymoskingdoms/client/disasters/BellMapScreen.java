package com.jaymoskingdoms.client.disasters;

import com.jaymoskingdoms.disasters.LastBell;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jspecify.annotations.Nullable;

/**
 * The Last Bell's map ({@link LastBell}): the land round its holder as far as it reaches, they at its middle. A filled
 * map in the other hand shows what it has drawn; past that, the land the eye can see now; past that, dark. Where they
 * click is where it falls. No words on it.
 */
final class BellMapScreen extends Screen {
	/** Cells across; the frame and the holder's mark. */
	static final int CELLS = 64;
	static final int FRAME = 0xFF2A0A06, UNKNOWN = 0xFF0E0C0C, HOLDER = 0xFFFFFFFF, PICK = 0xFFE03020;

	private final int range;
	private final int[] colours = new int[CELLS * CELLS];
	/** Whether the bell can be rung over each cell: only over land that is loaded now (the server will take no other). */
	private final boolean[] open = new boolean[CELLS * CELLS];
	private int originX, originZ;

	BellMapScreen(int range) {
		super(Component.empty());
		this.range = range;
	}

	@Override
	protected void init() {
		ClientLevel level = this.minecraft.level;
		if (level == null || this.minecraft.player == null) {
			return;
		}
		this.originX = this.minecraft.player.getBlockX();
		this.originZ = this.minecraft.player.getBlockZ();
		MapItemSavedData map = held(level);
		int step = this.range * 2 / CELLS;
		for (int cz = 0; cz < CELLS; cz++) {
			for (int cx = 0; cx < CELLS; cx++) {
				int x = this.originX - this.range + cx * step + step / 2, z = this.originZ - this.range + cz * step + step / 2;
				this.colours[cx + cz * CELLS] = colour(level, map, x, z);
				this.open[cx + cz * CELLS] = level.getChunkSource().hasChunk(x >> 4, z >> 4);
			}
		}
		level.playLocalSound(this.minecraft.player.getX(), this.minecraft.player.getY(), this.minecraft.player.getZ(), SoundEvents.BELL_RESONATE,
			net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.4F, false);
	}

	private @Nullable MapItemSavedData held(ClientLevel level) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = this.minecraft.player.getItemInHand(hand);
			MapId id = stack.is(Items.FILLED_MAP) ? stack.get(DataComponents.MAP_ID) : null;
			if (id != null) {
				return level.getMapData(id);
			}
		}
		return null;
	}

	/** What's at (x, z): the map's colour there, else the land's if the eye can see it, else dark. */
	private static int colour(ClientLevel level, @Nullable MapItemSavedData map, int x, int z) {
		if (map != null) {
			int per = 1 << map.scale;
			int px = Math.floorDiv(x - map.centerX, per) + 64, pz = Math.floorDiv(z - map.centerZ, per) + 64;
			if (px >= 0 && px < 128 && pz >= 0 && pz < 128) {
				int packed = map.colors[px + pz * 128] & 0xFF;
				if (packed >> 2 != 0) {
					return MapColor.getColorFromPackedId(packed);
				}
			}
		}
		if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) {
			return UNKNOWN;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = level.getBlockState(pos);
		if (state.getFluidState().is(FluidTags.WATER)) {
			return MapColor.WATER.calculateARGBColor(MapColor.Brightness.NORMAL);
		}
		MapColor.Brightness light = y > level.getSeaLevel() + 40 ? MapColor.Brightness.HIGH : y < level.getSeaLevel() + 4 ? MapColor.Brightness.LOW
			: MapColor.Brightness.NORMAL;
		int c = state.getMapColor(level, pos).calculateARGBColor(light);
		return c == 0 ? UNKNOWN : c;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int cell() {
		return Math.max(1, (Math.min(this.width, this.height) - 32) / CELLS);
	}

	private int left() {
		return (this.width - cell() * CELLS) / 2;
	}

	private int top() {
		return (this.height - cell() * CELLS) / 2;
	}

	/** The cell under (mx, my), or -1. */
	private int cellAt(double mx, double my) {
		int c = cell(), cx = Mth.floor((mx - left()) / c), cz = Mth.floor((my - top()) / c);
		return cx < 0 || cz < 0 || cx >= CELLS || cz >= CELLS ? -1 : cx + cz * CELLS;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int at = cellAt(event.x(), event.y());
		if (at < 0 || !this.open[at]) {
			return super.mouseClicked(event, doubleClick);
		}
		int step = this.range * 2 / CELLS;
		int x = this.originX - this.range + at % CELLS * step + step / 2, z = this.originZ - this.range + at / CELLS * step + step / 2;
		ClientPlayNetworking.send(new LastBell.Ring(x, z));
		this.onClose();
		return true;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int c = cell(), x0 = left(), y0 = top(), size = c * CELLS;
		graphics.fill(x0 - 4, y0 - 4, x0 + size + 4, y0 + size + 4, FRAME);
		for (int i = 0; i < this.colours.length; i++) {
			int x = x0 + i % CELLS * c, y = y0 + i / CELLS * c;
			graphics.fill(x, y, x + c, y + c, this.colours[i]);
			if (!this.open[i]) {
				// far land the bell can't reach now: shown (from a map) but dark
				graphics.fill(x, y, x + c, y + c, 0xB0000000);
			}
		}
		// a red cast over it all, deeper toward the edge
		graphics.fill(x0, y0, x0 + size, y0 + size, ARGB.color(40, 0x8A, 0x14, 0x08));
		int mid = CELLS / 2;
		graphics.fill(x0 + mid * c - 1, y0 + mid * c - 3, x0 + mid * c + 1, y0 + mid * c + 3, HOLDER);
		graphics.fill(x0 + mid * c - 3, y0 + mid * c - 1, x0 + mid * c + 3, y0 + mid * c + 1, HOLDER);
		int at = cellAt(mouseX, mouseY);
		if (at >= 0 && this.open[at]) {
			graphics.outline(x0 + at % CELLS * c - 1, y0 + at / CELLS * c - 1, c + 2, c + 2, PICK);
		}
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}
}
