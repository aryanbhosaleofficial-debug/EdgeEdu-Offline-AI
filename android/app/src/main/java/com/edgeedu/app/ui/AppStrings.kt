package com.edgeedu.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.edgeedu.app.session.Subject
import com.edgeedu.app.session.SubjectRef

/**
 * The app's UI text in one place, one instance per medium. The logged-in
 * profile's medium decides which instance is provided through [LocalStrings],
 * so choosing Hindi or Marathi at login switches the whole app's language —
 * an in-app choice, independent of the phone's system locale.
 *
 * Translations are a first pass for Maharashtra-Board Hindi/Marathi students and
 * are worth a native-speaker review; English is the source of truth.
 */
data class AppStrings(
    // Bottom navigation
    val navHome: String,
    val navAskAI: String,
    val navSearch: String,
    val navBrowse: String,
    val navSaved: String,
    val navProfile: String,
    // Session top bar
    val sessionSuffix: String,      // "<Subject> session"
    val startingSession: String,
    val endSession: String,
    val noActiveSession: String,
    val chooseSubject: String,
    // Provisioning / errors
    val downloadingVerifying: String,
    val filesWord: String,          // "X / Y files"
    val couldntLoad: String,
    val retryDownload: String,
    val backToLogin: String,
    // Home header
    val welcomeBack: String,
    val offline: String,
    val classWord: String,          // "Class N"
    val statTopics: String,
    val statVerified: String,
    val statSubjects: String,
    // Home body
    val subjects: String,
    val yourOwnSubjects: String,
    val yourOwnSubjectsDesc: String,
    val trustworthyMaths: String,
    val trustworthyMathsDesc: String,
    val topicsWord: String,         // "N topics"
    val verifiedMathEngine: String, // "· verified math engine"
    val addSubject: String,
    val buildFromNotes: String,
    val noNotesYet: String,
    val remove: String,
    // Add-subject dialog
    val nameYourSubject: String,
    val subjectNameLabel: String,
    val create: String,
    val cancel: String,
    // Chat
    val importNotes: String,
    val askPlaceholder: String,
    val ask: String,
    val sources: String,
    // Settings
    val localProfile: String,
    val localProfileDesc: String,
    val offlineTitle: String,
    val offlineDesc: String,
    val aiTutor: String,
    val aiTutorDesc: String,
    val downloadModel: String,
    val downloading: String,
    val startingDownload: String,
    val modelInstalled: String,
    val modelNotRunnable: String,
    val downloadFailed: String,
    val appearance: String,
    val themeSystem: String,
    val themeLight: String,
    val themeDark: String,
    val logOut: String,
    val logOutDesc: String,
    // Search
    val searchPlaceholder: String,
    val go: String,
    // Saved
    val nothingSaved: String,
    val addNote: String,
    val saveNote: String,
    // Browse
    val backToBooks: String,
    // Subject-specific "Ask AI" placeholders (English defaults; Hindi/Marathi
    // override below). The generic [askPlaceholder] is the fallback.
    val askPlaceholderMaths: String = "e.g. solve 2x + 3 = 7",
    val askPlaceholderScience: String = "e.g. what is gravitation?",
    val askPlaceholderGeography: String = "e.g. what is a field visit?",
    val askPlaceholderHistory: String = "e.g. what is historiography?",
    val askPlaceholderNotes: String = "e.g. ask a question from your notes",
    // Chapter picker (opened when a subject is tapped, so the tutor can be
    // focused on one chapter). English defaults; Hindi/Marathi override below.
    val chooseChapter: String = "Choose a chapter",
    val chooseChapterDesc: String = "Pick a chapter to keep the AI focused on it — or study the whole subject.",
    val allChapters: String = "Whole subject",
    val allChaptersDesc: String = "Ask across every chapter",
    val chaptersWord: String = "chapters",
    val chapterWord: String = "Chapter",
    // Home greeting (the sketch's "Hello (User name)").
    val helloUser: String = "Hello,",
    // Account & backup (PRD §17). English defaults so the three instances
    // stay compilable; Hindi/Marathi override them below.
    val accountTitle: String = "Account & backup",
    val accountDesc: String = "Optional: create an account to back up your questions and answers. " +
        "Everything still works offline — history is saved on your phone and uploaded automatically " +
        "when you're online.",
    val accountEmailLabel: String = "Email",
    val accountPasswordLabel: String = "Password",
    val accountCreate: String = "Create account",
    val accountSignIn: String = "Sign in",
    val accountSignedInAs: String = "Signed in as",
    val accountSignOut: String = "Sign out",
    val accountSyncNow: String = "Sync now",
    val accountAllSynced: String = "✓ All answers backed up",
    val accountPending: String = "answers waiting to upload",
    val accountSynced: String = "answers backed up",
    val accountSyncing: String = "Syncing…",
    val accountSyncFailed: String = "Sync failed",
    val accountNeedsInternet: String = "Creating an account needs an internet connection.",
    val accountNotConfigured: String = "Backup is not configured in this build " +
        "(set SYNC_BASE_URL to enable it). Your history is still saved on this device.",
    // Subject names
    val subjMaths1: String,
    val subjMaths2: String,
    val subjScience: String,
    val subjScience1: String,
    val subjScience2: String,
    val subjGeography: String,
    val subjSocialStudies: String,
) {
    /** Localized display name for a built-in [Subject]. */
    fun subjectName(subject: Subject): String = when (subject) {
        Subject.Maths1 -> subjMaths1
        Subject.Maths2 -> subjMaths2
        Subject.Science -> subjScience
        Subject.Science1 -> subjScience1
        Subject.Science2 -> subjScience2
        Subject.Geography -> subjGeography
        Subject.SocialStudies -> subjSocialStudies
    }

    /**
     * A subject-appropriate "Ask AI" placeholder example, so a Geography or
     * History session doesn't prompt with a maths equation. Falls back to the
     * generic [askPlaceholder] when no subject is active.
     */
    fun chatPlaceholder(ref: SubjectRef?): String = when (ref) {
        is SubjectRef.Custom -> askPlaceholderNotes
        is SubjectRef.Builtin -> when (ref.subject) {
            Subject.Maths1, Subject.Maths2 -> askPlaceholderMaths
            Subject.Science, Subject.Science1, Subject.Science2 -> askPlaceholderScience
            Subject.Geography -> askPlaceholderGeography
            Subject.SocialStudies -> askPlaceholderHistory
        }
        null -> askPlaceholder
    }
}

