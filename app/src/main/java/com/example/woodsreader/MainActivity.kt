package com.example.woodsreader

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
import com.example.woodsreader.service.BookMetadataExtractor
import com.example.woodsreader.ui.permissions.PermissionsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
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

            WoodsReaderTheme(readerTheme = prefs!!.theme) {
                val hasPermissions = hasRequiredPermissions()

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!hasPermissions && !prefs!!.hasGrantedPermissions) {
                        PermissionsScreen(
                            onPermissionsGranted = { checkPermissionsAndSave() },
                            onRequestPermission = { launchPermissionRequest() }
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
        if (hasRequiredPermissions()) {
            lifecycleScope.launch {
                // << CHANGE: Validación previa para evitar escrituras redundantes en DataStore
                val current = userPreferencesRepo.userPreferencesFlow.first()
                if (current.hasGrantedPermissions) return@launch
                
                userPreferencesRepo.markPermissionsGranted()
            }
        }
    }

    private fun launchPermissionRequest() {
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

    private fun hasRequiredPermissions(): Boolean {
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
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri != null) {
                Log.d("WoodsReader", "URI externo recibido: $uri")
                Toast.makeText(this, "Abriendo libro...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Delegates to BookScannerService's shared implementation to avoid code duplication. */
    private fun computePartialHash(uri: Uri): String = scannerService.computePartialHash(uri)

    /** Resuelve el nombre del archivo desde la URI (content:// o file://). */
    private fun resolveFileName(uri: Uri): String {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (col >= 0) return cursor.getString(col)
                }
            }
        }
        return uri.lastPathSegment ?: "book.epub"
    }

    /** Resuelve el tamaño del archivo desde la URI. */
    private fun resolveFileSize(uri: Uri): Long {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (col >= 0) return cursor.getLong(col)
                }
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
            .setRequiresDeviceIdle(true) // Solo escanea cuando no se usa el móvil
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

    val showBottomBar = currentRoute != Screen.Reader.route &&
            currentRoute != Screen.Onboarding.route &&
            !(currentRoute?.startsWith("book_detail/") == true)

    androidx.compose.material3.Scaffold(
        containerColor = com.example.woodsreader.ui.theme.HiddenForest.Background,
        bottomBar = {
            if (showBottomBar) {
                com.example.woodsreader.ui.library.WoodsBottomNavigation(
                    currentRoute      = currentRoute,
                    lastBookId        = lastBookId,
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
    ) { padding ->
        LectorNavHost(
            hasCompletedOnboarding = hasCompletedOnboarding,
            navController = navController,
            modifier = Modifier.padding(padding)
        )
    }
}
