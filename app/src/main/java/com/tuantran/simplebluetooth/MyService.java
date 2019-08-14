package com.tuantran.simplebluetooth;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class MyService extends Service {
    private static final String TAG = "MyService";

    private MyBroadcastReceiver myReceiver;

    private BluetoothDevice mDevice;

    private BluetoothAdapter mBTAdapter;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: service created");
        //Toast.makeText(getApplicationContext(), "Bluetooth service started", Toast.LENGTH_LONG).show();

        mBTAdapter = BluetoothAdapter.getDefaultAdapter();
        for(BluetoothDevice device:mBTAdapter.getBondedDevices()){
            if(device.getName()!=null)
                if(device.getName().equals("Dasan RCU"))
                    mDevice=device;
        }

        myReceiver = new MyBroadcastReceiver();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

        registerReceiver(myReceiver, filter);

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    class MyBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            switch (action) {
                case BluetoothDevice.ACTION_FOUND: {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    if (device.getName() != null)
                        if (device.getName().equals("Dasan RCU")) {
                            mDevice = device;
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                                boolean bond=mDevice.createBond();
                            }
                        }


                    Intent intentFound = new Intent("device.found");
                    Bundle b = new Bundle();
                    b.putParcelable("deviceFound", device);
                    intentFound.putExtra("deviceFound", b);
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intentFound);


                    break;
                }

                case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                    //mListener.onDiscoveryFinished();
                    LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent("discovery.finished"));
                    break;
                }

                case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                    Log.d(TAG, "onReceive: ACTION_BOND_STATE_CHANGED");
                    //mListener.onHandleDevicePairing();

                    mDevice=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                    Intent intent1 = new Intent("device.state.change");
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent1);
                    Toast.makeText(getApplicationContext(), "Device state changes", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "onReceive: Device state changes");

                    //handlePairingStateChange(mDevice);

                    if (mDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(getApplicationContext(), "Already connected", Toast.LENGTH_LONG).show();
                        Log.d(TAG, "BOND_BONDED: Device is bonded. ");
                        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(MyService.this);
                        localBroadcastManager.sendBroadcast(new Intent("close.connection.activity"));
                    }

                    if (mDevice.getBondState() == BluetoothDevice.BOND_BONDING) {
                        Toast.makeText(getApplicationContext(), "Connecting...", Toast.LENGTH_LONG).show();
                    }

                    if (mDevice.getBondState() == BluetoothDevice.BOND_NONE) {
                        Log.d(TAG, "handlePairingStateChange: BOND_NONE");
                        Toast.makeText(getApplicationContext(), "Connect failed or device is unpaired.", Toast.LENGTH_LONG).show();


                        Intent intent2 = new Intent(getApplicationContext(), ConnectionActivity.class);
                        intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        Bundle bundle = new Bundle();
                        bundle.putParcelable("device", mDevice);
                        intent2.putExtra("device", bundle);
                        startActivity(intent2);
                    }
                    break;
                }

                default:
                    // Does nothing
                    break;
            }
        }
    }

    /*private void handlePairingStateChange(BluetoothDevice device) {

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            Toast.makeText(getApplicationContext(), "Already connected", Toast.LENGTH_LONG).show();
            Log.d(TAG, "BOND_BONDED: Device is bonded. ");

        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
            Toast.makeText(getApplicationContext(), "Connecting...", Toast.LENGTH_LONG).show();
        }

        if (device.getBondState() == BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "handlePairingStateChange: BOND_NONE");
            Toast.makeText(getApplicationContext(), "Connect failed or device is unpaired.", Toast.LENGTH_LONG).show();

            Intent intent2 = new Intent(getApplicationContext(), ConnectionActivity.class);
            intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent2);
        }

    }*/


}


