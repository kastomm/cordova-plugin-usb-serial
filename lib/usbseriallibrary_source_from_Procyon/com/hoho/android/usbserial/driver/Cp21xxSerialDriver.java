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

public class Cp21xxSerialDriver implements UsbSerialDriver
{
    private static final String TAG;
    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;
    
    public Cp21xxSerialDriver(final UsbDevice device) {
        this.mDevice = device;
        this.mPort = new Cp21xxSerialPort(this.mDevice, 0);
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
        supportedDevices.put(4292, new int[] { 60000, 60016, 60017, 60032 });
        return supportedDevices;
    }
    
    static {
        TAG = Cp21xxSerialDriver.class.getSimpleName();
    }
    
    public class Cp21xxSerialPort extends CommonUsbSerialPort
    {
        private static final int DEFAULT_BAUD_RATE = 9600;
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private static final int REQTYPE_HOST_TO_DEVICE = 65;
        private static final int SILABSER_IFC_ENABLE_REQUEST_CODE = 0;
        private static final int SILABSER_SET_BAUDDIV_REQUEST_CODE = 1;
        private static final int SILABSER_SET_LINE_CTL_REQUEST_CODE = 3;
        private static final int SILABSER_SET_MHS_REQUEST_CODE = 7;
        private static final int SILABSER_SET_BAUDRATE = 30;
        private static final int SILABSER_FLUSH_REQUEST_CODE = 18;
        private static final int FLUSH_READ_CODE = 10;
        private static final int FLUSH_WRITE_CODE = 5;
        private static final int UART_ENABLE = 1;
        private static final int UART_DISABLE = 0;
        private static final int BAUD_RATE_GEN_FREQ = 3686400;
        private static final int MCR_DTR = 1;
        private static final int MCR_RTS = 2;
        private static final int MCR_ALL = 3;
        private static final int CONTROL_WRITE_DTR = 256;
        private static final int CONTROL_WRITE_RTS = 512;
        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;
        
        public Cp21xxSerialPort(final UsbDevice device, final int portNumber) {
            super(device, portNumber);
        }
        
        @Override
        public UsbSerialDriver getDriver() {
            return Cp21xxSerialDriver.this;
        }
        
        private int setConfigSingle(final int request, final int value) {
            return this.mConnection.controlTransfer(65, request, value, 0, (byte[])null, 0, 5000);
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
                        Log.d(Cp21xxSerialDriver.TAG, "claimInterface " + i + " SUCCESS");
                    }
                    else {
                        Log.d(Cp21xxSerialDriver.TAG, "claimInterface " + i + " FAIL");
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
                this.setConfigSingle(0, 1);
                this.setConfigSingle(7, 771);
                this.setConfigSingle(1, 384);
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
                this.setConfigSingle(0, 0);
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
                Log.d(Cp21xxSerialDriver.TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            }
            return offset;
        }
        
        private void setBaudRate(final int baudRate) throws IOException {
            final byte[] data = { (byte)(baudRate & 0xFF), (byte)(baudRate >> 8 & 0xFF), (byte)(baudRate >> 16 & 0xFF), (byte)(baudRate >> 24 & 0xFF) };
            final int ret = this.mConnection.controlTransfer(65, 30, 0, 0, data, 4, 5000);
            if (ret < 0) {
                throw new IOException("Error setting baud rate.");
            }
        }
        
        @Override
        public void setParameters(final int baudRate, final int dataBits, final int stopBits, final int parity) throws IOException {
            this.setBaudRate(baudRate);
            int configDataBits = 0;
            switch (dataBits) {
                case 5: {
                    configDataBits |= 0x500;
                    break;
                }
                case 6: {
                    configDataBits |= 0x600;
                    break;
                }
                case 7: {
                    configDataBits |= 0x700;
                    break;
                }
                case 8: {
                    configDataBits |= 0x800;
                    break;
                }
                default: {
                    configDataBits |= 0x800;
                    break;
                }
            }
            switch (parity) {
                case 1: {
                    configDataBits |= 0x10;
                    break;
                }
                case 2: {
                    configDataBits |= 0x20;
                    break;
                }
            }
            switch (stopBits) {
                case 1: {
                    configDataBits |= 0x0;
                    break;
                }
                case 2: {
                    configDataBits |= 0x2;
                    break;
                }
            }
            this.setConfigSingle(3, configDataBits);
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
            return true;
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
            return true;
        }
        
        @Override
        public void setRTS(final boolean value) throws IOException {
        }
        
        @Override
        public boolean purgeHwBuffers(final boolean purgeReadBuffers, final boolean purgeWriteBuffers) throws IOException {
            final int value = (purgeReadBuffers ? 10 : 0) | (purgeWriteBuffers ? 5 : 0);
            if (value != 0) {
                this.setConfigSingle(18, value);
            }
            return true;
        }
    }
}
