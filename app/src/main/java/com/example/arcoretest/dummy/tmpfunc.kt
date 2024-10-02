//fun distance() {
//    session.earth?.let { earth ->
//        if (earth.trackingState == TrackingState.TRACKING && !nodesProcessed) {
//            if ((session.earth?.cameraGeospatialPose?.horizontalAccuracy ?: 30.0) <= 20 &&
//                session.earth?.cameraGeospatialPose?.horizontalAccuracy != 0.0 &&
//                (session.earth?.cameraGeospatialPose?.verticalAccuracy ?: 30.0) <= 5 &&
//                session.earth?.cameraGeospatialPose?.verticalAccuracy != 0.0) {

//                }
//            }
//        }
//    }

//// 평면에 노드 배치
//suspend fun placeNode(
//    plane: Plane,
//    pose: Pose,
//    anchorId: String,
//    frame: Frame?,
//    childNodes: SnapshotStateList<Node>,
//    onSuccess: () -> Unit,
//) = mutex.withLock {
//    if(childNodes.any { it.name == anchorId }) return@withLock
//
//    frame?.getUpdatedPlanes()
//        ?.lastOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
//        ?.let { it.createAnchorOrNull(it.centerPose) }?.let { anchor ->
//            val anchorNode = createAnchorNode(
//                scriptNode[0],
//                plane.createAnchor(pose),
//            ).apply { name = anchorId }
//
//            childNodes.add(anchorNode)
//
//            delay(5000)
//
//            onSuccess()
//        }
//}

//// 절대좌표에 노드를 childNodes에 추가
//private fun processNode(
//    node: ARNode,
//    earth: Earth,
//    engine: Engine,
//    modelLoader: ModelLoader,
//    materialLoader: MaterialLoader,
//    modelInstances: MutableMap<String, ModelInstance>,
//    childNodes: MutableList<Node>
//) {
//    if (node.isActive) {
//        try {
//            val earthAnchor = earth.createAnchor(
//                node.latitude,
//                node.longitude,
//                earth.cameraGeospatialPose.altitude,
//                0f, 0f, 0f, 1f
//            )
//
//            val anchorNode = createAnchorNode(
//                node,
//                engine = engine,
//                modelLoader = modelLoader,
//                materialLoader = materialLoader,
//                modelInstances = modelInstances,
//                anchor = earthAnchor
//            )
//
//            childNodes.add(anchorNode)
//        } catch (e: Exception) {
//            Log.e("ARScene", "Error creating anchor for ${node.id}: ${e.message}")
//        }
//    }
//}

//private suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
//    if (!context.checkLocationPermission()) {
//        continuation.resume(null)
//        return@suspendCancellableCoroutine
//    }
//
//    val locationRequest = createCurrentLocationRequest(1000, 0)
//
//    fusedLocationClient.getCurrentLocation(locationRequest, createCancellationToken())
//        .addOnSuccessListener { location ->
//            continuation.resume(location)
//        }
//        .addOnFailureListener { exception ->
//            continuation.resumeWithException(exception)
//        }
//}

//private fun createCancellationToken(): CancellationToken {
//    val cancellationTokenSource = CancellationTokenSource()
//    return cancellationTokenSource.token
//}

//private fun createCurrentLocationRequest(limitTime: Long, cachingExpiresIn: Long): CurrentLocationRequest =
//    CurrentLocationRequest.Builder()
//        .setDurationMillis(limitTime)
//        .setMaxUpdateAgeMillis(cachingExpiresIn)
//        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
//        .build()

//data class ARNode(
//    val id: String,
//    val latitude: Double,
//    val longitude: Double,
//    val altitude: Double,
//    val model: String,
//    var isQuest: Boolean
//)

//val nodes: List<ARNode> = listOf(
//    ARNode(
//        "0",
//        36.10716757434519,
//        128.41650682192451,
//        73.33939074445516,
//        "models/quest.glb",
//        false
//    ),
//    ARNode(
//        "1",
//        36.10716757434519,
//        128.41650682192451,
//        73.33939074445516,
//        "models/penguin.glb",
//        false
//    ),
//    ARNode(
//        "2",
//        36.10719340772349,
//        128.41647777400757,
//        73.54002152141184,
//        "models/otter.glb",
//        false
//    ),
//    ARNode(
//        "3",
//        36.10714848472349,
//        128.41645558800757,
//        73.54002152141184,
//        "models/chick.glb",
//        false
//    ),
//    ARNode(
//        "4",
//        36.10718419443119,
//        128.41647704496236,
//        73.54002152141184,
//        "models/turtle.glb",
//        false
//    ),
//    ARNode(
//        "5",
//        36.10718419443119,
//        128.41647704496236,
//        73.54002152141184,
//        "models/unicorn.glb",
//        false
//    ),
//    ARNode(
//        "6",
//        36.106748456430424,
//        128.41639460336677,
//        68.46302377991378,
//        "models/wishotter.glb",
//        false
//    ),
//    ARNode(
//        "7",
//        36.10688456844942,
//        128.41625326737577,
//        68.78246488422155,
//        "models/direction.glb",
//        false
//    ),
//    ARNode(
//        "8",
//        36.10672958995879,
//        128.41622445983785,
//        67.63452187180519,
//        "models/penguin.glb",
//        false
//    ),
//    ARNode(
//        "9",
//        36.1067327895906,
//        128.4162147884974,
//        68.18832830246538,
//        "models/brownturtle.glb",
//        false
//    ),
//)

//@Immutable
//data class CurrentLocation(
//    val latitude: Double = 0.0,
//    val longitude: Double = 0.0,
//    val altitude: Double = 0.0,
//    val horizontalAccuracy: Double = 0.0,
//    val verticalAccuracy: Double = 0.0,
//    val distances: List<Float> = listOf()
//)