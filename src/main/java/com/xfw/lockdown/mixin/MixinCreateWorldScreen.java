package com.xfw.lockdown.mixin;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import com.xfw.lockdown.Config;
import com.xfw.lockdown.LockDown;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.apache.commons.io.FileUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={CreateWorldScreen.class})
public abstract class MixinCreateWorldScreen
        extends Screen {
    @Unique
    private static final String[] LOCKDOWN$OVERWORLD_DIRECTORIES = new String[]{"data", "entities", "poi", "region"};

    @Final
    @Shadow
    WorldCreationUiState uiState;

    protected MixinCreateWorldScreen(Component component) {
        super(component);
    }

    @Inject(method={"onCreate"}, at={@At(value="HEAD")}, cancellable=true)
    private void onCreate(CallbackInfo ci) {
        if (!Config.useTemplate.get()) {
            return;
        }

        if (this.minecraft == null) {
            LockDown.LOGGER.error("Minecraft client is not available while creating a templated world.");
            ci.cancel();
            return;
        }

        String mapName = this.uiState.getName().trim();
        String targetFolder = this.uiState.getTargetFolder();
        Path templateDirectory = this.minecraft.gameDirectory.toPath().resolve(Config.templateDirectory.get());
        Path savesDirectory = this.minecraft.getLevelSource().getBaseDir();
        Path targetDirectory = savesDirectory.resolve(targetFolder);

        if (this.lockdown$shouldFallbackToNormalWorldCreation(templateDirectory)) {
            return;
        }

        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));

        try {
            FileUtils.copyDirectory(templateDirectory.toFile(), targetDirectory.toFile());
        } catch (IOException e) {
            LockDown.LOGGER.error("Failed to copy template world from {} to {}", templateDirectory, targetDirectory, e);
            SystemToast.onWorldAccessFailure(this.minecraft, targetFolder);
            this.minecraft.setScreen(this);
            ci.cancel();
            return;
        }

        try {
            LevelStorageSource.LevelStorageAccess storageAccess = this.minecraft.getLevelSource().createAccess(targetFolder);
            storageAccess.renameLevel(mapName);
            storageAccess.close();
        } catch (IOException e) {
            SystemToast.onWorldAccessFailure(this.minecraft, mapName);
            LockDown.LOGGER.error("Failed to rename level {}", mapName, e);
            this.minecraft.setScreen(this);
            ci.cancel();
            return;
        }

        this.minecraft.createWorldOpenFlows().openWorld(targetFolder, () -> this.minecraft.setScreen(this));
        ci.cancel();
    }

    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"))
    private void onCreateNewWorldDirectory(CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> cir) {
        if (Config.useTemplate.get() || !Config.pinDimensionsEnabled.get() || this.minecraft == null) {
            return;
        }

        Optional<LevelStorageSource.LevelStorageAccess> access = cir.getReturnValue();
        if (access.isEmpty() || Config.pinnedDimensions.get().isEmpty()) {
            return;
        }

        Path templateDirectory = this.minecraft.gameDirectory.toPath().resolve(Config.templateDirectory.get());
        if (this.lockdown$shouldFallbackToNormalWorldCreation(templateDirectory)) {
            return;
        }

        Path targetDirectory = access.get().getLevelDirectory().path();
        for (String dimensionId : Config.pinnedDimensions.get()) {
            this.lockdown$copyPinnedDimension(templateDirectory, targetDirectory, dimensionId);
        }
    }

    @Unique
    private boolean lockdown$shouldFallbackToNormalWorldCreation(Path templateDirectory) {
        if (!Files.isDirectory(templateDirectory)) {
            return true;
        }

        try (DirectoryStream<Path> templateFiles = Files.newDirectoryStream(templateDirectory)) {
            return !templateFiles.iterator().hasNext();
        } catch (IOException e) {
            LockDown.LOGGER.warn("Failed to inspect template directory {}, falling back to normal world creation.", templateDirectory, e);
            return true;
        }
    }

    @Unique
    private void lockdown$copyPinnedDimension(Path templateDirectory, Path targetDirectory, String dimensionId) {
        Path sourceDimensionDirectory = this.lockdown$resolveDimensionDirectory(templateDirectory, dimensionId);
        if (sourceDimensionDirectory == null) {
            LockDown.LOGGER.warn("Pinned dimension {} is not supported yet. Currently supported: minecraft:overworld, custom dimensions, minecraft:the_nether, minecraft:the_end.", dimensionId);
            return;
        }

        Path targetDimensionDirectory = this.lockdown$resolveDimensionDirectory(targetDirectory, dimensionId);
        if (targetDimensionDirectory == null) {
            return;
        }

        if ("minecraft:overworld".equals(dimensionId)) {
            this.lockdown$copyOverworldDirectories(sourceDimensionDirectory, targetDimensionDirectory);
            return;
        }

        if (!Files.isDirectory(sourceDimensionDirectory)) {
            LockDown.LOGGER.warn("Pinned dimension {} does not exist in template world at {}.", dimensionId, sourceDimensionDirectory);
            return;
        }

        try {
            if (Files.exists(targetDimensionDirectory)) {
                FileUtils.deleteDirectory(targetDimensionDirectory.toFile());
            }

            FileUtils.copyDirectory(sourceDimensionDirectory.toFile(), targetDimensionDirectory.toFile());
            LockDown.LOGGER.info("Pinned dimension {} copied from template world.", dimensionId);
        } catch (IOException e) {
            LockDown.LOGGER.error("Failed to copy pinned dimension {} from {} to {}", dimensionId, sourceDimensionDirectory, targetDimensionDirectory, e);
        }
    }

    @Unique
    private void lockdown$copyOverworldDirectories(Path templateWorldDirectory, Path targetWorldDirectory) {
        boolean copiedAnyDirectory = false;

        for (String directoryName : LOCKDOWN$OVERWORLD_DIRECTORIES) {
            Path sourceDirectory = templateWorldDirectory.resolve(directoryName);
            if (!Files.isDirectory(sourceDirectory)) {
                continue;
            }

            Path targetDirectory = targetWorldDirectory.resolve(directoryName);
            try {
                if (Files.exists(targetDirectory)) {
                    FileUtils.deleteDirectory(targetDirectory.toFile());
                }

                FileUtils.copyDirectory(sourceDirectory.toFile(), targetDirectory.toFile());
                copiedAnyDirectory = true;
            } catch (IOException e) {
                LockDown.LOGGER.error("Failed to copy overworld directory {} from {} to {}", directoryName, sourceDirectory, targetDirectory, e);
            }
        }

        if (copiedAnyDirectory) {
            LockDown.LOGGER.info("Pinned overworld data copied from template world.");
            return;
        }

        LockDown.LOGGER.warn("Pinned overworld data does not exist in template world at {}. Expected one or more of {}.", templateWorldDirectory, java.util.Arrays.toString(LOCKDOWN$OVERWORLD_DIRECTORIES));
    }

    @Unique
    private Path lockdown$resolveDimensionDirectory(Path worldDirectory, String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:the_nether" -> worldDirectory.resolve("DIM-1");
            case "minecraft:the_end" -> worldDirectory.resolve("DIM1");
            case "minecraft:overworld" -> worldDirectory;
            default -> {
                ResourceLocation resourceLocation = ResourceLocation.tryParse(dimensionId);
                if (resourceLocation == null || "minecraft".equals(resourceLocation.getNamespace())) {
                    yield null;
                }

                yield worldDirectory.resolve("dimensions").resolve(resourceLocation.getNamespace()).resolve(resourceLocation.getPath());
            }
        };
    }
}
