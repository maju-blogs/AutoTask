package top.xjunz.tasker.service

import android.util.Log
import android.widget.ArrayAdapter
import cn.hutool.core.io.IoUtil
import cn.hutool.core.util.HexUtil
import cn.hutool.core.util.IdUtil
import cn.hutool.core.util.StrUtil
import cn.hutool.json.JSONObject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import top.xjunz.shared.trace.logcatStackTrace
import top.xjunz.tasker.R
import top.xjunz.tasker.app
import top.xjunz.tasker.engine.dto.XTaskDTO
import top.xjunz.tasker.engine.dto.toDTO
import top.xjunz.tasker.engine.task.XTask
import top.xjunz.tasker.ktx.toastUnexpectedError
import top.xjunz.tasker.service.controller.ShizukuAutomatorServiceController
import top.xjunz.tasker.task.applet.option.AppletOptionFactory
import top.xjunz.tasker.task.runtime.ITaskCompletionCallback
import top.xjunz.tasker.task.runtime.LocalTaskManager
import top.xjunz.tasker.task.storage.TaskStorage


class HandleMqttMsg {
    enum class MsgType(t: String) {

        DO_TASK("1"),
        ADD_TASK("2"),
        UPLOAD_RESULT("3"),
        UPLOAD_DATA("4"),
        UPLOAD_TASK("5");

        var type: String = t

    }

    companion object {

        val TAG = "HandleMqttMsg"

        fun sendMsg(msg: String, type: MsgType) {
            var jsonObject: JSONObject? = null
            try {
                jsonObject = JSONObject(msg)
            } catch (e: Exception) {
                Log.e(TAG, "data error")
            }
            if (null == jsonObject) {
                return
            }
            var taskSnr = jsonObject.getStr("taskSnr")
            val data = jsonObject.getStr("data")

            if (StrUtil.isEmpty(taskSnr)) {
                taskSnr = IdUtil.fastSimpleUUID()
            }
            val result = JSONObject()
            result.set("snr", taskSnr)
            result.set("type", type.type)
            if (StrUtil.isEmpty(data)) {
                result.set("data", msg)
            } else {
                result.set("data", data)
            }
            myMqttService.publishMessage("android-topic/out", result.toString())
        }

        fun handMsg(data: String) {
            var jsonObject: JSONObject? = null
            try {
                jsonObject = JSONObject(data)
            } catch (e: Exception) {
                Log.e(TAG, "msg not handle")
            }
            if (null == jsonObject) {
                return
            }
            val type = jsonObject.getStr("type")
            val data = jsonObject.getStr("data")
            val taskSnr = jsonObject.getStr("taskSnr")
            if (type == MsgType.DO_TASK.type) {
                doTask(data, taskSnr)
                return
            }
            if (type == MsgType.ADD_TASK.type) {
                addTask(data, taskSnr)
                return
            }
        }

        private fun doTask(taskId: String, taskSnr: String) {
            val allTasks = TaskStorage.getAllTasks()
            val get = allTasks.stream().filter { item -> taskId == item.taskId }
                .findFirst()
            if (!get.isPresent) {
                myMqttService.publishMessage(
                    "android-topic/out",
                    ResultData(taskSnr, false).toString()
                )
                return
            }
            var task = get.get()
            task.metadata.taskSnr = taskSnr
            taskCompletionCallback.setSnr(taskSnr)
            ShizukuAutomatorServiceController.remoteService?.taskManager?.addOneshotTaskIfAbsent(
                task.toDTO()
            )
            ShizukuAutomatorServiceController.remoteService?.scheduleOneshotTask(
                task.checksum,
                taskCompletionCallback
            )
        }


        val adapter: ArrayAdapter<XTask> = ArrayAdapter(app, R.string.add_task)
        val taskList: ArrayList<XTask> = ArrayList()

        @OptIn(ExperimentalSerializationApi::class, DelicateCoroutinesApi::class)
        private fun addTask(data: String, taskSnr: String) {
            val decodeHex = HexUtil.decodeHex(data)
            val dto = Json.decodeFromStream<XTaskDTO>(IoUtil.toStream(decodeHex))
            var taskId = dto.metadata.taskId
            if (StrUtil.isEmpty(taskId)) {
                dto.metadata.taskId = IdUtil.fastSimpleUUID()
                taskId = dto.metadata.taskId
            }
            val task = dto.toXTask(AppletOptionFactory, true)
            val first =
                TaskStorage.getAllTasks().stream().filter { it.taskId.equals(taskId) }.findFirst()
            GlobalScope.launch {
                if (first.isPresent) {
                    val currentChecksum = task.checksum
                    var removed = false
                    try {
                        TaskStorage.removeTask(first.get())
                        removed = true
                    } catch (t: Throwable) {
                        t.logcatStackTrace()
                        toastUnexpectedError(t)
                    }
                    // Restore checksum to current one
                    task.metadata.checksum = currentChecksum
                    if (!removed) return@launch
                    try {
                        TaskStorage.persistTask(task)
                        TaskStorage.addTask(task)
                        LocalTaskManager.updateTask(task.metadata.checksum, task)
                    } catch (t: Throwable) {
                        toastUnexpectedError(t)
                    }
                } else {
                    TaskStorage.persistTask(task)
                    TaskStorage.addTask(task)
                    LocalTaskManager.enableResidentTask(task)
                }
            }

            myMqttService.publishMessage(
                "android-topic/out",
                ResultData(taskSnr, true).toString()
            )
            taskList.add(task)
            adapter.notifyDataSetChanged()
        }


        private val taskCompletionCallback by lazy {
            var snr = ""

            object : ITaskCompletionCallback.Stub() {
                override fun onTaskCompleted(isSuccessful: Boolean) {
                    myMqttService.publishMessage(
                        "android-topic/out",
                        ResultData(snr, isSuccessful).toString()
                    )
                }

                fun setSnr(data: String) {
                    snr = data
                }
            }
        }
    }


    class ResultData(var snr: String, var isSuccessful: Boolean) {
        override fun toString(): String {
            return "{'snr':'$snr', 'data':$isSuccessful,'type':${MsgType.UPLOAD_RESULT.type}}"
        }
    }

}