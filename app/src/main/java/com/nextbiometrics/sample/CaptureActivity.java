package com.nextbiometrics.sample;

import java.nio.IntBuffer;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Environment;
import java.io.*;

import com.nextbiometrics.devices.NBDeviceImageQualityAlgorithm;
import com.nextbiometrics.devices.NBDevice;
import com.nextbiometrics.devices.NBDeviceScanFormatInfo;
import com.nextbiometrics.devices.NBDeviceScanResult;
import com.nextbiometrics.devices.NBDeviceScanStatus;
import com.nextbiometrics.devices.event.NBDeviceScanPreviewEvent;
import com.nextbiometrics.devices.event.NBDeviceScanPreviewListener;

public class CaptureActivity extends Activity implements OnClickListener {
    
    private TextView            log;
    private TextView            imageQuality;
    private ImageView           fingerImage;
    private Button              scanSnapshotBtn;
    private Button              scanBtn;
    private Button              statusBtn;
    private ScanTask            scanTask;
    
    private NBDevice            device;
    private boolean             back = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        
        scanSnapshotBtn = (Button) findViewById(R.id.btn_scan_snapshot);
        scanBtn = (Button) findViewById(R.id.btn_scan);
        statusBtn = (Button) findViewById(R.id.btn_getstatus);
        
