package android.wickham.com.activitytracker;

/*
 * Copyright (C) 2018 Mark Wickham
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import weka.classifiers.Classifier;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

public class MainActivity extends Activity {

    // Status Thread
    Thread m_statusThread;
    boolean m_StatusThreadStop;
    private static Integer	updateInterval = 500;

    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGyroscope, mMagnetometer;

    private TextView tv_acc_X,tv_acc_Y,tv_acc_Z;
    private TextView tv_gyro_X,tv_gyro_Y,tv_gyro_Z;
    private TextView tv_mag_X,tv_mag_Y,tv_mag_Z;
    private TextView classifyResult, modelSummary;

    private float acc_X, acc_Y, acc_Z;
    private float gyro_X, gyro_Y, gyro_Z;
    private float mag_X, mag_Y, mag_Z;

    // Create the object for the classifier and dataset structure
    private Instances dataSingle;
    private Classifier rf;

    private static String[] activityID;

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(mSensorEventListener, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(mSensorEventListener, mMagnetometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        //mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        mSensorManager.registerListener(mSensorEventListener, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        ConverterUtils.DataSource sourceSingle;

        StringBuilder builder = new StringBuilder();

        modelSummary = (TextView) findViewById(R.id.model_summary);
        classifyResult = (TextView) findViewById(R.id.classify_result);

        activityID = new String[] {
                "Other (transient)",    // 0
                "Lying",                // 1    y
                "Sitting",              // 2    y
                "Standing",             // 3    y
                "Walking",              // 4    y
                "Running",              // 5    y
                "Cycling",              // 6    y
                "Nordic Walking",       // 7    y
                //"Unknown",              // 8
                //"Watching TV",          // 9
                //"Computer Work",        // 10
                //"Car Driving",          // 11
                "Ascending Stairs",     // 12   y
                "Descending Stairs",    // 13   y
                "Vacuum cleaning",      // 16   y
                "Ironing",              // 17   y
                //"Folding Laundry",      // 18
                //"House Cleaning",       // 19
                //"Playing soccer",       // 20
                //"Unknown",              // 21
                //"Unknown",              // 22
                //"Unknown",              // 23
                "Rope jumping",          // 24   y
                "x","x","x","x","x","x","x","x","x","x"
        };

        tv_acc_X = (TextView)findViewById(R.id.Acc_X_Axis);
        tv_acc_Y = (TextView)findViewById(R.id.Acc_Y_Axis);
        tv_acc_Z = (TextView)findViewById(R.id.Acc_Z_Axis);

        tv_gyro_X = (TextView)findViewById(R.id.Gyro_X_Axis);
        tv_gyro_Y = (TextView)findViewById(R.id.Gyro_Y_Axis);
        tv_gyro_Z = (TextView)findViewById(R.id.Gyro_Z_Axis);

        tv_mag_X = (TextView)findViewById(R.id.Mag_X_Axis);
        tv_mag_Y = (TextView)findViewById(R.id.Mag_Y_Axis);
        tv_mag_Z = (TextView)findViewById(R.id.Mag_Z_Axis);

        try {
            // Load the Test data
            builder.append(getCurrentTimeStamp() + ": Loading single data source");
            sourceSingle = new ConverterUtils.DataSource(getResources().openRawResource(R.raw.subject101_single));
            dataSingle = sourceSingle.getDataSet();
            // Set the class attribute (Label) as the first class
            dataSingle.setClassIndex(0);
            builder.append("\n" + getCurrentTimeStamp() + ": Single data instance load complete");

            // Load the pre-built Random Forest model
            builder.append("\n" + getCurrentTimeStamp() + ": Loading model");

            rf = (Classifier) weka.core.SerializationHelper.read(getResources().openRawResource(R.raw.rf_i10_cross));
            builder.append("\n" + getCurrentTimeStamp() + ": Model load complete");
            //Toast.makeText(this, "Model loaded.", Toast.LENGTH_SHORT).show();

            // Show the results
            modelSummary.setText((CharSequence) builder.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Start the thread
        createAndRunStatusThread(this);
    }

    public void createAndRunStatusThread(final Activity act) {
        m_StatusThreadStop=false;
        m_statusThread = new Thread(new Runnable() {
            public void run() {
                while(!m_StatusThreadStop) {
                    try {
                        act.runOnUiThread(new Runnable() {
                            public void run() {
                                updateActivityStatus();
                            }
                        });
                        Thread.sleep(updateInterval);
                    }
                    catch(InterruptedException e) {
                        m_StatusThreadStop = true;
                        messageBox(act, "Exception in status thread: " + e.toString() + " - " + e.getMessage(), "createAndRunStatusThread Error");
                    }
                }
            }
        });
        m_statusThread.start();
    }

    private void updateActivityStatus() {
        //Toast.makeText(MainActivity.this, "Button pressed.", Toast.LENGTH_SHORT).show();
        // Grab the most recent values and classify them
        // Create a new Instance to classify
        Instance newInst = new DenseInstance(10);
        newInst.setDataset(dataSingle);
        newInst.setValue(0,0);   // ActivityID
        newInst.setValue(1,acc_X);  // Accelerometer X
        newInst.setValue(2,acc_Y);  // Accelerometer Y
        newInst.setValue(3,acc_Z);  // Accelerometer Z
        newInst.setValue(4,gyro_X); // Gyroscope X
        newInst.setValue(5,gyro_Y); // Gyroscope Y
        newInst.setValue(6,gyro_Z); // Gyroscope Z
        newInst.setValue(7,mag_X);  // Magnetometer X
        newInst.setValue(8,mag_Y);  // Magnetometer Y
        newInst.setValue(9,mag_Z);  // Magnetometer Z

        // Classify the instance
        try {
            double res = rf.classifyInstance(newInst);
            //classifyResult.setText("Result = " + String.valueOf(res));
            classifyResult.setText(activityID[(int) res] + ", " + String.valueOf(res));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getCurrentTimeStamp() {
        return new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
    }

    /**
     * Listener that handles sensor events
     */
    private final SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            //if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                acc_X = event.values[0];
                acc_Y = event.values[1];
                acc_Z = event.values[2];
                tv_acc_X.setText(Float.toString(acc_X));
                tv_acc_Y.setText(Float.toString(acc_Y));
                tv_acc_Z.setText(Float.toString(acc_Z));

            } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                gyro_X = event.values[0];
                gyro_Y = event.values[1];
                gyro_Z = event.values[2];
                tv_gyro_X.setText(Float.toString(gyro_X));
                tv_gyro_Y.setText(Float.toString(gyro_Y));
                tv_gyro_Z.setText(Float.toString(gyro_Z));

            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                mag_X = event.values[0];
                mag_Y = event.values[1];
                mag_Z = event.values[2];
                tv_mag_X.setText(Float.toString(mag_X));
                tv_mag_Y.setText(Float.toString(mag_Y));
                tv_mag_Z.setText(Float.toString(mag_Z));
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {
        }
    };

    public void messageBox(final Context context, final String message, final String title) {
        this.runOnUiThread(
                new Runnable() {
                    public void run() {
                        final AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                        alertDialog.setTitle(title);
                        alertDialog.setIcon(android.R.drawable.stat_sys_warning);
                        alertDialog.setMessage(message);
                        alertDialog.setCancelable(false);
                        alertDialog.setButton("Back", new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int which) {
                                alertDialog.cancel();
                            }
                        });
                        alertDialog.show();
                    }
                }
        );
    }
}
