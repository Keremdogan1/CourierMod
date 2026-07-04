package com.rpsunucusu.courier.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;
import java.util.ArrayList;

@Environment(EnvType.CLIENT)
public class CourierModClient implements ClientModInitializer {
    
    public static class LocationData {
        public String name;
        public int x;
        public int y;
        public int z;
        public String world;
    }
    
    public static List<LocationData> taksiNoktalari = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        System.out.println("[CourierMod] Initializing Client...");
        
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("couriermod", "taksi_sync"), (client, handler, buf, responseSender) -> {
            String json = buf.readString();
            client.execute(() -> {
                try {
                    Type listType = new TypeToken<ArrayList<LocationData>>(){}.getType();
                    taksiNoktalari = new Gson().fromJson(json, listType);
                    System.out.println("[CourierMod] Received Taksi Points from server! Total: " + taksiNoktalari.size());
                    CourierModJMPlugin.refreshWaypoints();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
        
        ClientPlayNetworking.registerGlobalReceiver(new Identifier("couriermod", "open_taksi_map"), (client, handler, buf, responseSender) -> {
            client.execute(() -> {
                System.out.println("[CourierMod] Received open_taksi_map packet!");
                CourierModJMPlugin.openFullscreenMap();
                if (client.player != null) {
                    // Send a chat message instead of an in-game HUD title because the Fullscreen map hides the HUD
                    client.player.sendMessage(net.minecraft.text.Text.literal("§e§l[Taksi] §aHaritadan gitmek istediğiniz §eTaksi Noktasına §atıklayın!"), false);
                }
            });
        });
    }
}
