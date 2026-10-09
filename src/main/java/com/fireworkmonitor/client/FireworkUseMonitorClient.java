package com.fireworkmonitor.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint: activates the client-side use observer (see {@code ClientGameModeMixin}).
 *
 * <p>The detection itself happens in the client mixins; this initializer only reports that the
 * client-side half of the monitor is active.</p>
 */
public class FireworkUseMonitorClient implements ClientModInitializer
{
	public static final Logger LOGGER = LoggerFactory.getLogger("FireworkUseMonitor");

	@Override
	public void onInitializeClient()
	{
		boolean dev = FabricLoader.getInstance().isDevelopmentEnvironment();
		LOGGER.info("Firework Use Monitor client observer active{}", dev ? " (development environment)" : "");
	}
}
