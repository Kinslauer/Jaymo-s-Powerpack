package com.jaymoskingdoms.client.mixin.mca;

import com.google.gson.JsonElement;
import com.jaymoskingdoms.Professions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.conczin.mca.client.gui.MCAButton;
import net.conczin.mca.client.gui.MCAScreens;
import net.conczin.mca.resources.Resources;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.5.21) MCA's Professions menu (villager, guard, archer) also offers the settlement's jobs: builder, farmer, miner,
 * woodcutter, one row each below MCA's own. The server answers them ({@code McaCommandMixin}). (0.5.29) The main page
 * offers Quests.
 */
@Mixin(MCAScreens.class)
abstract class McaScreensMixin {
	@Shadow
	@Final
	private Map<String, MCAButton[]> buttons;

	/** (0.5.29) The main page's Quests button. */
	private static final String QUESTS = "gui.button.quests";
	/** (0.7.53) A tailor's Change Clothes button ({@code Tailors}). */
	private static final String TAILOR = "gui.button.tailor";
	/** How many rows of buttons MCA's own main page has (talk ... family tree): Quests goes under them. */
	private static final int MAIN_ROWS = 6;

	/** How many rows of buttons MCA's own Professions page has. */
	private static final int MCA_ROWS = 3;

	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void jaymoskingdoms$jobs(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
		jaymoskingdoms$quests();
		MCAButton[] page = this.buttons.get("professions");
		if (page == null) {
			return;
		}
		// (MCA's own: the back arrow and villager on row 0, guard and archer on rows 1 and 2)
		int row = MCA_ROWS - 1;
		List<MCAButton> all = new ArrayList<>(Arrays.asList(page));
		StringBuilder json = new StringBuilder("[");
		for (String job : Professions.JOBS) {
			String id = "gui.button.profession." + job;
			if (all.stream().anyMatch(b -> b.identifier().equals(id))) {
				continue;
			}
			row++;
			json.append(json.length() > 1 ? "," : "").append("{\"identifier\":\"").append(id).append("\",\"x\":-90,\"y\":10,\"row\":").append(row)
				.append(",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"\"}");
		}
		all.addAll(Arrays.asList(Resources.GSON.fromJson(json.append("]").toString(), MCAButton[].class)));
		this.buttons.put("professions", all.toArray(MCAButton[]::new));
	}

	/** (0.5.29) The main page's Quests button, under MCA's own rows (the server opens the villager's quests). */
	private void jaymoskingdoms$quests() {
		MCAButton[] page = this.buttons.get("main");
		if (page == null || Arrays.stream(page).anyMatch(b -> b.identifier().equals(QUESTS))) {
			return;
		}
		int row = MAIN_ROWS;
		// (0.7.53) and Change Clothes under it, shown only on a tailor (McaTailorButtonMixin)
		String json = "[{\"identifier\":\"" + QUESTS + "\",\"x\":-90,\"y\":10,\"row\":" + row
			+ ",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"adult\",\"hideOnFail\":true},"
			+ "{\"identifier\":\"" + TAILOR + "\",\"x\":-90,\"y\":10,\"row\":" + (row + 1)
			+ ",\"width\":80,\"height\":20,\"notifyServer\":true,\"targetServer\":false,\"constraints\":\"adult\",\"hideOnFail\":true}]";
		List<MCAButton> all = new ArrayList<>(Arrays.asList(page));
		all.addAll(Arrays.asList(Resources.GSON.fromJson(json, MCAButton[].class)));
		this.buttons.put("main", all.toArray(MCAButton[]::new));
	}
}
