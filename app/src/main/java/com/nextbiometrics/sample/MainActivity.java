package com.nextbiometrics.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.os.Environment;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Toast;
import android.app.ProgressDialog;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.*;

import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDeviceScanFormatInfo;
import com.nextbiometrics.devices.NBDevices;
import com.nextbiometrics.devices.NBDevicesLibrary;
import com.nextbiometrics.devices.NBDeviceSecurityModel;
import com.nextbiometrics.devices.event.NBDevicesDeviceChangedEvent;
import com.nextbiometrics.devices.event.NBDevicesDeviceChangedListener;
import com.nextbiometrics.system.NBVersion;
import com.nextbiometrics.devices.NBDeviceCapabilities;

public class MainActivity extends Activity implements OnClickListener, NBDevicesDeviceChangedListener {
    
    //z91
    private static final String DEFAULT_SPI_NAME = "/dev/arafp0";
    private static final int PIN_OFFSET  = 343;
    private static final int AWAKE_PIN_NUMBER = PIN_OFFSET + 14;
    private static final int RESET_PIN_NUMBER = PIN_OFFSET + 13;
    private static final int CHIP_SELECT_PIN_NUMBER = PIN_OFFSET + 31;
    
    private TextView                    log;
    private Button                      testBtn;
    private Spinner                     commandSpinner;
    private ArrayAdapter<CharSequence>  spinnerAdapter;
    private TestCommand                 testCommandTask;
    private ProgressDialog              progressDialog;
    
