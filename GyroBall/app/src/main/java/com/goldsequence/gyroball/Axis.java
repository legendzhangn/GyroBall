// Written by Nan Zhang
// Released in 05/2012 with LGPL license

// This file is modified based on Google sample file

package com.goldsequence.gyroball;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.util.Log;

/**
 * A vertex shaded cube.
 */
class Axis
{

    public Axis(byte[] vertices, byte[] normals, byte[] indices, float[] myColor)
    {
        int one = 0x10000;
     
        //0.593549 0.774194 0.000000
        
        float colors[];
        colors = new float[vertices.length/3/4*4];
        for (int i = 0; i < vertices.length/3/4; i++)
        {
        	
        	colors[4*i] = myColor[0];
        	colors[4*i+1] = myColor[1];
        	colors[4*i+2] = myColor[2];
        	colors[4*i+3] = myColor[3];
        }
        
        fLength = indices.length/2;

        // Buffers to be passed to gl*Pointer() functions
        // must be direct, i.e., they must be placed on the
        // native heap where the garbage collector cannot
        // move them.
        //
        // Buffers with multi-byte datatypes (e.g., short, int, float)
        // must have their byte order set to native order

        ByteBuffer vbb = ByteBuffer.allocateDirect(vertices.length);
        vbb.order(ByteOrder.nativeOrder());
        mVertexByteBuffer = vbb;
        mVertexByteBuffer.put(vertices);
        mVertexByteBuffer.position(0);
        
        ByteBuffer nbb = ByteBuffer.allocateDirect(normals.length);
        mNormalByteBuffer = nbb;
        mNormalByteBuffer.put(normals); 
        mNormalByteBuffer.position(0);

        ByteBuffer cbb = ByteBuffer.allocateDirect(colors.length*4);
        cbb.order(ByteOrder.nativeOrder());
        mColorBuffer = cbb.asFloatBuffer();
        mColorBuffer.put(colors);
        mColorBuffer.position(0);

        ByteBuffer ibb = ByteBuffer.allocateDirect(indices.length);
        ibb.order(ByteOrder.nativeOrder());
        mIndexByteBuffer = ibb;
        mIndexByteBuffer.put(indices);
        mIndexByteBuffer.position(0);
    }

    public void draw(GL10 gl)
    {
        gl.glFrontFace(gl.GL_CCW); 

        // Enable face culling.
        gl.glEnable(GL10.GL_CULL_FACE);
        // What faces to remove with the face culling.
        gl.glCullFace(GL10.GL_BACK);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_COLOR_ARRAY);
        gl.glEnableClientState(GL10.GL_NORMAL_ARRAY);
        //gl.glVertexPointer(3, gl.GL_FLOAT, 0, mVertexBuffer);
        gl.glVertexPointer(3, gl.GL_FLOAT, 0, mVertexByteBuffer);
        //gl.glNormalPointer(gl.GL_FLOAT, 0, mNormalBuffer); 
        gl.glNormalPointer(gl.GL_FLOAT, 0, mNormalByteBuffer);
        
        gl.glColorPointer(4, gl.GL_FLOAT, 0, mColorBuffer);
        //gl.glDrawElements(gl.GL_TRIANGLES, fLength, gl.GL_UNSIGNED_SHORT, mIndexBuffer);
        gl.glDrawElements(gl.GL_TRIANGLES, fLength, gl.GL_UNSIGNED_SHORT, mIndexByteBuffer);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);
        gl.glDisableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glDisableClientState(GL10.GL_NORMAL_ARRAY);
        // Disable face culling.
        gl.glDisable(GL10.GL_CULL_FACE);
    }


    private FloatBuffer   mColorBuffer;
    private ByteBuffer   mVertexByteBuffer;
    private ByteBuffer   mNormalByteBuffer;
    private ByteBuffer  mIndexByteBuffer;
    private int fLength = 0;
}
