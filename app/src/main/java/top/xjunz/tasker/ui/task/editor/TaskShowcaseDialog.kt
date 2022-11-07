package top.xjunz.tasker.ui.task.editor

import android.os.Bundle
import android.view.View
import androidx.core.view.updatePadding
import top.xjunz.tasker.databinding.DialogTaskShowcaseBinding
import top.xjunz.tasker.ktx.applySystemInsets
import top.xjunz.tasker.ktx.show
import top.xjunz.tasker.ui.base.BaseDialogFragment

/**
 * @author xjunz 2022/07/30
 */
class TaskShowcaseDialog : BaseDialogFragment<DialogTaskShowcaseBinding>() {

    override val isFullScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.topBar.applySystemInsets { v, insets ->
            v.updatePadding(top = insets.top)
        }
        binding.ibCreateTask.setOnClickListener {
            TaskEditorDialog().show(parentFragmentManager)
        }
    }

}