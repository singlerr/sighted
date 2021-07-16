package com.kgb_8375.sighted;

import com.kgb_8375.sighted.config.ClientConfiguration;
import com.kgb_8375.sighted.config.Configuration;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("sighted")
public class Sighted {

    {instance = this; }
    private static Sighted instance;
    public static Sighted getInstance() {
        return instance;
    }

    public static final Logger LOGGER = LogManager.getLogger();

    private static Configuration config;

    public Sighted() {
        config = new Configuration();

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);

        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
    }

    private void setup(final FMLClientSetupEvent event) {
        LOGGER.info("Sighted client loaded");
    }

    public static Configuration getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return config.getClientConfig().enabled.get() && Minecraft.getInstance().getSingleplayerServer() == null;
    }
}
