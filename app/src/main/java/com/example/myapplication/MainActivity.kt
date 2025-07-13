package com.example.myapplication

import android.content.Context
import androidx.compose.foundation.combinedClickable
import android.net.Uri
import java.time.*
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import android.os.Bundle
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.activity.ComponentActivity
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter



data class TaggedImage(
    val uri: Uri,
    val tags: List<String>,
    val selectedTags: MutableList<String> = mutableStateListOf()
)

sealed class SideNavItem(val label: String, val icon: ImageVector) {
    object Home : SideNavItem("Home", Icons.Default.Home)
    object Search : SideNavItem("Search", Icons.Default.Search)
    object Cart : SideNavItem("Cart", Icons.Default.ShoppingCart)
    object Profile : SideNavItem("Profile", Icons.Default.Person)
    object OOTD : SideNavItem("Today's Outfit", Icons.Default.DateRange)
}


class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme { // ✅ Now resolved
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    ImageGridScreen()
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGridScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf<SideNavItem>(SideNavItem.Home) }

    val drawerItems = listOf(
        SideNavItem.Home,
        SideNavItem.Search,
        SideNavItem.Cart,
        SideNavItem.Profile,
        SideNavItem.OOTD
    )

    ModalNavigationDrawer(
        drawerState = drawerState, drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        selected = selectedTab::class == item::class,
                        onClick = {
                            selectedTab = item
                            scope.launch { drawerState.close() }
                        },
                        icon = {
                            Icon(item.icon, contentDescription = item.label)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("Smart Image Tagger") }, navigationIcon = {
                    IconButton(onClick = {
                        scope.launch { drawerState.open() }
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                })
            }) { innerPadding ->
            when (selectedTab) {
                is SideNavItem.Home -> MainImageGridContent(modifier = Modifier.padding(innerPadding))
                is SideNavItem.OOTD -> TodayOutfitScreen(modifier = Modifier.padding(innerPadding))
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Coming soon...")
                }
            }

        }
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
    fun toggleSelection(tagged: TaggedImage) {
        if (tagged in selectedForDeletion) {
            selectedForDeletion.remove(tagged)
        } else {
            selectedForDeletion.add(tagged)
        }
    }

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
                onClick = { launcher.launch("image/*") }, modifier = Modifier.weight(1f)
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
                text = { Text("This image is already uploaded.") })
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
                    Box(modifier = Modifier.combinedClickable(onClick = {
                        if (isSelectionMode) {
                            toggleSelection(tagged)
                        } else {
                            selectedImageForDialog = tagged
                        }
                    }, onLongClick = {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            selectedForDeletion.add(tagged)
                        }
                    })) {
                        Image(
                            painter = rememberAsyncImagePainter(tagged.uri),
                            contentDescription = "Image with tags",
                            modifier = Modifier.size(150.dp),
                            contentScale = ContentScale.Crop
                        )

                        if (isSelectionMode) {
                            Checkbox(
                                checked = tagged in selectedForDeletion, onCheckedChange = {
                                    if (it) selectedForDeletion.add(tagged)
                                    else selectedForDeletion.remove(tagged)
                                }, modifier = Modifier
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
                    }, modifier = Modifier.weight(1f)
                ) {
                    Text("Delete")
                }

                OutlinedButton(
                    onClick = {
                        selectedForDeletion.clear()
                        isSelectionMode = false
                    }, modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
            }
        }

        // Tag Dialog
        selectedImageForDialog?.let {
            FullScreenImageDialog(
                taggedImage = it, onDismissRequest = { selectedImageForDialog = null })
        }
    }
}

@Composable
fun FullScreenImageDialog(
    taggedImage: TaggedImage, onDismissRequest: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismissRequest, confirmButton = {
        TextButton(onClick = onDismissRequest) {
            Text("Done")
        }
    }, title = { Text("Select Tags") }, text = {
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
                            label = { Text(tag) })
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
                            label = { Text(tag) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    })
}

//@RequiresApi(Build.VERSION_CODES.O)
//@Composable
//fun TodayOutfitScreen() {
//    var currentTime by remember { mutableStateOf(LocalTime.now().toString()) }
//
//    LaunchedEffect(Unit) {
//        while (true) {
//            currentTime = LocalTime.now().toString().substringBeforeLast(".") // Remove milliseconds
//            kotlinx.coroutines.delay(1000) // Update every second
//        }
//    }
//    Box(
//        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
//    ) {
//        Text(
//            text = "Current Time: $currentTime",
//            style = MaterialTheme.typography.headlineMedium,
//            textAlign = TextAlign.Center
//        )
//    }
//}
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TodayOutfitScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val events = listOf("Office", "Gym", "Date", "Outing", "Dinner")
    var selectedEvent by remember { mutableStateOf(events.first()) }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val formattedDate = selectedDate.format(DateTimeFormatter.ISO_DATE)

    var upperWearUri by remember { mutableStateOf<Uri?>(null) }
    var lowerWearUri by remember { mutableStateOf<Uri?>(null) }

    val upperWearPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) upperWearUri = uri }

    val lowerWearPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) lowerWearUri = uri }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Log Today's Outfit", style = MaterialTheme.typography.headlineSmall)

        // Event Dropdown
        ExposedDropdownMenuBox(expanded = false, onExpandedChange = {}) {
            var expanded by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = selectedEvent,
                onValueChange = {},
                readOnly = true,
                label = { Text("Event") },
                trailingIcon = {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        Modifier.clickable { expanded = true })
                },
                modifier = Modifier.fillMaxWidth()
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                events.forEach { event ->
                    DropdownMenuItem(text = { Text(event) }, onClick = {
                        selectedEvent = event
                        expanded = false
                    })
                }
            }
        }

        // Calendar Date Picker
        Button(onClick = { showDatePicker = true }) {
            Text("Select Date: $formattedDate")
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        // Upper Wear Picker
        Button(onClick = { upperWearPicker.launch("image/*") }) {
            Text("Select Upper Wear")
        }
        upperWearUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Upper Wear",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Lower Wear Picker
        Button(onClick = { lowerWearPicker.launch("image/*") }) {
            Text("Select Lower Wear")
        }
        lowerWearUri?.let {
            Image(
                painter = rememberAsyncImagePainter(it),
                contentDescription = "Lower Wear",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
        }

        // Submit
        Button(
            onClick = {
                // Save to DB or ViewModel
            },
            enabled = upperWearUri != null && lowerWearUri != null
        ) {
            Text("Save Outfit")
        }

        // Display Summary
        if (upperWearUri != null && lowerWearUri != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Summary:\nDate: $formattedDate\nUpper: ${upperWearUri.toString()}\nLower: ${lowerWearUri.toString()}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}


fun generateTagsFromImage(
    context: Context, imageUri: Uri, onTagsReady: (List<String>) -> Unit
) {
    try {
        val image = InputImage.fromFilePath(context, imageUri)
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build()
        )

        labeler.process(image).addOnSuccessListener { labels ->
            val tagList = labels.take(15).map { it.text.lowercase() }.toMutableList()
            onTagsReady(tagList.distinct())
        }.addOnFailureListener {
            Log.e("MLKitError", "Error generating image labels: ${it.message}")
            onTagsReady(emptyList())
        }
    } catch (e: Exception) {
        Log.e("InputImageError", "Error creating InputImage: ${e.message}")
        onTagsReady(emptyList())
    }
}
