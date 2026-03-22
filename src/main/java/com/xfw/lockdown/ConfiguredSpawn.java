package com.xfw.lockdown;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public record ConfiguredSpawn(ResourceKey<Level> dimensionKey, double x, double y, double z, float yaw, float pitch) {
    @Nullable
    public static ConfiguredSpawn resolve(MinecraftServer server) {
        if (!Config.loginSpawnTeleportEnabled.get()) {
            return null;
        }

        String rawTarget = Config.loginSpawnTarget.get().trim();
        String[] parts = rawTarget.split(";");
        if (parts.length != 6) {
            LockDown.LOGGER.warn("Invalid login_spawn_target '{}'. Expected format: dimension_id;x;y;z;yaw;pitch", rawTarget);
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(parts[0].trim());
        if (dimensionId == null) {
            LockDown.LOGGER.warn("Invalid configured spawn dimension id '{}'", parts[0]);
            return null;
        }

        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        try {
            x = Double.parseDouble(parts[1].trim());
            y = Double.parseDouble(parts[2].trim());
            z = Double.parseDouble(parts[3].trim());
            yaw = Float.parseFloat(parts[4].trim());
            pitch = Float.parseFloat(parts[5].trim());
        } catch (NumberFormatException exception) {
            LockDown.LOGGER.warn("Invalid configured spawn target '{}'. Expected numbers after dimension id.", rawTarget, exception);
            return null;
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, dimensionId);
        if (server.getLevel(dimensionKey) == null) {
            LockDown.LOGGER.warn("Configured spawn dimension '{}' is not loaded or does not exist.", dimensionId);
            return null;
        }

        return new ConfiguredSpawn(dimensionKey, x, y, z, yaw, pitch);
    }

    @Nullable
    public ServerLevel getLevel(MinecraftServer server) {
        return server.getLevel(this.dimensionKey);
    }

    public BlockPos getSharedSpawnPos() {
        return BlockPos.containing(this.x, this.y, this.z);
    }

    public Vec3 getPosition() {
        return new Vec3(this.x, this.y, this.z);
    }

    public void applyDefaultSpawn(ServerLevel level) {
        level.setDefaultSpawnPos(this.getSharedSpawnPos(), this.yaw);
    }

    public DimensionTransition createDimensionTransition(ServerLevel level) {
        return new DimensionTransition(level, this.getPosition(), Vec3.ZERO, this.yaw, this.pitch, DimensionTransition.DO_NOTHING);
    }
}

