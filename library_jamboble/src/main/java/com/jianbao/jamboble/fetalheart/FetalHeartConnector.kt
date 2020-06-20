package com.jianbao.jamboble.fetalheart

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Environment
import android.os.Handler
import android.os.Message
import com.jianbao.jamboble.data.FetalHeartData
import com.jianbao.jamboble.utils.IoUtils
import com.jianbao.jamboble.utils.LogUtils
import com.luckcome.lmtpdecorder.LMTPDecoder
import com.luckcome.lmtpdecorder.LMTPDecoderListener
import com.luckcome.lmtpdecorder.data.FhrData
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.ref.WeakReference
import java.util.*

class FetalHeartConnector {
    val FILE_DIR = Environment.getDownloadCacheDirectory().absolutePath + File.pathSeparator + ".audio"

    // 服务的回调接口
    private var mCallback: Callback? = null

    // 服务当前连接的蓝牙设备
    private var mBtDevice: BluetoothDevice? = null

    // 蓝牙 socket
    private var mBluetoothSocket: BluetoothSocket? = null

    // 蓝牙输出流
    private var mOutputStream: OutputStream? = null

    // 是否保存标志
    private var isRecord = false

    /**
     * 蓝牙终端协议解析器
     */
    private var isReading = false

    /**
     * 数据解析器回调接口
     */
    private var mLMTPDecoder: LMTPDecoder = LMTPDecoder()
    private var mLMTPDListener: LMTPDListener = LMTPDListener(this)
    private var mReadThread: ReadThread? = null
    private var mConnectThread: ConnectThread? = null

    private var mNotifyHandler: NotifyHandler = NotifyHandler(this)

    companion object {
        val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val CONNECT_SUCCESS = 1
        const val CONNECT_FAILED = 2
        const val READ_DATA_SUCCESS = 3
        const val READ_DATA_FAILED = 4
        const val READ_INTERVAL = 30 //读取间隔

        /**
         * 消息编号，连接完成
         */
        private const val MSG_CONNECT_FINISHED = 10
        private const val MSG_NOTIFY_STATUS = 11
        private const val MSG_NOTIFY_DATA = 12
    }

    init {
        mLMTPDecoder.setLMTPDecoderListener(mLMTPDListener)
        mLMTPDecoder.prepare()
    }

    private fun notifyData(data: FetalHeartData) {
        val msg = Message()
        msg.what = MSG_NOTIFY_DATA
        msg.obj = data
        mNotifyHandler.sendMessage(msg)
    }


    /**
     * 设置要连接的蓝牙设备
     *
     * @param device
     */
    fun setBluetoothDevice(device: BluetoothDevice) {
        mBtDevice = device
    }

    /**
     * 设置回调接口
     *
     * @param cb
     */
    fun setCallback(cb: Callback) {
        mCallback = cb
    }

    /**
     * 启动连接线程
     */
    fun start() {
        if (mConnectThread == null) {
            mConnectThread = ConnectThread(this)
        }
        mConnectThread?.start()
        mLMTPDecoder.startWork()
    }


