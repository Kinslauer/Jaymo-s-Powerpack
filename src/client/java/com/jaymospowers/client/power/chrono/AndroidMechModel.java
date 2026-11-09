package com.jaymospowers.client.power.chrono;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.chrono.future.FutureMech;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * The android mech's shared GeckoLib model. The editable source is {@code art/android_mech.bbmodel}, generated with
 * its keyframes by {@code tools/gen_android_mech_gecko.py}; the renderer hides optional parts and supplies the skin for
 * the Warden, Bulwark, Strider or Reclaimer build. Its visor, core and lights keep the original full-bright overlays.
 */
public class AndroidMechModel extends DefaultedEntityGeoModel<FutureMech> {
	static final String[] BUILDS = {"warden", "bulwark", "strider", "reclaimer"};
	static final Identifier[] SKINS = new Identifier[BUILDS.length];
	static final Identifier[] GLOW = new Identifier[BUILDS.length];
	static final DataTicket<Integer> BUILD = DataTicket.create("jaymospowers_android_mech_build", Integer.class);
	static final DataTicket<Boolean> AIMING = DataTicket.create("jaymospowers_android_mech_aiming", Boolean.class);
	static final DataTicket<Boolean> SMASHING = DataTicket.create("jaymospowers_android_mech_smashing", Boolean.class);

	static {
		for (int i = 0; i < BUILDS.length; i++) {
			SKINS[i] = JaymosPowers.id("textures/entity/future/android_mech_" + BUILDS[i] + ".png");
			GLOW[i] = JaymosPowers.id("textures/entity/future/android_mech_" + BUILDS[i] + "_eyes.png");
		}
	}

	public AndroidMechModel() {
		super(JaymosPowers.id("android_mech"));
	}

	@Override
	public Identifier getTextureResource(GeoRenderState state) {
		return SKINS[state.getOrDefaultGeckolibData(BUILD, 0)];
	}

	/** Which builds (a bit each) show an optional part; parts not listed are on every build. */
	static final Map<String, Integer> PART_BUILDS = Map.ofEntries(
		// builds:start
			Map.entry("exhaust_r", 11),
			Map.entry("exhaust_l", 11),
			Map.entry("jets", 4),
			Map.entry("shell_pod", 2),
			Map.entry("antenna", 4),
			Map.entry("dome", 8),
			Map.entry("cannon_l", 5),
			Map.entry("claw_a", 10),
			Map.entry("claw_b", 10),
			Map.entry("claw_c", 10),
			Map.entry("shield", 2)
		// builds:end
	);
}
