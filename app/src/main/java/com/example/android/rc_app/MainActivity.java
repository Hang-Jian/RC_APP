package com.example.android.rc_app;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements Joystick.JoystickListener {
    private ProgressDialog progress = null;
    private BluetoothSocket btSocket = null;
    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private boolean isBtConnected = false;
    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    private final static int MESSAGE_READ = 2;        // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status
    private String address = null;

    private Joystick joystickLeft;
    private Joystick joystickRight;
    private boolean isUnlocked = false;

    private long startTime;
    private Handler handler = new Handler();

    // Multiwii
    char size0 = 0x10, size1 = 0xc8, roll0 = 0Xdc, roll1 = 0X05, pitch0 = 0Xdc, pitch1 = 0X05, yaw0 = 0Xdc, yaw1 = 0X05, throttle0 = 0Xe8, throttle1 = 0X03, aux1a = 0Xdc, aux1b = 0X05, aux2a = 0Xe8, aux2b = 0X03, aux3a = 0Xe8, aux3b = 0X03, aux4a = 0Xe8, aux4b = 0X03, crc;

    private int counter = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS); //receive the address of the bluetooth device
        setContentView(R.layout.activity_main);

        joystickLeft = (Joystick) findViewById(R.id.joystickLeft);
        joystickRight = (Joystick) findViewById(R.id.joystickRight);
        joystickLeft.setHatColor(255, 255, 0, 0);
        startTime = System.currentTimeMillis();
        handler.removeCallbacks(updateTimer);
        handler.postDelayed(updateTimer, 1000);
        new ConnectBT().execute(); //Call the class to connect
        mHandler = new Handler() {
            public void handleMessage(android.os.Message msg) {
                if (msg.what == MESSAGE_READ) {
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

    private Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            final TextView time = (TextView) findViewById(R.id.time);
            long spentTime = System.currentTimeMillis() - startTime;
            long minute = (spentTime / 1000) / 60;
            long second = (spentTime / 1000) % 60;
            String min, sec;
            if (minute < 10) {
                min = "0" + minute;
            } else {
                min = String.valueOf(minute);
            }
            if (second < 10) {
                sec = "0" + second;
            } else {
                sec = String.valueOf(second);
            }
            time.setText(min + " : " + sec);
            if (minute >= 6) {
                time.setTextColor(Color.RED);
            }
            handler.postDelayed(this, 1000);
        }
    };


    @Override
    public void onJoystickMoved(float xPercent, float yPercent, boolean edge, int id, double angle) throws IOException {
        switch (id) {
            case R.id.joystickRight:
                pitch_roll(xPercent, yPercent);
                break;
            case R.id.joystickLeft:
                boolean unlock = angle > 30.0 && angle < 50.0;
                boolean lock = angle > 130.0 && angle < 160.0;
                if (unlock && edge) {       // unlock
                    counter++;
                    if (counter == 30 && !isUnlocked) {
                        unlock(true);
                        joystickLeft.setHatkPos("Bottom");
                        isUnlocked = true;
                    }

                } else if (lock && edge) {  // lock
                    counter++;
                    if (counter == 30) {
                        if (isUnlocked) {
                            unlock(false);
                            joystickLeft.setHatkPos("Center");
                            isUnlocked = false;
                        } else {
                            try {
                                btSocket.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            finish();
                        }
                    }
                } else {
                    if (isUnlocked) {
                        throttle_yaw(xPercent, yPercent);
                    }
                    counter = 0;
                }
                break;
            default:
                break;
        }
    }

    void unlock(boolean lock) throws IOException {
        if (lock) {
            yaw0 = 0Xd0;
            yaw1 = 0X07;
            throttle0 = 0Xe8;
            throttle1 = 0X03;
            msg("Unlocked");
        } else {
            yaw0 = 0Xe8;
            yaw1 = 0X03;
            throttle0 = 0Xe8;
            throttle1 = 0X03;
            msg("Locked");
        }
        mConnectedThread.write('$');
        mConnectedThread.write('M');
        mConnectedThread.write('<');
        mConnectedThread.write(size0);
        mConnectedThread.write(size1);
        mConnectedThread.write(roll0);
        mConnectedThread.write(roll1);
        mConnectedThread.write(pitch0);
        mConnectedThread.write(pitch1);
        mConnectedThread.write(yaw0);
        mConnectedThread.write(yaw1);
        mConnectedThread.write(throttle0);
        mConnectedThread.write(throttle1);
        mConnectedThread.write(aux1a);
        mConnectedThread.write(aux1b);
        mConnectedThread.write(aux2a);
        mConnectedThread.write(aux2b);
        mConnectedThread.write(aux3a);
        mConnectedThread.write(aux3b);
        mConnectedThread.write(aux4a);
        mConnectedThread.write(aux4b);
        crc = (char) (0X10 ^ 0XC8 ^ roll0 ^ roll1 ^ pitch0 ^ pitch1 ^ yaw0 ^ yaw1 ^ throttle0 ^ throttle1 ^ aux1a ^ aux1b ^ aux2a ^ aux2b ^ aux3a ^ aux3b ^ aux4a ^ aux4b);
        mConnectedThread.write(crc);
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    void throttle_yaw(float xPercent, float yPercent) {
        mConnectedThread.write('$');
        mConnectedThread.write('M');
        mConnectedThread.write('<');
        mConnectedThread.write(size0);
        mConnectedThread.write(size1);
        mConnectedThread.write(roll0);
        mConnectedThread.write(roll1);
        mConnectedThread.write(pitch0);
        mConnectedThread.write(pitch1);
        if (xPercent == 0) {
            yaw0 = 0XDC;
            yaw1 = 0X05;
        } else {
            yaw0 = (char) ((1500 + 500 * xPercent) % 256);
            yaw1 = (char) ((1500 + 500 * xPercent) / 256);
        }
        mConnectedThread.write(yaw0);
        mConnectedThread.write(yaw1);
        throttle0 = (char) ((1000 + 1000 * yPercent) % 256);
        throttle1 = (char) ((1000 + 1000 * yPercent) / 256);
        mConnectedThread.write(throttle0);
        mConnectedThread.write(throttle1);
        mConnectedThread.write(aux1a);
        mConnectedThread.write(aux1b);
        mConnectedThread.write(aux2a);
        mConnectedThread.write(aux2b);
        mConnectedThread.write(aux3a);
        mConnectedThread.write(aux3b);
        mConnectedThread.write(aux4a);
        mConnectedThread.write(aux4b);
        crc = (char) (0X10 ^ 0XC8 ^ roll0 ^ roll1 ^ pitch0 ^ pitch1 ^ yaw0 ^ yaw1 ^ throttle0 ^ throttle1 ^ aux1a ^ aux1b ^ aux2a ^ aux2b ^ aux3a ^ aux3b ^ aux4a ^ aux4b);
        mConnectedThread.write(crc);
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    void pitch_roll(float xPercent, float yPercent) {
        mConnectedThread.write('$');
        mConnectedThread.write('M');
        mConnectedThread.write('<');
        mConnectedThread.write(size0);
        mConnectedThread.write(size1);
        if (xPercent == 0) {
            roll0 = 0XDC;
            roll1 = 0X05;
        } else {
            roll0 = (char) ((1500 + 500 * xPercent) % 256);
            roll1 = (char) ((1500 + 500 * xPercent) / 256);
        }

        if (yPercent == 0) {
            pitch0 = 0XDC;
            pitch1 = 0X05;
        } else {
            pitch0 = (char) ((1500 - 500 * yPercent) % 256);
            pitch1 = (char) ((1500 - 500 * yPercent) / 256);
        }
        mConnectedThread.write(roll0);
        mConnectedThread.write(roll1);
        mConnectedThread.write(pitch0);
        mConnectedThread.write(pitch1);
        mConnectedThread.write(yaw0);
        mConnectedThread.write(yaw1);
        mConnectedThread.write(throttle0);
        mConnectedThread.write(throttle1);
        mConnectedThread.write(aux1a);
        mConnectedThread.write(aux1b);
        mConnectedThread.write(aux2a);
        mConnectedThread.write(aux2b);
        mConnectedThread.write(aux3a);
        mConnectedThread.write(aux3b);
        mConnectedThread.write(aux4a);
        mConnectedThread.write(aux4b);
        crc = (char) (0X10 ^ 0XC8 ^ roll0 ^ roll1 ^ pitch0 ^ pitch1 ^ yaw0 ^ yaw1 ^ throttle0 ^ throttle1 ^ aux1a ^ aux1b ^ aux2a ^ aux2b ^ aux3a ^ aux3b ^ aux4a ^ aux4b);
        mConnectedThread.write(crc);
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    // fast way to call Toast
    private void msg(String s) {
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(MainActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices)    //while the progress dialog is shown, the connection is building in background
        {
            if (btSocket == null || !isBtConnected) {
                try {
                    BluetoothAdapter myBluetooth = BluetoothAdapter.getDefaultAdapter();
                    BluetoothDevice mmDevice = myBluetooth.getRemoteDevice(address);
                    btSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    myBluetooth.cancelDiscovery();
                    btSocket.connect();    //start connection
                    mConnectedThread = new ConnectedThread(btSocket);
                    mConnectedThread.start();
                } catch (IOException e) {
                    ConnectSuccess = false;    //if the try failed, you can check the exception here
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                msg("Connected.");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }

    private class ConnectedThread extends Thread {
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
            int bytes; // bytes returned from read()
            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.available();
                    if (bytes != 0) {
                        byte[] buffer = new byte[1024];  // buffer store for the stream
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

        /* Call this from the main activity to send data to the remote device */
        public void write(char input) {
            try {
                mmOutStream.write(input);
            } catch (IOException e) {
            }
        }


        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
            }
        }
    }
}
