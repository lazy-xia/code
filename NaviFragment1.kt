package com.higocon.higometerapp.business.navi.v

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.higocon.higometerapp.MeterApplication
import com.higocon.higometerapp.R
import com.higocon.higometerapp.base.HigoFragment
import com.higocon.higometerapp.bean.BleSendData
import com.higocon.higometerapp.bean.HistoryUpdateEntity
import com.higocon.higometerapp.bean.RidingRealtimeData
import com.higocon.higometerapp.business.MainPageActivity
import com.higocon.higometerapp.business.navi.i.ISuggestSelect
import com.higocon.higometerapp.business.navi.vm.NaviViewModel
import com.higocon.higometerapp.business.riding.vm.MainViewModel
import com.higocon.higometerapp.databinding.FragmentNaviBinding
import com.higocon.higometerapp.event.Stoploop
import com.higocon.higometerapp.util.DistanceUtil
import com.higocon.higometerapp.util.StringUtil
import com.higocon.higometerapp.util.ToastUtil
import com.higocon.higometerapp.util.TurnIconIdHelper
import com.higocon.higometerapp.util.ble.BleUtil
import com.higocon.higometerapp.util.ble.SeekRunData
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.formatter.DistanceFormatterOptions
import com.mapbox.navigation.base.formatter.UnitType
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.base.trip.model.RouteProgressState
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.MapboxNavigationProvider
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.formatter.MapboxDistanceFormatter
import com.mapbox.navigation.core.formatter.MapboxDistanceUtil
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.OffRouteObserver
import com.mapbox.navigation.core.trip.session.RouteProgressObserver
import com.mapbox.navigation.ui.maneuver.api.MapboxManeuverApi
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowApi
import com.mapbox.navigation.ui.maps.route.arrow.api.MapboxRouteArrowView
import com.mapbox.navigation.ui.maps.route.arrow.model.RouteArrowOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.tripprogress.api.MapboxTripProgressApi
import com.mapbox.navigation.ui.tripprogress.model.DistanceRemainingFormatter
import com.mapbox.navigation.ui.tripprogress.model.EstimatedTimeToArrivalFormatter
import com.mapbox.navigation.ui.tripprogress.model.TimeRemainingFormatter
import com.mapbox.navigation.ui.tripprogress.model.TripProgressUpdateFormatter
import com.mapbox.navigation.utils.internal.toPoint
import com.mapbox.search.result.SearchSuggestion
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/*
 *  Created : xiang.xia on 2022/3/18.
 */
class NaviFragment1 : HigoFragment<FragmentNaviBinding>() {
    private var originLocation: Location? = null

    //locationProvider
    private val navigationLocationProvider = NavigationLocationProvider()

    private var tripProgressApi:MapboxTripProgressApi?=null


    private fun  initTripProgress(){
        val distanceFormatterOptions=DistanceFormatterOptions.Builder(requireContext())
            .unitType(UnitType.METRIC)
            .build()
        val tripProgressFormatter = TripProgressUpdateFormatter.Builder(requireContext())
            .distanceRemainingFormatter(DistanceRemainingFormatter(distanceFormatterOptions))
            .timeRemainingFormatter(TimeRemainingFormatter(requireContext()))
            .estimatedTimeToArrivalFormatter(EstimatedTimeToArrivalFormatter(requireContext()))
            .build()

        tripProgressApi= MapboxTripProgressApi(tripProgressFormatter)
    }
    /**
     * Mapbox Navigation entry point. There should only be one instance of this object for the app.
     */
    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onResumedObserver = object : MapboxNavigationObserver {
            @SuppressLint("MissingPermission")
            override fun onAttached(mapboxNavigation: MapboxNavigation) {
                tripUID = UUID.randomUUID().toString()
                mapboxNavigation.registerLocationObserver(locationObserver)
                //监听路线变化
                mapboxNavigation.registerRoutesObserver(routesObserver)
                //监听路线进度行程变化
                mapboxNavigation.registerRouteProgressObserver(routeProgressObserver)
                //监听路线离线 偏离
                mapboxNavigation.registerOffRouteObserver(offRouteObserver)
                if (ActivityCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                ) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return
                }
                try {
                    mapboxNavigation.startTripSession()
                } catch (e: Exception) {
                   //重新初始化地图
                    initNavigation()
                }
                Log.i("TAG", "onAttached: mapboxnavigation startTripSession $tripUID 导航日志开启监听")
                Log.i(TAG, "onResume: 导航日志")

                if (!EventBus.getDefault().isRegistered(this@NaviFragment1)) {
                    EventBus.getDefault().register(this@NaviFragment1)
                }
                initTripProgress()
            }

