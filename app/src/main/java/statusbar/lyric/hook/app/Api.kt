/*
 * StatusBarLyric
 * Copyright (C) 2021-2022 fkj@fkj233.cn
 * https://github.com/577fkj/StatusBarLyric
 *
 * This software is free opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by 577fkj.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/577fkj/StatusBarLyric/blob/main/LICENSE>.
 */

package statusbar.lyric.hook.app

import android.content.Context
import android.content.Intent
import statusbar.lyric.utils.LogUtils
import statusbar.lyric.utils.Utils
import statusbar.lyric.utils.ktx.findClassOrNull
import statusbar.lyric.utils.ktx.hookAfterMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage
import statusbar.lyric.hook.BaseHook
import statusbar.lyric.utils.AppCenterUtils

class Api(private val lpparam: XC_LoadPackage.LoadPackageParam) {
    fun hook() {
        if ("StatusBarLyric.API.StatusBarLyric".findClassOrNull(lpparam.classLoader) == null) return
        AppCenterUtils(Utils.appCenterKey, lpparam)
        "StatusBarLyric.API.StatusBarLyric".hookAfterMethod("hasEnable", classLoader = lpparam.classLoader) {
            it.result = true
        }
        "StatusBarLyric.API.StatusBarLyric".hookAfterMethod("sendLyric", Context::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType, classLoader = lpparam.classLoader) {
            LogUtils.e("API: " + it.args[1])
            Utils.sendLyric(
                it.args[0] as Context,
                it.args[1] as String,
                it.args[2] as String,
                it.args[4] as Boolean,
                it.args[3] as String
            )
        }
        "StatusBarLyric.API.StatusBarLyric".hookAfterMethod("stopLyric", Context::class.java, classLoader = lpparam.classLoader) {
            (it.args[0] as Context).sendBroadcast(
                Intent().apply {
                    action = "Lyric_Server"
                    putExtra("Lyric_Type", "app_stop")
                }
            )
        }
    }
}