// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.util.List;
import android.hardware.usb.UsbDevice;

public interface UsbSerialDriver
{
    UsbDevice getDevice();
    
    List<UsbSerialPort> getPorts();
}
