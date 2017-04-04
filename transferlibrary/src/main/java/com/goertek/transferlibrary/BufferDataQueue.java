package com.goertek.transferlibrary;

import com.goertek.transferlibrary.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by landon.xu on 2017/2/18.
 */

public class BufferDataQueue {

    private static final String TAG = "BufferDataQueue";

    //最大缓存数据量
    private static final int MAX_BUFFER_COUNT = 65535;

    private List<String> mData = new ArrayList<>();

    /**
     * 将要发送的数据放到缓存队列里，当建立连接后，会自动发送
     *
     * @param data
     * @return 超过最大缓存数据量会返回false，否则返回true
     */
    public boolean put(String data) {
        synchronized (this) {
            LogUtils.d(TAG, "put data");
            if (mData.size() > MAX_BUFFER_COUNT) {
                return false;
            }
            boolean needNotify = false;
            if (mData.size() == 0)
                needNotify = true;
            mData.add(data);
            if (needNotify) {
                notify();
            }
            return true;
        }

    }

    /**
     * 获取待发送的数据，取出后自动从缓存中删除，同步阻塞
     *
     * @return
     * @throws InterruptedException
     */
    public String get() throws InterruptedException {
        synchronized (this) {
            if (mData.size() == 0) {
                LogUtils.d(TAG, "wait get");
                wait();
            }
            LogUtils.d(TAG, "get result");
            String result = mData.get(0);
            mData.remove(0);
            return result;
        }
    }

    public synchronized void clear() {
        mData.clear();
    }
}
