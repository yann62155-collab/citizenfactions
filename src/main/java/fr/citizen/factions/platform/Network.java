package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import fr.citizen.factions.ui.Actor;
import fr.citizen.factions.ui.View;
import fr.citizen.factions.ui.ViewCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
import java.util.function.Consumer;

/** Enregistrement des paquets et aiguillage. Aucun import client ici : ce code est charge aussi sur serveur dedie. */
public final class Network {
    public static final String VERSION = "1";

    /** Renseignes cote client uniquement (ClientSetup). */
    public static volatile Consumer<String> clientViewHandler;
    public static volatile Consumer<BondPayload> clientBondHandler;

    private Network() { }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, (p, ctx) -> {
            Consumer<String> h = clientViewHandler;
            if (h != null) h.accept(p.json());
        });
        r.playToClient(BondPayload.TYPE, BondPayload.CODEC, (p, ctx) -> {
            Consumer<BondPayload> h = clientBondHandler;
            if (h != null) h.accept(p);
        });
        r.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, Network::onAction);
    }

    private static void onAction(ActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        // toujours sur le thread serveur : les donnees des factions n'y sont modifiees que la
        ctx.enqueueWork(() -> {
            FactionRuntime rt = FactionRuntime.current();
            if (rt == null || !rt.rateLimit(player)) return;
            Actor actor = Admins.actor(player);
            View view;
            try {
                Map<String, String> args = ViewCodec.argsFromJson(payload.argsJson());
                view = rt.ui().handle(actor, payload.action(), args);
            } catch (IllegalArgumentException bad) {
                CitizenFactions.LOGGER.warn("Requête invalide de {} : {}", actor.name(), bad.getMessage());
                return;
            } catch (Throwable t) {
                CitizenFactions.LOGGER.error("Erreur d'interface pour {}", actor.name(), t);
                return;
            }
            send(player, view);
        });
    }

    public static void send(ServerPlayer player, View view) {
        PacketDistributor.sendToPlayer(player, new ViewPayload(ViewCodec.toJson(view)));
    }

    public static void sendBond(ServerPlayer player, BondPayload p) {
        PacketDistributor.sendToPlayer(player, p);
    }
}
