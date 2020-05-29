package com.example.android.rc_app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.io.IOException;

/**
 * Created by Joe on 2017/7/19.
 */

public class Joystick extends SurfaceView implements SurfaceHolder.Callback, View.OnTouchListener {
    private float centerX;
    private float centerY;
    private float baseRadius;
    private float hatRadius;
    private int A = 255, R = 0, G = 0, B = 255;
    private JoystickListener joystickCallBack;
    private boolean toBottom = false;
    private final int ratio = 5;

    private void setupDimensions() {
        centerX = getWidth() / 2;
        centerY = getHeight() / 2;
        baseRadius = Math.min(getWidth(), getHeight()) / 2;
        hatRadius = Math.min(getWidth(), getHeight()) / 7;
    }

    public void setHatColor(int a, int r, int g, int b) {
        A = a;
        R = r;
        G = g;
        B = b;
    }

    public void setHatkPos(String s) {
        switch (s) {
            case "Bottom":
                toBottom = true;
                break;
            case "Center":
                toBottom = false;
                break;
            default:
                toBottom = false;
                break;
        }
    }

    public Joystick(Context context) {
        super(context);
        getHolder().addCallback(this);
        setOnTouchListener(this);
        if (context instanceof JoystickListener) {
            joystickCallBack = (JoystickListener) context;
        }
    }

    public Joystick(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().addCallback(this);
        setOnTouchListener(this);
        if (context instanceof JoystickListener) {
            joystickCallBack = (JoystickListener) context;
        }
    }

    public Joystick(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(this);
        setOnTouchListener(this);
        if (context instanceof JoystickListener) {
            joystickCallBack = (JoystickListener) context;
        }
    }

    private void drawJoystick(float newX, float newY) {
        if (getHolder().getSurface().isValid()) {
            Canvas myCanvas = this.getHolder().lockCanvas();
            Paint colors = new Paint();
            myCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

            //First determine the sin and cos of the angle that the touched point is at relative to the center of the joystick
            float hypotenuse = (float) Math.sqrt(Math.pow(newX - centerX, 2) + Math.pow(newY - centerY, 2));
            float sin = (newY - centerY) / hypotenuse; //sin = o/h
            float cos = (newX - centerX) / hypotenuse; //cos = a/h


            //Draw the base first before shading
            colors.setARGB(255, R, G, B);
            myCanvas.drawCircle(centerX, centerY, baseRadius, colors);
            colors.setARGB(255, 0, 0, 0);
            myCanvas.drawCircle(centerX, centerY, baseRadius - 2, colors);

            for(int i = 1; i <= (int) (baseRadius / ratio); i++)
            {
                colors.setARGB(2 * A/i, R, G, B); //Gradually decrease the shade of black drawn to create a nice shading effect
                myCanvas.drawCircle(newX - cos * hypotenuse * (ratio/baseRadius) * i,
                        newY - sin * hypotenuse * (ratio/baseRadius) * i, i * (hatRadius * ratio / baseRadius), colors); //Gradually increase the size of the shading effect
            }

            //Drawing the joystick hat
            for(int i = 0; i <= (int) (hatRadius / ratio); i++)
            {
                colors.setARGB(A, (int) (i * (R * ratio / hatRadius)), (int) (i * (G * ratio / hatRadius)), (int) (i * (B * ratio / hatRadius))); //Change the joystick color for shading purposes
                myCanvas.drawCircle(newX, newY, hatRadius - (float) i * (ratio) / 2 , colors); //Draw the shading for the hat
            }

            getHolder().unlockCanvasAndPost(myCanvas);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        setupDimensions();
        drawJoystick(centerX, centerY);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v.equals(this)) if (event.getAction() != event.ACTION_UP) {
            float displacement = (float) Math.sqrt(Math.pow(centerX - event.getX(), 2) + Math.pow(centerY - event.getY(), 2));
            double angle = Math.atan2((event.getY() - centerY), (event.getX() - centerX));
            angle *= (180 / Math.PI);
            if (displacement < baseRadius) {
                float xPercent = (event.getX() - centerX) / baseRadius;
                float yPercent;
                if (!toBottom) {
                    yPercent = (event.getY() - centerY) / baseRadius;
                } else {
                    yPercent = (baseRadius - (event.getY() - centerY)) / (2 * baseRadius);
                }
                drawJoystick(event.getX(), event.getY());
                try {
                    joystickCallBack.onJoystickMoved(xPercent, yPercent, false, getId(), angle);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } else {
                float ratio = baseRadius / displacement;
                float constrantX = centerX + (event.getX() - centerX) * ratio;
                float constrantY = centerY + (event.getY() - centerY) * ratio;
                float xPercent = (constrantX - centerX) / baseRadius;
                float yPercent;
                if (!toBottom){
                    yPercent = (constrantY - centerY) / baseRadius;
                } else {
                    yPercent = (baseRadius - (constrantY - centerY)) / (2 * baseRadius);
                }
                drawJoystick(constrantX, constrantY);
                try {
                    joystickCallBack.onJoystickMoved(xPercent, yPercent, true, getId(), angle);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        } else {
            if (toBottom) {
                drawJoystick(centerX, centerY + baseRadius);
            } else {
                drawJoystick(centerX, centerY);
            }
            try {
                joystickCallBack.onJoystickMoved(0, 0, false, getId(), 0);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        return true;
    }

    public interface JoystickListener {
        void onJoystickMoved(float xPercent, float yPercent, boolean edge, int source, double angle) throws IOException;
    }
}
