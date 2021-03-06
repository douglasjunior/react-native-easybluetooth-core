/*
 * MIT License
 *
 * Copyright (c) 2017 Douglas Nassif Roma Junior
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package io.github.douglasjunior.ReactNativeEasyBluetooth.core;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableNativeArray;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothConfiguration;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothService;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothStatus;
import com.github.douglasjunior.bluetoothclassiclibrary.BluetoothWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class CoreModule extends ReactContextBaseJavaModule implements BluetoothService.OnBluetoothEventCallback, BluetoothService.OnBluetoothScanCallback {

    private static final String TAG = "CoreModule";

    private BluetoothAdapter mBluetoothAdapter;
    private Class<? extends BluetoothService> mBluetoothServiceClass;

    private BluetoothService mService;
    private BluetoothWriter mWriter;

    public CoreModule(ReactApplicationContext reactContext, Class<? extends BluetoothService> bluetoothServiceClass) {
        super(reactContext);
        try {
            this.mBluetoothServiceClass = bluetoothServiceClass;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                BluetoothManager mBluetoothManager = (BluetoothManager) getReactApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = mBluetoothManager.getAdapter();
            } else {
                mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public Map<String, Object> getConstants() {
        Map<String, Object> constants = new HashMap<>();
        constants.put("EVENT_DATA_READ", EasyBluetoothConstants.EVENT_DATA_READ);
        constants.put("EVENT_DEVICE_FOUND", EasyBluetoothConstants.EVENT_DEVICE_FOUND);
        constants.put("EVENT_DEVICE_NAME", EasyBluetoothConstants.EVENT_DEVICE_NAME);
        constants.put("EVENT_STATUS_CHANGE", EasyBluetoothConstants.EVENT_STATUS_CHANGE);
        return constants;
    }

    /* ====================================
                 REACT METHODS
    ==================================== */

    public void init(ReadableMap config, Promise promise) {
        Log.d(TAG, "config: " + config);
        try {
            if (!validateBluetoothAdapter(promise)) return;

            BluetoothConfiguration bluetoothConfig = new BluetoothConfiguration();
            bluetoothConfig.context = getReactApplicationContext();
            bluetoothConfig.bluetoothServiceClass = mBluetoothServiceClass;
            bluetoothConfig.deviceName = config.getString("deviceName");
            bluetoothConfig.characterDelimiter = config.getString("characterDelimiter").charAt(0);
            bluetoothConfig.bufferSize = config.getInt("bufferSize");
            if (config.hasKey("uuid"))
                bluetoothConfig.uuid = UUID.fromString(config.getString("uuid"));
            if (config.hasKey("uuidService"))
                bluetoothConfig.uuidService = UUID.fromString(config.getString("uuidService"));
            if (config.hasKey("uuidCharacteristic"))
                bluetoothConfig.uuidCharacteristic = UUID.fromString(config.getString("uuidCharacteristic"));
            if (config.hasKey("transport"))
                bluetoothConfig.transport = config.getInt("transport");
            bluetoothConfig.callListenersInMainThread = false;

            BluetoothService.init(bluetoothConfig);
            mService = BluetoothService.getDefaultInstance();
            mService.setOnScanCallback(this);
            mService.setOnEventCallback(this);

            mWriter = new BluetoothWriter(mService);

            WritableNativeMap returnConfig = new WritableNativeMap();
            returnConfig.merge(config);

            promise.resolve(returnConfig);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void startScan(final Promise promise) {
        Log.d(TAG, "startScan");
        try {
            if (!validateServiceConfig(promise)) return;

            mStartScanPromise = promise;
            mService.startScan();
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void stopScan(Promise promise) throws InterruptedException {
        Log.d(TAG, "stopScan");
        try {
            if (!validateServiceConfig(promise)) return;

            mService.stopScan();

            Thread.sleep(1000);

            promise.resolve(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void connect(ReadableMap device, final Promise promise) throws InterruptedException {
        Log.d(TAG, "connect: " + device);

        try {
            if (!validateServiceConfig(promise)) return;

            String address = device.getString("address");
            String name = device.getString("name");

            if (!mBluetoothAdapter.checkBluetoothAddress(address)) {
                promise.reject(new IllegalArgumentException("Invalid device address: " + address));
                return;
            }

            BluetoothDevice btDevice = mBluetoothAdapter.getRemoteDevice(address);

            mService.connect(btDevice);

            Thread.sleep(2000);
            while (mService.getStatus() == BluetoothStatus.CONNECTING) {
                Thread.yield();
            }

            if (mService.getStatus() == BluetoothStatus.CONNECTED) {
                promise.resolve(wrapDevice(btDevice, 0));
            } else {
                promise.reject(new IllegalStateException("Unable to connect to: " + name + " [" + address + "]"));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void disconnect(Promise promise) {
        Log.d(TAG, "disconnect");

        try {
            if (!validateServiceConfig(promise)) return;

            mService.disconnect();

            promise.resolve(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void getStatus(final Promise promise) {
        try {
            if (!validateServiceConfig(promise)) return;

            Log.d(TAG, "getStatus: " + mService.getStatus().name());

            promise.resolve(mService.getStatus().name());
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void write(String data, Promise promise) {
        Log.d(TAG, "write: " + data);

        try {
            if (!validateServiceConfig(promise)) return;

            mWriter.write(data);
            promise.resolve(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void writeln(String data, Promise promise) {
        Log.d(TAG, "writeln: " + data);

        try {
            if (!validateServiceConfig(promise)) return;

            mWriter.writeln(data);
            promise.resolve(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void writeIntArray(ReadableArray data, Promise promise) {
        Log.d(TAG, "writeIntArray: " + data);

        try {
            if (!validateServiceConfig(promise)) return;

            byte[] bytes = new byte[data.size()];

            for (int i = 0; i < data.size(); i++) {
                bytes[i] = (byte) data.getInt(i);
            }

            mService.write(bytes);
            promise.resolve(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void stopService(final Promise promise) {
        Log.d(TAG, "stopService");

        try {
            if (!validateServiceConfig(promise)) return;

            mService.stopService();
            mService = null;
            mWriter = null;
            promise.resolve(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    /* ====================================
            ADAPTER - REACT METHODS
     ==================================== */

    public void isAdapterEnable(final Promise promise) {
        try {
            if (!validateBluetoothAdapter(promise)) return;

            Log.d(TAG, "isAdapterEnable: " + mBluetoothAdapter.isEnabled());

            promise.resolve(mBluetoothAdapter.isEnabled());
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void enable(final Promise promise) {
        Log.d(TAG, "enable");

        try {
            if (!validateBluetoothAdapter(promise)) return;

            if (mBluetoothAdapter.enable()) {
                promise.resolve(null);
            } else {
                promise.reject(new IllegalAccessException("Could not enable bluetooth adapter."));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void disable(final Promise promise) {
        Log.d(TAG, "disable");

        try {
            if (!validateBluetoothAdapter(promise)) return;

            if (mBluetoothAdapter.disable()) {
                promise.resolve(null);
            } else {
                promise.reject(new IllegalAccessException("Could not disable bluetooth adapter."));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    public void getBoundedDevices(final Promise promise) {
        try {
            if (!validateBluetoothAdapter(promise)) return;

            WritableNativeArray devices = new WritableNativeArray();

            for (BluetoothDevice btDevice : mBluetoothAdapter.getBondedDevices()) {
                WritableNativeMap device = wrapDevice(btDevice, 0);
                devices.pushMap(device);
            }

            Log.d(TAG, "getBoundedDevices: " + devices);

            promise.resolve(devices);
        } catch (Exception ex) {
            ex.printStackTrace();
            promise.reject(ex);
        }
    }

    /* ====================================
                    EVENTS
     ==================================== */

    private void sendEvent(String eventName, Object params) {
        getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendEventDeviceFound(WritableNativeMap device) {
        sendEvent(EasyBluetoothConstants.EVENT_DEVICE_FOUND, device);
    }

    private void sendEventDataRead(String data) {
        sendEvent(EasyBluetoothConstants.EVENT_DATA_READ, data);
    }

    private void sendEventStatusChange(BluetoothStatus status) {
        sendEvent(EasyBluetoothConstants.EVENT_STATUS_CHANGE, status.name());
    }

    private void sendEventDeviceName(String name) {
        sendEvent(EasyBluetoothConstants.EVENT_DEVICE_NAME, name);
    }

    /* ====================================
                    OTHERS
     ==================================== */

    private boolean validateBluetoothAdapter(Promise promise) {
        if (mBluetoothAdapter == null) {
            promise.reject(new IllegalStateException("Bluetooth Adapter not found."));
            return false;
        }
        return true;
    }

    private boolean validateServiceConfig(Promise promise) {
        if (mService == null) {
            promise.reject(new IllegalStateException("BluetoothService has not been configured. " +
                    "Call ReactNativeEasyBluetooth.init(config)."));
            return false;
        }
        return true;
    }

    private WritableNativeMap wrapDevice(BluetoothDevice bluetoothDevice, int RSSI) {
        WritableNativeMap device = new WritableNativeMap();
        device.putString("address", bluetoothDevice.getAddress());
        device.putString("name", bluetoothDevice.getName());
        device.putInt("rssi", RSSI);
        WritableArray uuids = new WritableNativeArray();
        if (bluetoothDevice.getUuids() != null) {
            for (ParcelUuid uuid : bluetoothDevice.getUuids()) {
                uuids.pushString(uuid.toString());
            }
        }
        device.putArray("uuids", uuids);
        return device;
    }

    @Override
    public void onDataRead(byte[] bytes, int length) {
        sendEventDataRead(new String(bytes, 0, length));
    }

    @Override
    public void onStatusChange(BluetoothStatus bluetoothStatus) {
        sendEventStatusChange(bluetoothStatus);
    }

    @Override
    public void onDeviceName(String name) {
        sendEventDeviceName(name);
    }

    @Override
    public void onToast(String s) {

    }

    @Override
    public void onDataWrite(byte[] bytes) {

    }

    private WritableNativeArray mDevicesFound;
    private Promise mStartScanPromise;

    @Override
    public void onDeviceDiscovered(BluetoothDevice bluetoothDevice, int RSSI) {
        if (mDevicesFound != null) {
            mDevicesFound.pushMap(wrapDevice(bluetoothDevice, RSSI));
        }
        sendEventDeviceFound(wrapDevice(bluetoothDevice, RSSI));
    }

    @Override
    public void onStartScan() {
        mDevicesFound = new WritableNativeArray();
    }

    @Override
    public void onStopScan() {
        if (mStartScanPromise != null) {
            mStartScanPromise.resolve(mDevicesFound);
            mStartScanPromise = null;
        }
        mDevicesFound = null;
    }
}