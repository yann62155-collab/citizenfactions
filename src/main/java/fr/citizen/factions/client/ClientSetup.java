package fr.citizen.factions.client;

import com.mojang.blaze3d.platform.InputConstants;
import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.platform.ActionPayload;
import fr.citizen.factions.platform.Network;
import fr.citizen.factions.ui.View;
import fr.citizen.factions.ui.ViewCodec;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Initialisation cote client uniquement (classe chargee seulement quand Dist == CLIENT). */
public final class ClientSetup {
    /**
     * Touche U par defaut : libre en vanilla et non utilisee par Ice and Fire (R, G, X, F7),
     * Citizen Economy (K), Citizen Boutique (F6) ni Citizen Admin (Alt). Modifiable dans Options > Commandes.
     */
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.citizenfactions.open", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.citizenfactions");

    private ClientSetup() { }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_KEY));
        modBus.addListener(BondHud::register);
        Network.clientBondHandler = p -> Minecraft.getInstance().execute(() -> BondHud.update(p));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null)
                    PacketDistributor.sendToServer(new ActionPayload("open", "{\"to\":\"main\"}"));
            }
        });
        Network.clientViewHandler = json -> {
            View view;
            try { view = ViewCodec.fromJson(json); }
            catch (RuntimeException ex) { CitizenFactions.LOGGER.warn("Écran reçu invalide : {}", ex.getMessage()); return; }
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> {
                if (mc.screen instanceof ViewScreen vs) vs.update(view);
                else mc.setScreen(new ViewScreen(view));
            });
        };
    }
}
