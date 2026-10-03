package com.balsam.timetable.ui.slots

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.Semester
import com.balsam.timetable.data.db.TimeSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TimeSlotsViewModel : ViewModel() {
    private val repo = Graph.repository

    data class UiState(
        val loading: Boolean = true,
        val semester: Semester? = null,
        val slots: List<TimeSlot> = emptyList(),
        val saved: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        viewModelScope.launch {
            val semester = repo.currentSemesterOnce()
            if (semester == null) {
                _ui.value = _ui.value.copy(loading = false)
            } else {
                val slots = repo.slotsOnce(semester.id)
                _ui.value = UiState(loading = false, semester = semester, slots = slots)
            }
        }
    }

    fun updateSlot(slot: TimeSlot, start: String, end: String) {
        viewModelScope.launch {
            repo.updateSlot(slot.copy(startTime = start, endTime = end))
            reload()
        }
    }

    fun addSlot() {
        val s = _ui.value
        val semester = s.semester ?: return
        viewModelScope.launch {
            repo.addSlot(semester.id, "08:00", "08:45")
            reload()
        }
    }

    fun removeSlot(slot: TimeSlot) {
        viewModelScope.launch {
            repo.removeSlot(slot)
            reload()
        }
    }

    private suspend fun reload() {
        val semester = repo.currentSemesterOnce() ?: return
        _ui.value = _ui.value.copy(slots = repo.slotsOnce(semester.id), saved = true)
    }
}
