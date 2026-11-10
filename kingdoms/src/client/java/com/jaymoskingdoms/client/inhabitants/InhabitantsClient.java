package com.jaymoskingdoms.client.inhabitants;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.jaymoskingdoms.SkeletonLord;
import com.jaymoskingdoms.inhabitants.BarrowBeetle;
import com.jaymoskingdoms.inhabitants.Draugr;
import com.jaymoskingdoms.inhabitants.Inhabitants;
import com.jaymoskingdoms.monsters.Monsters;
import com.jaymoskingdoms.monsters.SilverSkeleton;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/**
 * The renderers of Kingdoms' GeckoLib mobs ({@link Inhabitants}, and the silver skeleton and the Skeleton Lord). One line
 * per mob, an {@link InhabitantRenderer} with its file name, size, shadow and head bone; a mob whose look depends on its
 * state passes that state into the render state ({@code addRenderData}) and reads it back when its bones are set.
 */
public final class InhabitantsClient {
	/** The barrow beetle and the draugr are drawn at their models' size. */
	static final float BEETLE_SHADOW = 0.3F, DRAUGR_SHADOW = 0.5F, SILVER_SHADOW = 0.6F;
	/** The Skeleton Lord's model stands 5 blocks to its crown (8 to the staff's tip): drawn to his 2.9-block height. */
	static final float LORD_SCALE = 0.55F, LORD_SHADOW = 0.9F;
	/** The Skeleton Lord's crystal bones, shown only while he sleeps as a statue. */
	static final String[] CRYSTALS = {"head_crystals", "crystal_brow", "crystal_hand", "crystal_fingerbase", "crystal_fingertips", "crystal_thumbbase",
		"crystal_thumbtip", "crystal_arm", "crystal_chest", "crystal_waist", "crystal_leg", "crystalized_pauldron", "R_crystalized_pauldron"};
	/** His laugh works his jaw this far (radians), this fast. */
	static final float LAUGH_JAW = 0.35F, LAUGH_JAW_SPEED = 0.9F;

	/**
	 * A draugr's tail (Warrior-born, every one of them), mangled in one of these ways, picked by its id: as it was, bent hard
	 * to one side, snapped and hanging from the break, cut through, gone at the root, hanging dead, broken twice, the tip
	 * gone. The bends (degrees, as GeckoLib turns a bone: +x hangs it lower) are added to whatever the walk or idle has it doing.
	 */
	enum DraugrTail { WHOLE, BENT, SNAPPED, HALF_CUT, ROOT_STUMP, LIMP, ZIGZAG, TIP_GONE }
	static final String[] DRAUGR_TAIL = {"tail_0", "tail_1", "tail_2", "tail_3", "tail_4"};
	/** BENT's sideways bend; SNAPPED's drop at the break (the rest kept straight down); LIMP's droop; ZIGZAG's up and down. */
	static final float TAIL_BEND = 60.0F, TAIL_SNAP = 55.0F, TAIL_STRAIGHT = 10.0F, TAIL_LIMP = 30.0F, TAIL_ZIG = -45.0F, TAIL_ZAG = 70.0F;

	static final DataTicket<Boolean> DRAUGR_AXE = DataTicket.create("jaymoskingdoms_draugr_axe", Boolean.class);
	static final DataTicket<Integer> DRAUGR_TAIL_ROLL = DataTicket.create("jaymoskingdoms_draugr_tail", Integer.class);
	static final DataTicket<Boolean> LORD_DORMANT = DataTicket.create("jaymoskingdoms_lord_dormant", Boolean.class);
	static final DataTicket<Float> LORD_SINCE_WOKE = DataTicket.create("jaymoskingdoms_lord_since_woke", Float.class);

	private InhabitantsClient() {
	}

	public static void register() {
		EntityRenderers.register(Inhabitants.BARROW_BEETLE,
			context -> new InhabitantRenderer<BarrowBeetle>(context, "barrow_beetle", 1.0F, BEETLE_SHADOW, "head"));
		EntityRenderers.register(Inhabitants.DRAUGR, DraugrRenderer::new);
		EntityRenderers.register(Monsters.SILVER_SKELETON,
			context -> new InhabitantRenderer<SilverSkeleton>(context, "silver_skeleton", 1.0F, SILVER_SHADOW, "head"));
		EntityRenderers.register(SkeletonLord.TYPE, LordRenderer::new);
	}

