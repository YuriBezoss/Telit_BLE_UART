package com.esignal.signaldemo;

/**
 * Created by ChiaHao on 2016/1/18.
 */

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.List;
import java.util.UUID;


/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */

/**
 * E-SIGNAL Ben Edit at 105/01/14
 * Fix  Multi Bluetooth LE Scanner at API 23
 * Android Support Max 7 Device Connection
 */

@TargetApi(21)
// using @TargeApi instead of @SuppressLint("NewApi")
@SuppressWarnings("deprecation")

public class BluetoothLeService extends Service
{
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mBluetoothScanner;

    private ScanSettings scanSettings;
    List<ScanFilter> filters;

    public BluetoothGatt mBluetoothGatt;
    public String mBluetoothGattAddress;
    public boolean mBluetoothGattServiceDiscover;
    public boolean mBluetoothGattConnected;

    private int mConnectionState = STATE_DISCONNECTED;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    private static final int STATE_ConnectSus = 3;

    public final static String ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA = "com.example.bluetooth.le.EXTRA_DATA";
    public final static String ACTION_GATT_DEVICE_DISCOVERED = "com.example.bluetooth.le.ACTION_GATT_DEVICE_DISCOVERED";
    public final static String ACTION_mBluetoothDeviceName = "com.example.bluetooth.le.mBluetoothDevice";
    public final static String ACTION_mBluetoothDeviceAddress = "com.example.bluetooth.le.mBluetoothDeviceAddress";
    //public final static String ACTION_mBluetoothDeviceAdv = "com.example.bluetooth.le.mBluetoothDeviceAdv";
    public final static String ACTION_Enable = "com.example.bluetooth.le.ACTION_Enable";
    public final static String ACTION_Connect_Fail = "com.example.bluetooth.le.ACTION_Connect_Fail";
    public final static String COUNTDOWN_BR = "com.example.bluetooth.le.countdown_br";

