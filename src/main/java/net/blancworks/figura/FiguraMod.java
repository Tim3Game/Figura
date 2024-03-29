package net.blancworks.figura;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.blancworks.figura.commands.FiguraCommands;
import net.blancworks.figura.lua.FiguraLuaManager;
import net.blancworks.figura.models.CustomModel;
import net.blancworks.figura.models.parsers.BlockbenchModelDeserializer;
import net.blancworks.figura.network.FiguraNetworkManager;
import net.blancworks.figura.trust.PlayerTrustManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;

public class FiguraMod implements ClientModInitializer {

    public static final Gson builder = new GsonBuilder().registerTypeAdapter(CustomModel.class, new BlockbenchModelDeserializer()).setPrettyPrinting().create();

    public static final Logger LOGGER = LogManager.getLogger();

    //Used during rendering.
    public static AbstractClientPlayerEntity curr_player;
    public static PlayerEntityModel curr_model;
    public static VertexConsumerProvider vertex_consumer_provider;
    private static PlayerData curr_data;
    public static float deltaTime;

    private static final boolean USE_DEBUG_MODEL = true;
    private static WatchKey watch_key;
    private static Path path;

    //Lua

    //Methods

    //Set current player.
    //If there is a model loaded for the player, it'll be assigned here to the current model.
    //Otherwise, sends the model to the request list.
    public static void setRenderingMode(AbstractClientPlayerEntity player, VertexConsumerProvider vertexConsumerProvider, PlayerEntityModel mdl, float dt) {
        curr_player = player;
        curr_data = PlayerDataManager.getDataForPlayer(player.getUuid());
        
        if(curr_data != null && curr_data.script != null && curr_data.script.vanillaModifications != null)
            curr_data.script.applyCustomValues(mdl);
        
        curr_data.vanillaModel = mdl;
        vertex_consumer_provider = vertexConsumerProvider;
        deltaTime = dt;
    }

    //Returns the current custom model for rendering. 
    //Set earlier by the player render function, used in the renderer mixin.
    public static PlayerData getCurrData() {
        PlayerData ret = curr_data;
        curr_data = null;
        return ret;
    }

    @Override
    public void onInitializeClient() {
        FiguraLuaManager.initialize();
        FiguraCommands.initialize();
        PlayerTrustManager.init();
        
        ClientTickEvents.END_CLIENT_TICK.register(FiguraMod::ClientEndTick);
    }

    //Client-side ticks.
    public static void ClientEndTick(MinecraftClient client) {
        PlayerDataManager.tick();
        FiguraNetworkManager.tickNetwork();
    }
    
    public static Path getModContentDirectory(){
        Path p = FabricLoader.INSTANCE.getGameDir().getParent().resolve("figura");
        try {
            Files.createDirectories(p);
        } catch (Exception e){
            e.printStackTrace();
        }
        return p;
    }

    public static void loadConfigs(){

    }
}
