package com.example.arcoretest

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.example.arcoretest.ui.theme.ARCoreTestTheme
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.isTracking
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.log

private const val kMaxModelInstances = 10
private const val VISIBLE_DISTANCE_THRESHOLD = 100.0 // meters

data class ARNode(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val model: String,
    val isActive: Boolean
)

class ARViewModel : ViewModel() {
    private val _arNodes = MutableStateFlow<List<ARNode>>(emptyList())
    val arNodes: StateFlow<List<ARNode>> = _arNodes.asStateFlow()

    fun setARNodes(nodes: List<ARNode>) {
        _arNodes.value = nodes
    }

    fun updateNodeStatus(id: String, isActive: Boolean) {
        _arNodes.update { nodes ->
            nodes.map { node ->
                if (node.id == id) node.copy(isActive = isActive) else node
            }
        }
    }
}

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val viewModel: ARViewModel by viewModels()

    private val nodes: MutableList<ARNode> = mutableListOf(
        ARNode(
            "1",
            36.10716645372349,
            128.41647777400757,
            73.54002152141184,
            "models/otter1.glb",
            true
        ),
        ARNode(
            "2",
            36.10719340772349,
            128.41647777400757,
            73.54002152141184,
            "models/quest.glb",
            true
        ),
        ARNode(
            "3",
            36.10714848472349,
            128.41645558800757,
            73.54002152141184,
            "models/chick.glb",
            true
        ),
        ARNode(
            "4",
            36.10718419443119,
            128.41647704496236,
            73.54002152141184,
            "models/turtle1.glb",
            true
        ),
        ARNode(
            "5",
            36.10718419443119,
            128.41647704496236,
            73.54002152141184,
            "models/turtle1.glb",
            false
        ),
        ARNode(
            "6",
        36.106748456430424,
        128.41639460336677,
        68.46302377991378,
        "models/turtle1.glb",
        true
        ),
        ARNode(
            "7",
            36.10688456844942,
            128.41625326737577,
            68.78246488422155,
            "models/otter1.glb",
            true
        ),
        ARNode(
            "8",
            36.10672958995879,
            128.41622445983785,
            67.63452187180519,
            "models/chick.glb",
            true
        ),
        ARNode(
            "9",
            36.1067327895906,
            128.4162147884974,
            68.18832830246538,
            "models/quest.glb",
            true
        ),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.setARNodes(nodes)

        setContent {
            ARCoreTestTheme {
                ARSceneComposable(viewModel = viewModel)
            }
        }
    }

    @Composable
    fun ARSceneComposable(viewModel: ARViewModel) {
        val arNodes by viewModel.arNodes.collectAsState()
        val engine = rememberEngine()
        val modelLoader = rememberModelLoader(engine)
        val materialLoader = rememberMaterialLoader(engine)
        val cameraNode = rememberARCameraNode(engine)
        val childNodes = rememberNodes()
        val view = rememberView(engine)
        val collisionSystem = rememberCollisionSystem(view)
        val planeRenderer by remember { mutableStateOf(false) }

        val modelInstances = remember { mutableMapOf<String, ModelInstance>() }
        var trackingFailureReason by remember {
            mutableStateOf<TrackingFailureReason?>(null)
        }
        var frame by remember { mutableStateOf<Frame?>(null) }

        var nodesProcessed by remember { mutableStateOf(false) }

        var longitude by remember { mutableStateOf(0.0) }
        var latitude by remember { mutableStateOf(0.0) }
        var altitude by remember { mutableStateOf(0.0) }

        Box(modifier = Modifier.fillMaxSize()) {
            ARScene(
                modifier = Modifier.fillMaxSize(),
                childNodes = childNodes,
                engine = engine,
                view = view,
                modelLoader = modelLoader,
                collisionSystem = collisionSystem,
                sessionConfiguration = { session, config ->
                    config.depthMode =
                        when (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                            true -> Config.DepthMode.AUTOMATIC
                            else -> Config.DepthMode.DISABLED
                        }
                    config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    config.lightEstimationMode =
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR
                    config.geospatialMode = Config.GeospatialMode.ENABLED
                },
                cameraNode = cameraNode,
                planeRenderer = false,
                onTrackingFailureChanged = {
                    trackingFailureReason = it
                },
                // 매 프레임마다 호출
                onSessionUpdated = { session, updatedFrame ->
                    frame = updatedFrame



                    Log.d(TAG, "ARSceneComposable: ${session.earth?.cameraGeospatialPose?.horizontalAccuracy}")
                    session.earth?.let { earth ->

                        latitude = earth.cameraGeospatialPose.latitude
                        longitude = earth.cameraGeospatialPose.longitude
                        altitude = earth.cameraGeospatialPose.altitude

                            if (earth.trackingState == TrackingState.TRACKING && !nodesProcessed) {
                            arNodes.forEach { node ->
                                session.earth?.let {
                                    processNode(
                                        node,
                                        it,
                                        engine,
                                        modelLoader,
                                        modelInstances,
                                        childNodes,
                                    )
                                }
                            }

                            nodesProcessed = true
                        }
                    }
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { motionEvent, node ->

                    })
            )
            Column {
                Text(
                    modifier = Modifier
                        .systemBarsPadding()
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    color = Color.White,
                    text = latitude.toString()
                )
                Text(
                    modifier = Modifier
                        .systemBarsPadding()
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    color = Color.White,
                    text = longitude.toString()
                )
                Text(
                    modifier = Modifier
                        .systemBarsPadding()
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    color = Color.White,
                    text = altitude.toString()
                )
            }
        }
    }

    private fun processNode(
        node: ARNode,
        earth: Earth,
        engine: Engine,
        modelLoader: ModelLoader,
        modelInstances: MutableMap<String, ModelInstance>,
        childNodes: MutableList<Node>,
    ) {
        val isVisible = 100.0 <= VISIBLE_DISTANCE_THRESHOLD

        if (node.isActive) {
            try {
                val earthAnchor = earth.createAnchor(
                    node.latitude,
                    node.longitude,
                    node.altitude,
                    0f, 0f, 0f, 1f
                )

                val anchorNode = createAnchorNode(
                    node,
                    engine = engine,
                    modelLoader = modelLoader,
                    modelInstances = modelInstances,
                    anchor = earthAnchor
                )

                childNodes.add(anchorNode)
            } catch (e: Exception) {
                Log.e("ARScene", "Error creating anchor for ${node.id}: ${e.message}")
            }
        }
    }

    // 특정 위치에 3D 모델을 배치
    private fun createAnchorNode(
        node: ARNode,
        engine: Engine,
        modelLoader: ModelLoader,
        modelInstances: MutableMap<String, ModelInstance>,
        anchor: Anchor
    ): AnchorNode {
        val anchorNode = AnchorNode(engine = engine, anchor = anchor).apply {
            isPositionEditable = false
            isRotationEditable = false
            isScaleEditable = false
        }

        val modelInstance = modelInstances[node.id] ?: modelLoader.createInstancedModel(
            node.model,
            kMaxModelInstances
        ).first()

        val modelNode = ModelNode(
            modelInstance = modelInstance,
            // Scale to fit in a 0.5 meters cube
            scaleToUnits = 0.5f
        ).apply {
            // Model Node needs to be editable for independent rotation from the anchor rotation
            rotation = Rotation(0f, 180f, 0f)
        }

        anchorNode.addChildNode(modelNode)

        return anchorNode
    }
}