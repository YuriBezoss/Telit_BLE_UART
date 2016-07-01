package com.esignal.signaldemo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.String.format;

public class MainActivity extends AppCompatActivity
{
    private BluetoothAdapter    mBluetoothAdapter;
    private BluetoothLeService  mBluetoothLeService;
    private LeDeviceListAdapter mLeDeviceListAdapter;

    Context         mContext;
    PopupWindow     mPopupWindow;
    ProgressDialog  progressDialog;
    TextView    mDataText;
    TextView    mConnect;
    ScrollView  mScroller;

    private Handler handler = new Handler();
    boolean mScanning;
    boolean mInitialFinished = false;
    private boolean OpenDialog = false;
    boolean SearchBLE = false;
    boolean BLUETOOTH_ENABLE = false;
    boolean BLUETOOTH_RECONNECT = false;
    long    counterLoops=0;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 1;

    private final ServiceConnection mServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service)
        {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize())
            {
                BLUETOOTH_ENABLE = false;
                finish();
            }
            else
            {
                BLUETOOTH_ENABLE = true;
            }
            // Automatically connects to the device upon successful start-up initialization.
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mBluetoothLeService = null;
            mInitialFinished = false;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            //BLE Status Changed
            final String action = intent.getAction();

            Log.d("mGattUpdateReceiver()", "action:" + action);     //debug
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))                //裝置連線成功
            {
                invalidateOptionsMenu();
                mConnect.setText("Connected");
                InsertMessage(mBluetoothLeService.mBluetoothGattAddress + " Connected");

                if(OpenDialog)
                {
                    OpenDialog=false;
                    progressDialog.dismiss();
                }
            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action))        //裝置斷線
            {
                //if (A0ReciveList!=null)
                //{
                //    Utils.writeLogFile(A0ReciveList);
                //    parserData(A0ReciveList);
                //    A0ReciveList = null;
                //    mBluetoothLeService.disconnect();
                //}
                invalidateOptionsMenu();
                mConnect.setText("Disconnected");
                InsertMessage(mBluetoothLeService.mBluetoothGattAddress + " Disonnected");

                if(OpenDialog)
                {
                    OpenDialog=false;
                    progressDialog.dismiss();
                }
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
                // Show all the supported services and characteristics on the user interface.
                invalidateOptionsMenu();

            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))
            {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));

                //displayData(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));
            }
            else if (BluetoothLeService.ACTION_GATT_DEVICE_DISCOVERED.equals(action))
            {
                DiscoverGattDevice( intent.getStringExtra(BluetoothLeService.ACTION_mBluetoothDeviceName),
                                    intent.getStringExtra(BluetoothLeService.ACTION_mBluetoothDeviceAddress));
            }
            //else if (BluetoothLeService.COUNTDOWN_BR.equals(action))
            //{
            //    if (checkTimeOut(intent))
            //    {
            //        //mBluetoothLeService.broadcastUpdate(mBluetoothLeService.ACTION_GATT_DISCONNECTED);
            //        //mBluetoothLeService.disconnect();
            //    }
            //}
            else if (BluetoothLeService.ACTION_Enable.equals(action))
            {
                progressDialog.setMessage("Connected");
                //Notify Enabled & Send Command to BLE Device
                //CommandTest();
            }
            else if (BluetoothLeService.ACTION_Connect_Fail.equals(action))
            {}
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this.getApplicationContext();

        mDataText = (TextView) findViewById(R.id.DataText);
        mScroller = (ScrollView) findViewById(R.id.Scroller);
        mConnect = (TextView) findViewById(R.id.textView);

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                scanLeDevice(true);
                showPopupWindowEvent();
            }
        });

        appVersion();

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Please turn Bluetooth power", Toast.LENGTH_SHORT).show();
            finish();
        }

        //receiveTmp=null;
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        startService(gattServiceIntent);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null)
        {
            finish();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
            {
            }
            else
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        //int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        //if (id == R.id.action_settings)
        if (item.getItemId() == R.id.action_settings)
        {
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults)
    {
        switch (requestCode)
        {
            case PERMISSION_REQUEST_COARSE_LOCATION:
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //  Log.d(TAG, "coarse location permission granted");
                }
                else
                {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        @Override
                        public void onDismiss(DialogInterface dialog)
                        {}
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        // unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unbindService(mServiceConnection);

        mBluetoothLeService.close();
        mBluetoothLeService = null;
        if(handler!=null)   handler.removeCallbacks(updateTimer);
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    @Override
    protected void onResume()
    {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled())
        {
            if (!mBluetoothAdapter.isEnabled())
            {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        //while(!mBluetoothLeService.mBluetoothGattConnected){}
        //bleTelitUART();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED)
        {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DEVICE_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_Enable);
        intentFilter.addAction(BluetoothLeService.ACTION_Connect_Fail);
        //intentFilter.addAction(BluetoothLeService.COUNTDOWN_BR);

        return intentFilter;
    }

    private void scanLeDevice(final boolean enable)
    {
        if (enable)
        {
            mScanning = true;
            mBluetoothLeService.ScanDevice(true);
        }
        else
        {
            mScanning = false;
            mBluetoothLeService.ScanDevice(false);
        }
    }

    private void DiscoverGattDevice(String DeviceName, String DeviceAddress)
    {
        String Address = DeviceAddress.replaceAll(":", "");

        if (DeviceName == null)
        {
            DeviceName = "Unknow Device";
        }

        if (!SearchBLE && (!BLUETOOTH_RECONNECT || !OpenDialog))
        {
            mLeDeviceListAdapter.addDevice(DeviceName, DeviceAddress);
            mLeDeviceListAdapter.notifyDataSetChanged();
        }
    }

    public void bleTelitUART()
    {
        //if(!mBluetoothLeService.mBluetoothGattConnected)
        //    return;
        final long  timeInterval = 3000;

        Timer   timer = new Timer(true);
        timer.schedule(new bleSendTask(), 3000, timeInterval);
        mDataText.setText(mDataText.getText()+ String.valueOf(counterLoops) +"\t, ");
        mScroller.fullScroll(ScrollView.FOCUS_DOWN);
    }

    public class bleSendTask extends TimerTask
    {
        @Override
        public void run()
        {
            mBluetoothLeService.writeCharacteristicCMD(Utils.genrateASCIIList());
            Log.d("Timer Task", "Write ASCII to PC RS-232.");
            counterLoops++;
        }
    }

    public boolean checkTimeOut(Intent intent)
    {
        long    lastSecand = intent.getLongExtra("countdown", 0);

        if (lastSecand < 2L)
        {
            Log.d("TimeOut()", "service Time Out: " + lastSecand);
            return true;
        }
        else
        {
            //InsertMessage("  " + lastSecand);
            Log.d("TimeOut()", "CountDown: " + lastSecand + " s");
            return false;
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices)
    {
        if (gattServices == null) return;
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR_MR1)
    private void appVersion()
    {
        try
        {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            int     versionCode = packageInfo.versionCode;
            String  versionName = packageInfo.versionName;
            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            toolbar.setTitle(toolbar.getTitle() + "\t\t\t v" + versionName + "." + versionCode);
            Log.d("appVersion", "Ver." + versionName  + "." + versionCode);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            Log.d("error ", "appVersion() " + e.toString());
            //e.printStackTrace();
        }
    }

    /*
    private void CommandTest(byte command)
    {
        byte[] testCommand = new byte[0];

        if(!mBluetoothLeService.mBluetoothGattConnected)
            return;

        switch ((byte)command)
        {
            case (byte) 0xA0:
                testCommand = new byte[]{0x4D, (byte) 0xFE, 0x00, 0x02, (byte) 0x81, (byte) 0xCE};
                break;

            case (byte) 0xA1:
                testCommand = Utils.mlcTestCommand((byte)0x01);
                break;

            default:
                break;
        }
        mBluetoothLeService.writeCharacteristicCMD(testCommand);
        Log.d("Cmd ", "Write Command to device.");

        StringBuilder sb= new StringBuilder(testCommand.length);
        for (byte indx: testCommand)
        {
            sb.append(format("%02X", indx));
        }
        Log.d("Cmd ", "Write Command to NC150: " + sb.toString());
        InsertMessage("C:" + sb.toString());
    }

    private void LogDebugShow(String info, byte[] data)
    {
        for (int i=0;i<data.length; i++)
        {
            Log.d(info, " data [" + i + "]= " + format("0x%02X",data[i]));
        }
    }
    */

    private void displayData(String data)
    {
            if(OpenDialog)
            {
                OpenDialog = false;
                progressDialog.cancel();
            }

        InsertMessage(data);
    }

    @Override
    public void onStop()
    {
        super.onStop();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showPopupWindowEvent()
    {
        dismissPopupWindow();
        View    rootView = getLayoutInflater().inflate(R.layout.popup_window, null);
        Display display = getWindowManager().getDefaultDisplay();
        Point   size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        ListView popupList = (ListView) rootView.findViewById(R.id.popup_list);
        Button popupButton = (Button) rootView.findViewById(R.id.button11);
        popupButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                scanLeDevice(false);
                //mDataText.setText("");
                dismissPopupWindow();
            }
        });

        mLeDeviceListAdapter.clear();
        popupList.setAdapter(mLeDeviceListAdapter);
        popupList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dismissPopupWindow();
                mDataText.setText("");
                OpenDialog("BLE Status", "Try to connect with device...");
                scanLeDevice(false);
                mBluetoothLeService.connect(mLeDeviceListAdapter.getDevice(position));
            }
        });

        mPopupWindow = new PopupWindow(rootView, 2 * width / 3, ActionBar.LayoutParams.WRAP_CONTENT, false);
        mPopupWindow.setAnimationStyle(R.style.popup_window);
        mPopupWindow.setOutsideTouchable(false);
        mPopupWindow.setFocusable(false);
        mPopupWindow.update();
        mPopupWindow.showAtLocation(getLayoutInflater().inflate(R.layout.activity_main, null),
                                    Gravity.CENTER, 0, 0);
    }

    private void dismissPopupWindow()
    {
        if (mPopupWindow != null)
        {
            mPopupWindow.dismiss();
        }
    }

    public static byte[] hexStringToByteArray(String s)
    {
        int getLength = s.length() % 2;

        if(getLength!=0)
        {
            s = "0" + s;
        }

        int     len = s.length();
        byte[]  data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) +
                                    Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void OpenDialog(String Title, String Meaasge)
    {
        if (!OpenDialog)
        {
            OpenDialog = true;
            progressDialog = new ProgressDialog(this);
            progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Cancel",
                    new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                        //    updateRemoveNodeState(RemoveNodeAppEvent.USER_CANCEL);
                        }
                    });
            progressDialog.setCancelable(false);
            progressDialog.setTitle(Title);
            progressDialog.setMessage(Meaasge);
            progressDialog.show();
        }
        else
        {
            progressDialog.setTitle(Title);
            progressDialog.setMessage(Meaasge);
        }
    }

    private void InsertMessage(String Message)
    {
        //mDataText.setText(mDataText.getText()+Message);
        mDataText.setText(mDataText.getText()+Message+"\r\n");
        //mDataText.append(Message);
        mScroller.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private Runnable updateTimer = new Runnable()
    {
        public void run()
        {
            handler.postDelayed(this, 1000);
        }
    };

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter
    {
        private ArrayList<String>   mLeDevices;
        private ArrayList<String>   mMacAddress;
        private LayoutInflater      mInflator;

        public LeDeviceListAdapter()
        {
            super();
            mLeDevices = new ArrayList<String>();
            mMacAddress = new ArrayList<String>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void addDevice(String Devicename, String MacAddress)
        {
            if (!mMacAddress.contains(MacAddress))
            {
                mLeDevices.add(Devicename);
                mMacAddress.add(MacAddress);
            }
        }

        public String getDevice(int position)
        {
            return mMacAddress.get(position);
        }

        public void clear()
        {
            mLeDevices.clear();
            mMacAddress.clear();
        }

        @Override
        public int getCount()
        {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i)
        {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i)
        {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup)
        {
            ViewHolder holder;

            String device = mMacAddress.get(i);
            String deviceNmae = mLeDevices.get(i);

            if (view == null)
            {
                holder = new ViewHolder();
                view = getLayoutInflater().inflate(R.layout.lst_items, null);
                holder.title = (TextView) view.findViewById(R.id.title);
                holder.subTitle = (TextView) view.findViewById(R.id.subTitle);
                view.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) view.getTag();
            }

            holder.title.setText(deviceNmae);
            holder.subTitle.setText(device);
            return view;
        }
    }

    static class ViewHolder
    {
        TextView title;
        TextView subTitle;
    }
}

