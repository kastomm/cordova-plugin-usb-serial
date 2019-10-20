// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbEndpoint;
import android.util.Log;
import android.hardware.usb.UsbDeviceConnection;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import android.hardware.usb.UsbDevice;

public class FtdiSerialDriver implements UsbSerialDriver
{
    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;
    
    public FtdiSerialDriver(final UsbDevice device) {
        this.mDevice = device;
        this.mPort = new FtdiSerialPort(this.mDevice, 0);
    }
    
    @Override
    public UsbDevice getDevice() {
        return this.mDevice;
    }
    
    @Override
    public List<UsbSerialPort> getPorts() {
        return Collections.singletonList(this.mPort);
    }
    
    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(1027, new int[] { 24577, 24597 });
        return supportedDevices;
    }
    
    private enum DeviceType
    {
        TYPE_BM, 
        TYPE_AM, 
        TYPE_2232C, 
        TYPE_R, 
        TYPE_2232H, 
        TYPE_4232H;
    }
    
    private class FtdiSerialPort extends CommonUsbSerialPort
    {
        public static final int USB_TYPE_STANDARD = 0;
        public static final int USB_TYPE_CLASS = 0;
        public static final int USB_TYPE_VENDOR = 0;
        public static final int USB_TYPE_RESERVED = 0;
        public static final int USB_RECIP_DEVICE = 0;
        public static final int USB_RECIP_INTERFACE = 1;
        public static final int USB_RECIP_ENDPOINT = 2;
        public static final int USB_RECIP_OTHER = 3;
        public static final int USB_ENDPOINT_IN = 128;
        public static final int USB_ENDPOINT_OUT = 0;
        public static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        public static final int USB_READ_TIMEOUT_MILLIS = 5000;
        private static final int SIO_RESET_REQUEST = 0;
        private static final int SIO_MODEM_CTRL_REQUEST = 1;
        private static final int SIO_SET_FLOW_CTRL_REQUEST = 2;
        private static final int SIO_SET_BAUD_RATE_REQUEST = 3;
        private static final int SIO_SET_DATA_REQUEST = 4;
        private static final int SIO_RESET_SIO = 0;
        private static final int SIO_RESET_PURGE_RX = 1;
        private static final int SIO_RESET_PURGE_TX = 2;
        public static final int FTDI_DEVICE_OUT_REQTYPE = 64;
        public static final int FTDI_DEVICE_IN_REQTYPE = 192;
        private static final int MODEM_STATUS_HEADER_LENGTH = 2;
        private final String TAG;
        private DeviceType mType;
        private int mInterface;
        private int mMaxPacketSize;
        private static final boolean ENABLE_ASYNC_READS = false;
        
        public FtdiSerialPort(final UsbDevice device, final int portNumber) {
            super(device, portNumber);
            this.TAG = FtdiSerialDriver.class.getSimpleName();
            this.mInterface = 0;
            this.mMaxPacketSize = 64;
        }
        
        @Override
        public UsbSerialDriver getDriver() {
            return FtdiSerialDriver.this;
        }
        
        private final int filterStatusBytes(final byte[] src, final byte[] dest, final int totalBytesRead, final int maxPacketSize) {
            final int packetsCount = totalBytesRead / maxPacketSize + ((totalBytesRead % maxPacketSize != 0) ? 1 : 0);
            for (int packetIdx = 0; packetIdx < packetsCount; ++packetIdx) {
                final int count = (packetIdx == packetsCount - 1) ? (totalBytesRead % maxPacketSize - 2) : (maxPacketSize - 2);
                if (count > 0) {
                    System.arraycopy(src, packetIdx * maxPacketSize + 2, dest, packetIdx * (maxPacketSize - 2), count);
                }
            }
            return totalBytesRead - packetsCount * 2;
        }
        
        public void reset() throws IOException {
            final int result = this.mConnection.controlTransfer(64, 0, 0, 0, (byte[])null, 0, 5000);
            if (result != 0) {
                throw new IOException("Reset failed: result=" + result);
            }
            this.mType = DeviceType.TYPE_R;
        }
        
        @Override
        public void open(final UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already open");
            }
            this.mConnection = connection;
            boolean opened = false;
            try {
                for (int i = 0; i < this.mDevice.getInterfaceCount(); ++i) {
                    if (!connection.claimInterface(this.mDevice.getInterface(i), true)) {
                        throw new IOException("Error claiming interface " + i);
                    }
                    Log.d(this.TAG, "claimInterface " + i + " SUCCESS");
                }
                this.reset();
                opened = true;
            }
            finally {
                if (!opened) {
                    this.close();
                    this.mConnection = null;
                }
            }
        }
        
        @Override
        public void close() throws IOException {
            if (this.mConnection == null) {
                throw new IOException("Already closed");
            }
            try {
                this.mConnection.close();
            }
            finally {
                this.mConnection = null;
            }
        }
        
        @Override
        public int read(final byte[] dest, final int timeoutMillis) throws IOException {
            final UsbEndpoint endpoint = this.mDevice.getInterface(0).getEndpoint(0);
            synchronized (this.mReadBufferLock) {
                final int readAmt = Math.min(dest.length, this.mReadBuffer.length);
                final int totalBytesRead = this.mConnection.bulkTransfer(endpoint, this.mReadBuffer, readAmt, timeoutMillis);
                if (totalBytesRead < 2) {
                    throw new IOException("Expected at least 2 bytes");
                }
                return this.filterStatusBytes(this.mReadBuffer, dest, totalBytesRead, endpoint.getMaxPacketSize());
            }
        }
        
        @Override
        public int write(final byte[] src, final int timeoutMillis) throws IOException {
            final UsbEndpoint endpoint = this.mDevice.getInterface(0).getEndpoint(1);
            int offset;
            int amtWritten;
            for (offset = 0; offset < src.length; offset += amtWritten) {
                final int writeLength;
                synchronized (this.mWriteBufferLock) {
                    writeLength = Math.min(src.length - offset, this.mWriteBuffer.length);
                    byte[] writeBuffer;
                    if (offset == 0) {
                        writeBuffer = src;
                    }
                    else {
                        System.arraycopy(src, offset, this.mWriteBuffer, 0, writeLength);
                        writeBuffer = this.mWriteBuffer;
                    }
                    amtWritten = this.mConnection.bulkTransfer(endpoint, writeBuffer, writeLength, timeoutMillis);
                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength + " bytes at offset " + offset + " length=" + src.length);
                }
                Log.d(this.TAG, "Wrote amtWritten=" + amtWritten + " attempted=" + writeLength);
            }
            return offset;
        }
        
        private int setBaudRate(final int baudRate) throws IOException {
            final long[] vals = this.convertBaudrate(baudRate);
            final long actualBaudrate = vals[0];
            final long index = vals[1];
            final long value = vals[2];
            final int result = this.mConnection.controlTransfer(64, 3, (int)value, (int)index, (byte[])null, 0, 5000);
            if (result != 0) {
                throw new IOException("Setting baudrate failed: result=" + result);
            }
            return (int)actualBaudrate;
        }
        
        @Override
        public void setParameters(final int baudRate, final int dataBits, final int stopBits, final int parity) throws IOException {
            this.setBaudRate(baudRate);
            int config = dataBits;
            switch (parity) {
                case 0: {
                    config |= 0x0;
                    break;
                }
                case 1: {
                    config |= 0x100;
                    break;
                }
                case 2: {
                    config |= 0x200;
                    break;
                }
                case 3: {
                    config |= 0x300;
                    break;
                }
                case 4: {
                    config |= 0x400;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown parity value: " + parity);
                }
            }
            switch (stopBits) {
                case 1: {
                    config |= 0x0;
                    break;
                }
                case 3: {
                    config |= 0x800;
                    break;
                }
                case 2: {
                    config |= 0x1000;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown stopBits value: " + stopBits);
                }
            }
            final int result = this.mConnection.controlTransfer(64, 4, config, 0, (byte[])null, 0, 5000);
            if (result != 0) {
                throw new IOException("Setting parameters failed: result=" + result);
            }
        }
        
        private long[] convertBaudrate(final int baudrate) {
            final int divisor = 24000000 / baudrate;
            int bestDivisor = 0;
            int bestBaud = 0;
            int bestBaudDiff = 0;
            final int[] fracCode = { 0, 3, 2, 4, 1, 5, 6, 7 };
            for (int i = 0; i < 2; ++i) {
                int tryDivisor = divisor + i;
                if (tryDivisor <= 8) {
                    tryDivisor = 8;
                }
                else if (this.mType != DeviceType.TYPE_AM && tryDivisor < 12) {
                    tryDivisor = 12;
                }
                else if (divisor < 16) {
                    tryDivisor = 16;
                }
                else if (this.mType != DeviceType.TYPE_AM) {
                    if (tryDivisor > 131071) {
                        tryDivisor = 131071;
                    }
                }
                final int baudEstimate = (24000000 + tryDivisor / 2) / tryDivisor;
                int baudDiff;
                if (baudEstimate < baudrate) {
                    baudDiff = baudrate - baudEstimate;
                }
                else {
                    baudDiff = baudEstimate - baudrate;
                }
                if (i == 0 || baudDiff < bestBaudDiff) {
                    bestDivisor = tryDivisor;
                    bestBaud = baudEstimate;
                    if ((bestBaudDiff = baudDiff) == 0) {
                        break;
                    }
                }
            }
            long encodedDivisor = bestDivisor >> 3 | fracCode[bestDivisor & 0x7] << 14;
            if (encodedDivisor == 1L) {
                encodedDivisor = 0L;
            }
            else if (encodedDivisor == 16385L) {
                encodedDivisor = 1L;
            }
            final long value = encodedDivisor & 0xFFFFL;
            long index;
            if (this.mType == DeviceType.TYPE_2232C || this.mType == DeviceType.TYPE_2232H || this.mType == DeviceType.TYPE_4232H) {
                index = (encodedDivisor >> 8 & 0xFFFFL);
                index &= 0xFF00L;
                index |= 0x0L;
            }
            else {
                index = (encodedDivisor >> 16 & 0xFFFFL);
            }
            return new long[] { bestBaud, index, value };
        }
        
        @Override
        public boolean getCD() throws IOException {
            return false;
        }
        
        @Override
        public boolean getCTS() throws IOException {
            return false;
        }
        
        @Override
        public boolean getDSR() throws IOException {
            return false;
        }
        
        @Override
        public boolean getDTR() throws IOException {
            return false;
        }
        
        @Override
        public void setDTR(final boolean value) throws IOException {
        }
        
        @Override
        public boolean getRI() throws IOException {
            return false;
        }
        
        @Override
        public boolean getRTS() throws IOException {
            return false;
        }
        
        @Override
        public void setRTS(final boolean value) throws IOException {
        }
        
        @Override
        public boolean purgeHwBuffers(final boolean purgeReadBuffers, final boolean purgeWriteBuffers) throws IOException {
            if (purgeReadBuffers) {
                final int result = this.mConnection.controlTransfer(64, 0, 1, 0, (byte[])null, 0, 5000);
                if (result != 0) {
                    throw new IOException("Flushing RX failed: result=" + result);
                }
            }
            if (purgeWriteBuffers) {
                final int result = this.mConnection.controlTransfer(64, 0, 2, 0, (byte[])null, 0, 5000);
                if (result != 0) {
                    throw new IOException("Flushing RX failed: result=" + result);
                }
            }
            return true;
        }
    }
}
