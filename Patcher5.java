import java.nio.file.*;
import java.io.*;

public class Patcher5 {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        String imports = "import net.minecraft.util.Identifier;\nimport net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;\nimport net.minecraft.network.PacketByteBuf;\nimport net.fabricmc.fabric.api.networking.v1.PacketByteBufs;";
        if (!content.contains("net.minecraft.util.Identifier")) {
            content = content.replace("import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;", "import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;\n" + imports);
        }

        String syncLoc = "public static final Identifier SYNC_LOCATIONS = new Identifier(\"couriermod\", \"sync_locations\");\n    public static net.minecraft.server.MinecraftServer serverInstance;";
        if (!content.contains("SYNC_LOCATIONS")) {
            content = content.replace("public static final String P =", syncLoc + "\n    public static final String P =");
        }

        if (!content.contains("serverInstance = server;")) {
            content = content.replace("ServerLifecycleEvents.SERVER_STARTED.register(server -> {", "ServerLifecycleEvents.SERVER_STARTED.register(server -> {\n            serverInstance = server;");
        }

        String syncMethod = "public void syncLocationsToAll() {\n" +
            "        if (serverInstance == null) return;\n" +
            "        String json = new Gson().toJson(data);\n" +
            "        for (ServerPlayerEntity p : serverInstance.getPlayerManager().getPlayerList()) {\n" +
            "            PacketByteBuf buf = PacketByteBufs.create();\n" +
            "            buf.writeString(json, 327670);\n" +
            "            ServerPlayNetworking.send(p, SYNC_LOCATIONS, buf);\n" +
            "        }\n" +
            "    }\n" +
            "    public void syncLocations(ServerPlayerEntity p) {\n" +
            "        String json = new Gson().toJson(data);\n" +
            "        PacketByteBuf buf = PacketByteBufs.create();\n" +
            "        buf.writeString(json, 327670);\n" +
            "        ServerPlayNetworking.send(p, SYNC_LOCATIONS, buf);\n" +
            "    }";
        if (!content.contains("syncLocationsToAll")) {
            content = content.replace("private void saveData() {", syncMethod + "\n\n    private void saveData() {");
            content = content.replace("try (FileWriter writer = new FileWriter(configFile)) {", "syncLocationsToAll();\n        try (FileWriter writer = new FileWriter(configFile)) {");
            
            // Add to join event
            String joinEvent = "net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {\n" +
                               "            syncLocations(handler.getPlayer());\n" +
                               "        });\n";
            content = content.replace("ServerPlayConnectionEvents.DISCONNECT", joinEvent + "\n        ServerPlayConnectionEvents.DISCONNECT");
        }

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patcher5 applied.");
    }
}
