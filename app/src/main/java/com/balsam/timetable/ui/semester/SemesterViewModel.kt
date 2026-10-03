package com.balsam.timetable.ui.semester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.balsam.timetable.Graph
import com.balsam.timetable.data.db.Semester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SemesterViewModel : ViewModel() {
    private val repo = Graph.repository

    val semesters: StateFlow<List<Semester>> = repo.semesters()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val firstLoaded = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            repo.semesters().collect { if (!firstLoaded.value) firstLoaded.value = true }
        }
    }

    fun create(name: String, startDate: String, totalWeeks: Int, copySlotsFrom: Long?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.createSemester(
                name = name.trim(),
                startDate = startDate,
                totalWeeks = totalWeeks.coerceIn(1, 40),
                copySlotsFrom = copySlotsFrom,
                setCurrent = true,
            )
        }
    }

    fun update(s: Semester, name: String, startDate: String, totalWeeks: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.updateSemester(
                s.copy(name = name.trim(), startDate = startDate, totalWeeks = totalWeeks.coerceIn(1, 40))
            )
        }
    }

    fun setCurrent(id: Long) = viewModelScope.launch { repo.setCurrentSemester(id) }
    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch { repo.setArchived(id, archived) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteSemester(id) }
}
