// Written by Nan Zhang
// Released in 05/2012 with LGPL license

// This file is modified based on Google sample file. We also used Madgwick's code for Quaternion filtering but
// with modification.


//=====================================================================================================
// IMU.c
// S.O.H. Madgwick
// 25th September 2010
//=====================================================================================================
// Description:
//
// Quaternion implementation of the 'DCM filter' [Mayhony et al].
//
// User must define 'halfT' as the (sample period / 2), and the filter gains 'Kp' and 'Ki'.
//
// Global variables 'q0', 'q1', 'q2', 'q3' are the quaternion elements representing the estimated
// orientation.  See my report for an overview of the use of quaternions in this application.
//
// User must call 'IMUupdate()' every sample period and parse calibrated gyroscope ('gx', 'gy', 'gz')
// and accelerometer ('ax', 'ay', 'ay') data.  Gyroscope units are radians/second, accelerometer 
// units are irrelevant as the vector is normalised.
//
//=====================================================================================================


package com.goldsequence.gyroball;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.util.Log;

/**
 * Render a pair of tumbling cubes.
 */

class CubeRenderer implements GLSurfaceView.Renderer {
	
	private final float PI = 3.1416f;
    private boolean mTranslucentBackground;
    //private Cube mCube;
    //private Square mSquare;
    private Axis[] mAxis = new Axis[3];
    static private float angleX, angleY, angleZ; // in degrees
    private float angleXTemp, angleYTemp, angleZTemp; // in degrees
    private float currentV[] = {0f,0f,0f};
    private float filtCoef = 0.9f;

    //---------------------------------------------------------------------------------------------------
    // Variable definitions

    static float q0 = 1, q1 = 0, q2 = 0, q3 = 0;	// Quaternion elements representing the estimated orientation
    static float exInt = 0, eyInt = 0, ezInt = 0;	// scaled integral error
    private final float Kp = 2.0f;			// proportional gain governs rate of convergence to accelerometer/magnetometer
    private final float  Ki = 0.005f;		// integral gain governs rate of convergence of gyroscope biases
    private final float  halfT = 0.5f; 		// half the sample period
    
    public float selfRpm = 0; // Round per min
    private float orbitRpm = 0; // Round per min // based on the measurement, selfRpm ~= 40*orbitRpm
    private long timeOld = 0;
    private float orbitAngleOld = 0.0f;
    private float selfAngleOld = 0.0f;
	private boolean firstTime = true;
	private final float orbitSelfRotationRatio = 40.0f;
	private final float frictionLoss = 10.0f; // orbitRpm loses 10 per sec
	public int viewAngle = 1; // Viewing angle of the ball

    
    private static boolean logOn = false;
    private static boolean androidLog = false;
    static PrintWriter mCurrentFile; 
    
    
    private static final String TAG = "CubeRenderer";

	
	public CubeRenderer(boolean useTranslucentBackground, byte[] shellVertices, byte[] shellNormals, byte[] shellIndices
			, byte[] coreVertices, byte[] coreNormals, byte[] coreIndices
			, byte[] axisVertices, byte[] axisNormals, byte[] axisIndices) {
        mTranslucentBackground = useTranslucentBackground;

        float shellColor[] = {0.004479f, 0.004479f, 0.541935f, 0.75f};
        float axisColor[] = {0.640000f, 0.640000f, 0.640000f, 1f};
        float coreColor[] = {0.593549f, 0.774194f, 0.000000f, 1f};
        //float coreColor[] = {1f, 0f, 0.000000f, 1f};
        mAxis[0] = new Axis(shellVertices, shellNormals, shellIndices, shellColor);
        mAxis[1] = new Axis(coreVertices, coreNormals, coreIndices, coreColor);
        mAxis[2] = new Axis(axisVertices, axisNormals, axisIndices, axisColor);
        
    }

    public void onDrawFrame(GL10 gl) {
    	float theta, aNorm;
    	int deltaT;
    	long tCurrent;
    	float orbitAngleDelta, selfAngleDelta, orbitRpmLoss;
        /*
         * Usually, the first thing one might want to do is to clear
         * the screen. The most efficient way of doing this is to use
         * glClear().
         */

        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        /*
         * Now we're ready to draw some 3D objects
         */

        gl.glEnable(gl.GL_BLEND); // To add transparency
        gl.glBlendFunc(gl.GL_SRC_ALPHA, gl.GL_ONE_MINUS_SRC_ALPHA);
        //gl.glBlendFunc(gl.GL_ONE_MINUS_DST_ALPHA,gl.GL_DST_ALPHA);
        
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        
        // Add lighting
        gl.glEnable(GL10.GL_LIGHTING);
        
        // Turn the first light on
        gl.glEnable(GL10.GL_LIGHT0);
        
        gl.glEnable(GL10.GL_COLOR_MATERIAL);
        
        float mat_shininess[] = {96.078431f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_SHININESS, mat_shininess, 0);
        
        
        
     // Define the ambient component of the first light
        float[] light0Ambient = {0.4f, 0.4f, 0.4f, 1.0f};
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_AMBIENT, light0Ambient, 0);
        
