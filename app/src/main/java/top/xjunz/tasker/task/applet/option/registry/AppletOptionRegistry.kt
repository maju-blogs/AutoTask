package top.xjunz.tasker.task.applet.option.registry

import top.xjunz.shared.utils.unsupportedOperation
import top.xjunz.tasker.engine.applet.base.Applet
import top.xjunz.tasker.task.applet.anno.AppletCategory
import top.xjunz.tasker.task.applet.option.AppletOption

/**
 * The abstract registry storing [AppletOption]s.
 *
 * @author xjunz 2022/08/11
 */
abstract class AppletOptionRegistry(val id: Int) {

    abstract val categoryNames: IntArray?

    private fun parseDeclaredOptions(): List<AppletOption> {
        return javaClass.declaredFields.mapNotNull m@{
            val anno = it.getDeclaredAnnotation(AppletCategory::class.java) ?: return@m null
            val accessible = it.isAccessible
            it.isAccessible = true
            val option = it.get(this) as AppletOption
            option.categoryId = anno.categoryId
            it.isAccessible = accessible
            return@m option
        }.sorted()
    }

    private fun appletCategoryOption(label: Int): AppletOption {
        return invertibleAppletOption(-1, label, AppletOption.TITLE_NONE) {
            unsupportedOperation()
        }
    }

    protected inline fun invertibleAppletOption(
        appletId: Int,
        title: Int,
        invertedTitle: Int = AppletOption.TITLE_AUTO_INVERTED,
        crossinline creator: () -> Applet
    ): AppletOption {
        return object : AppletOption(appletId, id, title, invertedTitle) {
            override fun rawCreateApplet(): Applet {
                return creator.invoke()
            }
        }
    }

    protected inline fun appletOption(
        appletId: Int,
        title: Int,
        crossinline creator: () -> Applet
    ): AppletOption {
        return object : AppletOption(appletId, id, title, TITLE_NONE) {
            override fun rawCreateApplet(): Applet {
                return creator.invoke()
            }
        }
    }

    val allOptions: List<AppletOption> by lazy {
        parseDeclaredOptions()
    }

    val categorizedOptions: List<AppletOption> by lazy {
        val ret = ArrayList<AppletOption>()
        var previousCategory = -1
        allOptions.forEach {
            if (it.categoryIndex != previousCategory) {
                val name = categoryNames?.getOrNull(it.categoryIndex)
                if (name != null && name != AppletOption.TITLE_NONE) {
                    ret.add(appletCategoryOption(name))
                }
            }
            ret.add(it)
            previousCategory = it.categoryIndex
        }
        return@lazy ret
    }

    fun createAppletFromId(id: Int): Applet {
        return allOptions.first { it.appletId == id }.yieldApplet()
    }

    fun findAppletOptionById(id: Int): AppletOption {
        return allOptions.first { it.appletId == id }
    }
}