package com.ssafy.ar

import android.content.Context
import android.location.Location
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.filament.Engine
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.ssafy.ar.data.NearestNPCInfo
import com.ssafy.ar.data.QuestInfo
import com.ssafy.ar.data.QuestState
import com.ssafy.ar.data.getModelUrl
import com.ssafy.ar.dummy.quests
import com.ssafy.ar.manager.ARLocationManager
import com.ssafy.ar.manager.ARNodeManager
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ImageNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.roundToInt

class ARViewModel(private val context: Context) : ViewModel() {

    lateinit var nodeManager: ARNodeManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    lateinit var locationManager: ARLocationManager

    init {
        viewModelScope.launch {
            nodeManager = ARNodeManager()

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            locationManager = ARLocationManager(context, fusedLocationClient)

            locationManager.currentLocation.collectLatest { location ->
                updateNearestNPC(location)
            }
        }
    }

    private val placingNodes = Collections.synchronizedSet(mutableSetOf<Long>())

    // 모든 Quest 정보
    private val _questInfos = MutableStateFlow<Map<Long, QuestInfo>>(emptyMap())
    val questInfos = _questInfos.asStateFlow()

    // 가장 가까운 NPC
    private val _nearestQuestInfo = MutableStateFlow(NearestNPCInfo())
    val nearestQuestInfo = _nearestQuestInfo.asStateFlow()

    // 퀘스트 완료 비율
    private val _rating = MutableStateFlow(0f)
    val rating: StateFlow<Float> = _rating.asStateFlow()

    // Dialog
    private val _showDialog = MutableStateFlow(false)
    val showDialog = _showDialog.asStateFlow()

    // Dialog Data
    private val _dialogData = MutableStateFlow(QuestInfo())
    val dialogData: StateFlow<QuestInfo> = _dialogData
    private var dialogCallback: ((Boolean) -> Unit)? = null

    fun getIsPlaceQuest(id: Long): Boolean? {
        return _questInfos.value[id]?.isPlace
    }

    fun getAllQuests() {
        _questInfos.value = quests

        updateRating(_questInfos.value.count { it.value.isComplete == QuestState.COMPLETE }.toFloat(),
            _questInfos.value.size.toFloat())
    }

    private fun updateIsPlaceQuest(id: Long, state: Boolean) {
        _questInfos.update { currentMap ->
            currentMap.toMutableMap().apply {
                this[id]?.let { npcLocation ->
                    this[id] = npcLocation.copy(isPlace = state)
                }
            }
        }
    }

    fun updateQuestState(questId: Long, state: QuestState) {
        _questInfos.update { currentMap ->
            currentMap.toMutableMap().apply {
                this[questId]?.let { npcLocation ->
                    this[questId] = npcLocation.copy(isComplete = state)
                }
            }
        }

        locationManager.currentLocation.value?.let {
            updateNearestNPC(it)
        }
    }

    private fun updateNearestNPC(location: Location?) {
        location?.let {
            val nearestNPCInfo = locationManager.operateNearestNPC(it, questInfos.value)

            _nearestQuestInfo.value = nearestNPCInfo

            locationManager.setFusedLocationClient(nearestNPCInfo.distance ?: 100f)
        }
    }

    fun placeNode(
        plane: Plane,
        pose: Pose,
        questInfo: QuestInfo,
        engine: Engine,
        modelLoader: ModelLoader,
        materialLoader: MaterialLoader,
        childNodes: SnapshotStateList<Node>
    ) {
        if (getIsPlaceQuest(questInfo.id) == true || !placingNodes.add(questInfo.id)) return

        viewModelScope.launch {
            nodeManager.placeNode(
                plane = plane,
                pose = pose,
                questInfo = questInfo,
                engine = engine,
                modelLoader = modelLoader,
                materialLoader = materialLoader,
                childNodes = childNodes,
                onSuccess = {
                    updateIsPlaceQuest(questInfo.id, true)

                    locationManager.currentLocation.value?.let {
                        updateNearestNPC(it)
                    }
                }
            )

            placingNodes.remove(questInfo.id)
        }
    }

    fun updateAnchorNode(
        questInfo: QuestInfo,
        childNode: ModelNode,
        parentNode: AnchorNode,
        modelLoader: ModelLoader,
        materialLoader: MaterialLoader
    ) {
        viewModelScope.launch {
            nodeManager.updateAnchorNode(
                id = questInfo.id,
                modelUrl = getModelUrl(questInfo.npcId),
                childNode = childNode,
                parentNode = parentNode,
                modelLoader = modelLoader,
                materialLoader = materialLoader
            )
        }
    }

    fun updateModelNode(
        childNode: ImageNode,
        parentNode: ModelNode,
        materialLoader: MaterialLoader
    ) {
        viewModelScope.launch {
            nodeManager.updateModelNode(
                childNode = childNode,
                parentNode = parentNode,
                materialLoader = materialLoader
            )
        }
    }

    private fun updateRating(numerator: Float, denominator: Float) {
        if(denominator == 0f) return

        val ratingValue: Float = (numerator)/(denominator) * 5

        val roundedRatingValue = (ratingValue * 10).roundToInt() / 10f

        _rating.value = roundedRatingValue
    }

    fun showQuestDialog(questInfo: QuestInfo, callback: (Boolean) -> Unit) {
        _dialogData.value = questInfo
        _showDialog.value = true
        dialogCallback = callback
    }

    fun onDialogConfirm() {
        _showDialog.value = false
        _dialogData.value = QuestInfo()
        dialogCallback?.invoke(true)
        dialogCallback = null
    }

    fun onDialogDismiss() {
        _showDialog.value = false
        _dialogData.value = QuestInfo()
        dialogCallback?.invoke(false)
        dialogCallback = null
    }

    override fun onCleared() {
        super.onCleared()

        locationManager.stopLocationUpdates()
    }
}