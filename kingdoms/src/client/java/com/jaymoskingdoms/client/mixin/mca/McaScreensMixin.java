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
 * woodcutter, one row each below MCA's own. The server answers them ({@code McaCommandMixin}).
 */
@Mixin(MCAScreens.class)
abstract class McaScreensMixin {
	@Shadow
	@Final
	private Map<String, MCAButton[]> buttons;

	/** How many rows of buttons MCA's own Professions page has. */
	private static final int MCA_ROWS = 3;

	@Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V", at = @At("TAIL"))
	private void jaymoskingdoms$jobs(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
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
}
