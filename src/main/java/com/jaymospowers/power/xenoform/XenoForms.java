package com.jaymospowers.power.xenoform;

import com.jaymospowers.power.xenoform.forms.Blur;
import com.jaymospowers.power.xenoform.forms.Bulwark;
import com.jaymospowers.power.xenoform.forms.Pyre;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Every alien the band knows, in dial order. The first {@link #STARTERS} are the ones a fresh band comes with one of;
 * the rest are scanned from the creatures that carry their DNA. Stage 1 ships Bulwark, Pyre and Blur; Prism, Wisp,
 * Gremlin, and the two Andy added (one scanned from a Warden, one from the Ender Dragon) follow in stage 2.
 */
public final class XenoForms {
	private static final Map<String, XenoForm> ALL = new LinkedHashMap<>();
	/** How many of the first forms a new band may start with. */
	public static final int STARTERS = 3;

	public static final XenoForm BULWARK = register(new Bulwark());
	public static final XenoForm PYRE = register(new Pyre());
	public static final XenoForm BLUR = register(new Blur());

	private XenoForms() {
	}

	private static XenoForm register(XenoForm form) {
		ALL.put(form.id, form);
		return form;
	}

	public static @Nullable XenoForm get(@Nullable String id) {
		return id == null ? null : ALL.get(id);
	}

	public static List<XenoForm> all() {
		return List.copyOf(ALL.values());
	}

	/** The alien whose DNA {@code creature} carries, or null. */
	public static @Nullable XenoForm scanOf(Entity creature) {
		for (XenoForm form : ALL.values()) {
			if (form.scans(creature)) {
				return form;
			}
		}
		return null;
	}
}
