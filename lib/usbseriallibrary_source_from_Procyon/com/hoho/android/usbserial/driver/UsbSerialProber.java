// 
// Decompiled by Procyon v0.5.36
// 

package com.hoho.android.usbserial.driver;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import android.hardware.usb.UsbDevice;
import java.util.ArrayList;
import java.util.List;
import android.hardware.usb.UsbManager;

public class UsbSerialProber
{
    private final ProbeTable mProbeTable;
    
    public UsbSerialProber(final ProbeTable probeTable) {
        this.mProbeTable = probeTable;
    }
    
    public static UsbSerialProber getDefaultProber() {
        return new UsbSerialProber(getDefaultProbeTable());
    }
    
    public static ProbeTable getDefaultProbeTable() {
        final ProbeTable probeTable = new ProbeTable();
        probeTable.addDriver(CdcAcmSerialDriver.class);
        probeTable.addDriver(Cp21xxSerialDriver.class);
        probeTable.addDriver(FtdiSerialDriver.class);
        probeTable.addDriver(ProlificSerialDriver.class);
        probeTable.addDriver(Ch34xSerialDriver.class);
        return probeTable;
    }
    
    public List<UsbSerialDriver> findAllDrivers(final UsbManager usbManager) {
        final List<UsbSerialDriver> result = new ArrayList<UsbSerialDriver>();
        for (final UsbDevice usbDevice : usbManager.getDeviceList().values()) {
            final UsbSerialDriver driver = this.probeDevice(usbDevice);
            if (driver != null) {
                result.add(driver);
            }
        }
        return result;
    }
    
    public UsbSerialDriver probeDevice(final UsbDevice usbDevice) {
        final int vendorId = usbDevice.getVendorId();
        final int productId = usbDevice.getProductId();
        final Class<? extends UsbSerialDriver> driverClass = this.mProbeTable.findDriver(vendorId, productId);
        if (driverClass != null) {
            UsbSerialDriver driver;
            try {
                final Constructor<? extends UsbSerialDriver> ctor = driverClass.getConstructor(UsbDevice.class);
                driver = (UsbSerialDriver)ctor.newInstance(usbDevice);
            }
            catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
            catch (IllegalArgumentException e2) {
                throw new RuntimeException(e2);
            }
            catch (InstantiationException e3) {
                throw new RuntimeException(e3);
            }
            catch (IllegalAccessException e4) {
                throw new RuntimeException(e4);
            }
            catch (InvocationTargetException e5) {
                throw new RuntimeException(e5);
            }
            return driver;
        }
        return null;
    }
}
