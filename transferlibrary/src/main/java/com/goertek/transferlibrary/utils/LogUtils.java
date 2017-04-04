package com.goertek.transferlibrary.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.util.Log;

/**
 * 打印log辅助类
 * Created by landon.xu on 2016/9/27.
 */

public class LogUtils {

    //是否打印log
    private static boolean mShowLog = true;

    //初始化是否打印log，如果debug版本打印，否则不打印
    public static void initDebug(Context context){
        ApplicationInfo info = context.getApplicationInfo();
        mShowLog = ((info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
    }


    public static void d(String tag, String msg){
        if(mShowLog){
            Log.d(tag,msg);
        }
    }

    public static void e(String tag, String msg){
        if(mShowLog){
            Log.e(tag,msg);
        }
    }

    public static void v(String tag, String msg){
        if(mShowLog){
            Log.v(tag, msg);
        }
    }

    public static void i(String tag, String msg){
        if(mShowLog){
            Log.i(tag, msg);
        }
    }

    public static void w(String tag, String msg){
        if(mShowLog){
            Log.w(tag,msg);
        }
    }
}
