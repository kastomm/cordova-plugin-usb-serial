// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.lang.reflect.Method;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;
import java.io.IOException;
import android.hardware.usb.UsbEndpoint;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import android.hardware.usb.UsbDevice;

public class ProlificSerialDriver implements UsbSerialDriver
{
    private final String TAG;
    private final UsbDevice mDevice;
    private final UsbSerialPort mPort;
    
    public ProlificSerialDriver(final UsbDevice device) {
        this.TAG = ProlificSerialDriver.class.getSimpleName();
        this.mDevice = device;
        this.mPort = new ProlificSerialPort(this.mDevice, 0);
    }
    
    @Override
    public List<UsbSerialPort> getPorts() {
        return Collections.singletonList(this.mPort);
    }
    
    @Override
    public UsbDevice getDevice() {
        return this.mDevice;
    }
    
    public static Map<Integer, int[]> getSupportedDevices() {
        final Map<Integer, int[]> supportedDevices = new LinkedHashMap<Integer, int[]>();
        supportedDevices.put(1659, new int[] { 8963 });
        return supportedDevices;
    }
    
    class ProlificSerialPort extends CommonUsbSerialPort
    {
        private static final int USB_READ_TIMEOUT_MILLIS = 1000;
        private static final int USB_WRITE_TIMEOUT_MILLIS = 5000;
        private static final int USB_RECIP_INTERFACE = 1;
        private static final int PROLIFIC_VENDOR_READ_REQUEST = 1;
        private static final int PROLIFIC_VENDOR_WRITE_REQUEST = 1;
        private static final int PROLIFIC_VENDOR_OUT_REQTYPE = 64;
        private static final int PROLIFIC_VENDOR_IN_REQTYPE = 192;
        private static final int PROLIFIC_CTRL_OUT_REQTYPE = 33;
        private static final int WRITE_ENDPOINT = 2;
        private static final int READ_ENDPOINT = 131;
        private static final int INTERRUPT_ENDPOINT = 129;
        private static final int FLUSH_RX_REQUEST = 8;
        private static final int FLUSH_TX_REQUEST = 9;
        private static final int SET_LINE_REQUEST = 32;
        private static final int SET_CONTROL_REQUEST = 34;
        private static final int CONTROL_DTR = 1;
        private static final int CONTROL_RTS = 2;
        private static final int STATUS_FLAG_CD = 1;
        private static final int STATUS_FLAG_DSR = 2;
        private static final int STATUS_FLAG_RI = 8;
        private static final int STATUS_FLAG_CTS = 128;
        private static final int STATUS_BUFFER_SIZE = 10;
        private static final int STATUS_BYTE_IDX = 8;
        private static final int DEVICE_TYPE_HX = 0;
        private static final int DEVICE_TYPE_0 = 1;
        private static final int DEVICE_TYPE_1 = 2;
        private int mDeviceType;
        private UsbEndpoint mReadEndpoint;
        private UsbEndpoint mWriteEndpoint;
        private UsbEndpoint mInterruptEndpoint;
        private int mControlLinesValue;
        private int mBaudRate;
        private int mDataBits;
        private int mStopBits;
        private int mParity;
        private int mStatus;
        private volatile Thread mReadStatusThread;
        private final Object mReadStatusThreadLock;
        boolean mStopReadStatusThread;
        private IOException mReadStatusException;
        
        public ProlificSerialPort(final UsbDevice device, final int portNumber) {
            super(device, portNumber);
            this.mDeviceType = 0;
            this.mControlLinesValue = 0;
            this.mBaudRate = -1;
            this.mDataBits = -1;
            this.mStopBits = -1;
            this.mParity = -1;
            this.mStatus = 0;
            this.mReadStatusThread = null;
            this.mReadStatusThreadLock = new Object();
            this.mStopReadStatusThread = false;
            this.mReadStatusException = null;
        }
        
        @Override
        public UsbSerialDriver getDriver() {
            return ProlificSerialDriver.this;
        }
        
        private final byte[] inControlTransfer(final int requestType, final int request, final int value, final int index, final int length) throws IOException {
            final byte[] buffer = new byte[length];
            final int result = this.mConnection.controlTransfer(requestType, request, value, index, buffer, length, 1000);
            if (result != length) {
                throw new IOException(String.format("ControlTransfer with value 0x%x failed: %d", value, result));
            }
            return buffer;
        }
        
        private final void outControlTransfer(final int requestType, final int request, final int value, final int index, final byte[] data) throws IOException {
            final int length = (data == null) ? 0 : data.length;
            final int result = this.mConnection.controlTransfer(requestType, request, value, index, data, length, 5000);
            if (result != length) {
                throw new IOException(String.format("ControlTransfer with value 0x%x failed: %d", value, result));
            }
        }
        
        private final byte[] vendorIn(final int value, final int index, final int length) throws IOException {
            return this.inControlTransfer(192, 1, value, index, length);
        }
        
        private final void vendorOut(final int value, final int index, final byte[] data) throws IOException {
            this.outControlTransfer(64, 1, value, index, data);
        }
        
