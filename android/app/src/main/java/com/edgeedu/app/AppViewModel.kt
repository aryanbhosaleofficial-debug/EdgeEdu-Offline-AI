package com.edgeedu.app

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.edgeedu.app.content.AssetContentSource
import com.edgeedu.app.content.ContentProvisioner
import com.edgeedu.app.content.ContentSource
import com.edgeedu.app.content.FallbackContentSource
import com.edgeedu.app.content.GithubContentSource
import com.edgeedu.app.content.HttpContentSource
import com.edgeedu.app.content.Profile
import com.edgeedu.app.content.ProfileStore
import com.edgeedu.app.data.BookmarkEntry
import com.edgeedu.app.data.CorpusRepository
import com.edgeedu.app.data.IndexedChunk
import com.edgeedu.app.data.UserDataStore
import com.edgeedu.app.notes.CustomSubject
import com.edgeedu.app.notes.CustomSubjectStore
import com.edgeedu.app.notes.ImportedFile
import com.edgeedu.app.notes.NoteImport
import com.edgeedu.app.notes.NoteStructurer
import com.edgeedu.app.notes.NotesStore
import com.edgeedu.app.data.ProgressStore
import com.edgeedu.app.history.ChatHistoryStore
import com.edgeedu.app.search.Bm25Index
import com.edgeedu.app.search.SearchFilters
import com.edgeedu.app.search.SearchHit
import com.edgeedu.app.sync.AccountStore
import com.edgeedu.app.sync.HistoryEntry
import com.edgeedu.app.sync.SyncClient
import com.edgeedu.app.sync.SyncException
import com.edgeedu.app.sync.SyncManager
import com.edgeedu.app.sync.SyncResult
import com.edgeedu.app.session.ChapterInfo
import com.edgeedu.app.session.Subject
import com.edgeedu.app.session.SubjectRef
import com.edgeedu.app.session.SubjectSession
import com.edgeedu.app.tutor.LlamaCppEngine
import com.edgeedu.app.tutor.LlmEngine
import com.edgeedu.app.tutor.ModelConfig
import com.edgeedu.app.tutor.ModelDownloader
import com.edgeedu.app.tutor.Tutor
import com.edgeedu.app.tutor.TutorReply
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ChatItem {
    data class User(val text: String) : ChatItem()
    data class Bot(val reply: TutorReply) : ChatItem()
}

data class CorpusInfo(
    val fileCount: Int,
    val chunkCount: Int,
    val verifiedSolutionChunks: Int,
    val contentVersion: Int,
    val subjectChunkCounts: Map<Subject, Int>,
)

sealed class AppState {
    /** Reading the saved profile on cold start. */
    data object Loading : AppState()

    /** No profile yet (first run or after logout): show the login screen. */
    data object NeedsLogin : AppState()

    /**
     * Downloading + verifying the profile's class+medium content (§12.2).
     * [filesTotal] == 0 means the count isn't known yet (indeterminate).
     */
    data class Provisioning(
        val scopeLabel: String,
        val filesDone: Int = 0,
        val filesTotal: Int = 0,
    ) : AppState()

    data class Ready(val info: CorpusInfo, val profile: Profile) : AppState()

    /** [retryable] is true when a saved profile lets the user retry the download. */
    data class Failed(val reason: String, val retryable: Boolean = false) : AppState()
}

/** State of the optional backup account + history sync (PRD §17). */
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Working : SyncStatus()
    data class Done(val uploaded: Int) : SyncStatus()
    data class Error(val reason: String) : SyncStatus()
}

/** Lifecycle of the optional on-device AI model (Settings opt-in, ~2 GB). */
sealed class ModelStatus {
    /** Not on the device yet — the user can start the download. */
    data object Idle : ModelStatus()
    data class Downloading(val mbDone: Int, val mbTotal: Int) : ModelStatus()
    /** The GGUF is present on the device. */
    data object Installed : ModelStatus()
    data class Failed(val reason: String) : ModelStatus()
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    /**
     * The logged-in profile's medium ("English" | "Hindi" | "Marathi"), which the
     * UI uses to pick its language. "English" until a profile is known.
     */
    private val _uiMedium = MutableStateFlow("English")
    val uiMedium: StateFlow<String> = _uiMedium.asStateFlow()

