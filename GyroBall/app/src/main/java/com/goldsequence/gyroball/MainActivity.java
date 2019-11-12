package com.goldsequence.gyroball;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteOrder;
import java.text.NumberFormat;
import java.util.List;

import com.goldsequence.gyroball.R;

import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "MainActivity";
	private FrameLayout canvasFrame;
	private GLSurfaceView mGLSurfaceView;
	private CubeRenderer mCubeRenderer;
	private SensorManager mSensorManager;
	private List<Sensor> gyroSensors;
	private Sensor gyroSensor;
	private long timeOld;
	private int ignoreMaxCnt = 0;
    private final int ignoreMaxPeriod = 5;
    private float[] oldGyroValues = new float[3];
    private TextView rotateSpeed;
    private int sensorCnt = 0, cntPeriod = 40;
    private float eventX, eventY, eventPreviousX, eventPreviousY;
    
    private MediaPlayer[] mp = new MediaPlayer[3];

    private final int coreVerticesNumber = 29526;
    private final int shellVerticesNumber = 69309;
    private final int axisVerticesNumber = 438;
    private final int coreIndicesNumber = 69309;
    private final int shellIndicesNumber = 127470;
    private final int axisIndicesNumber = 432;
    
    private byte[] axisVerticesByte = new byte [axisVerticesNumber*4];
    private byte[] axisNormalsByte = new byte [axisVerticesNumber*4];
    private byte[] axisIndicesByte = new byte [axisIndicesNumber*2];
    
    private byte[] coreNormalsByte = new byte [coreVerticesNumber*4];
    private byte[] coreVerticesByte = new byte [coreVerticesNumber*4];
    private byte[] coreIndicesByte = new byte [coreIndicesNumber*2];
    
    private byte[] shellVerticesByte = new byte [shellVerticesNumber*4];
    private byte[] shellNormalsByte = new byte [shellVerticesNumber*4];
    private byte[] shellIndicesByte = new byte [shellIndicesNumber*2];
    private Button resetButton;
    
    private boolean loadingEnd = true;
    private int maxSelfRpm = 0;
    private final float dispScale = 2.0f; // Scale the display selfRpm value to make it look smaller
    
    NumberFormat nf = NumberFormat.getInstance();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Set application display orientation
	   WindowManager windowManager =  (WindowManager) getSystemService(WINDOW_SERVICE);
	
	   Configuration config = getResources().getConfiguration();
	
	   int rotation = windowManager.getDefaultDisplay().getRotation();
	
	   // Tablet and phone have different natural orientations. We use this method to set
	   // our app as portrait in phone and as landscape in tablet.
	   if (((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
	            config.orientation == Configuration.ORIENTATION_LANDSCAPE)
	        || ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&    
	            config.orientation == Configuration.ORIENTATION_PORTRAIT)) 
	   {
		   this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	   }
	   else 
	   {
	       this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	   }
		   
		setContentView(R.layout.activity_main);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // Keep screen on
		
		rotateSpeed = (TextView)findViewById(R.id.textView1);
        rotateSpeed.setTextColor(Color.GREEN);
        rotateSpeed.setText("Loading");
        
        Typeface tf = Typeface.createFromAsset(getAssets(), "fonts/DS-DIGI.TTF");
        rotateSpeed.setTypeface(tf);
		
        long time= System.currentTimeMillis();
        
        canvasFrame = (FrameLayout) MainActivity.this.findViewById(R.id.canvasframe);
        mGLSurfaceView = new GLSurfaceView(MainActivity.this);
        canvasFrame.addView(mGLSurfaceView);
        
		
        loadData();
		mCubeRenderer = new CubeRenderer(false, shellVerticesByte, shellNormalsByte, shellIndicesByte, coreVerticesByte, coreNormalsByte, coreIndicesByte, axisVerticesByte, axisNormalsByte, axisIndicesByte);
		mGLSurfaceView.setRenderer(mCubeRenderer);
        
        Log.i(TAG, "Loading data takes "+(System.currentTimeMillis()-time)/1000+"s");
		
		mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        
        gyroSensors = mSensorManager.getSensorList(Sensor.TYPE_GYROSCOPE);
        if(gyroSensors.size() > 0)
        {
            gyroSensor = gyroSensors.get(0);
        }
        else
        {
        	AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
    		builder.setMessage("The app can't work properly because your phone does not have gyroscope sensor!")       
    		.setCancelable(false)       
    		.setPositiveButton("OK", new DialogInterface.OnClickListener() {           
    			public void onClick(DialogInterface dialog, int id) {          
    			}       
    		});
    		AlertDialog alert = builder.create();
    		alert.show();
        }
        
        timeOld = 0;
        ignoreMaxCnt = 0;
        oldGyroValues[0] = 0.0f;
        oldGyroValues[1] = 0.0f;
        oldGyroValues[2] = 0.0f;
        
        nf.setMaximumIntegerDigits(5);
        nf.setMinimumIntegerDigits(5);
        nf.setGroupingUsed(false);
        
        resetButton = (Button) findViewById(R.id.resetButton);
        //resetButton.setBackgroundColor(Color.RED);
        resetButton.setTextColor(Color.WHITE);
        resetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        resetButton.setOnClickListener(new View.OnClickListener() 
        {

			@Override
			public void onClick(View arg0) {
				maxSelfRpm = 0;
			}
        });
        
	}
	
	private void loadData()
	{
		AssetManager assetManager = getAssets();
		
		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) // For Little-Endian processor, which is 99% of smart phones
		{
			DataInputStream dis;
			try {
				dis = new DataInputStream (assetManager.open("normal1_line.bin"));
				dis.read(coreVerticesByte, 0, coreVerticesNumber*4);
				dis.read(coreNormalsByte, 0, coreVerticesNumber*4);
				dis.read(coreIndicesByte, 0, coreIndicesNumber*2);
				dis.close();
				//Log.i(TAG, "coreNormalsByte has "+coreNormalsByte.length+" bytes");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			try {
				dis = new DataInputStream (assetManager.open("normal0_line.bin"));
				dis.read(shellVerticesByte, 0, shellVerticesNumber*4);
				dis.read(shellNormalsByte, 0, shellVerticesNumber*4);
				dis.read(shellIndicesByte, 0, shellIndicesNumber*2);
				dis.close();
				//Log.i(TAG, "coreNormalsByte has "+coreNormalsByte.length+" bytes");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			try {
				dis = new DataInputStream (assetManager.open("normal3_line.bin"));
				dis.read(axisVerticesByte, 0, axisVerticesNumber*4);
				dis.read(axisNormalsByte, 0, axisVerticesNumber*4);
				dis.read(axisIndicesByte, 0, axisIndicesNumber*2);
				dis.close();
				//Log.i(TAG, "coreNormalsByte has "+coreNormalsByte.length+" bytes");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
		else
		{
			DataInputStream dis;
			try {
				dis = new DataInputStream (assetManager.open("normal1_line_big.bin"));
				dis.read(coreVerticesByte, 0, coreVerticesNumber*4);
				dis.read(coreNormalsByte, 0, coreVerticesNumber*4);
				dis.read(coreIndicesByte, 0, coreIndicesNumber*2);
				dis.close();
				//Log.i(TAG, "coreNormalsByte has "+coreNormalsByte.length+" bytes");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			try {
				dis = new DataInputStream (assetManager.open("normal0_line_big.bin"));
				dis.read(shellVerticesByte, 0, shellVerticesNumber*4);
				dis.read(shellNormalsByte, 0, shellVerticesNumber*4);
				dis.read(shellIndicesByte, 0, shellIndicesNumber*2);
				dis.close();
				//Log.i(TAG, "coreNormalsByte has "+coreNormalsByte.length+" bytes");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			try {
				dis = new DataInputStream (assetManager.open("normal3_line_big.bin"));
				dis.read(axisVerticesByte, 0, axisVerticesNumber*4);
				dis.read(axisNormalsByte, 0, axisVerticesNumber*4);
				dis.read(axisIndicesByte, 0, axisIndicesNumber*2);
				dis.close();
				//Log.i(TAG, "coreNormalsByte has "+coreNormalsByte.length+" bytes");
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}
	
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch (item.getItemId()){
		
		case R.id.settings:
			Intent intentSet =
				new Intent(MainActivity.this, SettingTabActivity.class);
			startActivity(intentSet);
			break;
		
		default:
			Intent intentSetAbout =
				new Intent(MainActivity.this, HelpActivity.class);
			startActivity(intentSetAbout);
			break;
		}
		return false;
	}
	
	private final SensorEventListener mSensorListener = new SensorEventListener() {


		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			if (loadingEnd == true){
				switch (event.sensor.getType()) {
		         case Sensor.TYPE_GYROSCOPE:
		             event.values.clone();
		             long timeNow = System.currentTimeMillis();
		             if (timeOld > 0)
		             {
		            	 if (timeNow-timeOld > 100) ignoreMaxCnt = ignoreMaxPeriod; // if two samples are far apart, the data is not reliable
		            	 if (ignoreMaxCnt > 0) ignoreMaxCnt--;
		            	 if (ignoreMaxCnt == 0)
		            	 {
		            		 mCubeRenderer.speedChange(event.values[0]-oldGyroValues[0], event.values[1]-oldGyroValues[1]);
		            	 }
		             }
		             sensorCnt = (sensorCnt + 1) % cntPeriod;
	                 if (sensorCnt == 0)
	                 {
	                	 int currentRpm = Math.abs((int)(mCubeRenderer.selfRpm/dispScale));
	                	 if (maxSelfRpm < currentRpm)  maxSelfRpm = currentRpm;
	                	 if (maxSelfRpm > 99999)  maxSelfRpm = 99999;
	                	 if (currentRpm > 99999)  currentRpm = 99999;
	                	 String rpmNow = nf.format(currentRpm);
	                	 String rpmMax = nf.format(maxSelfRpm);
	                	 rotateSpeed.setText(rpmNow+"/"+rpmMax);
	                 }
		             timeOld = timeNow;
		             oldGyroValues[0] = event.values[0];
		             oldGyroValues[1] = event.values[1];
		             oldGyroValues[2] = event.values[2];
		             

		             // Sound changes gyro ball rotates at different speeds
		             if (sensorCnt == 0)
		             {
		            	 
		            	 if (mCubeRenderer.selfRpm > 10000)
		            	 {
		            		 if (mp[0].isPlaying() == true)  mp[0].pause();
		            		 if (mp[1].isPlaying() == true)  mp[1].pause();
		            		 mp[2].start();
		            	 }
		            	 else if (mCubeRenderer.selfRpm > 200)
		            	 {
		            		 if (mp[0].isPlaying() == true)  mp[0].pause();
		            		 if (mp[2].isPlaying() == true)  mp[2].pause();
		            		 mp[1].start();
		            	 }
		            	 //else if (mCubeRenderer.selfRpm > 1000)
		            	 //{
		            	///	 mp[0].start();
		            	 //}
		            	 else
		            	 {
		            		 if (mp[0].isPlaying() == true)  mp[0].pause();
		            		 if (mp[1].isPlaying() == true)  mp[1].pause();
		            		 if (mp[2].isPlaying() == true)  mp[2].pause();
		            	 }
		            	 
		             }
		             break;
		             
		         }  
			}
		}
    	
    };
    
    @Override
    protected void onResume()
    {
    	super.onResume();
        mSensorManager.registerListener(mSensorListener,
      		  	  gyroSensor,
                    SensorManager.SENSOR_DELAY_FASTEST);
        mp[0] = MediaPlayer.create(MainActivity.this, R.raw.rotateslow);
        mp[1] = MediaPlayer.create(MainActivity.this, R.raw.rotatemedium);
        mp[2] = MediaPlayer.create(MainActivity.this, R.raw.rotatefast);
        for(int i=0; i<3; i++){
        	mp[i].setLooping(true);
        }
        
     // Listen to preference
 	   SharedPreferences sp =
 		   PreferenceManager.getDefaultSharedPreferences(this);
        
		String viewAngleString = sp.getString("viewAngle", "1");
		    int viewAngleMethod = Integer.valueOf(viewAngleString.trim()).intValue();
		
		if  (viewAngleMethod == 1)
		{
			mCubeRenderer.viewAngle = 1;
		}
		else
		{
			mCubeRenderer.viewAngle = 2;
		}
    };
    
    @Override
    protected void onStop()
    {
    	super.onStop();
    	Log.i(TAG, "onStop()");
    	mSensorManager.unregisterListener(mSensorListener);
    	
    	for(int i=0; i<3; i++){
    		if (mp[i].isPlaying() == true)
	        {
	    		mp[i].stop();
	    		mp[i].release();
	    	}
    	}
    }

    
    public boolean onTouchEvent(MotionEvent event) {
        int eventaction = event.getAction();
        
        if (loadingEnd == true)
        {
	        switch (eventaction) {
	            case MotionEvent.ACTION_DOWN: 
	            	//pressDown = true;
	                break;
	
	            case MotionEvent.ACTION_MOVE:
	                // finger moves on the screen

	            	eventX = event.getX();
	                eventY = event.getY();
	                if ((eventX-eventPreviousX)*(eventX-eventPreviousX) + (eventY-eventPreviousY)*(eventY-eventPreviousY) < 200)
	                {
	                	if (mCubeRenderer.selfRpm > 10000)
	                	{
	                		mCubeRenderer.selfRpm = mCubeRenderer.selfRpm - 4*500;
	                	}
	                	else
	                	{
	                		mCubeRenderer.selfRpm = mCubeRenderer.selfRpm - 4*250;
	                	}
	     
	                	if (mCubeRenderer.selfRpm < 0)  mCubeRenderer.selfRpm = 0;
	                }
	                else
	                {
	                	if (mCubeRenderer.selfRpm < 5000)
	                	{
	                		mCubeRenderer.selfRpm = mCubeRenderer.selfRpm + 250;
	                	}
	                }
	                eventPreviousX = eventX;
	                eventPreviousY = eventY;
	                break;
	
	            case MotionEvent.ACTION_UP:   
	                // finger leaves the screen
	                break;
	        }
        }

        // tell the system that we handled the event and no further processing is required
        return true; 
    }

}