            override fun onDetached(mapboxNavigation: MapboxNavigation) {
                Log.i(TAG, "onDetached: ")
                release()
   
            }
        },
        onInitialize = this::initNavigation
    )

//    private lateinit  var mapboxNavigation: MapboxNavigation
//    override fun onResume() {
//        super.onResume()
//
//    }
//
//    override fun onPause() {
//        super.onPause()
//        Log.i(TAG, "onPause: 导航日志")
//    }


    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(requireContext())
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        )

    }

    private var destinationCoordinate: Point? = null
    private var edOriginHeight: Int = 0

    private var naviIndex = 0
    private lateinit var mainViewModel: MainViewModel
    private var originPoint: Point? = null

    //开始导航的时候记录的坐标点位
    companion object {
        var naviPoints = CopyOnWriteArrayList<Point>()
        var firstLocationUpdateReceived = true
    }

    var startNaviTime: String = ""
    var recordTimer: Timer? = null
    var canAddPoint = false

    var startTime: Long = 0
    var startTrip: Float = 0f
    var startBattery: Int = 0

    var tripOnce: Float = 0f
    var battery: Int = 0
    var avgSpped: Float = 0f
    var maxSpped: Float = 0f

    var createPolylineAnnotationManager: PolylineAnnotationManager? = null


    var tripUID = ""


    override fun initListeners() {
        super.initListeners()
        binding.btnStartFreeNavi.setOnClickListener {
            viewModel.naviStatus.postValue(NaviViewModel.NAVI_START_FREE)
            //清空导航数据 并开始记录数据
            synchronized(NaviFragment1::class.java) {
                naviStartParam()
                clearPointsRecord()
            }
        }
        binding.btnStatNavi.setOnClickListener {
            naviStartParam()
            clearPointsRecord()
            viewModel.naviStatus.postValue(NaviViewModel.NAVI_SEARING)
        }
        binding.btnEnd.setOnClickListener {
            Log.d(TAG, "initListeners: 点击了结束按钮")
            stopNavi()
        }

        binding.llNaviPrepare.setOnClickListener { }
        binding.llNaviProces.setOnClickListener { }
    }

    @SuppressLint("MissingPermission")
    override fun initData() {
        firstLocationUpdateReceived = true
        //检查mapboxnavigation
//        if (!MapboxNavigationProvider.isCreated()) {
//            MapboxNavigationProvider.create(NavigationOptions.Builder(context!!)
//                .accessToken(getString(R.string.mapbox_access_token))
//                .build())
//        }

//        mapboxNavigation= MapboxNavigation(
//            NavigationOptions.Builder(requireContext()).apply {
//                accessToken(getString(R.string.mapbox_access_token))
//            }.build()
//        )

        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
        // initialize Navigation Camera
        viewportDataSource = MapboxNavigationViewportDataSource(binding.mapView.getMapboxMap())
        navigationCamera = NavigationCamera(
            binding.mapView.getMapboxMap(),
            binding.mapView.camera,
            viewportDataSource
        )
        viewportDataSource.followingPadding = followingPadding

        binding.mapView.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
//        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
//            // shows/hide the recenter button depending on the camera state
//            when (navigationCameraState) {
//                NavigationCameraState.TRANSITION_TO_FOLLOWING,
//                NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE
//                NavigationCameraState.TRANSITION_TO_OVERVIEW,
//                NavigationCameraState.OVERVIEW,
//                NavigationCameraState.IDLE -> binding.recenter.visibility = View.VISIBLE
//            }
//        }
        // make sure to use the same DistanceFormatterOptions across different features
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(requireContext()).build()

        // initialize maneuver api that feeds the data to the top banner maneuver view
        maneuverApi = MapboxManeuverApi(
            MapboxDistanceFormatter(distanceFormatterOptions)
        )

        //initialize routeLineApI
        val routeLineOptions = MapboxRouteLineOptions.Builder(context!!)
            .withVanishingRouteLineEnabled(true)
             .withRouteLineBelowLayerId("road-label").build()
        routeLineApi = MapboxRouteLineApi(routeLineOptions)
        routeLineView = MapboxRouteLineView(routeLineOptions)

        // initialize maneuver arrow view to draw arrows on the map
        val routeArrowOptions = RouteArrowOptions.Builder(context!!).build()
        routeArrowView = MapboxRouteArrowView(routeArrowOptions)


        binding.mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)

        //填充fragment
        childFragmentManager.beginTransaction()
            .replace(R.id.fra_searching, NaviSearchFragment(object : ISuggestSelect {
                override fun select(searchSuggestion: SearchSuggestion?, coordinate: Point?) {
                    //coordinate 目的地坐标
                    destinationCoordinate = coordinate
                    naviStart()
//                    showllGO(searchSuggestion)
                }
            }) {
                viewModel.naviStatus.postValue(NaviViewModel.NAVI_PREPARE)
            })
            .commit()


        //开始定位 mapnavigation init 调用了
