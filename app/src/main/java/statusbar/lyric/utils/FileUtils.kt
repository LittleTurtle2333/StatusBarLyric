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

@file:Suppress("DEPRECATION")

package statusbar.lyric.utils

import android.content.ContentResolver
import android.provider.MediaStore
import android.content.CursorLoader
import android.os.Build
import android.provider.DocumentsContract
import android.os.Environment
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import java.lang.Exception
import java.lang.IllegalArgumentException

class FileUtils(private val context: Context) {
    fun getFilePathByUri(uri: Uri): String? {
        // 以 file:// 开头的
        if (ContentResolver.SCHEME_FILE == uri.scheme) {
            return uri.path
        }
        // 以/storage开头的也直接返回
        if (isOtherDocument(uri)) {
            return uri.path
        }
        // 版本兼容的获取！
        var path = getFilePathByUriBELOWAPI11(uri)
        if (path != null) {
            return path
        }
        path = getFilePathByUriAPI11to18(uri)
        if (path != null) {
            return path
        }
        path = getFilePathByUriAPI19(uri)
        return path
    }

    private fun getFilePathByUriBELOWAPI11(uri: Uri): String? {
        if (ContentResolver.SCHEME_CONTENT == uri.scheme) {
            var path: String? = null
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    if (columnIndex > -1) {
                        path = cursor.getString(columnIndex)
                    }
                }
                cursor.close()
            }
            return path
        }
        return null
    }

    private fun getFilePathByUriAPI11to18(contentUri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        var result: String? = null
        val cursorLoader = CursorLoader(context, contentUri, projection, null, null, null)
        val cursor = cursorLoader.loadInBackground()
        if (cursor != null) {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToFirst()
            result = cursor.getString(columnIndex)
            cursor.close()
        }
        return result
    }

    private fun getFilePathByUriAPI19(uri: Uri): String? {
        if (ContentResolver.SCHEME_CONTENT == uri.scheme && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                if (isExternalStorageDocument(uri)) {
                    // ExternalStorageProvider
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return if (split.size > 1) {
                            Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                        } else {
                            Environment.getExternalStorageDirectory().toString() + "/"
                        }
                        // This is for checking SD Card
                    }
                } else if (isDownloadsDocument(uri)) {
                    //下载内容提供者时应当判断下载管理器是否被禁用
                    val stateCode =
                        context.packageManager.getApplicationEnabledSetting("com.android.providers.downloads")
                    if (stateCode != 0 && stateCode != 1) {
                        return null
                    }
                    var id = DocumentsContract.getDocumentId(uri)
                    // 如果出现这个RAW地址，我们则可以直接返回!
                    if (id.startsWith("raw:")) {
                        return id.replaceFirst("raw:".toRegex(), "")
                    }
                    if (id.contains(":")) {
                        val tmp = id.split(":").toTypedArray()
                        if (tmp.size > 1) {
                            id = tmp[1]
                        }
                    }
                    var contentUri = Uri.parse("content://downloads/public_downloads")
                    try {
                        contentUri = ContentUris.withAppendedId(contentUri!!, id.toLong())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    var path = getDataColumn(contentUri, null, null)
                    if (path != null) return path
                    // 兼容某些特殊情况下的文件管理器!
                    val fileName = getFileNameByUri(uri)
                    if (fileName != null) {
                        path = Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                        return path
                    }
                } else if (isMediaDocument(uri)) {
                    // MediaProvider
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split = docId.split(":").toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    when (type) {
                        "image" -> {
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        }
                        "video" -> {
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        }
                        "audio" -> {
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        }
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    return getDataColumn(contentUri, selection, selectionArgs)
                }
            }
        }
        return null
    }

    private fun getFileNameByUri(uri: Uri): String? {
        var relativePath = getFileRelativePathByUriAPI18(uri)
        if (relativePath == null) relativePath = ""
        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME
        )
        context.contentResolver.query(uri, projection, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                return relativePath + cursor.getString(index)
            }
        }
        return null
    }

    private fun getFileRelativePathByUriAPI18(uri: Uri): String? {
        val projection: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = arrayOf(
                MediaStore.MediaColumns.RELATIVE_PATH
            )
            context.contentResolver.query(uri, projection, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    return cursor.getString(index)
                }
            }
        }
        return null
    }

    private fun getDataColumn(uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        val column = MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        try {
            context.contentResolver.query(uri!!, projection, selection, selectionArgs, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(column)
                    return cursor.getString(columnIndex)
                }
            }
        } catch (iae: IllegalArgumentException) {
            iae.printStackTrace()
        }
        return null
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isOtherDocument(uri: Uri?): Boolean {
        // 以/storage开头的也直接返回
        if (uri != null && uri.path != null) {
            val path = uri.path
            if (path!!.startsWith("/storage")) {
                return true
            }
            if (path.startsWith("/external_files")) {
                return true
            }
        }
        return false
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
}