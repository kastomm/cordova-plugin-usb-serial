// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.io.IOException;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbDevice;

abstract class CommonUsbSerialPort implements UsbSerialPort
{
    public static final int DEFAULT_READ_BUFFER_SIZE = 16384;
    public static final int DEFAULT_WRITE_BUFFER_SIZE = 16384;
    protected final UsbDevice mDevice;
    protected final int mPortNumber;
    protected UsbDeviceConnection mConnection;
    protected final Object mReadBufferLock;
    protected final Object mWriteBufferLock;
    protected byte[] mReadBuffer;
    protected byte[] mWriteBuffer;
    
    public CommonUsbSerialPort(final UsbDevice device, final int portNumber) {
        this.mConnection = null;
        this.mReadBufferLock = new Object();
        this.mWriteBufferLock = new Object();
        this.mDevice = device;
        this.mPortNumber = portNumber;
        this.mReadBuffer = new byte[16384];
        this.mWriteBuffer = new byte[16384];
    }
    
    @Override
    public String toString() {
        return String.format("<%s device_name=%s device_id=%s port_number=%s>", this.getClass().getSimpleName(), this.mDevice.getDeviceName(), this.mDevice.getDeviceId(), this.mPortNumber);
    }
    
    public final UsbDevice getDevice() {
        return this.mDevice;
    }
    
    @Override
    public int getPortNumber() {
        return this.mPortNumber;
    }
    
    @Override
    public String getSerial() {
        return this.mConnection.getSerial();
    }
    
    public final void setReadBufferSize(final int bufferSize) {
        synchronized (this.mReadBufferLock) {
            if (bufferSize == this.mReadBuffer.length) {
                return;
            }
            this.mReadBuffer = new byte[bufferSize];
        }
    }
    
    public final void setWriteBufferSize(final int bufferSize) {
        synchronized (this.mWriteBufferLock) {
            if (bufferSize == this.mWriteBuffer.length) {
                return;
            }
            this.mWriteBuffer = new byte[bufferSize];
        }
    }
    
    @Override
    public abstract void open(final UsbDeviceConnection p0) throws IOException;
    
    @Override
    public abstract void close() throws IOException;
    
    @Override
    public abstract int read(final byte[] p0, final int p1) throws IOException;
    
    @Override
    public abstract int write(final byte[] p0, final int p1) throws IOException;
    
    @Override
    public abstract void setParameters(final int p0, final int p1, final int p2, final int p3) throws IOException;
    
    @Override
    public abstract boolean getCD() throws IOException;
    
    @Override
    public abstract boolean getCTS() throws IOException;
    
    @Override
    public abstract boolean getDSR() throws IOException;
    
    @Override
    public abstract boolean getDTR() throws IOException;
    
    @Override
    public abstract void setDTR(final boolean p0) throws IOException;
    
    @Override
    public abstract boolean getRI() throws IOException;
    
    @Override
    public abstract boolean getRTS() throws IOException;
    
    @Override
    public abstract void setRTS(final boolean p0) throws IOException;
    
    @Override
    public boolean purgeHwBuffers(final boolean flushReadBuffers, final boolean flushWriteBuffers) throws IOException {
        return !flushReadBuffers && !flushWriteBuffers;
    }
}
