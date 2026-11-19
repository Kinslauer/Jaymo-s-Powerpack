package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MasonTable;
import com.jaymoskingdoms.PlanSites;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.488) Laid plans' ghosts ({@link PlanSites}), near enough to see, coloured by what stands in each spot: a block not
 * yet in is its ghost in pale blue; a wrong block where one should go is tinted red; a block standing inside the plan's
 * box where the design has nothing (air, or a structure void) is tinted orange. Right blocks show nothing.
 * <p>
 * Layer view: holding a laid plan, the layer keys (numpad 8 and 2, rebindable) or Ctrl and scroll step through its
 * layers, one at a time, bottom to top; past either end shows them all again. Only that plan's ghosts are thinned.
 */
public final class PlanGhosts {
	/** How far from the eye ghosts are drawn (blocks); the most drawn at once (nearest first). */
	static final int RANGE = 48, MAX_SHOWN = 6000;
	/** How far from the eye blocks standing where a design has nothing are looked for (a box's every spot is looked at, so nearer). */
	static final int EXTRA_RANGE = 24;
	/** How often what's shown is looked at again (ticks); how often the layer is shown on the action bar while held. */
	static final int LOOK_TICKS = 5, LAYER_SHOW_TICKS = 40;
	/** The colours (ARGB, see-through): a missing block's ghost, a wrong block, a block where the design has none. */
	static final int MISSING = 0x6698C0FF, WRONG = 0x80FF3030, EXTRA = 0x80FF9A20;
	/** What each shown spot is. */
	private static final byte KIND_MISSING = 0, KIND_WRONG = 1, KIND_EXTRA = 2;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** One laid plan as sent: its blocks' places and states (filled in as pieces come). */
	private static final class Ghost {
		final BlockPos anchor;
		final List<int[]> rel = new ArrayList<>(), states = new ArrayList<>();
		int parts;
		/** Worked out from what's come (null: to work out again): its box's far corner and which spots in it the design fills. */
		int @Nullable [] size;
		@Nullable BitSet filled;

		Ghost(BlockPos anchor) {
			this.anchor = anchor;
		}

		/** Its box (width, height, depth from the anchor) and filled spots, worked out once per piece received. */
		void shape() {
			if (this.size != null) {
				return;
			}
			int mx = 0, my = 0, mz = 0;
			for (int[] chunk : this.rel) {
				for (int r : chunk) {
					mx = Math.max(mx, r & 0xFF);
					my = Math.max(my, r >> 8 & 0xFF);
					mz = Math.max(mz, r >> 16 & 0xFF);
				}
			}
			int[] size = {mx + 1, my + 1, mz + 1};
			BitSet filled = new BitSet(size[0] * size[1] * size[2]);
			for (int[] chunk : this.rel) {
				for (int r : chunk) {
					filled.set(index(size, r & 0xFF, r >> 8 & 0xFF, r >> 16 & 0xFF));
				}
			}
			this.size = size;
			this.filled = filled;
		}

		static int index(int[] size, int x, int y, int z) {
			return (y * size[2] + z) * size[0] + x;
		}
	}

