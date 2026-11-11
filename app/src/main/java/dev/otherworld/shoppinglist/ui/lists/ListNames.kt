package dev.otherworld.shoppinglist.ui.lists

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The name a new list gets when it's left blank: the day it was made, as the phone writes dates. */
fun defaultListName(today: LocalDate, locale: Locale): String =
    today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
