// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.nio.ByteBuffer;
import android.hardware.usb.UsbRequest;
import android.util.Log;
import java.io.IOException;
import android.hardware.usb.UsbDeviceConnection;
import android.os.Build;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import android.hardware.usb.UsbDevice;

public class CdcAcmSerialDriver implements UsbSerialDriver
{
    private final String TAG;
    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;
    
    public CdcAcmSerialDriver(final UsbDevice device) {
        this.TAG = CdcAcmSerialDriver.class.getSimpleName();
        this.mDevice = device;
        this.mPort = new CdcAcmSerialPort(device, 0);
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
        supportedDevices.put(9025, new int[] { 1, 67, 16, 66, 59, 68, 63, 68, 32822, 32823 });
        supportedDevices.put(5824, new int[] { 1155 });
        supportedDevices.put(1003, new int[] { 8260 });
        supportedDevices.put(7855, new int[] { 4 });
        return supportedDevices;
    }
    
    class CdcAcmSerialPort extends CommonUsbSerialPort
    {
        private final boolean mEnableAsyncReads;
        private UsbInterface mControlInterface;
        private UsbInterface mDataInterface;
        private UsbEndpoint mControlEndpoint;
        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;
        private boolean mRts;
        private boolean mDtr;
        private static final int USB_RECIP_INTERFACE = 1;
        private static final int USB_RT_ACM = 33;
        private static final int SET_LINE_CODING = 32;
        private static final int GET_LINE_CODING = 33;
        private static final int SET_CONTROL_LINE_STATE = 34;
        private static final int SEND_BREAK = 35;
        
        public CdcAcmSerialPort(final UsbDevice device, final int portNumber) {
            super(device, portNumber);
            this.mRts = false;
            this.mDtr = false;
            this.mEnableAsyncReads = (Build.VERSION.SDK_INT >= 17);
        }
        
        @Override
        public UsbSerialDriver getDriver() {
            return CdcAcmSerialDriver.this;
        }
        
        @Override
        public void open(final UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already open");
            }
            this.mConnection = connection;
            boolean opened = false;
            try {
                if (1 == this.mDevice.getInterfaceCount()) {
                    Log.d(CdcAcmSerialDriver.this.TAG, "device might be castrated ACM device, trying single interface logic");
                    this.openSingleInterface();
                }
                else {
                    Log.d(CdcAcmSerialDriver.this.TAG, "trying default interface logic");
                    this.openInterface();
                }
                if (this.mEnableAsyncReads) {
                    Log.d(CdcAcmSerialDriver.this.TAG, "Async reads enabled");
                }
                else {
                    Log.d(CdcAcmSerialDriver.this.TAG, "Async reads disabled.");
                }
                opened = true;
            }
            finally {
                if (!opened) {
                    this.mConnection = null;
                    this.mControlEndpoint = null;
                    this.mReadEndpoint = null;
                    this.mWriteEndpoint = null;
                }
            }
        }
        
        private void openSingleInterface() throws IOException {
            this.mControlInterface = this.mDevice.getInterface(0);
            Log.d(CdcAcmSerialDriver.this.TAG, "Control iface=" + this.mControlInterface);
            this.mDataInterface = this.mDevice.getInterface(0);
            Log.d(CdcAcmSerialDriver.this.TAG, "data iface=" + this.mDataInterface);
            if (!this.mConnection.claimInterface(this.mControlInterface, true)) {
                throw new IOException("Could not claim shared control/data interface.");
            }
            final int endCount = this.mControlInterface.getEndpointCount();
            if (endCount < 3) {
                Log.d(CdcAcmSerialDriver.this.TAG, "not enough endpoints - need 3. count=" + this.mControlInterface.getEndpointCount());
                throw new IOException("bang fuck Insufficient number of endpoints(" + this.mControlInterface.getEndpointCount() + ")");
            }
            this.mControlEndpoint = null;
            this.mReadEndpoint = null;
            this.mWriteEndpoint = null;
            for (int i = 0; i < endCount; ++i) {
                final UsbEndpoint ep = this.mControlInterface.getEndpoint(i);
                if (ep.getDirection() == 128 && ep.getType() == 3) {
                    Log.d(CdcAcmSerialDriver.this.TAG, "Found controlling endpoint");
                    this.mControlEndpoint = ep;
                }
                else if (ep.getDirection() == 128 && ep.getType() == 2) {
                    Log.d(CdcAcmSerialDriver.this.TAG, "Found reading endpoint");
                    this.mReadEndpoint = ep;
                }
                else if (ep.getDirection() == 0 && ep.getType() == 2) {
                    Log.d(CdcAcmSerialDriver.this.TAG, "Found writing endpoint");
                    this.mWriteEndpoint = ep;
                }
                if (this.mControlEndpoint != null && this.mReadEndpoint != null && this.mWriteEndpoint != null) {
                    Log.d(CdcAcmSerialDriver.this.TAG, "Found all required endpoints");
                    break;
                }
            }
            if (this.mControlEndpoint == null || this.mReadEndpoint == null || this.mWriteEndpoint == null) {
                Log.d(CdcAcmSerialDriver.this.TAG, "Could not establish all endpoints");
                throw new IOException("Could not establish all endpoints");
            }
        }
        
