package org.jetbrains.jet.jps.build;

import org.jetbrains.jps.incremental.CompileContext;

import java.io.*;

public class TargetTimestamps {
    private final File dataFile;

    private long timestamp;
    private short formatVersion;

    public TargetTimestamps(CompileContext context, KotlinBuildTarget target) throws IOException {
        dataFile = new File(context.getProjectDescriptor().dataManager.getDataPaths().getTargetDataRoot(target), "timestamps.dat");
        if (!dataFile.exists()) {
            timestamp = -1;
            formatVersion = -1;
            return;
        }

        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(dataFile)));
        try {
            timestamp = in.readLong();
            formatVersion = in.readShort();
        }
        finally {
            in.close();
        }
    }

    public long getTimestamp() {
        return timestamp;
    }

    public short getFormatVersion() {
        return formatVersion;
    }

    public void set(long timestamp, short formatVersion) throws IOException {
        this.timestamp = timestamp;
        this.formatVersion = formatVersion;

        DataOutputStream in = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(dataFile)));
        try {
            in.writeLong(timestamp);
            in.writeShort(formatVersion);
        }
        finally {
            in.close();
        }
    }
}