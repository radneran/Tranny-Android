package android.com.naren.tranny;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.StrictMode;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;
import java.util.logging.StreamHandler;

import static android.content.Intent.ACTION_VIEW;


public class MainActivity extends AppCompatActivity {
    //  private Handler mHandler; // handler that gets info from Bluetooth service
    boolean connected = false;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;
    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 2;
    private File hmDirectory;
    private final static int REQUEST_ENABLE_BT = 1;
    private final static int REQUEST_OPEN_LINK = 2;
    private final static String TAG = "trannymain";
    Resources res;
    BluetoothDevice nearestDevice = null;
    BluetoothSocket tmp = null;
    Parcelable nearestUuid = null;
    TextView tv;
    ProgressBar pb;
    UUID service = UUID.fromString("10932810-9328-0000-0000-109328109328");
    private Stack<Object> mFoundDevices = new Stack<Object>();
    private boolean complete = false;
    private boolean initialising = false;
    // Defines several constants used when transmitting messages between the
    // service and the UI.
    BluetoothAdapter mBluetoothAdapter;

    private interface MessageConstants {
        int TASK_COMPLETE = 1;
        int TASK_START = 2;
        int TASK_PROGRESS = 3;

    }


    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    StringBuilder displayFoundDevices = new StringBuilder();

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message inputMessage) {
            switch (inputMessage.what) {
                case MessageConstants.TASK_COMPLETE:
                    Log.d(TAG, "TASK_COMPLETE");
                    complete = true;
                    onResume();
                    break;
                case MessageConstants.TASK_START:
                    Log.d(TAG,"TASK_START");
                    pb = (ProgressBar)findViewById(R.id.progressBar);

                    tv.setText((String)inputMessage.obj+"\nFile Downloading: ");
                    pb.setMax(inputMessage.arg1);
                    pb.setProgress(0);
                    pb.setVisibility(ProgressBar.VISIBLE);
                    break;
                case MessageConstants.TASK_PROGRESS:
                    //Log.d(TAG, "TASK_PROGRESS = "+(Integer)inputMessage.obj+"\n");
                    pb.setProgress((Integer)inputMessage.obj);
                    break;

            }
        }
    };
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        Short prevRssi = 0;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Short maxRSSI = Short.MIN_VALUE;

            //  tv = (TextView) findViewById(R.id.sample_text);

            int numFound = 0;
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                numFound++;
                Short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI,Short.MIN_VALUE);
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mFoundDevices.push(rssi);
                mFoundDevices.push(dev);
                if(rssi < prevRssi && dev.getBluetoothClass().getDeviceClass() == BluetoothClass.Device.COMPUTER_LAPTOP)
                    mBluetoothAdapter.cancelDiscovery();
                prevRssi = rssi;

            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d(TAG, "ACTION_DISCOVERY_STARTED");
                tv.setText("Finding nearest device");
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d(TAG, "ACTION_DISCOVERY_FINISHED");
                int idx = 0;
                int nearestD = 0;
                for (Object i : mFoundDevices) {
                    idx++;
                    Short rssi = 0;
                    String deviceName = "";
                 //   String MAC = "";
                    if (i instanceof Short) {
                        rssi = (Short) i;
                        if ((Short) i > maxRSSI) {
                            maxRSSI = (Short) i;
                            nearestD = idx;
                        }
                    } else if (i instanceof BluetoothDevice) {
                        deviceName = ((BluetoothDevice) i).getName();
              //          MAC = ((BluetoothDevice) i).getAddress();
                    }
                  //  displayFoundDevices.append("Device " + deviceName + "\nRSSI: " + rssi + "\n\n");
                 //   tv.setText(displayFoundDevices.toString());
                }
                nearestDevice = (BluetoothDevice) mFoundDevices.get(nearestD);
                tv.setText("Found Device (" + nearestDevice.getName() + ")\nLooking for required service...");
                //tv.setText(displayFoundDevices.toString());

                nearestDevice.fetchUuidsWithSdp();
            } else if (BluetoothDevice.ACTION_UUID.equals(action)) {
                if(initialising){
                    initialising = false;
                    return;
                }
                Parcelable[] Uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                if (!connected) {
                    if (Uuids != null) {
                        /*for (Parcelable uuid : Uuids) {
                            Log.d(TAG, "Received UUID: "+ uuid.toString());
                        }*/
                        for (Parcelable uuid : Uuids) {
                            Log.d(TAG, "Received UUID: "+ uuid.toString());
                            nearestUuid = uuid;
                            if (nearestUuid.toString().equals(service.toString())) {
                                displayFoundDevices.append("Found service\n");
                                if (nearestUuid instanceof ParcelUuid) {
                                    ConnectThread connectNearest = new ConnectThread(nearestDevice, ((ParcelUuid) nearestUuid).getUuid());
                                    connectNearest.start();
                                    connected = true;
                                }
                                displayFoundDevices.append("UUID: " + uuid.toString() + " \n");
                                break;
                            }
                        }
                        if(!connected)
                            tv.setText("Could not find the required service on device. Please  ensure the server is installed and running.");
                    }else
                        tv.setText("Could not find the required service on device. Please  ensure the server is installed and running.");

                    }/* else {
                        displayFoundDevices.append("UUID: NULL\n");*/

                   // tv.setText(displayFoundDevices.toString());


                }

            }


    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE);
        setContentView(R.layout.activity_main);
        tv = (TextView) findViewById(R.id.sample_text);
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        res = getResources();

        hmDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).getPath() + "/TrannyFiles");
        if (!hmDirectory.exists()) {
            hmDirectory.mkdirs();
            Log.d(TAG, "Created home directory");
        } else {
            Log.d(TAG, "HomeDirectory exists");
        }


        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_UUID);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        registerReceiver(mReceiver, filter);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);

        // Example of a call to a native method


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_OPEN_LINK:
                Log.d(TAG, "REQUEST OPEN LINK");
                    if(resultCode == RESULT_OK){
                        Log.d(TAG, "RESULT CODE OK");
                        Message completeMsg = Message.obtain(mHandler, MessageConstants.TASK_COMPLETE);
                        completeMsg.sendToTarget();
                        break;
                    }
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                        if (!mBluetoothAdapter.startDiscovery()) {
                            Log.d(TAG, "Could not start discovery!");
                        } else
                            Log.d(TAG, "Searching for devices...");

                    return;

                } else {
                    tv.setText("Device requires bluetooth to be turned on. Please restart the application and allow bluetooth to be turned on.");
                    break;
                }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                    if (mBluetoothAdapter == null) {
                        // Device does not support Bluetooth
                        tv.setText("Device does not support Bluetooth");
                        return;
                    }
                    if (!mBluetoothAdapter.isEnabled()) {
                        initialising = true;
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    } else {
                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                        if (pairedDevices.size() > 0) {
                            // There are paired devices. Get the name and address of each paired device.
                            for (BluetoothDevice device : pairedDevices) {
                                String deviceName = device.getName();
                                if (deviceName.equals("RAD"))
                                    nearestDevice = device;
                                String deviceHardwareAddress = device.getAddress(); // MAC address

                                Log.d(TAG, "Paired Device: " + deviceName + "\nMAC Address:" + deviceHardwareAddress + "\n");

                                displayFoundDevices.append("Paired Device: " + deviceName + "\nMAC Address:" + deviceHardwareAddress + "\n");
                                ParcelUuid[] dUuids = device.getUuids();
                                /*if(dUuids!=null){
                                    for(ParcelUuid u: dUuids){
                                        Log.i(TAG, "UUID: " + u.toString());
                                        if(u.getUuid().equals(service)){
                                            ConnectThread connectNearest = new ConnectThread(nearestDevice, u.getUuid());
                                            connectNearest.start();
                                            connected=true;
                                            break;
                                        }
                                    }
                                }else{*/
                                    device.fetchUuidsWithSdp();
                                //}
                                if(connected)
                                    break;
                            }
                            if (displayFoundDevices != null)
                                tv.setText(displayFoundDevices);
                        } else {
                            Log.d(TAG, "No paired devices.");
                            if (!mBluetoothAdapter.startDiscovery()) {
                                Log.d(TAG, "Could not start discovery!");
                            } else
                                Log.d(TAG, "Searching for devices...");
                        }
                        return;
                    }
                } else {
                    if (tv != null)
                        tv.setText("Device requires location access to start bluetooth. Please restart the application and grant permission when prompted.");
                    return;
                }
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                            MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
                    return;
                } else {
                    if (tv != null)
                        tv.setText("Need file access for proper file transfer. Please restart application and grant permission when prompted");
                    return;
                }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (complete) {
            Log.d(TAG, "Finishing Activity");
            if(android.os.Build.VERSION.SDK_INT >= 21)
            {
                this.finishAndRemoveTask();
            }
            else
            {
                this.finish();
            }
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(mReceiver);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device, UUID MY_UUID) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            Log.d(TAG, "ConnectThread initialising");
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                Log.d(TAG, "Creating Socket");
            } catch (IOException e) {

            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            mBluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
                Log.d(TAG, "Connecting...");

            } catch (IOException connectException) {
                connectException.printStackTrace();
                Log.e(TAG, "Attempting to connect through Fallback");
                try {
                    Log.d(TAG, "trying fallback...");

                    tmp = (BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(mmDevice, 1);
                    tmp.connect();

                    Log.d(TAG, "Connected");
                } catch (Exception e2) {
                    Log.e("TAG", "Couldn't establish Bluetooth connection!");
                    try {
                        // Unable to connect; close the socket and return.
                        mmSocket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Could not close the client socket", closeException);
                    }
                    return;
                }

            }
            Log.d(TAG, "Initialising connected thread");

            ConnectedThread t = new ConnectedThread(tmp);
            t.start();
            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.

        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store for the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            Message completeMsg;
            Message taskStart;
            Message taskProg;
            mmBuffer = new byte[4098];
            byte[] reply = new byte[1];
            byte send = (byte) 0xFF;
            byte doNotSend = (byte) 0x0F;
            int fileSize = 0;
            // ByteArrayOutputStream tmpBuffer = new ByteArrayOutputStream();
            int numBytes = 0; // bytes returned from read()
            File[] fList = hmDirectory.listFiles();
            File currFile = null;
            int current = 0;
            boolean connEstablished = false;

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {

                    if (!connEstablished) {
                       // mmOutStream.write(send);
                       // Log.d(TAG, "Wrote to stream");
                        Log.d(TAG, "Trying to read input stream");
                        numBytes = mmInStream.read(mmBuffer);
                        //tmpBuffer.write(mmBuffer);
                        Log.d(TAG, "numBytes = " + numBytes + "\n");
                        String str = new String(mmBuffer, "UTF-8");
                        str = str.substring(0, numBytes);
                        Log.d(TAG, "numBytes = " + numBytes + "\n" +
                                "strlen = " + str.length() + "\n" + str);

                        if (str.contains("http")) {
                            Log.d(TAG, "Opening link "+str);
                            Intent openLink = new Intent(ACTION_VIEW, Uri.parse(str));
                            //openLink.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(openLink);
                            sleep(2000);
                            completeMsg = Message.obtain(mHandler, MessageConstants.TASK_COMPLETE);
                            completeMsg.sendToTarget();
                            cancel();
                            break;
                        } else {
                            String[] strArr;
                            strArr = str.split("---");
                            fileSize = Integer.parseInt(strArr[1]);
                            mmBuffer = new byte[fileSize];
                            Log.d(TAG, "numBytes = " + numBytes + "\n" +
                                    "File name = " + strArr[0]
                                    + "\nFile size = " + fileSize);
                            if (fList != null) {
                                Log.d(TAG, "fList size: " + fList.length);
                                for (File f : fList) {
                                    Log.d(TAG, f.getName());
                                    if (f.toString().contains(strArr[0])) {
                                        Log.d(TAG, "File exists: " + hmDirectory.getPath() + "/" + strArr[0]);
                                        reply[0] = doNotSend;

                                        mmOutStream.write(reply);
                                        Log.d(TAG, "Sent reply DoNotSend");
                                        currFile = new File(hmDirectory.getPath() + "/" + strArr[0]);
                                        Intent openFile = new Intent(ACTION_VIEW);
                                        openFile.setDataAndType(Uri.fromFile(currFile), getMimeType(currFile.getAbsolutePath()));
                                        openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                        startActivity(openFile);
                                        completeMsg = Message.obtain(mHandler, MessageConstants.TASK_COMPLETE);
                                        completeMsg.sendToTarget();
                                        break;

                                    }
                                }
                            }
                            if (reply[0] != doNotSend) {
                                Log.d(TAG, "Creating File: " + hmDirectory.getPath() + "/" + strArr[0]);

                                currFile = new File(hmDirectory.getPath() + "/" + strArr[0]);
                                final boolean newFile = currFile.createNewFile();
                                taskStart = Message.obtain(mHandler,MessageConstants.TASK_START,fileSize,0,strArr[0]);
                                taskStart.sendToTarget();
                                reply[0] = send;
                                mmOutStream.write(reply);
                                Log.d(TAG, "Sent reply Send");
                            }
                            connEstablished = true;

                        }
                    } else {
                        do {

                            numBytes = mmInStream.read(mmBuffer, current, mmBuffer.length - current);
                            current += numBytes;
                            taskProg = Message.obtain(mHandler,MessageConstants.TASK_PROGRESS,current);
                            taskProg.sendToTarget();
                            Log.d(TAG, "Bytes read: " + current);
                        } while (numBytes > -1 && current < fileSize);
                        if (currFile != null) {
                            Log.d(TAG, "Writing to " + currFile.getAbsolutePath());
                            FileOutputStream fos;
                            try {
                                fos = new FileOutputStream(currFile);
                                fos.write(mmBuffer);
                                fos.flush();
                                fos.close();
                                Intent openFile = new Intent(ACTION_VIEW);
                                Log.d(TAG,currFile.getAbsolutePath());
                                openFile.setDataAndType(Uri.fromFile(currFile), getMimeType(currFile.getAbsolutePath()));
                                openFile.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                startActivity(openFile);
                                completeMsg = Message.obtain(mHandler, MessageConstants.TASK_COMPLETE);
                                completeMsg.sendToTarget();

                            } catch (Exception e1) {
                                e1.printStackTrace();
                                if (e1 instanceof IOException) {
                                    Log.d(TAG, "Deleting file");
                                    boolean del = currFile.delete();
                                    if (del) {
                                        Log.d(TAG, "File deleted");
                                    }
                                }
                            }


                            break;


                        }


                        // Send the obtained bytes to the UI activity.
             /*       Message readMsg = mHandler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();*/
                    }
                } catch (Exception e) {

                    Log.d(TAG, "Input stream was disconnected", e);
                    if (e instanceof IOException && reply[0] == send) {
                        Log.d(TAG, "Deleting file");
                        boolean del = currFile.delete();
                        if (del) {
                            Log.d(TAG, "File deleted");
                        }
                    }
                    cancel();
                    break;
                }

                //  if(mmInStream.available()>0)


            }
        }

        private String getMimeType(String url) {
            //String parts[]=url.split(".");
            //String extension=parts[parts.length-1];
            //String extension = MimeTypeMap.getFileExtensionFromUrl(url);

            String extension = getExtension(url);
            extension = extension.toLowerCase();
           /* if(extension.isEmpty()){
                extension = getExtension(url);
            }*/
            Log.d(TAG, "Extension = " + extension + "\n");
            String type = null;
            if (extension != null) {
                MimeTypeMap mime = MimeTypeMap.getSingleton();
                type = mime.getMimeTypeFromExtension(extension);
                Log.d(TAG, "MIMEType = " + type.toString() + "\n");
            }
            return type;
        }
        private String getExtension(String path){
           int idx = path.lastIndexOf('.');

            return path.substring(idx+1);
        }

        /*public void transferToTmpBuffer(byte[] mmBuffer, ByteArrayOutputStream tmpBuffer){
           int size = mmBuffer.length;
           byte[] tmp = new byte[size];
           tmp =
       }*/
        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                // Share the sent message with the UI activity.
              /*  Message writtenMsg = mHandler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();*/
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
              /*  Message writeErrorMsg =
                        mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                mHandler.sendMessage(writeErrorMsg);*/
            }
        }


        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                Log.d(TAG, "Closing socket");
                if (mmSocket != null)
                    mmSocket.close();
            } catch (Exception e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }
    }

}
