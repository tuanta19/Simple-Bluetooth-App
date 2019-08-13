package com.tuantran.simplebluetooth;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // GUI Components
    private TextView mBluetoothStatus;
    private TextView mReadBuffer;
    private Button mScanBtn;
    private Button mOffBtn;
    private Button mListPairedDevicesBtn;
    private Button mDiscoverBtn;
    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;
    private ListView mDevicesListView;
    //private TextView appTv;

    private final String TAG = MainActivity.class.getSimpleName();
    private Handler mHandler; // Our main handler that will receive callback notifications
    //private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    private static final UUID BTMODULEUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    private final String NAME = "BluetoothApp";

    private BluetoothDevice mDevice;
    private Set<String> mDiscoveredDevices;

    private LocalBroadcastManager mLocalBroadcastManager;

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothStatus = (TextView) findViewById(R.id.bluetoothStatus);
        //mReadBuffer = (TextView) findViewById(R.id.readBuffer);
        mScanBtn = (Button) findViewById(R.id.scan);
        mOffBtn = (Button) findViewById(R.id.off);
        mDiscoverBtn = (Button) findViewById(R.id.discover);
        mListPairedDevicesBtn = (Button) findViewById(R.id.PairedBtn);


        mBTArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        mDevicesListView = (ListView) findViewById(R.id.devicesListView);
        mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        mDiscoveredDevices = new HashSet<>();


        // Ask for location permission if not already allowed
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


        mHandler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what == MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    mReadBuffer.setText(readMessage);
                }

                if (msg.what == CONNECTING_STATUS) {
                    if (msg.arg1 == 1)
                        mBluetoothStatus.setText("Connected to Device: " + (String) (msg.obj));
                    else
                        mBluetoothStatus.setText("Connection Failed");
                }
            }
        };



        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(), "Bluetooth device not found!", Toast.LENGTH_SHORT).show();
        } else {

//            if (mConnectedThread != null) //First check to make sure thread created
//                mConnectedThread.write("1");

            //discoverAtLaunch();

            mScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOn(v);
                }
            });

            mOffBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOff(v);
                }
            });

            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listPairedDevices(v);
                }
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    discover(v);
                }
            });

            Intent intent = new Intent(this, MyService.class);
            startService(intent);
        }

    }

    private void bluetoothOn(View view) {
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("Bluetooth enabled");
            Toast.makeText(getApplicationContext(), "Bluetooth turned on", Toast.LENGTH_SHORT).show();

        } else {
            Toast.makeText(getApplicationContext(), "Bluetooth is already on", Toast.LENGTH_SHORT).show();
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText("Enabled");
            } else
                mBluetoothStatus.setText("Disabled");
        }
    }

    private void bluetoothOff(View view) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Warning!");
        builder.setMessage("The RCU cannot be used if the Bluetooth off is available.");

        //add a button
        builder.setNegativeButton("Cancel", null);
        /*builder.setPositiveButton("Continue to turn OFF", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                mBTAdapter.disable(); // turn off
                mBluetoothStatus.setText("Bluetooth disabled");
                Toast.makeText(getApplicationContext(), "Bluetooth turned Off", Toast.LENGTH_SHORT).show();
            }
        });*/

        //create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();

    }

    private void unPairDevice(BluetoothDevice device) {
        mDevice = device;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Device is already paired");
        builder.setMessage("This device is already in paired list. Do you want to unpair device?");

        //add a button
        builder.setNegativeButton("Cancel", null);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    mBluetoothStatus.setText("Unpaired device");
                    Toast.makeText(getApplicationContext(), "Unpairing " + mDevice.getName(), Toast.LENGTH_SHORT).show();
                    Method m = null;
                    try {
                        m = mDevice.getClass()
                                .getMethod("removeBond", (Class[]) null);
                        m.invoke(mDevice, (Object[]) null);
                    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                        Log.e(TAG, e.getMessage());
                    }

                }
            });
        }

        //create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void discoverAtLaunch() {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            //Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); // clear items
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();

                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_FOUND);
                filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);

                registerReceiver(blReceiver, filter);
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void discover(View view) {
        // Check if the device is already discovering
        if (mBTAdapter.isDiscovering()) {
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
        } else {
            if (mBTAdapter.isEnabled()) {
                mDiscoveredDevices.clear();
                mBTArrayAdapter.clear(); // clear items

                mPairedDevices = mBTAdapter.getBondedDevices(); //get list of paired devices for checking further
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                mBluetoothStatus.setText("Discovering...");

                /*Intent intent = new Intent(this, MyService.class);
                startService(intent);*/

                IntentFilter filter = new IntentFilter();
                filter.addAction("device.found");
                filter.addAction("discovery.finished");
                filter.addAction("device.state.change");

                if (mLocalBroadcastManager == null)
                    mLocalBroadcastManager.getInstance(this).registerReceiver(blReceiver, filter);
            } else {
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }


    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("device.found".equals(action)) {
                Bundle bundle = intent.getBundleExtra("deviceFound");
                BluetoothDevice device = bundle.getParcelable("deviceFound");
                //BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                String deviceDescription = (device.getName() + "\n" + device.getAddress());
                mDiscoveredDevices.add(deviceDescription);
                mBTArrayAdapter.clear();
                mBTArrayAdapter.addAll(mDiscoveredDevices);
                mBTArrayAdapter.notifyDataSetChanged();

                Log.d(TAG, "onReceive: found device: " + device.getName());

                /*if (device.getName() != null)
                    if (device.getName().equals("Dasan RCU") ||
                            device.getName().equals("Galaxy G925F - AT")) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                            mDevice = device;
                            boolean bond = mDevice.createBond();
                            if (bond)
                                Log.d(TAG, "onReceive: bond: " + bond);
                        }

                    }*/
            }

            if ("discovery.finished".equals(action)) {
                Log.d(TAG, "onReceive: ACTION_DISCOVERY_FINISHED");
                mBluetoothStatus.setText("Discovery finished");
            }

            if ("device.state.change".equals(action)) {
                Log.d(TAG, "onReceive: ACTION_BOND_STATE_CHANGED");
                handlePairingStateChange(mDevice);
            }

        }
    };

    private void handlePairingStateChange(BluetoothDevice device) {

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            Toast.makeText(getApplicationContext(), "Already connected", Toast.LENGTH_LONG).show();
            mBluetoothStatus.setText("Connected " + mDevice.getName());
        }

        if (device.getBondState() == BluetoothDevice.BOND_BONDING) {
            Toast.makeText(getApplicationContext(), "Connecting...", Toast.LENGTH_LONG).show();
        }

        if (device.getBondState() == BluetoothDevice.BOND_NONE) {
            Log.d(TAG, "handlePairingStateChange: BOND_NONE");
            Toast.makeText(getApplicationContext(), "Connect failed or device is unpaired.", Toast.LENGTH_LONG).show();
            mBluetoothStatus.setText("Choose device to connect");
            handleReconnetion();
        }

    }

    private void handleReconnetion() {
        Log.d(TAG, "handleReconnetion: ");
        /*AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("RCU is unpaired. Need to connect again");
        builder.setMessage("Press Back and Home buttons on the RCU to connect again");

        //add a button
        //builder.setNegativeButton("Cancel", null);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    mBluetoothStatus.setText("Connecting...");
                    Toast.makeText(getApplicationContext(), "Connecting " + mDevice.getName(), Toast.LENGTH_SHORT).show();
                    boolean bond = mDevice.createBond();
                    if (bond)
                        Log.d(TAG, "onReceive: bond: " + bond);

                }
            });
        }

        //create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();*/


        Intent intent = new Intent(getBaseContext(), ConnectionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getBaseContext().startActivity(intent);
        Toast.makeText(getApplicationContext(), "Reconnect...!!!", Toast.LENGTH_SHORT).show();


    }

    private void listPairedDevices(View view) {
        mBTArrayAdapter.clear();
        mPairedDevices = mBTAdapter.getBondedDevices();
        if (mBTAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {

            if (!mBTAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            mBluetoothStatus.setText("Connecting...");
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0, info.length() - 17);


            mDevice = mBTAdapter.getRemoteDevice(address);
            Log.d(TAG, "onItemClick: device address " + address);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Log.d(TAG, "onItemClick: createBond");
                if (mPairedDevices.contains(mDevice)) {
                    Log.d(TAG, "onItemClick: device is already in paired list");
                    //Toast.makeText(getApplicationContext(),"Device is already in paired list ", Toast.LENGTH_LONG).show();
                    unPairDevice(mDevice);
                } else {
                    boolean outcome = mDevice.createBond();
                    if (outcome == true) {
                        // Toast.makeText(getApplicationContext(),"Bluetooth connected successful ", Toast.LENGTH_LONG).show();
                        //mBluetoothStatus.setText("Connected "+name);
                    } else {
                        //Toast.makeText(getApplicationContext(),"Bluetooth connect failed ", Toast.LENGTH_LONG).show();
                    }
                }
            }


        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mPairedDevices = mBTAdapter.getBondedDevices();
        if (mPairedDevices.size() > 0) {
            for (BluetoothDevice device : mPairedDevices)
                mBluetoothStatus.setText("Connected " + device.getName());
        }
    }

    /*private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        buffer = new byte[1024];
                        SystemClock.sleep(100); //pause and wait for rest of data. Adjust this depending on your sending speed.
                        bytes = mmInStream.available(); // how many bytes are ready to be read?
                        bytes = mmInStream.read(buffer, 0, bytes); // record how many bytes we actually read
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget(); // Send the obtained bytes to the UI activity
                    }
                } catch (IOException e) {
                    e.printStackTrace();

                    break;
                }
            }
        }

        *//* Call this from the main activity to send data to the remote device *//*
        public void write(String input) {
            byte[] bytes = input.getBytes();           //converts entered String into bytes
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
            }
        }

        *//* Call this from the main activity to shutdown the connection *//*
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }*/
}


