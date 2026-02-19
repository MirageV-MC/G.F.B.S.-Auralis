package org.mirage.gfbs.auralis;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.mirage.gfbs.auralis.api.AuralisApi;
import org.slf4j.Logger;

@Mod(GFBsAuralis.MODID)
public class GFBsAuralis {
    public static final String MODID = "gfbs_auralis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GFBsAuralis() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, GFBsAuralisConfig.SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, GFBsAuralisConfig.CLIENT_SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("GFBS-Auralis Startup...");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ServerSetup::init);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            event.enqueueWork(() -> {
                var cfg = GFBsAuralisConfig.CLIENT;
                AuralisAL al = AuralisAL.createAndStartGlobal(AuralisAL.Config.defaultsWithHrtf(cfg.enableHrtf.get()));
                int configuredMaxSources = cfg.maxSources.get();
                int reserve = cfg.reserveSourcesForVanilla.get();
                int effectiveMaxSources = Math.max(1, configuredMaxSources - reserve);
                AuralisEngine engine = new AuralisEngine(
                        Minecraft.getInstance(),
                        al,
                        effectiveMaxSources,
                        cfg.streamedChunkSize.get(),
                        cfg.maxStreamedBytes.get(),
                        cfg.attenuationExponent.get().floatValue(),
                        cfg.volumeSmoothing.get().floatValue()
                );
                AuralisApi.setEngine(engine);
                LOGGER.info("Auralis engine initialized (client). maxSources={} (configured={}, reserveForVanilla={})", effectiveMaxSources, configuredMaxSources, reserve);
            });
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("GFBS-Auralis server starting...");
    }

    @Mod.EventBusSubscriber(
            modid = MODID,
            bus = Mod.EventBusSubscriber.Bus.FORGE,
            value = Dist.CLIENT
    )
    public static class ClientModEvents {
        public static AuralisEngine engine;

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            ClientSoundController.flushPendingIfReady();
            if (AuralisApi.isInitialized()) {
                AuralisApi.engine().tick();
            }
        }

        @SubscribeEvent
        public static void onClientShutdown(GameShuttingDownEvent e){
            if (AuralisApi.isInitialized()) {
                AuralisApi.engine().shutdown();
            }
        }
    }
}
