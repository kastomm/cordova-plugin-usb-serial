// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import android.hardware.usb.UsbInterface;
import android.util.Log;
import java.io.IOException;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import android.hardware.usb.UsbDevice;

public class Ch34xSerialDriver implements UsbSerialDriver
{
    private static final String TAG;
    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;
    
    public Ch34xSerialDriver(final UsbDevice device) {
        this.mDevice = device;
        this.mPort = new Ch340SerialPort(this.mDevice, 0);
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
        supportedDevices.put(6790, new int[] { 29987 });
        return supportedDevices;
    }
    
    static {
        TAG = Ch34xSerialDriver.class.getSimpleName();
    }
    
    public class Ch340SerialPort extends CommonUsbSerialPort
    {
        private static final int USB_TIMEOUT_MILLIS = 5000;
        private final int DEFAULT_BAUD_RATE = 9600;
        private boolean dtr;
        private boolean rts;
        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;
        
        public Ch340SerialPort(final UsbDevice device, final int portNumber) {
            super(device, portNumber);
            this.dtr = false;
            this.rts = false;
        }
        
        @Override
        public UsbSerialDriver getDriver() {
            return Ch34xSerialDriver.this;
        }
        
        @Override
        public void open(final UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already opened.");
            }
            this.mConnection = connection;
            boolean opened = false;
            try {
                for (int i = 0; i < this.mDevice.getInterfaceCount(); ++i) {
                    final UsbInterface usbIface = this.mDevice.getInterface(i);
                    if (this.mConnection.claimInterface(usbIface, true)) {
                        Log.d(Ch34xSerialDriver.TAG, "claimInterface " + i + " SUCCESS");
                    }
                    else {
                        Log.d(Ch34xSerialDriver.TAG, "claimInterface " + i + " FAIL");
                    }
                }
                final UsbInterface dataIface = this.mDevice.getInterface(this.mDevice.getInterfaceCount() - 1);
                for (int j = 0; j < dataIface.getEndpointCount(); ++j) {
                    final UsbEndpoint ep = dataIface.getEndpoint(j);
                    if (ep.getType() == 2) {
                        if (ep.getDirection() == 128) {
                            this.mReadEndpoint = ep;
                        }
                        else {
                            this.mWriteEndpoint = ep;
                        }
                    }
                }
                this.initialize();
                this.setBaudRate(9600);
                opened = true;
            }
            finally {
                if (!opened) {
                    try {
                        this.close();
                    }
                    catch (IOException ex) {}
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
            final int numBytesRead;
            synchronized (this.mReadBufferLock) {
                final int readAmt = Math.min(dest.length, this.mReadBuffer.length);
                numBytesRead = this.mConnection.bulkTransfer(this.mReadEndpoint, this.mReadBuffer, readAmt, timeoutMillis);
                if (numBytesRead < 0) {
                    return 0;
                }
                System.arraycopy(this.mReadBuffer, 0, dest, 0, numBytesRead);
            }
            return numBytesRead;
        }
        
        @Override
        public int write(final byte[] src, final int timeoutMillis) throws IOException {
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
                    amtWritten = this.mConnection.bulkTransfer(this.mWriteEndpoint, writeBuffer, writeLength, timeoutMillis);
                }
                if (amtWritten <= 0) {
                    throw new IOException("Error writing " + writeLength + " bytes at offset " + offset + " length=" + src.length);
                }
                Log.d(Ch34xSerialDriver.TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            }
            return offset;
        }
        
        private int controlOut(final int request, final int value, final int index) {
            final int REQTYPE_HOST_TO_DEVICE = 65;
            return this.mConnection.controlTransfer(65, request, value, index, (byte[])null, 0, 5000);
        }
        
        private int controlIn(final int request, final int value, final int index, final byte[] buffer) {
            final int REQTYPE_HOST_TO_DEVICE = 192;
            return this.mConnection.controlTransfer(192, request, value, index, buffer, buffer.length, 5000);
        }
        
        private void checkState(final String msg, final int request, final int value, final int[] expected) throws IOException {
            final byte[] buffer = new byte[expected.length];
            final int ret = this.controlIn(request, value, 0, buffer);
            if (ret < 0) {
                throw new IOException("Faild send cmd [" + msg + "]");
            }
            if (ret != expected.length) {
                throw new IOException("Expected " + expected.length + " bytes, but get " + ret + " [" + msg + "]");
            }
            for (int i = 0; i < expected.length; ++i) {
                if (expected[i] != -1) {
                    final int current = buffer[i] & 0xFF;
                    if (expected[i] != current) {
                        throw new IOException("Expected 0x" + Integer.toHexString(expected[i]) + " bytes, but get 0x" + Integer.toHexString(current) + " [" + msg + "]");
                    }
                }
            }
        }
        
        private void writeHandshakeByte() throws IOException {
            if (this.controlOut(164, ~((this.dtr ? 32 : 0) | (this.rts ? 64 : 0)), 0) < 0) {
                throw new IOException("Faild to set handshake byte");
            }
        }
        
        private void initialize() throws IOException {
            this.checkState("init #1", 95, 0, new int[] { -1, 0 });
            if (this.controlOut(161, 0, 0) < 0) {
                throw new IOException("init failed! #2");
            }
            this.setBaudRate(9600);
            this.checkState("init #4", 149, 9496, new int[] { -1, 0 });
            if (this.controlOut(154, 9496, 80) < 0) {
                throw new IOException("init failed! #5");
            }
            this.checkState("init #6", 149, 1798, new int[] { 255, 238 });
            if (this.controlOut(161, 20511, 55562) < 0) {
                throw new IOException("init failed! #7");
            }
            this.setBaudRate(9600);
            this.writeHandshakeByte();
            this.checkState("init #10", 149, 1798, new int[] { -1, 238 });
        }
        
        private void setBaudRate(final int baudRate) throws IOException {
            final int[] baud = { 2400, 55553, 56, 4800, 25602, 31, 9600, 45570, 19, 19200, 55554, 13, 38400, 25603, 10, 115200, 52227, 8 };
            int i = 0;
            while (i < baud.length / 3) {
                if (baud[i * 3] == baudRate) {
                    int ret = this.controlOut(154, 4882, baud[i * 3 + 1]);
                    if (ret < 0) {
                        throw new IOException("Error setting baud rate. #1");
                    }
                    ret = this.controlOut(154, 3884, baud[i * 3 + 2]);
                    if (ret < 0) {
                        throw new IOException("Error setting baud rate. #1");
                    }
                    return;
                }
                else {
                    ++i;
                }
            }
            throw new IOException("Baud rate " + baudRate + " currently not supported");
        }
        
        @Override
        public void setParameters(final int baudRate, final int dataBits, final int stopBits, final int parity) throws IOException {
            this.setBaudRate(baudRate);
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
            return this.dtr;
        }
        
        @Override
        public void setDTR(final boolean value) throws IOException {
            this.dtr = value;
            this.writeHandshakeByte();
        }
        
        @Override
        public boolean getRI() throws IOException {
            return false;
        }
        
        @Override
        public boolean getRTS() throws IOException {
            return this.rts;
        }
        
        @Override
        public void setRTS(final boolean value) throws IOException {
            this.rts = value;
            this.writeHandshakeByte();
        }
        
        @Override
        public boolean purgeHwBuffers(final boolean purgeReadBuffers, final boolean purgeWriteBuffers) throws IOException {
            return true;
        }
    }
}
