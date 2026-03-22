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

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends Screen {
    @Unique
    private static final String[] LOCKDOWN$OVERWORLD_DIRECTORIES = new String[]{"data", "entities", "poi", "region"};

    @Final
    @Shadow
    WorldCreationUiState uiState;

    protected MixinCreateWorldScreen(Component component) {
        super(component);
    }

    /**
     * 拦截CreateWorldScreen#onCreate
     * 当开启模板复制时，完全用模板世界替代新世界。会跳过原版的世界创建逻辑
     */
    @Inject(method = {"onCreate"}, at = {@At(value = "HEAD")}, cancellable = true)
    private void onCreate(CallbackInfo ci) {
        //未启用模板复制时，不干预
        if (!Config.useTemplate.get()) {
            return;
        }

        if (this.minecraft == null) {
            LockDown.LOGGER.error("Minecraft client is not available while creating a templated world.");
            ci.cancel();
            return;
        }

        String mapName = this.uiState.getName().trim();
        String targetFolder = this.uiState.getTargetFolder();//save下的文件
        Path templateDirectory = this.minecraft.gameDirectory.toPath().resolve(Config.templateDirectory.get());//模板世界根目录
        Path savesDirectory = this.minecraft.getLevelSource().getBaseDir();
        Path targetDirectory = savesDirectory.resolve(targetFolder);

        if (this.lockdown$shouldFallbackToNormalWorldCreation(templateDirectory)) {
            return;
        }
        //显示“正在读取数据”的提示屏幕
        this.minecraft.forceSetScreen(new GenericMessageScreen(Component.translatable("selectWorld.data_read")));

        //完整复制模板世界到目标目录
        try {
            FileUtils.copyDirectory(templateDirectory.toFile(), targetDirectory.toFile());
        } catch (IOException e) {
            LockDown.LOGGER.error("Failed to copy template world from {} to {}", templateDirectory, targetDirectory, e);
            SystemToast.onWorldAccessFailure(this.minecraft, targetFolder);
            this.minecraft.setScreen(this);
            ci.cancel();
            return;
        }
        //重命名世界（将世界文件夹内的level.dat中的名称改为用户输入的显示名称）
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
        //直接打开世界，跳过原版创建流程
        this.minecraft.createWorldOpenFlows().openWorld(targetFolder, () -> this.minecraft.setScreen(this));
        ci.cancel();//取消原版
    }

    /**
     * 拦截CreateWorldScreen#createNewWorldDirectory（在正常创建世界时调用），
     * 当未启用模板复制但启用了固定维度复制时，从模板世界复制指定的维度数据到新世界。
     */
    @Inject(method = "createNewWorldDirectory", at = @At("RETURN"))
    private void onCreateNewWorldDirectory(CallbackInfoReturnable<Optional<LevelStorageSource.LevelStorageAccess>> cir) {
        //未启用模板复制，启用固定维度复制
        if (Config.useTemplate.get() || !Config.pinDimensionsEnabled.get() || this.minecraft == null) {
            return;
        }

        Optional<LevelStorageSource.LevelStorageAccess> access = cir.getReturnValue();
        //新世界目录创建成功且配置了要固定的维度
        if (access.isEmpty() || Config.pinnedDimensions.get().isEmpty()) {
            return;
        }
        //模板目录无效则放弃固定维度复制
        Path templateDirectory = this.minecraft.gameDirectory.toPath().resolve(Config.templateDirectory.get());
        if (this.lockdown$shouldFallbackToNormalWorldCreation(templateDirectory)) {
            return;
        }
        //遍历配置的维度ID，逐个复制
        Path targetDirectory = access.get().getLevelDirectory().path();
        for (String dimensionId : Config.pinnedDimensions.get()) {
            this.lockdown$copyPinnedDimension(templateDirectory, targetDirectory, dimensionId);
        }
    }

    /**
     * 检查模板目录是否回退到普通世界创建，如果模板目录不存在或为空，则回退到普通世界创建
     * @return true表示应该回退到普通世界创建，false表示可以继续使用模板复制或固定维度复制逻辑
     */
    @Unique
    private boolean lockdown$shouldFallbackToNormalWorldCreation(Path templateDirectory) {
        if (!Files.isDirectory(templateDirectory)) {
            return true;
        }

        //尝试列出目录内容，如果为空或出错则回退
        try (DirectoryStream<Path> templateFiles = Files.newDirectoryStream(templateDirectory)) {
            return !templateFiles.iterator().hasNext();
        } catch (IOException e) {
            LockDown.LOGGER.warn("Failed to inspect template directory {}, falling back to normal world creation.", templateDirectory, e);
            return true;
        }
    }

    /**
     * 从模板世界复制指定维度的数据到目标世界
     * @param templateDirectory 模板世界根目录
     * @param targetDirectory 目标世界根目录
     * @param dimensionId 维度ID
     */
    @Unique
    private void lockdown$copyPinnedDimension(Path templateDirectory, Path targetDirectory, String dimensionId) {
        //解析维度在模板世界中的路径
        Path sourceDimensionDirectory = this.lockdown$resolveDimensionDirectory(templateDirectory, dimensionId);
        if (sourceDimensionDirectory == null) {
            LockDown.LOGGER.warn("Pinned dimension {} is not supported yet. Currently supported: minecraft:overworld, custom dimensions, minecraft:the_nether, minecraft:the_end.", dimensionId);
            return;
        }
        //解析维度在目标世界中的路径
        Path targetDimensionDirectory = this.lockdown$resolveDimensionDirectory(targetDirectory, dimensionId);
        if (targetDimensionDirectory == null) {
            return;
        }

        //主世界比较特殊，需要复制data，entities，poi，region四个子目录
        if ("minecraft:overworld".equals(dimensionId)) {
            this.lockdown$copyOverworldDirectories(sourceDimensionDirectory, targetDimensionDirectory);
            return;
        }

        //非主世界，检查模板源目录是否存在
        if (!Files.isDirectory(sourceDimensionDirectory)) {
            LockDown.LOGGER.warn("Pinned dimension {} does not exist in template world at {}.", dimensionId, sourceDimensionDirectory);
            return;
        }

        try {
            //如果目标目录已存在，先删除覆盖
            if (Files.exists(targetDimensionDirectory)) {
                FileUtils.deleteDirectory(targetDimensionDirectory.toFile());
            }
            //复制整个维度目录
            FileUtils.copyDirectory(sourceDimensionDirectory.toFile(), targetDimensionDirectory.toFile());
            LockDown.LOGGER.info("Pinned dimension {} copied from template world.", dimensionId);
        } catch (IOException e) {
            LockDown.LOGGER.error("Failed to copy pinned dimension {} from {} to {}", dimensionId, sourceDimensionDirectory, targetDimensionDirectory, e);
        }
    }

    /**
     * 复制主世界的数据（仅复制data，entities，poi，region子目录）。
     * @param templateWorldDirectory 模板世界根目录
     * @param targetWorldDirectory   目标世界根目录
     */
    @Unique
    private void lockdown$copyOverworldDirectories(Path templateWorldDirectory, Path targetWorldDirectory) {
        boolean copiedAnyDirectory = false;

        for (String directoryName : LOCKDOWN$OVERWORLD_DIRECTORIES) {
            Path sourceDirectory = templateWorldDirectory.resolve(directoryName);
            if (!Files.isDirectory(sourceDirectory)) {
                continue;//模板中可能缺少某些东西，直接跳过
            }

            Path targetDirectory = targetWorldDirectory.resolve(directoryName);
            try {
                //删除目标已有目录，保证完整替换
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

    /**
     * 根据维度ID解析其在世界目录中的实际路径。
     * @param worldDirectory 世界根目录（模板世界或目标世界）
     * @param dimensionId    维度ID
     * @return 维度目录的Path，若无法解析则返回 null
     */
    @Unique
    private Path lockdown$resolveDimensionDirectory(Path worldDirectory, String dimensionId) {
        return switch (dimensionId) {
            case "minecraft:the_nether" -> worldDirectory.resolve("DIM-1");
            case "minecraft:the_end" -> worldDirectory.resolve("DIM1");
            case "minecraft:overworld" -> worldDirectory;
            default -> {
                //自定义维度在dimensions/命名空间/路径
                ResourceLocation resourceLocation = ResourceLocation.tryParse(dimensionId);
                if (resourceLocation == null || "minecraft".equals(resourceLocation.getNamespace())) {//不能用原版的维度
                    yield null;
                }

                yield worldDirectory.resolve("dimensions").resolve(resourceLocation.getNamespace()).resolve(resourceLocation.getPath());
            }
        };
    }
}
