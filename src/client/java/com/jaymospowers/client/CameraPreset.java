package com.jaymospowers.client;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Camera Utils (Max Henkel), if it's installed: its second custom third-person view, on F7, is set once per install to
 * Andy's preset ({@link #PRESET}: 3.4 blocks back, 0.1 up, 0.9 to the side, tipped 3 degrees, GUI kept). It reads its
 * config in its own client entrypoint, so the file is written here, before any mod starts. After that, players' own
 * changes (Right Alt + F7) are never touched.
 *
 * <p>While one of its views (or its detached camera) is on, the Web/Gravity shoulder view stands aside
 * ({@link #viewActive()}); its state is read by reflection, so it isn't a compile dependency.
 */
public final class CameraPreset implements PreLaunchEntrypoint {
	/** Bump to set the preset again (for a new preset in a later pack version). */
	private static final String MARKER = "jaymospowers-camera-preset-v1";
	/** Camera Utils' config keys for its view 2 (F7) and the values to put there. */
	private static final Map<String, String> PRESET = new LinkedHashMap<>();

	static {
		PRESET.put("third_person_cam_2_offset_x", "-3.4");
		PRESET.put("third_person_cam_2_offset_y", "0.1");
		PRESET.put("third_person_cam_2_offset_z", "0.9");
		PRESET.put("third_person_cam_2_rotation_x", "-3.0");
		PRESET.put("third_person_cam_2_inverted", "false");
		PRESET.put("third_person_cam_2_hide_gui", "false");
	}

	private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("camerautils");
	private static final String CONFIG_CLASS = "de.maxhenkel.camerautils.config.ClientConfig";
	private static VarHandle thirdPersonCam, detached;
	private static boolean looked;

	@Override
	public void onPreLaunch() {
		FabricLoader loader = FabricLoader.getInstance();
		if (!LOADED || loader.getEnvironmentType() != EnvType.CLIENT) {
			return;
		}
		Path marker = loader.getConfigDir().resolve(MARKER);
		if (Files.exists(marker)) {
			return;
		}
		Path config = loader.getConfigDir().resolve("camerautils").resolve("camerautils.properties");
		try {
			List<String> lines = Files.exists(config) ? new ArrayList<>(Files.readAllLines(config)) : new ArrayList<>();
			Map<String, String> left = new LinkedHashMap<>(PRESET);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				int eq = line.indexOf('=');
				if (!line.startsWith("#") && eq > 0) {
					String value = left.remove(line.substring(0, eq).trim());
					if (value != null) {
						lines.set(i, line.substring(0, eq).trim() + "=" + value);
					}
				}
			}
			left.forEach((key, value) -> lines.add(key + "=" + value));
			Files.createDirectories(config.getParent());
			Files.write(config, lines);
			Files.writeString(marker, "Jaymo's Powers set Camera Utils' F7 view to the pack's preset. Delete this file to do it again.\n");
		} catch (IOException e) {
			com.jaymospowers.JaymosPowers.LOGGER.warn("Couldn't set Camera Utils' F7 preset in {}", config, e);
		}
	}

	/** One of Camera Utils' custom views (F6/F7) or its detached camera is on right now. */
	public static boolean viewActive() {
		if (!LOADED) {
			return false;
		}
		if (!looked) {
			looked = true;
			try {
				MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Class.forName(CONFIG_CLASS), MethodHandles.lookup());
				thirdPersonCam = lookup.findStaticVarHandle(lookup.lookupClass(), "thirdPersonCam", int.class);
				detached = lookup.findStaticVarHandle(lookup.lookupClass(), "detached", boolean.class);
			} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
				thirdPersonCam = null;
				detached = null;
			}
		}
		if (thirdPersonCam == null) {
			return false;
		}
		try {
			return (int)thirdPersonCam.get() >= 0 || (boolean)detached.get();
		} catch (RuntimeException e) {
			return false;
		}
	}
}
