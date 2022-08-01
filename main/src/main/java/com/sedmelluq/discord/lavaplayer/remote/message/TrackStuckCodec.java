package com.sedmelluq.discord.lavaplayer.remote.message;

import com.sedmelluq.discord.lavaplayer.tools.ExceptionTools;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Codec for track exception message.
 */
public class TrackStuckCodec implements RemoteMessageCodec<TrackStuckMessage> {
    @Override
    public Class<TrackStuckMessage> getMessageClass() {
        return TrackStuckMessage.class;
    }

    @Override
    public int version(RemoteMessage message) {
        return 1;
    }

    @Override
    public void encode(DataOutput out, TrackStuckMessage message) throws IOException {
        out.writeLong(message.executorId);
        out.writeLong(message.thresholdMs);
    }

    @Override
    public TrackStuckMessage decode(DataInput in, int version) throws IOException {
        return new TrackStuckMessage(in.readLong(), in.readLong());
    }
}
