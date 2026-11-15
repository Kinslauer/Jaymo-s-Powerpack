package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.RealmMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import xaero.common.HudMod;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementReader;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderProvider;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;
import xaero.hud.minimap.waypoint.thirdparty.ThirdPartyWaypoints;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.lib.client.graphics.XaeroBufferProvider;

/**
 * The Xaero's Minimap side of {@link XaeroRealms}, loaded only when it is: each realm's seat a waypoint named for the
 * realm (and its holder, when another holds it) in the holder's colour, and dots along the realms' borders over the
 * minimap in the same colour. Through Xaero's own API for other mods' waypoints and map elements.
 */
final class XaeroRealmsMap {
	/** How far from the player borders are drawn (chunks). */
	static final int BORDER_CHUNKS = 16;
	/** One border dot every this many blocks. */
	static final int DOT_EVERY = 4;
	private static final Identifier ORIGIN = JaymosKingdoms.id("realms");

	private static boolean added;
	private static Object shownIn;
	private static int shownVersion = -1;

	private XaeroRealmsMap() {
	}

	static void refresh(Minecraft minecraft, List<RealmMap.Realm> realms, int version) {
		if (!added && HudMod.INSTANCE != null && HudMod.INSTANCE.getMinimap() != null) {
			HudMod.INSTANCE.getMinimap().getOverMapRendererHandler().add(new Borders());
			added = true;
		}
		MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
		MinimapWorld world = session == null ? null : session.getWorldManager().getAutoWorld();
		if (world == null || world.getContainer() == null) {
			return;
		}
		if (world.getContainer() == shownIn && version == shownVersion) {
			return;
		}
		shownIn = world.getContainer();
		shownVersion = version;
		ThirdPartyWaypoints waypoints = world.getContainer().getThirdPartyWaypointManager().get(ORIGIN);
		waypoints.clear();
		if (minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) {
			return;
		}
		for (RealmMap.Realm r : realms) {
			BlockPos seat = BlockPos.of(r.seat());
			String name = r.holder().equals(r.name()) ? r.name() : r.name() + " (" + r.holder() + ")";
			String initials = r.name().isEmpty() ? "?" : r.name().substring(0, 1).toUpperCase();
			waypoints.add("realm_" + r.id(), new Waypoint(seat.getX(), seat.getY(), seat.getZ(), name, initials,
				WaypointColor.fromDye(DyeColor.byId(r.dye())), WaypointPurpose.NORMAL));
		}
	}

	/** One dot of a border, in its holder's colour. */
	record Dot(double x, double z, int argb) {
	}

	/** The border dots near the player, worked out again when the realms change or the player crosses a chunk. */
	private static final class Dots extends MinimapElementRenderProvider<Dot, Void> {
		private List<Dot> dots = List.of();
		private int madeFor = -1;
		private long madeAt = Long.MIN_VALUE;
		private int next;

		@Override
		public void begin(MinimapElementRenderLocation location, Void context) {
			Minecraft minecraft = Minecraft.getInstance();
			this.next = 0;
			if (minecraft.player == null || minecraft.level == null || minecraft.level.dimension() != Level.OVERWORLD) {
				this.dots = List.of();
				this.madeFor = -1;
				return;
			}
			long here = minecraft.player.chunkPosition().pack();
			if (this.madeFor != XaeroRealms.version() || this.madeAt != here) {
				this.madeFor = XaeroRealms.version();
				this.madeAt = here;
				this.dots = make(minecraft.player.chunkPosition(), XaeroRealms.realms());
			}
		}

		private static List<Dot> make(ChunkPos here, List<RealmMap.Realm> realms) {
			List<Dot> out = new ArrayList<>();
			for (RealmMap.Realm r : realms) {
				Set<Long> land = new HashSet<>(r.claims());
				int argb = 0xFF000000 | DyeColor.byId(r.dye()).getTextureDiffuseColor();
				for (long claim : land) {
					ChunkPos c = ChunkPos.unpack(claim);
					if (Math.abs(c.x() - here.x()) > BORDER_CHUNKS || Math.abs(c.z() - here.z()) > BORDER_CHUNKS) {
						continue;
					}
					int x0 = c.x() << 4, z0 = c.z() << 4;
					for (int i = DOT_EVERY / 2; i < 16; i += DOT_EVERY) {
						if (!land.contains(ChunkPos.pack(c.x(), c.z() - 1))) {
							out.add(new Dot(x0 + i, z0, argb));
						}
						if (!land.contains(ChunkPos.pack(c.x(), c.z() + 1))) {
							out.add(new Dot(x0 + i, z0 + 16, argb));
						}
						if (!land.contains(ChunkPos.pack(c.x() - 1, c.z()))) {
							out.add(new Dot(x0, z0 + i, argb));
						}
						if (!land.contains(ChunkPos.pack(c.x() + 1, c.z()))) {
							out.add(new Dot(x0 + 16, z0 + i, argb));
						}
					}
				}
			}
			return List.copyOf(out);
		}

