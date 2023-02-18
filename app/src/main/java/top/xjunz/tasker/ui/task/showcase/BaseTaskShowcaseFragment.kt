/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.ui.task.showcase

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.transition.platform.MaterialSharedAxis
import top.xjunz.tasker.R
import top.xjunz.tasker.databinding.FragmentTaskShowcaseBinding
import top.xjunz.tasker.databinding.ItemTaskShowcaseBinding
import top.xjunz.tasker.engine.task.XTask
import top.xjunz.tasker.ktx.*
import top.xjunz.tasker.service.serviceController
import top.xjunz.tasker.task.runtime.LocalTaskManager
import top.xjunz.tasker.task.runtime.LocalTaskManager.isEnabled
import top.xjunz.tasker.ui.base.BaseFragment
import top.xjunz.tasker.ui.main.ColorScheme
import top.xjunz.tasker.ui.main.MainViewModel.Companion.peekMainViewModel
import top.xjunz.tasker.util.ClickUtil.setAntiMoneyClickListener

/**
 * @author xjunz 2022/12/16
 */
abstract class BaseTaskShowcaseFragment : BaseFragment<FragmentTaskShowcaseBinding>() {

    override val bindingRequiredSuperClassDepth: Int = 2

    protected val viewModel by lazy {
        requireParentFragment().viewModels<TaskShowcaseViewModel>().value
    }

    protected val taskList = mutableListOf<XTask>()

    protected val adapter = TaskAdapter()

    abstract fun initTaskList(): List<XTask>

    @SuppressLint("ClickableViewAccessibility")
    protected inner class TaskViewHolder(val binding: ItemTaskShowcaseBinding) :
        ViewHolder(binding.root) {
        init {
            binding.msEnabled.setOnInteractiveCheckedChangedListener { v, isChecked ->
                // Do not toggle the Switch instantly, because we want a confirmation.
                v.isChecked = !isChecked
                viewModel.requestToggleTask.value = taskList[adapterPosition]
            }
            binding.ibDelete.setAntiMoneyClickListener {
                viewModel.requestDeleteTask.value = taskList[adapterPosition]
            }
            binding.ibTrack.setAntiMoneyClickListener {
                val task = taskList[adapterPosition]
                if (LocalTaskManager.getSnapshotCount(task) == 0) {
                    toast(R.string.no_task_snapshots)
                    return@setAntiMoneyClickListener
                }
                viewModel.requestTrackTask.value = task
            }
            binding.ibEdit.setAntiMoneyClickListener {
                viewModel.requestEditTask.value = taskList[adapterPosition] to null
            }
        }
    }


    protected inner class TaskAdapter : RecyclerView.Adapter<TaskViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            return TaskViewHolder(
                ItemTaskShowcaseBinding.inflate(
                    requireActivity().layoutInflater, parent, false
                )
            )
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = taskList[position]
            val metadata = task.metadata
            val b = holder.binding
            b.msEnabled.isChecked = task.isEnabled
            b.tvTaskName.text = metadata.title
            b.tvTaskDesc.text = metadata.description
            b.tvAuthor.text = metadata.author
            if (metadata.description.isNullOrEmpty()) {
                b.tvTaskDesc.text = R.string.no_desc_provided.text
                b.tvTaskDesc.isEnabled = false
            } else {
                b.tvTaskDesc.text = metadata.description
                b.tvTaskDesc.isEnabled = true
            }
            b.tvBadge.isVisible = task.isPreload
            b.ibTrack.isVisible = false
            b.container.strokeColor = com.google.android.material.R.attr.colorOutline.attrColor
            if (task.isEnabled) {
                b.msEnabled.setText(R.string.is_enabled)
                if (serviceController.isServiceRunning) {
                    b.wave.fadeIn()
                    b.ibTrack.isVisible = true
                    b.container.strokeColor = ColorScheme.colorPrimary
                    b.container.cardElevation = 1.dpFloat
                } else {
                    b.wave.fadeOut()
                }
            } else {
                b.msEnabled.setText(R.string.not_is_enabled)
                b.wave.fadeOut()
            }
            if (viewModel.isPaused.isTrue) {
                b.wave.pause()
            } else {
                b.wave.resume()
            }
        }

        override fun getItemCount() = taskList.size

    }

    protected fun togglePlaceholder(visible: Boolean) {
        binding.root.beginAutoTransition(MaterialFadeThrough())
        binding.groupPlaceholder.isVisible = visible
    }

    fun getScrollTarget(): RecyclerView? {
        return if (isAdded) binding.rvTaskList else null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observe(viewModel.appbarHeight) {
            binding.rvTaskList.updatePadding(top = it)
        }
        observe(viewModel.paddingBottom) {
            binding.rvTaskList.updatePadding(bottom = it)
        }
        val mvm = peekMainViewModel()
        observe(mvm.allTaskLoaded) {
            taskList.clear()
            taskList.addAll(initTaskList())
            if (viewModel.paddingBottom.isNull()) {
                binding.rvTaskList.doOnPreDraw {
                    binding.rvTaskList.beginAutoTransition(
                        MaterialSharedAxis(MaterialSharedAxis.Z, true)
                    )
                    binding.rvTaskList.adapter = adapter
                }
            } else {
                binding.rvTaskList.adapter = adapter
            }
            if (taskList.isEmpty()) togglePlaceholder(true)
        }
        observe(mvm.isServiceRunning) {
            adapter.notifyItemRangeChanged(0, taskList.size, 0)
        }
        observeTransient(viewModel.onTaskDeleted) {
            val index = taskList.indexOf(it)
            if (index > -1) {
                taskList.removeAt(index)
                adapter.notifyItemRemoved(index)
                if (taskList.isEmpty()) togglePlaceholder(true)
            }
        }
        observeTransient(viewModel.onTaskToggled) {
            adapter.notifyItemChanged(taskList.indexOf(it), true)
        }
        observeTransient(viewModel.onTaskUpdated) {
            adapter.notifyItemChanged(taskList.indexOf(it), true)
        }
        observe(viewModel.isPaused) {
            adapter.notifyItemRangeChanged(0, taskList.size, true)
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.isPaused.value = true
    }

    override fun onResume() {
        super.onResume()
        viewModel.isPaused.value = false
    }
}