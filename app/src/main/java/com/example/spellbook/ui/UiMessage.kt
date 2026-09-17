package com.example.spellbook.ui

import android.content.Context
import androidx.annotation.StringRes

/**
 * Сообщение для пользователя, не привязанное к языку.
 *
 * ViewModel не может резолвить строки сама: язык интерфейса выбирается в настройках
 * и применяется только на уровне композиции. Поэтому хранится идентификатор ресурса
 * и аргументы, а текст собирается непосредственно перед показом.
 *
 * @param args подстановки формата; строковые аргументы (имена заклинаний, персонажей)
 * остаются на языке оригинала — это пользовательский контент.
 */
data class UiMessage(@param:StringRes val resId: Int, val args: List<Any> = emptyList()) {

    /** Собирает текст сообщения в контексте с нужной локалью. */
    fun resolve(context: Context): String = context.getString(resId, *args.toTypedArray())

    companion object {
        /** Короткая запись для сообщений с подстановками. */
        fun of(@StringRes resId: Int, vararg args: Any) = UiMessage(resId, args.toList())
    }
}
