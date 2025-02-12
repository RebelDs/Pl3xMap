package net.pl3x.map.util;

import java.nio.ByteBuffer;

public class ByteUtil {
    public static byte[] toBytes(int packed) {
        byte[] bytes = new byte[4];
        for (int i = 0; i < 4; i++) {
            bytes[i] = (byte) (packed >>> (i * 8));
        }
        return bytes;
    }

    public static int getInt(ByteBuffer buffer, int index) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value |= (buffer.get(index + i) & 0xFF) << (i * 8);
        }
        return value;
    }
}
