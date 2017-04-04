package com.goertek.wifitransferlibrary;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.goertek.transferlibrary.search.controller.DeviceSearcher;
import com.goertek.transferlibrary.search.device.DeviceSearched;
import com.goertek.transferlibrary.search.controller.ISearchListener;
import com.goertek.transferlibrary.search.device.ISearchedListener;

import java.net.SocketAddress;
import java.util.List;

public class Main2Activity extends AppCompatActivity implements ISearchListener, ISearchedListener {

    private static final String TAG = "Main2Activity";
    private boolean mServer = false;

    private TextView mInfoTextView;

    private DeviceSearched mDeviceSearched;

    private DeviceSearcher mIPBroadcast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        initViews();
    }

    private void initViews() {
        mInfoTextView = (TextView) findViewById(R.id.tv_info);
        Button startButton = (Button) findViewById(R.id.btn_start);
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mServer){
                    if(mDeviceSearched == null){
                        mDeviceSearched = new DeviceSearched(Main2Activity.this);
                        mDeviceSearched.setSearchedListener(Main2Activity.this);
                        mDeviceSearched.setSendString("hello world");
                        mDeviceSearched.start();
                    }
                }else{
                    if(mIPBroadcast == null){
                        mIPBroadcast = new DeviceSearcher(Main2Activity.this,Main2Activity.this);
                        mIPBroadcast.start();
                    }
                }
            }
        });
        Button stopButton = (Button) findViewById(R.id.btn_stop);
        stopButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mServer){
                    if(mDeviceSearched != null){
                        mDeviceSearched.onDestroy();
                        mDeviceSearched.interrupt();
                        mDeviceSearched = null;
                    }
                }else{
                    if(mIPBroadcast != null){
                        mIPBroadcast.stop();
                        mIPBroadcast = null;
                    }
                }
            }
        });
        RadioGroup rgType = (RadioGroup) findViewById(R.id.rg_type);
        rgType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int i) {
                switch (i){
                    case R.id.rb_client:
                        mServer = false;
                        break;
                    case R.id.rb_server:
                        mServer = true;
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void onSearchStart() {
        Log.d(TAG,"onSearchStart");
    }

    @Override
    public void onSearchFinish(List<String> devices) {
        if(devices != null){
            StringBuilder builder = new StringBuilder();
            for(String device : devices){
                builder.append(device);
            }
            mInfoTextView.setText(builder.toString());
        }
    }

    @Override
    public void onError(String error) {
        mInfoTextView.setText(error);
    }

    @Override
    public void onSearched(SocketAddress address) {
        mInfoTextView.setText(address.toString());
    }
}