	/** Its sword and shield, or its axe; its tail, mangled its own way. */
	static final class DraugrRenderer extends InhabitantRenderer<Draugr> {
		DraugrRenderer(EntityRendererProvider.Context context) {
			super(context, "draugr", 1.0F, DRAUGR_SHADOW, "head");
		}

		@Override
		public void addRenderData(Draugr draugr, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(draugr, related, state, partialTick);
			state.addGeckolibData(DRAUGR_AXE, draugr.axe());
			state.addGeckolibData(DRAUGR_TAIL_ROLL, draugr.getUUID().hashCode());
		}

		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean axe = info.getOrDefaultGeckolibData(DRAUGR_AXE, false);
			snapshots.ifPresent("axe", bone -> bone.skipRender(!axe));
			snapshots.ifPresent("sword", bone -> bone.skipRender(axe));
			snapshots.ifPresent("shield", bone -> bone.skipRender(axe));
			int roll = info.getOrDefaultGeckolibData(DRAUGR_TAIL_ROLL, 0);
			DraugrTail tail = DraugrTail.values()[Math.floorMod(roll, DraugrTail.values().length)];
			int side = (roll >>> 8 & 1) == 0 ? 1 : -1;
			switch (tail) {
				case BENT -> turn(snapshots, 2, 0.0F, side * TAIL_BEND);
				case SNAPPED -> {
					turn(snapshots, 2, TAIL_SNAP, 0.0F);
					turn(snapshots, 3, TAIL_STRAIGHT, 0.0F);
					turn(snapshots, 4, TAIL_STRAIGHT, 0.0F);
				}
				case HALF_CUT -> cut(snapshots, 3);
				case ROOT_STUMP -> cut(snapshots, 1);
				case LIMP -> {
					turn(snapshots, 0, TAIL_LIMP, 0.0F);
					for (int i = 1; i < DRAUGR_TAIL.length; i++) {
						turn(snapshots, i, TAIL_STRAIGHT, 0.0F);
					}
				}
				case ZIGZAG -> {
					turn(snapshots, 1, TAIL_ZIG, 0.0F);
					turn(snapshots, 2, TAIL_ZAG, 0.0F);
				}
				case TIP_GONE -> cut(snapshots, 4);
				default -> {
				}
			}
		}

		/** Bends a tail segment (and so all past it) by these degrees more. */
		private static void turn(BoneSnapshots snapshots, int segment, float down, float aside) {
			snapshots.ifPresent(DRAUGR_TAIL[segment], bone -> bone.setRotX(bone.getRotX() + down * Mth.DEG_TO_RAD)
				.setRotY(bone.getRotY() + aside * Mth.DEG_TO_RAD));
		}

		/** The tail ends before this segment. */
		private static void cut(BoneSnapshots snapshots, int segment) {
			snapshots.ifPresent(DRAUGR_TAIL[segment], bone -> bone.skipRender(true).skipChildrenRender(true));
		}
	}

	/** His crystals only while he sleeps; his jaw working while he laughs. */
	static final class LordRenderer extends InhabitantRenderer<SkeletonLord> {
		LordRenderer(EntityRendererProvider.Context context) {
			super(context, "skeleton_lord", LORD_SCALE, LORD_SHADOW, "head");
		}

		@Override
		public void addRenderData(SkeletonLord lord, Void related, LivingEntityRenderState state, float partialTick) {
			super.addRenderData(lord, related, state, partialTick);
			state.addGeckolibData(LORD_DORMANT, lord.dormant());
			state.addGeckolibData(LORD_SINCE_WOKE, lord.sinceWoke() + partialTick);
		}

		@Override
		public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> info, BoneSnapshots snapshots) {
			super.adjustModelBonesForRender(info, snapshots);
			boolean dormant = info.getOrDefaultGeckolibData(LORD_DORMANT, false);
			for (String crystal : CRYSTALS) {
				snapshots.ifPresent(crystal, bone -> bone.skipRender(!dormant));
			}
			float sinceWoke = info.getOrDefaultGeckolibData(LORD_SINCE_WOKE, 1000.0F);
			if (!dormant && sinceWoke < SkeletonLord.WAKE_TICKS) {
				snapshots.ifPresent("jaw", bone -> bone.setRotX(bone.getRotX() + Math.abs(Mth.sin(sinceWoke * LAUGH_JAW_SPEED)) * LAUGH_JAW));
			}
		}
	}
}
