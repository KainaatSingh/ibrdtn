package de.tubs.ibr.dtn.dtalkie;

import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import de.tubs.ibr.dtn.dtalkie.service.RecorderService;
import de.tubs.ibr.dtn.dtalkie.service.Utils;

public class RecordingFragment extends Fragment {

    @SuppressWarnings("unused")
    private static final String TAG = "RecordingFragment";
    
    private ImageButton mRecordButton = null;
    private FrameLayout mRecIndicator = null;
    
    private Boolean mRecording = false;
    
    private float mAnimScaleHeight = 1.0f;
    
    private SensorEventListener mSensorListener = new SensorEventListener() {
        public void onSensorChanged(SensorEvent event) {
            if (event.values.length > 0) {
                float current = event.values[0];
                float maxRange = event.sensor.getMaximumRange();
                boolean far = (current == maxRange);
                
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                if (prefs.getBoolean("sensor", false)) 
                {
                    if (far) {
                        stopRecording();
                    } else {
                        startRecording();
                    }
                }
            }
        }

        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    
    private OnTouchListener mTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch ( event.getAction() ) {
                case MotionEvent.ACTION_DOWN:
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                    if (prefs.getBoolean("ptt", false)) {
                        startRecording();
                    }
                    break;
                    
                case MotionEvent.ACTION_UP:
                    toggleRecording();
                    break;
            }
            
            return true;
        }
    };
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.recording_fragment, container, false);
        
        mRecordButton = (ImageButton) v.findViewById(R.id.button_record);
        mRecordButton.setOnTouchListener(mTouchListener);
        
        mRecIndicator = (FrameLayout) v.findViewById(R.id.ptt_background);
        
        return v;
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        // set volume control to VOICE_CALL
        getActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
    }

    @Override
    public void onPause() {
        SensorManager sm = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        sm.unregisterListener(mSensorListener);

        // we are going out of scope - stop recording
        stopRecording();
        
        // unregister from recorder events
        getActivity().unregisterReceiver(mRecorderEventReceiver);
        
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

    	IntentFilter filter = new IntentFilter();
    	filter.addAction(RecorderService.EVENT_RECORDING_EVENT);
    	filter.addAction(RecorderService.EVENT_RECORDING_INDICATOR);
    	getActivity().registerReceiver(mRecorderEventReceiver, filter);
        
        SensorManager sm = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_PROXIMITY);
        if (sensors.size() > 0) {
            Sensor sensor = sensors.get(0);
            sm.registerListener(mSensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }
    
    private void startRecording() {
        if (mRecording) return;
        mRecording = true;
        
        // lock screen orientation
        Utils.lockScreenOrientation(getActivity());
        
        // get recording preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        boolean indicator = !prefs.getBoolean("ptt", false);
        boolean auto_stop = !prefs.getBoolean("ptt", false);

        // start recording
        RecorderService.startRecording(getActivity(), RecorderService.TALKIE_GROUP_EID, indicator, auto_stop);
    }
    
    private void stopRecording() {
        if (!mRecording) return;
        mRecording = false;
        
        // unlock screen orientation
        Utils.unlockScreenOrientation(getActivity());
        
        // stop recording
        RecorderService.stopRecording(getActivity());
    }
    
    private void toggleRecording() {
        if (mRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }
    
    private BroadcastReceiver mRecorderEventReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (RecorderService.EVENT_RECORDING_EVENT.equals(intent.getAction())) {
				String action = intent.getStringExtra(RecorderService.EXTRA_RECORDING_ACTION);
				
				if (RecorderService.ACTION_START_RECORDING.equals(action)) {
			        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
			        
			        // show full indicator when dynamic indicator is disabled
			        if (prefs.getBoolean("ptt", false)) {
			        	setIndicator(1.0f, 0);
			        } else {
			        	setIndicator(0.0f, 0);
			        }
				}
				else if (RecorderService.ACTION_STOP_RECORDING.equals(action)) {
			        // set indicator level to zero
			        setIndicator(0.0f, 0);
			        
			        mRecording = false;
			        
			        // unlock screen orientation
			        Utils.unlockScreenOrientation(getActivity());
				}
				else if (RecorderService.ACTION_ABORT_RECORDING.equals(action)) {
			        // set indicator level to zero
			        setIndicator(0.0f, 0);
			        
			        mRecording = false;
			        
			        // unlock screen orientation
			        Utils.unlockScreenOrientation(getActivity());
				}
			}
			else if (RecorderService.EVENT_RECORDING_INDICATOR.equals(intent.getAction())) {
				// read indicator value
				float level = intent.getFloatExtra(RecorderService.EXTRA_LEVEL_NORMALIZED, 0.0f);
				
		        // set indicator level
		        setIndicator(level);
			}
		}
    	
    };
    
    private void setIndicator(Float level) {
    	setIndicator(level, 100);
    }
    
    private void setIndicator(Float level, int duration) {
        Animation a = new ScaleAnimation(1.0f, 1.0f, 1 - mAnimScaleHeight, 1 - level);
        
        mAnimScaleHeight = level;

        a.setDuration(duration);
        a.setInterpolator(new LinearInterpolator());
        a.startNow();
        mRecIndicator.startAnimation(a);
    }
}