private val EnglishStrings = AppStrings(
    navHome = "Home", navAskAI = "Ask AI", navSearch = "Search",
    navBrowse = "Browse", navSaved = "Saved", navProfile = "Profile",
    sessionSuffix = "session", startingSession = "Starting session…",
    endSession = "End session",
    noActiveSession = "No active session.\nPick a subject to get started.",
    chooseSubject = "Choose subject",
    downloadingVerifying = "Downloading & verifying", filesWord = "files",
    couldntLoad = "Couldn't load content:", retryDownload = "Retry download",
    backToLogin = "Back to login",
    welcomeBack = "Welcome back 👋", offline = "Offline", classWord = "Class",
    statTopics = "Topics", statVerified = "Verified", statSubjects = "Subjects",
    subjects = "Subjects", yourOwnSubjects = "Your own subjects",
    yourOwnSubjectsDesc = "Subject not in the app? Add it, import your notes (text, PDF or photos), and EdgeEdu structures them into chunks on your device.",
    trustworthyMaths = "Trustworthy maths ✓",
    trustworthyMathsDesc = "In maths sessions the model never does arithmetic — every computation is routed through a verified math engine.",
    topicsWord = "topics", verifiedMathEngine = "· verified math engine",
    addSubject = "Add a subject", buildFromNotes = "Build it from your own notes",
    noNotesYet = "No notes yet — open to add", remove = "Remove",
    nameYourSubject = "Name your subject (e.g. Economics). You'll add notes next.",
    subjectNameLabel = "Subject name", create = "Create", cancel = "Cancel",
    importNotes = "Import notes", askPlaceholder = "e.g. solve 2x + 3 = 7",
    ask = "Ask", sources = "Sources: ",
    localProfile = "Local profile",
    localProfileDesc = "No account, no password — nothing leaves this device.",
    offlineTitle = "Offline",
    offlineDesc = "Content is downloaded and verified on your device. No internet is used until you log out and back in.",
    aiTutor = "AI tutor",
    aiTutorDesc = "Download the on-device AI (~2 GB) so it can solve any maths or physics problem and explain it step by step. Use WiFi — it's a large, one-time download that resumes if interrupted. The verified maths solver works without it.",
    downloadModel = "Download AI model (2 GB)", downloading = "Downloading…",
    startingDownload = "Starting download…", modelInstalled = "✓ AI model installed",
    modelNotRunnable = "Note: this build can't run the model yet (on-device inference isn't compiled in). The verified step-by-step maths solver still works.",
    downloadFailed = "Download failed", appearance = "Appearance",
    themeSystem = "System", themeLight = "Light", themeDark = "Dark",
    logOut = "Log out",
    logOutDesc = "Logging out deletes the downloaded content to free storage. Your bookmarks and notes are kept and restored when you log back in with the same name and class.",
    searchPlaceholder = "quadratic / द्विघात समीकरण", go = "Go",
    nothingSaved = "Nothing saved yet.\nBookmark concepts with ☆ while browsing.",
    addNote = "Add a note…", saveNote = "Save note",
    backToBooks = "← Back to books",
    subjMaths1 = "Maths 1", subjMaths2 = "Maths 2",
    subjScience = "Science", subjScience1 = "Science 1", subjScience2 = "Science 2",
    subjGeography = "Geography", subjSocialStudies = "Social Studies / History",
)

