package com.example.myapplication

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.activity.ComponentActivity
import androidx.compose.material3.BottomAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.graphics.ImageDecoder
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.*

data class TaggedImage(
    val uri: Uri,
    val tags: List<String>,
    val selectedTags: MutableList<String> = mutableStateListOf()
)

sealed class BottomNavItem(val label: String, val icon: ImageVector) {
    object Home : BottomNavItem("Home", Icons.Default.Home)
    object Search : BottomNavItem("Search", Icons.Default.Search)
    object Cart : BottomNavItem("Cart", Icons.Default.ShoppingCart)
    object Profile : BottomNavItem("Profile", Icons.Default.Person)
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ImageGridScreen()
                }
            }
        }
    }
}

@Composable
fun ImageGridScreen() {
    var selectedTab by remember { mutableStateOf<BottomNavItem>(BottomNavItem.Home) }

    Scaffold(
        bottomBar = {
            CurvedBottomBar(
                selectedItem = selectedTab,
                onItemSelected = { selectedTab = it },
                onCenterButtonClick = {
                    // Handle FAB click here
                }
            )
        },
        floatingActionButton = {},
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        MainImageGridContent(Modifier.padding(innerPadding))
    }
}

@Composable
fun MainImageGridContent(modifier: Modifier = Modifier) {
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedForDeletion = remember { mutableStateListOf<TaggedImage>() }
    val taggedImages = remember { mutableStateListOf<TaggedImage>() }
    val context = LocalContext.current
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var selectedImageForDialog by remember { mutableStateOf<TaggedImage?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val alreadyAdded = taggedImages.any { it.uri == selectedUri }
            if (alreadyAdded) {
                showDuplicateDialog = true
            } else {
                generateTagsFromImage(context, selectedUri) { tags ->
                    val newTagged = TaggedImage(uri = selectedUri, tags = tags)
                    taggedImages.add(newTagged)
                    selectedImageForDialog = newTagged
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(15.dp)
    ) {
        // Upload/Delete Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Upload Image")
            }

            if (taggedImages.isNotEmpty()) {
                Button(
                    onClick = { isSelectionMode = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            }
        }

        // Duplicate Alert
        if (showDuplicateDialog) {
            AlertDialog(
                onDismissRequest = { showDuplicateDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDuplicateDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Duplicate Image") },
                text = { Text("This image is already uploaded.") }
            )
        }

        // Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(taggedImages, key = { it.uri.toString() }) { tagged ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.clickable {
                        if (isSelectionMode) {
                            if (tagged in selectedForDeletion) {
                                selectedForDeletion.remove(tagged)
                            } else {
                                selectedForDeletion.add(tagged)
                            }
                        } else {
                            selectedImageForDialog = tagged
                        }
                    }) {
                        Image(
                            painter = rememberAsyncImagePainter(tagged.uri),
                            contentDescription = "Image with tags",
                            modifier = Modifier.size(150.dp),
                            contentScale = ContentScale.Crop
                        )

                        if (isSelectionMode) {
                            Checkbox(
                                checked = tagged in selectedForDeletion,
                                onCheckedChange = {
                                    if (it) selectedForDeletion.add(tagged)
                                    else selectedForDeletion.remove(tagged)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isSelectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        taggedImages.removeAll(selectedForDeletion)
                        selectedForDeletion.clear()
                        isSelectionMode = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("OK")
                }

                OutlinedButton(
                    onClick = {
                        selectedForDeletion.clear()
                        isSelectionMode = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
        }

        // Tag Dialog
        selectedImageForDialog?.let {
            FullScreenImageDialog(
                taggedImage = it,
                onDismissRequest = { selectedImageForDialog = null }
            )
        }
    }
}

@Composable
fun CurvedBottomBar(
    selectedItem: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit,
    onCenterButtonClick: () -> Unit
) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Cart,
        BottomNavItem.Profile
    )

    Box {
        BottomAppBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            items.chunked(2).forEachIndexed { chunkIndex, chunk ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    chunk.forEach { item ->
                        NavigationBarItem(
                            selected = selectedItem::class == item::class,
                            onClick = { onItemSelected(item) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }

                if (chunkIndex == 0) {
                    Spacer(modifier = Modifier.width(56.dp))
                }
            }
        }

        FloatingActionButton(
            onClick = onCenterButtonClick,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-28).dp)
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Upload")
        }
    }
}

@Composable
fun FullScreenImageDialog(
    taggedImage: TaggedImage,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done")
            }
        },
        title = { Text("Select Tags") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = taggedImage.uri),
                    contentDescription = "Full screen image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )

                val (selected, unselected) = taggedImage.tags.partition {
                    taggedImage.selectedTags.contains(it)
                }

                if (selected.isNotEmpty()) {
                    Text("Selected Tags")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selected.forEach { tag ->
                            FilterChip(
                                selected = true,
                                onClick = { taggedImage.selectedTags.remove(tag) },
                                label = { Text(tag) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (unselected.isNotEmpty()) {
                    Text("Available Tags")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        unselected.forEach { tag ->
                            FilterChip(
                                selected = false,
                                onClick = { taggedImage.selectedTags.add(tag) },
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    )
}

fun generateTagsFromImage(
    context: Context,
    imageUri: Uri,
    onTagsReady: (List<String>) -> Unit
) {
    try {
        val image = InputImage.fromFilePath(context, imageUri)
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
        )

        labeler.process(image)
            .addOnSuccessListener { labels ->
                val tagList = labels.take(15).map { it.text.lowercase() }.toMutableList()

                try {
                    val metadataTags = mutableListOf<String>()
                    val exif = ExifInterface(context.contentResolver.openInputStream(imageUri)!!)

                    exif.getAttribute(ExifInterface.TAG_DATETIME)?.let {
                        val dateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.US)
                        val year = dateFormat.parse(it)?.let { d ->
                            SimpleDateFormat("yyyy", Locale.US).format(d)
                        } ?: "unknown_date"
                        metadataTags.add(year)
                    }

                    exif.getAttribute(ExifInterface.TAG_MAKE)?.let {
                        metadataTags.add(it.lowercase().replace(" ", "_"))
                    }

                    exif.getAttribute(ExifInterface.TAG_MODEL)?.let {
                        metadataTags.add(it.lowercase().replace(" ", "_"))
                    }

                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                        ImageDecoder.decodeBitmap(source)
                    } else {
                        val inputStream = context.contentResolver.openInputStream(imageUri)
                        BitmapFactory.decodeStream(inputStream)
                    }

                    bitmap?.let {
                        val resolution = when {
                            it.width * it.height > 1920 * 1080 -> "high_resolution"
                            it.width * it.height > 1280 * 720 -> "medium_resolution"
                            else -> "low_resolution"
                        }
                        metadataTags.add(resolution)
                    }

                    val mimeType =
                        context.contentResolver.getType(imageUri)?.lowercase() ?: "unknown"
                    metadataTags.add(
                        when {
                            mimeType.contains("jpeg") -> "jpeg"
                            mimeType.contains("png") -> "png"
                            else -> "image"
                        }
                    )

                    tagList.addAll(metadataTags)
                } catch (e: Exception) {
                    Log.e("ExifError", "Error reading EXIF: ${e.message}")
                }

                onTagsReady(tagList.distinct())
            }
            .addOnFailureListener {
                Log.e("MLKitError", "Error generating image labels: ${it.message}")
                onTagsReady(emptyList())
            }
    } catch (e: Exception) {
        Log.e("InputImageError", "Error creating InputImage: ${e.message}")
        onTagsReady(emptyList())
    }
}
