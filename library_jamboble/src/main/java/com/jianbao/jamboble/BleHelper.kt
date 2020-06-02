package com.jianbao.jamboble

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Message
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity
import com.dovar.dtoast.DToast
import com.jianbao.jamboble.BTControlManager.BTControlListener
import com.jianbao.jamboble.callbacks.BleDataCallback
import com.jianbao.jamboble.callbacks.IBleStatusCallback
import com.jianbao.jamboble.callbacks.IBleStatusCallback.State.Companion.CONNECTED
import com.jianbao.jamboble.callbacks.UnSteadyValueCallBack
import com.jianbao.jamboble.data.BTData
import com.jianbao.jamboble.device.BTDevice
import com.jianbao.jamboble.device.BTDeviceSupport
import com.jianbao.jamboble.fatscale.QnHelper
import com.jianbao.jamboble.utils.permissions.PermissionsUtil
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by zhangmingyao
 * date: 2018/7/20.
 * Email:501863760@qq.com
 */
class BleHelper(activity: FragmentActivity, deviceType: BTDeviceSupport.DeviceType) {

    private var mBluetoothAdapter: BluetoothAdapter? = null//蓝牙适配器，单例唯一
    private var mBTControlManager: BTControlManager? = null
    private var mBluetoothStateReceiver: BluetoothStateReceiver? = null
    private var mLeScanCallback: LeScanCallback? = null //蓝牙设备搜索结果
    private var mNewScanCallback: ScanCallback? = null //蓝牙设备搜索结果
    private val mBTControlListener: BTControlListener
    private var mHandler: Handler = ScanHandler(this@BleHelper)
    private var mScanning = false
    private val mLockAutoScan = Any()
    private val activityReference = WeakReference(activity)
    private var mQnUser: QnUser? = null

    private var mDataCallback: BleDataCallback? = null
    private var mUnSteadyValueCallBack: UnSteadyValueCallBack? = null
    private var mBleStatusCallback: IBleStatusCallback? = null

    private val mDeviceType = deviceType
    private var mScanRunnable: ScanRunnable? = null
    private var mRegistered = AtomicBoolean(false)

    companion object {
        private const val TAG = "BleHelper"
        private const val MESSAGE_SCAN = 0
        private const val REQUEST_ENABLE_BT = 1
    }

    init {
        mBTControlListener = BTListener(this)
        mBleStatusCallback = object :
            IBleStatusCallback {
            override fun onNotification() {
            }

            override fun doByThirdSdk(
                device: BluetoothDevice?,
                btDevice: BTDevice?,
                rssi: Int,
                scanRecord: ByteArray?
            ) {
            }

            override fun onBTStateChanged(state: Int) {
            }

            override fun onBTDeviceFound(device: BluetoothDevice?) {
            }

        }
    }

    /**
     * 断开蓝牙时是否需要自动重新扫描
     *
     * @param autoScanWhenDisconnected
     */
    private var isAutoScanWhenDisconnected: Boolean = true
        get() {
            synchronized(mLockAutoScan) { return field }
        }
        set(autoScanWhenDisconnected) {
            synchronized(mLockAutoScan) { field = autoScanWhenDisconnected }
        }

    /**
     * 初始化蓝牙对象，我们采用的蓝牙4.0，android 4.3以上支持
     */
    fun openBluetooth() {
        if (mDeviceType == BTDeviceSupport.DeviceType.FAT_SCALE && mQnUser == null) {
            showToast("请调用 updateQnUser 初始化用户数据")
            return
        }
        //检测手机是否支持蓝牙设备连接
        activityReference.get()?.also {
            if (it.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                checkBle(it)
            } else {
                showToast("您的手机不支持蓝牙功能")
            }
        }
    }

    private fun showToast(msg: String) {
        DToast.make(activityReference.get()).setText(R.id.tv_content_default, msg).show()
    }

