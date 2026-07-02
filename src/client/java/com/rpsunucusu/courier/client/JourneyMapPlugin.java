package com.rpsunucusu.courier.client;

import com.rpsunucusu.courier.CourierMod;
import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.event.ClientEvent;
import journeymap.client.api.display.Waypoint;
import journeymap.client.api.display.WaypointGroup;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@journeymap.client.api.ClientPlugin
public class JourneyMapPlugin implements IClientPlugin {

    private static JourneyMapPlugin instance;
    private IClientAPI jmAPI;
    private final Map<String, Waypoint> activeWaypoints = new HashMap<>();

    public JourneyMapPlugin() {
        instance = this;
    }

    public static JourneyMapPlugin getInstance() {
        return instance;
    }

    @Override
    public void initialize(IClientAPI jmAPI) {
        this.jmAPI = jmAPI;
        this.jmAPI.subscribe(getModId(), EnumSet.of(ClientEvent.Type.MAPPING_STARTED, ClientEvent.Type.MAPPING_STOPPED));
        updateWaypoints();
    }

    @Override
    public String getModId() {
        return "couriermod";
    }

    @Override
    public void onEvent(ClientEvent event) {
        if (event.type == ClientEvent.Type.MAPPING_STARTED) {
            updateWaypoints();
        }
    }

    public void updateWaypoints() {
        if (this.jmAPI == null) return;
        
        // Remove existing waypoints
        for (Waypoint wp : activeWaypoints.values()) {
            this.jmAPI.remove(wp);
        }
        activeWaypoints.clear();
        
        CourierMod.DataModel data = CourierModClient.clientData;
        if (data == null) return;

        WaypointGroup taksiGroup = new WaypointGroup("couriermod", "Taksi Duraklari");
        WaypointGroup kuryeGroup = new WaypointGroup("couriermod", "Kurye Noktalari");

        for (CourierMod.LocationData l : data.taksiNoktalari) {
            Waypoint wp = new Waypoint("couriermod", "taksi_" + l.name, l.world, new BlockPos((int)l.x, (int)l.y, (int)l.z));
            wp.setName(l.name);
            wp.setColor(0xFFFF00); // Yellow
            wp.setGroup(taksiGroup);
            try {
                this.jmAPI.show(wp);
                activeWaypoints.put("taksi_" + l.name, wp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        for (CourierMod.LocationData l : data.dagitimNoktalari) {
            Waypoint wp = new Waypoint("couriermod", "dagitim_" + l.name, l.world, new BlockPos((int)l.x, (int)l.y, (int)l.z));
            wp.setName("(Dagitim) " + l.name);
            wp.setColor(0x00FF00); // Green
            wp.setGroup(kuryeGroup);
            try {
                this.jmAPI.show(wp);
                activeWaypoints.put("dagitim_" + l.name, wp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        for (CourierMod.LocationData l : data.musteriNoktalari) {
            Waypoint wp = new Waypoint("couriermod", "musteri_" + l.name, l.world, new BlockPos((int)l.x, (int)l.y, (int)l.z));
            wp.setName("(Musteri) " + l.name);
            wp.setColor(0x0000FF); // Blue
            wp.setGroup(kuryeGroup);
            try {
                this.jmAPI.show(wp);
                activeWaypoints.put("musteri_" + l.name, wp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
