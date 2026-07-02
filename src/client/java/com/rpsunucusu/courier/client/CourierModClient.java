package com.rpsunucusu.courier.client;

import com.google.gson.Gson;
import com.rpsunucusu.courier.CourierMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

@Environment(EnvType.CLIENT)
public class CourierModClient implements ClientModInitializer {
    public static CourierMod.DataModel clientData = new CourierMod.DataModel();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(CourierMod.SYNC_LOCATIONS, (client, handler, buf, responseSender) -> {
            String json = buf.readString(327670);
            client.execute(() -> {
                clientData = new Gson().fromJson(json, CourierMod.DataModel.class);
                if (JourneyMapPlugin.getInstance() != null) {
                    JourneyMapPlugin.getInstance().updateWaypoints();
                }
            });
        });
    }
}
