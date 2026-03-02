@file:Suppress("DEPRECATION")

package com.doptsw.dongdiary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.doptsw.dongdiary.data.DiaryEntry
import com.doptsw.dongdiary.data.DiaryImage
import com.doptsw.dongdiary.data.DiaryRepository
import com.doptsw.dongdiary.data.LocalJsonStore
import com.doptsw.dongdiary.ui.theme.DongDiaryTheme
import com.doptsw.dongdiary.settings.SettingsRepository
import com.doptsw.dongdiary.settings.UserSettings
import com.doptsw.dongdiary.cloud.CloudBackupManager
import com.doptsw.dongdiary.cloud.BackupResult
import com.doptsw.dongdiary.cloud.RestoreResult
import com.doptsw.dongdiary.util.ImageUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.time.LocalDate
import java.io.File
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.security.MessageDigest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DongDiaryApp()
        }
    }
}

@Composable
fun DongDiaryApp() {
    DongDiaryTheme {
        val activity = LocalActivity.current as? ComponentActivity
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val settingsRepository = remember { SettingsRepository(context) }
        var currentSettings by remember { mutableStateOf<UserSettings?>(null) }
        var isUnlockedInSession by remember { mutableStateOf(false) }
        var shouldLockOnResume by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            currentSettings = settingsRepository.settingsFlow.first()
        }

        DisposableEffect(lifecycleOwner, activity, currentSettings) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> {
                        if (activity?.isChangingConfigurations != true) {
                            shouldLockOnResume = true
                        }
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        val loadedSettings = currentSettings
                        if (
                            shouldLockOnResume &&
                            loadedSettings != null &&
                            settingsRepository.hasPasscode(loadedSettings)
                        ) {
                            isUnlockedInSession = false
                        }
                        shouldLockOnResume = false
                    }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val loadedSettings = currentSettings
            if (loadedSettings == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "로딩 중...")
                }
            } else if (settingsRepository.hasPasscode(loadedSettings) && !isUnlockedInSession) {
                LockScreen(
                    passcodeHash = loadedSettings.passcodeHash.orEmpty(),
                    onUnlockSuccess = { isUnlockedInSession = true },
                )
            } else {
                val navController = rememberNavController()
                DongDiaryNavHost(navController = navController)
            }
        }
    }
}

