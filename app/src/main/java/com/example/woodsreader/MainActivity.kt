package com.example.woodsreader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.woodsreader.ui.theme.WoodsReaderTheme
import com.example.woodsreader.ui.navigation.Screen
import com.example.woodsreader.ui.navigation.LectorNavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import dagger.hilt.android.AndroidEntryPoint

import android.Manifest
import android.content.Intent
import android.util.Log
import android.widget.Toast
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.woodsreader.data.datastore.UserPreferencesRepository
import com.example.woodsreader.data.repository.BookRepository
import com.example.woodsreader.data.source.BookContentHash
import com.example.woodsreader.data.source.BookSourceFormatValidator
import com.example.woodsreader.data.source.ContentProviderIo
import com.example.woodsreader.data.source.LatestImportRequestGate
import com.example.woodsreader.service.BookMetadataExtractor
import com.example.woodsreader.ui.permissions.PermissionsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.util.UUID
import java.io.File
import javax.inject.Inject
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.woodsreader.service.BookScannerService
import com.example.woodsreader.worker.LibrarySyncWorker
import java.util.concurrent.TimeUnit
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.woodsreader.ui.library.LibraryViewModel


/**
 * Entrada principal de la app.
 * @AndroidEntryPoint habilita la inyección de Hilt en esta Activity.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var userPreferencesRepo: UserPreferencesRepository

    @Inject
    lateinit var bookRepository: BookRepository

    @Inject
    lateinit var bookMetadataExtractor: BookMetadataExtractor

    @Inject
    lateinit var scannerService: BookScannerService

    /**
     * Emite el bookId de un archivo abierto externamente.
     * El NavHost lo consume y navega directo al lector.
     */
    private val _pendingBookId = MutableStateFlow<String?>(null)
    val pendingBookId: StateFlow<String?> = _pendingBookId.asStateFlow()
    private val externalImportGate = LatestImportRequestGate()
    private var externalImportJob: Job? = null
    private var permissionCheckInProgress = false
    private var permissionRequestPending = false

    // Registrar result launcher para STORAGE en Android 11+
    private val requestManageStorage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionsAndSave()
    }

    // Registrar result launcher para almacenamiento viejo y Notificaciones
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissionsAndSave()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Inicializar SplashScreen antes de super.onCreate
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Mantener la splash screen en pantalla hasta que DataStore cargue
        var isDataStoreLoaded = false
        lifecycleScope.launch {
            userPreferencesRepo.userPreferencesFlow.collect {
                isDataStoreLoaded = true
            }
        }
        splashScreen.setKeepOnScreenCondition { !isDataStoreLoaded }

        setContent {
            val prefs by userPreferencesRepo.userPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = null
            )
            
            // Esperar a que cargue DataStore
            if (prefs == null) return@setContent

            val isExpired = remember { isBetaExpired() }

            WoodsReaderTheme(readerTheme = prefs!!.theme) {
                val hasPermissions = hasRequiredPermissions()

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (isExpired) {
                        BetaExpiredScreen(
                            onCloseApp = { finishAffinity() }
                        )
                    } else if (!hasPermissions && !prefs!!.manualFolderScanOnly) {
                        PermissionsScreen(
                            onRequestPermission = { launchPermissionRequest() },
                            onChooseFoldersManually = {
                                lifecycleScope.launch {
                                    try {
                                        userPreferencesRepo.setManualFolderScanOnly(true)
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        Log.e("WoodsReader", "Could not save manual folder mode", e)
                                        Toast.makeText(
                                            this@MainActivity,
                                            "No se pudo guardar el modo manual. Inténtalo de nuevo.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }
                        )
                    } else {
                        // Permisos otorgados -> mostrar Library/Onboarding
                        MainLayout(
                            hasCompletedOnboarding = prefs!!.hasCompletedOnboarding,
                            lastBookId    = prefs!!.lastReadBookId,
                            pendingBookId = pendingBookId,
                            onPendingConsumed = { _pendingBookId.value = null }
                        )
                    }
                }
            }
        }
        handleIncomingIntent(intent)
        scheduleBackgroundSync()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        checkPermissionsAndSave()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun checkPermissionsAndSave() {
        if (!hasRequiredPermissions() || permissionCheckInProgress) return
        permissionCheckInProgress = true
        lifecycleScope.launch {
            try {
                val current = userPreferencesRepo.userPreferencesFlow.first()
                val firstGrant = !current.hasGrantedPermissions
                val returningFromManualMode = current.manualFolderScanOnly
                if (firstGrant) userPreferencesRepo.markPermissionsGranted()
                if (returningFromManualMode) {
                    userPreferencesRepo.setManualFolderScanOnly(false)
                }

                if (firstGrant || permissionRequestPending || returningFromManualMode) {
                    scannerService.scan(current.customScanPaths)
                }
                permissionRequestPending = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WoodsReader", "Could not save storage permission state", e)
                Toast.makeText(
                    this@MainActivity,
                    "No se pudo guardar el estado del permiso.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                permissionCheckInProgress = false
            }
        }
    }

    private fun launchPermissionRequest() {
        permissionRequestPending = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.addCategory("android.intent.category.DEFAULT")
                intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                requestManageStorage.launch(intent)
            } catch (e: Exception) {
                val intent = Intent()
                intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                requestManageStorage.launch(intent)
            }
        } else {
            requestPermissions.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    private fun isRunningInTestLab(): Boolean {
        return try {
            Settings.System.getString(contentResolver, "firebase.test.lab") == "true"
        } catch (e: Exception) {
            false
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        if (isRunningInTestLab()) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return

        val requestId = externalImportGate.begin()
        externalImportJob?.cancel()
        externalImportJob = null
        _pendingBookId.value = null

        val uri = intent.data ?: return
        Toast.makeText(this, "Abriendo libro...", Toast.LENGTH_SHORT).show()
        externalImportJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (!isValidIncomingBook(uri)) {
                    withContext(Dispatchers.Main.immediate) {
                        if (externalImportGate.isCurrent(requestId)) {
                            Toast.makeText(
                                this@MainActivity,
                                "No se pudo leer un PDF o EPUB válido.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    return@launch
                }

                val name = resolveFileName(uri)
                val size = resolveFileSize(uri)
                val hash = scannerService.computePartialHash(uri)
                if (!externalImportGate.isCurrent(requestId)) return@launch

                val existing = (if (BookContentHash.isContentAddress(hash)) {
                    bookRepository.getBookByHash(hash)
                } else {
                    null
                }) ?: bookRepository.getBookByPath(uri.toString())

                if (!externalImportGate.isCurrent(requestId)) return@launch
                if (existing != null) {
                    publishPendingBookIfCurrent(existing.id, requestId)
                    return@launch
                }

                val entity = bookMetadataExtractor.extractMetadata(uri, name, size, hash)
                if (entity == null) {
                    withContext(Dispatchers.Main.immediate) {
                        if (externalImportGate.isCurrent(requestId)) {
                            Toast.makeText(
                                this@MainActivity,
                                "No se pudo leer un PDF o EPUB válido.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    return@launch
                }

                val persistedEntity = entity.copy(
                    fileHash = hash.takeIf(BookContentHash::isContentAddress)
                        ?: "pending-import-${UUID.randomUUID()}"
                )
                bookRepository.upsertBookIfCurrent(persistedEntity) {
                    externalImportGate.isCurrent(requestId)
                }
                publishPendingBookIfCurrent(persistedEntity.id, requestId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("WoodsReader", "External book import failed (${e.javaClass.simpleName})")
                withContext(Dispatchers.Main.immediate) {
                    if (externalImportGate.isCurrent(requestId)) {
                        Toast.makeText(
                            this@MainActivity,
                            "No se pudo abrir el libro.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private suspend fun isValidIncomingBook(uri: Uri): Boolean {
        val format = if (uri.scheme == "file" && uri.path != null) {
            File(uri.path!!).inputStream().use(BookSourceFormatValidator::detectFormat)
        } else {
            ContentProviderIo.run {
                contentResolver.openInputStream(uri)
                    ?.let(BookSourceFormatValidator::detectFormat)
            }
        }
        return format != null
    }

    private suspend fun publishPendingBookIfCurrent(bookId: String, requestId: Long) {
        withContext(Dispatchers.Main.immediate) {
            if (externalImportGate.isCurrent(requestId)) {
                _pendingBookId.value = bookId
            }
        }
    }

    /** Resuelve el nombre del archivo desde la URI (content:// o file://). */
    private suspend fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "content") {
            val name = ContentProviderIo.run {
                contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (col >= 0) cursor.getString(col) else null
                    } else {
                        null
                    }
                }
            }
            if (!name.isNullOrBlank()) return name
        }
        return uri.lastPathSegment ?: "book.epub"
    }

    /** Resuelve el tamaño del archivo desde la URI. */
    private suspend fun resolveFileSize(uri: Uri): Long {
        if (uri.scheme == "content") {
            return ContentProviderIo.run {
                contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (col >= 0 && !cursor.isNull(col)) cursor.getLong(col) else 0L
                    } else {
                        0L
                    }
                } ?: 0L
            }
        }
        return 0L
    }

    /**
     * Android 13+ (API 33) requiere permiso en tiempo de ejecución para mostrar notificaciones.
     * Sin POST_NOTIFICATIONS el servicio foreground de medios no aparece en el panel ni en la
     * pantalla de bloqueo. Se solicita al arrancar; el usuario solo lo ve una vez.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    /* requestCode = */ 1001
                )
            }
        }
    }

    private fun scheduleBackgroundSync() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<LibrarySyncWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "LibrarySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    private fun isBetaExpired(): Boolean {
        if (BuildConfig.DEBUG || BuildConfig.BETA_EXPIRATION_DAYS <= 0L) return false
        val maxDurationMillis = BuildConfig.BETA_EXPIRATION_DAYS * 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val buildTime = BuildConfig.BUILD_TIME_MILLIS
        return (now - buildTime) > maxDurationMillis
    }
}

// ── Destinos de navegación ──────────────────────────────────────
@Composable
fun MainLayout(
    hasCompletedOnboarding: Boolean,
    lastBookId: String?,
    pendingBookId: StateFlow<String?>,
    onPendingConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Historial de libros: obtenemos el ViewModel compartido con LibraryScreen
    val libraryVm: LibraryViewModel = hiltViewModel()
    val recentBooks by libraryVm.recentBooks.collectAsStateWithLifecycle()

    // Navegar al lector cuando se abre un archivo externamente (ej. desde el explorador).
    val pendingId by pendingBookId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingId) {
        val id = pendingId ?: return@LaunchedEffect
        onPendingConsumed()
        navController.navigate(Screen.Reader.createRoute(id)) {
            popUpTo(Screen.Library.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    // Reaccionar inmediatamente cuando se restablece la bienvenida desde Ajustes
    LaunchedEffect(hasCompletedOnboarding) {
        if (!hasCompletedOnboarding && currentRoute != Screen.Onboarding.route) {
            navController.navigate(Screen.Onboarding.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val showBottomBar = currentRoute != null &&
            !currentRoute.startsWith("reader") &&
            !currentRoute.startsWith("onboarding") &&
            !currentRoute.startsWith("book_detail")

    androidx.compose.material3.Scaffold(
        containerColor = com.example.woodsreader.ui.theme.HiddenForest.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                com.example.woodsreader.ui.library.WoodsBottomNavigation(
                    currentRoute      = currentRoute,
                    lastBookId        = lastBookId ?: recentBooks.firstOrNull()?.id,
                    recentBooks       = recentBooks,
                    onNavigate        = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onHistoryBookClick = { bookId ->
                        navController.navigate(Screen.Reader.createRoute(bookId)) {
                            popUpTo(Screen.Library.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { _ ->
        LectorNavHost(
            hasCompletedOnboarding = hasCompletedOnboarding,
            navController = navController,
            modifier = Modifier.fillMaxSize()
        )
    }
}


@Composable
fun BetaExpiredScreen(
    onCloseApp: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HiddenForest.Background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(HiddenForest.SurfaceVariant)
                    .border(1.dp, HiddenForest.Outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = HiddenForest.Accent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Text(
                text = "VERSIÓN DE PRUEBA FINALIZADA",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextLow,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "Periodo Beta Concluido",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = HiddenForest.Text,
                textAlign = TextAlign.Center
            )

            Text(
                text = "El periodo de prueba beta para esta versión anticipada de Woods Reader ha concluido.\n\nPara continuar disfrutando de la lectura y acceder a la versión más reciente, por favor solicita una actualización al desarrollador.",
                style = MaterialTheme.typography.bodyMedium,
                color = HiddenForest.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onCloseApp,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HiddenForest.Accent,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(48.dp)
            ) {
                Text(
                    text = "Cerrar Aplicación",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
