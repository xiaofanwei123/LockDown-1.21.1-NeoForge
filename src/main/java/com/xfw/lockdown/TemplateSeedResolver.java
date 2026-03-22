package com.xfw.lockdown;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

public final class TemplateSeedResolver {
    private static final Object LOCK = new Object();
    @Nullable
    private static Path cachedLevelDatPath;
    private static boolean hasCachedTemplateSeed;
    private static boolean cachedTemplateSeedPresent;
    private static long cachedTemplateSeed;

    private TemplateSeedResolver() {
    }

    public static void onServerStarting(@SuppressWarnings("unused") ServerStartingEvent event) {
        clearCache();
    }

    public static void clearCache() {
        synchronized (LOCK) {
            cachedLevelDatPath = null;
            hasCachedTemplateSeed = false;
            cachedTemplateSeedPresent = false;
            cachedTemplateSeed = 0L;
        }
    }

    public static boolean shouldUseTemplateSeed(ResourceKey<Level> dimensionKey) {
        if (!Config.templateSeedDimensionsEnabled.get()) {
            return false;
        }

        String dimensionId = dimensionKey.location().toString();
        return Config.templateSeedDimensions.get().stream().anyMatch(dimensionId::equals);
    }

    public static long resolveSeed(MinecraftServer server, ResourceKey<Level> dimensionKey, long fallbackSeed) {
        if (!shouldUseTemplateSeed(dimensionKey)) {
            return fallbackSeed;
        }

        OptionalLong templateSeed = getTemplateSeed(server);
        return templateSeed.orElse(fallbackSeed);
    }

    private static OptionalLong getTemplateSeed(MinecraftServer server) {
        Path levelDatPath = server.getServerDirectory().toAbsolutePath().normalize().resolve(Config.templateDirectory.get()).resolve("level.dat");
        synchronized (LOCK) {
            if (levelDatPath.equals(cachedLevelDatPath) && hasCachedTemplateSeed) {
                return cachedTemplateSeedPresent ? OptionalLong.of(cachedTemplateSeed) : OptionalLong.empty();
            }

            OptionalLong resolvedSeed = readTemplateSeed(levelDatPath);
            cachedLevelDatPath = levelDatPath;
            hasCachedTemplateSeed = true;
            cachedTemplateSeedPresent = resolvedSeed.isPresent();
            cachedTemplateSeed = resolvedSeed.orElse(0L);
            return resolvedSeed;
        }
    }

    private static OptionalLong readTemplateSeed(Path levelDatPath) {
        if (!Files.isRegularFile(levelDatPath)) {
            LockDown.LOGGER.warn("Template level.dat was not found at {}. Falling back to the new world's own seed.", levelDatPath);
            return OptionalLong.empty();
        }

        try {
            CompoundTag rootTag = NbtIo.readCompressed(levelDatPath, NbtAccounter.unlimitedHeap());
            if (!rootTag.contains("Data", Tag.TAG_COMPOUND)) {
                LockDown.LOGGER.warn("Template level.dat at {} does not contain a Data compound. Falling back to the new world's own seed.", levelDatPath);
                return OptionalLong.empty();
            }

            CompoundTag dataTag = rootTag.getCompound("Data");
            if (!dataTag.contains("WorldGenSettings", Tag.TAG_COMPOUND)) {
                LockDown.LOGGER.warn("Template level.dat at {} does not contain WorldGenSettings. Falling back to the new world's own seed.", levelDatPath);
                return OptionalLong.empty();
            }

            CompoundTag worldGenSettingsTag = dataTag.getCompound("WorldGenSettings");
            if (!worldGenSettingsTag.contains("seed", Tag.TAG_LONG)) {
                LockDown.LOGGER.warn("Template level.dat at {} does not contain a world generation seed. Falling back to the new world's own seed.", levelDatPath);
                return OptionalLong.empty();
            }

            long templateSeed = worldGenSettingsTag.getLong("seed");
            LockDown.LOGGER.info("Loaded template world seed {} from {}.", templateSeed, levelDatPath);
            return OptionalLong.of(templateSeed);
        } catch (IOException exception) {
            LockDown.LOGGER.warn("Failed to read template seed from {}. Falling back to the new world's own seed.", levelDatPath, exception);
            return OptionalLong.empty();
        }
    }
}


