package com.rpsunucusu.courier.client;

import journeymap.client.api.IClientAPI;
import journeymap.client.api.IClientPlugin;
import journeymap.client.api.display.MarkerOverlay;
import journeymap.client.api.display.DisplayType;
import journeymap.client.api.model.MapImage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;

@journeymap.client.api.ClientPlugin
public class CourierModJMPlugin implements IClientPlugin {

    private static IClientAPI jmAPI;

    @Override
    public void initialize(IClientAPI api) {
        jmAPI = api;
        System.out.println("[CourierMod] JourneyMap API initialized.");
    }

    @Override
    public String getModId() {
        return "couriermod";
    }

    @Override
    public void onEvent(journeymap.client.api.event.ClientEvent event) {
        // Handle events if needed
    }

    public static void refreshWaypoints() {
        if (jmAPI == null) return;
        
        try {
            jmAPI.removeAll("couriermod");
            
            for (CourierModClient.LocationData loc : CourierModClient.taksiNoktalari) {
                // Using a vanilla map texture instead of a JourneyMap one to avoid missing texture issues
                MapImage icon = new MapImage(new Identifier("minecraft", "textures/item/map.png"), 32, 32);
                icon.setAnchorX(icon.getDisplayWidth() / 2.0)
                    .setAnchorY(icon.getDisplayHeight() / 2.0);

                BlockPos pos = new BlockPos(loc.x, loc.y, loc.z);
                
                MarkerOverlay marker = new MarkerOverlay("couriermod", "taksi_" + loc.name.replaceAll("\\s+", "_"), pos, icon);
                marker.setTitle("§e§l" + loc.name + "\n§aTaksi çağırmak için tıklayın!");
                marker.setLabel("§e" + loc.name + " §7(Tıkla)");
                
                // Parse dimension correctly instead of hardcoding OVERWORLD
                net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimKey = net.minecraft.world.World.OVERWORLD;
                try {
                    dimKey = net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new Identifier(loc.world));
                } catch (Exception ignored) {}
                marker.setDimension(dimKey);
                
                marker.setOverlayListener(new journeymap.client.api.display.IOverlayListener() {
                    @Override
                    public void onActivate(journeymap.client.api.util.UIState mapState) {}
                    @Override
                    public void onDeactivate(journeymap.client.api.util.UIState mapState) {}
                    @Override
                    public void onMouseMove(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition) {}
                    @Override
                    public void onMouseOut(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition) {}
                    @Override
                    public boolean onMouseClick(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition, int button, boolean doubleClick) {
                        MinecraftClient.getInstance().execute(() -> {
                            if (MinecraftClient.getInstance().player != null && CourierModClient.taksiRequestedTime == 0) {
                                MinecraftClient.getInstance().player.networkHandler.sendCommand("taksi cagir " + loc.name);
                                // Mark the request time so we can show "Iletildi" and close after 3s
                                CourierModClient.taksiRequestedTime = System.currentTimeMillis();
                            }
                        });
                        return true;
                    }
                    @Override
                    public void onOverlayMenuPopup(journeymap.client.api.util.UIState mapState, java.awt.geom.Point2D.Double mousePosition, net.minecraft.util.math.BlockPos blockPosition, journeymap.client.api.display.ModPopupMenu modPopupMenu) {}
                });
                
                jmAPI.show(marker);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void openFullscreenMap() {
        if (jmAPI == null) {
            System.out.println("[CourierMod] JourneyMap API not initialized, cannot open map.");
            return;
        }
        try {
            // Directly open JourneyMap's Fullscreen map screen via reflection
            Class<?> fullscreenClass = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");
            Object fullscreenInstance = fullscreenClass.getDeclaredConstructor().newInstance();
            MinecraftClient.getInstance().setScreen((net.minecraft.client.gui.screen.Screen) fullscreenInstance);
            System.out.println("[CourierMod] JourneyMap Fullscreen map opened successfully.");
        } catch (ClassNotFoundException e) {
            System.err.println("[CourierMod] JourneyMap Fullscreen class not found. Is JourneyMap installed?");
            fallbackMessage();
        } catch (NoSuchMethodException e) {
            System.err.println("[CourierMod] JourneyMap Fullscreen has no default constructor, trying alternative...");
            tryAlternativeOpen();
        } catch (Exception e) {
            System.err.println("[CourierMod] Failed to open JourneyMap Fullscreen: " + e.getMessage());
            e.printStackTrace();
            fallbackMessage();
        }
    }

    private static void tryAlternativeOpen() {
        try {
            // Some JourneyMap versions use a static instance or factory
            Class<?> fullscreenClass = Class.forName("journeymap.client.ui.fullscreen.Fullscreen");
            // Try constructors with parameters
            for (java.lang.reflect.Constructor<?> constructor : fullscreenClass.getDeclaredConstructors()) {
                constructor.setAccessible(true);
                Class<?>[] paramTypes = constructor.getParameterTypes();
                if (paramTypes.length == 0) {
                    Object instance = constructor.newInstance();
                    MinecraftClient.getInstance().setScreen((net.minecraft.client.gui.screen.Screen) instance);
                    return;
                }
            }
            // If no suitable constructor found, try static methods
            for (java.lang.reflect.Method method : fullscreenClass.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isStatic(method.getModifiers()) 
                    && net.minecraft.client.gui.screen.Screen.class.isAssignableFrom(method.getReturnType())
                    && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    Object screen = method.invoke(null);
                    if (screen != null) {
                        MinecraftClient.getInstance().setScreen((net.minecraft.client.gui.screen.Screen) screen);
                        return;
                    }
                }
            }
            fallbackMessage();
        } catch (Exception e) {
            e.printStackTrace();
            fallbackMessage();
        }
    }

    private static void fallbackMessage() {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                net.minecraft.text.Text.literal("\u00a76[Taksi] \u00a7eHaritay\u0131 a\u00e7mak i\u00e7in \u00a7bJ \u00a7etu\u015funa bas\u0131n ve gitmek istedi\u011finiz Taksi Noktas\u0131na t\u0131klay\u0131n!"), false);
        }
    }
}
