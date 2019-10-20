// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.util.Iterator;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import android.util.Pair;
import java.util.Map;

public class ProbeTable
{
    private final Map<Pair<Integer, Integer>, Class<? extends UsbSerialDriver>> mProbeTable;
    
    public ProbeTable() {
        this.mProbeTable = new LinkedHashMap<Pair<Integer, Integer>, Class<? extends UsbSerialDriver>>();
    }
    
    public ProbeTable addProduct(final int vendorId, final int productId, final Class<? extends UsbSerialDriver> driverClass) {
        this.mProbeTable.put((Pair<Integer, Integer>)Pair.create((Object)vendorId, (Object)productId), driverClass);
        return this;
    }
    
    ProbeTable addDriver(final Class<? extends UsbSerialDriver> driverClass) {
        Method method;
        try {
            method = driverClass.getMethod("getSupportedDevices", (Class<?>[])new Class[0]);
        }
        catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        catch (NoSuchMethodException e2) {
            throw new RuntimeException(e2);
        }
        Map<Integer, int[]> devices;
        try {
            devices = (Map<Integer, int[]>)method.invoke(null, new Object[0]);
        }
        catch (IllegalArgumentException e3) {
            throw new RuntimeException(e3);
        }
        catch (IllegalAccessException e4) {
            throw new RuntimeException(e4);
        }
        catch (InvocationTargetException e5) {
            throw new RuntimeException(e5);
        }
        for (final Map.Entry<Integer, int[]> entry : devices.entrySet()) {
            final int vendorId = entry.getKey();
            for (final int productId : entry.getValue()) {
                this.addProduct(vendorId, productId, driverClass);
            }
        }
        return this;
    }
    
    public Class<? extends UsbSerialDriver> findDriver(final int vendorId, final int productId) {
        final Pair<Integer, Integer> pair = (Pair<Integer, Integer>)Pair.create((Object)vendorId, (Object)productId);
        return this.mProbeTable.get(pair);
    }
}