private val HindiStrings = AppStrings(
    navHome = "होम", navAskAI = "AI से पूछें", navSearch = "खोज",
    navBrowse = "ब्राउज़", navSaved = "सहेजे", navProfile = "प्रोफ़ाइल",
    sessionSuffix = "सत्र", startingSession = "सत्र शुरू हो रहा है…",
    endSession = "सत्र समाप्त करें",
    noActiveSession = "कोई सक्रिय सत्र नहीं।\nशुरू करने के लिए एक विषय चुनें।",
    chooseSubject = "विषय चुनें",
    downloadingVerifying = "डाउनलोड और सत्यापन हो रहा है", filesWord = "फ़ाइलें",
    couldntLoad = "सामग्री लोड नहीं हो सकी:", retryDownload = "फिर से डाउनलोड करें",
    backToLogin = "लॉगिन पर वापस जाएँ",
    welcomeBack = "वापसी पर स्वागत है 👋", offline = "ऑफ़लाइन", classWord = "कक्षा",
    statTopics = "विषय-वस्तु", statVerified = "सत्यापित", statSubjects = "विषय",
    subjects = "विषय", yourOwnSubjects = "आपके अपने विषय",
    yourOwnSubjectsDesc = "विषय ऐप में नहीं है? उसे जोड़ें, अपने नोट्स (टेक्स्ट, PDF या फ़ोटो) इम्पोर्ट करें, और EdgeEdu उन्हें आपके डिवाइस पर ही व्यवस्थित कर देगा।",
    trustworthyMaths = "भरोसेमंद गणित ✓",
    trustworthyMathsDesc = "गणित सत्रों में मॉडल कभी गणना नहीं करता — हर गणना एक सत्यापित गणित इंजन से होकर जाती है।",
    topicsWord = "विषय-वस्तु", verifiedMathEngine = "· सत्यापित गणित इंजन",
    addSubject = "विषय जोड़ें", buildFromNotes = "अपने नोट्स से बनाएँ",
    noNotesYet = "अभी कोई नोट्स नहीं — जोड़ने के लिए खोलें", remove = "हटाएँ",
    nameYourSubject = "अपने विषय का नाम दें (जैसे अर्थशास्त्र)। आगे नोट्स जोड़ें।",
    subjectNameLabel = "विषय का नाम", create = "बनाएँ", cancel = "रद्द करें",
    importNotes = "नोट्स इम्पोर्ट करें", askPlaceholder = "जैसे: solve 2x + 3 = 7",
    ask = "पूछें", sources = "स्रोत: ",
    localProfile = "लोकल प्रोफ़ाइल",
    localProfileDesc = "कोई खाता नहीं, कोई पासवर्ड नहीं — कुछ भी इस डिवाइस से बाहर नहीं जाता।",
    offlineTitle = "ऑफ़लाइन",
    offlineDesc = "सामग्री आपके डिवाइस पर डाउनलोड और सत्यापित होती है। लॉग आउट और दोबारा लॉगिन करने तक इंटरनेट का उपयोग नहीं होता।",
    aiTutor = "AI ट्यूटर",
    aiTutorDesc = "डिवाइस पर चलने वाला AI (~2 GB) डाउनलोड करें ताकि यह किसी भी गणित या भौतिकी की समस्या को चरण-दर-चरण हल कर सके। WiFi का उपयोग करें — यह एक बड़ा, एक-बार का डाउनलोड है जो रुकने पर फिर से शुरू हो जाता है। सत्यापित गणित हल इसके बिना भी काम करता है।",
    downloadModel = "AI मॉडल डाउनलोड करें (2 GB)", downloading = "डाउनलोड हो रहा है…",
    startingDownload = "डाउनलोड शुरू हो रहा है…", modelInstalled = "✓ AI मॉडल इंस्टॉल हो गया",
    modelNotRunnable = "ध्यान दें: यह बिल्ड अभी मॉडल नहीं चला सकता (ऑन-डिवाइस इन्फ़रेंस शामिल नहीं है)। सत्यापित चरण-दर-चरण गणित हल फिर भी काम करता है।",
    downloadFailed = "डाउनलोड विफल", appearance = "रूप-रंग",
    themeSystem = "सिस्टम", themeLight = "हल्का", themeDark = "गहरा",
    logOut = "लॉग आउट",
    logOutDesc = "लॉग आउट करने पर डाउनलोड की गई सामग्री हट जाती है ताकि जगह खाली हो। आपके बुकमार्क और नोट्स सुरक्षित रहते हैं और उसी नाम व कक्षा से दोबारा लॉगिन करने पर लौट आते हैं।",
    searchPlaceholder = "द्विघात समीकरण / quadratic", go = "खोजें",
    nothingSaved = "अभी कुछ सहेजा नहीं गया।\nब्राउज़ करते समय ☆ से अवधारणाएँ सहेजें।",
    addNote = "एक नोट जोड़ें…", saveNote = "नोट सहेजें",
    backToBooks = "← पुस्तकों पर वापस",
    askPlaceholderMaths = "जैसे: solve 2x + 3 = 7",
    askPlaceholderScience = "जैसे: गुरुत्वाकर्षण क्या है?",
    askPlaceholderGeography = "जैसे: क्षेत्र भ्रमण क्या है?",
    askPlaceholderHistory = "जैसे: इतिहास लेखन क्या है?",
    askPlaceholderNotes = "जैसे: अपने नोट्स से कोई प्रश्न पूछें",
    chooseChapter = "अध्याय चुनें",
    chooseChapterDesc = "AI को केंद्रित रखने के लिए एक अध्याय चुनें — या पूरा विषय पढ़ें।",
    allChapters = "पूरा विषय",
    allChaptersDesc = "हर अध्याय से पूछें",
    chaptersWord = "अध्याय",
    chapterWord = "अध्याय",
    helloUser = "नमस्ते,",
    accountTitle = "खाता और बैकअप",
    accountDesc = "वैकल्पिक: अपने सवाल-जवाब का बैकअप लेने के लिए खाता बनाएँ। सब कुछ ऑफ़लाइन ही चलता है — इतिहास आपके फ़ोन पर सहेजा जाता है और ऑनलाइन होते ही अपने आप अपलोड हो जाता है।",
    accountEmailLabel = "ईमेल",
    accountPasswordLabel = "पासवर्ड",
    accountCreate = "खाता बनाएँ",
    accountSignIn = "साइन इन करें",
    accountSignedInAs = "साइन इन:",
    accountSignOut = "साइन आउट",
    accountSyncNow = "अभी सिंक करें",
    accountAllSynced = "✓ सभी उत्तरों का बैकअप हो गया",
    accountPending = "उत्तर अपलोड की प्रतीक्षा में",
    accountSynced = "उत्तरों का बैकअप हुआ",
    accountSyncing = "सिंक हो रहा है…",
    accountSyncFailed = "सिंक विफल",
    accountNeedsInternet = "खाता बनाने के लिए इंटरनेट कनेक्शन चाहिए।",
    accountNotConfigured = "इस बिल्ड में बैकअप कॉन्फ़िगर नहीं है (सक्षम करने के लिए SYNC_BASE_URL सेट करें)। आपका इतिहास फिर भी इसी डिवाइस पर सहेजा जाता है।",
    subjMaths1 = "गणित 1", subjMaths2 = "गणित 2",
    subjScience = "विज्ञान", subjScience1 = "विज्ञान 1", subjScience2 = "विज्ञान 2",
    subjGeography = "भूगोल", subjSocialStudies = "सामाजिक विज्ञान / इतिहास",
)