        private void resetDevice() throws IOException {
            this.purgeHwBuffers(true, true);
        }
        
        private final void ctrlOut(final int request, final int value, final int index, final byte[] data) throws IOException {
            this.outControlTransfer(33, request, value, index, data);
        }
        
        private void doBlackMagic() throws IOException {
            this.vendorIn(33924, 0, 1);
            this.vendorOut(1028, 0, null);
            this.vendorIn(33924, 0, 1);
            this.vendorIn(33667, 0, 1);
            this.vendorIn(33924, 0, 1);
            this.vendorOut(1028, 1, null);
            this.vendorIn(33924, 0, 1);
            this.vendorIn(33667, 0, 1);
            this.vendorOut(0, 1, null);
            this.vendorOut(1, 0, null);
            this.vendorOut(2, (this.mDeviceType == 0) ? 68 : 36, null);
        }
        
        private void setControlLines(final int newControlLinesValue) throws IOException {
            this.ctrlOut(34, newControlLinesValue, 0, null);
            this.mControlLinesValue = newControlLinesValue;
        }
        
        private final void readStatusThreadFunction() {
            try {
                while (!this.mStopReadStatusThread) {
                    final byte[] buffer = new byte[10];
                    final int readBytesCount = this.mConnection.bulkTransfer(this.mInterruptEndpoint, buffer, 10, 500);
                    if (readBytesCount > 0) {
                        if (readBytesCount != 10) {
                            throw new IOException(String.format("Invalid CTS / DSR / CD / RI status buffer received, expected %d bytes, but received %d", 10, readBytesCount));
                        }
                        this.mStatus = (buffer[8] & 0xFF);
                    }
                }
            }
            catch (IOException e) {
                this.mReadStatusException = e;
            }
        }
        
