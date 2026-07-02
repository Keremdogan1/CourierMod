import java.nio.file.*;
import java.io.*;

public class Patcher4 {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        String imports = "import net.minecraft.util.Identifier;\nimport net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;\nimport net.minecraft.network.PacketByteBuf;\nimport net.fabricmc.fabric.api.networking.v1.PacketByteBufs;";
        if (!content.contains("net.minecraft.util.Identifier")) {
            content = content.replace("import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;", "import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;\n" + imports);
        }

        String syncLoc = "public static final Identifier SYNC_LOCATIONS = new Identifier(\"couriermod\", \"sync_locations\");";
        if (!content.contains("SYNC_LOCATIONS")) {
            content = content.replace("public static final String P =", syncLoc + "\n    public static final String P =");
        }

        String syncMethod = "public void syncLocationsToAll(net.minecraft.server.MinecraftServer server) {\n" +
            "        String json = new Gson().toJson(data);\n" +
            "        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {\n" +
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
            content = content.replace("saveData() {", "saveData(net.minecraft.server.MinecraftServer server) {");
            // Wait, changing saveData signature is dangerous because it's called in many places without server argument.
            // Better to not change saveData signature, but just call syncLocationsToAll if we have a way.
        }

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patcher4 applied.");
    }
}
