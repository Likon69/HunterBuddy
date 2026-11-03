package com.hunterbuddy.modules.flowesp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.ChunkPos;

final class FlowChunkCodec {
    private static final int LAVA_BIT = 1;
    private static final int SOURCE_BIT = 2;
    private static final int ROOF_BIT = 4;
    private static final int TIP_BIT = 8;

    private FlowChunkCodec() {
    }

    static void write(DataOutputStream out, FlowChunk chunk) throws IOException {
        out.writeInt(chunk.pos().x);
        out.writeInt(chunk.pos().z);
        out.writeShort(chunk.counts().sources());
        out.writeShort(chunk.counts().flows());
        out.writeLong(chunk.firstSeenMs());
        out.writeInt(chunk.columns().size());
        for (FlowColumn column : chunk.columns()) {
            writeColumn(out, column);
        }
    }

    private static void writeColumn(DataOutputStream out, FlowColumn column) throws IOException {
        out.writeByte(column.pos().localX());
        out.writeByte(column.pos().localZ());
        out.writeShort(column.span().bottomY());
        out.writeShort(column.span().topY());
        out.writeShort(column.flowLength());
        out.writeByte(encodeFlags(column.flags()));
    }

    static FlowChunk read(DataInputStream in) throws IOException {
        ChunkPos pos = new ChunkPos(in.readInt(), in.readInt());
        FlowChunk.FluidCounts counts = new FlowChunk.FluidCounts(in.readShort(), in.readShort());
        long firstSeen = in.readLong();
        int columnCount = in.readInt();
        List<FlowColumn> columns = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) {
            columns.add(readColumn(in));
        }
        return new FlowChunk(pos, counts, columns, firstSeen > 0 ? firstSeen : System.currentTimeMillis());
    }

    private static FlowColumn readColumn(DataInputStream in) throws IOException {
        FlowColumn.ColumnPos pos = new FlowColumn.ColumnPos(in.readByte(), in.readByte());
        FlowColumn.Span span = new FlowColumn.Span(in.readShort(), in.readShort());
        int flowLength = in.readShort();
        return new FlowColumn(pos, span, flowLength, decodeFlags(in.readByte()));
    }

    private static int encodeFlags(FlowColumn.ColumnFlags flags) {
        return (flags.lava() ? LAVA_BIT : 0) | (flags.hasSource() ? SOURCE_BIT : 0)
            | (flags.roof() ? ROOF_BIT : 0) | (flags.tip() ? TIP_BIT : 0);
    }

    private static FlowColumn.ColumnFlags decodeFlags(int bits) {
        return new FlowColumn.ColumnFlags((bits & LAVA_BIT) != 0, (bits & SOURCE_BIT) != 0,
            (bits & ROOF_BIT) != 0, (bits & TIP_BIT) != 0);
    }
}
