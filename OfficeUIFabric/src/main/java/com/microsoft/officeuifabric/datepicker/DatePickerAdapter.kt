/**
 * Copyright © 2018 Microsoft Corporation. All rights reserved.
 */

package com.microsoft.officeuifabric.datepicker

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.util.SimpleArrayMap
import android.support.v4.view.AccessibilityDelegateCompat
import android.support.v4.view.ViewCompat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import com.microsoft.officeuifabric.R
import com.microsoft.officeuifabric.datepicker.DatePickerDaySelectionDrawable.Mode
import com.microsoft.officeuifabric.managers.PreferencesManager
import com.microsoft.officeuifabric.util.DateTimeUtils
import org.threeten.bp.*
import org.threeten.bp.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * [DatePickerAdapter] is the adapter for the DatePicker
 */
class DatePickerAdapter : RecyclerView.Adapter<DatePickerAdapter.DatePickerDayViewHolder>, View.OnClickListener {
    companion object {
        private const val MONTH_BACK = 3L
        private const val MONTH_AHEAD = 12L
        private val DAY_IN_SECONDS = TimeUnit.DAYS.toSeconds(1).toInt()
    }

    /**
     * @return [LocalDate] the earliest date displayed
     */
    var minDate: LocalDate
        private set

    /**
     * @return [LocalDate] the selected date
     */
    var selectedDate: LocalDate? = null
        private set

