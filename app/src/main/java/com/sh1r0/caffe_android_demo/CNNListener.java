package com.sh1r0.caffe_android_demo2;

import android.graphics.Bitmap;
/**
 * Created by shiro on 2014/9/22.
 */
public interface CNNListener {
    void onTaskCompleted(int result);
    void onTaskCompletedTheta(float[] theta, Bitmap bitmap);
}
