// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

public class UsbSerialRuntimeException extends RuntimeException
{
    public UsbSerialRuntimeException() {
    }
    
    public UsbSerialRuntimeException(final String detailMessage, final Throwable throwable) {
        super(detailMessage, throwable);
    }
    
    public UsbSerialRuntimeException(final String detailMessage) {
        super(detailMessage);
    }
    
    public UsbSerialRuntimeException(final Throwable throwable) {
        super(throwable);
    }
}
