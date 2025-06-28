package com.example.myapplication

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import androidx.palette.graphics.Palette
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.*

data class TaggedImage(
    val uri: Uri,
    val tags: List<String>
)

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
                    taggedImages.add(TaggedImage(uri = selectedUri, tags = tags))
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(15.dp)
    ) {
        Button(
            onClick = { launcher.launch("image/*") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
        ) {
            Text("Upload Image")
        }

        if (showDuplicateDialog) {
            AlertDialog(
                onDismissRequest = { showDuplicateDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDuplicateDialog = false }) { Text("OK") }
                },
                title = { Text("Duplicate Image") },
                text = { Text("This image is already uploaded.") }
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(taggedImages, key = { it.uri.toString() }) { tagged ->
                Column(
                    modifier = Modifier.clickable {
                        selectedImageForDialog = tagged
                    }
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(tagged.uri),
                        contentDescription = "Image with tags: ${tagged.tags.joinToString()}",
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        selectedImageForDialog?.let { taggedImage ->
            FullScreenImageDialog(
                taggedImage = taggedImage,
                onDismissRequest = {
                    selectedImageForDialog = null
                }
            )
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
                Text("Close")
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = rememberAsyncImagePainter(model = taggedImage.uri),
                    contentDescription = "Full screen image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = taggedImage.tags.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
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
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.5f) // Lowered threshold to get more labels
                .build()
        )

        labeler.process(image)
            .addOnSuccessListener { labels ->
                val tagList = mutableListOf<String>()

                // Add ML Kit labels (up to 15 to avoid overwhelming)
                labels.take(15).forEach { label ->
                    tagList.add(label.text.lowercase())
                }

                try {
                    // Load bitmap for Palette and metadata
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            // Optionally, set other decoding options here
                        }
                    }


                    // Extract multiple colors from Palette
                    Palette.from(bitmap).generate { palette ->
                        palette?.let {
                            // Add dominant, vibrant, and muted colors
                            val colorTags = mutableListOf<String>()
                            listOf(
                                it.getDominantColor(0xCCCCCC),
                                it.getVibrantColor(0xCCCCCC),
                                it.getMutedColor(0xCCCCCC),
                                it.getLightVibrantColor(0xCCCCCC),
                                it.getDarkVibrantColor(0xCCCCCC),
                                it.getLightMutedColor(0xCCCCCC),
                                it.getDarkMutedColor(0xCCCCCC)
                            ).forEach { color ->
                                color.let { c ->
                                    val hexColor = String.format("#%06X", 0xFFFFFF and c)
                                    val colorName = getColorNameFromHex(hexColor)
                                    if (colorName !in colorTags) {
                                        colorTags.add(colorName.lowercase())
                                    }
                                }
                            }
                            tagList.addAll(colorTags.take(5)) // Limit to 5 unique color tags
                        }

                        // Add image metadata tags
                        val metadataTags = mutableListOf<String>()
                        try {
                            val exif =
                                ExifInterface(context.contentResolver.openInputStream(imageUri)!!)
                            exif.getAttribute(ExifInterface.TAG_DATETIME)?.let {
                                val dateFormat = SimpleDateFormat("yyyy:MM:dd", Locale.US)
                                val date = dateFormat.parse(it)?.let { date ->
                                    SimpleDateFormat("yyyy", Locale.US).format(date)
                                } ?: "unknown_date"
                                metadataTags.add(date)
                            }
                            exif.getAttribute(ExifInterface.TAG_MAKE)?.let {
                                metadataTags.add(it.lowercase().replace(" ", "_"))
                            }
                            exif.getAttribute(ExifInterface.TAG_MODEL)?.let {
                                metadataTags.add(it.lowercase().replace(" ", "_"))
                            }
                        } catch (e: Exception) {
                            Log.e("ExifError", "Error reading EXIF: ${e.message}")
                        }

                        // Add resolution tag
                        bitmap?.let {
                            val resolution = when {
                                it.width * it.height > 1920 * 1080 -> "high_resolution"
                                it.width * it.height > 1280 * 720 -> "medium_resolution"
                                else -> "low_resolution"
                            }
                            metadataTags.add(resolution)
                        }

                        // Add file type tag
                        val mimeType =
                            context.contentResolver.getType(imageUri)?.lowercase() ?: "unknown"
                        metadataTags.add(
                            when {
                                mimeType.contains("jpeg") -> "jpeg"
                                mimeType.contains("png") -> "png"
                                else -> "image"
                            }
                        )

                        tagList.addAll(metadataTags.take(5)) // Limit to 5 metadata tags

                        // Ensure at least 20 tags with fallback tags
                        val fallbackTags = listOf(
                            "photo", "image", "visual", "graphic", "media", "digital", "snapshot",
                            "picture", "capture", "scene", "view", "moment", "frame", "shot",
                            "exposure", "composition", "art", "detail", "texture", "pattern"
                        )
                        while (tagList.size < 20) {
                            val remaining = 20 - tagList.size
                            tagList.addAll(fallbackTags.take(remaining))
                        }
                        Log.d(
                            "ImageTags",
                            "$imageUri: ${tagList.distinct().take(20).joinToString(",")}"
                        )
                        onTagsReady(tagList.distinct().take(20)) // Ensure unique tags, max 20
                    }
                } catch (e: Exception) {
                    Log.e("PaletteError", "Error generating color tags: ${e.message}")
                    // Fallback if Palette fails
                    tagList.add("colorful")
                    onTagsReady(tagList.distinct().take(20))
                }
            }
            .addOnFailureListener { e ->
                Log.e("MLKitError", "Error generating image labels: ${e.message}")
                // Fallback tags if ML Kit fails
                val fallbackTags = listOf(
                    "unknown",
                    "image",
                    "photo",
                    "visual",
                    "graphic",
                    "media",
                    "digital",
                    "snapshot",
                    "picture",
                    "capture",
                    "scene",
                    "view",
                    "moment",
                    "frame",
                    "shot",
                    "exposure",
                    "composition",
                    "art",
                    "detail",
                    "texture"
                )
                onTagsReady(fallbackTags.take(20))
            }
    } catch (e: Exception) {
        Log.e("InputImageError", "Error creating InputImage: ${e.message}")
        onTagsReady(
            listOf(
                "unknown", "image", "photo", "visual", "graphic", "media", "digital",
                "snapshot", "picture", "capture", "scene", "view", "moment", "frame", "shot",
                "exposure", "composition", "art", "detail", "texture"
            )
        )
    }
}

fun getColorNameFromHex(hex: String): String {
    return when (hex.uppercase()) {
        "#FFFFFF" -> "white"
        "#000000" -> "black"
        "#FF0000" -> "red"
        "#00FF00" -> "green"
        "#0000FF" -> "blue"
        "#FFFF00" -> "yellow"
        "#FFA500" -> "orange"
        "#800080" -> "purple"
        "#FFC0CB" -> "pink"
        "#A52A2A" -> "brown"
        "#808080" -> "gray"
        else -> "colorful"
    }
}