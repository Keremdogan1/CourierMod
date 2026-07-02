import java.nio.file.*;
import java.io.*;

public class Patcher6 {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("src/main/java/com/rpsunucusu/courier/CourierMod.java");
        String content = new String(Files.readAllBytes(path), "UTF-8");

        String variables = "\n    public static final Identifier SYNC_LOCATIONS = new Identifier(\"couriermod\", \"sync_locations\");\n" +
                           "    public static net.minecraft.server.MinecraftServer serverInstance;\n";
        
        if (!content.contains("SYNC_LOCATIONS")) {
            content = content.replace("public class CourierMod implements ModInitializer {", "public class CourierMod implements ModInitializer {" + variables);
        }

        Files.write(path, content.getBytes("UTF-8"));
        System.out.println("Patcher6 applied.");
    }
}
