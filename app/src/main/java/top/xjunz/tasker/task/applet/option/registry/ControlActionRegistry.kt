/*
 * Copyright (c) 2022 xjunz. All rights reserved.
 */

package top.xjunz.tasker.task.applet.option.registry

import top.xjunz.tasker.R
import top.xjunz.tasker.engine.applet.action.Break
import top.xjunz.tasker.engine.applet.action.Repeat
import top.xjunz.tasker.engine.applet.action.Suspension
import top.xjunz.tasker.engine.applet.base.If
import top.xjunz.tasker.engine.applet.base.WaitFor
import top.xjunz.tasker.engine.applet.base.WaitUntil
import top.xjunz.tasker.ktx.clickable
import top.xjunz.tasker.ktx.foreColored
import top.xjunz.tasker.ktx.formatSpans
import top.xjunz.tasker.task.applet.anno.AppletOrdinal
import top.xjunz.tasker.task.applet.option.AppletOption
import top.xjunz.tasker.task.applet.value.VariantType
import top.xjunz.tasker.ui.main.EventCenter
import top.xjunz.tasker.util.formatMinSecMills

/**
 * @author xjunz 2022/12/04
 */
class ControlActionRegistry(id: Int) : AppletOptionRegistry(id) {

    @AppletOrdinal(0x00_00)
    val ifAction = appletOption(R.string._if) { If() }

    @AppletOrdinal(0x00_01)
    val waitUntilAction = appletOption(R.string.wait_until) {
        WaitUntil()
    }.withValueArgument<Int>(R.string.wait_timeout, VariantType.INT_INTERVAL)
        .withHelperText(R.string.tip_wait_timeout)
        .withValueDescriber<Int> {
            R.string.format_max_wait_duration.formatSpans(formatMinSecMills(it).foreColored())
        }

    @AppletOrdinal(0x0002)
    val waitForFlow = appletOption(R.string.wait_for_event) {
        WaitFor()
    }.withValueArgument<Int>(R.string.wait_timeout, VariantType.INT_INTERVAL)
        .withHelperText(R.string.tip_wait_timeout)
        .withValueDescriber<Int> {
            R.string.format_max_wait_duration.formatSpans(formatMinSecMills(it).foreColored())
        }

    @AppletOrdinal(0x00_03)
    val suspension = appletOption(R.string.delay) {
        Suspension()
    }.withValueArgument<Int>(R.string.delay_interval, VariantType.INT_INTERVAL)
        .withDescriber<Int> { applet, t ->
            R.string.format_delay.formatSpans(formatMinSecMills(t!!).foreColored().clickable {
                AppletOption.deliverEvent(it, AppletOption.EVENT_EDIT_VALUE)
                EventCenter.sendEvent(AppletOption.EVENT_EDIT_VALUE, applet)
            })
        }.descAsTitle()

    @AppletOrdinal(0x00_04)
    val repeatFlow = appletOption(R.string.loop) {
        Repeat()
    }.withDescriber<Int> { applet, t ->
        R.string.format_repeat.formatSpans(t.toString().foreColored().clickable {
            AppletOption.deliverEvent(it, AppletOption.EVENT_EDIT_VALUE)
            EventCenter.sendEvent(AppletOption.EVENT_EDIT_VALUE, applet)
        })
    }.descAsTitle().withHelperText(R.string.input_repeat_count)
        .withResult<Repeat>(R.string.loop)

    @AppletOrdinal(0x00_05)
    val breakAction = appletOption(R.string.break_loop) {
        Break()
    }.withRefArgument<Repeat>(R.string.loop)
        .hasCompositeTitle()
}