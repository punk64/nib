package com.yourname.nib

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.nib.core.CrashCatcher
import com.yourname.nib.core.calendar.AgendaItem
import com.yourname.nib.core.calendar.CalendarBridge
import com.yourname.nib.core.calendar.SyncReport
import com.yourname.nib.core.db.Conflict
import com.yourname.nib.core.db.Entry
import com.yourname.nib.core.db.EntryItem
import com.yourname.nib.core.db.NibRepository
import com.yourname.nib.core.db.NoteList
import com.yourname.nib.core.db.SyncStats
import com.yourname.nib.core.db.Tag
import com.yourname.nib.core.export.jsonToSnapshot
import com.yourname.nib.core.export.snapshotToJson
import com.yourname.nib.core.export.toIcs
import com.yourname.nib.core.export.toMarkdown
import com.yourname.nib.core.model.CaptureDraft
import com.yourname.nib.core.model.EntryBundle
import com.yourname.nib.core.model.EntryFilter
import com.yourname.nib.core.model.filterBundles
import com.yourname.nib.core.organize.normalizeFtsQuery
import com.yourname.nib.core.sync.SyncCursor
import com.yourname.nib.core.theme.ThemeConfig
import com.yourname.nib.core.theme.ThemeStore
import com.yourname.nib.core.time.NibDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class DeleteRequest(
    val ids: List<String>,
    val eventCount: Int,
)