    /**
     * @return [Int] the today's position
     */
    val todayPosition: Int
        get() = ChronoUnit.DAYS.between(minDate, ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS)).toInt() + 1

    private val LocalDate.getLocalDateToZonedDateTime: ZonedDateTime
        get() = ZonedDateTime.of(this, LocalTime.MIDNIGHT, ZoneId.systemDefault())

    private val firstDayOfWeekIndices = SimpleArrayMap<DayOfWeek, Int>(DayOfWeek.values().size)
    private val lastDayOfWeekIndices = SimpleArrayMap<DayOfWeek, Int>(DayOfWeek.values().size)

    private var firstDayOfWeek: DayOfWeek? = null
    private var dayCount: Int
    private val context: Context
    private val config: DatePickerView.Config
    private val listener: DateTimePickerListener

    private var selectedDuration: Duration? = null

    private val selectionDrawableCircle: DatePickerDaySelectionDrawable
    private val selectionDrawableStart: DatePickerDaySelectionDrawable
    private val selectionDrawableMiddle: DatePickerDaySelectionDrawable
    private val selectionDrawableEnd: DatePickerDaySelectionDrawable

    private val dayViewAccessibilityDelegate = DayViewAccessibilityDelegate()

    constructor(context: Context, config: DatePickerView.Config, listener: DateTimePickerListener) {
        this.context = context
        this.config = config
        this.listener = listener

        selectionDrawableCircle = DatePickerDaySelectionDrawable(this.context, Mode.SINGLE)
        selectionDrawableStart = DatePickerDaySelectionDrawable(this.context, Mode.START)
        selectionDrawableMiddle = DatePickerDaySelectionDrawable(this.context, Mode.MIDDLE)
        selectionDrawableEnd = DatePickerDaySelectionDrawable(this.context, Mode.END)

        updateDayIndicesAndHeading()

        val today = LocalDate.now()
        minDate = today.minusMonths(MONTH_BACK)
        minDate = minDate.minusDays(firstDayOfWeekIndices.get(minDate.dayOfWeek).toLong())

        var maxDate = today.plusMonths(MONTH_AHEAD)
        maxDate = maxDate.plusDays(lastDayOfWeekIndices.get(maxDate.dayOfWeek).toLong())

        dayCount = ChronoUnit.DAYS.between(minDate, maxDate).toInt() + 1
    }

    /**
     * Sets the selected date range
     */
    fun setSelectedDateRange(date: LocalDate?, duration: Duration) {
        if (selectedDate != null && selectedDuration != null && selectedDate == date && selectedDuration == duration) {
            return
        }

        val previousSelectedDate = selectedDate
        val previousSelectedDuration = selectedDuration

        selectedDate = date
        selectedDuration = duration

        if (date == null) {
            notifyDataSetChanged()
            return
        }

        val selectedDatePosition = ChronoUnit.DAYS.between(minDate, selectedDate).toInt()
        val selectedDateCount = (duration.seconds / DAY_IN_SECONDS).toInt() + 1
        notifyItemRangeChanged(selectedDatePosition, selectedDateCount)

        if (previousSelectedDuration != null && previousSelectedDate != null) {
            val datePosition = ChronoUnit.DAYS.between(minDate, previousSelectedDate).toInt()
            val dateCount = (previousSelectedDuration.seconds / DAY_IN_SECONDS).toInt() + 1
            notifyItemRangeChanged(datePosition, dateCount)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DatePickerDayViewHolder {
        val dayView = DatePickerDayView(parent.context, config)
        dayView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dayView.setOnClickListener(this)
        ViewCompat.setAccessibilityDelegate(dayView, dayViewAccessibilityDelegate)
        return DatePickerDayViewHolder(dayView)
    }

    override fun onBindViewHolder(holder: DatePickerDayViewHolder, position: Int) {
        val date = minDate.plusDays(position.toLong())
        holder.date = date

        val selectedDate = selectedDate ?: return
        val selectedDuration = selectedDuration ?: return

        val selectedDateEnd = LocalDateTime.of(selectedDate, LocalTime.MIDNIGHT).plus(selectedDuration).toLocalDate()
        holder.isSelected = DateTimeUtils.isBetween(date, selectedDate, selectedDateEnd)

        holder.selectedDrawable = when {
            date == null -> null
            date.isEqual(selectedDate) -> if (selectedDuration.toDays() < 1) selectionDrawableCircle else selectionDrawableStart
            date.isEqual(selectedDateEnd) -> selectionDrawableEnd
            else -> selectionDrawableMiddle
        }
    }

    override fun getItemCount() = dayCount

    override fun onClick(v: View) {
        listener.onDateSelected((v as DatePickerDayView).date.getLocalDateToZonedDateTime)
    }

    private fun updateDayIndicesAndHeading() {
        val weekStart = PreferencesManager.getWeekStart(context)
        if (weekStart == firstDayOfWeek)
            return

        firstDayOfWeek = weekStart

        var dayOfWeek = weekStart
        var i = 0
        while (i < 7) {
            firstDayOfWeekIndices.put(dayOfWeek, i)
            lastDayOfWeekIndices.put(dayOfWeek, 6 - i)
            dayOfWeek = dayOfWeek.plus(1)
            ++i
        }
    }

    /**
     * ViewHolder for the [DatePickerDayView]
     */
    inner class DatePickerDayViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        /**
         * Sets and gets the selected date in the [DatePickerDayView]
         */
        var date: LocalDate
            get() =  datePickerDayView.date
            set(value) {  datePickerDayView.date = value }

        /**
         * Sets and gets the selected Drawable in the [DatePickerDayView]
         */
        var selectedDrawable: Drawable?
            get() =  datePickerDayView.selectedDrawable
            set(value) { datePickerDayView.selectedDrawable = value }

        /**
         * Sets and gets the selected state of the [DatePickerDayView]
         */
        var isSelected: Boolean
            get() = datePickerDayView.isChecked
            set(value) { datePickerDayView.isChecked = value }

        private val datePickerDayView = itemView as DatePickerDayView
    }

    private inner class DayViewAccessibilityDelegate : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            // custom accessibility action only works for API level 21 (Lollipop) and higher (will be ignored in previous versions)
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.uifabric_action_goto_next_week,
                    host.resources.getString(R.string.accessibility_goto_next_week)
                )
            )
            info.addAction(
                AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    R.id.uifabric_action_goto_previous_week,
                    host.resources.getString(R.string.accessibility_goto_previous_week)
                )
            )
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle): Boolean {
            val selectedDate = selectedDate ?: return super.performAccessibilityAction(host, action, args)
            val date: LocalDate = when(action) {
                R.id.uifabric_action_goto_next_week -> selectedDate.plusDays(7)
                R.id.uifabric_action_goto_previous_week -> selectedDate.minusDays(7)
                else -> return super.performAccessibilityAction(host, action, args)
            }

            listener.onDateSelected(date.getLocalDateToZonedDateTime)
            return true
        }
    }
}