@Composable
fun DongDiaryNavHost(
    navController: NavHostController,
) {
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenDay = { date -> navController.navigate("day/$date") }
                )
            }

            composable("day/{date}") { backStackEntry ->
                val dateArg = backStackEntry.arguments?.getString("date")
                val baseDate = try {
                    dateArg?.let { LocalDate.parse(it) } ?: LocalDate.now()
                } catch (_: Exception) {
                    LocalDate.now()
                }
                DayDetailScreen(
                    baseDate = baseDate,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenDay: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { DiaryRepository(LocalJsonStore(context)) }

    val today = LocalDate.now()
    var currentYearMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var selectedDate by remember { mutableStateOf(today) }
    var entries by remember { mutableStateOf(emptyList<DiaryEntry>()) }
    var recentEntries by remember { mutableStateOf(emptyList<DiaryEntry>()) }
    var visibleRecentMonthCount by remember { mutableStateOf(10) }
    var expandedRecentMonthKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        entries = repository.getAll().entries
        recentEntries = repository.getAllSortedByUpdatedAtDesc()
    }

        val entriesById = remember(entries) {
            entries.groupBy { it.id }
        }
    val recentEntriesByMonth = remember(recentEntries) {
        recentEntries.groupBy { it.date.take(7) }.toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        val nextMonth = currentYearMonth.plusMonths(1)
        val canGoNextMonth = nextMonth.year < today.year ||
            (nextMonth.year == today.year && nextMonth.monthValue <= today.monthValue)
        val isCurrentMonth = currentYearMonth.year == today.year &&
            currentYearMonth.monthValue == today.monthValue

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${currentYearMonth.year}년 ${currentYearMonth.monthValue}월",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 56.dp),
                    )
                    TextButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Text(text = "설정")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        currentYearMonth = currentYearMonth.minusYears(1)
                    }) {
                        Text(text = "이전해", maxLines = 1)
                    }
                    TextButton(onClick = {
                        currentYearMonth = currentYearMonth.minusMonths(1)
                    }) {
                        Text(text = "이전달", maxLines = 1)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { currentYearMonth = today.withDayOfMonth(1) },
                        enabled = !isCurrentMonth,
                    ) {
                        Text(text = "이번달", maxLines = 1)
                    }
                    TextButton(
                        onClick = {
                            if (canGoNextMonth) {
                                currentYearMonth = nextMonth
                            }
                        },
                        enabled = canGoNextMonth,
                    ) {
                        Text(text = "다음달", maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        WeekHeaderRow()

        Spacer(modifier = Modifier.height(4.dp))

        val calendarDates = remember(currentYearMonth) {
            buildMonthCells(currentYearMonth)
        }

        LazyVerticalGrid(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            columns = GridCells.Fixed(7),
            verticalArrangement = Arrangement.Top,
            horizontalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(calendarDates) { date ->
                if (date == null) {
                    Spacer(
                        modifier = Modifier
                            .padding(4.dp)
                            .height(40.dp),
                    )
                } else {
                    val hasTodayEntry = entriesById[date.toString()]?.isNotEmpty() == true
                    val hasHistory = entries.any { e ->
                        val d = LocalDate.parse(e.date)
                        d.monthValue == date.monthValue &&
                            d.dayOfMonth == date.dayOfMonth &&
                            d.year < date.year
                    }
                    val isToday = date == LocalDate.now()
                    val isFuture = date.isAfter(today)
                    val isEditableRecentDate = !date.isBefore(today.minusDays(6)) && !isFuture
                    val hasAnyEntry = hasTodayEntry || hasHistory
                    val isClickable = when {
                        isFuture -> false
                        isEditableRecentDate -> true
                        else -> hasAnyEntry
                    }
                    DayCell(
                        date = date,
                        isToday = isToday,
                        isEditableRecentDate = isEditableRecentDate,
                        isSelected = date == selectedDate,
                        hasEntry = hasTodayEntry,
                        hasHistory = hasHistory,
                        enabled = isClickable,
                        onClick = {
                            selectedDate = date
                            onOpenDay(date.toString())
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "최근 일기",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (recentEntries.isEmpty()) {
            Text(
                text = "최근 일기가 없습니다.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                lazyItems(
                    items = recentEntriesByMonth.take(visibleRecentMonthCount),
                    key = { it.first },
                ) { monthGroup ->
                    val monthKey = monthGroup.first
                    val monthEntries = monthGroup.second
                    val isExpanded = expandedRecentMonthKey == monthKey

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = if (isExpanded) 2.dp else 0.dp,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(
                                onClick = {
                                    expandedRecentMonthKey = if (isExpanded) {
                                        null
                                    } else {
                                        monthKey
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = monthKey,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = "${monthEntries.size}개의 일기 작성",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = monthEntries.size.toString(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text(
                                        text = if (isExpanded) "접기" else "펼치기",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            if (isExpanded) {
                                monthEntries.forEach { entry ->
                                    RecentDiaryItem(
                                        entry = entry,
                                        onClick = { onOpenDay(entry.date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (recentEntriesByMonth.size > visibleRecentMonthCount) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = { visibleRecentMonthCount += 10 },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "더 보기")
                }
            }
        }

        Button(
            onClick = { onOpenDay(LocalDate.now().toString()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(text = "오늘의 일기 보러가기")
        }
    }
}

@Composable
fun DayDetailScreen(
    baseDate: LocalDate,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { DiaryRepository(LocalJsonStore(context)) }
    val today = LocalDate.now()
    val editableStartDate = today.minusDays(6)
    val scope = rememberCoroutineScope()

    val isPast = baseDate.isBefore(today)
    val canEditDate = !baseDate.isBefore(editableStartDate) && !baseDate.isAfter(today)

    var todayEntry by remember(baseDate) { mutableStateOf(repository.getEntriesForDate(baseDate).firstOrNull()) }
    var todayContent by remember(baseDate) { mutableStateOf(todayEntry?.content.orEmpty()) }
    var todayImages by remember(baseDate) { mutableStateOf(todayEntry?.images.orEmpty()) }
    var isEditingToday by remember(baseDate) { mutableStateOf(todayEntry == null && canEditDate) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var selectedImagePreview by remember { mutableStateOf<String?>(null) }

    val historicalEntries = remember(baseDate) {
        repository.getHistoricalEntriesForSameDay(baseDate)
            .sortedByDescending { it.date }
    }

    // Image picker launcher - using GetMultipleContents for broader compatibility
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    val newImages = mutableListOf<DiaryImage>()
                    for (uri in uris) {
                        val base64 = ImageUtil.compressImageToBase64(context, uri)
                        if (base64 != null) {
                            val image = DiaryImage(
                                id = ImageUtil.generateImageId(),
                                filename = ImageUtil.generateImageFilename(),
                                base64Data = base64,
                                timestamp = java.time.LocalDateTime.now().toString(),
                            )
                            newImages.add(image)
                        }
                    }
                    if (newImages.isNotEmpty()) {
                        todayImages = todayImages + newImages
                    }
                }
            }
        }
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .imePadding(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text(text = "뒤로")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${baseDate.year}년 ${baseDate.monthValue}월 ${baseDate.dayOfMonth}일",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "같은 날의 지난 일기들",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 과거 날짜: 조회 전용
            if (isPast && !canEditDate) {
                val selectedEntries = remember(baseDate) {
                    repository.getEntriesForDate(baseDate)
                }

                if (selectedEntries.isEmpty() && historicalEntries.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "이 날의 일기가 아직 없어요.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    if (selectedEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "이 날의 일기",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            selectedEntries.forEach { entry ->
                                HistoricalEntryCard(entry = entry, onImageClick = { selectedImagePreview = it })
                            }
                        }
                    }

                    if (historicalEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "이전 해 같은 날의 일기들",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            historicalEntries.forEach { entry ->
                                HistoricalEntryCard(entry = entry, onImageClick = { selectedImagePreview = it })
                            }
                        }
                    }
                }
            } else {
                // 오늘: 편집/보기 모드 + 과거 연도 일기
                if (historicalEntries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "이전 해 같은 날의 일기들",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        historicalEntries.forEach { entry ->
                            HistoricalEntryCard(entry = entry, onImageClick = { selectedImagePreview = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (canEditDate) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                    ) {
                        Text(
                            text = "선택한 날짜의 일기",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (!isEditingToday && todayEntry != null) {
                            // 뷰 모드
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true)
                                    .verticalScroll(rememberScrollState())
                                    .padding(bottom = 8.dp),
                            ) {
                                Text(
                                    text = todayEntry?.content.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                // Display images
                                if (todayImages.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "사진",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    ImageGallery(
                                        images = todayImages,
                                        onImageClick = { selectedImagePreview = it }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                TextButton(onClick = { isEditingToday = true }) {
                                    Text(text = "일기 수정하기")
                                }
                                if (todayEntry != null) {
                                    TextButton(onClick = { showDeleteConfirm = true }) {
                                        Text(text = "일기 삭제하기")
                                    }
                                }
                            }
                        } else {
                            // 편집 모드
                            androidx.compose.material3.OutlinedTextField(
                                value = todayContent,
                                onValueChange = { todayContent = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 300.dp),
                                placeholder = { Text(text = "이 날 있었던 일을 자유롭게 적어보세요.") },
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Image preview during editing
                            if (todayImages.isNotEmpty()) {
                                Text(
                                    text = "추가된 사진 (${todayImages.size}개)",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp),
                                ) {
                                    items(todayImages.size) { index ->
                                        val image = todayImages[index]
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(100.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { selectedImagePreview = image.base64Data }
                                        ) {
                                            val context = LocalContext.current
                                            val cachedFile = remember(image.id, image.filename) {
                                                ImageUtil.base64ToCachedFile(context, image.base64Data, image.filename)
                                            }

                                            if (cachedFile != null && cachedFile.exists()) {
                                                val request = ImageRequest.Builder(context)
                                                    .data(cachedFile)
                                                    .crossfade(true)
                                                    .build()

                                                SubcomposeAsyncImage(
                                                    model = request,
                                                    contentDescription = "gallery-thumbnail",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                ) {
                                                    when (painter.state) {
                                                        is AsyncImagePainter.State.Loading -> {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(4.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(text = "로딩 중...", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                        is AsyncImagePainter.State.Error -> {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(4.dp),
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                verticalArrangement = Arrangement.Center
                                                            ) {
                                                                Text(text = "이미지 로드 실패", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        }
                                                        else -> {
                                                            SubcomposeAsyncImageContent()
                                                        }
                                                    }
                                                }
                                            } else {
                                                // fallback: try bytes if file couldn't be written
                                                val imageBytes = remember(image.base64Data) { ImageUtil.base64ToByteArray(image.base64Data) }
                                                if (imageBytes != null) {
                                                    val request = ImageRequest.Builder(context)
                                                        .data(imageBytes)
                                                        .crossfade(true)
                                                        .build()

                                                    SubcomposeAsyncImage(
                                                        model = request,
                                                        contentDescription = "gallery-thumbnail",
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentScale = ContentScale.Crop,
                                                    ) {
                                                        when (painter.state) {
                                                            is AsyncImagePainter.State.Loading -> {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(4.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    verticalArrangement = Arrangement.Center
                                                                ) {
                                                                    Text(text = "로딩 중...", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                            is AsyncImagePainter.State.Error -> {
                                                                Column(
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .padding(4.dp),
                                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                                    verticalArrangement = Arrangement.Center
                                                                ) {
                                                                    Text(text = "이미지 로드 실패", style = MaterialTheme.typography.labelSmall)
                                                                }
                                                            }
                                                            else -> {
                                                                SubcomposeAsyncImageContent()
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(4.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(text = "이미지 없음", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    todayImages = todayImages.filterIndexed { i, _ -> i != index }
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .background(
                                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        shape = MaterialTheme.shapes.small
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Delete image",
                                                    tint = MaterialTheme.colorScheme.onError
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { 
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add images",
                                        modifier = Modifier.height(20.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("사진 추가")
                                }

                                Button(
                                    onClick = {
                                        repository.saveOrUpdateDate(baseDate, todayContent, todayImages)
                                        todayEntry = repository.getEntriesForDate(baseDate).firstOrNull()
                                        if (todayEntry != null) {
                                            todayImages = todayEntry!!.images
                                        }
                                        isEditingToday = false
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                ) {
                                    Text(
                                        text = if (todayEntry == null) {
                                            "저장"
                                        } else {
                                            "수정 완료"
                                        },
                                    )
                                }
                            }

                            if (todayEntry != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(text = "일기 삭제하기")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        repository.deleteByDate(baseDate)
                        todayEntry = null
                        todayContent = ""
                        todayImages = emptyList()
                        showDeleteConfirm = false
                    },
                ) {
                    Text(text = "삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = "취소")
                }
            },
            title = { Text(text = "일기 삭제") },
            text = { Text(text = "정말로 이 날짜의 일기를 삭제할까요? 이 동작은 되돌릴 수 없어요.") },
        )
    }

    // Image preview modal
    if (selectedImagePreview != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedImagePreview = null },
            confirmButton = {
                TextButton(onClick = { selectedImagePreview = null }) {
                    Text(text = "닫기")
                }
            },
            title = { Text(text = "사진") },
            text = {
                val previewBytes = remember(selectedImagePreview) { selectedImagePreview?.let { ImageUtil.base64ToByteArray(it) } }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (previewBytes != null) {
                        val request = ImageRequest.Builder(LocalContext.current)
                            .data(previewBytes)
                            .crossfade(true)
                            .build()

                        SubcomposeAsyncImage(
                            model = request,
                            contentDescription = "preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            contentScale = ContentScale.Fit,
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Loading -> {
                                    Text(text = "로딩 중...", style = MaterialTheme.typography.bodySmall)
                                }
                                is AsyncImagePainter.State.Error -> {
                                    Text(text = "이미지를 불러올 수 없습니다.", style = MaterialTheme.typography.bodySmall)
                                }
                                else -> {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }
                    } else {
                        Text(text = "이미지를 로드할 수 없어요.")
                    }
                }
            }
        )
    }
}

@Composable
private fun HistoricalEntryCard(
    entry: DiaryEntry,
    onImageClick: (String) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${entry.date} 의 일기",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = entry.content,
            style = MaterialTheme.typography.bodyMedium,
        )

        // Display images if present
        if (entry.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            ImageGallery(
                images = entry.images,
                onImageClick = onImageClick
            )
        }
    }
}

@Composable
private fun ImageGallery(
    images: List<DiaryImage>,
    onImageClick: (String) -> Unit = {}
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
    ) {
        items(images.size) { index ->
            val image = images[index]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    .clickable { onImageClick(image.base64Data) }
            ) {
                val context = LocalContext.current
                val cachedFileState = remember(image.id, image.filename) { mutableStateOf<File?>(null) }

                LaunchedEffect(image.id) {
                    val file = try {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            ImageUtil.base64ToCachedFile(context, image.base64Data, image.filename)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                    cachedFileState.value = file
                }

                val cachedFile = cachedFileState.value

                if (cachedFile != null && cachedFile.exists()) {
                    val request = ImageRequest.Builder(context)
                        .data(cachedFile)
                        .crossfade(true)
                        .build()

                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = "thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    ) {
                        when (painter.state) {
                            is AsyncImagePainter.State.Loading -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(text = "로딩 중...", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            is AsyncImagePainter.State.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(text = "로드 실패", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            else -> {
                                SubcomposeAsyncImageContent()
                            }
                        }
                    }
                } else {
                    // fallback to byte array (decoded in memory)
                    val imageBytes = remember(image.base64Data) { ImageUtil.base64ToByteArray(image.base64Data) }
                    if (imageBytes != null) {
                        val request = ImageRequest.Builder(context)
                            .data(imageBytes)
                            .crossfade(true)
                            .build()

                        SubcomposeAsyncImage(
                            model = request,
                            contentDescription = "thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        ) {
                            when (painter.state) {
                                is AsyncImagePainter.State.Loading -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "로딩 중...", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                is AsyncImagePainter.State.Error -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(text = "로드 실패", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                else -> {
                                    SubcomposeAsyncImageContent()
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = "이미지 없음", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { SettingsRepository(context) }
    val diaryRepository = remember { DiaryRepository(LocalJsonStore(context)) }
    val backupManager = remember { CloudBackupManager(context, diaryRepository, repository) }
    val scope = rememberCoroutineScope()

    var settings by remember {
        mutableStateOf(UserSettings())
    }
    var showAddPasscodeDialog by remember { mutableStateOf(false) }
    var showChangePasscodeDialog by remember { mutableStateOf(false) }
    var showRemovePasscodeDialog by remember { mutableStateOf(false) }

    val googleSignInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // Drive 파일 및 appDataFolder 접근 권한 요청
            .requestScopes(
                Scope(DriveScopes.DRIVE_FILE),
                Scope(DriveScopes.DRIVE_APPDATA),
            )
            .build()
    }
    val googleSignInClient = remember {
        GoogleSignIn.getClient(context, googleSignInOptions)
    }

    val activity = LocalActivity.current

    val snackBarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val signInLauncher = rememberLauncherForActivityResult<Intent, ActivityResult>(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        if (task.isSuccessful) {
            try {
                val account = task.result
                scope.launch {
                    repository.updateLoginState(
                        isLoggedIn = true,
                        accountId = account.id,
                        displayName = account.displayName,
                        email = account.email,
                    )
                    settings = settings.copy(
                        isLoggedIn = true,
                        googleAccountId = account.id,
                        googleDisplayName = account.displayName,
                        googleEmail = account.email,
                    )
                    snackBarHostState.showSnackbar("로그인되었습니다: ${account.email ?: account.displayName}")
                }
            } catch (e: Exception) {
                scope.launch {
                    snackBarHostState.showSnackbar("로그인 처리 중 오류: ${e.message}")
                }
            }
        } else {
            val exception = task.exception
            if (exception is ApiException) {
                scope.launch {
                    snackBarHostState.showSnackbar("로그인 실패: ${exception.statusCode}")
                }
            } else {
                scope.launch {
                    snackBarHostState.showSnackbar("로그인이 취소되었습니다.")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val current = repository.settingsFlow.first()
        settings = current
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(hostState = snackBarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text(text = "뒤로")
                }
                Text(
                    text = "설정",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Google 계정",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (settings.isLoggedIn) {
                        Text(
                            text = settings.googleEmail ?: "로그인됨",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    scope.launch {
                                        repository.updateLoginState(
                                            isLoggedIn = false,
                                            accountId = null,
                                            displayName = null,
                                            email = null,
                                        )
                                        settings = settings.copy(
                                            isLoggedIn = false,
                                            googleAccountId = null,
                                            googleDisplayName = null,
                                            googleEmail = null,
                                        )
                                    }
                                }
                            },
                        ) {
                            Text(text = "로그아웃")
                        }
                    } else {
                        Text(
                            text = "로그인하지 않은 상태입니다.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (activity != null) {
                                    val signInIntent = googleSignInClient.signInIntent
                                    signInLauncher.launch(signInIntent)
                                }
                            },
                        ) {
                            Text(text = "Google 계정으로 로그인")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
            Text(
                text = "백업 설정",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Button(
                    enabled = settings.isLoggedIn,
                    onClick = {
                        scope.launch {
                            try {
                                when (backupManager.backupToCloud()) {
                                    BackupResult.SUCCESS ->
                                        snackBarHostState.showSnackbar("백업이 완료되었습니다.")
                                    BackupResult.NOT_LOGGED_IN ->
                                        snackBarHostState.showSnackbar("로그인한 사용자만 백업할 수 있습니다.")
                                    BackupResult.ERROR ->
                                        snackBarHostState.showSnackbar("백업 중 알 수 없는 오류가 발생했습니다.")
                                }
                            } catch (e: Exception) {
                                val message = e.message ?: e::class.java.simpleName
                                snackBarHostState.showSnackbar("백업 오류: $message")
                            }
                        }
                    },
                ) {
                    Text(text = "백업하기")
                }

                Button(
                    enabled = settings.isLoggedIn,
                    onClick = {
                        scope.launch {
                            try {
                                when (backupManager.restoreFromCloudIfNewer()) {
                                    RestoreResult.Success ->
                                        snackBarHostState.showSnackbar("클라우드에서 최신 백업을 불러왔어요.")
                                    RestoreResult.NotLoggedIn ->
                                        snackBarHostState.showSnackbar("로그인한 사용자만 복원할 수 있습니다.")
                                    RestoreResult.NoBackup ->
                                        snackBarHostState.showSnackbar("클라우드 백업이 아직 없습니다.")
                                    RestoreResult.OlderThanLocal ->
                                        snackBarHostState.showSnackbar("클라우드 백업이 로컬보다 오래되어 유지했습니다.")
                                    RestoreResult.Error ->
                                        snackBarHostState.showSnackbar("복원 중 알 수 없는 오류가 발생했습니다.")
                                }
                            } catch (e: Exception) {
                                val message = e.message ?: e::class.java.simpleName
                                snackBarHostState.showSnackbar("복원 오류: $message")
                            }
                        }
                    },
                ) {
                    Text(text = "복구하기")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "앱 종료 시 자동으로 클라우드에 백업",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = settings.autoBackupOnExit,
                    onCheckedChange = { checked ->
                        settings = settings.copy(autoBackupOnExit = checked)
                        scope.launch {
                            repository.updateAutoBackup(checked)
                        }
                    },
                    enabled = settings.isLoggedIn,
                )
            }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 앱 버전 표시
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "비밀번호",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
            if (repository.hasPasscode(settings)) {
                Text(
                    text = "앱 시작 시 4자리 비밀번호를 입력해야 합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showChangePasscodeDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "비밀번호 수정")
                    }
                    Button(
                        onClick = { showRemovePasscodeDialog = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "비밀번호 삭제")
                    }
                }
            } else {
                Text(
                    text = "현재 비밀번호가 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showAddPasscodeDialog = true },
                ) {
                    Text(text = "비밀번호 추가")
                }
            }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val versionName = remember {
                val pm = context.packageManager
                val pkg = context.packageName
                try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0).versionName ?: "?"
                } catch (e: Exception) {
                    "?"
                }
            }
            androidx.compose.material3.Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = "Presented by DOPT $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
    }
    if (showAddPasscodeDialog) {
        PasscodeAddDialog(
            onDismiss = { showAddPasscodeDialog = false },
            onConfirm = { newPin ->
                scope.launch {
                    val hash = hashPin(newPin)
                    repository.setPasscodeHash(hash)
                    settings = settings.copy(passcodeHash = hash)
                    showAddPasscodeDialog = false
                    snackBarHostState.showSnackbar("비밀번호가 설정되었습니다.")
                }
            },
        )
    }

    if (showChangePasscodeDialog) {
        PasscodeChangeDialog(
            onDismiss = { showChangePasscodeDialog = false },
            currentHash = settings.passcodeHash.orEmpty(),
            onConfirm = { newPin ->
                scope.launch {
                    val hash = hashPin(newPin)
                    repository.setPasscodeHash(hash)
                    settings = settings.copy(passcodeHash = hash)
                    showChangePasscodeDialog = false
                    snackBarHostState.showSnackbar("비밀번호가 변경되었습니다.")
                }
            },
        )
    }

    if (showRemovePasscodeDialog) {
        PasscodeRemoveDialog(
            onDismiss = { showRemovePasscodeDialog = false },
            currentHash = settings.passcodeHash.orEmpty(),
            onConfirm = {
                scope.launch {
                    repository.clearPasscodeHash()
                    settings = settings.copy(passcodeHash = null)
                    showRemovePasscodeDialog = false
                    snackBarHostState.showSnackbar("비밀번호가 삭제되었습니다.")
                }
            },
        )
    }
}

@Composable
private fun LockScreen(
    passcodeHash: String,
    onUnlockSuccess: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "앱 잠금", style = MaterialTheme.typography.titleLarge)
            Text(text = "4자리 비밀번호를 입력하세요.", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = sanitizePin(it)
                    errorMessage = null
                },
                label = { Text("비밀번호") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = {
                    if (!isValidPin(pin)) {
                        errorMessage = "비밀번호는 숫자 4자리여야 합니다."
                    } else if (hashPin(pin) != passcodeHash) {
                        errorMessage = "비밀번호가 올바르지 않습니다."
                    } else {
                        onUnlockSuccess()
                    }
                },
                enabled = isValidPin(pin),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "확인")
            }
        }
    }
}

@Composable
private fun RecentDiaryItem(
    entry: DiaryEntry,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = entry.date,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = entry.content.ifBlank { "(내용 없음)" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "수정: ${formatUpdatedAt(entry.updatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PasscodeAddDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                when {
                    !isValidPin(newPin) -> error = "새 비밀번호는 숫자 4자리여야 합니다."
                    newPin != confirmPin -> error = "비밀번호 확인이 일치하지 않습니다."
                    else -> onConfirm(newPin)
                }
            }) { Text(text = "저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "취소") }
        },
        title = { Text(text = "비밀번호 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        newPin = sanitizePin(it)
                        error = null
                    },
                    label = { Text("새 비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = sanitizePin(it)
                        error = null
                    },
                    label = { Text("비밀번호 확인") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

@Composable
private fun PasscodeChangeDialog(
    onDismiss: () -> Unit,
    currentHash: String,
    onConfirm: (String) -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                when {
                    hashPin(currentPin) != currentHash -> error = "현재 비밀번호가 올바르지 않습니다."
                    !isValidPin(newPin) -> error = "새 비밀번호는 숫자 4자리여야 합니다."
                    newPin != confirmPin -> error = "비밀번호 확인이 일치하지 않습니다."
                    else -> onConfirm(newPin)
                }
            }) { Text(text = "변경") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "취소") }
        },
        title = { Text(text = "비밀번호 수정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = {
                        currentPin = sanitizePin(it)
                        error = null
                    },
                    label = { Text("현재 비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = {
                        newPin = sanitizePin(it)
                        error = null
                    },
                    label = { Text("새 비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = {
                        confirmPin = sanitizePin(it)
                        error = null
                    },
                    label = { Text("비밀번호 확인") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

@Composable
private fun PasscodeRemoveDialog(
    onDismiss: () -> Unit,
    currentHash: String,
    onConfirm: () -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (hashPin(currentPin) != currentHash) {
                    error = "현재 비밀번호가 올바르지 않습니다."
                } else {
                    onConfirm()
                }
            }) { Text(text = "삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "취소") }
        },
        title = { Text(text = "비밀번호 삭제") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "현재 비밀번호를 입력하세요.")
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = {
                        currentPin = sanitizePin(it)
                        error = null
                    },
                    label = { Text("현재 비밀번호") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
    )
}

private fun sanitizePin(input: String): String {
    return input.filter { it.isDigit() }.take(4)
}

private fun isValidPin(pin: String): Boolean {
    return pin.length == 4 && pin.all { it.isDigit() }
}

private fun hashPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun formatUpdatedAt(updatedAt: String): String {
    return runCatching {
        val parsed = java.time.LocalDateTime.parse(updatedAt)
        "${parsed.toLocalDate()} ${parsed.toLocalTime().withSecond(0).withNano(0)}"
    }.getOrElse {
        updatedAt.take(16)
    }
}

@Composable
private fun WeekHeaderRow() {
    val days = listOf("일", "월", "화", "수", "목", "금", "토")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isEditableRecentDate: Boolean,
    isSelected: Boolean,
    hasEntry: Boolean,
    hasHistory: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
        isEditableRecentDate -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.background
    }

    val baseModifier = Modifier
        .padding(4.dp)
        .height(56.dp)
        .fillMaxWidth()
        .background(
            color = if (enabled) bgColor else bgColor.copy(alpha = 0.3f),
            shape = MaterialTheme.shapes.small,
        )
        .let {
            if (enabled) {
                it.clickable { onClick() }
            } else {
                it
            }
        }
        .padding(4.dp)

    Column(
        modifier = baseModifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasEntry) {
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (hasHistory) {
                Text(
                    text = "●",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun buildMonthCells(firstDayOfMonth: LocalDate): List<LocalDate?> {
    val first = firstDayOfMonth.withDayOfMonth(1)
    val daysInMonth = first.lengthOfMonth()
    val firstDayIndex = first.dayOfWeek.value % 7 // Sunday=7 -> 0, Monday=1 -> 1 ...

    val cells = mutableListOf<LocalDate?>()
    repeat(firstDayIndex) { cells.add(null) }

    for (day in 1..daysInMonth) {
        cells.add(first.withDayOfMonth(day))
    }

    while (cells.size % 7 != 0) {
        cells.add(null)
    }

    return cells
}
