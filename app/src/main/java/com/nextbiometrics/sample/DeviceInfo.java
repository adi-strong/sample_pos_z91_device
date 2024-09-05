package com.nextbiometrics.sample;

import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDevices;

import java.io.Serializable;

public class DeviceInfo implements Serializable {
    private String id;

    private boolean isSpi;
    private String spiName;
    private int awakePin;
    private int resetPin;
    private int chipSelectPin;

    public DeviceInfo(String id) {
        this(id, false, null, -1, -1, -1);
    }

    public DeviceInfo(String spiName, int awakePin, int resetPin, int chipSelectPin) {
        this(null, true, spiName, awakePin, resetPin, chipSelectPin);
    }

    public DeviceInfo(String id, boolean isSpi, String spiName, int awakePin, int resetPin, int chipSelectPin) {
        this.id = id;
        this.isSpi = isSpi;
        this.spiName = spiName;
        this.awakePin = awakePin;
        this.resetPin = resetPin;
        this.chipSelectPin = chipSelectPin;
    }

    public String getId() {
        return id;
    }

    public boolean isSpi() {
        return isSpi;
    }

    public String getSpiName() {
        return spiName;
    }

    public int getAwakePin() {
        return awakePin;
    }

    public int getResetPin() {
        return resetPin;
    }

    public int getChipSelectPin() {
        return chipSelectPin;
    }

    public static NBDevice getDevice(DeviceInfo deviceInfo) {
        if (deviceInfo == null || !deviceInfo.isSpi()) {
            NBDevice[] devices = NBDevices.getDevices();
            for (NBDevice device : devices) {
                if (deviceInfo != null && deviceInfo.getId() != null && deviceInfo.getId().equals(device.getId())) {
                    return device;
                }
            }
            if (devices.length > 0)
                return devices[0];
            return null;
        } else {
            NBDevice device = NBDevice.connectToSpi(deviceInfo.getSpiName(), deviceInfo.getAwakePin(), deviceInfo.getResetPin(), deviceInfo.getChipSelectPin(), NBDevice.DEVICE_CONNECT_TO_SPI_SKIP_GPIO_INIT_FLAG);
            // NBDevice device = NBDevice.connectToBluetooth(0);
            // byte[] defaultAuthId1 = "AUTH1\0".getBytes();
            // byte[] defaultAuthKey1 = {
            //     (byte)0xDA, (byte)0x2E, (byte)0x35, (byte)0xB6, (byte)0xCB, (byte)0x96, (byte)0x2B, (byte)0x5F, (byte)0x9F, (byte)0x34, (byte)0x1F, (byte)0xD1, (byte)0x47, (byte)0x41, (byte)0xA0, (byte)0x4D,
            //     (byte)0xA4, (byte)0x09, (byte)0xCE, (byte)0xE8, (byte)0x35, (byte)0x48, (byte)0x3C, (byte)0x60, (byte)0xFB, (byte)0x13, (byte)0x91, (byte)0xE0, (byte)0x9E, (byte)0x95, (byte)0xB2, (byte)0x7F
            // };
            // device.openSession(defaultAuthId1, defaultAuthKey1);
            deviceInfo.id = device.getId();
            return device;
        }
    }
}
