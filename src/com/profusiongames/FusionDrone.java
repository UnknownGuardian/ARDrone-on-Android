package com.profusiongames;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.webkit.WebChromeClient;
import android.webkit.JsResult;


import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;
import com.codeminders.ardrone.ARDrone;
import com.codeminders.ardrone.DroneVideoListener;
import com.codeminders.ardrone.NavData;
import com.codeminders.ardrone.NavDataListener;




public class FusionDrone extends Activity implements NavDataListener, DroneVideoListener, SensorEventListener {

	private static FusionDrone fDrone;
	private static ARDrone drone;
	private static SensorManager sensorManager;
	private static Sensor mCompass;
	
	private static boolean isConnected = false;
	private static boolean isFlying = false;
	
	//Sensor Data
	private int batteryLife = 0;	
	private float height = 0;
	private float pitch = 0;
	private float roll = 0;	
	private float yaw = 0;
	
	
	public static int queueToShow = 0;
	
	private double startX = -1f;
	private double startY = -1f;
	private double startZ = -1f;

	/* Components */
	@SuppressWarnings("unused")
	private TextView statusBar;
	private Button connectionStartButton;
	private ProgressBar connectionWhirlProgress;
	private Button launchButton;
	
	private ImageView videoDisplay;
	private Spinner flightfigure;
	private Spinner maneuvretime;
	
	//Instrument Panel
    private WebView mWebView;
    private Handler mHandler = new Handler();
	
	/* Components Joystick */

