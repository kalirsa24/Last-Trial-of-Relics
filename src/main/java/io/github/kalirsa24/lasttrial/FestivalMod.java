package io.github.kalirsa24.lasttrial;

import io.github.kalirsa24.lasttrial.command.FestivalCommands;
import io.github.kalirsa24.lasttrial.core.FestivalConfig;
import io.github.kalirsa24.lasttrial.core.ServerEvents;
import io.github.kalirsa24.lasttrial.network.FestivalNetworking;
import io.github.kalirsa24.lasttrial.registry.ModBlockEntities;
import io.github.kalirsa24.lasttrial.registry.ModBlocks;
import io.github.kalirsa24.lasttrial.registry.ModCreativeTabs;
import io.github.kalirsa24.lasttrial.registry.ModEntities;
import io.github.kalirsa24.lasttrial.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FestivalMod implements ModInitializer {

	public static final String MOD_ID = "lasttrial";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		FestivalConfig.load();

		ModBlocks.register();
		ModItems.register();
		ModBlockEntities.register();
		ModEntities.register();
		ModItems.addToCreativeTabs();
		ModCreativeTabs.register();

		FestivalNetworking.registerServerHandlers();

		FestivalCommands.register();
		ServerEvents.register();

		LOGGER.info("[Festival] Празднество загружено");
	}
}
