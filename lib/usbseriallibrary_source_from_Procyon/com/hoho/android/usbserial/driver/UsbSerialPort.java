// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.io.IOException;
import android.hardware.usb.UsbDeviceConnection;

public interface UsbSerialPort
{
    public static final int DATABITS_5 = 5;
    public static final int DATABITS_6 = 6;
    public static final int DATABITS_7 = 7;
    public static final int DATABITS_8 = 8;
    public static final int FLOWCONTROL_NONE = 0;
    public static final int FLOWCONTROL_RTSCTS_IN = 1;
    public static final int FLOWCONTROL_RTSCTS_OUT = 2;
    public static final int FLOWCONTROL_XONXOFF_IN = 4;
    public static final int FLOWCONTROL_XONXOFF_OUT = 8;
    public static final int PARITY_NONE = 0;
    public static final int PARITY_ODD = 1;
    public static final int PARITY_EVEN = 2;
    public static final int PARITY_MARK = 3;
    public static final int PARITY_SPACE = 4;
    public static final int STOPBITS_1 = 1;
    public static final int STOPBITS_1_5 = 3;
    public static final int STOPBITS_2 = 2;
    
    UsbSerialDriver getDriver();
    
    int getPortNumber();
    
    String getSerial();
    
    void open(final UsbDeviceConnection p0) throws IOException;
    
    void close() throws IOException;
    
    int read(final byte[] p0, final int p1) throws IOException;
    
    int write(final byte[] p0, final int p1) throws IOException;
    
    void setParameters(final int p0, final int p1, final int p2, final int p3) throws IOException;
    
    boolean getCD() throws IOException;
    
    boolean getCTS() throws IOException;
    
    boolean getDSR() throws IOException;
    
    boolean getDTR() throws IOException;
    
    void setDTR(final boolean p0) throws IOException;
    
    boolean getRI() throws IOException;
    
    boolean getRTS() throws IOException;
    
    void setRTS(final boolean p0) throws IOException;
    
    boolean purgeHwBuffers(final boolean p0, final boolean p1) throws IOException;
}