class NibViewModel(
    private val repo: NibRepository,
    private val themeStore: ThemeStore,
    private val reschedule: () -> Unit,
    private val calendar: CalendarBridge,
    private val syncCursor: SyncCursor,
) : ViewModel() {
    private val filter = MutableStateFlow(EntryFilter())
    private val search = MutableStateFlow("")
    private val savedTheme = MutableStateFlow(themeStore.load())
    private val themeState = MutableStateFlow(savedTheme.value)
    private val doneVisible = MutableStateFlow(false)
    private val agendaState = MutableStateFlow<List<AgendaItem>>(emptyList())
    private val agendaErrorState = MutableStateFlow<String?>(null)
    private val calendarCountState = MutableStateFlow(0)
    private val calendarSyncState = MutableStateFlow<SyncReport?>(null)
    private val deleteRequestState = MutableStateFlow<DeleteRequest?>(null)
    private var agendaFrom = NibDate.today()
    private var agendaTo = NibDate.today().plusDays(1)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val ftsState = MutableStateFlow<Set<String>?>(null)
    private val syncState = MutableStateFlow(SyncStats(0, 0L, 0))
    private val noticeState = MutableStateFlow<String?>(null)
    private var ftsJob: Job? = null

    val filterState: StateFlow<EntryFilter> = filter.asStateFlow()
    val searchState: StateFlow<String> = search.asStateFlow()
    val theme: StateFlow<ThemeConfig> = themeState.asStateFlow()
    val themeDirty: StateFlow<Boolean> = combine(themeState, savedTheme) { now, saved -> now != saved }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val doneVisibleState: StateFlow<Boolean> = doneVisible.asStateFlow()
    val agenda: StateFlow<List<AgendaItem>> = agendaState.asStateFlow()
    val agendaError: StateFlow<String?> = agendaErrorState.asStateFlow()
    val calendarCount: StateFlow<Int> = calendarCountState.asStateFlow()
    val calendarSync: StateFlow<SyncReport?> = calendarSyncState.asStateFlow()
    val deleteRequest: StateFlow<DeleteRequest?> = deleteRequestState.asStateFlow()
    val selectedIds: StateFlow<Set<String>> = selected.asStateFlow()
    val ftsIds: StateFlow<Set<String>?> = ftsState.asStateFlow()
    val sync: StateFlow<SyncStats> = syncState.asStateFlow()
    val notice: StateFlow<String?> = noticeState.asStateFlow()

    val lists: StateFlow<List<NoteList>> = repo.observeLists()
        .catch { error -> CrashCatcher.record(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tags: StateFlow<List<Tag>> = repo.observeTags()
        .catch { error -> CrashCatcher.record(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val conflicts: StateFlow<List<Conflict>> = repo.observeConflicts()
        .catch { error -> CrashCatcher.record(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBundles: StateFlow<List<EntryBundle>> = repo.observeBundles()
        .catch { error -> CrashCatcher.record(error); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bundles: StateFlow<List<EntryBundle>> = combine(
        allBundles,
        filter,
        search,
        ftsState,
    ) { all, currentFilter, query, ids -> filterBundles(all, currentFilter, query, ids) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        launchSafe(Dispatchers.IO) { repo.seedDefaults() }
        reloadAgenda()
        refreshSyncState()
    }

    private fun launchSafe(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        viewModelScope.launch(context) {
            runCatching { block() }.onFailure { CrashCatcher.record(it) }
        }
    }

    fun setFilter(next: EntryFilter) {
        filter.value = next
    }

    fun toggleTag(tagId: String) {
        val current = filter.value
        val next = if (tagId in current.tagIds) current.tagIds - tagId else current.tagIds + tagId
        filter.value = current.copy(tagIds = next)
    }

    fun setSearch(query: String) {
        search.value = query
        ftsJob?.cancel()
        ftsJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val match = normalizeFtsQuery(query)
                ftsState.value = if (match == null || !repo.ftsAvailable()) null else repo.searchFts(match)
            }.onFailure { CrashCatcher.record(it) }
        }
    }

    fun toggleDoneVisible() {
        doneVisible.value = !doneVisible.value
    }

    fun setStyle(styleId: String) {
        updateTheme { it.copy(styleId = styleId) }
    }

    fun setColor(colorKey: String) {
        updateTheme { it.copy(colorKey = colorKey) }
    }

    fun setDark(dark: Boolean) {
        updateTheme { it.copy(dark = dark) }
    }

    fun setTune(key: String, value: Int) {
        updateTheme { it.withTune(key, value) }
    }

    fun resetTune() {
        updateTheme { it.resetTune() }
    }

    fun commitTheme() {
        val next = themeState.value
        savedTheme.value = next
        themeStore.save(next)
    }

    fun revertTheme() {
        themeState.value = savedTheme.value
    }

    private fun updateTheme(change: (ThemeConfig) -> ThemeConfig) {
        themeState.value = change(themeState.value)
    }

    fun saveDraft(draft: CaptureDraft) {
        launchSafe {
            val extra = repo.resolveTagNames(draft.newTagNames)
            repo.createEntry(
                raw = draft.raw,
                title = draft.title,
                dueDate = draft.dueDate,
                dueTime = draft.dueTime,
                listId = draft.listId,
                tagIds = draft.tagIds + extra,
                priority = draft.priority,
                repeatFreq = draft.repeatFreq,
                repeatN = draft.repeatN,
                leadMinutes = draft.leadMinutes,
                channel = draft.channel,
                durationMin = draft.durationMin,
            )
            afterWrite()
        }
    }

    fun saveEntry(entry: Entry, tagIds: Set<String>) {
        launchSafe {
            repo.updateEntry(entry, tagIds)
            afterWrite()
        }
    }

    fun setDone(id: String, done: Boolean) {
        launchSafe {
            repo.setDone(id, done)
            afterWrite()
        }
    }

    fun deleteEntry(id: String) = requestDelete(listOf(id))

    fun requestDelete(ids: List<String>) {
        val targets = ids.filter { it.isNotBlank() }
        if (targets.isEmpty()) return
        launchSafe(Dispatchers.IO) {
            deleteRequestState.value = DeleteRequest(targets, repo.calendarEventCount(targets))
        }
    }

    fun confirmDelete(syncCalendar: Boolean) {
        val request = deleteRequestState.value ?: return
        deleteRequestState.value = null
        launchSafe {
            if (!syncCalendar && request.eventCount > 0) repo.clearCalendarEventIds(request.ids)
            repo.softDeleteIds(request.ids.toSet())
            clearSelection()
            afterWrite()
        }
    }

    fun cancelDelete() {
        deleteRequestState.value = null
    }

    fun addList(name: String) {
        launchSafe { repo.addList(name) }
    }

    fun renameList(list: NoteList, name: String) {
        launchSafe { repo.renameList(list, name) }
    }

    fun deleteList(id: String) {
        launchSafe { repo.deleteList(id) }
    }

    fun addTag(name: String) {
        launchSafe { repo.addTag(name) }
    }

    fun deleteTag(id: String) {
        launchSafe { repo.deleteTag(id) }
    }

    fun items(entryId: String): Flow<List<EntryItem>> = repo.items(entryId)

    fun addItem(entryId: String, text: String) {
        launchSafe { repo.addItem(entryId, text) }
    }

    fun setItemDone(id: String, done: Boolean) {
        launchSafe { repo.setItemDone(id, done) }
    }

    fun setItemText(id: String, text: String) {
        launchSafe { repo.setItemText(id, text) }
    }

    fun deleteItem(id: String) {
        launchSafe { repo.deleteItem(id) }
    }

    fun swapItems(firstId: String, firstSort: String, secondId: String, secondSort: String) {
        launchSafe { repo.swapItemSort(firstId, firstSort, secondId, secondSort) }
    }

    fun setPinned(id: String, pinned: Boolean) {
        launchSafe {
            repo.setPinned(id, pinned)
            afterWrite()
        }
    }

    fun reorder(id: String, prev: String?, next: String?, targetIds: List<String>) {
        launchSafe {
            if (!repo.reorder(id, prev, next)) repo.rebalance(targetIds)
            afterWrite()
        }
    }

    fun toggleSelect(id: String) {
        val current = selected.value
        selected.value = if (id in current) current - id else current + id
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    fun batchSetList(listId: String?) {
        val ids = selected.value
        launchSafe {
            repo.setListForIds(ids, listId)
            clearSelection()
            afterWrite()
        }
    }

    fun batchAddTag(tagId: String) {
        val ids = selected.value
        launchSafe {
            repo.addTagToIds(ids, tagId)
            clearSelection()
            afterWrite()
        }
    }

    fun batchDelete() {
        requestDelete(selected.value.toList())
    }

    fun refreshSyncState() {
        launchSafe(Dispatchers.IO) {
            syncState.value = repo.syncStats(syncCursor)
        }
    }

    fun deleteConflict(id: String) {
        launchSafe(Dispatchers.IO) {
            repo.deleteConflict(id)
            syncState.value = repo.syncStats(syncCursor)
        }
    }

    suspend fun buildExport(format: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val snapshot = repo.snapshot()
        val text = when (format) {
            "md" -> toMarkdown(snapshot)
            "ics" -> toIcs(snapshot)
            else -> snapshotToJson(snapshot)
        }
        val ext = when (format) {
            "md" -> "md"
            "ics" -> "ics"
            else -> "json"
        }
        "落笔备份-${System.currentTimeMillis()}.$ext" to text
    }

    suspend fun importBackup(text: String): String {
        val message = withContext(Dispatchers.IO) {
            val snapshot = runCatching { jsonToSnapshot(text) }.getOrNull()
                ?: return@withContext "备份文件无法解析"
            if (snapshot.entries.isEmpty() && snapshot.lists.isEmpty() && snapshot.tags.isEmpty()) {
                return@withContext "备份文件为空"
            }
            repo.importSnapshot(snapshot)
            syncState.value = repo.syncStats(syncCursor)
            "已导入 ${snapshot.entries.size} 条记录"
        }
        afterWrite()
        return message
    }

    fun clearNotice() {
        noticeState.value = null
    }

    fun setNotice(message: String) {
        noticeState.value = message
    }

    fun reloadAgenda(from: NibDate? = null, to: NibDate? = null) {
        if (from != null) agendaFrom = from
        if (to != null) agendaTo = to
        val fromKey = agendaFrom
        val toKey = agendaTo
        launchSafe(Dispatchers.IO) {
            val result = calendar.agenda(fromKey, toKey)
            agendaState.value = result.items
            agendaErrorState.value = result.error
            calendarCountState.value = calendar.calendarCount()
        }
    }

    fun onForeground() {
        launchSafe {
            if (repo.consumePendingCaptures() > 0) afterWrite()
        }
        calendar.pull { reloadAgenda() }
        refreshSyncState()
    }

    fun onCalendarViewEnter() = onForeground()

    fun onCalendarChanged() {
        calendar.pull { reloadAgenda() }
    }

    private fun afterWrite() {
        reschedule()
        pushCalendar()
        refreshSyncState()
    }

    fun syncCalendarNow() {
        pushCalendar()
    }

    private fun pushCalendar() {
        calendar.push { report ->
            calendarSyncState.value = report
            val message = report.error ?: report.note
            if (message != null) noticeState.value = "系统日历：$message"
            reloadAgenda()
        }
    }
}
