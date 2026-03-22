package com.xfw.lockdown;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

public final class ConfiguredSpawnHandler {
    private ConfiguredSpawnHandler() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ConfiguredSpawn configuredSpawn = ConfiguredSpawn.resolve(event.getServer());
        if (configuredSpawn == null) {
            return;
        }

        ServerLevel targetLevel = configuredSpawn.getLevel(event.getServer());
        if (targetLevel == null) {
            return;
        }

        configuredSpawn.applyDefaultSpawn(targetLevel);
    }

    public static void onPlayerRespawnPosition(PlayerRespawnPositionEvent event) {
        if (event.isFromEndFight()) {
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (player.getRespawnPosition() != null && !event.getOriginalDimensionTransition().missingRespawnBlock()) {
            return;
        }

        ConfiguredSpawn configuredSpawn = ConfiguredSpawn.resolve(player.server);
        if (configuredSpawn == null) {
            return;
        }

        ServerLevel targetLevel = configuredSpawn.getLevel(player.server);
        if (targetLevel == null) {
            return;
        }

        configuredSpawn.applyDefaultSpawn(targetLevel);
        event.setDimensionTransition(configuredSpawn.createDimensionTransition(targetLevel));
        event.setCopyOriginalSpawnPosition(false);
    }
}





