package com.example.arcoretest

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.loaders.MaterialLoader
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.forEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val kModelFile = "models/direction.glb"
private const val kMaxModelInstances = 10

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

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    val createdAnchorNodes = mutableMapOf<String, AnchorNode>()

    private val nodes: MutableList<ARNode> = mutableListOf(
        ARNode("0", 36.1069, 128.4167, 10.0, "models/raccoon1.glb", true),
        ARNode("1", 36.1069, 128.4167, 40.0, "models/quest.glb", true),
        ARNode("2", 36.1069, 128.4167, 40.5, "models/quest.glb", true),
        ARNode("3", 36.1069, 128.4167, 41.0, "models/quest.glb", true),
        ARNode("4", 36.1069, 128.4167, 41.5, "models/quest.glb", true),
        ARNode("5", 36.1069, 128.4167, 42.0, "models/quest.glb", true),
        ARNode("6", 36.1069, 128.4167, 42.5, "models/quest.glb", true),
        ARNode("7", 36.1069, 128.4167, 43.0, "models/raccoon1.glb", true),
        ARNode("8", 36.1069, 128.4167, 43.5, "models/raccoon1.glb", true),
        ARNode("9", 36.1069, 128.4167, 44.0, "models/raccoon1.glb", true),
        ARNode("10", 36.1069, 128.4167, 44.5, "models/raccoon1.glb", true),
        ARNode("11", 36.1069, 128.4167, 45.0, "models/raccoon1.glb", true),
        ARNode("12", 36.1069, 128.4167, 45.5, "models/raccoon1.glb", true),
        ARNode("13", 36.1069, 128.4167, 46.0, "models/raccoon1.glb", true),
        ARNode("14", 36.1069, 128.4167, 46.5, "models/raccoon1.glb", true),
        ARNode("15", 36.1069, 128.4167, 47.0, "models/raccoon1.glb", true),
        ARNode("16", 36.1069, 128.4167, 47.5, "models/raccoon1.glb", true),
        ARNode("17", 36.1069, 128.4167, 48.0, "models/raccoon1.glb", true),
        ARNode("18", 36.1069, 128.4167, 48.5, "models/raccoon1.glb", true),
        ARNode("19", 36.1069, 128.4167, 49.0, "models/raccoon1.glb", true),
        ARNode("20", 36.1069, 128.4167, 49.5, "models/raccoon1.glb", true),
        ARNode("21", 36.1069, 128.4167, 50.0, "models/raccoon1.glb", true),
        ARNode("22", 36.1069, 128.4167, 50.5, "models/raccoon1.glb", true),
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
        val planeRenderer by remember { mutableStateOf(true) }

        val modelInstances = remember { mutableListOf<ModelInstance>() }
        var trackingFailureReason by remember {
            mutableStateOf<TrackingFailureReason?>(null)
        }
        var frame by remember { mutableStateOf<Frame?>(null) }

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
                    config.geospatialMode = Config.GeospatialMode.ENABLED
                    config.instantPlacementMode = Config.InstantPlacementMode.DISABLED
                    config.lightEstimationMode =
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                cameraNode = cameraNode,
                planeRenderer = planeRenderer,
                onTrackingFailureChanged = {
                    trackingFailureReason = it
                },
                // 매 프레임마다 호출
                onSessionUpdated = { session, updatedFrame ->
                    frame = updatedFrame

                    val earth = session.earth

                    if (earth?.trackingState == TrackingState.TRACKING) {
                        Log.d(TAG, "ARSceneComposable: 트래킹")
                        arNodes.forEach { node ->
                            processNode(
                                node,
                                earth,
                                engine,
                                modelLoader,
                                materialLoader,
                                modelInstances,
                                childNodes,
                                createdAnchorNodes)
                        }
                    }
                },
                // 탭한 부분에 3D 모델 배치
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { motionEvent, node ->

                    })
            )
        }
    }

    private fun processNode(
        node: ARNode,
        earth: Earth?,
        engine: Engine,
        modelLoader: ModelLoader,
        materialLoader: MaterialLoader,
        modelInstances: MutableList<ModelInstance>,
        childNodes: MutableList<Node>,
        createdAnchorNodes: MutableMap<String, AnchorNode>
    ) {
        if (node.isActive) {
            try {
                val earthAnchor = earth?.createAnchor(
                    node.latitude,
                    node.longitude,
                    node.altitude,
                    0f, 0f, 0f, 1f
                )

                earthAnchor?.let {
                    val anchorNode = createAnchorNode(
                        engine = engine,
                        modelLoader = modelLoader,
                        materialLoader = materialLoader,
                        modelInstances = modelInstances,
                        anchor = earthAnchor
                    )

                    childNodes.add(anchorNode)
                    createdAnchorNodes[node.id] = anchorNode
                }
            } catch (e: Exception) {
                Log.e("ARScene", "Error creating anchor for ${node.id}: ${e.message}")
            }
        } else if (!node.isActive && createdAnchorNodes.containsKey(node.id)) {
            val anchorNode = createdAnchorNodes.remove(node.id)
            anchorNode?.let {
                childNodes.remove(it)
                it.destroy()
            }
            Log.d("ARScene", "Removed anchor node for ${node.id}")
        }
    }

    private fun createAnchorNode(
        engine: Engine,
        modelLoader: ModelLoader,
        materialLoader: MaterialLoader,
        modelInstances: MutableList<ModelInstance>,
        anchor: Anchor
    ): AnchorNode {
        val anchorNode = AnchorNode(engine = engine, anchor = anchor)
        val modelNode = ModelNode(
            modelInstance = modelInstances.apply {
                if (isEmpty()) {
                    this += modelLoader.createInstancedModel(kModelFile, kMaxModelInstances)
                }
            }.removeLast(),
            // Scale to fit in a 0.5 meters cube
            scaleToUnits = 0.5f
        ).apply {
            // Model Node needs to be editable for independent rotation from the anchor rotation
            isEditable = true
            rotation = Rotation(0f, 180f, 0f)
        }

        anchorNode.addChildNode(modelNode)

        return anchorNode
    }
}