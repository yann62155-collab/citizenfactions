package fr.citizen.factions.platform;

import fr.citizen.factions.CitizenFactions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Serveur -> client : etat de la barre de lien (envoye chaque seconde pendant la monte, puis show = false). */
public record BondPayload(boolean show, String name, float bond, String tier) implements CustomPacketPayload {
    public static final Type<BondPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CitizenFactions.MOD_ID, "bond"));
    public static final StreamCodec<ByteBuf, BondPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BondPayload::show,
            ByteBufCodecs.stringUtf8(64), BondPayload::name,
            ByteBufCodecs.FLOAT, BondPayload::bond,
            ByteBufCodecs.stringUtf8(32), BondPayload::tier,
            BondPayload::new);

    public static final BondPayload HIDE = new BondPayload(false, "", 0, "");

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
