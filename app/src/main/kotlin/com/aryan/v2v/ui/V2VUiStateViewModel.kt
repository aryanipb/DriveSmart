package com.aryan.v2v.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.aryan.v2v.V2VState

data class DashboardState(
    val ego: V2VState = V2VState(0f, 0f, 0f, 0f, 0f),
    val neighbors: List<V2VState> = emptyList(),
    val prediction: FloatArray = FloatArray(0),
    val statusText: String = "Initializing V2V...",
    val modelText: String = "Model output initializing..."
)

data class CoordinateRow(
    val index: Int,
    val x: Float?,
    val y: Float?
)

class V2VUiStateViewModel : ViewModel() {
    private val _dashboardState = MutableLiveData(DashboardState())
    val dashboardState: LiveData<DashboardState> = _dashboardState

    private val _connectedDeviceIds = MutableLiveData<List<String>>(emptyList())
    val connectedDeviceIds: LiveData<List<String>> = _connectedDeviceIds

    private val _predictedRows = MutableLiveData<List<CoordinateRow>>(
        List(30) { idx -> CoordinateRow(idx + 1, null, null) }
    )
    val predictedRows: LiveData<List<CoordinateRow>> = _predictedRows

    fun publishDashboard(state: DashboardState) {
        _dashboardState.postValue(state)
    }

    fun publishConnectedIds(ids: List<String>) {
        _connectedDeviceIds.postValue(ids)
    }

    fun publishPredictedRows(prediction: FloatArray) {
        val rows = ArrayList<CoordinateRow>(30)
        var pointIdx = 0
        var floatIdx = 0
        while (pointIdx < 30) {
            if (floatIdx + 1 < prediction.size) {
                rows.add(CoordinateRow(pointIdx + 1, prediction[floatIdx], prediction[floatIdx + 1]))
                floatIdx += 2
            } else {
                rows.add(CoordinateRow(pointIdx + 1, null, null))
            }
            pointIdx++
        }
        _predictedRows.postValue(rows)
    }
}
