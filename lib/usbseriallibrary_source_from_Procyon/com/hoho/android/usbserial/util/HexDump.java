// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.util;

public class HexDump
{
    private static final char[] HEX_DIGITS;
    
    public static String dumpHexString(final byte[] array) {
        return dumpHexString(array, 0, array.length);
    }
    
    public static String dumpHexString(final byte[] array, final int offset, final int length) {
        final StringBuilder result = new StringBuilder();
        final byte[] line = new byte[16];
        int lineIndex = 0;
        result.append("\n0x");
        result.append(toHexString(offset));
        for (int i = offset; i < offset + length; ++i) {
            if (lineIndex == 16) {
                result.append(" ");
                for (int j = 0; j < 16; ++j) {
                    if (line[j] > 32 && line[j] < 126) {
                        result.append(new String(line, j, 1));
                    }
                    else {
                        result.append(".");
                    }
                }
                result.append("\n0x");
                result.append(toHexString(i));
                lineIndex = 0;
            }
            final byte b = array[i];
            result.append(" ");
            result.append(HexDump.HEX_DIGITS[b >>> 4 & 0xF]);
            result.append(HexDump.HEX_DIGITS[b & 0xF]);
            line[lineIndex++] = b;
        }
        if (lineIndex != 16) {
            int count = (16 - lineIndex) * 3;
            ++count;
            for (int k = 0; k < count; ++k) {
                result.append(" ");
            }
            for (int k = 0; k < lineIndex; ++k) {
                if (line[k] > 32 && line[k] < 126) {
                    result.append(new String(line, k, 1));
                }
                else {
                    result.append(".");
                }
            }
        }
        return result.toString();
    }
    
    public static String toHexString(final byte b) {
        return toHexString(toByteArray(b));
    }
    
    public static String toHexString(final byte[] array) {
        return toHexString(array, 0, array.length);
    }
    
    public static String toHexString(final byte[] array, final int offset, final int length) {
        final char[] buf = new char[length * 2];
        int bufIndex = 0;
        for (int i = offset; i < offset + length; ++i) {
            final byte b = array[i];
            buf[bufIndex++] = HexDump.HEX_DIGITS[b >>> 4 & 0xF];
            buf[bufIndex++] = HexDump.HEX_DIGITS[b & 0xF];
        }
        return new String(buf);
    }
    
    public static String toHexString(final int i) {
        return toHexString(toByteArray(i));
    }
    
    public static String toHexString(final short i) {
        return toHexString(toByteArray(i));
    }
    
    public static byte[] toByteArray(final byte b) {
        final byte[] array = { b };
        return array;
    }
    
    public static byte[] toByteArray(final int i) {
        final byte[] array = { (byte)(i >> 24 & 0xFF), (byte)(i >> 16 & 0xFF), (byte)(i >> 8 & 0xFF), (byte)(i & 0xFF) };
        return array;
    }
    
    public static byte[] toByteArray(final short i) {
        final byte[] array = { (byte)(i >> 8 & 0xFF), (byte)(i & 0xFF) };
        return array;
    }
    
    private static int toByte(final char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        throw new RuntimeException("Invalid hex char '" + c + "'");
    }
    
    public static byte[] hexStringToByteArray(final String hexString) {
        final int length = hexString.length();
        final byte[] buffer = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            buffer[i / 2] = (byte)(toByte(hexString.charAt(i)) << 4 | toByte(hexString.charAt(i + 1)));
        }
        return buffer;
    }
    
    static {
        HEX_DIGITS = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
    }
}
