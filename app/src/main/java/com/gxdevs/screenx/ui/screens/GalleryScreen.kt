package com.gxdevs.screenx.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.gxdevs.screenx.R
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.List as LucideListIcon
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Trash2
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gxdevs.screenx.utils.RecordedVideo
import com.gxdevs.screenx.utils.VideoHelper
import com.gxdevs.screenx.ui.components.VideoPreviewBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    videos: List<RecordedVideo>,
    onBackClick: () -> Unit,
    onDeleteVideo: (RecordedVideo) -> Unit,
    onTrimVideoClick: (RecordedVideo) -> Unit,
    settingsManager: com.gxdevs.screenx.data.SettingsManager,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isGridView by settingsManager.galleryGridViewFlow.collectAsState(initial = true)
    var isRefreshing by remember { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 500),
        finishedListener = { isRefreshing = false },
        label = "refreshSpin"
    )
    
    // Filter categories: 0 = All, 1 = Videos, 2 = Screenshots
    var selectedCategory by remember { mutableIntStateOf(0) }
    
    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }
    var selectedScreenshotForViewer by remember { mutableStateOf<RecordedVideo?>(null) }
    var mediaToDelete by remember { mutableStateOf<RecordedVideo?>(null) }
    var mediaToRename by remember { mutableStateOf<RecordedVideo?>(null) }

    val filteredList = remember(videos, selectedCategory) {
        when (selectedCategory) {
            1 -> videos.filter { it.isVideo }
            2 -> videos.filter { !it.isVideo }
            else -> videos
        }
    }

    val videoCount = remember(videos) { videos.count { it.isVideo } }
    val screenshotCount = remember(videos) { videos.count { !it.isVideo } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.library_title),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isRefreshing = true
                        onRefresh()
                        android.widget.Toast.makeText(context, context.getString(R.string.home_toast_refreshed_fmt, videos.size), android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Lucide.RotateCcw,
                            contentDescription = stringResource(R.string.refresh),
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            settingsManager.setGalleryGridView(!isGridView)
                        }
                    }) {
                        Icon(
                            imageVector = if (isGridView) Lucide.LucideListIcon else Lucide.LayoutGrid,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
        ) {
            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == 0,
                    onClick = { selectedCategory = 0 },
                    label = { Text("${stringResource(R.string.library_all)} (${videos.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = selectedCategory == 1,
                    onClick = { selectedCategory = 1 },
                    label = { Text("${stringResource(R.string.library_videos)} ($videoCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(Lucide.Film, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                FilterChip(
                    selected = selectedCategory == 2,
                    onClick = { selectedCategory = 2 },
                    label = { Text("${stringResource(R.string.library_screenshots)} ($screenshotCount)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    leadingIcon = {
                        Icon(Lucide.Camera, contentDescription = null, modifier = Modifier.size(14.dp))
                    },
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (filteredList.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (selectedCategory == 2) Lucide.Camera else Lucide.Film,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.library_no_items),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredList, key = { it.id }) { item ->
                                GalleryGridItem(
                                    item = item,
                                    onClick = {
                                        if (item.isVideo) {
                                            selectedVideoForPlayback = item
                                        } else {
                                            selectedScreenshotForViewer = item
                                        }
                                    },
                                    onDelete = { mediaToDelete = item },
                                    onRename = { mediaToRename = item },
                                    onTrim = { onTrimVideoClick(item) }
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 840.dp)
                            ) {
                                items(filteredList, key = { it.id }) { item ->
                                    GalleryListItem(
                                        item = item,
                                        onClick = {
                                            if (item.isVideo) {
                                                selectedVideoForPlayback = item
                                            } else {
                                                selectedScreenshotForViewer = item
                                            }
                                        },
                                        onDelete = { mediaToDelete = item },
                                        onRename = { mediaToRename = item },
                                        onTrim = { onTrimVideoClick(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Video Player Bottom Sheet
    selectedVideoForPlayback?.let { video ->
        VideoPreviewBottomSheet(
            video = video,
            onDismiss = { selectedVideoForPlayback = null },
            onShare = { VideoHelper.shareVideo(context, video) },
            onTrim = {
                selectedVideoForPlayback = null
                onTrimVideoClick(video)
            },
            onRename = { _ ->
                onRefresh()
            },
            onDelete = {
                selectedVideoForPlayback = null
                mediaToDelete = video
            }
        )
    }

    // Screenshot Viewer Dialog
    selectedScreenshotForViewer?.let { shot ->
        ScreenshotViewerDialog(
            imageUri = shot.uri,
            imageName = shot.name,
            onDismiss = { selectedScreenshotForViewer = null },
            onShare = { VideoHelper.shareVideo(context, shot) },
            onDelete = {
                mediaToDelete = shot
                selectedScreenshotForViewer = null
            }
        )
    }

    // Delete Confirmation Dialog
    mediaToDelete?.let { item ->
        DeleteConfirmDialog(
            media = item,
            onDismiss = { mediaToDelete = null },
            onConfirm = {
                val target = item
                mediaToDelete = null
                onDeleteVideo(target)
            }
        )
    }

    // Rename Media Dialog
    mediaToRename?.let { item ->
        RenameMediaDialog(
            initialName = item.name,
            isVideo = item.isVideo,
            onDismiss = { mediaToRename = null },
            onConfirm = { newName ->
                val success = VideoHelper.renameMedia(context, item, newName)
                mediaToRename = null
                if (success) {
                    Toast.makeText(context, context.getString(R.string.library_rename_success), Toast.LENGTH_SHORT).show()
                    val refreshIntent = Intent("com.gxdevs.screenx.action.RECORDING_SAVED").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(refreshIntent)
                } else {
                    Toast.makeText(context, context.getString(R.string.library_rename_failed), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun GalleryGridItem(
    item: RecordedVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onTrim: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var expandedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(item.uri) {
        thumbnail = VideoHelper.loadThumbnail(context, item.uri, item.isVideo)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Lucide.CirclePlay else Lucide.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Duration or Screenshot badge overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                if (item.isVideo) {
                    Text(
                        text = VideoHelper.formatDuration(item.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Camera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Screenshot",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title & Menu Overlay at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color(0x66000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cleanTitle = VideoHelper.getHumanizedTitle(item)

                Text(
                    text = cleanTitle,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { expandedMenu = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.EllipsisVertical,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        if (item.isVideo) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trimmer_play)) },
                                onClick = {
                                    expandedMenu = false
                                    onClick()
                                },
                                leadingIcon = { Icon(Lucide.Play, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.trimmer_mode_trim)) },
                                onClick = {
                                    expandedMenu = false
                                    onTrim()
                                },
                                leadingIcon = { Icon(Lucide.Scissors, contentDescription = null) }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_view_all)) },
                                onClick = {
                                    expandedMenu = false
                                    onClick()
                                },
                                leadingIcon = { Icon(Lucide.Image, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = {
                                expandedMenu = false
                                onRename()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            onClick = {
                                expandedMenu = false
                                VideoHelper.shareVideo(context, item)
                            },
                            leadingIcon = { Icon(Lucide.Share2, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                expandedMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryListItem(
    item: RecordedVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onTrim: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    var expandedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(item.uri) {
        thumbnail = VideoHelper.loadThumbnail(context, item.uri, item.isVideo)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (item.isVideo) Lucide.CirclePlay else Lucide.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xCC000000), RoundedCornerShape(topStart = 6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (item.isVideo) {
                        Text(
                            text = VideoHelper.formatDuration(item.duration),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "IMG",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = VideoHelper.getHumanizedTitle(item),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = VideoHelper.getHumanizedSubtitle(item),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { expandedMenu = true }) {
                    Icon(
                        imageVector = Lucide.EllipsisVertical,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false }
                ) {
                    if (item.isVideo) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.trimmer_play)) },
                            onClick = {
                                expandedMenu = false
                                onClick()
                            },
                            leadingIcon = { Icon(Lucide.Play, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.trimmer_mode_trim)) },
                            onClick = {
                                expandedMenu = false
                                onTrim()
                            },
                            leadingIcon = { Icon(Lucide.Scissors, contentDescription = null) }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_view_all)) },
                            onClick = {
                                expandedMenu = false
                                onClick()
                            },
                            leadingIcon = { Icon(Lucide.Image, contentDescription = null) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename)) },
                        onClick = {
                            expandedMenu = false
                            onRename()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = {
                            expandedMenu = false
                            VideoHelper.shareVideo(context, item)
                        },
                        leadingIcon = { Icon(Lucide.Share2, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

@Composable
fun ScreenshotViewerDialog(
    imageUri: Uri,
    imageName: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(imageUri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(context.contentResolver, imageUri)
                    android.graphics.ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = imageName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Top bar overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color(0x88000000))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Lucide.ArrowLeft, contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Text(
                        text = imageName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                    IconButton(onClick = onShare) {
                        Icon(Lucide.Share2, contentDescription = stringResource(R.string.share), tint = Color.White)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Lucide.Trash2, contentDescription = stringResource(R.string.delete), tint = Color(0xFFEF5350))
                    }
                }
            }
        }
    }
}

@Composable
fun RenameMediaDialog(
    initialName: String,
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val extension = if (isVideo) ".mp4" else ".png"
    val baseName = remember(initialName) {
        if (initialName.endsWith(extension, ignoreCase = true)) {
            initialName.dropLast(extension.length)
        } else {
            initialName
        }
    }
    var text by remember { mutableStateOf(baseName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 440.dp),
        title = {
            Text(
                text = stringResource(R.string.library_rename_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.library_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Format: $extension",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                    }
                },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    media: RecordedVideo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 440.dp),
        title = {
            Text(
                text = stringResource(R.string.library_delete_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.library_delete_dialog_msg),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
