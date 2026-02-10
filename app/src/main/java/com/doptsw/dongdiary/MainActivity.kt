@file:Suppress("DEPRECATION")

package com.doptsw.dongdiary

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        val navController = rememberNavController()
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            DongDiaryNavHost(navController = navController)
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

    LaunchedEffect(Unit) {
        entries = repository.getAll().entries
    }

    val entriesById = remember(entries) {
        entries.groupBy { it.id }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row {
                TextButton(onClick = {
                    currentYearMonth = currentYearMonth.minusYears(1)
                }) {
                    Text(text = "이전 해")
                }
                TextButton(onClick = {
                    currentYearMonth = currentYearMonth.minusMonths(1)
                }) {
                    Text(text = "이전 달")
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${currentYearMonth.year}년 ${currentYearMonth.monthValue}월",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "선택한 날짜: ${selectedDate}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        currentYearMonth = today.withDayOfMonth(1)
                        selectedDate = today
                    },
                ) {
                    Text(text = "오늘로 이동")
                }
            }
            Row {
                val nextMonth = currentYearMonth.plusMonths(1)
                val canGoNextMonth = nextMonth.year < today.year ||
                    (nextMonth.year == today.year && nextMonth.monthValue <= today.monthValue)

                TextButton(
                    onClick = {
                        if (canGoNextMonth) {
                            currentYearMonth = nextMonth
                        }
                    },
                    enabled = canGoNextMonth,
                ) {
                    Text(text = "다음 달")
                }
                TextButton(onClick = onOpenSettings) {
                    Text(text = "설정")
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
                    val hasAnyEntry = hasTodayEntry || hasHistory
                    val isClickable = when {
                        isFuture -> false
                        isToday -> true
                        else -> hasAnyEntry
                    }
                    DayCell(
                        date = date,
                        isToday = isToday,
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
    val scope = rememberCoroutineScope()

    val isToday = baseDate == today
    val isPast = baseDate.isBefore(today)

    var todayEntry by remember { mutableStateOf(repository.getEntriesForDate(today).firstOrNull()) }
    var todayContent by remember { mutableStateOf(todayEntry?.content.orEmpty()) }
    var todayImages by remember { mutableStateOf(todayEntry?.images.orEmpty()) }
    var isEditingToday by remember { mutableStateOf(todayEntry == null && isToday) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(text = "뒤로")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${baseDate.year}년 ${baseDate.monthValue}월 ${baseDate.dayOfMonth}일",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "같은 날의 지난 일기들",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(modifier = Modifier.height(0.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 과거 날짜: 조회 전용
            if (isPast) {
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

                if (isToday) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                    ) {
                        Text(
                            text = "오늘의 일기",
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
                                    Text(text = "오늘의 일기 수정하기")
                                }
                                if (todayEntry != null) {
                                    TextButton(onClick = { showDeleteConfirm = true }) {
                                        Text(text = "오늘의 일기 삭제하기")
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
                                placeholder = { Text(text = "오늘 있었던 일을 자유롭게 적어보세요.") },
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
                                        repository.saveOrUpdateToday(todayContent, todayImages)
                                        todayEntry = repository.getEntriesForDate(today).firstOrNull()
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
                                    Text(text = "오늘의 일기 삭제하기")
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
                        repository.deleteToday()
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
            title = { Text(text = "오늘의 일기 삭제") },
            text = { Text(text = "정말로 오늘의 일기를 삭제할까요? 이 동작은 되돌릴 수 없어요.") },
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

    val activity = LocalContext.current as? Activity

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) {
                    Text(text = "뒤로")
                }
                Text(
                    text = "설정",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(modifier = Modifier.height(0.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(24.dp))

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
                    Text(text = "지금 백업하기")
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
                    Text(text = "클라우드에서 불러오기")
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

            Spacer(modifier = Modifier.height(24.dp))

            // 앱 버전 표시
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
                text = "버전 $versionName",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
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
    isSelected: Boolean,
    hasEntry: Boolean,
    hasHistory: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
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