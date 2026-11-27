package com.jaymospowers.power.zfighter.pod;

import com.jaymospowers.power.zfighter.PodScanner;
import com.jaymospowers.power.zfighter.fuel.FuelWorks;
import com.jaymospowers.power.zfighter.sky.PodScope;

/**
 * Pod travel, the Unbound way: everything the pod gained beside its parts, registered in one place. Modules and their sockets
 * ({@link PodModules}), the return stage ({@link PodReturn}), the logbook ({@link PodLog}), stardust ({@link PodStardust}),
 * landing beacons ({@link PodBeacons}), the Fuel Works ({@link FuelWorks}), the scanner's needle ({@link PodScanner}) and the
 * telescope's watch ({@link PodScope}). Called once, at the end of {@link PodParts#init}.
 */
final class PodTravel {
	private PodTravel() {
	}

	static void init() {
		PodModules.init();
		PodFlight.init();
		PodPost.init();
		PodReturn.init();
		PodLog.init();
		PodStardust.init();
		PodBeacons.init();
		FuelWorks.init();
		PodScanner.init();
		PodScope.init();
	}
}
