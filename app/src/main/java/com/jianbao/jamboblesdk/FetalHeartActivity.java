package com.jianbao.jamboblesdk;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.jianbao.fastble.data.BleDevice;
import com.jianbao.jamboble.BleState;
import com.jianbao.jamboble.callbacks.FetalHeartBleCallback;
import com.jianbao.jamboble.data.BTData;
import com.jianbao.jamboble.fetalheart.FetalHeartHelper;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FetalHeartActivity extends AppCompatActivity {

    private TextView mTvStatus;
    private TextView mTvValueRealtime;
    private Button mBtnOpenBle;
    private DevicesAdapter devicesAdapter;
    private RecyclerView mRvBle;
    private ProgressBar mPbLoading;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fetal_heart);

        mTvStatus = findViewById(R.id.tv_status);
        mTvValueRealtime = findViewById(R.id.tv_value_realtime);
        mBtnOpenBle = findViewById(R.id.btn_open_ble);
        mRvBle = findViewById(R.id.rv_ble);
        mPbLoading = findViewById(R.id.pb_loading);

        devicesAdapter = new DevicesAdapter();
        devicesAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                if (view.getId() == R.id.btn_connect) {
                    BleDevice device = (BleDevice) adapter.getData().get(position);
                    if (device.isConnected()) {
                        //断开连接
                        FetalHeartHelper.getInstance().disconnect();
                    } else {
                        //连接设备
                        FetalHeartHelper.getInstance().connectDevice(device);
                    }
                } else if (view.getId() == R.id.btn_record) {
                    //获取录制状态
                    if (FetalHeartHelper.getInstance().getRecordStatus()) {
                        ((Button) view).setText("开始录制");
                        //结束录制
                        FetalHeartHelper.getInstance().finishRecord();
                    } else {
                        ((Button) view).setText("停止录制");
                        //录音,返回录音文件路径
                        String recordPath = FetalHeartHelper.getInstance().startRecord();
                        System.out.println(recordPath);
                    }
                }
            }

        });
        mRvBle.setLayoutManager(new LinearLayoutManager(this));
        mRvBle.setAdapter(devicesAdapter);

        mBtnOpenBle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (mBtnOpenBle.getText().toString()) {
                    case "开始扫描":
                        FetalHeartHelper.getInstance().scanFetalHeartDevice();
                        break;
                    case "停止扫描":
                        FetalHeartHelper.getInstance().stopScan();
                        break;
                }
            }
        });

        FetalHeartHelper.getInstance().setFetalHeartBleCallback(new FetalHeartBleCallback() {
            @Override
            public void onBTDeviceFound(List<BleDevice> list) {
                devicesAdapter.updateList(list);
            }

            @Override
            public void onBTDeviceScanning(BleDevice device) {
                mPbLoading.setVisibility(View.GONE);
                devicesAdapter.addDevice(device);
            }

            @Override
            public void onBTStateChanged(@NotNull BleState state) {
                switch (state) {
                    //未开启蓝牙
                    case NOT_FOUND:
                        mTvStatus.setText("请打开蓝牙");
                        break;
                    //正在扫描
                    case SCAN_START:
                        mBtnOpenBle.setText("停止扫描");
                        mTvStatus.setText("开始扫描...");
                        mPbLoading.setVisibility(View.VISIBLE);
                        break;
                    //连接成功
                    case CONNECTED:
                        mTvStatus.setText("连接设备成功");
                        FetalHeartHelper.getInstance().getConnectedDevice().setConnected(true);
                        devicesAdapter.notifyDataSetChanged();
                        break;
                    //长时间未搜索到设备
                    case TIMEOUT:
                        mBtnOpenBle.setText("开始扫描");
                        mTvStatus.setText("超时");
                        break;
                    case CONNECTING:
                        mBtnOpenBle.setText("开始扫描");
                        mTvStatus.setText("连接中");
                        break;
                    case DISCONNECT:
                        mTvStatus.setText("断开连接");
                        devicesAdapter.notifyDataSetChanged();
                        break;
                    case CONNECT_FAILED:
                        mTvStatus.setText("连接失败");
                        break;
                    case SCAN_STOP:
                        mTvStatus.setText("已停止扫描");
                        mBtnOpenBle.setText("开始扫描");
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onBTDataReceived(@Nullable BTData data) {
                mTvValueRealtime.setText(data.toString());
            }

            @Override
            public void onLocalBTEnabled(boolean enabled) {

            }
        });
    }

    static class DevicesAdapter extends BaseQuickAdapter<BleDevice, BaseViewHolder> {
        public DevicesAdapter() {
            super(R.layout.item_devices);
        }

        @Override
        protected void convert(BaseViewHolder helper, BleDevice item) {
            helper.setText(R.id.tv_device_name, item.getName() + "\n" + item.getMac());
            helper.setText(R.id.btn_connect, item.isConnected() ? "断开连接" : "连接设备");
            helper.setGone(R.id.btn_record, item.isConnected());
            helper.addOnClickListener(R.id.btn_connect);
            helper.addOnClickListener(R.id.btn_record);
        }

        public void updateList(List<BleDevice> list) {
            mData.clear();
            mData.addAll(list);
            notifyDataSetChanged();
        }

        public void addDevice(BleDevice bleDevice) {
            mData.add(bleDevice);
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return mData.get(position).hashCode();
        }
    }

    @Override
    protected void onDestroy() {
        FetalHeartHelper.getInstance().destroy();
        super.onDestroy();
    }
}
