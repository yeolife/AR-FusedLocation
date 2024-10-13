package com.example.arcoretest.ui

import android.Manifest
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.arcoretest.R
import com.google.ar.core.Camera
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingFailureReason
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarConfig
import com.gowtham.ratingbar.RatingBarStyle
import com.gowtham.ratingbar.StepSize
import com.ssafy.ar.ARViewModel
import com.ssafy.ar.data.QuestState
import com.ssafy.ar.data.SpeciesType
import com.ssafy.ar.data.getImageResource
import com.ssafy.ar.data.scripts
import com.ssafy.ar.ui.ArStatusText
import com.ssafy.ar.ui.CustomCard
import com.ssafy.ar.ui.QuestDialog
import com.ssafy.ar.util.MultiplePermissionsHandler
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.isTrackingPlane
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberOnGestureListener
import io.github.sceneview.rememberView
import io.github.sceneview.safeDestroyView
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "ArScreen"

@Composable
fun ARSceneComposable(
    onPermissionDenied: () -> Unit
) {
    // Screen Size
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    val density = LocalDensity.current
    val widthPx = with(density) { screenWidth.toPx() }.toInt()
    val heightPx = with(density) { screenHeight.toPx() }.toInt()

    // LifeCycle
    val context = LocalContext.current
    val viewModel: ARViewModel = viewModel(factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ARViewModel(context) as T
        }
    })
    val coroutineScope = rememberCoroutineScope()

    // Permission
    var hasPermission by remember { mutableStateOf(false) }

    // AR Basic
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    val collisionSystem = rememberCollisionSystem(view)
    val childNodes = rememberNodes()
    val cameraNode = rememberARCameraNode(engine)
    var planeRenderer by remember { mutableStateOf(false) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }

    // AR State
    val questInfos by viewModel.questInfos.collectAsState()
    val nearestQuestInfo by viewModel.nearestQuestInfo.collectAsState()

    // RatingBar
    val rating by viewModel.rating.collectAsState()
    var showRating by remember { mutableStateOf(true) }
    val animatedRating by animateFloatAsState(
        targetValue = if (showRating) rating else 0f,
        label = "Rating Animation"
    )

    // Dialog & SnackBar
    val showDialog by viewModel.showDialog.collectAsState()
    val dialogData by viewModel.dialogData.collectAsState()
    val snackBarHostState = remember { SnackbarHostState() }

    MultiplePermissionsHandler(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) { permissionResults ->
        if (permissionResults.all { permissions -> permissions.value }) {
            hasPermission = true

            viewModel.locationManager.startLocationUpdates()

            viewModel.getAllQuests()
        } else {
            hasPermission = false

            onPermissionDenied()

            return@MultiplePermissionsHandler
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                childNodes.clear()
                collisionSystem.destroy()
                engine.safeDestroyView(view)

                viewModel.locationManager.stopLocationUpdates()
                coroutineScope.cancel()
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

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
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.focusMode = Config.FocusMode.AUTO
//                config.geospatialMode = Config.GeospatialMode.ENABLED
            },
            cameraNode = cameraNode,
            planeRenderer = planeRenderer,
            onTrackingFailureChanged = { trackingFailureReason = it },
            onSessionUpdated = { session, frame ->
                if (trackingFailureReason == null) {
                    val desiredPlaneFindingMode =
                        if (nearestQuestInfo.shouldPlace || childNodes.lastOrNull()?.isVisible == false)
                            Config.PlaneFindingMode.HORIZONTAL
                        else
                            Config.PlaneFindingMode.DISABLED

                    if (desiredPlaneFindingMode != session.config.planeFindingMode) {
                        session.configure(session.config.apply {
                            setPlaneFindingMode(desiredPlaneFindingMode)
                        })
                    }
                }

                if (frame.isTrackingPlane() && nearestQuestInfo.shouldPlace) {
                    nearestQuestInfo.npc?.let { quest ->
                        val planeAndPose = viewModel.nodeManager.findPlaneInView(frame, widthPx, heightPx, frame.camera)

                        if (planeAndPose != null) {
                            val (plane, pose) = planeAndPose

                            if (childNodes.all { it.name != quest.id.toString() }) {
                                viewModel.placeNode(
                                    plane,
                                    pose,
                                    quest,
                                    engine,
                                    modelLoader,
                                    materialLoader,
                                    childNodes
                                )
                            }
                        }
                    }
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    if (node is ModelNode || node?.parent is ModelNode) {
                        val modelNode = if (node is ModelNode) node else node.parent as? ModelNode

                        val anchorNode = modelNode?.parent as? AnchorNode

                        val anchorId = anchorNode?.name?.toLong()

                        if (anchorId != null) {
                            val quest = questInfos[anchorId]

                            quest?.let {
                                when (quest.isComplete) {
                                    // 퀘스트 진행전
                                    QuestState.WAIT -> {
                                        viewModel.showQuestDialog(
                                            quest
                                        ) { accepted ->
                                            if (accepted) {
                                                viewModel.updateQuestState(
                                                    anchorId,
                                                    QuestState.PROGRESS
                                                )

                                                viewModel.updateAnchorNode(
                                                    quest,
                                                    modelNode,
                                                    anchorNode,
                                                    modelLoader,
                                                    materialLoader
                                                )
                                            }
                                        }
                                    }
                                    // 퀘스트 진행중
                                    QuestState.PROGRESS -> {
                                        viewModel.showQuestDialog(
                                            quest
                                        ) { accepted ->
                                            if (accepted) {
                                                viewModel.updateQuestState(
                                                    anchorId,
                                                    QuestState.COMPLETE
                                                )

                                                val imageNode = modelNode.childNodes
                                                    .filterIsInstance<ImageNode>()
                                                    .firstOrNull()

                                                imageNode?.let {
                                                    viewModel.updateModelNode(
                                                        imageNode,
                                                        modelNode,
                                                        materialLoader
                                                    )
                                                }

                                                coroutineScope.launch {
                                                    snackBarHostState.showSnackbar("퀘스트가 완료되었습니다!")
                                                }
                                            }
                                        }
                                    }
                                    // 퀘스트 완료
                                    QuestState.COMPLETE -> {
                                        coroutineScope.launch {
                                            snackBarHostState.showSnackbar(
                                                scripts[quest.questType]?.completeMessage ?: ""
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                })
        )
        Column {
            Box(
                modifier = Modifier
                    .padding(top = 60.dp, start = 40.dp, end = 40.dp)
            ) {
                CustomCard(
                    imageUrl = SpeciesType.fromLong(nearestQuestInfo.npc?.speciesId ?: 1L)
                        ?.getImageResource() ?: (R.drawable.maple),
                    title = "가까운 미션 ${nearestQuestInfo.npc?.id ?: "검색중.."} ",
                    state = nearestQuestInfo.npc?.isComplete ?: QuestState.WAIT,
                    distanceText = "${
                        nearestQuestInfo.distance?.let {
                            if (nearestQuestInfo.shouldPlace)
                                "목적지 도착!"
                            else
                                "%.2f m".format(it)
                        } ?: "검색중.."
                    } ")
                Card(
                    modifier = Modifier
                        .offset(y = (-20).dp)
                        .align(Alignment.TopCenter),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Gray)
                ) {
                    RatingBar(
                        value = animatedRating,
                        config = RatingBarConfig()
                            .isIndicator(true)
                            .stepSize(StepSize.HALF)
                            .numStars(5)
                            .size(28.dp)
                            .inactiveColor(Color.LightGray)
                            .style(RatingBarStyle.Normal),
                        onValueChange = { },
                        onRatingChanged = { },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            ArStatusText(
                trackingFailureReason = trackingFailureReason,
                isAvailable = nearestQuestInfo.shouldPlace,
                isPlace = nearestQuestInfo.npc?.id?.let { viewModel.getIsPlaceQuest(it) }
            )
        }

        SnackbarHost(
            hostState = snackBarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) { snackbarData ->
            Snackbar(snackbarData = snackbarData)
        }
    }

    if (showDialog) {
        QuestDialog(
            dialogData,
            onConfirm = { viewModel.onDialogConfirm() },
            onDismiss = { viewModel.onDialogDismiss() }
        )
    }
}