        private final int getStatus() throws IOException {
            if (this.mReadStatusThread == null && this.mReadStatusException == null) {
                synchronized (this.mReadStatusThreadLock) {
                    if (this.mReadStatusThread == null) {
                        final byte[] buffer = new byte[10];
                        final int readBytes = this.mConnection.bulkTransfer(this.mInterruptEndpoint, buffer, 10, 100);
                        if (readBytes != 10) {
                            Log.w(ProlificSerialDriver.this.TAG, "Could not read initial CTS / DSR / CD / RI status");
                        }
                        else {
                            this.mStatus = (buffer[8] & 0xFF);
                        }
                        (this.mReadStatusThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                ProlificSerialPort.this.readStatusThreadFunction();
                            }
                        })).setDaemon(true);
                        this.mReadStatusThread.start();
                    }
                }
            }
            final IOException readStatusException = this.mReadStatusException;
            if (this.mReadStatusException != null) {
                this.mReadStatusException = null;
                throw readStatusException;
            }
            return this.mStatus;
        }
        
        private final boolean testStatusFlag(final int flag) throws IOException {
            return (this.getStatus() & flag) == flag;
        }
        
        @Override
        public void open(final UsbDeviceConnection connection) throws IOException {
            if (this.mConnection != null) {
                throw new IOException("Already open");
            }
            final UsbInterface usbInterface = this.mDevice.getInterface(0);
            if (!connection.claimInterface(usbInterface, true)) {
                throw new IOException("Error claiming Prolific interface 0");
            }
            this.mConnection = connection;
            boolean opened = false;
            try {
                for (int i = 0; i < usbInterface.getEndpointCount(); ++i) {
                    final UsbEndpoint currentEndpoint = usbInterface.getEndpoint(i);
                    switch (currentEndpoint.getAddress()) {
                        case 131: {
                            this.mReadEndpoint = currentEndpoint;
                            break;
                        }
                        case 2: {
                            this.mWriteEndpoint = currentEndpoint;
                            break;
                        }
                        case 129: {
                            this.mInterruptEndpoint = currentEndpoint;
                            break;
                        }
                    }
                }
                if (this.mDevice.getDeviceClass() == 2) {
                    this.mDeviceType = 1;
                }
                else {
                    try {
                        final Method getRawDescriptorsMethod = this.mConnection.getClass().getMethod("getRawDescriptors", (Class<?>[])new Class[0]);
                        final byte[] rawDescriptors = (byte[])getRawDescriptorsMethod.invoke(this.mConnection, new Object[0]);
                        final byte maxPacketSize0 = rawDescriptors[7];
                        if (maxPacketSize0 == 64) {
                            this.mDeviceType = 0;
                        }
                        else if (this.mDevice.getDeviceClass() == 0 || this.mDevice.getDeviceClass() == 255) {
                            this.mDeviceType = 2;
                        }
                        else {
                            Log.w(ProlificSerialDriver.this.TAG, "Could not detect PL2303 subtype, Assuming that it is a HX device");
                            this.mDeviceType = 0;
                        }
                    }
                    catch (NoSuchMethodException e2) {
                        Log.w(ProlificSerialDriver.this.TAG, "Method UsbDeviceConnection.getRawDescriptors, required for PL2303 subtype detection, not available! Assuming that it is a HX device");
                        this.mDeviceType = 0;
                    }
                    catch (Exception e) {
                        Log.e(ProlificSerialDriver.this.TAG, "An unexpected exception occured while trying to detect PL2303 subtype", (Throwable)e);
                    }
                }
                this.setControlLines(this.mControlLinesValue);
                this.resetDevice();
                this.doBlackMagic();
                opened = true;
            }
            finally {
                if (!opened) {
                    this.mConnection = null;
                    connection.releaseInterface(usbInterface);
                }
            }
        }
        
        @Override
        public void close() throws IOException {
            if (this.mConnection == null) {
                throw new IOException("Already closed");
            }
            try {
                this.mStopReadStatusThread = true;
                synchronized (this.mReadStatusThreadLock) {
                    if (this.mReadStatusThread != null) {
                        try {
                            this.mReadStatusThread.join();
                        }
                        catch (Exception e) {
                            Log.w(ProlificSerialDriver.this.TAG, "An error occured while waiting for status read thread", (Throwable)e);
                        }
                    }
                }
                this.resetDevice();
            }
            finally {
                try {
                    this.mConnection.releaseInterface(this.mDevice.getInterface(0));
                }
                finally {
                    this.mConnection = null;
                }
            }
        }
        
        @Override
        public int read(final byte[] dest, final int timeoutMillis) throws IOException {
            synchronized (this.mReadBufferLock) {
                final int readAmt = Math.min(dest.length, this.mReadBuffer.length);
                final int numBytesRead = this.mConnection.bulkTransfer(this.mReadEndpoint, this.mReadBuffer, readAmt, timeoutMillis);
                if (numBytesRead < 0) {
                    return 0;
                }
                System.arraycopy(this.mReadBuffer, 0, dest, 0, numBytesRead);
                return numBytesRead;
            }
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
            }
            return offset;
        }
        
        @Override
        public void setParameters(final int baudRate, final int dataBits, final int stopBits, final int parity) throws IOException {
            if (this.mBaudRate == baudRate && this.mDataBits == dataBits && this.mStopBits == stopBits && this.mParity == parity) {
                return;
            }
            final byte[] lineRequestData = { (byte)(baudRate & 0xFF), (byte)(baudRate >> 8 & 0xFF), (byte)(baudRate >> 16 & 0xFF), (byte)(baudRate >> 24 & 0xFF), 0, 0, 0 };
            switch (stopBits) {
                case 1: {
                    lineRequestData[4] = 0;
                    break;
                }
                case 3: {
                    lineRequestData[4] = 1;
                    break;
                }
                case 2: {
                    lineRequestData[4] = 2;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown stopBits value: " + stopBits);
                }
            }
            switch (parity) {
                case 0: {
                    lineRequestData[5] = 0;
                    break;
                }
                case 1: {
                    lineRequestData[5] = 1;
                    break;
                }
                case 2: {
                    lineRequestData[5] = 2;
                    break;
                }
                case 3: {
                    lineRequestData[5] = 3;
                    break;
                }
                case 4: {
                    lineRequestData[5] = 4;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("Unknown parity value: " + parity);
                }
            }
            lineRequestData[6] = (byte)dataBits;
            this.ctrlOut(32, 0, 0, lineRequestData);
            this.resetDevice();
            this.mBaudRate = baudRate;
            this.mDataBits = dataBits;
            this.mStopBits = stopBits;
            this.mParity = parity;
        }
        
        @Override
        public boolean getCD() throws IOException {
            return this.testStatusFlag(1);
        }
        
        @Override
        public boolean getCTS() throws IOException {
            return this.testStatusFlag(128);
        }
        
        @Override
        public boolean getDSR() throws IOException {
            return this.testStatusFlag(2);
        }
        
        @Override
        public boolean getDTR() throws IOException {
            return (this.mControlLinesValue & 0x1) == 0x1;
        }
        
        @Override
        public void setDTR(final boolean value) throws IOException {
            int newControlLinesValue;
            if (value) {
                newControlLinesValue = (this.mControlLinesValue | 0x1);
            }
            else {
                newControlLinesValue = (this.mControlLinesValue & 0xFFFFFFFE);
            }
            this.setControlLines(newControlLinesValue);
        }
        
        @Override
        public boolean getRI() throws IOException {
            return this.testStatusFlag(8);
        }
        
        @Override
        public boolean getRTS() throws IOException {
            return (this.mControlLinesValue & 0x2) == 0x2;
        }
        
        @Override
        public void setRTS(final boolean value) throws IOException {
            int newControlLinesValue;
            if (value) {
                newControlLinesValue = (this.mControlLinesValue | 0x2);
            }
            else {
                newControlLinesValue = (this.mControlLinesValue & 0xFFFFFFFD);
            }
            this.setControlLines(newControlLinesValue);
        }
        
        @Override
        public boolean purgeHwBuffers(final boolean purgeReadBuffers, final boolean purgeWriteBuffers) throws IOException {
            if (purgeReadBuffers) {
                this.vendorOut(8, 0, null);
            }
            if (purgeWriteBuffers) {
                this.vendorOut(9, 0, null);
            }
            return purgeReadBuffers || purgeWriteBuffers;
        }
    }
}