//        mapboxNavigation.startTripSession()

        viewModel = ViewModelProvider(requireActivity()).get(NaviViewModel::class.java)
        viewModel.naviStatus.observe(requireActivity()) {
            uiChange(it)
        }


        //initialize location puck 设置地图中位置标记 设置数据来源 由mapboxlocationProvider提供
        binding.mapView.location.apply {
            locationPuck = LocationPuck2D(
                bearingImage = context?.let {
                    ContextCompat.getDrawable(it,
                        R.drawable.mapbox_navigation_puck_icon)
                }
            )
            setLocationProvider(navigationLocationProvider)
            enabled = true
        }
        createPolylineAnnotationManager =
            binding.mapView.annotations.createPolylineAnnotationManager()
//        binding.mapView.postDelayed(
//            {
//            val snapshot = binding.mapView.snapshot()
//                println("截图。。。。。。")
//            }
//            ,5000)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(data: RidingRealtimeData) {
        //刷新数据
//        binding.imgLed.visibility = if (0 != data.light) View.VISIBLE else View.INVISIBLE
//        binding.imgBike.visibility = if (0 != data.bootStyle) View.VISIBLE else View.INVISIBLE
        if (0 == data.unit) {//m
            binding.tvSpeed.text = StringUtil.instance?.changeTvSize("${data.speed / 10f}")
        }
//        binding.progressBattery.progress = data.battery
        binding.tvBattery.text = StringUtil.instance?.changeTvSize("${data.battery}%")
