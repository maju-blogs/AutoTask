package top.xjunz.tasker.task.applet.flow

import android.os.Handler
import android.os.Looper
import android.os.Message
import top.xjunz.shared.ktx.casted
import top.xjunz.tasker.engine.AutomatorTask
import top.xjunz.tasker.engine.applet.base.Flow
import top.xjunz.tasker.engine.applet.serialization.AppletValues
import top.xjunz.tasker.engine.runtime.FlowRuntime

/**
 * @author xjunz 2022/11/15
 */
class DelayFlow : Flow() {

    override val valueType: Int = AppletValues.VAL_TYPE_INT

    override fun staticCheckMySelf() {
        super.staticCheckMySelf()
        check(value != null && value!!.casted<Int>() > 0) {
            "Delay must not be 0!"
        }
    }

    override fun doApply(task: AutomatorTask, runtime: FlowRuntime) {
        val callSuper = {
            super.doApply(task, runtime)
        }
        // Every delay flow has its handler
        var handler = task.handlers[hashCode()]
        // Remove previous messages
        handler?.removeMessages(id)
        handler = object : Handler(Looper.myLooper()!!) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.what != id) return
                callSuper()
            }
        }
        task.handlers[hashCode()] = handler
        handler.sendEmptyMessageDelayed(id, value!!.casted())
    }

}