package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Serveur -> client : ce dragon est primordial (kind = faction) ou ne l'est plus (kind vide). Sert a l'afficher en grand. */
public record PrimordialPayload(int entityId, UUID uuid, String kind) implements CustomPacketPayload {
    public static final Type<PrimordialPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "primordial"));
    public static final StreamCodec<ByteBuf, PrimordialPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PrimordialPayload::entityId,
            UUIDUtil.STREAM_CODEC, PrimordialPayload::uuid,
            ByteBufCodecs.stringUtf8(32), PrimordialPayload::kind,
            PrimordialPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
