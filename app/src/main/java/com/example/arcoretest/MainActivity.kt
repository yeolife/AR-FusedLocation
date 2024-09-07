package com.example.arcoretest

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

    private val createdAnchorNodes = mutableMapOf<String, AnchorNode>()

    private val nodes: MutableList<ARNode> = mutableListOf(
        ARNode("11", 36.10167, 128.41989, 76.60828696470708, "models/chick.glb", true),
        ARNode("12", 36.10167, 128.41989, 76.60828696470708, "models/otter2.glb", true),
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

        val modelInstances = remember { mutableListOf<ModelInstance>() }
        var trackingFailureReason by remember {
            mutableStateOf<TrackingFailureReason?>(null)
        }
        var frame by remember { mutableStateOf<Frame?>(null) }

        var nodesProcessed by remember { mutableStateOf(false) }

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

                    session.earth?.let { earth ->
                        if(earth.trackingState == TrackingState.TRACKING && !nodesProcessed) {
                            Log.d(TAG, "ARSceneComposable: ${session.earth?.cameraGeospatialPose?.altitude}")
                            Log.d(TAG, "ARSceneComposable2: ${session.earth?.cameraGeospatialPose?.longitude}")
                            arNodes.forEach { node ->
                                session.earth?.let {
                                    processNode(
                                        node,
                                        it,
                                        engine,
                                        modelLoader,
                                        modelInstances,
                                        childNodes,
                                        createdAnchorNodes)
                                }
                            }

                            nodesProcessed = true
                        }
                    }
                },
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { motionEvent, node -> })
            )
        }
    }

    private fun processNode(
        node: ARNode,
        earth: Earth,
        engine: Engine,
        modelLoader: ModelLoader,
        modelInstances: MutableList<ModelInstance>,
        childNodes: MutableList<Node>,
        createdAnchorNodes: MutableMap<String, AnchorNode>
    ) {

        val isVisible = 100.0 <= VISIBLE_DISTANCE_THRESHOLD

        if (node.isActive && isVisible) {
            if(!createdAnchorNodes.containsKey(node.id)) {
                try {
                    val earthAnchor = earth.createAnchor(
                        node.latitude,
                        node.longitude,
                        node.altitude,
                        0f, 0f, 0f, 1f
                    )

                    val anchorNode = createAnchorNode(
                        node.model,
                        engine = engine,
                        modelLoader = modelLoader,
                        modelInstances = modelInstances,
                        anchor = earthAnchor
                    )

                    childNodes.add(anchorNode)
                    createdAnchorNodes[node.id] = anchorNode
                } catch (e: Exception) {
                    Log.e("ARScene", "Error creating anchor for ${node.id}: ${e.message}")
                }
            }
        } else if (!node.isActive || !isVisible) {
            createdAnchorNodes.remove(node.id)?.let {
                childNodes.remove(it)
                it.destroy()
                Log.d("ARScene", "Removed anchor node for ${node.id}")
            }
        }
    }

    private fun createAnchorNode(
        fileUrl: String,
        engine: Engine,
        modelLoader: ModelLoader,
        modelInstances: MutableList<ModelInstance>,
        anchor: Anchor
    ): AnchorNode {
        val anchorNode = AnchorNode(engine = engine, anchor = anchor).apply {
            isEditable = false
            isPositionEditable = false
            isRotationEditable = false
            isScaleEditable = false
        }
        val modelNode = ModelNode(
            modelInstance = modelInstances.apply {
                if (isEmpty()) {
                    this += modelLoader.createInstancedModel(fileUrl, kMaxModelInstances)
                }
            }.removeLast(),
            // Scale to fit in a 0.5 meters cube
            scaleToUnits = 0.5f
        ).apply {
            // Model Node needs to be editable for independent rotation from the anchor rotation
            isEditable = false
            isPositionEditable = false
            isRotationEditable = false
            isScaleEditable = false
            rotation = Rotation(0f, 180f, 0f)
        }

        anchorNode.addChildNode(modelNode)

        return anchorNode
    }
}