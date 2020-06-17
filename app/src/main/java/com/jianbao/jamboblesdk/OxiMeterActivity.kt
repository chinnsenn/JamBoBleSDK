package com.jianbao.jamboblesdk

import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.creative.FingerOximeter.FingerOximeter
import com.jianbao.fastble.JamBoBleHelper
import com.jianbao.jamboble.BaseBleHelper.Companion.MESSAGE_TIMEOUT
import com.jianbao.jamboble.BleState
import com.jianbao.jamboble.callbacks.BleDataCallback
import com.jianbao.jamboble.callbacks.IBleStatusCallback
import com.jianbao.jamboble.data.BTData
import com.jianbao.jamboble.data.OximeterData
import com.jianbao.jamboble.data.SpO2Data
import com.jianbao.jamboble.device.oximeter.FingerOximeterCallback
import com.jianbao.jamboble.device.oximeter.OximeterDevice
import com.jianbao.jamboble.device.oximeter.OximeterReader
import com.jianbao.jamboble.device.oximeter.OximeterWriter
import com.jianbao.jamboble.draw.DrawThreadNW
import java.lang.ref.WeakReference

class OxiMeterActivity : AppCompatActivity() {
    private val mDtBloodOx by lazy(LazyThreadSafetyMode.NONE) { findViewById<DrawThreadNW>(R.id.dt_blood_ox) }
    private val mTvValueRealtime by lazy(LazyThreadSafetyMode.NONE) { findViewById<TextView>(R.id.tv_value_realtime) }
    private val mBtnOpenBle by lazy(LazyThreadSafetyMode.NONE) { findViewById<Button>(R.id.btn_open_ble) }
    private val mLayoutMeasuringGuide by lazy(LazyThreadSafetyMode.NONE) {
        findViewById<LinearLayout>(
            R.id.layout_measuring_guide
        )
    }
    private val mTvStatus by lazy(LazyThreadSafetyMode.NONE) { findViewById<TextView>(R.id.tv_status) }

    private var mFingerOximeter: FingerOximeter? = null
    private var mOximeterWriter: OximeterWriter? = null
    private var mOximeterReader: OximeterReader? = null

    private lateinit var mHandler: Handler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oxi_meter)

        mBtnOpenBle.setOnClickListener {
            when (mBtnOpenBle.text) {
                "停止扫描" -> {
                    mBtnOpenBle.text = "开始扫描"
                    JamBoBleHelper.instance.stopScan()
                }
                "开始扫描" -> {
                    JamBoBleHelper.instance.scanOxiMeterDevice()
                }
            }

        }
        //通用数据回调
        JamBoBleHelper.instance.setBleDataCallBack(
            object : BleDataCallback {
                override fun onBTStateChanged(state: BleState) {
                    when (state) {
                        //未开启蓝牙
                        BleState.NOT_FOUND -> {
                            mTvStatus.text = "请打开蓝牙"
                        }
                        //正在扫描
                        BleState.SCAN_START -> {
                            mBtnOpenBle.text = "停止扫描"
                            mTvStatus.text = "开始扫描..."
                        }
                        //连接成功
                        BleState.CONNECTED -> {
                            mTvStatus.text = "连接设备成功"
                            mLayoutMeasuringGuide.visibility = View.INVISIBLE
                        }
                        //长时间未搜索到设备
                        BleState.TIMEOUT -> {
                            mBtnOpenBle.text = "开始扫描"
                            mTvStatus.text = "超时"
                        }
                        BleState.CONNECT_FAILED -> {
                            mTvStatus.text = "连接失败"
                        }
                        BleState.CONNECTEING -> {
                            mTvStatus.text = "连接中"
                        }
                        BleState.DISCONNECT -> {
                            mTvStatus.text = "断开连接"
                        }
                    }
                }

                override fun onBTDataReceived(data: BTData?) {
                    if (data is OximeterData) {
                        val btDevice = JamBoBleHelper.instance.getBTDevice()
                        if (btDevice is OximeterDevice) {
                            btDevice.oximeterHelper.also {
                                it.addBuffer(data.data)
                            }
                        }
                    }
                }

                override fun onLocalBTEnabled(enabled: Boolean) {
                }

            })

        JamBoBleHelper.instance.setBleStatusCallback(
            object : IBleStatusCallback {
                override fun onBTDeviceFound(device: BluetoothDevice?) {
                }

                override fun onNotification() {
                    JamBoBleHelper.instance.getBTDevice().also {
                        if (it is OximeterDevice) {
                            it.oximeterHelper?.also { helper ->
                                mOximeterReader = OximeterReader(helper)
                                mOximeterWriter = OximeterWriter(helper)
                                mFingerOximeter = FingerOximeter(
                                    mOximeterReader,
                                    mOximeterWriter,
                                    FingerOximeterCallback(mHandler, mDtBloodOx)
                                )
                                mFingerOximeter?.Start()
                                mFingerOximeter?.SetWaveAction(true)

                            }

                        }
                    }
                }
            }
        )

        mHandler = CurveHandler(this)
        mDtBloodOx.setmHandler(mHandler)
    }

    /**
     * 暂停记录，暂停绘制波纹图
     */
    private fun pauseRecord() {
        if (mDtBloodOx != null) {
            mDtBloodOx.pauseDraw()
        }
        if (mFingerOximeter != null) {
            mFingerOximeter!!.Stop()
        }
    }

    override fun onDestroy() {
        //释放资源
        JamBoBleHelper.instance.destroy()
        super.onDestroy()
    }

    class CurveHandler(activity: OxiMeterActivity) : Handler() {
        private val mWeakReference = WeakReference(activity)

        override fun handleMessage(msg: Message) {
            mWeakReference.get()?.also { act ->
                when (msg.what) {
                    FingerOximeterCallback.MSG_DATA_SPO2_WAVE -> {
                        //波形图数据，开始绘制
                        act.mDtBloodOx.startDraw()
                    }
                    FingerOximeterCallback.MSG_DATA_SPO2_PARA -> {
                        //波形参数
                        if (msg.data != null) {
                            val data: SpO2Data? = msg.data.getSerializable("data") as SpO2Data?
                            data?.also {
                                if (it.spO2 > 0 && it.pr > 0) {
                                    //nStatus探头状态 ->true为正常 false为脱落
                                    if (!it.isStatus) {
                                        sendEmptyMessage(FingerOximeterCallback.MSG_PROBE_OFF)
                                    } else {
                                        //显示正在测量的实时数据
                                        act.mTvValueRealtime.text = it.toString()
                                    }

                                }
                            }

                        }
                    }

                    FingerOximeterCallback.MSG_PROBE_OFF -> {
                        act.mDtBloodOx.cleanWaveData()
                        act.pauseRecord()
                        if (act.mOximeterReader != null) {
                            act.mOximeterReader?.close()
                        }
                        if (act.mOximeterWriter != null) {
                            act.mOximeterWriter?.close()
                        }

                        //需要等待自动关闭
                        JamBoBleHelper.instance.onBTStateChanged(BleState.SCAN_START)
                    }
                    MESSAGE_TIMEOUT -> {
                        JamBoBleHelper.instance.onBTStateChanged(BleState.TIMEOUT)
                    }
                    else -> {

                    }
                }

            }
        }
    }
}