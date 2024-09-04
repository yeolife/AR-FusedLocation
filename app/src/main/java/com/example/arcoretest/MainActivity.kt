package com.example.arcoretest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
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
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.collision.Vector3
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
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

private const val kModelFile = "models/direction.glb"
private const val kMaxModelInstances = 10

data class ARNode(
    val id: String,
    val latitude: Double,
    val longitude: Double,
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

class MainActivity : ComponentActivity() {
    private val viewModel: ARViewModel by viewModels()

    private val nodes: MutableList<ARNode> = mutableListOf(
        ARNode("1", 36.106439100723954, 128.4165645218601, "models/arrow.glb", false),
        ARNode("2", 36.10642826562254, 128.41658732063672, "models/raccoon1.glb", true)
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
        var planeRenderer by remember { mutableStateOf(true) }

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
                    config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                    config.lightEstimationMode =
                        Config.LightEstimationMode.ENVIRONMENTAL_HDR
                },
                cameraNode = cameraNode,
                planeRenderer = planeRenderer,
                onTrackingFailureChanged = {
                    trackingFailureReason = it
                },
                // 첫번째 수평 평면 감지 & 매 프레임마다 호출
                onSessionUpdated = { session, updatedFrame ->
                    frame = updatedFrame

                    if (childNodes.isEmpty()) {
                        updatedFrame.getUpdatedPlanes()
                            .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                            ?.let { it.createAnchorOrNull(it.centerPose) }?.let { anchor ->
                                childNodes += createAnchorNode(
                                    engine = engine,
                                    modelLoader = modelLoader,
                                    materialLoader = materialLoader,
                                    modelInstances = modelInstances,
                                    anchor = anchor
                                )
                            }
                    }
                },
                // 탭한 부분에 3D 모델 배치
                onGestureListener = rememberOnGestureListener(
                    onSingleTapConfirmed = { motionEvent, node ->
                        if (node == null) {
                            val hitResults = frame?.hitTest(motionEvent.x, motionEvent.y)
                            hitResults?.firstOrNull {
                                it.isValid(
                                    depthPoint = false,
                                    point = false
                                )
                            }?.createAnchorOrNull()
                                ?.let { anchor ->
                                    planeRenderer = false
                                    childNodes += createAnchorNode(
                                        engine = engine,
                                        modelLoader = modelLoader,
                                        materialLoader = materialLoader,
                                        modelInstances = modelInstances,
                                        anchor = anchor
                                    )
                                }
                        }
                    })
            )
            Text(
                modifier = Modifier
                    .systemBarsPadding()
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp, start = 32.dp, end = 32.dp),
                textAlign = TextAlign.Center,
                fontSize = 28.sp,
                color = Color.White,
                text = trackingFailureReason?.let {
                    it.getDescription(LocalContext.current)
                } ?: if (childNodes.isEmpty()) {
                    stringResource(R.string.point_your_phone_down)
                } else {
                    stringResource(R.string.tap_anywhere_to_add_model)
                }
            )
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
        val boundingBoxNode = CubeNode(
            engine,
            size = modelNode.extents,
            center = modelNode.center,
            materialInstance = materialLoader.createColorInstance(Color.White.copy(alpha = 0.5f))
        ).apply {
            isVisible = false
        }
        modelNode.addChildNode(boundingBoxNode)
        anchorNode.addChildNode(modelNode)

        listOf(modelNode, anchorNode).forEach {
            it.onEditingChanged = { editingTransforms ->
                boundingBoxNode.isVisible = editingTransforms.isNotEmpty()
            }
        }
        return anchorNode
    }
}