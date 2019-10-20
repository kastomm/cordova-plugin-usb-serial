interface Window {
  serial: {
    requestPermission(opts?: requestPermissionOptions, successCallback: () => void, errorCallback: (err) => void);
    open(opts: UsbSerial.openOptions, successCallback, errorCallback: (err) => void);
    write(data: string, successCallback: () => void, errorCallback: (err) => void);
    writeHex(hexString: string, successCallback: () => void, errorCallback: (err) => void);
    read(successCallback: (data: ArrayBuffer) => void, errorCallback: (err) => void);
    close(successCallback: () => void, errorCallback: (err) => void);
    registerReadCallback(successCallback: () => void, errorCallback: (err) => void);
    usbDetached(successCallback: () => void, errorCallback: (err) => void);
    usbAttached(successCallback: () => void, errorCallback: (err) => void);
  }
}

declare namespace UsbSerial {
  interface requestPermissionOptions {
    vid: string;
    pid: string;
    driver?: string;
  }
  interface openOptions {
    baudRate?: number;
    dataBits?: number;
    stopBits?: number;
    parity?: number;
    dtr?: boolean;
    rts?: boolean;
    sleepOnPause?: boolean;
  }
}