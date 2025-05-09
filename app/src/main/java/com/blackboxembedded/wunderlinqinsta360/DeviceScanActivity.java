/*
WunderLINQ Client Application
Copyright (C) 2020  Keith Conger, Black Box Embedded, LLC

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
package com.blackboxembedded.wunderlinqinsta360;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.location.LocationManagerCompat;

import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.arashivision.sdkcamera.camera.InstaCameraManager;
import com.arashivision.sdkcamera.camera.callback.IScanBleListener;
import com.clj.fastble.data.BleDevice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeviceScanActivity extends AppCompatActivity implements IScanBleListener {
    public final static String TAG = "DeviceScanActivity";
    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_BLUETOOTH_CONNECT = 100;
    private static final int PERMISSION_REQUEST_BLUETOOTH_SCAN = 101;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 102;
    private static final int SETTINGS_CHECK = 10;

    private ListView listView;

    private int lastPosition = 0;
    private int highlightColor;

    private CountDownTimer cTimer = null;
    private boolean timerRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Enable Debug Logging
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPrefs.getBoolean("prefDebugLogging", false)) {
            File outputFile = new File(getApplicationContext().getExternalFilesDir(null), "wunderlinq-insta360.log");
            // Check if the file size is larger than 20MB (20 * 1024 * 1024 bytes).
            if (outputFile.exists() && outputFile.length() > 20 * 1024 * 1024) {
                // Delete the file.
                if (outputFile.delete()) {
                    Log.d(TAG, "File deleted successfully.");
                } else {
                    Log.e(TAG, "Failed to delete file.");
                }
            } else {
                Log.d(TAG, "File not over 20Mb");
            }


            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.d(TAG, "Starting Log File: " + outputFile.getAbsolutePath());
                        Process process = Runtime.getRuntime().exec("logcat -f " + outputFile.getAbsolutePath());

                        // Wait for the process to complete.
                        int exitCode = process.waitFor();

                        // Do some more work after the process completes.
                        if (exitCode != 0) {
                            // Handle error.
                            Log.d(TAG, "ERROR exitCode: " + exitCode);
                        } else {
                            Log.d(TAG, "NO ERROR exitCode: " + exitCode);
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        setContentView(R.layout.device_scan_activity);
        listView = findViewById(R.id.listview);
        listView.setClickable(true);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                SoundManager.playSound(DeviceScanActivity.this, R.raw.enter);
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                        (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
                    final BleDevice device = mLeDeviceListAdapter.getDevice(position);
                    if (device == null) return;
                    InstaCameraManager.getInstance().stopBleScan();
                    InstaCameraManager.getInstance().connectBle(device);
                    final Intent intent = new Intent(DeviceScanActivity.this, DeviceControlActivity.class);
                    intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device);
                    startActivity(intent);
                }
            }
        });

        highlightColor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getInt("prefHighlightColor", getResources().getColor(R.color.colorAccent));

        getSupportActionBar().setTitle(R.string.cameralist_title);

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkSystem();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
            if (!mBluetoothAdapter.isEnabled()) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            }

            // Initializes list view adapter.
            mLeDeviceListAdapter = new LeDeviceListAdapter();
            listView.setAdapter(mLeDeviceListAdapter);
            InstaCameraManager.getInstance().setScanBleListener(this);
            InstaCameraManager.getInstance().startBleScan();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == SETTINGS_CHECK) {
            highlightColor = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this).getInt("prefHighlightColor", getResources().getColor(R.color.colorAccent));
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (cTimer != null){
            cancelTimer();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_settings:
                //Launch Settings
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivityForResult(settingsIntent, SETTINGS_CHECK);
                return true;
            case R.id.action_about:
                //Launch About Screen
                Intent aboutIntent = new Intent(this, AboutActivity.class);
                startActivity(aboutIntent);
                return true;
            case R.id.action_exit:
                //Close App
                finishAffinity();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                SoundManager.playSound(this, R.raw.directional);
                if (listView.getSelectedItemPosition() == 0 && lastPosition == 0){
                    listView.setSelection(listView.getCount() - 1);
                }
                lastPosition = listView.getSelectedItemPosition();
                mLeDeviceListAdapter.notifyDataSetChanged();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                SoundManager.playSound(this, R.raw.directional);
                if ((listView.getSelectedItemPosition() == (listView.getCount() - 1)) && lastPosition == (listView.getCount() - 1) ){
                    listView.setSelection(0);
                }
                lastPosition = listView.getSelectedItemPosition();
                mLeDeviceListAdapter.notifyDataSetChanged();
                return true;
            case KeyEvent.KEYCODE_ESCAPE:
                SoundManager.playSound(this, R.raw.enter);
                String callingApp = "wunderlinq://datagrid";
                Intent intent = new
                        Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(Uri.parse(callingApp));
                startActivity(intent);
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isFinishing()) {
            InstaCameraManager.getInstance().setScanBleListener(null);
            InstaCameraManager.getInstance().stopBleScan();
        }
    }

    @Override
    public void onScanStartSuccess() {

    }

    @Override
    public void onScanStartFail() {

    }

    @Override
    public void onScanning(BleDevice bleDevice) {
        mLeDeviceListAdapter.addDevice(bleDevice);
        mLeDeviceListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onScanFinish(List<BleDevice> bleDeviceList) {
        mLeDeviceListAdapter.updateData(bleDeviceList);
    }

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private ArrayList<BleDevice> mLeDevices;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BleDevice>();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        public void addDevice(BleDevice device) {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                    (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
                if (!mLeDevices.contains(device)) {
                    Log.d(TAG,"Found Camera: " + device.getDevice().getName());
                    mLeDevices.add(device);
                }
            }
        }

        private void updateData(List<BleDevice> list) {
            mLeDevices.clear();
            mLeDevices.addAll(list);
            notifyDataSetChanged();
        }

        public BleDevice getDevice(int position) {

            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                    (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
                BleDevice device = mLeDevices.get(i);
                final String deviceName = device.getName();
                if (deviceName != null && deviceName.length() > 0)
                    viewHolder.deviceName.setText(deviceName);
                else
                    viewHolder.deviceName.setText(R.string.unknown_device);
            }

            if (i == lastPosition) {
                view.setBackgroundColor(highlightColor);
            }

            return view;
        }
    }

    static class ViewHolder {
        TextView deviceName;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_REQUEST_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "PERMISSION_REQUEST_FINE_LOCATION permission granted");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_REQUEST_BLUETOOTH_SCAN);
                        }
                    }
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.negative_location_alert_title));
                    builder.setMessage(getString(R.string.negative_location_alert_body));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(DeviceScanActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_REQUEST_BLUETOOTH_SCAN);
                                }
                            }
                        }
                    });
                    builder.show();
                }
                break;
            }
            case PERMISSION_REQUEST_BLUETOOTH_SCAN: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "PERMISSION_REQUEST_BLUETOOTH_SCAN permission granted");
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_BLUETOOTH_CONNECT);
                        }
                    }
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.negative_btscan_alert_title));
                    builder.setMessage(getString(R.string.negative_btscan_alert_body));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(DeviceScanActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_REQUEST_BLUETOOTH_CONNECT);
                                }
                            }
                        }
                    });
                    builder.show();
                }
                break;
            }
            case PERMISSION_REQUEST_BLUETOOTH_CONNECT: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "PERMISSION_REQUEST_BLUETOOTH_CONNECT permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.negative_btconnect_alert_title));
                    builder.setMessage(getString(R.string.negative_btconnect_alert_body));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                break;
            }
        }
    }

    // Check Required System Services and Permissions
    void checkSystem() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
            if (isLocationEnabled(this)) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(getString(R.string.location_request_alert_title));
                    builder.setMessage(getString(R.string.location_request_alert_body));
                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(DeviceScanActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
                        }
                    });
                    builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    builder.show();
                }
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.location_services_alert_title));
                builder.setMessage(getString(R.string.location_services_alert_body));
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        startActivity(intent);
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                builder.show();
            }
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.bt_request_alert_title));
                builder.setMessage(getString(R.string.bt_request_alert_body));
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(DeviceScanActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_REQUEST_BLUETOOTH_SCAN);
                    }
                });
                builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
                builder.show();
            }
        }
    }

    //start timer function
    void startTimer() {
        if(!timerRunning) {
            cTimer = new CountDownTimer(10000, 1000) {
                public void onTick(long millisUntilFinished) {
                }

                public void onFinish() {
                    InstaCameraManager.getInstance().startBleScan();
                    timerRunning = false;
                }
            };
            timerRunning = true;
            cTimer.start();
        }
    }

    //cancel timer
    void cancelTimer() {
        if(cTimer!=null)
            cTimer.cancel();
    }

    private boolean isLocationEnabled(Context context) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return LocationManagerCompat.isLocationEnabled(locationManager);
    }

    private static boolean isBluetoothEnabled(Context context) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }
}