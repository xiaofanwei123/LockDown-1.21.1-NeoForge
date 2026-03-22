package com.xfw.lockdown;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(LockDown.MODID)
public class LockDown {
    public static final String MODID = "lockdown";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LockDown(@SuppressWarnings("unused") IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC, MODID + ".toml");
        NeoForge.EVENT_BUS.addListener(TemplateSeedResolver::onServerStarting);
        NeoForge.EVENT_BUS.addListener(ConfiguredSpawnHandler::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ConfiguredSpawnHandler::onPlayerRespawnPosition);
    }

}
