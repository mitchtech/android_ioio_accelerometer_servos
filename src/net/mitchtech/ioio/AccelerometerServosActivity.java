package net.mitchtech.ioio;

import ioio.lib.api.PwmOutput;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.AbstractIOIOActivity;
import net.mitchtech.ioio.accelerometerservos.R;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

public class AccelerometerServosActivity extends AbstractIOIOActivity implements
		SensorEventListener {
	private final int TILT_PIN = 3;
	private final int PAN_PIN = 6;

	private final int PWM_FREQ = 100;

	private int mXValue = 500;
	private int mYValue = 500;
	/* sensor data */
	SensorManager m_sensorManager;
	float[] m_lastMagFields;
	float[] m_lastAccels;
	private float[] m_rotationMatrix = new float[16];
	//private float[] m_remappedR = new float[16];
	private float[] m_orientation = new float[4];

	/* fix random noise by averaging tilt values */
	final static int AVERAGE_BUFFER = 30;
	float[] m_prevPitch = new float[AVERAGE_BUFFER];
	float m_lastPitch = 0.f;
	float m_lastYaw = 0.f;
	/* current index int m_prevEasts */
	int m_pitchIndex = 0;

	float[] m_prevRoll = new float[AVERAGE_BUFFER];
	float m_lastRoll = 0.f;
	/* current index into m_prevTilts */
	int m_rollIndex = 0;

	/* center of the rotation */
//	private float m_tiltCentreX = 0.f;
//	private float m_tiltCentreY = 0.f;
//	private float m_tiltCentreZ = 0.f;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		m_sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		registerListeners();
	}

	private void registerListeners() {
		m_sensorManager.registerListener(this,
				m_sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
				SensorManager.SENSOR_DELAY_GAME);
		m_sensorManager.registerListener(this,
				m_sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_GAME);
	}

	private void unregisterListeners() {
		m_sensorManager.unregisterListener(this);
	}

	@Override
	public void onDestroy() {
		unregisterListeners();
		super.onDestroy();
	}

	@Override
	public void onPause() {
		unregisterListeners();
		super.onPause();
	}

	@Override
	public void onResume() {
		registerListeners();
		super.onResume();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			accel(event);
		}
		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
			mag(event);
		}
	}

	private void accel(SensorEvent event) {
		if (m_lastAccels == null) {
			m_lastAccels = new float[3];
		}

		System.arraycopy(event.values, 0, m_lastAccels, 0, 3);

		/*
		 * if (m_lastMagFields != null) { computeOrientation(); }
		 */
	}

	private void mag(SensorEvent event) {
		if (m_lastMagFields == null) {
			m_lastMagFields = new float[3];
		}

		System.arraycopy(event.values, 0, m_lastMagFields, 0, 3);

		if (m_lastAccels != null) {
			computeOrientation();
		}
	}

	Filter[] m_filters = { new Filter(), new Filter(), new Filter() };

	private class Filter {
		static final int AVERAGE_BUFFER = 10;
		float[] m_arr = new float[AVERAGE_BUFFER];
		int m_idx = 0;

		public float append(float val) {
			m_arr[m_idx] = val;
			m_idx++;
			if (m_idx == AVERAGE_BUFFER)
				m_idx = 0;
			return avg();
		}

		public float avg() {
			float sum = 0;
			for (float x : m_arr)
				sum += x;
			return sum / AVERAGE_BUFFER;
		}

	}

	private void computeOrientation() {
		if (SensorManager.getRotationMatrix(m_rotationMatrix, null, m_lastMagFields, m_lastAccels)) {
			SensorManager.getOrientation(m_rotationMatrix, m_orientation);

			/* 1 radian = 57.2957795 degrees */
			/*
			 * [0] : yaw, rotation around z axis [1] : pitch, rotation around x
			 * axis [2] : roll, rotation around y axis
			 */
			float yaw = m_orientation[0] * 57.2957795f;
			float pitch = m_orientation[1] * 57.2957795f;
			float roll = m_orientation[2] * 57.2957795f;

			m_lastYaw = m_filters[0].append(yaw);
			m_lastPitch = m_filters[1].append(pitch);
			m_lastRoll = m_filters[2].append(roll);
			TextView rt = (TextView) findViewById(R.id.roll);
			TextView pt = (TextView) findViewById(R.id.pitch);
			TextView yt = (TextView) findViewById(R.id.yaw);

			int servo1 = 50;
			if (m_lastRoll >= 0) {
				servo1 = (int) (50 + (180 - m_lastRoll));
			} else {
				// servo1 = (int) (50 - (180 + m_lastRoll));
				servo1 = 0;
			}

			int servo2 = 50;
			if (m_lastYaw > 100) {
				servo2 = 100;
			} else if (m_lastYaw >= 0) {
				servo2 = (int) m_lastYaw;
			} else {
				servo2 = 0;
			}

			rt.setText("roll y: " + m_lastRoll + " servo1: " + servo1);
			pt.setText("pitch x: " + m_lastPitch);
			yt.setText("azi z: " + m_lastYaw + " servo2: " + servo2);

			mXValue = servo1 * 10;
			mYValue = servo2 * 10;
		}
	}

	class IOIOThread extends AbstractIOIOActivity.IOIOThread {
		private PwmOutput panPwmOutput;
		private PwmOutput tiltPwmOutput;

		public void setup() throws ConnectionLostException {
			try {
				panPwmOutput = ioio_.openPwmOutput(PAN_PIN, PWM_FREQ);
				tiltPwmOutput = ioio_.openPwmOutput(TILT_PIN, PWM_FREQ);
			} catch (ConnectionLostException e) {
				throw e;
			}
		}

		public void loop() throws ConnectionLostException {
			try {
				panPwmOutput.setPulseWidth(500 + mXValue * 2);
				tiltPwmOutput.setPulseWidth(500 + mYValue * 2);
				sleep(10);
			} catch (InterruptedException e) {
				ioio_.disconnect();
			} catch (ConnectionLostException e) {
				throw e;
			}
		}
	}

	@Override
	protected AbstractIOIOActivity.IOIOThread createIOIOThread() {
		return new IOIOThread();
	}

}