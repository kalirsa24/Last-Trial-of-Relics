package com.example.festival;

import com.example.festival.command.FestivalCommands;
import com.example.festival.core.FestivalConfig;
import com.example.festival.core.ServerEvents;
import com.example.festival.network.FestivalNetworking;
import com.example.festival.registry.ModBlockEntities;
import com.example.festival.registry.ModBlocks;
import com.example.festival.registry.ModCreativeTabs;
import com.example.festival.registry.ModEntities;
import com.example.festival.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FestivalMod implements ModInitializer {

	public static final String MOD_ID = "festival";
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