        // Define the diffuse component of the first light
        float[] light0Diffuse = {1f, 1f, 1f, 1.0f};
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_DIFFUSE, light0Diffuse, 0);
        
     // Define the specular component and shininess of the first light
        float[] light0Specular = {1f, 1f, 1f, 1.0f};
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_SPECULAR, light0Specular, 0);	
        
         // Define the position of the first light
        float light0Position[] = {0.0f, 2.5f, -1.25f, 1.0f}; 
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_POSITION, light0Position, 0); 
        
        // Define a direction vector for the light, this one points right down the Z axis
        float[] light0Direction = {0.0f, -1.0f, 0.5f};
        gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_SPOT_DIRECTION, light0Direction, 0);
        
        gl.glLightf(GL10.GL_LIGHT0, GL10.GL_SPOT_CUTOFF, 30.0f);

        
        gl.glLoadIdentity();
        if (viewAngle == 1)	gl.glRotatef(30, 1f, 0, 0);
        
        
        
        // Animation parts start from here
        if (firstTime == true)
        {
        	timeOld = System.currentTimeMillis();
        	firstTime = false;
        }
        else
        {
        	tCurrent = System.currentTimeMillis();
        	deltaT = (int) (tCurrent - timeOld);
        	orbitAngleDelta = 1.0f*deltaT/1000/60*orbitRpm*360;
        	orbitAngleOld += orbitAngleDelta;
        	selfAngleDelta = 1.0f*deltaT/1000/60*selfRpm*360;
        	selfAngleOld += selfAngleDelta;

	        gl.glRotatef(orbitAngleOld, 0, 0, 1.0f);

	        gl.glRotatef(selfAngleOld, 1f, 0, 0);

	        
	        // friction reduces speed
	        orbitRpmLoss = 1.0f * deltaT * frictionLoss / 1000;
	        if (orbitRpm > 0)
	        {
	        	if (orbitRpm > orbitRpmLoss)
	        	{
	        		orbitRpm -= orbitRpmLoss;
	        	}
	        	else
	        	{
	        		orbitRpm = 0;
	        	}
	        }
	        else if (orbitRpm < 0)
	        {
	        	if (-orbitRpm > orbitRpmLoss)
	        	{
	        		orbitRpm += orbitRpmLoss;
	        	}
	        	else
	        	{
	        		orbitRpm = 0;
	        	}
	        }
	        else
	        {
	        	//if orbitRpm == 0, do nothing
	        }
	        selfRpm = orbitRpm * orbitSelfRotationRatio;
	        
	        timeOld = tCurrent;
        }
        
        //Log.i(TAG, "(System.currentTimeMillis()%86400000) = "+(System.currentTimeMillis()%86400000));

        
        
        float mat_specular[] = {0.460000f, 0.460000f, 0.460000f, 1f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_SPECULAR, mat_specular, 0);
        float mat_ambient[] = {0f, 0f, 0f, 1f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_AMBIENT, mat_ambient, 0);
        float mat_diffuse[] = {0.593549f, 0.774194f, 0.000000f, 1f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_DIFFUSE, mat_diffuse, 0);
        mAxis[1].draw(gl);  // core
        mAxis[2].draw(gl);  // axis
        
        
        // This part is used as background, not moving
        gl.glLoadIdentity();
        if (viewAngle == 1)	gl.glRotatef(30, 1f, 0, 0);
        
        float mat_specular2[] = {0.430000f, 0.430000f, 0.430000f, 1f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_SPECULAR, mat_specular2, 0);
        float mat_ambient2[] = {0f, 0f, 0f, 1f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_AMBIENT, mat_ambient2, 0);
        float mat_diffuse2[] = {0.004479f, 0.004479f, 0.541935f, 1f};
        gl.glMaterialfv(GL10.GL_FRONT_AND_BACK, GL10.GL_DIFFUSE, mat_diffuse2, 0);
        mAxis[0].draw(gl); // shell 

        
    }
    


    public void onSurfaceChanged(GL10 gl, int width, int height) {
        gl.glViewport(0, 0, width, height);

        /*
         * Set our projection matrix. This doesn't have to be done
         * each time we draw, but usually a new projection needs to
         * be set when the viewport is resized.
         */

        float ratio = (float) width / height;
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glFrustumf(-ratio, ratio, -1, 1, 2f, 10);

        gl.glTranslatef(0, 0, -2.6f);

        gl.glRotatef(180f,  0, 0, 1);
        
        if (androidLog == true)	Log.d(TAG, "onSurfaceChanged()");
   }
    
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        /*
         * By default, OpenGL enables features that improve quality
         * but reduce performance. One might want to tweak that
         * especially on software renderer.
         */
        gl.glDisable(GL10.GL_DITHER); 

        /*
         * Some one-time OpenGL initialization can be made here
         * probably based on features of this particular context
         */
         gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT,
                 GL10.GL_FASTEST);

         if (mTranslucentBackground) {
             gl.glClearColor(0,0,0,0);
         } else {
             gl.glClearColor(1,1,1,1);
         }
         gl.glEnable(GL10.GL_CULL_FACE);
         gl.glShadeModel(GL10.GL_SMOOTH);
         gl.glEnable(GL10.GL_DEPTH_TEST);
         
         if (androidLog == true)	Log.d(TAG, "onSurfaceCreated()");
    }
    
    // deltaOmegaX and deltaOmegaY are read from gyroscope
    public void speedChange(float deltaOmegaX, float deltaOmegaY)
    {
    	float k = 100.0f;
    	float alpha = 0.9f;
    	float rpmDelta = 0f;
    	rpmDelta = (float) (k*(deltaOmegaX*Math.cos(1.0f*orbitAngleOld/360*2*PI) + deltaOmegaY*Math.sin(1.0*orbitAngleOld/360*2*PI)));
    	if ((rpmDelta > 0) || (selfRpm > 20000.0f))
    	{
    		selfRpm += rpmDelta;
    	}
    	else
    	{
    		selfRpm += alpha*rpmDelta; // When alpha < 1, making it harder to slow down, only happens for selfRpm < 20000
    	}
    	orbitRpm = selfRpm / orbitSelfRotationRatio;
    }
    
    
}
