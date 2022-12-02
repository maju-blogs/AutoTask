package top.xjunz.tasker.ui.task.editor

import android.graphics.Canvas
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import top.xjunz.shared.ktx.casted
import top.xjunz.tasker.R
import top.xjunz.tasker.engine.applet.base.Applet
import top.xjunz.tasker.engine.applet.base.ControlFlow
import top.xjunz.tasker.engine.applet.base.Flow
import top.xjunz.tasker.ktx.format
import top.xjunz.tasker.ktx.toast
import top.xjunz.tasker.task.applet.flatSize
import top.xjunz.tasker.task.applet.isContainer
import top.xjunz.tasker.task.applet.isDescendantOf
import java.util.*

/**
 * @author xjunz 2022/11/08
 */
open class FlowItemTouchHelperCallback(
    private val rv: RecyclerView,
    private val viewModel: FlowViewModel
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
) {

    init {
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                firstVisiblePosition = -1
                lastVisiblePosition = -1
            }
        })
    }

    object DiffCallback : DiffUtil.ItemCallback<Applet>() {
        override fun areItemsTheSame(oldItem: Applet, newItem: Applet): Boolean {
            return oldItem === newItem
        }

        override fun areContentsTheSame(oldItem: Applet, newItem: Applet): Boolean {
            return true
        }
    }

    private val adapter by lazy {
        rv.adapter!!.casted<ListAdapter<Applet, *>>().apply {
            registerAdapterDataObserver(object : AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    firstVisiblePosition = -1
                    lastVisiblePosition = -1
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    firstVisiblePosition = -1
                    lastVisiblePosition = -1
                }
            })
        }
    }

    protected val Applet.isCollapsed: Boolean get() = viewModel.isCollapsed(this)

    protected val currentList: List<Applet> get() = adapter.currentList

    protected open fun onMoveEnded(hasDragged: Boolean, position: Int) {
        /* no-op */
    }

    protected open fun shouldBeInvolvedInSwipe(next: Applet, origin: Applet): Boolean {
        if (origin is Flow && !origin.isCollapsed) {
            return next.isDescendantOf(origin)
        }
        return false
    }

    protected open fun doRemove(parent: Flow, from: Applet): Int {
        parent.remove(from)
        return if (from is Flow) from.flatSize else 1
    }

    private val layoutManager: LinearLayoutManager by lazy {
        rv.layoutManager!!.casted()
    }

    private val pendingChangedApplets = mutableSetOf<Applet>()

    private var hasDragged: Boolean? = null
    private var hasSwapped: Boolean = false

    private var firstVisiblePosition = -1
    private var lastVisiblePosition = -1

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        val position = viewHolder.adapterPosition
        if (position == RecyclerView.NO_POSITION) return
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            if (dY != 0F || dX != 0F) {
                hasDragged = true
            } else if (hasDragged != true) {
                hasDragged = false
            }
        } else if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {

            if (firstVisiblePosition == -1)
                firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
            if (lastVisiblePosition == -1)
                lastVisiblePosition = layoutManager.findLastVisibleItemPosition()

            for (i in firstVisiblePosition..lastVisiblePosition) {
                if (i == position) continue
                val vh = recyclerView.findViewHolderForAdapterPosition(i)
                if (vh != null
                    && shouldBeInvolvedInSwipe(
                        currentList[vh.adapterPosition], currentList[position]
                    )
                ) {
                    vh.itemView.translationX = dX
                }
            }
        }
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.adapterPosition
        val fromApplet = currentList[from]
        val to = target.adapterPosition
        val toApplet = currentList[to]
        Collections.swap(fromApplet.requireParent(), fromApplet.index, toApplet.index)
        hasSwapped = true
        if (fromApplet.index == 0 || toApplet.index == 0) {
            pendingChangedApplets.add(fromApplet)
            pendingChangedApplets.add(toApplet)
        }
        if (fromApplet is Flow && !fromApplet.isContainer) {
            viewModel.notifyFlowChanged()
        } else {
            viewModel.regenerateApplets()
            adapter.notifyItemMoved(from, to)
        }
        return true
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        try {
            val adapterPosition = viewHolder.adapterPosition
            if (hasDragged != null)
                onMoveEnded(hasDragged!!, adapterPosition)
            if (adapterPosition == RecyclerView.NO_POSITION) return
            if (recyclerView.isComputingLayout) return
            if (hasSwapped) {
                // Update indexes
                val target = currentList[adapterPosition]
                if (target !is Flow || target.isContainer) {
                    val parent = target.requireParent()
                    adapter.notifyItemRangeChanged(
                        adapterPosition - target.index, parent.size, true
                    )
                }
                // Update relation text
                pendingChangedApplets.forEach {
                    notifyAppletChanged(it)
                }
            }
        } finally {
            hasDragged = null
            hasSwapped = false
            pendingChangedApplets.clear()
        }
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = current.adapterPosition
        if (from == RecyclerView.NO_POSITION) return false
        val to = target.adapterPosition
        if (to == RecyclerView.NO_POSITION) return false
        if (currentList[from] is ControlFlow) return false
        if (currentList[from].parent != currentList[to].parent) return false
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val pos = viewHolder.adapterPosition
        val from = currentList[pos]
        val parent = from.requireParent()
        val count = doRemove(parent, from)
        if (count == 0) {
            toast(R.string.removed)
        } else {
            toast(R.string.format_applet_is_removed.format(count))
        }
        parent.forEachIndexed { index, applet ->
            applet.index = index
        }
        if (from is Flow) {
            // Update relation text
            if (from.index == 0 && parent.isNotEmpty())
                notifyAppletChanged(parent.first())
        } else
        // Update indexes
            parent.forEach {
                notifyAppletChanged(it)
            }
        viewModel.notifyFlowChanged()
    }

    private fun notifyAppletChanged(applet: Applet) {
        adapter.notifyItemChanged(currentList.indexOf(applet))
    }
}