    //public final static UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);
    public final static UUID UUID_NOTIFY_CHARACTERISTIC =  UUID.fromString(SampleGattAttributes.NOTIFY_CHARACTERISTIC);
    public final static UUID UUID_WRITE_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.WTIYE_CHARACTERISTIC);

    private int BLE_CONNECT_TIMEOUT=6000;                                   //CONNECT TIME OUT SETTING
    private Handler handler = new Handler();
    Intent  bi = new Intent(COUNTDOWN_BR);
    CountDownTimer  cdt = null;


    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    /**********************************************************
     * 發現週邊藍芽裝置
     **********************************************************/
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback()
    {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
        {
            DidDiscoverDevice(ACTION_GATT_DEVICE_DISCOVERED, device, rssi, scanRecord);
        }
    };

    private ScanCallback mScanCallback = new ScanCallback()
    {
        @Override
        public void onScanResult(int callbackType, ScanResult result)
        {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            BluetoothDevice Device = result.getDevice();

            if (Device != null)
            {
                String DeviceName = Device.getName();
                if (DeviceName == null)
                {
                    DeviceName = "Unknow Device";
                }

                Log.i(TAG, "DiscoverDevice:" + Device.getAddress());

                final Intent intent = new Intent(ACTION_GATT_DEVICE_DISCOVERED);
                intent.putExtra(ACTION_mBluetoothDeviceName, Device.getName());
                intent.putExtra(ACTION_mBluetoothDeviceAddress, Device.getAddress());

                sendBroadcast(intent);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results)
        {
            for (ScanResult sr : results)
            {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode)
        {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback()
    {
        // BLE Connection Change
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            String  intentAction;
            String  address = gatt.getDevice().getAddress();

            if (newState == BluetoothProfile.STATE_CONNECTED)
            {
                mConnectionState = STATE_CONNECTED;
                Log.i(TAG, "Connect Sus " + address);
                gatt.discoverServices();
                mBluetoothGattConnected=true;

                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED)
            {   //Device Disconnected
                intentAction = ACTION_GATT_DISCONNECTED;
                mBluetoothGattConnected=false;
                gatt.close();

                if (mConnectionState==STATE_CONNECTING)
                {
                    mConnectionState = STATE_DISCONNECTED;
                    Log.d(TAG, "Disconnect Fail");
                    handler.removeCallbacks(TimeOUTCheckTimer);

                    intentAction = ACTION_Connect_Fail;
                    broadcastUpdate(intentAction);
                }
                else
                {
                    Log.i(TAG, "Disconnected from GATT server.");
                    broadcastUpdate(intentAction);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                handler.removeCallbacks(TimeOUTCheckTimer);
                Log.i(TAG, "Discover Service");
                mBluetoothGattServiceDiscover = true;

                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                NotifyEnable(gatt);
            }
            else
            {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status)
        {
            //   Log.i(TAG, "Write OVer");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic)
        {
            final byte[] data = characteristic.getValue();
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status)
        {
            Log.i(TAG, "characteristic uuid = " +
                    descriptor.getCharacteristic().getUuid().toString() + descriptor.getValue());

            final Intent intent = new Intent(ACTION_Enable);
            intent.putExtra(ACTION_Enable, descriptor.getCharacteristic().getUuid().toString());
            sendBroadcast(intent);
        }
    };

    //private void broadcastUpdate(final String action)
    public void broadcastUpdate(final String action)
    {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void DidDiscoverDevice(final String action,
                                   final BluetoothDevice Device,
                                   int rssi, byte[] scanRecord)
    {
        if (Device != null)
        {
            Log.i(TAG, "DiscoverDevice:" + Device.getAddress());

            final StringBuilder stringBuilder = new StringBuilder(scanRecord.length);
            for (byte byteChar : scanRecord)
                stringBuilder.append(String.format("%02X", byteChar));

            final Intent intent = new Intent(action);
            intent.putExtra(ACTION_mBluetoothDeviceName, Device.getName());
            intent.putExtra(ACTION_mBluetoothDeviceAddress, Device.getAddress());

            sendBroadcast(intent);
        }
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic)
    {
        final Intent intent = new Intent(action);

        // This is special handling for the Heart Rate Measurement profile.  Data parsing is
        // carried out as per profile specifications:
        // http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
        // For all other profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();

        if (data != null && data.length > 0)
        {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X", byteChar));
            intent.putExtra(EXTRA_DATA, stringBuilder.toString());
        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder
    {
        BluetoothLeService getService()
        {
            return BluetoothLeService.this;
        }
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        Log.d(TAG, "Service countdown timer is setting.");
        cdt = new CountDownTimer(BLE_CONNECT_TIMEOUT, 1000)
        {
            @Override
            public void onTick(long millisUntilFinished)
            {
                long tmp = millisUntilFinished/1000;

                Log.i(TAG, "Countdown second remaining: " + millisUntilFinished);
                bi.putExtra("countdown", tmp);
                sendBroadcast(bi);
            }

            @Override
            public void onFinish()
            {
                bi.putExtra("countdown", 0L);
                sendBroadcast(bi);
                Log.i(TAG, "Timer finished");
            }
        };
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize()
    {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null)
            {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            } else {
                Log.e(TAG, "Start to initialize BluetoothManager.");
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */

    /**********************************************************
     * 對裝置下達連線
     **********************************************************/
    public boolean connect(final String address)
    {
        if (mBluetoothAdapter == null || address == null || mBluetoothGattConnected)
        {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);

        mConnectionState = STATE_CONNECTING;
        mBluetoothGattAddress = address;
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.w(TAG, "Trying to Connect with " + mBluetoothGattAddress);

        //Connect Time Out Check  6 second
        handler.postDelayed(TimeOUTCheckTimer, BLE_CONNECT_TIMEOUT);
        return true;
    }

    public void ScanDevice(final boolean enable)
    {
        if(enable)
        {
            if (Build.VERSION.SDK_INT < 23)
            {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
            else
            {
                if(mBluetoothScanner==null)
                {
                    mBluetoothScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
                    ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
                    scanSettingsBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                    scanSettings = scanSettingsBuilder.build();

                 /*   ScanFilter filter = new ScanFilter.Builder().setDeviceName(DeviceNameFilter).build();
                    filters = new ArrayList<ScanFilter>();
                    filters.add(filter);*/
                }
                mBluetoothScanner.startScan(filters, scanSettings, mScanCallback);
            }
        }
        else
        {
            if (Build.VERSION.SDK_INT < 23)
            {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
            else
            {
                mBluetoothScanner.stopScan(mScanCallback);
            }
        }
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */

    public void disconnect()
    {
        if (mBluetoothAdapter == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        ScanDevice(false);
        mBluetoothAdapter.cancelDiscovery();
        mConnectionState = STATE_DISCONNECTED;
        if(mBluetoothGatt != null) mBluetoothGatt.disconnect();
        cdt.cancel();
    }

    public void close()
    {
        if(mBluetoothGatt != null) mBluetoothGatt.close();

        mBluetoothGatt = null;
        mBluetoothGattAddress=null;
        mBluetoothGattConnected=false;
        cdt.cancel();

        if(handler!=null)   handler.removeCallbacks(TimeOUTCheckTimer);
    }

    public void readCharacteristic(BluetoothGattCharacteristic characteristic,BluetoothGatt Gatt)
    {
        if (mBluetoothAdapter == null || Gatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Gatt.readCharacteristic(characteristic);
    }

    public void NotifyEnable(BluetoothGatt gatt)
    {
        if (mBluetoothAdapter == null || gatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        for (BluetoothGattService GattService : gatt.getServices())
        {
            List<BluetoothGattCharacteristic> mGattCharacteristics = GattService.getCharacteristics();
            for (BluetoothGattCharacteristic mCharacteristic : mGattCharacteristics)
            {
                if (UUID_NOTIFY_CHARACTERISTIC.equals(mCharacteristic.getUuid()))
                {
                    setCharacteristicNotification(gatt,mCharacteristic, true);
                }
            }
        }
    }

    public void setCharacteristicNotification(BluetoothGatt gatt,
                                              BluetoothGattCharacteristic characteristic,
                                              boolean enabled)
    {
        if (mBluetoothAdapter == null || gatt == null)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        gatt.setCharacteristicNotification(characteristic, enabled);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);
    }

    //public void writeCharacteristicC(BluetoothGattCharacteristic characteristic)
    public void writeCharacteristicCMD(byte[] value)
    {
        if (mBluetoothAdapter == null || mBluetoothGatt == null || !mBluetoothGattConnected)
        {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        /*
        try
        {
            Thread.sleep(200);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
        */

        for (BluetoothGattService GattService : mBluetoothGatt.getServices())
        {
            List<BluetoothGattCharacteristic> mGattCharacteristics = GattService.getCharacteristics();
            for (BluetoothGattCharacteristic mCharacteristic : mGattCharacteristics)
            {
                if (UUID_WRITE_CHARACTERISTIC.equals(mCharacteristic.getUuid()))
                {
                    mCharacteristic.setValue(value);
                    mBluetoothGatt.writeCharacteristic(mCharacteristic);
                }
            }
        }
    }

    /*
    public static byte[] hexStringToByteArray(String s)
    {
        int len = s.length();
        byte[] data = new byte[len/2];

        for(int i = 0; i < len; i+=2)
        {
            data[i/2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i+1), 16));
        }

        return data;
    }
    */

    private Runnable TimeOUTCheckTimer = new Runnable()
    {
        public void run()
        {
            mConnectionState = STATE_DISCONNECTED;
            mBluetoothAdapter.cancelDiscovery();
            if(mBluetoothGatt!=null)
            {
                mBluetoothGatt.disconnect();
                mBluetoothGatt = null;
                mConnectionState = STATE_DISCONNECTED;
            }

            String intentAction;
            intentAction = ACTION_Connect_Fail;
            broadcastUpdate(intentAction);

            handler.postDelayed(this, BLE_CONNECT_TIMEOUT);
            handler.removeCallbacks(TimeOUTCheckTimer);
        }
    };
}