        private void openInterface() throws IOException {
            Log.d(CdcAcmSerialDriver.this.TAG, "claiming interfaces, count=" + this.mDevice.getInterfaceCount());
            this.mControlInterface = this.mDevice.getInterface(0);
            Log.d(CdcAcmSerialDriver.this.TAG, "Control iface=" + this.mControlInterface);
            if (!this.mConnection.claimInterface(this.mControlInterface, true)) {
                throw new IOException("Could not claim control interface.");
            }
            this.mControlEndpoint = this.mControlInterface.getEndpoint(0);
            Log.d(CdcAcmSerialDriver.this.TAG, "Control endpoint direction: " + this.mControlEndpoint.getDirection());
            Log.d(CdcAcmSerialDriver.this.TAG, "Claiming data interface.");
            this.mDataInterface = this.mDevice.getInterface(1);
            Log.d(CdcAcmSerialDriver.this.TAG, "data iface=" + this.mDataInterface);
            if (!this.mConnection.claimInterface(this.mDataInterface, true)) {
                throw new IOException("Could not claim data interface.");
            }
            final UsbEndpoint ep0 = this.mDataInterface.getEndpoint(0);
            final UsbEndpoint ep2 = this.mDataInterface.getEndpoint(1);
            if (ep0.getDirection() == 128) {
                this.mReadEndpoint = ep0;
                this.mWriteEndpoint = ep2;
            }
            else {
                this.mReadEndpoint = ep2;
                this.mWriteEndpoint = ep0;
            }
            Log.d(CdcAcmSerialDriver.this.TAG, "Read endpoint direction: " + this.mReadEndpoint.getDirection());
            Log.d(CdcAcmSerialDriver.this.TAG, "Write endpoint direction: " + this.mWriteEndpoint.getDirection());
        }
        
        private int sendAcmControlMessage(final int request, final int value, final byte[] buf) {
            return this.mConnection.controlTransfer(33, request, value, 0, buf, (buf != null) ? buf.length : 0, 5000);
        }
        
        @Override
        public void close() throws IOException {
            if (this.mConnection == null) {
                throw new IOException("Already closed");
            }
            this.mConnection.close();
            this.mConnection = null;
        }
        
        @Override
        public int read(final byte[] dest, final int timeoutMillis) throws IOException {
            if (this.mEnableAsyncReads) {
                final UsbRequest request = new UsbRequest();
                try {
                    request.initialize(this.mConnection, this.mReadEndpoint);
                    final ByteBuffer buf = ByteBuffer.wrap(dest);
                    if (!request.queue(buf, dest.length)) {
                        throw new IOException("Error queueing request.");
                    }
                    final UsbRequest response = this.mConnection.requestWait();
                    if (response == null) {
                        throw new IOException("Null response");
                    }
                    final int nread = buf.position();
                    if (nread > 0) {
                        return nread;
                    }
                    return 0;
                }
                finally {
                    request.close();
                }
            }
            final int numBytesRead;
            synchronized (this.mReadBufferLock) {
                final int readAmt = Math.min(dest.length, this.mReadBuffer.length);
                numBytesRead = this.mConnection.bulkTransfer(this.mReadEndpoint, this.mReadBuffer, readAmt, timeoutMillis);
                if (numBytesRead < 0) {
                    if (timeoutMillis == Integer.MAX_VALUE) {
                        return -1;
                    }
                    return 0;
                }
                else {
                    System.arraycopy(this.mReadBuffer, 0, dest, 0, numBytesRead);
                }
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
                Log.d(CdcAcmSerialDriver.this.TAG, "Wrote amt=" + amtWritten + " attempted=" + writeLength);
            }
            return offset;
        }
        
        @Override
        public void setParameters(final int baudRate, final int dataBits, final int stopBits, final int parity) {
            byte stopBitsByte = 0;
            switch (stopBits) {
                case 1: {
                    stopBitsByte = 0;
                    break;
                }
                case 3: {
                    stopBitsByte = 1;
                    break;
                }
                case 2: {
                    stopBitsByte = 2;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Bad value for stopBits: " + stopBits);
                }
            }
            byte parityBitesByte = 0;
            switch (parity) {
                case 0: {
                    parityBitesByte = 0;
                    break;
                }
                case 1: {
                    parityBitesByte = 1;
                    break;
                }
                case 2: {
                    parityBitesByte = 2;
                    break;
                }
                case 3: {
                    parityBitesByte = 3;
                    break;
                }
                case 4: {
                    parityBitesByte = 4;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Bad value for parity: " + parity);
                }
            }
            final byte[] msg = { (byte)(baudRate & 0xFF), (byte)(baudRate >> 8 & 0xFF), (byte)(baudRate >> 16 & 0xFF), (byte)(baudRate >> 24 & 0xFF), stopBitsByte, parityBitesByte, (byte)dataBits };
            this.sendAcmControlMessage(32, 0, msg);
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
            return this.mDtr;
        }
        
        @Override
        public void setDTR(final boolean value) throws IOException {
            this.mDtr = value;
            this.setDtrRts();
        }
        
        @Override
        public boolean getRI() throws IOException {
            return false;
        }
        
        @Override
        public boolean getRTS() throws IOException {
            return this.mRts;
        }
        
        @Override
        public void setRTS(final boolean value) throws IOException {
            this.mRts = value;
            this.setDtrRts();
        }
        
        private void setDtrRts() {
            final int value = (this.mRts ? 2 : 0) | (this.mDtr ? 1 : 0);
            this.sendAcmControlMessage(34, value, null);
        }
    }
}