    private DeviceInfo                  deviceInfo;
    private NBDevice                    device;
    private AtomicBoolean               calibrating;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        calibrating = new AtomicBoolean(false);
        testBtn = (Button) findViewById(R.id.btn_test);
        log = (TextView) findViewById(R.id.device_log);
        commandSpinner = (Spinner) findViewById(R.id.cmd_spinner);
        spinnerAdapter = ArrayAdapter.createFromResource(this, R.array.sensor_cmd_list,
          android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        commandSpinner.setAdapter(spinnerAdapter);
        commandSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
                updateButtons();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parentView) {
                testBtn.setEnabled(false);
            }
        });
        
        try {
            setLog(null);
            NBDevices.initialize(this, this);
        }
        catch (Throwable tw) {
            log.setText(tw.toString());
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (testCommandTask != null) {
            testCommandTask.cancel(true);
        }
        setDevice(null, null);
        if (NBDevices.isInitialized())
            NBDevices.terminate();
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        this.deviceInfo = null;
        updateDevice();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateDevice();
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (testCommandTask != null) {
            testCommandTask.cancel(true);
        }
    }
    
    @Override
    public void onClick(View v) {
        int id = v.getId();
        
        if (id == R.id.btn_test) {
            CharSequence item = spinnerAdapter.getItem(commandSpinner.getSelectedItemPosition());
            if (item != null) {
                String selectedCmd = item.toString();
                if (device != null && selectedCmd.equals("SCAN")) {
                    Intent intent = new Intent(this, CaptureActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("value", deviceInfo);
                    //device.closeSession(); // TODO close session properly
                    device.dispose();
                    intent.putExtras(bundle);
                    startActivity(intent);
                } else {
                    testCommandTask = new TestCommand();
                    testCommandTask.execute(selectedCmd);
                }
            }
        }
    }
    
    private void updateDevice() {
        setDevice(null, null);
        NBDevice device = null;
        DeviceInfo deviceInfo = this.deviceInfo;
        if (deviceInfo != null) {
            device = DeviceInfo.getDevice(deviceInfo);
        } else {
            device = DeviceInfo.getDevice(null);
        }
        if (device != null) {
            if (deviceInfo != null && deviceInfo.getId() != null && deviceInfo.getId().equals(device.getId())) {
                deviceInfo = new DeviceInfo(device.getId(), deviceInfo.isSpi(), deviceInfo.getSpiName(),
                  deviceInfo.getAwakePin(), deviceInfo.getResetPin(), deviceInfo.getChipSelectPin());
            } else {
                deviceInfo = new DeviceInfo(device.getId());
            }
        } else {
            deviceInfo = null;
        }
        
        setDevice(deviceInfo, device);
        updateButtons();
    }
    
    private void setLog(String text) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.welcome_message)).append("\n");
        NBVersion version = NBDevicesLibrary.getVersion();
        builder.append(getString(R.string.library_version)).append(version).append("\n\n");
        builder.append(text).append("\n\n");
        builder.append(getString(R.string.welcome_message_continue));
        log.setText(builder.toString());
    }
    
    private String getDeviceStatus() {
        final StringBuilder builder = new StringBuilder();
        if (device == null) {
            builder.append(getString(R.string.device_not_connected)).append("\n");
        } else {
            builder.append(getString(R.string.device_id)).append(device.getId()).append("\n");
            builder.append(getString(R.string.device_manufacturer)).append(device.getManufacturer()).append("\n");
            builder.append(getString(R.string.device_model)).append(device.getModel()).append("\n");
            builder.append(getString(R.string.device_serialnumber)).append(device.getSerialNumber()).append("\n");
            if(device.getModuleSerialNumber().compareTo(device.getSerialNumber()) != 0)
            {
                builder.append(getString(R.string.module_serialnumber)).append(device.getModuleSerialNumber()).append("\n");
            }
            builder.append(getString(R.string.device_product)).append(device.getProduct()).append("\n");
            builder.append(getString(R.string.device_firmware_version)).append(device.getFirmwareVersion()).append("\n");
            builder.append(getString(R.string.device_type)).append(device.getType().toString()).append("\n");
            builder.append(getString(R.string.device_connection_type)).append(device.getConnectionType().toString()).append("\n");
            builder.append(getString(R.string.device_state)).append(device.getState()).append("\n");
            builder.append(getString(R.string.device_supported_formats)).append("\n");
            for (NBDeviceScanFormatInfo format : device.getSupportedScanFormats()) {
                builder.append(String.format("\t%s\n", format));
            }
            
            NBDeviceCapabilities capabilities = device.getCapabilities();
            if(capabilities.requiresExternalCalibrationData) {
                String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/" + device.getSerialNumber() + "_calblob.bin";
                builder.append("Device compensation data:" + path).append("\n");
                File file = new File(path);
                
                if(!file.exists()) {
                    builder.append("Device compensation data missing!");
                }
            }
        }
        return builder.toString();
    }
    
    private void setDevice(DeviceInfo deviceInfo, final NBDevice device) {
        if (this.deviceInfo == null || !this.deviceInfo.isSpi())
            this.deviceInfo = deviceInfo;
        if (device == null && this.device != null) {
            this.device.dispose();
            this.device = null;
        } else {
            this.device = device;
        }
        
        if(device != null && !device.isSessionOpen()) {
            byte[] cakId = "DefaultCAKKey1\0".getBytes();
            byte[] cak = {
              (byte)0x05, (byte)0x4B, (byte)0x38, (byte)0x3A, (byte)0xCF, (byte)0x5B, (byte)0xB8, (byte)0x01, (byte)0xDC, (byte)0xBB, (byte)0x85, (byte)0xB4, (byte)0x47, (byte)0xFF, (byte)0xF0, (byte)0x79,
              (byte)0x77, (byte)0x90, (byte)0x90, (byte)0x81, (byte)0x51, (byte)0x42, (byte)0xC1, (byte)0xBF, (byte)0xF6, (byte)0xD1, (byte)0x66, (byte)0x65, (byte)0x0A, (byte)0x66, (byte)0x34, (byte)0x11
            };
            byte[] cdkId = "Application Lock\0".getBytes();
            byte[] cdk = {
              (byte)0x6B, (byte)0xC5, (byte)0x51, (byte)0xD1, (byte)0x12, (byte)0xF7, (byte)0xE3, (byte)0x42, (byte)0xBD, (byte)0xDC, (byte)0xFB, (byte)0x5D, (byte)0x79, (byte)0x4E, (byte)0x5A, (byte)0xD6,
              (byte)0x54, (byte)0xD1, (byte)0xC9, (byte)0x90, (byte)0x28, (byte)0x05, (byte)0xCF, (byte)0x5E, (byte)0x4C, (byte)0x83, (byte)0x63, (byte)0xFB, (byte)0xC2, (byte)0x3C, (byte)0xF6, (byte)0xAB
            };
            byte[] defaultAuthKey1Id = "AUTH1\0".getBytes();
            byte[] defaultAuthKey1 = {
              (byte)0xDA, (byte)0x2E, (byte)0x35, (byte)0xB6, (byte)0xCB, (byte)0x96, (byte)0x2B, (byte)0x5F, (byte)0x9F, (byte)0x34, (byte)0x1F, (byte)0xD1, (byte)0x47, (byte)0x41, (byte)0xA0, (byte)0x4D,
              (byte)0xA4, (byte)0x09, (byte)0xCE, (byte)0xE8, (byte)0x35, (byte)0x48, (byte)0x3C, (byte)0x60, (byte)0xFB, (byte)0x13, (byte)0x91, (byte)0xE0, (byte)0x9E, (byte)0x95, (byte)0xB2, (byte)0x7F
            };
            NBDeviceSecurityModel security = NBDeviceSecurityModel.get(device.getCapabilities().securityModel);
            if(security == NBDeviceSecurityModel.Model65200CakOnly) {
                device.openSession(cakId, cak);
            }
            else if(security == NBDeviceSecurityModel.Model65200CakCdk) {
                try {
                    device.openSession(cdkId, cdk);
                    device.SetBlobParameter(NBDevice.BLOB_PARAMETER_SET_CDK, null);
                    device.closeSession();
                }
                catch (RuntimeException ex) {
                }
                device.openSession(cakId, cak);
                device.SetBlobParameter(NBDevice.BLOB_PARAMETER_SET_CDK, cdk);
                device.closeSession();
                device.openSession(cdkId, cdk);
            }
            else if(security == NBDeviceSecurityModel.Model65100) {
                device.openSession(defaultAuthKey1Id, defaultAuthKey1);
            }
        }
        
        if(device != null && device.getCapabilities().requiresExternalCalibrationData) {
            final String paths = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/" + device.getSerialNumber() + "_calblob.bin";
            new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/").mkdirs();
            File file = new File(paths);
            if(file.exists()) {
                int size = (int) file.length();
                byte[] bytes = new byte[size];
                try {
                    BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                }
                catch (IOException ex) {}
                device.SetBlobParameter(NBDevice.BLOB_PARAMETER_CALIBRATION_DATA, bytes);
            }
            else if(calibrating.compareAndSet(false, true)) {
                runOnUiThread(new Runnable() { public void run() {
                    final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                    alert.setTitle("Calibration data");
                    alert.setMessage("Device is not calibrated, do you want to create calibration data file in the following location?\nSensor must be clean!\nThis operation may take several minutes." + paths);
                    
                    alert.setPositiveButton("Ok",
                      new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int whichButton) {
                              progressDialog = new ProgressDialog(MainActivity.this);
                              progressDialog.setMessage("Generating calibration data...");
                              progressDialog.setTitle("Creation of calibration data file:");
                              progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                              progressDialog.setCancelable(false);
                              progressDialog.setCanceledOnTouchOutside(false);
                              progressDialog.show();
                              
                              new Thread() {
                                  public void run() {
                                      try {
                                          byte[] data = MainActivity.this.device.GenerateCalibrationData();
                                          FileOutputStream fos = new FileOutputStream(paths);
                                          fos.write(data);
                                          fos.close();
                                          setDevice(MainActivity.this.deviceInfo, MainActivity.this.device);
                                          updateButtons();
                                          progressDialog.dismiss();
                                          calibrating.set(false);
                                          runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, "Calibration data created", Toast.LENGTH_LONG).show(); } });
                                      }
                                      catch(final Exception e) {
                                          runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_LONG).show(); } });
                                      }
                                  }
                              }.start();
                              runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, "Creating calibration data, please wait ...", Toast.LENGTH_LONG).show(); } });
                          }
                      }
                    );
                    
                    alert.setNegativeButton("Cancel",
                      new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int whichButton) {
                              calibrating.set(false);
                              runOnUiThread(new Runnable() { public void run() { Toast.makeText(MainActivity.this, "Device compensation data is missing!", Toast.LENGTH_LONG).show(); } });
                          }
                      }
                    );
                    alert.setCancelable(false);
                    
                    alert.show();
                } });
            }
        }
    }
    
    private void updateButtons() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CharSequence item = spinnerAdapter.getItem(commandSpinner.getSelectedItemPosition());
                if (item != null) {
                    String name = item.toString();
                    if ("CONNECT_TO_SPI".equals(name)) {
                        testBtn.setEnabled(true);
                    }
                    else {
                        testBtn.setEnabled(device != null);
                    }
                }
                else {
                    testBtn.setEnabled(device != null);
                }
                setLog(getDeviceStatus());
            }
        });
    }
    
    @Override
    public void added(NBDevicesDeviceChangedEvent event) {
        NBDevice device = event.getDevice();
        setDevice(new DeviceInfo(device.getId()), device);
        updateButtons();
    }
    
    @Override
    public void removed(NBDevicesDeviceChangedEvent event) {
        setDevice(null, null);
        updateButtons();
    }
    
    private class TestCommand extends AsyncTask<String, String, Boolean> {
        
        @Override
        protected void onPreExecute() {
            testBtn.setEnabled(false);
            commandSpinner.setEnabled(false);
            super.onPreExecute();
        }
        
        @Override
        protected Boolean doInBackground(String... params) {
            try {
                switch (params[0]) {
                    case "CONNECT_TO_SPI":
                        if (deviceInfo != null && deviceInfo.isSpi()) setDevice(null, null);
                        DeviceInfo newDeviceInfo = new DeviceInfo(DEFAULT_SPI_NAME, AWAKE_PIN_NUMBER, RESET_PIN_NUMBER, CHIP_SELECT_PIN_NUMBER);
                        NBDevice newDevice = DeviceInfo.getDevice(newDeviceInfo);
                        setDevice(newDeviceInfo, newDevice);
                        updateButtons();
                        return true;
                    case "GET_STATUS":
                        publishProgress(getDeviceStatus());
                        return true;
                    case "GET_FINGER_DETECT_VALUE":
                        if (device != null) {
                            int detectValue = device.getFingerDetectValue();
                            publishProgress(String.format("%s%d", getString(R.string.finger_detect_value), detectValue));
                        }
                        return true;
                    case "SOFT_RESET":
                        if (device != null) {
                            device.reset();
                            publishProgress(getString(R.string.device_has_been_reset));
                        }
                        return true;
                    default:
                        publishProgress(getString(R.string.unknown_command));
                        return true;
                }
            }
            catch (Throwable e) {
                e.printStackTrace();
                publishProgress(e.getMessage());
            }
            return null;
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            setLog(values[0]);
            super.onProgressUpdate(values);
        }
        
        @Override
        protected void onPostExecute(Boolean result) {
            testBtn.setEnabled(true);
            commandSpinner.setEnabled(true);
            super.onPostExecute(result);
        }
    }
}