    /** The focused subject session; null until the student picks a subject. */
    private val _session = MutableStateFlow<SubjectSession?>(null)
    val session: StateFlow<SubjectSession?> = _session.asStateFlow()

    private val _sessionLoading = MutableStateFlow(false)
    val sessionLoading: StateFlow<Boolean> = _sessionLoading.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatItem>>(emptyList())
    val chat: StateFlow<List<ChatItem>> = _chat.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _searchHits = MutableStateFlow<List<SearchHit>>(emptyList())
    val searchHits: StateFlow<List<SearchHit>> = _searchHits.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<BookmarkEntry>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntry>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<Map<String, String>>(emptyMap())
    val notes: StateFlow<Map<String, String>> = _notes.asStateFlow()

    /** Imported note files for the active subject (PRD §8). */
    private val _importedFiles = MutableStateFlow<List<ImportedFile>>(emptyList())
    val importedFiles: StateFlow<List<ImportedFile>> = _importedFiles.asStateFlow()

    /** User-created subjects for the logged-in class ("add your own subject"). */
    private val _customSubjects = MutableStateFlow<List<CustomSubject>>(emptyList())
    val customSubjects: StateFlow<List<CustomSubject>> = _customSubjects.asStateFlow()

    /** Transient one-line status for imports (success/error), shown then dismissed. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()
    fun clearNotice() { _notice.value = null }

    private lateinit var repository: CorpusRepository
    private lateinit var userData: UserDataStore

    private val app: Application get() = getApplication()
    private val contentDir: File get() = File(app.filesDir, "content")
    private val profileStore by lazy { ProfileStore(app) }
    private val notesStore by lazy { NotesStore(app) }
    private val customSubjectStore by lazy { CustomSubjectStore(app) }
    /**
     * Config-driven model (Qwen2.5-3B default), or the mock when unavailable.
     * Recreated after a model download so new sessions pick up the real engine.
     */
    private var _llmEngine: LlmEngine? = null
    private val llmEngine: LlmEngine
        get() = _llmEngine ?: ModelConfig.createEngine(app).also { _llmEngine = it }
    private val provisioner by lazy {
        ContentProvisioner(contentDir, File(app.filesDir, "content_version"))
    }

    // ---- On-device AI model: opt-in ~2 GB download (PRD §13) -----------------
    private val modelFile: File get() = File(File(app.filesDir, "models"), ModelConfig.modelFileName)
    private val modelDownloader by lazy { ModelDownloader(BuildConfig.MODEL_URL, modelFile) }

    /** True only when this build can actually run a GGUF (native llama.cpp compiled in). */
    val nativeInferenceAvailable: Boolean get() = LlamaCppEngine.nativeLoaded

    private val _modelStatus = MutableStateFlow<ModelStatus>(
        if (modelFile.isFile && modelFile.length() > 0) ModelStatus.Installed else ModelStatus.Idle
    )
    val modelStatus: StateFlow<ModelStatus> = _modelStatus.asStateFlow()

