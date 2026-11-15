package com.hostelattendance.data.local

/**
 * In-memory state holder that preserves UI position, selected filters, and scroll locations
 * across tab switches throughout the app session.
 */
object AppStateMemory {

    object AttendanceTab {
        var selectedWingId: String? = null
        var selectedDateString: String? = null
        var searchQuery: String = ""

        // Map of wingId -> Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        val wingScrollPositions = mutableMapOf<String, Pair<Int, Int>>()

        fun getScrollPosition(wingId: String): Pair<Int, Int> {
            return wingScrollPositions[wingId] ?: Pair(0, 0)
        }

        fun saveScrollPosition(wingId: String, index: Int, offset: Int) {
            if (wingId.isNotBlank()) {
                wingScrollPositions[wingId] = Pair(index, offset)
            }
        }

        fun clear() {
            selectedWingId = null
            selectedDateString = null
            searchQuery = ""
            wingScrollPositions.clear()
        }
    }

    object ReportsTab {
        var selectedScopeName: String? = null
        var selectedWingId: String? = null
        var searchQuery: String = ""
        var disputeFilterTab: String = "PENDING"
        var currentYearMonth: String? = null
        var scrollIndex: Int = 0
        var scrollOffset: Int = 0

        fun clear() {
            selectedScopeName = null
            selectedWingId = null
            searchQuery = ""
            disputeFilterTab = "PENDING"
            currentYearMonth = null
            scrollIndex = 0
            scrollOffset = 0
        }
    }

    object RegistrationTab {
        var selectedWingId: String? = null
        var scrollIndex: Int = 0
        var scrollOffset: Int = 0

        fun clear() {
            selectedWingId = null
            scrollIndex = 0
            scrollOffset = 0
        }
    }

    fun clearAll() {
        AttendanceTab.clear()
        ReportsTab.clear()
        RegistrationTab.clear()
    }
}
