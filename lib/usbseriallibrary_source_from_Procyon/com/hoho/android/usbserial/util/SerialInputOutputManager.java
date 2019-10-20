// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.util;

import java.io.IOException;
import android.util.Log;
import java.nio.ByteBuffer;
import com.hoho.android.usbserial.driver.UsbSerialPort;

public class SerialInputOutputManager implements Runnable
{
    private static final String TAG;
    private static final boolean DEBUG = true;
    private static final int READ_WAIT_MILLIS = 200;
    private static final int BUFSIZ = 4096;
    private final UsbSerialPort mDriver;
    private final ByteBuffer mReadBuffer;
    private final ByteBuffer mWriteBuffer;
    private State mState;
    private Listener mListener;
    
    public SerialInputOutputManager(final UsbSerialPort driver) {
        this(driver, null);
    }
    
    public SerialInputOutputManager(final UsbSerialPort driver, final Listener listener) {
        this.mReadBuffer = ByteBuffer.allocate(4096);
        this.mWriteBuffer = ByteBuffer.allocate(4096);
        this.mState = State.STOPPED;
        this.mDriver = driver;
        this.mListener = listener;
    }
    
    public synchronized void setListener(final Listener listener) {
        this.mListener = listener;
    }
    
    public synchronized Listener getListener() {
        return this.mListener;
    }
    
    public void writeAsync(final byte[] data) {
        synchronized (this.mWriteBuffer) {
            this.mWriteBuffer.put(data);
        }
    }
    
    public synchronized void stop() {
        if (this.getState() == State.RUNNING) {
            Log.i(SerialInputOutputManager.TAG, "Stop requested");
            this.mState = State.STOPPING;
        }
    }
    
    private synchronized State getState() {
        return this.mState;
    }
    
    @Override
    public void run() {
        synchronized (this) {
            if (this.getState() != State.STOPPED) {
                throw new IllegalStateException("Already running.");
            }
            this.mState = State.RUNNING;
        }
        Log.i(SerialInputOutputManager.TAG, "Running ..");
        try {
            while (this.getState() == State.RUNNING) {
                this.step();
            }
            Log.i(SerialInputOutputManager.TAG, "Stopping mState=" + this.getState());
        }
        catch (Exception e) {
            Log.w(SerialInputOutputManager.TAG, "Run ending due to exception: " + e.getMessage(), (Throwable)e);
            final Listener listener = this.getListener();
            if (listener != null) {
                listener.onRunError(e);
            }
            synchronized (this) {
                this.mState = State.STOPPED;
                Log.i(SerialInputOutputManager.TAG, "Stopped.");
            }
        }
        finally {
            synchronized (this) {
                this.mState = State.STOPPED;
                Log.i(SerialInputOutputManager.TAG, "Stopped.");
            }
        }
    }
    
    private void step() throws IOException {
        int len = this.mDriver.read(this.mReadBuffer.array(), 200);
        if (len > 0) {
            Log.d(SerialInputOutputManager.TAG, "Read data len=" + len);
            final Listener listener = this.getListener();
            if (listener != null) {
                final byte[] data = new byte[len];
                this.mReadBuffer.get(data, 0, len);
                listener.onNewData(data);
            }
            this.mReadBuffer.clear();
        }
        byte[] outBuff = null;
        synchronized (this.mWriteBuffer) {
            len = this.mWriteBuffer.position();
            if (len > 0) {
                outBuff = new byte[len];
                this.mWriteBuffer.rewind();
                this.mWriteBuffer.get(outBuff, 0, len);
                this.mWriteBuffer.clear();
            }
        }
        if (outBuff != null) {
            Log.d(SerialInputOutputManager.TAG, "Writing data len=" + len);
            this.mDriver.write(outBuff, 200);
        }
    }
    
    static {
        TAG = SerialInputOutputManager.class.getSimpleName();
    }
    
    private enum State
    {
        STOPPED, 
        RUNNING, 
        STOPPING;
    }
    
    public interface Listener
    {
        void onNewData(final byte[] p0);
        
        void onRunError(final Exception p0);
    }
}