    /** Start the opt-in model download. No-op if already running or installed. */
    fun downloadModel() {
        if (_modelStatus.value is ModelStatus.Downloading || _modelStatus.value is ModelStatus.Installed) return
        _modelStatus.value = ModelStatus.Downloading(0, 0)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    modelDownloader.download { p ->
                        val totalMb = if (p.bytesTotal > 0) (p.bytesTotal / 1_000_000).toInt() else 0
                        _modelStatus.value = ModelStatus.Downloading((p.bytesDone / 1_000_000).toInt(), totalMb)
                    }
                }
                // Drop the mock so the next session rebuilds on the real engine.
                (_llmEngine as? LlamaCppEngine)?.close()
                _llmEngine = null
                _modelStatus.value = ModelStatus.Installed
            } catch (e: Exception) {
                _modelStatus.value = ModelStatus.Failed(e.message ?: "download failed")
            }
        }
    }

    fun cancelModelDownload() {
        modelDownloader.cancel()
        _modelStatus.value = ModelStatus.Idle
    }
    /**
     * The download origin, in priority order (the SHA-256 verification step is
     * identical for all — §14.1):
     *  1. the GitHub Release when [BuildConfig.CONTENT_RELEASE_TAG] is set (default
     *     host: login downloads the class+medium JSON + PDF slice);
     *  2. a generic static host when [BuildConfig.CONTENT_BASE_URL] is set;
     *  3. the bundled-asset origin.
     *
     * A remote origin is always wrapped so it falls back to the bundled assets
     * on any failure (unpublished release, offline, 404): the identical content
     * ships in the APK, so the app is genuinely offline-first and never
     * hard-fails a download when it already has the content.
     */
    private val bundledSource: ContentSource get() = AssetContentSource(app.assets)

    private val contentSource: ContentSource
        get() = when {
            BuildConfig.CONTENT_RELEASE_TAG.isNotBlank() -> FallbackContentSource(
                GithubContentSource(
                    BuildConfig.CONTENT_GITHUB_OWNER,
                    BuildConfig.CONTENT_GITHUB_REPO,
                    BuildConfig.CONTENT_RELEASE_TAG,
                ),
                bundledSource,
            )
            BuildConfig.CONTENT_BASE_URL.isNotBlank() ->
                FallbackContentSource(HttpContentSource(BuildConfig.CONTENT_BASE_URL), bundledSource)
            else -> bundledSource
        }

    /** Last profile we tried to load, so a failed download can be retried. */
    private var lastProfile: Profile? = null

    // ---- Study progress (home screen progress bars) ---------------------------
    private val progressStore by lazy { ProgressStore(app) }

    /**
     * Per-subject progress 0..1, keyed by [SubjectRef.key]: distinct chunks
     * the student engaged with (tutor sources, bookmarks) ÷ subject size.
     */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private fun refreshProgress() {
        if (state.value !is AppState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            val studied = progressStore.studiedCounts()
            val totals = mutableMapOf<String, Int>()
            for ((subject, count) in corpusInfo().subjectChunkCounts) {
                totals[SubjectRef.Builtin(subject).key] = count
            }
            for (custom in _customSubjects.value) {
                totals[SubjectRef.Custom(custom.id, custom.name).key] = custom.chunkCount
            }
            _progress.value = totals.mapValues { (key, total) ->
                if (total <= 0) 0f
                else ((studied[key] ?: 0).toFloat() / total).coerceIn(0f, 1f)
            }
        }
    }

    /** Chunks the student just engaged with count toward subject progress. */
    private fun markStudied(chunks: List<IndexedChunk>) {
        val key = _session.value?.subject?.key ?: return
        if (chunks.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (c in chunks) {
                progressStore.markStudied(key, "${c.file}#${c.chunk.chunk_id}")
            }
            refreshProgress()
        }
    }

    // ---- Account + offline history sync (PRD §17) ----------------------------
    private val historyStore by lazy { ChatHistoryStore(app) }
    private val accountStore by lazy { AccountStore(app) }
    private val syncClient by lazy { SyncClient(BuildConfig.SYNC_BASE_URL) }
    private val syncManager by lazy { SyncManager(syncClient, historyStore) }

    /** Whether this build has a sync backend configured at all. */
    val syncConfigured: Boolean get() = BuildConfig.SYNC_BASE_URL.isNotBlank()

    private val _accountEmail = MutableStateFlow<String?>(null)
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    /** Answers saved locally but not yet uploaded to the account's database. */
    private val _pendingHistory = MutableStateFlow(0)
    val pendingHistory: StateFlow<Int> = _pendingHistory.asStateFlow()

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /**
     * The moment the device comes online, upload pending history (PRD §17.3).
     * Registration is best-effort — sync also runs on sign-in and on demand.
     */
    private fun watchConnectivity() {
        if (!syncConfigured) return
        try {
            val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    syncNow()
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (_: Exception) {
            // No connectivity service / permission: manual "Sync now" still works.
        }
    }

    override fun onCleared() {
        networkCallback?.let {
            try {
                val cm = app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        super.onCleared()
    }

    /** Creates the backup account (online only), then uploads pending history. */
    fun createAccount(email: String, password: String) = accountAction(email, password, signup = true)

    /** Signs in to an existing backup account, then uploads pending history. */
    fun signInAccount(email: String, password: String) = accountAction(email, password, signup = false)

    private fun accountAction(email: String, password: String, signup: Boolean) {
        if (_syncStatus.value is SyncStatus.Working) return
        _syncStatus.value = SyncStatus.Working
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    if (signup) syncClient.signup(email, password) else syncClient.login(email, password)
                }
                withContext(Dispatchers.IO) { accountStore.save(email.trim(), token) }
                _accountEmail.value = email.trim()
                _syncStatus.value = SyncStatus.Idle
                syncNow()
            } catch (e: SyncException) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "failed")
            }
        }
    }

    /** Signs out of the backup account. Local history is kept on the device. */
    fun signOutAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            accountStore.clear()
            _accountEmail.value = null
            _syncStatus.value = SyncStatus.Idle
        }
    }

    /** Uploads all pending history now (also runs automatically when online). */
    fun syncNow() {
        if (_syncStatus.value is SyncStatus.Working) return
        _syncStatus.value = SyncStatus.Working
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { syncManager.sync(accountStore.token) }
            refreshPendingHistory()
            _syncStatus.value = when (result) {
                is SyncResult.Synced -> SyncStatus.Done(result.uploaded)
                is SyncResult.Failed -> SyncStatus.Error(result.reason)
                is SyncResult.Skipped -> SyncStatus.Idle
            }
        }
    }

    private suspend fun refreshPendingHistory() {
        _pendingHistory.value = withContext(Dispatchers.IO) { historyStore.unsyncedCount() }
    }

    /** Every exchange is recorded locally, offline — sync uploads it later. */
    private fun recordHistory(question: String, reply: TutorReply) {
        val profile = lastProfile ?: return
        val subject = _session.value?.subject?.label ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val answer = buildString {
                append(reply.text)
                for (s in reply.steps) {
                    append('\n').append(if (s.verified) "✓ " else "• ").append(s.text)
                    if (s.latex.isNotBlank()) append("  [").append(s.latex).append(']')
                }
            }
            historyStore.add(
                HistoryEntry(
                    asked_at = System.currentTimeMillis(),
                    standard = profile.standard,
                    medium = profile.medium,
                    subject = subject,
                    question = question,
                    answer = answer,
                    engine = reply.engine,
                )
            )
            _pendingHistory.value = historyStore.unsyncedCount()
        }
    }

    init {
        userData = UserDataStore(app)
        viewModelScope.launch {
            // User data (bookmarks/notes) persists across logout, so it loads
            // regardless of login state (§12.4).
            val profile = withContext(Dispatchers.IO) {
                _bookmarks.value = userData.bookmarks()
                _notes.value = userData.notes()
                _accountEmail.value = accountStore.email
                _pendingHistory.value = historyStore.unsyncedCount()
                profileStore.current()
            }
            watchConnectivity()
            refreshProfiles()
            if (profile == null) {
                _state.value = AppState.NeedsLogin
            } else {
                // Cold start with a saved profile: content is already downloaded
                // (unless a prior crash interrupted it), so load without re-fetching.
                loadForProfile(profile, forceDownload = false)
            }
        }
    }

    /** Saved local profiles for the "Who's studying?" picker (§12.1). */
    private val _profiles = MutableStateFlow<List<com.edgeedu.app.content.StoredProfile>>(emptyList())
    val profiles: StateFlow<List<com.edgeedu.app.content.StoredProfile>> = _profiles.asStateFlow()

    private suspend fun refreshProfiles() {
        _profiles.value = withContext(Dispatchers.IO) { profileStore.profiles() }
    }

    /** Creates a new local profile (§12.1) and triggers the one-time download. */
    fun login(name: String, standard: Int, medium: String) {
        val profile = Profile(name.trim(), standard, medium)
        if (profile.name.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profileStore.setActive(profileStore.add(profile)) }
            refreshProfiles()
            // A new profile always (re-)downloads this class+medium (§12.5).
            loadForProfile(profile, forceDownload = true)
        }
    }

    /** Picks a saved profile from the who-is-studying list (Netflix-style). */
    fun selectProfile(stored: com.edgeedu.app.content.StoredProfile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profileStore.setActive(stored.id) }
            // Content on disk may belong to another profile's class+medium;
            // loadForProfile falls back to a re-download when it doesn't match.
            loadForProfile(stored.profile, forceDownload = false)
        }
    }

    /** Removes a saved profile from the picker (its user data is kept). */
    fun deleteProfile(stored: com.edgeedu.app.content.StoredProfile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { profileStore.remove(stored.id) }
            refreshProfiles()
        }
    }

    /**
     * Logout (§12.4): deselect the profile + wipe downloaded content; saved
     * profiles and user data are kept, so logging back in is one tap.
     */
    fun logout() {
        viewModelScope.launch {
            endSession()
            _customSubjects.value = emptyList()
            withContext(Dispatchers.IO) {
                profileStore.clear()
                provisioner.clear()
            }
            refreshProfiles()
            _uiMedium.value = "English"
            _state.value = AppState.NeedsLogin
        }
    }

    /** Retry a failed download for the saved profile (PRD §12.2 retry path). */
    fun retry() {
        val profile = lastProfile ?: return
        viewModelScope.launch { loadForProfile(profile, forceDownload = true) }
    }

    private suspend fun loadForProfile(profile: Profile, forceDownload: Boolean) {
        lastProfile = profile
        _uiMedium.value = profile.medium   // switch UI language to the profile's medium
        _state.value = AppState.Provisioning(profile.scope.label)
        try {
            repository = withContext(Dispatchers.IO) {
                if (forceDownload || !provisioner.isProvisioned()) {
                    provisioner.provision(contentSource, profile.scope) { done, total ->
                        _state.value = AppState.Provisioning(profile.scope.label, done, total)
                    }
                }
                try {
                    CorpusRepository.load(contentDir, profile.scope)
                } catch (e: Exception) {
                    if (forceDownload) throw e
                    // The content on disk belongs to a different class+medium
                    // (profile switch): download this profile's slice.
                    provisioner.provision(contentSource, profile.scope) { done, total ->
                        _state.value = AppState.Provisioning(profile.scope.label, done, total)
                    }
                    CorpusRepository.load(contentDir, profile.scope)
                }
            }
            _state.value = AppState.Ready(corpusInfo(), profile)
            refreshCustomSubjects()
            refreshProgress()
        } catch (e: Exception) {
            _state.value = AppState.Failed(e.message ?: "failed to load content", retryable = true)
        }
    }

    private fun corpusInfo() = CorpusInfo(
        fileCount = repository.fileCount,
        chunkCount = repository.chunks.size,
        verifiedSolutionChunks = repository.verifiedSolutionChunks,
        contentVersion = repository.contentVersion,
        subjectChunkCounts = repository.chunks
            .groupingBy { Subject.of(it.subject) }
            .eachCount()
            .filterKeys { it != null }
            .mapKeys { it.key!! },
    )

    fun repositoryOrNull(): CorpusRepository? =
        if (state.value is AppState.Ready) repository else null

    /**
     * Starts a focused session: indexes only [subject]'s chunks and wires the
     * tutor with math tooling only for Mathematics. Replaces any previous
     * session (its index becomes garbage) and clears session-scoped state.
     */
    fun startSession(ref: SubjectRef, chapterId: Int? = null) {
        val current = _session.value
        if (_sessionLoading.value ||
            (current?.subject == ref && current.chapterId == chapterId)
        ) return
        _sessionLoading.value = true
        _session.value = null
        _chat.value = emptyList()
        _searchHits.value = emptyList()
        viewModelScope.launch {
            try {
                _session.value = withContext(Dispatchers.Default) { buildSession(ref, chapterId) }
                refreshImportedFiles(ref)
            } finally {
                _sessionLoading.value = false
            }
        }
    }

    /**
     * The textbook chapters available for a built-in [ref], in file order, so the
     * student can open a single chapter and keep the tutor focused on it. Returns
     * empty when the subject isn't chaptered (a flat file, or a custom subject).
     */
    fun chaptersFor(ref: SubjectRef): List<ChapterInfo> {
        if (ref !is SubjectRef.Builtin || state.value !is AppState.Ready) return emptyList()
        return repository.chunks
            .filter { Subject.of(it.subject) == ref.subject && it.chapterId > 0 }
            .groupBy { it.chapterId }
            .toSortedMap()
            .map { (id, chunks) ->
                ChapterInfo(id, chunks.first().chapterTitle, chunks.size)
            }
    }

    /** Creates a user-defined subject and immediately opens its (empty) session. */
    fun createCustomSubject(name: String) {
        val profile = lastProfile ?: return
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { customSubjectStore.create(clean, profile.standard) }
            refreshCustomSubjects()
            startSession(SubjectRef.Custom(id, clean))
        }
    }

    fun deleteCustomSubject(subject: CustomSubject) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                customSubjectStore.delete(subject.id)
                File(File(app.filesDir, "custom"), "${subject.id}.json").delete()
            }
            if ((_session.value?.subject as? SubjectRef.Custom)?.id == subject.id) endSession()
            refreshCustomSubjects()
        }
    }

    private suspend fun refreshCustomSubjects() {
        val profile = lastProfile ?: return
        _customSubjects.value = withContext(Dispatchers.IO) { customSubjectStore.subjects(profile.standard) }
        refreshProgress()
    }

    /**
     * Builds the focused session index. Built-in subjects index the textbook
     * PLUS the student's imported notes (PRD §8.1 dual corpus); a custom subject
     * indexes only its own structured notes. Notes carry
     * [com.edgeedu.app.data.ChunkSource.Notes] so answers can show their source.
     */
    private fun buildSession(ref: SubjectRef, chapterId: Int? = null): SubjectSession {
        val profile = lastProfile
        var chapterTitle = ""
        val all = when (ref) {
            is SubjectRef.Builtin -> {
                var textbook = repository.chunks.filter { Subject.of(it.subject) == ref.subject }
                if (chapterId != null) {
                    // Focus the session on one chapter so retrieval and the tutor's
                    // context stay tight; the student's own notes stay available.
                    chapterTitle = textbook.firstOrNull { it.chapterId == chapterId }?.chapterTitle ?: ""
                    textbook = textbook.filter { it.chapterId == chapterId }
                }
                val notes = if (profile != null) {
                    notesStore.chunksFor(ref.subject.name, profile.standard, profile.medium)
                } else emptyList()
                textbook + notes
            }
            is SubjectRef.Custom ->
                if (profile != null) customSubjectStore.chunksFor(ref.id, profile.medium) else emptyList()
        }
        val index = Bm25Index(all)
        return SubjectSession(
            subject = ref,
            chunks = all,
            index = index,
            tutor = Tutor(index, llmEngine, computeEnabled = ref.computes),
            chapterId = chapterId,
            chapterTitle = chapterTitle,
        )
    }

    private suspend fun refreshImportedFiles(ref: SubjectRef) {
        val profile = lastProfile ?: return
        _importedFiles.value = withContext(Dispatchers.IO) {
            when (ref) {
                is SubjectRef.Builtin -> notesStore.importedFiles(ref.subject.name, profile.standard)
                is SubjectRef.Custom -> emptyList()
            }
        }
    }

    /**
     * Imports a notes file (PRD §8): validate → extract text (OCR for photos,
     * PdfBox for PDFs) → then for a built-in subject store as imported notes,
     * or for a custom subject run the structuring AI (chunks + headings +
     * keywords) and (re)write its offline JSON. The new content is retrievable
     * immediately.
     */
    fun importNotes(uri: Uri) {
        val session = _session.value ?: return
        val profile = lastProfile ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val custom = withContext(Dispatchers.IO) {
                    val name = queryName(uri)
                    NoteImport.validateType(name)
                    // The structuring pipeline: any upload becomes the same
                    // v3.1 chapter/chunk shape the shipped subjects use. A
                    // file that already IS a curriculum JSON round-trips
                    // losslessly instead of being flattened to text.
                    val chapters = curriculumJsonChapters(uri, name)
                        ?: NoteStructurer.structureChapters(extractImportedText(uri, name), name)
                    if (chapters.isEmpty()) throw com.edgeedu.app.notes.ImportException(
                        "No readable text found in the file."
                    )
                    when (val ref = session.subject) {
                        is SubjectRef.Custom -> {
                            customSubjectStore.appendChapters(ref.id, chapters)
                            writeCustomJson(ref, profile)
                            chapters.sumOf { it.chunks.size }
                        }
                        is SubjectRef.Builtin -> {
                            notesStore.addImport(name, ref.subject.name, profile.standard, chapters)
                            null
                        }
                    }
                }
                _session.value = withContext(Dispatchers.Default) {
                    buildSession(session.subject, session.chapterId)
                }
                refreshImportedFiles(session.subject)
                refreshCustomSubjects()
                _notice.value =
                    if (custom != null) "Structured into $custom chunks ✓" else "Notes imported ✓"
            } catch (e: Exception) {
                _notice.value = e.message ?: "Import failed."
            } finally {
                _busy.value = false
            }
        }
    }

    /** Chapters parsed from an uploaded curriculum-format .json, or null. */
    private fun curriculumJsonChapters(uri: Uri, name: String) =
        if (NoteImport.extension(name) == "json") {
            val bytes = readBytes(uri)
            NoteImport.validate(name, bytes.size)
            NoteStructurer.fromCurriculumJson(bytes.decodeToString())
        } else null

    /** Extracts plain text from any supported import type (image/PDF/text/JSON). */
    private fun extractImportedText(uri: Uri, name: String): String = when {
        NoteImport.isImage(name) -> {
            querySize(uri)?.let {
                if (it > NoteImport.MAX_BYTES) throw com.edgeedu.app.notes.ImportException(
                    "File is too large (max ${NoteImport.MAX_BYTES / 1_000_000} MB)."
                )
            }
            com.edgeedu.app.notes.ImageTextExtractor.extract(app, uri)
        }
        NoteImport.isPdf(name) -> {
            val bytes = readBytes(uri)
            NoteImport.validate(name, bytes.size)
            com.edgeedu.app.notes.PdfTextExtractor.extract(app, bytes)
        }
        else -> NoteImport.extractText(name, readBytes(uri))
    }

    /** (Re)writes the custom subject's structured notes as an offline JSON file. */
    private fun writeCustomJson(ref: SubjectRef.Custom, profile: Profile) {
        val chapters = customSubjectStore.structuredChapters(ref.id)
        val jsonText = NoteStructurer.buildJson(ref.label, profile.standard, profile.medium, chapters)
        val dir = File(app.filesDir, "custom").apply { mkdirs() }
        File(dir, "${ref.id}.json").writeText(jsonText)
    }

    fun deleteImport(file: ImportedFile) {
        val session = _session.value ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { notesStore.deleteFile(file.id) }
            _session.value = withContext(Dispatchers.Default) {
                buildSession(session.subject, session.chapterId)
            }
            refreshImportedFiles(session.subject)
        }
    }

    private fun queryName(uri: Uri): String =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment ?: "notes.txt"

    private fun querySize(uri: Uri): Long? =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

    private fun readBytes(uri: Uri): ByteArray =
        app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw com.edgeedu.app.notes.ImportException("Couldn't open the file.")

    fun endSession() {
        _session.value = null
        _chat.value = emptyList()
        _searchHits.value = emptyList()
        _importedFiles.value = emptyList()
    }

    fun ask(question: String, language: String?) {
        val session = _session.value ?: return
        if (_busy.value || question.isBlank()) return
        _chat.value += ChatItem.User(question)
        _busy.value = true
        viewModelScope.launch {
            try {
                val reply = withContext(Dispatchers.Default) {
                    session.tutor.ask(question, SearchFilters(language = language))
                }
                _chat.value += ChatItem.Bot(reply)
                recordHistory(question, reply)
                markStudied(reply.sources)
            } finally {
                _busy.value = false
            }
        }
    }

    fun search(query: String, language: String?, standard: Int?) {
        val session = _session.value ?: return
        viewModelScope.launch {
            _searchHits.value = withContext(Dispatchers.Default) {
                session.index.search(
                    query, SearchFilters(language = language, standard = standard), k = 15
                )
            }
        }
    }

    fun isBookmarked(chunk: IndexedChunk): Boolean =
        _bookmarks.value.any { it.key == UserDataStore.keyOf(chunk) }

    fun toggleBookmark(chunk: IndexedChunk) {
        val key = UserDataStore.keyOf(chunk)
        viewModelScope.launch(Dispatchers.IO) {
            if (_bookmarks.value.any { it.key == key }) {
                userData.removeBookmark(key)
            } else {
                userData.addBookmark(
                    BookmarkEntry(
                        key = key,
                        file = chunk.file,
                        chunkId = chunk.chunk.chunk_id,
                        heading = chunk.chunk.heading,
                        subject = chunk.subject,
                        standard = chunk.standard,
                        language = chunk.language,
                        createdAt = System.currentTimeMillis(),
                    )
                )
                markStudied(listOf(chunk))
            }
            _bookmarks.value = userData.bookmarks()
        }
    }

    fun removeBookmark(entry: BookmarkEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            userData.removeBookmark(entry.key)
            _bookmarks.value = userData.bookmarks()
        }
    }

    fun saveNote(key: String, text: String) {
        viewModelScope.launch(Dispatchers.IO) {
            userData.putNote(key, text)
            _notes.value = userData.notes()
        }
    }
}
