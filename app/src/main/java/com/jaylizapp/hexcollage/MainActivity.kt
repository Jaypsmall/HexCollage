package com.jaylizapp.hexcollage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jaylizapp.hexcollage.ui.theme.HexCollageTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.Random

// --- Premium Gold Colors ---
val GoldDark = Color(0xFF8C6221)
val GoldMedium = Color(0xFFC29B47)
val GoldLight = Color(0xFFFFF3A8)

val PremiumGoldGradient = Brush.linearGradient(
    colors = listOf(GoldDark, GoldLight, GoldMedium, GoldDark),
    start = androidx.compose.ui.geometry.Offset(0f, 0f),
    end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
)

// --- Model & Saver ---
data class CollageConfig(
    val tileSize: Int = 120,
    val mediumImages: Int = 20,
    val bigImages: Int = 5,
    val sizeMin: Int = 150,
    val sizeMax: Int = 400,
    val blurBase: Float = 8f,
    val brightnessBase: Float = 0.4f,
    val contrastFinal: Float = 1.2f,
    val glowChance: Float = 0.3f,
    val numOverlay: Int = 30,
    val rotateRandom: Boolean = true,
    val modeFull: Boolean = false
)

val CollageConfigSaver = Saver<CollageConfig, Map<String, Any>>(
    save = { config ->
        mapOf(
            "tileSize" to config.tileSize,
            "mediumImages" to config.mediumImages,
            "bigImages" to config.bigImages,
            "sizeMin" to config.sizeMin,
            "sizeMax" to config.sizeMax,
            "blurBase" to config.blurBase,
            "brightnessBase" to config.brightnessBase,
            "contrastFinal" to config.contrastFinal,
            "glowChance" to config.glowChance,
            "numOverlay" to config.numOverlay,
            "rotateRandom" to config.rotateRandom,
            "modeFull" to config.modeFull
        )
    },
    restore = { map ->
        CollageConfig(
            tileSize = map["tileSize"] as Int,
            mediumImages = map["mediumImages"] as Int,
            bigImages = map["bigImages"] as Int,
            sizeMin = map["sizeMin"] as Int,
            sizeMax = map["sizeMax"] as Int,
            blurBase = map["blurBase"] as Float,
            brightnessBase = map["brightnessBase"] as Float,
            contrastFinal = map["contrastFinal"] as Float,
            glowChance = map["glowChance"] as Float,
            numOverlay = map["numOverlay"] as Int,
            rotateRandom = map["rotateRandom"] as Boolean,
            modeFull = map["modeFull"] as Boolean
        )
    }
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure status bar icons are light on dark background
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        
        setContent {
            HexCollageTheme(darkTheme = true) {
                CollageApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    // Using rememberSaveable to handle rotation without losing state
    var imageUris by rememberSaveable { mutableStateOf<List<Uri>>(emptyList()) }
    var config by rememberSaveable(stateSaver = CollageConfigSaver) { mutableStateOf(CollageConfig()) }
    var isExporting by remember { mutableStateOf(false) }
    
    val graphicsLayer = rememberGraphicsLayer()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris -> if (uris.isNotEmpty()) imageUris = uris }
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = Color(0xFF0F0F0F),
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ) {
                DrawerHeader()
                HorizontalDivider(color = GoldDark.copy(alpha = 0.3f), thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))
                
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .weight(1f) // Push credits to bottom
                        .verticalScroll(rememberScrollState())
                ) {
                    DrawerSectionTitle("MODOS DE ARTE")
                    DrawerItem("CLEAN", Icons.Default.AutoAwesome) { 
                        config = config.copy(mediumImages = 10, bigImages = 2, rotateRandom = false, modeFull = false) 
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem("RAP STYLE", Icons.Default.MusicNote) { 
                        config = config.copy(mediumImages = 25, bigImages = 5, rotateRandom = true, modeFull = false) 
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem("TOTAL CHAOS", Icons.Default.Whatshot) { 
                        config = config.copy(mediumImages = 45, bigImages = 12, rotateRandom = true, modeFull = true) 
                        scope.launch { drawerState.close() }
                    }
                    DrawerItem("VIDEOCLIP", Icons.Default.Movie) { 
                        config = config.copy(mediumImages = 35, bigImages = 8, rotateRandom = true, modeFull = false) 
                        scope.launch { drawerState.close() }
                    }

                    Spacer(Modifier.height(20.dp))
                    DrawerSectionTitle("MOTOR AJUSTES")
                    SettingSlider("Capas", config.mediumImages.toFloat(), 5f, 80f) { config = config.copy(mediumImages = it.toInt()) }
                    SettingSlider("Héroes", config.bigImages.toFloat(), 1f, 20f) { config = config.copy(bigImages = it.toInt()) }
                    
                    Spacer(Modifier.height(12.dp))
                    DrawerSwitch("Rotación Dinámica", config.rotateRandom) { config = config.copy(rotateRandom = it) }
                    DrawerSwitch("Fondo Caótico", config.modeFull) { config = config.copy(modeFull = it) }
                }

                // Credits Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "HEX COLLAGE PRO v1.0",
                        style = TextStyle(brush = PremiumGoldGradient, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Created by JAYLIZ with ❤️",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color(0xFF050505),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "HEX COLLAGE",
                            style = TextStyle(
                                brush = PremiumGoldGradient,
                                fontWeight = FontWeight.Black,
                                fontSize = 26.sp,
                                letterSpacing = 6.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú", tint = GoldMedium, modifier = Modifier.size(30.dp))
                        }
                    },
                    actions = {
                        IconButton(onClick = { 
                            val rand = Random()
                            config = config.copy(
                                mediumImages = rand.nextInt(60) + 10,
                                rotateRandom = rand.nextBoolean(),
                                modeFull = rand.nextBoolean()
                            )
                        }) {
                            Icon(Icons.Default.AutoFixHigh, contentDescription = "Magic", tint = GoldMedium)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.98f),
                    tonalElevation = 16.dp,
                    border = BorderStroke(1.dp, GoldDark.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(20.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        PremiumButton(
                            text = "📂 FOTOS",
                            onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting
                        )
                        
                        PremiumButton(
                            text = if (isExporting) "" else "🎬 EXPORTAR",
                            onClick = {
                                if (imageUris.isNotEmpty()) {
                                    isExporting = true
                                    scope.launch {
                                        try {
                                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                                            val success = saveBitmapToGallery(context, bitmap)
                                            if (success) snackbarHostState.showSnackbar("¡Obra Maestra Guardada! ✨")
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Error: ${e.message}")
                                        } finally {
                                            isExporting = false
                                        }
                                    }
                                } else {
                                    scope.launch { snackbarHostState.showSnackbar("Añade fotos primero") }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isExporting && imageUris.isNotEmpty(),
                            isAction = true
                        ) {
                            if (isExporting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 3.dp)
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                if (imageUris.isEmpty()) {
                    EmptyPremiumState(modifier = Modifier.align(Alignment.Center))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                graphicsLayer.record { this@drawWithContent.drawContent() }
                                drawLayer(graphicsLayer)
                            }
                    ) {
                        CollageCanvas(imageUris, config)
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(PremiumGoldGradient, RoundedCornerShape(25.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black, RoundedCornerShape(23.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("HX", color = GoldLight, fontWeight = FontWeight.Black, fontSize = 34.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "HEX COLLAGE PRO",
            style = TextStyle(brush = PremiumGoldGradient, fontWeight = FontWeight.Black, fontSize = 22.sp, letterSpacing = 2.sp)
        )
        Text("CREATIVE ART STUDIO", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
    }
}

@Composable
fun DrawerSectionTitle(title: String) {
    Text(
        title,
        color = GoldMedium,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 3.sp,
        modifier = Modifier.padding(bottom = 12.dp, top = 9.dp)
    )
}

@Composable
fun DrawerItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = GoldMedium, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun DrawerSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.LightGray, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldLight,
                checkedTrackColor = GoldDark,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isAction: Boolean = false,
    content: @Composable () -> Unit = {}
) {
    val bgColor = if (isAction) PremiumGoldGradient else Brush.linearGradient(listOf(Color(0xFF222222), Color(0xFF111111)))
    val textColor = if (isAction) Color.Black else Color.White
    
    Box(
        modifier = modifier
            .height(60.dp)
            .shadow(if (enabled) 12.dp else 0.dp, RoundedCornerShape(15.dp))
            .background(bgColor, RoundedCornerShape(15.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .border(
                width = 1.dp,
                brush = if (isAction) Brush.linearGradient(listOf(GoldLight, GoldDark)) else Brush.linearGradient(listOf(Color.DarkGray, Color.Transparent)),
                shape = RoundedCornerShape(15.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = textColor, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
fun EmptyPremiumState(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(120.dp).blur(10.dp), tint = GoldDark)
            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(80.dp), tint = GoldLight)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "ESTUDIO HEX",
            style = TextStyle(brush = PremiumGoldGradient, fontWeight = FontWeight.Black, fontSize = 28.sp, letterSpacing = 10.sp)
        )
        Text("IMPORTA FOTOS PARA EMPEZAR", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    }
}

@Composable
fun SettingSlider(label: String, value: Float, rangeMin: Float, rangeMax: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(value.toInt().toString(), color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = rangeMin..rangeMax,
            colors = SliderDefaults.colors(
                thumbColor = GoldMedium,
                activeTrackColor = GoldMedium,
                inactiveTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
fun CollageCanvas(uris: List<Uri>, config: CollageConfig) {
    val random = remember { Random() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val width = maxWidth
        val height = maxHeight
        
        if (!config.modeFull) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF020202)))
            val totalImages = config.mediumImages + config.bigImages
            repeat(totalImages.coerceAtMost(60)) { index ->
                val uri = uris[index % uris.size]
                val isHero = index >= config.mediumImages
                
                val randomOffset = remember(uri, index, config) {
                    val spread = if (isHero) 6.0 else 4.5
                    val x = (random.nextGaussian() * (width.value / spread)).toFloat()
                    val y = (random.nextGaussian() * (height.value / spread)).toFloat()
                    Pair(x, y)
                }
                
                val rotation = if (config.rotateRandom) remember(uri, index) { (random.nextInt(46) - 23).toFloat() } else 0f
                val size = remember(uri, index, isHero, config) {
                    if (isHero) (random.nextInt(config.sizeMax - (config.sizeMax/2)) + (config.sizeMax/2)).dp
                    else (random.nextInt(config.sizeMax - config.sizeMin) + config.sizeMin).dp
                }

                if (isHero && random.nextFloat() < config.glowChance) {
                    Box(modifier = Modifier
                        .size(size + 20.dp)
                        .offset(x = randomOffset.first.dp, y = randomOffset.second.dp)
                        .rotate(rotation)
                        .align(Alignment.Center)
                        .background(GoldMedium.copy(alpha = 0.2f), CircleShape)
                        .blur(30.dp)
                    )
                }

                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(size)
                        .offset(x = randomOffset.first.dp, y = randomOffset.second.dp)
                        .rotate(rotation)
                        .align(Alignment.Center)
                        .shadow(if (isHero) 40.dp else 15.dp, spotColor = if(isHero) GoldDark else Color.Black),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            uris.take(60).forEachIndexed { index, uri ->
                val rotation = remember(uri, index) { (random.nextInt(70) - 35).toFloat() }
                val size = remember(uri, index) { (random.nextInt(250) + 120).dp }
                val xPos = remember(uri, index) { (random.nextInt(width.value.toInt() + 300) - 150).dp }
                val yPos = remember(uri, index) { (random.nextInt(height.value.toInt() + 300) - 150).dp }
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.size(size).offset(x = xPos, y = yPos).rotate(rotation).shadow(20.dp, spotColor = GoldDark.copy(alpha = 0.5f)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    val filename = "HexCollage_${System.currentTimeMillis()}.jpg"
    var imageUri: Uri? = null
    val contentResolver = context.contentResolver
    try {
        val fos: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/HexCollage")
            }
            imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            imageUri?.let { contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString()
            val file = java.io.File(imagesDir, filename)
            val stream = java.io.FileOutputStream(file)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            stream
        }
        fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