//        binding.wavePower.addData(Random().nextInt(800).absoluteValue)
    }


    private fun stopNavi() {
        mapboxNavigation.setNavigationRoutes(listOf())
        val emptyData = BleSendData(0, 0, 0, 0)
        MainPageActivity.bleSendData = emptyData
        viewModel.naviStatus.postValue(NaviViewModel.NAVI_MAP)
        Log.i(TAG, "stopNavi: uichange")
        mainViewModel.naviData.postValue(emptyData)
        naviIndex = 0
        for (i in 0..5) {
            BleUtil.instance.send70cmd(emptyData)
        }

        if (null != recordTimer) {
            recordTimer!!.cancel()
            recordTimer = null
        }
        binding.mapView.annotations.removeAnnotationManager(createPolylineAnnotationManager!!)

        val consume = startBattery - battery
        val durationTime = (System.currentTimeMillis() - startTime) / 1000 / 60
        val distance = tripOnce - startTrip
        val body = HistoryUpdateEntity(
            "",
            "${MeterApplication.instance.user.id}",
            "",
            StringUtil.dateStr(Date(), "YYYY-MM-dd"),
            String.format("%.2f", distance),
            "$durationTime",
            "$avgSpped",
            "$maxSpped",
            "$consume",
            "",
            getCoordinates(),
            StringUtil.dateStr(Date(), "HH:mm"),
            StringUtil.dateStr(Date(), "YYYY-MM-dd"),
            startNaviTime
        )
        viewModel.uploadLog(body) {
            //清空导航数据
            clearPointsRecord()
        }

        endNaviParam()
    }

    private fun endNaviParam() {
        binding.tvTrip.text = ""
    }


    fun getCoordinates(): String {
        synchronized(NaviFragment1::class.java) {
            val buffer = StringBuffer()
            repeat(naviPoints.size) {
                val point = naviPoints[it]
                buffer.append("${point.latitude()}:${point.longitude()},")
            }
            return if (buffer.isEmpty()) {
                ""
            } else {
                buffer.substring(0, buffer.length - 1).toString()
            }
        }
    }

    private fun uiChange(it: Int?) {
        Log.i(TAG, "uiChange: $it")
        binding.llNaviProces.visibility = View.GONE
        binding.llNaviPrepare.visibility = View.GONE
        binding.llSearch.visibility = View.GONE
        binding.llRoutes.visibility = View.GONE
        binding.maneuverView.visibility = View.GONE
        binding.llGo.visibility = View.GONE

        when (it) {
            NaviViewModel.NAVI_MAP, NaviViewModel.NAVI_PREPARE -> {
                binding.llNaviPrepare.visibility = View.VISIBLE
            }
            NaviViewModel.NAVI_START -> {
                binding.llNaviProces.visibility = View.VISIBLE
                binding.maneuverView.visibility = View.VISIBLE
            }
            NaviViewModel.NAVI_START_FREE -> {
                binding.llNaviProces.visibility = View.VISIBLE
            }
            NaviViewModel.NAVI_SEARING -> {
                binding.llSearch.visibility = View.VISIBLE
            }
            NaviViewModel.NAVI_SEARING_ROUTE -> {
                binding.llRoutes.visibility = View.VISIBLE
            }
        }
    }

    private lateinit var viewModel: NaviViewModel
    private val pixelDensity = Resources.getSystem().displayMetrics.density
    private val followingPadding: EdgeInsets by lazy {
        EdgeInsets(
            180.0 * pixelDensity,
            40.0 * pixelDensity,
            240.0 * pixelDensity,
            40.0 * pixelDensity
        )
    }

    private fun showllGO(searchSuggestion: SearchSuggestion) {

        binding.tvName.text = searchSuggestion.name
        binding.tvAddress.text = searchSuggestion.address?.street
        naviStart()
        binding.tvDistance.text = "${String.format("%.1f", searchSuggestion.distanceMeters?.div(1000))} km"
    }

    override fun getLayoutResID(): Int {
        return R.layout.fragment_navi
    }

    //位置监听器
    private val locationObserver = object : LocationObserver {
        //更精确的位置回调 进度更高
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            //刷新到ui上来
            Log.i(TAG, "locationobserver onNewLocationMatcherResult: ")
            val transitionOptions: (ValueAnimator.() -> Unit) = {
                duration = 1000
            }
            val enhancedLocation = locationMatcherResult.enhancedLocation
            viewModel.currentLocation.value=enhancedLocation
            originLocation = enhancedLocation
            originPoint = Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude)
            navigationLocationProvider.changePosition(
                location = enhancedLocation,
                keyPoints = locationMatcherResult.keyPoints,
//                latLngTransitionOptions = transitionOptions,
//                bearingTransitionOptions = transitionOptions
            )
            // update camera position to account for new location
            viewportDataSource.onLocationChanged(enhancedLocation)
            viewportDataSource.evaluate()

            if (firstLocationUpdateReceived) {
                firstLocationUpdateReceived = false
                updateCamera(enhancedLocation)
            }

