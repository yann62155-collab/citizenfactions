package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serveur -> client : ecran a afficher (JSON produit par ViewCodec). */
public record ViewPayload(String json) implements CustomPacketPayload {
    public static final Type<ViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "view"));
    public static final StreamCodec<ByteBuf, ViewPayload> CODEC = ByteBufCodecs.stringUtf8(262_144).map(ViewPayload::new, ViewPayload::json);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