        fingerImage = (ImageView) findViewById(R.id.finger_image);
        log = (TextView) findViewById(R.id.device_log);
        imageQuality = (TextView) findViewById(R.id.image_quality);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        setDevice(getDevice());
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (device != null && device.isScanRunning())
            device.cancelScan();
        if (scanTask != null) {
            scanTask.cancel(true);
        }
        setDevice(null);
    }
    
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (device != null) {
            if (id == R.id.btn_scan_snapshot) {
                startCapture(ScanType.SNAPSHOT);
            } else if (id == R.id.btn_scan) {
                startCapture(ScanType.ONE_FINGERPRINT);
            } else if (id == R.id.btn_getstatus) {
                getStatus();
            }
        }
    }
    
    @Override
    public void onBackPressed()
    {
        super.onBackPressed();
        if (device != null && device.isScanRunning())
            device.cancelScan();
        if (scanTask != null) {
            scanTask.cancel(true);
        }
        setDevice(null);
        back = true;
    }
    
    private void setDevice(NBDevice device) {
        if (device == null && this.device != null) {
            this.device.dispose();
            this.device = null;
            Log.d("TEST", "TEST DEVICE : "+device);
        } else {
            this.device = device;
        }
        this.device = device;
        enableButtons(device != null);
        log.setText(device != null ? getString(R.string.scan_start) : getString(R.string.device_not_connected));
        fingerImage.setImageResource(R.drawable.scan_process_initial);
        
        if(device != null && device.getCapabilities().requiresExternalCalibrationData) {
            final String paths = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/NBData/" + device.getSerialNumber() + "_calblob.bin";
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
        }
    }
    
    private NBDevice getDevice() {
        Intent intent = this.getIntent();
        Bundle bundle = intent.getExtras();
        DeviceInfo deviceInfo = (DeviceInfo)bundle.getSerializable("value");
        return DeviceInfo.getDevice(deviceInfo);
    }
    
    private void enableButtons(boolean en) {
        scanBtn.setEnabled(en);
        scanSnapshotBtn.setEnabled(en);
        statusBtn.setEnabled(en);
    }
    
    private void startCapture(ScanType scanType) {
        scanTask = new ScanTask(scanType);
        scanTask.execute(device.getSupportedScanFormats()[0]);
    }
    
    private static Bitmap convertToBitmap(NBDeviceScanFormatInfo formatInfo, byte[] image){
        IntBuffer buf = IntBuffer.allocate(image.length);
        for (byte pixel : image) {
            int grey = pixel & 0x0ff;
            buf.put(Color.argb(255, grey, grey, grey));
        }
        return Bitmap.createBitmap(buf.array(), formatInfo.getWidth(), formatInfo.getHeight(), Config.ARGB_8888);
    }
    
    private void cancelCapture() {
        new AsyncTask<Void, Void, String>() {
            
            @Override
            protected void onPreExecute() {
                enableButtons(false);
            }
            
            @Override
            protected String doInBackground(Void... params) {
                try {
                    device.cancelScan();
                    return null;
                }
                catch (RuntimeException e) {
                    e.printStackTrace();
                    return e.getMessage();
                }
            }
            
            @Override
            protected void onPostExecute(String msg) {
                enableButtons(true);
                if (msg != null && !"".equals(msg)) {
                    log.setText(msg);
                }
            }
        }.execute();
    }
    
    private void getStatus() {
        new AsyncTask<Void, Void, String>() {
            
            @Override
            protected void onPreExecute() {
                enableButtons(false);
            }
            
            @Override
            protected String doInBackground(Void... params) {
                try {
                    return device.getState().toString();
                }
                catch (RuntimeException e) {
                    e.printStackTrace();
                    return e.getMessage();
                }
            }
            
            @Override
            protected void onPostExecute(String msg) {
                enableButtons(true);
                log.setText(getString(R.string.device_state));
                log.append(" ");
                log.append(msg);
            }
        }.execute();
    }
    
    private enum ScanType {
        SNAPSHOT,
        ONE_FINGERPRINT
    }
    
    private class ScanProgress {
        private String message;
        private Bitmap image;
        private int qualityScore;
        
        ScanProgress(NBDeviceScanResult result, int fingerprintDetectValue) {
            this(result.getStatus(), fingerprintDetectValue, result.getFormat(), result.getImage());
        }
        
        ScanProgress(NBDeviceScanPreviewEvent eventDetails) {
            this(eventDetails.getStatus(), eventDetails.getFingerDetectValue(), eventDetails.getFormat(), eventDetails.getImage());
        }
        
        ScanProgress(NBDeviceScanStatus status, int fingerprintDetectValue, NBDeviceScanFormatInfo formatInfo, byte[] image) {
            this(String.format("%s %s, %s %d", getString(R.string.scan_status), status, getString(R.string.finger_detect_value), fingerprintDetectValue),
              NBDevice.GetImageQuality(image, formatInfo.getWidth(), formatInfo.getHeight(), formatInfo.getHorizontalResolution(), NBDeviceImageQualityAlgorithm.NFIQ),
              convertToBitmap(formatInfo, image));
        }
        
        ScanProgress(String message, int qualityScore, Bitmap image) {
            this.message = message;
            this.image = image;
            this.qualityScore = qualityScore;
        }
        
        ScanProgress(String message) {
            this(message, 0, null);
        }
        
        String getMessage() {
            return message;
        }
        
        Bitmap getImage() {
            return image;
        }
        
        int getQualityScore() {
            return qualityScore;
        }
    }
    
    private class ScanTask extends AsyncTask<NBDeviceScanFormatInfo, ScanProgress, ScanProgress> implements NBDeviceScanPreviewListener {
        private ScanType scanType;
        
        ScanTask(ScanType scanType) {
            this.scanType = scanType;
        }
        
        @Override
        protected void onPreExecute() {
            enableButtons(false);
            log.setText(R.string.scan_in_progress);
            fingerImage.setImageResource(R.drawable.scan_process_initial);
        }
        
        @Override
        protected ScanProgress doInBackground(NBDeviceScanFormatInfo... params) {
            try {
                // Ensure enough priority for the scanning thread to prevent long capture time
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
                
                NBDeviceScanFormatInfo format = params[0];
                if (scanType == ScanType.SNAPSHOT)
                    return new ScanProgress(device.scan(format), device.getFingerDetectValue());
                return new ScanProgress(device.scanEx(format, 1000000, this), device.getFingerDetectValue());
            }
            catch (Throwable e) {
                e.printStackTrace();
                publishProgress(new ScanProgress(String.format("%s %s", getString(R.string.scan_failed), e.getMessage())));
                return null;
            }
        }
        
        @Override
        protected void onProgressUpdate(ScanProgress... msg) {
            ScanProgress progress = msg[0];
            updateView(progress);
        }
        
        @Override
        protected void onPostExecute(ScanProgress fp) {
            enableButtons(true);
            if (fp != null) {
                updateView(fp);
            }
            else {
                fingerImage.setImageResource(R.drawable.scan_process_fail);
            }
        }
        
        @Override
        public void preview(NBDeviceScanPreviewEvent event) {
            publishProgress(new ScanProgress(event));
        }
        
        private void updateView(ScanProgress progress) {
            fingerImage.setImageBitmap(progress.getImage());
            log.setText(progress.getMessage());
            imageQuality.setText("Image quality: " + String.valueOf(progress.getQualityScore()));
        }
    }
}