		@Override
		public boolean hasNext(MinimapElementRenderLocation location, Void context) {
			return this.next < this.dots.size();
		}

		@Override
		public Dot getNext(MinimapElementRenderLocation location, Void context) {
			return this.dots.get(this.next++);
		}

		@Override
		public void end(MinimapElementRenderLocation location, Void context) {
		}
	}

	/** Where a dot is; it has no menu and can't be clicked. */
	private static final class DotReader extends MinimapElementReader<Dot, Void> {
		@Override
		public boolean isHidden(Dot dot, Void context) {
			return false;
		}

		@Override
		public double getRenderX(Dot dot, Void context, float partial) {
			return dot.x();
		}

		@Override
		public double getRenderY(Dot dot, Void context, float partial) {
			Minecraft minecraft = Minecraft.getInstance();
			return minecraft.player == null ? 64 : minecraft.player.getY();
		}

		@Override
		public double getRenderZ(Dot dot, Void context, float partial) {
			return dot.z();
		}

		@Override
		public int getInteractionBoxLeft(Dot dot, Void context, float partial) {
			return 0;
		}

		@Override
		public int getInteractionBoxRight(Dot dot, Void context, float partial) {
			return 0;
		}

		@Override
		public int getInteractionBoxTop(Dot dot, Void context, float partial) {
			return 0;
		}

		@Override
		public int getInteractionBoxBottom(Dot dot, Void context, float partial) {
			return 0;
		}

		@Override
		public int getRenderBoxLeft(Dot dot, Void context, float partial) {
			return -1;
		}

		@Override
		public int getRenderBoxRight(Dot dot, Void context, float partial) {
			return 1;
		}

		@Override
		public int getRenderBoxTop(Dot dot, Void context, float partial) {
			return -1;
		}

		@Override
		public int getRenderBoxBottom(Dot dot, Void context, float partial) {
			return 1;
		}

		@Override
		public int getLeftSideLength(Dot dot, Minecraft minecraft) {
			return 0;
		}

		@Override
		public String getMenuName(Dot dot) {
			return "";
		}

		@Override
		public String getFilterName(Dot dot) {
			return "";
		}

		@Override
		public int getMenuTextFillLeftPadding(Dot dot) {
			return 0;
		}

		@Override
		public int getRightClickTitleBackgroundColor(Dot dot) {
			return 0;
		}

		@Override
		public boolean shouldScaleBoxWithOptionalScale() {
			return false;
		}
	}

	/** Draws the dots over the minimap; a dot off its edge isn't pinned to the rim. */
	private static final class Borders extends MinimapElementRenderer<Dot, Void> {
		Borders() {
			super(new DotReader(), new Dots(), null);
		}

		@Override
		public boolean renderElement(Dot dot, boolean highlighted, boolean outOfBounds, double depth, float scale, double partialX,
			double partialY, MinimapElementRenderInfo info, MinimapElementGraphics graphics, XaeroBufferProvider buffers) {
			if (outOfBounds) {
				return false;
			}
			graphics.fill(-1, -1, 1, 1, dot.argb());
			return true;
		}

		@Override
		public void preRender(MinimapElementRenderInfo info, XaeroBufferProvider buffers, MultiTextureRenderTypeRendererProvider renderers) {
		}

		@Override
		public void postRender(MinimapElementRenderInfo info, XaeroBufferProvider buffers, MultiTextureRenderTypeRendererProvider renderers) {
		}

		@Override
		public boolean shouldRender(MinimapElementRenderLocation location) {
			return location == MinimapElementRenderLocation.OVER_MINIMAP;
		}
	}
}
