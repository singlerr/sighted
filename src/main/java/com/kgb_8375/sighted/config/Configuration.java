package com.kgb_8375.sighted.config;

import com.kgb_8375.sighted.config.ClientConfiguration;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;

public class Configuration {
    private final ClientConfiguration clientConfig;

    public Configuration() {
        final Pair<ClientConfiguration, ForgeConfigSpec> cli = new ForgeConfigSpec.Builder().configure(ClientConfiguration::new);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, cli.getRight());

        clientConfig = cli.getLeft();
    }

    public ClientConfiguration getClientConfig() {
        return clientConfig;
    }
}