private val MarathiStrings = AppStrings(
    navHome = "होम", navAskAI = "AI ला विचारा", navSearch = "शोध",
    navBrowse = "ब्राउझ", navSaved = "जतन केलेले", navProfile = "प्रोफाइल",
    sessionSuffix = "सत्र", startingSession = "सत्र सुरू होत आहे…",
    endSession = "सत्र संपवा",
    noActiveSession = "कोणतेही सक्रिय सत्र नाही.\nसुरू करण्यासाठी एक विषय निवडा.",
    chooseSubject = "विषय निवडा",
    downloadingVerifying = "डाउनलोड व पडताळणी होत आहे", filesWord = "फायली",
    couldntLoad = "सामग्री लोड होऊ शकली नाही:", retryDownload = "पुन्हा डाउनलोड करा",
    backToLogin = "लॉगिनवर परत जा",
    welcomeBack = "पुन्हा स्वागत आहे 👋", offline = "ऑफलाइन", classWord = "इयत्ता",
    statTopics = "घटक", statVerified = "पडताळलेले", statSubjects = "विषय",
    subjects = "विषय", yourOwnSubjects = "तुमचे स्वतःचे विषय",
    yourOwnSubjectsDesc = "विषय ॲपमध्ये नाही? तो जोडा, तुमच्या नोट्स (मजकूर, PDF किंवा फोटो) इम्पोर्ट करा, आणि EdgeEdu त्या तुमच्या डिव्हाइसवरच रचून देईल.",
    trustworthyMaths = "विश्वासार्ह गणित ✓",
    trustworthyMathsDesc = "गणिताच्या सत्रांत मॉडेल कधीही आकडेमोड करत नाही — प्रत्येक गणन पडताळलेल्या गणित इंजिनातून जाते.",
    topicsWord = "घटक", verifiedMathEngine = "· पडताळलेले गणित इंजिन",
    addSubject = "विषय जोडा", buildFromNotes = "तुमच्या नोट्समधून तयार करा",
    noNotesYet = "अजून नोट्स नाहीत — जोडण्यासाठी उघडा", remove = "काढा",
    nameYourSubject = "तुमच्या विषयाला नाव द्या (उदा. अर्थशास्त्र). पुढे नोट्स जोडा.",
    subjectNameLabel = "विषयाचे नाव", create = "तयार करा", cancel = "रद्द करा",
    importNotes = "नोट्स इम्पोर्ट करा", askPlaceholder = "उदा.: solve 2x + 3 = 7",
    ask = "विचारा", sources = "स्रोत: ",
    localProfile = "लोकल प्रोफाइल",
    localProfileDesc = "खाते नाही, पासवर्ड नाही — काहीही या डिव्हाइसच्या बाहेर जात नाही.",
    offlineTitle = "ऑफलाइन",
    offlineDesc = "सामग्री तुमच्या डिव्हाइसवर डाउनलोड व पडताळली जाते. लॉग आउट करून पुन्हा लॉगिन करेपर्यंत इंटरनेट वापरले जात नाही.",
    aiTutor = "AI शिक्षक",
    aiTutorDesc = "डिव्हाइसवर चालणारा AI (~2 GB) डाउनलोड करा म्हणजे तो कोणतीही गणित किंवा भौतिकशास्त्राची समस्या टप्प्याटप्प्याने सोडवू शकेल. WiFi वापरा — हा मोठा, एकदाच होणारा डाउनलोड आहे जो खंडित झाल्यास पुन्हा सुरू होतो. पडताळलेले गणित सोडवणे याशिवायही चालते.",
    downloadModel = "AI मॉडेल डाउनलोड करा (2 GB)", downloading = "डाउनलोड होत आहे…",
    startingDownload = "डाउनलोड सुरू होत आहे…", modelInstalled = "✓ AI मॉडेल इंस्टॉल झाले",
    modelNotRunnable = "टीप: ही बिल्ड अजून मॉडेल चालवू शकत नाही (ऑन-डिव्हाइस इन्फरन्स समाविष्ट नाही). पडताळलेले टप्प्याटप्प्याने गणित सोडवणे तरीही चालते.",
    downloadFailed = "डाउनलोड अयशस्वी", appearance = "स्वरूप",
    themeSystem = "सिस्टम", themeLight = "उजळ", themeDark = "गडद",
    logOut = "लॉग आउट",
    logOutDesc = "लॉग आउट केल्यावर डाउनलोड केलेली सामग्री हटते जेणेकरून जागा मोकळी होते. तुमचे बुकमार्क व नोट्स जपले जातात आणि त्याच नाव व इयत्तेने पुन्हा लॉगिन केल्यावर परत येतात.",
    searchPlaceholder = "द्विघात समीकरण / quadratic", go = "शोधा",
    nothingSaved = "अजून काहीही जतन केलेले नाही.\nब्राउझ करताना ☆ ने संकल्पना जतन करा.",
    addNote = "एक नोट जोडा…", saveNote = "नोट जतन करा",
    backToBooks = "← पुस्तकांकडे परत",
    askPlaceholderMaths = "उदा.: solve 2x + 3 = 7",
    askPlaceholderScience = "उदा.: गुरुत्वाकर्षण म्हणजे काय?",
    askPlaceholderGeography = "उदा.: क्षेत्रभेट म्हणजे काय?",
    askPlaceholderHistory = "उदा.: इतिहासलेखन म्हणजे काय?",
    askPlaceholderNotes = "उदा.: तुमच्या नोट्समधून प्रश्न विचारा",
    chooseChapter = "धडा निवडा",
    chooseChapterDesc = "AI लक्ष केंद्रित ठेवण्यासाठी एक धडा निवडा — किंवा संपूर्ण विषय अभ्यासा.",
    allChapters = "संपूर्ण विषय",
    allChaptersDesc = "प्रत्येक धड्यातून विचारा",
    chaptersWord = "धडे",
    chapterWord = "धडा",
    helloUser = "नमस्कार,",
    accountTitle = "खाते आणि बॅकअप",
    accountDesc = "ऐच्छिक: तुमच्या प्रश्नोत्तरांचा बॅकअप घेण्यासाठी खाते तयार करा. सर्व काही ऑफलाइनच चालते — इतिहास तुमच्या फोनवर जतन होतो आणि ऑनलाइन होताच आपोआप अपलोड होतो.",
    accountEmailLabel = "ईमेल",
    accountPasswordLabel = "पासवर्ड",
    accountCreate = "खाते तयार करा",
    accountSignIn = "साइन इन करा",
    accountSignedInAs = "साइन इन:",
    accountSignOut = "साइन आउट",
    accountSyncNow = "आता सिंक करा",
    accountAllSynced = "✓ सर्व उत्तरांचा बॅकअप झाला",
    accountPending = "उत्तरे अपलोडच्या प्रतीक्षेत",
    accountSynced = "उत्तरांचा बॅकअप झाला",
    accountSyncing = "सिंक होत आहे…",
    accountSyncFailed = "सिंक अयशस्वी",
    accountNeedsInternet = "खाते तयार करण्यासाठी इंटरनेट कनेक्शन आवश्यक आहे.",
    accountNotConfigured = "या बिल्डमध्ये बॅकअप कॉन्फिगर केलेले नाही (सक्षम करण्यासाठी SYNC_BASE_URL सेट करा). तुमचा इतिहास तरीही याच डिव्हाइसवर जतन होतो.",
    subjMaths1 = "गणित 1", subjMaths2 = "गणित 2",
    subjScience = "विज्ञान", subjScience1 = "विज्ञान 1", subjScience2 = "विज्ञान 2",
    subjGeography = "भूगोल", subjSocialStudies = "सामाजिक शास्त्र / इतिहास",
)

/** The string table for a profile [medium] ("English" | "Hindi" | "Marathi"). */
fun stringsFor(medium: String): AppStrings = when (medium) {
    "Hindi" -> HindiStrings
    "Marathi" -> MarathiStrings
    else -> EnglishStrings
}

/** Provided at the app root from the logged-in medium; English until then. */
val LocalStrings = staticCompositionLocalOf { EnglishStrings }