    /**
     * 停止数据读取和解析
     */
    fun cancel() {
        isReading = false
        if (mBluetoothSocket != null) {
            try {
                mBluetoothSocket!!.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        mConnectThread = null
        mReadThread = null
        mLMTPDecoder.stopWork()
    }

    /**
     * 启动记录功能
     */
    fun recordStart(): String? {
        val path: File = getRecordFilePath()
        val fname = "" + System.currentTimeMillis()
        mLMTPDecoder.beginRecordWave(path, fname)
        isRecord = true
        return "$path${File.pathSeparator}$fname.wav" //固定此格式
    }

    fun getRecordFilePath(): File {
        val path = File(FILE_DIR)
        if (!path.exists()) {
            try {
                path.mkdirs()
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
        }
        return path
    }

    /**
     * 结束记录
     */
    fun recordFinished() {
        isRecord = false
        mLMTPDecoder.finishRecordWave()
    }


    /**
     * 获取记录状态
     *
     * @return
     */
    fun getRecordStatus(): Boolean {
        return isRecord
    }

    /**
     * 设置宫缩复位
     *
     * @param value 宫缩复位的值
     */
    fun setTocoReset(value: Int) {
        mLMTPDecoder.sendTocoReset(value)
    }

    /**
     * 设置一次手动胎动
     */
    fun setFM() {
        mLMTPDecoder.setFM()
    }

    /**
     * 设置胎心音量
     *
     * @param value 胎心音量大小
     */
    fun setFhrVolume(value: Int) {
        mLMTPDecoder.sendFhrVolue(value)
    }

    private fun notifyStatus(status: Int) {
        val msg = Message()
        msg.what = MSG_NOTIFY_STATUS
        msg.obj = status
        mNotifyHandler.sendMessage(msg)
    }

    private fun nitifyData(data: FetalHeartData) {
        val msg = Message()
        msg.what = MSG_NOTIFY_DATA
        msg.obj = data
        mNotifyHandler.sendMessage(msg)
    }

    /**
     * 获取工作状态
     *
     * @return
     */
    fun getReadingStatus(): Boolean {
        return isReading
    }

    fun destroy() {
        mLMTPDecoder.release()
    }

    /**
     * 胎心数据解析，回调接口
     *
     * @author Administrator
     */
    class LMTPDListener(connector: FetalHeartConnector) : LMTPDecoderListener {
        private val mWeakReference = WeakReference(connector)

        /**
         * 有新数据产生的时候回调
         */
        override fun fhrDataChanged(data: FhrData?) {
            val fetalHeartData = FetalHeartData()
            data?.also { fhrData ->
                fetalHeartData.fhr1 = fhrData.fhr1
                fetalHeartData.fhr2 = fhrData.fhr2
                fetalHeartData.toco = fhrData.toco
                fetalHeartData.afm = fhrData.afm
                fetalHeartData.fhrSignal = fhrData.fhrSignal
                fetalHeartData.afmFlag = fhrData.afmFlag
                fetalHeartData.fmFlag = fhrData.fmFlag
                fetalHeartData.tocoFlag = fhrData.tocoFlag
                fetalHeartData.devicePower = fhrData.devicePower
                fetalHeartData.isHaveFhr1 = fhrData.isHaveFhr1
                fetalHeartData.isHaveFhr2 = fhrData.isHaveFhr2
                fetalHeartData.isHaveToco = fhrData.isHaveToco
                fetalHeartData.isHaveAfm = fhrData.isHaveAfm
                if (fhrData.fmFlag.toInt() != 0) LogUtils.v("LMTPD", "LMTPD...1...fm")
                if (fhrData.tocoFlag.toInt() != 0) LogUtils.v("LMTPD", "LMTPD...2...toco")
            }
            mWeakReference.get()?.also {
                it.notifyData(fetalHeartData)
            }
        }

        /**
         * 有命令产生的时候回调
         */
        override fun sendCommand(bytes: ByteArray?) {
            mWeakReference.get()?.apply {
                // 这里添加从蓝牙发送数据的代码
                if (mOutputStream != null) {
                    try {
                        mOutputStream?.write(bytes)
                        mOutputStream?.flush()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }

    }

    //=====================连接蓝牙线程========================
    /**
     * 连接蓝牙线程
     *
     * @author 毛晓飞
     */
    private class ConnectThread(connector: FetalHeartConnector) : Thread() {
        private val mWeakReference = WeakReference(connector)
        private var tmp: BluetoothSocket? = null
        override fun run() {
            mWeakReference.get()?.also {
                //第一步，获取BluetoothSocket对象
                try {
                    tmp =
                        it.mBtDevice?.createInsecureRfcommSocketToServiceRecord(
                            MY_UUID
                        )
                } catch (e: IOException) {
                    it.notifyStatus(CONNECT_FAILED)
                }
                it.mBluetoothSocket = tmp
                //mBluetoothAdapter.cancelDiscovery();

                //第二步，BluetoothSocket连接
                try {
                    it.mBluetoothSocket?.connect()
                    it.notifyStatus(CONNECT_SUCCESS)
                    it.mNotifyHandler.sendEmptyMessage(MSG_CONNECT_FINISHED)
                } catch (e: IOException) {
                    it.notifyStatus(CONNECT_FAILED)
                } catch (e: Exception) {
                    it.notifyStatus(CONNECT_FAILED)
                }

                //第三步，获取BluetoothSocket输出流
                try {
                    it.mOutputStream = it.mBluetoothSocket?.outputStream
                } catch (e: Exception) {
                    it.mOutputStream = null
                    e.printStackTrace()
                }
            }

        }
    }

    class NotifyHandler(connector: FetalHeartConnector) : Handler() {
        private val weakReference = WeakReference(connector)

        override fun handleMessage(msg: Message) {
            weakReference.get()?.also { it ->
                when (msg.what) {
                    MSG_CONNECT_FINISHED -> {
                        //开始同步数据
                        it.isReading = true
                        it.mReadThread = ReadThread(it)
                        it.mReadThread?.start()
                    }
                    MSG_NOTIFY_STATUS -> if (it.mCallback != null) {
                        LogUtils.d("dispServiceStatus %s", (msg.obj as Int).toString())
                        it.mCallback?.dispServiceStatus(msg.obj as Int)
                    }
                    MSG_NOTIFY_DATA -> if (it.mCallback != null) {
                        it.mCallback?.dispInfor(msg.obj as FetalHeartData)
                    }
                    else -> {
                    }
                }
            }
        }
    }

    //=====================读取蓝牙数据线程========================
    /**
     * 读取蓝牙数据线程
     *
     */
    private class ReadThread(connector: FetalHeartConnector) : Thread() {
        private val weakReference = WeakReference(connector)
        private var mInputStream: InputStream? = null
        override fun run() {
            weakReference.get()?.also { service ->
                try {
                    //第一步，获取输入流
                    mInputStream = service.mBluetoothSocket?.inputStream
                    service.notifyStatus(READ_DATA_SUCCESS)

                    //第二步，每30毫秒读取一次数据
                    var len: Int
                    val buffer = ByteArray(2048)
                    while (service.isReading) {
                        try {
                            len = mInputStream?.read(buffer)!!
                            //将数据设置到胎心仪解析器
                            service.mLMTPDecoder.putData(buffer, 0, len)
                            try {
                                sleep(READ_INTERVAL.toLong())
                            } catch (e: InterruptedException) {
                                e.printStackTrace()
                            }
                        } catch (e: IOException) {
                            service.notifyStatus(READ_DATA_FAILED)
                            service.isReading = false
                        }
                    }
                } catch (e: IOException) {
                    service.notifyStatus(READ_DATA_FAILED)
                    service.isReading = false
                } finally {
                    if (mInputStream != null) {
                        IoUtils.closeSilently(mInputStream)
                    }
                    //关闭蓝牙套接字
                    if (service.mBluetoothSocket != null) {
                        IoUtils.closeSilently(service.mBluetoothSocket)
                    }
                }
            }

        }
    }

    /**
     * 服务的回调接口定义
     */
    interface Callback {
        /**
         * 主要显示监护数据信息
         *
         * @param data
         */
        fun dispInfor(data: FetalHeartData?)

        /**
         * 主要显示记录状态
         *
         * @param status
         */
        fun dispServiceStatus(status: Int)
    }
}