package com.sonicether.soundphysics;

import com.sonicether.soundphysics.integration.sable.SableAcousticIntegration;
import com.sonicether.soundphysics.integration.ClothConfigIntegration;
import com.sonicether.soundphysics.debug.RaycastRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.nio.file.Path;

@Mod(SoundPhysicsMod.MODID)
public class NeoForgeSoundPhysicsMod extends SoundPhysicsMod {

    public NeoForgeSoundPhysicsMod(IEventBus eventBus) {
        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::clientSetup);
    }

    public void commonSetup(FMLCommonSetupEvent event) {
        init();
    }

    public void clientSetup(FMLClientSetupEvent event) {
        initClient();
        NeoForge.EVENT_BUS.addListener(NeoForgeDiagnosticsCommand::register);
        NeoForge.EVENT_BUS.addListener(NeoForgeRuntimeDiagnostics::onClientTick);
        NeoForge.EVENT_BUS.addListener(NeoForgeSoundSourceEvents::onPlaySoundSource);
        NeoForge.EVENT_BUS.addListener(NeoForgeSoundSourceEvents::onPlayStreamingSource);
        RaycastRenderer.setExternalRenderHookInstalled(true);
        NeoForge.EVENT_BUS.addListener(NeoForgeRaycastRenderEvents::onRenderLevelStage);
        if (isSableLoaded()) {
            SableAcousticIntegration.init();
        }
        if (isClothConfigLoaded()) {
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (client, parent) -> {
                return ClothConfigIntegration.createConfigScreen(parent);
            });
        }
    }

    private static boolean isClothConfigLoaded() {
        if (ModList.get().isLoaded("cloth_config")) {
            try {
                Class.forName("me.shedaniel.clothconfig2.api.ConfigBuilder");
                Loggers.log("Using Cloth Config GUI");
                return true;
            } catch (Exception e) {
                Loggers.log("Failed to load Cloth Config: {}", e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }

    private static boolean isSableLoaded() {
        return ModList.get().isLoaded("sable");
    }

    @Override
    public Path getConfigFolder() {
        return FMLLoader.getGamePath().resolve("config");
    }
}
