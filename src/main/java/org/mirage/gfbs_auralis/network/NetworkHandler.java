package org.mirage.gfbs_auralis.network;

/**
 * G.F.B.S. Mirage (mirage_gfbs) - A Minecraft Mod
 * Copyright (C) 2025-2029 Mirage-MC
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.mirage.gfbs_auralis.GFBsAuralis;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class NetworkHandler {
    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(GFBsAuralis.MODID, "gfbs_auralis_main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerMessages() {
        registerMessage(SyncSoundPacket.class, SyncSoundPacket::encode, SyncSoundPacket::decode, SyncSoundPacket::handle);
        registerMessage(PlaySoundPacket.class, PlaySoundPacket::encode, PlaySoundPacket::decode, PlaySoundPacket::handle);
        registerMessage(StopSoundPacket.class, StopSoundPacket::encode, StopSoundPacket::decode, StopSoundPacket::handle);
        registerMessage(LoopSoundPacket.class, LoopSoundPacket::encode, LoopSoundPacket::decode, LoopSoundPacket::handle);

        // New: server->client sound control for the /auralis command
        registerMessage(SoundControlPacket.class, SoundControlPacket::encode, SoundControlPacket::decode, SoundControlPacket::handle);
    }

    private static <MSG> void registerMessage(Class<MSG> messageType,
                                              BiConsumer<MSG, FriendlyByteBuf> encoder,
                                              Function<FriendlyByteBuf, MSG> decoder,
                                              BiConsumer<MSG, Supplier<NetworkEvent.Context>> messageConsumer) {
        CHANNEL.registerMessage(packetId++, messageType, encoder, decoder, messageConsumer);
    }
}