//            if ((viewModel.naviStatus.value != NaviViewModel.NAVI_START) ) {
//                updateCamera(enhancedLocation)
//            }

            //历史记录 5s记录一个点来获取数据
            if ((viewModel.naviStatus.value == NaviViewModel.NAVI_START_FREE) || (viewModel.naviStatus.value == NaviViewModel.NAVI_START)) {
                if (null == recordTimer) {
                    recordTimer = Timer()
                    recordTimer!!.schedule(object : TimerTask() {
                        override fun run() {
                            canAddPoint = true

                            if ((viewModel.naviStatus.value == NaviViewModel.NAVI_START_FREE) || (viewModel.naviStatus.value == NaviViewModel.NAVI_START)) {
                                binding.mapView.post {
                                    navigationCamera.requestNavigationCameraToFollowing()
                                }
                            }
                        }
                    }, 1000, 5 * 1000)
                }
                if (canAddPoint) {
                    synchronized(NaviFragment1::class.java) {
                        Log.i(TAG,
                            "  renderLine onNewLocationMatcherResult: 路线新增坐标点... ${naviPoints.size}")
                        //当前坐标与记录最后一次坐标相差50m记录一次
                        var distance = 0.0
                        if (naviPoints.isEmpty()) {
                            distance = 100.0
                        } else {
                            val lastPoint = naviPoints[naviPoints.size - 1]
                            lastPoint?.let {
                                distance = DistanceUtil.getDistance(it.latitude(),
                                    it.longitude(),
                                    enhancedLocation.latitude,
                                    enhancedLocation.longitude)
                                println("distance is $distance")
                            }
                        }
//                        if ((viewModel.naviStatus.value == NaviViewModel.NAVI_START)||(viewModel.naviStatus.value == NaviViewModel.NAVI_START_FREE)) {
//                            navigationCamera.requestNavigationCameraToFollowing()
//                        }
                        if (distance >= 50) {
                            naviPoints.add(enhancedLocation.toPoint())
                            if (viewModel.naviStatus.value == NaviViewModel.NAVI_START_FREE) {
                                renderLine()
                            }
                        }
                        canAddPoint = false
                    }
                }
            }
        }

        //原始的位置回调
        override fun onNewRawLocation(rawLocation: Location) {
        }
    }

    private fun renderLine() {
        binding.mapView.post {
            synchronized(NaviFragment1::class.java) {
                Log.i(TAG, "renderLine: 绘制路线：points size = ${naviPoints.size}")
                Log.i(TAG, "renderLine: 绘制路线：createPolylineAnnotationManager= $createPolylineAnnotationManager")
                createPolylineAnnotationManager?.create(PolylineAnnotationOptions()
                    .withPoints(naviPoints)
                    .withLineColor("#FF9000")
                    .withLineWidth(12.0))
            }
        }
    }

    //路线监听器
    private var routesObserver: RoutesObserver = RoutesObserver {
        Log.i(TAG, "$tripUID 导航日志  routesObserver：可绘制路线")
        if (it.navigationRoutes.isNotEmpty()) {
            routeLineApi.setNavigationRoutes(
                it.navigationRoutes
            ) { value ->
                binding.mapView.getMapboxMap().getStyle()?.apply {
                    routeLineView.renderRouteDrawData(this, value)
                }
            }
//                ToastUtil.of(requireActivity()).toast("路线变更了，，，，，")
            viewportDataSource.onRouteChanged(it.navigationRoutes.first())
            viewportDataSource.evaluate()
        } else {
            // remove the route line and route arrow from the map
            val style = binding.mapView.getMapboxMap().getStyle()
            if (style != null) {
                routeLineApi.clearRouteLine { value ->
                    routeLineView.renderClearRouteLineValue(
                        style,
                        value
                    )
                }
                routeArrowView.render(style, routeArrowApi.clearArrows())
            }
            // remove the route reference from camera position evaluations
            viewportDataSource.clearRouteData()
            viewportDataSource.evaluate()
        }
    }

    //路线进度行程监听器
    private val routeProgressObserver = RouteProgressObserver {
        Log.i(TAG, "routeProgressObserver progress:$it ")

        viewportDataSource.onRouteProgressChanged(it)
        viewportDataSource.evaluate()
        // draw the upcoming maneuver arrow on the map
        val style = binding.mapView.getMapboxMap().getStyle()
        if (style != null) {
            val maneuverArrowResult = routeArrowApi.addUpcomingManeuverArrow(it)
            routeArrowView.renderManeuverUpdate(style, maneuverArrowResult)
        }

        // update top banner with maneuver instructions
        val maneuvers = maneuverApi.getManeuvers(it)
        maneuvers.fold(
            { error ->
//                Toast.makeText(
//                    context,
//                    error.errorMessage,
//                    Toast.LENGTH_SHORT
//                ).show()
                Log.e(TAG, "${error.errorMessage}" )
            },
            {
                viewModel.naviStatus.postValue(NaviViewModel.NAVI_START)
                binding.maneuverView.renderManeuvers(maneuvers)
            }
        )
        //todo 路线是否绘制
//        routeLineApi.updateWithRouteProgress(it) { result ->
//            routeLineView.renderRouteLineUpdate(binding.mapView.getMapboxMap().getStyle()!!, result)
//        }


        // update bottom trip progress summary
//        binding.tripProgressView.render(
//            tripProgressApi.getTripProgress(routeProgress)
//        )

        //发送指令给设备
//        val maneuver = it.currentLegProgress?.upcomingStep?.maneuver()
        val maneuver = maneuvers.value?.get(0)
        //获取转角枚举?

        var iconNo: Int?=null
        maneuver?.let {
            iconNo = turnIconHelper.getIconNo(maneuver.primary.type, maneuver.primary.modifier)
//        ToastUtil.of(requireActivity()).toast("导航sdk 转角:${maneuver?.primary.type} modifier:${maneuver?.primary.modifier }\n iconNo:${iconNo}")
            if (iconNo != null) {
                naviIndex = iconNo!!
            }
        }
        val tripProgress=tripProgressApi?.getTripProgress(it)

        val distance = it.currentLegProgress?.currentStepProgress?.distanceRemaining?.toDouble()
//        val distance = tripProgress.distanceRemaining

//        val distance = it.currentLegProgress?.upcomingStep?.distance()
        // make sure to use the same DistanceFormatterOptions across different features
        val distanceFormatterOptions = DistanceFormatterOptions.Builder(requireContext()).build()
        val formatDistance = MapboxDistanceUtil.formatDistance(distance!!,
            distanceFormatterOptions.roundingIncrement,
            distanceFormatterOptions.unitType,
            requireContext(),
            distanceFormatterOptions.locale)
        var remainDistance = formatDistance.distance
        //400 10000 用km 其余m
        if (distance > 400) {
            remainDistance *= 1000
        }
        Log.i(TAG, ": remainDistance:$remainDistance  type:${distanceFormatterOptions.unitType}")
        //发送转角距离指令
        var data = iconNo?.let { it1 -> BleSendData(it1, distance.toInt(), 0, 0) }
        if (data != null) {
            MainPageActivity.bleSendData = data
        }
        if (data != null) {

//            BleUtil.instance?.getRidingData(data,requireActivity())
            BleUtil.instance.send70cmd(data)
            mainViewModel.naviData.postValue(data)
        }
        tripProgress?.let {
            Log.i(TAG, "trip remainDistance:${tripProgress.distanceRemaining} ")
            binding.tvTrip.text ="${String.format("%.2f", tripProgress.distanceRemaining / 1000)} Km"
        }

        when (it.currentState) {
            RouteProgressState.COMPLETE -> {
                Log.d(TAG, "导航结束了: ")
                stopNavi()
            }
        }
//        binding.tvTrip.text = "RemainDistance: $remainDistance"

//        binding.tvSpeed.post{
//            binding.tvSpeed=
//        }

    }



    val turnIconHelper = TurnIconIdHelper()

    //路线偏离监听器
    private val offRouteObserver = OffRouteObserver {
        Log.i(TAG, "offRouteObserver: $it")
    }

    //开始导航
    private fun naviStart() {
        //清空导航数据
        ToastUtil.of(requireActivity()).loading()
//        hideSoftKeyboard(binding.ed)
        startNaviTime = StringUtil.dateStr(Date(), "HH:mm")
//        val originPoint = navigationLocationProvider.lastLocation?.let {
//            Point.fromLngLat(it.longitude, it.latitude)
//        }
        mapboxNavigation.requestRoutes(
            RouteOptions.builder()
                .applyDefaultNavigationOptions(DirectionsCriteria.PROFILE_CYCLING)
                .annotationsList(
                    listOf(
//                        DirectionsCriteria.ANNOTATION_CONGESTION_NUMERIC,
//                        DirectionsCriteria.ANNOTATION_DISTANCE,
                        DirectionsCriteria.ANNOTATION_CONGESTION
//                        DirectionsCriteria.ANNOTATION_DURATION
                    )
                )
                .enableRefresh(true)
                .coordinatesList(listOf(originPoint, destinationCoordinate))
//                .baseUrl("https://api.mapbox.com/")
//                .user("mapbox")
//                .language("en")
                .bearingsList(
                    listOf(
                        originLocation?.bearing?.let {
                            Bearing.builder()
                                .angle(it.toDouble())
                                .degrees(45.0)
                                .build()
                        },
                        null
                    )
                )
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    Log.i(TAG, "onCanceled: ")
                    ToastUtil.of(requireActivity()).cancelLoading()
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    ToastUtil.of(requireActivity()).toast("No suitable edges near location")
                    ToastUtil.of(requireActivity()).cancelLoading()
                    Log.i(TAG, "onFailure:$reasons")
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin,
                ) {
//                ToastUtil.of(requireActivity()).toast("Search route success")
                    setRouteAndStartNavigation(routes)
                    ToastUtil.of(requireActivity()).cancelLoading()
                }

            }
        )

    }

    private fun clearPointsRecord() {
        synchronized(NaviFragment1::class.java) {
            naviPoints.clear()
        }
    }

    private fun naviStartParam() {
        startTime = System.currentTimeMillis()
        startNaviTime = StringUtil.dateStr(Date(), "HH:mm")
        startBattery = battery
        startTrip = tripOnce
    }

    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource
    private lateinit var navigationCamera: NavigationCamera


    //绘制线路
    private lateinit var routeLineApi: MapboxRouteLineApi
    private lateinit var routeLineView: MapboxRouteLineView

    //绘制方向箭头
    private val routeArrowApi: MapboxRouteArrowApi = MapboxRouteArrowApi()
    private lateinit var routeArrowView: MapboxRouteArrowView
    private lateinit var maneuverApi: MapboxManeuverApi

    private fun setRouteAndStartNavigation(routes: List<NavigationRoute>) {
        //设置路线
        mapboxNavigation.setNavigationRoutes(routes)
        navigationCamera.requestNavigationCameraToFollowing()
        viewModel.naviStatus.postValue(NaviViewModel.NAVI_START)
    }


    private fun updateCamera(enhancedLocation: Location) {
        val mapAnimationOptions = MapAnimationOptions.Builder().duration(0).build()
        binding.mapView.camera.easeTo(CameraOptions.Builder()
            .center(Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
            .zoom(14.0)
            .padding(EdgeInsets(0.0, 0.0, 200.0, 0.0)).build(),
            mapAnimationOptions
        )
    }

    private fun updateCameraNoDuration(enhancedLocation: Location) {
        val mapAnimationOptions = MapAnimationOptions.Builder().build()
//        binding.mapView.camera.easeTo(CameraOptions.Builder()
//            .center(Point.fromLngLat(enhancedLocation.longitude, enhancedLocation.latitude))
//            .zoom(14.0)
//            .padding(EdgeInsets(0.0, 0.0, 300.0, 0.0)).build(),
//            mapAnimationOptions)

        navigationCamera.requestNavigationCameraToFollowing(
            stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                .maxDuration(0) // instant transition
                .build()
        )
    }


    private fun release() {
        Log.i(TAG, "release: ")
        maneuverApi.cancel()
        routeLineApi.cancel()
        routeLineView.cancel()
//        mapboxNavigation.stopTripSession()
        mapboxNavigation.unregisterLocationObserver(locationObserver)
        mapboxNavigation.unregisterRoutesObserver(routesObserver)
        mapboxNavigation.unregisterRouteProgressObserver(routeProgressObserver)
        mapboxNavigation.unregisterOffRouteObserver(offRouteObserver)
        EventBus.getDefault().unregister(this)
        recordTimer?.cancel()
        recordTimer = null
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onLoop(ev: Stoploop) {
        if (ev.page != Stoploop.NAVI)
            binding.rlContainer.visibility = View.GONE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onBleData(data: SeekRunData) {
        if (null != data.data70) {
            val speedStr = StringUtil.instance?.changeTvSize("${data.data70!!.speed}")
            binding.tvSpeed.text = speedStr
            binding.tvSpeed1.text = speedStr

            val batteryStr = StringUtil.instance?.changeTvSize("${data.data70!!.battery}%")
            binding.llBattery.visibility = View.VISIBLE
            binding.llBattery1.visibility = View.VISIBLE
            binding.tvBattery.text = batteryStr
            binding.tvBattery1.text = batteryStr
            binding.pbBattery.progress = data.data70!!.battery
            binding.pbBattery1.progress = data.data70!!.battery
            battery = data.data70!!.battery

            binding.imgLed1.visibility =
                if (data.data70!!.led == 0) View.INVISIBLE else View.VISIBLE
            binding.imgPush1.visibility =
                if (data.data70!!.zhutui == 0) View.INVISIBLE else View.VISIBLE
        }
        if (null != data.data74) {
            tripOnce = data.data74!!.distanceOnce
        }
        if (null != data.data72) {
            avgSpped = data.data72!!.avgSpeed
            maxSpped = data.data72!!.maxSpeed
        }

    }


    override fun onDestroy() {
        super.onDestroy()
//        release()
        Log.i(TAG, "$tripUID 导航日志  onPause：解除监听")
    }
}