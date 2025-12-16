package com.aryansdevstudios.classclock

import com.google.gson.annotations.SerializedName

data class TimetableResponse(@SerializedName("timetables") val timetables: List<Timetable>)

data class Timetable(
    @SerializedName("dayOfWeek") val dayOfWeek: String,
    @SerializedName("periods") val periods: List<Period>
)

data class Period(
    @SerializedName("periodName") val periodName: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String
)