	DualJoystickView joystick;
	private int leftx, lefty;
	private int rightx, righty;
	

	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main3);
		fDrone = this;

		//Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

		//Getting Sensor Services for Control Device
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mCompass = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
		
        //Inituializing GUI
		getUIComponents();
		
	}
	
	
	

	
	
	
	
	
	
	
	
	@Override
	protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, mCompass, SensorManager.SENSOR_DELAY_NORMAL);
    }
	@Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }
	@Override
    protected void onStop() {
		super.onStop();
		sensorManager.unregisterListener(this);
		try {
			if(drone != null)
			{
				drone.clearImageListeners();
				drone.clearNavDataListeners();
				drone.clearStatusChangeListeners();
				drone.clearStatusChangeListeners();
				drone.disconnect();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}   
    }
	
	
	private void getUIComponents() {
		statusBar = (TextView) findViewById(R.id.statusBar);

		//Selecting one of the predefined flight maneuvre
		flightfigure = (Spinner) findViewById(R.id.spinner1);
		ArrayAdapter adapter = ArrayAdapter.createFromResource(
	            this, R.array.FliMan, android.R.layout.simple_spinner_item);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    flightfigure.setAdapter(adapter);
	    flightfigure.setSelection(6);
	    
	    
	    //Selecting the duration for the pre-configured flight maneuvre
	    maneuvretime = (Spinner) findViewById(R.id.spinner2);
		ArrayAdapter adapter1 = ArrayAdapter.createFromResource(
	            this, R.array.ManDur, android.R.layout.simple_spinner_item);
	    adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
	    maneuvretime.setAdapter(adapter1);
	    maneuvretime.setSelection(4);

	    
	    //Standard Button. These still have associated handler. The remaining elements use a common
	    //handler "MyClickHandler" to save resources
		connectionStartButton = (Button) findViewById(R.id.connectButton);
		connectionWhirlProgress = (ProgressBar) findViewById(R.id.progressBar1);
		connectionWhirlProgress.setVisibility(ProgressBar.INVISIBLE);
		connectionStartButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.v("DRONE", "Clicked Connection button");
				if (!isConnected) {

					connectionWhirlProgress.setVisibility(ProgressBar.VISIBLE); 
					connectionStartButton.setEnabled(false);
					connectionStartButton.setText("Connecting...");
					(new DroneStarter()).execute(FusionDrone.drone);
				} else {
					if(isFlying) { try { drone.land(); Thread.sleep(400);} catch (Exception e) {e.printStackTrace();}} //if going to disconnect, but still flying, attempt to tell drone to land
					connectionStartButton.setEnabled(false);
					connectionStartButton.setText("Disconnecting...");
					(new DroneEnder()).execute(FusionDrone.drone);
				}
			}
		});
		launchButton = (Button)findViewById(R.id.launchButton);
		launchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Log.v("DRONE", "Clicked Launch button");
				if (!isConnected) {
					//do nothing
				} else if(isFlying) {
					try { drone.land(); 
					      launchButton.setText("Takeoff"); 
					      isFlying = false;} 
					catch (IOException e) {e.printStackTrace();}
				} else	{
					try { drone.trim();
					      drone.takeOff(); 
					      launchButton.setText("Land"); 
					      isFlying = true;}
					catch (IOException e) {e.printStackTrace();}
				}
			}
		});

		//Displaying battery and height status
		//battery = (TextProgressBar) findViewById(R.id.battprogressBar);
		//battery.setText("Battery Level");
		//altitude = (Gauge) findViewById(R.id.meter2);
		mWebView = (WebView) findViewById(R.id.webView1);
		WebSettings webSettings = mWebView.getSettings();
        webSettings.setSavePassword(false);
        webSettings.setSaveFormData(false);
        webSettings.setJavaScriptEnabled(true);
        webSettings.setSupportZoom(false);
        
        mWebView.setWebChromeClient(new MyWebChromeClient());

        mWebView.addJavascriptInterface(new DemoJavaScriptInterface(), "demo");
        mWebView.loadUrl("file:///android_asset/Gauge.html");
		
		//Take care of the streaming video
		videoDisplay = (ImageView) findViewById(R.id.droneVideoDisplay);
		
		//Initialize the Soft-Joysticks
        joystick = (DualJoystickView)findViewById(R.id.dualjoystickView);
        joystick.setOnJostickMovedListener(_listenerLeft, _listenerRight);
             
        
    
	}
	
    final class DemoJavaScriptInterface {

        DemoJavaScriptInterface() {
        }

        /**
         * This is not called on the UI thread. Post a runnable to invoke
         * loadUrl on the UI thread.
         */
        public void clickOnAndroid() {
            mHandler.post(new Runnable() {
                public void run() {
                    mWebView.loadUrl("javascript:wave()");
                }
            });

        }
    }

    /**
     * Provides a hook for calling "alert" from javascript. Useful for
     * debugging your javascript.
     */
    final class MyWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
            result.confirm();
            return true;
        }
    }
    
	public void MyClickHandler(View v) {
  	  
   	   switch(v.getId())
   	   {
   	   case R.id.mayday:
   		  //We execute a builtin flight-operation
 
   		 if (!isConnected) {
		   		//do nothing
   		 } else if(isFlying) {
   		 try {
 
   		     drone.playAnimation(((int) flightfigure.getSelectedItemPosition()), ((((int) maneuvretime.getSelectedItemPosition())+1)*100));
   		     
   		 } 
			catch (IOException e) {e.printStackTrace();}
		   	}
   		 
	        break;
   	   case R.id.blinkButton:
  		  //Licht
   		   if (!isConnected) {
   			   		//do nothing
   		   } else if(isFlying) {
			try { 
				drone.playLED(1,10,2); //ARDroneLib/Soft/Common/led_animation.h (whole file, with details) for the LED animation
				drone.playLED(2,10,2); 
				drone.playLED(3,10,2); 
				drone.playLED(4,10,2); 
			} 
			catch (IOException e) {e.printStackTrace();}
   		   	}
	        break;
   	   case R.id.animateButton:
   		   //Video
			try {
				if(drone != null)
					drone.sendVideoOnData();

				} catch (IOException e) {
					e.printStackTrace();
				}
   	       break;
   	   

  	  }
   	    	 }

	
	
	public static ARDrone getARDrone() {
		return drone;
	}
	public static FusionDrone getFusionDrone() {
		return fDrone;
	}
	
	@Override
	public void navDataReceived(NavData nd) {
				
		batteryLife = nd.getBattery();
		height = nd.getAltitude()*1000; //Multiply to allow for Altimeter Usage
		pitch = nd.getPitch()*-1; 
		roll = nd.getRoll(); 
		yaw = nd.getYaw(); 
	    
		if (nd.isBatteryTooLow())
		{
			if(isFlying) { try { drone.land(); Thread.sleep(400);} catch (Exception e) {e.printStackTrace();}} //if going to disconnect, but still flying, attempt to tell drone to land
			connectionStartButton.setEnabled(false);
			connectionStartButton.setText("Disconnecting...");
			(new DroneEnder()).execute(FusionDrone.drone);
		}
		
		runOnUiThread(new Runnable() {
			public void run() {
				//Battery State
				mWebView.loadUrl("javascript:radial1.setValue("+String.valueOf(batteryLife)+")");
				//Altitude
				mWebView.loadUrl("javascript:radial2.setValue("+String.valueOf(height)+")");				
				//Orientation of Controller
				mWebView.loadUrl("javascript:radial3.setValue("+String.valueOf(startX)+")");
				//Yaw
				mWebView.loadUrl("javascript:radial5.setValue("+String.valueOf(yaw)+")");
			    //mWebView.loadUrl("javascript:radial4.setValue("+String.valueOf(height)+")");
				//Horizon (Pitch and Roll)
				mWebView.loadUrl("javascript:radial6.setPitchAnimated("+String.valueOf(pitch)+")");
				mWebView.loadUrl("javascript:radial6.setRollAnimated("+String.valueOf(roll)+")");

				
			}
		});
	}

	@Override
	public void frameReceived(final int startX, final int startY, final int w, final int h, final int[] rgbArray, final int offset, final int scansize) 
	{
		(new VideoDisplayer(startX, startY, w, h, rgbArray, offset, scansize)).execute();
	}


	
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		//Log.v("DRONE", "Accuracy changed: " + accuracy);
		
	}

	private float sensorThreshold = 3;
	@Override
	public void onSensorChanged(SensorEvent e) {
		
		//if(Math.random() < 1) return;
		//Log.v("DRONE", "sensor: " + e.sensor + ", x: " + MathUtil.trunk(e.values[0]) + ", y: " + MathUtil.trunk(e.values[1]) + ", z: " + MathUtil.trunk(e.values[2]));
		//if(startX == -1f) 
		//{
			startX = e.values[0];
			startY = e.values[1];
			startZ = e.values[2];
		//}
		float shortX = MathUtil.trunk(MathUtil.getShortestAngle(e.values[0],(float) startX));
		float shortY = MathUtil.trunk(MathUtil.getShortestAngle(e.values[1],(float) startY));
		float shortZ = MathUtil.trunk(MathUtil.getShortestAngle(e.values[2],(float) startZ));
		if( MathUtil.abs(shortX) <  sensorThreshold) shortX = 0;// do nothing
		if( MathUtil.abs(shortY) <  sensorThreshold) shortY = 0;// do nothing
		if( MathUtil.abs(shortZ) <  sensorThreshold) shortZ = 0;// do nothing
		//Log.v("DRONE", "sensor difference: x: " + shortX + ", y: " + shortY + ", z: " + shortZ);
	} 
	


    
    
    
    
    
    
    
	private class DroneStarter extends AsyncTask<ARDrone, Integer, Boolean> {
		private static final int CONNECTION_TIMEOUT = 3000;

		@Override
		protected Boolean doInBackground(ARDrone... drones) {
			ARDrone drone = drones[0];
			try {
				drone = new ARDrone();
				FusionDrone.drone = drone; // passing in null objects will not pass object refs
				drone.connect();
				drone.clearEmergencySignal();
				drone.waitForReady(CONNECTION_TIMEOUT);
				drone.playLED(1, 10, 4);
				drone.addNavDataListener(FusionDrone.fDrone);
				drone.addImageListener(FusionDrone.fDrone);
				drone.selectVideoChannel(ARDrone.VideoChannel.HORIZONTAL_ONLY);
				drone.setCombinedYawMode(true);
				try {
					//drone.sendTagDetectionOnData();
					drone.sendVideoOnData();
					//drone.enableAutomaticVideoBitrate();
				}
				catch(Exception e) { e.printStackTrace();}
				Log.v("DRONE", "Connected to ARDrone" + FusionDrone.drone);
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				try {
					drone.clearEmergencySignal();
					drone.clearImageListeners();
					drone.clearNavDataListeners();
					drone.clearStatusChangeListeners();
					drone.disconnect();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				Log.v("DRONE", "Caught exception. Connection time out." + FusionDrone.drone);
			}
			return false;
		}

		protected void onPostExecute(Boolean success) {
			if (success.booleanValue()) {
				connectionStartButton.setText("Disconnect...");
			} else {
				connectionStartButton.setText("Error 1. Retry?");
			}
			isConnected = success.booleanValue();
			connectionStartButton.setEnabled(true);
			connectionWhirlProgress.setVisibility(Button.INVISIBLE);
		}
	}
	
	
	
	
	
	
	
	
	

	private class DroneEnder extends AsyncTask<ARDrone, Integer, Boolean> {
		@Override
		protected Boolean doInBackground(ARDrone... drones) {
			ARDrone drone = drones[0];
			try {
				drone.playLED(2, 10, 4);
				drone.clearEmergencySignal();
				drone.clearImageListeners();
				drone.clearNavDataListeners();
				drone.clearStatusChangeListeners();
				drone.disconnect();
				Log.v("DRONE", "Disconnected to ARDrone" + FusionDrone.drone);
				mWebView.loadUrl("javascript:radial1.setValue("+String.valueOf(0)+")");
				mWebView.loadUrl("javascript:radial2.setValue("+String.valueOf(0)+")");				
				mWebView.loadUrl("javascript:radial3.setValue("+String.valueOf(0)+")");
				mWebView.loadUrl("javascript:radial4.setValue("+String.valueOf(0)+")");
			    mWebView.loadUrl("javascript:radial5.setValue("+String.valueOf(0)+")");
				mWebView.loadUrl("javascript:radial6.setPitchAnimated("+String.valueOf(0)+")");
				mWebView.loadUrl("javascript:radial6.setRollAnimated("+String.valueOf(0)+")");
				
				return true;
			} catch (Exception e) {
				e.printStackTrace();
				try {
					drone.clearEmergencySignal();
					drone.clearImageListeners();
					drone.clearNavDataListeners();
					drone.clearStatusChangeListeners();
					drone.disconnect();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
				Log.v("DRONE", "Caught exception. Disconnection error.");
			}
			return false;
		}

		protected void onPostExecute(Boolean success) {
			if (success.booleanValue()) {
				connectionStartButton.setText("Connect...");
				connectionStartButton.setEnabled(true);
				//launchButton.setVisibility(Button.INVISIBLE);
				
			} else {
				connectionStartButton.setText("Error 2. Retry?");
			}
			isConnected = !success.booleanValue();

			connectionWhirlProgress.setVisibility(ProgressBar.INVISIBLE);// invisible
			
		}
	}

	
	
	
	
	
	
	
	
	
	private class VideoDisplayer extends AsyncTask<Void, Integer, Void> {
		
		public Bitmap b;
		public int[]rgbArray;
		public int offset;
		public int scansize;
		public int w;
		public int h;
		public VideoDisplayer(int x, int y, int width, int height, int[] arr, int off, int scan) {
	        super();
	        // do stuff
	        rgbArray = arr;
	        offset = off;
	        scansize = scan;
	        w = width;
	        h = height;
	        
	    }

		
		@Override
		protected Void doInBackground(Void... params) {

			b =  Bitmap.createBitmap(rgbArray, offset, scansize, w, h, Bitmap.Config.RGB_565);
			b.setDensity(100); //needed?
			return null;
		}
		@Override
		protected void onPostExecute(Void param) {;
			//Log.v("Drone Control", "THe system memory is : " + Runtime.getRuntime().freeMemory());
			
			((BitmapDrawable)videoDisplay.getDrawable()).getBitmap().recycle(); 
			//videoDisplay.setImageDrawable(new BitmapDrawable(b));
	
			videoDisplay.setImageBitmap(b);
			
			FusionDrone.queueToShow--;
			//Log.v("Drone Control", "Queue = " + FusionDrone.queueToShow);
		}
	}

	
	
	
	
//Joystick Control and send of move-commands. Be careful the calculation of values was
//taken from the Java-Drone project and is meant for a PS3 Controller. This was adapted
//to the softjoystick!!! Currently it works, but use at your own risk!
	private void CalculateMove()
	{
		//The drone expects floats between -1.0 and 1.0
		float left_right_tilt = 0f;
        float front_back_tilt = 0f;
        float vertical_speed = 0f;
        float angular_speed = 0f;

        if(leftx != 0)
        {
            left_right_tilt = ((float) leftx) / 10f;
        }

        if(lefty != 0)
        {
            front_back_tilt = ((float) lefty) / 10f;
        }

        if(rightx != 0)
        {
            angular_speed = ((float) rightx) / 10f;
        }

        if(righty != 0)
        {
            vertical_speed = ((float) righty) / 10f;
        }

        if(leftx != 0 || lefty != 0 || rightx != 0 || righty != 0)
        {
        	if (!isConnected) 
        	{
        		//do nothing
        	}
        	else if(isFlying) 
        	{
        		try {drone.move(left_right_tilt, front_back_tilt, vertical_speed, angular_speed);}
        		catch (IOException e) {e.printStackTrace();}
   	        }			
        }    
        else
        {
        	if (!isConnected) 
        	{
        		//do nothing
        	}
        	else if(isFlying) 
        	{
        		try {drone.hover();}
        		catch (IOException e) {e.printStackTrace();}
   	        }			
        }	
           
    }

	
	
	//The left joystick controls movements in the x-dimension
    private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

		@Override
		public void OnMoved(int pan, int tilt) 
		{
			leftx = pan;
			lefty = tilt;
			
			CalculateMove();
		}

		@Override
		public void OnReleased() {
			
			if (!isConnected) {
		   		//do nothing
	       } else if(isFlying) {
		        try { 
		        	drone.hover();		} 
		catch (IOException e) {e.printStackTrace();}
	   	}			
			
			

		}
		
		public void OnReturnedToCenter() {
			leftx=0;
			lefty=0;
			CalculateMove();
		};
	}; 

	//the right joystick controls movement in the y and z dimension
    private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

		@Override
		public void OnMoved(int pan, int tilt) 
		{
			righty = tilt*-1;
			rightx = pan;
			
			CalculateMove();
		}

		@Override
		public void OnReleased() {
        //do Nothing
		}
		
		public void OnReturnedToCenter() {
			rightx=0;
			righty=0;
			CalculateMove();
		};
	}; 

	//Taking Care of the Config Menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.configmenu, menu);
	    return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	        
	        //Manual Trim and standard configuration                    
	        case R.id.item2:     if (!isConnected) {//do nothing
	        					 } else if(!isFlying) {
	        						   try { 
	        							    drone.trim();
	        		                        drone.setConfigOption("control:altitude_max", "2000");
	        		                        drone.setConfigOption("control:altitude_min", "500");
	        		                        drone.setConfigOption("control:euler_angle_max", "0.2");
	        		                        drone.setConfigOption("control:control_vz_max", "2000.0");
	        		                        drone.setConfigOption("control:control_yaw", "2.0");						   	
	        						       } 
	        						   catch (IOException e) {e.printStackTrace();}
	        					 }		 
	                             break;
	        //Preparing for Outdoor Flight                     
	        case R.id.item3:     Toast.makeText(this, "Flying Outdoor", Toast.LENGTH_LONG).show();
	        					 break;
	        					//Submenu with predefined choices regarding max. flightlevel
	        case R.id.item4:    if (!isConnected) {//do nothing
								 } else if(!isFlying) {
									   try { 
										   drone.setConfigOption("control:altitude_max", "1000");
									       } 
									   catch (IOException e) {e.printStackTrace();}
								 }		
	                             break;
	        case R.id.item5:     if (!isConnected) {//do nothing
								 } else if(!isFlying) {
									   try { 
										   drone.setConfigOption("control:altitude_max", "1250");
									       } 
									   catch (IOException e) {e.printStackTrace();}
								 }	
					            break;
	        case R.id.item6:     if (!isConnected) {//do nothing
								 } else if(!isFlying) {
									   try { 
										   drone.setConfigOption("control:altitude_max", "1500");
									       } 
									   catch (IOException e) {e.printStackTrace();}
								 }	
					            break;
	        case R.id.item7:    if (!isConnected) {//do nothing
								 } else if(!isFlying) {
									   try { 
										   drone.setConfigOption("control:altitude_max", "2000");
									       } 
									   catch (IOException e) {e.printStackTrace();}
								 }	
            break;
            //Video-Channel
	        case R.id.item9:    if (!isConnected) {//do nothing
								 } else if(!isFlying) {
									   try { 
										   drone.setConfigOption("video:video_channel", "0");
									       } 
									   catch (IOException e) {e.printStackTrace();}
								 }	
	        					 break;
	        case R.id.item10:    if (!isConnected) {//do nothing
			 } else if(!isFlying) {
				   try { 
					   drone.setConfigOption("video:video_channel", "1");
				       } 
				   catch (IOException e) {e.printStackTrace();}
			 }	
            break;
	        case R.id.item11:    if (!isConnected) {//do nothing
			 } else if(!isFlying) {
				   try { 
					   drone.setConfigOption("video:video_channel", "2");
				       } 
				   catch (IOException e) {e.printStackTrace();}
			 }	
           break;
	       case R.id.item12:    if (!isConnected) {//do nothing
			 } else if(!isFlying) {
				   try { 
					   drone.setConfigOption("video:video_channel", "3");
				       } 
				   catch (IOException e) {e.printStackTrace();}
			 }	
           break;
	    }
	    return true;
	}
}