	private static final Map<UUID, Ghost> GHOSTS = new HashMap<>();
	/** What's drawn this moment: places, states (the ghost's, or the wrong block's) and kinds. */
	private static long[] shownPos = new long[0];
	private static BlockState[] shownStates = new BlockState[0];
	private static byte[] shownKinds = new byte[0];
	private static final Map<BlockState, List<BakedQuad>> QUADS = new HashMap<>();
	private static final Direction[] SIDES = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, null};

	private static KeyMapping layerUp, layerDown;
	/** The layer view: the plan it's for, and its layer (from the bottom, 0 up; -1 shows them all). */
	private static @Nullable UUID layerSite;
	private static int layer = -1;

	private PlanGhosts() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(PlanSites.Ghost.TYPE, (payload, context) -> {
			Ghost g = GHOSTS.get(payload.id());
			if (payload.part() == 0 || g == null || !g.anchor.equals(payload.anchor())) {
				g = new Ghost(payload.anchor());
				GHOSTS.put(payload.id(), g);
			}
			g.rel.add(payload.rel());
			g.states.add(payload.states());
			g.parts = payload.parts();
			g.size = null;
			g.filled = null;
		});
		ClientPlayNetworking.registerGlobalReceiver(PlanSites.Gone.TYPE, (payload, context) -> GHOSTS.remove(payload.id()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GHOSTS.clear();
			shownPos = new long[0];
			shownStates = new BlockState[0];
			shownKinds = new byte[0];
			layerSite = null;
			layer = -1;
		});
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("building"));
		layerUp = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.plan_layer_up", InputConstants.KEY_NUMPAD8, category));
		layerDown = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.plan_layer_down", InputConstants.KEY_NUMPAD2, category));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (minecraft.level == null || minecraft.player == null) {
				return;
			}
			layerKeys(minecraft);
			if (minecraft.player.tickCount % LOOK_TICKS == 0) {
				look(minecraft);
			}
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(PlanGhosts::render);
	}

	// ---- the layer view ---------------------------------------------------------------------------------------------

	/** The laid plan in {@code player}'s hand (main hand first) whose ghosts are here; null if none. */
	private static @Nullable UUID heldSite(LocalPlayer player) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			UUID site = MasonTable.laidSite(stack);
			if (site != null && GHOSTS.containsKey(site)) {
				return site;
			}
		}
		return null;
	}

	private static void layerKeys(Minecraft minecraft) {
		int steps = 0;
		while (layerUp.consumeClick()) {
			steps++;
		}
		while (layerDown.consumeClick()) {
			steps--;
		}
		UUID held = heldSite(minecraft.player);
		if (held == null || !held.equals(layerSite)) {
			// (a new plan in hand starts with all its layers)
			layerSite = held;
			layer = -1;
		}
		if (held == null) {
			return;
		}
		if (steps != 0 && minecraft.gui.screen() == null) {
			step(minecraft, steps);
		} else if (layer >= 0 && minecraft.player.tickCount % LAYER_SHOW_TICKS == 0) {
			showLayer(minecraft);
		}
	}

	/** Ctrl and scroll while holding a laid plan (and not in a stance, whose belt that is) steps its layer. True when it took the scroll. */
	public static boolean scroll(double y) {
		Minecraft minecraft = Minecraft.getInstance();
		if (y == 0.0 || minecraft.player == null || minecraft.gui.screen() != null
			|| !(InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL))
			|| com.jaymoskingdoms.forms.Forms.book(minecraft.player).stance()) {
			return false;
		}
		UUID held = heldSite(minecraft.player);
		if (held == null) {
			return false;
		}
		if (!held.equals(layerSite)) {
			layerSite = held;
			layer = -1;
		}
		step(minecraft, y > 0.0 ? 1 : -1);
		return true;
	}

	/** Steps the layer by {@code steps}: up from all is the bottom layer, down from all the top; past either end is all again. */
	private static void step(Minecraft minecraft, int steps) {
		Ghost g = GHOSTS.get(layerSite);
		if (g == null) {
			return;
		}
		g.shape();
		int height = g.size[1];
		int now = layer;
		for (int i = 0; i < Math.abs(steps); i++) {
			if (steps > 0) {
				now = now < 0 ? 0 : now + 1 >= height ? -1 : now + 1;
			} else {
				now = now < 0 ? height - 1 : now - 1;
			}
		}
		layer = now;
		showLayer(minecraft);
		look(minecraft);
	}

	private static void showLayer(Minecraft minecraft) {
		Ghost g = GHOSTS.get(layerSite);
		if (g == null) {
			return;
		}
		g.shape();
		minecraft.player.sendOverlayMessage(layer < 0 ? Component.translatable("message.jaymoskingdoms.plan_layer.all", g.size[1]).withStyle(ChatFormatting.YELLOW)
			: Component.translatable("message.jaymoskingdoms.plan_layer.one", layer + 1, g.size[1]).withStyle(ChatFormatting.YELLOW));
	}

	// ---- what's shown -----------------------------------------------------------------------------------------------

	/** Works out what to draw: missing, wrong and extra blocks, within range (and in the layer shown), nearest first. */
	private static void look(Minecraft minecraft) {
		if (GHOSTS.isEmpty()) {
			shownPos = new long[0];
			shownStates = new BlockState[0];
			shownKinds = new byte[0];
			QUADS.clear();
			return;
		}
		BlockPos eye = minecraft.player.blockPosition();
		List<long[]> found = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (Map.Entry<UUID, Ghost> e : GHOSTS.entrySet()) {
			Ghost g = e.getValue();
			int onlyY = e.getKey().equals(layerSite) ? layer : -1;
			// the design's blocks: missing or wrong
			for (int k = 0; k < g.rel.size(); k++) {
				int[] rel = g.rel.get(k), states = g.states.get(k);
				for (int i = 0; i < rel.length; i++) {
					int r = rel[i];
					if (onlyY >= 0 && (r >> 8 & 0xFF) != onlyY) {
						continue;
					}
					at.set(g.anchor.getX() + (r & 0xFF), g.anchor.getY() + (r >> 8 & 0xFF), g.anchor.getZ() + (r >> 16 & 0xFF));
					int d = dist(at, eye);
					if (d > RANGE * RANGE) {
						continue;
					}
					BlockState want = Block.stateById(states[i]);
					BlockState here = minecraft.level.getBlockState(at);
					if (PlanSites.matches(here, want)) {
						continue;
					}
					if (here.canBeReplaced()) {
						if (want.getRenderShape() == RenderShape.MODEL) {
							found.add(new long[] {at.asLong(), states[i], d, KIND_MISSING});
						}
					} else if (here.getRenderShape() == RenderShape.MODEL) {
						found.add(new long[] {at.asLong(), Block.getId(here), d, KIND_WRONG});
					}
				}
			}
			// the box's other spots: anything standing where the design has nothing
			g.shape();
			int[] size = g.size;
			int x0 = Math.max(0, eye.getX() - EXTRA_RANGE - g.anchor.getX()), x1 = Math.min(size[0] - 1, eye.getX() + EXTRA_RANGE - g.anchor.getX());
			int y0 = Math.max(0, eye.getY() - EXTRA_RANGE - g.anchor.getY()), y1 = Math.min(size[1] - 1, eye.getY() + EXTRA_RANGE - g.anchor.getY());
			int z0 = Math.max(0, eye.getZ() - EXTRA_RANGE - g.anchor.getZ()), z1 = Math.min(size[2] - 1, eye.getZ() + EXTRA_RANGE - g.anchor.getZ());
			if (onlyY >= 0) {
				y0 = Math.max(y0, onlyY);
				y1 = Math.min(y1, onlyY);
			}
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					for (int x = x0; x <= x1; x++) {
						if (g.filled.get(Ghost.index(size, x, y, z))) {
							continue;
						}
						at.set(g.anchor.getX() + x, g.anchor.getY() + y, g.anchor.getZ() + z);
						int d = dist(at, eye);
						if (d > EXTRA_RANGE * EXTRA_RANGE) {
							continue;
						}
						BlockState here = minecraft.level.getBlockState(at);
						// (water and fire are left be: the design can't ask for them by hand)
						if (here.isAir() || here.getRenderShape() != RenderShape.MODEL || here.is(BlockTags.FIRE)) {
							continue;
						}
						found.add(new long[] {at.asLong(), Block.getId(here), d, KIND_EXTRA});
					}
				}
			}
		}
		if (found.size() > MAX_SHOWN) {
			found.sort((a, b) -> Long.compare(a[2], b[2]));
			found = found.subList(0, MAX_SHOWN);
		}
		long[] pos = new long[found.size()];
		BlockState[] states = new BlockState[found.size()];
		byte[] kinds = new byte[found.size()];
		for (int i = 0; i < pos.length; i++) {
			long[] f = found.get(i);
			pos[i] = f[0];
			states[i] = Block.stateById((int)f[1]);
			kinds[i] = (byte)f[3];
		}
		shownPos = pos;
		shownStates = states;
		shownKinds = kinds;
	}

	private static int dist(BlockPos a, BlockPos b) {
		int dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	/** {@code state}'s model, flattened to quads (kept). */
	private static List<BakedQuad> quads(Minecraft minecraft, BlockState state) {
		return QUADS.computeIfAbsent(state, s -> {
			BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(s);
			List<BlockStateModelPart> parts = new ArrayList<>();
			model.collectParts(RandomSource.create(42L), parts);
			List<BakedQuad> out = new ArrayList<>();
			for (BlockStateModelPart part : parts) {
				for (Direction side : SIDES) {
					out.addAll(part.getQuads(side));
				}
			}
			return List.copyOf(out);
		});
	}

	private static void render(LevelRenderContext context) {
		long[] pos = shownPos;
		BlockState[] states = shownStates;
		byte[] kinds = shownKinds;
		if (pos.length == 0) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
			QuadInstance instance = new QuadInstance();
			instance.setLightCoords(FULL_BRIGHT);
			instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
			for (int i = 0; i < pos.length; i++) {
				boolean ghost = kinds[i] == KIND_MISSING;
				instance.setColor(ghost ? MISSING : kinds[i] == KIND_WRONG ? WRONG : EXTRA);
				PoseStack.Pose p = pose.copy();
				// a ghost a hair small, so it doesn't flicker against what's beside it; a tint a hair large, over the block
				float inset = ghost ? 0.01F : -0.01F;
				p.translate((float)(BlockPos.getX(pos[i]) - camera.x) + inset, (float)(BlockPos.getY(pos[i]) - camera.y) + inset,
					(float)(BlockPos.getZ(pos[i]) - camera.z) + inset);
				float scale = 1.0F - 2.0F * inset;
				p.scale(scale, scale, scale);
				for (BakedQuad quad : quads(minecraft, states[i])) {
					consumer.putBakedQuad(p, quad, instance);
				}
			}
		});
		poseStack.popPose();
	}
}