    private fun checkBle(activity: FragmentActivity) {
        // 初始化 Bluetooth adapter,
        // 通过蓝牙管理器得到一个参考蓝牙适配器(API必须在以上android4.3或以上和版本)
        PermissionsUtil.requestMustNot(
            activity, PermissionsUtil.OnPermissionGranted { initBluetooth(activity) },
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun initBluetooth(activity: Activity) {
        if (mBluetoothAdapter == null) {
            val bluetoothManager =
                activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            mBluetoothAdapter = bluetoothManager.adapter
            mBluetoothAdapter?.also { adapter ->
                //在scanLeDevice之前,避免连接时BluetoothLeService仍为null
                if (mBTControlManager == null) {
                    mBTControlManager = BTControlManager(activity)
                        .addBtControlListener(mBTControlListener)
                        .addServiceConnect {
                            registerReceiver()
                            if (adapter.isEnabled) {
                                scanLeDevice(true)
                            }
                        }
                        .init()
                } else {
                    registerReceiver()
                    if (adapter.isEnabled) {
                        scanLeDevice(true)
                    }
                }
            } ?: also {
                showToast("您的手机不支持蓝牙功能")
            }
        }

        // 为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
        mBluetoothAdapter?.also {
            val enabled = it.isEnabled
            //            mBleDataCallback.onLocalBTEnabled(enabled);
            if (!enabled) {
                onBTStateChanged(IBleStatusCallback.State.NOT_FOUND)
                val enableBtIntent = Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE
                )
                activity.startActivityForResult(
                    enableBtIntent,
                    REQUEST_ENABLE_BT
                )
            }
        }
    }

    /**
     * 数据回调
     */
    private fun setDataCallBack(callback: BleDataCallback) {
        this.mDataCallback = callback
    }

    /**
     * 动态数据回调（仅体重支持
     */
    private fun setUnSteadyValueCallBack(callback: UnSteadyValueCallBack) {
        this.mUnSteadyValueCallBack = callback
    }

    /**
     * 测量体重需传入用户参数
     */
    fun updateQnUser(qnUser: QnUser) {
        if (mDeviceType != BTDeviceSupport.DeviceType.FAT_SCALE) {
            showToast("非体重测量无需设置用户参数")
            return
        }
        this.mQnUser = qnUser
        QnHelper.instance.updateQNUser(qnUser)
    }

    /**
     * 蓝牙扫描
     *
     * @param enable
     */
    fun scanLeDevice(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            scanLeDeviceNewApi(enable)
        } else {
            scanLeDeviceLowApi(enable)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun scanLeDeviceNewApi(enable: Boolean) {
        mHandler.removeMessages(MESSAGE_SCAN)
        mBluetoothAdapter?.also {
            if (enable) {
                if (!it.isEnabled) {
                    return
                }
            }
            if (mNewScanCallback == null) {
                mNewScanCallback = BLENewCallBack(this)
            }
            if (enable) {
                val scanner = it.bluetoothLeScanner
                if (mScanning) {
                    scanner.stopScan(mNewScanCallback)
                    Log.i(TAG, "正在查找设备..." + "已启动")
                }
                mScanning = true
                scanner.startScan(mNewScanCallback)
            } else {
                if (mScanning) {
                    if (it.isEnabled) {
                        it.bluetoothLeScanner.stopScan(mNewScanCallback)
                    }
                    mScanning = false
                    Log.i(TAG, "===停止查找设备===")
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun scanLeDeviceLowApi(enable: Boolean) {
        mHandler.removeMessages(MESSAGE_SCAN)
        mBluetoothAdapter?.also {
            if (enable) {
                if (!it.isEnabled) {
                    return
                }
            }
            if (mLeScanCallback == null) {
                mLeScanCallback = BLECallback(this)
            }
            if (enable) {
                if (mScanning) {
                    it.stopLeScan(mLeScanCallback)
                    Log.i(TAG, "正在查找设备..." + "已启动")
                }
                val ret = it.startLeScan(mLeScanCallback)
                if (ret) {
                    mScanning = true
                    onBTStateChanged(IBleStatusCallback.State.SCAN_START)
                } else {
                    mHandler.sendEmptyMessageDelayed(MESSAGE_SCAN, 1000)
                }
                Log.i(TAG, "正在查找设备...$ret")
            } else {
                if (mScanning) {
                    if (it.isEnabled) {
                        it.stopLeScan(mLeScanCallback)
                    }
                    mScanning = false
                    Log.i(TAG, "===停止查找设备===")
                }
            }
        }
    }

    /**
     * 注册广播，监听蓝牙开关状态
     */
    fun registerReceiver() {
        if (mRegistered.compareAndSet(false, false)) {
            activityReference.get()?.also {
                if (mBluetoothStateReceiver == null) {
                    mBluetoothStateReceiver = BluetoothStateReceiver(this)
                    val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                    it.registerReceiver(mBluetoothStateReceiver, filter)
                    mRegistered.set(true)
                }
            }
        }
    }

    /**
     * 注销广播
     */
    fun unregisterReceiver() {
        if (mRegistered.compareAndSet(true, true)) {
            activityReference.get()?.also {
                mBluetoothStateReceiver?.also { receiver ->
                    it.unregisterReceiver(receiver)
                    mRegistered.set(false)
                }
            }
        }
    }

    val isEnable: Boolean
        get() = mBluetoothAdapter?.isEnabled ?: false

    fun doReSearch() {
        activityReference.get()?.also {
            // 为了确保设备上蓝牙能使用, 如果当前蓝牙设备没启用,弹出对话框向用户要求授予权限来启用
            mBluetoothAdapter?.also { adapter ->
                val enabled = adapter.isEnabled
                if (!enabled) {
                    val enableBtIntent = Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE
                    )
                    it.startActivityForResult(
                        enableBtIntent,
                        REQUEST_ENABLE_BT
                    )
                } else {
                    onBTStateChanged(IBleStatusCallback.State.SCAN_START)
                    scanLeDevice(true)
                }
            } ?: also {
                openBluetooth()
            }
        }

    }

    protected fun connectDevice(device: BTDevice?, adress: String?): Boolean {
        scanLeDevice(false)
        return mBTControlManager?.connect(device, adress) ?: false
    }

    public fun getConnectedDevice(): BTDevice? {
        return mBTControlManager?.connectDevice
    }

    private fun handlerScanResult(
        device: BluetoothDevice,
        rssi: Int,
        scanRecord: ByteArray
    ) {
        activityReference.get()?.also {
            if (mScanRunnable == null) {
                mScanRunnable = ScanRunnable(this)
            }
            mScanRunnable?.setDevice(device, rssi, scanRecord)
            it.runOnUiThread(mScanRunnable)
        }
    }

    fun destroy() {
        scanLeDevice(false)

        //注销广播
        unregisterReceiver()

        //释放资源
        if (mBTControlManager != null) {
            mBTControlManager!!.dispose()
            mBTControlManager = null
        }
        if (mLeScanCallback != null) {
            mLeScanCallback = null
        }
        if (mBleStatusCallback != null) {
            mBleStatusCallback = null
        }
    }

    private fun receive(state: Int) {
        when (state) {
            BluetoothAdapter.STATE_ON -> //mBleDataCallback.onLocalBTEnabled(true);
                scanLeDevice(true)
            BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                //                mBleDataCallback.onLocalBTEnabled(false);
                onBTStateChanged(IBleStatusCallback.State.NOT_FOUND)
                scanLeDevice(false)
            }
            else -> {
            }
        }
    }

    private fun checkDevice(
        device: BluetoothDevice?,
        rssi: Int,
        scanRecord: ByteArray
    ) {
        val btDevice = BTDeviceSupport.checkSupport(device, mDeviceType)
        println("BleHelper.checkDevice ${device?.name.toString()}")
        device?.also { blDevice ->
            btDevice?.also { btDevice ->
                mBleStatusCallback?.also { callback ->
                    println("device.getAddress() = " + blDevice.address)
                    scanLeDevice(false)
                    Log.i(TAG, "已找到设备，准备连接...")
                    mBleStatusCallback!!.onBTDeviceFound(device)
                    if (BTDeviceSupport.isYolandaFatScale(btDevice)) {
                        if (mBTControlManager != null) {
                            mBTControlManager!!.connectDevice = btDevice
                        }

                        QnHelper.instance.also {
                            it.connectDevice(this, mQnUser!!, device, btDevice, rssi, scanRecord)
                        }
//                        doYolandaConnect(device, btDevice, rssi, scanRecord)
                    } else {
                        if (mBTControlManager != null) {
                            mBTControlManager!!.connect(btDevice, device.address)
                        }
                    }
                }
            }
        }
    }

    private fun onConnectChanged(connected: Boolean) {
        if (!connected && isAutoScanWhenDisconnected) {
            scanLeDevice(true)
        } else if (connected) {
            //连接成功
            onBTStateChanged(CONNECTED)
        }
    }

    /**
     * 蓝牙回调
     * @param state IBleStatusCallback.State
     */
    fun onBTStateChanged(state: Int) {
        mDataCallback?.onBTStateChanged(state)
    }

    fun onBTDataReceived(btData: BTData?) {
        btData?.also {
            mDataCallback?.onBTDataReceived(btData)
        }
    }

    fun onLocalBTEnabled(enabled: Boolean) {
//        mBleDataCallback.onLocalBTEnabled(enabled);
    }

    fun onUnsteadyValue(value: Float) {
        mUnSteadyValueCallBack?.onUnsteadyValue(value)
    }

    fun onBTDeviceFound(device: BluetoothDevice?) {
        mBleStatusCallback?.onBTDeviceFound(device)
    }

    fun onNotification() {
        mBleStatusCallback?.onNotification()
    }

    fun doByThirdSdk(
        device: BluetoothDevice?,
        btDevice: BTDevice?,
        rssi: Int,
        scanRecord: ByteArray?
    ) {
        mBleStatusCallback?.doByThirdSdk(device, btDevice, rssi, scanRecord)
    }

    /**
     * 蓝牙广播监听
     *
     * @author 毛晓飞
     */
    private class BluetoothStateReceiver(helper: BleHelper) : BroadcastReceiver() {
        private val mReference = WeakReference(helper)
        override fun onReceive(
            context: Context,
            intent: Intent
        ) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
            mReference.get()?.receive(state)
        }

    }

    /******************静态内部类的处理 */
    class BLECallback internal constructor(callback: BleHelper) : LeScanCallback {
        private val reference = WeakReference(callback)
        override fun onLeScan(
            device: BluetoothDevice,
            rssi: Int,
            scanRecord: ByteArray
        ) {
            reference.get()?.handlerScanResult(device, rssi, scanRecord)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    class BLENewCallBack internal constructor(helper: BleHelper) : ScanCallback() {

        private val reference = WeakReference(helper)

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let {
                it.scanRecord?.bytes?.let { it1 ->
                    reference.get()?.handlerScanResult(
                        it.device, it.rssi,
                        it1
                    )
                }
            }
        }
    }

    class ScanRunnable internal constructor(helper: BleHelper) : Runnable {
        private var device: BluetoothDevice? = null
        private var rssi = 0
        private lateinit var scanRecord: ByteArray
        private val mReference = WeakReference(helper)
        fun setDevice(
            device: BluetoothDevice?,
            rssi: Int,
            scanRecord: ByteArray
        ) {
            this.device = device
            this.rssi = rssi
            this.scanRecord = scanRecord
        }

        override fun run() {
            mReference.get()?.also { it.checkDevice(device, rssi, scanRecord) }
        }
    }

    private class ScanHandler internal constructor(helper: BleHelper) : Handler() {
        var reference = WeakReference(helper)
        override fun handleMessage(msg: Message) {
            if (msg.what == MESSAGE_SCAN) {
                reference.get()?.scanLeDevice(true)
            }
        }

    }

    private class BTListener internal constructor(helper: BleHelper) : BTControlListener {
        private val mWeakReference = WeakReference(helper)
        override fun onDataReceived(btData: BTData?) {
            mWeakReference.get()?.onBTDataReceived(btData)
        }

        override fun onActionNotification() {
            val bleHelper = mWeakReference.get()
            bleHelper?.onNotification()
        }

        override fun onConnectChanged(connected: Boolean) {
            Log.i(
                TAG,
                if (connected) "已成功连接，可以开始测量" else "连接失败，请重新连接"
            )
            val bleHelper = mWeakReference.get()
            bleHelper?.onConnectChanged(connected)
        }

    }
}