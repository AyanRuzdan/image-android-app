package com.example.myapplication

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import androidx.palette.graphics.Palette

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
            items(taggedImages) { tagged ->
                Column {
                    Image(
                        painter = rememberAsyncImagePainter(tagged.uri),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tagged.tags.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// Extract tags using ML Kit and Palette API
fun generateTagsFromImage(
    context: Context,
    imageUri: Uri,
    onTagsReady: (List<String>) -> Unit
) {
    val image = InputImage.fromFilePath(context, imageUri)
    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    labeler.process(image)
        .addOnSuccessListener { labels ->
            val tagList = mutableListOf<String>()

            labels.forEach { label ->
                if (label.confidence >= 0.6) {
                    tagList.add(label.text.lowercase())
                }
            }

            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            Palette.from(bitmap).generate { palette ->
                val color = palette?.getDominantColor(0xCCCCCC)
                color?.let {
                    val hexColor = String.format("#%06X", 0xFFFFFF and it)
                    val colorName = getColorNameFromHex(hexColor)
                    tagList.add(colorName)
                }
                onTagsReady(tagList)
            }
        }
        .addOnFailureListener {
            onTagsReady(listOf("Unknown"))
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
        else -> "colorful"
    }
}
