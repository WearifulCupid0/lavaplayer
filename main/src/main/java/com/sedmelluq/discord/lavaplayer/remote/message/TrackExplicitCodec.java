package com.sedmelluq.discord.lavaplayer.remote.message;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Codec for track exception message.
 */
public class TrackExplicitCodec implements RemoteMessageCodec<TrackExplicitMessage> {
    @Override
    public Class<TrackExplicitMessage> getMessageClass() {
        return TrackExplicitMessage.class;
    }

    @Override
    public int version(RemoteMessage message) {
        return 1;
    }

    @Override
    public void encode(DataOutput out, TrackExplicitMessage message) throws IOException {
        out.writeLong(message.executorId);
    }

    @Override
    public TrackExplicitMessage decode(DataInput in, int version) throws IOException {
        return new TrackExplicitMessage(in.readLong());
    }
}
